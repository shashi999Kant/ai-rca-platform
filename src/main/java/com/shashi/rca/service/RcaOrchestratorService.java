package com.shashi.rca.service;

import com.shashi.rca.dto.RcaContext;
import com.shashi.rca.dto.RcaReport;
import com.shashi.rca.model.Incident;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RcaOrchestratorService {

    private static final String UPSTREAM_QUERY =
            "MATCH path = (:Service)-[:CALLS*1..3]->(:Service {name: $serviceName}) " +
            "RETURN [n IN nodes(path) | n.name] AS chain";

    private static final String DOWNSTREAM_QUERY =
            "MATCH path = (:Service {name: $serviceName})-[:CALLS*1..3]->(:Service) " +
            "RETURN [n IN nodes(path) | n.name] AS chain";

    private final Driver neo4jDriver;
    private final VectorStore vectorStore;
    private final LlmClient llmClient;
    private final double similarityThreshold;
    private final int topK;

    public RcaOrchestratorService(Driver neo4jDriver,
                                  VectorStore vectorStore,
                                  LlmClient llmClient,
                                  @Value("${rca.rag.similarity-threshold:0.75}") double similarityThreshold,
                                  @Value("${rca.rag.top-k:3}") int topK) {
        this.neo4jDriver = neo4jDriver;
        this.vectorStore = vectorStore;
        this.llmClient = llmClient;
        this.similarityThreshold = similarityThreshold;
        this.topK = topK;
    }

    public RcaReport runAnalysis(Incident incident) {
        String service = incident.getRootService();

        log.info("[RCA step 2/4] Loading service graph from Neo4j for [{}]", service);

        String upstream = callPaths(UPSTREAM_QUERY, service, "No upstream callers found.");
        String downstream = callPaths(DOWNSTREAM_QUERY, service, "No downstream dependencies found.");
        log.info("[RCA step 2/4] Graph context OK for [{}]\n  upstream:\n{}\n  downstream:\n{}", service, upstream, downstream);

        log.info("[RCA step 3/4] Searching pgvector for verified similar incidents");
        String history = similarVerifiedIncidents(incident);

        return llmClient.analyze(new RcaContext(
                service, incident.getTriggerException(), incident.getMessage(), upstream, downstream, history));
    }

    private String callPaths(String cypher, String service, String emptyText) {
        List<String> paths = new ArrayList<>();
        try (Session session = neo4jDriver.session()) {
            Result result = session.run(cypher, Map.of("serviceName", service));
            while (result.hasNext()) {
                paths.add(String.join(" -> ", result.next().get("chain").asList(v -> v.asString())));
            }
        } catch (Exception e) {
            log.error("[RCA STOPPED at Neo4j step 2/4] Could not read graph: {}", e.getMessage());
        }
        return paths.isEmpty() ? emptyText : String.join("\n", paths);
    }

    private String similarVerifiedIncidents(Incident incident) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder()
                .query(incident.getMessage() != null ? incident.getMessage() : incident.getTriggerException())
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression(b.and(
                        b.eq("service", incident.getRootService()),
                        b.eq("exceptionType", incident.getTriggerException())).build())
                .build());

        log.info("[RCA step 3/4] pgvector search done ({} verified match(es))", matches.size());
        matches.forEach(d -> log.info("  RAG match score={} reportId={}", d.getScore(), d.getMetadata().get("reportId")));

        return matches.isEmpty()
                ? "No verified past incident is similar enough."
                : matches.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
    }
}
