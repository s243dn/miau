package com.mediatracker.scraping.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class EpicFreeGamesService {

    private static final String EPIC_FREE_GAMES_API = "https://store-site-backend-static.ak.epicgames.com/freeGamesPromotions?locale=es-ES&country=ES&allowCountries=ES";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<FreeGameDto> getCurrentFreeGames() {
        List<FreeGameDto> freeGames = new ArrayList<>();
        Request request = new Request.Builder()
                .url(EPIC_FREE_GAMES_API)
                .header("User-Agent", "MediaPriceTracker/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return freeGames;

            String json = response.body().string();
            JsonNode root = mapper.readTree(json);
            JsonNode elements = root.path("data").path("Catalog").path("searchStore").path("elements");

            for (JsonNode game : elements) {
                JsonNode price = game.path("price").path("totalPrice");
                int discountPrice = price.path("discountPrice").asInt();
                if (discountPrice == 0) {
                    FreeGameDto dto = new FreeGameDto();
                    dto.setTitle(game.path("title").asText());
                    dto.setDescription(game.path("description").asText());
                    dto.setUrl("https://store.epicgames.com/es-ES/p/" + game.path("slug").asText());
                    dto.setImageUrl(game.path("keyImages").get(0).path("url").asText());

                    JsonNode promotion = game.path("promotions").path("promotionalOffers").get(0).path("promotionalOffers").get(0);
                    long endDateMillis = promotion.path("endDate").asLong();
                    dto.setExpiresAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(endDateMillis), ZoneId.systemDefault()));

                    freeGames.add(dto);
                }
            }
        } catch (IOException e) {
            System.err.println("Error obteniendo juegos gratis de Epic: " + e.getMessage());
        }
        return freeGames;
    }

    public static class FreeGameDto {
        private String title, description, url, imageUrl;
        private LocalDateTime expiresAt;

        // Getters y Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    }
}
