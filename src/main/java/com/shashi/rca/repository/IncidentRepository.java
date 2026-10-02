package com.shashi.rca.repository;

import com.shashi.rca.model.Incident;
import com.shashi.rca.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, String> {

    Optional<Incident> findByTraceId(String traceId);

    long countByDuplicateOfIsNull();

    long countByDuplicateOfIsNotNull();

    /**
     * Returns 1 if inserted, 0 if an incident with this trace_id already exists.
     * The unique index decides, so two concurrent copies of one message cannot both get in.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO incidents (id, trace_id, root_service, trigger_exception, message,
                                   error_signature, duplicate_of, status, created_at)
            VALUES (:id, :traceId, :service, :exception, :message, :signature, :duplicateOf, :status, :createdAt)
            ON CONFLICT (trace_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(String id, String traceId, String service, String exception, String message,
                       String signature, String duplicateOf, String status, LocalDateTime createdAt);

    /**
     * Compare-and-set on status: only one caller can move an incident out of {@code from}.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Incident i SET i.status = :to WHERE i.id = :id AND i.status = :from")
    int updateStatus(String id, IncidentStatus from, IncidentStatus to);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Incident i SET i.status = com.shashi.rca.model.IncidentStatus.FAILED " +
           "WHERE i.id = :id AND i.status <> com.shashi.rca.model.IncidentStatus.DONE")
    int markFailed(String id);

    @Query("""
            SELECT i FROM Incident i
            WHERE i.errorSignature = :signature AND i.duplicateOf IS NULL
              AND i.status IN :statuses AND i.createdAt >= :since
            ORDER BY i.createdAt DESC LIMIT 1
            """)
    Optional<Incident> findRecentOriginal(String signature, Collection<IncidentStatus> statuses, LocalDateTime since);
}
