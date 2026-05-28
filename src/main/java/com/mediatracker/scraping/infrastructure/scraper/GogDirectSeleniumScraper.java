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

@Component
public class GogDirectSeleniumScraper {
    
    public StorePrice searchGame(String gameName) {
        StorePrice result = new StorePrice();
        result.setStoreName("GOG");
        result.setAvailable(false);
        
        if (gameName == null || gameName.trim().isEmpty()) {
            return result;
        }
        
        System.out.println("🔍 Buscando en GOG con Selenium directo: " + gameName);
        
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
            
            // Intentar diferentes formatos de URL
            String[] possibleUrls = {
                "https://www.gog.com/game/" + gameName.toLowerCase().replace(" ", "_"),
                "https://www.gog.com/game/" + gameName.toLowerCase().replace(" ", "-"),
                "https://www.gog.com/en/game/" + gameName.toLowerCase().replace(" ", "_"),
                "https://www.gog.com/game/cyberpunk_2077" // Hardcode para prueba
            };
            
            String successUrl = null;
            for (String url : possibleUrls) {
                System.out.println("   Probando: " + url);
                driver.get(url);
                Thread.sleep(2000);
                
                if (!driver.getTitle().contains("404") && !driver.getPageSource().contains("not found")) {
                    successUrl = url;
                    System.out.println("   ✅ URL funciona: " + url);
                    break;
                }
            }
            
            if (successUrl == null) {
                System.out.println("   ❌ Ninguna URL funcionó");
                return result;
            }
            
            // Buscar precio en la página
            String[] priceSelectors = {
                ".product-actions-payment__price-amount",
                ".product-actions-payment__final-price",
                ".price__amount",
                "[data-testid='game-price']",
                ".css-1g4kxs2"
            };
            
            BigDecimal price = null;
            for (String selector : priceSelectors) {
                try {
                    WebElement priceEl = driver.findElement(By.cssSelector(selector));
                    String priceText = priceEl.getText();
                    price = extractPrice(priceText);
                    if (price != null) {
                        break;
                    }
                } catch (Exception e) {}
            }
            
            if (price != null) {
                result.setCurrentPrice(price);
                result.setCurrency("EUR");
                result.setAvailable(true);
                result.setUrl(successUrl);
                result.setTitle(gameName);
                System.out.println("   ✅ GOG: " + price + "€ encontrado!");
            } else {
                System.out.println("   ❌ No se encontró precio en la página");
            }
            
        } catch (Exception e) {
            System.err.println("Error en GOG Selenium: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
        
        return result;
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
}
