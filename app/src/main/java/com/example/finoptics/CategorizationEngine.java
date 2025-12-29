package com.example.finoptics;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CategorizationEngine {

    // --- LAYER 1: Direct Vendor Mapping ---
    private static final Map<String, String> VENDOR_MAP = new HashMap<String, String>() {{
        put("starbucks", "Food & Dining");
        put("zomato", "Food & Dining");
        put("swiggy", "Food & Dining");
        put("uber", "Transport");
        put("ola", "Transport");
        put("netflix", "Entertainment");
        put("amazon", "Shopping");
        put("petrol", "Transport");
    }};

    // --- LAYER 2: Keyword Clusters ---
    private static final String[] FOOD_KEYS = {"rice", "tea", "coffee", "burger", "pizza", "lunch", "dinner", "juice"};
    private static final String[] TRAVEL_KEYS = {"fuel", "auto", "metro", "bus", "parking", "cab"};

    /**
     * Main entry point for the 3-layer logic.
     * Returns a Result object containing Amount and Category.
     */
    public static ParseResult process(String note) {
        ParseResult result = new ParseResult();
        String input = note.toLowerCase().trim();

        // 1. Always extract the amount first using Regex
        result.amount = extractAmount(input);

        // 2. Try Layer 1: Vendor Match
        result.category = getLocalMatch(input);

        // 3. Try Layer 2: Keyword Match (if Layer 1 failed)
        if (result.category == null) {
            result.category = getKeywordMatch(input);
        }

        // If result.category is still null, the Activity will trigger Layer 3 (Cloud AI)
        return result;
    }

    private static String getLocalMatch(String input) {
        for (String key : VENDOR_MAP.keySet()) {
            if (input.contains(key)) return VENDOR_MAP.get(key);
        }
        return null;
    }

    private static String getKeywordMatch(String input) {
        for (String key : FOOD_KEYS) {
            if (input.contains(key)) return "Food & Dining";
        }
        for (String key : TRAVEL_KEYS) {
            if (input.contains(key)) return "Transport";
        }
        return null;
    }

    private static double extractAmount(String input) {
        Pattern p = Pattern.compile("(\\d+(\\.\\d+)?)");
        Matcher m = p.matcher(input);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return 0.0;
    }

    // Helper class to return multiple values
    public static class ParseResult {
        public double amount = 0.0;
        public String category = null;
    }
}