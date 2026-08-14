
package  com.docusphere.backend.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardDTO {
    
    // Counters
    private Long totalDocuments;
    private Long totalUsers;
    private Long totalTeams;
    private Long activeSessions;
    
    // Growth metrics
    private Double documentGrowth;
    private Double userGrowth;
    private Double teamGrowth;
    private Double sessionGrowth;
    
    // Storage metrics
    private Long usedStorageBytes;
    private Long storageQuotaBytes;
    
    // Chart data
    private List<MonthlyUploadDTO> monthlyUploads;
    private List<TopTeamDTO> topTeams;
}
