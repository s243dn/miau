package com.mediatracker.scraping.infrastructure.scraper;

import com.mediatracker.scraping.domain.model.StorePrice;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class GogDatabaseScraper {
    
    private static final String GOGDB_SEARCH = "https://www.gogdb.org/products?search=";
    private static final String GOGDB_PRODUCT = "https://www.gogdb.org/product/";
    
    public StorePrice searchGame(String gameName) {
        StorePrice result = new StorePrice();
        result.setStoreName("GOG");
        result.setAvailable(false);
        
        if (gameName == null || gameName.trim().isEmpty()) {
            return result;
        }
        
        System.out.println("\n🎮 GOG Database: " + gameName);
        
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
            System.out.println("   🔍 Buscando: " + searchUrl);
            driver.get(searchUrl);
            Thread.sleep(3000);
            
            List<WebElement> rows = driver.findElements(By.cssSelector("table tr"));
            System.out.println("   📋 Filas encontradas: " + rows.size());
            
            if (rows.size() <= 1) {
                System.out.println("   ❌ No hay resultados");
                return result;
            }
            
            String gameId = null;
            String gameTitle = null;
            String gameType = null;
            
            // Normalizar el nombre buscado para comparación EXACTA
            String normalizedSearch = normalizeExact(gameName);
            System.out.println("   🔎 Buscando coincidencia EXACTA de: '" + normalizedSearch + "'");
            
            // Recorrer todos los resultados buscando coincidencia EXACTA
            for (int i = 1; i < rows.size(); i++) {
                List<WebElement> cells = rows.get(i).findElements(By.cssSelector("td"));
                if (cells.size() >= 4) {
                    String id = cells.get(0).getText().trim();
                    String name = cells.get(1).getText().trim();
                    String type = cells.get(2).getText().trim();
                    
                    String normalizedName = normalizeExact(name);
                    
                    System.out.println("      Comparando: '" + normalizedName + "' vs '" + normalizedSearch + "'");
                    
                    // COINCIDENCIA EXACTA (normalizado)
                    if (normalizedName.equals(normalizedSearch)) {
                        gameId = id;
                        gameTitle = name;
                        gameType = type;
                        System.out.println("   ✅ COINCIDENCIA EXACTA!");
                        System.out.println("      Nombre: " + name);
                        System.out.println("      Tipo: " + type);
                        System.out.println("      ID: " + id);
                        break;
                    }
                }
            }
            
            // Si no encontró coincidencia exacta, buscar el primero que no sea DLC/Goodies
            if (gameId == null) {
                System.out.println("   ⚠️ No hay coincidencia exacta, buscando primer resultado válido...");
                for (int i = 1; i < rows.size(); i++) {
                    List<WebElement> cells = rows.get(i).findElements(By.cssSelector("td"));
                    if (cells.size() >= 4) {
                        String id = cells.get(0).getText().trim();
                        String name = cells.get(1).getText().trim();
                        String type = cells.get(2).getText().trim();
                        String nameLower = name.toLowerCase();
                        
                        // Excluir DLCs y Goodies
                        if (!type.equals("DLC") && 
                            !nameLower.contains("dlc") && 
                            !nameLower.contains("goodies") &&
                            !nameLower.contains("redmod")) {
                            gameId = id;
                            gameTitle = name;
                            gameType = type;
                            System.out.println("   ⚠️ Usando: " + name + " (Tipo: " + type + ", ID: " + id + ")");
                            break;
                        }
                    }
                }
            }
            
            if (gameId == null) {
                System.out.println("   ❌ No se encontró ningún ID válido");
                return result;
            }
            
            // ASIGNACIÓN CORRECTA: gameId va a gameId, title va a title
            result.setGameId(gameId);
            result.setTitle(gameTitle);
            result.setAvailable(true);
            
            // Obtener precio usando el ID
            String productUrl = GOGDB_PRODUCT + gameId;
            System.out.println("\n   🌐 Cargando página del producto: " + productUrl);
            driver.get(productUrl);
            Thread.sleep(2000);
            
            BigDecimal price = extractCurrentPrice(driver);
            if (price != null) {
                result.setCurrentPrice(price);
                result.setCurrency("EUR");
                result.setUrl(productUrl);
                System.out.println("   💰 PRECIO: " + price + "€");
            } else {
                System.out.println("   ⚠️ No se encontró precio");
            }
            
        } catch (Exception e) {
            System.err.println("   ❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
        
        return result;
    }
    
    /**
     * Normalización EXACTA - elimina todo caracter especial y espacios
     * Ej: "Cyberpunk 2077" -> "cyberpunk2077"
     *     "Cyberpunk 2077 REDmod" -> "cyberpunk2077redmod"
     */
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
    
    private BigDecimal extractCurrentPrice(WebDriver driver) {
        try {
            List<WebElement> rows = driver.findElements(By.cssSelector("table tr"));
            
            for (int i = rows.size() - 1; i >= 0; i--) {
                List<WebElement> cells = rows.get(i).findElements(By.cssSelector("td"));
                if (cells.size() >= 4) {
                    String finalPrice = cells.get(3).getText();
                    if (finalPrice != null && !finalPrice.isEmpty() && !finalPrice.equals("-")) {
                        System.out.println("      Precio encontrado en fila " + i + ": " + finalPrice);
                        return extractPrice(finalPrice);
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }
    
    private BigDecimal extractPrice(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            String cleaned = text.replace("€", "").replace("$", "").trim();
            cleaned = cleaned.replace(",", ".");
            Pattern pattern = Pattern.compile("\\d+\\.?\\d*");
            var matcher = pattern.matcher(cleaned);
            if (matcher.find()) {
                return new BigDecimal(matcher.group());
            }
        } catch (NumberFormatException e) {}
        return null;
    }
}
