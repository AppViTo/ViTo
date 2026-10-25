package com.vito.mosaic.domain.port.outbound;

/** Avisos en tiempo real a los clientes conectados a {@code /ws/mosaic/{eventId}}. */
public interface MosaicRealtimeNotifier {

    /** Frame {@code ITEM_REMOVED} de asyncapi.yaml ({@code MosaicItemRemoved}). */
    void itemRemoved(String eventId, String itemId);
}
