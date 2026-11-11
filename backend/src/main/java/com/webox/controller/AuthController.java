package com.webox.controller;

import com.webox.common.ApiResponse;
import com.webox.common.RequestContext;
import com.webox.dto.Dtos.AuthView;
import com.webox.dto.Dtos.LoginRequest;
import com.webox.dto.Dtos.RegisterRequest;
import com.webox.dto.Dtos.Session;
import com.webox.dto.Dtos.UserView;
import com.webox.service.AuthService;
import com.webox.service.PreferenceService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PreferenceService preferenceService;

    public AuthController(AuthService authService, PreferenceService preferenceService) {
        this.authService = authService;
        this.preferenceService = preferenceService;
    }

    @PostMapping("/register")
    public ApiResponse<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthView> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletResponse response) {
        Session session = authService.login(request);
        setRefreshCookie(response, session.refreshToken());
        return ApiResponse.ok(new AuthView(session.accessToken(),
                session.expiresInSeconds(), session.user()));
    }

    /** Called on every page load: restores the session after a refresh without localStorage tokens. */
    @PostMapping("/refresh")
    public ApiResponse<AuthView> refresh(
            @CookieValue(name = "webox_refresh", required = false) String refreshToken,
            HttpServletResponse response) {
        Session session = authService.refresh(refreshToken);
        setRefreshCookie(response, session.refreshToken());
        return ApiResponse.ok(new AuthView(session.accessToken(),
                session.expiresInSeconds(), session.user()));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout(
            @CookieValue(name = "webox_refresh", required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        clearRefreshCookie(response);
        return ApiResponse.ok(Map.of("message", "You have been signed out."));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(Map.of(
                "user", authService.currentUserView(),
                "preferences", preferenceService.get(RequestContext.userId())));
    }

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        // httpOnly + SameSite + server-side rotation: the refresh token is never readable by JS.
        // Set Secure=true when terminating TLS in front of the app.
        ResponseCookie cookie = ResponseCookie.from("webox_refresh", rawToken)
                .httpOnly(true)
                .secure(false)
                .path("/api/auth")
                .sameSite("Lax")
                .maxAge(Duration.ofSeconds(authService.refreshTokenMaxAgeSeconds()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("webox_refresh", "")
                .httpOnly(true).secure(false).path("/api/auth").sameSite("Lax")
                .maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
