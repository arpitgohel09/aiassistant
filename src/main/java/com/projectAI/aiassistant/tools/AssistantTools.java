package com.projectAI.aiassistant.tools;

import java.util.List;
import com.projectAI.aiassistant.rag.KnowledgeSearchService;
import com.projectAI.aiassistant.rag.KnowledgeSearchService.Source;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AssistantTools {

    private final KnowledgeSearchService searchService;

    private final ExchangeRateService exchangeRateService;

    @Tool(name = "search_knowledge_base", description = "Search uploaded documents for evidence. Returns source names, chunk IDs and excerpts.")
    public List<Source> searchKnowledgeBase(@ToolParam(description = "Specific question to search for") String query) {
        return searchService.search(query).stream().map(Source::from).toList();
    }

    @Tool(name = "get_exchange_rate", description = "Get the latest published reference exchange rate from Frankfurter, including its date. Not a live trading quote.")
    public ExchangeRateService.Rate getExchangeRate(
        @ToolParam(description = "Three-letter base currency, e.g. USD") String base,
        @ToolParam(description = "Three-letter target currency, e.g. INR") String target) {
        return exchangeRateService.rate(base, target);
    }
}
