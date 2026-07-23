package com.example.fams.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentDTO {
    private Long id;
    private Long externalDepartmentId;
    private Long parentExternalDepartmentId;
    private Long companyId;
    private String companyName;
    private Long branchId;
    private String branchName;
    private String name;
    private String description;
    private String departmentCode;
    private String budget;
    private java.time.LocalDate effectiveFrom;
    private java.time.LocalDate effectiveTo;
    private Long departmentHeadId;
    private String departmentHeadName;
    private String syncSource;
    private java.time.LocalDateTime lastSyncedAt;
    private String status;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

