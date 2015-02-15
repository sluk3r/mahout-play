/**
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

package org.apache.mahout.cf.taste.impl.recommender;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.RefreshHelper;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.cf.taste.recommender.UserBasedRecommender;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.apache.mahout.common.LongPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * <p>
 * A simple {@link org.apache.mahout.cf.taste.recommender.Recommender}
 * which uses a given {@link DataModel} and {@link UserNeighborhood} to produce recommendations.
 * </p>
 */
public class GenericUserBasedRecommender extends AbstractRecommender implements UserBasedRecommender {
  
  private static final Logger log = LoggerFactory.getLogger(GenericUserBasedRecommender.class);
  
  private final UserNeighborhood neighborhood;
  private final UserSimilarity similarity;
  private final RefreshHelper refreshHelper;
  private EstimatedPreferenceCapper capper;
  
  public GenericUserBasedRecommender(DataModel dataModel,
                                     UserNeighborhood neighborhood,
                                     UserSimilarity similarity) {
    super(dataModel);
    Preconditions.checkArgument(neighborhood != null, "neighborhood is null");
    this.neighborhood = neighborhood;
    this.similarity = similarity;
    this.refreshHelper = new RefreshHelper(new Callable<Void>() {
      @Override
      public Void call() {
        capper = buildCapper();
        return null;
      }
    });
    refreshHelper.addDependency(dataModel);
    refreshHelper.addDependency(similarity);
    refreshHelper.addDependency(neighborhood);
    capper = buildCapper();
  }
  
  public UserSimilarity getSimilarity() {
    return similarity;
  }
  
  @Override //wxc pro 2015-02-05 17:01:06 这个includeKnownItems具体指？怎么就算是Known了？
  public List<RecommendedItem> recommend(long userID, int howMany, IDRescorer rescorer, boolean includeKnownItems)
    throws TasteException {
    Preconditions.checkArgument(howMany >= 1, "howMany must be at least 1");

    log.debug("Recommending items for user ID '{}'", userID);
    //wxc pro 2015-02-05 17:03:36 感觉这个getUserNeighborhood应该加个阈值或返回的个数限制， 而不是直接返回所有吧， 这里也不大可能是返回所有， 问题来了， 据什么来做末尾的取舍的？ 构造方法里的threshold应该是起这个作用。 NearestNUserNeighborhood这个类貌似是解决返回指定是是最大个数的实现。
    long[] theNeighborhood = neighborhood.getUserNeighborhood(userID);

    if (theNeighborhood.length == 0) {
      return Collections.emptyList();
    }

    FastIDSet allItemIDs = getAllOtherItems(theNeighborhood, userID, includeKnownItems);
    //wxc pro 2015-02-05 17:17:49 到这里了才做Estimator操作？是不是以Filter的方式放到前一阶段比较好？
    TopItems.Estimator<Long> estimator = new Estimator(userID, theNeighborhood);

    List<RecommendedItem> topItems = TopItems
        .getTopItems(howMany, allItemIDs.iterator(), rescorer, estimator);

    log.debug("Recommendations are: {}", topItems);
    return topItems;
  }
  
  @Override
  public float estimatePreference(long userID, long itemID) throws TasteException {
    DataModel model = getDataModel();
    Float actualPref = model.getPreferenceValue(userID, itemID);
    if (actualPref != null) {
      return actualPref;
    }
    long[] theNeighborhood = neighborhood.getUserNeighborhood(userID);
    return doEstimatePreference(userID, theNeighborhood, itemID);
  }
  
  @Override
  public long[] mostSimilarUserIDs(long userID, int howMany) throws TasteException {
    return mostSimilarUserIDs(userID, howMany, null);
  }
  
  @Override
  public long[] mostSimilarUserIDs(long userID, int howMany, Rescorer<LongPair> rescorer) throws TasteException {
    TopItems.Estimator<Long> estimator = new MostSimilarEstimator(userID, similarity, rescorer);
    return doMostSimilarUsers(howMany, estimator);
  }
  
  private long[] doMostSimilarUsers(int howMany, TopItems.Estimator<Long> estimator) throws TasteException {
    DataModel model = getDataModel();
    return TopItems.getTopUsers(howMany, model.getUserIDs(), null, estimator);
  }
  
