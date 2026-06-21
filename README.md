# HLD101 — Search Typeahead System (Java / Spring Boot)

## Prerequisites
- Java 17+
- Maven
- Docker (for Redis)

## Setup

1. Start 3 Redis nodes:
   docker run -d -p 6379:6379 redis
   docker run -d -p 6380:6380 redis --port 6380
   docker run -d -p 6381:6381 redis --port 6381

2. Load the dataset (run once):
   mvn compile exec:java -Dexec.mainClass="com.typeahead.DatasetLoader"

3. Start the server:
   mvn spring-boot:run

4. Open browser:
   http://localhost:8080

## APIs

GET  /suggest?q=<prefix>            Returns top 10 suggestions (cache-aside)
POST /search?q=<query>              Records search, returns {"message":"Searched"}
GET  /cache/debug?prefix=<prefix>   Shows which Redis node owns prefix and hit/miss
GET  /trending                      Returns trending queries by recency score
GET  /suggest/trending?q=<prefix>   Trending-ranked suggestions

## Design Decisions

Consistent Hashing: MD5 hash of cache key mod 3 — same prefix always maps to same Redis node.
Cache-Aside: Check Redis first, miss goes to SQLite, result written back to Redis (TTL 60s).
Batch Writes: ConcurrentHashMap buffer, flushed every 5s or every 10 searches via @Scheduled.
Trending Score: 0.7 * count + 0.3 * count * e^(-age/3600) — exponential decay prevents
short-lived viral queries from permanently dominating.
