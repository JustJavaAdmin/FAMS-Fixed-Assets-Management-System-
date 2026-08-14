package com.example.fams.depreciation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DepreciationParametersRepository extends JpaRepository<DepreciationParameters, Long> {

    // Find parameters for a specific asset
    Optional<DepreciationParameters> findByAssetIdAndIsActiveTrue(Long assetId);

    @Query("""
            select p from DepreciationParameters p
            where p.category = :category
              and p.assetId is null
              and p.isActive = true
              and p.effectiveFromDate <= :asOfDate
              and (p.effectiveToDate is null or p.effectiveToDate >= :asOfDate)
            order by p.effectiveFromDate desc, p.id desc
            """)
    List<DepreciationParameters> findEffectiveCategoryParameters(@Param("category") String category,
                                                                 @Param("asOfDate") LocalDate asOfDate);

    // Find all active parameters that apply to a given date
    List<DepreciationParameters> findByIsActiveTrueAndEffectiveFromDateLessThanEqualAndEffectiveToDateIsNullOrEffectiveToDateGreaterThanEqual(
            LocalDate date1, LocalDate date2);

    @Query("""
            select p from DepreciationParameters p
            where p.assetId = :assetId
              and p.isActive = true
              and p.effectiveFromDate <= :asOfDate
              and (p.effectiveToDate is null or p.effectiveToDate >= :asOfDate)
            order by p.effectiveFromDate desc, p.id desc
            """)
    List<DepreciationParameters> findEffectiveAssetParameters(@Param("assetId") Long assetId,
                                                              @Param("asOfDate") LocalDate asOfDate);

}

