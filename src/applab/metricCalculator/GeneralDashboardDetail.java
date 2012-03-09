package applab.metricCalculator;

/**
 * Class represents the general details that need to be calculated and saved during dashboard calculation.
 * Will be augmented as new use cases come up
 *
 * Copyright (C) 2012 Grameen Foundation
 */
public class GeneralDashboardDetail {

    private Double totalInterviewer;
    private Double totalFemaleInterviewer;
    private Double totalMaleInterviewer;

    public GeneralDashboardDetail() {
        this.totalInterviewer = 0.0;
        this.totalFemaleInterviewer = 0.0;
        this.totalMaleInterviewer = 0.0;
    }

    public Double getTotalInterviewer() {
        return totalInterviewer;
    }

    public Double getTotalFemaleInterviewer() {
        return totalFemaleInterviewer;
    }

    public Double getTotalMaleInterviewer() {
        return totalMaleInterviewer;
    }

    public void addToTotalInterviewer(Double totalInterviewer) {
        this.totalInterviewer += totalInterviewer;
    }

    public void addToTotalFemaleInterviewer(Double totalFemaleInterviewer) {
        this.totalFemaleInterviewer += totalFemaleInterviewer;
    }

    public void addToTotalMaleInterviewer(Double totalMaleInterviewer) {
        this.totalMaleInterviewer += totalMaleInterviewer;
    }

}
