package com.projectAI.aiassistant.rag;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentIngestionServiceTest {
    @Test void extractsRealPdfTextAndRetainsSource() throws Exception {
        byte[] bytes;
        try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument();
             var output = new java.io.ByteArrayOutputStream()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage();
            pdf.addPage(page);
            try (var content = new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                    org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 600);
                content.showText("Employees receive twenty days of annual leave.");
                content.endText();
            }
            pdf.save(output);
            bytes = output.toByteArray();
        }
        var store = mock(VectorStore.class);
        var file = new MockMultipartFile("file", "handbook.pdf", "application/pdf", bytes);
        assertThat(new DocumentIngestionService(store).ingestPdf(file)).isEqualTo(1);
        ArgumentCaptor<List<Document>> chunks = ArgumentCaptor.forClass(List.class);
        verify(store).add(chunks.capture());
        assertThat(chunks.getValue().getFirst().getText().replaceAll("\\s+", " ")).contains("twenty days");
        assertThat(chunks.getValue().getFirst().getMetadata()).containsEntry("source", "handbook.pdf");
    }
    @Test void textStoresMetadataAndReportsCount() {
        var store = mock(VectorStore.class);
        int count = new DocumentIngestionService(store).ingestText("Employees receive twenty days annual leave.", "handbook");
        ArgumentCaptor<List<Document>> chunks = ArgumentCaptor.forClass(List.class);
        verify(store).add(chunks.capture());
        assertThat(chunks.getValue()).hasSize(count);
        assertThat(chunks.getValue().getFirst().getMetadata()).containsEntry("source", "handbook");
    }
    @Test void rejectsDisguisedPdfBeforeIndexing() {
        var store = mock(VectorStore.class);
        var fake = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a PDF".getBytes());
        assertThatThrownBy(() -> new DocumentIngestionService(store).ingestPdf(fake)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }
    @Test void rejectsBlankText() {
        var store = mock(VectorStore.class);
        assertThatThrownBy(() -> new DocumentIngestionService(store).ingestText(" ", "test")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }
}
