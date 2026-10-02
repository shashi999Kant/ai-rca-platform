package com.shashi.rca.controller;

import com.shashi.rca.dto.ReportResponse;
import com.shashi.rca.dto.StatsResponse;
import com.shashi.rca.model.Incident;
import com.shashi.rca.repository.IncidentRepository;
import com.shashi.rca.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentRepository incidentRepository;
    private final ReportService reportService;

    @GetMapping("/incidents")
    public List<Incident> incidents(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return incidentRepository.findAll(PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    @GetMapping("/incidents/{id}/report")
    public ReportResponse report(@PathVariable String id) {
        return reportService.reportForIncident(id);
    }

    @PostMapping("/reports/{id}/verify")
    public ReportResponse verify(@PathVariable String id) {
        return reportService.verify(id);
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return reportService.stats();
    }
}
