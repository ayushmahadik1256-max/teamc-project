package com.apnileap.teamc.config;

import com.apnileap.teamc.entity.UserRole;
import com.apnileap.teamc.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityRoleFilter extends OncePerRequestFilter {
    public static final String ROLE_HEADER = "X-User-Role";
    public static final String AUTH_HEADER = "Authorization";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // Allow CORS preflight requests without authorization check
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        String roleStr = extractRole(request);

        // C4 Audit API requires AUDITOR or IT_ADMIN role if role header is provided or enforced
        if (uri.startsWith("/api/v1/audit")) {
            if (roleStr != null && !roleStr.isBlank()) {
                if (!UserRole.AUDITOR.name().equalsIgnoreCase(roleStr) && !UserRole.IT_ADMIN.name().equalsIgnoreCase(roleStr)) {
                    throw new UnauthorizedException("Access to audit logs requires AUDITOR or IT_ADMIN role. Current role: " + roleStr);
                }
            }
        }

        // File metadata update (PATCH) requires IT_ADMIN role if role is provided
        if (request.getMethod().equalsIgnoreCase("PATCH") && (uri.contains("/files/"))) {
            if (roleStr != null && !roleStr.isBlank()) {
                if (!UserRole.IT_ADMIN.name().equalsIgnoreCase(roleStr)) {
                    throw new UnauthorizedException("File retention/location changes require IT_ADMIN role. Current role: " + roleStr);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractRole(HttpServletRequest request) {
        String roleHeader = request.getHeader(ROLE_HEADER);
        if (roleHeader != null && !roleHeader.isBlank()) {
            return roleHeader.trim();
        }
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }
}
