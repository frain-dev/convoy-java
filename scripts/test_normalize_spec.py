"""Offline fixture checks. Pass the checksum-verified generator JAR as argv[1]."""

import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


JAR = str(Path(sys.argv.pop(1)).resolve())
ROOT = Path(__file__).resolve().parent.parent


class NormalizeSpecTest(unittest.TestCase):
    def setUp(self):
        self.array = {
            "type": "array",
            "items": {"$ref": "#/components/schemas/Attempt"},
            "minItems": 0,
            "maxItems": 10,
            "description": "Latest delivery attempts",
        }
        self.page = {
            "type": "object",
            "required": ["content", "next_cursor"],
            "properties": {"content": copy.deepcopy(self.array), "next_cursor": {"type": "string"}},
        }
        self.spec = {
            "openapi": "3.0.3",
            "paths": {
                "/attempts": {
                    "get": {
                        "operationId": "GetDeliveryAttempts",
                        "responses": {"200": {"content": {"application/json": {"schema": {
                            "allOf": [{"type": "object", "properties": {
                                "data": {"oneOf": [copy.deepcopy(self.array), copy.deepcopy(self.page)]}
                            }}]
                        }}}}},
                    }
                }
            },
            "components": {"schemas": {
                "Attempt": {"type": "object", "properties": {"uid": {"type": "string"}}},
                "UnrelatedArray": {"type": "array", "items": {"type": "string"}},
            }},
        }

    def normalize(self, spec):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            source, output = folder / "input.json", folder / "output.json"
            source.write_text(json.dumps(spec))
            subprocess.run(
                ["java", "-cp", JAR, "scripts/NormalizeSpec.java", str(source), str(output), str(folder / "templates")],
                cwd=ROOT, check=True, capture_output=True, text=True,
            )
            template = (folder / "templates/libraries/native/pojo.mustache").read_text()
            self.assertIn("{{^vendorExtensions.x-convoy-array-alias}}", template)
            return json.loads(output.read_text())

    def test_only_target_array_changes_and_constraints_survive(self):
        expected = copy.deepcopy(self.spec)
        schema = expected["paths"]["/attempts"]["get"]["responses"]["200"]["content"]["application/json"]["schema"]
        schema["allOf"][0]["properties"]["data"]["oneOf"][0] = {"$ref": "#/components/schemas/DeliveryAttemptList"}
        expected["components"]["schemas"]["DeliveryAttemptList"] = {
            **self.array, "x-generate-alias-as-model": True, "x-convoy-array-alias": True,
        }
        self.assertEqual(expected, self.normalize(self.spec))

    def test_normalization_is_idempotent(self):
        normalized = self.normalize(self.spec)
        self.assertEqual(normalized, self.normalize(normalized))

    def test_other_operations_are_unchanged(self):
        self.spec["paths"]["/attempts"]["get"]["operationId"] = "OtherOperation"
        self.assertEqual(self.spec, self.normalize(self.spec))

    def test_conflicting_schema_name_fails_clearly(self):
        self.spec["components"]["schemas"]["DeliveryAttemptList"] = {"type": "string"}
        with self.assertRaises(subprocess.CalledProcessError) as failure:
            self.normalize(self.spec)
        self.assertIn("Conflicting schema: DeliveryAttemptList", failure.exception.stderr)


if __name__ == "__main__":
    unittest.main()
