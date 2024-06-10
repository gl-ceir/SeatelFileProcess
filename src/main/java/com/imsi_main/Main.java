package com.imsi_main;

import com.imsi_main.fileProcessor.FileProcessor;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.logging.Logger;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        if (args.length < 2) {
            logger.severe("Usage: java -jar your-jar-file.jar <> <db-properties-file-path>");
            return;
        }

        String processPropertiesPath = args[0];
        String dbPropertiesPath = args[1];

        Properties processProperties = new Properties();
        Properties dbProperties = new Properties();

        try (FileInputStream processInput = new FileInputStream(processPropertiesPath);
             FileInputStream dbInput = new FileInputStream(dbPropertiesPath)) {

            processProperties.load(processInput);
            dbProperties.load(dbInput);

            // Decrypt the database password
            String encryptedPassword = dbProperties.getProperty("spring.datasource.password");
            String decryptedPassword = getPassword(processProperties.getProperty("passwordDecryptor"), encryptedPassword);

            // Replace the encrypted password with the decrypted password
            dbProperties.setProperty("spring.datasource.password", decryptedPassword);

        } catch (IOException e) {
            logger.severe("Failed to load properties file: " + e.getMessage());
            return;
        }

        FileProcessor fileProcessor = new FileProcessor();
        fileProcessor.processFiles(processProperties, dbProperties);
    }

    private static String getPassword(final String passwordDecryptor,final String encryptedPassword) {
        String passwordDecryptorNew = passwordDecryptor.replace("${APP_HOME}", System.getenv("APP_HOME"));
        logger.info("Decrypting Password");
        String line;
        String response = null;
        try {
            String cmd = "java -jar " + passwordDecryptorNew + " "+encryptedPassword;
            logger.fine("cmd to run: " + cmd);
            Process pro = Runtime.getRuntime().exec(cmd);
            BufferedReader in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            while ((line = in.readLine()) != null) {
                response = line;
            }
            return response;
        } catch (Exception e) {
            logger.severe("Error in getPassword: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
