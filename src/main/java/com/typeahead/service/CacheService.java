package com.typeahead.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typeahead.model.QueryResult;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CacheService {

    @Value("${redis.nodes}")
    private String redisNodesConfig;

    @Value("${cache.ttl}")
    private int cacheTtl;

    private List<JedisPool> redisPools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    @PostConstruct
    public void init() {
        redisPools = new ArrayList<>();
        for (String node : redisNodesConfig.split(",")) {
            String[] parts = node.trim().split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            redisPools.add(new JedisPool(host, port));
        }
        System.out.println("[CACHE] Initialized " + redisPools.size() + " Redis nodes.");
    }

    private int getNodeIndex(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(key.getBytes());
            BigInteger bigInt = new BigInteger(1, hash);
            return bigInt.mod(BigInteger.valueOf(redisPools.size())).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private JedisPool getPool(String key) {
        return redisPools.get(getNodeIndex(key));
    }

    public List<QueryResult> get(String prefix) {
        String cacheKey = "suggest:" + prefix.toLowerCase();
        try (Jedis jedis = getPool(cacheKey).getResource()) {
            String val = jedis.get(cacheKey);
            if (val != null) {
                hitCount.incrementAndGet();
                System.out.println("[CACHE HIT] prefix='" + prefix + "' node=" + getNodeIndex(cacheKey));
                return objectMapper.readValue(val, new TypeReference<List<QueryResult>>() {});
            }
        } catch (Exception e) {
            System.err.println("[CACHE] Read error: " + e.getMessage());
        }
        missCount.incrementAndGet();
        System.out.println("[CACHE MISS] prefix='" + prefix + "' node=" + getNodeIndex(cacheKey));
        return null;
    }

    public Map<String, Object> getStats() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        double hitRate = total == 0 ? 0.0 : (double) hits / total * 100.0;
        return Map.of(
            "hits", hits,
            "misses", misses,
            "total", total,
            "hitRatePercent", Math.round(hitRate * 100.0) / 100.0
        );
    }

    public void set(String prefix, List<QueryResult> suggestions) {
        String cacheKey = "suggest:" + prefix.toLowerCase();
        try (Jedis jedis = getPool(cacheKey).getResource()) {
            String json = objectMapper.writeValueAsString(suggestions);
            jedis.setex(cacheKey, cacheTtl, json);
        } catch (Exception e) {
            System.err.println("[CACHE] Write error: " + e.getMessage());
        }
    }

    public void invalidate(String query) {
        for (int i = 1; i <= query.length(); i++) {
            String prefix = query.substring(0, i);
            String cacheKey = "suggest:" + prefix.toLowerCase();
            try (Jedis jedis = getPool(cacheKey).getResource()) {
                jedis.del(cacheKey);
            } catch (Exception e) {
            }
        }
    }

    public Map<String, Object> getDebugInfo(String prefix) {
        String cacheKey = "suggest:" + prefix.toLowerCase();
        int nodeIndex = getNodeIndex(cacheKey);
        boolean hit = false;
        try (Jedis jedis = getPool(cacheKey).getResource()) {
            hit = jedis.exists(cacheKey);
        } catch (Exception ignored) {}

        return Map.of(
            "prefix", prefix,
            "assignedNode", "cache-node-" + nodeIndex,
            "status", hit ? "HIT" : "MISS"
        );
    }
}
