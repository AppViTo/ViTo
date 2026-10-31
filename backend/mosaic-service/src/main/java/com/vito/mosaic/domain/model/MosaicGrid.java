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
    
    /**
     * Deja en {@code null} la celda del ítem retirado (T-B26).
     *
     * @return {@code true} si el ítem estaba en la grilla
     */
    public boolean removeItem(String itemId) {
        if (itemId == null || grid == null) {
            return false;
        }
        boolean found = false;
        for (List<MosaicItem> row : grid) {
            for (int col = 0; col < row.size(); col++) {
                MosaicItem cell = row.get(col);
                if (cell != null && itemId.equals(cell.getId())) {
                    row.set(col, null);
                    found = true;
                }
            }
        }
        if (found) {
            totalItems = Math.max(0, totalItems - 1);
            lastUpdatedAt = LocalDateTime.now();
        }
        return found;
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
