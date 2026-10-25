package io.github.maoz3000.miniwsa.domain;

/**
 * JSON can carry unpaired UTF-16 surrogates ({@code "\ud800"}), which are not valid Unicode text: ClickHouse
 * cannot store them as UTF-8 and clients cannot display them.
 */
public final class Utf16 {

    private Utf16() {
    }

    /**
     * @return true when the text has no unpaired surrogate; {@code null} counts as well-formed
     */
    public static boolean isWellFormed(String text) {
        // paired surrogates decode to supplementary code points; only unpaired ones remain in the surrogate range
        return text == null
                || text.codePoints().noneMatch(cp -> cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE);
    }
}
