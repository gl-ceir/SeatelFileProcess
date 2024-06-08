package com.imsi_main.validation;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Logger;

public class FileValidator {
    private static final Logger logger = Logger.getLogger(FileValidator.class.getName());
    private Properties properties;

    public FileValidator(Properties properties) {
        this.properties = properties;
    }

    public boolean validateHeaders(File file, String expectedHeader) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine();
            return expectedHeader.equals(header);
        } catch (IOException e) {
            logger.severe("Failed to read file: " + file.getName() + " (" + e.getMessage() + ")");
            return false;
        }
    }

    public boolean validateContents(File file, String msisdnColumn, String imsiColumn, String dateColumn) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine();
            String[] columns = header.split(properties.getProperty("file.separator"));

            int msisdnIndex = getIndex(columns, msisdnColumn);
            int imsiIndex = getIndex(columns, imsiColumn);
            int dateIndex = getIndex(columns, dateColumn);

            if (msisdnIndex == -1 || imsiIndex == -1 || dateIndex == -1) {
                return false;
            }

            Set<String> msisdnSet = new HashSet<>();
            Set<String> imsiSet = new HashSet<>();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(properties.getProperty("file.separator"));

                if (values[msisdnIndex].isEmpty() || values[imsiIndex].isEmpty()) {
                    return false;
                }

                if (!msisdnSet.add(values[msisdnIndex]) || !imsiSet.add(values[imsiIndex])) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            logger.severe("Failed to read file: " + file.getName() + " (" + e.getMessage() + ")");
            return false;
        }
    }

    private int getIndex(String[] columns, String column) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    public boolean validateContents(File file, String msisdnColumn, String dateColumn) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine();
            String[] columns = header.split(properties.getProperty("file.separator"));

            int msisdnIndex = getIndex(columns, msisdnColumn);
            int dateIndex = getIndex(columns, dateColumn);

            if (msisdnIndex == -1 || dateIndex == -1) {
                return false;
            }

            Set<String> msisdnSet = new HashSet<>();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(properties.getProperty("file.separator"));

                if (values[msisdnIndex].isEmpty()) {
                    return false;
                }

                if (!msisdnSet.add(values[msisdnIndex])) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            logger.severe("Failed to read file: " + file.getName() + " (" + e.getMessage() + ")");
            return false;
        }
    }

    public boolean validateAddFile(File addFile) {
        return validateHeaders(addFile, properties.getProperty("header.addFile")) &&
                validateContents(addFile, properties.getProperty("addFilePath.msisdn"), properties.getProperty("addFilePath.imsi"), properties.getProperty("addFilePath.created_date"));
    }

    public void moveFileToCorruptFolder(File file) {
        Path sourcePath = file.toPath();
        Path targetPath = Paths.get(properties.getProperty("fileCorruptPath"), file.getName());
        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("File moved to corrupt folder: " + targetPath);
        } catch (IOException e) {
            logger.severe("Failed to move file to corrupt folder: " + e.getMessage());
        }
    }

    public void moveFileToCorruptFolder(String filePath) {
        Path sourcePath = Paths.get(filePath);
        Path targetPath = Paths.get(properties.getProperty("fileCorruptPath"), sourcePath.getFileName().toString());
        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("File moved to corrupt folder: " + targetPath);
        } catch (IOException e) {
            logger.severe("Failed to move file to corrupt folder: " + e.getMessage());
        }
    }
}
