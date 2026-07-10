package com.docusphere.backend.ocr.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class GoogleVisionIsolationTest {

    @Autowired
    private GoogleVisionService googleVisionService;

    @Test
    public void testRealGoogleVision_LocalFile() {
        System.out.println("====== STARTING REAL GOOGLE VISION LOCAL ISOLATION TEST ======");
        try {
            String filePath = "sample_document.png";
            java.io.File file = new java.io.File(filePath);
            System.out.println("Testing with file: " + file.getAbsolutePath() + " (Exists: " + file.exists() + ", Size: " + file.length() + " bytes)");
            
            assertTrue(file.exists(), "Local test file sample_document.png should exist");
            
            String result = googleVisionService.extractText(file.getAbsolutePath());
            System.out.println("====== ISOLATION TEST RESULT ======");
            System.out.println("Extracted Text:\n" + result);
            System.out.println("===================================");
            
            assertNotNull(result);
        } catch (Exception e) {
            System.out.println("====== ISOLATION TEST FAILED ======");
            e.printStackTrace();
            fail("Google Vision isolation test failed: " + e.getMessage());
        }
    }

    @Test
    public void testRealGoogleVision_PdfFile() {
        System.out.println("====== STARTING REAL GOOGLE VISION PDF ISOLATION TEST ======");
        String pdfPath = "test_doc.pdf";
        try {
            // Create a simple PDF using PDFBox
            try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
                doc.addPage(page);
                
                // Add some text to the page
                try (org.apache.pdfbox.pdmodel.PDPageContentStream contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    contentStream.beginText();
                    contentStream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                    contentStream.newLineAtOffset(100, 700);
                    contentStream.showText("Hello Docusphere OCR PDF Test");
                    contentStream.endText();
                }
                
                doc.save(pdfPath);
            }
            
            java.io.File file = new java.io.File(pdfPath);
            System.out.println("Testing PDF with file: " + file.getAbsolutePath() + " (Exists: " + file.exists() + ", Size: " + file.length() + " bytes)");
            
            String result = googleVisionService.extractText(file.getAbsolutePath());
            System.out.println("====== PDF ISOLATION TEST RESULT ======");
            System.out.println("Extracted Text:\n" + result);
            System.out.println("=======================================");
            
            assertNotNull(result);
        } catch (Exception e) {
            System.out.println("====== PDF ISOLATION TEST FAILED ======");
            e.printStackTrace();
            fail("Google Vision PDF isolation test failed: " + e.getMessage());
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(pdfPath));
            } catch (Exception ignored) {}
        }
    }
}

