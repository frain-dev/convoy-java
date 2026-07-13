package com.getconvoy.webhook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * signature-vectors.json is generated from the server signing code
 * (convoy/pkg/signature) and vendored here so this library verifies against the
 * same canonical set as every other Convoy SDK. Regenerate upstream with
 * CONVOY_WRITE_VECTORS=1 go test ./pkg/signature -run TestGenerateSignatureVectors
 */
class SharedVectorsTest {

    static final class Vector {
        String name;
        boolean advanced;
        String hash;
        String encoding;
        String secret;
        String payload;
        String header;
        long tolerance;
        boolean valid;

        @Override
        public String toString() {
            return name;
        }
    }

    static List<Vector> vectors() {
        try (InputStream in = SharedVectorsTest.class.getResourceAsStream("/signature-vectors.json")) {
            if (in == null) {
                throw new IllegalStateException("signature-vectors.json not found on test classpath");
            }
            Type listType = new TypeToken<List<Vector>>() {}.getType();
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), listType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void sharedVector(Vector v) throws Exception {
        Webhook webhook = new Webhook(v.secret, v.hash, v.encoding, v.tolerance);

        if (v.valid) {
            assertTrue(webhook.verify(v.payload, v.header), v.name);
        } else {
            // Fail closed: invalid vectors must not verify. Simple mode returns
            // false; advanced mode throws.
            boolean accepted;
            try {
                accepted = webhook.verify(v.payload, v.header);
            } catch (WebhookVerificationException e) {
                accepted = false;
            }
            assertFalse(accepted, v.name);
        }
    }
}
