package com.typeahead.controller;

import com.typeahead.service.SuggestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> suggest(@RequestParam(required = false) String q) {
        if (q == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "q is required"));
        }
        if (q.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(suggestionService.getSuggestions(q));
    }

    @GetMapping("/suggest/trending")
    public ResponseEntity<?> suggestTrending(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(suggestionService.getTrendingSuggestions(q));
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody(required = false) Map<String, String> body) {
        if (body == null || !body.containsKey("query") || body.get("query") == null || body.get("query").isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "query is required"));
        }
        suggestionService.recordSearch(body.get("query"));
        return ResponseEntity.ok(Map.of("message", "Searched"));
    }

    @GetMapping("/cache/debug")
    public Map<String, Object> cacheDebug(@RequestParam String prefix) {
        return suggestionService.getCacheDebug(prefix);
    }

    @GetMapping("/cache/stats")
    public Map<String, Object> cacheStats() {
        return suggestionService.getCacheStats();
    }

    @GetMapping("/trending")
    public ResponseEntity<?> trending(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(suggestionService.getTrending(limit));
    }
}
