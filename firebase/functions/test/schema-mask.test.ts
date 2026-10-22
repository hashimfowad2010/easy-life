import { describe, expect, it } from "vitest";
import { PROMPTS } from "../src/config";
import { MASK, maskForCloud } from "../src/mask";
import { hasRequiredFields, toStrictSchema } from "../src/schema";

function walk(node: unknown, visit: (n: Record<string, unknown>) => void): void {
  if (Array.isArray(node)) return node.forEach((n) => walk(n, visit));
  if (node === null || typeof node !== "object") return;
  visit(node as Record<string, unknown>);
  for (const [k, v] of Object.entries(node)) {
    if (k === "properties") Object.values(v as object).forEach((p) => walk(p, visit));
    else walk(v, visit);
  }
}

describe("toStrictSchema", () => {
  it("closes every object and turns nullable types into anyOf, for every bundled task", () => {
    for (const [name, task] of Object.entries(PROMPTS.tasks)) {
      const strict = toStrictSchema(task.schema);
      walk(strict, (n) => {
        if (n.type === "object") expect(n.additionalProperties, `${name}: open object`).toBe(false);
        expect(Array.isArray(n.type), `${name}: type array left`).toBe(false);
        for (const bad of ["minimum", "maximum", "minLength", "maxLength", "minItems"]) expect(n[bad]).toBeUndefined();
      });
    }
  });

  it("keeps property names that look like keywords", () => {
    const strict = toStrictSchema({
      type: "object",
      properties: { type: { type: ["string", "null"], maxLength: 5 }, minimum: { type: "number", minimum: 0 } },
      required: ["type"],
    }) as Record<string, any>;
    expect(Object.keys(strict.properties)).toEqual(["type", "minimum"]);
    expect(strict.properties.type).toEqual({ anyOf: [{ type: "string" }, { type: "null" }] });
    expect(strict.properties.minimum).toEqual({ type: "number" });
    expect(strict.additionalProperties).toBe(false);
  });

  it("checks required top-level fields", () => {
    const schema = { type: "object", required: ["confidence"] };
    expect(hasRequiredFields(schema, { confidence: 0.9 })).toBe(true);
    expect(hasRequiredFields(schema, { other: 1 })).toBe(false);
    expect(hasRequiredFields(schema, [1])).toBe(false);
    expect(hasRequiredFields(undefined, "anything")).toBe(true);
  });
});

describe("maskForCloud (same rules as the phone)", () => {
  it("masks CNIC, IBAN, cards, phones, references, long numbers and email", () => {
    const text = [
      "CNIC 35202-1234567-1",
      "IBAN PK36 SCBL 0000 0011 2345 6702",
      "Card 4111 1111 1111 1111",
      "Call 0300-1234567 or +92 321 7654321",
      "REF NO 11 12345 1234567 U",
      "A/C 00112233445566",
      "mail ali@example.com",
    ].join("\n");
    const r = maskForCloud(text);
    expect(r.text).not.toMatch(/\d{5}-\d{7}-\d|PK36|4111|0300|7654321|1234567|00112233445566|ali@/);
    expect(r.maskedCount).toBeGreaterThanOrEqual(8);
  });

  it("keeps amounts, dates and units so the AI can still work", () => {
    const text = "PKR 1,250.00 debited on 22-10-2026. Units 186. Due 24 OCT 26. Rs 6407";
    const r = maskForCloud(text);
    expect(r.text).toBe(text);
    expect(r.maskedCount).toBe(0);
    expect(MASK).toBe("[MASKED]");
  });
});
