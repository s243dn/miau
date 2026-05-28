#!/bin/bash

# ============================================
# Script para crear MediaPriceTracker desde cero
# Estructura hexagonal + vertical slicing
# Basado en FG_Academy
# ============================================

set -e

echo "🚀 Creando MediaPriceTracker desde cero..."

# Colores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Eliminar proyecto anterior si existe
rm -rf src pom.xml

# Crear estructura base
mkdir -p src/main/java/com/mediatracker
mkdir -p src/main/resources/templates/auth
mkdir -p src/main/resources/templates/games
mkdir -p src/main/resources/templates/wishlist
mkdir -p src/main/resources/static/css
mkdir -p src/main/resources/static/js

# ============================================
# 1. POM.XML
# ============================================

cat > pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.1.5</version>
        <relativePath/>
    </parent>
    <groupId>com.mediatracker</groupId>
    <artifactId>mediapricetracker</artifactId>
    <version>1.0.0</version>
    <name>mediapricetracker</name>
    <properties><java.version>17</java.version></properties>
    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-thymeleaf</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
        <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>
        <dependency><groupId>org.jsoup</groupId><artifactId>jsoup</artifactId><version>1.17.2</version></dependency>
        <dependency><groupId>org.webjars</groupId><artifactId>bootstrap</artifactId><version>5.3.0</version></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    </dependencies>
    <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
</project>
EOF

echo -e "${GREEN}✅ pom.xml creado${NC}"

# ============================================
# 2. CLASE PRINCIPAL
# ============================================

cat > src/main/java/com/mediatracker/MediaTrackerApplication.java << 'EOF'
package com.mediatracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MediaTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MediaTrackerApplication.class, args);
        System.out.println("========================================");
        System.out.println("  MediaPriceTracker iniciado!");
        System.out.println("  http://localhost:8080");
        System.out.println("========================================");
    }
}
EOF

# ============================================
# 3. APPLICATION.PROPERTIES
# ============================================

cat > src/main/resources/application.properties << 'EOF'
server.port=8080
spring.datasource.url=jdbc:h2:mem:mediatracker
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.thymeleaf.cache=false
logging.level.org.springframework.web=INFO
EOF

# ============================================
# 4. SECURITY CONFIG
# ============================================

mkdir -p src/main/java/com/mediatracker/common/config

cat > src/main/java/com/mediatracker/common/config/SecurityConfig.java << 'EOF'
package com.mediatracker.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher("/"),
                    AntPathRequestMatcher.antMatcher("/login"),
                    AntPathRequestMatcher.antMatcher("/register"),
                    AntPathRequestMatcher.antMatcher("/h2-console/**"),
                    AntPathRequestMatcher.antMatcher("/css/**"),
                    AntPathRequestMatcher.antMatcher("/js/**"),
                    AntPathRequestMatcher.antMatcher("/webjars/**")
                ).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**"))
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
EOF

# ============================================
# 5. ENTIDADES JPA
# ============================================

mkdir -p src/main/java/com/mediatracker/auth/infraestructure/db/jpa/entity
mkdir -p src/main/java/com/mediatracker/auth/infraestructure/db/jpa/repository

cat > src/main/java/com/mediatracker/auth/infraestructure/db/jpa/entity/UserEntity.java << 'EOF'
package com.mediatracker.auth.infraestructure.db.jpa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(unique = true, nullable = false) private String username;
    @Column(unique = true, nullable = false) private String email;
    @Column(nullable = false) private String password;
    private String role = "USER";
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
EOF

cat > src/main/java/com/mediatracker/auth/infraestructure/db/jpa/repository/UserEntityJpaRepository.java << 'EOF'
package com.mediatracker.auth.infraestructure.db.jpa.repository;

import com.mediatracker.auth.infraestructure.db.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserEntityJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
EOF

# ============================================
# 6. USER DETAILS SERVICE
# ============================================

mkdir -p src/main/java/com/mediatracker/auth/infraestructure/security

