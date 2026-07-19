## ADDED Requirements

### Requirement: Document Ingestion
The system SHALL accept text documents (via API POST and local Markdown file scanning), split them into chunks (max 500 chars, 50-char overlap), generate vector embeddings using a local sentence-transformers model, and store both chunks and vectors in PostgreSQL with pgvector extension.

#### Scenario: Ingest a single document via API
- **WHEN** a client POSTs a document with `title` and `content` to `/api/v1/rag/documents`
- **THEN** the system splits the content into chunks, generates embeddings for each chunk, stores them in the `rag_document_chunks` table with pgvector, and returns the document ID with chunk count

#### Scenario: Scan local Markdown files
- **WHEN** the RAG service starts or receives `POST /api/v1/rag/scan`
- **THEN** the system scans `docs/` directory for `*.md` files, splits each file into chunks by headings/paragraphs, generates embeddings, and upserts into pgvector keyed by file path (re-scanning a changed file replaces its previous chunks)

#### Scenario: Ingest a large document
- **WHEN** a client POSTs a document exceeding 5000 characters
- **THEN** the system splits it into N chunks (each ≤ 500 chars, overlapping 50 chars with neighbors), generates N embedding vectors, and stores all chunks atomically — partial failure rolls back the entire document

#### Scenario: Empty document rejected
- **WHEN** a client POSTs a document with empty content
- **THEN** the system returns HTTP 400 with error `EMPTY_DOCUMENT`

### Requirement: Semantic Search
The system SHALL accept a natural language query, convert it to a vector embedding, and return the top-K most semantically similar document chunks ranked by cosine similarity, within a configurable similarity threshold.

#### Scenario: Basic semantic search
- **WHEN** a client GETs `/api/v1/rag/search?q=如何配置用户权限&top_k=5`
- **THEN** the system converts the query to an embedding, performs cosine similarity search on pgvector, and returns up to 5 chunks with similarity scores >= 0.7, ordered by score descending

#### Scenario: No results above threshold
- **WHEN** the highest similarity score is below 0.7
- **THEN** the system returns an empty result list (not an error) with `{"chunks": [], "message": "No relevant content found"}`

#### Scenario: Parameter validation
- **WHEN** `top_k` exceeds 20
- **THEN** the system caps it at 20 (silently) to prevent excessive resource consumption

### Requirement: Prompt Assembly
The system SHALL provide an endpoint that, given a user query, performs semantic search and assembles the retrieved chunks into a structured prompt context suitable for injection into an LLM system message.

#### Scenario: Prompt assembly with context
- **WHEN** a client POSTs `{"query": "如何创建用户", "top_k": 3}` to `/api/v1/rag/assemble`
- **THEN** the system retrieves the top 3 chunks, formats them as:
  ```
  基于以下参考资料回答问题:
  [来源: <title>] <chunk_content>
  ---
  问题: <original query>
  ```
  and returns the assembled prompt string along with source metadata (document titles, chunk indices)

#### Scenario: No context found
- **WHEN** no chunks match the query above the similarity threshold
- **THEN** the system returns the assembled prompt with a note: "未找到相关资料，请基于通用知识回答" followed by the original query

### Requirement: Document Management
The system SHALL support listing all ingested documents and deleting a document with all its associated chunks and vectors.

#### Scenario: List documents
- **WHEN** a client GETs `/api/v1/rag/documents`
- **THEN** the system returns a paginated list of documents with `id`, `title`, `chunk_count`, `created_at`

#### Scenario: Delete a document
- **WHEN** a client DELETEs `/api/v1/rag/documents/{id}`
- **THEN** the system removes the document and all its associated chunks and vectors, returns HTTP 204

#### Scenario: Delete non-existent document
- **WHEN** a client DELETEs a document ID that does not exist
- **THEN** the system returns HTTP 404 with error `DOCUMENT_NOT_FOUND`

### Requirement: Embedding Model
The system SHALL use a locally loaded sentence-transformers model for generating embeddings. The default model is `all-MiniLM-L6-v2` (384 dimensions). The model MUST be loaded once at application startup.

#### Scenario: Model warm-up
- **WHEN** the RAG service starts
- **THEN** the sentence-transformers model is loaded into memory and the service logs "Embedding model loaded: all-MiniLM-L6-v2 (384d)" before accepting requests

#### Scenario: Embedding generation
- **WHEN** a text chunk is submitted for embedding
- **THEN** the model generates a 384-dimensional float vector normalized to unit length
