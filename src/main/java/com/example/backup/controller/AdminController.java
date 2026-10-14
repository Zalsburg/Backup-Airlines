package com.example.backup.controller;

import com.example.backup.model.*;
import com.example.backup.repository.*;
import com.example.backup.service.ActivityLogService;
import com.example.backup.service.FileStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final UserRepository userRepo;
    private final BackupFileRepository fileRepo;
    private final FileStorageService fileStorageService;
    private final ActivityLogService activityLogService;
    private final RoleRepository roleRepo;
    private final BCryptPasswordEncoder encoder;

    public AdminController(UserRepository userRepo, BackupFileRepository fileRepo,
                           FileStorageService fileStorageService, ActivityLogService activityLogService,
                           RoleRepository roleRepo, BCryptPasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.fileRepo = fileRepo;
        this.fileStorageService = fileStorageService;
        this.activityLogService = activityLogService;
        this.roleRepo = roleRepo;
        this.encoder = encoder;
    }

    @GetMapping("/users")
    public String users(Model m) {
        m.addAttribute("users", userRepo.findAll());
        return "admin-users";
    }

    @GetMapping("/files")
    public String files(Model m) {
        m.addAttribute("files", fileRepo.findAll());
        return "admin-files";
    }

    @GetMapping("/logs")
    public String logs(Model m) {
        m.addAttribute("logs", activityLogService.getRecent());
        return "admin-logs";
    }

    /**
     * Exporta la bitácora completa en CSV para el componente de minería de datos.
     * Columnas: id,created_at,username,rol,action,file_id (mismo formato que
     * data/raw/activity_logs_simulados.csv). Solo accesible con ROLE_ADMIN (/admin/**).
     */
    @GetMapping("/logs/export.csv")
    public ResponseEntity<byte[]> exportLogs() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("America/Bogota"));
        StringBuilder csv = new StringBuilder("id,created_at,username,rol,action,file_id\n");
        for (ActivityLog log : activityLogService.getAllChronological()) {
            User u = log.getUser();
            String username = u != null ? u.getUsername() : "";
            String rol = (u != null && u.getRoles() != null && !u.getRoles().isEmpty())
                    ? u.getRoles().iterator().next().getName() : "";
            String fileId = log.getFile() != null ? String.valueOf(log.getFile().getId()) : "";
            String fecha = log.getCreatedAt() != null ? fmt.format(log.getCreatedAt()) : "";
            csv.append(log.getId()).append(',')
               .append(fecha).append(',')
               .append(username).append(',')
               .append(rol).append(',')
               .append(log.getAction() != null ? log.getAction() : "").append(',')
               .append(fileId).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity_logs.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/file/{id}/delete")
    public String deleteFile(@PathVariable Long id, RedirectAttributes ra) throws IOException {
        BackupFile f = fileRepo.findById(id).orElseThrow();
        fileStorageService.permanentDelete(f);
        ra.addFlashAttribute("message", "Archivo eliminado.");
        return "redirect:/admin/files";
    }

    @PostMapping("/user/create")
    public String createUser(@RequestParam String username,
                             @RequestParam String fullName,
                             @RequestParam String password,
                             @RequestParam String role,
                             RedirectAttributes ra) {
        if (userRepo.findByUsername(username).isPresent()) {
            ra.addFlashAttribute("error", "El usuario ya existe.");
            return "redirect:/admin/users";
        }
        User u = new User();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setPassword(encoder.encode(password));
        u.setEnabled(true);
        Role r = roleRepo.findByName(role).orElseGet(() -> roleRepo.save(new Role(null, role)));
        u.setRoles(Set.of(r));
        userRepo.save(u);
        ra.addFlashAttribute("message", "Usuario creado: " + username);
        return "redirect:/admin/users";
    }

    @PostMapping("/user/{id}/toggle")
    public String toggleUser(@PathVariable Long id, RedirectAttributes ra) {
        User u = userRepo.findById(id).orElseThrow();
        u.setEnabled(!u.isEnabled());
        userRepo.save(u);
        ra.addFlashAttribute("message", "Estado actualizado para " + u.getUsername());
        return "redirect:/admin/users";
    }

    @PostMapping("/user/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes ra) {
        userRepo.deleteById(id);
        ra.addFlashAttribute("message", "Usuario eliminado.");
        return "redirect:/admin/users";
    }
}
