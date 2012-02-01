package applab.metricCalculator;

import java.util.HashMap;

/**
 * Stores the data that defines each metric parameter. Data taken directly from the DB
 *
 * Copyright (C) 2012 Grameen Foundation
 */

public class MetricParameter {

    // Name of the metric as it is in Saleforce M_E_Metric__c.Name (does not include the lickert option or the headers)
    private String metricName;

    // Id in the db (zebrasurvey.id) for the survey
    private Integer surveyId;

    // Binding for the question involved. answers.question_name. Must match with the survey xml. If the survey is changed then this will break
    private String binding;

    // The type of question number|boolean|singleSelect|multiSelect
    private QuestionType questionType;

    // Calculation type sum|count|average
    private MetricCalculationType calculationType;

    // Which of the possible select options should be included as positive answers for the metric. Needs to match the bindings in the survey
    private String selectOptions;

    // What field should the metric be grouped by e.g. district - TODO - Make this work. Currently all are grouped by district
    private String groupByField;

    // How many lickert options are there in this question
    private Integer lickert;

    // Does this metric only count submissions that answered this question
    private String onlyAnsweredSurveys;

    // Is the question a repeat question i.e. counts how many times the question was answered per survey
    private String isRepeat;

    // Stores the submissions for this binding. This is used if the metric is only for submissions who answered an optional question
    private HashMap<String, Double> totalSubmissions;

    public MetricParameter(
            String metricName,
            Integer surveyId,
            String binding,
            String questionType,
            String calculationType,
            String selectOptions,
            String groupByField,
            Integer lickert,
            String onlyAnsweredSurveys,
            String isRepeat
    ) {

        this.metricName = metricName;
        this.surveyId = surveyId;
        this.binding = binding;
        this.questionType = QuestionType.valueOf(questionType);
        this.calculationType = MetricCalculationType.valueOf(calculationType);
        this.selectOptions = selectOptions;
        this.groupByField = groupByField;
        this.lickert = lickert;
        this.onlyAnsweredSurveys = onlyAnsweredSurveys;
        this.isRepeat = isRepeat;
        this.totalSubmissions = new HashMap<String, Double>();
    }

    public String getMetricName() {
        return metricName;
    }

    public Integer getSurveyId() {
        return surveyId;
    }

