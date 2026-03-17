-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Documents table
CREATE TABLE IF NOT EXISTS documents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name   VARCHAR(512)  NOT NULL,
    file_type   VARCHAR(64)   NOT NULL,
    file_size   BIGINT        NOT NULL,
    status      VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    chunk_count INTEGER       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Document chunks with vector embeddings
CREATE TABLE IF NOT EXISTS document_chunks (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID          NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index  INTEGER       NOT NULL,
    content      TEXT          NOT NULL,
    token_count  INTEGER       NOT NULL DEFAULT 0,
    embedding    vector(1536),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Q&A sessions
CREATE TABLE IF NOT EXISTS qa_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Q&A history
CREATE TABLE IF NOT EXISTS qa_history (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id       UUID          REFERENCES qa_sessions(id) ON DELETE SET NULL,
    question         TEXT          NOT NULL,
    answer           TEXT          NOT NULL,
    source_chunk_ids UUID[]        NOT NULL DEFAULT '{}',
    model_used       VARCHAR(64)   NOT NULL,
    tokens_used      INTEGER       NOT NULL DEFAULT 0,
    latency_ms       INTEGER       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_chunks_document_id ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(status);
CREATE INDEX IF NOT EXISTS idx_qa_history_session ON qa_history(session_id);
CREATE INDEX IF NOT EXISTS idx_qa_history_created ON qa_history(created_at DESC);
