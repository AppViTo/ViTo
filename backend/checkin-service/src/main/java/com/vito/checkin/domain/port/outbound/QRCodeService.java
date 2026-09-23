package com.vito.checkin.domain.port.outbound;

public interface QRCodeService {
    
    String generateQRCode(String eventId, String userId);
    
    String generateQRCodeHash(String qrCode);
    
    boolean validateQRCode(String qrCode, String eventId, String userId);
    
    byte[] generateQRCodeImage(String qrCode, int width, int height);
}
