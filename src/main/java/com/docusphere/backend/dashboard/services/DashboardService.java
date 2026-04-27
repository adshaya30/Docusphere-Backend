package com.docusphere.backend.dashboard.services;

import com.docusphere.backend.dashboard.dto.DashboardResponse;

public interface DashboardService {
    DashboardResponse getDashboard(Long ownerId, int recentDays);
}
