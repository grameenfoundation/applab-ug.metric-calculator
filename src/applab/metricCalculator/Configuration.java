package applab.metricCalculator;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Class to access the configuration file.
 * Parses XML file to HashMap and then provides access methods.
 * Singleton class as should use same configuration throughout each instance of the app
 *
 * XML look like:
 * <?xml version="1.0"?>
 * <configuration>
 *     <configItem>
 *         <configName>databaseURL</configName>
 *         <configValue>jdbc:mysql://localhost:3306/zebra</configValue>
 *     </configItem>
 *     .
 *     .
 *     .
 *     <configItem>
 *         <configName>databasePassword</configName>
 *         <configValue></configValue>
 *     </configItem>
 * </configuration>
 * 
 * Copyright (C) 2012 Grameen Foundation
 */
public class Configuration {

    private static Configuration singletonValue;

    // Default path to the configuration file
    private static final String defaultFilePath = "../conf/configuration.xml";

    // Allows a different filepath to be used for a custom config
    private String filePath;

    private HashMap<String, String> configurationMap;

    /**
     * Empty constructor as the work is done in the init proc
     */
    public Configuration() {
    }

    /**
     * Create the singleton value and add default settings
     */
    public static void init() {

        Configuration config =  new Configuration();
        config.configurationMap = new HashMap<String, String>();
        config.filePath = defaultFilePath;
        singletonValue = config;
    }

    /**
     * Change path to config file. Must be called before parseConfig to have any affect
     *
     * @param filePath - File path to config file
     */
    public static void setFilePath(String filePath) {
        singletonValue.filePath = filePath;
    }

    /**
     * Initiate the parsing of the config file.
     * Reads in file and normalises it
     */
    public static void parseConfig() throws ParserConfigurationException, SAXException, IOException {

        File xmlFile = new File(singletonValue.filePath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document xmlDocument = dBuilder.parse(xmlFile);
        xmlDocument.getDocumentElement().normalize();
        Element rootNode = xmlDocument.getDocumentElement();
        parseConfigXml(rootNode);
    }

    /**
     * Identifies each config item element
     */
    private static void parseConfigXml(Element rootElement) {

        for (Node childNode = rootElement.getFirstChild(); childNode != null; childNode = childNode.getNextSibling()) {
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals("configItem")) {
                    parseConfigItem((Element)childNode);
                }
            }
        }
    }

    /**
     * Parses each configItem element into its configName and configValue parts
     * Adds config name value pair to config map
     *
     * @param configElement - The configItem element to be parsed 
     */
    private static void parseConfigItem(Element configElement) {

        String configName = null;
        String configValue = null;
        for (Node childNode = configElement.getFirstChild(); childNode != null; childNode = childNode.getNextSibling()) {
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals("configName")) {
                    configName = parseCharacterData((Element) childNode);
                }
                if (childNode.getNodeName().equals("configValue")) {
                    configValue = parseCharacterData((Element) childNode);
                }
            }
        }
        if (configName != null && configValue != null) {
            singletonValue.configurationMap.put(configName, configValue);
        }
    }

    private static String parseCharacterData(Element element) {
        Node child = element.getFirstChild();
        if (child instanceof CharacterData) {
            return ((CharacterData)child).getData();
        }
        return null;
    }

    /**
     * Retrieve a config value
     *
     * @param name         - The name of the config value required
     * @param defaultValue - value to be returned if config value is missing
     *
     * @return - The config value matching the name or the default value if value is missing
     */
    public static String getConfiguration(String name, String defaultValue) {

        if (singletonValue == null) {
            init();
        }
        if (singletonValue.configurationMap.containsKey(name)) {
            return singletonValue.configurationMap.get(name);
        }
        return defaultValue;
    }
}
