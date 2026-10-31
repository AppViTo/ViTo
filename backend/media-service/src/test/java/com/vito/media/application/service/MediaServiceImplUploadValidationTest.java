package com.vito.media.application.service;

import com.vito.media.domain.exception.MediaException;
import com.vito.media.domain.model.Media;
import com.vito.media.domain.port.outbound.ImageProcessingPort;
import com.vito.media.domain.port.outbound.MediaEventPublisher;
import com.vito.media.domain.port.outbound.MediaRepository;
import com.vito.media.domain.port.outbound.StoragePort;
import com.vito.media.infrastructure.adapter.outbound.ImageIoReencoder;
import com.vito.media.infrastructure.adapter.outbound.TikaFileTypeDetector;
import com.vito.media.support.SampleFiles;
import com.vito.media.support.SampleImages;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * T-B19: el tipo se valida contra el contenido real, con la lista y el límite de CONVENTIONS §8.
 * T-B25: solo se guarda la imagen re-codificada y el vídeo se rechaza (excepción A3).
 */
@ExtendWith(MockitoExtension.class)
class MediaServiceImplUploadValidationTest {

    private static final long TEN_MB = 10L * 1024 * 1024;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private StoragePort storagePort;

    @Mock
    private MediaEventPublisher eventPublisher;

    @Mock
    private ImageProcessingPort imageProcessingPort;

    private MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaServiceImpl(mediaRepository, storagePort, eventPublisher, imageProcessingPort,
                new TikaFileTypeDetector(), new ImageIoReencoder(
                        ImageIoReencoder.DEFAULT_MAX_PIXELS, ImageIoReencoder.DEFAULT_MAX_CONCURRENT));
        ReflectionTestUtils.setField(mediaService, "maxFileSize", TEN_MB);
        ReflectionTestUtils.setField(mediaService, "allowedTypes", "image/jpeg,image/png,image/webp,video/mp4");
        ReflectionTestUtils.setField(mediaService, "bucketName", "vito-test");
    }

    @Test
    void pngDeclaredAsJpeg_IsRejectedBecauseContentDoesNotMatch() {
        MockMultipartFile file = file("foto.jpg", "image/jpeg", SampleFiles.png());

        MediaException.ContentTypeMismatchException ex = assertThrows(
                MediaException.ContentTypeMismatchException.class, () -> upload(file));

        assertEquals("El contenido del archivo es image/png, no image/jpeg", ex.getMessage());
        verifyNoInteractions(storagePort, mediaRepository);
    }

    @Test
    void htmlDeclaredAsPng_IsRejected() {
        MockMultipartFile file = file("foto.png", "image/png", SampleFiles.html());

        MediaException.UnsupportedFileTypeException ex = assertThrows(
                MediaException.UnsupportedFileTypeException.class, () -> upload(file));

        assertEquals("El tipo de archivo text/html no está permitido", ex.getMessage());
        assertEquals("Formatos permitidos: image/jpeg, image/png, image/webp, video/mp4", ex.getDetail());
        verifyNoInteractions(storagePort, mediaRepository);
    }

    @Test
    void gif_IsRejected() {
        MockMultipartFile file = file("animacion.gif", "image/gif", SampleFiles.gif());

        assertThrows(MediaException.UnsupportedFileTypeException.class, () -> upload(file));
        verifyNoInteractions(storagePort, mediaRepository);
    }

    @Test
    void elevenMegabyteFile_IsRejected() {
        byte[] data = new byte[11 * 1024 * 1024];
        byte[] jpeg = SampleFiles.jpeg();
        System.arraycopy(jpeg, 0, data, 0, jpeg.length);
        MockMultipartFile file = file("grande.jpg", "image/jpeg", data);

        MediaException.FileTooLargeException ex = assertThrows(
                MediaException.FileTooLargeException.class, () -> upload(file));

        assertEquals("El archivo supera el tamaño máximo de 10 MB", ex.getMessage());
        verifyNoInteractions(storagePort, mediaRepository);
    }

    @Test
    void emptyFile_IsRejected() {
        MockMultipartFile file = file("vacio.png", "image/png", new byte[0]);

        assertThrows(MediaException.EmptyFileException.class, () -> upload(file));
    }

    @Test
    void validPng_IsStoredWithItsRealType() {
        whenStorageAndRepositoryWork();
        MockMultipartFile file = file("foto.png", "image/png", SampleFiles.png());

        Media media = upload(file);

        assertEquals("image/png", media.getContentType());
        verify(storagePort).uploadFile(anyString(), any(), eq("image/png"));
    }

    /** T-B25: lo que llega a S3 es la versión re-codificada, no los bytes del cliente. */
    @Test
    void jpegWithGps_OnlyTheReencodedVersionIsStored() {
        whenStorageAndRepositoryWork();
        byte[] original = SampleImages.jpegWithExifGps(40, 20, 1);
        MockMultipartFile file = file("foto.jpeg", "image/jpeg", original);

        Media media = upload(file);

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(storagePort).uploadFile(anyString(), stored.capture(), eq("image/jpeg"));
        assertFalse(SampleImages.contains(stored.getValue(), SampleImages.SECRET));
        assertEquals(stored.getValue().length, media.getFileSize());
        assertEquals(40, media.getMetadata().getWidth());
        assertTrue(media.getFileName().endsWith(".jpg"), media.getFileName());
    }

    /** WebP se guarda como JPEG: el tipo y la extensión son los del archivo guardado. */
    @Test
    void webp_IsStoredAsJpegWithMatchingTypeAndExtension() {
        whenStorageAndRepositoryWork();
        MockMultipartFile file = file("foto.webp", "image/webp", SampleImages.webpLossy());

        Media media = upload(file);

        assertEquals("image/jpeg", media.getContentType());
        assertTrue(media.getFileName().endsWith(".jpg"), media.getFileName());
    }

    @Test
    void corruptImage_IsRejectedAndNothingIsStored() {
        MockMultipartFile file = file("foto.png", "image/png", SampleImages.corruptPng());

        assertThrows(MediaException.UnreadableImageException.class, () -> upload(file));
        verifyNoInteractions(storagePort, mediaRepository);
    }

    @Test
    void decompressionBomb_IsRejectedAndNothingIsStored() {
        MockMultipartFile file = file("bomba.png", "image/png", SampleImages.pngBomb());

        assertThrows(MediaException.ImageTooLargeException.class, () -> upload(file));
        verifyNoInteractions(storagePort, mediaRepository);
    }

    /** Excepción A3: el vídeo no se re-codifica, así que no se publica en esta ola (leads, 2026-10-24). */
    @Test
    void validMp4_IsRejectedInThisWave() {
        MockMultipartFile file = file("video.mp4", "video/mp4", SampleFiles.mp4());

        MediaException.VideoNotAllowedException ex = assertThrows(
                MediaException.VideoNotAllowedException.class, () -> upload(file));

        assertEquals("Por ahora no se admiten vídeos: sube una imagen JPEG, PNG o WebP", ex.getMessage());
        verifyNoInteractions(storagePort, mediaRepository);
    }

    private Media upload(MockMultipartFile file) {
        return mediaService.uploadMedia(file, "evt_1", "usr_1");
    }

    private void whenStorageAndRepositoryWork() {
        when(storagePort.uploadFile(anyString(), any(), anyString())).thenReturn("https://cdn.test/archivo");
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static MockMultipartFile file(String name, String contentType, byte[] data) {
        return new MockMultipartFile("file", name, contentType, data);
    }
}
