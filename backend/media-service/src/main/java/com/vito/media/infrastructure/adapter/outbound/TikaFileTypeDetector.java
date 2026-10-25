package com.vito.media.infrastructure.adapter.outbound;

import com.vito.media.domain.port.outbound.FileTypeDetectorPort;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

/**
 * Detección por magic bytes (T-B19): no usa el nombre del archivo ni el Content-Type.
 * Carga el catálogo de Tika más {@code tika/vito-mimetypes.xml} de forma explícita.
 */
@Component
public class TikaFileTypeDetector implements FileTypeDetectorPort {

    private final MimeTypes mimeTypes;

    public TikaFileTypeDetector() {
        try {
            mimeTypes = MimeTypesFactory.create(resource("org/apache/tika/mime/tika-mimetypes.xml"),
                    resource("tika/vito-mimetypes.xml"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (MimeTypeException e) {
            throw new IllegalStateException("Catálogo de tipos MIME no válido", e);
        }
    }

    @Override
    public String detect(byte[] data) {
        try (TikaInputStream stream = TikaInputStream.get(data)) {
            return mimeTypes.detect(stream, new Metadata()).getBaseType().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static URL resource(String path) {
        URL url = TikaFileTypeDetector.class.getClassLoader().getResource(path);
        if (url == null) {
            throw new IllegalStateException("No se encuentra " + path + " en el classpath");
        }
        return url;
    }
}
