/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.classifier.naivebayes

import org.apache.mahout.classifier.stats.{ResultAnalyzer, ClassifierResult}
import org.apache.mahout.math._
import scalabindings._
import scalabindings.RLikeOps._
import drm.RLikeDrmOps._
import drm._
import scala.reflect.ClassTag
import scala.language.asInstanceOf
import collection._
import scala.collection.JavaConversions._

/**
 * Distributed training of a Naive Bayes model. Follows the approach presented in Rennie et.al.: Tackling the poor
 * assumptions of Naive Bayes Text classifiers, ICML 2003, http://people.csail.mit.edu/jrennie/papers/icml03-nb.pdf
 */
trait NaiveBayes extends java.io.Serializable{

  /** default value for the Laplacian smoothing parameter */
  def defaultAlphaI = 1.0f

  // function to extract categories from string keys
  type CategoryParser = String => String

  /** Default: seqdirectory/seq2Sparse Categories are Stored in Drm Keys as: /Category/document_id */
  def seq2SparseCategoryParser: CategoryParser = x => x.split("/")(1)


  /**
   * Distributed training of a Naive Bayes model. Follows the approach presented in Rennie et.al.: Tackling the poor
   * assumptions of Naive Bayes Text classifiers, ICML 2003, http://people.csail.mit.edu/jrennie/papers/icml03-nb.pdf
   *
   * @param observationsPerLabel a DrmLike[Int] matrix containing term frequency counts for each label.
   * @param trainComplementary whether or not to train a complementary Naive Bayes model
   * @param alphaI Laplace smoothing parameter
   * @return trained naive bayes model
   */
  def train(observationsPerLabel: DrmLike[Int],
            labelIndex: Map[String, Integer],
            trainComplementary: Boolean = true,
            alphaI: Float = defaultAlphaI): NBModel = {

    // Summation of all weights per feature
    val weightsPerFeature = observationsPerLabel.colSums

    // Distributed summation of all weights per label
    val weightsPerLabel = observationsPerLabel.rowSums

    // Collect a matrix to pass to the NaiveBayesModel
    val inCoreTFIDF = observationsPerLabel.collect

    // perLabelThetaNormalizer Vector is expected by NaiveBayesModel. We can pass a null value
    // or Vector of zeroes in the case of a standard NB model.
    var thetaNormalizer = weightsPerFeature.like()

    // Instantiate a trainer and retrieve the perLabelThetaNormalizer Vector from it in the case of
    // a complementary NB model
    if (trainComplementary) {
      val thetaTrainer = new ComplementaryNBThetaTrainer(weightsPerFeature,
                                                         weightsPerLabel,
                                                         alphaI)
      // local training of the theta normalization
      for (labelIndex <- 0 until inCoreTFIDF.nrow) {
        thetaTrainer.train(labelIndex, inCoreTFIDF(labelIndex, ::))
      }
      thetaNormalizer = thetaTrainer.retrievePerLabelThetaNormalizer
    }

    new NBModel(inCoreTFIDF,
                weightsPerFeature,
                weightsPerLabel,
                thetaNormalizer,
                labelIndex,
                alphaI,
                trainComplementary)
  }

