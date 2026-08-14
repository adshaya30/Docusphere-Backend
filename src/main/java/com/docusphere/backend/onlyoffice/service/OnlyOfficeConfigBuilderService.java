package com.docusphere.backend.onlyoffice.service;



import com.docusphere.backend.authentication.entity.User;

import com.docusphere.backend.document.entity.Document;

import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;

import io.jsonwebtoken.Jwts;

import io.jsonwebtoken.security.Keys;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;



import java.util.LinkedHashMap;

import java.util.Locale;

import java.util.Map;

import java.util.UUID;



@Slf4j

@Service

public class OnlyOfficeConfigBuilderService {



    @Value("${app.frontend-url:http://localhost:5173}")

    private String frontendUrl;



    @Value("${app.onlyoffice.callback-url:http://host.docker.internal:8080/api/onlyoffice/callback}")

    private String onlyofficeCallbackUrl;



    @Value("${app.onlyoffice.jwt.secret:V8pX9iu5gDWzQrHP5Od62XOOiuOnlrtF}")

    private String onlyofficeJwtSecret;



    /** Base URL ONLYOFFICE Document Server uses to fetch document bytes (must be reachable from its container). */

    @Value("${app.onlyoffice.document-download-base-url:http://host.docker.internal:8080}")

    private String onlyOfficeDocumentDownloadBaseUrl;



    public OnlyOfficeConfig buildConfig(Document document, User user, boolean canEdit, boolean canView, boolean canDownload, String docKey, String callbackToken) {

        return buildConfig(document, user, canEdit, canView, canDownload, docKey, callbackToken, null, null);

    }



    public OnlyOfficeConfig buildConfig(

            Document document,

            User user,

            boolean canEdit,

            boolean canView,

            boolean canDownload,

            String docKey,

            String callbackToken,

            String downloadToken

    ) {

        return buildConfig(document, user, canEdit, canView, canDownload, docKey, callbackToken, downloadToken, null);

    }



    public OnlyOfficeConfig buildConfig(

            Document document,

            User user,

            boolean canEdit,

            boolean canView,

            boolean canDownload,

            String docKey,

            String callbackToken,

            String downloadToken,

            String shareToken

    ) {

        String fileExtension = getFileExtension(document);

        String callbackUrl = onlyofficeCallbackUrl + "?id=" + document.getId() + "&token=" + callbackToken;



        long lastModified = document.getUpdatedAt() != null

                ? java.sql.Timestamp.valueOf(document.getUpdatedAt()).getTime()

                : (document.getCreatedAt() != null ? java.sql.Timestamp.valueOf(document.getCreatedAt()).getTime() : System.currentTimeMillis());



        String documentUrl = buildDocumentDownloadUrl(document.getId(), shareToken, downloadToken, lastModified);

        log.debug("ONLYOFFICE document.url for {}: {}", document.getId(), documentUrl);



        Map<String, Object> documentClaims = buildDocumentClaims(

                fileExtension,

                docKey,

                document.getName(),

                documentUrl,

                canEdit,

                canView,

                canDownload

        );



        Map<String, Object> editorConfigClaims = buildEditorConfigClaims(

                document,

                user,

                canEdit,

                callbackUrl,

                shareToken

        );



        String documentType = getDocumentType(fileExtension);

        String token = signConfig(documentClaims, editorConfigClaims, documentType);



        OnlyOfficeConfig.DocumentInfo documentInfo = toDocumentInfo(documentClaims);

        OnlyOfficeConfig.EditorConfig editorConfig = toEditorConfig(editorConfigClaims);



        return OnlyOfficeConfig.builder()

                .documentType(documentType)

                .document(documentInfo)

                .editorConfig(editorConfig)

                .width("100%")

                .height("100%")

                .token(token)

                .build();

    }



    public OnlyOfficeConfig buildVersionPreviewConfig(

            Document document,

            UUID versionId,

            String versionTitle,

            String docKey,

            User user

    ) {

        return buildVersionPreviewConfig(document, versionId, versionTitle, docKey, user, null);

    }



    public OnlyOfficeConfig buildVersionPreviewConfig(

            Document document,

            UUID versionId,

            String versionTitle,

            String docKey,

            User user,

            String downloadToken

    ) {

        return buildVersionPreviewConfig(document, versionId, versionTitle, docKey, user, downloadToken, null);

    }



