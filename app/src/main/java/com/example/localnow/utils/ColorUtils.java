package com.example.localnow.utils;

public class ColorUtils {

    /**
     * Returns a color code based on the event category.
     * @param category The category of the event.
     * @return An integer color code.
     */
    public static int getCategoryColor(String category) {
        if (category == null) {
            return 0xFFFF4757; // Default Red
        }
        switch (category) {
            case "축제":
                return 0xFFFFB6C1; // Pink
            case "문화/예술":
                return 0xFFFFEB3B; // Yellow
            case "공연/전시":
                return 0xFF2196F3; // Blue
            case "교육/강좌":
                return 0xFF4DD0E1; // Mint
            case "플리마켓":
                return 0xFF34A853; // Green for Flea Market
            default:
                return 0xFFFF4757; // Default Red
        }
    }
}
