package com.typeahead.service;

import com.typeahead.model.QueryResult;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class TrendingService {

    private static final double RECENCY_WINDOW = 3600.0;
    private static final double RECENCY_WEIGHT = 0.3;
    private static final double HISTORY_WEIGHT = 0.7;

    public double computeScore(long count, double lastSearched) {
        double now = System.currentTimeMillis() / 1000.0;
        double ageSeconds = (lastSearched > 0) ? (now - lastSearched) : RECENCY_WINDOW;
        double recencyBoost = Math.exp(-ageSeconds / RECENCY_WINDOW);
        return (HISTORY_WEIGHT * count) + (RECENCY_WEIGHT * count * recencyBoost);
    }

    public List<QueryResult> sortByTrending(List<QueryResult> results, int limit) {
        results.forEach(r -> r.setScore(computeScore(r.getCount(), r.getLastSearched())));
        return results.stream()
            .sorted(Comparator.comparingDouble(QueryResult::getScore).reversed())
            .limit(limit)
            .toList();
    }
}
