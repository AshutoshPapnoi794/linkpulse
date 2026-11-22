package com.linkpulse.url.controller;

import com.linkpulse.url.service.UrlRedirectService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final UrlRedirectService redirectService;

    public RedirectController(UrlRedirectService redirectService) {
        this.redirectService = redirectService;
    }

    @GetMapping("/{shortCode:[A-Za-z0-9_-]{3,16}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request){
        String referrer = request.getHeader(HttpHeaders.REFERER);

        URI destination = redirectService.resolveAndRecordClick(shortCode,referrer);
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(destination)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
