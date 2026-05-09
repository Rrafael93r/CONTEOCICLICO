package com.pharmaser.conteociclico.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    @Value("${api.key}")
    private String apiKey;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/actuator/health".equals(path) || "/error".equals(path);
    }

    @Override
    protected void doFilterInternal(@org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            @org.springframework.lang.NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Permitir OPTIONS (CORS preflight) sin validación
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Permitir endpoints públicos (Solo los que no requieren ni token ni API Key - actualmente ninguno)
        // Eliminada excepción de login para forzar uso de API Key incluso al autenticarse

        // Si la petición trae un JWT Bearer token, dejarlo pasar al JwtRequestFilter
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Sin JWT, verificar la API Key
        String requestApiKey = request.getHeader("x-api-key");

        if (apiKey != null && apiKey.equals(requestApiKey)) {
            // ROLE_API permite pasar @PreAuthorize en endpoints de importación masiva
            Authentication auth = new UsernamePasswordAuthenticationToken(
                "API_USER", null,
                List.of(new SimpleGrantedAuthority("ROLE_API"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or missing API Key");
        }
    }
}
