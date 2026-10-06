# How this project works

Read the controller first, then ingestion, then retrieval, then the agent and MCP.
You should be able to trace one request without knowing the framework internals.

## Technologies and their jobs

| Technology | Meaning in this project |
| --- | --- |
| Java 21 | Language and JVM running the application. Records represent small response objects. |
| Maven and wrapper | Resolve compatible libraries, compile, test and package. mvnw pins the build tool. |
| Spring Boot 4.1.1 | Starts the server and creates/injects configured components (beans). |
| Spring MVC / Tomcat | Receive HTTP requests and convert JSON to Java and back. |
| Spring AI 2.0.1 | Adapters for chat, embeddings, vector stores and tool execution. |
| Gemini chat model | Writes answers from supplied evidence; does not query PostgreSQL itself. |
| Embedding model | Turns text into numeric vectors. Documents and questions use the same model. |
| PostgreSQL + pgvector | Durable storage and vector similarity search. An ordinary database without the extension cannot do this job. |
| JDBC / HikariCP | Database connectivity and a pool of reusable connections. No JPA entities are needed here. |
| PDFBox through Spring AI | Extracts text from PDF pages. Not an OCR engine. |
| Lombok | Generates constructors and the SLF4J logger at compilation. |
| SLF4J | Logging API: log ingestion counts without printing private document bodies. |
| MCP | Protocol allowing AI clients to discover/call our tools. Streamable HTTP is the selected transport. |
| Frankfurter API | External source for published reference currency rates, demonstrating an HTTP tool. |
| Actuator | Health endpoint for checking the running application. |
| JUnit / Mockito / AssertJ | Test runner, controlled substitutes for dependencies, and readable assertions. |
| GitHub Actions | Runs verification when pushed to GitHub. Nothing has been published automatically. |

## File-by-file changes

All Java paths below are under src/main/java/com/projectAI/aiassistant.

### controller/AiController.java — HTTP entry point

Uses small request records for JSON input and keeps all behavior in services.
Ingestion returns the actual chunk count, while /ask returns an Answer record with
text and evidence. The controller remains a thin HTTP boundary.

### rag/DocumentIngestionService.java — preparing searchable information

For text: validate size and content, add source metadata, split into roughly
500-token chunks, call vectorStore.add. That last operation calls the embedding
provider and writes vectors plus text into PostgreSQL.

For PDF: check size, type and PDF header, extract text per page, preserve reader
metadata, attach source, split and add. Reject empty extraction, which often means
a scanned PDF. The header check catches obvious mislabeled files, but is not a
complete file-security system. File-name/type checks alone are insufficient.

A token is a model's text unit, not a word or character. Splitting keeps retrieval
focused and model input manageable. The previous LangChain4j chunk size used
characters; matching the number 500 does not preserve identical chunk behavior.

Repeated uploads are not deduplicated. A later version could track document IDs,
content hashes, ingestion status and chunk ownership. Do not claim this version
implements those features.

### rag/KnowledgeSearchService.java — shared retrieval

Validates the question and asks VectorStore for up to four hits above 0.70 similarity.
The store embeds the question and searches PostgreSQL. This is shared by direct
RAG and the agent/MCP tool, avoiding different search logic in different endpoints.

Source records use real IDs and excerpts from retrieved documents. A similarity
score is not a probability that an answer is correct. Tune it using actual questions.

### rag/RagService.java — direct RAG

Always retrieves first. If no chunks qualify, it returns a fixed fallback without
a chat-model call (embedding search still happened). Otherwise it labels chunks
with IDs, sends the question/evidence and asks for citations.

The system instructions are kept separate from the supplied evidence. The prompt
says document instructions are untrusted. This is a useful boundary, not a proof
against prompt injection. Sources in the response show what was retrieved; they
do not automatically validate model claims or citation accuracy.

### agent/AgentService.java — model-selected actions

Instead of always searching, the model receives tool descriptions and chooses
a tool. Spring AI executes the Java method, passes the result back, and repeats
until a final answer. We provide only read-only tools, with call limits in YAML.

The Java application executes tools; the model never receives database credentials.
Requests are stateless. There is no cross-user chat memory or autonomous background
task. A tool loop is sufficient for this project's agent requirement.

### tools/AssistantTools.java — reusable capabilities

