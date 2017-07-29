package org.lenskit.mooc.uu;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleSortedMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.lenskit.api.Result;
import org.lenskit.api.ResultMap;
import org.lenskit.basic.AbstractItemScorer;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.entities.CommonTypes;
import org.lenskit.data.ratings.Rating;
import org.lenskit.results.Results;
import org.lenskit.util.ScoredIdAccumulator;
import org.lenskit.util.TopNScoredIdAccumulator;
import org.lenskit.util.collections.LongUtils;
import org.lenskit.util.math.Scalars;
import org.lenskit.util.math.Vectors;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

/**
 * User-user item scorer.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {
    private final DataAccessObject dao;
    private final int neighborhoodSize;

    /**
     * Instantiate a new user-user item scorer.
     * @param dao The data access object.
     */
    @Inject
    public SimpleUserUserItemScorer(DataAccessObject dao) {
        this.dao = dao;
        neighborhoodSize = 30;
    }

    @Nonnull
    @Override
    public ResultMap scoreWithDetails(long user, @Nonnull Collection<Long> items) {
        // Score the items for the user with user-user CF
        LongSet users = getUsers();
        Map<Long, Long2DoubleOpenHashMap> normalizedUsersRatings = new HashMap<>();
        Map<Long, Double> meanUsersRatings = new HashMap<>();
        Map<Long, Double> similarities = new HashMap<>();

        // Calculate mean user ratings and normalize ratings
        for(long u: users) {
            Long2DoubleOpenHashMap userRatings = getUserRatingVector(u);
            double mean = calculateMeanRating(userRatings);
            meanUsersRatings.put(u, mean);
            Long2DoubleOpenHashMap normalizedUserRatings = normalizeUserRatings(userRatings, mean);
            normalizedUsersRatings.put(u, normalizedUserRatings);
        }
        // Calculate similarities
        Long2DoubleOpenHashMap selectedUserRatings = normalizedUsersRatings.get(user);
        for(long u: users) {
            if(u != user)
                similarities.put(u, calculateCosineSimilarity(selectedUserRatings, normalizedUsersRatings.get(u)));
        }

        // Calculate neighbors
        ValueComparator bvc = new ValueComparator(similarities);
        TreeMap<Long,Double> sorted_map = new TreeMap<>(bvc);
        sorted_map.putAll(similarities);

        // Create a place to store the results of our score computations
        List<Result> results = new ArrayList<>();
        double selectedUserMeanRating = meanUsersRatings.get(user);
        // Calculate scores for all items
        for(long item : items) {
            // Calculate score
            int count = 1;
            double weightSum = 0.0;
            double weightRatingSum = 0.0;
            for (Map.Entry<Long, Double> entry : sorted_map.entrySet()) {
                if (count > neighborhoodSize)
                    break;
                long otherUser = entry.getKey();
                Long2DoubleOpenHashMap otherUserNormalizedRatings = normalizedUsersRatings.get(otherUser);
                if(otherUserNormalizedRatings.containsKey(item)) {
                    double weight = similarities.get(otherUser);
                    double rating = otherUserNormalizedRatings.get(item);
                    // whose similarity to the target user is positive
                    if(weight > 0) {
                        weightSum += weight;
                        weightRatingSum += weight * rating;
                        count++; // take only users who rated item
                    }
                }
            }

            // Refuse to score items if there are not at least 2 neighbors to contribute to the item’s score.
            if(count > 1) {
                double score = selectedUserMeanRating + weightRatingSum / weightSum;
                results.add(Results.create(item, score));
            }
        }

        return Results.newResultMap(results);
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

    private LongSet getUsers() {
        return dao.getEntityIds(CommonTypes.USER);
    }

    private Double calculateMeanRating(Long2DoubleOpenHashMap userRatings) {
        double sum = 0.0;
        int count = 0;
        for(double r: userRatings.values()) {
            sum += r;
            count++;
        }
        return sum / count;
    }

    private Long2DoubleOpenHashMap normalizeUserRatings(Long2DoubleOpenHashMap userRatings, Double meanRating) {
        Long2DoubleOpenHashMap result = new Long2DoubleOpenHashMap();
        for(Map.Entry<Long, Double> r: userRatings.entrySet()) {
            result.put((long)r.getKey(), r.getValue() - meanRating);
        }
        return result;
    }

    private Double calculateCosineSimilarity(Long2DoubleOpenHashMap u1Ratings, Long2DoubleOpenHashMap u2Ratings) {
        double u1u2 = 0.0;
        double u1u1 = 0.0;
        double u2u2 = 0.0;

        HashSet<Long> commonItems = new HashSet<>();
        commonItems.addAll(u1Ratings.keySet());
        commonItems.addAll(u2Ratings.keySet());

        for(long item: commonItems) {
            double u1 = 0.0;
            double u2 = 0.0;
            if(u1Ratings.containsKey(item)) {
                u1 = u1Ratings.get(item);
            }
            if(u2Ratings.containsKey(item)) {
                u2 = u2Ratings.get(item);
            }
            u1u2 += u1*u2;
            u1u1 += u1*u1;
            u2u2 += u2*u2;
        }

        return u1u2 / Math.sqrt(u1u1) / Math.sqrt(u2u2);
    }

    /**
     * Get a user's rating vector.
     * @param user The user ID.
     * @return The rating vector, mapping item IDs to the user's rating
     *         for that item.
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

}