cat > src/main/java/com/mediatracker/auth/infraestructure/security/CustomUserDetailsService.java << 'EOF'
package com.mediatracker.auth.infraestructure.security;

import com.mediatracker.auth.infraestructure.db.jpa.entity.UserEntity;
import com.mediatracker.auth.infraestructure.db.jpa.repository.UserEntityJpaRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserEntityJpaRepository userRepository;

    public CustomUserDetailsService(UserEntityJpaRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        return new User(user.getUsername(), user.getPassword(), Collections.emptyList());
    }
}
EOF

# ============================================
# 7. AUTH CONTROLLER
# ============================================

mkdir -p src/main/java/com/mediatracker/auth/infraestructure/web/controller

cat > src/main/java/com/mediatracker/auth/infraestructure/web/controller/AuthController.java << 'EOF'
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
    public String registerUser(@RequestParam String username,
                               @RequestParam String email,
                               @RequestParam String password,
                               Model model) {

        System.out.println("📝 Registrando usuario: " + username);

        if (userRepository.existsByUsername(username)) {
            model.addAttribute("error", "El nombre de usuario ya existe");
            return "auth/register";
        }

        if (userRepository.existsByEmail(email)) {
            model.addAttribute("error", "El email ya está registrado");
            return "auth/register";
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");

        userRepository.save(user);
        System.out.println("✅ Usuario registrado: " + username);

        return "redirect:/login?registered=true";
    }
}
EOF

# ============================================
# 8. THYMELEAF CONTROLLER (Páginas)
# ============================================

mkdir -p src/main/java/com/mediatracker/thym/infraestructure/web/controller

cat > src/main/java/com/mediatracker/thym/infraestructure/web/controller/PaginasController.java << 'EOF'
package com.mediatracker.thym.infraestructure.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaginasController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/games")
    public String games() {
        return "games/list";
    }

    @GetMapping("/wishlist")
    public String wishlist() {
        return "wishlist/list";
    }
}
EOF

# ============================================
# 9. VISTAS HTML
# ============================================

# index.html
cat > src/main/resources/templates/index.html << 'EOF'
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>MediaPriceTracker</title><link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet"></head>
<body><div class="container text-center mt-5"><h1>🎮 MediaPriceTracker</h1><p>Compara precios de videojuegos</p><a href="/login" class="btn btn-primary">Iniciar Sesión</a><a href="/register" class="btn btn-secondary">Registrarse</a></div></body>
</html>
EOF

# auth/login.html
cat > src/main/resources/templates/auth/login.html << 'EOF'
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Login</title><link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet"></head>
<body class="bg-light"><div class="container"><div class="row justify-content-center mt-5"><div class="col-md-4"><div class="card"><div class="card-header bg-primary text-white">Iniciar Sesión</div><div class="card-body"><div th:if="${param.registered != null}" class="alert alert-success">¡Registro exitoso!</div><div th:if="${param.error != null}" class="alert alert-danger">Usuario o contraseña incorrectos</div><form action="/login" method="post"><input type="text" name="username" class="form-control mb-2" placeholder="Usuario" required><input type="password" name="password" class="form-control mb-2" placeholder="Contraseña" required><button type="submit" class="btn btn-primary w-100">Ingresar</button></form><hr><a href="/register" class="btn btn-link w-100">Registrarse</a></div></div></div></div></div></body>
</html>
EOF

# auth/register.html
cat > src/main/resources/templates/auth/register.html << 'EOF'
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Registro</title><link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet"></head>
<body class="bg-light"><div class="container"><div class="row justify-content-center mt-5"><div class="col-md-4"><div class="card"><div class="card-header bg-success text-white">Registro</div><div class="card-body"><div th:if="${error}" class="alert alert-danger" th:text="${error}"></div><form action="/register" method="post"><input type="text" name="username" class="form-control mb-2" placeholder="Usuario" required><input type="email" name="email" class="form-control mb-2" placeholder="Email" required><input type="password" name="password" class="form-control mb-2" placeholder="Contraseña" required><button type="submit" class="btn btn-success w-100">Registrarse</button></form><hr><a href="/login" class="btn btn-link w-100">Ya tengo cuenta</a></div></div></div></div></div></body>
</html>
EOF