Two @Tool methods: search_knowledge_base and get_exchange_rate.
Names/descriptions and @ToolParam annotations tell the model/client what inputs
each tool expects. Both agent and MCP use the same methods.

### tools/ExchangeRateService.java — external API boundary

Accepts only three-letter currency codes and sends them to a fixed host.
A user cannot turn this into an arbitrary URL fetch. Sets connection/read timeouts,
checks for an actual returned rate, and includes its publication date.
The latest published reference rate may be from the previous business day.

### config/McpConfiguration.java — MCP registration

Turns @Tool methods into ToolCallbacks. The MCP starter publishes them at /mcp.
There is no duplicated database code. A remote client can discover the tool schemas
and invoke them without calling /api/ai/ask.

### config/ApiKeyFilter.java — optional shared-secret authentication

When ASSISTANT_API_KEY is set, REST and MCP require X-API-Key. Uses constant-time
byte comparison. No key is needed for the local learning workflow.
The app binds to loopback by default. A shared key is not multi-user authorization;
all authenticated clients access the same document collection.

### controller/ApiExceptionHandler.java and rag/Inputs.java

Return 400 for bad inputs, 413 for oversized multipart uploads, and a generic 503
for otherwise unhandled failures. Provider payloads and secrets are not returned.
The generic fallback is intentionally basic; refine error categories and operational
logging before a public deployment.

### application.yml and pom.xml

The MCP server, Gemini model adapters, pgvector store, and Actuator are configured
through Spring Boot. The BOM keeps Spring AI module versions aligned. Database
credentials come from environment variables, while upload and tool-call limits live
in one configuration file.

The `rag_embeddings` table stores document text, metadata, and 1,536-dimensional
Gemini embeddings. Startup initializes the table when necessary and does not delete
existing data.

### Tests, README and CI

Unit tests verify source mapping, empty retrieval, ingestion metadata, invalid PDF
rejection and a real Spring AI tool loop with a scripted model response.
Application tests replace external dependencies. HTTP tests use a real embedded
server for REST validation, authentication and MCP session/discovery/tool invocation.
CI runs Maven verify; it does not upload documents or call a model provider.

## Common problems

1. **Wrong database user or port.** psql defaults may use a Unix socket while Java
   uses TCP. Test the exact URL/host/user used by the application. A leftover Docker
   environment variable can silently send Spring to port 5433.
2. **pgvector installed for the wrong PostgreSQL.** vector.control must live in the
   running server's extension directory. PG_CONFIG selects the target at build time.
3. **Framework code from different versions.** Earlier sample code used getContent;
   the actual jar uses getText. Compile small increments and check real signatures.
4. **Hidden configuration mistakes.** Unknown YAML properties can be ignored.
   We checked installed Spring AI metadata for the actual model/limit property names.
5. **A passing mock test is not a live deployment test.** Tests prove our routing
   and contracts, not provider credentials, billing, database schema or answer quality.
6. **A model ignores evidence.** Inspect retrieved excerpts before rewriting prompts.
   Too-strict thresholds, poor PDF extraction or unsuitable chunks can cause failure.
7. **Tool calling seems magical.** Inspect the tool name, arguments, output and second
   model response. The agent test demonstrates this two-response sequence explicitly.
8. **PDF parsing succeeds but text is empty.** Image-only PDFs need OCR. File extension
   checks cannot determine whether useful text exists.
9. **Costs and timeouts.** Uploads call embeddings; questions call embeddings plus chat.
   Agent turns may call the model several times. Limits help, but quotas are still needed.
10. **Mocks do not match framework behavior.** A ChatModel test double must provide
    the options expected by Spring AI. Compile and run behavior tests after changing
    framework integrations.

## A sensible learning sequence

1. Run ./mvnw test without any credentials and inspect one test.
2. Install/enable pgvector, set DB_USER and GEMINI_API_KEY, and start the app.
3. Upload one sentence and inspect `rag_embeddings` using psql.
4. Ask a related question and compare answer/sources with stored text.
5. Ask an unrelated question and observe the fallback.
6. Try the agent exchange-rate request; compare it with direct RAG.
7. Connect an MCP client and discover the same tools.
8. Change one setting (chunk size or threshold), re-index test documents and measure
   the effect. Do not change everything at once.

Before calling this production-ready, add per-user authorization, migrations,
ingestion lifecycle/deduplication, rate limits, dependency scanning, evaluations,
backups and deployment monitoring. These are explicit next-stage work.
