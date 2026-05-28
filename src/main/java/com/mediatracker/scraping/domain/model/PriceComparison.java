package com.mediatracker.scraping.domain.model;

import java.math.BigDecimal;
import java.util.List;

public class PriceComparison {
    // Steam
    private BigDecimal steamPrice;
    private BigDecimal steamOriginalPrice;
    private Integer steamDiscount;
    private String steamUrl;
    private boolean steamAvailable;
    
    // Steam Editions & DLCs
    private List<SteamEdition> steamEditions;
    private List<SteamDlc> steamDlcs;
    
    // GOG
    private BigDecimal gogPrice;
    private BigDecimal gogOriginalPrice;
    private Integer gogDiscount;
    private String gogUrl;
    private boolean gogAvailable;
    
    // Instant Gaming
    private BigDecimal instantGamingPrice;
    private BigDecimal instantGamingTotalPrice;
    private String instantGamingUrl;
    private BigDecimal instantGamingIva;
    private BigDecimal instantGamingCommission;
    private List<PlatformPrice> instantGamingPrices;
    private boolean instantGamingAvailable;
    
    // Comparativa
    private String bestOption;
    private BigDecimal bestPrice;
    private BigDecimal savings;
    private String gameTitle;
    private String gameImageUrl;
    
    // Getters y Setters - Steam
    public BigDecimal getSteamPrice() { return steamPrice; }
    public void setSteamPrice(BigDecimal steamPrice) { this.steamPrice = steamPrice; }
    
    public BigDecimal getSteamOriginalPrice() { return steamOriginalPrice; }
    public void setSteamOriginalPrice(BigDecimal steamOriginalPrice) { this.steamOriginalPrice = steamOriginalPrice; }
    
    public Integer getSteamDiscount() { return steamDiscount; }
    public void setSteamDiscount(Integer steamDiscount) { this.steamDiscount = steamDiscount; }
    
    public String getSteamUrl() { return steamUrl; }
    public void setSteamUrl(String steamUrl) { this.steamUrl = steamUrl; }
    
    public boolean isSteamAvailable() { return steamAvailable; }
    public void setSteamAvailable(boolean steamAvailable) { this.steamAvailable = steamAvailable; }
    
    // Steam Editions & DLCs
    public List<SteamEdition> getSteamEditions() { return steamEditions; }
    public void setSteamEditions(List<SteamEdition> steamEditions) { this.steamEditions = steamEditions; }
    
    public List<SteamDlc> getSteamDlcs() { return steamDlcs; }
    public void setSteamDlcs(List<SteamDlc> steamDlcs) { this.steamDlcs = steamDlcs; }
    
    // Getters y Setters - GOG
    public BigDecimal getGogPrice() { return gogPrice; }
    public void setGogPrice(BigDecimal gogPrice) { this.gogPrice = gogPrice; }
    
    public BigDecimal getGogOriginalPrice() { return gogOriginalPrice; }
    public void setGogOriginalPrice(BigDecimal gogOriginalPrice) { this.gogOriginalPrice = gogOriginalPrice; }
    
    public Integer getGogDiscount() { return gogDiscount; }
    public void setGogDiscount(Integer gogDiscount) { this.gogDiscount = gogDiscount; }
    
    public String getGogUrl() { return gogUrl; }
    public void setGogUrl(String gogUrl) { this.gogUrl = gogUrl; }
    
    public boolean isGogAvailable() { return gogAvailable; }
    public void setGogAvailable(boolean gogAvailable) { this.gogAvailable = gogAvailable; }
    
    // Getters y Setters - Instant Gaming
    public BigDecimal getInstantGamingPrice() { return instantGamingPrice; }
    public void setInstantGamingPrice(BigDecimal instantGamingPrice) { this.instantGamingPrice = instantGamingPrice; }
    
    public BigDecimal getInstantGamingTotalPrice() { return instantGamingTotalPrice; }
    public void setInstantGamingTotalPrice(BigDecimal instantGamingTotalPrice) { this.instantGamingTotalPrice = instantGamingTotalPrice; }
    
    public String getInstantGamingUrl() { return instantGamingUrl; }
    public void setInstantGamingUrl(String instantGamingUrl) { this.instantGamingUrl = instantGamingUrl; }
    
    public BigDecimal getInstantGamingIva() { return instantGamingIva; }
    public void setInstantGamingIva(BigDecimal instantGamingIva) { this.instantGamingIva = instantGamingIva; }
    
    public BigDecimal getInstantGamingCommission() { return instantGamingCommission; }
    public void setInstantGamingCommission(BigDecimal instantGamingCommission) { this.instantGamingCommission = instantGamingCommission; }
    
    public List<PlatformPrice> getInstantGamingPrices() { return instantGamingPrices; }
    public void setInstantGamingPrices(List<PlatformPrice> instantGamingPrices) { this.instantGamingPrices = instantGamingPrices; }
    
    public boolean isInstantGamingAvailable() { return instantGamingAvailable; }
    public void setInstantGamingAvailable(boolean instantGamingAvailable) { this.instantGamingAvailable = instantGamingAvailable; }
    
    // Getters y Setters - Comparativa
    public String getBestOption() { return bestOption; }
    public void setBestOption(String bestOption) { this.bestOption = bestOption; }
    
    public BigDecimal getBestPrice() { return bestPrice; }
    public void setBestPrice(BigDecimal bestPrice) { this.bestPrice = bestPrice; }
    
    public BigDecimal getSavings() { return savings; }
    public void setSavings(BigDecimal savings) { this.savings = savings; }
    
    public String getGameTitle() { return gameTitle; }
    public void setGameTitle(String gameTitle) { this.gameTitle = gameTitle; }
    
    public String getGameImageUrl() { return gameImageUrl; }
    public void setGameImageUrl(String gameImageUrl) { this.gameImageUrl = gameImageUrl; }
    
    // Métodos auxiliares
    public String getBestOptionIcon() {
        if ("STEAM".equals(bestOption)) return "fab fa-steam";
        if ("GOG".equals(bestOption)) return "fab fa-gog";
        if ("INSTANT_GAMING".equals(bestOption)) return "fas fa-tag";
        return "fas fa-question";
    }
    
    public String getBestOptionText() {
        if ("STEAM".equals(bestOption)) return "Steam";
        if ("GOG".equals(bestOption)) return "GOG";
        if ("INSTANT_GAMING".equals(bestOption)) return "Instant Gaming";
        return "No disponible";
    }
    
    public void calculateBestOption() {
        BigDecimal lowest = null;
        String best = null;
        
        if (steamAvailable && steamPrice != null) {
            lowest = steamPrice;
            best = "STEAM";
        }
        if (gogAvailable && gogPrice != null) {
            if (lowest == null || gogPrice.compareTo(lowest) < 0) {
                lowest = gogPrice;
                best = "GOG";
            }
        }
        if (instantGamingAvailable && instantGamingTotalPrice != null) {
            if (lowest == null || instantGamingTotalPrice.compareTo(lowest) < 0) {
                lowest = instantGamingTotalPrice;
                best = "INSTANT_GAMING";
            }
        }
        
        this.bestOption = best;
        this.bestPrice = lowest;
        
        if (steamAvailable && steamPrice != null && best != null && !"STEAM".equals(best) && lowest != null) {
            this.savings = steamPrice.subtract(lowest);
        }
    }
    
    // Clases internas
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
