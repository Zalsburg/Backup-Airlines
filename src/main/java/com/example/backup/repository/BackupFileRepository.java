package com.example.backup.repository;

import com.example.backup.model.BackupFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BackupFileRepository extends JpaRepository<BackupFile, Long> {
    List<BackupFile> findByOwnerIdAndTrashedFalse(Long ownerId);
    List<BackupFile> findByOwnerIdAndTrashedTrue(Long ownerId);
    List<BackupFile> findByTrashedTrue();
}
