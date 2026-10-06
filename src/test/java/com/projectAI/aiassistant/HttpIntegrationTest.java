package com.projectAI.aiassistant;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"assistant.api-key=test-secret", "management.health.db.enabled=false",
        "spring.ai.google.genai.api-key=test-key",
        "spring.ai.google.genai.embedding.api-key=test-key"})
class HttpIntegrationTest {
    @MockitoBean VectorStore vectorStore;
    @MockitoBean ChatModel chatModel;
    @MockitoBean EmbeddingModel embeddingModel;
    @Value("${local.server.port}") int port;
    final HttpClient http = HttpClient.newHttpClient();

    HttpResponse<String> post(String path, String body, boolean authenticated, String session) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (authenticated) builder.header("X-API-Key", "test-secret");
        if (session != null) builder.header("Mcp-Session-Id", session);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void authenticationAndValidation() throws Exception {
        assertThat(post("/api/ai/ask", "{\"question\":\"leave?\"}", false, null).statusCode()).isEqualTo(401);
        assertThat(post("/api/ai/ask", "{\"question\":\" \"}", true, null).statusCode()).isEqualTo(400);
        assertThat(post("/api/ai/ask", "broken-json", true, null).statusCode()).isEqualTo(400);
        assertThat(post("/mcp", "{}", false, null).statusCode()).isEqualTo(401);
    }

    @Test void restIngestionAndNoEvidenceAnswer() throws Exception {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        var ingest = post("/api/ai/ingest",
            "{\"content\":\"Employees get twenty days of annual leave.\",\"source\":\"handbook\"}", true, null);
        assertThat(ingest.statusCode()).isEqualTo(200);
        assertThat(ingest.body()).contains("chunksIndexed");
        var answer = post("/api/ai/ask", "{\"question\":\"Unknown policy?\"}", true, null);
        assertThat(answer.statusCode()).isEqualTo(200);
        assertThat(answer.body()).contains("I don't have that information.", "\"sources\":[]");
    }

    @Test void mcpInitializesListsAndCallsSearchTool() throws Exception {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        var init = post("/mcp", """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
            "protocolVersion":"2025-03-26","capabilities":{},
            "clientInfo":{"name":"integration-test","version":"1.0"}}}
            """, true, null);
        assertThat(init.statusCode()).isEqualTo(200);
        String session = init.headers().firstValue("Mcp-Session-Id").orElseThrow();
        post("/mcp", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", true, session);
        var listing = post("/mcp", "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", true, session);
        assertThat(listing.statusCode()).isEqualTo(200);
        assertThat(listing.body()).contains("search_knowledge_base", "get_exchange_rate");
        var result = post("/mcp", """
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
            "name":"search_knowledge_base","arguments":{"query":"leave"}}}
            """, true, session);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).contains("result").doesNotContain("\"isError\":true");
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }
}
