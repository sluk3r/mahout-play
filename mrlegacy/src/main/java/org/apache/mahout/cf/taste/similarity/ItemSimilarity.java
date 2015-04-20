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

package org.apache.mahout.cf.taste.similarity;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;

//wxc pro 2015-4-10:18:06:35 这个接口很明确， 计算指定一组Item之间的相似度。

/**
 * <p>
 * Implementations of this interface define a notion of similarity between two items. Implementations should  //wxc pro 2015-02-05 16:22:51 这个notion具体指？
 * return values in the range -1.0 to 1.0, with 1.0 representing perfect similarity.
 * </p>
 * 
 * @see UserSimilarity
 */
public interface ItemSimilarity extends Refreshable { //wxc pro 2015-02-05 16:23:16  实现时的数据存储是怎样的？一共有20个实现类。
  
  /**
   * <p>
   * Returns the degree of similarity, of two items, based on the preferences that users have expressed for
   * the items.
   * </p>
   * 
   * @param itemID1 first item ID
   * @param itemID2 second item ID
   * @return similarity between the items, in [-1,1] or {@link Double#NaN} similarity is unknown
   * @throws org.apache.mahout.cf.taste.common.NoSuchItemException
   *  if either item is known to be non-existent in the data
   * @throws TasteException if an error occurs while accessing the data
   */
  double itemSimilarity(long itemID1, long itemID2) throws TasteException; //wxc pro 2015-02-05 16:21:10 这是要给两个item打分？

  /**
   * <p>A bulk-get version of {@link #itemSimilarity(long, long)}.</p>
   *
   * @param itemID1 first item ID
   * @param itemID2s second item IDs to compute similarity with
   * @return similarity between itemID1 and other items
   * @throws org.apache.mahout.cf.taste.common.NoSuchItemException
   *  if any item is known to be non-existent in the data
   * @throws TasteException if an error occurs while accessing the data
   */
  double[] itemSimilarities(long itemID1, long[] itemID2s) throws TasteException;

  /**
   * @return all IDs of similar items, in no particular order
   */
  long[] allSimilarItemIDs(long itemID) throws TasteException; //wxc pro 2015-02-05 16:21:38 貌似是针对所有的Item查相似性。
}
