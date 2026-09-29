package com.shashi.rca.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.shashi.rca.dto.RcaReport;
import com.shashi.rca.dto.TelemetryMessage;
import com.shashi.rca.model.Incident;
import com.shashi.rca.model.IncidentStatus;
import com.shashi.rca.model.RcaReportEntity;
import com.shashi.rca.repository.IncidentRepository;
import com.shashi.rca.repository.RcaReportRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryIngestionService {

    private final IncidentRepository incidentRepository;
    private final RcaReportRepository rcaReportRepository;
    private final RcaOrchestratorService rcaOrchestratorService;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "app.telemetry.raw")
    public void processIncomingTelemetry(String rawJsonPayload) {
        TelemetryMessage telemetry;
        try {
            telemetry = objectMapper.readValue(rawJsonPayload, TelemetryMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Skipping message that is not valid telemetry JSON: {}", rawJsonPayload, e);
            return;
        }

        log.info("Received telemetry packet from service [{}] with trace [{}]", telemetry.service(), telemetry.traceId());

        if (telemetry.exception() == null || telemetry.exception().isBlank()) {
            return;
        }

        Incident incident = incidentRepository.findByTraceId(telemetry.traceId()).orElse(null);
        if (incident != null && incident.getStatus() != IncidentStatus.FAILED) {
            log.info("Trace [{}] already has incident {} ({}), skipping.", telemetry.traceId(), incident.getId(), incident.getStatus());
            return;
        }

        if (incident == null) {
            incident = incidentRepository.save(Incident.builder()
                    .id(UUID.randomUUID().toString())
                    .traceId(telemetry.traceId())
                    .rootService(telemetry.service())
                    .triggerException(telemetry.exception())
                    .status(IncidentStatus.OPEN)
                    .build());
            log.warn("🚨 New Incident {} created for trace {}.", incident.getId(), telemetry.traceId());
        } else {
            log.warn("🔁 Retrying analysis for previously FAILED incident {}.", incident.getId());
        }

        try {
            RcaReport report = rcaOrchestratorService.runAnalysis(telemetry.exception(), telemetry.message(), telemetry.service());

            rcaReportRepository.save(RcaReportEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .incident(incident)
                    .rootCause(report.rootCause())
                    .confidenceScore(report.confidence())
                    .impactedServices(String.join(",", report.affectedServices()))
                    .remediations(String.join(";", report.recommendation()))
                    .build());
            log.info("✅ Structured RCA Report successfully generated for incident: {}", incident.getId());

            // FEEDBACK LOOP: seed the vector store so the system learns from this incident
            String vectorSummary = String.format("Log: %s | Cause: %s | Fix: %s",
                    telemetry.message(), report.rootCause(), String.join("; ", report.recommendation()));
            vectorStore.add(List.of(new Document(vectorSummary, Map.of("service", telemetry.service()))));
            log.info("🧠 Fed incident pattern back into pgvector knowledge base.");

            incident.setStatus(IncidentStatus.ANALYZED);
        } catch (Exception e) {
            log.error("❌ RCA analysis failed for incident {}. Marked FAILED so it can be retried.", incident.getId(), e);
            incident.setStatus(IncidentStatus.FAILED);
        }
        incidentRepository.save(incident);
    }
}