  protected float doEstimatePreference(long theUserID, long[] theNeighborhood, long itemID) throws TasteException {
    if (theNeighborhood.length == 0) {
      return Float.NaN;
    }
    DataModel dataModel = getDataModel();
    double preference = 0.0;
    double totalSimilarity = 0.0;
    int count = 0;
    for (long userID : theNeighborhood) {
      if (userID != theUserID) {
        // See GenericItemBasedRecommender.doEstimatePreference() too
        Float pref = dataModel.getPreferenceValue(userID, itemID);
        if (pref != null) {
          double theSimilarity = similarity.userSimilarity(theUserID, userID); //wxc pro 2015-02-05 18:24:57 两个User的相似程度， 前面已经搞过了吧？
          if (!Double.isNaN(theSimilarity)) {
            preference += theSimilarity * pref;
            totalSimilarity += theSimilarity;
            count++;
          }
        }
      }
    }
    // Throw out the estimate if it was based on no data points, of course, but also if based on
    // just one. This is a bit of a band-aid on the 'stock' item-based algorithm for the moment.
    // The reason is that in this case the estimate is, simply, the user's rating for one item
    // that happened to have a defined similarity. The similarity score doesn't matter, and that
    // seems like a bad situation.
    if (count <= 1) { //wxc pro 2015-02-05 18:25:47 这个小于等于1代表什么？
      return Float.NaN;
    }
    float estimate = (float) (preference / totalSimilarity);
    if (capper != null) { //wxc pro 2015-02-05 18:26:12  capper代表啥？
      estimate = capper.capEstimate(estimate);
    }
    return estimate;
  }
  
  protected FastIDSet getAllOtherItems(long[] theNeighborhood, long theUserID, boolean includeKnownItems)
    throws TasteException {
    DataModel dataModel = getDataModel();
    FastIDSet possibleItemIDs = new FastIDSet();
    for (long userID : theNeighborhood) {
      possibleItemIDs.addAll(dataModel.getItemIDsFromUser(userID)); //wxc 2015-02-05 17:14:41 这个getItemIDsFromUser仅仅提供一个取数操作，本身并没有计算功能。
    }
    if (!includeKnownItems) {
      possibleItemIDs.removeAll(dataModel.getItemIDsFromUser(theUserID)); //wxc pro 2015-02-05 17:09:58 把自己给剔除掉？这样是有个不好的问题： 分页方面的控制。
    }
    return possibleItemIDs;
  }
  
  @Override
  public void refresh(Collection<Refreshable> alreadyRefreshed) {
    refreshHelper.refresh(alreadyRefreshed);
  }
  
  @Override
  public String toString() {
    return "GenericUserBasedRecommender[neighborhood:" + neighborhood + ']';
  }

  private EstimatedPreferenceCapper buildCapper() {
    DataModel dataModel = getDataModel();
    if (Float.isNaN(dataModel.getMinPreference()) && Float.isNaN(dataModel.getMaxPreference())) {
      return null;
    } else {
      return new EstimatedPreferenceCapper(dataModel);
    }
  }
  
  private static final class MostSimilarEstimator implements TopItems.Estimator<Long> {
    
    private final long toUserID;
    private final UserSimilarity similarity;
    private final Rescorer<LongPair> rescorer;
    
    private MostSimilarEstimator(long toUserID, UserSimilarity similarity, Rescorer<LongPair> rescorer) {
      this.toUserID = toUserID;
      this.similarity = similarity;
      this.rescorer = rescorer;
    }
    
    @Override
    public double estimate(Long userID) throws TasteException {
      // Don't consider the user itself as a possible most similar user
      if (userID == toUserID) {
        return Double.NaN;
      }
      if (rescorer == null) {
        return similarity.userSimilarity(toUserID, userID);
      } else {
        LongPair pair = new LongPair(toUserID, userID);
        if (rescorer.isFiltered(pair)) {
          return Double.NaN;
        }
        double originalEstimate = similarity.userSimilarity(toUserID, userID);
        return rescorer.rescore(pair, originalEstimate);
      }
    }
  }
  
  private final class Estimator implements TopItems.Estimator<Long> {
    
    private final long theUserID;
    private final long[] theNeighborhood;
    
    Estimator(long theUserID, long[] theNeighborhood) {
      this.theUserID = theUserID;
      this.theNeighborhood = theNeighborhood;
    }
    
    @Override
    public double estimate(Long itemID) throws TasteException {
      return doEstimatePreference(theUserID, theNeighborhood, itemID);
    }
  }
}
