package org.lenskit.mooc.ii;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.lenskit.api.Result;
import org.lenskit.api.ResultMap;
import org.lenskit.basic.AbstractItemScorer;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.ratings.Rating;
import org.lenskit.results.Results;
import org.lenskit.util.ScoredIdAccumulator;
import org.lenskit.util.TopNScoredIdAccumulator;
import org.lenskit.util.math.Vectors;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleItemItemScorer extends AbstractItemScorer {
    private final SimpleItemItemModel model;
    private final DataAccessObject dao;
    private final int neighborhoodSize;

    @Inject
    public SimpleItemItemScorer(SimpleItemItemModel m, DataAccessObject dao) {
        model = m;
        this.dao = dao;
        neighborhoodSize = 20;
    }

    /**
     * Score items for a user.
     * @param user The user ID.
     * @param items The score vector.  Its key domain is the items to score, and the scores
     *               (rating predictions) should be written back to this vector.
     */
    @Override
    public ResultMap scoreWithDetails(long user, @Nonnull Collection<Long> items) {
        Long2DoubleMap itemMeans = model.getItemMeans();
        Long2DoubleMap ratings = getUserRatingVector(user);

        // Normalize the user's ratings by subtracting the item mean from each one.
        for (Map.Entry<Long, Double> entry : ratings.entrySet()) {
            entry.setValue(entry.getValue() - itemMeans.get(entry.getKey()));
        }

        List<Result> results = new ArrayList<>();

        for (long item: items) {
            // Compute the user's score for each item, add it to results
            Long2DoubleMap itemNeighbors = model.getNeighbors(item);

            // Calculate top neighbors
            ValueComparator bvc = new ValueComparator(itemNeighbors);
            TreeMap<Long,Double> sortedSimilarities = new TreeMap<>(bvc);
            sortedSimilarities.putAll(itemNeighbors);
            int count = 1;
            double weightSum = 0.0;
            double weightRatingSum = 0.0;
            for (Map.Entry<Long, Double> entry : sortedSimilarities.entrySet()) {
                if (count > neighborhoodSize)
                    break;
                Long otherItem = entry.getKey();
                if(ratings.containsKey(otherItem)) {
                    double weight = entry.getValue();
                    double rating = ratings.get(otherItem);
                    weightSum += weight;
                    weightRatingSum += weight * rating;
                    count ++;
                }
            }
            double score = itemMeans.get(item) + weightRatingSum / weightSum;
            results.add(Results.create(item, score));
        }

        return Results.newResultMap(results);

    }

    /**
     * Get a user's ratings.
     * @param user The user ID.
     * @return The ratings to retrieve.
     */
    private Long2DoubleOpenHashMap getUserRatingVector(long user) {
        List<Rating> history = dao.query(Rating.class)
                                  .withAttribute(CommonAttributes.USER_ID, user)
                                  .get();

        Long2DoubleOpenHashMap ratings = new Long2DoubleOpenHashMap();
        for (Rating r: history) {
            ratings.put(r.getItemId(), r.getValue());
        }

        return ratings;
    }


    class ValueComparator implements Comparator<Long> {

        Map<Long, Double> base;
        public ValueComparator(Map<Long, Double> base) {
            this.base = base;
        }

        // Note: this comparator imposes orderings that are inconsistent with equals.
        public int compare(Long a, Long b) {
            if (base.get(a) >= base.get(b)) {
                return -1;
            } else {
                return 1;
            } // returning 0 would merge keys
        }
    }
}
