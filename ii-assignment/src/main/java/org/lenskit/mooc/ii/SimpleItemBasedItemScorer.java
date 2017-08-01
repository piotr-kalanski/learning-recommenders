package org.lenskit.mooc.ii;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import org.lenskit.api.Result;
import org.lenskit.api.ResultMap;
import org.lenskit.basic.AbstractItemBasedItemScorer;
import org.lenskit.results.Results;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Global item scorer to find similar items.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */

public class SimpleItemBasedItemScorer extends AbstractItemBasedItemScorer {
    private final SimpleItemItemModel model;

    @Inject
    public SimpleItemBasedItemScorer(SimpleItemItemModel mod) {
        model = mod;
    }

    /**
     * Score items with respect to a set of reference items.
     * @param basket The reference items.
     * @param items The score vector. Its domain is the items to be scored, and the scores should
     *               be stored into this vector.
     */
    @Override
    public ResultMap scoreRelatedItemsWithDetails(@Nonnull Collection<Long> basket, Collection<Long> items) {
        List<Result> results = new ArrayList<>();

        // Score the items and put them in results
        for (long item: items) {
            Long2DoubleMap itemNeighbors = model.getNeighbors(item);
            double score = 0.0;
            for(long otherItem: basket) {
                if(itemNeighbors.containsKey(otherItem)) {
                    score += itemNeighbors.get(otherItem);
                }
            }
            results.add(Results.create(item, score));
        }

        return Results.newResultMap(results);
    }
}
