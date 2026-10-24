package com.vendoryx.util;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {

    private PasswordUtil() {}

    public static String hash(String plain) {
        if (plain == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        return BCrypt.hashpw(plain, BCrypt.gensalt());
    }

    /**
     * Verifies plain password against stored value.
     * Legacy: if stored does not start with "$2", treat as plain text.
     */
    public static boolean verify(String plain, String stored) {
        if (plain == null || stored == null || stored.isBlank()) {
            return false;
        }
        if (isBcrypt(stored)) {
            return BCrypt.checkpw(plain, stored);
        }
        return stored.equals(plain);
    }

    public static boolean needsRehash(String stored) {
        return stored != null && !stored.isBlank() && !isBcrypt(stored);
    }

    private static boolean isBcrypt(String stored) {
        return stored.startsWith("$2");
    }
}
