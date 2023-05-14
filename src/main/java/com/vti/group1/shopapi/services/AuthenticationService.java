package com.vti.group1.shopapi.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.vti.group1.shopapi.auth.AuthenticationRequest;
import com.vti.group1.shopapi.auth.AuthenticationResponse;
import com.vti.group1.shopapi.auth.LogoutResponse;
import com.vti.group1.shopapi.auth.RegisterRequest;
import com.vti.group1.shopapi.entity.Role;
import com.vti.group1.shopapi.entity.User;
import com.vti.group1.shopapi.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class.getName());

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BlacklistTokenService blacklistTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthenticationResponse register(RegisterRequest request) {

        final User user = createUserFromRequestData(request);

        if (userRepository.existsByEmail(request.getEmail()))
            return createResponseForExistedEmailRequest(request);

        return createResponseForRegisterRequest(user);
    }

    private AuthenticationResponse createResponseForRegisterRequest(User user) {
        var newUser = userRepository.save(user);
        var jwtToken = jwtService.generateToken(user);

        return AuthenticationResponse.builder()
                .message("User registered successfully")
                .uuid(newUser.getUuid())
                .token(jwtToken).build();
    }

    private AuthenticationResponse createResponseForExistedEmailRequest(RegisterRequest request) {
        String info = String.format("%s is already registered", request.getEmail());
        logger.info(info);
        return AuthenticationResponse.builder().message("Email is already registered").build();
    }

    private User createUserFromRequestData(RegisterRequest request) {
        String info = String.format("Creating user from request data for %s", request.getEmail());
        logger.info(info);

        return User.builder().firstName(request.getFirstName())
                .lastName(request.getLastName()).email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()));
        } catch (Exception e) {
            return responseIncorrectDataMessage(request);
        }

        var user = userRepository.findByEmail(request.getEmail());
        if (user.isEmpty())
            return responseMessageForNotRegisteredEmail(request);

        return responseSuccessMessageWithToken(user.get());
    }

    private AuthenticationResponse responseIncorrectDataMessage(AuthenticationRequest request) {
        String info = String.format("%s is not authenticated", request.getEmail());
        logger.info(info);

        return AuthenticationResponse.builder().message("Email or password is incorrect").build();
    }

    private AuthenticationResponse responseMessageForNotRegisteredEmail(AuthenticationRequest request) {
        String info = String.format("%s is not registered", request.getEmail());
        logger.info(info);

        return AuthenticationResponse.builder().message("Email is not registered").build();
    }

    private AuthenticationResponse responseSuccessMessageWithToken(User user) {
        var jwtToken = jwtService.generateToken(user);

        String info = String.format("%s authenticated successfully", user.getEmail());
        logger.info(info);

        return AuthenticationResponse.builder()
                .message("User authenticated successfully")
                .token(jwtToken)
                .build();
    }

    public LogoutResponse logout(HttpServletRequest request) {

        String token = getTokenFromRequest(request);

        if (token == null) {
            logger.warn("Non-token request detected");
            return LogoutResponse.builder().message("User is not logged in").build();
        }

        if (blacklistTokenService.isTokenInBlacklist(token)) {
            logger.warn("Token is already blacklisted");
            return LogoutResponse.builder().message("User is not logged in").build();
        }

        logger.info("User logging out");

        blacklistTokenService.addTokenToBlacklist(token);

        logger.info("User logged out successfully");
        return LogoutResponse.builder().message("User logged out successfully").build();
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (bearerToken == null) {
            return null;
        }

        if (StringUtils.hasText("bearerToken")
                && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}
