package com.shashi.rca.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Rows are inserted with a native INSERT ... ON CONFLICT (see IncidentRepository), so this entity is read-mostly.
 */
@Entity
@Table(name = "incidents")
@Getter
@NoArgsConstructor
public class Incident {

    @Id
    private String id;

    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Column(name = "root_service", nullable = false)
    private String rootService;

    @Column(name = "trigger_exception", nullable = false)
    private String triggerException;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "error_signature", nullable = false)
    private String errorSignature;

    @Column(name = "duplicate_of")
    private String duplicateOf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
