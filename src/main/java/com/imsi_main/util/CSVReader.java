package com.imsi_main.util;

import java.io.*;
import java.util.*;

public class CSVReader {
    public static List<String[]> readCSV(File file) throws IOException {
        List<String[]> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.split(","));
            }
        }
        return lines;
    }
}
