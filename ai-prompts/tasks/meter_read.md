Task: meter_read.
The photo shows an electricity meter. Read the cumulative kWh display only (the main counter), not the serial number, year, voltage, current rating or "imp/kWh" constant.
- "reading": the number shown, as an integer (ignore a red/decimal last drum if it is clearly a decimal).
- "digitsSeen": exactly the digits you can see, left to right, using "?" for any digit you cannot read.
- If a "previousReading" is given in the input, the new reading should normally be equal or higher; if it is lower, still report what you see and add "lower_than_previous" to "issues".
- Possible issues: "blurry", "glare", "partly_hidden", "multiple_displays", "lower_than_previous", "not_a_meter".
- If you are not sure about any digit, confidence must be below 0.8.
