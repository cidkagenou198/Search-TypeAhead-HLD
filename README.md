# Search Typeahead System — API Documentation





https://github.com/user-attachments/assets/0f2967a8-29d7-4dbf-9ecf-43f2c7619a57





<img width="1909" height="1033" alt="image" src="https://github.com/user-attachments/assets/380211e1-f34a-4630-a5cd-e4c1fdea678d" />
<img width="690" height="665" alt="image" src="https://github.com/user-attachments/assets/19ae006a-6625-4489-832d-0876f18d10c8" />
<img width="841" height="349" alt="image" src="https://github.com/user-attachments/assets/0d6eb50a-0c5c-477a-85df-b39700d19eaf" />
<img width="806" height="198" alt="image" src="https://github.com/user-attachments/assets/e1c49056-d9ee-4356-83df-0c178a53db41" />
<img width="709" height="187" alt="image" src="https://github.com/user-attachments/assets/71fb6a9e-4310-409d-bff8-b00af0275ffb" />


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
| GET    | `/cache/stats`                     | Returns cache hit/miss counts and hit rate for performance report |
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

# Check cache stats (hit rate for performance report)
curl "http://localhost:8080/cache/stats"
```

---

# Redis Implementation Guide — Search Typeahead System

This is the continuation to your main project doc. Your `CacheService.java` already
contains the Redis/Jedis wiring (consistent hashing, cache-aside, TTL, invalidation).
This guide covers the part that's still missing: **getting Redis actually running,
verifying it works correctly, and proving the consistent-hashing + hit-rate behavior
for your viva and performance report.**

---

## 1. What "Implementing Redis" Means Here

You need 3 things working together, and each is independently verifiable:

| Piece                         | Where it lives                          |
|---------------------------------|---------------------------------------|
| 3 running Redis server processes | Docker containers (or native installs) |
| Consistent hashing routing       | `CacheService.getNodeIndex()`         |
| Cache-aside read/write logic     | `CacheService.get()` / `.set()`       |

The code is already done. This guide gets the servers running and shows you how to
prove each piece works.

---

## 2. Installing & Running 3 Redis Nodes

### Option A — Docker (recommended)

```bash
# Pull the image once
docker pull redis

# Node 1 — port 6379
docker run -d --name redis-node1 -p 6379:6379 redis

# Node 2 — port 6380
docker run -d --name redis-node2 -p 6380:6380 redis redis-server --port 6380

# Node 3 — port 6381
docker run -d --name redis-node3 -p 6381:6381 redis redis-server --port 6381
```

> Note the difference from a single-node setup: nodes 2 and 3 need
> `redis-server --port 6380` (etc.) passed as the container command, otherwise
> Redis listens on its default port 6379 *inside* the container regardless of
> the host port mapping, and Jedis will fail to connect.

**Verify all 3 are running:**
```bash
docker ps
```
You should see `redis-node1`, `redis-node2`, `redis-node3` all with status `Up`.

**Verify each one actually responds:**
```bash
redis-cli -p 6379 ping   # → PONG
redis-cli -p 6380 ping   # → PONG
redis-cli -p 6381 ping   # → PONG
```
If any of these hangs or errors with "Connection refused," that node isn't up —
check `docker logs redis-node2` (etc.) for the reason.

### Option B — Native install (no Docker)

```bash
# Ubuntu/Debian
sudo apt install redis-server

# macOS
brew install redis
```

Start 3 separate instances on different ports using config overrides:
```bash
redis-server --port 6379 --daemonize yes
redis-server --port 6380 --daemonize yes
redis-server --port 6381 --daemonize yes
```

Verify the same way: `redis-cli -p 6379 ping`, etc.

### Stopping / cleaning up later
```bash
docker stop redis-node1 redis-node2 redis-node3
docker rm redis-node1 redis-node2 redis-node3
```

---

## 3. Confirming Spring Boot Connects to All 3 Nodes

Your `application.properties` already has:
```properties
redis.nodes=localhost:6379,localhost:6380,localhost:6381
```

When you run `mvn spring-boot:run`, you should see this in the console (from
`CacheService.init()`):
```
[CACHE] Initialized 3 Redis nodes.
```

If it throws a connection exception at startup, it means the Redis containers
weren't running *before* you started the Spring Boot app — start Redis first,
always.

---

## 4. Proving Consistent Hashing Works

This is the part you'll most likely be asked to demonstrate live in your viva.

**Step 1 — Hit the debug endpoint repeatedly with the same prefix:**
```bash
curl "http://localhost:8080/cache/debug?prefix=iph"
curl "http://localhost:8080/cache/debug?prefix=iph"
curl "http://localhost:8080/cache/debug?prefix=iph"
```
Every response should show the **same `nodeIndex`**, e.g.:
```json
{ "prefix": "iph", "assignedNode": "cache-node-0", "status": "MISS" }
```

**Step 2 — Try a few different prefixes and show they land on different nodes:**
```bash
curl "http://localhost:8080/cache/debug?prefix=a"
curl "http://localhost:8080/cache/debug?prefix=java"
curl "http://localhost:8080/cache/debug?prefix=world"
```
You should see a mix of `cache-node-0`, `cache-node-1`, `cache-node-2` — proving the keys are
**distributed**, not all piling onto one node.

**Step 3 — Confirm directly inside Redis which node actually holds the key:**
```bash
redis-cli -p 6379 KEYS "suggest:*"
redis-cli -p 6380 KEYS "suggest:*"
redis-cli -p 6381 KEYS "suggest:*"
```
The key `suggest:iph` should only appear on the port matching the `assignedNode`
your debug endpoint reported (cache-node-0 → 6379, cache-node-1 → 6380, cache-node-2 → 6381).

---

## 5. Proving Cache-Aside Behavior (Hit vs Miss)

```bash
# First call — cache is empty, this is a MISS, data comes from SQLite
curl "http://localhost:8080/suggest?q=iph"
# → returns suggestions (sourced from DB)

