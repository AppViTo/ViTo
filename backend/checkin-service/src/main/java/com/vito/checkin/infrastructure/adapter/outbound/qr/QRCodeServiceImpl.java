package com.vito.checkin.infrastructure.adapter.outbound.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.vito.checkin.domain.port.outbound.QRCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class QRCodeServiceImpl implements QRCodeService {
    
    @Value("${qr.secret}")
    private String secretKey;
    private static final String ALGORITHM = "HmacSHA256";
    
    @Override
    public String generateQRCode(String eventId, String userId) {
        String payload = eventId + ":" + userId + ":" + UUID.randomUUID().toString();
        
        try {
            String signature = generateHMAC(payload);
            String qrContent = payload + ":" + signature;
            
            return Base64.getUrlEncoder().withoutPadding().encodeToString(qrContent.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
    
    @Override
    public String generateQRCodeHash(String qrCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(qrCode.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate QR code hash", e);
        }
    }
    
    @Override
    public boolean validateQRCode(String qrCode, String eventId, String userId) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(qrCode), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            
            if (parts.length != 4) {
                return false;
            }
            
            String payloadEventId = parts[0];
            String payloadUserId = parts[1];
            String uuid = parts[2];
            String signature = parts[3];
            
            // Validate event ID and user ID
            if (!payloadEventId.equals(eventId) || !payloadUserId.equals(userId)) {
                return false;
            }
            
            // Validate signature
            String payload = payloadEventId + ":" + payloadUserId + ":" + uuid;
            String expectedSignature = generateHMAC(payload);
            
            return signature.equals(expectedSignature);
        } catch (Exception e) {
            log.error("QR code validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public byte[] generateQRCodeImage(String qrCode, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            
            BitMatrix bitMatrix = qrCodeWriter.encode(qrCode, BarcodeFormat.QR_CODE, width, height, hints);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code image", e);
        }
    }
    
    private String generateHMAC(String payload) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(secretKeySpec);
        
        byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
    }
}
