package com.example.backup.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "file_versions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FileVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String storageFilename;
    private String filename;
    private String contentType;
    private long size;
    private Instant createdAt;

    @ManyToOne
    @JoinColumn(name = "backup_file_id")
    private BackupFile backupFile;

    public String getCreatedAtFormatted() {
        if (createdAt == null) return "—";
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.of("America/Bogota"))
                .format(createdAt);
    }
}
