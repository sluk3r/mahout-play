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
//wxc 2015-4-10:17:20:21 这个接口只有两个方法。先混个脸熟吧。
/**
 * <p>
 * Implementations of this interface define a notion of similarity between two users. Implementations should
 * return values in the range -1.0 to 1.0, with 1.0 representing perfect similarity.
 * </p>
 * 
 * @see ItemSimilarity
 */
public interface UserSimilarity extends Refreshable { //wxc pro 2015-02-05 16:32:41  这个接口跟ItemSimilarity有什么差别？或者说有什么关联？
  
  /**
   * <p>
   * Returns the degree of similarity, of two users, based on the their preferences.
   * </p>
   * 
   * @param userID1 first user ID
   * @param userID2 second user ID
   * @return similarity between the users, in [-1,1] or {@link Double#NaN} similarity is unknown
   * @throws org.apache.mahout.cf.taste.common.NoSuchUserException
   *  if either user is known to be non-existent in the data
   * @throws TasteException if an error occurs while accessing the data
   */
  double userSimilarity(long userID1, long userID2) throws TasteException;

  // Should we implement userSimilarities() like ItemSimilarity.itemSimilarities()?
  
  /**
   * <p>
   * Attaches a {@link PreferenceInferrer} to the {@link UserSimilarity} implementation. //wxc pro 2015-4-10:17:12:27 这个PreferenceInferrer是什么概念？计算时要用到这个？
   * </p>
   * 
   * @param inferrer {@link PreferenceInferrer}
   */
  void setPreferenceInferrer(PreferenceInferrer inferrer);//wxc pro 2015-4-10:17:23:14 接下来的问题是这个方法什么时候调用， 以及这个接口里又定义了哪些东西？顺便看了下，PreferenceInferrer接口里只有一个方法。看ItemSimilarity没有PreferenceInferrer方面的概念，
  
}
