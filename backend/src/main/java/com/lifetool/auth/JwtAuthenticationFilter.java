package com.lifetool.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        DispatcherType dispatcherType = request.getDispatcherType();
        if (dispatcherType == DispatcherType.ASYNC || dispatcherType == DispatcherType.ERROR) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        String queryToken = request.getParameter("access_token");
        log.info("Auth filter request path={}, hasAuthHeader={}, authPrefix={}", request.getRequestURI(), header != null, header == null ? "" : header.substring(0, Math.min(20, header.length())));
        String token = null;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else if (queryToken != null && !queryToken.isBlank()) {
            token = queryToken.trim();
        }
        if (token != null && !token.isBlank()) {
            boolean valid = jwtProvider.validate(token);
            String tokenType = null;
            if (valid) {
                tokenType = jwtProvider.getTokenType(token);
            }
            log.info("Auth filter parsed path={}, valid={}, tokenType={}", request.getRequestURI(), valid, tokenType);
            if (valid && "access".equals(tokenType)) {
                String userId = jwtProvider.getUserId(token);
                log.info("Auth filter authenticated userId={} path={}", userId, request.getRequestURI());
                var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
