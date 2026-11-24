package stockbuddy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Reads simple CSV files (like the ones Stock Rover exports), keyed by ticker symbol. */
public final class Csv {
    private Csv() {}

    public static Map<String, Map<String, String>> readByTicker(Path file) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        if (!Files.exists(file)) return out;
        try {
            List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty()) return out;
            List<String> head = split(lines.get(0).replace("﻿", ""));
            for (int i = 0; i < head.size(); i++) head.set(i, head.get(i).trim().toLowerCase());
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).trim().isEmpty()) continue;
                List<String> cells = split(lines.get(i));
                Map<String, String> row = new HashMap<>();
                for (int c = 0; c < head.size() && c < cells.size(); c++) row.put(head.get(c), cells.get(c).trim());
                String t = text(row, "ticker", "symbol");
                if (t != null && !t.trim().isEmpty()) out.put(t.toUpperCase().replace("$", ""), row);
            }
        } catch (Exception e) {
            System.out.println("  Could not read " + file + ": " + e.getMessage());
        }
        return out;
    }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (char ch : line.toCharArray()) {
            if (ch == '"') q = !q;
            else if (ch == ',' && !q) { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(ch);
        }
        out.add(cur.toString());
        return out;
    }

    public static String text(Map<String, String> row, String... names) {
        for (String n : names) if (row.containsKey(n)) return row.get(n);
        return null;
    }

    public static double num(Map<String, String> row, double def, String... names) {
        String v = text(row, names);
        if (v == null) return def;
        try { return Double.parseDouble(v.replaceAll("[$,%\\s]", "")); } catch (NumberFormatException e) { return def; }
    }
}
