package com.example.backup.controller;

import com.example.backup.model.*;
import com.example.backup.repository.*;
import com.example.backup.service.ActivityLogService;
import com.example.backup.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.*;

@Controller
public class FileController {

    private final FileStorageService fileService;
    private final BackupFileRepository fileRepo;
    private final FileVersionRepository fileVersionRepo;
    private final UserRepository userRepo;
    private final FolderRepository folderRepo;
    private final TagRepository tagRepo;
    private final FileShareRepository fileShareRepo;
    private final ActivityLogService activityLogService;

    public FileController(FileStorageService fileService,
                          BackupFileRepository fileRepo,
                          FileVersionRepository fileVersionRepo,
                          UserRepository userRepo,
                          FolderRepository folderRepo,
                          TagRepository tagRepo,
                          FileShareRepository fileShareRepo,
                          ActivityLogService activityLogService) {
        this.fileService = fileService;
        this.fileRepo = fileRepo;
        this.fileVersionRepo = fileVersionRepo;
        this.userRepo = userRepo;
        this.folderRepo = folderRepo;
        this.tagRepo = tagRepo;
        this.fileShareRepo = fileShareRepo;
        this.activityLogService = activityLogService;
    }

    private User currentUser(Principal principal) {
        return userRepo.findByUsername(principal.getName())
                .orElseThrow(() -> new SecurityException("Usuario no encontrado"));
    }

