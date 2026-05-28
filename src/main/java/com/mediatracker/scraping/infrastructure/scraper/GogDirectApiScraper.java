package com.mediatracker.scraping.infrastructure.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediatracker.scraping.domain.model.StorePrice;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class GogDirectApiScraper {
    
    private static final String GOG_API_URL = "https://catalog.gog.com/v1/catalog";
    
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(chain -> {
                Request original = chain.request();
                Request request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .header("Accept-Language", "es-ES,es;q=0.9")
                    .build();
                return chain.proceed(request);
            })
            .build();
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    public StorePrice searchGame(String gameName) {
        StorePrice result = new StorePrice();
        result.setStoreName("GOG");
        result.setAvailable(false);
        
        if (gameName == null || gameName.trim().isEmpty()) {
            return result;
        }
        
        System.out.println("🔍 Buscando en GOG API: " + gameName);
        
        try {
            String searchTerm = URLEncoder.encode(gameName, StandardCharsets.UTF_8);
            String url = GOG_API_URL + "?limit=5&search=" + searchTerm + 
                         "&order=desc:score&countryCode=ES&locale=es-ES";
            
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    JsonNode root = mapper.readTree(json);
                    JsonNode products = root.path("products");
                    
                    if (products.isArray() && products.size() > 0) {
                        String normalizedSearch = normalizeString(gameName);
                        JsonNode bestMatch = null;
                        String bestMatchTitle = null;
                        BigDecimal bestMatchPrice = null;
                        
                        for (JsonNode product : products) {
                            String title = product.path("title").asText();
                            String normalizedTitle = normalizeString(title);
                            
                            if (normalizedTitle.equals(normalizedSearch) || 
                                normalizedTitle.startsWith(normalizedSearch)) {
                                bestMatch = product;
                                bestMatchTitle = title;
                                
                                // Extraer precio
                                JsonNode price = product.path("price");
                                if (!price.isMissingNode()) {
                                    String amount = price.path("amount").asText();
                                    if (!amount.isEmpty()) {
                                        bestMatchPrice = new BigDecimal(amount);
                                    }
                                }
                                break;
                            }
                        }
                        
                        if (bestMatch == null && products.size() > 0) {
                            bestMatch = products.get(0);
                            bestMatchTitle = bestMatch.path("title").asText();
                            JsonNode price = bestMatch.path("price");
                            if (!price.isMissingNode()) {
                                String amount = price.path("amount").asText();
                                if (!amount.isEmpty()) {
                                    bestMatchPrice = new BigDecimal(amount);
                                }
                            }
                        }
                        
                        if (bestMatch != null && bestMatchPrice != null) {
                            result.setTitle(bestMatchTitle);
                            result.setCurrentPrice(bestMatchPrice);
                            result.setCurrency("EUR");
                            result.setAvailable(true);
                            
                            String slug = bestMatch.path("slug").asText();
                            result.setUrl("https://www.gog.com/game/" + slug);
                            
                            // Buscar precio original (descuento)
                            JsonNode originalPrice = bestMatch.path("originalPrice");
                            if (!originalPrice.isMissingNode()) {
                                BigDecimal original = new BigDecimal(originalPrice.path("amount").asText());
                                if (original.compareTo(bestMatchPrice) > 0) {
                                    result.setOriginalPrice(original);
                                    int discount = (int) ((original.doubleValue() - bestMatchPrice.doubleValue()) 
                                            / original.doubleValue() * 100);
                                    result.setDiscountPercent(discount);
                                }
                            }
                            
                            System.out.println("   ✅ GOG: " + bestMatchPrice + "€ - " + bestMatchTitle);
                            return result;
                        }
                    }
                }
            }
            
            System.out.println("   ❌ No se encontró el juego en GOG API");
            
        } catch (IOException e) {
            System.err.println("Error en GOG API: " + e.getMessage());
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
