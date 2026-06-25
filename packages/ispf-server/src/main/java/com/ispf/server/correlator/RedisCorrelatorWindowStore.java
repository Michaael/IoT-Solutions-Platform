package com.ispf.server.correlator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis sorted-set sliding windows for correlator hits (shared across replicas).
 */
@Component
@ConditionalOnProperty(prefix = "ispf.redis", name = {"enabled", "correlator-windows-enabled"}, havingValue = "true")
public class RedisCorrelatorWindowStore implements CorrelatorWindowStore {

    private static final String KEY_PREFIX = "ispf:corr:hits:";
    private static final long RETENTION_MS = 3_600_000L;

    private final StringRedisTemplate redis;
    private final AtomicLong sequence = new AtomicLong();

    public RedisCorrelatorWindowStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void recordHit(String correlatorId, String objectPath, String eventName, Instant occurredAt) {
        String key = hitsKey(correlatorId, objectPath);
        long score = occurredAt.toEpochMilli();
        String member = encodeMember(eventName, sequence.incrementAndGet());
        redis.opsForZSet().add(key, member, score);
        trimKey(key, Instant.now().minusMillis(RETENTION_MS));
    }

    @Override
    public long countHitsSince(String correlatorId, String objectPath, Instant since) {
        Long count = redis.opsForZSet().count(hitsKey(correlatorId, objectPath), since.toEpochMilli(), Double.MAX_VALUE);
        return count != null ? count : 0L;
    }

    @Override
    public Optional<CorrelatorHit> findFirstHitSince(
            String correlatorId,
            String objectPath,
            String eventName,
            Instant since
    ) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().rangeByScoreWithScores(
                hitsKey(correlatorId, objectPath),
                since.toEpochMilli(),
                Double.MAX_VALUE,
                0,
                256
        );
        if (tuples == null || tuples.isEmpty()) {
            return Optional.empty();
        }
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            CorrelatorHit hit = decodeHit(correlatorId, objectPath, tuple);
            if (eventName.equals(hit.eventName())) {
                return Optional.of(hit);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<CorrelatorHit> listHitsSince(String correlatorId, String objectPath, Instant since) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().rangeByScoreWithScores(
                hitsKey(correlatorId, objectPath),
                since.toEpochMilli(),
                Double.MAX_VALUE
        );
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<CorrelatorHit> hits = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            hits.add(decodeHit(correlatorId, objectPath, tuple));
        }
        return hits;
    }

    @Override
    public void clearCorrelator(String correlatorId) {
        deleteKeys(KEY_PREFIX + correlatorId + ":*");
    }

    @Override
    public void purgeOlderThan(Instant cutoff) {
        for (String key : scanKeys(KEY_PREFIX + "*")) {
            trimKey(key, cutoff);
        }
    }

    @Override
    public void remapCorrelatorId(String oldId, String newId) {
        for (String oldKey : scanKeys(KEY_PREFIX + oldId + ":*")) {
            String suffix = oldKey.substring((KEY_PREFIX + oldId).length());
            String newKey = KEY_PREFIX + newId + suffix;
            Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().rangeWithScores(oldKey, 0, -1);
            if (tuples != null && !tuples.isEmpty()) {
                for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                    if (tuple.getValue() != null && tuple.getScore() != null) {
                        redis.opsForZSet().add(newKey, tuple.getValue(), tuple.getScore());
                    }
                }
            }
            redis.delete(oldKey);
        }
    }

    static String hitsKey(String correlatorId, String objectPath) {
        return KEY_PREFIX + correlatorId + ":" + objectPath.replace(':', '_');
    }

    private static String encodeMember(String eventName, long seq) {
        return eventName + "|" + seq;
    }

    private static CorrelatorHit decodeHit(
            String correlatorId,
            String objectPath,
            ZSetOperations.TypedTuple<String> tuple
    ) {
        String member = tuple.getValue() != null ? tuple.getValue() : "";
        String eventName = member.contains("|") ? member.substring(0, member.indexOf('|')) : member;
        double score = tuple.getScore() != null ? tuple.getScore() : 0D;
        return new CorrelatorHit(correlatorId, objectPath, eventName, Instant.ofEpochMilli((long) score));
    }

    private void trimKey(String key, Instant cutoff) {
        redis.opsForZSet().removeRangeByScore(key, 0, cutoff.toEpochMilli() - 1);
    }

    private void deleteKeys(String pattern) {
        List<String> keys = scanKeys(pattern);
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(256).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }
}
