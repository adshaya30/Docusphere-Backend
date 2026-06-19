package com.docusphere.backend.onlyoffice.service;

import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OnlyOfficeConfigBuilderService {

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.onlyoffice.callback-url:http://localhost:8080/api/onlyoffice/callback}")
    private String onlyofficeCallbackUrl;

    @Value("${app.onlyoffice.jwt.secret:V8pX9iu5gDWzQrHP5Od62XOOiuOnlrtF}")
    private String onlyofficeJwtSecret;

    public OnlyOfficeConfig buildConfig(Document document, User user, boolean canEdit, boolean canView, boolean canDownload, String docKey, String callbackToken) {
        String fileExtension = getFileExtension(document);
        String callbackUrl = onlyofficeCallbackUrl + "?id=" + document.getId() + "&token=" + callbackToken;

        OnlyOfficeConfig.Permissions permissions = OnlyOfficeConfig.Permissions.builder()
                .edit(canEdit)
                .comment(canView)
                .download(canDownload)
                .print(canDownload)
                .build();

        OnlyOfficeConfig.UserInfo userInfo = OnlyOfficeConfig.UserInfo.builder()
                .id(String.valueOf(user.getId()))
                .name(user.getFullName())
                .build();

        OnlyOfficeConfig.GoBack goBack = OnlyOfficeConfig.GoBack.builder()
                .url(frontendUrl + "/documents/" + document.getId() + "/preview?edited=true")
                .build();

        OnlyOfficeConfig.Customization customization = OnlyOfficeConfig.Customization.builder()
                .forcesave(true)
                .autosave(true)
                .goback(goBack)
                .build();

        OnlyOfficeConfig.EditorConfig editorConfig = OnlyOfficeConfig.EditorConfig.builder()
                .mode(canEdit ? "edit" : "view")
                .lang("en")
                .user(userInfo)
                .customization(customization)
                .callbackUrl(callbackUrl)
                .build();

        long lastModified = document.getUpdatedAt() != null 
                ? java.sql.Timestamp.valueOf(document.getUpdatedAt()).getTime() 
                : (document.getCreatedAt() != null ? java.sql.Timestamp.valueOf(document.getCreatedAt()).getTime() : System.currentTimeMillis());

        String fileUrl = document.getFileUrl();
        if (fileUrl != null) {
            String separator = fileUrl.contains("?") ? "&" : "?";
            fileUrl = fileUrl + separator + "cb=" + lastModified;
        }

        OnlyOfficeConfig.DocumentInfo documentInfo = OnlyOfficeConfig.DocumentInfo.builder()
                .fileType(fileExtension)
                .key(docKey)
                .title(document.getName())
                .url(fileUrl)
                .permissions(permissions)
                .build();

        Map<String, Object> claims = new HashMap<>();
        claims.put("document", documentInfo);
        claims.put("editorConfig", editorConfig);
        claims.put("documentType", getDocumentType(fileExtension));

        String token;
        try {
            token = Jwts.builder()
                    .claims(claims)
                    .signWith(Keys.hmacShaKeyFor(onlyofficeJwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .compact();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate ONLYOFFICE security token", e);
        }

        return OnlyOfficeConfig.builder()
                .documentType(getDocumentType(fileExtension))
                .document(documentInfo)
                .editorConfig(editorConfig)
                .width("100%")
                .height("100%")
                .token(token)
                .build();
    }

    private String getFileExtension(Document document) {
        String fileExtension = document.getType();
        if (fileExtension == null && document.getName() != null) {
            int dotIdx = document.getName().lastIndexOf('.');
            if (dotIdx > 0 && dotIdx < document.getName().length() - 1) {
                fileExtension = document.getName().substring(dotIdx + 1);
            }
        }
        return fileExtension != null ? fileExtension : "docx";
    }

    private String getDocumentType(String ext) {
        if (ext == null) return "word";
        String extLower = ext.toLowerCase();
        if (java.util.List.of("docx", "doc", "txt", "rtf", "odt").contains(extLower)) return "word";
        if (java.util.List.of("xlsx", "xls", "csv", "ods").contains(extLower)) return "cell";
        if (java.util.List.of("pptx", "ppt", "odp").contains(extLower)) return "slide";
        return "word";
    }
}
