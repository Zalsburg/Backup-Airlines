package com.example.backup.repository;

import com.example.backup.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByOwnerIdAndParentIsNull(Long ownerId);
    List<Folder> findByOwnerId(Long ownerId);
}
