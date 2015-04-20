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

package org.apache.mahout.cf.taste.model;

import java.io.Serializable;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;

/**
 * <p>
 * Implementations represent a repository of information about users and their associated {@link Preference}s
 * for items. //wxc pro 2015-01-30 11:26:00 这个DataModel是不是Mahout的核心基础类？数据层面上？
 * </p>
 */
public interface DataModel extends Refreshable, Serializable {
  
  /**
   * @return all user IDs in the model, in order
   * @throws TasteException
   *           if an error occurs while accessing the data
   */
  //wxc 2015-4-10:8:37:36 并没有直接返回一个Long型来， 而是封装成一个Iterator来。 现在的直觉是封装过度。 不过应该是有它内存好处的。
  LongPrimitiveIterator getUserIDs() throws TasteException; //wxc pro 2015-4-10:8:37:02 这里怎么会抛出Taste异常？ 看了下，除后三个外， 别的方法都抛出这个异常。 异常的设计惯例？
  
  /**
   * @param userID
   *          ID of user to get prefs for
   * @return user's preferences, ordered by item ID
   * @throws org.apache.mahout.cf.taste.common.NoSuchUserException
   *           if the user does not exist
   * @throws TasteException
   *           if an error occurs while accessing the data
   */
  //wxc  2015-4-10:8:41:34 又是一个封装。 这个封装把Array内置起来， 是不是也透露这个一对多是根基，再往下就是对业务的深刻把握。 体现业务建模的功底。
  PreferenceArray getPreferencesFromUser(long userID) throws TasteException;
  
  /**
   * @param userID
   *          ID of user to get prefs for
   * @return IDs of items user expresses a preference for
   * @throws org.apache.mahout.cf.taste.common.NoSuchUserException
   *           if the user does not exist
   * @throws TasteException
   *           if an error occurs while accessing the data
   */
  FastIDSet getItemIDsFromUser(long userID) throws TasteException; //wxc pro 2015-4-10:8:47:18 这个方法跟前面的getPreferencesFromUser有什么区别？
  
  /**
   * @return a {@link LongPrimitiveIterator} of all item IDs in the model, in order
   * @throws TasteException
   *           if an error occurs while accessing the data
   */
  LongPrimitiveIterator getItemIDs() throws TasteException;
  
  /**
   * @param itemID
   *          item ID
   * @return all existing {@link Preference}s expressed for that item, ordered by user ID, as an array
   * @throws org.apache.mahout.cf.taste.common.NoSuchItemException
   *           if the item does not exist
   * @throws TasteException
   *           if an error occurs while accessing the data
   */
  PreferenceArray getPreferencesForItem(long itemID) throws TasteException;//wxc 2015-4-10:8:48:40 Item、Preference和User， 推荐系统的核心。Preference又是User和Item的结合。这样看DataModel并不是仅仅是对Data封装，而已经包含进了Item和Preference的概念。
  
  /**
   * Retrieves the preference value for a single user and item.
   * 
   * @param userID
   *          user ID to get pref value from
   * @param itemID
   *          item ID to get pref value for
   * @return preference value from the given user for the given item or null if none exists
   * @throws org.apache.mahout.cf.taste.common.NoSuchUserException
   *           if the user does not exist
   * @throws TasteException
   *           if an error occurs while accessing the data
   */
  Float getPreferenceValue(long userID, long itemID) throws TasteException;//wxc 2015-4-10:8:52:53 应该是针对指定用户和Item看推荐值多大？

  /**
   * Retrieves the time at which a preference value from a user and item was set, if known. //wxc pro 2015-4-10:8:54:19 set具体指？
   * Time is expressed in the usual way, as a number of milliseconds since the epoch.
   *
   * @param userID user ID for preference in question
   * @param itemID item ID for preference in question
   * @return time at which preference was set or null if no preference exists or its time is not known
   * @throws org.apache.mahout.cf.taste.common.NoSuchUserException if the user does not exist
   * @throws TasteException if an error occurs while accessing the data
   */
  Long getPreferenceTime(long userID, long itemID) throws TasteException;
  
