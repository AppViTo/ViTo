package com.vito.mosaic.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MosaicItem {
    
    private String id;
    
    private String eventId;
    
    private String userId;
    
    private String username;
    
    private String displayName;
    
    private String avatar;
    
    private MosaicItemType type;
    
    private String content;
    
    private String mediaUrl;
    
    private LocalDateTime timestamp;
    
    private MosaicPosition position;
    
    private int likesCount;
    
    private int commentsCount;
    
    public enum MosaicItemType {
        CHECK_IN,
        POST,
        PHOTO,
        REACTION,
        STORY,
        HIGHLIGHT
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MosaicPosition {
        private int row;
        private int col;
        private int width;
        private int height;
        
        public static MosaicPosition defaultPosition(int index) {
            int row = index / 4;
            int col = index % 4;
            return MosaicPosition.builder()
                    .row(row)
                    .col(col)
                    .width(1)
                    .height(1)
                    .build();
        }
    }
}
