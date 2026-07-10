package com.docusphere.backend.document.util;

import org.springframework.http.MediaType;

public final class DocumentMediaTypes {

    private DocumentMediaTypes() {
    }

    public static MediaType resolveContentType(String fileName, String fallbackType) {
        String extension = extractExtension(fileName);
        if (extension == null && fallbackType != null && !fallbackType.isBlank()) {
            extension = fallbackType.toLowerCase().replace(".", "");
        }
        if (extension == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        return switch (extension) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "doc" -> MediaType.parseMediaType("application/msword");
            case "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "xls" -> MediaType.parseMediaType("application/vnd.ms-excel");
            case "pptx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation");
            case "ppt" -> MediaType.parseMediaType("application/vnd.ms-powerpoint");
            case "txt" -> MediaType.TEXT_PLAIN;
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "csv" -> MediaType.parseMediaType("text/csv");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private static String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }
}
