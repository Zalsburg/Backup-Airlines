package com.example.backup.repository;

import com.example.backup.model.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileVersionRepository extends JpaRepository<FileVersion, Long> {
    List<FileVersion> findByBackupFileIdOrderByCreatedAtDesc(Long backupFileId);
}