    public OnlyOfficeConfig buildVersionPreviewConfig(

            Document document,

            UUID versionId,

            String versionTitle,

            String docKey,

            User user,

            String downloadToken,

            String shareToken

    ) {

        String fileExtension = getFileExtension(document);

        String callbackUrl = onlyofficeCallbackUrl + "?id=" + document.getId() + "&token=preview_only";

        String documentUrl = buildVersionDownloadUrl(document.getId(), versionId, shareToken, downloadToken);



        Map<String, Object> documentClaims = buildDocumentClaims(

                fileExtension,

                docKey,

                versionTitle,

                documentUrl,

                false,

                false,

                true

        );



        Map<String, Object> editorConfigClaims = buildVersionEditorConfigClaims(document, user, callbackUrl);

        String documentType = getDocumentType(fileExtension);

        String token = signConfig(documentClaims, editorConfigClaims, documentType);



        return OnlyOfficeConfig.builder()

                .documentType(documentType)

                .document(toDocumentInfo(documentClaims))

                .editorConfig(toEditorConfig(editorConfigClaims))

                .width("100%")

                .height("100%")

                .token(token)

                .build();

    }



    private String buildDocumentDownloadUrl(UUID documentId, String shareToken, String downloadToken, long cacheBuster) {

        StringBuilder url = new StringBuilder(normalizeBaseUrl(onlyOfficeDocumentDownloadBaseUrl))

                .append("/api/documents/")

                .append(documentId)

                .append("/download");

        appendShareAndDownloadTokens(url, shareToken, downloadToken, cacheBuster);

        return url.toString();

    }



    private String buildVersionDownloadUrl(UUID documentId, UUID versionId, String shareToken, String downloadToken) {

        StringBuilder url = new StringBuilder(normalizeBaseUrl(onlyOfficeDocumentDownloadBaseUrl))

                .append("/api/documents/")

                .append(documentId)

                .append("/versions/")

                .append(versionId)

                .append("/download");

        appendShareAndDownloadTokens(url, shareToken, downloadToken, System.currentTimeMillis());

        return url.toString();

    }



    /**

     * ONLYOFFICE fetches this URL server-side. Keep query values unencoded so the signed JWT url matches exactly.

     */

    private void appendShareAndDownloadTokens(StringBuilder url, String shareToken, String downloadToken, long cacheBuster) {

        boolean hasParam = false;

        if (shareToken != null && !shareToken.isBlank()) {

            url.append("?token=").append(shareToken.trim());

            hasParam = true;

        }

        if (downloadToken != null && !downloadToken.isBlank()) {

            url.append(hasParam ? "&" : "?").append("dlToken=").append(downloadToken.trim());

            hasParam = true;

        }

        url.append(hasParam ? "&" : "?").append("cb=").append(cacheBuster);

    }



    private Map<String, Object> buildDocumentClaims(

            String fileType,

            String key,

            String title,

            String url,

            boolean canEdit,

            boolean canView,

            boolean canDownload

    ) {

        Map<String, Object> permissions = new LinkedHashMap<>();

        permissions.put("edit", canEdit);

        permissions.put("comment", canView);

        permissions.put("download", canDownload);

        permissions.put("print", canDownload);



        Map<String, Object> document = new LinkedHashMap<>();

        document.put("fileType", fileType);

        document.put("key", key);

        document.put("title", title);

        document.put("url", url);

        document.put("permissions", permissions);

        return document;

    }



    private Map<String, Object> buildEditorConfigClaims(
            Document document,
            User user,
            boolean canEdit,
            String callbackUrl,
            String shareToken
    ) {

        Map<String, Object> userInfo = new LinkedHashMap<>();

        userInfo.put("id", String.valueOf(user.getId()));

        userInfo.put("name", user.getFullName());



        Map<String, Object> goBack = new LinkedHashMap<>();

        if (shareToken != null && !shareToken.isBlank()) {

            goBack.put("url", frontendUrl + "/share/" + shareToken.trim());

        } else {

            goBack.put("url", frontendUrl + "/documents/" + document.getId() + "/preview?edited=true");

        }



        Map<String, Object> customization = new LinkedHashMap<>();

        customization.put("forcesave", true);

        customization.put("autosave", true);

        customization.put("goback", goBack);



        Map<String, Object> editorConfig = new LinkedHashMap<>();

        editorConfig.put("mode", canEdit ? "edit" : "view");

        editorConfig.put("lang", "en");

        editorConfig.put("user", userInfo);

        editorConfig.put("customization", customization);

        editorConfig.put("callbackUrl", callbackUrl);

        return editorConfig;

    }



