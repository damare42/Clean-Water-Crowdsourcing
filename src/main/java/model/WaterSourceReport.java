package model;

/**
 * Created by Sean on 10/6/2016.
 * A report about a water source: where it is, what kind it is and its condition.
 * Instances are immutable and always valid.
 */
public class WaterSourceReport {
    private final int reportNumber;
    private final String user;
    private final String date;
    private final String time;
    private final double latitude;
    private final double longitude;
    private final WaterSourceType type;
    private final WaterSourceCondition condition;

    /**
     * Creates a report. Use {@link WaterSourceReportHolder#submit} to create a report
     * with the next free report number and store it.
     *
     * @throws IllegalArgumentException if the report number is not positive, the
     *         latitude is outside [-90, 90], the longitude is outside [-180, 180], or
     *         the type or condition is missing
     */
    public WaterSourceReport(int reportNumber, String user, String date, String time,
                             double latitude, double longitude,
                             WaterSourceType type, WaterSourceCondition condition) {
        if (reportNumber < 1) {
            throw new IllegalArgumentException("Report number must be positive: " + reportNumber);
        }
        if (!Validation.isValidLatitude(latitude)) {
            throw new IllegalArgumentException(Validation.MSG_INVALID_LATITUDE);
        }
        if (!Validation.isValidLongitude(longitude)) {
            throw new IllegalArgumentException(Validation.MSG_INVALID_LONGITUDE);
        }
        if (type == null) {
            throw new IllegalArgumentException(Validation.MSG_TYPE_REQUIRED);
        }
        if (condition == null) {
            throw new IllegalArgumentException(Validation.MSG_CONDITION_REQUIRED);
        }
        this.reportNumber = reportNumber;
        this.user = user;
        this.date = date;
        this.time = time;
        this.latitude = latitude;
        this.longitude = longitude;
        this.type = type;
        this.condition = condition;
    }

    public int getReportNumber() {
        return reportNumber;
    }

    /**
     * @return the id of the account that filed the report
     */
    public String getUser() {
        return user;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public WaterSourceType getType() {
        return type;
    }

    public WaterSourceCondition getCondition() {
        return condition;
    }

    @Override
    public String toString() {
        return "Date: " + date
                + " Time: " + time
                + " User: " + user
                + " Type: " + type
                + " Condition: " + condition
                + " Report Number: " + reportNumber
                + " Latitude: " + latitude
                + " Longitude: " + longitude;
    }
}
