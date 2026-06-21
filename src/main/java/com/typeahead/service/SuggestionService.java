package com.typeahead.service;

import com.typeahead.model.QueryResult;
import com.typeahead.repository.QueryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SuggestionService {

    private final QueryRepository queryRepository;
    private final CacheService cacheService;
    private final TrendingService trendingService;
    private final BatchWriterService batchWriterService;

    public SuggestionService(
        QueryRepository queryRepository,
        CacheService cacheService,
        TrendingService trendingService,
        BatchWriterService batchWriterService
    ) {
        this.queryRepository = queryRepository;
        this.cacheService = cacheService;
        this.trendingService = trendingService;
        this.batchWriterService = batchWriterService;
    }

    public List<QueryResult> getSuggestions(String prefix) {
        prefix = prefix.trim().toLowerCase();
        if (prefix.length() < 3) return List.of();

        List<QueryResult> cached = cacheService.get(prefix);
        if (cached != null) {
            return cached;
        }

        List<QueryResult> results = queryRepository.getSuggestions(prefix, 10);
        cacheService.set(prefix, results);
        return results;
    }

    public List<QueryResult> getTrendingSuggestions(String prefix) {
        prefix = prefix.trim().toLowerCase();
        if (prefix.length() < 3) return List.of();
        List<QueryResult> results = queryRepository.getSuggestionsWithRecency(prefix);
        return trendingService.sortByTrending(results, 10);
    }

    public void recordSearch(String query) {
        query = query.trim().toLowerCase();
        batchWriterService.add(query);
        cacheService.invalidate(query);
    }

    public List<QueryResult> getTrending(int limit) {
        List<QueryResult> recent = queryRepository.getRecentQueries(50);
        return trendingService.sortByTrending(recent, limit);
    }

    public Map<String, Object> getCacheDebug(String prefix) {
        return cacheService.getDebugInfo(prefix);
    }

    public Map<String, Object> getCacheStats() {
        return cacheService.getStats();
    }
}
