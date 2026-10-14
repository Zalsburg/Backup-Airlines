package com.example.backup.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message    = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        int status = 500;
        if (statusCode != null) {
            status = Integer.parseInt(statusCode.toString());
        }

        String errorMsg;
        if (status == 403) {
            errorMsg = "No tienes permiso para acceder a este recurso.";
        } else if (status == 404) {
            errorMsg = "La página o recurso que buscas no existe.";
        } else {
            errorMsg = (message != null && !message.toString().isBlank())
                    ? message.toString()
                    : "Ocurrió un error inesperado en el servidor.";
        }

        model.addAttribute("status", status);
        model.addAttribute("error", errorMsg);
        return "error";
    }
}
