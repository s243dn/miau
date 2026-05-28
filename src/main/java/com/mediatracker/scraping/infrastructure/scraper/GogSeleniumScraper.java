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
public class GogSeleniumScraper {
    
    private static final String GOG_DB_SEARCH_URL = "https://www.gogdb.org/search?q=";
    
    public StorePrice searchGame(String gameName) {
        StorePrice result = new StorePrice();
        result.setStoreName("GOG");
        result.setAvailable(false);
        
        if (gameName == null || gameName.trim().isEmpty()) {
            return result;
        }
        
        System.out.println("🔍 Buscando en GOG Database con Selenium: " + gameName);
        
        WebDriver driver = null;
        
        try {
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-blink-features=AutomationControlled");
            
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            
            // Construir URL de búsqueda
            String searchUrl = GOG_DB_SEARCH_URL + gameName.replace(" ", "+");
            System.out.println("   Cargando: " + searchUrl);
            driver.get(searchUrl);
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            
            Thread.sleep(2000);
            
            // Buscar resultados de productos
            List<WebElement> results = driver.findElements(By.cssSelector("a[href^='/product/']"));
            System.out.println("   Resultados encontrados: " + results.size());
            
            if (results.isEmpty()) {
                System.out.println("   ❌ No se encontraron resultados");
                return result;
            }
            
            String normalizedSearch = normalizeString(gameName);
            WebElement bestMatch = null;
            String bestMatchTitle = null;
            int bestScore = 0;
            
            // Evaluar cada resultado para encontrar la mejor coincidencia
            for (WebElement element : results) {
                String title = element.getText();
                if (title == null || title.isEmpty()) continue;
                
                String normalizedTitle = normalizeString(title);
                int score = calculateMatchScore(normalizedTitle, normalizedSearch);
                
                System.out.println("   - " + title + " (score: " + score + ")");
                
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = element;
                    bestMatchTitle = title;
                }
            }
            
            if (bestMatch != null && bestScore > 50) {
                // Obtener URL del producto
                String productUrl = bestMatch.getAttribute("href");
                if (!productUrl.startsWith("http")) {
                    productUrl = "https://www.gogdb.org" + productUrl;
                }
                
                result.setTitle(bestMatchTitle);
                result.setUrl(productUrl);
                result.setAvailable(true);
                
                System.out.println("   ✅ Producto encontrado: " + bestMatchTitle);
                System.out.println("   URL: " + productUrl);
                
                // Navegar a la página del producto para obtener el precio
                driver.get(productUrl);
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
                Thread.sleep(1500);
                
                // Buscar precio en la página del producto
                String[] priceSelectors = {
                    ".price",
                    ".current-price",
                    ".product-price",
                    "span.price",
                    "div.price"
                };
                
                String priceText = null;
                for (String selector : priceSelectors) {
                    try {
                        WebElement priceEl = driver.findElement(By.cssSelector(selector));
                        priceText = priceEl.getText();
                        if (priceText != null && !priceText.isEmpty()) {
                            break;
                        }
                    } catch (Exception e) {}
                }
                
                if (priceText != null && !priceText.isEmpty()) {
                    BigDecimal price = extractPrice(priceText);
                    if (price != null) {
                        result.setCurrentPrice(price);
                        result.setCurrency("EUR");
                        System.out.println("   💰 Precio: " + price + "€");
                    }
                } else {
                    System.out.println("   ⚠️ No se encontró precio en la página");
                }
                
                // Buscar precio original (descuento)
                String[] oldPriceSelectors = {
                    ".old-price",
                    ".original-price",
                    "span.old-price"
                };
                
                for (String selector : oldPriceSelectors) {
                    try {
                        WebElement oldPriceEl = driver.findElement(By.cssSelector(selector));
                        String oldPriceText = oldPriceEl.getText();
                        BigDecimal originalPrice = extractPrice(oldPriceText);
                        if (originalPrice != null && result.getCurrentPrice() != null) {
                            result.setOriginalPrice(originalPrice);
                            int discount = (int) ((originalPrice.doubleValue() - result.getCurrentPrice().doubleValue()) 
                                    / originalPrice.doubleValue() * 100);
                            result.setDiscountPercent(discount);
                            System.out.println("   💰 Precio original: " + originalPrice + "€ (descuento: " + discount + "%)");
                            break;
                        }
                    } catch (Exception e) {}
                }
                
            } else {
                System.out.println("   ❌ No se encontró coincidencia buena (mejor score: " + bestScore + ")");
            }
            
        } catch (Exception e) {
            System.err.println("Error en GOG Selenium: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
        
        return result;
    }
    
    private int calculateMatchScore(String title, String search) {
        if (title.equals(search)) return 100;
        if (title.startsWith(search)) return 90;
        if (title.contains(search)) return 70;
        if (search.contains(title)) return 60;
        
        // Coincidencia parcial
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
        
        if (matches > 0) {
            return 40 + (matches * 10);
        }
        
        return 0;
    }
    
    private BigDecimal extractPrice(String priceText) {
        if (priceText == null || priceText.isEmpty()) return null;
        try {
            // Limpiar el texto del precio
            String cleaned = priceText
                    .replace("€", "")
                    .replace("$", "")
                    .replace("EUR", "")
                    .replace("euros", "")
                    .trim();
            
            cleaned = cleaned.replace(",", ".");
            
            // Extraer el primer número que encuentre
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+[\\.]?\\d*");
            var matcher = pattern.matcher(cleaned);
            if (matcher.find()) {
                String numberStr = matcher.group();
                return new BigDecimal(numberStr);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error extrayendo precio: " + priceText);
        }
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
