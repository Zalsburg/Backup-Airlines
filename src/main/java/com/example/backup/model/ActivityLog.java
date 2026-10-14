package com.example.backup.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "activity_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String action;

    @ManyToOne
    @JoinColumn(name = "file_id")
    private BackupFile file;

    private Instant createdAt = Instant.now();

    public String getCreatedAtFormatted() {
        if (createdAt == null) return "—";
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .withZone(ZoneId.of("America/Bogota"))
                .format(createdAt);
    }
}
