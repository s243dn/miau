package com.mediatracker.scraping.infrastructure.scraper;

import com.mediatracker.scraping.domain.model.GogPrice;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Component
public class GogScraper {
    
    private static final String GOGDB_SEARCH = "https://www.gogdb.org/products?search=";
    private static final String GOGDB_PRODUCT = "https://www.gogdb.org/product/";
    
    public GogPrice searchGame(String gameName) {
        GogPrice result = new GogPrice();
        result.setAvailable(false);
        
        if (gameName == null || gameName.trim().isEmpty()) {
            return result;
        }
        
        System.out.println("\n🎮 GOGDB: " + gameName);
        
        try {
            String searchUrl = GOGDB_SEARCH + gameName.replace(" ", "+");
            System.out.println("   🔍 Buscando ID: " + searchUrl);
            
            Document searchDoc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();
            
            Elements rows = searchDoc.select("table tr");
            String gameId = null;
            String gameTitle = null;
            String normalizedSearch = normalizeString(gameName);
            
            for (int i = 1; i < rows.size(); i++) {
                Elements cells = rows.get(i).select("td");
                if (cells.size() >= 4) {
                    String id = cells.get(1).text().trim();
                    String name = cells.get(2).text().trim();
                    String type = cells.get(3).text().trim();
                    String normalizedName = normalizeString(name);
                    
                    if (normalizedName.equals(normalizedSearch) && !type.equals("DLC")) {
                        gameId = id;
                        gameTitle = name;
                        System.out.println("   ✅ ID encontrado: " + gameId);
                        break;
                    }
                }
            }
            
            if (gameId == null) {
                System.out.println("   ❌ No se encontró ID");
                return result;
            }
            
            result.setGameId(gameId);
            result.setTitle(gameTitle);
            result.setUrl("https://www.gog.com/game/" + gameId);
            
            String productUrl = GOGDB_PRODUCT + gameId;
            System.out.println("   🌐 Obteniendo precio: " + productUrl);
            
            Document productDoc = Jsoup.connect(productUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();
            
            Elements tables = productDoc.select("table");
            Element priceTable = null;
            
            for (Element table : tables) {
                if (table.text().contains("Start") && table.text().contains("End") && table.text().contains("Final")) {
                    priceTable = table;
                    break;
                }
            }
            
            if (priceTable != null) {
                Elements priceRows = priceTable.select("tr");
                for (int i = priceRows.size() - 1; i >= 0; i--) {
                    Elements cells = priceRows.get(i).select("td");
                    if (cells.size() >= 5) {
                        String finalPrice = cells.get(3).text().trim();
                        String discount = cells.get(4).text().trim();
                        
                        if (finalPrice != null && !finalPrice.isEmpty() && !finalPrice.equals("-")) {
                            String cleanPrice = finalPrice.replace("$", "").replace("€", "").trim();
                            BigDecimal price = extractPrice(cleanPrice);
                            if (price != null) {
                                result.setPrice(price);
                                result.setCurrency("EUR");
                                result.setAvailable(true);
                                
                                if (discount != null && !discount.isEmpty() && !discount.equals("-")) {
                                    String cleanDiscount = discount.replace("%", "").trim();
                                    try {
                                        result.setDiscount(Integer.parseInt(cleanDiscount));
                                    } catch (NumberFormatException e) {}
                                }
                                
                                System.out.println("   💰 PRECIO: " + price + "€" + 
                                    (result.getDiscount() != null ? " (DESCUENTO: -" + result.getDiscount() + "%)" : ""));
                                return result;
                            }
                        }
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.println("   ❌ Error: " + e.getMessage());
        }
        
        return result;
    }
    
    private BigDecimal extractPrice(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            Pattern pattern = Pattern.compile("\\d+\\.?\\d*");
            var matcher = pattern.matcher(text);
            if (matcher.find()) {
                return new BigDecimal(matcher.group());
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }
    
    private String normalizeString(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .trim()
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
                .replaceAll("\\s+", " ");
    }
}
