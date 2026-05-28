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
