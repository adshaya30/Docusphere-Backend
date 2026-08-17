package com.docusphere.backend.editor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEditorDocumentRequest {

    @NotBlank(message = "Document name is required")
    private String documentName;

    @NotBlank(message = "Document type is required (word, excel, or presentation)")
    private String documentType;
}
