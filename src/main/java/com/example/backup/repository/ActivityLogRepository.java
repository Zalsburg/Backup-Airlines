package com.example.backup.repository;

import com.example.backup.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findTop50ByOrderByCreatedAtDesc();
    List<ActivityLog> findByUserId(Long userId);
    List<ActivityLog> findByFileId(Long fileId);

    /** Bitácora completa en orden cronológico (exportación para minería de datos). */
    List<ActivityLog> findAllByOrderByCreatedAtAsc();

    @Modifying
    @Query("UPDATE ActivityLog a SET a.file = null WHERE a.file.id = :fileId")
    void nullifyFileReference(@Param("fileId") Long fileId);
}
