package com.docusphere.backend.ocr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import org.springframework.context.annotation.Lazy;

@Component
@Lazy
@Slf4j
public class TextCleanupUtil {

    private static final int MAX_CHAR_LIMIT = 12000; // Safe limit (~3000 tokens)

    public String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        log.info("Original OCR text length: {} characters", text.length());

        // 1. Trim leading/trailing spaces
        String cleaned = text.trim();

        // 2. Remove duplicate spaces (replace multiple spaces with a single space)
        cleaned = cleaned.replaceAll("[ \\t]+", " ");

        // 3. Trim excessive newlines (replace 3 or more consecutive newlines with exactly two newlines)
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");

        // 4. Merge broken lines within paragraphs (replace single newlines with spaces)
        String[] paragraphs = cleaned.split("\\n\\n");
        StringBuilder mergedText = new StringBuilder();
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i];
            String mergedParagraph = paragraph.replace("\n", " ").trim();
            mergedParagraph = mergedParagraph.replaceAll("[ \\t]+", " ");
            mergedText.append(mergedParagraph);
            if (i < paragraphs.length - 1) {
                mergedText.append("\n\n");
            }
        }
        cleaned = mergedText.toString().trim();

        // 5. Limit token size safely (truncation with warning)
        if (cleaned.length() > MAX_CHAR_LIMIT) {
            log.warn("Extracted text length ({} chars) exceeds safety limit ({} chars). Truncating.", cleaned.length(), MAX_CHAR_LIMIT);
            cleaned = cleaned.substring(0, MAX_CHAR_LIMIT) + "\n... [TRUNCATED DUE TO SIZE SAFETY LIMIT]";
        }

        log.info("Cleaned OCR text length: {} characters", cleaned.length());
        return cleaned;
    }
}
