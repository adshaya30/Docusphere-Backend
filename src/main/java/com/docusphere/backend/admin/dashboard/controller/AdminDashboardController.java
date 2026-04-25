package com.docusphere.backend.admin.dashboard.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import com.docusphere.backend.admin.dashboard.dto.AdminDashboardDTO;
import com.docusphere.backend.admin.dashboard.service.AdminDashboardService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;


@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    @Autowired
    private AdminDashboardService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminDashboardDTO> getDashboard() {
        return ResponseEntity.ok(service.getDashboardData());
    }
}
