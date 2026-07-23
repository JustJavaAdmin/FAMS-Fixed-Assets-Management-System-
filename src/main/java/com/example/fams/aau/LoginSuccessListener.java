package com.example.fams.aau;

import com.example.fams.external.StructureSyncService;
import com.example.fams.dto.ExternalEmployeeDepartmentDto;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Component;

/**
 * Logs (System.out) the group(s) of the user that just logged in via Keycloak/OIDC.
 * Triggered once per interactive login (not on every authenticated request).
 */
@Component
public class LoginSuccessListener {

    private final StructureSyncService structureSyncService;

    public LoginSuccessListener(StructureSyncService structureSyncService) {
        this.structureSyncService = structureSyncService;
    }

    @EventListener
    public void onLoginSuccess(InteractiveAuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof DefaultOidcUser oidcUser) {
            Object group = oidcUser.getClaims().get("groups");
            String name = (String) oidcUser.getClaims().get("name");
            System.out.println("=== User logged in: " + name + " | group(s): " + group + " ===");
            printDepartment(oidcUser);
        } else {
            System.out.println("=== User logged in (non-OIDC principal): " + principal + " ===");
        }
    }

    private void printDepartment(DefaultOidcUser oidcUser) {
        String email = claimAsText(oidcUser, "email");
        String preferredUsername = claimAsText(oidcUser, "preferred_username");
        String displayName = claimAsText(oidcUser, "name");

        ExternalEmployeeDepartmentDto department = structureSyncService.resolveCurrentUserDepartment()
                .or(() -> structureSyncService.getEmployeeDepartment(email))
                .or(() -> structureSyncService.getEmployeeDepartment(preferredUsername))
                .or(() -> structureSyncService.getEmployeeDepartment(displayName))
                .orElse(null);

        if (department == null) {
            System.out.println("=== Department lookup: no department found for "
                    + firstNonBlank(email, preferredUsername, displayName, oidcUser.getSubject()) + " ===");
            return;
        }

        System.out.println("=== Department: " + department.getName()
                + " | code=" + department.getCode()
                + " | companyId=" + department.getCompanyId()
                + " ===");
    }

    private String claimAsText(DefaultOidcUser oidcUser, String claimName) {
        Object value = oidcUser.getClaims().get(claimName);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown-user";
    }
}
