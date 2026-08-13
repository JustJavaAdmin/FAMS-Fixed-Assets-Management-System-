package com.example.fams.depreciation;

import com.example.fams.common.AppClock;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounting_journal_batches")
public class AccountingJournalBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String batchNumber;

    @Column(nullable = false, length = 40)
    private String sourceModule = "DEPRECIATION";

    @Column(nullable = false, length = 32)
    private String sourcePeriod;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountingJournalStatus status = AccountingJournalStatus.POSTED;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void beforeCreate() {
        createdAt = AppClock.now();
    }

    public Long getId() {
        return id;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    public void setSourceModule(String sourceModule) {
        this.sourceModule = sourceModule;
    }

    public String getSourcePeriod() {
        return sourcePeriod;
    }

    public void setSourcePeriod(String sourcePeriod) {
        this.sourcePeriod = sourcePeriod;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public void setTotalDebit(BigDecimal totalDebit) {
        this.totalDebit = totalDebit;
    }

    public BigDecimal getTotalCredit() {
        return totalCredit;
    }

    public void setTotalCredit(BigDecimal totalCredit) {
        this.totalCredit = totalCredit;
    }

    public AccountingJournalStatus getStatus() {
        return status;
    }

    public void setStatus(AccountingJournalStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