    public String getBinding() {
        return binding;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public MetricCalculationType getCalculationType() {
        return calculationType;
    }

    public String getSelectOptions() {
        return selectOptions;
    }

    public String getGroupByField() {
        return groupByField;
    }

    public Integer getLickert() {
        return lickert;
    }

    public String getIsRepeat() {
        return isRepeat;
    }

    public String getOnlyAnsweredSurveys() {
        return onlyAnsweredSurveys;
    }

    /**
     * Builds the query string that gets all the submissions for this metric
     *
     * @return - The query string
     */
    public String getQueryString() {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");

        // If counting number of repeat answers
        if (this.getIsRepeat().equals("Y")) {
            commandText.append("count(a.id) as answer, ");
        }
        else {
            commandText.append("a.answer as answer, ");
        }
        commandText.append("s.interviewer_id as interviewer_id ");
        commandText.append("FROM ");
        commandText.append(DatabaseHelpers.SUBMISSION_ANSWERS_TABLE + " a, ");
        commandText.append(DatabaseHelpers.SUBMISSION_TABLE + " s ");
        commandText.append("WHERE ");
        commandText.append("s.survey_id = ");
        commandText.append(this.surveyId);
        commandText.append(" AND s.id = a.submission_id ");
        commandText.append(" AND a.question_name = '");
        commandText.append(this.binding);
        commandText.append("' ");
        commandText.append(getSelectOptionClause());
        if (this.getIsRepeat().equals("Y")) {
            commandText.append(" GROUP BY s.id");
        }
        return commandText.toString();
    }

    /**
     * Builds the part of the query string that deals with single or multiple select questions
     *
     * @return - Part of the where clause for the select options
     */
    private String getSelectOptionClause() {

        StringBuilder selectOptionClause = new StringBuilder();

        // If it is a lickert style question then we allow all select options.
        if (this.lickert != 0) {
            return selectOptionClause.toString();
        }
        switch (this.questionType) {
            case singleSelect:
                selectOptionClause.append("AND a.answer = '");
                selectOptionClause.append(this.selectOptions);
                selectOptionClause.append("' ");
                break;
            case multiSelect:
                String[] optionList = this.selectOptions.split(" ");
                Integer counter = 0;
                for (String option : optionList) {
                    selectOptionClause.append("(a.answer LIKE '" + option + "' ");
                    selectOptionClause.append(" OR a.answer LIKE '% " + option + " %' ");
                    selectOptionClause.append(" OR a.answer LIKE '% " + option + "' ");
                    selectOptionClause.append(" OR a.answer LIKE '" + option + " %' ");
                    if (counter < optionList.length - 1) {
                        selectOptionClause.append(" OR ");
                    }
                }
                break;
        }
        return selectOptionClause.toString();
    }

    /**
     * Updates a metric calculation object based on the parameters in this instance
     *
     * @param metricCalculation - The object that contains the counters for this metric param
     * @param answer            - The answer to this question
     *
     * @return - The metric calculation object
     */
    public MetricCalculation updateCalculation(MetricCalculation metricCalculation, String answer) {

        if (this.questionType.toString().equals("number")) {
            try {
                Double answerInt = Double.valueOf(answer);
                metricCalculation.addToValue1(answerInt);
            }
            catch (NumberFormatException e) {
                System.out.println("Answer " + answer + " is not a valid number so cannot add to total");
                return metricCalculation;
            }
        }
        else {
            switch (this.questionType) {
                case bool :
                    if (answer.equals("1") || answer.toLowerCase().equals("true") || answer.toLowerCase().equals("yes")) {
                        metricCalculation.addToValue1(1.0);
                    }
                    break;
                case singleSelect :	
                case multiSelect :
                    metricCalculation.addToValue1(1.0);
                    break;
            }
        }
        return metricCalculation;
    }

    /**
     * Build the query to count how many submissions count towards this metric
     *
     * @return - The query string
     */
    public String getTotalSubmissionQuery() {

        StringBuilder commandText = new StringBuilder();
        commandText.append("SELECT ");
        commandText.append("s.interviewer_id ");
        commandText.append("FROM ");
        commandText.append("zebrasurveysubmissions s ");
        if (this.onlyAnsweredSurveys.equals("Y")) {
            commandText.append(", answers a ");
        }
        commandText.append("");
        commandText.append("WHERE ");
        commandText.append("s.survey_id = " + String.valueOf(this.surveyId) + " ");
        if (this.onlyAnsweredSurveys.equals("Y")) {
            commandText.append("AND a.submission_id = s.id ");
            commandText.append("AND a.question_name = '" + this.binding + "' ");
            commandText.append("AND a.position = 0");
        }
        return commandText.toString();
    }

    /**
     * Add to the total submission count for this metric
     *
     * @param subDivider - The subdivider key used for the map
     * @param total      - How many to add to the map
     */
    public void addToTotalSubmissions(String subDivider, Double total) {

        if (this.totalSubmissions.containsKey(subDivider)) {
            this.totalSubmissions.put(subDivider, this.totalSubmissions.get(subDivider) + total);
        }
        else {
            this.totalSubmissions.put(subDivider, total);
        }
    }

    /**
     * Get the total submissions from the map
     *
     * @param subDivider - The subdivider key used for the map
     *
     * @return - The total submissions
     */
    public Double getTotalSubmissions(String subDivider) {

        Double total = -1.0;
        if (this.totalSubmissions.containsKey(subDivider)) {
            total = this.totalSubmissions.get(subDivider);
        }
        return total;
    }
}