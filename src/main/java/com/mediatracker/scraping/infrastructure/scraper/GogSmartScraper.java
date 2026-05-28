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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class GogSmartScraper {
    
    private static final String GOG_SEARCH_API = "https://catalog.gog.com/v1/catalog";
    private static final String GOGDB_BASE = "https://www.gogdb.org/product/";
    
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    public StorePrice searchGame(String gameName) {
        StorePrice result = new StorePrice();
        result.setStoreName("GOG");
        result.setAvailable(false);
        
        if (gameName == null || gameName.trim().isEmpty()) {
            return result;
        }
        
        System.out.println("🔍 Buscando en GOG (combinación): " + gameName);
        
        // PASO 1: Obtener ID numérico desde GOG API
        String gogId = fetchGogIdFromApi(gameName);
        
        if (gogId == null) {
            System.out.println("   ❌ No se encontró ID en GOG API");
            return result;
        }
        
        System.out.println("   📦 ID numérico obtenido: " + gogId);
        
        // PASO 2: Usar GOG Database con el ID numérico
        fetchPriceFromGogDatabase(gogId, result);
        
        return result;
    }
    
    private String fetchGogIdFromApi(String gameName) {
        try {
            String searchUrl = GOG_SEARCH_API + "?limit=3&search=" + URLEncoder.encode(gameName, StandardCharsets.UTF_8) +
                               "&order=desc:score&countryCode=ES&locale=es-ES";
            
            Request request = new Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "MediaPriceTracker/1.0")
                    .header("Accept", "application/json")
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    JsonNode root = mapper.readTree(json);
                    JsonNode products = root.path("products");
                    
                    if (products.isArray() && products.size() > 0) {
                        String normalizedSearch = normalizeString(gameName);
                        
                        // Buscar coincidencia exacta
                        for (JsonNode product : products) {
                            String title = product.path("title").asText();
                            String normalizedTitle = normalizeString(title);
                            
                            if (normalizedTitle.equals(normalizedSearch) || 
                                normalizedTitle.startsWith(normalizedSearch)) {
                                String id = product.path("id").asText();
                                System.out.println("   ✅ Coincidencia encontrada: " + title + " (ID: " + id + ")");
                                return id;
                            }
                        }
                        
                        // Si no hay exacta, tomar el primero
                        String id = products.get(0).path("id").asText();
                        String title = products.get(0).path("title").asText();
                        System.out.println("   ⚠️ Usando primera coincidencia: " + title + " (ID: " + id + ")");
                        return id;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error en GOG API: " + e.getMessage());
        }
        
        return null;
    }
    
    private void fetchPriceFromGogDatabase(String gogId, StorePrice result) {
        WebDriver driver = null;
        
        try {
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
            
            String url = GOGDB_BASE + gogId;
            System.out.println("   🌐 Cargando GOG Database: " + url);
            driver.get(url);
            
            Thread.sleep(3000);
            
            // Extraer título
            try {
                String title = driver.findElement(By.tagName("h1")).getText();
                result.setTitle(title);
                result.setUrl(url);
                result.setAvailable(true);
                result.setGameId(gogId);
            } catch (Exception e) {
                System.out.println("   ❌ No se encontró título en GOG Database");
                return;
            }
            
            // Extraer precio actual de la tabla
            BigDecimal price = extractCurrentPriceFromTable(driver);
            if (price != null) {
                result.setCurrentPrice(price);
                result.setCurrency("EUR");
                System.out.println("   ✅ GOG Database: " + price + "€ - " + result.getTitle());
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
    
    private BigDecimal extractCurrentPriceFromTable(WebDriver driver) {
        try {
            List<WebElement> tables = driver.findElements(By.cssSelector("table"));
            if (tables.isEmpty()) return null;
            
            WebElement priceTable = tables.get(0);
            List<WebElement> rows = priceTable.findElements(By.cssSelector("tr"));
            
            String currentPrice = null;
            
            // Buscar la fila más reciente con precio (últimas filas)
            for (int i = rows.size() - 1; i >= 0; i--) {
                WebElement row = rows.get(i);
                List<WebElement> cells = row.findElements(By.cssSelector("td"));
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
            System.err.println("Error extrayendo precio de tabla: " + e.getMessage());
        }
        
        return null;
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
}