  /**
   * Extract label Keys from raw TF or TF-IDF Matrix generated by seqdirectory/seq2sparse
   * and aggregate TF or TF-IDF values by their label
   * Override this method in engine specific modules to optimize
   *
   * @param stringKeyedObservations DrmLike matrix; Output from seq2sparse
   *   in form K = eg./Category/document_title
   *           V = TF or TF-IDF values per term
   * @param cParser a String => String function used to extract categories from
   *   Keys of the stringKeyedObservations DRM. The default
   *   CategoryParser will extract "Category" from: '/Category/document_id'
   * @return  (labelIndexMap,aggregatedByLabelObservationDrm)
   *   labelIndexMap is a HashMap [String, Integer] K = label row index
   *                                                V = label
   *   aggregatedByLabelObservationDrm is a DrmLike[Int] of aggregated
   *   TF or TF-IDF counts per label
   */
  def extractLabelsAndAggregateObservations[K: ClassTag](stringKeyedObservations: DrmLike[K],
                                                         cParser: CategoryParser = seq2SparseCategoryParser)
                                                        (implicit ctx: DistributedContext):
                                                        (mutable.HashMap[String, Integer], DrmLike[Int])= {

    stringKeyedObservations.checkpoint()

    val numDocs=stringKeyedObservations.nrow
    val numFeatures=stringKeyedObservations.ncol

    // Extract categories from labels assigned by seq2sparse
    // Categories are Stored in Drm Keys as eg.: /Category/document_id

    // Get a new DRM with a single column so that we don't have to collect the
    // DRM into memory upfront.
    val strippedObeservations= stringKeyedObservations.mapBlock(ncol=1){
      case(keys, block) =>
        val blockB = block.like(keys.size, 1)
        keys -> blockB
    }

    // Extract the row label bindings (the String keys) from the slim Drm
    // strip the document_id from the row keys keeping only the category.
    // Sort the bindings alphabetically into a Vector
    val labelVectorByRowIndex = strippedObeservations
                                  .getRowLabelBindings
                                  .map(x => x._2 -> cParser(x._1))
                                  .toVector.sortWith(_._1 < _._1)

    //TODO: add a .toIntKeyed(...) method to DrmLike?

    // Copy stringKeyedObservations to an Int-Keyed Drm so that we can compute transpose
    // Copy the Collected Matrices up front for now until we hav a distributed way of converting
    val inCoreStringKeyedObservations = stringKeyedObservations.collect
    val inCoreIntKeyedObservations = new SparseMatrix(
                             stringKeyedObservations.nrow.toInt,
                             stringKeyedObservations.ncol)
    for (i <- 0 until inCoreStringKeyedObservations.nrow.toInt) {
      inCoreIntKeyedObservations(i, ::) = inCoreStringKeyedObservations(i, ::)
    }

    val intKeyedObservations= drmParallelize(inCoreIntKeyedObservations)

    stringKeyedObservations.uncache()

    var labelIndex = 0
    val labelIndexMap = new mutable.HashMap[String, Integer]
    val encodedLabelByRowIndexVector = new DenseVector(labelVectorByRowIndex.size)
    
    // Encode Categories as an Integer (Double) so we can broadcast as a vector
    // where each element is an Int-encoded category whose index corresponds
    // to its row in the Drm
    for (i <- 0 until labelVectorByRowIndex.size) {
      if (!(labelIndexMap.contains(labelVectorByRowIndex(i)._2))) {
        encodedLabelByRowIndexVector(i) = labelIndex.toDouble
        labelIndexMap.put(labelVectorByRowIndex(i)._2, labelIndex)
        labelIndex += 1
      }
      // don't like this casting but need to use a java.lang.Integer when setting rowLabelBindings
      encodedLabelByRowIndexVector(i) = labelIndexMap
                                          .getOrElse(labelVectorByRowIndex(i)._2, -1)
                                          .asInstanceOf[Int].toDouble
    }

    // "Combiner": Map and aggregate by Category. Do this by broadcasting the encoded
    // category vector and mapping a transposed IntKeyed Drm out so that all categories
    // will be present on all nodes as columns and can be referenced by
    // BCastEncodedCategoryByRowVector.  Iteratively sum all categories.
    val nLabels = labelIndex

    val bcastEncodedCategoryByRowVector = drmBroadcast(encodedLabelByRowIndexVector)

    val aggregetedObservationByLabelDrm = intKeyedObservations.t.mapBlock(ncol = nLabels) {
      case (keys, blockA) =>
        val blockB = blockA.like(keys.size, nLabels)
        var label : Int = 0
        for (i <- 0 until keys.size) {
          blockA(i, ::).nonZeroes().foreach { elem =>
            label = bcastEncodedCategoryByRowVector.get(elem.index).toInt
            blockB(i, label) = blockB(i, label) + blockA(i, elem.index)
          }
        }
        keys -> blockB
    }.t

    (labelIndexMap, aggregetedObservationByLabelDrm)
  }

