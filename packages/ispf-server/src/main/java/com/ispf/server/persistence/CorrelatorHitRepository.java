package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.CorrelatorHitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ispf.server.persistence.entity.CorrelatorHitEntity;

import java.time.Instant;
import java.util.List;

public interface CorrelatorHitRepository extends JpaRepository<CorrelatorHitEntity, Long> {

    long countByCorrelatorIdAndObjectPathAndOccurredAtAfter(
            String correlatorId,
            String objectPath,
            Instant since
    );

    boolean existsByCorrelatorIdAndObjectPathAndEventNameAndOccurredAtAfter(
            String correlatorId,
            String objectPath,
            String eventName,
            Instant since
    );

    List<CorrelatorHitEntity> findByCorrelatorIdAndObjectPathAndOccurredAtAfterOrderByOccurredAtAsc(
            String correlatorId,
            String objectPath,
            Instant since
    );

    java.util.Optional<CorrelatorHitEntity> findFirstByCorrelatorIdAndObjectPathAndEventNameAndOccurredAtAfterOrderByOccurredAtAsc(
            String correlatorId,
            String objectPath,
            String eventName,
            Instant since
    );

    @Modifying
    @Query("DELETE FROM CorrelatorHitEntity h WHERE h.correlatorId = :correlatorId")
    void deleteByCorrelatorId(@Param("correlatorId") String correlatorId);

    @Modifying
    @Query("DELETE FROM CorrelatorHitEntity h WHERE h.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE CorrelatorHitEntity h SET h.correlatorId = :newId WHERE h.correlatorId = :oldId")
    void remapCorrelatorId(@Param("oldId") String oldId, @Param("newId") String newId);
}
