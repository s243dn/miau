package com.mediatracker.scraping.infrastructure.web.controller;

import com.mediatracker.scraping.domain.model.ScrapedGame;
import com.mediatracker.scraping.infrastructure.scraper.SteamScraper;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/scraping")
public class ScrapingController {

    private final SteamScraper steamScraper;

    public ScrapingController(SteamScraper steamScraper) {
        this.steamScraper = steamScraper;
    }

    @GetMapping("/search")
    public ScrapedGame searchGames(@RequestParam(required = false) String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return null;
        }
        return steamScraper.searchByExactName(searchTerm);
    }
    
    @GetMapping("/game/{steamId}")
    public ScrapedGame getGameDetails(@PathVariable String steamId) {
        return steamScraper.getGameDetails(steamId);
    }
}
