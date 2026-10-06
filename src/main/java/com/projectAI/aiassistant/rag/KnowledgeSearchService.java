package com.projectAI.aiassistant.rag;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    private final VectorStore vectorStore;

    public List<Document> search(String question) {
        return vectorStore.similaritySearch(SearchRequest.builder()
            .query(Inputs.text(question, "question", 4000))
            .topK(4)
            .similarityThreshold(0.70)
            .build());
    }

    public record Source(String chunkId, String source, String excerpt) {
        public static Source from(Document doc) {
            String source = String.valueOf(doc.getMetadata().getOrDefault("source", "unknown"));
            return new Source(doc.getId(), source, doc.getText());
        }
    }
}
