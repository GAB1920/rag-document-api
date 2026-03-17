# 📄 RAG Document Q&A API

A production-ready **Retrieval-Augmented Generation (RAG)** API built with Spring Boot, PostgreSQL + pgvector, and OpenAI. Upload documents, ask questions in natural language, and get accurate answers grounded in your content.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client (REST)                            │
└────────────────────┬──────────────────────┬────────────────────┘
                     │                      │
              POST /documents          POST /qa/ask
                     │                      │
┌────────────────────▼──────────────────────▼────────────────────┐
│                   Spring Boot API  (:8080)                      │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │               INGESTION PIPELINE (async)                 │   │
│  │                                                          │   │
│  │  MultipartFile ──► Tika Extract ──► Chunk (512/64)      │   │
│  │       ──► OpenAI Embeddings ──► pgvector INSERT         │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    RAG PIPELINE                          │   │
│  │                                                          │   │
│  │  Question ──► Embed ──► cosine_distance search          │   │
│  │       ──► Top-K chunks ──► Build context prompt         │   │
│  │       ──► GPT-4o ──► Answer + sources                   │   │
│  └──────────────────────────────────────────────────────────┘   │
└───────────────────────────┬────────────────────────────────────┘
                            │
         ┌──────────────────┴──────────────────┐
         │                                     │
┌────────▼─────────┐                 ┌─────────▼────────┐
│  PostgreSQL 16   │                 │   OpenAI API      │
│  + pgvector      │                 │                   │
│                  │                 │  text-embedding   │
│  documents       │                 │  -3-small         │
│  document_chunks │                 │                   │
│  (vector 1536)   │                 │  gpt-4o           │
│  qa_history      │                 └──────────────────┘
│  HNSW index      │
└──────────────────┘
```

---

## Features

- **Multi-format ingestion** — PDF, DOCX, TXT, HTML, Markdown via Apache Tika
- **Semantic chunking** — Sentence-aware sliding window (configurable size + overlap)
- **pgvector + HNSW** — Fast approximate nearest-neighbour search at scale
- **OpenAI integration** — `text-embedding-3-small` for embeddings, `gpt-4o` for generation
- **Async processing** — Document ingestion runs in the background; poll for `READY` status
- **Session support** — Group Q&A turns into sessions for conversational context
- **Source attribution** — Every answer includes ranked source chunks with similarity scores
- **Flyway migrations** — Schema versioning out of the box
- **Docker Compose** — One command to run everything locally

---

## Quick Start

### Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Docker & Docker Compose | latest |
| OpenAI API key | — |

### 1. Clone & configure

```bash
git clone https://github.com/YOUR_USERNAME/rag-document-api.git
cd rag-document-api
cp .env.example .env
# Edit .env and set OPENAI_API_KEY=sk-...
```

### 2. Run with Docker Compose

```bash
docker compose up --build
```

The API will be available at `http://localhost:8080` once the health check passes (~60 s).

### 3. Run locally (with existing Postgres)

```bash
# Start pgvector Postgres
docker run -d --name rag-pg \
  -e POSTGRES_DB=ragdb -e POSTGRES_USER=raguser -e POSTGRES_PASSWORD=ragpassword \
  -p 5432:5432 pgvector/pgvector:pg16

# Run the app
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

---

## API Reference

### Documents

#### Upload a document

```bash
curl -X POST http://localhost:8080/api/v1/documents \
  -F "file=@/path/to/your/document.pdf"
```

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "fileName": "document.pdf",
  "fileType": "application/pdf",
  "fileSize": 204800,
  "status": "PENDING",
  "chunkCount": 0,
  "createdAt": "2024-03-17T10:00:00Z"
}
```

> Poll `GET /api/v1/documents/{id}` until `status` is `READY` before querying.

#### List all documents

```bash
curl http://localhost:8080/api/v1/documents
```

#### Get document status

