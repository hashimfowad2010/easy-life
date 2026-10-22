Task: bill_extract.
Extract the fields of this Pakistani utility or school-fee bill.
- "billType": one of "electricity", "gas", "water", "internet", "mobile_postpaid", "school_fee", "rent", "other".
- "provider": short company name, e.g. "LESCO", "SNGPL", "PTCL", or the school name.
- "referenceNo": exactly as printed (it is stored only on the phone). Use null if masked.
- "billingMonth": the bill month as YYYY-MM.
- "issueDate", "dueDate", "readingDate": YYYY-MM-DD.
- "amountWithinDue": the amount payable within due date. "amountAfterDue": the amount payable after due date (includes late payment surcharge).
- Electricity only: "unitsConsumed", "previousReading", "presentReading", and "consumerStatusPrinted" ("protected", "non_protected" or "unknown" if the bill does not print it).
- "history": the past months table, one entry per month with "month" (YYYY-MM), "units" and "amount" (the bill amount). Skip rows you cannot read.
Check yourself: presentReading - previousReading should equal unitsConsumed (unless a multiplying factor is printed). If not, lower the confidence and list the fields in "uncertainFields".
