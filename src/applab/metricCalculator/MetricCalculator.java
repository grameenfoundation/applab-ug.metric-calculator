package applab.metricCalculator;

import java.rmi.RemoteException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.xml.rpc.ServiceException;

import com.sforce.soap.enterprise.sobject.M_E_Metric_Data__c;

/**
 * Main class for the metric calculator
 * Deals with parsing cmd line args and setting up the dashboards to calculate
 *
 * Copyright (C) 2012 Grameen Foundation
 */

public class MetricCalculator {

    private int partnerId = -1;
    private String partnerName;
    private int dashboardId = -1;
    private String dashboardName;
    private int saveAttempts;
    private String filePath;

    private Boolean calculateAll = false;
    private Boolean activeOnly = true;

    private ArrayList<Dashboard> dashboards;
    private Integer quarterModifier;

    /**
     * Main method
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            new MetricCalculator(args).calculate();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (ServiceException e) {
            e.printStackTrace();
        }
    }

    /**
     * Constructor - Parses the cmd line args and sets up the singleton classes
     * @param args
     */
    public MetricCalculator(String[] args) {

        for (int i = 0; args.length > 0; i++) {
            if (args[i].equalsIgnoreCase("help")) {
                printUsage();
                System.exit(1);
            }
            else if (args[i].equalsIgnoreCase("all")) {
                this.calculateAll = true;
            }
            else if (args[i].equalsIgnoreCase("allowInactive")) {
                this.activeOnly = false;
            }
            else {
                System.out.println("The argument " + args[i] + " is invalid. See usage below");
                printUsage();
                System.exit(1);
            }
        }

        // Set up the singleton classes
        InterviewerMap.init();
        Configuration.init();
        parseParameters();
        if (this.filePath != null) {
            Configuration.setFilePath(this.filePath);
        }
        try {
            Configuration.parseConfig();
        } catch (Exception e) {
            System.out.println("Failed to parse configuration");
            e.printStackTrace();
            System.exit(-1);
        }
        try {
            DatabaseHelpers.createConnection(Configuration.getConfiguration("databaseURL", ""),
                    Configuration.getConfiguration("databaseUsername", ""),
                    Configuration.getConfiguration("databasePassword", "")
            );
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        // Set the quarter modifier if there is one
        if (this.quarterModifier != 0) {
            InterviewerMap.setQuarterModifier(this.quarterModifier);
        }
        this.dashboards = new ArrayList<Dashboard>();
        this.saveAttempts = 0;
    }

    /**
     * The workhorse method for this app. Calculates the metrics for the parameters given
     */
    public void calculate() throws RemoteException, SQLException, ServiceException {


        // If we are calculating all the dashboards then ignore the system properties
        if (calculateAll) {
                calculateAllDashboards();
        }
        else {

            // Call the calculate methods in order of priority
            if (this.dashboardId > -1) {
                calculateDashboard(this.dashboardId);
            }
            else if (this.dashboardName != null) {
                this.dashboardId = DatabaseHelpers.getDashboardIdFromName(this.dashboardName, this.activeOnly);
                if (this.dashboardId != -1) {
                    calculateDashboard(this.dashboardId);
                }
            }
            else if (this.partnerId > -1) {
                calculatePartnerDashboards(this.partnerId);
            }
            else if (this.partnerName != null) {
                this.partnerId = DatabaseHelpers.getPartnerId(this.partnerName, this.activeOnly);
                if (this.partnerId != -1) {
                    calculatePartnerDashboards(this.partnerId);
                }
            }
            else {

                // Should not get here as the parameter parsing should have dealt with this but just in case
                System.out.println("You have not entered any valid properties. Please see usage below.");
                printUsage();
                System.exit(-1);
            }
        }
        if (this.dashboards.isEmpty()) {

            // No metrics require updating
            System.out.println("No metrics require updating from the parameters you entered");
            printUsage();
            System.exit(-1);
        }

        // Dig out all the metric datas from the dashboards
        ArrayList<M_E_Metric_Data__c> datas = new ArrayList<M_E_Metric_Data__c>();
        for (Dashboard dashboard : this.dashboards) {
            datas.addAll(dashboard.getDatas());
        }

        // Upload the metrics to Salesforce
        uploadMetricDatas(datas);
    }

    /**
     * Calculate all the possible dashboards
     */
    private void calculateAllDashboards() throws SQLException, RemoteException, ServiceException {

        ResultSet resultSet = DatabaseHelpers.executeSelectQuery(getAllDashboardsQueryString());
        while (resultSet.next()) {
            calculateDashboard(resultSet.getInt("id"));
        }
    }

    private String getAllDashboardsQueryString() {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("id");
        commandText.append("FROM ");
        commandText.append(DatabaseHelpers.DASHBOARD_TABLE);
        if (this.activeOnly) {
            commandText.append(" WHERE ");
            commandText.append("active = 'Y'");
        }
        return commandText.toString();
    }

    private void calculateDashboard(Integer dashboardId) throws SQLException, RemoteException, ServiceException {

        Dashboard dashboard = new Dashboard(dashboardId, this.activeOnly);
        if (dashboard.loadParameters()){
            if (dashboard.calculateDashboard()) {
                this.dashboards.add(dashboard);
            }
        }
    }

    private void calculatePartnerDashboards(Integer partnerId) throws SQLException, RemoteException, ServiceException {

        ResultSet resultSet = DatabaseHelpers.executeSelectQuery(getAllPartnerDashboardsQueryString());
        while (resultSet.next()) {
            calculateDashboard(resultSet.getInt("dashboardId"));
        }
    }

    private String getAllPartnerDashboardsQueryString() {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("d.id as dashboardId ");
        commandText.append("FROM ");
        commandText.append(DatabaseHelpers.DASHBOARD_TABLE + " d, ");
        commandText.append(DatabaseHelpers.PARTNER_TABLE + " p ");
        commandText.append("WHERE ");
        commandText.append("p.id = " + this.partnerId);
        return commandText.toString();
    }

    private void parseParameters() {

        // Parse the command line arguments. These are passed in as properties
        String partnerId = System.getProperty("partnerId");
        if (partnerId != null) {

            // Try to parse the property to an integer
            try {
                this.partnerId = Integer.valueOf(partnerId);
            }
            catch (NumberFormatException e) {
                 System.out.println("Partner Id Parameter should be a valid positive integer. You entered " + partnerId);
                 System.exit(-1);
            }
        }
        String dashboardId = System.getProperty("dashboardId");
        if (dashboardId != null) {

            // Try to parse the property to an integer
            try {
                this.dashboardId = Integer.valueOf(dashboardId);
            }
            catch (NumberFormatException e) {
                 System.out.println("Dashboard Id Parameter should be a valid positive integer. You entered " + dashboardId);
                 System.exit(-1);
            }
        }

        String quarterModifier = System.getProperty("quarterModifier");
        if (quarterModifier != null) {

            // Try to parse the property to an integer
            try {
                this.quarterModifier = Integer.valueOf(quarterModifier);
            }
            catch (NumberFormatException e) {
                 System.out.println("Quarter Modifier Parameter should be a valid positive integer. You entered " + dashboardId);
                 System.exit(-1);
            }
        }
        else {
            this.quarterModifier = 0;
        }
        this.partnerName = System.getProperty("partnerName");
        this.dashboardName = System.getProperty("dashboardName");
        this.filePath = System.getProperty("configFile");

        // Check that any of the required properties have been passed in 
        if (!(this.partnerId > -1)  && this.partnerName == null && !(this.dashboardId > -1) && this.dashboardName == null) {
            System.out.println("You have not entered any of the required properties.");
            printUsage();
            System.exit(-1);
        }
    }

    /**
     * Print the usage message to the console 
     */
    public void printUsage() {

        System.out.println("Format is java <-DpartnerId=1> <-DpartnerName=partName> <-DdashboardId=5> <-DdashboardName=dashName> <-quarterModifier=n> MetricCalculator <help> <all> <allowInactive>");
        System.out.println("You must include atleast one of the properties.");
        System.out.println("Dashboard name or Id will override partner name or Id");
        System.out.println("Id will override name");
        System.out.println("quarterModifier - How many quarters back the figures need to be calculated for");
        System.out.println("Options:-");
        System.out.println("help          - Prints usage");
        System.out.println("all           - Calculates all the dashboards");
        System.out.println("allowInactive - Calculate dashboards and parameters that have been set to inactive");
    }

    public void uploadMetricDatas(ArrayList<M_E_Metric_Data__c> datas) throws RemoteException {

        if (datas.size() == 0 || this.saveAttempts > 4) { 
            return;
        }
        ArrayList<M_E_Metric_Data__c> batch = new ArrayList<M_E_Metric_Data__c>();
        ArrayList<M_E_Metric_Data__c> failedDatas = new ArrayList<M_E_Metric_Data__c>();

        // Salesforce only allows objects to be pushed up in batches of 200
        for (int i = 0; i < datas.size(); i++) {
            batch.add(datas.get(i));
            if ((i % 200) == 0) {
                failedDatas.addAll(SalesforceProxy.saveDatasToSalesforce(batch));
                batch.clear();
            }
        }
        failedDatas.addAll(SalesforceProxy.saveDatasToSalesforce(batch));
        batch.clear();
        this.saveAttempts++;
        uploadMetricDatas(failedDatas);
    }

}