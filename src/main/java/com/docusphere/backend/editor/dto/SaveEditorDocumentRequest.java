package com.docusphere.backend.editor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveEditorDocumentRequest {

    @NotNull(message = "Document ID is required")
    private UUID id;

    @NotBlank(message = "Document name is required")
    private String name;

    private String status;
}
