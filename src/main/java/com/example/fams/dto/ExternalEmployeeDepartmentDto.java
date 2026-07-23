package com.example.fams.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalEmployeeDepartmentDto {
    private Long id;
    private String code;
    private String name;
    private Long companyId;
    private Long parentDepartmentId;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
    private ExternalJobGradeDto jobGrade;
    private ExternalJobStepDto jobStep;
    private ExternalPayGroupDto payGroup;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalJobGradeDto {
        private Long id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalJobStepDto {
        private Long id;
        private String stepName;
        private BigDecimal grossSalary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalPayGroupDto {
        private Long id;
        private String code;
        private String name;
        private String payFrequency;
        private String status;
    }
}
