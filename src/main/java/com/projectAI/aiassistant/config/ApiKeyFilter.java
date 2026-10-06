package com.projectAI.aiassistant.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final String key;

    public ApiKeyFilter(@Value("${assistant.api-key:}") String key) {
        this.key = key;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean protectedPath = path.startsWith("/api/") || path.equals("/mcp") || path.startsWith("/mcp/");
        String provided = request.getHeader("X-API-Key");
        if (protectedPath && !key.isBlank() && (provided == null ||
            !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)))) {
            response.setStatus(401);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
