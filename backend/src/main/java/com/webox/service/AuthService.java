package com.webox.service;

import com.webox.common.ApiException;
import com.webox.common.AppClock;
import com.webox.common.ErrorCode;
import com.webox.common.JsonLog;
import com.webox.common.TextUtil;
import com.webox.config.AppProperties;
import com.webox.dto.Dtos.LoginRequest;
import com.webox.dto.Dtos.RegisterRequest;
import com.webox.dto.Dtos.Session;
import com.webox.dto.Dtos.UserView;
import com.webox.entity.PreferenceEntity;
import com.webox.entity.RefreshTokenEntity;
import com.webox.entity.UserEntity;
import com.webox.repository.PreferenceRepository;
import com.webox.repository.RefreshTokenRepository;
import com.webox.repository.UserRepository;
import com.webox.security.JwtUtil;
import com.webox.security.PasswordHasher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern STRONG_PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshRepository;
    private final PreferenceRepository preferenceRepository;
    private final PasswordHasher passwordHasher;
    private final JwtUtil jwt;
    private final AppProperties props;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshRepository,
                       PreferenceRepository preferenceRepository, PasswordHasher passwordHasher,
                       JwtUtil jwt, AppProperties props) {
        this.userRepository = userRepository;
        this.refreshRepository = refreshRepository;
        this.preferenceRepository = preferenceRepository;
        this.passwordHasher = passwordHasher;
        this.jwt = jwt;
        this.props = props;
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        String email = TextUtil.clean(request.email(), 200).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Please enter a valid email address.");
        }
        String password = request.password();
        if (password == null || !STRONG_PASSWORD.matcher(password).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Password must be at least 8 characters and contain both letters and numbers.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL,
                    "This email is already registered. Please sign in instead.");
        }
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordHasher.hash(password));
        user.setFullName(TextUtil.clean(request.fullName(), 100));
        user.setRole(com.webox.common.Constants.ROLE_EMPLOYEE);
        user.setCreatedAt(AppClock.now());
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL,
                    "This email is already registered. Please sign in instead.");
        }
        PreferenceEntity preference = new PreferenceEntity();
        preference.setUserId(user.getId());
        preference.setUpdatedAt(AppClock.now());
        preferenceRepository.save(preference);
        JsonLog.info("user_registered", "userId", user.getId()); // email intentionally omitted
        return toView(user);
    }

    @Transactional
    public Session login(LoginRequest request) {
        String email = TextUtil.clean(request.email(), 200).toLowerCase(Locale.ROOT);
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED,
                        "Incorrect email or password."));
        if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Incorrect email or password.");
        }
        refreshRepository.purgeExpired(AppClock.now());
        JsonLog.info("user_login", "userId", user.getId(), "role", user.getRole());
        return newSession(user);
    }

    /** Rotation: the presented refresh token is revoked and a fresh one is issued. */
    @Transactional
    public Session refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Your session has expired. Please sign in again.");
        }
        RefreshTokenEntity stored = refreshRepository.findByTokenHash(sha256(rawRefreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED,
                        "Your session has expired. Please sign in again."));
        if (stored.isRevoked() || !stored.getExpiresAt().isAfter(AppClock.now())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Your session has expired. Please sign in again.");
        }
        stored.setRevoked(true);
        refreshRepository.save(stored);
        UserEntity user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED,
                        "Your account is no longer available."));
        return newSession(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshRepository.findByTokenHash(sha256(rawRefreshToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshRepository.save(token);
        });
    }

    public UserView currentUserView() {
        var current = com.webox.common.RequestContext.user();
        return new UserView(current.id(), current.email(), current.fullName(), current.role());
    }

    public long refreshTokenMaxAgeSeconds() {
        return props.getRefreshTokenDays() * 24L * 3600L;
    }

    private Session newSession(UserEntity user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUserId(user.getId());
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(AppClock.now().plusDays(props.getRefreshTokenDays()));
        token.setRevoked(false);
        token.setCreatedAt(AppClock.now());
        refreshRepository.save(token);
        String accessToken = jwt.issue(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
        return new Session(accessToken, raw, props.getAccessTokenMinutes() * 60L, toView(user));
    }

    private static UserView toView(UserEntity user) {
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Token hashing failed", e);
        }
    }
}
