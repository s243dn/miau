package com.mediatracker.scraping.infrastructure.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.mediatracker.scraping.application.service.UnifiedPriceComparisonService;

@Controller 
public class PriceCompareController {

    private final UnifiedPriceComparisonService unifiedPriceComparisonService;

    public PriceCompareController(UnifiedPriceComparisonService unifiedPriceComparisonService) {
        this.unifiedPriceComparisonService = unifiedPriceComparisonService;
    }

    // BORRASTE EL MÉTODO 'comparePage' Y YA NO HAY COLISIÓN

    // ESTE ES EL ÚNICO MÉTODO QUE DEBE QUEDAR AQUÍ:
    @GetMapping("/api/compare-fragment")
    public String getPriceFragment(@RequestParam String gameName, Model model) {
        var comparison = unifiedPriceComparisonService.getFullComparison(gameName);
        model.addAttribute("comparison", comparison);
        
        // Esto le dice a Thymeleaf que cargue el trozo de HTML
        return "fragments/price_cards :: price_list";
    }
}