package com.mediatracker.scraping.infrastructure.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediatracker.scraping.domain.model.ScrapedGame;
import com.mediatracker.scraping.domain.model.GameType;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class SteamDetailsScraper {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Obtiene TODOS los detalles de un juego de Steam incluyendo ediciones y DLCs
     */
    public SteamGameFullDetails getFullGameDetails(String steamId) {
        SteamGameFullDetails details = new SteamGameFullDetails();
        details.setSteamId(steamId);
        
        try {
            // API principal de Steam
            String apiUrl = "https://store.steampowered.com/api/appdetails?appids=" + steamId + "&cc=es&l=spanish";
            String jsonResponse = Jsoup.connect(apiUrl)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .execute()
                    .body();
            
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode appData = root.path(steamId).path("data");
            
            if (appData.isMissingNode()) {
                return details;
            }
            
            // Información básica
            details.setTitle(appData.path("name").asText());
            details.setDescription(appData.path("short_description").asText());
            details.setHeaderImage(appData.path("header_image").asText());
            details.setDeveloper(appData.path("developers").get(0).asText());
            details.setPublisher(appData.path("publishers").get(0).asText());
            details.setReleaseDate(appData.path("release_date").path("date").asText());
            
            // Precio actual
            JsonNode priceOverview = appData.path("price_overview");
            if (!priceOverview.isMissingNode()) {
                details.setCurrentPrice(BigDecimal.valueOf(priceOverview.path("final").asInt() / 100.0));
                details.setOriginalPrice(BigDecimal.valueOf(priceOverview.path("initial").asInt() / 100.0));
                details.setDiscountPercent(priceOverview.path("discount_percent").asInt());
            }
            
            // ========== 1. OBTENER EDICIONES ESPECIALES ==========
            List<SteamEdition> editions = new ArrayList<>();
            JsonNode packages = appData.path("packages");
            
            for (JsonNode pkgId : packages) {
                String packageUrl = "https://store.steampowered.com/api/packagedetails?packageids=" + pkgId.asInt() + "&cc=es";
                try {
                    String pkgJson = Jsoup.connect(packageUrl)
                            .ignoreContentType(true)
                            .userAgent("Mozilla/5.0")
                            .timeout(10000)
                            .execute()
                            .body();
                    
                    JsonNode pkgRoot = mapper.readTree(pkgJson);
                    JsonNode pkgData = pkgRoot.path(pkgId.asInt() + "").path("data");
                    
                    if (!pkgData.isMissingNode()) {
                        String pkgName = pkgData.path("name").asText();
                        // Filtrar solo ediciones especiales (no el juego base)
                        if (isSpecialEdition(pkgName) && !pkgName.equalsIgnoreCase(details.getTitle())) {
                            SteamEdition edition = new SteamEdition();
                            edition.setName(pkgName);
                            edition.setType(extractEditionType(pkgName));
                            
                            JsonNode pkgPrice = pkgData.path("price");
                            if (!pkgPrice.isMissingNode()) {
                                edition.setPrice(BigDecimal.valueOf(pkgPrice.path("final").asInt() / 100.0));
                                edition.setOriginalPrice(BigDecimal.valueOf(pkgPrice.path("initial").asInt() / 100.0));
                                if (edition.getOriginalPrice() != null && edition.getOriginalPrice().compareTo(edition.getPrice()) > 0) {
                                    int discount = (int) ((edition.getOriginalPrice().doubleValue() - edition.getPrice().doubleValue()) 
                                            / edition.getOriginalPrice().doubleValue() * 100);
                                    edition.setDiscountPercent(discount);
                                }
                            }
                            editions.add(edition);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error obteniendo paquete: " + e.getMessage());
                }
            }
            details.setEditions(editions);
            
            // ========== 2. OBTENER DLCs ==========
            List<SteamDlc> dlcs = new ArrayList<>();
            JsonNode dlcIds = appData.path("dlc");
            
            for (JsonNode dlcId : dlcIds) {
                String dlcUrl = "https://store.steampowered.com/api/appdetails?appids=" + dlcId.asText() + "&cc=es";
                try {
                    String dlcJson = Jsoup.connect(dlcUrl)
                            .ignoreContentType(true)
                            .userAgent("Mozilla/5.0")
                            .timeout(10000)
                            .execute()
                            .body();
                    
                    JsonNode dlcRoot = mapper.readTree(dlcJson);
                    JsonNode dlcData = dlcRoot.path(dlcId.asText()).path("data");
                    
                    if (!dlcData.isMissingNode()) {
                        SteamDlc dlc = new SteamDlc();
                        dlc.setId(dlcId.asText());
                        dlc.setName(dlcData.path("name").asText());
                        dlc.setDescription(dlcData.path("short_description").asText());
                        
                        JsonNode dlcPrice = dlcData.path("price_overview");
                        if (!dlcPrice.isMissingNode()) {
                            dlc.setCurrentPrice(BigDecimal.valueOf(dlcPrice.path("final").asInt() / 100.0));
                            dlc.setOriginalPrice(BigDecimal.valueOf(dlcPrice.path("initial").asInt() / 100.0));
                            dlc.setDiscountPercent(dlcPrice.path("discount_percent").asInt());
                        }
                        
                        dlcs.add(dlc);
                    }
                } catch (Exception e) {
                    System.err.println("Error obteniendo DLC: " + e.getMessage());
                }
            }
            details.setDlcs(dlcs);
            
        } catch (IOException e) {
            System.err.println("Error en SteamDetailsScraper: " + e.getMessage());
        }
        
        return details;
    }
    
    private boolean isSpecialEdition(String name) {
        String lower = name.toLowerCase();
        return lower.contains("deluxe") || lower.contains("gold") || lower.contains("ultimate") ||
               lower.contains("collector") || lower.contains("legendary") || lower.contains("game of the year") ||
               lower.contains("goty") || lower.contains("premium") || lower.contains("digital deluxe");
    }
    
    private String extractEditionType(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("deluxe")) return "Deluxe Edition";
        if (lower.contains("gold")) return "Gold Edition";
        if (lower.contains("ultimate")) return "Ultimate Edition";
        if (lower.contains("collector")) return "Collector's Edition";
        if (lower.contains("legendary")) return "Legendary Edition";
        if (lower.contains("game of the year") || lower.contains("goty")) return "Game of the Year Edition";
        if (lower.contains("premium")) return "Premium Edition";
        return "Special Edition";
    }
    
    // Clases internas para los datos
    public static class SteamGameFullDetails {
        private String steamId;
        private String title;
        private String description;
        private String headerImage;
        private String developer;
        private String publisher;
        private String releaseDate;
        private BigDecimal currentPrice;
        private BigDecimal originalPrice;
        private Integer discountPercent;
        private List<SteamEdition> editions = new ArrayList<>();
        private List<SteamDlc> dlcs = new ArrayList<>();
        
        // Getters y Setters
        public String getSteamId() { return steamId; }
        public void setSteamId(String steamId) { this.steamId = steamId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getHeaderImage() { return headerImage; }
        public void setHeaderImage(String headerImage) { this.headerImage = headerImage; }
        public String getDeveloper() { return developer; }
        public void setDeveloper(String developer) { this.developer = developer; }
        public String getPublisher() { return publisher; }
        public void setPublisher(String publisher) { this.publisher = publisher; }
        public String getReleaseDate() { return releaseDate; }
        public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        public BigDecimal getOriginalPrice() { return originalPrice; }
        public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }
        public Integer getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }
        public List<SteamEdition> getEditions() { return editions; }
        public void setEditions(List<SteamEdition> editions) { this.editions = editions; }
        public List<SteamDlc> getDlcs() { return dlcs; }
        public void setDlcs(List<SteamDlc> dlcs) { this.dlcs = dlcs; }
    }
    
    public static class SteamEdition {
        private String name;
        private String type;
        private BigDecimal price;
        private BigDecimal originalPrice;
        private Integer discountPercent;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public BigDecimal getOriginalPrice() { return originalPrice; }
        public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }
        public Integer getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }
    }
    
    public static class SteamDlc {
        private String id;
        private String name;
        private String description;
        private BigDecimal currentPrice;
        private BigDecimal originalPrice;
        private Integer discountPercent;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        public BigDecimal getOriginalPrice() { return originalPrice; }
        public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }
        public Integer getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }
    }
}
