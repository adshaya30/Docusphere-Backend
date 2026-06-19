package com.docusphere.backend.ocr.service;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import org.springframework.context.annotation.Lazy;
import org.threeten.bp.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@Lazy
@Slf4j
public class GoogleVisionService {

    @Value("${google.vision.credentials.path:classpath:docusphere-ocr-7af6c7033da4.json}")
    private String credentialsPath;

    @Value("${google.vision.timeout.connection:15000}")
    private int connectionTimeout;

    @Value("${google.vision.timeout.read:15000}")
    private int readTimeout;

    @Value("${google.vision.bucket:docusphere-ocr-storage}")
    private String gcsBucketName;

    private ImageAnnotatorSettings settings;
    private com.google.cloud.storage.Storage storageClient;

    @PostConstruct
    public void init() {
        try {
            GoogleCredentials credentials;
            if (credentialsPath.startsWith("classpath:")) {
                String resourceName = credentialsPath.substring("classpath:".length());
                try (InputStream is = new ClassPathResource(resourceName).getInputStream()) {
                    credentials = GoogleCredentials.fromStream(is);
                    log.info("Successfully loaded Google credentials from classpath: {}", resourceName);
                }
            } else {
                try (InputStream is = Files.newInputStream(Paths.get(credentialsPath))) {
                    credentials = GoogleCredentials.fromStream(is);
                    log.info("Successfully loaded Google credentials from file path: {}", credentialsPath);
                }
            }

            ImageAnnotatorSettings.Builder builder = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials));

            // Set timeouts on stub settings
            builder.batchAnnotateImagesSettings().getRetrySettings().toBuilder()
                    .setInitialRpcTimeout(Duration.ofMillis(connectionTimeout))
                    .setMaxRpcTimeout(Duration.ofMillis(readTimeout))
                    .setTotalTimeout(Duration.ofMillis(readTimeout))
                    .build();

            this.settings = builder.build();
            log.info("Google Vision settings initialized successfully.");

