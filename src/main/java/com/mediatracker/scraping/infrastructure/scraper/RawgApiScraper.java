package com.mediatracker.scraping.infrastructure.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RawgApiScraper {
    
    private static final String BASE_URL = "https://api.rawg.io/api";
    
    @Value("${rawg.api.key:7e0d3bb4fbc24ef68188538513be11a5}")
    private String apiKey;
    
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Búsqueda FLEXIBLE - obtiene muchos resultados y filtra localmente
     */
    public List<RawgGame> searchGamesFlexible(String query, int limit) {
        List<RawgGame> games = new ArrayList<>();
        
        if (query == null || query.trim().isEmpty()) {
            return games;
        }
        
        String lowerQuery = query.toLowerCase().trim();
        System.out.println("🔍 Búsqueda flexible RAWg: '" + query + "'");
        
        // Buscar en múltiples páginas para obtener más resultados
        int page = 1;
        int pageSize = 40; // Máximo por página
        
        while (games.size() < limit && page <= 3) { // Máximo 3 páginas
            String url = BASE_URL + "/games?key=" + apiKey + 
                        "&search=" + urlEncode(query) + 
                        "&page_size=" + pageSize + 
                        "&page=" + page;
            
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "MediaPriceTracker/1.0")
                        .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        System.err.println("Error RAWg API: " + response.code());
                        break;
                    }
                    
                    String json = response.body().string();
                    JsonNode root = mapper.readTree(json);
                    JsonNode results = root.get("results");
                    
                    if (results == null || !results.isArray() || results.size() == 0) {
                        break;
                    }
                    
                    for (JsonNode gameNode : results) {
                        RawgGame game = parseGame(gameNode);
                        // Añadir todos los resultados que contengan la palabra clave
                        if (game.getName().toLowerCase().contains(lowerQuery) || 
                            lowerQuery.contains(game.getName().toLowerCase())) {
                            games.add(game);
                        }
                    }
                    
                    // Verificar si hay más páginas
                    if (!root.has("next") || root.get("next").isNull()) {
                        break;
                    }
                    page++;
                }
            } catch (IOException e) {
                System.err.println("Error en RAWg API: " + e.getMessage());
                break;
            }
        }
        
        System.out.println("   📊 Resultados encontrados: " + games.size());
        return games;
    }
    
    /**
     * Búsqueda EXACTA - para GOG y comparativas
     */
    public RawgGame searchExactGame(String exactName) {
        if (exactName == null || exactName.trim().isEmpty()) {
            return null;
        }
        
        List<RawgGame> results = searchGamesFlexible(exactName, 30);
        String normalizedSearch = normalizeString(exactName);
        
        // Primero buscar coincidencia exacta
        for (RawgGame game : results) {
            String normalizedName = normalizeString(game.getName());
            if (normalizedName.equals(normalizedSearch)) {
                System.out.println("✅ Coincidencia exacta: " + game.getName());
                return game;
            }
        }
        
        // Si no hay exacta, devolver el primero que contenga la búsqueda
        if (!results.isEmpty()) {
            System.out.println("⚠️ Usando: " + results.get(0).getName());
            return results.get(0);
        }
        
        return null;
    }
    
    private RawgGame parseGame(JsonNode node) {
        RawgGame game = new RawgGame();
        
        game.setId(node.path("id").asInt());
        game.setSlug(node.path("slug").asText());
        game.setName(node.path("name").asText());
        game.setBackgroundImage(node.path("background_image").asText());
        game.setMetacritic(node.path("metacritic").asInt());
        game.setReleaseDate(node.path("released").asText());
        
        // Géneros
        JsonNode genres = node.path("genres");
        for (JsonNode genre : genres) {
            game.getGenres().add(genre.path("name").asText());
        }
        
        // Extraer Steam ID
        JsonNode stores = node.path("stores");
        for (JsonNode store : stores) {
            JsonNode storeData = store.path("store");
            String storeName = storeData.path("name").asText().toLowerCase();
            String storeUrl = store.path("url").asText();
            
            if (storeName.contains("steam") && storeUrl != null) {
                String steamId = extractSteamIdFromUrl(storeUrl);
                if (steamId != null) {
                    game.setSteamId(steamId);
                    break;
                }
            }
        }
        
        return game;
    }
    
    private String extractSteamIdFromUrl(String url) {
        if (url == null) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/app/(\\d+)");
        var matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }
    
    private String normalizeString(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replace(":", "")
                .replace("-", " ")
                .replace("'", "")
                .replace("é", "e")
                .replace("è", "e")
                .replace("á", "a")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ñ", "n")
                .replaceAll("[^a-z0-9 ]", "")
                .trim()
                .replaceAll("\\s+", " ");
    }
    
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
    
    public static class RawgGame {
        private int id;
        private String name;
        private String slug;
        private String backgroundImage;
        private String steamId;
        private int metacritic;
        private String releaseDate;
        private List<String> genres;
        
        public RawgGame() {
            this.genres = new ArrayList<>();
        }
        
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getBackgroundImage() { return backgroundImage; }
        public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }
        public String getSteamId() { return steamId; }
        public void setSteamId(String steamId) { this.steamId = steamId; }
        public int getMetacritic() { return metacritic; }
        public void setMetacritic(int metacritic) { this.metacritic = metacritic; }
        public String getReleaseDate() { return releaseDate; }
        public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
        public List<String> getGenres() { return genres; }
        public void setGenres(List<String> genres) { this.genres = genres; }
    }
}
