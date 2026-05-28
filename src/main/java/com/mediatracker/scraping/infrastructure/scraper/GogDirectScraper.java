package com.mediatracker.scraping.infrastructure.scraper;

import com.mediatracker.scraping.domain.model.StorePrice;
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
public class GogDirectScraper {
    
    private static final String GOGDB_SEARCH = "https://www.gogdb.org/products?search=";
    private static final String GOGDB_PRODUCT = "https://www.gogdb.org/product/";
    
    public StorePrice searchGame(String gameName) {
        StorePrice result = new StorePrice();
        result.setStoreName("GOG");
        result.setAvailable(false);
        
        if (gameName == null || gameName.trim().isEmpty()) {
            return result;
        }
        
        System.out.println("\n🎮 GOG Direct: " + gameName);
        
        try {
            // PASO 1: Obtener el ID desde la búsqueda
            String searchUrl = GOGDB_SEARCH + gameName.replace(" ", "+");
            System.out.println("   🔍 Buscando ID: " + searchUrl);
            
            Document searchDoc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();
            
            Elements rows = searchDoc.select("table tr");
            String normalizedSearch = normalizeExact(gameName);
            String gameId = null;
            String gameTitle = null;
            
            for (int i = 1; i < rows.size(); i++) {
                Elements cells = rows.get(i).select("td");
                if (cells.size() >= 4) {
                    String id = cells.get(1).text().trim();
                    String name = cells.get(2).text().trim();
                    String type = cells.get(3).text().trim();
                    
                    String normalizedName = normalizeExact(name);
                    
                    if (normalizedName.equals(normalizedSearch)) {
                        gameId = id;
                        gameTitle = name;
                        System.out.println("   ✅ ID encontrado: " + gameId + " - " + gameTitle);
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
            result.setAvailable(true);
            result.setUrl("https://www.gog.com/game/" + gameId);
            
            // PASO 2: Obtener el precio desde la página del producto
            String productUrl = GOGDB_PRODUCT + gameId;
            System.out.println("   🌐 Obteniendo precio: " + productUrl);
            
            Document productDoc = Jsoup.connect(productUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();
            
            // Buscar la tabla de precios (la que está después del encabezado "Prices")
            Elements allTables = productDoc.select("table");
            System.out.println("   📋 Tablas encontradas: " + allTables.size());
            
            Element priceTable = null;
            for (Element table : allTables) {
                // Buscar la tabla que contiene encabezados de precios
                if (table.text().contains("Start") && table.text().contains("End") && table.text().contains("Final")) {
                    priceTable = table;
                    System.out.println("   ✅ Tabla de precios encontrada");
                    break;
                }
            }
            
            if (priceTable != null) {
                Elements priceRows = priceTable.select("tr");
                System.out.println("   📊 Filas de precios encontradas: " + (priceRows.size() - 1));
                
                String today = LocalDate.now().toString();
                System.out.println("   📅 Fecha actual: " + today);
                
                BigDecimal finalPrice = null;
                String finalPriceText = null;
                
                // Recorrer las filas de precios (saltando la cabecera)
                for (int i = 1; i < priceRows.size(); i++) {
                    Elements cells = priceRows.get(i).select("td");
                    if (cells.size() >= 5) {
                        String startDate = cells.get(0).text().trim();
                        String endDate = cells.get(1).text().trim();
                        String finalPriceCell = cells.get(3).text().trim();
                        
                        // Verificar si la fecha actual está dentro del rango
                        if (isDateInRange(today, startDate, endDate)) {
                            finalPriceText = finalPriceCell;
                            System.out.println("      Precio encontrado para período " + startDate + " - " + endDate + ": " + finalPriceCell);
                            break;
                        }
                    }
                }
                
                // Si no encontró por fecha, tomar la última fila (precio más reciente)
                if (finalPriceText == null && priceRows.size() > 1) {
                    Elements lastRowCells = priceRows.get(priceRows.size() - 1).select("td");
                    if (lastRowCells.size() >= 5) {
                        finalPriceText = lastRowCells.get(3).text().trim();
                        System.out.println("      Usando último precio registrado: " + finalPriceText);
                    }
                }
                
                if (finalPriceText != null) {
                    // Limpiar el precio (quitar $, €, etc.)
                    String cleanPrice = finalPriceText.replace("$", "").replace("€", "").trim();
                    finalPrice = extractPrice(cleanPrice);
                    if (finalPrice != null) {
                        result.setCurrentPrice(finalPrice);
                        result.setCurrency("EUR");
                        System.out.println("   💰 PRECIO: " + finalPrice + "€");
                    }
                }
            }
            
            if (result.getCurrentPrice() == null) {
                System.out.println("   ⚠️ No se encontró precio en la tabla");
            }
            
        } catch (IOException e) {
            System.err.println("   ❌ Error: " + e.getMessage());
        }
        
        return result;
    }
    
    private boolean isDateInRange(String targetDate, String startDate, String endDate) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate target = LocalDate.parse(targetDate);
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            return (target.isEqual(start) || target.isAfter(start)) && 
                   (target.isEqual(end) || target.isBefore(end));
        } catch (Exception e) {
            return false;
        }
    }
    
    private BigDecimal extractPrice(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            Pattern pattern = Pattern.compile("\\d+\\.?\\d*");
            var matcher = pattern.matcher(text);
            if (matcher.find()) {
                return new BigDecimal(matcher.group());
            }
        } catch (NumberFormatException e) {}
        return null;
    }
    
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
}
