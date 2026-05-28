package com.mediatracker.scraping.application.service;

import com.mediatracker.scraping.domain.model.PriceComparison;
import com.mediatracker.scraping.infrastructure.scraper.GogScraper;
import com.mediatracker.scraping.infrastructure.scraper.SteamScraper;
import com.mediatracker.scraping.infrastructure.scraper.RawgApiScraper;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class GameComparisonService {
    
    private final RawgApiScraper rawgScraper;
    private final SteamScraper steamScraper;
    private final GogScraper gogScraper;
    
    public GameComparisonService(RawgApiScraper rawgScraper,
                                  SteamScraper steamScraper,
                                  GogScraper gogScraper) {
        this.rawgScraper = rawgScraper;
        this.steamScraper = steamScraper;
        this.gogScraper = gogScraper;
    }
    
    public PriceComparison getFullComparison(String gameName) {
        PriceComparison comparison = new PriceComparison();
        comparison.setGameTitle(gameName);
        
        System.out.println("\n=== COMPARATIVA: " + gameName + " ===");
        
        // 1. RAWg - nombre exacto e imagen
        RawgApiScraper.RawgGame rawgGame = rawgScraper.searchExactGame(gameName);
        String exactGameName = gameName;
        
        if (rawgGame != null) {
            exactGameName = rawgGame.getName();
            comparison.setGameTitle(exactGameName);
            comparison.setGameImageUrl(rawgGame.getBackgroundImage());
            System.out.println("📦 RAWg nombre exacto: " + exactGameName);
        }
        
        // 2. STEAM - usar searchByExactName
        try {
            var steamGame = steamScraper.searchByExactName(exactGameName);
            if (steamGame != null && steamGame.getPrice() != null) {
                comparison.setSteamAvailable(true);
                comparison.setSteamPrice(steamGame.getPrice());
                comparison.setSteamOriginalPrice(steamGame.getOriginalPrice());
                comparison.setSteamDiscount(steamGame.getDiscount());
                comparison.setSteamUrl(steamGame.getUrl());
                System.out.println("🎮 Steam: " + steamGame.getPrice() + "€" + 
                    (steamGame.getDiscount() != null ? " (-" + steamGame.getDiscount() + "%)" : ""));
            } else {
                System.out.println("⚠️ Steam: No se encontró el juego");
            }
        } catch (Exception e) {
            System.err.println("Error Steam: " + e.getMessage());
        }
        
        // 3. GOG
        try {
            var gogResult = gogScraper.searchGame(exactGameName);
            if (gogResult != null && gogResult.isAvailable() && gogResult.getPrice() != null) {
                comparison.setGogAvailable(true);
                comparison.setGogPrice(gogResult.getPrice());
                comparison.setGogOriginalPrice(gogResult.getOriginalPrice());
                comparison.setGogDiscount(gogResult.getDiscount());
                comparison.setGogUrl(gogResult.getUrl());
                System.out.println("🎮 GOG: " + gogResult.getPrice() + "€" + 
                    (gogResult.getDiscount() != null ? " (-" + gogResult.getDiscount() + "%)" : ""));
            }
        } catch (Exception e) {
            System.err.println("Error GOG: " + e.getMessage());
        }
        
        // 4. Mejor opción
        BigDecimal steamPrice = comparison.getSteamPrice();
        BigDecimal gogPrice = comparison.getGogPrice();
        
        if (steamPrice != null && gogPrice != null) {
            if (steamPrice.compareTo(gogPrice) <= 0) {
                comparison.setBestOption("STEAM");
                comparison.setBestPrice(steamPrice);
                comparison.setSavings(gogPrice.subtract(steamPrice));
            } else {
                comparison.setBestOption("GOG");
                comparison.setBestPrice(gogPrice);
                comparison.setSavings(steamPrice.subtract(gogPrice));
            }
        } else if (steamPrice != null) {
            comparison.setBestOption("STEAM");
            comparison.setBestPrice(steamPrice);
        } else if (gogPrice != null) {
            comparison.setBestOption("GOG");
            comparison.setBestPrice(gogPrice);
        }
        
        System.out.println("🏆 Mejor opción: " + (comparison.getBestOption() != null ? comparison.getBestOption() : "Ninguna"));
        return comparison;
    }
}
