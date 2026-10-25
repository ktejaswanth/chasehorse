package com.chasehorse.erp.evidence;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Checks that a file's real bytes match the image type the client declared (not just its extension / header). */
public final class FileSignature {
    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");

    private FileSignature() {
    }

    public static boolean matches(String mime, byte[] h) {
        if (mime == null || h == null) {
            return false;
        }
        return switch (mime) {
            case "image/jpeg" -> h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "image/png" -> h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                    && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A;
            case "image/webp" -> h.length >= 12 && ascii(h, 0, 4).equals("RIFF") && ascii(h, 8, 12).equals("WEBP");
            case "image/heic" -> h.length >= 12 && ascii(h, 4, 8).equals("ftyp") && HEIF_BRANDS.contains(ascii(h, 8, 12));
            default -> false;
        };
    }

    private static String ascii(byte[] b, int from, int to) {
        return new String(b, from, to - from, StandardCharsets.ISO_8859_1);
    }
}
