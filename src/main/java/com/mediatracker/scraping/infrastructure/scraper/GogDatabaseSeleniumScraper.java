package com.mediatracker.scraping.infrastructure.scraper;

import com.mediatracker.scraping.domain.model.StorePrice;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Component
public class GogDatabaseSeleniumScraper {
    
    private static final String GOGDB_URL = "https://www.gogdb.org";
    
    public StorePrice searchGame(String gameName) {
        StorePrice result = new StorePrice();
        result.setStoreName("GOG Database");
        result.setAvailable(false);
        
        if (gameName == null || gameName.trim().isEmpty()) {
            return result;
        }
        
        System.out.println("🔍 Buscando en GOG Database: " + gameName);
        
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
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
            
            // Ir directamente a la URL de búsqueda
            String searchUrl = GOGDB_URL + "/search?q=" + gameName.replace(" ", "+");
            System.out.println("   URL: " + searchUrl);
            driver.get(searchUrl);
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            
            Thread.sleep(2000);
            
            // Buscar todos los enlaces a productos
            List<WebElement> results = driver.findElements(By.cssSelector("a[href*='/product/']"));
            System.out.println("   Resultados encontrados: " + results.size());
            
            if (results.isEmpty()) {
                System.out.println("   ❌ No se encontraron resultados");
                return result;
            }
            
            String normalizedSearch = normalizeString(gameName);
            WebElement bestMatch = null;
            String bestMatchTitle = null;
            String bestMatchUrl = null;
            
            // Buscar coincidencia EXACTA
            for (WebElement element : results) {
                String title = element.getText();
                if (title == null || title.isEmpty()) continue;
                
                String normalizedTitle = normalizeString(title);
                
                System.out.println("   Comparando: '" + normalizedTitle + "' con '" + normalizedSearch + "'");
                
                // Coincidencia EXACTA
                if (normalizedTitle.equals(normalizedSearch)) {
                    bestMatch = element;
                    bestMatchTitle = title;
                    bestMatchUrl = element.getAttribute("href");
                    System.out.println("   ✅ Coincidencia EXACTA encontrada: " + title);
                    break;
                }
            }
            
            // Si no hay coincidencia exacta, buscar la que más se parezca (pero con umbral alto)
            if (bestMatch == null) {
                int bestScore = 0;
                for (WebElement element : results) {
                    String title = element.getText();
                    if (title == null || title.isEmpty()) continue;
                    
                    String normalizedTitle = normalizeString(title);
                    int score = calculateExactMatchScore(normalizedTitle, normalizedSearch);
                    
                    if (score > bestScore && score >= 80) {
                        bestScore = score;
                        bestMatch = element;
                        bestMatchTitle = title;
                        bestMatchUrl = element.getAttribute("href");
                    }
                }
                
                if (bestMatch != null) {
                    System.out.println("   ⚠️ Coincidencia parcial aceptada (score: " + bestScore + "): " + bestMatchTitle);
                }
            }
            
            if (bestMatch != null) {
                result.setTitle(bestMatchTitle);
                result.setUrl(bestMatchUrl);
                result.setAvailable(true);
                
                System.out.println("   ✅ Producto seleccionado: " + bestMatchTitle);
                System.out.println("   URL: " + bestMatchUrl);
                
                // Navegar a la página del producto
                driver.get(bestMatchUrl);
                Thread.sleep(2000);
                
                // Buscar precio actual en la tabla
                String currentPrice = extractCurrentPriceFromTable(driver);
                
                if (currentPrice != null) {
                    BigDecimal price = extractPrice(currentPrice);
                    if (price != null) {
                        result.setCurrentPrice(price);
                        result.setCurrency("EUR");
                        System.out.println("   💰 Precio actual: " + price + "€");
                    }
                } else {
                    System.out.println("   ⚠️ No se encontró precio en la tabla");
                }
                
            } else {
                System.out.println("   ❌ No se encontró coincidencia exacta para: " + gameName);
            }
            
        } catch (Exception e) {
            System.err.println("Error en GOG Database: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
        
        return result;
    }
    
    private String extractCurrentPriceFromTable(WebDriver driver) {
        try {
            // Buscar la tabla de precios
            List<WebElement> tables = driver.findElements(By.cssSelector("table"));
            if (tables.isEmpty()) return null;
            
            WebElement priceTable = tables.get(0);
            List<WebElement> rows = priceTable.findElements(By.cssSelector("tr"));
            
            String currentPrice = null;
            String today = java.time.LocalDate.now().toString();
            
            // Recorrer filas para encontrar la más reciente
            for (WebElement row : rows) {
                List<WebElement> cells = row.findElements(By.cssSelector("td"));
                if (cells.size() >= 4) {
                    String date = cells.get(0).getText();
                    String finalPrice = cells.get(3).getText();
                    
                    // Verificar si la fecha es hoy o reciente
                    if (date.contains(today) || isRecentDate(date)) {
                        currentPrice = finalPrice;
                        break;
                    }
                }
            }
            
            // Si no encontró por fecha, tomar el último precio de la tabla
            if (currentPrice == null && rows.size() > 1) {
                WebElement lastRow = rows.get(rows.size() - 1);
                List<WebElement> cells = lastRow.findElements(By.cssSelector("td"));
                if (cells.size() >= 4) {
                    currentPrice = cells.get(3).getText();
                }
            }
            
            return currentPrice;
        } catch (Exception e) {
            return null;
        }
    }
    
    private boolean isRecentDate(String date) {
        try {
            // Formato esperado: "2026-05-18"
            if (date.length() < 10) return false;
            String dateStr = date.substring(0, 10);
            java.time.LocalDate entryDate = java.time.LocalDate.parse(dateStr);
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.Period period = java.time.Period.between(entryDate, today);
            return period.getDays() <= 7; // Precios de los últimos 7 días
        } catch (Exception e) {
            return false;
        }
    }
    
    private int calculateExactMatchScore(String title, String search) {
        if (title.equals(search)) return 100;
        if (title.startsWith(search) && search.length() > 5) return 85;
        if (title.endsWith(search)) return 80;
        if (title.contains(search)) return 60;
        return 0;
    }
    
    private BigDecimal extractPrice(String priceText) {
        if (priceText == null || priceText.isEmpty()) return null;
        try {
            String cleaned = priceText
                    .replace("€", "")
                    .replace("$", "")
                    .replace("EUR", "")
                    .replace("-", "")
                    .trim();
            
            cleaned = cleaned.replace(",", ".");
            
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+[\\.]?\\d*");
            var matcher = pattern.matcher(cleaned);
            if (matcher.find()) {
                String numberStr = matcher.group();
                return new BigDecimal(numberStr);
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
