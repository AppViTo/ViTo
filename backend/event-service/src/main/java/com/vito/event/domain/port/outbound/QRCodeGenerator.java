package com.vito.event.domain.port.outbound;

public interface QRCodeGenerator {
    
    String generateCheckInQR(String eventId, String userId);
    
    boolean validateCheckInQR(String qrCode, String eventId, String userId);
}
