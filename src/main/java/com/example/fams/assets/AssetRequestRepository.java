package com.example.fams.assets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetRequestRepository extends JpaRepository<AssetRequest, Long> {
    List<AssetRequest> findByStatusOrderByRequestedAtDesc(AssetRequest.RequestStatus status);
    List<AssetRequest> findByRequestedByOrderByRequestedAtDesc(String requestedBy);
    List<AssetRequest> findByAssetAndStatusOrderByRequestedAtDesc(Asset asset, AssetRequest.RequestStatus status);
    List<AssetRequest> findByAssetIdAndStatusOrderByRequestedAtDesc(Long assetId, AssetRequest.RequestStatus status);
    List<AssetRequest> findByRequestedByAndStatusOrderByRequestedAtDesc(String requestedBy, AssetRequest.RequestStatus status);
    long countByStatus(AssetRequest.RequestStatus status);
}

