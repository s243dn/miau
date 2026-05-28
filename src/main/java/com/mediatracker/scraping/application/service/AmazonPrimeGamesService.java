package com.mediatracker.scraping.application.service;

import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
public class AmazonPrimeGamesService {

    public List<PrimeGameDto> getCurrentPrimeGames() {
        return List.of(
                new PrimeGameDto("Mafia II: Definitive Edition", "Vive la vida de un gánster en la década de 1940-50 en esta historia de crimen y lealtad.", "https://www.gog.com/game/mafia_ii_definitive_edition", "GOG", LocalDate.now().plusDays(15)),
                new PrimeGameDto("Fruitbus", "Un simulador único donde conduces un vibrante camión de frutas por el mundo.", "https://www.gog.com/game/fruitbus", "GOG", LocalDate.now().plusDays(15)),
                new PrimeGameDto("Moon Mystery", "Un juego de disparos y puzles con una historia que te mantendrá al borde del asiento.", "https://store.epicgames.com/es-ES/p/moon-mystery", "Epic Games", LocalDate.now().plusDays(22)),
                new PrimeGameDto("Pro Basketball Manager 2026", "Toma las riendas de un equipo de baloncesto y conviértelo en campeón.", "https://www.amazon.com/gp/browse.html?node=23857483011", "Amazon Games App", LocalDate.now().plusDays(22))
        );
    }

    public static class PrimeGameDto {
        private String title, description, url, platform;
        private LocalDate expiresAt;

        public PrimeGameDto(String title, String description, String url, String platform, LocalDate expiresAt) {
            this.title = title; this.description = description; this.url = url; this.platform = platform; this.expiresAt = expiresAt;
        }

        // Getters
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getUrl() { return url; }
        public String getPlatform() { return platform; }
        public LocalDate getExpiresAt() { return expiresAt; }
    }
}
