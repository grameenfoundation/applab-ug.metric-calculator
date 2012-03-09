package applab.metricCalculator;

import java.rmi.RemoteException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.rpc.ServiceException;

import com.sforce.soap.enterprise.QueryResult;
import com.sforce.soap.enterprise.sobject.M_E_Metric_Data__c;
import com.sforce.soap.enterprise.sobject.M_E_Metric__c;
import com.sforce.soap.enterprise.sobject.SObject;
/**
 * Calculates the metrics for a dashboard from its parameters
 * The parameters are taken from the DB and then calculated against the submissions for the quarter we are in.
 * A Dashboard also has the option to show more generic metrics e.g. total surveys submitted. These metrics are not stroed in the
 * DB as parameters but rather as flags on the Dashboard table.
 * 
 * TODO - Allow subdividers other than District.
 * TODO - Allow for no subDividers. Just raw totals.
 *
 * Copyright (C) 2012 Grameen Foundation
 */

public class Dashboard {

    private int dashboardId = -1;

    // Only consider active Dashboards and Metric Parameters
    private Boolean activeOnly;

    // Calculate the gender split of the interviewers Y|N value
    private String calculateGender;
    
    // Calculate the quantity of surveys submitted Y|N value
    private String calculateSurveyCount;

    // The full name of the partner as used to display
    private String partnerName;

    // The short code of the partner as used as the prefix to all metrics
    private String partnerShortCode;

    // Map of the parameters for this dashboard. Key is metric name
    private HashMap<String, MetricParameter> parameterMap;

    // Map of the calculation that have a subDivider. Key is metricName_subDivder
    private HashMap<String, MetricCalculation> calcualtionMap;

    // Total calculations. Ones that do not have a subDivider. Key is metric name
    private HashMap<String, MetricCalculation> totalCalcualtionMap;

    // Metrics in case we need to create any new metric datas
    private HashMap<String, M_E_Metric__c> metricMap;

    // List of the people ids that conducted interviews for this dashboard
    private ArrayList<String> peopleList;

    // General detail map. Key is subdivider subDivider
    private HashMap<String, GeneralDashboardDetail> totalGeneralDetailMap;

    /**
     * Constructor
     *
     * @param dashboardId - The id in the DB for the dashboard.
     * @param activeOnly  - Only include active metrics.
     */
    public Dashboard(int dashboardId, Boolean activeOnly) {

        this.dashboardId = dashboardId;
        this.activeOnly = activeOnly;
        this.parameterMap = new HashMap<String, MetricParameter>();
        this.calculateGender = "N";
        this.calculateSurveyCount = "N";
        this.metricMap = new HashMap<String, M_E_Metric__c>();
        this.totalCalcualtionMap = new HashMap<String, MetricCalculation>();
    }

    /**
     * Load the parameters form the DB into the parameter map
     *
     * @return - Boolean indicating if there are any parameters to calculate
     */
    public Boolean loadParameters() throws SQLException {

        // There is no dashboard id set so return.
        if (this.dashboardId == -1) {
            System.out.println("Dashboard id is not set");
            return false;
        }

        System.out.println("Starting Dashboard with id: " + this.dashboardId);

        // Get the parameters from the DB.
        ResultSet resultSet = DatabaseHelpers.executeSelectQuery(DatabaseHelpers.getMetricParametersQueryString(this.dashboardId, this.activeOnly));
        if (DatabaseHelpers.getNumberOfRows(resultSet) == 0) {
            System.out.println("There are no valid Metric Parameters for dashboard with ID - " + this.dashboardId);
            return false;
        }

        // Add the general dashboard wide config from the DB
        this.calculateGender = resultSet.getString("includeInterviewerGender");
        this.calculateSurveyCount = resultSet.getString("includeSurveyCount");
        this.partnerName = resultSet.getString("partnerName");
        this.partnerShortCode = resultSet.getString("partnerShortCode");

        // Loop through the record set to generate the map of metrics
        do {
            parameterMap.put(resultSet.getString("name"),
                    new MetricParameter(
                            resultSet.getString("name"),
                            resultSet.getInt("surveyId"),
                            resultSet.getString("binding"),
                            resultSet.getString("questionType"),
                            resultSet.getString("calculationType"),
                            resultSet.getString("selectOptions"),
                            resultSet.getString("groupByField"),
                            resultSet.getInt("lickert"),
                            resultSet.getString("onlyAnsweredSurveys"),
                            resultSet.getString("isRepeat")
                    )
            );
        }
        while (resultSet.next());
        return true;
    }

