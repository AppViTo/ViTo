package com.vito.media.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Imágenes de prueba de T-B25 generadas en memoria: con EXIF/GPS, políglotas, dañadas y
 * bombas de descompresión. Ningún binario en el repo salvo tres WebP de 1×1 en base64.
 */
public final class SampleImages {

    /** Cadena que no debe sobrevivir a la re-codificación (va en el GPS, en tEXt o anexada). */
    public static final String SECRET = "VITO-GPS-SECRET";

    public static final String PAYLOAD = "<script>alert('vito-polyglot')</script>";

    // WebP de 1×1 de las pruebas de soporte de Modernizr (lossy, lossless y con alfa)
    private static final String WEBP_LOSSY = "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA";
    private static final String WEBP_LOSSLESS = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";
    private static final String WEBP_ALPHA = "UklGRkoAAABXRUJQVlA4WAoAAAAQAAAAAAAAAAAAQUxQSAwAAAARBxAR/Q9ERP8DAABWUDggGAAAABQBAJ0BKgEAAQAAAP4AAA3AAP7mtQAAAA==";

    private SampleImages() {
    }

    /** Mitad izquierda roja y derecha azul: permite comprobar giros. */
    public static BufferedImage halves(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return image;
    }

    public static byte[] jpeg(int width, int height) {
        return write(halves(width, height), "jpg");
    }

    public static byte[] png(int width, int height) {
        return write(halves(width, height), "png");
    }

    /** JPEG con un APP1 EXIF que lleva Orientation, GPSLatitude y {@link #SECRET} en el GPS. */
    public static byte[] jpegWithExifGps(int width, int height, int orientation) {
        byte[] jpeg = jpeg(width, height);
        byte[] tiff = exifTiff(orientation);
        int afterApp0 = 4 + u16(jpeg, 4); // SOI (2) + marcador APP0 (2) + longitud del segmento
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, afterApp0);
        int length = 2 + 6 + tiff.length;
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
        out.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(tiff);
        out.write(jpeg, afterApp0, jpeg.length - afterApp0);
        return out.toByteArray();
    }

    /** PNG con chunks tEXt y eXIf (EXIF con GPS) justo después del IHDR. */
    public static byte[] pngWithTextAndExif(int width, int height) {
        byte[] png = png(width, height);
        int afterIhdr = 8 + 25;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, afterIhdr);
        out.writeBytes(chunk("tEXt", ascii("Comment\0" + SECRET)));
        out.writeBytes(chunk("eXIf", exifTiff(1)));
        out.write(png, afterIhdr, png.length - afterIhdr);
        return out.toByteArray();
    }

    /** Imagen válida con {@link #PAYLOAD} anexado al final (después de IEND / EOI). */
    public static byte[] polyglot(byte[] image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(image);
        out.writeBytes(ascii(PAYLOAD));
        return out.toByteArray();
    }

    /** Firma PNG válida (Tika la acepta) seguida de bytes aleatorios. */
    public static byte[] corruptPng() {
        return concat(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, noise(2048));
    }

    /** Cabecera JPEG/JFIF válida seguida de bytes aleatorios. */
    public static byte[] corruptJpeg() {
        byte[] jpeg = jpeg(16, 16);
        return concat(java.util.Arrays.copyOf(jpeg, 20), noise(2048));
    }

    /**
     * Bomba PNG real: 20 000 × 20 000 en gris de 1 bit (400 MP, ~1,6 GB como raster RGBA),
     * con el IDAT comprimido a unos pocos KB.
     */
    public static byte[] pngBomb() {
        int side = 20_000;
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(side).putInt(side)
                .put((byte) 1).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(idat)) {
            byte[] row = new byte[1 + side / 8]; // filtro 0 + 2500 bytes a cero
            for (int y = 0; y < side; y++) {
                deflate.write(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return concat(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'},
                chunk("IHDR", ihdr.array()), chunk("IDAT", idat.toByteArray()), chunk("IEND", new byte[0]));
    }

    /** JPEG válido de 8×8 con el SOF reescrito a 65 535 × 65 535 (4,3 gigapíxeles). */
    public static byte[] jpegBomb() {
        byte[] jpeg = jpeg(8, 8);
        for (int i = 2; i + 9 < jpeg.length; i++) {
            if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == 0xC0) {
                for (int k = 5; k <= 8; k++) {
                    jpeg[i + k] = (byte) 0xFF;
                }
                return jpeg;
            }
        }
        throw new IllegalStateException("JPEG sin SOF0");
    }

    public static byte[] webpLossy() {
        return Base64.getDecoder().decode(WEBP_LOSSY);
    }

    public static byte[] webpLossless() {
        return Base64.getDecoder().decode(WEBP_LOSSLESS);
    }

    public static byte[] webpAlpha() {
        return Base64.getDecoder().decode(WEBP_ALPHA);
    }

    public static boolean contains(byte[] data, String text) {
        return indexOf(data, ascii(text)) >= 0;
    }

    public static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i + pattern.length <= data.length; i++) {
            for (int k = 0; k < pattern.length; k++) {
                if (data[i + k] != pattern[k]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /** TIFF little-endian: IFD0 con Orientation y puntero GPS; IFD GPS con latitud y {@link #SECRET}. */
    private static byte[] exifTiff(int orientation) {
        byte[] secret = concat(ascii("ASCII\0\0\0"), ascii(SECRET));
        ByteBuffer b = ByteBuffer.allocate(104 + secret.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put(ascii("II")).putShort((short) 42).putInt(8);
        // IFD0 en 8 → termina en 38
        b.putShort((short) 2);
        b.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        b.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38);
        b.putInt(0);
        // IFD GPS en 38 → termina en 80
        b.putShort((short) 3);
        b.putShort((short) 0x0001).putShort((short) 2).putInt(2).put((byte) 'N').put(new byte[3]);
        b.putShort((short) 0x0002).putShort((short) 5).putInt(3).putInt(80);
        b.putShort((short) 0x001B).putShort((short) 7).putInt(secret.length).putInt(104);
        b.putInt(0);
        // GPSLatitude 4° 36' 12,34" en 80 → termina en 104
        b.putInt(4).putInt(1).putInt(36).putInt(1).putInt(1234).putInt(100);
        b.put(secret);
        return b.array();
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] typeBytes = ascii(type);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        return ByteBuffer.allocate(12 + data.length).putInt(data.length).put(typeBytes).put(data)
                .putInt((int) crc.getValue()).array();
    }

    private static byte[] noise(int size) {
        byte[] bytes = new byte[size];
        new Random(25).nextBytes(bytes);
        return bytes;
    }

    private static byte[] write(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("ImageIO no escribe " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int u16(byte[] d, int i) {
        return (d[i] & 0xFF) << 8 | (d[i + 1] & 0xFF);
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
