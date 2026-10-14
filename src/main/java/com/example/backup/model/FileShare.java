package com.example.backup.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "file_shares")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FileShare {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "file_id", nullable = false)
    private BackupFile file;

    @ManyToOne
    @JoinColumn(name = "shared_with_user_id")
    private User sharedWithUser;

    private String publicToken;

    @Enumerated(EnumType.STRING)
    private Permission permission = Permission.READ;

    private Instant expiresAt;

    public enum Permission { READ, WRITE }
}
