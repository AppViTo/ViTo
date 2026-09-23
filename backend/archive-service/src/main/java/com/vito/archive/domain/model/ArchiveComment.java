package com.vito.archive.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveComment {
    private String id;
    private String archiveId;
    private String mediaId;
    private String userId;
    private String username;
    private String displayName;
    private String? avatar;
    private String content;
    private String? parentId;
    private int likesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
