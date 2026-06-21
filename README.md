# Search Typeahead System — API Documentation

**Base URL:** `http://localhost:8080`
*(Use `http://localhost:8000` if running the Python/FastAPI version instead.)*

All responses are JSON. All endpoints are unauthenticated (local/demo use only).

---

## Endpoint Summary

| Method | Endpoint                          | Purpose                                                        |
|--------|------------------------------------|------------------------------------------------------------------|
| GET    | `/suggest?q={prefix}`             | Fetch up to 10 prefix-matching suggestions                       |
| POST   | `/search`                          | Submit a search; increments/creates the query's count            |
| GET    | `/cache/debug?prefix={prefix}`    | Debug endpoint — shows cache routing + hit/miss for a prefix      |
| GET    | `/trending`                        | (Optional) Returns top trending queries, no prefix needed         |

---

## 1. GET `/suggest`

Returns up to 10 suggestions matching a typed prefix, sorted by count (basic version) or by a recency-weighted trending score (enhanced version).

**Query Parameters**

| Name | Type   | Required | Description                                   |
|------|--------|----------|------------------------------------------------|
| `q`  | string | Yes      | The prefix typed by the user. Case-insensitive. |

**Request Example**
```
GET /suggest?q=iph
```

**Response — 200 OK**
```json
[
  { "query": "iphone",         "count": 100000 },
  { "query": "iphone 15",      "count": 85000  },
  { "query": "iphone charger", "count": 60000  }
]
```

**Edge Cases**

| Case                       | Behavior                                  |
|-----------------------------|--------------------------------------------|
| `q` missing                | `400 Bad Request` — `{ "error": "q is required" }` |
| `q` empty string            | `200 OK` — returns `[]`                    |
| No prefix matches found     | `200 OK` — returns `[]`                    |
| Mixed-case input             | Normalized to lowercase before matching    |

**Behavior Notes**
- Checks the cache first (routed via consistent hashing on the prefix). On a cache miss, falls back to the primary data store and repopulates the cache.
- Result list is capped at 10 items, sorted descending by count (or trending score, if recency-aware ranking is enabled).
- Should respond with low (p95) latency — measure and report this in your performance write-up.

---

## 2. POST `/search`

Submits a search query. Returns a dummy response and records the query for count updates (via batching, not a synchronous DB write).

**Request Body**
```json
{ "query": "iphone 15" }
```

| Field   | Type   | Required | Description            |
|---------|--------|----------|--------------------------|
| `query` | string | Yes      | The exact search term submitted |

**Response — 200 OK**
```json
{ "message": "Searched" }
```

**Edge Cases**

| Case                  | Behavior                                       |
|-------------------------|---------------------------------------------|
| `query` missing/empty   | `400 Bad Request` — `{ "error": "query is required" }` |
| New query (not seen before) | Inserted with an initial count of 1     |
| Existing query           | Its count is incremented                    |

**Behavior Notes**
- The increment is **not written to the DB immediately** — it's added to an in-memory batch buffer/queue.
- The batch writer flushes to the database periodically (e.g., every 5 seconds) or after a configurable number of searches, whichever comes first.
- The update is reflected in `/suggest` and `/trending` only after the next flush (or cache invalidation) — mention this eventual-consistency behavior in your write-up.

---

## 3. GET `/cache/debug`

Debug-only endpoint for demonstrating consistent hashing during your viva. Shows which logical cache node a given prefix maps to, and whether the last lookup was a hit or miss.

**Query Parameters**

| Name     | Type   | Required | Description                  |
|-----------|--------|----------|--------------------------------|
| `prefix`  | string | Yes      | The prefix to check routing for |

**Request Example**
```
GET /cache/debug?prefix=iph
```

**Response — 200 OK**
```json
{
  "prefix": "iph",
  "assignedNode": "cache-node-2",
  "status": "HIT"
}
```

`status` is one of: `"HIT"`, `"MISS"`.

---

## 4. GET `/trending` (Optional)

Not in the core rubric API list, but useful for satisfying the UI requirement of a dedicated **"Trending searches"** section without needing a prefix. Returns the top N queries ranked by recency-weighted score.

**Query Parameters**

| Name    | Type | Required | Default | Description                  |
|----------|------|----------|---------|--------------------------------|
| `limit`  | int  | No       | 10      | Number of trending queries to return |

**Response — 200 OK**
```json
[
  { "query": "world cup 2026", "count": 4200, "trendingScore": 3891.2 },
  { "query": "iphone 17",      "count": 9000, "trendingScore": 3104.7 }
]
```

---

## Standard Error Format

All error responses follow this shape:

```json
{ "error": "human-readable message" }
```

| Status Code | Meaning                          |
|--------------|------------------------------------|
| 200          | Success                            |
| 400          | Bad request (missing/invalid input) |
| 500          | Internal server error               |

---

## Quick Test (cURL)

```bash
# Get suggestions
curl "http://localhost:8080/suggest?q=iph"

# Submit a search
curl -X POST http://localhost:8080/search \
  -H "Content-Type: application/json" \
  -d '{"query": "iphone 15"}'

# Check cache routing
curl "http://localhost:8080/cache/debug?prefix=iph"

# Trending (optional)
curl "http://localhost:8080/trending?limit=5"
```
