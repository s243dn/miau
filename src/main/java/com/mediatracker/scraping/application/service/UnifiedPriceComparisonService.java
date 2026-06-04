package com.mediatracker.scraping.application.service;

import com.mediatracker.scraping.domain.model.ScrapedGame;
import com.mediatracker.scraping.domain.model.StorePrice;
import com.mediatracker.scraping.infrastructure.scraper.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class UnifiedPriceComparisonService {

    private final RawgApiScraper rawgScraper;
    private final SteamDatabaseScraper steamScraper;
    private final GogDirectScraper gogScraper;
    private final EnebaScraper enebaScraper;
    private final HumbleBundleScraper humbleScraper;
    private final FanaticalScraper fanaticalScraper;
    private final RestTemplate restTemplate;

    public UnifiedPriceComparisonService(
            RawgApiScraper rawgScraper,
            SteamDatabaseScraper steamScraper,
            GogDirectScraper gogScraper,
            EnebaScraper enebaScraper,
            HumbleBundleScraper humbleScraper,
            FanaticalScraper fanaticalScraper) {

        this.rawgScraper = rawgScraper;
        this.steamScraper = steamScraper;
        this.gogScraper = gogScraper;
        this.enebaScraper = enebaScraper;
        this.humbleScraper = humbleScraper;
        this.fanaticalScraper = fanaticalScraper;
        this.restTemplate = new RestTemplate();
    }

    public UnifiedComparison getFullComparison(String gameName) {

        UnifiedComparison comparison = new UnifiedComparison();
        comparison.setGameName(gameName);

        System.out.println("\n═══════════════════════════════════════");
        System.out.println("   BUSCANDO GLOBALMENTE: " + gameName);
        System.out.println("═══════════════════════════════════════\n");

        RawgApiScraper.RawgGame rawgGame = rawgScraper.searchExactGame(gameName);

        String exactGameName = (rawgGame != null) ? rawgGame.getName() : gameName;
        String gameImage = (rawgGame != null) ? rawgGame.getBackgroundImage() : null;

        comparison.setGameTitle(exactGameName);
        comparison.setGameImage(gameImage);

        // ==========================
        // STEAM
        // ==========================
        System.out.println("\n🎮 STEAM:");
        StorePrice steamPrice = steamScraper.searchGameByName(exactGameName, "ES");
        comparison.setSteam(steamPrice);

        if (steamPrice != null && steamPrice.isAvailable() && steamPrice.getCurrentPrice() != null) {
            System.out.println("   ✅ " + steamPrice.getCurrentPrice() + "€");
        } else {
            System.out.println("   ❌ No encontrado");
        }

        // ==========================
        // GOG
        // ==========================
        System.out.println("\n🎮 GOG:");
        StorePrice gogPrice = gogScraper.searchGame(exactGameName);
        comparison.setGog(gogPrice);

        if (gogPrice != null && gogPrice.isAvailable() && gogPrice.getCurrentPrice() != null) {
            System.out.println("   ✅ " + gogPrice.getCurrentPrice() + "€");
        } else {
            System.out.println("   ❌ No encontrado");
        }

        // ==========================
        // ENEBA (más barato)
        // ==========================
        System.out.println("\n🎮 ENEBA:");
        try {
            comparison.setEneba(
                    processScraperCheapest(enebaScraper.searchGames(exactGameName), "Eneba")
            );
        } catch (Exception e) {
            System.out.println("   ❌ Error Eneba: " + e.getMessage());
        }

        // ==========================
        // HUMBLE BUNDLE (premium)
        // ==========================
        System.out.println("\n🎮 HUMBLE BUNDLE:");
        try {
            comparison.setHumbleBundle(
                    processScraperExpensive(humbleScraper.searchGames(exactGameName), "Humble Bundle")
            );
        } catch (Exception e) {
            System.out.println("   ❌ Error Humble: " + e.getMessage());
        }

        // ==========================
        // FANATICAL (premium)
        // ==========================
        System.out.println("\n🎮 FANATICAL:");
        try {
            comparison.setFanatical(
                    processScraperExpensive(fanaticalScraper.searchGames(exactGameName), "Fanatical")
            );
        } catch (Exception e) {
            System.out.println("   ❌ Error Fanatical: " + e.getMessage());
        }

        // ==========================
        // CHEAPSHARK
        // ==========================
        System.out.println("\n🌐 CHEAPSHARK:");

        StorePrice cheapSharkPrice = new StorePrice();
        cheapSharkPrice.setAvailable(false);

        try {
            String safeTitle = URLEncoder.encode(exactGameName, StandardCharsets.UTF_8);
            String url = "https://www.cheapshark.com/api/1.0/games?title=" + safeTitle;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restTemplate.getForObject(url, List.class);

            if (response != null && !response.isEmpty()) {
                String cheapest = (String) response.get(0).get("cheapest");

                if (cheapest != null) {
                    cheapSharkPrice.setAvailable(true);
                    cheapSharkPrice.setCurrentPrice(new BigDecimal(cheapest));

                    System.out.println("   ✅ " + cheapSharkPrice.getCurrentPrice() + "€");
                }
            }

        } catch (Exception e) {
            System.out.println("   ❌ Error CheapShark: " + e.getMessage());
        }

        comparison.setCheapShark(cheapSharkPrice);

        comparison.calculateBestOption();

        System.out.println("\n═══════════════════════════════════════");
        System.out.println("   MEJOR OPCIÓN: " + comparison.getBestStore()
                + " - " + comparison.getBestPrice() + "€");
        System.out.println("═══════════════════════════════════════\n");

        return comparison;
    }

    // ==========================
    // HELPERS
    // ==========================

    private StorePrice processScraperCheapest(List<ScrapedGame> games, String storeName) {
        StorePrice priceObj = new StorePrice();
        priceObj.setAvailable(false);

        if (games == null || games.isEmpty()) return priceObj;

        ScrapedGame best = null;

        BigDecimal min = new BigDecimal("2.00");
        BigDecimal max = new BigDecimal("100.00");

        for (ScrapedGame g : games) {
            if (g.getPrice() != null
                    && g.getPrice().compareTo(min) >= 0
                    && g.getPrice().compareTo(max) <= 0) {

                if (best == null || g.getPrice().compareTo(best.getPrice()) < 0) {
                    best = g;
                }
            }
        }

        if (best != null) {
            priceObj.setAvailable(true);
            priceObj.setCurrentPrice(best.getPrice());
            System.out.println("   ✅ " + storeName + ": " + best.getPrice() + "€");
        }

        return priceObj;
    }

    private StorePrice processScraperExpensive(List<ScrapedGame> games, String storeName) {
        StorePrice priceObj = new StorePrice();
        priceObj.setAvailable(false);

        if (games == null || games.isEmpty()) return priceObj;

        ScrapedGame best = null;

        BigDecimal min = new BigDecimal("15.00");
        BigDecimal max = new BigDecimal("70.00");

        for (ScrapedGame g : games) {
            if (g.getPrice() != null
                    && g.getPrice().compareTo(min) >= 0
                    && g.getPrice().compareTo(max) <= 0) {

                if (best == null || g.getPrice().compareTo(best.getPrice()) > 0) {
                    best = g;
                }
            }
        }

        if (best != null) {
            priceObj.setAvailable(true);
            priceObj.setCurrentPrice(best.getPrice());
            System.out.println("   ✅ " + storeName + ": " + best.getPrice() + "€");
        }

        return priceObj;
    }

    // ==========================
    // INNER CLASS
    // ==========================
    public static class UnifiedComparison {

        private String gameName;
        private String gameTitle;
        private String gameImage;

        private StorePrice steam;
        private StorePrice gog;
        private StorePrice epic;
        private StorePrice eneba;
        private StorePrice cheapShark;
        private StorePrice humbleBundle;
        private StorePrice fanatical;

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

        public StorePrice getEneba() { return eneba; }
        public void setEneba(StorePrice eneba) { this.eneba = eneba; }

        public StorePrice getCheapShark() { return cheapShark; }
        public void setCheapShark(StorePrice cheapShark) { this.cheapShark = cheapShark; }

        public StorePrice getHumbleBundle() { return humbleBundle; }
        public void setHumbleBundle(StorePrice humbleBundle) { this.humbleBundle = humbleBundle; }

        public StorePrice getFanatical() { return fanatical; }
        public void setFanatical(StorePrice fanatical) { this.fanatical = fanatical; }

        public String getBestStore() { return bestStore; }
        public BigDecimal getBestPrice() { return bestPrice; }

        public void calculateBestOption() {

            BigDecimal lowest = null;
            bestStore = null;

            lowest = checkStore(steam, "Steam", lowest);
            lowest = checkStore(gog, "GOG", lowest);
            lowest = checkStore(eneba, "Eneba", lowest);
            lowest = checkStore(humbleBundle, "Humble Bundle", lowest);
            lowest = checkStore(fanatical, "Fanatical", lowest);
            lowest = checkStore(cheapShark, "CheapShark", lowest);

            bestPrice = lowest;
        }

        private BigDecimal checkStore(StorePrice store, String name, BigDecimal currentLowest) {
            if (store != null && store.isAvailable() && store.getCurrentPrice() != null) {
                if (currentLowest == null
                        || store.getCurrentPrice().compareTo(currentLowest) < 0) {
                    bestStore = name;
                    return store.getCurrentPrice();
                }
            }
            return currentLowest;
        }
    }
}