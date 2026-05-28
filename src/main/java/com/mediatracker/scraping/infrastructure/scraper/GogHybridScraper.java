package com.mediatracker.scraping.infrastructure.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediatracker.scraping.domain.model.StorePrice;
import io.github.bonigarcia.wdm.WebDriverManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class GogHybridScraper {
    
    private static final String GOGDB_SEARCH = "https://www.gogdb.org/products?search=";
    private static final String GOG_PRICE_API = "https://api.gog.com/products/prices";
    
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(chain -> {
                Request original = chain.request();
                Request request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json")
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
        
        System.out.println("\n🎮 GOG Hybrid: " + gameName);
        
        // PASO 1: Obtener ID desde gogdb.org (con Selenium)
        String gameId = fetchGameIdFromGogdb(gameName);
        
        if (gameId == null) {
            System.out.println("   ❌ No se pudo obtener ID desde gogdb.org");
            return result;
        }
        
        System.out.println("   ✅ ID obtenido: " + gameId);
        result.setGameId(gameId);
        result.setTitle(gameName);
        result.setAvailable(true);
        
        // PASO 2: Obtener precio desde API de GOG usando el ID
        try {
            String priceUrl = GOG_PRICE_API + "?ids=" + gameId + "&countryCode=ES";
            
            Request request = new Request.Builder()
                    .url(priceUrl)
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    JsonNode root = mapper.readTree(json);
                    JsonNode prices = root.path("prices");
                    
                    for (JsonNode price : prices) {
                        JsonNode finalPrice = price.path("finalPrice");
                        if (!finalPrice.isMissingNode()) {
                            BigDecimal amount = new BigDecimal(finalPrice.path("amount").asText());
                            result.setCurrentPrice(amount);
                            result.setCurrency(finalPrice.path("currency").asText("EUR"));
                            result.setUrl("https://www.gog.com/game/" + gameId);
                            System.out.println("   💰 Precio: " + amount + "€");
                            break;
                        }
                    }
                } else {
                    System.err.println("   Error precio API: " + response.code());
                }
            }
        } catch (Exception e) {
            System.err.println("   Error obteniendo precio: " + e.getMessage());
        }
        
        return result;
    }
    
    private String fetchGameIdFromGogdb(String gameName) {
        WebDriver driver = null;
        
        try {
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
            
            String searchUrl = GOGDB_SEARCH + gameName.replace(" ", "+");
            System.out.println("   🔍 Buscando en gogdb: " + searchUrl);
            driver.get(searchUrl);
            Thread.sleep(3000);
            
            List<WebElement> rows = driver.findElements(By.cssSelector("table tr"));
            System.out.println("   📋 Filas encontradas: " + (rows.size() - 1));
            
            String normalizedSearch = normalizeExact(gameName);
            
            for (int i = 1; i < rows.size(); i++) {
                List<WebElement> cells = rows.get(i).findElements(By.cssSelector("td"));
                if (cells.size() >= 4) {
                    String id = cells.get(0).getText().trim();
                    String name = cells.get(1).getText().trim();
                    String type = cells.get(2).getText().trim();
                    
                    String normalizedName = normalizeExact(name);
                    
                    System.out.println("      " + name + " (tipo:" + type + ") -> " + normalizedName);
                    
                    if (normalizedName.equals(normalizedSearch)) {
                        System.out.println("   ✅ Coincidencia exacta: ID " + id);
                        return id;
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("   Error en gogdb: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
        
        return null;
    }
    
    private String normalizeExact(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .trim()
                .replace(":", "")
                .replace("-", "")
                .replace("'", "")
                .replace(" ", "")
                .replace("é", "e")
                .replace("è", "e")
                .replace("á", "a")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ñ", "n")
                .replaceAll("[^a-z0-9]", "");
    }
}
