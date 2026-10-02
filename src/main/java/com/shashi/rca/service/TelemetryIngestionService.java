package com.shashi.rca.service;

import com.shashi.rca.config.KafkaConfig;
import com.shashi.rca.dto.TelemetryMessage;
import com.shashi.rca.model.Incident;
import com.shashi.rca.model.IncidentStatus;
import com.shashi.rca.repository.IncidentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fast path: parse, de-duplicate, group and store. The slow LLM work happens in {@link IncidentAnalysisService}.
 */
@Service
@Slf4j
public class TelemetryIngestionService {

    private static final List<IncidentStatus> REUSABLE = List.of(
            IncidentStatus.OPEN, IncidentStatus.ANALYZING, IncidentStatus.DONE);

    private final IncidentRepository incidentRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final Duration groupingWindow;
    private final Counter incidentsReceived;

    public TelemetryIngestionService(IncidentRepository incidentRepository,
                                     KafkaTemplate<String, String> kafkaTemplate,
                                     JsonMapper jsonMapper,
                                     MeterRegistry meterRegistry,
                                     @Value("${rca.grouping.window:30m}") Duration groupingWindow) {
        this.incidentRepository = incidentRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.groupingWindow = groupingWindow;
        this.incidentsReceived = meterRegistry.counter("rca.incidents.received");
    }

    @KafkaListener(topics = KafkaConfig.RAW_TOPIC, groupId = "rca-ingest")
    public void onTelemetry(String rawJson) {
        TelemetryMessage telemetry = jsonMapper.readValue(rawJson, TelemetryMessage.class);

        if (telemetry.exception() == null || telemetry.exception().isBlank()) {
            return; // not an error, nothing to analyse
        }
        if (isBlank(telemetry.traceId()) || isBlank(telemetry.service())) {
            throw new IllegalArgumentException("Telemetry is missing traceId or service: " + rawJson);
        }
        incidentsReceived.increment();

        String signature = ErrorSignature.of(telemetry.service(), telemetry.exception(), telemetry.message());
        LocalDateTime now = LocalDateTime.now();
        Optional<Incident> original = incidentRepository.findRecentOriginal(
                signature, REUSABLE, now.minus(groupingWindow));

        String incidentId = UUID.randomUUID().toString();
        int inserted = incidentRepository.insertIfAbsent(
                incidentId, telemetry.traceId(), telemetry.service(), telemetry.exception(), telemetry.message(),
                signature, original.map(Incident::getId).orElse(null),
                (original.isPresent() ? IncidentStatus.DONE : IncidentStatus.OPEN).name(), now);

        if (inserted == 0) {
            handleDuplicateTrace(telemetry.traceId());
            return;
        }
        if (original.isPresent()) {
            log.info("Incident {} grouped with {} (same error signature), no LLM call.", incidentId, original.get().getId());
            return;
        }

        log.warn("[RCA step 0/4] New incident {} for trace {} on [{}] — queued for analysis.",
                incidentId, telemetry.traceId(), telemetry.service());
        requestAnalysis(incidentId);
    }

    /**
     * Kafka delivers at least once, so the same trace can arrive again. If the first attempt crashed after
     * saving but before publishing, the incident is still OPEN and is re-published; the analysis worker's
     * status check makes a second request harmless.
     */
    private void handleDuplicateTrace(String traceId) {
        Incident existing = incidentRepository.findByTraceId(traceId).orElseThrow();
        if (existing.getStatus() == IncidentStatus.OPEN && existing.getDuplicateOf() == null) {
            log.info("Trace {} seen again while still OPEN, re-publishing analysis request.", traceId);
            requestAnalysis(existing.getId());
        } else {
            log.info("Duplicate trace {} ignored (incident {} is {}).", traceId, existing.getId(), existing.getStatus());
        }
    }

    private void requestAnalysis(String incidentId) {
        kafkaTemplate.send(KafkaConfig.ANALYSIS_TOPIC, incidentId, incidentId).join();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
