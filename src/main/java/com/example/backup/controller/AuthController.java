package com.example.backup.controller;

import com.example.backup.model.Role;
import com.example.backup.model.User;
import com.example.backup.repository.RoleRepository;
import com.example.backup.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.Set;

@Controller
public class AuthController {
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final BCryptPasswordEncoder encoder;

    public AuthController(UserRepository userRepo, RoleRepository roleRepo, BCryptPasswordEncoder encoder){
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.encoder = encoder;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model m){
        m.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerSubmit(@Valid @ModelAttribute("user") User user,
                                 BindingResult bindingResult,
                                 Model model) {

        // 1️ Validación: campos vacíos
        if(bindingResult.hasErrors()){
            model.addAttribute("error", "Por favor, completa todos los campos correctamente.");
            return "register";
        }

        // 2️ Validación: usuario ya existe
        if(userRepo.findByUsername(user.getUsername()).isPresent()){
            model.addAttribute("error", "El nombre de usuario ya está registrado.");
            return "register";
        }

        // 3️ Validación: contraseña mínima 6 caracteres
        if(user.getPassword().length() < 6){
            model.addAttribute("error", "La contraseña debe tener al menos 6 caracteres.");
            return "register";
        }

        // 4️ Guardar usuario con rol y contraseña encriptada
        user.setPassword(encoder.encode(user.getPassword()));
        Role userRole = roleRepo.findByName("ROLE_USER")
                .orElseGet(() -> roleRepo.save(new Role(null,"ROLE_USER")));
        user.setRoles(Set.of(userRole));
        user.setEnabled(true);
        userRepo.save(user);

        return "redirect:/login?registered";
    }
}
