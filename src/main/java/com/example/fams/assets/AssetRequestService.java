package com.example.fams.assets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AssetRequestService {

    private final AssetRequestRepository assetRequestRepository;
    private final AssetRepository assetRepository;

    public AssetRequestService(AssetRequestRepository assetRequestRepository,
                              AssetRepository assetRepository) {
        this.assetRequestRepository = assetRequestRepository;
        this.assetRepository = assetRepository;
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

        return assetRequestRepository.save(request);
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

        return assetRequestRepository.save(request);
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

        return assetRequestRepository.save(request);
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

