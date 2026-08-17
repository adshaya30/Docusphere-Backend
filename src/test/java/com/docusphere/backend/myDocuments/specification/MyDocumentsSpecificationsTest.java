package com.docusphere.backend.myDocuments.specification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MyDocumentsSpecifications type filter")
class MyDocumentsSpecificationsTest {

    @Test
    @DisplayName("Should expand sheet to all spreadsheet extensions")
    void resolveExtensions_sheetGroup() {
        Set<String> extensions = MyDocumentsSpecifications.resolveExtensions("sheet");
        assertEquals(Set.of("xls", "xlsx", "xlsm", "xlsb", "csv", "ods"), extensions);
    }

    @Test
    @DisplayName("Should expand word/powerpoint/image/pdf groups")
    void resolveExtensions_otherGroups() {
        assertEquals(Set.of("doc", "docx"), MyDocumentsSpecifications.resolveExtensions("word"));
        assertEquals(Set.of("ppt", "pptx"), MyDocumentsSpecifications.resolveExtensions("powerpoint"));
        assertEquals(Set.of("png", "jpg", "jpeg", "gif", "webp"), MyDocumentsSpecifications.resolveExtensions("image"));
        assertEquals(Set.of("pdf"), MyDocumentsSpecifications.resolveExtensions("pdf"));
    }

    @Test
    @DisplayName("Should keep concrete extensions unchanged")
    void resolveExtensions_concrete() {
        assertEquals(Set.of("docx"), MyDocumentsSpecifications.resolveExtensions("DOCX"));
        assertEquals(Set.of("xlsx"), MyDocumentsSpecifications.resolveExtensions("xlsx"));
        assertEquals(Set.of("jpg"), MyDocumentsSpecifications.resolveExtensions("jpg"));
        assertTrue(MyDocumentsSpecifications.resolveExtensions("csv").contains("csv"));
    }
}
