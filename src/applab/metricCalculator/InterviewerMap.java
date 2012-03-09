package applab.metricCalculator;

import java.rmi.RemoteException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Set;

import javax.xml.rpc.ServiceException;

import com.sforce.soap.enterprise.QueryResult;
import com.sforce.soap.enterprise.sobject.CKW__c;
import com.sforce.soap.enterprise.sobject.Person__c;
import com.sforce.soap.enterprise.sobject.SObject;

/**
 * Class that deals with storing details about the interviewers that requires to be used by all the dashboards being generated.
 * Works as a cache.
 *
 * Copyright (C) 2012 Grameen Foundation
 */
public class InterviewerMap {

    private static InterviewerMap singletonValue;

    private HashMap<String, Person> personMap;

    // Due to an old design hangover some of the submissions use CKW id some use person. Person is correct so this is
    // a little switcheroo to all the CWK__c.Name fields to be xlated to Person__c.Name
    private HashMap<String, String> ckwSwitcherooMap;

    // Districts so they can be added to any metric datas that are needed
    private HashMap<String, String> districtMap;

    // A map of maps that contains the total number of submissions for each survey and for each question asked. This is filled lazily
    private HashMap<String, HashMap<String, Double>> submissionCount;

    private Calendar time;
    public InterviewerMap() {
    }

    public static void init() {

        InterviewerMap map = new InterviewerMap();
        map.personMap = new HashMap<String, Person>();
        map.ckwSwitcherooMap = new HashMap<String, String>();
        map.districtMap = new HashMap<String, String>();
        map.submissionCount = new HashMap<String, HashMap<String, Double>>();
        map.time = Calendar.getInstance();
        map.time.roll(Calendar.DAY_OF_YEAR, false);
        InterviewerMap.singletonValue = map;
    }

    /**
     * Get all the interviewer details from Salesforce for anyone who has submitted
     *
     * @param dashboardId - The id of the dashboard
     *
     * @return - A List of the ids for everyone who submitted a survey for this dashboard
     */
    public static ArrayList<String> populateMapForDashboard(Integer dashboardId) throws SQLException, RemoteException, ServiceException {

        ArrayList<String> peopleList = new ArrayList<String>();

        String surveyIdString = getSurveyIds(dashboardId);
        if (surveyIdString.equals("")) {
            return peopleList;
        }
        ArrayList<String> personIds = new ArrayList<String>();
        ArrayList<String> ckwIds = new ArrayList<String>();
        ResultSet resultSet = DatabaseHelpers.executeSelectQuery(getDistinctInterviewerNameQuery(surveyIdString));
        while(resultSet.next()) {
            String id = resultSet.getString("interviewer_id");
            id = id.toUpperCase();

            // If we have already fetched the persons details then ignore them but add the person id to the list of interviewers.
            if (singletonValue.personMap.containsKey(id)) {
                peopleList.add(id);
                continue;
            }
            if (singletonValue.ckwSwitcherooMap.containsKey(id)) {
                peopleList.add(singletonValue.ckwSwitcherooMap.get(id));
                continue;
            }
            if (id.startsWith("CKW")) {
                ckwIds.add(id);
            }
            else if (id.startsWith("PERSON")) {
                personIds.add(id);
            }
        }

        // Get all the interviewers who are people from Salesforce
        if (personIds.size() > 0) {
            peopleList.addAll(addPeopleToMap(personIds));
        }

        // Add all the CKWs to the map
        if (ckwIds.size() > 0) {
            peopleList.addAll(addCkwsToMap(ckwIds));
        }
        return peopleList;
    }

