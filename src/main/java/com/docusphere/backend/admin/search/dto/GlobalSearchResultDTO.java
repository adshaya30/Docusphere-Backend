package com.docusphere.backend.admin.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResultDTO {
    private List<UserSearchResult> users;
    private List<TeamSearchResult> teams;
    private List<DocumentSearchResult> documents;
    private Long totalResults;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSearchResult {
        private Long id;
        private String fullName;
        private String email;
        private String role;
        private String type = "user";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamSearchResult {
        private String id;
        private String teamName;
        private Integer memberCount;
        private String type = "team";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentSearchResult {
        private String id;
        private String fileName;
        private String fileType;
        private String type = "document";
    }
}
