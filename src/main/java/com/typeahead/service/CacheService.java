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

@Service
public class CacheService {

    @Value("${redis.nodes}")
    private String redisNodesConfig;

    @Value("${cache.ttl}")
    private int cacheTtl;

    private List<JedisPool> redisPools;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                System.out.println("[CACHE HIT] prefix='" + prefix + "' node=" + getNodeIndex(cacheKey));
                return objectMapper.readValue(val, new TypeReference<List<QueryResult>>() {});
            }
        } catch (Exception e) {
            System.err.println("[CACHE] Read error: " + e.getMessage());
        }
        System.out.println("[CACHE MISS] prefix='" + prefix + "' node=" + getNodeIndex(cacheKey));
        return null;
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
            "cacheKey", cacheKey,
            "nodeIndex", nodeIndex,
            "hit", hit
        );
    }
}
