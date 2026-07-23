package com.example.fams.department;

import com.example.fams.assets.AssetRepository;
import com.example.fams.dto.ExternalEmployeeDepartmentDto;
import com.example.fams.dto.StructureSyncStatusDto;
import com.example.fams.external.StructureSyncService;
import com.example.fams.lifecycle.AssetLifecycleService;
import com.example.fams.lifecycle.AssetLifecycleWorkflowRepository;
import com.example.fams.maintenance.MaintenanceTaskRepository;
import com.example.fams.organization.DepartmentHeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DepartmentControllerTest {

    @Mock private AssetRepository assetRepository;
    @Mock private DepartmentHeadRepository departmentHeadRepository;
    @Mock private AssetLifecycleWorkflowRepository workflowRepository;
    @Mock private AssetLifecycleService assetLifecycleService;
    @Mock private MaintenanceTaskRepository maintenanceTaskRepository;
    @Mock private StructureSyncService structureSyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        DepartmentController controller = new DepartmentController(
                assetRepository,
                departmentHeadRepository,
                workflowRepository,
                assetLifecycleService,
                maintenanceTaskRepository,
                structureSyncService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void syncStatusEndpointReturnsCurrentSyncStatus() throws Exception {
        when(structureSyncService.getSyncStatus()).thenReturn(new StructureSyncStatusDto(
                true,
                LocalDateTime.parse("2026-07-22T10:15:30"),
                LocalDateTime.parse("2026-07-22T10:10:00"),
                null,
                1L,
                2L,
                1L
        ));

        mockMvc.perform(get("/api/company-structure/sync-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncedAtLeastOnce", is(true)))
                .andExpect(jsonPath("$.sourceCompanyId", is(1L)));
    }

    @Test
    void myDepartmentEndpointReturnsCachedDepartment() throws Exception {
        when(structureSyncService.resolveCurrentUserDepartment()).thenReturn(Optional.of(
                ExternalEmployeeDepartmentDto.builder()
                        .id(1L)
                        .code("100454")
                        .name("Drivers")
                        .companyId(1L)
                        .effectiveFrom(LocalDate.parse("2026-07-10"))
                        .status("ACTIVE")
                        .jobGrade(ExternalEmployeeDepartmentDto.ExternalJobGradeDto.builder().id(3L).name("ASK ventures").build())
                        .jobStep(ExternalEmployeeDepartmentDto.ExternalJobStepDto.builder().id(6L).stepName("STEP-20416000").grossSalary(new BigDecimal("1701333.33")).build())
                        .payGroup(ExternalEmployeeDepartmentDto.ExternalPayGroupDto.builder().id(1L).code("PG-754440E4").name("15 tons drivers").payFrequency("MONTHLY").status("ACTIVE").build())
                        .build()
        ));

        mockMvc.perform(get("/api/company-structure/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Drivers")))
                .andExpect(jsonPath("$.jobGrade.name", is("ASK ventures")));
    }
}
