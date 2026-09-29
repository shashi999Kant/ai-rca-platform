package com.shashi.rca.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/init-check")
public class RcaBootController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    // Spring AI automatically autoconfigures these abstract interfaces using your YAML values
    public RcaBootController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @PostMapping("/test-pipeline")
    public Map<String, Object> verifySystemPipeline(@RequestBody String sampleErrorLog) {
        // 1. Verify Vector Embedding Generation & PostgreSQL storage
        Document diagnosticDoc = new Document(sampleErrorLog, Map.of("severity", "CRITICAL"));
        vectorStore.add(List.of(diagnosticDoc));

        // 2. Verify Chat Engine execution via Gemini
        String aiResponse = chatClient.prompt()
                .user("Provide a 1-sentence quick assessment of this log payload: " + sampleErrorLog)
                .call()
                .content();

        return Map.of(
                "status", "SUCCESS",
                "database_action", "Vector record successfully generated and stored.",
                "gemini_assessment", aiResponse
        );
    }
}
