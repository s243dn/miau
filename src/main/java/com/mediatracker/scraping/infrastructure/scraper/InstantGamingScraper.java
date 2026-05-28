package com.mediatracker.scraping.infrastructure.scraper;

import com.mediatracker.scraping.domain.model.PlatformPrice;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class InstantGamingScraper {

    private static final String BASE_URL = "https://www.instant-gaming.com";
    private static final String SEARCH_URL = BASE_URL + "/es/buscar/?q=";
    
    private static final BigDecimal IVA_SPAIN = new BigDecimal("0.21");
    private static final BigDecimal PAYMENT_FEE = new BigDecimal("0.02");
    private static final BigDecimal FIXED_FEE = new BigDecimal("0.35");
    
    private final Random random = new Random();

    public List<PlatformPrice> searchGame(String gameTitle) {
        List<PlatformPrice> prices = new ArrayList<>();
        
        if (gameTitle == null || gameTitle.trim().isEmpty()) {
            return prices;
        }
        
        System.out.println("🔍 Buscando en Instant Gaming: " + gameTitle);
        
        WebDriver driver = null;
        
        try {
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0");
            
            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            
            String cleanTitle = cleanGameTitle(gameTitle);
            String searchUrl = SEARCH_URL + cleanTitle.replace(" ", "+");
            
            System.out.println("   URL: " + searchUrl);
            driver.get(searchUrl);
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".product, .item-product, .grid-item")));
            
            Thread.sleep(3000);
            
            List<WebElement> products = driver.findElements(By.cssSelector(".product, .item-product, .grid-item, .product-item"));
            System.out.println("   Productos encontrados: " + products.size());
            
            for (WebElement product : products) {
                try {
                    String title = "";
                    String[] titleSelectors = {".title", ".name", "h3", ".product-title"};
                    for (String selector : titleSelectors) {
                        try {
                            WebElement titleEl = product.findElement(By.cssSelector(selector));
                            if (titleEl != null) {
                                title = titleEl.getText();
                                if (!title.isEmpty()) break;
                            }
                        } catch (Exception e) {}
                    }
                    
                    if (title.isEmpty()) continue;
                    
                    System.out.println("   Producto encontrado: " + title);
                    
                    if (!title.toLowerCase().contains(cleanTitle.substring(0, Math.min(cleanTitle.length(), 10)))) {
                        continue;
                    }
                    
                    String priceText = "";
                    String[] priceSelectors = {".price", ".amount", ".price-number"};
                    for (String selector : priceSelectors) {
                        try {
                            WebElement priceEl = product.findElement(By.cssSelector(selector));
                            if (priceEl != null) {
                                priceText = priceEl.getText();
                                if (!priceText.isEmpty()) break;
                            }
                        } catch (Exception e) {}
                    }
                    
                    BigDecimal price = extractPrice(priceText);
                    
                    if (price != null) {
                        WebElement linkEl = product.findElement(By.cssSelector("a"));
                        String productUrl = linkEl.getAttribute("href");
                        
                        PlatformPrice platformPrice = new PlatformPrice();
                        platformPrice.setPlatform("INSTANT_GAMING");
                        platformPrice.setStoreName("Instant Gaming");
                        platformPrice.setPrice(price);
                        platformPrice.setOriginalPrice(price);
                        platformPrice.setUrl(productUrl != null ? productUrl : BASE_URL + productUrl);
                        platformPrice.setCurrency("EUR");
                        platformPrice.setRegion("ES");
                        
                        calculateSpanishFees(platformPrice);
                        prices.add(platformPrice);
                        
                        System.out.println("   ✅ Precio IG: " + price + "€ (total: " + platformPrice.getTotalWithFees() + "€)");
                        break;
                    }
                    
                } catch (Exception e) {
                    System.err.println("   Error procesando producto: " + e.getMessage());
                }
            }
            
            Thread.sleep(random.nextInt(2000) + 1000);
            
        } catch (Exception e) {
            System.err.println("Error en Instant Gaming: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
        
        return prices;
    }
    
    private String cleanGameTitle(String title) {
        String cleaned = title.toLowerCase();
        cleaned = cleaned.replace(" - ", " ");
        cleaned = cleaned.replace(": ", " ");
        cleaned = cleaned.replaceAll("\\b(edition|deluxe|collector|gold|ultimate|complete|game of the year|goty|standard|special)\\b", "");
        cleaned = cleaned.replaceAll("[^a-z0-9 ]", "");
        cleaned = cleaned.trim();
        
        if (cleaned.length() < 3) {
            cleaned = title.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
        }
        
        return cleaned;
    }
    
    private void calculateSpanishFees(PlatformPrice price) {
        if (price.getPrice() == null) return;
        
        BigDecimal basePrice = price.getPrice();
        BigDecimal ivaAmount = basePrice.multiply(IVA_SPAIN).setScale(2, RoundingMode.HALF_UP);
        BigDecimal commissionAmount = basePrice.multiply(PAYMENT_FEE).add(FIXED_FEE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalWithFees = basePrice.add(ivaAmount).add(commissionAmount).setScale(2, RoundingMode.HALF_UP);
        
        price.setIvaAmount(ivaAmount);
        price.setCommissionAmount(commissionAmount);
        price.setTotalWithFees(totalWithFees);
        price.setHasFeesCalculated(true);
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
        } catch (NumberFormatException e) {
            System.err.println("Error parsing price: " + priceText);
        }
        return null;
    }
}
