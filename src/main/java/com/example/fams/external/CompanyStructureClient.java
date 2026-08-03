package com.example.fams.external;

import com.example.fams.dto.ExternalCompanyStructureDto;
import com.example.fams.dto.ExternalEmployeeDepartmentDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "companyStructureClient",
        url = "${fams.company-structure.base-url:https://justhr2-dvhhe2e3bvfgc4fq.westus3-01.azurewebsites.net/}",
        configuration = CompanyStructureFeignConfig.class
)
public interface CompanyStructureClient {

    @GetMapping("/api/companies/1/structure")
    ExternalCompanyStructureDto getCompanyStructure();

    @GetMapping("/api/employees/department")
    ExternalEmployeeDepartmentDto getEmployeeDepartment(@RequestParam("email") String email);
}
