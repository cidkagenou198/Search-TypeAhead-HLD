package com.typeahead.controller;

import com.typeahead.service.SuggestionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class SearchController {

    private final SuggestionService suggestionService;

    public SearchController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping("/suggest")
    public Map<String, Object> suggest(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) {
            return Map.of("suggestions", List.of(), "source", "empty");
        }
        return suggestionService.getSuggestions(q);
    }

    @GetMapping("/suggest/trending")
    public Map<String, Object> suggestTrending(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) {
            return Map.of("suggestions", List.of());
        }
        return Map.of("suggestions", suggestionService.getTrendingSuggestions(q));
    }

    @PostMapping("/search")
    public Map<String, String> search(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return Map.of("message", "empty query");
        }
        suggestionService.recordSearch(q);
        return Map.of("message", "Searched");
    }

    @GetMapping("/cache/debug")
    public Map<String, Object> cacheDebug(@RequestParam String prefix) {
        return suggestionService.getCacheDebug(prefix);
    }

    @GetMapping("/trending")
    public Map<String, Object> trending(@RequestParam(defaultValue = "10") int limit) {
        return Map.of("trending", suggestionService.getTrending(limit));
    }
}
