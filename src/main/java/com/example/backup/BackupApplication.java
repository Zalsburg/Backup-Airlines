package com.example.backup;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.example.backup.model.*;
import com.example.backup.repository.*;

import java.util.Set;

@SpringBootApplication
@EnableScheduling
public class BackupApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackupApplication.class, args);
    }

    @Bean
    CommandLineRunner init(RoleRepository roleRepo,
                           UserRepository userRepo,
                           TagRepository tagRepo,
                           BCryptPasswordEncoder encoder) {
        return args -> {
            // Crear roles
            if (roleRepo.findByName("ROLE_ADMIN").isEmpty()) roleRepo.save(new Role(null, "ROLE_ADMIN"));
            if (roleRepo.findByName("ROLE_USER").isEmpty())  roleRepo.save(new Role(null, "ROLE_USER"));

            // Crear admin por defecto
            if (userRepo.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(encoder.encode("admin123"));
                admin.setFullName("Administrador");
                admin.setEnabled(true);
                admin.setRoles(Set.of(roleRepo.findByName("ROLE_ADMIN").get()));
                userRepo.save(admin);
            }

            // Crear usuario demo
            if (userRepo.findByUsername("usuario1").isEmpty()) {
                User u = new User();
                u.setUsername("usuario1");
                u.setPassword(encoder.encode("user1234"));
                u.setFullName("Usuario Demo");
                u.setEnabled(true);
                u.setRoles(Set.of(roleRepo.findByName("ROLE_USER").get()));
                userRepo.save(u);
            }

            // Crear tags de ejemplo
            for (String t : new String[]{"importante", "trabajo", "personal", "urgente"}) {
                if (tagRepo.findByName(t).isEmpty()) tagRepo.save(new Tag(null, t, null));
            }
        };
    }
}
