package com.shashi.rca.service;

import com.shashi.rca.config.KafkaConfig;
import com.shashi.rca.dto.RcaReport;
import com.shashi.rca.model.Incident;
import com.shashi.rca.model.IncidentStatus;
import com.shashi.rca.model.RcaReportEntity;
import com.shashi.rca.repository.IncidentRepository;
import com.shashi.rca.repository.RcaReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Slow path: one LLM analysis per message on the analysis topic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentAnalysisService {

    private final IncidentRepository incidentRepository;
    private final RcaReportRepository rcaReportRepository;
    private final RcaOrchestratorService rcaOrchestratorService;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(topics = KafkaConfig.ANALYSIS_TOPIC, groupId = "rca-analysis", concurrency = "3",
            properties = {"max.poll.records=1", "max.poll.interval.ms=600000"})
    public void onAnalysisRequest(String incidentId) {
        if (incidentRepository.updateStatus(incidentId, IncidentStatus.OPEN, IncidentStatus.ANALYZING) == 0) {
            log.info("Incident {} is not OPEN (already taken or finished), skipping.", incidentId);
            return;
        }

        log.info("[RCA step 1/4] Analysis worker claimed incident {}", incidentId);

        try {
            Incident incident = incidentRepository.findById(incidentId).orElseThrow();
            RcaReport report = rcaOrchestratorService.runAnalysis(incident);

            transactionTemplate.executeWithoutResult(tx -> {
                rcaReportRepository.save(RcaReportEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .incident(incident)
                        .rootCause(report.rootCause())
                        .confidenceScore(report.confidence())
                        .impactedServices(String.join(",", orEmpty(report.affectedServices())))
                        .remediations(String.join(";", orEmpty(report.recommendations())))
                        .build());
                incidentRepository.updateStatus(incidentId, IncidentStatus.ANALYZING, IncidentStatus.DONE);
            });
            log.info("[RCA COMPLETE] Report saved; incident {} is DONE.", incidentId);
        } catch (RuntimeException e) {
            incidentRepository.updateStatus(incidentId, IncidentStatus.ANALYZING, IncidentStatus.OPEN);
            log.error("[RCA FAILED] incident {} — Kafka will retry unless max retries reached. {}",
                    incidentId, LlmFailureMessages.summarize(e));
            throw e;
        }
    }

    private static List<String> orEmpty(List<String> values) {
        return values != null ? values : List.of();
    }
}
