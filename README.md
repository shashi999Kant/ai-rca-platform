# AI Incident Root Cause Analysis (RCA) Engine

An event-driven AI system that analyzes application errors and generates **Root Cause Analysis (RCA)** reports using **RAG, service dependency graphs, and Google Gemini**.

## 🚀 Tech Stack

- **Java 21**
- **Spring Boot**
- **Spring AI**
- **Apache Kafka**
- **PostgreSQL + pgvector**
- **Neo4j**
- **Google Gemini**
- **Resilience4j**
- **Micrometer / Prometheus**
- **Testcontainers**
- **Docker**

## 🏗️ Architecture

```text
Application Logs
      ↓
    Kafka
      ↓
Incident Ingestion
      ↓
PostgreSQL
      ↓
RCA Analysis Worker
   ↙        ↓        ↘
Neo4j    pgvector   Gemini
Graph      RAG       LLM
   ↘        ↓        ↙
       RCA Report
           ↓
      PostgreSQL
```

## 🔄 How It Works

1. Application errors are received through Kafka.
2. Similar errors are grouped using an **error signature** to reduce duplicate processing.
3. **Neo4j** provides upstream/downstream service dependencies.
4. **pgvector** retrieves similar **human-verified incidents**.
5. **Gemini** combines the error, service dependencies, and retrieved incidents to generate a structured RCA.
6. The RCA report is stored in PostgreSQL.
7. Engineers can verify reports and add verified reports to the RAG knowledge base.

## ✨ Key Features

- Event-driven incident processing with Kafka
- Error deduplication and incident grouping
- RAG using PostgreSQL + pgvector
- Service dependency analysis using Neo4j
- Human-verified knowledge base
- Structured AI-generated RCA reports
- Kafka retries and Dead Letter Topics (DLT)
- Resilience4j circuit breaker and timeout handling
- Prometheus metrics
- Integration testing with Testcontainers

## 🛠️ Run Locally

### Prerequisites

- Java 21
- Docker
- Google Gemini API key

### 1. Start Infrastructure

```powershell
docker compose up -d
```

### 2. Set Gemini API Key

```powershell
$env:SPRING_AI_GOOGLE_GENAI_API_KEY="<your-api-key>"
```

### 3. Start Application

```powershell
.\mvnw.cmd spring-boot:run
```

### 4. Run Tests

```powershell
.\mvnw.cmd test
```

## 📡 API Endpoints

| Endpoint | Description |
|---|---|
| `GET /incidents` | List incidents |
| `GET /incidents/{id}/report` | Get RCA report |
| `POST /reports/{id}/verify` | Verify an RCA and add it to the knowledge base |
| `GET /stats` | View processing statistics |
| `GET /actuator/prometheus` | Prometheus metrics |

## 🧠 RAG Flow

```text
Incident
   ↓
Extract Service + Exception
   ↓
Search Verified Incidents
   ↓
Retrieve Relevant Context
   ↓
Combine with Neo4j Service Graph
   ↓
Gemini
   ↓
Structured RCA
```

## 📌 Example

```text
Error: SerializationException
Service: account-service
        ↓
      Kafka
        ↓
  Incident Analysis
     ↙      ↓      ↘
  Neo4j  pgvector  Gemini
     ↘      ↓      ↙
       RCA Report
```

## 📊 Project Goal

This project demonstrates how **event-driven architecture, RAG, vector search, knowledge graphs, and LLMs** can be combined to assist engineers in investigating application incidents.