# dashboard.html
cat > src/main/resources/templates/dashboard.html << 'EOF'
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Dashboard</title><link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet"></head>
<body><nav class="navbar navbar-dark bg-dark"><div class="container"><a class="navbar-brand" href="/dashboard">MediaPriceTracker</a><a href="/logout" class="btn btn-outline-light">Cerrar Sesión</a></div></nav><div class="container mt-4"><h1>Dashboard</h1><p>Bienvenido</p><div class="row"><div class="col-md-4"><div class="card"><div class="card-body"><h5>🎮 Juegos</h5><a href="/games" class="btn btn-primary">Explorar</a></div></div></div><div class="col-md-4"><div class="card"><div class="card-body"><h5>❤️ Wishlist</h5><a href="/wishlist" class="btn btn-primary">Ver</a></div></div></div></div></div></body>
</html>
EOF

# games/list.html
cat > src/main/resources/templates/games/list.html << 'EOF'
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Juegos</title><link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet"></head>
<body><nav class="navbar navbar-dark bg-dark"><div class="container"><a class="navbar-brand" href="/dashboard">MediaPriceTracker</a><a href="/logout" class="btn btn-outline-light">Cerrar Sesión</a></div></nav><div class="container mt-4"><h1>Explorar Juegos</h1><div class="row"><div class="col-12"><p>Próximamente: juegos desde Steam</p></div></div></div></body>
</html>
EOF

# wishlist/list.html
cat > src/main/resources/templates/wishlist/list.html << 'EOF'
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Wishlist</title><link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet"></head>
<body><nav class="navbar navbar-dark bg-dark"><div class="container"><a class="navbar-brand" href="/dashboard">MediaPriceTracker</a><a href="/logout" class="btn btn-outline-light">Cerrar Sesión</a></div></nav><div class="container mt-4"><h1>Mi Wishlist</h1><div class="row"><div class="col-12"><p>No hay juegos en tu wishlist</p></div></div></div></body>
</html>
EOF

# ============================================
# 10. DATA LOADER (Usuarios por defecto)
# ============================================

mkdir -p src/main/java/com/mediatracker/common/config

cat > src/main/java/com/mediatracker/common/config/DataLoader.java << 'EOF'
package com.mediatracker.common.config;

import com.mediatracker.auth.infraestructure.db.jpa.entity.UserEntity;
import com.mediatracker.auth.infraestructure.db.jpa.repository.UserEntityJpaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private final UserEntityJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(UserEntityJpaRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!userRepository.existsByUsername("admin")) {
            UserEntity admin = new UserEntity();
            admin.setUsername("admin");
            admin.setEmail("admin@mediatracker.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole("ADMIN");
            userRepository.save(admin);
            System.out.println("✅ Usuario admin creado: admin / admin123");
        }

        if (!userRepository.existsByUsername("user")) {
            UserEntity user = new UserEntity();
            user.setUsername("user");
            user.setEmail("user@mediatracker.com");
            user.setPassword(passwordEncoder.encode("user123"));
            user.setRole("USER");
            userRepository.save(user);
            System.out.println("✅ Usuario user creado: user / user123");
        }

        System.out.println("✅ DataLoader finalizado");
    }
}
EOF

# ============================================
# FINALIZAR
# ============================================

echo -e "${GREEN}✅ Proyecto creado correctamente!${NC}"
echo ""
echo -e "${YELLOW}📋 Para ejecutar:${NC}"
echo "   cd /ruta/del/proyecto"
echo "   mvn clean spring-boot:run"
echo ""
echo -e "${YELLOW}🔐 Credenciales de prueba:${NC}"
echo "   admin / admin123"
echo "   user / user123"
echo ""
echo -e "${GREEN}🚀 Ejecuta ahora: mvn clean spring-boot:run${NC}"