package org.apache.mahout;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.impl.neighborhood.ThresholdUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.PearsonCorrelationSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.UserBasedRecommender;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by wangxichun on 2015/1/23.
 */
public class Demo {

    @Test
    public void demo() throws IOException, TasteException {
        DataModel model = new FileDataModel(new File("J:\\code\\openSrc\\mahout-master\\examples\\src\\test\\resources\\dataset.csv"));
        UserSimilarity similarity = new PearsonCorrelationSimilarity(model); //wxc pro 2015-02-05 16:42:30 PearsonCorrelationSimilarity也是个ItemSimilarity， 这里只用UserSimilarity接口的特征？
        UserNeighborhood neighborhood = new ThresholdUserNeighborhood(0.1, similarity, model);

        UserBasedRecommender recommender = new GenericUserBasedRecommender(model, neighborhood, similarity);


        List<RecommendedItem> recommendations = recommender.recommend(2, 3); //wxc 2015-02-05 16:59:13 针对UserId是2的用户， 推荐出三个Item来。
        System.out.println("recommendations size: " + recommendations.size());
        for (RecommendedItem recommendation : recommendations) {
            System.out.println(recommendation);
        }
    }
}
