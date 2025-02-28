package com.kaiburr.taskmanager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        logger.info("🔍 Incoming request: " + request.getMethod() + " " + requestURI);

        // ✅ Skip JWT authentication for public routes
        if (requestURI.startsWith("/auth/")) {
            logger.info("🚀 Skipping JWT authentication for: " + requestURI);
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("🚨 No Bearer token found, skipping authentication for: " + requestURI);
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        logger.info("📛 Extracted Token: " + token);

        try {
            String username = jwtUtil.extractUsername(token);
            logger.info("📛 Extracted Username from Token: " + username);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                logger.info("🔍 Checking user in UserDetailsService: " + username);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                logger.info("✅ User found in UserDetailsService: " + userDetails.getUsername());

                if (jwtUtil.validateToken(token, userDetails)) {
                    logger.info("✅ Token is valid, setting authentication for user: " + username);
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    logger.error("❌ Invalid or expired token for user: " + username);
                    sendUnauthorizedResponse(response, "Invalid or expired token");
                    return;
                }
            }
        } catch (Exception e) {
            logger.error("🚨 Authentication failed: " + e.getMessage());
            sendUnauthorizedResponse(response, "Authentication failed: " + e.getMessage());
            return;
        }

        chain.doFilter(request, response);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
