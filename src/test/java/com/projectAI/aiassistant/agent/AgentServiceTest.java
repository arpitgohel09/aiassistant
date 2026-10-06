package com.projectAI.aiassistant.agent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import com.projectAI.aiassistant.rag.KnowledgeSearchService;
import com.projectAI.aiassistant.tools.AssistantTools;
import com.projectAI.aiassistant.tools.ExchangeRateService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentServiceTest {
    @Test void executesModelRequestedSearchAndReturnsFinalAnswer() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(org.springframework.ai.model.tool.ToolCallingChatOptions.builder().build());
        var store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        var call = new AssistantMessage.ToolCall("call-1", "function", "search_knowledge_base", "{\"query\":\"leave policy\"}");
        var toolRequest = AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
        when(model.call(any(Prompt.class))).thenReturn(
            new ChatResponse(List.of(new Generation(toolRequest))),
            new ChatResponse(List.of(new Generation(new AssistantMessage("No evidence found.")))));
        var tools = new AssistantTools(new KnowledgeSearchService(store), new ExchangeRateService());
        assertThat(new AgentService(ChatClient.builder(model), tools).ask("What is our leave policy?")).isEqualTo("No evidence found.");
        verify(store).similaritySearch(any(SearchRequest.class));
        verify(model, times(2)).call(any(Prompt.class));
    }
}