    private Map<String, Object> buildVersionEditorConfigClaims(Document document, User user, String callbackUrl) {

        Map<String, Object> userInfo = new LinkedHashMap<>();

        userInfo.put("id", String.valueOf(user.getId()));

        userInfo.put("name", user.getFullName());



        Map<String, Object> goBack = new LinkedHashMap<>();

        goBack.put("url", frontendUrl + "/documents/" + document.getId() + "/versions");



        Map<String, Object> customization = new LinkedHashMap<>();

        customization.put("forcesave", false);

        customization.put("autosave", false);

        customization.put("goback", goBack);



        Map<String, Object> editorConfig = new LinkedHashMap<>();

        editorConfig.put("mode", "view");

        editorConfig.put("lang", "en");

        editorConfig.put("user", userInfo);

        editorConfig.put("customization", customization);

        editorConfig.put("callbackUrl", callbackUrl);

        return editorConfig;

    }



    private String signConfig(Map<String, Object> documentClaims, Map<String, Object> editorConfigClaims, String documentType) {

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("document", documentClaims);

        payload.put("editorConfig", editorConfigClaims);

        payload.put("documentType", documentType);



        return Jwts.builder()

                .claims(payload)

                .signWith(Keys.hmacShaKeyFor(onlyofficeJwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))

                .compact();

    }



    private OnlyOfficeConfig.DocumentInfo toDocumentInfo(Map<String, Object> documentClaims) {

        @SuppressWarnings("unchecked")

        Map<String, Object> permissionsMap = (Map<String, Object>) documentClaims.get("permissions");

        OnlyOfficeConfig.Permissions permissions = OnlyOfficeConfig.Permissions.builder()

                .edit(Boolean.TRUE.equals(permissionsMap.get("edit")))

                .comment(Boolean.TRUE.equals(permissionsMap.get("comment")))

                .download(Boolean.TRUE.equals(permissionsMap.get("download")))

                .print(Boolean.TRUE.equals(permissionsMap.get("print")))

                .build();



        return OnlyOfficeConfig.DocumentInfo.builder()

                .fileType((String) documentClaims.get("fileType"))

                .key((String) documentClaims.get("key"))

                .title((String) documentClaims.get("title"))

                .url((String) documentClaims.get("url"))

                .permissions(permissions)

                .build();

    }



    @SuppressWarnings("unchecked")

    private OnlyOfficeConfig.EditorConfig toEditorConfig(Map<String, Object> editorConfigClaims) {

        Map<String, Object> userMap = (Map<String, Object>) editorConfigClaims.get("user");

        Map<String, Object> customizationMap = (Map<String, Object>) editorConfigClaims.get("customization");

        Map<String, Object> goBackMap = customizationMap != null

                ? (Map<String, Object>) customizationMap.get("goback")

                : null;



        return OnlyOfficeConfig.EditorConfig.builder()

                .mode((String) editorConfigClaims.get("mode"))

                .lang((String) editorConfigClaims.get("lang"))

                .callbackUrl((String) editorConfigClaims.get("callbackUrl"))

                .user(OnlyOfficeConfig.UserInfo.builder()

                        .id((String) userMap.get("id"))

                        .name((String) userMap.get("name"))

                        .build())

                .customization(OnlyOfficeConfig.Customization.builder()

                        .forcesave(Boolean.TRUE.equals(customizationMap.get("forcesave")))

                        .autosave(Boolean.TRUE.equals(customizationMap.get("autosave")))

                        .goback(goBackMap != null

                                ? OnlyOfficeConfig.GoBack.builder().url((String) goBackMap.get("url")).build()

                                : null)

                        .build())

                .build();

    }



    private String normalizeBaseUrl(String baseUrl) {

        if (baseUrl == null || baseUrl.isBlank()) {

            return "http://host.docker.internal:8080";

        }

        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

    }



    private String getFileExtension(Document document) {

        String fileExtension = document.getType();

        if (fileExtension != null) {

            fileExtension = fileExtension.trim().toLowerCase(Locale.ROOT).replace(".", "");

        }

        if ((fileExtension == null || fileExtension.isBlank()) && document.getName() != null) {

            int dotIdx = document.getName().lastIndexOf('.');

            if (dotIdx > 0 && dotIdx < document.getName().length() - 1) {

                fileExtension = document.getName().substring(dotIdx + 1).toLowerCase(Locale.ROOT);

            }

        }

        return fileExtension != null && !fileExtension.isBlank() ? fileExtension : "docx";

    }



    private String getDocumentType(String ext) {

        if (ext == null) return "word";

        String extLower = ext.toLowerCase(Locale.ROOT);

        if (java.util.List.of("docx", "doc", "txt", "rtf", "odt").contains(extLower)) return "word";

        if (java.util.List.of("xlsx", "xls", "csv", "ods").contains(extLower)) return "cell";

        if (java.util.List.of("pptx", "ppt", "odp").contains(extLower)) return "slide";

        return "word";

    }

}


