package com.mediatracker.scraping.infrastructure.web.controller;

import com.mediatracker.scraping.application.service.UnifiedPriceComparisonService;
import com.mediatracker.scraping.infrastructure.scraper.RawgApiScraper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.List;

@Controller
public class GameSearchController {
    
    // 1. Cambiamos al servicio UNIFICADO que tiene Eneba y la API
    private final UnifiedPriceComparisonService unifiedComparisonService;
    private final RawgApiScraper rawgScraper;
    
    public GameSearchController(UnifiedPriceComparisonService unifiedComparisonService, RawgApiScraper rawgScraper) {
        this.unifiedComparisonService = unifiedComparisonService;
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
        // 2. Pedimos el objeto unificado (con Steam, GOG, Eneba, CheapShark)
        UnifiedPriceComparisonService.UnifiedComparison comparison = unifiedComparisonService.getFullComparison(name);
        model.addAttribute("comparison", comparison);
        
        // 3. OJO: El controlador manda a dibujar a "detail.html"
        return "games/detail"; 
    }
    
    @GetMapping("/api/search")
    @ResponseBody
    public List<RawgApiScraper.RawgGame> searchGames(@RequestParam String q) {
        System.out.println("🔍 Búsqueda RAWg: " + q);
        return rawgScraper.searchGamesFlexible(q, 20);
    }
}