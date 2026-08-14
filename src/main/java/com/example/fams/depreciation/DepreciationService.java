package com.example.fams.depreciation;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.common.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DepreciationService {

    private static final String SOURCE_MODULE = "DEPRECIATION";
    private static final String DEPRECIATION_EXPENSE_ACCOUNT = "6100";
    private static final String DEPRECIATION_EXPENSE_NAME = "Depreciation Expense";
    private static final String ACCUMULATED_DEPRECIATION_ACCOUNT = "1705";
    private static final String ACCUMULATED_DEPRECIATION_NAME = "Accumulated Depreciation";

    private final DepreciationParametersRepository parametersRepository;
    private final DepreciationPostingRepository postingRepository;
    private final AssetRepository assetRepository;
    private final DepreciationCalculationService calculationService;
    private final AccountingJournalBatchRepository journalBatchRepository;
    private final AccountingJournalLineRepository journalLineRepository;

    public DepreciationService(DepreciationParametersRepository parametersRepository,
                               DepreciationPostingRepository postingRepository,
                               AssetRepository assetRepository,
                               DepreciationCalculationService calculationService,
                               AccountingJournalBatchRepository journalBatchRepository,
                               AccountingJournalLineRepository journalLineRepository) {
        this.parametersRepository = parametersRepository;
        this.postingRepository = postingRepository;
        this.assetRepository = assetRepository;
        this.calculationService = calculationService;
        this.journalBatchRepository = journalBatchRepository;
        this.journalLineRepository = journalLineRepository;
    }

    @Transactional(readOnly = true)
    public Optional<DepreciationParameters> getParametersForAsset(Long assetId, LocalDate asOfDate) {
        return parametersRepository.findEffectiveAssetParameters(assetId, asOfDate).stream().findFirst();
    }

    @Transactional
    public DepreciationParameters saveParameters(DepreciationParameters parameters) {
        if (parameters.getEffectiveFromDate() == null) {
            parameters.setEffectiveFromDate(AppClock.today());
        }
        return parametersRepository.save(parameters);
    }

    @Transactional
    public void updateParametersWithEffectiveDate(Long parametersId, DepreciationParameters newParameters) {
        DepreciationParameters current = parametersRepository.findById(parametersId)
                .orElseThrow(() -> new IllegalArgumentException("Depreciation parameters not found: " + parametersId));
        if (newParameters.getEffectiveFromDate() == null) {
            throw new IllegalArgumentException("Effective from date is required");
        }
        current.setEffectiveToDate(newParameters.getEffectiveFromDate().minusDays(1));
        current.setIsActive(false);
        parametersRepository.save(current);

        newParameters.setId(null);
        newParameters.setAssetId(current.getAssetId());
        newParameters.setCategory(current.getCategory());
        newParameters.setIsActive(true);
        parametersRepository.save(newParameters);
    }

    @Transactional(readOnly = true)
    public List<DepreciationParameters> getCategoryParameters(String category) {
        return parametersRepository.findEffectiveCategoryParameters(category, AppClock.today());
    }

    @Transactional
    public DepreciationRunResult runDepreciation(String depreciationPeriod, LocalDate periodEndDate) {
        DepreciationRunResult result = new DepreciationRunResult();
        result.setPeriod(depreciationPeriod);
        result.setRunDate(AppClock.today());

        try {
            DepreciationPeriod period = DepreciationPeriod.from(depreciationPeriod, periodEndDate);
            List<DepreciationPosting> savedPostings = new ArrayList<>();

            for (Asset asset : assetRepository.findAll()) {
                try {
                    ensurePeriodCanBeRecalculated(asset.getId(), period.code());
                    deleteExistingDraftPosting(asset.getId(), period.code());

                    DepreciationPosting posting = calculateDepreciationForAsset(asset, period);
                    if (posting == null) {
                        result.addFailedAsset(asset.getAssetCode(), "Asset is not eligible or has no depreciation parameters configured for this period");
                        continue;
                    }

                    savedPostings.add(postingRepository.save(posting));
                    result.addSuccessfulAsset(asset.getAssetCode());
                } catch (Exception e) {
                    result.addFailedAsset(asset.getAssetCode(), e.getMessage());
                }
            }

            result.setStatus(result.getFailureCount() > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED");
        } catch (Exception e) {
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    public static String resolvePeriodType(String depreciationPeriod) {
        if (depreciationPeriod == null) {
            return "annual";
        }
        if (depreciationPeriod.matches("^\\d{4}-Q[1-4]$")) {
            return "quarterly";
        }
        if (depreciationPeriod.matches("^\\d{4}-A$")) {
            return "annual";
        }
        if (depreciationPeriod.matches("^\\d{4}-\\d{2}$")) {
            return "monthly";
        }
        return "annual";
    }

    @Transactional(readOnly = true)
    public DepreciationPosting calculateDepreciationForAsset(Asset asset,
                                                             String depreciationPeriod,
                                                             LocalDate periodEndDate,
                                                             Integer fiscalYear,
                                                             String periodType) {
        return calculateDepreciationForAsset(asset, DepreciationPeriod.from(depreciationPeriod, periodEndDate));
    }

    @Transactional(readOnly = true)
    public DepreciationPosting calculateDepreciationForAsset(Asset asset, DepreciationPeriod period) {
        if (!isAssetDepreciable(asset, period)) {
            return null;
        }

        Optional<DepreciationParameters> parameters = resolveParameters(asset, period.endDate());
        if (parameters.isEmpty()) {
            return null;
        }

        DepreciationParameters params = parameters.get();
        BigDecimal assetCost = asset.getPurchaseCost();
        BigDecimal residualValue = defaultDecimal(params.getResidualValue());
        BigDecimal openingAccumulated = postingRepository
                .findFirstByAssetIdAndPeriodEndDateBeforeAndStatusNotOrderByPeriodEndDateDesc(
                        asset.getId(), period.startDate(), DepreciationPostingStatus.REVERSED)
                .map(DepreciationPosting::getClosingAccumulatedDepreciation)
                .map(this::defaultDecimal)
                .orElse(BigDecimal.ZERO);

        BigDecimal annualCharge = calculationService.calculateAnnualDepreciation(
                assetCost,
                residualValue,
                params.getUsefulLifeYears(),
                params.getMethod(),
                1,
                openingAccumulated
        );

        BigDecimal depreciationCharge = calculationService.prorateCharge(annualCharge, period.type().name().toLowerCase());
        BigDecimal depreciableLimit = assetCost.subtract(residualValue);
        BigDecimal closingAccumulated = openingAccumulated.add(depreciationCharge);
        if (closingAccumulated.compareTo(depreciableLimit) > 0) {
            closingAccumulated = depreciableLimit;
            depreciationCharge = closingAccumulated.subtract(openingAccumulated).max(BigDecimal.ZERO);
        }

        DepreciationPosting posting = new DepreciationPosting();
        posting.setAssetId(asset.getId());
        posting.setAssetCode(asset.getAssetCode());
        posting.setAssetName(asset.getName());
        posting.setCategory(asset.getCategory());
        posting.setDepartment(asset.getDepartment());
        posting.setDepreciationMethod(params.getMethod());
        posting.setDepreciationPeriod(period.code());
        posting.setPeriodType(period.type());
        posting.setPeriodNumber(period.periodNumber());
        posting.setPeriodStartDate(period.startDate());
        posting.setPeriodEndDate(period.endDate());
        posting.setFiscalYear(period.fiscalYear());
        posting.setAssetCost(assetCost);
        posting.setUsefulLifeYears(params.getUsefulLifeYears());
        posting.setResidualValue(residualValue);
        posting.setOpeningAccumulatedDepreciation(openingAccumulated);
        posting.setDepreciationCharge(depreciationCharge);
        posting.setClosingAccumulatedDepreciation(closingAccumulated);
        posting.setBookValue(calculationService.calculateBookValue(assetCost, closingAccumulated));
        posting.setFullyDepreciated(calculationService.isFullyDepreciated(assetCost, residualValue, closingAccumulated));
        posting.setStatus(DepreciationPostingStatus.DRAFT);
        return posting;
    }

    @Transactional(readOnly = true)
    public Optional<DepreciationParameters> resolveParameters(Asset asset) {
        return resolveParameters(asset, AppClock.today());
    }

    @Transactional(readOnly = true)
    public Optional<DepreciationParameters> resolveParameters(Asset asset, LocalDate asOfDate) {
        Optional<DepreciationParameters> assetParams = getParametersForAsset(asset.getId(), asOfDate);
        if (assetParams.isPresent()) {
            return assetParams;
        }
        return parametersRepository.findEffectiveCategoryParameters(asset.getCategory(), asOfDate).stream().findFirst();
    }

    @Transactional
    public void deleteExistingPosting(Long assetId, String depreciationPeriod) {
        deleteExistingDraftPosting(assetId, depreciationPeriod);
    }

    @Transactional
    public void deleteExistingDraftPosting(Long assetId, String depreciationPeriod) {
        List<DepreciationPosting> existing = postingRepository.findByAssetIdAndDepreciationPeriod(assetId, depreciationPeriod);
        List<DepreciationPosting> drafts = existing.stream()
                .filter(p -> p.getStatus() == null || p.getStatus() == DepreciationPostingStatus.DRAFT)
                .toList();
        if (!drafts.isEmpty()) {
            postingRepository.deleteAll(drafts);
        }
    }

    @Transactional(readOnly = true)
    public List<DepreciationPosting> getDepreciationHistory(Long assetId) {
        return postingRepository.findByAssetIdOrderByDepreciationPeriodDesc(assetId);
    }

    @Transactional(readOnly = true)
    public DepreciationReport getDepreciationReport(String depreciationPeriod) {
        return buildReport(postingRepository.findByDepreciationPeriodOrderByAssetCode(depreciationPeriod), depreciationPeriod);
    }

    @Transactional(readOnly = true)
    public List<DepreciationCategoryReport> getCategoryReport(String depreciationPeriod) {
        return postingRepository.findByDepreciationPeriodOrderByAssetCode(depreciationPeriod).stream()
                .collect(Collectors.groupingBy(DepreciationPosting::getCategory))
                .entrySet()
                .stream()
                .map(entry -> buildCategoryReport(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(DepreciationCategoryReport::getCategory))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DepreciationDepartmentReport> getDepartmentReport(String depreciationPeriod) {
        return postingRepository.findByDepreciationPeriodOrderByAssetCode(depreciationPeriod).stream()
                .collect(Collectors.groupingBy(DepreciationPosting::getDepartment))
                .entrySet()
                .stream()
                .map(entry -> buildDepartmentReport(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(DepreciationDepartmentReport::getDepartment))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DepreciationSummary getLatestSummary() {
        List<Asset> assets = assetRepository.findAll();
        DepreciationSummary summary = new DepreciationSummary();

        BigDecimal totalOriginalCost = BigDecimal.ZERO;
        BigDecimal totalAccumulated = BigDecimal.ZERO;
        BigDecimal totalBookValue = BigDecimal.ZERO;
        int fullyDepreciatedCount = 0;

        for (Asset asset : assets) {
            if (!isAssetDepreciable(asset)) {
                continue;
            }

            BigDecimal cost = defaultDecimal(asset.getPurchaseCost());
            totalOriginalCost = totalOriginalCost.add(cost);

            Optional<DepreciationPosting> latest = getLatestPostingForAsset(asset.getId());
            if (latest.isPresent()) {
                DepreciationPosting posting = latest.get();
                totalAccumulated = totalAccumulated.add(defaultDecimal(posting.getClosingAccumulatedDepreciation()));
                totalBookValue = totalBookValue.add(defaultDecimal(posting.getBookValue()));
                if (Boolean.TRUE.equals(posting.getFullyDepreciated())) {
                    fullyDepreciatedCount++;
                }
            } else {
                totalBookValue = totalBookValue.add(cost);
            }
        }

        summary.setTotalOriginalCost(totalOriginalCost);
        summary.setTotalAccumulatedDepreciation(totalAccumulated);
        summary.setTotalBookValue(totalBookValue);
        summary.setAssetCount(assets.size());
        summary.setConfiguredAssetCount(countConfiguredAssets(assets));
        summary.setFullyDepreciatedCount(fullyDepreciatedCount);
        return summary;
    }

    @Transactional(readOnly = true)
    public List<DepreciationPosting> getAllPostings() {
        return postingRepository.findAll().stream()
                .sorted((a, b) -> safeCreatedAt(b).compareTo(safeCreatedAt(a)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DepreciationPosting> getLatestPostingsByPeriod(int limit) {
        return getAllPostings().stream().limit(limit).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<String> getLatestDepreciationPeriod() {
        return postingRepository.findAll().stream()
                .filter(p -> p.getStatus() != DepreciationPostingStatus.REVERSED)
                .max(Comparator.comparing(DepreciationPosting::getPeriodEndDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(DepreciationPosting::getDepreciationPeriod);
    }

    @Transactional(readOnly = true)
    public DepreciationDashboardData getDashboardData(int postsToShow) {
        List<DepreciationPosting> latestPostings = getLatestPostingsByPeriod(postsToShow);
        LocalDateTime lastCalculatedAt = latestPostings.stream()
                .map(DepreciationPosting::getCreatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new DepreciationDashboardData(
                lastCalculatedAt,
                getLatestDepreciationPeriod().orElse(null),
                latestPostings,
                getLatestSummary()
        );
    }

    @Transactional(readOnly = true)
    public Optional<DepreciationPosting> getLatestPostingForAsset(Long assetId) {
        return postingRepository.findFirstByAssetIdAndStatusNotOrderByPeriodEndDateDesc(assetId, DepreciationPostingStatus.REVERSED);
    }

    @Transactional
    public int approveDepreciationPeriod(String depreciationPeriod) {
        List<DepreciationPosting> postings = postingRepository.findByDepreciationPeriodOrderByAssetCode(depreciationPeriod);
        int approved = 0;
        for (DepreciationPosting posting : postings) {
            if (posting.getStatus() == DepreciationPostingStatus.DRAFT) {
                posting.setStatus(DepreciationPostingStatus.APPROVED);
                approved++;
            }
        }
        postingRepository.saveAll(postings);
        return approved;
    }

    @Transactional
    public AccountingJournalBatch postDepreciationPeriod(String depreciationPeriod) {
        List<DepreciationPosting> postings = postingRepository.findByDepreciationPeriodOrderByAssetCode(depreciationPeriod)
                .stream()
                .filter(p -> p.getStatus() != DepreciationPostingStatus.REVERSED)
                .toList();
        if (postings.isEmpty()) {
            throw new IllegalStateException("No depreciation postings found for period " + depreciationPeriod);
        }
        boolean allApproved = postings.stream().allMatch(p -> p.getStatus() == DepreciationPostingStatus.APPROVED);
        if (!allApproved) {
            throw new IllegalStateException("All depreciation records must be approved before posting period " + depreciationPeriod);
        }

        DepreciationPosting first = postings.getFirst();
        DepreciationPeriod period = DepreciationPeriod.from(depreciationPeriod, first.getPeriodEndDate());
        return createAccountingBatch(period, postings);
    }

    @Transactional
    public int lockDepreciationPeriod(String depreciationPeriod) {
        List<DepreciationPosting> postings = postingRepository.findByDepreciationPeriodOrderByAssetCode(depreciationPeriod);
        int locked = 0;
        for (DepreciationPosting posting : postings) {
            if (posting.getStatus() == DepreciationPostingStatus.POSTED) {
                posting.setStatus(DepreciationPostingStatus.LOCKED);
                locked++;
            }
        }
        if (locked == 0) {
            throw new IllegalStateException("No posted depreciation records found for period " + depreciationPeriod);
        }
        postingRepository.saveAll(postings);
        return locked;
    }

    @Transactional
    public int reverseDepreciationPeriod(String depreciationPeriod) {
        List<DepreciationPosting> postings = postingRepository.findByDepreciationPeriodOrderByAssetCode(depreciationPeriod);
        boolean hasLockedRecords = postings.stream().anyMatch(p -> p.getStatus() == DepreciationPostingStatus.LOCKED);
        if (hasLockedRecords) {
            throw new IllegalStateException("Locked depreciation periods cannot be reversed");
        }

        int reversed = 0;
        for (DepreciationPosting posting : postings) {
            if (posting.getStatus() == DepreciationPostingStatus.POSTED) {
                posting.setStatus(DepreciationPostingStatus.REVERSED);
                reversed++;
            }
        }
        if (reversed == 0) {
            throw new IllegalStateException("No posted depreciation records found for period " + depreciationPeriod);
        }
        postingRepository.saveAll(postings);
        journalBatchRepository.findBySourceModuleAndSourcePeriod(SOURCE_MODULE, depreciationPeriod)
                .ifPresent(batch -> batch.setStatus(AccountingJournalStatus.REVERSED));
        return reversed;
    }

    private void ensurePeriodCanBeRecalculated(Long assetId, String depreciationPeriod) {
        boolean locked = postingRepository.findByAssetIdAndDepreciationPeriod(assetId, depreciationPeriod).stream()
                .anyMatch(p -> p.getStatus() == DepreciationPostingStatus.APPROVED
                        || p.getStatus() == DepreciationPostingStatus.POSTED
                        || p.getStatus() == DepreciationPostingStatus.LOCKED);
        if (locked) {
            throw new IllegalStateException("Depreciation period is already approved, posted, or locked for this asset");
        }
    }

    private AccountingJournalBatch createAccountingBatch(DepreciationPeriod period, List<DepreciationPosting> postings) {
        List<DepreciationPosting> chargeablePostings = postings.stream()
                .filter(p -> defaultDecimal(p.getDepreciationCharge()).signum() > 0)
                .toList();
        if (chargeablePostings.isEmpty()) {
            throw new IllegalStateException("No positive depreciation charges found for period " + period.code());
        }

        journalBatchRepository.findBySourceModuleAndSourcePeriod(SOURCE_MODULE, period.code()).ifPresent(existing -> {
            journalLineRepository.deleteByBatchId(existing.getId());
            journalBatchRepository.delete(existing);
            journalBatchRepository.flush();
        });

        BigDecimal totalCharge = chargeablePostings.stream()
                .map(DepreciationPosting::getDepreciationCharge)
                .map(this::defaultDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AccountingJournalBatch batch = new AccountingJournalBatch();
        batch.setBatchNumber("DEP-" + period.code() + "-" + AppClock.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        batch.setSourcePeriod(period.code());
        batch.setEntryDate(period.endDate());
        batch.setTotalDebit(totalCharge);
        batch.setTotalCredit(totalCharge);
        batch.setStatus(AccountingJournalStatus.POSTED);
        AccountingJournalBatch savedBatch = journalBatchRepository.save(batch);

        List<AccountingJournalLine> lines = new ArrayList<>();
        for (DepreciationPosting posting : chargeablePostings) {
            lines.add(journalLine(savedBatch.getId(), posting, DEPRECIATION_EXPENSE_ACCOUNT, DEPRECIATION_EXPENSE_NAME,
                    posting.getDepreciationCharge(), BigDecimal.ZERO));
            lines.add(journalLine(savedBatch.getId(), posting, ACCUMULATED_DEPRECIATION_ACCOUNT, ACCUMULATED_DEPRECIATION_NAME,
                    BigDecimal.ZERO, posting.getDepreciationCharge()));
        }
        for (DepreciationPosting posting : postings) {
            posting.setJournalBatchId(savedBatch.getId());
            posting.setStatus(DepreciationPostingStatus.POSTED);
        }

        journalLineRepository.saveAll(lines);
        postingRepository.saveAll(postings);
        return savedBatch;
    }

    private AccountingJournalLine journalLine(Long batchId,
                                              DepreciationPosting posting,
                                              String accountCode,
                                              String accountName,
                                              BigDecimal debit,
                                              BigDecimal credit) {
        AccountingJournalLine line = new AccountingJournalLine();
        line.setBatchId(batchId);
        line.setAssetCode(posting.getAssetCode());
        line.setAccountCode(accountCode);
        line.setAccountName(accountName);
        line.setDebit(defaultDecimal(debit));
        line.setCredit(defaultDecimal(credit));
        line.setDepartment(posting.getDepartment());
        line.setCategory(posting.getCategory());
        line.setNarration("Depreciation for " + posting.getAssetCode() + " in " + posting.getDepreciationPeriod());
        return line;
    }

    private DepreciationReport buildReport(List<DepreciationPosting> postings, String period) {
        DepreciationReport report = new DepreciationReport();
        report.setPeriod(period);
        report.setPostings(postings);
        report.setTotalOriginalCost(sum(postings, DepreciationPosting::getAssetCost));
        report.setTotalAccumulatedDepreciation(sum(postings, DepreciationPosting::getClosingAccumulatedDepreciation));
        report.setTotalDepreciationCharge(sum(postings, DepreciationPosting::getDepreciationCharge));
        report.setTotalBookValue(sum(postings, DepreciationPosting::getBookValue));
        report.setAssetCount(postings.size());
        report.setFullyDepreciatedCount((int) postings.stream().filter(p -> Boolean.TRUE.equals(p.getFullyDepreciated())).count());
        return report;
    }

    private DepreciationCategoryReport buildCategoryReport(String category, List<DepreciationPosting> postings) {
        DepreciationCategoryReport report = new DepreciationCategoryReport();
        report.setCategory(category);
        report.setPostings(postings);
        report.setTotalOriginalCost(sum(postings, DepreciationPosting::getAssetCost));
        report.setTotalAccumulatedDepreciation(sum(postings, DepreciationPosting::getClosingAccumulatedDepreciation));
        report.setTotalDepreciationCharge(sum(postings, DepreciationPosting::getDepreciationCharge));
        report.setTotalBookValue(sum(postings, DepreciationPosting::getBookValue));
        report.setAssetCount(postings.size());
        return report;
    }

    private DepreciationDepartmentReport buildDepartmentReport(String department, List<DepreciationPosting> postings) {
        DepreciationDepartmentReport report = new DepreciationDepartmentReport();
        report.setDepartment(department);
        report.setPostings(postings);
        report.setTotalOriginalCost(sum(postings, DepreciationPosting::getAssetCost));
        report.setTotalAccumulatedDepreciation(sum(postings, DepreciationPosting::getClosingAccumulatedDepreciation));
        report.setTotalDepreciationCharge(sum(postings, DepreciationPosting::getDepreciationCharge));
        report.setTotalBookValue(sum(postings, DepreciationPosting::getBookValue));
        report.setAssetCount(postings.size());
        return report;
    }

    private boolean isAssetDepreciable(Asset asset) {
        if (asset == null || asset.getPurchaseCost() == null || asset.getPurchaseCost().signum() <= 0) {
            return false;
        }
        String status = asset.getStatus();
        return status != null
                && !status.equalsIgnoreCase("Disposed")
                && !status.equalsIgnoreCase("Scrapped")
                && !status.equalsIgnoreCase("Retired");
    }

    private boolean isAssetDepreciable(Asset asset, DepreciationPeriod period) {
        return isAssetDepreciable(asset)
                && (asset.getPurchaseDate() == null || !asset.getPurchaseDate().isAfter(period.endDate()));
    }

    private int countConfiguredAssets(List<Asset> assets) {
        List<DepreciationParameters> allParams = parametersRepository.findAll();
        java.util.Set<Long> assetParamIds = allParams.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()) && p.getAssetId() != null)
                .map(DepreciationParameters::getAssetId)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> categoryParams = allParams.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()) && p.getAssetId() == null && p.getCategory() != null)
                .map(DepreciationParameters::getCategory)
                .collect(java.util.stream.Collectors.toSet());

        int count = 0;
        for (Asset asset : assets) {
            if (assetParamIds.contains(asset.getId())
                    || (asset.getCategory() != null && categoryParams.contains(asset.getCategory()))) {
                count++;
            }
        }
        return count;
    }

    private BigDecimal sum(List<DepreciationPosting> postings,
                           java.util.function.Function<DepreciationPosting, BigDecimal> extractor) {
        return postings.stream().map(extractor).map(this::defaultDecimal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private LocalDateTime safeCreatedAt(DepreciationPosting posting) {
        return posting.getCreatedAt() != null ? posting.getCreatedAt() : LocalDateTime.MIN;
    }
}
