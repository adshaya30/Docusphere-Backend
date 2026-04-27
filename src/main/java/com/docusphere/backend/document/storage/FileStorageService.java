package com.docusphere.backend.document.storage;

public interface FileStorageService {

    String copyFile(String sourcePath, String targetPath);

    void deleteFile(String path);

    byte[] loadFile(String path);
}
