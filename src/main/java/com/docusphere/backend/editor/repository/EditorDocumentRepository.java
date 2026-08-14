package com.docusphere.backend.editor.repository;

import com.docusphere.backend.editor.entity.EditorDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EditorDocumentRepository extends JpaRepository<EditorDocument, UUID> {
    
    /**
     * Find all editor documents belonging to a specific owner.
     *
     * @param ownerId the user ID of the owner
     * @return list of editor documents
     */
    List<EditorDocument> findByOwnerId(Long ownerId);
}
