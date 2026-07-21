# Convoy Java SDK

Official Convoy SDK for Java: **webhook signature verification** (hand-written)
and an **API client** generated from Convoy's OpenAPI spec via
[OpenAPI Generator](https://openapi-generator.tech/) (java, `native` library —
JDK `java.net.http`, Jackson).

## Install

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("io.github.frain-dev:convoy:0.2.0")
}
```

Maven:

```xml
<dependency>
  <groupId>io.github.frain-dev</groupId>
  <artifactId>convoy</artifactId>
  <version>0.2.0</version>
</dependency>
```

## Use the API client

```java
import com.getconvoy.api.EventsApi;
import com.getconvoy.client.ApiClient;
import com.getconvoy.models.ModelsCreateEvent;

import java.util.Map;

ApiClient client = new ApiClient();
// Default base URI is https://us.getconvoy.cloud/api; point elsewhere for
// EU cloud or self-hosted instances.
client.updateBaseUri("https://us.getconvoy.cloud/api");
client.setRequestInterceptor(b -> b.header("Authorization", "Bearer " + apiKey));

EventsApi events = new EventsApi(client);
events.createEndpointEvent("project-id", new ModelsCreateEvent()
        .endpointId("endpoint-id")
        .eventType("invoice.paid")
        .data(Map.of("amount", 100, "currency", "USD")));
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

### Regenerating the API client

The client (`com.getconvoy.api`, `com.getconvoy.client`, `com.getconvoy.models`)
is generated; do not edit it by hand. The hand-written verify package
(`com.getconvoy.webhook`) is never touched by generation.

CI on `frain-dev/convoy` dispatches `sdk_generation.yaml` when OpenAPI
artifacts change. Locally:

```bash
./scripts/generate.sh   # requires java 17+, curl, rsync
./gradlew test
```

## License

[MIT](LICENSE)
