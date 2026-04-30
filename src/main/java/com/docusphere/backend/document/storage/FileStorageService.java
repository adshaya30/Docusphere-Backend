package com.docusphere.backend.document.storage;

import java.io.File;

/**
 * Unified file storage service interface.
 * All Supabase operations (upload, download, copy, delete) go through here.
 */
public interface FileStorageService {

    /**
     * Copy a file from source to target path in storage
     */
    String copyFile(String sourcePath, String targetPath);

    /**
     * Delete a file from storage
     */
    void deleteFile(String path);

    /**
     * Load file bytes from storage
     */
    byte[] loadFile(String path);

    /**
     * Upload file directly from File object
     */
    String uploadFile(File file, String storageKey);

    /**
     * Get public URL for a file in storage
     */
    String getPublicUrl(String storageKey);
}
