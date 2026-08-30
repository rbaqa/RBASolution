package techrba.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility for parsing and normalising decimal values that may appear in
 * locale specific formats (e.g. Croatian/European decimal comma: "36,29"
 * vs US decimal point: "36.29") extracted from UI text or API responses.
 *
 * <p>Designed to make currency assertions robust against formatting and
 * to guard against common edge cases requested by stakeholders.</p>
 */
public final class DecimalParser {

    private DecimalParser() {
        // static utility
    }

    /**
     * Parses a raw numeric string (comma or dot decimal separator) to BigDecimal.
     * Robustly handles European/Croatian ("1.234,56" or "36,29"), US ("36.29")
     * and plain API decimals ("0.830960").
     */
    public static BigDecimal parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Cannot parse null/empty numeric string");
        }
        String candidate = raw.trim().replaceAll("\\s", "");
        if (candidate.contains(",")) {
            // European format: ',' is the decimal separator, '.' is the group separator
            candidate = candidate.replace(".", "").replace(",", ".");
            return new BigDecimal(candidate).setScale(4, RoundingMode.HALF_UP);
        }
        // Plain/dot format ('1.234' ambiguity ignored in favour of decimal point)
        return new BigDecimal(candidate).setScale(4, RoundingMode.HALF_UP);
    }

    /** Simple helper: builds a value with a given scale. */
    public static BigDecimal amount(int scale, double value) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
