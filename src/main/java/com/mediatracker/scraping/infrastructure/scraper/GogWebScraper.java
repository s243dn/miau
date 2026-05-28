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

@Component
public class GogWebScraper {
    
    public StorePrice searchGame(String gameName) {
        StorePrice result = new StorePrice();
        result.setStoreName("GOG");
        result.setAvailable(false);
        
        if (gameName == null || gameName.trim().isEmpty()) {
            return result;
        }
        
        System.out.println("🔍 Buscando en GOG web con Selenium: " + gameName);
        
        WebDriver driver = null;
        
        try {
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0");
            
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
            
            // Construir URL del juego en GOG
            String slug = gameName.toLowerCase()
                    .replace(" ", "_")
                    .replace(":", "")
                    .replace("'", "")
                    .replace("é", "e")
                    .replace("è", "e")
                    .replace("á", "a")
                    .replace("í", "i")
                    .replace("ó", "o")
                    .replace("ú", "u")
                    .replace("ñ", "n");
            
            String url = "https://www.gog.com/game/" + slug;
            System.out.println("   Cargando: " + url);
            driver.get(url);
            
            Thread.sleep(5000);
            
            // Verificar si estamos en una página de error
            String pageSource = driver.getPageSource();
            if (pageSource.contains("404") || pageSource.contains("not found") || driver.getTitle().contains("404")) {
                System.out.println("   ❌ Juego no encontrado en GOG");
                return result;
            }
            
            // Extraer título
            String title = driver.findElement(By.tagName("h1")).getText();
            result.setTitle(title);
            result.setUrl(url);
            result.setAvailable(true);
            
            // Buscar precio - múltiples selectores posibles
            String[] priceSelectors = {
                "span[data-testid='game-price']",
                ".product-actions-payment__price-amount",
                ".product-actions-payment__final-price",
                ".price__amount",
                ".css-1g4kxs2",
                ".css-1d68on5",
                "[class*='price']"
            };
            
            BigDecimal price = null;
            for (String selector : priceSelectors) {
                try {
                    WebElement priceEl = driver.findElement(By.cssSelector(selector));
                    String priceText = priceEl.getText();
                    if (priceText != null && !priceText.isEmpty()) {
                        price = extractPrice(priceText);
                        if (price != null) {
                            System.out.println("   ✅ Precio encontrado con selector: " + selector);
                            break;
                        }
                    }
                } catch (Exception e) {}
            }
            
            // Si no se encontró precio, buscar en el HTML
            if (price == null) {
                String html = driver.getPageSource();
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"price\":\\{\"amount\":([0-9.]+)");
                var matcher = pattern.matcher(html);
                if (matcher.find()) {
                    price = new BigDecimal(matcher.group(1));
                    System.out.println("   ✅ Precio encontrado en JSON embed");
                }
            }
            
            if (price != null) {
                result.setCurrentPrice(price);
                result.setCurrency("EUR");
                System.out.println("   💰 Precio GOG: " + price + "€ - " + title);
            } else {
                System.out.println("   ⚠️ No se encontró precio para: " + title);
            }
            
        } catch (Exception e) {
            System.err.println("Error en GOG web: " + e.getMessage());
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
                    .replace("FREE", "")
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
