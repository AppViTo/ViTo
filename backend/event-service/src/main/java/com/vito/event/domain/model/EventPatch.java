package com.vito.event.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Cambios de {@code updateEvent} (US2, FR-020) con la semántica de JSON Merge Patch: un campo
 * {@code null} no se envió; un {@link Change} con valor {@code null} pide borrarlo. Solo los
 * campos editables de {@code UpdateEventRequest}: {@code id}, {@code organizerId},
 * {@code status}, {@code stats} y {@code createdAt} no existen aquí.
 */
@Builder
public record EventPatch(
        Change<String> title,
        Change<String> description,
        // details
        Change<Instant> startDate,
        Change<Instant> endDate,
        Change<String> location,
        Change<String> address,
        Change<Double> latitude,
        Change<Double> longitude,
        Change<List<String>> coverImages,
        Change<List<String>> tags,
        Change<String> category,
        // settings
        Change<Boolean> isPublic,
        Change<Boolean> requiresApproval,
        Change<Integer> maxAttendees,
        Change<Boolean> allowGuestInvites,
        Change<Boolean> enableLiveStreaming,
        Change<Boolean> enableMosaic,
        Change<Boolean> hideAddressUntilAccepted) {

    /** Un campo enviado; {@code value} {@code null} = borrar (merge patch). */
    public record Change<T>(T value) {
        public static <T> Change<T> of(T value) {
            return new Change<>(value);
        }
    }

    /** {@code true} si no trae ningún cambio ({@code minProperties: 1}). */
    public boolean isEmpty() {
        return Stream.of(title, description, startDate, endDate, location, address, latitude, longitude,
                        coverImages, tags, category, isPublic, requiresApproval, maxAttendees, allowGuestInvites,
                        enableLiveStreaming, enableMosaic, hideAddressUntilAccepted)
                .allMatch(Objects::isNull);
    }

    /** El valor nuevo si se envió, o el actual si no. */
    public static <T> T valueOr(Change<T> change, T current) {
        return change != null ? change.value() : current;
    }
}
