package com.example.fams.depreciation;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DepreciationServiceTest {

    private DepreciationParametersRepository parametersRepository;
    private DepreciationPostingRepository postingRepository;
    private AssetRepository assetRepository;
    private AccountingJournalBatchRepository journalBatchRepository;
    private AccountingJournalLineRepository journalLineRepository;
    private DepreciationService service;

    @BeforeEach
    void setUp() {
        parametersRepository = mock(DepreciationParametersRepository.class);
        postingRepository = mock(DepreciationPostingRepository.class);
        assetRepository = mock(AssetRepository.class);
        journalBatchRepository = mock(AccountingJournalBatchRepository.class);
        journalLineRepository = mock(AccountingJournalLineRepository.class);
        service = new DepreciationService(
                parametersRepository,
                postingRepository,
                assetRepository,
                new DepreciationCalculationService(),
                journalBatchRepository,
                journalLineRepository
        );

        when(postingRepository.save(any(DepreciationPosting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(journalBatchRepository.findBySourceModuleAndSourcePeriod(anyString(), anyString())).thenReturn(Optional.empty());
        when(journalBatchRepository.save(any(AccountingJournalBatch.class))).thenAnswer(invocation -> {
            AccountingJournalBatch batch = invocation.getArgument(0);
            ReflectionTestUtils.setField(batch, "id", 99L);
            return batch;
        });
        when(journalLineRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rerunUsesPreviousPeriodOpeningBalanceNotDeletedCurrentDraft() {
        Asset asset = asset(1L, "AST-001", LocalDate.of(2025, 1, 1), "Active", "IT Equipment", new BigDecimal("1200.00"));
        DepreciationParameters params = parameters(1L, DepreciationMethod.STRAIGHT_LINE, 12, BigDecimal.ZERO, LocalDate.of(2025, 1, 1));
        DepreciationPosting currentDraft = posting(1L, "2026-01", LocalDate.of(2026, 1, 31), new BigDecimal("100.00"), DepreciationPostingStatus.DRAFT);
        DepreciationPosting priorPosted = posting(1L, "2025-12", LocalDate.of(2025, 12, 31), new BigDecimal("50.00"), DepreciationPostingStatus.POSTED);

        when(assetRepository.findAll()).thenReturn(List.of(asset));
        when(postingRepository.findByAssetIdAndDepreciationPeriod(1L, "2026-01")).thenReturn(List.of(currentDraft));
        when(parametersRepository.findEffectiveAssetParameters(1L, LocalDate.of(2026, 1, 31))).thenReturn(List.of(params));
        when(postingRepository.findFirstByAssetIdAndPeriodEndDateBeforeAndStatusNotOrderByPeriodEndDateDesc(
                1L, LocalDate.of(2026, 1, 1), DepreciationPostingStatus.REVERSED)).thenReturn(Optional.of(priorPosted));

        DepreciationRunResult result = service.runDepreciation("2026-01", LocalDate.of(2026, 1, 31));

        assertEquals("COMPLETED", result.getStatus());
        ArgumentCaptor<DepreciationPosting> postingCaptor = ArgumentCaptor.forClass(DepreciationPosting.class);
        verify(postingRepository).save(postingCaptor.capture());
        DepreciationPosting saved = postingCaptor.getValue();
        assertEquals(new BigDecimal("50.00"), saved.getOpeningAccumulatedDepreciation());
        assertEquals(new BigDecimal("8.33"), saved.getDepreciationCharge());
        assertEquals(new BigDecimal("58.33"), saved.getClosingAccumulatedDepreciation());
        verify(postingRepository).deleteAll(List.of(currentDraft));
    }

    @Test
    void postedPeriodCannotBeRecalculated() {
        Asset asset = asset(1L, "AST-001", LocalDate.of(2025, 1, 1), "Active", "IT Equipment", new BigDecimal("1200.00"));
        DepreciationPosting existingPosted = posting(1L, "2026-01", LocalDate.of(2026, 1, 31), new BigDecimal("50.00"), DepreciationPostingStatus.POSTED);

        when(assetRepository.findAll()).thenReturn(List.of(asset));
        when(postingRepository.findByAssetIdAndDepreciationPeriod(1L, "2026-01")).thenReturn(List.of(existingPosted));

        DepreciationRunResult result = service.runDepreciation("2026-01", LocalDate.of(2026, 1, 31));

        assertEquals("COMPLETED_WITH_ERRORS", result.getStatus());
        assertEquals(1, result.getFailureCount());
        verify(postingRepository, never()).save(any(DepreciationPosting.class));
        verify(journalBatchRepository, never()).save(any(AccountingJournalBatch.class));
    }

    @Test
    void successfulRunCreatesDraftPostingWithoutJournalBatch() {
        Asset asset = asset(1L, "AST-001", LocalDate.of(2025, 1, 1), "Active", "IT Equipment", new BigDecimal("1200.00"));
        DepreciationParameters params = parameters(1L, DepreciationMethod.STRAIGHT_LINE, 12, BigDecimal.ZERO, LocalDate.of(2025, 1, 1));

        when(assetRepository.findAll()).thenReturn(List.of(asset));
        when(postingRepository.findByAssetIdAndDepreciationPeriod(1L, "2026-01")).thenReturn(List.of());
        when(parametersRepository.findEffectiveAssetParameters(1L, LocalDate.of(2026, 1, 31))).thenReturn(List.of(params));
        when(postingRepository.findFirstByAssetIdAndPeriodEndDateBeforeAndStatusNotOrderByPeriodEndDateDesc(
                1L, LocalDate.of(2026, 1, 1), DepreciationPostingStatus.REVERSED)).thenReturn(Optional.empty());

        DepreciationRunResult result = service.runDepreciation("2026-01", LocalDate.of(2026, 1, 31));

        assertEquals("COMPLETED", result.getStatus());
        ArgumentCaptor<DepreciationPosting> postingCaptor = ArgumentCaptor.forClass(DepreciationPosting.class);
        verify(postingRepository).save(postingCaptor.capture());
        assertEquals(DepreciationPostingStatus.DRAFT, postingCaptor.getValue().getStatus());
        verify(journalBatchRepository, never()).save(any(AccountingJournalBatch.class));
    }

    @Test
    void approveDepreciationPeriodUpdatesOnlyDraftPostings() {
        DepreciationPosting draft = posting(1L, "2026-01", LocalDate.of(2026, 1, 31), new BigDecimal("8.33"), DepreciationPostingStatus.DRAFT);
        DepreciationPosting alreadyApproved = posting(2L, "2026-01", LocalDate.of(2026, 1, 31), new BigDecimal("10.00"), DepreciationPostingStatus.APPROVED);

        when(postingRepository.findByDepreciationPeriodOrderByAssetCode("2026-01")).thenReturn(List.of(draft, alreadyApproved));

        int approvedCount = service.approveDepreciationPeriod("2026-01");

        assertEquals(1, approvedCount);
        assertEquals(DepreciationPostingStatus.APPROVED, draft.getStatus());
        assertEquals(DepreciationPostingStatus.APPROVED, alreadyApproved.getStatus());
        verify(postingRepository).saveAll(List.of(draft, alreadyApproved));
    }

    @Test
    void postingApprovedPeriodCreatesBalancedAccountingJournalLines() {
        DepreciationPosting approvedPosting = posting(1L, "2026-01", LocalDate.of(2026, 1, 31), new BigDecimal("8.33"), DepreciationPostingStatus.APPROVED);

        when(postingRepository.findByDepreciationPeriodOrderByAssetCode("2026-01")).thenReturn(List.of(approvedPosting));

        AccountingJournalBatch batch = service.postDepreciationPeriod("2026-01");

        assertNotNull(batch);
        ArgumentCaptor<AccountingJournalBatch> batchCaptor = ArgumentCaptor.forClass(AccountingJournalBatch.class);
        verify(journalBatchRepository).save(batchCaptor.capture());
        assertEquals(new BigDecimal("8.33"), batchCaptor.getValue().getTotalDebit());
        assertEquals(new BigDecimal("8.33"), batchCaptor.getValue().getTotalCredit());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AccountingJournalLine>> lineCaptor = ArgumentCaptor.forClass(List.class);
        verify(journalLineRepository).saveAll(lineCaptor.capture());
        List<AccountingJournalLine> lines = lineCaptor.getValue();
        assertEquals(2, lines.size());
        assertEquals(new BigDecimal("8.33"), lines.stream().map(AccountingJournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("8.33"), lines.stream().map(AccountingJournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(DepreciationPostingStatus.POSTED, approvedPosting.getStatus());
    }

    @Test
    void postingRejectsMixedStatusPeriod() {
        DepreciationPosting approvedPosting = posting(1L, "2026-01", LocalDate.of(2026, 1, 31), new BigDecimal("8.33"), DepreciationPostingStatus.APPROVED);
        DepreciationPosting draftPosting = posting(2L, "2026-01", LocalDate.of(2026, 1, 31), new BigDecimal("10.00"), DepreciationPostingStatus.DRAFT);

        when(postingRepository.findByDepreciationPeriodOrderByAssetCode("2026-01")).thenReturn(List.of(approvedPosting, draftPosting));

        assertThrows(IllegalStateException.class, () -> service.postDepreciationPeriod("2026-01"));
        verify(journalBatchRepository, never()).save(any(AccountingJournalBatch.class));
    }

    @Test
    void lockPeriodUpdatesPostedRecords() {
        DepreciationPosting posted = posting(1L, "2026-01", LocalDate.of(2026, 1, 31), new BigDecimal("8.33"), DepreciationPostingStatus.POSTED);

        when(postingRepository.findByDepreciationPeriodOrderByAssetCode("2026-01")).thenReturn(List.of(posted));

        int lockedCount = service.lockDepreciationPeriod("2026-01");

        assertEquals(1, lockedCount);
        assertEquals(DepreciationPostingStatus.LOCKED, posted.getStatus());
        verify(postingRepository).saveAll(List.of(posted));
    }

    @Test
    void reversePeriodUpdatesPostedRecordsAndJournalBatch() {
        DepreciationPosting posted = posting(1L, "2026-01", LocalDate.of(2026, 1, 31), new BigDecimal("8.33"), DepreciationPostingStatus.POSTED);
        AccountingJournalBatch batch = new AccountingJournalBatch();
        batch.setBatchNumber("DEP-2026-01-20260814170000");
        batch.setSourcePeriod("2026-01");
        batch.setEntryDate(LocalDate.of(2026, 1, 31));
        batch.setStatus(AccountingJournalStatus.POSTED);

        when(postingRepository.findByDepreciationPeriodOrderByAssetCode("2026-01")).thenReturn(List.of(posted));
        when(journalBatchRepository.findBySourceModuleAndSourcePeriod("DEPRECIATION", "2026-01")).thenReturn(Optional.of(batch));

        int reversedCount = service.reverseDepreciationPeriod("2026-01");

        assertEquals(1, reversedCount);
        assertEquals(DepreciationPostingStatus.REVERSED, posted.getStatus());
        assertEquals(AccountingJournalStatus.REVERSED, batch.getStatus());
        verify(postingRepository).saveAll(List.of(posted));
    }

    @Test
    void reverseLockedPeriodIsRejected() {
        DepreciationPosting locked = posting(1L, "2026-01", LocalDate.of(2026, 1, 31), new BigDecimal("8.33"), DepreciationPostingStatus.LOCKED);

        when(postingRepository.findByDepreciationPeriodOrderByAssetCode("2026-01")).thenReturn(List.of(locked));

        assertThrows(IllegalStateException.class, () -> service.reverseDepreciationPeriod("2026-01"));
        verify(postingRepository, never()).saveAll(anyList());
    }

    @Test
    void journalReportReturnsDepreciationBatchesWithLineCounts() {
        AccountingJournalBatch batch = journalBatch(99L, "DEP-2026-01-20260814170000", "2026-01", AccountingJournalStatus.POSTED);
        AccountingJournalLine debit = journalLine(99L, "AST-001", "6100", "Depreciation Expense", new BigDecimal("8.33"), BigDecimal.ZERO);
        AccountingJournalLine credit = journalLine(99L, "AST-001", "1705", "Accumulated Depreciation", BigDecimal.ZERO, new BigDecimal("8.33"));

        when(journalBatchRepository.findBySourceModuleOrderByCreatedAtDesc("DEPRECIATION")).thenReturn(List.of(batch));
        when(journalLineRepository.findByBatchId(99L)).thenReturn(List.of(debit, credit));

        List<AccountingJournalBatchReport> reports = service.getDepreciationJournalBatches("2026-01", null, null, AccountingJournalStatus.POSTED, null);

        assertEquals(1, reports.size());
        assertEquals("DEP-2026-01-20260814170000", reports.getFirst().getBatchNumber());
        assertEquals(2, reports.getFirst().getLineCount());
    }

    @Test
    void journalExportIncludesDebitAndCreditLines() {
        AccountingJournalBatch batch = journalBatch(99L, "DEP-2026-01-20260814170000", "2026-01", AccountingJournalStatus.POSTED);
        AccountingJournalLine debit = journalLine(99L, "AST-001", "6100", "Depreciation Expense", new BigDecimal("8.33"), BigDecimal.ZERO);
        AccountingJournalLine credit = journalLine(99L, "AST-001", "1705", "Accumulated Depreciation", BigDecimal.ZERO, new BigDecimal("8.33"));

        when(journalBatchRepository.findBySourceModuleOrderByCreatedAtDesc("DEPRECIATION")).thenReturn(List.of(batch));
        when(journalLineRepository.findByBatchId(99L)).thenReturn(List.of(debit, credit));
        when(journalLineRepository.findByBatchIdOrderByAssetCodeAscAccountCodeAsc(99L)).thenReturn(List.of(credit, debit));
        when(journalBatchRepository.findById(99L)).thenReturn(Optional.of(batch));

        String csv = service.exportDepreciationJournalCsv("2026-01", null, null, AccountingJournalStatus.POSTED, null);

        assertTrue(csv.startsWith("Batch Number,Source Period,Entry Date"));
        assertTrue(csv.contains("6100,Depreciation Expense,8.33,0"));
        assertTrue(csv.contains("1705,Accumulated Depreciation,0,8.33"));
    }

    private Asset asset(Long id, String code, LocalDate purchaseDate, String status, String category, BigDecimal cost) {
        Asset asset = new Asset();
        ReflectionTestUtils.setField(asset, "id", id);
        asset.setAssetCode(code);
        asset.setName("Laptop");
        asset.setPurchaseDate(purchaseDate);
        asset.setStatus(status);
        asset.setCategory(category);
        asset.setDepartment("Finance");
        asset.setBranch("HQ");
        asset.setPurchaseCost(cost);
        return asset;
    }

    private DepreciationParameters parameters(Long assetId,
                                              DepreciationMethod method,
                                              int usefulLifeYears,
                                              BigDecimal residualValue,
                                              LocalDate effectiveFrom) {
        DepreciationParameters params = new DepreciationParameters();
        params.setAssetId(assetId);
        params.setMethod(method);
        params.setUsefulLifeYears(usefulLifeYears);
        params.setResidualValue(residualValue);
        params.setEffectiveFromDate(effectiveFrom);
        params.setIsActive(true);
        return params;
    }

    private DepreciationPosting posting(Long assetId,
                                        String period,
                                        LocalDate periodEndDate,
                                        BigDecimal closingAccumulated,
                                        DepreciationPostingStatus status) {
        DepreciationPosting posting = new DepreciationPosting();
        posting.setAssetId(assetId);
        posting.setAssetCode("AST-001");
        posting.setAssetName("Laptop");
        posting.setCategory("IT Equipment");
        posting.setDepartment("Finance");
        posting.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        posting.setDepreciationPeriod(period);
        posting.setPeriodType(DepreciationPeriodType.MONTHLY);
        posting.setPeriodNumber(1);
        posting.setPeriodStartDate(periodEndDate.withDayOfMonth(1));
        posting.setPeriodEndDate(periodEndDate);
        posting.setFiscalYear(periodEndDate.getYear());
        posting.setAssetCost(new BigDecimal("1200.00"));
        posting.setOpeningAccumulatedDepreciation(BigDecimal.ZERO);
        posting.setDepreciationCharge(closingAccumulated);
        posting.setClosingAccumulatedDepreciation(closingAccumulated);
        posting.setBookValue(new BigDecimal("1200.00").subtract(closingAccumulated));
        posting.setResidualValue(BigDecimal.ZERO);
        posting.setUsefulLifeYears(12);
        posting.setStatus(status);
        return posting;
    }

    private AccountingJournalBatch journalBatch(Long id,
                                                String batchNumber,
                                                String sourcePeriod,
                                                AccountingJournalStatus status) {
        AccountingJournalBatch batch = new AccountingJournalBatch();
        ReflectionTestUtils.setField(batch, "id", id);
        batch.setBatchNumber(batchNumber);
        batch.setSourcePeriod(sourcePeriod);
        batch.setEntryDate(LocalDate.of(2026, 1, 31));
        batch.setTotalDebit(new BigDecimal("8.33"));
        batch.setTotalCredit(new BigDecimal("8.33"));
        batch.setStatus(status);
        return batch;
    }

    private AccountingJournalLine journalLine(Long batchId,
                                              String assetCode,
                                              String accountCode,
                                              String accountName,
                                              BigDecimal debit,
                                              BigDecimal credit) {
        AccountingJournalLine line = new AccountingJournalLine();
        line.setBatchId(batchId);
        line.setAssetCode(assetCode);
        line.setAccountCode(accountCode);
        line.setAccountName(accountName);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setDepartment("Finance");
        line.setCategory("IT Equipment");
        line.setNarration("Depreciation for " + assetCode + " in 2026-01");
        return line;
    }
}
