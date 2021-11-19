package net.netconomy.tools.restflow.impl


import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class UtfCharsets {

    public static final Charset DEFAULT = StandardCharsets.UTF_8
    public static final Set<Charset> UTF_CHARSETS =
            [StandardCharsets.UTF_8, StandardCharsets.UTF_16,
             StandardCharsets.UTF_16BE, StandardCharsets.UTF_16LE].asImmutable()

    static final boolean isUtf(Charset charset) {
        UTF_CHARSETS.contains(charset)
    }

    static final Charset forceUtf(Charset charset) {
        isUtf(charset) ? charset : DEFAULT
    }
}
