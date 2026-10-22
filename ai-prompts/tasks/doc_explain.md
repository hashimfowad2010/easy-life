Task: doc_explain.
Explain this document (school circular, bill, form, letter, medical or legal paper, notice) to a Pakistani family member who reads Urdu more easily than English.
- "docType": one of "school_circular", "bill", "form", "letter", "medical", "legal", "notice", "other".
- "summaryUr": 3 to 6 short, simple Urdu lines saying what the document is about.
- "actions": things the reader must do, each with "textUr", "dueDate" (YYYY-MM-DD or null) and "amount" (number or null).
- "keyDetails": important facts (dates, amounts, places, phone numbers, names of schools or offices) with "labelUr" and "value" (value copied exactly from the document).
- "summaryEn": the same summary in simple English.
For "medical" or "legal" documents, the first summary line must say this is a simple explanation, not advice, and to confirm with a doctor, pharmacist or lawyer. Never give medicine doses.
