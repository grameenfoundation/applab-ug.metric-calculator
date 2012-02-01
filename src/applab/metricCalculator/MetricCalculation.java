package applab.metricCalculator;

import com.sforce.soap.enterprise.sobject.M_E_Metric_Data__c;
import com.sforce.soap.enterprise.sobject.M_E_Metric__c;

/**
 * 
 *
 * Copyright (C) 2012 Grameen Foundation
 */
public class MetricCalculation {

    private Double value1;
    private Double value2;
    private String subDivider;
    private String parameterName;
    private String metricName;
    private M_E_Metric_Data__c data;

    public MetricCalculation(String subDivider, String parameterName, String metricName) {
        this.subDivider = subDivider;
        this.parameterName = parameterName;
        this.metricName = metricName;
        this.value1 = 0.0;
        this.value2 = 0.0;
    }

    public double getValue1() {
        return this.value1;
    }

    public void addToValue1(Double modifier) {
        this.value1 += modifier;
    }

    public void addToValue2(Double modifier) {
        this.value2 += modifier;
    }

    public String getSubDivider() {
        return this.subDivider;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getMetricName() {
        return metricName;
    }

	public void addMetricData(M_E_Metric_Data__c metricData) {
        this.data = metricData;
    }

    public M_E_Metric_Data__c getData() {
        return this.data;
    }

    public void updateMetricDataValue(MetricParameter parameter, Double total) {

        // A total of -1.0 indicates that we are dealing with a total metric.
        if (total == -1.0) {
            total = this.value2;
            if (parameter.getCalculationType().toString().equals("percentage")) {
                this.data.setActual_Value__c(this.value1 / total);
                return;
            }
        }
        this.data.setActual_Value__c(this.calculateValue(parameter, total));
    }

    public Double calculateValue(MetricParameter parameter, Double total) {

        // Calculate the Actual Value
        Double returnValue = 0.0;
        switch (parameter.getCalculationType()) {
            case sum :
            case count :
                returnValue = this.value1;
                break;
            case average :
                returnValue = this.value1 / total;
                break;
            case percentage :
                returnValue = (this.value1 / total) * 100;
                break;
        }
        return returnValue;
    }

    public M_E_Metric_Data__c createNewMetricData(
            MetricParameter parameter,
            M_E_Metric__c metric,
            String subDividerId,
            Double total
    ) {

        M_E_Metric_Data__c data = new M_E_Metric_Data__c();
        data.setM_E_Metric__c(metric.getId());

        // Calculate the Actual Value
        if (total == -1.0) {
            total = this.value2;
        }
        data.setActual_Value__c(this.calculateValue(parameter, total));
        data.setDistrict__c(subDividerId);
        data.setDate__c(Utils.getQuarterStartDate());
        return data;
    }
}
