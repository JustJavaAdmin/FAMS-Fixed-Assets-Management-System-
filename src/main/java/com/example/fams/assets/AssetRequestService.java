package com.example.fams.assets;

import com.example.fams.aau.keycloak.SyncedUser;
import com.example.fams.aau.keycloak.SyncedUserRepository;
import com.example.fams.mail.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AssetRequestService {

    private final AssetRequestRepository assetRequestRepository;
    private final AssetRepository assetRepository;
    private final SyncedUserRepository syncedUserRepository;
    private final EmailService emailService;

    // Base URL used to build links inside notification emails. Defaults to localhost:8080
    @Value("${fams.base-url:http://localhost:9090}")
    private String appBaseUrl;

    public AssetRequestService(AssetRequestRepository assetRequestRepository,
                               AssetRepository assetRepository,
                               SyncedUserRepository syncedUserRepository,
                               EmailService emailService) {
        this.assetRequestRepository = assetRequestRepository;
        this.assetRepository = assetRepository;
        this.syncedUserRepository = syncedUserRepository;
        this.emailService = emailService;
    }

    /**
     * Get all unassigned assets that can be requested
     */
    public List<Asset> getAvailableAssets() {
        return assetRepository.findAll().stream()
                .filter(asset -> asset.getCustodian() == null || asset.getCustodian().isBlank())
                .filter(asset -> !"Disposed".equalsIgnoreCase(asset.getStatus()))
                .toList();
    }

    /**
     * Employee requests an asset
     */
    @Transactional
    public AssetRequest requestAsset(Long assetId, String requestedBy, String requestedByName, String reason) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        // Verify asset is unassigned
        if (asset.getCustodian() != null && !asset.getCustodian().isBlank()) {
            throw new IllegalArgumentException("Asset is already assigned to someone");
        }

        // Check if employee already has a pending request for this asset
        List<AssetRequest> existing = assetRequestRepository.findByAssetIdAndStatusOrderByRequestedAtDesc(
                assetId, AssetRequest.RequestStatus.PENDING);
        if (!existing.isEmpty()) {
            throw new IllegalArgumentException("You already have a pending request for this asset");
        }

        AssetRequest request = new AssetRequest();
        request.setAsset(asset);
        request.setRequestedBy(requestedBy);
        request.setRequestedByName(requestedByName);
        request.setReason(reason);
        request.setStatus(AssetRequest.RequestStatus.PENDING);

        AssetRequest saved = assetRequestRepository.save(request);

        // Notify asset managers by email about the new request. Use the local SyncedUser snapshot
        // to find users in the assetManager group and send them a clean, professional message.
        try {
            List<SyncedUser> managers = syncedUserRepository.findByGroupName(",assetManager,");
            if (managers != null && !managers.isEmpty()) {
                String subject = "Asset request awaiting approval: " + asset.getName();
                StringBuilder body = new StringBuilder();
                body.append("Hello,\n\n");
                body.append("An employee has requested the following asset and your approval is required:\n\n");
                body.append("Asset: ").append(asset.getName()).append(" (ID: ").append(asset.getId()).append(")\n");
                body.append("Requested by: ").append(requestedByName).append("\n");
                if (reason != null && !reason.isBlank()) {
                    body.append("Reason: ").append(reason).append("\n");
                }
                body.append("\nYou can review and action this request here: ")
                        .append(appBaseUrl.replaceAll("/+$", ""))
                        .append("/asset-manager/asset-requests")
                        .append("\n\nRegards,\nFAMS Notification Service\n");

                for (SyncedUser manager : managers) {
                    String to = manager.getEmail();
                    if (to == null || to.isBlank()) continue;
                    try {
                        emailService.sendEmail(to, subject, body.toString());
                    } catch (Exception ex) {
                        // Log and continue - do not fail the request because an email couldn't be sent
                        System.err.println("Failed to send asset-request notification to " + to + ": " + ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            // Protect the main flow: any failures in notification should not prevent request creation
            System.err.println("Failed to notify asset managers: " + ex.getMessage());
        }

        return saved;
    }

    /**
     * Get all pending asset requests
     */
    public List<AssetRequest> getPendingRequests() {
        return assetRequestRepository.findByStatusOrderByRequestedAtDesc(AssetRequest.RequestStatus.PENDING);
    }

    /**
     * Get requests for a specific employee
     */
    public List<AssetRequest> getMyRequests(String username) {
        return assetRequestRepository.findByRequestedByOrderByRequestedAtDesc(username);
    }

    /**
     * Get pending requests for an employee
     */
    public List<AssetRequest> getMyPendingRequests(String username) {
        return assetRequestRepository.findByRequestedByAndStatusOrderByRequestedAtDesc(
                username, AssetRequest.RequestStatus.PENDING);
    }

    /**
     * Approve an asset request
     */
    @Transactional
    public AssetRequest approveRequest(Long requestId, String approvedBy, String approvedByName, String notes) {
        AssetRequest request = assetRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Asset request not found"));

        if (request.getStatus() != AssetRequest.RequestStatus.PENDING) {
            throw new IllegalArgumentException("Only pending requests can be approved");
        }

        // Verify asset is still unassigned
        Asset asset = request.getAsset();
        if (asset.getCustodian() != null && !asset.getCustodian().isBlank()) {
            throw new IllegalArgumentException("Asset has already been assigned to someone else");
        }

        // Assign asset to the requester
        asset.setCustodian(request.getRequestedByName());
        asset.setStatus("Assigned");
        assetRepository.save(asset);

        // Update request
        request.setStatus(AssetRequest.RequestStatus.APPROVED);
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovedBy(approvedBy);
        request.setApprovedByName(approvedByName);
        request.setApprovalNotes(notes);

        AssetRequest saved = assetRequestRepository.save(request);

        // Notify the requester by email about approval
        try {
            String username = request.getRequestedBy();
            syncedUserRepository.findByUsername(username).ifPresent(user -> {
                String to = user.getEmail();
                if (to == null || to.isBlank()) return;
                String subject = "Your asset request has been approved: " + asset.getName();
                StringBuilder body = new StringBuilder();
                body.append("Hello ").append(request.getRequestedByName() == null ? "" : request.getRequestedByName()).append(",\n\n");
                body.append("Good news — your request for the following asset has been approved:\n\n");
                body.append("Asset: ").append(asset.getName()).append(" (ID: ").append(asset.getId()).append(")\n");
                body.append("Approved by: ").append(approvedByName).append("\n");
                if (notes != null && !notes.isBlank()) {
                    body.append("Notes: ").append(notes).append("\n");
                }
                body.append("\nYou can view your requests here: ")
                        .append(appBaseUrl.replaceAll("/+$", ""))
                        .append("/employee/asset-requests")
                        .append("\n\nRegards,\nFAMS Notification Service\n");
                try {
                    emailService.sendEmail(to, subject, body.toString());
                } catch (Exception ex) {
                    System.err.println("Failed to send approval notification to " + to + ": " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            System.err.println("Failed to send approval notification: " + ex.getMessage());
        }

        return saved;
    }

    /**
     * Reject an asset request
     */
    @Transactional
    public AssetRequest rejectRequest(Long requestId, String rejectedBy, String rejectedByName, String notes) {
        AssetRequest request = assetRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Asset request not found"));

        if (request.getStatus() != AssetRequest.RequestStatus.PENDING) {
            throw new IllegalArgumentException("Only pending requests can be rejected");
        }

        request.setStatus(AssetRequest.RequestStatus.REJECTED);
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovedBy(rejectedBy);
        request.setApprovedByName(rejectedByName);
        request.setApprovalNotes(notes);

        AssetRequest saved = assetRequestRepository.save(request);

        // Notify the requester by email about rejection
        try {
            String username = request.getRequestedBy();
            syncedUserRepository.findByUsername(username).ifPresent(user -> {
                String to = user.getEmail();
                if (to == null || to.isBlank()) return;
                String subject = "Your asset request was declined: " + request.getAsset().getName();
                StringBuilder body = new StringBuilder();
                body.append("Hello ").append(request.getRequestedByName() == null ? "" : request.getRequestedByName()).append(",\n\n");
                body.append("We regret to inform you that your request for the following asset was declined:\n\n");
                body.append("Asset: ").append(request.getAsset().getName()).append(" (ID: ").append(request.getAsset().getId()).append(")\n");
                body.append("Processed by: ").append(rejectedByName).append("\n");
                if (notes != null && !notes.isBlank()) {
                    body.append("Reason: ").append(notes).append("\n");
                }
                body.append("\nYou can view your requests here: ")
                        .append(appBaseUrl.replaceAll("/+$", ""))
                        .append("/employee/asset-requests")
                        .append("\n\nRegards,\nFAMS Notification Service\n");
                try {
                    emailService.sendEmail(to, subject, body.toString());
                } catch (Exception ex) {
                    System.err.println("Failed to send rejection notification to " + to + ": " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            System.err.println("Failed to send rejection notification: " + ex.getMessage());
        }

        return saved;
    }

    /**
     * Cancel an asset request (by requester)
     */
    @Transactional
    public AssetRequest cancelRequest(Long requestId) {
        AssetRequest request = assetRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Asset request not found"));

        if (request.getStatus() != AssetRequest.RequestStatus.PENDING) {
            throw new IllegalArgumentException("Only pending requests can be cancelled");
        }

        request.setStatus(AssetRequest.RequestStatus.CANCELLED);
        return assetRequestRepository.save(request);
    }

    /**
     * Count pending asset requests
     */
    public long countPendingRequests() {
        return assetRequestRepository.countByStatus(AssetRequest.RequestStatus.PENDING);
    }
}

