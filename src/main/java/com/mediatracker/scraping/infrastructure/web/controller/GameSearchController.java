package com.mediatracker.scraping.infrastructure.web.controller;

import com.mediatracker.scraping.application.service.GameComparisonService;
import com.mediatracker.scraping.domain.model.PriceComparison;
import com.mediatracker.scraping.infrastructure.scraper.RawgApiScraper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.List;

@Controller
public class GameSearchController {
    
    private final GameComparisonService gameComparisonService;
    private final RawgApiScraper rawgScraper;
    
    public GameSearchController(GameComparisonService gameComparisonService, RawgApiScraper rawgScraper) {
        this.gameComparisonService = gameComparisonService;
        this.rawgScraper = rawgScraper;
    }
    
    @GetMapping("/games")
    public String gamesPage(Model model) {
        model.addAttribute("currentPage", "games");
        return "games/list";
    }
    
    @GetMapping("/games/compare")
    public String compareGame(@RequestParam String name, Model model) {
        model.addAttribute("currentPage", "games");
        PriceComparison comparison = gameComparisonService.getFullComparison(name);
        model.addAttribute("comparison", comparison);
        return "games/detail";
    }
    
    @GetMapping("/api/search")
    @ResponseBody
    public List<RawgApiScraper.RawgGame> searchGames(@RequestParam String q) {
        System.out.println("🔍 Búsqueda RAWg: " + q);
        return rawgScraper.searchGamesFlexible(q, 20);
    }
}