    // ===================== DASHBOARD =====================
    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model m, Principal principal) {
        User user = currentUser(principal);
        List<BackupFile> files = fileRepo.findByOwnerIdAndTrashedFalse(user.getId());
        List<Folder> folders = folderRepo.findByOwnerIdAndParentIsNull(user.getId());
        List<BackupFile> sharedWithMe = new ArrayList<>();
        fileShareRepo.findBySharedWithUserId(user.getId()).forEach(s -> sharedWithMe.add(s.getFile()));

        m.addAttribute("files", files);
        m.addAttribute("folders", folders);
        m.addAttribute("sharedWithMe", sharedWithMe);
        m.addAttribute("username", user.getUsername());
        m.addAttribute("isAdmin", user.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_ADMIN")));
        return "dashboard";
    }

    // ===================== UPLOAD =====================
    @PostMapping("/upload")
    public String upload(@RequestParam("files") MultipartFile[] files,
                         @RequestParam(value = "folderId", required = false) Long folderId,
                         Principal principal,
                         RedirectAttributes redirectAttributes) throws IOException {
        User user = currentUser(principal);
        Folder folder = (folderId != null) ? folderRepo.findById(folderId).orElse(null) : null;
        for (MultipartFile f : files) {
            if (f.isEmpty()) continue;
            BackupFile bf = fileService.store(f, user);
            if (folder != null) { bf.setFolder(folder); fileRepo.save(bf); }
        }
        redirectAttributes.addFlashAttribute("message", "Archivos subidos correctamente.");
        return "redirect:/dashboard";
    }

    // ===================== DESCARGA / PREVISUALIZACIÓN =====================
    @GetMapping("/file/{id}/download")
    @ResponseBody
    public ResponseEntity<Resource> download(@PathVariable Long id, Principal principal) throws IOException {
        BackupFile bf = fileRepo.findById(id).orElseThrow();
        User user = currentUser(principal);
        if (!bf.getOwner().getUsername().equals(principal.getName()) && !isSharedWith(bf, user))
            throw new SecurityException("No autorizado");

        activityLogService.log(user, "DOWNLOAD_FILE", bf);
        Resource res = fileService.loadAsResource(bf.getStorageFilename());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + bf.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(
                        bf.getContentType() == null ? "application/octet-stream" : bf.getContentType()))
                .body(res);
    }

    @GetMapping("/file/{id}/preview")
    @ResponseBody
    public ResponseEntity<Resource> preview(@PathVariable Long id, Principal principal) throws IOException {
        BackupFile bf = fileRepo.findById(id).orElseThrow();
        User user = currentUser(principal);
        if (!bf.getOwner().getUsername().equals(principal.getName()) && !isSharedWith(bf, user))
            throw new SecurityException("No autorizado");

        activityLogService.log(user, "PREVIEW_FILE", bf);
        Resource res = fileService.loadAsResource(bf.getStorageFilename());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + bf.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(
                        bf.getContentType() == null ? "application/octet-stream" : bf.getContentType()))
                .body(res);
    }

    // ===================== LINK PÚBLICO =====================
    @GetMapping("/public/{token}")
    @ResponseBody
    public ResponseEntity<Resource> publicDownload(@PathVariable String token) throws IOException {
        FileShare share = fileShareRepo.findByPublicToken(token)
                .orElseThrow(() -> new SecurityException("Enlace inválido o expirado"));
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(Instant.now()))
            throw new SecurityException("Enlace expirado");

        BackupFile bf = share.getFile();
        Resource res = fileService.loadAsResource(bf.getStorageFilename());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + bf.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(
                        bf.getContentType() == null ? "application/octet-stream" : bf.getContentType()))
                .body(res);
    }

    // ===================== COMPARTIR =====================
    @PostMapping("/file/{id}/share")
    public String shareFile(@PathVariable Long id,
                            @RequestParam(value = "username", required = false) String username,
                            @RequestParam(value = "makePublic", required = false) Boolean makePublic,
                            Principal principal,
                            RedirectAttributes ra) {
        BackupFile bf = fileRepo.findById(id).orElseThrow();
        if (!bf.getOwner().getUsername().equals(principal.getName()))
            throw new SecurityException("No autorizado");

        if (Boolean.TRUE.equals(makePublic)) {
            FileShare share = new FileShare();
            share.setFile(bf);
            share.setPublicToken(UUID.randomUUID().toString());
            share.setPermission(FileShare.Permission.READ);
            share.setExpiresAt(Instant.now().plusSeconds(7 * 24 * 3600));
            fileShareRepo.save(share);
            ra.addFlashAttribute("message", "Enlace público creado.");
        } else if (username != null && !username.isBlank()) {
            userRepo.findByUsername(username).ifPresentOrElse(target -> {
                FileShare share = new FileShare();
                share.setFile(bf);
                share.setSharedWithUser(target);
                share.setPermission(FileShare.Permission.READ);
                fileShareRepo.save(share);
            }, () -> ra.addFlashAttribute("error", "Usuario no encontrado: " + username));
            if (!ra.getFlashAttributes().containsKey("error"))
                ra.addFlashAttribute("message", "Archivo compartido con " + username);
        }

        User user = currentUser(principal);
        activityLogService.log(user, "SHARE_FILE", bf);
        return "redirect:/dashboard";
    }

    // ===================== FAVORITO =====================
    @PostMapping("/file/{id}/favorite")
    public String toggleFavorite(@PathVariable Long id, Principal principal) {
        BackupFile bf = fileRepo.findById(id).orElseThrow();
        if (!bf.getOwner().getUsername().equals(principal.getName()))
            throw new SecurityException("No autorizado");
        bf.setFavorite(!bf.isFavorite());
        fileRepo.save(bf);
        return "redirect:/dashboard";
    }

    // ===================== PAPELERA =====================
    @PostMapping("/file/{id}/trash")
    public String trash(@PathVariable Long id, Principal principal) {
        BackupFile bf = fileRepo.findById(id).orElseThrow();
        if (!bf.getOwner().getUsername().equals(principal.getName()))
            throw new SecurityException("No autorizado");
        fileService.softDelete(bf, currentUser(principal));
        return "redirect:/dashboard";
    }

    @GetMapping("/trash")
    public String trashView(Model m, Principal principal) {
        User user = currentUser(principal);
        List<BackupFile> trashed = fileRepo.findByOwnerIdAndTrashedTrue(user.getId());
        m.addAttribute("trashed", trashed);
        return "trash";
    }

    @PostMapping("/file/{id}/restore")
    public String restore(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        BackupFile bf = fileRepo.findById(id).orElseThrow();
        if (!bf.getOwner().getUsername().equals(principal.getName()))
            throw new SecurityException("No autorizado");
        fileService.restore(bf);
        ra.addFlashAttribute("message", "Archivo restaurado correctamente.");
        return "redirect:/trash";
    }

    @PostMapping("/file/{id}/delete")
    public String permanentDelete(@PathVariable Long id, Principal principal, RedirectAttributes ra) throws IOException {
        BackupFile bf = fileRepo.findById(id).orElseThrow();
        if (!bf.getOwner().getUsername().equals(principal.getName()))
            throw new SecurityException("No autorizado");
        fileService.permanentDelete(bf);
        ra.addFlashAttribute("message", "Archivo eliminado permanentemente.");
        return "redirect:/trash";
    }

    // ===================== VERSIONES =====================
    @GetMapping("/file/{id}/versions")
    public String versions(@PathVariable Long id, Model m, Principal principal) {
        BackupFile bf = fileRepo.findById(id).orElseThrow();
        if (!bf.getOwner().getUsername().equals(principal.getName()))
            throw new SecurityException("No autorizado");
        m.addAttribute("versions", fileVersionRepo.findByBackupFileIdOrderByCreatedAtDesc(bf.getId()));
        m.addAttribute("file", bf);
        return "file-versions";
    }

    @PostMapping("/file/{fileId}/version/{versionId}/restore")
    public String restoreVersion(@PathVariable Long fileId, @PathVariable Long versionId, Principal principal) throws IOException {
        BackupFile bf = fileRepo.findById(fileId).orElseThrow();
        if (!bf.getOwner().getUsername().equals(principal.getName()))
            throw new SecurityException("No autorizado");
        FileVersion v = fileVersionRepo.findById(versionId).orElseThrow();
        fileService.restoreVersionAsNew(v, bf);
        activityLogService.log(currentUser(principal), "RESTORE_VERSION", bf);
        return "redirect:/file/" + fileId + "/versions";
    }

    // ===================== CARPETAS =====================
    @PostMapping("/folder/create")
    public String createFolder(@RequestParam String name,
                               @RequestParam(required = false) Long parentId,
                               Principal principal, RedirectAttributes ra) {
        User user = currentUser(principal);
        Folder folder = new Folder();
        folder.setOwner(user);
        folder.setName(name);
        if (parentId != null) folderRepo.findById(parentId).ifPresent(folder::setParent);
        folderRepo.save(folder);
        ra.addFlashAttribute("message", "Carpeta creada: " + name);
        return "redirect:/dashboard";
    }

    // ===================== TAGS =====================
    @PostMapping("/file/{id}/tag")
    public String addTag(@PathVariable Long id, @RequestParam String tagName, Principal principal) {
        BackupFile bf = fileRepo.findById(id).orElseThrow();
        if (!bf.getOwner().getUsername().equals(principal.getName()))
            throw new SecurityException("No autorizado");
        Tag tag = tagRepo.findByName(tagName.toLowerCase())
                .orElseGet(() -> tagRepo.save(new Tag(null, tagName.toLowerCase(), null)));
        if (bf.getTags() == null) bf.setTags(new HashSet<>());
        bf.getTags().add(tag);
        fileRepo.save(bf);
        return "redirect:/dashboard";
    }

    // ===================== MANEJO FALLBACK GET =====================
    @GetMapping("/file/{id}/restore")
    public String handleGetRestore(@PathVariable Long id) { return "redirect:/trash"; }

    @GetMapping("/file/{id}/delete")
    public String handleGetDelete(@PathVariable Long id) { return "redirect:/trash"; }

    // ===================== HELPER =====================
    private boolean isSharedWith(BackupFile bf, User user) {
        return fileShareRepo.findByFileId(bf.getId()).stream()
                .anyMatch(s -> s.getSharedWithUser() != null && s.getSharedWithUser().getId().equals(user.getId()));
    }
}
