package model.persistence;

import model.WaterSourceReport;
import model.WaterSourceReportDraft;

import java.util.List;
import java.util.Optional;

/**
 * Stores water source reports and assigns their numbers. Report numbers start at 1
 * and each new report gets one more than the highest stored number.
 */
public interface ReportRepository {

    /**
     * Stores a new report under the next report number. Assigning the number and
     * storing the report is atomic.
     *
     * @param draft the report to store
     * @return the stored report with its number
     */
    WaterSourceReport add(WaterSourceReportDraft draft);

    /**
     * @return all reports in ascending report-number order (unmodifiable)
     */
    List<WaterSourceReport> findAll();

    /**
     * @param reportNumber the number of the report
     * @return the report, or empty if there is none with this number
     */
    Optional<WaterSourceReport> findByNumber(int reportNumber);

    /**
     * @return the number the next added report will get, if no other report is
     *         added first
     */
    int nextReportNumber();
}
