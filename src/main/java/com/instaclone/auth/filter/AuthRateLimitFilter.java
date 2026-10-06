package com.instaclone.auth.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.instaclone.auth.controller.AuthController;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles the auth endpoints most attractive to abuse: credential stuffing against /login, bulk
 * account creation against /register, and email-bombing/token-guessing against the forgot/reset-
 * password pair. /refresh and /logout are deliberately excluded — refresh already requires
 * possession of the rotating httpOnly cookie and is called automatically on every page load (see
 * the frontend's ensureFreshSession single-flight helper), so throttling it risks breaking normal
 * multi-tab usage for no real security benefit.
 *
 * Two independent buckets gate /login and /forgot-password: one keyed by source IP (stops one
 * attacker hammering many accounts) and one keyed by the identity named in the request body itself
 * — username/email, read directly off the JSON — so an attacker who rotates source IPs while
 * always targeting the same victim can't evade the limit just by changing address, which a
 * per-IP-only bucket can't see at all. /register and /reset-password stay IP-only: the former's
 * threat is bulk creation rather than hammering one existing account, and the latter's body carries
 * an opaque high-entropy token rather than a human-readable identity worth extracting.
 *
 * An OPTIONS request is skipped entirely before any of this — the browser's CORS preflight for a
 * cross-origin POST with Content-Type: application/json never reaches AuthController, so letting it
 * consume a token here would silently halve the real limit for the app's own frontend.
 *
 * Buckets live in a bounded, access-expiring Caffeine cache, not a plain map — Bucket4j's own docs
 * call this out as required for local/in-memory usage, since nothing else would ever free the
 * memory for a (path, key) pair hit once and never again on these unauthenticated endpoints.
 * Still local to this JVM — correct for this app's single-instance deployment, but wouldn't share
 * state across horizontally-scaled instances without moving to Bucket4j's Redis-backed proxy
 * manager; not applied here since nothing in this app's deployment runs more than one instance.
 *
 * The four path constants and the two identity-bearing field names are this file's only knowledge
 * of AuthController's shape, and the paths are AuthController's own constants, not re-typed
 * literals, so a renamed route fails this file to compile instead of silently losing protection.
 *
 * Disabled via AuthRateLimitFilter.ENABLED_PROPERTY=false in the integration tests that register
 * many accounts from the same host in one run — they'd otherwise trip the /register limit
 * immediately.
 */
@Component
@ConditionalOnProperty(name = AuthRateLimitFilter.ENABLED_PROPERTY, matchIfMissing = true)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    public static final String ENABLED_PROPERTY = "app.rate-limit.auth.enabled";

    private record Limit(int capacity, Duration period) {}

    private static final String LOGIN_PATH = AuthController.BASE_PATH + AuthController.LOGIN_PATH;
    private static final String REGISTER_PATH = AuthController.BASE_PATH + AuthController.REGISTER_PATH;
    private static final String FORGOT_PASSWORD_PATH =
            AuthController.BASE_PATH + AuthController.FORGOT_PASSWORD_PATH;
    private static final String RESET_PASSWORD_PATH = AuthController.BASE_PATH + AuthController.RESET_PASSWORD_PATH;

    private static final Map<String, Limit> LIMITS = Map.of(
            LOGIN_PATH, new Limit(5, Duration.ofMinutes(1)),
            REGISTER_PATH, new Limit(5, Duration.ofHours(1)),
            FORGOT_PASSWORD_PATH, new Limit(3, Duration.ofHours(1)),
            RESET_PASSWORD_PATH, new Limit(10, Duration.ofMinutes(1)));

    private static final Map<String, String> IDENTITY_FIELDS =
            Map.of(LOGIN_PATH, "usernameOrEmail", FORGOT_PASSWORD_PATH, "email");

    // Strips a semicolon matrix-parameter suffix (e.g. ";jsessionid=x") from each path segment —
    // Spring MVC's own routing does the same before matching @PostMapping, so without this a
    // request to /auth/login;x=1 could reach the controller while missing this filter's exact
    // path lookup entirely.
    private static final Pattern MATRIX_PARAMS = Pattern.compile(";[^/]*");

    private final Cache<String, Bucket> buckets =
            Caffeine.newBuilder().maximumSize(100_000).expireAfterAccess(Duration.ofHours(1)).build();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = MATRIX_PARAMS.matcher(request.getServletPath()).replaceAll("");
        Limit limit = LIMITS.get(path);
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ConsumptionProbe ipProbe = bucketFor(path + ":ip:" + clientIp(request), limit).tryConsumeAndReturnRemaining(1);
        if (!ipProbe.isConsumed()) {
            reject(response, ipProbe);
            return;
        }

        HttpServletRequest effectiveRequest = request;
        String identityField = IDENTITY_FIELDS.get(path);
        if (identityField != null) {
            byte[] body = request.getInputStream().readAllBytes();
            effectiveRequest = new ReplayableBodyRequest(request, body);
            String identity = extractJsonField(body, identityField);
            if (StringUtils.hasText(identity)) {
                ConsumptionProbe identityProbe = bucketFor(
                                path + ":id:" + identity.trim().toLowerCase(Locale.ROOT), limit)
                        .tryConsumeAndReturnRemaining(1);
                if (!identityProbe.isConsumed()) {
                    reject(response, identityProbe);
                    return;
                }
            }
        }

        filterChain.doFilter(effectiveRequest, response);
    }

    private Bucket bucketFor(String key, Limit limit) {
        return buckets.get(key, k -> newBucket(limit));
    }

    private Bucket newBucket(Limit limit) {
        Bandwidth bandwidth = Bandwidth.classic(limit.capacity(), Refill.greedy(limit.capacity(), limit.period()));
        return Bucket.builder().addLimit(bandwidth).build();
    }

    // Trusts X-Forwarded-For's first (left-most, original-client) entry over the raw TCP peer —
    // request.getRemoteAddr() alone would be the reverse proxy's own address for every client the
    // moment this app sits behind one (this app's own deployment plan is Cloudflare), collapsing
    // every real user into one shared bucket. Only safe when the app is actually unreachable except
    // through that trusted proxy; this app doesn't yet enforce that boundary itself.
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String extractJsonField(byte[] body, String fieldName) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(new String(body, StandardCharsets.UTF_8));
        return matcher.find() ? matcher.group(1) : null;
    }

    private void reject(HttpServletResponse response, ConsumptionProbe probe) throws IOException {
        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Hand-written rather than routed through GlobalExceptionHandler/an injected ObjectMapper —
        // this filter runs ahead of DispatcherServlet (and, in some bootstrap orderings, ahead of
        // JacksonAutoConfiguration), so it can't rely on either being available. Matches the shape
        // ProblemDetail.forStatusAndDetail(TOO_MANY_REQUESTS, ...) itself serializes to.
        response.getWriter()
                .write(
                        """
                        {"type":"about:blank","title":"Too Many Requests","status":429,\
                        "detail":"Too many requests. Please try again later."}""");
    }

    /** Replays a body this filter already consumed off the real stream, so AuthController can still bind it. */
    private static final class ReplayableBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        ReplayableBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {}

                @Override
                public int read() {
                    return source.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
