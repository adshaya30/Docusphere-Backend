package com.docusphere.backend.upload.constant;

import java.util.Set;

public final class UploadConstant {

    private UploadConstant() {}

    // ---------------- STORAGE ----------------
    public static final String TEMP_FOLDER = "temp";
    public static final String CHUNK_PREFIX = "chunk_";
    public static final String DEFAULT_UPLOAD_DIR = "uploads";

    // ---------------- VALIDATION ----------------
    public static final int MIN_CHUNK_INDEX = 0;
    public static final int MIN_TOTAL_CHUNKS = 1;

    // Max file size = 50MB
    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    // Allowed file types
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );
}