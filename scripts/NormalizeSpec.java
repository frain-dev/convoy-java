import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Keep Java/native generation compatible with the delivery-attempts union.
 *
 * OpenAPI Generator 7.23.0 emits invalid Java for an inline array in oneOf
 * (List<T>.class, getList<T>(), etc.). A named array schema, together with
 * x-generate-alias-as-model, uses its supported concrete collection model.
 * This only changes the temporary generation input, never the wire schema.
 * Jackson/YAML are provided by the checksum-verified generator JAR, so this
 * source-file launcher needs no additional build tool or dependency download.
 */
class NormalizeSpec {
    private static final String ARRAY_MODEL = "DeliveryAttemptList";

    public static void main(String[] args) throws Exception {
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        ObjectNode spec = (ObjectNode) yaml.readTree(Path.of(args[0]).toFile());
        ObjectNode schemas = (ObjectNode) spec.path("components").path("schemas");

        for (JsonNode path : spec.path("paths")) {
            JsonNode operation = path.path("get");
            if (!"GetDeliveryAttempts".equals(operation.path("operationId").asText())) {
                continue;
            }
            JsonNode response = operation.path("responses").path("200")
                    .path("content").path("application/json").path("schema");
            normalizeResponse(response, schemas);
        }

        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(Path.of(args[1]).toFile(), spec);
        prepareTemplates(Path.of(args[2]));
    }

    private static void normalizeResponse(JsonNode response, ObjectNode schemas) {
        JsonNode variants = response.path("properties").path("data").path("oneOf");
        if (variants.isArray()) {
            ArrayNode oneOf = (ArrayNode) variants;
            for (int i = 0; i < oneOf.size(); i++) {
                JsonNode variant = oneOf.get(i);
                if (!"array".equals(variant.path("type").asText())) {
                    continue;
                }
                JsonNode existing = schemas.get(ARRAY_MODEL);
                if (existing != null && !existing.equals(variant)) {
                    throw new IllegalArgumentException("Conflicting schema: " + ARRAY_MODEL);
                }
                // Preserve items, constraints and descriptions verbatim.
                ObjectNode array = variant.deepCopy();
                array.put("x-generate-alias-as-model", true);
                array.put("x-convoy-array-alias", true);
                schemas.set(ARRAY_MODEL, array);
                oneOf.set(i, schemas.objectNode().put("$ref", "#/components/schemas/" + ARRAY_MODEL));
            }
        }
        for (JsonNode component : response.path("allOf")) {
            normalizeResponse(component, schemas);
        }
    }

    private static void prepareTemplates(Path directory) throws Exception {
        // The stock POJO equality/hash template violates the List contract for
        // array aliases. Keep ArrayList's implementations for this alias only.
        // Read the pinned template from the verified JAR instead of vendoring
        // or silently patching generated Java. Fail closed if its layout moves.
        String template;
        try (var source = NormalizeSpec.class.getResourceAsStream("/Java/libraries/native/pojo.mustache")) {
            template = new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
        int start = template.indexOf("  /**\n   * Return true if this {{name}} object is equal to o.");
        int end = template.indexOf("  @Override\n  public String toString()", start);
        if (start < 0 || end < start) {
            throw new IllegalStateException("Generator POJO equality template changed; review the array alias workaround");
        }
        String guard = "vendorExtensions.x-convoy-array-alias";
        template = template.substring(0, start) + "{{^" + guard + "}}\n"
                + template.substring(start, end) + "{{/" + guard + "}}\n" + template.substring(end);
        Path target = directory.resolve("libraries/native/pojo.mustache");
        Files.createDirectories(target.getParent());
        Files.writeString(target, template);
    }
}
