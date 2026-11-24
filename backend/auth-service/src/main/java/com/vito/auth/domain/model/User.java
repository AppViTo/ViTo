package com.vito.auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String username;
    
    @Indexed(unique = true)
    private String email;
    
    private String passwordHash;
    
    private Profile profile;
    
    private Stats stats;
    
    private Settings settings;
    
    private List<String> roles;

    /** Ola 2a: campos normalizados para {@code searchUsers}. Null en usuarios sin migrar. */
    private Search search;

    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Profile {
        private String displayName;
        private String bio;
        private String avatar;
        private String coverImage;
        private Location location;
    }
    
    /**
     * {@code search.username}: el username en minúsculas y sin tildes. {@code search.terms}: las
     * palabras de {@code displayName} igual de normalizadas (research R3). Se escriben al
     * registrar y en cualquier cambio de username o nombre visible.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Search {
        private String username;
        private List<String> terms;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String type;
        private List<Double> coordinates;
        private String city;
        private String country;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        @Builder.Default
        private Long followersCount = 0L;
        @Builder.Default
        private Long followingCount = 0L;
        @Builder.Default
        private Long postsCount = 0L;
        @Builder.Default
        private Long streamsCount = 0L;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Settings {
        @Builder.Default
        private Boolean privateProfile = false;
        @Builder.Default
        private String allowDMs = "everyone";
        private NotificationSettings notifications;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettings {
        @Builder.Default
        private Boolean likes = true;
        @Builder.Default
        private Boolean comments = true;
        @Builder.Default
        private Boolean follows = true;
        @Builder.Default
        private Boolean streams = true;
    }
}