```bash
curl http://localhost:8080/api/v1/documents/3fa85f64-5717-4562-b3fc-2c963f66afa6
```

#### Delete a document

```bash
curl -X DELETE http://localhost:8080/api/v1/documents/3fa85f64-5717-4562-b3fc-2c963f66afa6
```

---

### Q&A

#### Ask a question (global scope)

```bash
curl -X POST http://localhost:8080/api/v1/qa/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What are the main topics covered in the uploaded documents?"
  }'
```

```json
{
  "id": "a1b2c3d4-...",
  "question": "What are the main topics covered in the uploaded documents?",
  "answer": "Based on the provided context, the documents cover...",
  "sources": [
    {
      "chunkId": "...",
      "documentId": "3fa85f64-...",
      "documentName": "document.pdf",
      "chunkIndex": 4,
      "content": "Relevant excerpt from the document...",
      "similarityScore": 0.9124
    }
  ],
  "modelUsed": "gpt-4o",
  "tokensUsed": 512,
  "latencyMs": 1843,
  "createdAt": "2024-03-17T10:05:00Z"
}
```

#### Ask scoped to a specific document

```bash
curl -X POST http://localhost:8080/api/v1/qa/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Summarize section 3",
    "documentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
  }'
```

#### Ask within a session (conversational)

```bash
curl -X POST http://localhost:8080/api/v1/qa/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What does it say about pricing?",
    "sessionId": "session-uuid-here"
  }'
```

#### Get Q&A history

```bash
curl "http://localhost:8080/api/v1/qa/history?page=0&size=20"
```

---

## Configuration

All settings are in `src/main/resources/application.yml` and can be overridden via environment variables:

| Environment Variable | Default | Description |
|---|---|---|
| `OPENAI_API_KEY` | _(required)_ | Your OpenAI API key |
| `DB_HOST` | `localhost` | Postgres host |
| `DB_PORT` | `5432` | Postgres port |
| `DB_NAME` | `ragdb` | Database name |
| `DB_USER` | `raguser` | Database user |
| `DB_PASS` | `ragpassword` | Database password |
| `SERVER_PORT` | `8080` | API server port |

RAG tuning (in `application.yml`):

| Property | Default | Description |
|---|---|---|
| `rag.chunk-size` | `512` | Max chars per chunk |
| `rag.chunk-overlap` | `64` | Overlap between chunks |
| `rag.top-k-results` | `5` | Chunks retrieved per query |
| `rag.similarity-threshold` | `0.70` | Min cosine similarity (0–1) |
| `openai.embedding-model` | `text-embedding-3-small` | Embedding model |
| `openai.chat-model` | `gpt-4o` | Chat/generation model |

---

## Project Structure

```
src/
├── main/
│   ├── java/com/ragapi/
│   │   ├── RagDocumentApiApplication.java
│   │   ├── config/          # AppConfig, OpenAiProperties, RagProperties
│   │   ├── controller/      # DocumentController, QaController, GlobalExceptionHandler
│   │   ├── dto/             # DocumentDtos, QaDtos, OpenAiDtos
│   │   ├── model/           # Document, DocumentChunk, QaHistory
│   │   ├── repository/      # JPA repos + native pgvector queries
│   │   └── service/         # OpenAiService, RagService, DocumentService,
│   │                        # TextChunkingService, DocumentExtractionService
│   └── resources/
│       ├── application.yml
│       └── db/migration/    # Flyway SQL migrations
└── test/
    └── java/com/ragapi/
        ├── TextChunkingServiceTest.java      # Unit tests
        └── DocumentApiIntegrationTest.java   # Testcontainers integration tests
```

---

## Running Tests

```bash
# Unit tests only (no Docker needed)
./mvnw test -Dtest=TextChunkingServiceTest

# All tests including integration (requires Docker for Testcontainers)
./mvnw verify
```

---

## Health Check

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## License

MIT
