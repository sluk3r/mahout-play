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

package org.apache.mahout.cf.taste.impl.similarity;

import com.google.common.base.Preconditions;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.RefreshHelper;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;

import java.util.Collection;

public abstract class AbstractItemSimilarity implements ItemSimilarity {

  private final DataModel dataModel;
  private final RefreshHelper refreshHelper;

  protected AbstractItemSimilarity(DataModel dataModel) {
    Preconditions.checkArgument(dataModel != null, "dataModel is null");
    this.dataModel = dataModel;
    this.refreshHelper = new RefreshHelper(null);
    refreshHelper.addDependency(this.dataModel);
  }

  protected DataModel getDataModel() {
    return dataModel;
  }

  @Override
  public long[] allSimilarItemIDs(long itemID) throws TasteException {
    FastIDSet allSimilarItemIDs = new FastIDSet();
    LongPrimitiveIterator allItemIDs = dataModel.getItemIDs();
    while (allItemIDs.hasNext()) {//wxc  2015-02-05 16:29:21 跟猜的一致， 要做遍历操作。
      long possiblySimilarItemID = allItemIDs.nextLong();//wxc pro 2015-02-05 16:30:04 这个nextLong有什么特殊意义？怎么是Long？一般的迭代器只是一个Next操作。
      if (!Double.isNaN(itemSimilarity(itemID, possiblySimilarItemID))) { //wxc 2015-02-05 16:31:20 还是要依赖这个itemSimilarity。
        allSimilarItemIDs.add(possiblySimilarItemID);
      }
    }
    return allSimilarItemIDs.toArray();
  }

  @Override
  public void refresh(Collection<Refreshable> alreadyRefreshed) {
    refreshHelper.refresh(alreadyRefreshed);
  } //wxc pro 2015-02-05 16:27:47 出于什么考虑， 要搞这个刷新的接口？
}
