package fr.maxlego08.zauctionhouse.api.filter;

import java.text.Normalizer;

/**
 * Normalizes searchable text so styled small-caps item names remain discoverable.
 */
public final class SearchTextNormalizer {

    private SearchTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder builder = new StringBuilder(normalized.length());

        normalized.codePoints().forEach(codePoint -> {
            int mappedCodePoint = mapSmallCaps(codePoint);
            builder.appendCodePoint(Character.toLowerCase(mappedCodePoint));
        });

        return builder.toString();
    }

    private static int mapSmallCaps(int codePoint) {
        return switch (codePoint) {
            case 0x1D00 -> 'a';
            case 0x0299 -> 'b';
            case 0x1D04 -> 'c';
            case 0x1D05 -> 'd';
            case 0x1D07 -> 'e';
            case 0xA730 -> 'f';
            case 0x0262 -> 'g';
            case 0x029C -> 'h';
            case 0x026A -> 'i';
            case 0x1D0A -> 'j';
            case 0x1D0B -> 'k';
            case 0x029F -> 'l';
            case 0x1D0D -> 'm';
            case 0x0274 -> 'n';
            case 0x1D0F -> 'o';
            case 0x1D18 -> 'p';
            case 0xA7AF -> 'q';
            case 0x0280 -> 'r';
            case 0xA731 -> 's';
            case 0x1D1B -> 't';
            case 0x1D1C -> 'u';
            case 0x1D20 -> 'v';
            case 0x1D21 -> 'w';
            case 0x028F -> 'y';
            case 0x1D22 -> 'z';
            default -> codePoint;
        };
    }
}
