package com.projectAI.aiassistant.rag;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagServiceTest {
    @Test void noEvidenceDoesNotCallModel() {
        var store = mock(VectorStore.class);
        var model = mock(ChatModel.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        var service = new RagService(ChatClient.builder(model), new KnowledgeSearchService(store));
        assertThat(service.askQuestion("Leave policy?").sources()).isEmpty();
        verifyNoInteractions(model);
    }
    @Test void answerIncludesActualRetrievedSourceAndContext() {
        var store = mock(VectorStore.class);
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
        var document = new Document("Annual leave is 20 days.", Map.of("source", "handbook"));
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document));
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getContents()).contains("Annual leave is 20 days.");
            return new ChatResponse(List.of(new Generation(new AssistantMessage("20 days"))));
        });
        var result = new RagService(ChatClient.builder(model), new KnowledgeSearchService(store)).askQuestion("Leave?");
        assertThat(result.answer()).isEqualTo("20 days");
        assertThat(result.sources().getFirst().chunkId()).isEqualTo(document.getId());
    }
    @Test void blankSearchDoesNotReachDatabase() {
        var store = mock(VectorStore.class);
        assertThatThrownBy(() -> new KnowledgeSearchService(store).search(" ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }
}
