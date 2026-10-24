package com.vito.media.infrastructure.adapter.outbound;

import java.nio.charset.StandardCharsets;

/**
 * Lee solo la etiqueta Orientation (0x0112) del IFD0 del EXIF de un JPEG, recorriendo los
 * marcadores sin parsear el resto de metadatos. Ante cualquier dato inesperado devuelve 1
 * (sin girar): la orientación es cosmética, nunca motivo de rechazo.
 */
final class ExifOrientation {

    static final int NORMAL = 1;

    private static final int TAG_ORIENTATION = 0x0112;
    private static final int TYPE_SHORT = 3;
    private static final byte[] EXIF_HEADER = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);

    private ExifOrientation() {
    }

    static int read(byte[] jpeg) {
        try {
            return readOrNormal(jpeg);
        } catch (RuntimeException e) {
            return NORMAL;
        }
    }

    private static int readOrNormal(byte[] d) {
        if (d.length < 4 || u8(d, 0) != 0xFF || u8(d, 1) != 0xD8) {
            return NORMAL;
        }
        int i = 2;
        while (i + 4 <= d.length) {
            if (u8(d, i) != 0xFF) {
                return NORMAL;
            }
            int marker = u8(d, i + 1);
            if (marker == 0xFF) { // relleno
                i++;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) { // empiezan los datos o termina: no hay más APPn
                return NORMAL;
            }
            int length = u16(d, i + 2, false);
            if (length < 2) {
                return NORMAL;
            }
            int segment = i + 4;
            if (marker == 0xE1 && startsWith(d, segment, EXIF_HEADER)) {
                return fromTiff(d, segment + EXIF_HEADER.length, i + 2 + length);
            }
            i += 2 + length;
        }
        return NORMAL;
    }

    private static int fromTiff(byte[] d, int tiff, int end) {
        boolean little;
        if (u8(d, tiff) == 'I' && u8(d, tiff + 1) == 'I') {
            little = true;
        } else if (u8(d, tiff) == 'M' && u8(d, tiff + 1) == 'M') {
            little = false;
        } else {
            return NORMAL;
        }
        int ifd = tiff + (int) u32(d, tiff + 4, little);
        int entries = u16(d, ifd, little);
        for (int n = 0; n < entries; n++) {
            int entry = ifd + 2 + n * 12;
            if (entry + 12 > end) {
                return NORMAL;
            }
            if (u16(d, entry, little) == TAG_ORIENTATION && u16(d, entry + 2, little) == TYPE_SHORT) {
                int value = u16(d, entry + 8, little);
                return value >= 1 && value <= 8 ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static boolean startsWith(byte[] d, int offset, byte[] prefix) {
        if (offset + prefix.length > d.length) {
            return false;
        }
        for (int k = 0; k < prefix.length; k++) {
            if (d[offset + k] != prefix[k]) {
                return false;
            }
        }
        return true;
    }

    private static int u8(byte[] d, int i) {
        return d[i] & 0xFF;
    }

    private static int u16(byte[] d, int i, boolean little) {
        return little ? u8(d, i) | u8(d, i + 1) << 8 : u8(d, i) << 8 | u8(d, i + 1);
    }

    private static long u32(byte[] d, int i, boolean little) {
        return little
                ? (long) u16(d, i, true) | (long) u16(d, i + 2, true) << 16
                : (long) u16(d, i, false) << 16 | u16(d, i + 2, false);
    }
}
