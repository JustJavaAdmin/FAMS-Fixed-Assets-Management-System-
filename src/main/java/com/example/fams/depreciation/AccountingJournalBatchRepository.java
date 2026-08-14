package com.example.fams.depreciation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountingJournalBatchRepository extends JpaRepository<AccountingJournalBatch, Long> {
    Optional<AccountingJournalBatch> findBySourceModuleAndSourcePeriod(String sourceModule, String sourcePeriod);
    List<AccountingJournalBatch> findBySourceModuleOrderByCreatedAtDesc(String sourceModule);
}
