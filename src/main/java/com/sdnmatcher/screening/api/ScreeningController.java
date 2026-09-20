package com.sdnmatcher.screening.api;

import com.sdnmatcher.screening.service.ScreeningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/screenings")
public class ScreeningController {
    private final ScreeningService screeningService;

    public ScreeningController(ScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    @PostMapping
    public List<ScreeningResult> screenAll() {
        return screeningService.screenAll();
    }

    @GetMapping("/{accountId}")
    public ScreeningResult screenOne(@PathVariable String accountId) {
        return screeningService.screenOne(accountId);
    }
}
