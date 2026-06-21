package com.typeahead.model;

public class QueryResult {

    private String query;
    private long count;
    private double lastSearched;
    private double score;

    public QueryResult() {}

    public QueryResult(String query, long count, double lastSearched) {
        this.query = query;
        this.count = count;
        this.lastSearched = lastSearched;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }

    public double getLastSearched() { return lastSearched; }
    public void setLastSearched(double lastSearched) { this.lastSearched = lastSearched; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
