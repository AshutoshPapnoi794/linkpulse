package com.linkpulse.url.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Service
public class OriginalUrlValidator {
    private static final int MAX_URL_LENGTH = 2048;

    public String validateAndNormalize(String originalUrl){
        if (originalUrl==null || originalUrl.isBlank()){
            throw new IllegalArgumentException(
                    "Original URL cannot be empty"
            );
        }

        String value = originalUrl.trim();

        if (originalUrl.length()>MAX_URL_LENGTH){
            throw new IllegalArgumentException(
                    "Original URL cannot exceed 2048 characters"
            );
        }

        try {
            URI uri = new URI(value);

            String scheme = uri.getScheme();

            if (scheme==null){
                throw new IllegalArgumentException(
                        "URL must include a protocol"
                );
            }

            scheme = scheme.toLowerCase(Locale.ROOT);

            if (!"http".equals(scheme) && !"https".equals(scheme)){
                throw new IllegalArgumentException(
                        "Only HTTP and HTTPS URLs are supported"
                );
            }

            if (uri.getHost()==null || uri.getHost().isBlank()){
                throw new IllegalArgumentException(
                        "URL must contain a valid hostname"
                );
            }

            if (uri.getUserInfo() !=null){
                throw new IllegalArgumentException(
                        "URLs containing embedded credentials are not allowed"
                );
            }

            if (uri.getPort()>64435){
                throw new IllegalArgumentException(
                        "URL contains an invalid port"
                );
            }

            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "Original URL is not a valid URI",
                    exception
            );
        }
    }
}
