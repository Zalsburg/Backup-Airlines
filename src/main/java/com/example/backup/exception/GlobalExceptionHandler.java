package com.example.backup.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleSecurity(SecurityException e, Model model) {
        model.addAttribute("error", "Acceso denegado: " + e.getMessage());
        model.addAttribute("status", 403);
        return "error";
    }

    @ExceptionHandler({FileNotFoundException.class, NoSuchFileException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(Exception e, Model model) {
        model.addAttribute("error", "Recurso no encontrado.");
        model.addAttribute("status", 404);
        return "error";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSize(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "¡El archivo excede el tamaño máximo permitido de 50 MB!");
        return "redirect:/dashboard";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneral(Exception e, Model model) {
        model.addAttribute("error", "Error interno: " + e.getMessage());
        model.addAttribute("status", 500);
        return "error";
    }
}
