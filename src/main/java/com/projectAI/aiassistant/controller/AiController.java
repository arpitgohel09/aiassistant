package com.projectAI.aiassistant.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.projectAI.aiassistant.rag.DocumentIngestionService;
import com.projectAI.aiassistant.rag.RagService;
import com.projectAI.aiassistant.agent.AgentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

	private final RagService ragService;
	private final DocumentIngestionService ingestionService;
	private final AgentService agentService;

	@PostMapping("/ask")
	public RagService.Answer ask(@RequestBody QuestionRequest request) {
		return this.ragService.askQuestion(request.question());
	}

	@PostMapping("/agent/ask")
	public Map<String, String> agent(@RequestBody QuestionRequest request) {
		return Map.of("answer", this.agentService.ask(request.question()));
	}

	@PostMapping("/ingest")
	public Map<String, Integer> ingest(@RequestBody IngestRequest request) {
		int count = this.ingestionService.ingestText(request.content(), request.source());
		return Map.of("chunksIndexed", count);
	}

	@PostMapping("/ingest/pdf")
	public Map<String, Integer> ingestPdf(@RequestParam("file") MultipartFile file) throws IOException {
		return Map.of("chunksIndexed", this.ingestionService.ingestPdf(file));
	}

	public record QuestionRequest(String question) {}

	public record IngestRequest(String content, String source) {}
}
