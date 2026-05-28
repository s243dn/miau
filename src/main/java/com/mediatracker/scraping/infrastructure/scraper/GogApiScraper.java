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
public class GogApiScraper {
    
    private static final String GOG_CATALOG_API = "https://catalog.gog.com/v1/catalog";
    private static final String GOG_PRICE_API = "https://api.gog.com/products/prices";
    
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(chain -> {
                Request original = chain.request();
                Request request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .header("Accept-Language", "es-ES,es;q=0.9")
                    .header("Origin", "https://www.gog.com")
                    .header("Referer", "https://www.gog.com/")
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
        
        System.out.println("🎮 Buscando en GOG API: " + gameName);
        
        try {
            // PASO 1: Buscar el juego en el catálogo
            String searchUrl = GOG_CATALOG_API + "?search=" + URLEncoder.encode(gameName, StandardCharsets.UTF_8) 
                             + "&limit=5&order=desc:score&countryCode=ES&locale=es-ES";
            
            Request request = new Request.Builder().url(searchUrl).build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("GOG API error: " + response.code());
                    return result;
                }
                
                String json = response.body().string();
                JsonNode root = mapper.readTree(json);
                JsonNode products = root.path("products");
                
                if (products.isArray() && products.size() > 0) {
                    String gameId = null;
                    String gameTitle = null;
                    
                    // Buscar coincidencia exacta
                    for (JsonNode product : products) {
                        String title = product.path("title").asText();
                        if (title.equalsIgnoreCase(gameName)) {
                            gameId = product.path("id").asText();
                            gameTitle = title;
                            break;
                        }
                    }
                    
                    if (gameId == null) {
                        gameId = products.get(0).path("id").asText();
                        gameTitle = products.get(0).path("title").asText();
                    }
                    
                    result.setTitle(gameTitle);
                    result.setGameId(gameId);
                    result.setAvailable(true);
                    
                    // PASO 2: Obtener el precio
                    String priceUrl = GOG_PRICE_API + "?ids=" + gameId + "&countryCode=ES";
                    Request priceRequest = new Request.Builder().url(priceUrl).build();
                    
                    try (Response priceResponse = client.newCall(priceRequest).execute()) {
                        if (priceResponse.isSuccessful()) {
                            String priceJson = priceResponse.body().string();
                            JsonNode priceRoot = mapper.readTree(priceJson);
                            JsonNode prices = priceRoot.path("prices");
                            
                            for (JsonNode price : prices) {
                                JsonNode finalPrice = price.path("finalPrice");
                                if (!finalPrice.isMissingNode()) {
                                    BigDecimal amount = new BigDecimal(finalPrice.path("amount").asText());
                                    result.setCurrentPrice(amount);
                                    result.setCurrency(finalPrice.path("currency").asText("EUR"));
                                    result.setUrl("https://www.gog.com/game/" + gameId);
                                    break;
                                }
                            }
                        }
                    }
                    
                    System.out.println("   ✅ GOG: " + result.getCurrentPrice() + "€ - " + gameTitle);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error en GOG API: " + e.getMessage());
        }
        
        return result;
    }
}
