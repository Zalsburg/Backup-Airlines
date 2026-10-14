package com.example.backup.service;

import com.example.backup.model.ActivityLog;
import com.example.backup.model.BackupFile;
import com.example.backup.model.User;
import com.example.backup.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ActivityLogService {
    private final ActivityLogRepository repo;

    public ActivityLogService(ActivityLogRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void log(User user, String action, BackupFile file) {
        ActivityLog log = new ActivityLog();
        log.setUser(user);
        log.setAction(action);
        log.setFile(file);
        log.setCreatedAt(Instant.now());
        repo.save(log);
    }

    public List<ActivityLog> getRecent() {
        return repo.findTop50ByOrderByCreatedAtDesc();
    }

    public List<ActivityLog> getAllChronological() {
        return repo.findAllByOrderByCreatedAtAsc();
    }
}
