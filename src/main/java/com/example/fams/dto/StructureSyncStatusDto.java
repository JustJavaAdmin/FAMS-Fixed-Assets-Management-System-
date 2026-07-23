package com.example.fams.dto;

import java.time.LocalDateTime;

public record StructureSyncStatusDto(
        boolean syncedAtLeastOnce,
        LocalDateTime lastSuccessfulSync,
        LocalDateTime lastAttemptedSync,
        String lastError,
        Long sourceCompanyId,
        Long departmentCount,
        Long headCount
) {
}
