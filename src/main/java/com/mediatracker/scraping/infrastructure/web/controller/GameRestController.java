package com.mediatracker.scraping.infrastructure.web.controller;

import com.mediatracker.scraping.application.service.GameComparisonService;
import com.mediatracker.scraping.infrastructure.scraper.RawgApiScraper;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameRestController {

    private final GameComparisonService gameComparisonService;
    private final RawgApiScraper rawgScraper;

    public GameRestController(GameComparisonService gameComparisonService, RawgApiScraper rawgScraper) {
        this.gameComparisonService = gameComparisonService;
        this.rawgScraper = rawgScraper;
    }

    @GetMapping("/search")
    public List<RawgApiScraper.RawgGame> searchGames(@RequestParam String q) {
        System.out.println("🔍 API search recibido: " + q);
        RawgApiScraper.RawgGame game = rawgScraper.searchExactGame(q);
        return game != null ? List.of(game) : List.of();
    }
}
