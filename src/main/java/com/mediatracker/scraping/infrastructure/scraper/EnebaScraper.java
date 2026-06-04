package com.mediatracker.scraping.infrastructure.scraper;

import com.mediatracker.scraping.domain.model.ScrapedGame;
import com.mediatracker.scraping.domain.model.GameType;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class EnebaScraper {

    private static final String BASE_SEARCH_URL = "https://www.eneba.com/es/store/all?regions[]=europe&regions[]=global&text=";

    public List<ScrapedGame> searchGames(String gameTitle) {
        List<ScrapedGame> games = new ArrayList<>();
        if (gameTitle == null || gameTitle.trim().isEmpty()) return games;

        WebDriver driver = null;

        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            
            options.addArguments("--disable-gpu", "--window-size=1920,1080");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            driver = new ChromeDriver(options);
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Set<String> processedUrls = new HashSet<>();

            // 🔥 BUCLE DE PAGINACIÓN (Escanea hasta 3 páginas de Eneba) 🔥
            int maxPages = 3; 
            
            for (int page = 1; page <= maxPages; page++) {
                String searchUrl = BASE_SEARCH_URL + gameTitle.replace(" ", "%20") + "&page=" + page;
                System.out.println("\n=========================================");
                System.out.println("🔍 Buscando en Eneba (Página " + page + "): " + searchUrl);
                driver.get(searchUrl);
                
                System.out.println("⬇️ Haciendo scroll de arriba a abajo...");
                long lastHeight = (long) js.executeScript("return document.body.scrollHeight");
                for (int i = 0; i < 5; i++) {
                    js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                    Thread.sleep(1500);
                    long newHeight = (long) js.executeScript("return document.body.scrollHeight");
                    if (newHeight == lastHeight) break;
                    lastHeight = newHeight;
                }

                String script = 
                    "var results = [];" +
                    "var leafNodes = document.querySelectorAll('*');" +
                    "for(var i=0; i<leafNodes.length; i++) {" +
                    "    var el = leafNodes[i];" +
                    "    if(el.children.length === 0 && (el.textContent.includes('€') || el.textContent.includes('EUR'))) {" +
                    "        var parent = el.parentElement;" +
                    "        for(var j=0; j<8; j++) {" +
                    "            if(!parent) break;" +
                    "            var img = parent.querySelector('img');" +
                    "            var a = parent.querySelector('a');" +
                    "            if(img && a && parent.innerText) {" +
                    "                results.push({" +
                    "                    url: a.href," +
                    "                    text: parent.innerText," +
                    "                    image: img.src" +
                    "                });" +
                    "                break;" + 
                    "            }" +
                    "            parent = parent.parentElement;" +
                    "        }" +
                    "    }" +
                    "}" +
                    "return results;";

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> extractedCards = (List<Map<String, Object>>) js.executeScript(script);
                
                System.out.println("👀 Detectados " + extractedCards.size() + " bloques en la página " + page);

                int gamesAddedInThisPage = 0;

                for (Map<String, Object> cardData : extractedCards) {
                    try {
                        String url = (String) cardData.get("url");
                        String rawText = (String) cardData.get("text");
                        String text = rawText.replaceAll("(?i)cashback", "").trim();
                        String imgUrl = (String) cardData.get("image");
                        
                        // Evitamos duplicados entre páginas
                        if (processedUrls.contains(url)) continue;
                        processedUrls.add(url);

                        String[] lines = text.split("\\r?\\n");
                        String title = "";
                        BigDecimal price = null;
                        
                        for (String line : lines) {
                            line = line.trim();
                            if (line.isEmpty()) continue;
                            
                            String lowerLine = line.toLowerCase();
                            
                            if (lowerLine.contains("rango de precios") || lowerLine.startsWith("desde") || 
                                lowerLine.contains("vendido por") || lowerLine.contains("añadir al carrito") ||
                                lowerLine.contains("ver ofertas") || lowerLine.equals("país") || 
                                lowerLine.equals("europa") || lowerLine.equals("global")) {
                                continue;
                            }

                            if (line.contains("€") || line.contains("EUR")) {
                                String cleanPrice = line.replaceAll("[^0-9,]", "").replace(",", ".");
                                if (!cleanPrice.isEmpty()) {
                                    price = new BigDecimal(cleanPrice);
                                }
                            } else {
                                if (line.length() > title.length()) {
                                    title = line;
                                }
                            }
                        }
                        
                        if (price != null && !title.isEmpty()) {
                            String lowerTitle = title.toLowerCase();
                            
                            // 🛑 FILTRO ARREGLADO: Bloquea basura, pero respeta Steam, GOG, PSN, etc. 🛑
                            if (lowerTitle.contains("minecoins") || lowerTitle.contains(" points") || 
                                lowerTitle.contains("cash card") || lowerTitle.contains("shark") || 
                                lowerTitle.contains("gift card") || lowerTitle.contains("tarjeta") ||
                                lowerTitle.contains("wallet") || lowerTitle.contains("monedero") ||
                                lowerTitle.contains("dlc") || lowerTitle.contains("pass") ||
                                lowerTitle.contains("account") || lowerTitle.contains("cuenta") ||
                                lowerTitle.length() < 15) { 
                                continue; 
                            }

                            System.out.println("-> Analizando: " + title + " | Precio: " + price + "€");
                            
                            ScrapedGame game = new ScrapedGame();
                            game.setTitle(title);
                            game.setPrice(price);
                            game.setUrl(url);
                            game.setCoverArtUrl(imgUrl);
                            
                            String upperTitle = title.toUpperCase();
                            if (upperTitle.contains("EUROPE") || upperTitle.contains("EU ") || upperTitle.endsWith(" EU")) {
                                game.setSource("ENEBA - EUROPA 🇪🇺");
                            } else if (upperTitle.contains("GLOBAL")) {
                                game.setSource("ENEBA - GLOBAL 🌍");
                            } else {
                                game.setSource("ENEBA - EU/GLOBAL ✔️");
                            }

                            game.setGameType(GameType.GAME);
                            if(upperTitle.contains("EDITION") || upperTitle.contains("DELUXE") || upperTitle.contains("ULTIMATE")) {
                                game.setGameType(GameType.EDITION);
                            } else if (upperTitle.contains("DLC") || upperTitle.contains("PASS")) {
                                game.setGameType(GameType.DLC);
                            }
                            
                            games.add(game);
                            gamesAddedInThisPage++;
                        }
                    } catch (Exception ex) {}
                }
                
                // Si la página no nos ha dado ningún juego nuevo, cortamos el bucle
                if (gamesAddedInThisPage == 0) {
                    System.out.println("⏭️ No hay más resultados relevantes. Pasando al análisis final...");
                    break;
                }
            }
            
            System.out.println("\n✅ TOTAL FINAL: " + games.size() + " juegos leídos en todas las páginas.");
            System.out.println("=========================================\n");

        } catch (Exception e) {
            System.err.println("❌ Error scraping Eneba: " + e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignore) {}
            }
        }
        return games;
    }
}