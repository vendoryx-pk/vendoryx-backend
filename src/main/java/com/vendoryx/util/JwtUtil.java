package com.vendoryx.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.vendoryx.config.DotEnv;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class JwtUtil {

    private static final Algorithm ALGORITHM;
    private static final long EXPIRY_HOURS = 12;

    static {
        DotEnv.load();
        String secret = DotEnv.get("VENDORYX_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new ExceptionInInitializerError(
                    "Missing required environment variable: VENDORYX_JWT_SECRET "
                            + "(must be at least 32 characters).");
        }
        secret = secret.trim();
        if (secret.length() < 32) {
            throw new ExceptionInInitializerError(
                    "VENDORYX_JWT_SECRET must be at least 32 characters (got " + secret.length() + ").");
        }
        ALGORITHM = Algorithm.HMAC256(secret);
    }

    private JwtUtil() {}

    public static String createToken(int userId, String role, List<Integer> shopIds) {
        List<Integer> ids = shopIds != null ? shopIds : Collections.emptyList();
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("role", role != null ? role : "")
                .withClaim("shopIds", ids)
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.now().plus(EXPIRY_HOURS, ChronoUnit.HOURS)))
                .sign(ALGORITHM);
    }

    public static DecodedJWT verify(String token) throws JWTVerificationException {
        return JWT.require(ALGORITHM).build().verify(token);
    }

    public static int getUserId(DecodedJWT jwt) {
        return jwt.getClaim("userId").asInt();
    }

    public static String getRole(DecodedJWT jwt) {
        return jwt.getClaim("role").asString();
    }

    public static List<Integer> getShopIds(DecodedJWT jwt) {
        List<Integer> ids = jwt.getClaim("shopIds").asList(Integer.class);
        return ids != null ? new ArrayList<>(ids) : new ArrayList<>();
    }
}
