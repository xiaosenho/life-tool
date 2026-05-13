package com.lifetool.ai.store;

import com.lifetool.ai.dto.AiJob;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class AiJobStore {

    private final Map<String, AiJob> jobs = new ConcurrentHashMap<>();

    public AiJob save(AiJob job) {
        jobs.put(job.getId(), job);
        return job;
    }

    public Optional<AiJob> findById(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public List<AiJob> findByUserId(String userId) {
        return jobs.values().stream()
                .filter(j -> j.getUserId().equals(userId))
                .toList();
    }

    public void updateStatus(String id, AiJob.Status status, String resultJson) {
        AiJob job = jobs.get(id);
        if (job != null) {
            job.setStatus(status);
            job.setResultJson(resultJson);
        }
    }
}
