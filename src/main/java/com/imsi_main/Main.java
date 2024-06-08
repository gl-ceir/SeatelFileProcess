package com.imsi_main;

import com.imsi_main.fileProcessor.FileProcessor;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("/home/braj/Goldilocks/application.properties")) {
            properties.load(input);
        } catch (IOException e) {
            logger.severe("Failed to load properties file: " + e.getMessage());
            return;
        }

        FileProcessor fileProcessor = new FileProcessor();
        fileProcessor.processFiles(properties);
    }
}
