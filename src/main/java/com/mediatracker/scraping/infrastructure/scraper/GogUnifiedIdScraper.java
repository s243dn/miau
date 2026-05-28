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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

@Component
public class GogUnifiedIdScraper {
    
    private static final String GOGDB_BASE = "https://www.gogdb.org/product/";
    private static final String GOGDB_BACKUPS = "https://www.gogdb.org/backups_v3/";
    private static final String GOG_CATALOG_API = "https://catalog.gog.com/v1/catalog";
    private static final String WIKIDATA_SPARQL = "https://query.wikidata.org/sparql";
    
    // Caché local de IDs
    private static final Map<String, String> idCache = new HashMap<>();
    private static final String CACHE_FILE = "gog_ids_full.cache";
    private static boolean indexLoaded = false;
    
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    public StorePrice searchGame(String gameName) {
        StorePrice result = new StorePrice();
        result.setStoreName("GOG");
        result.setAvailable(false);
        
        if (gameName == null || gameName.trim().isEmpty()) {
            return result;
        }
        
        System.out.println("\n🎮 BUSCANDO EN GOG: " + gameName);
        System.out.println("─────────────────────────────────────────");
        
        // Cargar índice si no está cargado
        if (!indexLoaded) {
            loadFullIndex();
        }
        
        String normalizedName = normalizeString(gameName);
        String gogId = null;
        String source = null;
        
        // ========== OPCIÓN 1: Buscar en índice local (backups_v3) ==========
        System.out.println("📁 Opción 1: Buscando en índice local...");
        if (idCache.containsKey(normalizedName)) {
            gogId = idCache.get(normalizedName);
            source = "índice local (GOG Database backups_v3)";
            System.out.println("   ✅ ID encontrado en caché local");
        } else {
            // Búsqueda parcial en caché
            for (Map.Entry<String, String> entry : idCache.entrySet()) {
                if (entry.getKey().contains(normalizedName) || normalizedName.contains(entry.getKey())) {
                    gogId = entry.getValue();
                    source = "índice local (coincidencia parcial)";
                    System.out.println("   ✅ ID encontrado por coincidencia parcial: " + entry.getKey());
                    break;
                }
            }
        }
        
        // ========== OPCIÓN 2: API de GOG catalog ==========
        if (gogId == null) {
            System.out.println("🌐 Opción 2: Consultando API de GOG catalog...");
            gogId = fetchIdFromGogApi(gameName);
            if (gogId != null) {
                source = "API GOG catalog";
                idCache.put(normalizedName, gogId);
                saveCache();
                System.out.println("   ✅ ID obtenido de API");
            }
        }
        
        // ========== OPCIÓN 3: Wikidata SPARQL ==========
        if (gogId == null) {
            System.out.println("📚 Opción 3: Consultando Wikidata SPARQL...");
            gogId = fetchIdFromWikidata(gameName);
            if (gogId != null) {
                source = "Wikidata SPARQL";
                idCache.put(normalizedName, gogId);
                saveCache();
                System.out.println("   ✅ ID obtenido de Wikidata");
            }
        }
        
        if (gogId == null) {
            System.out.println("\n❌ No se encontró ID para: " + gameName);
            return result;
        }
        
        System.out.println("\n✅ ID ENCONTRADO: " + gogId);
        System.out.println("   Fuente: " + source);
        
        // Obtener precio usando el ID
        fetchPriceFromGogDatabase(gogId, gameName, result);
        
        return result;
    }
    
    private void loadFullIndex() {
        System.out.println("📥 Cargando índice completo de GOG Database...");
        
        // Intentar cargar desde caché local primero
        loadCache();
        
        if (idCache.isEmpty()) {
            // Descargar índice desde backups_v3
            try {
                downloadAndParseBackups();
            } catch (Exception e) {
                System.err.println("Error descargando backups: " + e.getMessage());
            }
        }
        
        indexLoaded = true;
        System.out.println("   📊 Índice cargado: " + idCache.size() + " juegos");
    }
    
    private void downloadAndParseBackups() throws Exception {
        // Obtener lista de archivos de backup
        String backupIndex = fetchUrlContent(GOGDB_BACKUPS);
        
        // Buscar archivos .json.gz
        Pattern pattern = Pattern.compile("href=\"([^\"]+\\.json\\.gz)\"");
        var matcher = pattern.matcher(backupIndex);
        
        List<String> backupFiles = new ArrayList<>();
        while (matcher.find()) {
            backupFiles.add(GOGDB_BACKUPS + matcher.group(1));
        }
        
        System.out.println("   📦 Archivos de backup encontrados: " + backupFiles.size());
        
        // Procesar cada archivo (limitamos a los primeros 10 para no sobrecargar)
        int processed = 0;
        for (String backupUrl : backupFiles) {
            if (processed++ > 10) break;
            
            try {
                System.out.println("   Procesando: " + backupUrl);
                String content = fetchUrlContent(backupUrl);
                
                // Descomprimir si es gzip
                if (backupUrl.endsWith(".gz")) {
                    content = gunzip(content);
                }
                
                JsonNode root = mapper.readTree(content);
                parseGameEntries(root);
                
            } catch (Exception e) {
                System.err.println("   Error procesando " + backupUrl + ": " + e.getMessage());
            }
        }
        
        saveCache();
    }
    
