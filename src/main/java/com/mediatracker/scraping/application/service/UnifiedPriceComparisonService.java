package com.mediatracker.scraping.application.service;

import com.mediatracker.scraping.domain.model.StorePrice;
import com.mediatracker.scraping.infrastructure.scraper.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class UnifiedPriceComparisonService {
    
    private final RawgApiScraper rawgScraper;
    private final SteamDatabaseScraper steamScraper;
    private final GogDirectScraper gogScraper;
    
    public UnifiedPriceComparisonService(RawgApiScraper rawgScraper,
                                          SteamDatabaseScraper steamScraper,
                                          GogDirectScraper gogScraper) {
        this.rawgScraper = rawgScraper;
        this.steamScraper = steamScraper;
        this.gogScraper = gogScraper;
    }
    
    public UnifiedComparison getFullComparison(String gameName) {
        UnifiedComparison comparison = new UnifiedComparison();
        comparison.setGameName(gameName);
        
        System.out.println("\n═══════════════════════════════════════");
        System.out.println("   BUSCANDO: " + gameName);
        System.out.println("═══════════════════════════════════════\n");
        
        // RAWg para nombre exacto
        RawgApiScraper.RawgGame rawgGame = rawgScraper.searchExactGame(gameName);
        
        String exactGameName = gameName;
        String gameImage = null;
        
        if (rawgGame != null) {
            exactGameName = rawgGame.getName();
            gameImage = rawgGame.getBackgroundImage();
            System.out.println("📦 RAWg → " + exactGameName);
        }
        
        comparison.setGameTitle(exactGameName);
        comparison.setGameImage(gameImage);
        
        // Steam
        System.out.println("\n🎮 STEAM:");
        StorePrice steamPrice = steamScraper.searchGameByName(exactGameName, "ES");
        comparison.setSteam(steamPrice);
        if (steamPrice.isAvailable()) {
            System.out.println("   ✅ " + steamPrice.getCurrentPrice() + "€");
        } else {
            System.out.println("   ❌ No encontrado");
        }
        
        // GOG Direct (Jsoup)
        System.out.println("\n🎮 GOG Direct (Jsoup):");
        StorePrice gogPrice = gogScraper.searchGame(exactGameName);
        comparison.setGog(gogPrice);
        if (gogPrice.isAvailable() && gogPrice.getCurrentPrice() != null) {
            System.out.println("   ✅ " + gogPrice.getCurrentPrice() + "€");
        } else {
            System.out.println("   ❌ No encontrado");
        }
        
        comparison.setEpic(null);
        comparison.calculateBestOption();
        
        System.out.println("\n═══════════════════════════════════════");
        System.out.println("   MEJOR OPCIÓN: " + comparison.getBestStore() + " - " + comparison.getBestPrice() + "€");
        System.out.println("═══════════════════════════════════════\n");
        
        return comparison;
    }
    
    public static class UnifiedComparison {
        private String gameName;
        private String gameTitle;
        private String gameImage;
        private StorePrice steam;
        private StorePrice gog;
        private StorePrice epic;
        private String bestStore;
        private BigDecimal bestPrice;
        
        public String getGameName() { return gameName; }
        public void setGameName(String gameName) { this.gameName = gameName; }
        public String getGameTitle() { return gameTitle; }
        public void setGameTitle(String gameTitle) { this.gameTitle = gameTitle; }
        public String getGameImage() { return gameImage; }
        public void setGameImage(String gameImage) { this.gameImage = gameImage; }
        public StorePrice getSteam() { return steam; }
        public void setSteam(StorePrice steam) { this.steam = steam; }
        public StorePrice getGog() { return gog; }
        public void setGog(StorePrice gog) { this.gog = gog; }
        public StorePrice getEpic() { return epic; }
        public void setEpic(StorePrice epic) { this.epic = epic; }
        public String getBestStore() { return bestStore; }
        public BigDecimal getBestPrice() { return bestPrice; }
        
        public void calculateBestOption() {
            BigDecimal lowest = null;
            String best = null;
            
            if (steam != null && steam.isAvailable() && steam.getCurrentPrice() != null) {
                lowest = steam.getCurrentPrice();
                best = "Steam";
            }
            if (gog != null && gog.isAvailable() && gog.getCurrentPrice() != null) {
                if (lowest == null || gog.getCurrentPrice().compareTo(lowest) < 0) {
                    lowest = gog.getCurrentPrice();
                    best = "GOG";
                }
            }
            
            this.bestStore = best;
            this.bestPrice = lowest;
        }
    }
}
