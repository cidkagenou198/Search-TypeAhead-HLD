package com.typeahead.service;

import com.typeahead.repository.QueryRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BatchWriterService {

    private final QueryRepository queryRepository;

    @Value("${batch.size}")
    private int batchSize;

    private final ConcurrentHashMap<String, AtomicInteger> buffer = new ConcurrentHashMap<>();

    public BatchWriterService(QueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    public void add(String query) {
        buffer.computeIfAbsent(query.toLowerCase(), k -> new AtomicInteger(0))
              .incrementAndGet();

        int total = buffer.values().stream().mapToInt(AtomicInteger::get).sum();
        if (total >= batchSize) {
            flush();
        }
    }

    @Scheduled(fixedDelayString = "${batch.flush.interval.seconds}000")
    public synchronized void flush() {
        if (buffer.isEmpty()) return;

        Map<String, Integer> snapshot = new HashMap<>();
        buffer.forEach((k, v) -> snapshot.put(k, v.get()));
        buffer.clear();

        System.out.println("[BATCH] Flushing " + snapshot.size() + " unique queries to DB...");
        queryRepository.bulkUpsert(snapshot);
        System.out.println("[BATCH] Flush complete.");
    }

    @PreDestroy
    public void onShutdown() {
        System.out.println("[BATCH] Shutdown flush...");
        flush();
    }
}