  /**
   * Test a trained model with a labeled dataset
   * @param model a trained NBModel
   * @param testSet a labeled testing set
   * @param testComplementary test using a complementary or a standard NB classifier
   * @param cParser a String => String function used to extract categories from
   *   Keys of the testing set DRM. The default
   *   CategoryParser will extract "Category" from: '/Category/document_id'
   * @tparam K implicitly determined Key type of test set DRM: String
   * @return a result analyzer with confusion matrix and accuracy statistics
   */
  def test[K: ClassTag](model: NBModel,
                        testSet: DrmLike[K],
                        testComplementary: Boolean = false,
                        cParser: CategoryParser = seq2SparseCategoryParser)
                        (implicit ctx: DistributedContext): ResultAnalyzer = {

    val labelMap = model.labelIndex

    val numLabels = model.numLabels

    testSet.checkpoint()

    val numTestInstances = testSet.nrow.toInt

    // instantiate the correct type of classifier
    val classifier = testComplementary match {
      case true => new ComplementaryNBClassifier(model) with Serializable
      case _ => new StandardNBClassifier(model) with Serializable
    }
    
    if (testComplementary) {
      assert(testComplementary == model.isComplementary,
        "Complementary Label Assignment requires Complementary Training")
    }

    /**  need to change the model around so that we can broadcast it?            */
    /*   for now just classifying each sequentially.                             */
    /*
    val bcastWeightMatrix = drmBroadcast(model.weightsPerLabelAndFeature)
    val bcastFeatureWeights = drmBroadcast(model.weightsPerFeature)
    val bcastLabelWeights = drmBroadcast(model.weightsPerLabel)
    val bcastWeightNormalizers = drmBroadcast(model.perlabelThetaNormalizer)
    val bcastLabelIndex = labelMap
    val alphaI = model.alphaI
    val bcastIsComplementary = model.isComplementary

    val scoredTestSet = testSet.mapBlock(ncol = numLabels){
      case (keys, block)=>
        val closureModel = new NBModel(bcastWeightMatrix,
                                       bcastFeatureWeights,
                                       bcastLabelWeights,
                                       bcastWeightNormalizers,
                                       bcastLabelIndex,
                                       alphaI,
                                       bcastIsComplementary)
        val classifier = closureModel match {
          case xx if model.isComplementary => new ComplementaryNBClassifier(closureModel)
          case _ => new StandardNBClassifier(closureModel)
        }
        val numInstances = keys.size
        val blockB= block.like(numInstances, numLabels)
        for(i <- 0 until numInstances){
          blockB(i, ::) := classifier.classifyFull(block(i, ::) )
        }
        keys -> blockB
    }

    // may want to strip this down if we think that numDocuments x numLabels wont fit into memory
    val testSetLabelMap = scoredTestSet.getRowLabelBindings

    // collect so that we can slice rows.
    val inCoreScoredTestSet = scoredTestSet.collect

    testSet.uncache()
    */


    /** Sequentially: */

    // Since we cant broadcast the model as is do it sequentially up front for now
    val inCoreTestSet = testSet.collect

    // get the labels of the test set and extract the keys
    val testSetLabelMap = testSet.getRowLabelBindings //.map(x => cParser(x._1) -> x._2)

    // empty Matrix in which we'll set the classification scores
    val inCoreScoredTestSet = testSet.like(numTestInstances, numLabels)

    testSet.uncache()
    
    for (i <- 0 until numTestInstances) {
      inCoreScoredTestSet(i, ::) := classifier.classifyFull(inCoreTestSet(i, ::))
    }

    // todo: reverse the labelMaps in training and through the model?

    // reverse the label map and extract the labels
    val reverseTestSetLabelMap = testSetLabelMap.map(x => x._2 -> cParser(x._1))

    val reverseLabelMap = labelMap.map(x => x._2 -> x._1)

    val analyzer = new ResultAnalyzer(labelMap.keys.toList.sorted, "DEFAULT")

    // need to do this with out collecting
    // val inCoreScoredTestSet = scoredTestSet.collect
    for (i <- 0 until numTestInstances) {
      val (bestIdx, bestScore) = argmax(inCoreScoredTestSet(i,::))
      val classifierResult = new ClassifierResult(reverseLabelMap(bestIdx), bestScore)
      analyzer.addInstance(reverseTestSetLabelMap(i), classifierResult)
    }

    analyzer
  }

