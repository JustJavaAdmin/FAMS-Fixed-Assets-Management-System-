package com.example.fams.depreciation;

import java.time.LocalDate;
import java.time.YearMonth;

public record DepreciationPeriod(
        String code,
        DepreciationPeriodType type,
        int fiscalYear,
        int periodNumber,
        LocalDate startDate,
        LocalDate endDate
) {
    public static DepreciationPeriod from(String periodCode, LocalDate suppliedEndDate) {
        if (periodCode == null || periodCode.isBlank()) {
            throw new IllegalArgumentException("Depreciation period cannot be empty");
        }

        if (periodCode.matches("^\\d{4}-Q[1-4]$")) {
            int year = Integer.parseInt(periodCode.substring(0, 4));
            int quarter = Integer.parseInt(periodCode.substring(6, 7));
            int startMonth = ((quarter - 1) * 3) + 1;
            LocalDate start = LocalDate.of(year, startMonth, 1);
            LocalDate end = YearMonth.of(year, startMonth + 2).atEndOfMonth();
            return validateEndDate(new DepreciationPeriod(periodCode, DepreciationPeriodType.QUARTERLY, year, quarter, start, end), suppliedEndDate);
        }

        if (periodCode.matches("^\\d{4}-A$")) {
            int year = Integer.parseInt(periodCode.substring(0, 4));
            return validateEndDate(new DepreciationPeriod(periodCode, DepreciationPeriodType.ANNUAL, year, 1,
                    LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)), suppliedEndDate);
        }

        if (periodCode.matches("^\\d{4}-\\d{2}$")) {
            int year = Integer.parseInt(periodCode.substring(0, 4));
            int month = Integer.parseInt(periodCode.substring(5, 7));
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("Invalid month in depreciation period");
            }
            YearMonth yearMonth = YearMonth.of(year, month);
            return validateEndDate(new DepreciationPeriod(periodCode, DepreciationPeriodType.MONTHLY, year, month,
                    yearMonth.atDay(1), yearMonth.atEndOfMonth()), suppliedEndDate);
        }

        throw new IllegalArgumentException("Invalid period format. Use YYYY-MM, YYYY-Q1/Q2/Q3/Q4, or YYYY-A");
    }

    private static DepreciationPeriod validateEndDate(DepreciationPeriod period, LocalDate suppliedEndDate) {
        if (suppliedEndDate != null && !period.endDate().equals(suppliedEndDate)) {
            throw new IllegalArgumentException("Period end date must match the selected depreciation period");
        }
        return period;
    }
}
