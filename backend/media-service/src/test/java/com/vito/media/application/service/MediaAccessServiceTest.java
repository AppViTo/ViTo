package com.vito.media.application.service;

import com.vito.media.domain.exception.MediaException;
import com.vito.media.domain.model.EventAccess;
import com.vito.media.domain.model.Media;
import com.vito.media.domain.model.PresignedUrl;
import com.vito.media.domain.port.outbound.EventAccessLookup;
import com.vito.media.domain.port.outbound.FileTypeDetectorPort;
import com.vito.media.domain.port.outbound.ImageProcessingPort;
import com.vito.media.domain.port.outbound.ImageReencoderPort;
import com.vito.media.domain.port.outbound.MediaEventPublisher;
import com.vito.media.domain.port.outbound.MediaRepository;
import com.vito.media.domain.port.outbound.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T-B33: las 3 operaciones de media de la Ola 3 (T-C09) y el borrado al retirar del mosaico.
 * Acceso: listan y piden URL el organizador y quien tiene RSVP ACCEPTED; borran el dueño y el
 * organizador (decisión 3).
 */
@ExtendWith(MockitoExtension.class)
class MediaAccessServiceTest {

    private static final String EVENT = "evt_1";
    private static final String ORGANIZER = "usr_org";
    private static final String OWNER = "usr_owner";
    private static final String GUEST = "usr_guest";

    @Mock private MediaRepository mediaRepository;
    @Mock private StoragePort storagePort;
    @Mock private MediaEventPublisher eventPublisher;
    @Mock private ImageProcessingPort imageProcessingPort;
    @Mock private FileTypeDetectorPort fileTypeDetector;
    @Mock private ImageReencoderPort imageReencoder;
    @Mock private EventAccessLookup eventAccessLookup;

