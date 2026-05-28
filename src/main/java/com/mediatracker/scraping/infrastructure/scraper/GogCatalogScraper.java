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
import java.io.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class GogCatalogScraper {
    
    private static final String GOG_CATALOG_API = "https://catalog.gog.com/v1/catalog";
    private static final String GOGDB_BASE = "https://www.gogdb.org/product/";
    
    // Caché local de IDs (nombre -> ID numérico)
    private static final Map<String, String> gameIdCache = new HashMap<>();
    private static final String CACHE_FILE = "gog_ids.cache";
    
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
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
        
        System.out.println("🔍 Buscando en GOG: " + gameName);
        
        // 1. Buscar ID numérico en caché o API
        String gogId = getGameId(gameName);
        
        if (gogId == null) {
            System.out.println("   ❌ No se encontró ID para: " + gameName);
            return result;
        }
        
        System.out.println("   📦 ID encontrado: " + gogId);
        
        // 2. Obtener precio desde GOG Database usando el ID
        fetchPriceFromGogDatabase(gogId, gameName, result);
        
        return result;
    }
    
    private String getGameId(String gameName) {
        String normalizedName = normalizeString(gameName);
        
        // Buscar en caché
        if (gameIdCache.containsKey(normalizedName)) {
            System.out.println("   ✅ ID encontrado en caché");
            return gameIdCache.get(normalizedName);
        }
        
        // Buscar en API de GOG
        try {
            String searchUrl = GOG_CATALOG_API + "?limit=10&search=" + 
                               URLEncoder.encode(gameName, StandardCharsets.UTF_8) +
                               "&order=desc:score&countryCode=ES&locale=es-ES";
            
            Request request = new Request.Builder().url(searchUrl).build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    JsonNode root = mapper.readTree(json);
                    JsonNode products = root.path("products");
                    
                    if (products.isArray()) {
                        String bestMatchId = null;
                        String bestMatchTitle = null;
                        int bestScore = 0;
                        
                        for (JsonNode product : products) {
                            String title = product.path("title").asText();
                            String normalizedTitle = normalizeString(title);
                            
                            int score = calculateMatchScore(normalizedTitle, normalizedName);
                            
                            if (score > bestScore && score > 50) {
                                bestScore = score;
                                bestMatchId = product.path("id").asText();
                                bestMatchTitle = title;
                            }
                        }
                        
                        if (bestMatchId != null) {
                            System.out.println("   ✅ ID obtenido de API: " + bestMatchId + " (" + bestMatchTitle + ")");
                            gameIdCache.put(normalizedName, bestMatchId);
                            saveCache();
                            return bestMatchId;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo ID: " + e.getMessage());
        }
        
        return null;
    }
    
    private void fetchPriceFromGogDatabase(String gogId, String gameName, StorePrice result) {
        WebDriver driver = null;
        
        try {
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
            
            String url = GOGDB_BASE + gogId;
            System.out.println("   🌐 Cargando: " + url);
            driver.get(url);
            
            Thread.sleep(4000);
            
            // Verificar que la página cargó
            String title = driver.findElement(By.tagName("h1")).getText();
            result.setTitle(title);
            result.setUrl(url);
            result.setAvailable(true);
            result.setGameId(gogId);
            
            // Extraer precio actual de la tabla
            BigDecimal price = extractCurrentPrice(driver);
            if (price != null) {
                result.setCurrentPrice(price);
                result.setCurrency("EUR");
                System.out.println("   ✅ GOG: " + price + "€ - " + title);
            } else {
                System.out.println("   ⚠️ No se encontró precio en GOG Database");
            }
            
        } catch (Exception e) {
            System.err.println("Error en GOG Database: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }
    
    private BigDecimal extractCurrentPrice(WebDriver driver) {
        try {
            // Buscar la tabla de precios
            var rows = driver.findElements(By.cssSelector("table tr"));
            if (rows.isEmpty()) return null;
            
            String currentPrice = null;
            
            // Recorrer filas para encontrar la más reciente
            for (int i = rows.size() - 1; i >= 0; i--) {
                var cells = rows.get(i).findElements(By.cssSelector("td"));
                if (cells.size() >= 4) {
                    String finalPrice = cells.get(3).getText();
                    if (finalPrice != null && !finalPrice.isEmpty() && !finalPrice.equals("-")) {
                        currentPrice = finalPrice;
                        break;
                    }
                }
            }
            
            if (currentPrice != null) {
                return extractPrice(currentPrice);
            }
            
        } catch (Exception e) {
            System.err.println("Error extrayendo precio: " + e.getMessage());
        }
        
        return null;
    }
    
    private int calculateMatchScore(String title, String search) {
        if (title.equals(search)) return 100;
        if (title.startsWith(search)) return 90;
        if (title.contains(search)) return 70;
        if (search.contains(title)) return 60;
        
        // Coincidencia de palabras clave
        String[] titleWords = title.split(" ");
        String[] searchWords = search.split(" ");
        int matches = 0;
        for (String tw : titleWords) {
            for (String sw : searchWords) {
                if (tw.equals(sw) || tw.startsWith(sw) || sw.startsWith(tw)) {
                    matches++;
                    break;
                }
            }
        }
        
        return matches * 10;
    }
    
    private BigDecimal extractPrice(String priceText) {
        if (priceText == null || priceText.isEmpty()) return null;
        try {
            String cleaned = priceText
                    .replace("€", "")
                    .replace("$", "")
                    .replace("EUR", "")
                    .trim();
            
            cleaned = cleaned.replace(",", ".");
            
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+[\\.]?\\d*");
            var matcher = pattern.matcher(cleaned);
            if (matcher.find()) {
                return new BigDecimal(matcher.group());
            }
        } catch (NumberFormatException e) {}
        return null;
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
    
    private void saveCache() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CACHE_FILE))) {
            oos.writeObject(gameIdCache);
        } catch (Exception e) {
            System.err.println("Error guardando caché: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadCache() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CACHE_FILE))) {
            Map<String, String> loaded = (Map<String, String>) ois.readObject();
            gameIdCache.putAll(loaded);
            System.out.println("📦 Caché de GOG IDs cargada: " + gameIdCache.size() + " entradas");
        } catch (Exception e) {
            System.out.println("No se encontró caché de GOG IDs");
        }
    }
    
    // Cargar caché al iniciar
    {
        loadCache();
    }
}
