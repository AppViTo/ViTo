package com.vito.media.infrastructure.adapter.outbound;

import com.vito.media.domain.exception.MediaException;
import com.vito.media.domain.port.outbound.ImageReencoderPort.ReencodedImage;
import com.vito.media.support.SampleImages;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** T-B25 (excepción A3): solo sale la versión re-codificada, sin metadatos ni datos anexados. */
class ImageIoReencoderTest {

    private final ImageIoReencoder reencoder = new ImageIoReencoder(
            ImageIoReencoder.DEFAULT_MAX_PIXELS, ImageIoReencoder.DEFAULT_MAX_CONCURRENT);

    @Test
    void jpegWithExifAndGps_OutputHasNoExifNorGps() throws IOException {
        byte[] input = SampleImages.jpegWithExifGps(40, 20, 1);
        assertTrue(SampleImages.contains(input, "Exif"));
        assertTrue(SampleImages.contains(input, SampleImages.SECRET));

        ReencodedImage out = reencoder.reencode(input, "image/jpeg");

        assertEquals("image/jpeg", out.contentType());
        assertFalse(SampleImages.contains(out.data(), "Exif"), "el APP1 EXIF sigue en la salida");
        assertFalse(SampleImages.contains(out.data(), SampleImages.SECRET), "el GPS sigue en la salida");
        assertEquals(-1, SampleImages.indexOf(out.data(), new byte[]{(byte) 0xFF, (byte) 0xE1}), "hay un APP1");
        BufferedImage decoded = decode(out.data());
        assertEquals(40, decoded.getWidth());
        assertEquals(20, decoded.getHeight());
    }

    /** Quitar el EXIF borra la orientación: se aplica a los píxeles (6 = girar 90° a la derecha). */
    @Test
    void jpegWithOrientation6_IsRotatedBeforeDroppingExif() throws IOException {
        ReencodedImage out = reencoder.reencode(SampleImages.jpegWithExifGps(40, 20, 6), "image/jpeg");

        assertEquals(20, out.width());
        assertEquals(40, out.height());
        BufferedImage decoded = decode(out.data());
        // La mitad izquierda (roja) del original queda arriba
        assertTrue(isReddish(decoded.getRGB(10, 5)), Integer.toHexString(decoded.getRGB(10, 5)));
        assertFalse(isReddish(decoded.getRGB(10, 35)), Integer.toHexString(decoded.getRGB(10, 35)));
    }

    @Test
    void pngWithTextAndExifChunks_OutputHasNeither() {
        byte[] input = SampleImages.pngWithTextAndExif(8, 8);

        ReencodedImage out = reencoder.reencode(input, "image/png");

        assertEquals("image/png", out.contentType());
        assertFalse(SampleImages.contains(out.data(), "tEXt"));
        assertFalse(SampleImages.contains(out.data(), "eXIf"));
        assertFalse(SampleImages.contains(out.data(), SampleImages.SECRET));
    }

    @Test
    void pngPolyglot_AppendedDataIsRemoved() throws IOException {
        byte[] input = SampleImages.polyglot(SampleImages.png(8, 8));

        ReencodedImage out = reencoder.reencode(input, "image/png");

        assertFalse(SampleImages.contains(out.data(), SampleImages.PAYLOAD));
        assertTrue(SampleImages.contains(out.data(), "IEND"));
        // Nada después del IEND + CRC
        assertEquals(out.data().length, SampleImages.indexOf(out.data(), "IEND".getBytes()) + 8);
        assertNotNull(decode(out.data()));
    }

    @Test
    void jpegPolyglot_AppendedDataIsRemoved() {
        byte[] input = SampleImages.polyglot(SampleImages.jpeg(16, 16));

        ReencodedImage out = reencoder.reencode(input, "image/jpeg");

        assertFalse(SampleImages.contains(out.data(), SampleImages.PAYLOAD));
        int length = out.data().length;
        assertEquals(0xFF, out.data()[length - 2] & 0xFF, "no termina en EOI");
        assertEquals(0xD9, out.data()[length - 1] & 0xFF, "no termina en EOI");
    }

    @Test
    void corruptPng_IsRejected() {
        assertThrows(MediaException.UnreadableImageException.class,
                () -> reencoder.reencode(SampleImages.corruptPng(), "image/png"));
    }

    @Test
    void corruptJpeg_IsRejected() {
        assertThrows(MediaException.UnreadableImageException.class,
                () -> reencoder.reencode(SampleImages.corruptJpeg(), "image/jpeg"));
    }

    /** 400 MP: se rechaza con la cabecera, sin reservar el raster (si no, tardaría y agotaría el heap). */
    @Test
    void pngDecompressionBomb_IsRejectedBeforeDecoding() {
        byte[] bomb = SampleImages.pngBomb();
        assertTrue(bomb.length < 1024 * 1024, "la bomba debe caber de sobra en 10 MB: " + bomb.length);

        MediaException.ImageTooLargeException ex = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(MediaException.ImageTooLargeException.class,
                        () -> reencoder.reencode(bomb, "image/png")));

        assertEquals("La imagen supera el máximo de 16 megapíxeles", ex.getMessage());
    }

    @Test
    void jpegDecompressionBomb_IsRejectedBeforeDecoding() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThrows(
                MediaException.ImageTooLargeException.class,
                () -> reencoder.reencode(SampleImages.jpegBomb(), "image/jpeg")));
    }

    @Test
    void lossyWebp_IsStoredAsJpeg() throws IOException {
        ReencodedImage out = reencoder.reencode(SampleImages.webpLossy(), "image/webp");

        assertEquals("image/jpeg", out.contentType());
        assertEquals(1, decode(out.data()).getWidth());
    }

    @Test
    void losslessWebp_IsReencoded() throws IOException {
        ReencodedImage out = reencoder.reencode(SampleImages.webpLossless(), "image/webp");

        assertEquals(1, decode(out.data()).getWidth());
    }

    @Test
    void webpWithAlpha_IsStoredAsPngKeepingTransparency() throws IOException {
        ReencodedImage out = reencoder.reencode(SampleImages.webpAlpha(), "image/webp");

        assertEquals("image/png", out.contentType());
        assertTrue(decode(out.data()).getColorModel().hasAlpha());
    }

    @Test
    void onlyTheThreeImageTypesAreReencoded() {
        assertTrue(reencoder.supports("image/jpeg"));
        assertTrue(reencoder.supports("image/png"));
        assertTrue(reencoder.supports("image/webp"));
        assertFalse(reencoder.supports("video/mp4"));
        assertFalse(reencoder.supports("image/gif"));
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        assertNotNull(image, "la salida no decodifica");
        return image;
    }

    private static boolean isReddish(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 150 && c.getBlue() < 100;
    }
}
