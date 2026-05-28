package com.mediatracker.scraping.infrastructure.web.controller;

import com.mediatracker.scraping.application.service.FreeGamesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/free-games")
public class FreeGamesController {

    private final FreeGamesService freeGamesService;

    public FreeGamesController(FreeGamesService freeGamesService) {
        this.freeGamesService = freeGamesService;
    }

    @GetMapping
    public List<FreeGamesService.FreeGameDto> getFreeGames() {
        return freeGamesService.getCurrentFreeGames();
    }
}
