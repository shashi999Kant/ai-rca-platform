package com.shashi.rca.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.shashi.rca.model.Incident;

import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, String> {
    Optional<Incident> findByTraceId(String traceId);
}
