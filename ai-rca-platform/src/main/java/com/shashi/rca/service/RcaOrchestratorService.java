package com.shashi.rca.service;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import com.shashi.rca.dto.RcaReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RcaOrchestratorService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final Driver neo4jDriver;

    public RcaOrchestratorService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, Driver neo4jDriver) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.neo4jDriver = neo4jDriver;
    }

    public RcaReport runAnalysis(String triggerException, String rawLogMessage, String failingService) {
        log.info("Running Semantic RAG Lookup for Exception: {}", triggerException);

        // 1. GRAPH TOPOLOGY RETRIEVAL via Native Driver Session
        List<String> dependencyChain = new ArrayList<>();
        try (Session session = neo4jDriver.session()) {
            String cypherQuery = "MATCH path = (upstream:Service)-[:CALLS*1..3]->(target:Service {name: $serviceName}) " +
                    "UNWIND nodes(path) as n " +
                    "RETURN DISTINCT n.name as serviceName";

            Result result = session.run(cypherQuery, Map.of("serviceName", failingService));
            while (result.hasNext()) {
                Record record = result.next();
                dependencyChain.add(record.get("serviceName").asString());
            }
        } catch (Exception e) {
            log.error("⚠️ Failed to extract graph context natively: {}", e.getMessage());
        }

        String graphContext = dependencyChain.isEmpty() ?
                "No upstream dependencies detected. Target acts as root ingress." :
                dependencyChain.stream().collect(Collectors.joining(" -> "));

        log.info("Graph Topology Context established: {}", graphContext);


        // 2. Query pgvector for historically similar incidents
        List<Document> similarIncidents = vectorStore.similaritySearch(
                SearchRequest.builder().query(rawLogMessage)
                        .topK(2)
                        .build()
        );

        String ragContext = similarIncidents.isEmpty() ?
                "No historical matching incidents found for this signature." :
                similarIncidents.stream()
                        .map(Document::getText)
                        .collect(Collectors.joining("\n---\n"));

        log.info("Executing Gemini Structured Analysis Loop...");

        // 3. Execute Prompt Engineering Strategy with Structured output mapping
        return this.chatClient.prompt()
                .system(sp -> sp.text("""
                    You are an elite, highly specialized Staff Site Reliability Engineer and Distributed Systems Architect.
                    Your objective is to identify the root cause of production incidents across a microservices mesh.
                    
                    You must baseline your causal reasoning using the following distinct operational dimensions:
                    
                    [DIMENSION 1: ARCHITECTURAL GRAPH TOPOLOGY]
                    This trace represents the physical communication dependencies feeding into the failing service:
                    {graph_context}
                    
                    [DIMENSION 2: HISTORICAL INCIDENT DATA (RAG)]
                    These are verified signatures and corresponding fixes from previous post-mortems:
                    {rag_context}
                    
                    [CRITICAL INSTRUCTIONS]
                    - Map out your assessment logically by tracking how errors propagate from upstream services.
                    - If the log indicates data deserialization or schema issues, evaluate if upstream producers introduced schema breaking changes.
                    """)
                        .param("graph_context", graphContext)
                        .param("rag_context", ragContext))
                .user(u -> u.text("""
                    ANOMALY ENCOUNTERED:
                    Target Failing Service: {service}
                    Trigger Exception: {exception}
                    Raw Telemetry Signature: {log}
                    """)
                        .param("service", failingService)
                        .param("exception", triggerException)
                        .param("log", rawLogMessage))
                .call()
                .entity(RcaReport.class);
    }
}
