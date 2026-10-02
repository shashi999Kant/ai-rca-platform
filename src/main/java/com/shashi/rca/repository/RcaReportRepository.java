package com.shashi.rca.repository;

import com.shashi.rca.model.RcaReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RcaReportRepository extends JpaRepository<RcaReportEntity, String> {

    Optional<RcaReportEntity> findByIncident_Id(String incidentId);
}
