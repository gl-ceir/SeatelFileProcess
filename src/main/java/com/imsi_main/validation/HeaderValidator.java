package com.imsi_main.validation;
import java.io.*;
import java.util.logging.Logger;

public class HeaderValidator {

    private static final Logger logger = Logger.getLogger(HeaderValidator.class.getName());

    public static boolean validateHeaders(File file, String expectedHeaders) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String headerLine = br.readLine();
            if (headerLine != null) {
                if (headerLine.equals(expectedHeaders)) {
                    return true;
                } else {
                    logger.severe("Headers do not match expected headers in file: " + file.getName());
                    return false;
                }
            } else {
                logger.severe("File is empty or headers are missing: " + file.getName());
                return false;
            }
        } catch (IOException e) {
            logger.severe("Failed to read file: " + file.getName() + " - " + e.getMessage());
            return false;
        }
    }
}
