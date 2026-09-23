package com.vito.archive.infrastructure.adapter.outbound;

import com.vito.archive.domain.port.outbound.ShareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ShareServiceImpl implements ShareService {
    
    private final StringRedisTemplate redisTemplate;
    
    @Value("${app.share.base-url:http://localhost:8080/archive}")
    private String baseUrl;
    
    private static final String SHARE_CODE_PREFIX = "share:";
    private static final String SHARE_CODE_TTL_DAYS = 365;
    
    public ShareServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public String generateShareCode(String archiveId) {
        String code = generateShortCode(archiveId);
        String key = SHARE_CODE_PREFIX + code;
        
        redisTemplate.opsForValue().set(key, archiveId, SHARE_CODE_TTL_DAYS, TimeUnit.DAYS);
        redisTemplate.opsForValue().set("archive:" + archiveId, code, SHARE_CODE_TTL_DAYS, TimeUnit.DAYS);
        
        return code;
    }
    
    @Override
    public String generateShareUrl(String archiveId, String shareCode) {
        return baseUrl + "/share/" + shareCode;
    }
    
    @Override
    public boolean validateShareCode(String shareCode) {
        String key = SHARE_CODE_PREFIX + shareCode;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    @Override
    public String getArchiveIdByShareCode(String shareCode) {
        String key = SHARE_CODE_PREFIX + shareCode;
        return redisTemplate.opsForValue().get(key);
    }
    
    private String generateShortCode(String archiveId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(archiveId.getBytes());
            
            // Take first 6 bytes and encode to base64url
            byte[] codeBytes = new byte[6];
            System.arraycopy(hash, 0, codeBytes, 0, 6);
            
            String code = Base64.getUrlEncoder().withoutPadding().encodeToString(codeBytes);
            
            // Ensure it's alphanumeric and reasonable length
            return code.substring(0, 8).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to UUID-based code
            return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
    }
}
