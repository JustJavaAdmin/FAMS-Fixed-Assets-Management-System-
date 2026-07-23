package com.example.fams.organization;

import com.example.fams.organization.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.example.fams.aau.keycloak.KeycloakAdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class CompanyStructureService {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DepartmentHeadRepository departmentHeadRepository;

    @Autowired
    private KeycloakAdminService keycloakAdminService;

    @Value("${keycloak.realm}")
    private String realmName;

    // ============== COMPANY MANAGEMENT ==============

    public CompanyDTO createCompany(CompanyDTO dto) throws Exception {
        try {
            if (companyRepository.findByNameAndIsActiveTrue(dto.getName()).isPresent()) {
                throw new Exception("Company with this name already exists");
            }

            Company company = Company.builder()
                    .name(dto.getName())
                    .description(dto.getDescription())
                    .registrationNumber(dto.getRegistrationNumber())
                    .taxId(dto.getTaxId())
                    .industry(dto.getIndustry())
                    .status(Company.CompanyStatus.valueOf(dto.getStatus() != null ? dto.getStatus() : "ACTIVE"))
                    .isActive(true)
                    .build();

            Company saved = companyRepository.save(company);
            log.info("Company created: " + saved.getId());
            return mapCompanyToDTO(saved);
        } catch (Exception e) {
            log.error("Error creating company: " + e.getMessage());
            throw new Exception("Failed to create company: " + e.getMessage());
        }
    }

    public CompanyDTO updateCompany(Long id, CompanyDTO dto) throws Exception {
        try {
            Company company = companyRepository.findById(id)
                    .orElseThrow(() -> new Exception("Company not found"));

            company.setName(dto.getName());
            company.setDescription(dto.getDescription());
            company.setRegistrationNumber(dto.getRegistrationNumber());
            company.setTaxId(dto.getTaxId());
            company.setIndustry(dto.getIndustry());
            company.setStatus(Company.CompanyStatus.valueOf(dto.getStatus() != null ? dto.getStatus() : "ACTIVE"));

            Company updated = companyRepository.save(company);
            log.info("Company updated: " + updated.getId());
            return mapCompanyToDTO(updated);
        } catch (Exception e) {
            log.error("Error updating company: " + e.getMessage());
            throw new Exception("Failed to update company: " + e.getMessage());
        }
    }

    public void deleteCompany(Long id) throws Exception {
        try {
            Company company = companyRepository.findById(id)
                    .orElseThrow(() -> new Exception("Company not found"));

            company.setIsActive(false);
            companyRepository.save(company);
            log.info("Company deleted: " + id);
        } catch (Exception e) {
            log.error("Error deleting company: " + e.getMessage());
            throw new Exception("Failed to delete company: " + e.getMessage());
        }
    }

    public CompanyDTO getCompany(Long id) throws Exception {
        try {
            Company company = companyRepository.findById(id)
                    .orElseThrow(() -> new Exception("Company not found"));
            return mapCompanyToDTO(company);
        } catch (Exception e) {
            log.error("Error retrieving company: " + e.getMessage());
            throw new Exception("Failed to retrieve company: " + e.getMessage());
        }
    }

    public List<CompanyDTO> getAllCompanies() {
        return companyRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                .stream().map(this::mapCompanyToDTO).collect(Collectors.toList());
    }

    // ============== LOCATION MANAGEMENT ==============

    public LocationDTO createLocation(LocationDTO dto) throws Exception {
        try {
            Company company = companyRepository.findById(dto.getCompanyId())
                    .orElseThrow(() -> new Exception("Company not found"));

            if (locationRepository.findByNameAndCompanyIdAndIsActiveTrue(dto.getName(), dto.getCompanyId()).isPresent()) {
                throw new Exception("Location with this name already exists in this company");
            }

            Location location = Location.builder()
                    .company(company)
                    .name(dto.getName())
                    .address(dto.getAddress())
                    .city(dto.getCity())
                    .state(dto.getState())
                    .country(dto.getCountry())
                    .postalCode(dto.getPostalCode())
                    .phoneNumber(dto.getPhoneNumber())
                    .contactPerson(dto.getContactPerson())
                    .locationType(Location.LocationType.valueOf(dto.getLocationType() != null ? dto.getLocationType() : "OFFICE"))
                    .status(Location.LocationStatus.valueOf(dto.getStatus() != null ? dto.getStatus() : "ACTIVE"))
                    .isActive(true)
                    .build();

            Location saved = locationRepository.save(location);
            log.info("Location created: " + saved.getId());
            return mapLocationToDTO(saved);
        } catch (Exception e) {
            log.error("Error creating location: " + e.getMessage());
            throw new Exception("Failed to create location: " + e.getMessage());
        }
    }

    public LocationDTO updateLocation(Long id, LocationDTO dto) throws Exception {
        try {
            Location location = locationRepository.findById(id)
                    .orElseThrow(() -> new Exception("Location not found"));

            location.setName(dto.getName());
            location.setAddress(dto.getAddress());
            location.setCity(dto.getCity());
            location.setState(dto.getState());
            location.setCountry(dto.getCountry());
            location.setPostalCode(dto.getPostalCode());
            location.setPhoneNumber(dto.getPhoneNumber());
            location.setContactPerson(dto.getContactPerson());
            location.setLocationType(Location.LocationType.valueOf(dto.getLocationType() != null ? dto.getLocationType() : "OFFICE"));
            location.setStatus(Location.LocationStatus.valueOf(dto.getStatus() != null ? dto.getStatus() : "ACTIVE"));

            Location updated = locationRepository.save(location);
            log.info("Location updated: " + updated.getId());
            return mapLocationToDTO(updated);
        } catch (Exception e) {
            log.error("Error updating location: " + e.getMessage());
            throw new Exception("Failed to update location: " + e.getMessage());
        }
    }

    public void deleteLocation(Long id) throws Exception {
        try {
            Location location = locationRepository.findById(id)
                    .orElseThrow(() -> new Exception("Location not found"));

            location.setIsActive(false);
            locationRepository.save(location);
            log.info("Location deleted: " + id);
        } catch (Exception e) {
            log.error("Error deleting location: " + e.getMessage());
            throw new Exception("Failed to delete location: " + e.getMessage());
        }
    }

    public List<LocationDTO> getLocationsByCompany(Long companyId) {
        return locationRepository.findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(companyId)
                .stream().map(this::mapLocationToDTO).collect(Collectors.toList());
    }

    // ============== BRANCH MANAGEMENT ==============

    public BranchDTO createBranch(BranchDTO dto) throws Exception {
        try {
            Company company = companyRepository.findById(dto.getCompanyId())
                    .orElseThrow(() -> new Exception("Company not found"));

            Location location = locationRepository.findById(dto.getLocationId())
                    .orElseThrow(() -> new Exception("Location not found"));

            if (branchRepository.findByNameAndCompanyIdAndIsActiveTrue(dto.getName(), dto.getCompanyId()).isPresent()) {
                throw new Exception("Branch with this name already exists in this company");
            }

            Branch branch = Branch.builder()
                    .company(company)
                    .location(location)
                    .name(dto.getName())
                    .description(dto.getDescription())
                    .branchCode(dto.getBranchCode())
                    .managerName(dto.getManagerName())
                    .managerPhone(dto.getManagerPhone())
                    .managerEmail(dto.getManagerEmail())
                    .status(Branch.BranchStatus.valueOf(dto.getStatus() != null ? dto.getStatus() : "ACTIVE"))
                    .isActive(true)
                    .build();

            Branch saved = branchRepository.save(branch);
            log.info("Branch created: " + saved.getId());
            return mapBranchToDTO(saved);
        } catch (Exception e) {
            log.error("Error creating branch: " + e.getMessage());
            throw new Exception("Failed to create branch: " + e.getMessage());
        }
    }

    public BranchDTO updateBranch(Long id, BranchDTO dto) throws Exception {
        try {
            Branch branch = branchRepository.findById(id)
                    .orElseThrow(() -> new Exception("Branch not found"));

            branch.setName(dto.getName());
            branch.setDescription(dto.getDescription());
            branch.setBranchCode(dto.getBranchCode());
            branch.setManagerName(dto.getManagerName());
            branch.setManagerPhone(dto.getManagerPhone());
            branch.setManagerEmail(dto.getManagerEmail());
            branch.setStatus(Branch.BranchStatus.valueOf(dto.getStatus() != null ? dto.getStatus() : "ACTIVE"));

            Branch updated = branchRepository.save(branch);
            log.info("Branch updated: " + updated.getId());
            return mapBranchToDTO(updated);
        } catch (Exception e) {
            log.error("Error updating branch: " + e.getMessage());
            throw new Exception("Failed to update branch: " + e.getMessage());
        }
    }

    public void deleteBranch(Long id) throws Exception {
        try {
            Branch branch = branchRepository.findById(id)
                    .orElseThrow(() -> new Exception("Branch not found"));

            branch.setIsActive(false);
            branchRepository.save(branch);
            log.info("Branch deleted: " + id);
        } catch (Exception e) {
            log.error("Error deleting branch: " + e.getMessage());
            throw new Exception("Failed to delete branch: " + e.getMessage());
        }
    }

    public List<BranchDTO> getBranchesByCompany(Long companyId) {
        return branchRepository.findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(companyId)
                .stream().map(this::mapBranchToDTO).collect(Collectors.toList());
    }

    public List<BranchDTO> getBranchesByLocation(Long locationId) {
        return branchRepository.findByLocationIdAndIsActiveTrueOrderByCreatedAtDesc(locationId)
                .stream().map(this::mapBranchToDTO).collect(Collectors.toList());
    }

    // ============== DEPARTMENT MANAGEMENT ==============

    public DepartmentDTO createDepartment(DepartmentDTO dto) throws Exception {
        try {
            Company company = companyRepository.findById(dto.getCompanyId())
                    .orElseThrow(() -> new Exception("Company not found"));

            Branch branch = branchRepository.findById(dto.getBranchId())
                    .orElseThrow(() -> new Exception("Branch not found"));

            if (departmentRepository.findByNameAndBranchIdAndIsActiveTrue(dto.getName(), dto.getBranchId()).isPresent()) {
                throw new Exception("Department with this name already exists in this branch");
            }

            Department department = Department.builder()
                    .company(company)
                    .branch(branch)
                    .name(dto.getName())
                    .description(dto.getDescription())
                    .departmentCode(dto.getDepartmentCode())
                    .budget(dto.getBudget())
                    .status(Department.DepartmentStatus.valueOf(dto.getStatus() != null ? dto.getStatus() : "ACTIVE"))
                    .isActive(true)
                    .build();

            Department saved = departmentRepository.save(department);
            log.info("Department created: " + saved.getId());
            return mapDepartmentToDTO(saved);
        } catch (Exception e) {
            log.error("Error creating department: " + e.getMessage());
            throw new Exception("Failed to create department: " + e.getMessage());
        }
    }

    public DepartmentDTO updateDepartment(Long id, DepartmentDTO dto) throws Exception {
        try {
            Department department = departmentRepository.findById(id)
                    .orElseThrow(() -> new Exception("Department not found"));

            department.setName(dto.getName());
            department.setDescription(dto.getDescription());
            department.setDepartmentCode(dto.getDepartmentCode());
            department.setBudget(dto.getBudget());
            department.setStatus(Department.DepartmentStatus.valueOf(dto.getStatus() != null ? dto.getStatus() : "ACTIVE"));

            Department updated = departmentRepository.save(department);
            log.info("Department updated: " + updated.getId());
            return mapDepartmentToDTO(updated);
        } catch (Exception e) {
            log.error("Error updating department: " + e.getMessage());
            throw new Exception("Failed to update department: " + e.getMessage());
        }
    }

    public void deleteDepartment(Long id) throws Exception {
        try {
            Department department = departmentRepository.findById(id)
                    .orElseThrow(() -> new Exception("Department not found"));

            department.setIsActive(false);
            departmentRepository.save(department);
            log.info("Department deleted: " + id);
        } catch (Exception e) {
            log.error("Error deleting department: " + e.getMessage());
            throw new Exception("Failed to delete department: " + e.getMessage());
        }
    }

    public List<DepartmentDTO> getDepartmentsByCompany(Long companyId) {
        return departmentRepository.findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(companyId)
                .stream().map(this::mapDepartmentToDTO).collect(Collectors.toList());
    }

    public List<DepartmentDTO> getDepartmentsByBranch(Long branchId) {
        return departmentRepository.findByBranchIdAndIsActiveTrueOrderByCreatedAtDesc(branchId)
                .stream().map(this::mapDepartmentToDTO).collect(Collectors.toList());
    }

    // ============== DEPARTMENT HEAD MANAGEMENT ==============

    public DepartmentHeadDTO assignDepartmentHead(DepartmentHeadDTO dto) throws Exception {
        try {
            Department department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new Exception("Department not found"));

            if (departmentHeadRepository.findByDepartmentIdAndUserIdAndIsActiveTrue(dto.getDepartmentId(), dto.getUserId()).isPresent()) {
                throw new Exception("User is already a department head for this department");
            }

            if (dto.getIsPrimary() != null && dto.getIsPrimary()) {
                Optional<DepartmentHead> existingPrimary = departmentHeadRepository.findByDepartmentIdAndIsPrimaryTrueAndIsActiveTrue(dto.getDepartmentId());
                if (existingPrimary.isPresent()) {
                    DepartmentHead primary = existingPrimary.get();
                    primary.setIsPrimary(false);
                    departmentHeadRepository.save(primary);
                }
            }

            DepartmentHead head = DepartmentHead.builder()
                    .department(department)
                    .userId(dto.getUserId())
                    .userName(dto.getUserName())
                    .userEmail(dto.getUserEmail())
                    .fullName(dto.getFullName())
                    .isPrimary(dto.getIsPrimary() != null ? dto.getIsPrimary() : false)
                    .status(DepartmentHead.HeadStatus.ACTIVE)
                    .isActive(true)
                    .build();

            DepartmentHead saved = departmentHeadRepository.save(head);
            log.info("Department head assigned: " + saved.getId());

            // Update Keycloak groups: remove 'employees' and add 'departmentHead'
            try {
                if (dto.getUserId() != null) {
                    // best-effort: remove employees group if present
                    try {
                        keycloakAdminService.removeUserFromGroup(realmName, dto.getUserId(), "employees");
                    } catch (Exception ignore) {
                        // ignore failures to remove (group may not exist or user not in group)
                    }
                    // add departmentHead group
                    try {
                        keycloakAdminService.addUserToGroup(realmName, dto.getUserId(), "departmentHead");
                    } catch (Exception ex) {
                        log.warn("Failed to add user to departmentHead group in Keycloak: " + ex.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Could not update Keycloak groups for department head assignment: " + e.getMessage());
            }
            return mapDepartmentHeadToDTO(saved);
        } catch (Exception e) {
            log.error("Error assigning department head: " + e.getMessage());
            throw new Exception("Failed to assign department head: " + e.getMessage());
        }
    }

    public void removeDepartmentHead(Long headId) throws Exception {
        try {
            DepartmentHead head = departmentHeadRepository.findById(headId)
                    .orElseThrow(() -> new Exception("Department head not found"));

            head.setIsActive(false);
            head.setStatus(DepartmentHead.HeadStatus.REMOVED);
            head.setRemovedAt(LocalDateTime.now());
            departmentHeadRepository.save(head);
            log.info("Department head removed: " + headId);

            // Update Keycloak groups: remove 'departmentHead' and add back 'employees'
            try {
                String userId = head.getUserId();
                if (userId != null) {
                    try {
                        keycloakAdminService.removeUserFromGroup(realmName, userId, "departmentHead");
                    } catch (Exception ignore) {
                        // ignore failure
                    }
                    try {
                        keycloakAdminService.addUserToGroup(realmName, userId, "employees");
                    } catch (Exception ex) {
                        log.warn("Failed to add user back to employees group in Keycloak: " + ex.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Could not update Keycloak groups for department head removal: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error removing department head: " + e.getMessage());
            throw new Exception("Failed to remove department head: " + e.getMessage());
        }
    }

    public List<DepartmentHeadDTO> getDepartmentHeads(Long departmentId) {
        return departmentHeadRepository.findByDepartmentIdAndIsActiveTrueOrderByAssignedAtDesc(departmentId)
                .stream().map(this::mapDepartmentHeadToDTO).collect(Collectors.toList());
    }

    public List<DepartmentHeadDTO> getAllDepartmentHeads() {
        return departmentHeadRepository.findAllActiveDepartmentHeads()
                .stream().map(this::mapDepartmentHeadToDTO).collect(Collectors.toList());
    }

    public List<DepartmentHeadDTO> getDepartmentHeadsByUser(String userId) {
        return departmentHeadRepository.findByUserIdAndIsActiveTrueOrderByAssignedAtDesc(userId)
                .stream().map(this::mapDepartmentHeadToDTO).collect(Collectors.toList());
    }

    // ============== MAPPING METHODS ==============

    private CompanyDTO mapCompanyToDTO(Company company) {
        return CompanyDTO.builder()
                .id(company.getId())
                .name(company.getName())
                .description(company.getDescription())
                .registrationNumber(company.getRegistrationNumber())
                .taxId(company.getTaxId())
                .industry(company.getIndustry())
                .status(company.getStatus().toString())
                .isActive(company.getIsActive())
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .build();
    }

    private LocationDTO mapLocationToDTO(Location location) {
        return LocationDTO.builder()
                .id(location.getId())
                .companyId(location.getCompany().getId())
                .companyName(location.getCompany().getName())
                .name(location.getName())
                .address(location.getAddress())
                .city(location.getCity())
                .state(location.getState())
                .country(location.getCountry())
                .postalCode(location.getPostalCode())
                .phoneNumber(location.getPhoneNumber())
                .contactPerson(location.getContactPerson())
                .locationType(location.getLocationType().toString())
                .status(location.getStatus().toString())
                .isActive(location.getIsActive())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }

    private BranchDTO mapBranchToDTO(Branch branch) {
        return BranchDTO.builder()
                .id(branch.getId())
                .companyId(branch.getCompany().getId())
                .companyName(branch.getCompany().getName())
                .locationId(branch.getLocation().getId())
                .locationName(branch.getLocation().getName())
                .name(branch.getName())
                .description(branch.getDescription())
                .branchCode(branch.getBranchCode())
                .managerName(branch.getManagerName())
                .managerPhone(branch.getManagerPhone())
                .managerEmail(branch.getManagerEmail())
                .status(branch.getStatus().toString())
                .isActive(branch.getIsActive())
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .build();
    }

    private DepartmentDTO mapDepartmentToDTO(Department department) {
        return DepartmentDTO.builder()
                .id(department.getId())
                .externalDepartmentId(department.getExternalDepartmentId())
                .parentExternalDepartmentId(department.getParentExternalDepartmentId())
                .companyId(department.getCompany().getId())
                .companyName(department.getCompany().getName())
                .branchId(department.getBranch().getId())
                .branchName(department.getBranch().getName())
                .name(department.getName())
                .description(department.getDescription())
                .departmentCode(department.getDepartmentCode())
                .budget(department.getBudget())
                .effectiveFrom(department.getEffectiveFrom())
                .effectiveTo(department.getEffectiveTo())
                .departmentHeadId(department.getDepartmentHeadId())
                .departmentHeadName(department.getDepartmentHeadName())
                .syncSource(department.getSyncSource())
                .lastSyncedAt(department.getLastSyncedAt())
                .status(department.getStatus().toString())
                .isActive(department.getIsActive())
                .createdAt(department.getCreatedAt())
                .updatedAt(department.getUpdatedAt())
                .build();
    }

    private DepartmentHeadDTO mapDepartmentHeadToDTO(DepartmentHead head) {
        return DepartmentHeadDTO.builder()
                .id(head.getId())
                .departmentId(head.getDepartment().getId())
                .departmentName(head.getDepartment().getName())
                .branchName(head.getDepartment().getBranch().getName())
                .companyName(head.getDepartment().getCompany().getName())
                .userId(head.getUserId())
                .userName(head.getUserName())
                .userEmail(head.getUserEmail())
                .fullName(head.getFullName())
                .isPrimary(head.getIsPrimary())
                .status(head.getStatus().toString())
                .isActive(head.getIsActive())
                .assignedAt(head.getAssignedAt())
                .removedAt(head.getRemovedAt())
                .createdAt(head.getCreatedAt())
                .updatedAt(head.getUpdatedAt())
                .build();
    }
}

