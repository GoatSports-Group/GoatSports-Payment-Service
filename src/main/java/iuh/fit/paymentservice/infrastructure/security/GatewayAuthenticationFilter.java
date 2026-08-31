package iuh.fit.paymentservice.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private final byte[] gatewaySecret;

    public GatewayAuthenticationFilter(@Value("${app.gateway.secret}") String gatewaySecret) {
        this.gatewaySecret = gatewaySecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedSecret = request.getHeader("X-Gateway-Secret");
        if (!isValidSecret(suppliedSecret)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"statusCode\":403,\"message\":\"Payment API must be called through API Gateway\"}");
            return;
        }

        String principal = firstNonBlank(
                request.getHeader("X-User-Email"),
                request.getHeader("X-User-Username"),
                "gateway"
        );
        String authoritiesHeader = request.getHeader("X-User-Authorities");
        List<SimpleGrantedAuthority> authorities = authoritiesHeader == null
                ? List.of()
                : Arrays.stream(authoritiesHeader.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(SimpleGrantedAuthority::new)
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(request.getHeader("X-User-Id"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isValidSecret(String suppliedSecret) {
        return suppliedSecret != null
                && MessageDigest.isEqual(
                gatewaySecret,
                suppliedSecret.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("gateway");
    }
}
