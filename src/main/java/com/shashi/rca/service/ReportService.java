package com.shashi.rca.service;

import com.shashi.rca.dto.ReportResponse;
import com.shashi.rca.dto.StatsResponse;
import com.shashi.rca.model.Incident;
import com.shashi.rca.model.RcaReportEntity;
import com.shashi.rca.repository.IncidentRepository;
import com.shashi.rca.repository.RcaReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final IncidentRepository incidentRepository;
    private final RcaReportRepository rcaReportRepository;
    private final VectorStore vectorStore;

    @Transactional(readOnly = true)
    public ReportResponse reportForIncident(String incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
        // Grouped incidents point at the incident whose report they reuse
        String reportOwnerId = incident.getDuplicateOf() != null ? incident.getDuplicateOf() : incident.getId();
        return rcaReportRepository.findByIncident_Id(reportOwnerId)
                .map(ReportResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No report yet"));
    }

    /**
     * A human confirms the report is correct; only then does it become "history" for future RAG lookups.
     */
    @Transactional
    public ReportResponse verify(String reportId) {
        RcaReportEntity report = rcaReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        if (report.isVerified()) {
            return ReportResponse.from(report);
        }

        Incident incident = report.getIncident();
        String text = String.format("Service: %s | Exception: %s | Log: %s | Root cause: %s | Fix: %s",
                incident.getRootService(), incident.getTriggerException(), incident.getMessage(),
                report.getRootCause(), report.getRemediations());

        // The report id is reused as the vector document id, so a repeated verify overwrites instead of duplicating
        vectorStore.add(List.of(new Document(report.getId(), text, Map.of(
                "service", incident.getRootService(),
                "exceptionType", incident.getTriggerException(),
                "reportId", report.getId(),
                "incidentId", incident.getId()))));

        report.setVerified(true);
        report.setVerifiedAt(LocalDateTime.now());
        log.info("Report {} verified and added to the knowledge base.", reportId);
        return ReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        long analysed = incidentRepository.countByDuplicateOfIsNull();
        long reused = incidentRepository.countByDuplicateOfIsNotNull();
        long total = analysed + reused;
        double reduction = total == 0 ? 0.0 : Math.round(reused * 1000.0 / total) / 10.0;
        return new StatsResponse(total, analysed, reused, reduction);
    }
}
