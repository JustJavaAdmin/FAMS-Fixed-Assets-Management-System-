package com.example.fams.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalCompanyStructureDto {

    private Long id;
    private String name;
    private String code;
    private String status;
    private Long parentCompanyId;
    private List<ExternalDepartmentDto> departments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalDepartmentDto {
        private Long id;
        private String code;
        private String name;
        private Long parentDepartmentId;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private String status;
        private Long departmentHeadId;
        private String departmentHeadName;
        private List<ExternalEmployeeDto> employees;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalEmployeeDto {
        private Long id;
        private String name;
        private String email;
        private String employeeCode;
    }
}
