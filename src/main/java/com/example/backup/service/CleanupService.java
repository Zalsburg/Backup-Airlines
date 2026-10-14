package com.example.backup.service;

import com.example.backup.model.BackupFile;
import com.example.backup.repository.BackupFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CleanupService {
    private final BackupFileRepository backupFileRepository;
    private final FileStorageService fileStorageService;
    private final long retentionDays;

    public CleanupService(BackupFileRepository repo, FileStorageService storage,
                          @Value("${app.trash.retention-days:30}") long retentionDays){
        this.backupFileRepository = repo;
        this.fileStorageService = storage;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void purgeOldTrashedFiles(){
        List<BackupFile> trashed = backupFileRepository.findByTrashedTrue();
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        for(BackupFile f: trashed){
            if (f.getTrashedAt() != null && f.getTrashedAt().isBefore(cutoff)){
                try {
                    fileStorageService.permanentDelete(f);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
