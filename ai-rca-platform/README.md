# AI-Powered Real-Time Root Cause Analysis (RCA) Platform

An event-driven, hybrid **GraphRAG (Graph-Augmented Retrieval-Augmented Generation)** anomaly detection engine built with **Spring Boot 4.x**, **Spring AI**, **Apache Kafka**, **PostgreSQL (pgvector)**, and **Neo4j**.

This platform intercepts high-throughput, distributed microservice telemetry streams via Kafka, models structural architectural boundaries inside a graph database, queries historical resolutions semantically, and utilizes **Gemini 2.5 Flash** to emit deterministic, structured post-mortem diagnostics in real-time.

---

## 🏗️ System Architecture

The platform bridges real-time event streaming with multi-context artificial intelligence to analyze complex distributed systems failures.

1. **Ingest Layer:** Asynchronous message boundary consumers monitor microservice mesh logs via Apache Kafka topics.
2. **Context Enrichment Engine (GraphRAG):**
* **Structural Context (Neo4j):** Resolves operational system maps up to 3 tiers deep to evaluate failure propagation down dependency trees.
* **Semantic Context (PostgreSQL pgvector):** Leverages Matryoshka Representation Learning (MRL) to truncate text-embeddings to 1536 dimensions, bypassing physical index ceilings while preserving historical resolution patterns.


3. **Inference Layer:** Spring AI orchestrates prompt engineering boundaries to direct Gemini 2.5 Flash to generate schema-enforced, type-safe Java structured records.
4. **Persistence & Feedback Loop:** Reports land in relational tables while the learned outcome is fed back into vector domains to improve future evaluations.

---

## 🛠️ Technology Stack

* **Core Framework:** Spring Boot 4.x, Spring Framework 7.x (Jakarta EE 11)
* **AI Orchestration:** Spring AI (Google GenAI Gateway integrations)
* **Large Language Model:** Gemini 2.5 Flash (`gemini-2.5-flash`), Gemini Embedding Engine (`gemini-embedding-001`)
* **Event Ingestion:** Apache Kafka
* **Vector Store:** PostgreSQL 16+ with `pgvector` extension
* **Graph Database:** Neo4j (Cypher Query Engine)

---

## 📋 Configuration Setup

Create an `application.yml` profile under `src/main/resources/`:

```yaml
spring:
  application:
    name: ai-rca-platform

  # 1. Relational & Vector Persistence
  datasource:
    url: jdbc:postgresql://localhost:5432/rca_db
    username: rca_user
    password: rca_password
    driver-class-name: org.postgresql.Driver
  sql:
    init:
      mode: always
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

  # 2. Vector Store Scaling & Matryoshka Projections
  ai:
    vectorstore:
      pgvector:
        table-name: vector_store_records
        initialize-schema: true
        dimensions: 1536 # Constrained to match HNSW indexing limits

    # 3. Google GenAI Gateways
    google:
      genai:
        api-key: ${SPRING_AI_GOOGLE_GENAI_API_KEY}
        chat:
          model: gemini-2.5-flash
        embedding:
          api-key: ${SPRING_AI_GOOGLE_GENAI_API_KEY}
          text:
            model: gemini-embedding-001
            dimensions: 1536 # Forces Gemini MRL compression

  # 4. Neo4j Graph Configurations
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: rca_password

  # 5. Kafka Core Pipelines
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: rca-core-consumers
      auto-offset-reset: earliest
    properties:
      "[spring.json.trusted.packages]": "*"

```

---

## 🚀 Getting Started & Execution

### 1. Fire up Infrastructure Dependencies

Bring up your persistent datastores and event broker streaming infrastructure locally using Docker Compose:

```bash
docker-compose up -d

```

### 2. Export Global Cloud Credentials

Ensure your local terminal shell holds your active Gemini engine gateway api-key before execution:

```bash
export SPRING_AI_GOOGLE_GENAI_API_KEY="your-gemini-api-key-here"

```

### 3. Run the Spring Boot App

Compile and execute the core service context straight from the root project directory:

```bash
./mvnw spring-boot:run

```

---

## 🧪 Verification & Simulation

To simulate an active enterprise outage cascading across your microservice grid, push a broken serialization context directly into the streaming engine partition using the Kafka terminal producer shell:

```bash
docker exec -it rca-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic app.telemetry.raw

```

Paste the following mock anomaly signature telemetry packet into the stream prompt:

```json
{
  "service": "account-service",
  "timestamp": "2026-06-26T00:15:00",
  "traceId": "trace-graph-rag-01",
  "exception": "SerializationException",
  "message": "Failed to deserialize CustomerCreated event. Expected magic byte 0x00 but found 0x7b (JSON start brace). Schema registry mismatch mapping ID 4402."
}

```

### Reviewing the Output Data Flow

1. **IntelliJ Application Logs** will instantly capture the stream event, execute the Neo4j programmatic session query, find historical RAG resolutions, and dispatch a unified prompt to Gemini.
2. **PostgreSQL Relational Evaluation:** Run the following validation statement in **pgAdmin** to confirm that the schema-enforced AI response populated correctly:

```sql
SELECT 
    i.root_service, i.trigger_exception, r.root_cause, r.confidence_score, r.remediations
FROM incidents i
JOIN rca_reports r ON i.id = r.incident_id;

```

---

## 📈 Future Milestones

* **Autonomous Remediation Agents:** Extending Spring AI `@Tool` callbacks to grant the LLM permission to query live Prometheus metrics or capture Kubernetes pod cluster logs.
* **Hybrid Resiliency Fallbacks:** Embedding Resilience4j circuit breakers to automatically route requests to a local open-source model running via Ollama if the external cloud gateway experiences latency or rate limits.
* **Streaming UI Dashboard:** A Next.js 15 dashboard showing real-time incident analysis powered by persistent Server-Sent Events (SSE).
