package com.shashi.rca.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    private String id;

    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Column(name = "root_service", nullable = false)
    private String rootService;

    @Column(name = "trigger_exception", length = 255)
    private String triggerException;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}