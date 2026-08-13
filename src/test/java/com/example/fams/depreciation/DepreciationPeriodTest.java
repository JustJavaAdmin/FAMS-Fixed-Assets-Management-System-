package com.example.fams.depreciation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DepreciationPeriodTest {

    @Test
    void decemberMonthlyPeriodIsNotAnnual() {
        DepreciationPeriod period = DepreciationPeriod.from("2026-12", LocalDate.of(2026, 12, 31));

        assertEquals(DepreciationPeriodType.MONTHLY, period.type());
        assertEquals(12, period.periodNumber());
        assertEquals(LocalDate.of(2026, 12, 1), period.startDate());
        assertEquals("monthly", DepreciationService.resolvePeriodType("2026-12"));
    }

    @Test
    void annualPeriodUsesExplicitAnnualCode() {
        DepreciationPeriod period = DepreciationPeriod.from("2026-A", LocalDate.of(2026, 12, 31));

        assertEquals(DepreciationPeriodType.ANNUAL, period.type());
        assertEquals(1, period.periodNumber());
        assertEquals(LocalDate.of(2026, 1, 1), period.startDate());
        assertEquals("annual", DepreciationService.resolvePeriodType("2026-A"));
    }

    @Test
    void rejectsMismatchedPeriodEndDate() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DepreciationPeriod.from("2026-02", LocalDate.of(2026, 2, 27)));

        assertEquals("Period end date must match the selected depreciation period", error.getMessage());
    }
}
