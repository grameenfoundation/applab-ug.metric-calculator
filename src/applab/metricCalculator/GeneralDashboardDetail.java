package applab.metricCalculator;

/**
 * Class represents the general details that need to be calculated and saved during dashboard calculation.
 * Will be augmented as new use cases come up
 *
 * Copyright (C) 2012 Grameen Foundation
 */
public class GeneralDashboardDetail {

    private Double totalInterviewer;

    public GeneralDashboardDetail() {
        this.totalInterviewer = 0.0;
    }

    public Double getTotalInterviewer() {
        return totalInterviewer;
    }

    public void addToTotalInterviewer(Double totalInterviewer) {
        this.totalInterviewer += totalInterviewer;
    }
}
