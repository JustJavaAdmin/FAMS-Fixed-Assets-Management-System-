package com.example.fams.external;

import com.example.fams.aau.keycloak.KeycloakAdminService;
import com.example.fams.aau.keycloak.SyncedUser;
import com.example.fams.aau.keycloak.SyncedUserRepository;
import com.example.fams.dto.ExternalCompanyStructureDto;
import com.example.fams.dto.ExternalEmployeeDepartmentDto;
import com.example.fams.dto.StructureSyncStatusDto;
import com.example.fams.organization.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StructureSyncService {

    private final CompanyStructureClient client;
    private final StructureSyncProperties properties;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final LocationRepository locationRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentHeadRepository departmentHeadRepository;
    private final SyncedUserRepository syncedUserRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final String realmName;

    private volatile ExternalCompanyStructureDto lastCompanyStructure;
    private volatile LocalDateTime lastSuccessfulSync;
    private volatile LocalDateTime lastAttemptedSync;
    private volatile String lastError;
    private volatile boolean syncedAtLeastOnce;
    private volatile Map<String, ExternalEmployeeDepartmentDto> employeeDepartmentCache = Map.of();

    public StructureSyncService(
            CompanyStructureClient client,
            StructureSyncProperties properties,
            CompanyRepository companyRepository,
            BranchRepository branchRepository,
            LocationRepository locationRepository,
            DepartmentRepository departmentRepository,
            DepartmentHeadRepository departmentHeadRepository,
            SyncedUserRepository syncedUserRepository,
            KeycloakAdminService keycloakAdminService,
            @Value("${keycloak.realm}") String realmName
    ) {
        this.client = client;
        this.properties = properties;
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.locationRepository = locationRepository;
        this.departmentRepository = departmentRepository;
        this.departmentHeadRepository = departmentHeadRepository;
        this.syncedUserRepository = syncedUserRepository;
        this.keycloakAdminService = keycloakAdminService;
        this.realmName = realmName;
    }

    @Bean
    public ApplicationRunner companyStructureSyncOnStartup() {
        return args -> syncNow();
    }

    @Scheduled(fixedDelayString = "${fams.company-structure.sync-interval-ms:21600000}")
    public void scheduledSync() {
        syncNow();
    }

    @Transactional
    public synchronized void syncNow() {
        lastAttemptedSync = LocalDateTime.now();
        try {
            ExternalCompanyStructureDto remote = client.getCompanyStructure();
            if (remote == null) {
                throw new IllegalStateException("Company structure response was empty.");
            }
            lastCompanyStructure = remote;
            persistSnapshot(remote);
            rebuildEmployeeDepartmentCache(remote);
            lastSuccessfulSync = LocalDateTime.now();
            lastError = null;
            syncedAtLeastOnce = true;
            log.info("Company structure sync complete for external company id={}", remote.getId());
        } catch (Exception ex) {
            lastError = ex.getMessage();
            log.warn("Company structure sync failed: {}", ex.getMessage());
        }
    }

    public StructureSyncStatusDto getSyncStatus() {
        long departmentCount = lastCompanyStructure == null || lastCompanyStructure.getDepartments() == null
                ? 0
                : lastCompanyStructure.getDepartments().size();
        long headCount = departmentHeadRepository.findAllActiveDepartmentHeads().size();
        return new StructureSyncStatusDto(
                syncedAtLeastOnce,
                lastSuccessfulSync,
                lastAttemptedSync,
                lastError,
                properties.getCompanyId(),
                departmentCount,
                headCount
        );
    }

    public ExternalCompanyStructureDto getLastCompanyStructure() {
        return lastCompanyStructure;
    }

    public Optional<ExternalEmployeeDepartmentDto> getEmployeeDepartment(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String key = email.trim().toLowerCase();
        ExternalEmployeeDepartmentDto cached = employeeDepartmentCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            ExternalEmployeeDepartmentDto remote = client.getEmployeeDepartment(email.trim());
            if (remote != null) {
                employeeDepartmentCache = putCache(employeeDepartmentCache, key, remote);
            }
            return Optional.ofNullable(remote);
        } catch (Exception ex) {
            log.warn("Unable to resolve department for {}: {}", email, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ExternalEmployeeDepartmentDto> resolveCurrentUserDepartment() {
        for (String candidate : currentUserCandidates()) {
            Optional<ExternalEmployeeDepartmentDto> department = getEmployeeDepartment(candidate);
            if (department.isPresent()) {
                return department;
            }
        }
        return Optional.empty();
    }

    private void persistSnapshot(ExternalCompanyStructureDto remote) {
        Company company = companyRepository.findByExternalCompanyId(remote.getId())
                .orElseGet(Company::new);
        company.setExternalCompanyId(remote.getId());
        company.setName(remote.getName());
        company.setStatus(parseCompanyStatus(remote.getStatus()));
        company.setIsActive(true);
        company = companyRepository.save(company);

        Location location = ensureSyncLocation(company);
        Branch branch = ensureSyncBranch(company, location);

        Map<Long, ExternalCompanyStructureDto.ExternalEmployeeDto> employeeIndex = indexEmployees(remote);
        Set<Long> seenDepartmentIds = new LinkedHashSet<>();
        Set<String> currentHeadUserIds = new LinkedHashSet<>();
        Set<String> revokedHeadUserIds = new LinkedHashSet<>();

        List<ExternalCompanyStructureDto.ExternalDepartmentDto> departments =
                remote.getDepartments() == null ? List.of() : remote.getDepartments();
        for (ExternalCompanyStructureDto.ExternalDepartmentDto externalDepartment : departments) {
            if (externalDepartment == null || externalDepartment.getId() == null) {
                continue;
            }
            seenDepartmentIds.add(externalDepartment.getId());
            Department department = departmentRepository.findByExternalDepartmentId(externalDepartment.getId())
                    .orElseGet(Department::new);
            department.setExternalDepartmentId(externalDepartment.getId());
            department.setParentExternalDepartmentId(externalDepartment.getParentDepartmentId());
            department.setCompany(company);
            department.setBranch(branch);
            department.setName(externalDepartment.getName());
            department.setDepartmentCode(externalDepartment.getCode());
            department.setStatus(parseDepartmentStatus(externalDepartment.getStatus()));
            department.setIsActive(true);
            department.setEffectiveFrom(externalDepartment.getEffectiveFrom());
            department.setEffectiveTo(externalDepartment.getEffectiveTo());
            department.setDepartmentHeadId(externalDepartment.getDepartmentHeadId());
            department.setDepartmentHeadName(externalDepartment.getDepartmentHeadName());
            department.setSyncSource("EXTERNAL");
            department.setLastSyncedAt(LocalDateTime.now());
            departmentRepository.save(department);

            syncDepartmentHead(department, externalDepartment, employeeIndex, currentHeadUserIds, revokedHeadUserIds);
        }

        List<Department> staleDepartments = departmentRepository.findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(company.getId())
                .stream()
                .filter(department -> department.getExternalDepartmentId() != null
                        && !seenDepartmentIds.contains(department.getExternalDepartmentId()))
                .toList();
        for (Department stale : staleDepartments) {
            stale.setIsActive(false);
            stale.setStatus(Department.DepartmentStatus.INACTIVE);
            stale.setLastSyncedAt(LocalDateTime.now());
            departmentRepository.save(stale);
            deactivateDepartmentHeads(stale, revokedHeadUserIds);
        }

        reconcileDepartmentHeadGroups(currentHeadUserIds, revokedHeadUserIds);
    }

    private void syncDepartmentHead(
            Department department,
            ExternalCompanyStructureDto.ExternalDepartmentDto externalDepartment,
            Map<Long, ExternalCompanyStructureDto.ExternalEmployeeDto> employeeIndex,
            Set<String> currentHeadUserIds,
            Set<String> revokedHeadUserIds
    ) {
        Long headId = externalDepartment.getDepartmentHeadId();
        if (headId == null) {
            deactivateDepartmentHeads(department, revokedHeadUserIds);
            return;
        }

        ExternalCompanyStructureDto.ExternalEmployeeDto employee = employeeIndex.get(headId);
        String keycloakUserId = resolveKeycloakUserId(employee);
        String userEmail = employee != null ? employee.getEmail() : null;
        String userName = employee != null ? employee.getName() : externalDepartment.getDepartmentHeadName();
        String fullName = employee != null ? employee.getName() : externalDepartment.getDepartmentHeadName();

        if (keycloakUserId != null) {
            currentHeadUserIds.add(keycloakUserId);
        }

        List<DepartmentHead> activeHeads = departmentHeadRepository.findByDepartmentIdAndIsActiveTrueOrderByAssignedAtDesc(department.getId());
        DepartmentHead matched = activeHeads.stream()
                .filter(head -> keycloakUserId != null && keycloakUserId.equals(head.getUserId()))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            matched = activeHeads.stream().findFirst().orElseGet(DepartmentHead::new);
        }

        String previousUserId = matched.getUserId();
        matched.setDepartment(department);
        matched.setUserId(keycloakUserId != null ? keycloakUserId : String.valueOf(headId));
        matched.setUserName(defaultString(userName, externalDepartment.getDepartmentHeadName()));
        matched.setUserEmail(defaultString(userEmail, ""));
        matched.setFullName(defaultString(fullName, defaultString(userName, externalDepartment.getDepartmentHeadName())));
        matched.setIsPrimary(true);
        matched.setStatus(DepartmentHead.HeadStatus.ACTIVE);
        matched.setIsActive(true);
        departmentHeadRepository.save(matched);

        if (previousUserId != null && !previousUserId.isBlank() && !previousUserId.equals(matched.getUserId())) {
            revokedHeadUserIds.add(previousUserId);
        }

        for (DepartmentHead other : activeHeads) {
            if (matched.getId() == null || !matched.getId().equals(other.getId())) {
                if (other.getUserId() != null && !other.getUserId().isBlank()) {
                    revokedHeadUserIds.add(other.getUserId());
                }
                other.setIsActive(false);
                other.setStatus(DepartmentHead.HeadStatus.REMOVED);
                other.setRemovedAt(LocalDateTime.now());
                departmentHeadRepository.save(other);
            }
        }

        if (keycloakUserId != null) {
            try {
                keycloakAdminService.addUserToGroup(realmName, keycloakUserId, "departmentHead");
            } catch (Exception ex) {
                log.warn("Could not add user {} to departmentHead group: {}", keycloakUserId, ex.getMessage());
            }
        }
    }

    private void deactivateDepartmentHeads(Department department, Set<String> revokedHeadUserIds) {
        List<DepartmentHead> activeHeads = departmentHeadRepository.findByDepartmentIdAndIsActiveTrueOrderByAssignedAtDesc(department.getId());
        for (DepartmentHead head : activeHeads) {
            if (head.getUserId() != null && !head.getUserId().isBlank()) {
                revokedHeadUserIds.add(head.getUserId());
            }
            head.setIsActive(false);
            head.setStatus(DepartmentHead.HeadStatus.REMOVED);
            head.setRemovedAt(LocalDateTime.now());
            departmentHeadRepository.save(head);
        }
    }

    private void reconcileDepartmentHeadGroups(Set<String> currentHeadUserIds, Set<String> revokedHeadUserIds) {
        List<DepartmentHead> activeHeads = departmentHeadRepository.findAllActiveDepartmentHeads();
        Set<String> activeUserIds = activeHeads.stream()
                .map(DepartmentHead::getUserId)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
        for (String userId : activeUserIds) {
            if (!currentHeadUserIds.contains(userId)) {
                try {
                    keycloakAdminService.removeUserFromGroup(realmName, userId, "departmentHead");
                } catch (Exception ex) {
                    log.warn("Could not remove user {} from departmentHead group: {}", userId, ex.getMessage());
                }
            }
        }

        for (String userId : revokedHeadUserIds) {
            if (!activeUserIds.contains(userId)) {
                try {
                    keycloakAdminService.removeUserFromGroup(realmName, userId, "departmentHead");
                } catch (Exception ex) {
                    log.warn("Could not remove revoked user {} from departmentHead group: {}", userId, ex.getMessage());
                }
            }
        }
    }

    private Location ensureSyncLocation(Company company) {
        return locationRepository.findByNameAndCompanyIdAndIsActiveTrue("External Sync", company.getId())
                .orElseGet(() -> locationRepository.save(Location.builder()
                        .company(company)
                        .name("External Sync")
                        .address("External structure synchronization")
                        .city("N/A")
                        .state("N/A")
                        .country("N/A")
                        .postalCode("N/A")
                        .phoneNumber("N/A")
                        .contactPerson("System")
                        .locationType(Location.LocationType.OFFICE)
                        .status(Location.LocationStatus.ACTIVE)
                        .isActive(true)
                        .build()));
    }

    private Branch ensureSyncBranch(Company company, Location location) {
        return branchRepository.findByBranchCodeAndIsActiveTrue("EXT-SYNC-" + properties.getCompanyId())
                .orElseGet(() -> branchRepository.save(Branch.builder()
                        .company(company)
                        .location(location)
                        .name("External Sync")
                        .description("Branch used to anchor external company structure data")
                        .branchCode("EXT-SYNC-" + properties.getCompanyId())
                        .managerName("System")
                        .managerEmail("system@localhost")
                        .status(Branch.BranchStatus.ACTIVE)
                        .isActive(true)
                        .build()));
    }

    private void rebuildEmployeeDepartmentCache(ExternalCompanyStructureDto remote) {
        Map<String, ExternalEmployeeDepartmentDto> cache = new LinkedHashMap<>();
        List<ExternalCompanyStructureDto.ExternalDepartmentDto> departments =
                remote.getDepartments() == null ? List.of() : remote.getDepartments();
        for (ExternalCompanyStructureDto.ExternalDepartmentDto department : departments) {
            if (department == null || department.getEmployees() == null) {
                continue;
            }
            ExternalEmployeeDepartmentDto mappedDepartment = toEmployeeDepartmentDto(remote.getId(), department);
            for (ExternalCompanyStructureDto.ExternalEmployeeDto employee : department.getEmployees()) {
                if (employee == null || employee.getEmail() == null || employee.getEmail().isBlank()) {
                    continue;
                }
                cache.put(employee.getEmail().trim().toLowerCase(), mappedDepartment);
            }
        }
        employeeDepartmentCache = cache;
    }

    private Map<Long, ExternalCompanyStructureDto.ExternalEmployeeDto> indexEmployees(ExternalCompanyStructureDto remote) {
        Map<Long, ExternalCompanyStructureDto.ExternalEmployeeDto> index = new LinkedHashMap<>();
        List<ExternalCompanyStructureDto.ExternalDepartmentDto> departments =
                remote.getDepartments() == null ? List.of() : remote.getDepartments();
        for (ExternalCompanyStructureDto.ExternalDepartmentDto department : departments) {
            if (department == null || department.getEmployees() == null) {
                continue;
            }
            for (ExternalCompanyStructureDto.ExternalEmployeeDto employee : department.getEmployees()) {
                if (employee != null && employee.getId() != null) {
                    index.put(employee.getId(), employee);
                }
            }
        }
        return index;
    }

    private ExternalEmployeeDepartmentDto toEmployeeDepartmentDto(Long companyId, ExternalCompanyStructureDto.ExternalDepartmentDto department) {
        return ExternalEmployeeDepartmentDto.builder()
                .id(department.getId())
                .code(department.getCode())
                .name(department.getName())
                .companyId(companyId)
                .parentDepartmentId(department.getParentDepartmentId())
                .effectiveFrom(department.getEffectiveFrom())
                .effectiveTo(department.getEffectiveTo())
                .status(department.getStatus())
                .build();
    }

    private String resolveKeycloakUserId(ExternalCompanyStructureDto.ExternalEmployeeDto employee) {
        if (employee == null) {
            return null;
        }
        if (employee.getEmail() != null && !employee.getEmail().isBlank()) {
            Optional<SyncedUser> byEmail = syncedUserRepository.findAll().stream()
                    .filter(user -> user.getEmail() != null && user.getEmail().equalsIgnoreCase(employee.getEmail()))
                    .findFirst();
            if (byEmail.isPresent()) {
                return byEmail.get().getId();
            }
        }
        if (employee.getName() != null && !employee.getName().isBlank()) {
            Optional<SyncedUser> byName = syncedUserRepository.findAll().stream()
                    .filter(user -> user.getUsername() != null && user.getUsername().equalsIgnoreCase(employee.getName()))
                    .findFirst();
            if (byName.isPresent()) {
                return byName.get().getId();
            }
        }
        return employee.getEmail();
    }

    private List<String> currentUserCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof DefaultOidcUser oidc) {
            addCandidate(candidates, oidc.getClaims().get("email"));
            addCandidate(candidates, oidc.getClaims().get("preferred_username"));
            addCandidate(candidates, oidc.getClaims().get("name"));
            addCandidate(candidates, oidc.getSubject());
        }
        if (auth != null && auth.getName() != null) {
            candidates.add(auth.getName());
        }
        return candidates.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private void addCandidate(Set<String> candidates, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            candidates.add(text);
        }
    }

    private Company.CompanyStatus parseCompanyStatus(String status) {
        if (status == null || status.isBlank()) {
            return Company.CompanyStatus.ACTIVE;
        }
        try {
            return Company.CompanyStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception ex) {
            return Company.CompanyStatus.ACTIVE;
        }
    }

    private Department.DepartmentStatus parseDepartmentStatus(String status) {
        if (status == null || status.isBlank()) {
            return Department.DepartmentStatus.ACTIVE;
        }
        try {
            return Department.DepartmentStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception ex) {
            return Department.DepartmentStatus.ACTIVE;
        }
    }

    private Map<String, ExternalEmployeeDepartmentDto> putCache(Map<String, ExternalEmployeeDepartmentDto> current, String key, ExternalEmployeeDepartmentDto value) {
        Map<String, ExternalEmployeeDepartmentDto> copy = new LinkedHashMap<>(current);
        copy.put(key, value);
        return copy;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