  /**
   * argmax with values as well
   * returns a tuple of index of the max score and the score itself.
   * @param v Vector of of scores
   * @return  (bestIndex, bestScore)
   */
  def argmax(v: Vector): (Int, Double) = {
    var bestIdx: Int = Integer.MIN_VALUE
    var bestScore: Double = Integer.MIN_VALUE.asInstanceOf[Int].toDouble
    for(i <- 0 until v.size) {
      if(v(i) > bestScore){
        bestScore = v(i)
        bestIdx = i
      }
    }
    (bestIdx, bestScore)
  }

}

object NaiveBayes extends NaiveBayes with java.io.Serializable

/**
 * Trainer for the weight normalization vector used by Transform Weight Normalized Complement
 * Naive Bayes.  See: Rennie et.al.: Tackling the poor assumptions of Naive Bayes Text classifiers,
 * ICML 2003, http://people.csail.mit.edu/jrennie/papers/icml03-nb.pdf Sec. 3.2.
 *
 * @param weightsPerFeature a Vector of summed TF or TF-IDF weights for each word in dictionary.
 * @param weightsPerLabel a Vector of summed TF or TF-IDF weights for each label.
 * @param alphaI Laplace smoothing factor. Defaut value of 1.
 */
class ComplementaryNBThetaTrainer(private val weightsPerFeature: Vector,
                                  private val weightsPerLabel: Vector,
                                  private val alphaI: Double = 1.0) {
                                   
   private val perLabelThetaNormalizer: Vector = weightsPerLabel.like()
   private val totalWeightSum: Double = weightsPerLabel.zSum
   private var numFeatures: Double = weightsPerFeature.getNumNondefaultElements

   assert(weightsPerFeature != null, "weightsPerFeature vector can not be null")
   assert(weightsPerLabel != null, "weightsPerLabel vector can not be null")

  /**
   * Train the weight normalization vector for each label
   * @param label
   * @param featurePerLabelWeight
   */
  def train(label: Int, featurePerLabelWeight: Vector) {
    val currentLabelWeight = labelWeight(label)
    // sum weights for each label including those with zero word counts
    for (i <- 0 until featurePerLabelWeight.size) {
      val currentFeaturePerLabelWeight = featurePerLabelWeight(i)
      updatePerLabelThetaNormalizer(label,
        ComplementaryNBClassifier.computeWeight(featureWeight(i),
                                                currentFeaturePerLabelWeight,
                                                totalWeightSum,
                                                currentLabelWeight,
                                                alphaI,
                                                numFeatures)
                                   )
    }
  }

  /**
   * getter for summed TF or TF-IDF weights by label
   * @param label index of label
   * @return sum of word TF or TF-IDF weights for label
   */
  def labelWeight(label: Int): Double = {
    weightsPerLabel(label)
  }

  /**
   * getter for summed TF or TF-IDF weights by word.
   * @param feature index of word.
   * @return sum of TF or TF-IDF weights for word.
   */
  def featureWeight(feature: Int): Double = {
    weightsPerFeature(feature)
  }

  /**
   * add the magnitude of the current weight to the current
   * label's corresponding Vector element.
   * @param label index of label to update.
   * @param weight weight to add.
   */
  def updatePerLabelThetaNormalizer(label: Int, weight: Double) {
    perLabelThetaNormalizer(label) = perLabelThetaNormalizer(label) + Math.abs(weight)
  }

  /**
   * Getter for the weight normalizer vector as indexed by label
   * @return a copy of the weight normalizer vector.
   */
  def retrievePerLabelThetaNormalizer: Vector = {
    perLabelThetaNormalizer.cloned
  }

}
