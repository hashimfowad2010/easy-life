/**
 * Server-side copy of the phone's Masker.forCloud (spec rule 4), as a second safety net: the phone already masks
 * before sending, and the function masks again before anything reaches Claude. Keep in sync with
 * app/src/main/java/com/easylife/app/core/ai/Masker.kt.
 */
export const MASK = "[MASKED]";

const ID_PATTERNS: RegExp[] = [
  // IBAN, with or without spaces: PK36 SCBL 0000 0011 2345 6702
  /\bPK\s?\d{2}\s?[A-Z]{4}(?:\s?\d{4}){4}\b/gi,
  // CNIC 35202-1234567-1
  /\b\d{5}-\d{7}-\d\b/g,
  // Card numbers in groups: 4111 1111 1111 1111 / 4111-1111-1111-1111
  /\b\d{4}[ -]\d{4}[ -]\d{4}[ -]\d{1,7}\b/g,
  // LESCO-style reference numbers: 11 11111 1111111 (optionally followed by a letter)
  /\b\d{2}\s\d{5}\s\d{7}(?:\s?[A-Z])?\b/g,
  // Pakistani mobile numbers: +92 300 1234567, 0300-1234567, 03001234567
  /(?<!\d)(?:\+92|0092|0)\s?3\d{2}[\s-]?\d{7}\b/g,
  // Landlines with area code: 042-35761234
  /\b0\d{2,3}-\d{7,8}\b/g,
  // Any contiguous run of 11+ digits (accounts, cards, references, CNIC without dashes)
  /\d{11,}/g,
  // Email addresses
  /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g,
];

export interface MaskResult {
  text: string;
  maskedCount: number;
}

export function maskForCloud(text: string): MaskResult {
  let out = text;
  let maskedCount = 0;
  for (const pattern of ID_PATTERNS) {
    out = out.replace(pattern, () => {
      maskedCount++;
      return MASK;
    });
  }
  return { text: out, maskedCount };
}
