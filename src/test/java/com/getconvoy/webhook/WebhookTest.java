package com.getconvoy.webhook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookTest {

    private static String hmacHex(String algorithm, String secret, String data) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
        byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(out.length * 2);
        for (byte b : out) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    void verifiesSimpleSignature() throws Exception {
        String secret = "endpoint-secret";
        String payload = "{\"event\":\"charge.success\"}";
        String signature = hmacHex("HmacSHA256", secret, payload);

        assertTrue(new Webhook(secret).verify(payload, signature));
    }

    @Test
    void rejectsTamperedSimpleSignature() throws Exception {
        assertFalse(new Webhook("endpoint-secret").verify("{\"event\":\"charge.success\"}", "deadbeef"));
    }

    @Test
    void missingHeaderThrows() {
        Webhook webhook = new Webhook("endpoint-secret");
        assertThrows(WebhookVerificationException.class, () -> webhook.verify("{}", ""));
    }

    @Test
    void unsupportedHashThrows() {
        Webhook webhook = new Webhook("endpoint-secret", "MD5", "hex", 300L);
        assertThrows(WebhookVerificationException.class, () -> webhook.verify("{}", "abc"));
    }

    @Test
    void advancedSignatureKeyMustStartWithV() throws Exception {
        String secret = "convoy-webhook-secret";
        String payload = "{\"m\":1}";
        long timestamp = System.currentTimeMillis() / 1000L;
        String signature = hmacHex("HmacSHA256", secret, timestamp + "," + payload);
        Webhook webhook = new Webhook(secret);

        // Control: the same signature under a v-prefixed key verifies.
        assertTrue(webhook.verify(payload, "t=" + timestamp + ",v1=" + signature));

        // A non-v key (which still contains "v") must not be treated as a signature.
        assertThrows(
                WebhookVerificationException.class,
                () -> webhook.verify(payload, "t=" + timestamp + ",nav=" + signature));
    }
}
