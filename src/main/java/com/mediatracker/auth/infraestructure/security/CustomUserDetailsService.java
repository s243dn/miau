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
