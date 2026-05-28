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
public class FreeGamesService {

    private static final String FREE_GAMES_API = "https://api.egdata.app/free-games?country=ES";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public List<FreeGameDto> getCurrentFreeGames() {
        List<FreeGameDto> freeGames = new ArrayList<>();

        Request request = new Request.Builder()
                .url(FREE_GAMES_API)
                .header("User-Agent", "MediaPriceTracker/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Error al obtener juegos gratis: HTTP " + response.code());
                return freeGames;
            }

            String json = response.body().string();
            JsonNode root = mapper.readTree(json);

            // La respuesta es un array directo
            if (root.isArray()) {
                for (JsonNode game : root) {
                    FreeGameDto dto = new FreeGameDto();
                    dto.setTitle(game.path("title").asText());
                    dto.setDescription(game.path("description").asText());
                    
                    // URL del juego en Epic Store
                    String url = game.path("url").asText();
                    if (url == null || url.isEmpty()) {
                        String productSlug = game.path("productSlug").asText();
                        if (productSlug != null && !productSlug.isEmpty()) {
                            url = "https://store.epicgames.com/p/" + productSlug;
                        }
                    }
                    dto.setUrl(url);
                    
                    // Imagen (buscar en keyImages)
                    String imageUrl = "";
                    JsonNode keyImages = game.path("keyImages");
                    for (JsonNode img : keyImages) {
                        String type = img.path("type").asText();
                        if ("OfferImageWide".equals(type) || "DieselStoreFrontWide".equals(type)) {
                            imageUrl = img.path("url").asText();
                            break;
                        }
                    }
                    dto.setImageUrl(imageUrl);
                    
                    // Fechas del giveaway
                    JsonNode giveaway = game.path("giveaway");
                    if (!giveaway.isMissingNode()) {
                        long startMillis = giveaway.path("startDate").asLong();
                        long endMillis = giveaway.path("endDate").asLong();
                        if (startMillis > 0) {
                            dto.setStartDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), ZoneId.systemDefault()));
                        }
                        if (endMillis > 0) {
                            dto.setEndDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis), ZoneId.systemDefault()));
                        }
                    }
                    
                    freeGames.add(dto);
                }
            }
        } catch (IOException e) {
            System.err.println("Error conectando con egdata.app: " + e.getMessage());
        }

        return freeGames;
    }

    public static class FreeGameDto {
        private String title, description, url, imageUrl;
        private LocalDateTime startDate, endDate;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    }
}
