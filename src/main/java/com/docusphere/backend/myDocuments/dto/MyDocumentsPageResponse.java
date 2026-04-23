package com.docusphere.backend.myDocuments.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MyDocumentsPageResponse {
    private List<MyDocumentItemResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}



