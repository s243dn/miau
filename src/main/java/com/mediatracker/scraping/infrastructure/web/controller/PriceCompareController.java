package com.mediatracker.scraping.infrastructure.web.controller;

import com.mediatracker.scraping.application.service.GameComparisonService;
import com.mediatracker.scraping.domain.model.PriceComparison;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PriceCompareController {

    private final GameComparisonService gameComparisonService;

    public PriceCompareController(GameComparisonService gameComparisonService) {
        this.gameComparisonService = gameComparisonService;
    }

    @GetMapping("/api/compare")
    public PriceComparison compare(@RequestParam String name) {
        System.out.println("🎮 Comparando: " + name);
        return gameComparisonService.getFullComparison(name);
    }
}
