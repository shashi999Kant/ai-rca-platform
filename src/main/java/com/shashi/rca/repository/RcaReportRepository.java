package com.shashi.rca.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.shashi.rca.model.RcaReportEntity;

public interface RcaReportRepository extends JpaRepository<RcaReportEntity, String> {}