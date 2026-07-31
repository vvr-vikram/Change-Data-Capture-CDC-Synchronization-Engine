package com.example.cdcsync.config;

import com.example.cdcsync.model.User;
import com.example.cdcsync.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .role("ADMIN")
                    .build();
            userRepository.save(admin);
            log.info("Seeded default admin user: admin/admin123");
        }

        if (userRepository.findByUsername("operator").isEmpty()) {
            User operator = User.builder()
                    .username("operator")
                    .password(passwordEncoder.encode("operator123"))
                    .role("OPERATOR")
                    .build();
            userRepository.save(operator);
            log.info("Seeded default operator user: operator/operator123");
        }
    }
}
