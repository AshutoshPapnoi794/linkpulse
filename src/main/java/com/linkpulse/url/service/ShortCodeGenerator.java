package com.linkpulse.url.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ShortCodeGenerator {
    private static final String BASE62 =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generateCode(){
        StringBuilder sb = new StringBuilder(CODE_LENGTH);

        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = random.nextInt(62);
            sb.append(BASE62.charAt(index));
        }

        return sb.toString();
    }
}
