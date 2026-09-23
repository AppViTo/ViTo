package com.vito.mosaic.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MosaicGrid {
    
    private String eventId;
    
    private List<List<MosaicItem>> grid;
    
    private int totalItems;
    
    private int lastUpdatedIndex;
    
    private LocalDateTime lastUpdatedAt;
    
    public static MosaicGrid createEmpty(String eventId, int rows, int cols) {
        List<List<MosaicItem>> grid = new java.util.ArrayList<>();
        for (int i = 0; i < rows; i++) {
            List<MosaicItem> row = new java.util.ArrayList<>();
            for (int j = 0; j < cols; j++) {
                row.add(null);
            }
            grid.add(row);
        }
        
        return MosaicGrid.builder()
                .eventId(eventId)
                .grid(grid)
                .totalItems(0)
                .lastUpdatedIndex(-1)
                .lastUpdatedAt(LocalDateTime.now())
                .build();
    }
    
    public void addItem(MosaicItem item, int index) {
        int row = index / 4;
        int col = index % 4;
        
        if (row < grid.size() && col < grid.get(row).size()) {
            grid.get(row).set(col, item);
            totalItems++;
            lastUpdatedIndex = index;
            lastUpdatedAt = LocalDateTime.now();
        }
    }
    
    public MosaicItem getLatestItem() {
        if (lastUpdatedIndex >= 0) {
            int row = lastUpdatedIndex / 4;
            int col = lastUpdatedIndex % 4;
            return grid.get(row).get(col);
        }
        return null;
    }
}
