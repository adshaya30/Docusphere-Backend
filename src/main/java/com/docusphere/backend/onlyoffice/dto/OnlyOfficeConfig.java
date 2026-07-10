package com.docusphere.backend.onlyoffice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OnlyOfficeConfig {
    private String documentType;
    private DocumentInfo document;
    private EditorConfig editorConfig;
    private String width;
    private String height;
    private String token;

    @Data
    @Builder
    public static class DocumentInfo {
        private String fileType;
        private String key;
        private String title;
        private String url;
        private Permissions permissions;
    }

    @Data
    @Builder
    public static class Permissions {
        private boolean edit;
        private boolean comment;
        private boolean download;
        private boolean print;
    }

    @Data
    @Builder
    public static class EditorConfig {
        private String mode;
        private String lang;
        private UserInfo user;
        private Customization customization;
        private String callbackUrl;
    }

    @Data
    @Builder
    public static class UserInfo {
        private String id;
        private String name;
    }

    @Data
    @Builder
    public static class Customization {
        private boolean forcesave;
        private boolean autosave;
        private GoBack goback;
    }

    @Data
    @Builder
    public static class GoBack {
        private String url;
    }
}
