package applab.metricCalculator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * Class to deal with database interactions
 *
 * Copyright (C) 2012 Grameen Foundation
 */
public class DatabaseHelpers {

    final static String JDBC_DRIVER = "com.mysql.jdbc.Driver";

    final static String SUBMISSION_ANSWERS_TABLE = "answers";
    final static String PARTNER_TABLE = "partner";
    final static String DASHBOARD_TABLE = "dashboard";
    final static String METRIC_PARAMETER_TABLE = "metricparameter";
    final static String SURVEY_TABLE = "zebrasurvey";
    final static String SUBMISSION_TABLE = "zebrasurveysubmissions";
    final static String DASHBOARD_SURVEY_TABLE = "dashboardsurvey";
    private static Connection connection;

    /**
     * Constructor will create a connection to the DB.
     * This will need to be improved to use connection pooling and non-persistent connections
     *
     * @param databaseName - The name of the database being used
     * @param username     - The username for the database
     * @param password     -  The password for the database
     */
    public static void createConnection (
            String url,
            String username,
            String password
    ) throws ClassNotFoundException, SQLException {

        // Make sure the JDBC driver is loaded into memory
        Class.forName(JDBC_DRIVER);
        try {
            connection = DriverManager.getConnection(url, username, password);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void closeConnection() throws SQLException {
        connection.close();
    }

    public static Boolean checkConnection() throws SQLException {
        return connection.isClosed();
    }

    public static PreparedStatement getPreparedStatement(String query) throws SQLException {
        return connection.prepareStatement(query);
    }

    /**
     * Execute a select statement.
     * 
     * @param query - The query string for the select statement
     *
     * @return - The result set
     */
    public static ResultSet executeSelectQuery(PreparedStatement statement) throws SQLException {
        statement.executeQuery();
        return statement.getResultSet();
    }

    /**
     * Executes a select query that returns a ResultSet
     * 
     * @param query
     * @return
     * @throws SQLException
     */
    public static ResultSet executeSelectQuery(String query) throws SQLException {
        Statement selectStatement = connection.createStatement();
        selectStatement.executeQuery(query);
        return selectStatement.getResultSet();
    }

    /**
     * Get the number of rows that are in the result set.
     * Returns the result set with the cursor on the first row.
     *
     * @param rs - The result set
     *
     * @return - The number of rows in the result set
     */
    public static int getNumberOfRows(ResultSet resultSet) {

        int totalRow = 0;
        try {
            boolean hasRows = resultSet.last();

            // Do we have any rows in the result set
            if (hasRows) {
                totalRow = resultSet.getRow();
                resultSet.first();
            }
        }
        catch (Exception e) {
            return totalRow;
        }
        return totalRow;
    }

    /**
     * Get the id for a Dashboard from the name.
     *
     * @param dashboardName - Name of the dashboard
     * @param activeOnly    - Only allow a returned id if the dashboard is active
     *
     * @return The id for the dashboard
     */
    public static int getDashboardIdFromName(String dashboardName, Boolean activeOnly) throws SQLException {

        PreparedStatement statement = connection.prepareStatement(getDashboardIdQueryString(activeOnly));
        statement.setString(1, dashboardName);
        ResultSet rs = executeSelectQuery(statement);

        // Check that we have the expected number of rows
        int rowCount = getNumberOfRows(rs);
        if (rowCount != 1) {
            return -1;
        }
        int id = rs.getInt("id");
        statement.close();
        return id;
    }

    /**
     * Get the id for a partner from the partner name
     *
     * @param partnerName - The name of the partner
     * @param activeOnly  - Only interested in active partners
     *
     * @return - The unique id for the partner from the DB
     */
    public static int getPartnerId(String partnerName, Boolean activeOnly) throws SQLException {

        PreparedStatement statement = connection.prepareStatement(getPartnerIdQueryString(activeOnly));
        statement.setString(1, partnerName);
        ResultSet rs = executeSelectQuery(statement);

        // Check that we have the expected number of rows
        int rowCount = getNumberOfRows(rs);
        if (rowCount != 1) {
            // TODO - Handle this
        }
        int id = rs.getInt("id");
        statement.close();
        return id;
    }

    /**
     * Generate the query string for getting the dashboard id from the name
     *
     * @param activeOnly - Only allow active dashboards
     *
     * @return - The query string
     */
    public static String getDashboardIdQueryString(Boolean activeOnly) {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("id ");
        commandText.append("FROM ");
        commandText.append(DASHBOARD_TABLE);
        commandText.append(" WHERE ");
        commandText.append("name = ?");
        if (activeOnly) {
            commandText.append(" AND active = 'Y'");
        }
        return commandText.toString();
    }

    /**
     * Get the partner name for a given dashboard
     *
     * @param dashboardId - The id for the dashboard
     *
     * @return - The partner name as it is in Salesforce
     * @throws SQLException 
     */
    public static String getPartnerName(int dashboardId) throws SQLException {

        ResultSet rs = executeSelectQuery(getPartnerNameQuery(dashboardId));

        // Check that we have the expected number of rows
        int rowCount = getNumberOfRows(rs);
        if (rowCount != 1) {
            // TODO - Handle this
        }
        String name = rs.getString("name");
        rs.close();
        return name;
    }

    /**
     * Get the query String to get the partner id
     *
     * @param activeOnly  - Boolean to indicate if the partner must be active
     *
     * @return - Query String
     */
    public static String getPartnerIdQueryString(Boolean activeOnly) {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("id ");
        commandText.append("FROM ");
        commandText.append(PARTNER_TABLE);
        commandText.append(" WHERE ");
        commandText.append("name = ?");
        if (activeOnly) {
            commandText.append(" AND active = 'Y'");
        }
        return commandText.toString();
    }

    /**
     * Build the query string to get the partner name
     *
     * @param dashboardId
     *
     * @return - The query string
     */
    private static String getPartnerNameQuery(int dashboardId) {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("p.name ");
        commandText.append("FROM ");
        commandText.append(PARTNER_TABLE + " p, ");
        commandText.append(DASHBOARD_TABLE + " d ");
        commandText.append("WHERE" );
        commandText.append("d.id = " + dashboardId + " ");
        commandText.append("d.partner_id = p.id");
        return commandText.toString();
    }

    /**
     * Get the Dashboard ids for a give partner
     *
     * @param partnerId  - The partner id
     * @param activeOnly - Only get active dashboards
     * @return - An arrayList of ids. List is empty if there are no available dashboards
     */
    public static ArrayList<Integer> getDashboardIds(int partnerId, Boolean activeOnly) throws SQLException {

        ArrayList<Integer> ids = new ArrayList<Integer>();
        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("id ");
        commandText.append("FROM ");
        commandText.append(DASHBOARD_TABLE);
        commandText.append(" WHERE ");
        commandText.append("partner_id = ");
        commandText.append(Integer.toString(partnerId));
        if (activeOnly) {
            commandText.append(" AND active = 'Y'");
        }
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(commandText.toString());

        // Loop through the record set to generate the array
        while (rs.next()) {
            ids.add(rs.getInt("id"));
        }
        statement.close();
        return ids;
    }

    /**
     * Generate the query string to get the metric parameters for a given dashboard
     *
     * @param dashboardId - Id for the dashboard
     * @param activeOnly  - Allow only non active dashboards
     *
     * @return - The query string
     */
    public static String getMetricParametersQueryString(int dashboardId, Boolean activeOnly) {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("p.name as partnerName, ");
        commandText.append("p.short_code as partnerShortCode, ");
        commandText.append("d.id as dashboardId, ");
        commandText.append("d.include_interviewer_gender as includeInterviewerGender, ");
        commandText.append("d.include_survey_count as includeSurveyCount,");
        commandText.append("mp.id as parameterId, ");
        commandText.append("mp.survey_id as surveyId, ");
        commandText.append("mp.name as name, ");
        commandText.append("mp.binding as binding, ");
        commandText.append("mp.question_type as questionType, ");
        commandText.append("mp.calculation_type as calculationType, ");
        commandText.append("mp.select_options as selectOptions, ");
        commandText.append("mp.group_by_field as groupByField, ");
        commandText.append("mp.lickert as lickert, ");
        commandText.append("mp.is_repeat as isRepeat, ");
        commandText.append("mp.only_answered_surveys as onlyAnsweredSurveys ");
        commandText.append("FROM ");
        commandText.append(PARTNER_TABLE + " p, ");
        commandText.append(METRIC_PARAMETER_TABLE + " mp, ");
        commandText.append(DASHBOARD_TABLE + " d ");
        commandText.append(" WHERE ");
        commandText.append("d.id = ");
        commandText.append(Integer.toString(dashboardId));
        commandText.append(" AND p.id = d.partner_id ");
        commandText.append("AND d.id = mp.dashboard_id ");
        if (activeOnly) {
            commandText.append("AND d.active = 'Y'");
            commandText.append("AND mp.active = 'Y'");
        }
        return commandText.toString();
    }

    /**
     * Get String to get the dates to only get this quarters data
     *
     * @return - The string
     */
    public static String getQuarterStartEndParameter() {

        StringBuilder commandText = new StringBuilder();
        commandText.append(" AND s.handset_submit_time >= '");
        commandText.append(Utils.getQuarterDate(InterviewerMap.getTime(), true, false, false) + "' ");
        commandText.append("AND s.handset_submit_time <= '");
        commandText.append(Utils.getQuarterDate(InterviewerMap.getTime(), false, false, false) + "' ");
        return commandText.toString();
    }
}