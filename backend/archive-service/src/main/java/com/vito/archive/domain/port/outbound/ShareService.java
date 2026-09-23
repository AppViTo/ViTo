package com.vito.archive.domain.port.outbound;

public interface ShareService {
    
    String generateShareCode(String archiveId);
    
    String generateShareUrl(String archiveId, String shareCode);
    
    boolean validateShareCode(String shareCode);
    
    String getArchiveIdByShareCode(String shareCode);
}
