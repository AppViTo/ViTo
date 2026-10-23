package com.vito.media.support;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Archivos mínimos reales (con sus magic bytes) para probar la validación de subidas. */
public final class SampleFiles {

    private SampleFiles() {
    }

    public static byte[] png() {
        return image("png");
    }

    public static byte[] jpeg() {
        return image("jpg");
    }

    public static byte[] gif() {
        return image("gif");
    }

    /** Cabecera RIFF/WEBP con un chunk VP8 vacío: suficiente para la detección por contenido. */
    public static byte[] webp() {
        return concat(ascii("RIFF"), new byte[]{26, 0, 0, 0}, ascii("WEBPVP8 "), new byte[]{14, 0, 0, 0},
                new byte[14]);
    }

    /** MP4 típico: caja {@code ftyp} con marca {@code isom}. */
    public static byte[] mp4() {
        return isoMedia("isom");
    }

    /** Archivo ISO BMFF (MP4, QuickTime, HEIC...) con la marca principal dada en la caja {@code ftyp}. */
    public static byte[] isoMedia(String majorBrand) {
        return concat(new byte[]{0, 0, 0, 24}, ascii("ftyp" + majorBrand), new byte[]{0, 0, 2, 0},
                ascii("isomiso2avc1mp41"), new byte[]{0, 0, 0, 8}, ascii("free"));
    }

    public static byte[] html() {
        return ascii("<!DOCTYPE html><html><body><script>alert(1)</script></body></html>");
    }

    private static byte[] image(String format) {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("ImageIO no escribe " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
