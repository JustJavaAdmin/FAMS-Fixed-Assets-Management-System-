package com.example.fams.external;

import com.example.fams.aau.keycloak.KeycloakAdminService;
import com.example.fams.aau.keycloak.SyncedUser;
import com.example.fams.aau.keycloak.SyncedUserRepository;
import com.example.fams.dto.ExternalCompanyStructureDto;
import com.example.fams.organization.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StructureSyncServiceTest {

    @Mock private CompanyStructureClient client;
    @Mock private StructureSyncProperties properties;
    @Mock private CompanyRepository companyRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private DepartmentHeadRepository departmentHeadRepository;
    @Mock private SyncedUserRepository syncedUserRepository;
    @Mock private KeycloakAdminService keycloakAdminService;

    private StructureSyncService service;

    @BeforeEach
    void setUp() {
        when(properties.getCompanyId()).thenReturn(1L);
        service = new StructureSyncService(
                client,
                properties,
                companyRepository,
                branchRepository,
                locationRepository,
                departmentRepository,
                departmentHeadRepository,
                syncedUserRepository,
                keycloakAdminService,
                "fams"
        );
    }

    @Test
    void syncNowPersistsExternalDepartmentAndAssignsHeadRole() {
        ExternalCompanyStructureDto.ExternalEmployeeDto employee = ExternalCompanyStructureDto.ExternalEmployeeDto.builder()
                .id(15L)
                .name("Ayooh Faz")
                .email("ayooh.faz@company.com")
                .employeeCode("EMP015")
                .build();
        ExternalCompanyStructureDto.ExternalDepartmentDto department = ExternalCompanyStructureDto.ExternalDepartmentDto.builder()
                .id(3L)
                .code("100455")
                .name("Cooks")
                .departmentHeadId(15L)
                .departmentHeadName("Ayooh Faz")
                .employees(List.of(employee))
                .build();
        ExternalCompanyStructureDto remote = ExternalCompanyStructureDto.builder()
                .id(1L)
                .name("Google llc")
                .code("glc")
                .status("ACTIVE")
                .departments(List.of(department))
                .build();

        when(client.getCompanyStructure()).thenReturn(remote);
        when(companyRepository.findByExternalCompanyId(1L)).thenReturn(Optional.empty());
        when(companyRepository.save(any())).thenAnswer(invocation -> (Company) invocation.getArgument(0));
        when(locationRepository.findByNameAndCompanyIdAndIsActiveTrue(any(), any())).thenReturn(Optional.empty());
        when(locationRepository.save(any())).thenAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            location.setId(20L);
            return location;
        });
        when(branchRepository.findByBranchCodeAndIsActiveTrue(any())).thenReturn(Optional.empty());
        when(branchRepository.save(any())).thenAnswer(invocation -> {
            Branch branch = invocation.getArgument(0);
            branch.setId(30L);
            return branch;
        });
        when(departmentRepository.findByExternalDepartmentId(3L)).thenReturn(Optional.empty());
        when(departmentRepository.save(any())).thenAnswer(invocation -> {
            Department dept = invocation.getArgument(0);
            dept.setId(40L);
            return dept;
        });
        when(departmentRepository.findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(departmentHeadRepository.findByDepartmentIdAndIsActiveTrueOrderByAssignedAtDesc(any())).thenReturn(List.of());
        when(departmentHeadRepository.findAllActiveDepartmentHeads()).thenReturn(List.of());
        SyncedUser syncedUser = SyncedUser.builder()
                .keycloakId("kc-123")
                .username("ayooh.faz@company.com")
                .email("ayooh.faz@company.com")
                .enabled(true)
                .build();
        when(syncedUserRepository.findAll()).thenReturn(List.of(syncedUser));

        service.syncNow();

        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(companyCaptor.capture());
        assertEquals(1L, companyCaptor.getValue().getExternalCompanyId());
        assertEquals("Google llc", companyCaptor.getValue().getName());

        ArgumentCaptor<Department> departmentCaptor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(departmentCaptor.capture());
        assertEquals(3L, departmentCaptor.getValue().getExternalDepartmentId());
        assertEquals("100455", departmentCaptor.getValue().getDepartmentCode());
        assertEquals("Ayooh Faz", departmentCaptor.getValue().getDepartmentHeadName());

        ArgumentCaptor<DepartmentHead> headCaptor = ArgumentCaptor.forClass(DepartmentHead.class);
        verify(departmentHeadRepository).save(headCaptor.capture());
        assertEquals("kc-123", headCaptor.getValue().getUserId());
        assertEquals("Ayooh Faz", headCaptor.getValue().getUserName());
        verify(keycloakAdminService).addUserToGroup("fams", "kc-123", "departmentHead");
        assertTrue(service.getSyncStatus().syncedAtLeastOnce());
    }
}
