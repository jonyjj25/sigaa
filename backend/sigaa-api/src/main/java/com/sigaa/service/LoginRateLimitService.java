package com.sigaa.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Límite progresivo en memoria para frenar fuerza bruta antes de consultar las
 * credenciales. La identidad se almacena como hash para no conservar correos en
 * memoria. El bloqueo de cuenta persistente sigue siendo responsabilidad de la
 * política de seguridad existente.
 */
@Service
public class LoginRateLimitService {
    private static final long MAX_BLOCK_SECONDS = 15 * 60L;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();
    private final Clock clock;
    private final long windowSeconds;
    private final int identityAttempts;
    private final int ipAttempts;
    private final long baseBlockSeconds;

    @Autowired
    public LoginRateLimitService(
            @Value("${app.auth.rate-limit.window-seconds:900}") long windowSeconds,
            @Value("${app.auth.rate-limit.identity-attempts:5}") int identityAttempts,
            @Value("${app.auth.rate-limit.ip-attempts:30}") int ipAttempts,
            @Value("${app.auth.rate-limit.base-block-seconds:30}") long baseBlockSeconds) {
        this(windowSeconds, identityAttempts, ipAttempts, baseBlockSeconds, Clock.systemUTC());
    }

    LoginRateLimitService(long windowSeconds, int identityAttempts, int ipAttempts,
                          long baseBlockSeconds, Clock clock) {
        this.windowSeconds = Math.max(60, windowSeconds);
        this.identityAttempts = Math.max(2, identityAttempts);
        this.ipAttempts = Math.max(this.identityAttempts, ipAttempts);
        this.baseBlockSeconds = Math.max(1, baseBlockSeconds);
        this.clock = clock;
    }

    public Decision checkLogin(String ipAddress, String identity) {
        return check("login", ipAddress, identity);
    }

    public void recordLoginFailure(String ipAddress, String identity) {
        recordFailure("login", ipAddress, identity);
    }

    public void recordLoginSuccess(String ipAddress, String identity) {
        clearIdentity("login", identity);
    }

    public Decision checkSecondFactor(String ipAddress, String identity) {
        return check("2fa", ipAddress, identity);
    }

    public void recordSecondFactorFailure(String ipAddress, String identity) {
        recordFailure("2fa", ipAddress, identity);
    }

    public void recordSecondFactorSuccess(String identity) {
        clearIdentity("2fa", identity);
    }

    private Decision check(String scope, String ipAddress, String identity) {
        Instant now = clock.instant();
        long identityRetry = retryAfter(identityKey(scope, identity), now);
        long ipRetry = retryAfter(ipKey(scope, ipAddress), now);
        cleanupOccasionally(now);
        long retryAfter = Math.max(identityRetry, ipRetry);
        return new Decision(retryAfter == 0, retryAfter);
    }

    private void recordFailure(String scope, String ipAddress, String identity) {
        Instant now = clock.instant();
        register(identityKey(scope, identity), identityAttempts, now);
        register(ipKey(scope, ipAddress), ipAttempts, now);
        cleanupOccasionally(now);
    }

    private void register(String key, int threshold, Instant now) {
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket());
        synchronized (bucket) {
            prune(bucket, now);
            bucket.attempts.addLast(now);
            int excess = bucket.attempts.size() - threshold;
            if (excess >= 0) {
                int exponent = Math.min(excess, 8);
                long blockSeconds = Math.min(MAX_BLOCK_SECONDS, baseBlockSeconds * (1L << exponent));
                Instant candidate = now.plusSeconds(blockSeconds);
                if (bucket.blockedUntil == null || candidate.isAfter(bucket.blockedUntil)) {
                    bucket.blockedUntil = candidate;
                }
            }
        }
    }

    private long retryAfter(String key, Instant now) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) return 0;
        synchronized (bucket) {
            prune(bucket, now);
            if (bucket.blockedUntil == null || !bucket.blockedUntil.isAfter(now)) return 0;
            return Math.max(1, bucket.blockedUntil.getEpochSecond() - now.getEpochSecond());
        }
    }

    private void clearIdentity(String scope, String identity) {
        buckets.remove(identityKey(scope, identity));
    }

    private void prune(Bucket bucket, Instant now) {
        Instant cutoff = now.minusSeconds(windowSeconds);
        while (!bucket.attempts.isEmpty() && bucket.attempts.peekFirst().isBefore(cutoff)) {
            bucket.attempts.removeFirst();
        }
        if (bucket.blockedUntil != null && !bucket.blockedUntil.isAfter(now)) {
            bucket.blockedUntil = null;
        }
    }

    private void cleanupOccasionally(Instant now) {
        if ((operations.incrementAndGet() & 255) != 0) return;
        buckets.entrySet().removeIf(entry -> {
            Bucket bucket = entry.getValue();
            synchronized (bucket) {
                prune(bucket, now);
                return bucket.attempts.isEmpty() && bucket.blockedUntil == null;
            }
        });
    }

    private String identityKey(String scope, String identity) {
        String normalized = identity == null ? "" : identity.trim().toLowerCase(Locale.ROOT);
        return scope + ":identity:" + sha256(normalized);
    }

    private String ipKey(String scope, String ipAddress) {
        String normalized = ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress.trim();
        return scope + ":ip:" + normalized;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    private static final class Bucket {
        private final ArrayDeque<Instant> attempts = new ArrayDeque<>();
        private Instant blockedUntil;
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {}
}
