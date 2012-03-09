package applab.metricCalculator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * Utils class
 *
 * Copyright (C) 2012 Grameen Foundation
 */

public class Utils {

    /**
     * Creates a comma separated list that can be passed to an IN clause in both SQL and SOQL
     * TODO - Escape the input
     *
     * @param list      - The list that needs to be turned into a string
     * @param incQuotes - should each item be wrapped in quotes
     *
     * @return - The String to be passed into the query
     */
    public static String generateCommaSeparatedString(ArrayList<String> list, Boolean incQuotes) {

        String returnString = "";
        Integer size = list.size();
        for(Integer i = 0; i < size; i++) {
            if (incQuotes) {
                returnString += "'";
            }
            returnString += list.get(i);
            if (incQuotes) {
                returnString += "'";
            }
            if (i < size - 1) {
                returnString += ",";
            }
        }
        return returnString;
    }

    /**
     *  Create a standard string to uniquely identify a metric data. If null or empty params are passed in
     *  that part of the name will be left out. It would be best to never pass in a blank metricName.
     *
     *  @param metricName     - The name of the metric
     *  @param subDividerName - The further sub divider that the metrics are spilt by (ideally should be the group by field in the calculation)
     *
     *  @return - A string that is of the format <metricName>_<subDividerName>
     */
    public static String createMetricLabel(String metricName, String subDividerName) {

        String metricLabelName = "";
        if (metricName != null && !metricName.equals("")) {
            metricLabelName += metricName;
        }
        if (subDividerName != null && !subDividerName.equals("")) {
            if (!metricLabelName.equals("")) {
                metricLabelName += "_";
            }
            metricLabelName += subDividerName;
        }
        return metricLabelName;
    }

    /**
     * Generate the quarter dates to define the quarters as a string that can be passed into a query
     *
     * @param calendar - The calendar to use as the base of the calculations
     * @param isStart  - Is it the start of the quarter
     * @param forSoql  - format for soql or sql
     * @param isDate   - Ignore time section
     *
     * @return - A string rep of the date to be passed into a query.
     */
    public static String getQuarterDate(Calendar calendar, Boolean isStart, Boolean forSoql, Boolean isDate) {

        String dateString = "";
        String[] quarterValues = getQuarterValues(calendar, isStart);
        dateString = quarterValues[2] + "-" + quarterValues[0] + "-" + quarterValues[1];
        if (isDate) {
            return dateString;
        }
        if (forSoql) {
            dateString += "T";
        }
        else {
            dateString += " ";
        }
        if (isStart) {
            dateString += "00:00:00";
        }
        else {
            dateString += "23:23:59";
        }
        if (forSoql) {
            dateString.replace(" ", "T");
            dateString += "Z";
        }
        return dateString;
    }

    /**
     * Get the Date that starts the current quarter
     *
     * @return - The date
     */
    public static Date getQuarterStartDate() {

        String quarterValues[] = getQuarterValues(InterviewerMap.getTime(), true);
        Calendar now = Calendar.getInstance();
        now.set(Calendar.DAY_OF_MONTH, Integer.parseInt(quarterValues[1]));
        now.set(Calendar.MONTH, Integer.parseInt(quarterValues[0]) - 1);
        now.set(Calendar.YEAR, Integer.parseInt(quarterValues[2]));
        return now.getTime();
    }

    /**
     * Get the day, month and year values for this start or end of a quarter based on the current date
     *
     * @param calendar - calendar to use as the base of the quarter
     * @param isStart
     * @return
     */
    private static String[] getQuarterValues(Calendar calendar, Boolean isStart) {

        if (calendar.get(Calendar.MONTH) < 3) {
            if (isStart) {
                return new String[] { "01", "01", String.valueOf(calendar.get(Calendar.YEAR)) };
            }
            else {
                return new String[] { "03", "31", String.valueOf(calendar.get(Calendar.YEAR))  };
            }
        }
        else if (calendar.get(Calendar.MONTH) < 6) {
            if (isStart) {
                return new String[] { "04", "01", String.valueOf(calendar.get(Calendar.YEAR))  };
            }
            else {
                return new String[] { "06", "30", String.valueOf(calendar.get(Calendar.YEAR))  };
            }
        }
        else if (calendar.get(Calendar.MONTH) < 9) {
            if (isStart) {
                return new String[] { "07", "01", String.valueOf(calendar.get(Calendar.YEAR))  };
            }
            else {
                return new String[] { "09", "30", String.valueOf(calendar.get(Calendar.YEAR))  };
            }
        }
        else {
            if (isStart) {
                return new String[] { "10", "01", String.valueOf(calendar.get(Calendar.YEAR))  };
            }
            else {
                return new String[] { "12", "31", String.valueOf(calendar.get(Calendar.YEAR))  };
            }
        }
    }
}
