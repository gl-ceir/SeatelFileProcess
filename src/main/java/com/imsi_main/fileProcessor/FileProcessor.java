package com.imsi_main.fileProcessor;

import com.imsi_main.util.CSVReader;
import com.imsi_main.validation.FileValidator;
import com.imsi_main.database.Database;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileProcessor {

    private static final Logger logger = Logger.getLogger(FileProcessor.class.getName());
    private FileValidator fileValidator;
    private Database database;

    public void processFiles(Properties processProperties, Properties dbProperties) {
        fileValidator = new FileValidator(processProperties);
        database = new Database(dbProperties.getProperty("spring.datasource.url"),
                dbProperties.getProperty("spring.datasource.username"),
                dbProperties.getProperty("spring.datasource.password"));
        logger.info("Program Started");

        try {
            while (true) {
                File addFile = getFirstFile(processProperties.getProperty("addFilePath"), processProperties.getProperty("addFileNamePrefix"));
                File delFile = getFirstFile(processProperties.getProperty("delFilePath"), processProperties.getProperty("delFileNamePrefix"));

                if (addFile == null || delFile == null) {
                    logger.info("No more files to process.");
                    break;
                }

                if (!validateDate(addFile, delFile)) {
                    logger.info("Date validation failed.");
                    updateAuditTrail(processProperties, "501", "FAIL", "Date validation failed.");
                    moveFileToCorruptFolder(addFile.getPath(), processProperties.getProperty("fileCorruptPath"));
                    moveFileToCorruptFolder(delFile.getPath(), processProperties.getProperty("fileCorruptPath"));
                    continue;
                }

                try {
                    logger.info("Processing SIM Change and HLR Deactivation");
                    processSimChangeAndHlrDeactivation(addFile, delFile, processProperties);
                } catch (IOException e) {
                    logger.severe("Failed to process files: " + e.getMessage());
                }

                moveFileToProcessedFolder(addFile.getPath(), processProperties.getProperty("fileProcessedPath"));
                moveFileToProcessedFolder(delFile.getPath(), processProperties.getProperty("fileProcessedPath"));
            }
        } finally {
            database.close();
        }

        logger.info("Program Finished");
    }


    private File getFirstFile(String directoryPath, String filePrefix) {
        logger.info("Getting the first file");
        File dir = new File(directoryPath);
        File[] files = dir.listFiles((d, name) -> name.startsWith(filePrefix));
        if (files == null || files.length == 0) {
            return null;
        }

        TreeMap<String, File> fileMap = new TreeMap<>();
        for (File file : files) {
            String dateStr = extractDateFromFilename(file.getName());
            if (dateStr != null) {
                fileMap.put(dateStr, file);
            }
        }

        return fileMap.firstEntry().getValue();
    }

    private boolean validateDate(File addFile, File delFile) {
        logger.info("Validating dates");
        String addFileName = addFile.getName();
        String delFileName = delFile.getName();

        String addFileDateStr = extractDateFromFilename(addFileName);
        String delFileDateStr = extractDateFromFilename(delFileName);

        if (addFileDateStr == null || delFileDateStr == null || !addFileDateStr.equals(delFileDateStr)) {
            logger.severe("Date in file names does not match: Add File Date - " + addFileDateStr + ", Del File Date - " + delFileDateStr);
            return false;
        }

        return true;
    }

    private String extractDateFromFilename(String fileName) {
        Pattern datePattern = Pattern.compile("(\\d{8})");
        Matcher matcher = datePattern.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void processSimChangeAndHlrDeactivation(File addFile, File delFile, Properties properties) throws IOException {
        logger.info("Processing SIM and HLR Deactivation");
        logger.info("Add File: " + addFile.getPath());
        logger.info("Del File: " + delFile.getPath());

        List<String[]> addFileLines = CSVReader.readCSV(addFile);
        List<String[]> delFileLines = CSVReader.readCSV(delFile);

        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String operator = properties.getProperty("operator");

        String simChangeFilePath = properties.getProperty("simchangeFileDir") + timestamp + "_" + properties.getProperty("simchangeFileName");
        String hlrDeactivationFilePath = properties.getProperty("hlrDeactivationFileDir") + timestamp + "_" + properties.getProperty("hlrDeactivationFileName");
        String addFilePathForHlrDel = properties.getProperty("addFilePathForHlrDelDir") + properties.getProperty("addFilePathForHlrDelName")+"_"+ operator + "_" +date+".csv";
        String addFilePathForHlrAdd = properties.getProperty("addFilePathForHlrAddDir") + properties.getProperty("addFilePathForHlrAddName")+"_"+ operator + "_" +date+".csv";

        try (BufferedWriter simChangeWriter = new BufferedWriter(new FileWriter(simChangeFilePath));
             BufferedWriter hlrDeacWriter = new BufferedWriter(new FileWriter(hlrDeactivationFilePath));
             BufferedWriter addHlrWriter = new BufferedWriter(new FileWriter(addFilePathForHlrAdd));
             BufferedWriter delHlrWriter = new BufferedWriter(new FileWriter(addFilePathForHlrDel));
             BufferedWriter errorWriter = new BufferedWriter(new FileWriter(properties.getProperty("fileCorruptPath") + "/error.csv"))) {

            // Write headers
            hlrDeacWriter.write("Delete Date Time,MSISDN");
            hlrDeacWriter.newLine();
            simChangeWriter.write("newImsi,oldImsi,msisdn,activation_date");
            simChangeWriter.newLine();
            addHlrWriter.write("imsi,msisdn,activation_date");
            addHlrWriter.newLine();
            delHlrWriter.write("imsi,msisdn,activation_date");
            delHlrWriter.newLine();
            errorWriter.write("Status,Message");
            errorWriter.newLine();

            // Write addFile contents to addHlrWriter
            for (String[] line : addFileLines) {
                if (!line[0].equals("CREATED_DATE")) { // Skip header row
                    logger.info("Add Hlr Deactivation");
                    addHlrWriter.write(String.join(",", line[4], line[2], line[0])); // Assuming IMSI, MSISDN, CREATED_DATE
                    addHlrWriter.newLine();
                }
            }

            // Process delFile contents
            for (String[] line : delFileLines) {
                if (!line[0].equals("Customer Account ID")) { // Skip header row
                    String msisdn = line[1];
                    String oldImsi = getImsi(msisdn, addFileLines); // Method to retrieve old IMSI from delFile
                    String newImsi = getNewImsi(msisdn, addFileLines);
                    String delDate = line[8]; // Delete Date Time

                    if (newImsi != null && oldImsi != null) {
                        logger.info("Sim writer");
                        simChangeWriter.write(String.join(",", newImsi, oldImsi, msisdn, delDate));
                        simChangeWriter.newLine();
                    } else {
                        logger.info("HLR deactivation");
                        hlrDeacWriter.write(String.join(",", delDate, msisdn)); // Assuming Delete Date Time, MSISDN
                        hlrDeacWriter.newLine();
                    }
                    if(newImsi !=null){
                        logger.info("del Hlr ");
                        delHlrWriter.write(String.join(",", newImsi, msisdn, delDate));
                        delHlrWriter.newLine();
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("Failed to process files: " + e.getMessage());
            throw e;
        }
    }

    private String getImsi(String msisdn, List<String[]> addFileLines) {
        for (String[] line : addFileLines) {
            if (line.length > 1 && line[1].equals(msisdn)) { // Assuming MSISDN is at index 1
                return line[0];
            }
        }
        return database.getImsi(msisdn);
    }

    private String getNewImsi(String msisdn, List<String[]> addFileLines) {
        for (String[] line : addFileLines) {
            if (line.length > 1 && line[2].equals(msisdn)) { // Assuming MSISDN is at index 1
                return line[4]; // Assuming IMSI is at index 0
            }
        }
        return null;
    }

    private void moveFileToCorruptFolder(String filePath, String corruptFolderPath) {
        Path sourcePath = Paths.get(filePath);
        Path targetPath = Paths.get(corruptFolderPath, sourcePath.getFileName().toString());
        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("File moved to corrupt folder: " + targetPath);
        } catch (IOException e) {
            logger.severe("Failed to move file to corrupt folder: " + e.getMessage());
        }
    }

    private void moveFileToProcessedFolder(String filePath, String processedFolderPath) {
        Path sourcePath = Paths.get(filePath);
        Path targetPath = Paths.get(processedFolderPath, sourcePath.getFileName().toString());
        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("File moved to processed folder: " + targetPath);
        } catch (IOException e) {
            logger.severe("Failed to move file to processed folder: " + e.getMessage());
        }
    }

    private void updateAuditTrail(Properties properties, String statusCode, String status, String message) {
        String auditTrailPath = properties.getProperty("fileCorruptPath") + "/audit_trail.log";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(auditTrailPath, true))) {
            writer.write(String.format("%s,%s,%s,%s,%s%n",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),
                    statusCode,
                    status,
                    message,
                    properties.getProperty("fileProcessedPath")));
        } catch (IOException e) {
            logger.severe("Failed to update audit trail: " + e.getMessage());
        }
    }
}
