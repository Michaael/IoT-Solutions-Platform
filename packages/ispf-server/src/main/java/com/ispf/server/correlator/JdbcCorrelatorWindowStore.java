package com.ispf.server.correlator;

import com.ispf.server.persistence.CorrelatorHitRepository;
import com.ispf.server.persistence.entity.CorrelatorHitEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "ispf.redis", name = "correlator-windows-enabled", havingValue = "false", matchIfMissing = true)
public class JdbcCorrelatorWindowStore implements CorrelatorWindowStore {

    private final CorrelatorHitRepository hitRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public JdbcCorrelatorWindowStore(CorrelatorHitRepository hitRepository) {
        this.hitRepository = hitRepository;
    }

    @Override
    public void recordHit(String correlatorId, String objectPath, String eventName, Instant occurredAt) {
        CorrelatorHitEntity hit = new CorrelatorHitEntity();
        hit.setCorrelatorId(correlatorId);
        hit.setObjectPath(objectPath);
        hit.setEventName(eventName);
        hit.setOccurredAt(occurredAt);
        hitRepository.save(hit);
        entityManager.flush();
    }

    @Override
    public long countHitsSince(String correlatorId, String objectPath, Instant since) {
        return hitRepository.countByCorrelatorIdAndObjectPathAndOccurredAtAfter(correlatorId, objectPath, since);
    }

    @Override
    public Optional<CorrelatorHit> findFirstHitSince(
            String correlatorId,
            String objectPath,
            String eventName,
            Instant since
    ) {
        return hitRepository
                .findFirstByCorrelatorIdAndObjectPathAndEventNameAndOccurredAtAfterOrderByOccurredAtAsc(
                        correlatorId,
                        objectPath,
                        eventName,
                        since
                )
                .map(this::toHit);
    }

    @Override
    public List<CorrelatorHit> listHitsSince(String correlatorId, String objectPath, Instant since) {
        return hitRepository
                .findByCorrelatorIdAndObjectPathAndOccurredAtAfterOrderByOccurredAtAsc(
                        correlatorId,
                        objectPath,
                        since
                )
                .stream()
                .map(this::toHit)
                .toList();
    }

    @Override
    public void clearCorrelator(String correlatorId) {
        hitRepository.deleteByCorrelatorId(correlatorId);
    }

    @Override
    public void purgeOlderThan(Instant cutoff) {
        hitRepository.deleteOlderThan(cutoff);
    }

    @Override
    public void remapCorrelatorId(String oldId, String newId) {
        hitRepository.remapCorrelatorId(oldId, newId);
    }

    private CorrelatorHit toHit(CorrelatorHitEntity entity) {
        return new CorrelatorHit(
                entity.getCorrelatorId(),
                entity.getObjectPath(),
                entity.getEventName(),
                entity.getOccurredAt()
        );
    }
}
