package com.getconvoy.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies Convoy webhook signatures.
 *
 * <p>Simple mode signs {@code HMAC(secret, payload)}; advanced mode signs
 * {@code HMAC(secret, "{timestamp},{payload}")} and ships a header of the form
 * {@code t=<unix>,v1=<sig>[,v1=<sig>...]}. This mirrors the server signing code
 * in {@code pkg/signature} and the other Convoy SDKs.
 */
public final class Webhook {
    public static final long DEFAULT_TOLERANCE_SECONDS = 300L;
    public static final String DEFAULT_ENCODING = "hex";
    public static final String DEFAULT_HASH = "SHA256";

    private final String secret;
    private final String hash;
    private final String encoding;
    private final long toleranceSeconds;

    public Webhook(String secret) {
        this(secret, DEFAULT_HASH, DEFAULT_ENCODING, DEFAULT_TOLERANCE_SECONDS);
    }

    public Webhook(String secret, String hash, String encoding, long toleranceSeconds) {
        this.secret = secret;
        this.hash = hash;
        this.encoding = encoding;
        this.toleranceSeconds = toleranceSeconds;
    }

    /**
     * Returns {@code true} when the signature header is valid for the payload.
     * Simple mode returns {@code false} on a mismatch; advanced mode throws.
     * Either way verification fails closed: it never returns {@code true} for a
     * forged, expired, or malformed signature.
     */
    public boolean verify(String payload, String header) throws WebhookVerificationException {
        if (header == null || header.isEmpty()) {
            throw new WebhookVerificationException("missing signature header");
        }

        boolean advanced = header.split(",", -1).length > 1;
        return advanced ? verifyAdvanced(payload, header) : verifySimple(payload, header);
    }

    private boolean verifySimple(String payload, String header) throws WebhookVerificationException {
        return secureEquals(computeSignature(payload), header);
    }

    private boolean verifyAdvanced(String payload, String header) throws WebhookVerificationException {
        long timestamp = -1L;
        boolean haveTimestamp = false;
        List<String> signatures = new ArrayList<>();

        for (String item : header.split(",", -1)) {
            String[] parts = item.split("=", 2);
            String key = parts[0].trim();

            if (key.equals("t")) {
                if (parts.length != 2) {
                    throw new WebhookVerificationException("invalid header: malformed timestamp");
                }
                try {
                    timestamp = Long.parseLong(parts[1].trim());
                    haveTimestamp = true;
                } catch (NumberFormatException e) {
                    throw new WebhookVerificationException("invalid header: malformed timestamp");
                }
            } else if (key.startsWith("v")) {
                // Only keys starting with "v" (v1, v2...) carry a signature, so a
                // valid signature cannot be smuggled under an unrelated key.
                if (parts.length == 2) {
                    signatures.add(parts[1]);
                }
            }
        }

        if (!haveTimestamp) {
            throw new WebhookVerificationException("invalid header: missing timestamp");
        }

        if (toleranceSeconds > 0) {
            long now = System.currentTimeMillis() / 1000L;
            if (now - timestamp > toleranceSeconds) {
                throw new WebhookVerificationException("timestamp expired");
            }
        }

        String expected = computeSignature(timestamp + "," + payload);
        for (String signature : signatures) {
            if (secureEquals(expected, signature)) {
                return true;
            }
        }

        throw new WebhookVerificationException("no matching signature");
    }

    private String computeSignature(String payload) throws WebhookVerificationException {
        byte[] mac = hmac(payload);
        switch (encoding) {
            case "hex":
                return toHex(mac);
            case "base64":
                return Base64.getEncoder().encodeToString(mac);
            default:
                throw new WebhookVerificationException("invalid encoding: " + encoding);
        }
    }

    private byte[] hmac(String payload) throws WebhookVerificationException {
        String algorithm = macAlgorithm(hash);
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new WebhookVerificationException("failed to compute signature: " + e.getMessage());
        }
    }

    private static String macAlgorithm(String hash) throws WebhookVerificationException {
        // Convoy only signs with SHA256 or SHA512; reject anything else.
        switch (hash.toUpperCase(Locale.ROOT)) {
            case "SHA256":
                return "HmacSHA256";
            case "SHA512":
                return "HmacSHA512";
            default:
                throw new WebhookVerificationException("unsupported hash algorithm: " + hash);
        }
    }

    private static boolean secureEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
