package com.docusphere.backend.dashboard.dto;

public record DashboardResponse(
        long total,
        long recent,
        long starred,
        long uploads
) {
}