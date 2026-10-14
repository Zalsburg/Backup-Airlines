package com.example.backup.service;

import com.example.backup.model.*;
import com.example.backup.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@Service
public class FileStorageService {

    private final Path storageLocation;
    private final BackupFileRepository backupFileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogService activityLogService;

    public FileStorageService(@Value("${app.storage.location}") String storageLocation,
                              BackupFileRepository backupFileRepository,
                              FileVersionRepository fileVersionRepository,
                              ActivityLogRepository activityLogRepository,
                              ActivityLogService activityLogService) throws IOException {
        this.storageLocation = Paths.get(storageLocation).toAbsolutePath().normalize();
        this.backupFileRepository = backupFileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.activityLogRepository = activityLogRepository;
        this.activityLogService = activityLogService;
        Files.createDirectories(this.storageLocation);
    }

    @Transactional
    public BackupFile store(MultipartFile file, User owner) throws IOException {
        String original = file.getOriginalFilename();
        String uuidName = UUID.randomUUID().toString() + "_" + Objects.requireNonNull(original);
        Path target = storageLocation.resolve(uuidName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        Optional<BackupFile> existing = backupFileRepository.findAll()
                .stream()
                .filter(b -> b.getOwner().getId().equals(owner.getId())
                        && b.getFilename().equals(original)
                        && !b.isTrashed())
                .findFirst();

        BackupFile saved;
        if (existing.isPresent()) {
            BackupFile bf = existing.get();
            FileVersion v = new FileVersion();
            v.setBackupFile(bf);
            v.setCreatedAt(Instant.now());
            v.setFilename(original);
            v.setStorageFilename(uuidName);
            v.setContentType(file.getContentType());
            v.setSize(file.getSize());
            fileVersionRepository.save(v);
            bf.setSize(file.getSize());
            bf.setCreatedAt(Instant.now());
            saved = backupFileRepository.save(bf);
        } else {
            BackupFile bf = new BackupFile();
            bf.setFilename(original);
            bf.setStorageFilename(uuidName);
            bf.setContentType(file.getContentType());
            bf.setSize(file.getSize());
            bf.setCreatedAt(Instant.now());
            bf.setOwner(owner);
            saved = backupFileRepository.save(bf);

            FileVersion v = new FileVersion();
            v.setBackupFile(saved);
            v.setCreatedAt(Instant.now());
            v.setFilename(original);
            v.setStorageFilename(uuidName);
            v.setContentType(file.getContentType());
            v.setSize(file.getSize());
            fileVersionRepository.save(v);
        }
        activityLogService.log(owner, "UPLOAD_FILE", saved);
        return saved;
    }

    @Transactional
    public void restoreVersionAsNew(FileVersion version, BackupFile backupFile) throws IOException {
        Path src = storageLocation.resolve(version.getStorageFilename());
        if (!Files.exists(src))
            throw new FileNotFoundException("Versión no encontrada: " + version.getStorageFilename());
        String newStorageName = UUID.randomUUID().toString() + "_" + version.getFilename();
        Path dest = storageLocation.resolve(newStorageName);
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);

        FileVersion v = new FileVersion();
        v.setBackupFile(backupFile);
        v.setCreatedAt(Instant.now());
        v.setFilename(version.getFilename());
        v.setStorageFilename(newStorageName);
        v.setContentType(version.getContentType());
        v.setSize(version.getSize());
        fileVersionRepository.save(v);

        backupFile.setStorageFilename(newStorageName);
        backupFile.setSize(version.getSize());
        backupFile.setCreatedAt(Instant.now());
        backupFileRepository.save(backupFile);
    }

    public Resource loadAsResource(String storageFilename) throws IOException {
        Path file = storageLocation.resolve(storageFilename).normalize();
        if (!Files.exists(file))
            throw new FileNotFoundException("Archivo no encontrado: " + storageFilename);
        return new UrlResource(file.toUri());
    }

    @Transactional
    public void softDelete(BackupFile file, User deletedBy) {
        file.setTrashed(true);
        file.setTrashedAt(Instant.now());
        file.setDeletedBy(deletedBy);
        backupFileRepository.save(file);
        activityLogService.log(deletedBy, "DELETE_FILE", file);
    }

    @Transactional
    public void restore(BackupFile file) {
        file.setTrashed(false);
        file.setTrashedAt(null);
        file.setDeletedBy(null);
        backupFileRepository.save(file);
    }

    @Transactional
    public void permanentDelete(BackupFile file) throws IOException {
        // 1. Desreferenciar los activity_logs que apuntan a este archivo (evita FK violation)
        activityLogRepository.nullifyFileReference(file.getId());

        // 2. Eliminar versiones del disco y BD
        var versions = fileVersionRepository.findByBackupFileIdOrderByCreatedAtDesc(file.getId());
        for (FileVersion v : versions) {
            Path p = storageLocation.resolve(v.getStorageFilename());
            Files.deleteIfExists(p);
            fileVersionRepository.delete(v);
        }

        // 3. Eliminar archivo físico actual si existe
        if (file.getStorageFilename() != null) {
            Files.deleteIfExists(storageLocation.resolve(file.getStorageFilename()));
        }

        // 4. Eliminar registro en BD
        backupFileRepository.delete(file);
    }
}
