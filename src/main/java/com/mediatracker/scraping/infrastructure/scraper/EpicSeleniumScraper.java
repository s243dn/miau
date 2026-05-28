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
public class EpicSeleniumScraper {
    
    private static final String EPIC_SEARCH_URL = "https://store.epicgames.com/es-ES/browse?q=";
    
    public StorePrice searchGame(String gameName) {
        StorePrice result = new StorePrice();
        result.setStoreName("Epic Games");
        result.setAvailable(false);
        
        if (gameName == null || gameName.trim().isEmpty()) {
            return result;
        }
        
        System.out.println("🔍 Buscando en Epic Games: " + gameName);
        
        WebDriver driver = null;
        
        try {
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-blink-features=AutomationControlled");
            
            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            
            String searchUrl = EPIC_SEARCH_URL + gameName.replace(" ", "%20");
            System.out.println("   URL: " + searchUrl);
            driver.get(searchUrl);
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-component='Card']")));
            
            Thread.sleep(4000);
            
            List<WebElement> products = driver.findElements(By.cssSelector("[data-component='Card']"));
            
            System.out.println("   Productos encontrados: " + products.size());
            
            String normalizedSearch = normalizeString(gameName);
            WebElement bestMatch = null;
            String bestMatchTitle = null;
            int bestScore = 0;
            
            for (WebElement product : products) {
                try {
                    String title = "";
                    String[] selectors = {"[data-component='Title']", "h5", ".title"};
                    for (String selector : selectors) {
                        try {
                            WebElement titleEl = product.findElement(By.cssSelector(selector));
                            title = titleEl.getText();
                            if (!title.isEmpty()) break;
                        } catch (Exception e) {}
                    }
                    
                    if (title.isEmpty()) continue;
                    
                    String normalizedTitle = normalizeString(title);
                    int score = calculateMatchScore(normalizedTitle, normalizedSearch);
                    
                    System.out.println("   - " + title + " (score: " + score + ")");
                    
                    if (score > bestScore) {
                        bestScore = score;
                        bestMatch = product;
                        bestMatchTitle = title;
                    }
                } catch (Exception e) {}
            }
            
            if (bestMatch != null && bestScore > 50) {
                result.setTitle(bestMatchTitle);
                result.setAvailable(true);
                
                // Obtener enlace
                try {
                    WebElement link = bestMatch.findElement(By.cssSelector("a"));
                    String href = link.getAttribute("href");
                    result.setUrl(href);
                } catch (Exception e) {}
                
                // Obtener precio
                try {
                    WebElement priceEl = bestMatch.findElement(By.cssSelector("[data-component='Price']"));
                    String priceText = priceEl.getText();
                    BigDecimal price = extractPrice(priceText);
                    if (price != null) {
                        result.setCurrentPrice(price);
                        result.setCurrency("EUR");
                    }
                } catch (Exception e) {}
                
                System.out.println("   ✅ Epic: " + result.getCurrentPrice() + "€ - " + bestMatchTitle);
            } else {
                System.out.println("   ❌ No se encontró coincidencia buena para: " + gameName);
            }
            
        } catch (Exception e) {
            System.err.println("Error en Epic: " + e.getMessage());
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
        return 0;
    }
    
    private BigDecimal extractPrice(String priceText) {
        if (priceText == null || priceText.isEmpty()) return null;
        try {
            String cleaned = priceText.replace("€", "").replace("$", "").trim();
            cleaned = cleaned.replace(",", ".");
            String[] parts = cleaned.split(" ");
            String numberStr = parts[0].replaceAll("[^0-9.]", "");
            if (!numberStr.isEmpty()) {
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
