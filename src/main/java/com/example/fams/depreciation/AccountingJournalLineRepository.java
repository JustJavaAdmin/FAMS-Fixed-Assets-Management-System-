package com.example.fams.depreciation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountingJournalLineRepository extends JpaRepository<AccountingJournalLine, Long> {
    List<AccountingJournalLine> findByBatchId(Long batchId);
    List<AccountingJournalLine> findByBatchIdOrderByAssetCodeAscAccountCodeAsc(Long batchId);
    void deleteByBatchId(Long batchId);
}
