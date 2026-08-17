package com.docusphere.backend.myDocuments.specification;

import com.docusphere.backend.document.entity.Document;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MyDocumentsSpecifications {

    private static final Map<String, Set<String>> GROUPED_TYPES = Map.of(
            "word", Set.of("doc", "docx"),
            "sheet", Set.of("xls", "xlsx", "xlsm", "xlsb", "csv", "ods"),
            "powerpoint", Set.of("ppt", "pptx"),
            "image", Set.of("png", "jpg", "jpeg", "gif", "webp"),
            "pdf", Set.of("pdf")
    );

    private static final Map<String, Set<String>> MIME_BY_EXTENSION = Map.ofEntries(
            Map.entry("doc", Set.of("application/msword")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xls", Set.of("application/vnd.ms-excel")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("xlsm", Set.of("application/vnd.ms-excel.sheet.macroenabled.12")),
            Map.entry("xlsb", Set.of("application/vnd.ms-excel.sheet.binary.macroenabled.12")),
            Map.entry("csv", Set.of("text/csv", "application/csv")),
            Map.entry("ods", Set.of("application/vnd.oasis.opendocument.spreadsheet")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("gif", Set.of("image/gif")),
            Map.entry("webp", Set.of("image/webp")),
            Map.entry("pdf", Set.of("application/pdf"))
    );

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

    /**
     * Filters by grouped UI type (word/sheet/powerpoint/image/pdf) or a concrete extension.
     * Matches document.type (extension or mime) and/or filename extension.
     */
    public static Specification<Document> hasType(String type) {
        Set<String> extensions = resolveExtensions(type);
        List<String> typeValues = buildTypeMatchValues(extensions);

        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            var typePath = cb.lower(root.get("type"));
            var namePath = cb.lower(root.get("name"));

            predicates.add(typePath.in(typeValues));

            for (String extension : extensions) {
                predicates.add(cb.like(namePath, "%." + extension));
            }

            return cb.or(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    public static Specification<Document> hasSearch(String search) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
    }

    static Set<String> resolveExtensions(String type) {
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return GROUPED_TYPES.getOrDefault(normalized, Set.of(normalized));
    }

    private static List<String> buildTypeMatchValues(Set<String> extensions) {
        List<String> values = new ArrayList<>(extensions);
        for (String extension : extensions) {
            Set<String> mimes = MIME_BY_EXTENSION.get(extension);
            if (mimes != null) {
                values.addAll(mimes);
            }
        }
        return values;
    }
}