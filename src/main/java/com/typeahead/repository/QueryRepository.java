package com.typeahead.repository;

import com.typeahead.model.QueryResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class QueryRepository {

    private final JdbcTemplate jdbc;

    public QueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<QueryResult> getSuggestions(String prefix, int limit) {
        String sql = "SELECT query, count, last_searched FROM queries " +
                     "WHERE query LIKE ? ORDER BY count DESC LIMIT ?";
        return jdbc.query(sql,
            (rs, rowNum) -> new QueryResult(
                rs.getString("query"),
                rs.getLong("count"),
                rs.getDouble("last_searched")
            ),
            prefix.toLowerCase() + "%", limit
        );
    }

    public List<QueryResult> getSuggestionsWithRecency(String prefix) {
        String sql = "SELECT query, count, last_searched FROM queries " +
                     "WHERE query LIKE ? LIMIT 50";
        return jdbc.query(sql,
            (rs, rowNum) -> new QueryResult(
                rs.getString("query"),
                rs.getLong("count"),
                rs.getDouble("last_searched")
            ),
            prefix.toLowerCase() + "%"
        );
    }

    public void upsertQuery(String query) {
        double now = System.currentTimeMillis() / 1000.0;
        String sql = "INSERT INTO queries (query, count, last_searched) VALUES (?, 1, ?) " +
                     "ON CONFLICT(query) DO UPDATE SET count = count + 1, last_searched = ?";
        jdbc.update(sql, query.toLowerCase(), now, now);
    }

    public void bulkUpsert(Map<String, Integer> queryCounts) {
        double now = System.currentTimeMillis() / 1000.0;
        String sql = "INSERT INTO queries (query, count, last_searched) VALUES (?, ?, ?) " +
                     "ON CONFLICT(query) DO UPDATE SET count = count + ?, last_searched = ?";
        for (Map.Entry<String, Integer> entry : queryCounts.entrySet()) {
            jdbc.update(sql,
                entry.getKey().toLowerCase(),
                entry.getValue(),
                now,
                entry.getValue(),
                now
            );
        }
    }

    public List<QueryResult> getRecentQueries(int limit) {
        String sql = "SELECT query, count, last_searched FROM queries " +
                     "ORDER BY last_searched DESC, count DESC LIMIT ?";
        return jdbc.query(sql,
            (rs, rowNum) -> new QueryResult(
                rs.getString("query"),
                rs.getLong("count"),
                rs.getDouble("last_searched")
            ),
            limit
        );
    }
}
