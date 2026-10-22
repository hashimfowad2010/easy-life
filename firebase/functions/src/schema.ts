/**
 * Turns an /ai-prompts schema into one that Claude's structured outputs accept:
 * - every object gets `additionalProperties: false` (required by the API),
 * - `type: ["string", "null"]` becomes `anyOf: [{type: "string"}, {type: "null"}]`,
 * - numeric/string/array size limits the API does not support are dropped (the phone validates the answer again).
 */
export type JsonSchema = { [key: string]: unknown };

const UNSUPPORTED = new Set([
  "$schema",
  "minimum",
  "maximum",
  "exclusiveMinimum",
  "exclusiveMaximum",
  "multipleOf",
  "minLength",
  "maxLength",
  "minItems",
  "maxItems",
  "uniqueItems",
]);

function closeObject(node: JsonSchema): JsonSchema {
  if (node.type === "object") node.additionalProperties = false;
  return node;
}

export function toStrictSchema(node: unknown): unknown {
  if (Array.isArray(node)) return node.map(toStrictSchema);
  if (node === null || typeof node !== "object") return node;

  const out: JsonSchema = {};
  for (const [key, value] of Object.entries(node as JsonSchema)) {
    if (UNSUPPORTED.has(key)) continue;
    if (key === "properties" && value && typeof value === "object") {
      // Property names are data, not keywords: convert each property's schema, keep the names.
      out[key] = Object.fromEntries(Object.entries(value as JsonSchema).map(([name, s]) => [name, toStrictSchema(s)]));
    } else {
      out[key] = toStrictSchema(value);
    }
  }

  if (Array.isArray(out.type)) {
    const { type, ...rest } = out;
    const branches = (type as string[]).map((t) => (t === "null" ? { type: "null" } : closeObject({ ...rest, type: t })));
    return branches.length === 1 ? branches[0] : { anyOf: branches };
  }
  return closeObject(out);
}

/** Minimal check that a parsed answer has the schema's required top-level fields (the phone does the full check). */
export function hasRequiredFields(schema: JsonSchema | undefined, value: unknown): boolean {
  if (!schema) return true;
  if (value === null || typeof value !== "object" || Array.isArray(value)) return false;
  const required = Array.isArray(schema.required) ? (schema.required as string[]) : [];
  return required.every((field) => field in (value as object));
}
