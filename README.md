# Knowledge Assistant

A small Spring Boot application that combines document retrieval, Gemini, tool
calling, and MCP. Upload text or a PDF, ask a question, and inspect the excerpts
used to produce the answer.

There are two ways to ask: `/ask` always searches the uploaded documents;
`/agent/ask` lets the model choose between document search and a currency-rate
lookup. The same tools are available to MCP clients.

## Stack

Java 21, Spring Boot 4.1.1, Spring AI 2.0.1, PostgreSQL with pgvector, and Google
Gemini for chat and embeddings. PDF text extraction uses PDFBox, and Maven builds
and packages the application.

## Run locally

You need Java 21, a PostgreSQL instance with pgvector installed, and a Gemini API
key from Google AI Studio. Docker isn't required.

Create the database once, then enable the extension:

```bash
createdb aiassistant
psql -d aiassistant -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

If PostgreSQL cannot find `vector.control`, install pgvector for your server
version using the [pgvector installation guide](https://github.com/pgvector/pgvector#installation).

From the project folder:

```bash
export DB_URL="jdbc:postgresql://localhost:5432/aiassistant"
export DB_USER="$(whoami)"
export DB_PASSWORD=""
export GEMINI_API_KEY="your-key"
./mvnw spring-boot:run
```

Use your database password if one is required. Set these variables in the same
terminal or in your IDE; Spring Boot does not load `.env` files automatically.
A leftover `SPRING_DATASOURCE_URL` overrides `DB_URL`, so check it if the app
connects to the wrong port.

The app listens on `127.0.0.1:8080` and creates `rag_embeddings` on startup.

## Try it

Index a short policy:

```bash
curl -sS http://localhost:8080/api/ai/ingest \
  -H 'Content-Type: application/json' \
  -d '{"content":"Employees receive 20 annual leave days per year.","source":"leave-policy"}'
```

Ask about it:

```bash
curl -sS http://localhost:8080/api/ai/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"How many annual leave days do employees receive?"}'
```

Ingestion returns a chunk count. Answers include a `sources` array containing
the retrieved excerpts and their IDs. Check those excerpts when an answer seems wrong.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/ai/ingest` | Index JSON with `content` and optional `source` |
| `POST /api/ai/ingest/pdf` | Upload a PDF as multipart field `file` |
| `POST /api/ai/ask` | Answer a document question |
| `POST /api/ai/agent/ask` | Answer using tools; takes the same `question` field |
| `/mcp` | Streamable HTTP MCP endpoint |
| `GET /actuator/health` | Check application and database health |

For example, ask the agent: “What is the latest published USD to INR exchange rate?”
It can call Frankfurter for a dated reference rate.

## MCP and authentication

Connect a client that supports Streamable HTTP to `http://127.0.0.1:8080/mcp`.
It will discover `search_knowledge_base(query)` and
`get_exchange_rate(base, target)`. A stdio-only client needs a bridge.

Set `ASSISTANT_API_KEY` to require an `X-API-Key` header on REST and MCP requests.
Without it, local requests are unauthenticated. This shared key does not separate
one user's documents from another's.

If authentication is enabled, add `-H "X-API-Key: $ASSISTANT_API_KEY"` to the
examples above.

## Tests and build

```bash
./mvnw verify
java -jar target/aiassistant-0.0.1-SNAPSHOT.jar
```

Tests run without PostgreSQL or an API key. They cover PDF extraction, retrieval,
tool execution, HTTP validation, authentication and MCP sessions using substitutes
for the model and vector store. Running the JAR needs the environment above.
Live PostgreSQL/Gemini testing is still required.

## Design notes

The retrieval steps are explicit so they're easy to debug. RAG and the agent share
one search service; MCP exposes the same tools rather than a second implementation.
PostgreSQL holds both the text and its vectors. Currency lookup is a small example
of a tool that obtains information outside the uploaded documents.

This is deliberately a compact project. Repeated uploads create duplicate chunks,
scanned PDFs need OCR, and retrieval settings still need tuning against a question set.
Sources show retrieved evidence; they don't guarantee the model used it correctly.
Model calls send text to the provider and can incur charges.

Before deployment, the next work is document ownership, upload deduplication,
schema migrations, rate limits and live answer-quality checks. Tool calls are
currently capped at three per tool and six per turn.

The [code walkthrough](docs/WALKTHROUGH.md) explains each file, the technology
choices and the debugging problems encountered while building this.
