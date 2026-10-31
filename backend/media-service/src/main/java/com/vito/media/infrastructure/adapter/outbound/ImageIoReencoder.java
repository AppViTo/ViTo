package com.vito.media.infrastructure.adapter.outbound;

import com.vito.media.domain.exception.MediaException;
import com.vito.media.domain.port.outbound.ImageReencoderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Re-codificación con ImageIO (T-B25, excepción A3). Lee con los plugins de TwelveMonkeys (WebP,
 * JPEG CMYK) o los del JDK y escribe con los writers JPEG y PNG del JDK, sin metadatos: el
 * archivo de salida solo lleva los píxeles. WebP no tiene writer en Java puro, así que se
 * guarda como PNG si tiene transparencia y como JPEG si no.
 *
 * <p>Memoria: el tope de píxeles se comprueba con la cabecera, antes de reservar el raster, y
 * los decodificados simultáneos se limitan con un semáforo. {@link #worstCaseHeapBytes} da el
 * peor caso, que {@code ImageMemoryBudgetTest} contrasta con el {@code mem_limit} de
 * {@code docker-compose.prod.yml}.
 */
@Slf4j
@Component
public class ImageIoReencoder implements ImageReencoderPort {

    /** 16 MP: cubre las fotos de 12 MP de los móviles y 4000 × 4000 (T-B25). */
    public static final long DEFAULT_MAX_PIXELS = 16_000_000L;

    public static final int DEFAULT_MAX_CONCURRENT = 1;

    /**
     * Peor caso por píxel durante una re-codificación: PNG de 16 bits RGBA (8 B, se escribe sin
     * copia); WebP decodificado (4 B) + YUV del decodificador (1,5 B) + copia RGB para JPEG (3 B);
     * JPEG (3 B) + copia girada (3 B). Se redondea a 9.
     */
    static final int WORST_CASE_BYTES_PER_PIXEL = 9;

    static final float JPEG_QUALITY = 0.9f;

    private static final Set<String> SUPPORTED = Set.of("image/jpeg", "image/png", "image/webp");

    private final long maxPixels;
    private final Semaphore decodePermits;

    public ImageIoReencoder(
            @Value("${media.image.max-pixels:" + DEFAULT_MAX_PIXELS + "}") long maxPixels,
            @Value("${media.image.max-concurrent:" + DEFAULT_MAX_CONCURRENT + "}") int maxConcurrent) {
        this.maxPixels = maxPixels;
        this.decodePermits = new Semaphore(maxConcurrent, true);
        // Registra los plugins de TwelveMonkeys aunque ImageIO se haya iniciado con otro classloader
        ImageIO.scanForPlugins();
        ImageIO.setUseCache(false);
    }

    public static long worstCaseHeapBytes(long maxPixels, int maxConcurrent) {
        return maxPixels * WORST_CASE_BYTES_PER_PIXEL * maxConcurrent;
    }

    @Override
    public boolean supports(String contentType) {
        return SUPPORTED.contains(contentType);
    }

    @Override
    public ReencodedImage reencode(byte[] data, String contentType) {
        if (!supports(contentType)) {
            throw new IllegalArgumentException("Tipo no re-codificable: " + contentType);
        }
        try {
            decodePermits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaException.UploadFailedException("re-codificación interrumpida");
        }
        try {
            BufferedImage image = decode(data, contentType);
            if ("image/jpeg".equals(contentType)) {
                image = applyOrientation(image, ExifOrientation.read(data));
            }
            return encode(image, contentType);
        } finally {
            decodePermits.release();
        }
    }

    private BufferedImage decode(byte[] data, String contentType) {
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(contentType);
            if (!readers.hasNext()) {
                throw new IllegalStateException("No hay lector de ImageIO para " + contentType);
            }
            ImageReader reader = readers.next();
            try {
                // ignoreMetadata: no se parsean EXIF, XMP ni chunks de texto (menos superficie)
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0) {
                    throw new MediaException.UnreadableImageException();
                }
                if (pixels > maxPixels) {
                    throw new MediaException.ImageTooLargeException(maxPixels);
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new MediaException.UnreadableImageException();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (MediaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Los decodificadores fallan con IIOException o con excepciones no comprobadas
            log.debug("Imagen {} no decodifica: {}", contentType, e.toString());
            throw new MediaException.UnreadableImageException();
        }
    }

    private ReencodedImage encode(BufferedImage image, String sourceType) {
        boolean png = "image/png".equals(sourceType)
                || ("image/webp".equals(sourceType) && image.getColorModel().hasAlpha());
        BufferedImage output = png ? image : withoutAlpha(image);
        String format = png ? "png" : "jpeg";
        byte[] bytes = write(output, format);
        return new ReencodedImage(bytes, "image/" + format, output.getWidth(), output.getHeight());
    }

    private static byte[] write(BufferedImage image, String format) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if ("jpeg".equals(format)) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            // Metadatos null: el writer solo pone lo mínimo del formato (JFIF / IHDR)
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /** El writer JPEG del JDK solo acepta RGB o gris de 8 bits. */
    private static BufferedImage withoutAlpha(BufferedImage image) {
        int type = image.getType();
        if (type == BufferedImage.TYPE_3BYTE_BGR || type == BufferedImage.TYPE_BYTE_GRAY
                || type == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /**
     * Al quitar el EXIF se pierde la orientación: se aplica a los píxeles para que la foto no
     * quede girada. Transformaciones de la etiqueta 0x0112 (TIFF 6.0 / EXIF 2.3).
     */
    static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        int type = image.getType() == BufferedImage.TYPE_BYTE_GRAY
                ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
        BufferedImage oriented = new BufferedImage(swap ? h : w, swap ? w : h, type);
        Graphics2D g = oriented.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, t, null);
        } finally {
            g.dispose();
        }
        return oriented;
    }
}