  /**
   * @return total number of items known to the model. This is generally the union of all items preferred by //wxc pro 2015-4-10:8:56:21 为什么这里把preferred特别指出来？
   *         at least one user but could include more.
   * @throws TasteException
   *           if an error occurs while accessing the data
   */
  int getNumItems() throws TasteException;
  
  /**
   * @return total number of users known to the model.//wxc pro 2015-4-10:8:57:07 一个跟当前方法没直接关系的问题。 这个数值怎么能反映出来动态的增减？
   * @throws TasteException
   *           if an error occurs while accessing the data
   */
  int getNumUsers() throws TasteException;
  
  /**
   * @param itemID item ID to check for
   * @return the number of users who have expressed a preference for the item //wxc pro 2015-4-10:9:01:26  express了， 这里不考虑express的具体值？
   * @throws TasteException if an error occurs while accessing the data
   */
  int getNumUsersWithPreferenceFor(long itemID) throws TasteException;

  /**
   * @param itemID1 first item ID to check for
   * @param itemID2 second item ID to check for
   * @return the number of users who have expressed a preference for the items
   * @throws TasteException if an error occurs while accessing the data
   */
  int getNumUsersWithPreferenceFor(long itemID1, long itemID2) throws TasteException; //wxc pro 2015-4-10:9:03:14 为什么单独搞一个两个Item的方法？而没有做成一个通用的方法（即指定一组Item）？
  
  /**
   * <p>
   * Sets a particular preference (item plus rating) for a user.
   * </p>
   * 
   * @param userID
   *          user to set preference for
   * @param itemID
   *          item to set preference for
   * @param value
   *          preference value
   * @throws org.apache.mahout.cf.taste.common.NoSuchItemException
   *           if the item does not exist
   * @throws org.apache.mahout.cf.taste.common.NoSuchUserException
   *           if the user does not exist
   * @throws TasteException
   *           if an error occurs while accessing the data
   */
  void setPreference(long userID, long itemID, float value) throws TasteException;//wxc pro 2015-4-10:9:04:58 这个方法是在加载时调用？
  
  /**
   * <p>
   * Removes a particular preference for a user.
   * </p>
   * 
   * @param userID
   *          user from which to remove preference
   * @param itemID
   *          item to remove preference for
   * @throws org.apache.mahout.cf.taste.common.NoSuchItemException
   *           if the item does not exist
   * @throws org.apache.mahout.cf.taste.common.NoSuchUserException
   *           if the user does not exist
   * @throws TasteException
   *           if an error occurs while accessing the data
   */
  void removePreference(long userID, long itemID) throws TasteException;//wxc pro 2015-4-10:9:06:21 把已经表达了Pref的User和Item对去掉。 这样的动态增减也就表明了为什么定义LongPrimitiveIterator这样的加载机制，而不是把值直接返回。

  /**
   * @return true if this implementation actually stores and returns distinct preference values;
   *  that is, if it is not a 'boolean' DataModel
   */
  boolean hasPreferenceValues();//wxc pro 2015-4-10:9:09:38   DataModel有boolean与否之分？

  /** //wxc pro 2015-4-10:9:12:36 关于Recommender的这一段完全看不懂。
   * @return the maximum preference value that is possible in the current problem domain being evaluated. For
   * example, if the domain is movie ratings on a scale of 1 to 5, this should be 5. While a
   * {@link org.apache.mahout.cf.taste.recommender.Recommender} may estimate a preference value above 5.0, it
   * isn't "fair" to consider that the system is actually suggesting an impossible rating of, say, 5.4 stars.
   * In practice the application would cap this estimate to 5.0. Since evaluators evaluate
   * the difference between estimated and actual value, this at least prevents this effect from unfairly
   * penalizing a {@link org.apache.mahout.cf.taste.recommender.Recommender}
   */
  float getMaxPreference(); //wxc pro 2015-4-10:9:11:58 拿到这个值有什么用？

  /**
   * @see #getMaxPreference()
   */
  float getMinPreference();
  
}
