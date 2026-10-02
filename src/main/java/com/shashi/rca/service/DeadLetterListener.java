package com.shashi.rca.service;

import com.shashi.rca.config.KafkaConfig;
import com.shashi.rca.repository.IncidentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterListener {

    private final IncidentRepository incidentRepository;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = {KafkaConfig.RAW_DLT, KafkaConfig.ANALYSIS_DLT}, groupId = "rca-dlt")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        meterRegistry.counter("rca.dlt.messages", "topic", record.topic()).increment();

        if (KafkaConfig.ANALYSIS_DLT.equals(record.topic())) {
            incidentRepository.markFailed(record.value());
            log.error("[RCA STOPPED — DLT] Incident {} marked FAILED after all retries. "
                    + "Search logs above for [RCA STOPPED at Gemini] or [RCA step 2/4] to see which step failed.",
                    record.value());
        } else {
            log.error("Unreadable telemetry moved to {}: {}", record.topic(), record.value());
        }
    }
}