    /**
     * Fetch Person__c objects from salesforce and add them to the PersonMap
     *
     * @param personIds - List of the Person__c.Name values taken from the DB
     *
     * @return - List of the Person__c.Name values that we found in Salesforce
     */
    private static ArrayList<String> addPeopleToMap(ArrayList<String> personIds) throws RemoteException, ServiceException {

        ArrayList<String> peopleList = new ArrayList<String>();
        QueryResult result = SalesforceProxy.getSalesforceObjects(getSalesforcePersonQuery(personIds));
        Boolean moreRows = true;
        if (result.getSize() > 0) {
            while (moreRows) {
                SObject[] people = result.getRecords();
                for (int i = 0; i < people.length; i++) {
                    Person__c person = (Person__c)people[i];
                    peopleList.add(person.getName().toUpperCase());

                    // Create an error string to indicate that a person is missing vital info
                    String error = "";
                    Boolean addToMap = true;
                    if (!singletonValue.personMap.containsKey(person.getName().toUpperCase())) {
                        if (person.getGender__c() == null) {
                            error += " has no gender set.";
                            addToMap = false;
                        }
                        if (person.getDistrict__c() == null) {
                            error += " has no District set.";
                            addToMap = false;
                        }
                        if (addToMap) {
                            singletonValue.personMap.put(person.getName().toUpperCase(), singletonValue.new Person(person.getGender__c(), person.getDistrict__r().getName()));
                        }
                        else {
                            System.out.println("PERSON " + person.getName().toUpperCase() + error);
                        }
                    }
                    if (addToMap && !singletonValue.districtMap.containsKey(person.getDistrict__r().getName().toUpperCase())) {
                        singletonValue.districtMap.put(person.getDistrict__r().getName(), person.getDistrict__r().getId());
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
        return peopleList;
    }

    /**
     * Fetch CKW__c objects from salesforce and add them to the PersonMap. Also populate the ckwSwitcherooMap so the CKC__c.Name values
     * can be mapped to CKW__c.Person__r.Name
     *
     * @param personIds - List of the CKW__c.Person__r.Name values taken from the DB
     *
     * @return - List of the CKW__c.Person__r.Name values that we found in Salesforce
     */
    private static ArrayList<String> addCkwsToMap(ArrayList<String> ckwIds) throws RemoteException, ServiceException {

        ArrayList<String> peopleList = new ArrayList<String>();
        QueryResult result = SalesforceProxy.getSalesforceObjects(getSalesforceCkwQuery(ckwIds));
        Boolean moreRows = true;
        if (result.getSize() > 0) {
            while (moreRows) {
                SObject[] ckws = result.getRecords();
                for (int i = 0; i < ckws.length; i++) {
                    CKW__c ckw = (CKW__c)ckws[i];
                    singletonValue.ckwSwitcherooMap.put(ckw.getName().toUpperCase(), ckw.getPerson__r().getName().toUpperCase());
                    peopleList.add(ckw.getPerson__r().getName().toUpperCase());

                    // Create an error string to indicate that a CKW is missing vital info
                    String error = "";
                    Boolean addToMap = true;
                    if (!singletonValue.personMap.containsKey(ckw.getPerson__r().getName().toUpperCase())) {
                        if (ckw.getPerson__r().getGender__c() == null) {
                            error += " has no gender set.";
                            addToMap = false;
                        }
                        if (ckw.getPerson__r().getDistrict__c() == null) {
                            error += " has no District set.";
                            addToMap = false;
                        }
                        if (addToMap) {
                            singletonValue.personMap.put(ckw.getPerson__r().getName().toUpperCase(), singletonValue.new Person(ckw.getPerson__r().getGender__c(), ckw.getPerson__r().getDistrict__r().getName()));
                        }
                        else {
                            System.out.println("CKW " + ckw.getName() + error);
                        }
                    }
                    if (addToMap && !singletonValue.districtMap.containsKey(ckw.getPerson__r().getDistrict__r().getName())) {
                        singletonValue.districtMap.put(ckw.getPerson__r().getDistrict__r().getName(), ckw.getPerson__r().getDistrict__r().getId());
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
        return peopleList;
    }

    /**
     * Get all the survey ids for a given dashboard
     *
     * @param dashboardId
     * 
     * @return - A string that can be added to SQL IN clause to get all the submissions
     */
    private static String getSurveyIds(Integer dashboardId) throws SQLException {

        String surveyIdString = "";
        ArrayList<Integer> surveyIds = new ArrayList<Integer>();
        ResultSet resultSet = DatabaseHelpers.executeSelectQuery(getSurveyIdsQuery(dashboardId));
        while(resultSet.next()) {
            surveyIds.add(resultSet.getInt("survey_id"));
        }
        Integer size = surveyIds.size();
        for(Integer i = 0; i < size; i++) {
            surveyIdString += surveyIds.get(i).toString();
            if (i < size - 1) {
                surveyIdString += ",";
            }
        }
        return surveyIdString;
    }

    /**
     * Get all the survey ids from the submission count map
     * @param key
     * @return
     */
    public static Set<String> getSubmissionKeySet(String key) {

        if (singletonValue.submissionCount.containsKey(key)) {
             return singletonValue.submissionCount.get(key).keySet();
        }
        return null;
    }

    /**
     * Get the total submissions for a given survey. Will split by district
     * TODO - Allow to split by other field.
     * Will populate the submission map lazily if this survey has not been fetched already
     *
     * @param surveyId - The survey that the total is required for 
     * @param key      - The splitter field
     *
     * @return
     */
    public static Double getTotalSubmissions(Integer surveyId, String key) throws SQLException {

        if (surveyId == null) {
            return -1.0;
        }
        String surveyIdString = String.valueOf(surveyId);
        if (singletonValue.submissionCount.containsKey(surveyIdString)) {
            if (singletonValue.submissionCount.get(surveyIdString).containsKey(key)) {
                return singletonValue.submissionCount.get(surveyIdString).get(key);
            }
            else {
                return -1.0;
            }
        }
        else {
            singletonValue.submissionCount.put(surveyIdString, new HashMap<String, Double>());
        }

        // The total has not been fetched so go and get it
        HashMap<String, Double> map = singletonValue.submissionCount.get(surveyIdString);
        if (map == null) {
            map = new HashMap<String, Double>();
        }

        ResultSet resultSet = DatabaseHelpers.executeSelectQuery(getSubmissionCountQuery(surveyId));
        while (resultSet.next()) {
            String subDivider = getSubDivider(resultSet.getString("interviewer_id").toUpperCase());
            if (subDivider == null) {
                continue;
            }
            if (map.containsKey(subDivider)) {
                map.put(subDivider, map.get(subDivider) + 1.0);
            }
            else {
                map.put(subDivider, 1.0);
            }
            if (map.containsKey("total")) {
                map.put("total", map.get("total") + 1.0);
            }
            else {
                map.put("total", 1.0);
            }
        }
        singletonValue.submissionCount.put(surveyIdString, map);
        resultSet.close();
        return map.get(key);
    }

    private static String getSubmissionCountQuery(Integer surveyId) {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("interviewer_id ");
        commandText.append("FROM ");
        commandText.append(DatabaseHelpers.SUBMISSION_TABLE + " s ");
        commandText.append("WHERE ");
        commandText.append("s.survey_id = " + String.valueOf(surveyId) + " ");
        commandText.append(DatabaseHelpers.getQuarterStartEndParameter());
        return commandText.toString();
    }

    private static String getSalesforcePersonQuery(ArrayList<String> personNames) {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("Name, ");
        commandText.append("Id, ");
        commandText.append("District__c, ");
        commandText.append("District__r.Name, ");
        commandText.append("District__r.Id, ");
        commandText.append("Gender__c ");
        commandText.append("FROM ");
        commandText.append("Person__c ");
        commandText.append("WHERE ");
        commandText.append("Name IN (");
        commandText.append(Utils.generateCommaSeparatedString(personNames, true));
        commandText.append(")");
        return commandText.toString();
    }

    private static String getSalesforceCkwQuery(ArrayList<String> ckwNames) {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("Person__r.Name, ");
        commandText.append("Id, ");
        commandText.append("Name, ");
        commandText.append("Person__r.District__r.Name, ");
        commandText.append("Person__r.District__c, ");
        commandText.append("Person__r.District__r.Id, ");
        commandText.append("Person__r.Gender__c ");
        commandText.append("FROM ");
        commandText.append("CKW__c ");
        commandText.append("WHERE ");
        commandText.append("Name IN (");
        commandText.append(Utils.generateCommaSeparatedString(ckwNames, true));
        commandText.append(")");
        return commandText.toString();
    }

    private static String getDistinctInterviewerNameQuery(String surveyIds) {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT DISTINCT ");
        commandText.append("interviewer_id ");
        commandText.append("FROM ");
        commandText.append(DatabaseHelpers.SUBMISSION_TABLE);
        commandText.append(" WHERE ");
        commandText.append("survey_id IN (");
        commandText.append(surveyIds);
        commandText.append(")");
        return commandText.toString();
    }

    private static String getSurveyIdsQuery(Integer dashboardId) {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT DISTINCT ");
        commandText.append("survey_id ");
        commandText.append("FROM ");
        commandText.append(DatabaseHelpers.DASHBOARD_SURVEY_TABLE);
        commandText.append(" WHERE ");
        commandText.append("dashboard_id = ");
        commandText.append(dashboardId);
        return commandText.toString();
    }

    private static String CkwNameSwitcheroo(String personId) {

        if (personId.startsWith("CKW") && singletonValue.ckwSwitcherooMap.containsKey(personId)) {
            return singletonValue.ckwSwitcherooMap.get(personId);
        }
        return personId;
    }

    /**
     * Get the subdivider for a given person.
     * TODO - Allow for more than the district to be used as the subdivider
     *
     * @param personId - Id for that person
     *
     * @return - The district
     */
    public static String getSubDivider(String personId) {

        // Due to having the CKW__c.Name in the DB instead of the Person__c.Name for some of the interviewers we may need to do
        // a cheeky switcheroo
        personId = CkwNameSwitcheroo(personId);
        if (!singletonValue.personMap.containsKey(personId)) {
            return null;
        }
        return singletonValue.personMap.get(personId).getDistrict();
    }

    /**
     * Get the gender of an interviewer
     *
     * @param personId - The id of the interviewer
     *
     * @return - The gender of the interviewer
     */
    public static String getGender(String personId) {

        personId = CkwNameSwitcheroo(personId);
        if (!singletonValue.personMap.containsKey(personId)) {
            return null;
        }
        return singletonValue.personMap.get(personId).getGender();
    }

    /**
     * Get the Salesforce record id for a given district
     * @param districtName
     * @return
     */
    public static String getDistrictId(String districtName) {

        if (districtName == null) {
            return null;
        }
        if (singletonValue.districtMap.containsKey(districtName)) {
            return singletonValue.districtMap.get(districtName);
        }
        return null;
    }

    /**
     * Get the calendar object that has been created at the start of the process.
     * 
     * @return
     */
    public static Calendar getTime() {

        if (singletonValue == null) {
            init();
        }
        return singletonValue.time;
    }

    /**
     * Private class the represents a person object that holds the details about an interviewer what are needed to calcualte the metrics
     *
     */
    private class Person {
        private String gender;
        private String district;

        public Person(String gender, String district) {
            this.gender = gender;
            this.district = district;
        }

        public String getGender() {
            return this.gender;
        }

        public String getDistrict() {
            return this.district;
        }
    }
}
