package com.docusphere.backend.admin.dashboard.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.docusphere.backend.admin.dashboard.dto.AdminDashboardDTO;
import com.docusphere.backend.admin.dashboard.dto.MonthlyUploadDTO;
import com.docusphere.backend.admin.dashboard.dto.TopTeamDTO;
import com.docusphere.backend.admin.dashboard.repository.*;

import com.docusphere.backend.Common.exception.AdminDashboardException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminDashboardService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardService.class);

    @Autowired
    private AdminUserRepository userRepository;

    @Autowired
    private AdminDocumentRepository documentRepository;

    @Autowired
    private AdminTeamRepository teamRepository;

    @Autowired
    private AdminTeamMemberRepository teamMemberRepository;

    public AdminDashboardDTO getDashboardData() {
        try {
            // Get current counts
            long totalUsers = userRepository.count();
            long totalDocs = documentRepository.count();
            long totalTeams = teamRepository.count();

            Long activeSessionsCount = null;
            try {
                activeSessionsCount = userRepository.countActiveSessions();
                logger.info("Fetched active sessions from auth.sessions: {}", activeSessionsCount);
            } catch (Exception e) {
                logger.error("Error querying auth.sessions (this is expected if schema is restricted): {}", e.getMessage());
            }
            
            // Fallback to active team members if sessions are 0 or query failed
            if (activeSessionsCount == null || activeSessionsCount == 0) {
                try {
                    activeSessionsCount = teamMemberRepository.countByActiveTrue();
                    logger.info("Falling back to active team members count: {}", activeSessionsCount);
                } catch (Exception e) {
                    logger.warn("Could not fetch active members count: {}", e.getMessage());
                }
            }
            
            long activeSessions = activeSessionsCount != null ? activeSessionsCount : 0L;
            
            logger.info("Dashboard counts - Users: {}, Docs: {}, Teams: {}, Sessions: {}", totalUsers, totalDocs, totalTeams, activeSessions);

            // Calculate monthly growth for documents
            Long currentMonthDocs = documentRepository.getCurrentMonthDocumentCount();
            Long previousMonthDocs = documentRepository.getPreviousMonthDocumentCount();
            if (currentMonthDocs == null)
                currentMonthDocs = 0L;
            if (previousMonthDocs == null)
                previousMonthDocs = 0L;
            Double documentGrowth = calculateGrowthPercentage(currentMonthDocs, previousMonthDocs);

            // Calculate monthly growth for users
            Long currentMonthUsers = userRepository.getCurrentMonthUserCount();
            Long previousMonthUsers = userRepository.getPreviousMonthUserCount();
            if (currentMonthUsers == null)
                currentMonthUsers = 0L;
            if (previousMonthUsers == null)
                previousMonthUsers = 0L;
            Double userGrowth = calculateGrowthPercentage(currentMonthUsers, previousMonthUsers);

            // Calculate monthly growth for teams
            Long currentMonthTeams = teamRepository.getCurrentMonthTeamCount();
            Long previousMonthTeams = teamRepository.getPreviousMonthTeamCount();
            if (currentMonthTeams == null)
                currentMonthTeams = 0L;
            if (previousMonthTeams == null)
                previousMonthTeams = 0L;
            Double teamGrowth = calculateGrowthPercentage(currentMonthTeams, previousMonthTeams);
            
            // For sessions, we don't have historical data yet, so we'll use 0.0 or a mock calculation
            Double sessionGrowth = 0.0;
            
            logger.info("Growth metrics - Docs: {}%, Users: {}%, Teams: {}%, Sessions: {}%", documentGrowth, userGrowth, teamGrowth, sessionGrowth);

            // Get monthly uploads
            List<Object[]> monthlyData = documentRepository.getMonthlyUploads();
            List<MonthlyUploadDTO> monthlyUploads = new ArrayList<>();
            if (monthlyData != null) {
                logger.info("Monthly uploads data found: {} records", monthlyData.size());
                for (Object[] row : monthlyData) {
                    try {
                        if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                            Integer month = ((Number) row[0]).intValue();
                            Long count = ((Number) row[1]).longValue();
                            monthlyUploads.add(new MonthlyUploadDTO(month, count));
                        }
                    } catch (Exception e) {
                        logger.error("Error processing monthly upload row: {}", e.getMessage());
                    }
                }
            } else {
                logger.warn("No monthly uploads data found");
            }

            // Get top 5 teams by member activity
            List<Object[]> topTeamsData = teamMemberRepository.getTopTeamsByMembers();
            List<TopTeamDTO> topTeams = new ArrayList<>();
            if (topTeamsData != null) {
                logger.info("Top teams data found: {} records", topTeamsData.size());
                for (Object[] row : topTeamsData) {
                    try {
                        if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                            String teamName = (String) row[0];
                            Long memberCount = ((Number) row[1]).longValue();
                            Double activityPercent = (row.length >= 3 && row[2] != null) ? ((Number) row[2]).doubleValue() : 0.0;
                            topTeams.add(new TopTeamDTO(teamName, memberCount, activityPercent));
                        }
                    } catch (Exception e) {
                        logger.error("Error processing top team row: {}", e.getMessage());
                    }
                }
            } else {
                logger.warn("No top teams data found");
            }

            AdminDashboardDTO dto = new AdminDashboardDTO();
            dto.setTotalDocuments(totalDocs);
            dto.setTotalUsers(totalUsers);
            dto.setTotalTeams(totalTeams);
            dto.setActiveSessions(activeSessions);
            dto.setDocumentGrowth(documentGrowth);
            dto.setUserGrowth(userGrowth);
            dto.setTeamGrowth(teamGrowth);
            dto.setSessionGrowth(sessionGrowth);
            dto.setMonthlyUploads(monthlyUploads);
            dto.setTopTeams(topTeams);
            
            logger.info("Dashboard data successfully compiled");
            return dto;
        } catch (Exception e) {
            logger.error("Error fetching dashboard data: {}", e.getMessage(), e);
            throw new AdminDashboardException("Failed to fetch dashboard data: " + e.getMessage(), e);
        }
    }

    private Double calculateGrowthPercentage(Long currentMonth, Long previousMonth) {
        long current = (currentMonth != null) ? currentMonth : 0L;
        long previous = (previousMonth != null) ? previousMonth : 0L;
        
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        
        double growth = ((double) (current - previous) / previous) * 100.0;
        // Round to 2 decimal places
        return Math.round(growth * 100.0) / 100.0;
    }
}
