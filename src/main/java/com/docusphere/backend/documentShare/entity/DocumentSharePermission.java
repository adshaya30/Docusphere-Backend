package com.docusphere.backend.documentShare.entity;

public enum DocumentSharePermission {
    VIEW,
    COMMENT,
    EDIT;

    public boolean canView() {
        return true;
    }

    public boolean canComment() {
        return this == COMMENT || this == EDIT;
    }

    public boolean canEdit() {
        return this == EDIT;
    }
}
