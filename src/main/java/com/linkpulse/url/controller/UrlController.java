package com.linkpulse.url.controller;

import com.linkpulse.url.dto.CreateUrlRequest;
import com.linkpulse.url.dto.PageResponse;
import com.linkpulse.url.dto.ShortUrlResponse;
import com.linkpulse.url.dto.UpdateUrlRequest;
import com.linkpulse.url.service.UrlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {
    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping
    public ResponseEntity<ShortUrlResponse> createUrl(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateUrlRequest request){
        Long userId = Long.parseLong(jwt.getSubject());

        ShortUrlResponse response = urlService.createUrl(userId,request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ShortUrlResponse>> getMyUrls(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size){
        Long userId = Long.parseLong(jwt.getSubject());

        return ResponseEntity.ok(
                urlService.getMyUrls(userId,page,size)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShortUrlResponse> getUrl(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id){
        Long userId = Long.parseLong(jwt.getSubject());

        return ResponseEntity.ok(
                urlService.getUrl(userId,id)
        );
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ShortUrlResponse> updateExpiration(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody UpdateUrlRequest request){
        Long userId = Long.parseLong(jwt.getSubject());

        return ResponseEntity.ok(
                urlService.updateExpiration(userId,id,request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateUrl(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id){
        Long userId = Long.parseLong(jwt.getSubject());

        urlService.deactivateUrl(userId,id);

        return ResponseEntity.noContent().build();
    }
}
