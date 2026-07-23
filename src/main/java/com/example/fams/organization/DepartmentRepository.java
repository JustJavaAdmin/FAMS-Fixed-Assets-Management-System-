package com.example.fams.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByNameAndBranchIdAndIsActiveTrue(String name, Long branchId);
    Optional<Department> findByDepartmentCodeAndIsActiveTrue(String departmentCode);
    Optional<Department> findByExternalDepartmentId(Long externalDepartmentId);
    List<Department> findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(Long companyId);
    List<Department> findByBranchIdAndIsActiveTrueOrderByCreatedAtDesc(Long branchId);
    List<Department> findByStatusAndIsActiveTrueOrderByCreatedAtDesc(Department.DepartmentStatus status);
}

