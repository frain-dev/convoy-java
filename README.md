# Convoy Java SDK

Official Convoy SDK for Java.

> Status: this initial release provides **webhook signature verification**. The
> generated API client will follow once the OpenAPI spec covers the full API
> surface.

## Install

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("com.getconvoy:convoy:0.1.0")
}
```

Maven:

```xml
<dependency>
  <groupId>com.getconvoy</groupId>
  <artifactId>convoy</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Verify webhook signatures

Verify with the raw request body, before parsing it. `verify` returns `true`
for a valid signature; simple mode returns `false` on a mismatch and advanced
mode throws `WebhookVerificationException`. It fails closed either way.

```java
import com.getconvoy.webhook.Webhook;
import com.getconvoy.webhook.WebhookVerificationException;

Webhook webhook = new Webhook("endpoint-secret");

try {
    if (!webhook.verify(rawBody, request.getHeader("X-Convoy-Signature"))) {
        // reject the request
    }
    // process the event
} catch (WebhookVerificationException e) {
    // reject the request
}
```

Constructor options mirror the other Convoy SDKs:

```java
// secret, hash (SHA256|SHA512), encoding (hex|base64), tolerance (seconds)
new Webhook("endpoint-secret", "SHA512", "base64", 300);
```

## Development

```bash
./gradlew test
```

Tests verify against `signature-vectors.json`, a shared cross-SDK vector set
generated from the server signing code so every Convoy SDK verifies identically.

## License

[MIT](LICENSE)
