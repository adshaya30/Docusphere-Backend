package com.docusphere.backend.editor.service;

import com.docusphere.backend.editor.dto.CreateEditorDocumentRequest;
import com.docusphere.backend.editor.dto.SaveEditorDocumentRequest;
import com.docusphere.backend.editor.entity.EditorDocument;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeCallback;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;

import java.util.List;
import java.util.UUID;

public interface EditorService {

    /**
     * Create a new empty editor document (Word, Excel, or Presentation).
     *
     * @param ownerId user ID of the document creator
     * @param request DTO containing the document name and type
     * @return ONLYOFFICE editor configuration to load the new document
     */
    OnlyOfficeConfig createDocument(Long ownerId, CreateEditorDocumentRequest request);

    /**
     * List all editor documents belonging to a user.
     *
     * @param ownerId user ID of the owner
     * @return list of editor documents
     */
    List<EditorDocument> listDocuments(Long ownerId);

    /**
     * Get ONLYOFFICE editor configuration to load/open an existing document.
     *
     * @param ownerId user ID of the requester
     * @param documentId UUID of the document to open
     * @return ONLYOFFICE editor configuration
     */
    OnlyOfficeConfig getDocumentConfig(Long ownerId, UUID documentId);

    /**
     * Save metadata (name, status) of an editor document.
     *
     * @param ownerId user ID of the requester
     * @param request DTO containing the update details
     * @return the updated editor document entity
     */
    EditorDocument saveMetadata(Long ownerId, SaveEditorDocumentRequest request);

    /**
     * Delete an editor document and its physical storage in Supabase.
     *
     * @param ownerId user ID of the requester
     * @param documentId UUID of the document to delete
     */
    void deleteDocument(Long ownerId, UUID documentId);

    /**
     * Handle the ONLYOFFICE save callback by updating the storage and metadata.
     *
     * @param id UUID of the document
     * @param token authentication callback token to validate request
     * @param callback ONLYOFFICE callback payload
     */
    void handleSaveCallback(UUID id, String token, OnlyOfficeCallback callback);
}
