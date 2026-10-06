package com.projectAI.aiassistant.rag;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentIngestionService {

	private final VectorStore vectorStore;
	private final TokenTextSplitter splitter = TokenTextSplitter.builder()
		.withChunkSize(500)
		.withMinChunkSizeChars(200)
		.build();

	public int ingestText(String content, String source) {
		Inputs.text(content, "content", 200000);
		if (source != null && source.length() > 255) throw new IllegalArgumentException("source exceeds 255 characters");
		if (!StringUtils.hasText(content)) {
			throw new IllegalArgumentException("content must not be blank");
		}

		String documentSource = StringUtils.hasText(source) ? source : "manual-text";
		Document document = new Document(content, Map.of("source", documentSource));
		List<Document> chunks = this.splitter.apply(List.of(document));
		if (chunks.isEmpty()) throw new IllegalArgumentException("No text could be indexed");
		this.vectorStore.add(chunks);
		log.info("Ingested {} chunks from source: {}", chunks.size(), documentSource);
		return chunks.size();
	}

	public int ingestPdf(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("file must not be empty");
		}
		if (!isPdf(file)) {
			throw new IllegalArgumentException("file must be a PDF");
		}
		if (file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("Maximum PDF size is 10 MB");
		byte[] bytes = file.getBytes();
		if (bytes.length < 5 || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
			throw new IllegalArgumentException("File does not contain a PDF header");

		PagePdfDocumentReader reader = new PagePdfDocumentReader(
			new ByteArrayResource(bytes),
			PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build());
		String source = StringUtils.hasText(file.getOriginalFilename())
			? file.getOriginalFilename()
			: "uploaded.pdf";
		List<Document> documents;
		try {
			documents = reader.read().stream().map(document -> withSource(document, source)).toList();
		}
		catch (RuntimeException ex) {
			throw new IllegalArgumentException("PDF could not be read; check that it is valid and not encrypted", ex);
		}
		long textLength = documents.stream().mapToLong(doc -> doc.getText().length()).sum();
		if (textLength > 200000) throw new IllegalArgumentException("Extracted PDF text exceeds 200000 characters");
		List<Document> chunks = this.splitter.apply(documents);
		if (chunks.isEmpty()) throw new IllegalArgumentException("PDF contains no extractable text; scanned PDFs require OCR");
		this.vectorStore.add(chunks);
		log.info("Ingested {} chunks from PDF: {}", chunks.size(), source);
		return chunks.size();
	}

	private Document withSource(Document document, String source) {
		Map<String, Object> metadata = new HashMap<>(document.getMetadata());
		metadata.put("source", source);
		return new Document(document.getText(), metadata);
	}

	private boolean isPdf(MultipartFile file) {
		String contentType = file.getContentType();
		String filename = file.getOriginalFilename();
		return "application/pdf".equalsIgnoreCase(contentType)
			|| (filename != null && filename.toLowerCase().endsWith(".pdf"));
	}
}
