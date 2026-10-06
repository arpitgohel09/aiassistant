package com.projectAI.aiassistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

@SpringBootTest(properties = {
    "spring.ai.google.genai.api-key=test-key",
    "spring.ai.google.genai.embedding.api-key=test-key"
})
class AiassistantApplicationTests {
    @MockitoBean VectorStore vectorStore;
    @MockitoBean ChatModel chatModel;
    @MockitoBean EmbeddingModel embeddingModel;

	@Test
	void contextLoads() {
	}

}
