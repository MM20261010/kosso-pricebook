package org.privateprice.pricebook;

import java.text.Normalizer;
import java.util.Locale;

/** Pure calculations: no Android dependencies or network access. */
public final class PriceMath {
    private PriceMath() { }

    public static String key(String name) {
        if (name == null) return "";
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\u3000・･_―‐-]+", "")
                .trim();
    }

    public static double excludingTax(double includingTax, double percent) {
        if (!Double.isFinite(includingTax) || includingTax < 0 ||
                !Double.isFinite(percent) || percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Invalid price or tax rate");
        }
        return includingTax / (1 + percent / 100.0);
    }

    public static double percentChange(double previous, double current) {
        if (!Double.isFinite(previous) || !Double.isFinite(current) || previous <= 0) {
            throw new IllegalArgumentException("Invalid comparison");
        }
        return 100 * (current - previous) / previous;
    }

    public static double unitNet(double gross, double taxRate, double packSize) {
        if (!Double.isFinite(packSize) || packSize <= 0) throw new IllegalArgumentException("Invalid pack size");
        return excludingTax(gross, taxRate) * 100 / packSize;
    }
}
