package com.mediatracker.scraping.infrastructure.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediatracker.scraping.domain.model.StorePrice;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class SteamDatabaseScraper {
    
    private static final String STEAM_API_URL = "https://store.steampowered.com/api/appdetails";
    private static final String STEAM_SEARCH_URL = "https://store.steampowered.com/search/";
    
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Busca un juego en Steam por nombre exacto
     */
    public StorePrice searchGameByName(String gameName, String countryCode) {
        StorePrice result = new StorePrice();
        result.setStoreName("Steam");
        result.setAvailable(false);
        
        if (gameName == null || gameName.isEmpty()) {
            return result;
        }
        
        try {
            // Primero buscar el Steam ID por nombre
            String searchUrl = STEAM_SEARCH_URL + "?term=" + URLEncoder.encode(gameName, StandardCharsets.UTF_8);
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            
            String steamId = null;
            String foundTitle = null;
            
            // Buscar coincidencia exacta
            var results = doc.select("a.search_result_row");
            String normalizedSearch = normalizeString(gameName);
            
            for (var element : results) {
                String title = element.select(".title").text();
                String normalizedTitle = normalizeString(title);
                
                if (normalizedTitle.equals(normalizedSearch) || normalizedTitle.startsWith(normalizedSearch)) {
                    String href = element.attr("href");
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/app/(\\d+)");
                    var matcher = pattern.matcher(href);
                    if (matcher.find()) {
                        steamId = matcher.group(1);
                        foundTitle = title;
                        break;
                    }
                }
            }
            
            if (steamId != null) {
                result = getGameById(steamId, countryCode);
                result.setTitle(foundTitle);
            }
            
        } catch (IOException e) {
            System.err.println("Error buscando en Steam: " + e.getMessage());
        }
        
        return result;
    }
    
    public StorePrice getGameById(String steamId, String countryCode) {
        StorePrice result = new StorePrice();
        result.setStoreName("Steam");
        result.setAvailable(false);
        
        if (steamId == null || steamId.isEmpty()) {
            result.setErrorMessage("No Steam ID provided");
            return result;
        }
        
        result.setGameId(steamId);
        
        try {
            String url = STEAM_API_URL + "?appids=" + steamId + "&cc=" + countryCode;
            
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "MediaPriceTracker/1.0")
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    JsonNode root = mapper.readTree(json);
                    JsonNode data = root.path(steamId).path("data");
                    
                    if (!data.isMissingNode()) {
                        result.setTitle(data.path("name").asText());
                        result.setUrl("https://store.steampowered.com/app/" + steamId);
                        result.setAvailable(true);
                        
                        JsonNode priceOverview = data.path("price_overview");
                        if (!priceOverview.isMissingNode()) {
                            int finalPrice = priceOverview.path("final").asInt();
                            int initialPrice = priceOverview.path("initial").asInt();
                            int discount = priceOverview.path("discount_percent").asInt();
                            
                            result.setCurrentPrice(BigDecimal.valueOf(finalPrice / 100.0));
                            if (initialPrice > finalPrice) {
                                result.setOriginalPrice(BigDecimal.valueOf(initialPrice / 100.0));
                                result.setDiscountPercent(discount);
                            }
                            result.setCurrency(priceOverview.path("currency").asText("EUR"));
                        }
                    }
                }
            }
        } catch (IOException e) {
            result.setErrorMessage(e.getMessage());
            System.err.println("Error en Steam API: " + e.getMessage());
        }
        
        return result;
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
}
