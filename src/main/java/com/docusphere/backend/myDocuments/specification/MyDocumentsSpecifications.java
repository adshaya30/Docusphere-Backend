package com.docusphere.backend.myDocuments.specification;
import com.docusphere.backend.document.entity.Document;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class MyDocumentsSpecifications {

    private MyDocumentsSpecifications() {
    }

    public static Specification<Document> hasOwner(Long ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<Document> isNotDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }


    public static Specification<Document> hasTeamId(UUID teamId) {
        return (root, query, cb) -> cb.equal(root.get("teamId"), teamId);
    }

    public static Specification<Document> isUserSpace() {
        return (root, query, cb) -> cb.isNull(root.get("teamId"));
    }

    public static Specification<Document> hasStatus(Document.UploadStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Document> hasType(String type) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("type")), type.toLowerCase());
    }

    public static Specification<Document> hasSearch(String search) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
    }


}