    private MediaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MediaServiceImpl(mediaRepository, storagePort, eventPublisher, imageProcessingPort,
                fileTypeDetector, imageReencoder, eventAccessLookup);
    }

    private static Media photo(String id) {
        return Media.builder().id(id).eventId(EVENT).uploadedBy(OWNER)
                .location(Media.S3Location.builder()
                        .key("events/evt_1/usr_owner/" + id + ".jpg")
                        .thumbnailKey("events/evt_1/usr_owner/thumb_" + id + ".jpg")
                        .url("https://cdn.vito.com/events/evt_1/usr_owner/" + id + ".jpg")
                        .build())
                .build();
    }

    private void access(String rsvpStatus) {
        when(eventAccessLookup.findAccess(EVENT)).thenReturn(Optional.of(new EventAccess(ORGANIZER, rsvpStatus)));
    }

    // ---- listEventMedia

    @Test
    void organizerAndAcceptedGuestListTheEventMedia() {
        access(null);
        when(mediaRepository.findByEventId(EVENT, 1, 10)).thenReturn(List.of(photo("m1")));
        assertEquals(1, service.listEventMedia(EVENT, ORGANIZER, 1, 10).size());

        access("ACCEPTED");
        assertEquals(1, service.listEventMedia(EVENT, GUEST, 1, 10).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "MAYBE", "DECLINED", "WAITLISTED", "NONE"})
    void withoutAcceptedRsvpListingIs403(String status) {
        access("NONE".equals(status) ? null : status);

        MediaException.UnauthorizedException ex = assertThrows(MediaException.UnauthorizedException.class,
                () -> service.listEventMedia(EVENT, GUEST, 0, 20));
        assertEquals("No tienes acceso a los archivos de este evento", ex.getMessage());
        verify(mediaRepository, never()).findByEventId(any(), anyInt(), anyInt());
    }

    @Test
    void unknownEventIs404() {
        when(eventAccessLookup.findAccess("evt_x")).thenReturn(Optional.empty());

        assertThrows(MediaException.EventNotFoundException.class, () -> service.listEventMedia("evt_x", GUEST, 0, 20));
    }

    @Test
    void pageAndSizeOutOfRangeAre400WithoutAskingEventService() {
        MediaException.InvalidParameterException size = assertThrows(MediaException.InvalidParameterException.class,
                () -> service.listEventMedia(EVENT, ORGANIZER, 0, 101));
        assertEquals("size", size.getParameter());
        assertEquals("size debe estar entre 1 y 100", size.getMessage());
        assertEquals("debe ser menor o igual a 100", size.getDetail());
        assertEquals("debe ser mayor o igual a 1", assertThrows(MediaException.InvalidParameterException.class,
                () -> service.listEventMedia(EVENT, ORGANIZER, 0, 0)).getDetail());
        assertEquals("page", assertThrows(MediaException.InvalidParameterException.class,
                () -> service.listEventMedia(EVENT, ORGANIZER, -1, 20)).getParameter());
        verifyNoInteractions(eventAccessLookup);
    }

    // ---- deleteMedia

    @Test
    void ownerDeletesFileThumbnailAndRecordWithoutAskingEventService() {
        when(mediaRepository.findById("m1")).thenReturn(Optional.of(photo("m1")));

        service.deleteMedia("m1", OWNER);

        verify(storagePort).deleteFile("events/evt_1/usr_owner/m1.jpg");
        verify(storagePort).deleteFile("events/evt_1/usr_owner/thumb_m1.jpg");
        verify(mediaRepository).deleteById("m1");
        verify(eventPublisher).publishMediaDeleted("m1", EVENT);
        verifyNoInteractions(eventAccessLookup);
    }

    @Test
    void organizerDeletesSomeoneElsesFile() {
        when(mediaRepository.findById("m1")).thenReturn(Optional.of(photo("m1")));
        access(null);

        service.deleteMedia("m1", ORGANIZER);

        verify(mediaRepository).deleteById("m1");
    }

    @Test
    void anAcceptedGuestCannotDeleteSomeoneElsesFile() {
        when(mediaRepository.findById("m1")).thenReturn(Optional.of(photo("m1")));
        access("ACCEPTED");

        MediaException.UnauthorizedException ex = assertThrows(MediaException.UnauthorizedException.class,
                () -> service.deleteMedia("m1", GUEST));
        assertEquals("Solo quien subió el archivo o el organizador del evento puede borrarlo", ex.getMessage());
        verifyNoInteractions(storagePort);
        verify(mediaRepository, never()).deleteById(any());
    }

    @Test
    void storageFailureKeepsTheRecordSoTheDeleteCanBeRetried() {
        when(mediaRepository.findById("m1")).thenReturn(Optional.of(photo("m1")));
        doThrow(new RuntimeException("R2 caído")).when(storagePort).deleteFile(any());

        assertThrows(RuntimeException.class, () -> service.deleteMedia("m1", OWNER));
        verify(mediaRepository, never()).deleteById(any());
    }

    @Test
    void missingOrLegacySoftDeletedMediaIs404() {
        when(mediaRepository.findById("m_x")).thenReturn(Optional.empty());
        Media legacy = photo("m_old");
        legacy.setDeleted(true);
        when(mediaRepository.findById("m_old")).thenReturn(Optional.of(legacy));

        assertThrows(MediaException.MediaNotFoundException.class, () -> service.deleteMedia("m_x", OWNER));
        assertThrows(MediaException.MediaNotFoundException.class, () -> service.deleteMedia("m_old", OWNER));
        assertTrue(service.getMediaById("m_old").isEmpty());
    }

    // ---- getMediaPresignedUrl

    @Test
    void presignedUrlDefaultsTo900SecondsForAnAcceptedGuest() {
        when(mediaRepository.findById("m1")).thenReturn(Optional.of(photo("m1")));
        access("ACCEPTED");
        when(storagePort.generatePresignedUrl("events/evt_1/usr_owner/m1.jpg", Duration.ofSeconds(900)))
                .thenReturn("https://r2.example/m1.jpg?X-Amz-Expires=900");
        Instant before = Instant.now();

        PresignedUrl presigned = service.generatePresignedUrl("m1", GUEST, null);

        assertEquals("https://r2.example/m1.jpg?X-Amz-Expires=900", presigned.url());
        assertEquals(900, presigned.expiresIn());
        assertFalse(presigned.expiresAt().isBefore(before.plusSeconds(900)));
        assertFalse(presigned.expiresAt().isAfter(Instant.now().plusSeconds(900)));
    }

    @Test
    void presignedUrlAcceptsTheLimits() {
        when(mediaRepository.findById("m1")).thenReturn(Optional.of(photo("m1")));
        access(null);
        when(storagePort.generatePresignedUrl(any(), any())).thenReturn("https://r2.example/m1.jpg");

        assertEquals(1, service.generatePresignedUrl("m1", ORGANIZER, 1).expiresIn());
        assertEquals(3600, service.generatePresignedUrl("m1", ORGANIZER, 3600).expiresIn());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 3601})
    void presignedUrlOutOfRangeIs400(int seconds) {
        MediaException.InvalidParameterException ex = assertThrows(MediaException.InvalidParameterException.class,
                () -> service.generatePresignedUrl("m1", ORGANIZER, seconds));
        assertEquals("expiresIn", ex.getParameter());
        assertEquals("expiresIn debe estar entre 1 y 3600 segundos", ex.getMessage());
        verifyNoInteractions(mediaRepository, storagePort);
    }

    @Test
    void presignedUrlForSomeoneWithoutAccessIs403() {
        when(mediaRepository.findById("m1")).thenReturn(Optional.of(photo("m1")));
        access("MAYBE");

        assertThrows(MediaException.UnauthorizedException.class, () -> service.generatePresignedUrl("m1", GUEST, null));
        verifyNoInteractions(storagePort);
    }

    // ---- removeMosaicItem → deleteMosaicItemMedia (decisión 2 de T-C09)

    @Test
    void removingAMosaicItemDeletesItsFilesAndIsIdempotent() {
        List<String> urls = List.of("https://cdn.vito.com/events/evt_1/usr_owner/m1.jpg");
        when(mediaRepository.findByEventIdAndUrls(EVENT, urls)).thenReturn(List.of(photo("m1")), List.of());

        assertEquals(1, service.deleteMosaicItemMedia(EVENT, urls));
        assertEquals(0, service.deleteMosaicItemMedia(EVENT, urls));

        verify(storagePort, times(2)).deleteFile(any());
        verify(mediaRepository, times(1)).deleteById("m1");
    }

    @Test
    void anItemWithoutPhotosDeletesNothing() {
        assertEquals(0, service.deleteMosaicItemMedia(EVENT, List.of()));
        assertEquals(0, service.deleteMosaicItemMedia(EVENT, null));
        verifyNoInteractions(mediaRepository, storagePort);
    }
}