    /**
     * Calculate all the metrics for the dashboard.
     *
     * @return - Boolean indicating that there are some calculations to upload to SFDC
     */
    public Boolean calculateDashboard() throws SQLException, RemoteException, ServiceException {

        if (this.parameterMap.isEmpty()) {
            System.out.println("");
            return false;
        }

        // Load all the people that have submitted surveys 
        this.peopleList = InterviewerMap.populateMapForDashboard(this.dashboardId);

        // Calculate each metric for this dashboard
        this.calcualtionMap = new HashMap<String, MetricCalculation>();
        for (Entry<String, MetricParameter> entry : this.parameterMap.entrySet()) {
            calculateMetric(entry.getValue());
        }
        if (this.calcualtionMap.isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Calculates and individual Metric Parameter.
     *
     * TODO - Expand to allow for other subDividers and to allow fo no subDividers
     *
     * @param entry - Key = MetricName, Value = MetricParamter
     */
    private void calculateMetric(MetricParameter parameter) throws SQLException {

        String onlyAnsweredSurveys = parameter.getOnlyAnsweredSurveys();
        if (parameter.getBinding() == null) {
            System.out.println("Parameter has no binding so cannot calculate a metric from it");
            return;
        }
        String metricName = parameter.getMetricName();

        // Run the query to get the answers for this metric
        ResultSet answersSet = DatabaseHelpers.executeSelectQuery(parameter.getQueryString());
        if (DatabaseHelpers.getNumberOfRows(answersSet) < 1) {
            return;
        }
        do {

            String lickertName = "";

            // Dig out the subdivider
            String personId = answersSet.getString("interviewer_id").toUpperCase();
            String subDivider = InterviewerMap.getSubDivider(personId);
            if (subDivider == null) {
                continue;
            }

            // Find the correct metricCalculation for this subDivider. Format for a lickert question is <metricName>_<lickertNumberOnsurvey>
            if (parameter.getLickert() > 0) {
                lickertName = "_" + answersSet.getString("answer");
            }
            String metricLabel = Utils.createMetricLabel(metricName + lickertName, subDivider);

            // Get the calculation from the map or make a new one if it does not exisit
            MetricCalculation metricCalculation = this.calcualtionMap.get(metricLabel);
            if (metricCalculation == null) {
                metricCalculation = new MetricCalculation(subDivider, metricName, metricName + lickertName);
            }

            // Update the calculations values
            metricCalculation.setNeedsUpdate(true);
            this.calcualtionMap.put(metricLabel, parameter.updateCalculation(metricCalculation, answersSet.getString("answer")));

            // Add to the parameters total submissions if this metric only uses submissions who answered the question
            if (onlyAnsweredSurveys.equals("Y")) {
                parameter.addToTotalSubmissions(subDivider, 1.0);
                parameter.addToTotalSubmissions("total", 1.0);
            }

            // Sort out the totals
            MetricCalculation total = this.totalCalcualtionMap.get(metricName + lickertName);
            if (total == null) {
                total = new MetricCalculation(subDivider, metricName, metricName + lickertName);
            }

            // Update the calculations values
            total.setNeedsUpdate(true);
            this.totalCalcualtionMap.put(metricName + lickertName, parameter.updateCalculation(total, answersSet.getString("answer")));

        } while (answersSet.next());
    }

    /**
     * Extract the M_E_Metric_Datas from the metric calculation and add to list to be uploaded
     *
     * @return - List of metrics to upload
     */
    public ArrayList<M_E_Metric_Data__c> getDatas() throws SQLException, RemoteException, ServiceException {

        ArrayList<M_E_Metric_Data__c> datas = new ArrayList<M_E_Metric_Data__c>();

        // Get the generic metrics if needed
        if (this.calculateGender.equals("Y")) {
            calculateGenderSplit();
        }

        // Calculate the total survey submissions if required
        if (this.calculateSurveyCount.equals("Y")) {
              calculateSubmissionTotals();
        }

        // Get the metric data from salesforce.
        getMetricDatas();

        for (Entry<String, MetricCalculation> entry : this.calcualtionMap.entrySet()) {

            MetricParameter parameter = this.parameterMap.get(entry.getValue().getParameterName());
            if (parameter == null) {
                continue;
            }
            String subDivider = entry.getValue().getSubDivider();
            Double totalSubDivider;
            if (parameter.getOnlyAnsweredSurveys().equals("I")) {
                GeneralDashboardDetail detail = this.totalGeneralDetailMap.get(subDivider);
                if (detail == null) {
                    totalSubDivider = 0.0;
                }
                else {
                    totalSubDivider = this.totalGeneralDetailMap.get(subDivider).getTotalInterviewer();
                }
            }
            else if (parameter.getOnlyAnsweredSurveys().equals("Y")) {
                totalSubDivider = parameter.getTotalSubmissions(subDivider);
            }
            else {
                totalSubDivider = InterviewerMap.getTotalSubmissions(parameter.getSurveyId(), subDivider);
            }
            M_E_Metric_Data__c data = entry.getValue().getData();
            if (data == null) {
                if (this.metricMap.isEmpty() || !this.metricMap.containsKey(entry.getValue().getMetricName())) {
                    populateMetricMap();
                }
                if (!this.metricMap.containsKey(entry.getValue().getMetricName())) {
                    continue;
                }
                data = entry.getValue().createNewMetricData(
                    parameter,
                    this.metricMap.get(entry.getValue().getMetricName()),
                    InterviewerMap.getDistrictId(subDivider),
                    totalSubDivider
                );
            }
            else {
                if (entry.getValue().getNeedsUpdate()) {
                    entry.getValue().updateMetricDataValue(parameter, totalSubDivider);
                    data = entry.getValue().getData();
                }
            }

            // Only add datas that need updating
            if (entry.getValue().getNeedsUpdate()) {
                datas.add(data);
            }
        }
        datas.addAll(calculateTotals());
        return datas;
    }

    /**
     * Perform the calculation to update the total metrics. These are the ones with no subdivider
     *
     * @return - List of metric datas to upload to SF
     */
    private ArrayList<M_E_Metric_Data__c> calculateTotals() throws RemoteException, ServiceException, SQLException {

        ArrayList<M_E_Metric_Data__c> datas = new ArrayList<M_E_Metric_Data__c>();
        if (this.totalCalcualtionMap.isEmpty()) {
            return datas;
        }

        for (Entry<String, MetricCalculation> entry : this.totalCalcualtionMap.entrySet()) {
System.out.println(entry.getKey());
            M_E_Metric_Data__c data = entry.getValue().getData();
            MetricParameter parameter = this.parameterMap.get(entry.getValue().getParameterName());
            if (parameter == null) {
                continue;
            }

            Double total;
            if (parameter.getOnlyAnsweredSurveys().equals("I")) {
                GeneralDashboardDetail detail = this.totalGeneralDetailMap.get("total");
                if (detail == null) {
                    total = 0.0;
                }
                else {
                    total = this.totalGeneralDetailMap.get("total").getTotalInterviewer();
                }
            }
            else if (parameter.getOnlyAnsweredSurveys().equals("Y")) {
                total = parameter.getTotalSubmissions("total");
            }
            else {
                total = InterviewerMap.getTotalSubmissions(parameter.getSurveyId(), "total");
            }
            if (data == null) {
                if (this.metricMap.isEmpty() || !this.metricMap.containsKey(entry.getValue().getMetricName())) {
                    populateMetricMap();
                }
                if (!this.metricMap.containsKey(entry.getValue().getMetricName())) {
                    continue;
                }
System.out.println("CREATING NEW TOTAL " + entry.getKey());
                data = entry.getValue().createNewMetricData(
                    this.parameterMap.get(entry.getValue().getParameterName()),
                    this.metricMap.get(entry.getValue().getMetricName()),
                    null,
                    total
                );
            }
            else {
                entry.getValue().updateMetricDataValue(this.parameterMap.get(entry.getValue().getParameterName()), total);
                data = entry.getValue().getData();
            }
            datas.add(data);
        }
        return datas;
    }

    /**
     * Calculate the gender split for the interviewers who conducted surveys in this quarter
     */
    private void calculateGenderSplit() {

        this.totalGeneralDetailMap = new HashMap<String, GeneralDashboardDetail>();
        this.totalGeneralDetailMap.put("total", new GeneralDashboardDetail());

        // Create the metric parameter. The name for gender splits needs to follow a convention of <partner_short_code>_<gender>_Interviewer
        String maleParamName = this.partnerShortCode + "_Male_Interviewer";
        this.parameterMap.put(maleParamName, new MetricParameter(
                maleParamName,
                null,
                null,
                "number",
                "percentage",
                null,
                null,
                0,
                "I",
                null
        ));
        String femaleParamName = this.partnerShortCode + "_Female_Interviewer";
        this.parameterMap.put(femaleParamName, new MetricParameter(
                femaleParamName,
                null,
                null,
                "number",
                "percentage",
                null,
                null,
                0,
                "I",
                null
        ));

        // Loop through all the people who have conducted a survey for this dashboard
        for (String personId : this.peopleList) {

            // Get the subDivider for this person
            String subDivider = InterviewerMap.getSubDivider(personId);
            String gender = InterviewerMap.getGender(personId);
            if (gender == null) {
                continue;
            }

            // Add to the general detail map
            if (!this.totalGeneralDetailMap.containsKey(subDivider)) {
                this.totalGeneralDetailMap.put(subDivider, new GeneralDashboardDetail());
            }
            this.totalGeneralDetailMap.get(subDivider).addToTotalInterviewer(1.0);
            this.totalGeneralDetailMap.get("total").addToTotalInterviewer(1.0);

            String metricLabel = this.partnerShortCode + "_" + gender + "_Interviewer_" + subDivider;
            String key;

            // Decide the metric key for this person
            if (gender.equals("Male")) {
                key = maleParamName;
                this.totalGeneralDetailMap.get(subDivider).addToTotalFemaleInterviewer(1.0);
                this.totalGeneralDetailMap.get("total").addToTotalFemaleInterviewer(1.0);
            }
            else {
                key = femaleParamName;
                this.totalGeneralDetailMap.get(subDivider).addToTotalMaleInterviewer(1.0);
                this.totalGeneralDetailMap.get("total").addToTotalMaleInterviewer(1.0);
            }
            MetricParameter parameter = this.parameterMap.get(key);
            MetricCalculation metricCalculation = this.calcualtionMap.get(metricLabel);
            if (metricCalculation == null) {
                metricCalculation = new MetricCalculation(subDivider, key, key);
            }
            metricCalculation.setNeedsUpdate(true);
            this.calcualtionMap.put(metricLabel, parameter.updateCalculation(metricCalculation, "1"));

            // Sort the totals
            MetricCalculation total = this.totalCalcualtionMap.get(this.partnerShortCode + "_" + gender + "_Interviewer");
            if (total == null) {
                total = new MetricCalculation(null, key, key);
            }
            total.setNeedsUpdate(true);
            this.totalCalcualtionMap.put(this.partnerShortCode + "_" + gender + "_Interviewer", parameter.updateCalculation(total, "1"));
        }
    }

    /**
     * Calculate the total submissions for each survey id for this dashboard. Can show surveys that we not used in a calculation
     */
    private void calculateSubmissionTotals() throws SQLException {

        // Get all the surveys that are to be shown in this dashboard
        ResultSet resultSet = DatabaseHelpers.executeSelectQuery(getSurveysForDashboardQuery());
        if (DatabaseHelpers.getNumberOfRows(resultSet) < 1) {
            return;
        }
        do {

            Integer surveyId = resultSet.getInt("surveyId");
            String surveySalesforceName = resultSet.getString("surveySalesforceId");
            InterviewerMap.getTotalSubmissions(surveyId, null);
            Set<String> subDividers = InterviewerMap.getSubmissionKeySet(surveyId.toString());
            if (subDividers == null) {
                continue;
            }

            // Create the metric parameter
            String paramName = this.partnerShortCode + "_total_surveys_" + surveySalesforceName;
            MetricParameter parameter = new MetricParameter(
                    paramName,
                    surveyId,
                    null,
                    "number",
                    "sum",
                    null,
                    null,
                    0,
                    "N",
                    null
            );
            this.parameterMap.put(paramName, parameter);
            Iterator<String> iter = subDividers.iterator();
            while (iter.hasNext()) {
                String subDivider = iter.next();

                if (!subDivider.equals("total")) {
                     String metricLabel = paramName + "_" + subDivider;
                    MetricCalculation metricCalculation = this.calcualtionMap.get(metricLabel);
                    if (metricCalculation == null) {
                        metricCalculation = new MetricCalculation(subDivider, paramName, paramName);
                    }
                    metricCalculation.setNeedsUpdate(true);
                    this.calcualtionMap.put(metricLabel, parameter.updateCalculation(metricCalculation, InterviewerMap.getTotalSubmissions(surveyId, subDivider).toString()));
                }
            }
            MetricCalculation total = this.totalCalcualtionMap.get(paramName);
            if (total == null) {
                total = new MetricCalculation(null, paramName, paramName);
            }
            total.setNeedsUpdate(true);
            this.totalCalcualtionMap.put(paramName, parameter.updateCalculation(total, InterviewerMap.getTotalSubmissions(surveyId, "total").toString()));
        } while (resultSet.next());
    }

    /**
     * Get all the metric objects from salesforce and populate them in a map to be used when creating new metric datas
     */
    private void populateMetricMap() throws RemoteException, ServiceException, SQLException {

        // Get all the metrics that have currently been created in Salesforce
        if (this.partnerName == null) {
            this.partnerName = DatabaseHelpers.getPartnerName(this.dashboardId);
        }
        QueryResult result = SalesforceProxy.getSalesforceObjects(getMetricsSalesforceQueryString(this.partnerName));
        Boolean moreRows = true;
        if (result.getSize() > 0) {
            while (moreRows) {
                SObject[] datas = result.getRecords();
                for (int i = 0; i < datas.length; i++) {
                    M_E_Metric__c metric = (M_E_Metric__c)datas[i];
                    this.metricMap.put(metric.getName(), metric);
                }
                if (result.isDone()) {
                    moreRows = false;
                }
                else {
                    result = SalesforceProxy.getSalesforceObjectsMore(result.getQueryLocator());
                }
            }
        }
    }

    /**
     * Get all the metric datas for this dashboard and add them to the relevant MetricCalculation
     */
    private void getMetricDatas() throws RemoteException, ServiceException, SQLException {

        if (this.partnerName == null) {
            this.partnerName = DatabaseHelpers.getPartnerName(this.dashboardId);
        }

        // Get all the metric datas that have currently been created in Salesforce
        QueryResult result = SalesforceProxy.getSalesforceObjects(getMetricDatasSalesforceQueryString());
        Boolean moreRows = true;
        if (result.getSize() > 0) {
            while (moreRows) {
                SObject[] datas = result.getRecords();
                for (int i = 0; i < datas.length; i++) {
                    M_E_Metric_Data__c data = (M_E_Metric_Data__c)datas[i];
                    String districtString = null;
                    String metricName = data.getM_E_Metric__r().getName();
                    Boolean isLickert = data.getM_E_Metric__r().getIs_Lickert__c();
                    data.setM_E_Metric__r(null);
                    if (data.getDistrict__c() != null) {
                        districtString = data.getDistrict__r().getName();
                    }
                    data.setDistrict__c(null);
                    String key = Utils.createMetricLabel(metricName, districtString);
                    MetricCalculation calc;
                    if (districtString == null) {
                        calc = this.totalCalcualtionMap.get(key);
                    }
                    else {
                        calc = this.calcualtionMap.get(key);
                    }
                    if (calc == null) {

                        // Check to see if this metric is lickert. If so need to do some tweaking to the name
                        String paramName = metricName;
                        if (isLickert) {
                            paramName = paramName.substring(0, paramName.lastIndexOf("_"));
                        }
                        calc = new MetricCalculation(districtString, paramName, metricName);
                    }
                    calc.addMetricData(data);
                    if (districtString == null) {
                        this.totalCalcualtionMap.put(key, calc);
                    }
                    else {
                        this.calcualtionMap.put(key, calc);
                    }
                }
                if (result.isDone()) {
                    moreRows = false;
                }
                else {
                    result = SalesforceProxy.getSalesforceObjectsMore(result.getQueryLocator());
                }
            }
        }
    }

    /**
     * Build the query string to get all the surveys for this dashboard
     *
     * @return - The query string
     */
    private String getSurveysForDashboardQuery() {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("s.survey_id as surveySalesforceId, ");
        commandText.append("s.id surveyId ");
        commandText.append("FROM ");
        commandText.append(DatabaseHelpers.DASHBOARD_SURVEY_TABLE + " ds, ");
        commandText.append(DatabaseHelpers.SURVEY_TABLE + " s");
        commandText.append(" WHERE ");
        commandText.append("ds.dashboard_id = " + this.dashboardId);
        commandText.append(" AND ds.survey_id = s.id");
        return commandText.toString();
    }

    /**
     * Generate the salesforce query to get all the Metrics for this dashboard. Based on the metric parameter names
     *
     * @param partnerName - Name of the partner of whom we require the metrics
     */
    private String getMetricsSalesforceQueryString(String partnerName) {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("Name, ");
        commandText.append("Id ");
        commandText.append("FROM ");
        commandText.append("M_E_Metric__c ");
        commandText.append("WHERE ");
        commandText.append("Name IN (");
        commandText.append(getMetricNames());
        commandText.append(") ");
        return commandText.toString();
    }
    
    /**
     * Generate the Salesforce query to get the metric datas for the current quarter.
     *
     * @return - The query String
     */
    private String getMetricDatasSalesforceQueryString() {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("Name, ");
        commandText.append("Id, ");
        commandText.append("District__r.Name, ");
        commandText.append("District__c, ");
        commandText.append("M_E_Metric__r.Name, ");
        commandText.append("M_E_Metric__r.Is_Lickert__c, ");
        commandText.append("Actual_Value__c ");
        commandText.append("FROM ");
        commandText.append("M_E_Metric_Data__c ");
        commandText.append("WHERE ");
        commandText.append("M_E_Metric__r.Name IN (");

        // Get the names of the metrics including the lickert scale
        commandText.append(getMetricNames());
        commandText.append(") ");

        // Add the dates in
        commandText.append("AND Date__c >= " + Utils.getQuarterDate(InterviewerMap.getTime(), true, true, true) + " ");
        commandText.append("AND Date__c <= " + Utils.getQuarterDate(InterviewerMap.getTime(), false, true, true) + " ");
        commandText.append("AND M_E_Metric__r.Is_Header__c = false");
        return commandText.toString();
    }

    /**
     * Gets a comma separated list of the available metric names. 
     *
     * @return - String of the names suitable for an SQL or SOQL query.
     */
    private String getMetricNames() {

        ArrayList<String> metricNames = new ArrayList<String>();
        for (Entry<String, MetricParameter> entry : this.parameterMap.entrySet()) {
            Integer lickert = entry.getValue().getLickert();
            if (lickert == 0) {
                metricNames.add(entry.getKey());
                continue;
            }
            for (Integer i = 1; i <= lickert; i++) {
                metricNames.add(entry.getKey() + "_" + String.valueOf(i));
            }
        }
        metricNames.addAll(this.parameterMap.keySet());
        return Utils.generateCommaSeparatedString(metricNames, true);
    }
}
