package com.mediatracker.scraping.infrastructure.web.controller;

import com.mediatracker.scraping.application.service.AmazonPrimeGamesService;
import com.mediatracker.scraping.application.service.EpicFreeGamesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/offers")
public class OffersController {

    private final EpicFreeGamesService epicFreeGamesService;
    private final AmazonPrimeGamesService amazonPrimeGamesService;

    public OffersController(EpicFreeGamesService epicFreeGamesService, AmazonPrimeGamesService amazonPrimeGamesService) {
        this.epicFreeGamesService = epicFreeGamesService;
        this.amazonPrimeGamesService = amazonPrimeGamesService;
    }

    @GetMapping
    public Map<String, Object> getOffers() {
        Map<String, Object> response = new HashMap<>();
        response.put("epicGames", epicFreeGamesService.getCurrentFreeGames());
        response.put("amazonPrime", amazonPrimeGamesService.getCurrentPrimeGames());
        return response;
    }
}
