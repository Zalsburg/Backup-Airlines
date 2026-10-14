package com.example.backup.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "backup_files")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class BackupFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;
    private String storageFilename;
    private String contentType;
    private long size;
    private Instant createdAt;

    private boolean trashed = false;
    private Instant trashedAt;
    private boolean favorite = false;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne
    @JoinColumn(name = "deleted_by")
    private User deletedBy;

    @ManyToOne
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "file_tags",
        joinColumns = @JoinColumn(name = "file_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags;

    @OneToMany(mappedBy = "backupFile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FileVersion> versions;

    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FileShare> shares;

    private static final DateTimeFormatter FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Bogota"));

    public String getTrashedAtFormatted() {
        return trashedAt != null ? FMT.format(trashedAt) : "—";
    }

    public String getCreatedAtFormatted() {
        return createdAt != null ? FMT.format(createdAt) : "—";
    }
}
