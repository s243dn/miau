package com.mediatracker.thym.infraestructure.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OffersPageController {

    @GetMapping("/offers")
    public String offers() {
        return "offers";
    }
}
