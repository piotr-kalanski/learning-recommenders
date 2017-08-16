package org.lenskit.mooc.hybrid;

import javafx.util.Pair;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.lenskit.api.ItemScorer;
import org.lenskit.api.Result;
import org.lenskit.bias.BiasModel;
import org.lenskit.bias.UserBiasModel;
import org.lenskit.data.ratings.Rating;
import org.lenskit.data.ratings.RatingSummary;
import org.lenskit.inject.Transient;
import org.lenskit.util.ProgressLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.*;

/**
 * Trainer that builds logistic models.
 */
public class LogisticModelProvider implements Provider<LogisticModel> {
    private static final Logger logger = LoggerFactory.getLogger(LogisticModelProvider.class);
    private static final double LEARNING_RATE = 0.00005;
    private static final int ITERATION_COUNT = 100;

    private final LogisticTrainingSplit dataSplit;
    private final BiasModel baseline;
    private final RecommenderList recommenders;
    private final RatingSummary ratingSummary;
    private final int parameterCount;
    private final Random random;

    @Inject
    public LogisticModelProvider(@Transient LogisticTrainingSplit split,
                                 @Transient UserBiasModel bias,
                                 @Transient RecommenderList recs,
                                 @Transient RatingSummary rs,
                                 @Transient Random rng) {
        dataSplit = split;
        baseline = bias;
        recommenders = recs;
        ratingSummary = rs;
        parameterCount = 1 + recommenders.getRecommenderCount() + 1;
        random = rng;
    }

    @Override
    public LogisticModel get() {
        ProgressLogger progressLogger = ProgressLogger.create(logger);
        progressLogger.setCount(ITERATION_COUNT);
        progressLogger.start();
        List<ItemScorer> scorers = recommenders.getItemScorers();
        double intercept = 0;
        double[] params = new double[parameterCount];

        LogisticModel current = LogisticModel.create(intercept, params);

        // Implement model training
        List<Rating> ratings = dataSplit.getTuneRatings();
        Map<Long, Map<Long,RealVector>> cachedScores = new HashMap<>();
        double[] scores = new double[recommenders.getRecommenderCount()+2];
        for(Rating rating: ratings) {
            double bias = baseline.getIntercept() + baseline.getUserBias(rating.getUserId()) + baseline.getItemBias(rating.getItemId());
            scores[0] = bias;
            scores[1] = Math.log10(ratingSummary.getItemRatingCount(rating.getItemId()));
            int i = 0;
            for(ItemScorer is: scorers) {
                Result score = is.score(rating.getUserId(), rating.getItemId());
                scores[i+2] = score == null ? 0.0 : score.getScore() - bias;
                i++;
            }
            Map<Long,RealVector> userCachedRatings;
            if(cachedScores.containsKey(rating.getUserId())) {
                userCachedRatings = cachedScores.get(rating.getUserId());
            }
            else {
                userCachedRatings = new HashMap<>();
            }
            userCachedRatings.put(rating.getItemId(), new ArrayRealVector(scores));
            cachedScores.put(rating.getUserId(), userCachedRatings);
        }
        RealVector coefficients = current.getCoefficients();
        for(int it=0;it<ITERATION_COUNT;it++) {
            Collections.shuffle(ratings, random);
            for(Rating rating: ratings) {
                RealVector allScores = cachedScores.get(rating.getUserId()).get(rating.getItemId());
                double delta_intercept = LEARNING_RATE*rating.getValue()*current.evaluate(-rating.getValue(), allScores);
                intercept += delta_intercept;
                for(int i=0;i<params.length;i++) {
                    params[i] += delta_intercept * allScores.getEntry(i);
                    coefficients.setEntry(i, params[i]);
                }
                //current = LogisticModel.create(intercept, params);
                current = new LogisticModel(intercept, coefficients);
            }
            progressLogger.advance();
            progressLogger.logProgress();
            System.gc();
        }

        return current;
    }

}
