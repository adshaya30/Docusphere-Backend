package com.docusphere.backend.ocr.service;

import com.docusphere.backend.ocr.dto.OcrResponse;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OcrJobManager {
    
    @Data
    @Builder
    public static class JobStatus {
        private String jobId;
        private String status; // UPLOADING, EXTRACTING, SUMMARIZING, COMPLETED, FAILED
        private int progress;
        private OcrResponse result;
        private String error;
    }

    private final Map<String, JobStatus> jobs = new ConcurrentHashMap<>();

    public String createJob(String filename) {
        String jobId = java.util.UUID.randomUUID().toString();
        jobs.put(jobId, JobStatus.builder()
                .jobId(jobId)
                .status("UPLOADING")
                .progress(10)
                .build());
        return jobId;
    }

    public void updateJob(String jobId, String status, int progress) {
        JobStatus job = jobs.get(jobId);
        if (job != null) {
            job.setStatus(status);
            job.setProgress(progress);
        }
    }

    public void completeJob(String jobId, OcrResponse result) {
        JobStatus job = jobs.get(jobId);
        if (job != null) {
            job.setStatus("COMPLETED");
            job.setProgress(100);
            job.setResult(result);
        }
    }

    public void failJob(String jobId, String error) {
        JobStatus job = jobs.get(jobId);
        if (job != null) {
            job.setStatus("FAILED");
            job.setError(error);
        }
    }

    public JobStatus getJobStatus(String jobId) {
        return jobs.get(jobId);
    }
}
