package com.instaclone.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;

/**
 * Pure unit test against the filter directly — no Spring context needed, so it stays fast and
 * doesn't need the app.rate-limit.auth.enabled=false escape hatch the real integration tests use.
 */
class AuthRateLimitFilterTest {

    private final AuthRateLimitFilter filter = new AuthRateLimitFilter();

    @Test
    void allowsUpToCapacityThenRejectsWithRetryAfter() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 5; i++) {
            filter.doFilter(loginRequest("203.0.113.5", "user" + i + "@example.com"), mock(HttpServletResponse.class), chain);
        }
        verify(chain, times(5)).doFilter(any(), any());

        HttpServletResponse sixthResponse = mock(HttpServletResponse.class);
        StringWriter body = stubWriter(sixthResponse);
        filter.doFilter(loginRequest("203.0.113.5", "yetAnother@example.com"), sixthResponse, chain);

        verify(chain, times(5)).doFilter(any(), any());
        verify(sixthResponse).setStatus(429);
        verify(sixthResponse).setHeader(org.mockito.ArgumentMatchers.eq("Retry-After"), any());
        assertThat(body.toString()).contains("\"status\":429");
    }

    @Test
    void aDifferentSourceIpGetsItsOwnIpBudget() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 5; i++) {
            filter.doFilter(loginRequest("203.0.113.9", "same@example.com"), mock(HttpServletResponse.class), chain);
        }
        // A different IP targeting a different identity should not be affected by the first IP's budget.
        HttpServletResponse response = mock(HttpServletResponse.class);
        filter.doFilter(loginRequest("203.0.113.10", "different@example.com"), response, chain);

        verify(chain, times(6)).doFilter(any(), any());
    }

    @Test
    void rotatingSourceIpsStillShareOneBudgetPerTargetIdentity() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 5; i++) {
            filter.doFilter(loginRequest("198.51.100." + i, "victim@example.com"), mock(HttpServletResponse.class), chain);
        }
        verify(chain, times(5)).doFilter(any(), any());

        // A 6th distinct source IP, still targeting the SAME victim identity, must still be blocked —
        // this is exactly the gap a pure per-IP bucket can't close.
        HttpServletResponse response = mock(HttpServletResponse.class);
        stubWriter(response);
        filter.doFilter(loginRequest("198.51.100.99", "victim@example.com"), response, chain);

        verify(chain, times(5)).doFilter(any(), any());
        verify(response).setStatus(429);
    }

    @Test
    void unrelatedPathsAreNeverThrottled() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/posts/1");
        for (int i = 0; i < 20; i++) {
            filter.doFilter(request, mock(HttpServletResponse.class), chain);
        }
        verify(chain, times(20)).doFilter(any(), any());
    }

    @Test
    void corsPreflightNeverConsumesABudget() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest preflight = mock(HttpServletRequest.class);
        when(preflight.getMethod()).thenReturn("OPTIONS");
        when(preflight.getServletPath()).thenReturn("/auth/login");
        for (int i = 0; i < 10; i++) {
            filter.doFilter(preflight, mock(HttpServletResponse.class), chain);
        }
        verify(chain, times(10)).doFilter(any(), any());

        // The real POSTs should still have their full budget — none of those OPTIONS calls cost anything.
        for (int i = 0; i < 5; i++) {
            filter.doFilter(loginRequest("203.0.113.20", "user" + i + "@example.com"), mock(HttpServletResponse.class), chain);
        }
        verify(chain, times(15)).doFilter(any(), any());
    }

    @Test
    void matrixParameterSuffixIsStrippedBeforeMatching() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 5; i++) {
            filter.doFilter(loginRequest("203.0.113.30", "user" + i + "@example.com", "/auth/login;jsessionid=x"), mock(HttpServletResponse.class), chain);
        }
        HttpServletResponse sixthResponse = mock(HttpServletResponse.class);
        stubWriter(sixthResponse);
        filter.doFilter(
                loginRequest("203.0.113.30", "someoneElse@example.com", "/auth/login;jsessionid=x"),
                sixthResponse,
                chain);

        verify(chain, times(5)).doFilter(any(), any());
        verify(sixthResponse).setStatus(429);
    }

    private HttpServletRequest loginRequest(String remoteAddr, String usernameOrEmail) throws Exception {
        return loginRequest(remoteAddr, usernameOrEmail, "/auth/login");
    }

    private HttpServletRequest loginRequest(String remoteAddr, String usernameOrEmail, String servletPath) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn(servletPath);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        String body = "{\"usernameOrEmail\":\"" + usernameOrEmail + "\",\"password\":\"x\"}";
        ServletInputStream inputStream =
                new DelegatingServletInputStream(new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(request.getInputStream()).thenReturn(inputStream);
        return request;
    }

    private StringWriter stubWriter(HttpServletResponse response) throws Exception {
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
        return stringWriter;
    }
}
