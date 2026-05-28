package com.mediatracker.auth.infraestructure.web.controller;

import com.mediatracker.auth.infraestructure.db.jpa.entity.UserEntity;
import com.mediatracker.auth.infraestructure.db.jpa.repository.UserEntityJpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final UserEntityJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserEntityJpaRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        System.out.println("✅ AuthController inicializado");
    }

    @GetMapping("/login")
    public String showLoginForm() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegisterForm() {
        return "auth/register";
    }
    @PostMapping("/register")
public String registerUser(@RequestParam(required = false) String username,
                           @RequestParam(required = false) String email,
                           @RequestParam(required = false) String password,
                           Model model) {

    System.out.println("=========================================");
    System.out.println("🔥 POST /register RECIBIDO");
    System.out.println("Username: '" + username + "'");
    System.out.println("Email: '" + email + "'");
    System.out.println("Password: '" + password + "'");
    System.out.println("=========================================");

    // Validar que los parámetros no sean null
    if (username == null || username.isEmpty()) {
        model.addAttribute("error", "El usuario es obligatorio");
        return "auth/register";
    }
    if (email == null || email.isEmpty()) {
        model.addAttribute("error", "El email es obligatorio");
        return "auth/register";
    }
    if (password == null || password.isEmpty()) {
        model.addAttribute("error", "La contraseña es obligatoria");
        return "auth/register";
    }

    try {
        // Verificar si ya existe
        boolean existsUsername = userRepository.existsByUsername(username);
        System.out.println("¿Existe username? " + existsUsername);
        
        if (existsUsername) {
            model.addAttribute("error", "El nombre de usuario ya existe");
            return "auth/register";
        }

        boolean existsEmail = userRepository.existsByEmail(email);
        System.out.println("¿Existe email? " + existsEmail);
        
        if (existsEmail) {
            model.addAttribute("error", "El email ya está registrado");
            return "auth/register";
        }

        // Crear y guardar usuario
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        String encodedPassword = passwordEncoder.encode(password);
        System.out.println("Contraseña encriptada: " + encodedPassword);
        user.setPassword(encodedPassword);
        user.setRole("USER");

        System.out.println("Guardando en BD...");
        UserEntity saved = userRepository.save(user);
        System.out.println("✅ Usuario guardado con ID: " + saved.getId());
        
        // Verificar que se guardó
        System.out.println("Total usuarios en BD: " + userRepository.count());

    } catch (Exception e) {
        System.out.println("❌ ERROR: " + e.getMessage());
        e.printStackTrace();
        model.addAttribute("error", "Error interno: " + e.getMessage());
        return "auth/register";
    }

    System.out.println("Redirigiendo a login");
    return "redirect:/login?registered=true";
}
}