    private void parseGameEntries(JsonNode root) {
        if (root.isArray()) {
            for (JsonNode game : root) {
                String name = game.path("name").asText();
                String id = game.path("id").asText();
                if (name != null && !name.isEmpty() && id != null && !id.isEmpty()) {
                    idCache.put(normalizeString(name), id);
                }
            }
        } else if (root.has("products")) {
            for (JsonNode game : root.path("products")) {
                String name = game.path("name").asText();
                String id = game.path("id").asText();
                if (name != null && !name.isEmpty() && id != null && !id.isEmpty()) {
                    idCache.put(normalizeString(name), id);
                }
            }
        }
    }
    
    private String fetchIdFromGogApi(String gameName) {
        try {
            String url = GOG_CATALOG_API + "?limit=5&search=" + 
                         URLEncoder.encode(gameName, StandardCharsets.UTF_8) +
                         "&order=desc:score&countryCode=ES&locale=es-ES";
            
            String json = fetchUrlContent(url);
            JsonNode root = mapper.readTree(json);
            JsonNode products = root.path("products");
            
            if (products.isArray()) {
                String normalizedSearch = normalizeString(gameName);
                for (JsonNode product : products) {
                    String title = product.path("title").asText();
                    if (normalizeString(title).equals(normalizedSearch)) {
                        return product.path("id").asText();
                    }
                }
                if (products.size() > 0) {
                    return products.get(0).path("id").asText();
                }
            }
        } catch (Exception e) {
            System.err.println("Error en GOG API: " + e.getMessage());
        }
        return null;
    }
    
    private String fetchIdFromWikidata(String gameName) {
        try {
            String sparqlQuery = 
                "SELECT ?item ?itemLabel ?gogId WHERE {" +
                "  ?item wdt:P31 wd:Q7889 ." + // instancia de videojuego
                "  ?item wdt:P12727 ?gogId ." + // con ID de GOG
                "  ?item rdfs:label \"" + escapeWikidata(gameName) + "\"@en ." +
                "  SERVICE wikibase:label { bd:serviceParam wikibase:language \"en\". }" +
                "} LIMIT 1";
            
            String url = WIKIDATA_SPARQL + "?format=json&query=" + URLEncoder.encode(sparqlQuery, StandardCharsets.UTF_8);
            String json = fetchUrlContent(url);
            JsonNode root = mapper.readTree(json);
            JsonNode results = root.path("results").path("bindings");
            
            if (results.size() > 0) {
                return results.get(0).path("gogId").path("value").asText();
            }
        } catch (Exception e) {
            System.err.println("Error en Wikidata: " + e.getMessage());
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
            
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
            
            String url = GOGDB_BASE + gogId;
            System.out.println("🌐 Cargando página del producto: " + url);
            driver.get(url);
            
            Thread.sleep(3000);
            
            // Extraer título
            try {
                String title = driver.findElement(By.tagName("h1")).getText();
                result.setTitle(title);
            } catch (Exception e) {
                result.setTitle(gameName);
            }
            
            result.setUrl(url);
            result.setAvailable(true);
            result.setGameId(gogId);
            
            // Extraer precio
            BigDecimal price = extractCurrentPrice(driver);
            if (price != null) {
                result.setCurrentPrice(price);
                result.setCurrency("EUR");
                System.out.println("💰 PRECIO ENCONTRADO: " + price + "€");
            } else {
                System.out.println("⚠️ No se encontró precio en la página");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error obteniendo precio: " + e.getMessage());
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
            
            // Recorrer de atrás hacia adelante (registros más recientes)
            for (int i = rows.size() - 1; i >= 0; i--) {
                var cells = rows.get(i).findElements(By.cssSelector("td"));
                if (cells.size() >= 4) {
                    String finalPrice = cells.get(3).getText();
                    if (finalPrice != null && !finalPrice.isEmpty() && !finalPrice.equals("-")) {
                        System.out.println("   Fila encontrada: " + cells.get(0).getText() + " → " + finalPrice);
                        return extractPrice(finalPrice);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error extrayendo precio: " + e.getMessage());
        }
        return null;
    }
    
    // Métodos auxiliares
    private String fetchUrlContent(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MediaPriceTracker/1.0")
                .header("Accept", "application/json")
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }
    
    private String gunzip(String content) {
        // Implementación simplificada - para producción usar GZIPInputStream
        return content;
    }
    
    private BigDecimal extractPrice(String priceText) {
        if (priceText == null || priceText.isEmpty()) return null;
        try {
            String cleaned = priceText.replace("€", "").replace("$", "").trim();
            cleaned = cleaned.replace(",", ".");
            Pattern pattern = Pattern.compile("\\d+[\\.]?\\d*");
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
                .replace("-", "")
                .replace("'", "")
                .replace("é", "e")
                .replace("è", "e")
                .replace("á", "a")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ñ", "n")
                .replaceAll("[^a-z0-9]", "");
    }
    
    private String escapeWikidata(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    @SuppressWarnings("unchecked")
    private void loadCache() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CACHE_FILE))) {
            Map<String, String> loaded = (Map<String, String>) ois.readObject();
            idCache.putAll(loaded);
            System.out.println("   💾 Caché cargada: " + idCache.size() + " entradas");
        } catch (Exception e) {
            System.out.println("   📭 No se encontró caché local");
        }
    }
    
    private void saveCache() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CACHE_FILE))) {
            oos.writeObject(idCache);
            System.out.println("   💾 Caché guardada: " + idCache.size() + " entradas");
        } catch (Exception e) {
            System.err.println("Error guardando caché: " + e.getMessage());
        }
    }
}
