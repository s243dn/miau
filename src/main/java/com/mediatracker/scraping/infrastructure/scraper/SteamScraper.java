package com.mediatracker.scraping.infrastructure.scraper;

import com.mediatracker.scraping.domain.model.ScrapedGame;
import com.mediatracker.scraping.domain.model.GameType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class SteamScraper {

    private final ObjectMapper mapper = new ObjectMapper();
    private long lastRequestTime = 0;

    private void waitForRateLimit() {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime;
        if (timeSinceLastRequest < 1000) {
            try { Thread.sleep(1000 - timeSinceLastRequest); } catch (InterruptedException e) {}
        }
        lastRequestTime = System.currentTimeMillis();
    }

    /**
     * Búsqueda SIN FILTROS - solo por nombre exacto
     */
    public ScrapedGame searchByExactName(String exactGameName) {
        if (exactGameName == null || exactGameName.trim().isEmpty()) {
            return null;
        }
        
        System.out.println("🎮 Buscando en Steam (sin filtros): " + exactGameName);
        
        String encodedTerm = URLEncoder.encode(exactGameName.trim(), StandardCharsets.UTF_8);
        // URL SIN filtros de precio, SIN filtros de idioma
        String url = "https://store.steampowered.com/search/?term=" + encodedTerm;
        
        try {
            waitForRateLimit();
            
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();

            Elements gameElements = doc.select("a.search_result_row");
            String normalizedSearch = normalizeTitle(exactGameName);
            
            System.out.println("  Resultados encontrados: " + gameElements.size());
            
            // Primera pasada: buscar coincidencia EXACTA
            for (Element element : gameElements) {
                String title = element.select(".title").text();
                if (title == null || title.isEmpty()) continue;
                
                String normalizedTitle = normalizeTitle(title);
                
                // Coincidencia exacta (ignorando mayúsculas/minúsculas)
                if (normalizedTitle.equals(normalizedSearch)) {
                    String steamId = extractAppId(element.attr("href"));
                    if (steamId != null && !steamId.isEmpty()) {
                        System.out.println("  ✅ Coincidencia EXACTA: " + title);
                        return getGameDetails(steamId);
                    }
                }
            }
            
            // Segunda pasada: buscar coincidencia que empiece igual
            for (Element element : gameElements) {
                String title = element.select(".title").text();
                if (title == null || title.isEmpty()) continue;
                
                String normalizedTitle = normalizeTitle(title);
                
                if (normalizedTitle.startsWith(normalizedSearch)) {
                    String steamId = extractAppId(element.attr("href"));
                    if (steamId != null && !steamId.isEmpty()) {
                        System.out.println("  ✅ Coincidencia por prefijo: " + title);
                        return getGameDetails(steamId);
                    }
                }
            }
            
            System.out.println("  ❌ No se encontró coincidencia para: " + exactGameName);
            
        } catch (IOException e) { 
            System.err.println("Error scraping Steam: " + e.getMessage());
        }
        
        return null;
    }
    
    private String normalizeTitle(String title) {
        return title.toLowerCase()
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
                .trim()
                .replaceAll("\\s+", " ");
    }

    public ScrapedGame getGameDetails(String steamId) {
        if (steamId == null || steamId.equals("null") || steamId.trim().isEmpty()) {
            return new ScrapedGame();
        }
        waitForRateLimit();
        
        ScrapedGame game = new ScrapedGame();
        game.setSteamId(steamId);
        game.setUrl("https://store.steampowered.com/app/" + steamId);
        game.setSource("STEAM");
        
        try {
            String apiUrl = "https://store.steampowered.com/api/appdetails?appids=" + steamId + "&cc=es";
            String jsonResponse = Jsoup.connect(apiUrl).ignoreContentType(true).userAgent("Mozilla/5.0").timeout(10000).execute().body();
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode appData = root.path(steamId).path("data");
            
            if (appData.isMissingNode()) {
                return getGameDetailsByScraping(steamId);
            }
            
            String title = appData.path("name").asText();
            if (title == null || title.isEmpty()) return getGameDetailsByScraping(steamId);
            
            game.setTitle(title);
            game.setHeaderImageUrl(appData.path("header_image").asText());
            game.setCoverArtUrl(appData.path("header_image").asText());
            game.setDescription(appData.path("short_description").asText());
            
            JsonNode developers = appData.path("developers");
            if (developers.size() > 0) game.setDeveloper(developers.get(0).asText());
            JsonNode publishers = appData.path("publishers");
            if (publishers.size() > 0) game.setPublisher(publishers.get(0).asText());
            game.setReleaseDate(appData.path("release_date").path("date").asText());
            
            JsonNode genres = appData.path("genres");
            List<String> genreList = new ArrayList<>();
            for (JsonNode genre : genres) genreList.add(genre.path("description").asText());
            game.setGenres(genreList);
            
            JsonNode priceOverview = appData.path("price_overview");
            if (!priceOverview.isMissingNode()) {
                int finalPrice = priceOverview.path("final").asInt();
                int initialPrice = priceOverview.path("initial").asInt();
                int discountPercent = priceOverview.path("discount_percent").asInt();
                game.setPrice(BigDecimal.valueOf(finalPrice / 100.0));
                if (initialPrice > 0 && initialPrice != finalPrice) {
                    game.setOriginalPrice(BigDecimal.valueOf(initialPrice / 100.0));
                    game.setDiscount(discountPercent);
                }
            }
            
            List<ScrapedGame> editions = new ArrayList<>();
            JsonNode packages = appData.path("packages");
            for (JsonNode pkgId : packages) {
                String packageUrl = "https://store.steampowered.com/api/packagedetails?packageids=" + pkgId.asInt() + "&cc=es";
                try {
                    String pkgJson = Jsoup.connect(packageUrl).ignoreContentType(true).userAgent("Mozilla/5.0").timeout(8000).execute().body();
                    JsonNode pkgRoot = mapper.readTree(pkgJson);
                    JsonNode pkgData = pkgRoot.path(pkgId.asInt() + "").path("data");
                    if (!pkgData.isMissingNode()) {
                        String pkgName = pkgData.path("name").asText();
                        JsonNode pkgPrice = pkgData.path("price");
                        if (!pkgName.equalsIgnoreCase(game.getTitle()) && !pkgName.toLowerCase().contains("standard")) {
                            ScrapedGame edition = new ScrapedGame();
                            edition.setTitle(pkgName);
                            edition.setSource("STEAM");
                            edition.setGameType(GameType.EDITION);
                            if (!pkgPrice.isMissingNode()) {
                                edition.setPrice(BigDecimal.valueOf(pkgPrice.path("final").asInt() / 100.0));
                            }
                            editions.add(edition);
                        }
                    }
                } catch (Exception e) {}
            }
            game.setEditions(editions);
            
            List<ScrapedGame> dlcs = new ArrayList<>();
            JsonNode dlcIds = appData.path("dlc");
            for (JsonNode dlcId : dlcIds) {
                String dlcUrl = "https://store.steampowered.com/api/appdetails?appids=" + dlcId.asText() + "&cc=es";
                try {
                    String dlcJson = Jsoup.connect(dlcUrl).ignoreContentType(true).userAgent("Mozilla/5.0").timeout(8000).execute().body();
                    JsonNode dlcRoot = mapper.readTree(dlcJson);
                    JsonNode dlcData = dlcRoot.path(dlcId.asText()).path("data");
                    if (!dlcData.isMissingNode()) {
                        String dlcTitle = dlcData.path("name").asText();
                        JsonNode dlcPrice = dlcData.path("price_overview");
                        ScrapedGame dlc = new ScrapedGame();
                        dlc.setTitle(dlcTitle);
                        dlc.setSource("STEAM");
                        dlc.setGameType(GameType.DLC);
                        if (!dlcPrice.isMissingNode()) {
                            dlc.setPrice(BigDecimal.valueOf(dlcPrice.path("final").asInt() / 100.0));
                        }
                        dlcs.add(dlc);
                    }
                } catch (Exception e) {}
            }
            game.setDlcs(dlcs);
            
            game.setGameType(detectGameType(title));

        } catch (IOException e) {
            return getGameDetailsByScraping(steamId);
        }
        return game;
    }

    private ScrapedGame getGameDetailsByScraping(String steamId) {
        ScrapedGame game = new ScrapedGame();
        game.setSteamId(steamId);
        game.setUrl("https://store.steampowered.com/app/" + steamId);
        
        try {
            Document doc = Jsoup.connect(game.getUrl())
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();
            
            String title = doc.select(".apphub_AppName").text();
            if (title.isEmpty()) title = doc.select("title").text().replace("en Steam", "").trim();
            game.setTitle(title);
            
            String headerImage = doc.select(".game_header_image_full").attr("src");
            if (headerImage.isEmpty()) headerImage = doc.select("img[src*='header.jpg']").attr("src");
            game.setHeaderImageUrl(headerImage);
            game.setCoverArtUrl(headerImage);
            
            String description = doc.select(".game_description_snippet").text();
            if (description.isEmpty()) description = doc.select("#game_area_description").text();
            game.setDescription(description);
            
            Element devElement = doc.select("#developers_list a").first();
            if (devElement != null) game.setDeveloper(devElement.text());
            Element pubElement = doc.select("#developers_list a").last();
            if (pubElement != null && pubElement != devElement) game.setPublisher(pubElement.text());
            game.setReleaseDate(doc.select(".date").text());
            
            Element discountFinal = doc.select(".discount_final_price").first();
            if (discountFinal != null) {
                try { game.setPrice(new BigDecimal(discountFinal.text().replace("€", "").replace(",", ".").trim())); } catch (NumberFormatException e) {}
            }
            if (game.getPrice() == null) {
                Element normalPrice = doc.select(".game_purchase_price").first();
                if (normalPrice != null) {
                    try { game.setPrice(new BigDecimal(normalPrice.text().replace("€", "").replace(",", ".").trim())); } catch (NumberFormatException e) {}
                }
            }
            game.setGameType(detectGameType(title));
            
        } catch (IOException e) {
            game.setTitle("Juego " + steamId);
            game.setDescription("No se pudieron cargar los detalles.");
        }
        return game;
    }
    
    private String extractAppId(String url) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/app/(\\d+)");
        var matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }
    
    private GameType detectGameType(String title) {
        String lowerTitle = title.toLowerCase();
        if (lowerTitle.contains("dlc") || lowerTitle.contains("expansion") || lowerTitle.contains("season pass")) return GameType.DLC;
        if (lowerTitle.contains("edition") || lowerTitle.contains("deluxe") || lowerTitle.contains("collector")) return GameType.EDITION;
        if (lowerTitle.contains("soundtrack")) return GameType.SOUNDTRACK;
        if (lowerTitle.contains("demo")) return GameType.DEMO;
        return GameType.GAME;
    }
}
