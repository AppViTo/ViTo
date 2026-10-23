package com.vito.media.domain.port.outbound;

/** Tipo MIME real de un archivo según su contenido, sin confiar en el que declara el cliente. */
public interface FileTypeDetectorPort {

    String detect(byte[] data);
}
