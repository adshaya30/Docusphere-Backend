package com.docusphere.backend.ocr.service;

import com.docusphere.backend.ocr.dto.AiAnalysisOutcome;
import com.docusphere.backend.ocr.dto.OcrResponse;
import com.docusphere.backend.ocr.entity.OcrDocument;
import com.docusphere.backend.ocr.repository.OcrRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OcrServiceTest {

    @Mock
    private OcrRepository ocrRepository;
    @Mock
    private GoogleVisionService googleVisionService;
    @Mock
    private GeminiService geminiService;
    @Mock
    private TextCleanupUtil textCleanupUtil;
    @Mock
    private OcrJobManager ocrJobManager;
    @Mock
    private OcrUploadService ocrUploadService;

    @InjectMocks
    private OcrService ocrService;

    private String rawText;
    private String cleanedText;
    private AiAnalysisOutcome mockOutcome;

    @BeforeEach
    public void setUp() {
        rawText = "This  is a   raw text.\nLine 2";
        cleanedText = "This is a raw text. Line 2";
        mockOutcome = AiAnalysisOutcome.builder()
                .summary("Mock summary")
                .keyPoints(List.of("Mock point 1"))
                .tags(List.of("MockTag"))
                .build();
    }

    @Test
    public void testProcessDocument_Success() throws IOException {
        byte[] pngHeader = new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngHeader);

        when(googleVisionService.extractText(anyString())).thenReturn(rawText);
        when(textCleanupUtil.cleanText(rawText)).thenReturn(cleanedText);
        when(geminiService.summarizeText(cleanedText)).thenReturn(mockOutcome);

        OcrResponse response = ocrService.processDocument(file);

        assertNotNull(response);
        assertEquals("test.png", response.getTitle());
        assertEquals("Mock summary", response.getSummary());
        assertEquals(cleanedText, response.getExtractedText());
        assertEquals(mockOutcome.getTags(), response.getTags());

        verify(googleVisionService).extractText(anyString());
        verify(textCleanupUtil).cleanText(rawText);
        verify(geminiService).summarizeText(cleanedText);

        // Verify save to repository was skipped
        verify(ocrRepository, never()).save(any(OcrDocument.class));
    }

    @Test
    public void testProcessDocumentAsync_Success() throws IOException {
        byte[] pngHeader = new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngHeader);
        String jobId = "job-123";

        when(googleVisionService.extractText(anyString())).thenReturn(rawText);
        when(textCleanupUtil.cleanText(rawText)).thenReturn(cleanedText);
        when(geminiService.summarizeText(cleanedText)).thenReturn(mockOutcome);

        ocrService.processDocumentAsync(file, jobId);

        verify(ocrJobManager).updateJob(jobId, "EXTRACTING", 30);
        verify(ocrJobManager).updateJob(jobId, "EXTRACTING", 50);
        verify(ocrJobManager).updateJobWithPartial(jobId, "SUMMARIZING", 80, cleanedText);

        ArgumentCaptor<OcrResponse> responseCaptor = ArgumentCaptor.forClass(OcrResponse.class);
        verify(ocrJobManager).completeJob(eq(jobId), responseCaptor.capture());

        OcrResponse response = responseCaptor.getValue();
        assertEquals("test.png", response.getTitle());
        assertEquals("Mock summary", response.getSummary());
        assertEquals(cleanedText, response.getExtractedText());

        verify(ocrRepository, never()).save(any(OcrDocument.class));
    }

    @Test
    public void testProcessFileAsync_Success() throws IOException {
        Path tempPath = Files.createTempFile("test_merged_", ".png");
        byte[] pngHeader = new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, 0, 0, 0, 0};
        Files.write(tempPath, pngHeader);
        File tempFile = tempPath.toFile();
        String jobId = "job-456";
        String sessionId = "session-789";

        when(googleVisionService.extractText(tempFile.getAbsolutePath())).thenReturn(rawText);
        when(textCleanupUtil.cleanText(rawText)).thenReturn(cleanedText);
        when(geminiService.summarizeText(cleanedText)).thenReturn(mockOutcome);

        ocrService.processFileAsync(tempFile, jobId, sessionId);

        verify(ocrJobManager).updateJob(jobId, "EXTRACTING", 30);
        verify(ocrJobManager).updateJobWithPartial(jobId, "SUMMARIZING", 70, cleanedText);
        verify(ocrJobManager).completeJob(eq(jobId), any(OcrResponse.class));
        verify(ocrUploadService).cleanup(sessionId);
        verify(ocrRepository, never()).save(any(OcrDocument.class));

        // Clean up mock temp file
        Files.deleteIfExists(tempPath);
    }
}
