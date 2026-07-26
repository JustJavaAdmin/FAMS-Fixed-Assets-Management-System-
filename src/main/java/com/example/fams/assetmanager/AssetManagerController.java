package com.example.fams.assetmanager;

import com.example.fams.assets.AssetRequest;
import com.example.fams.assets.AssetRequestService;
import com.example.fams.core.config.AuthenticationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/asset-manager")
public class AssetManagerController {

    @Autowired
    AssetRequestService assetRequestService;

    @Autowired
    AuthenticationManager authenticationManager;


    /**
     * View all pending asset requests for approval
     */
    @GetMapping("/asset-requests")
    public String assetRequests(Model model) {
        if (!authenticationManager.isAssetManager()) {
            return "redirect:/dashboard";
        }

        List<AssetRequest> pendingRequests = assetRequestService.getPendingRequests();
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("pendingCount", pendingRequests.size());
        return "asset-manager/asset-requests";
    }

    /**
     * Approve an asset request
     */
    @PostMapping("/asset-requests/{requestId}/approve")
    public String approveAssetRequest(@PathVariable Long requestId,
                                      @RequestParam(required = false) String notes,
                                      @AuthenticationPrincipal OidcUser principal,
                                      RedirectAttributes redirectAttributes) {
        if (!authenticationManager.isAssetManager()) {
            redirectAttributes.addFlashAttribute("errorMessage", "You do not have permission to approve requests.");
            return "redirect:/dashboard";
        }

        try {
            String approverName = resolveUserName(principal);
            String approverUsername = principal != null ? principal.getPreferredUsername() : "assetManager";
            AssetRequest approved = assetRequestService.approveRequest(requestId, approverUsername, approverName, notes);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Asset request approved and assigned to " + approved.getRequestedByName());
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", sanitize(ex.getMessage()));
        }
        return "redirect:/asset-manager/asset-requests";
    }

    /**
     * Reject an asset request
     */
    @PostMapping("/asset-requests/{requestId}/reject")
    public String rejectAssetRequest(@PathVariable Long requestId,
                                    @RequestParam(required = false) String notes,
                                    @AuthenticationPrincipal OidcUser principal,
                                    RedirectAttributes redirectAttributes) {
        if (!authenticationManager.isAssetManager()) {
            redirectAttributes.addFlashAttribute("errorMessage", "You do not have permission to reject requests.");
            return "redirect:/dashboard";
        }

        try {
            String rejecterName = resolveUserName(principal);
            String rejecterUsername = principal != null ? principal.getPreferredUsername() : "assetManager";
            assetRequestService.rejectRequest(requestId, rejecterUsername, rejecterName, notes);
            redirectAttributes.addFlashAttribute("successMessage", "Asset request rejected.");
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", sanitize(ex.getMessage()));
        }
        return "redirect:/asset-manager/asset-requests";
    }

    /* ══════════════════════════════════════════════════════════
       Helpers
       ══════════════════════════════════════════════════════════ */

    /**
     * Best-effort display name from the OIDC principal.
     * Falls back gracefully if principal is null (e.g. form-login).
     */
    private String resolveUserName(OidcUser principal) {
        if (principal == null) return "Asset Manager";
        String name = principal.getFullName();
        if (name != null && !name.isBlank()) return name;
        name = principal.getPreferredUsername();
        if (name != null && !name.isBlank()) return name;
        name = principal.getEmail();
        return (name != null && !name.isBlank()) ? name : "Asset Manager";
    }

    /**
     * Strips HTML and truncates before writing to flash attributes.
     */
    private String sanitize(String msg) {
        if (msg == null) return "An unexpected error occurred.";
        String clean = msg.replaceAll("<[^>]+>", "");
        return clean.length() > 250 ? clean.substring(0, 247) + "…" : clean;
    }
}

