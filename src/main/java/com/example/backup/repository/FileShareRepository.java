package com.example.backup.repository;

import com.example.backup.model.FileShare;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FileShareRepository extends JpaRepository<FileShare, Long> {
    List<FileShare> findBySharedWithUserId(Long userId);
    Optional<FileShare> findByPublicToken(String token);
    List<FileShare> findByFileId(Long fileId);
}
