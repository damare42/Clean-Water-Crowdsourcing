package model;

/**
 * A measured value of a {@link WaterPurityReport} that the history chart can show.
 */
public enum PurityMetric {
    /** {@link WaterPurityReport#getVirusPpm()}. */
    Virus("Virus PPM"),
    /** {@link WaterPurityReport#getContaminantPpm()}. */
    Contaminant("Contaminant PPM");

    private final String label;

    PurityMetric(String label) {
        this.label = label;
    }

    /**
     * @return the name of the value for display, e.g. "Virus PPM"
     */
    public String label() {
        return label;
    }

    /**
     * @param report a purity report
     * @return this value of the report, in parts per million
     */
    public double measure(WaterPurityReport report) {
        return this == Virus ? report.getVirusPpm() : report.getContaminantPpm();
    }

    @Override
    public String toString() {
        return label;
    }
}
