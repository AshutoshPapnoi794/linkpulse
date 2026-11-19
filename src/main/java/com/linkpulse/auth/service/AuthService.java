package com.linkpulse.auth.service;

import com.linkpulse.auth.dto.AuthResponse;
import com.linkpulse.auth.dto.LoginRequest;
import com.linkpulse.auth.dto.RegisterRequest;
import com.linkpulse.auth.dto.UserSummary;
import com.linkpulse.auth.entity.Role;
import com.linkpulse.auth.entity.User;
import com.linkpulse.auth.exception.EmailAlreadyRegisteredException;
import com.linkpulse.auth.exception.PasswordTooLongException;
import com.linkpulse.auth.repository.UserRepository;
import com.linkpulse.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES =72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request){
        String email = normalizeEmail(request.email());
        validatePasswordLength(request.password());

        if(userRepository.existsByEmail(email)){
            throw new EmailAlreadyRegisteredException();
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User(
                email,
                encodedPassword,
                Role.ROLE_USER
        );

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }

        return createAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {

        String email = normalizeEmail(request.email());

        validatePasswordLength(request.password());

        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        email,
                        request.password()
                )
        );

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user was not found"
                        )
                );

        return createAuthResponse(user);
    }

    private AuthResponse createAuthResponse(User user){
        String accessToken = jwtService.generateAccessToken(user);

        return new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                new UserSummary(
                        user.getId(),
                        user.getEmail(),
                        user.getRole().toString()
                )
        );
    }

    private String normalizeEmail(String email){
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePasswordLength(String password){
        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;

        if (byteLength>BCRYPT_MAX_BYTES){
            throw new PasswordTooLongException();
        }
    }
}
