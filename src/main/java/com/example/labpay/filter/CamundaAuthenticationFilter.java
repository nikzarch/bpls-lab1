package com.example.labpay.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.IdentityService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CamundaAuthenticationFilter extends OncePerRequestFilter {

    private final IdentityService identityService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            List<String> groups = new ArrayList<>();
            for (var authority : auth.getAuthorities()) {
                String role = authority.getAuthority();
                groups.add(role.startsWith("ROLE_") ? role.substring(5) : role);
            }
            identityService.setAuthentication(auth.getName(), groups);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            identityService.clearAuthentication();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/camunda")
                || path.startsWith("/engine-rest")
                || path.startsWith("/api/auth");
    }
}