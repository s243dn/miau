package com.mediatracker.scraping.infrastructure.web.controller;

import com.mediatracker.scraping.application.service.UnifiedPriceComparisonService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
public class UnifiedComparisonController {
    
    private final UnifiedPriceComparisonService comparisonService;
    
    public UnifiedComparisonController(UnifiedPriceComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }
    
    @GetMapping("/compare")
    public UnifiedPriceComparisonService.UnifiedComparison compareGame(@RequestParam String name) {
        System.out.println("📊 Comparativa unificada para: " + name);
        return comparisonService.getFullComparison(name);
    }
}
