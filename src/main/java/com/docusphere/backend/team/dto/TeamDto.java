package com.docusphere.backend.team.dto;



import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class TeamDto {

    private UUID id;
    private String name;
    private String description;
    private Integer memberCount;
    private Integer documentCount;
    private String currentUserRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AddMemberRequest> members;

    public TeamDto() {}

    public TeamDto(UUID id, String name, String description,
                   Integer memberCount, Integer documentCount,
                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.memberCount = memberCount;
        this.documentCount = documentCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }

    public Integer getDocumentCount() { return documentCount; }
    public void setDocumentCount(Integer documentCount) { this.documentCount = documentCount; }

    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<AddMemberRequest> getMembers() { return members; }
    public void setMembers(List<AddMemberRequest> members) { this.members = members; }
}