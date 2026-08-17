package com.docusphere.backend.admin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.stereotype.Repository;

import com.docusphere.backend.document.entity.Document;

import java.util.List;


@Repository
public interface AdminDocumentRepository extends JpaRepository<Document, Long> {

// Monthly Uploads for the last 12 months
   @Query(value = """
    SELECT 
        EXTRACT(MONTH FROM DATE_TRUNC('month', created_at)) AS month,
        COUNT(*) AS total_uploads
    FROM documents
    WHERE created_at >= NOW() - INTERVAL '12 months'
    GROUP BY DATE_TRUNC('month', created_at)
    ORDER BY DATE_TRUNC('month', created_at)
    """, nativeQuery = true)
    List<Object[]> getMonthlyUploads();

// Top 5 teams with the most document uploads in the last 7 days
    @Query(value = """
    SELECT t.team_name, COUNT(d.id) AS doc_count,
           COALESCE(SUM(CASE WHEN d.status = 'COMPLETED' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(d.id), 0), 0) AS activity_percent
    FROM documents d
    JOIN team t ON d.team_id = t.id
    WHERE d.created_at >= NOW() - INTERVAL '7 days'
    GROUP BY t.id, t.team_name
    ORDER BY doc_count DESC
    LIMIT 5
    """, nativeQuery = true)
    List<Object[]> getTopTeamsLast7Days();


// Total document count for the current month
    @Query(value = """
    SELECT COUNT(*)
    FROM documents
    WHERE created_at >= date_trunc('month', CURRENT_DATE)
    """, nativeQuery = true)
    Long getCurrentMonthDocumentCount();

// Total document count for the previous month
   @Query(value = """
    SELECT COUNT(*)
    FROM documents
    WHERE created_at >= date_trunc('month', CURRENT_DATE - INTERVAL '1 month')
      AND created_at < date_trunc('month', CURRENT_DATE)
   """, nativeQuery = true)
   Long getPreviousMonthDocumentCount();

    @Query("SELECT COALESCE(SUM(d.sizeBytes), 0) FROM Document d")
    Long sumTotalStorageBytes();
}