            // Initialize GCS Storage client
            this.storageClient = com.google.cloud.storage.StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .build()
                    .getService();
            log.info("Google Cloud Storage client initialized successfully.");
        } catch (Exception e) {
            log.warn("Failed to load credentials from {} (falling back to application default credentials): {}", credentialsPath, e.getMessage());
            try {
                ImageAnnotatorSettings.Builder builder = ImageAnnotatorSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(GoogleCredentials.getApplicationDefault()));

                builder.batchAnnotateImagesSettings().getRetrySettings().toBuilder()
                        .setInitialRpcTimeout(Duration.ofMillis(connectionTimeout))
                        .setMaxRpcTimeout(Duration.ofMillis(readTimeout))
                        .setTotalTimeout(Duration.ofMillis(readTimeout))
                        .build();

                this.settings = builder.build();
                log.info("Google Vision settings initialized with application default credentials.");

                this.storageClient = com.google.cloud.storage.StorageOptions.getDefaultInstance().getService();
                log.info("Google Cloud Storage client initialized with application default credentials.");
            } catch (Exception ex) {
                log.error("Failed to initialize Google Vision settings using application default credentials: {}", ex.getMessage(), ex);
            }
        }
    }

    private boolean isPdfFile(java.io.File file) {
        if (file == null || !file.exists() || file.length() < 4) {
            return false;
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = fis.read(header);
            if (read == 4) {
                return (header[0] & 0xFF) == 0x25 && // %
                       (header[1] & 0xFF) == 0x50 && // P
                       (header[2] & 0xFF) == 0x44 && // D
                       (header[3] & 0xFF) == 0x46;   // F
            }
        } catch (Exception e) {
            log.warn("Failed to check magic bytes for file: {}", file.getName(), e);
        }
        return false;
    }

    public String extractText(String filePath) {
        log.info("Google Vision OCR started for file: {}", filePath);
        long startTime = System.currentTimeMillis();

        java.io.File file = new java.io.File(filePath);
        String fileType = "";
        long fileSize = 0;
        String mimeType = "unknown";
        boolean isPdf = false;
        
        if (file.exists()) {
            fileSize = file.length();
            int dotIndex = file.getName().lastIndexOf('.');
            if (dotIndex > 0) {
                fileType = file.getName().substring(dotIndex + 1);
            }
            try {
                mimeType = java.nio.file.Files.probeContentType(file.toPath());
            } catch (Exception e) {
                mimeType = "unknown-error";
            }
            // Sniff magic bytes for PDF format
            isPdf = isPdfFile(file);
        }

        log.info("=== OCR REQUEST DETAIL LOG ===");
        log.info("File Path: {}", filePath);
        log.info("File Type/Extension: {}", fileType);
        log.info("Mime Type: {}", mimeType);
        log.info("File Size: {} bytes", fileSize);
        log.info("Is PDF (Magic Bytes): {}", isPdf);
        log.info("Bucket Name: {}", gcsBucketName);
        log.info("==============================");

        System.out.println("=== OCR REQUEST DETAIL LOG ===");
        System.out.println("File Path: " + filePath);
        System.out.println("File Type/Extension: " + fileType);
        System.out.println("Mime Type: " + mimeType);
        System.out.println("File Size: " + fileSize + " bytes");
        System.out.println("Is PDF (Magic Bytes): " + isPdf);
        System.out.println("Bucket Name: " + gcsBucketName);
        System.out.println("==============================");

        try {
            if (isPdf) {
                try {
                    log.info("Attempting native GCS Async OCR for PDF: {}", filePath);
                    System.out.println("Attempting native GCS Async OCR for PDF: " + filePath);
                    return processPdfWithGcsAndVision(filePath, startTime);
                } catch (Exception gcsEx) {
                    log.warn("Native GCS Async OCR failed, falling back to local PDFBox rendering: {}", gcsEx.getMessage(), gcsEx);
                    System.err.println("Native GCS Async OCR failed, falling back to local PDFBox rendering: " + gcsEx.getMessage());
                    return extractTextFromPdf(filePath, startTime);
                }
            } else {
                java.io.File imgFile = new java.io.File(filePath);
                return extractTextFromImage(imgFile, startTime);
            }
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            String stackTrace = sw.toString();

            log.error("Google Vision OCR failed. Full Exception Stack Trace:\n{}", stackTrace);
            System.err.println("Google Vision OCR failed. Full Exception Stack Trace:\n" + stackTrace);

            throw new RuntimeException("Google Vision OCR failed: " + e.getMessage(), e);
        }
    }

    private String processPdfWithGcsAndVision(String filePath, long startTime) throws Exception {
        java.io.File file = new java.io.File(filePath);
        if (storageClient == null) {
            throw new IllegalStateException("Google Cloud Storage client is not initialized.");
        }
        
        String gcsFileName = "ocr-inputs/" + System.currentTimeMillis() + "_" + file.getName();
        String gcsInputUri = "gs://" + gcsBucketName + "/" + gcsFileName;
        String gcsOutputDir = "ocr-outputs/" + System.currentTimeMillis() + "/";
        String gcsOutputUri = "gs://" + gcsBucketName + "/" + gcsOutputDir;

        log.info("Uploading PDF to GCS: {}/{}", gcsBucketName, gcsFileName);
        System.out.println("Uploading PDF to GCS: " + gcsBucketName + "/" + gcsFileName);
        
        com.google.cloud.storage.BlobId blobId = com.google.cloud.storage.BlobId.of(gcsBucketName, gcsFileName);
        com.google.cloud.storage.BlobInfo blobInfo = com.google.cloud.storage.BlobInfo.newBuilder(blobId)
                .setContentType("application/pdf")
                .build();
        
        // Upload bytes to GCS
        storageClient.create(blobInfo, Files.readAllBytes(file.toPath()));
        log.info("Uploaded PDF to GCS successfully. URI: {}", gcsInputUri);
        System.out.println("Uploaded PDF to GCS successfully. URI: " + gcsInputUri);

        try (ImageAnnotatorClient client = settings != null ? ImageAnnotatorClient.create(settings) : ImageAnnotatorClient.create()) {
            GcsSource gcsSource = GcsSource.newBuilder().setUri(gcsInputUri).build();
            InputConfig inputConfig = InputConfig.newBuilder()
                    .setGcsSource(gcsSource)
                    .setMimeType("application/pdf")
                    .build();

            GcsDestination gcsDestination = GcsDestination.newBuilder().setUri(gcsOutputUri).build();
            OutputConfig outputConfig = OutputConfig.newBuilder()
                    .setGcsDestination(gcsDestination)
                    .setBatchSize(8)
                    .build();

            Feature feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
            AsyncAnnotateFileRequest request = AsyncAnnotateFileRequest.newBuilder()
                    .setInputConfig(inputConfig)
                    .addFeatures(feature)
                    .setOutputConfig(outputConfig)
                    .build();

            log.info("Sending asyncBatchAnnotateFiles request for: {}", gcsInputUri);
            System.out.println("Sending asyncBatchAnnotateFiles request for: " + gcsInputUri);
            
            com.google.api.gax.longrunning.OperationFuture<AsyncBatchAnnotateFilesResponse, OperationMetadata> future =
                    client.asyncBatchAnnotateFilesAsync(java.util.Collections.singletonList(request));

            // Wait for completion — 5 minute timeout to prevent infinite thread blocking
            AsyncBatchAnnotateFilesResponse response = future.get(5, java.util.concurrent.TimeUnit.MINUTES);
            log.info("Async batch annotate files completed successfully.");
            System.out.println("Async batch annotate files completed successfully.");

            // Read the generated JSON results from the output GCS prefix
            return readOcrResultsFromGcs(gcsOutputDir, startTime);
        } finally {
            // Cleanup GCS input file
            try {
                storageClient.delete(blobId);
            } catch (Exception e) {
                log.warn("Failed to delete input file from GCS: {}", e.getMessage());
            }
        }
    }

    private String readOcrResultsFromGcs(String outputPrefix, long startTime) throws Exception {
        StringBuilder textBuilder = new StringBuilder();
        com.google.api.gax.paging.Page<com.google.cloud.storage.Blob> blobs = 
                storageClient.list(gcsBucketName, com.google.cloud.storage.Storage.BlobListOption.prefix(outputPrefix));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (com.google.cloud.storage.Blob blob : blobs.iterateAll()) {
            log.info("Reading GCS OCR result blob: {}", blob.getName());
            System.out.println("Reading GCS OCR result blob: " + blob.getName());
            
            byte[] bytes = blob.getContent();
            com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(bytes);
            com.fasterxml.jackson.databind.JsonNode responses = rootNode.path("responses");
            
            if (responses.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode responseNode : responses) {
                    com.fasterxml.jackson.databind.JsonNode fullTextAnnotation = responseNode.path("fullTextAnnotation");
                    String text = fullTextAnnotation.path("text").asText();
                    if (text != null && !text.isEmpty()) {
                        textBuilder.append(text).append("\n");
                    }
                }
            }
            
            // Delete GCS output JSON file to clean up
            try {
                storageClient.delete(blob.getBlobId());
            } catch (Exception e) {
                log.warn("Failed to delete output GCS blob: {}", blob.getName(), e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("GCS Async PDF OCR completed in {} ms. Extracted {} characters.", duration, textBuilder.length());
        System.out.println("GCS Async PDF OCR completed in " + duration + " ms. Extracted " + textBuilder.length() + " characters.");
        return textBuilder.toString();
    }

    private String extractTextFromImage(java.io.File file, long startTime) {
        int attempt = 0;
        Exception lastException = null;

        // Verify image readability BEFORE Vision API call
        try {
            System.out.println("VERIFYING IMAGE READABILITY: " + file.getAbsolutePath());
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file);
            if (img == null) {
                throw new RuntimeException("Image is corrupted or unsupported format (ImageIO.read returned null)");
            }
            log.info("Image readability check passed. Dimensions: {}x{}", img.getWidth(), img.getHeight());
            System.out.println("IMAGE READABILITY PASSED. Dimensions: " + img.getWidth() + "x" + img.getHeight() + ", Size: " + file.length() + " bytes");
        } catch (Exception e) {
            log.error("Image readability verification failed: {}", e.getMessage());
            System.out.println("IMAGE READABILITY FAILED: " + e.getMessage());
            throw new RuntimeException("Image is corrupted: " + e.getMessage(), e);
        }

        while (attempt < 2) {
            attempt++;
            try {
                System.out.println("STAGE: VISION_REQUEST_STARTED - Attempt: " + attempt + ", File: " + file.getName());
                log.info("STAGE: VISION_REQUEST_STARTED - Attempt: {}, File: {}", attempt, file.getName());

                ByteString imgByteString;
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    imgByteString = ByteString.readFrom(fis);
                }

                Image img = Image.newBuilder().setContent(imgByteString).build();
                Feature feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
                AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                        .addFeatures(feat)
                        .setImage(img)
                        .build();

                List<AnnotateImageRequest> requests = new ArrayList<>();
                requests.add(request);

                try (ImageAnnotatorClient client = settings != null ? ImageAnnotatorClient.create(settings) : ImageAnnotatorClient.create()) {
                    log.info("Calling ImageAnnotatorClient.batchAnnotateImages() with requests...");
                    System.out.println("Calling ImageAnnotatorClient.batchAnnotateImages() with requests...");
                    BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
                    log.info("Received BatchAnnotateImagesResponse from Google Vision API");
                    System.out.println("Received BatchAnnotateImagesResponse from Google Vision API");
                    List<AnnotateImageResponse> responses = response.getResponsesList();

                    for (AnnotateImageResponse res : responses) {
                        if (res.hasError()) {
                            log.error("Google Vision API error response: Code={}, Message={}", res.getError().getCode(), res.getError().getMessage());
                            System.err.println("Google Vision API error response: Code=" + res.getError().getCode() + ", Message=" + res.getError().getMessage());
                            throw new RuntimeException(res.getError().getMessage());
                        }
                        if (res.hasFullTextAnnotation()) {
                            long duration = System.currentTimeMillis() - startTime;
                            String text = res.getFullTextAnnotation().getText();
                            log.info("Google Vision OCR image processing completed in {} ms. Extracted {} characters.", duration, text.length());
                            System.out.println("STAGE: VISION_REQUEST_COMPLETED - Extracted " + text.length() + " chars in " + duration + " ms");
                            return text;
                        }
                    }
                }

                // If we reach here, no text was detected.
                log.warn("Google Vision OCR: No text detected in image chunk");
                System.out.println("STAGE: VISION_REQUEST_COMPLETED - No text detected");
                return "";

            } catch (Exception e) {
                lastException = e;
                log.warn("Google Vision OCR attempt {} failed: {}", attempt, e.getMessage(), e);
                System.err.println("Google Vision OCR attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < 2) {
                    log.info("Retrying Google Vision OCR...");
                }
            }
        }
        throw new RuntimeException("Google Vision OCR failed: " + (lastException != null ? lastException.getMessage() : "Unknown error"), lastException);
    }

    private String extractTextFromPdf(String filePath, long startTime) throws Exception {
        System.out.println("STAGE: PDF_RENDER_STARTED - File: " + filePath);
        log.info("STAGE: PDF_RENDER_STARTED - File: {}", filePath);
        
        StringBuilder extractedText = new StringBuilder();
        
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(new java.io.File(filePath))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int numPages = document.getNumberOfPages();
            log.info("PDF page count: {}", numPages);
            System.out.println("PDF Page Count: " + numPages);
            
            for (int page = 0; page < numPages; page++) {
                log.info("Processing PDF page {}/{}", page + 1, numPages);
                System.out.println("Processing PDF page " + (page + 1) + "/" + numPages);
                
                // Render image with 300 DPI for good OCR quality
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
                
                // Save the rendered page image as a PNG file inside the /debug-ocr/ directory
                java.io.File pageFile;
                try {
                    java.nio.file.Path debugDir = java.nio.file.Paths.get("debug-ocr");
                    if (!java.nio.file.Files.exists(debugDir)) {
                        java.nio.file.Files.createDirectories(debugDir);
                    }
                    String baseName = new java.io.File(filePath).getName();
                    pageFile = debugDir.resolve("pdf_page_" + System.currentTimeMillis() + "_page_" + (page + 1) + "_" + baseName + ".png").toFile();
                    javax.imageio.ImageIO.write(bim, "png", pageFile);
                    log.info("Saved PDF page to temp PNG: {}", pageFile.getAbsolutePath());
                    System.out.println("SAVED PDF PAGE PNG: " + pageFile.getAbsolutePath());
                } catch (Exception e) {
                    log.error("Failed to write PDF page to file: {}", e.getMessage());
                    throw new RuntimeException("Failed to render PDF page to PNG: " + e.getMessage(), e);
                }

                // Verify the generated PNG exists, size > 0, and ImageIO.read() succeeds
                if (!pageFile.exists()) {
                    throw new RuntimeException("Generated PDF page file does not exist: " + pageFile.getAbsolutePath());
                }
                if (pageFile.length() <= 0) {
                    throw new RuntimeException("Generated PDF page file is empty: " + pageFile.getAbsolutePath());
                }
                
                // Read and verify
                try {
                    BufferedImage checkImg = javax.imageio.ImageIO.read(pageFile);
                    if (checkImg == null) {
                        throw new RuntimeException("Generated PDF page PNG is corrupted or unreadable (ImageIO.read returned null)");
                    }
                    log.info("PDF page PNG verified successfully. Dimensions: {}x{}, Size: {} bytes", 
                        checkImg.getWidth(), checkImg.getHeight(), pageFile.length());
                    System.out.println("VERIFIED PDF PAGE PNG - Dimensions: " + checkImg.getWidth() + "x" + checkImg.getHeight() + ", Size: " + pageFile.length() + " bytes");
                } catch (Exception e) {
                    throw new RuntimeException("Generated PDF page PNG validation failed: " + e.getMessage(), e);
                }

                // Pass to the image OCR endpoint
                try {
                    String pageText = extractTextFromImage(pageFile, startTime);
                    extractedText.append(pageText).append("\n\n");
                } finally {
                    try {
                        if (pageFile != null && pageFile.exists()) {
                            java.nio.file.Files.delete(pageFile.toPath());
                            log.info("Successfully deleted temp PDF page image: {}", pageFile.getAbsolutePath());
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to delete temp PDF page image {}: {}", pageFile.getAbsolutePath(), ex.getMessage());
                    }
                }
            }
        }
        
        System.out.println("STAGE: PDF_RENDER_COMPLETED - Extracted " + extractedText.length() + " chars total");
        log.info("STAGE: PDF_RENDER_COMPLETED - Extracted {} chars total", extractedText.length());
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Google Vision OCR PDF processing completed in {} ms. Extracted total {} characters.", duration, extractedText.length());
        return extractedText.toString();
    }
}

