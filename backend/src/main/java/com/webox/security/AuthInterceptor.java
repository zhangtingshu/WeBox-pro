package com.webox.security;

import com.webox.common.ApiException;
import com.webox.common.CurrentUser;
import com.webox.common.ErrorCode;
import com.webox.common.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/** Bearer token verification + role check for every /api/** endpoint except public auth/meta. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwt;

    public AuthInterceptor(JwtUtil jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        // Public: auth endpoints, static enums, and uploaded/public dish images.
        // /api/auth/me is intentionally NOT public.
        if (path.startsWith("/api/uploads/") || path.equals("/api/meta")
                || path.equals("/api/auth/register") || path.equals("/api/auth/login")
                || path.equals("/api/auth/refresh") || path.equals("/api/auth/logout")) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Please sign in to continue.");
        }
        JwtUtil.Claims claims = jwt.verify(header.substring(7));
        RequestContext.setUser(new CurrentUser(
                claims.userId(), claims.email(), claims.name(), claims.role()));

        if (handler instanceof HandlerMethod method) {
            RequireRole annotation = method.getMethodAnnotation(RequireRole.class);
            if (annotation == null) {
                annotation = method.getBeanType().getAnnotation(RequireRole.class);
            }
            if (annotation != null && Arrays.stream(annotation.value())
                    .noneMatch(role -> role.equals(claims.role()))) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        "This area is only available to administrators.");
            }
        }
        return true;
    }
}
