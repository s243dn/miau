package com.mediatracker.scraping.infrastructure.web.controller;

import com.mediatracker.scraping.infrastructure.scraper.SteamDetailsScraper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/steam")
public class SteamDetailsController {
    
    private final SteamDetailsScraper steamDetailsScraper;
    
    public SteamDetailsController(SteamDetailsScraper steamDetailsScraper) {
        this.steamDetailsScraper = steamDetailsScraper;
    }
    
    @GetMapping("/details/{steamId}")
    public SteamDetailsScraper.SteamGameFullDetails getGameDetails(@PathVariable String steamId) {
        System.out.println("🔍 Obteniendo detalles completos de Steam ID: " + steamId);
        return steamDetailsScraper.getFullGameDetails(steamId);
    }
}
