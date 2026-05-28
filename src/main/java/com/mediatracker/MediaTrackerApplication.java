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
