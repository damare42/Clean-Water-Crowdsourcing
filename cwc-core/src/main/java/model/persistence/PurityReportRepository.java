package model.persistence;

import model.WaterPurityReport;
import model.WaterPurityReportDraft;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stores water purity reports and assigns their numbers. Purity reports are numbered
 * separately from water source reports: numbers start at 1 and each new report gets
 * one more than the highest stored number.
 */
public interface PurityReportRepository {

    /**
     * Stores a new report under the next report number. Assigning the number and
     * storing the report is atomic.
     *
     * @param draft the report to store
     * @return the stored report with its number
     */
    WaterPurityReport add(WaterPurityReportDraft draft);

    /**
     * @return all reports in ascending report-number order (unmodifiable)
     */
    List<WaterPurityReport> findAll();

    /**
     * @param from the earliest report time to include
     * @param until the first report time not to include
     * @return the reports filed in [from, until), in ascending report-number order
     *         (unmodifiable)
     */
    List<WaterPurityReport> findReportedBetween(Instant from, Instant until);

    /**
     * @param reportNumber the number of the report
     * @return the report, or empty if there is none with this number
     */
    Optional<WaterPurityReport> findByNumber(int reportNumber);

    /**
     * @return the number the next added report will get, if no other report is
     *         added first
     */
    int nextReportNumber();
}