# Second call — same prefix, now served from Redis (HIT)
curl "http://localhost:8080/suggest?q=iph"
# → returns same suggestions (sourced from cache now)
```

**Check the TTL directly in Redis:**
```bash
redis-cli -p 6380 TTL suggest:iph
```
This returns the number of seconds remaining before the key expires
(your config sets this to 60s via `cache.ttl`).

**Confirm invalidation on search submission:**
```bash
curl -X POST http://localhost:8080/search \
  -H "Content-Type: application/json" \
  -d '{"query": "iphone"}'

redis-cli -p 6380 EXISTS suggest:i
redis-cli -p 6380 EXISTS suggest:ip
redis-cli -p 6380 EXISTS suggest:iph
```
These keys should now return `0` (deleted) — your `cacheService.invalidate()`
call clears every prefix of the submitted query so stale suggestions aren't served.

---

## 6. Hit-Rate Tracking (for your Performance Report)

The assignment may ask you to **report cache hit rate**. A dedicated endpoint is
already wired up:

```bash
curl "http://localhost:8080/cache/stats"
```
```json
{ "hits": 31, "misses": 19, "total": 50, "hitRatePercent": 62.0 }
```

**To generate a hit-rate number for your report:** hit `/suggest` with a mix of
repeated and new prefixes (e.g., 50 requests, half repeats), then call `/cache/stats`.

The counters live in `CacheService.java`:
- `hitCount` / `missCount` are `AtomicLong` fields, incremented on every `get()` call
- `getStats()` returns the current snapshot including `hitRatePercent`

---

## 7. Troubleshooting

| Symptom                                         | Cause                                      | Fix                                              |
|--------------------------------------------------|-----------------------------------------------|---------------------------------------------------|
| `Connection refused` at Spring Boot startup       | Redis containers not running yet              | Start all 3 Redis containers, then start the app   |
| All keys land on `cache-node-0`                   | `redis.nodes` only has 1 entry, or split error | Check `application.properties` has all 3 comma-separated hosts |
| Node 2/3 won't accept connections                 | Forgot `redis-server --port 638X` in `docker run` | Re-create the container with the port flag, see Step 2 |
| `/suggest` always returns from DB                 | TTL too short, or invalidation firing on every read | Confirm `cache.ttl=60` and that invalidation only runs in `recordSearch()`, not `getSuggestions()` |
| Data "disappears" after restarting Docker          | Expected — Redis here is in-memory only, not persisted | Not a bug; mention this as a stated trade-off in your write-up |

---

## 8.Demo Script (Redis-specific)

A clean order to demonstrate this part live:

1. `docker ps` — show 3 Redis containers running.
2. `curl /suggest?q=iph` twice — show first call from DB, second from cache.
3. `curl /cache/debug?prefix=iph` a few times — show the **same node** every time.
4. `curl /cache/debug?prefix=java` — show a **different node** than step 3.
5. `redis-cli -p <port> KEYS "suggest:*"` — show the key physically sitting on the
   node your debug endpoint predicted.
6. `curl -X POST /search` with `{"query":"iphone"}` then re-check `EXISTS suggest:iph` — show it
   was invalidated.
7. `curl /cache/stats` — show the hit rate number for your performance report.

That sequence alone proves: distribution, consistent hashing, cache-aside,
expiry/invalidation, and gives you a real metric — everything the rubric asks for
in sections 6 and 10 of the assignment.
