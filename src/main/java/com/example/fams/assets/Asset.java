package com.example.fams.assets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String assetCode;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 120)
    private String category;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 120)
    private String serialNumber;

    @Column(length = 120)
    private String manufacturer;

    @Column(length = 120)
    private String model;

    private LocalDate purchaseDate;

    @Column(precision = 19, scale = 2)
    private BigDecimal purchaseCost;

    @Column(length = 160)
    private String vendor;

    private LocalDate warrantyExpiry;

    @Column(nullable = false, length = 120)
    private String department;

    @Column(nullable = false, length = 120)
    private String branch;

    @Column(length = 120)
    private String custodian;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 180)
    private String imagePublicId;

    @Column(length = 32)
    private String barcodeValue;

    @Column(columnDefinition = "text")
    private String barcodeImageDataUri;

    @Column(columnDefinition = "text")
    private String qrCodeImageDataUri;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void beforeCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null || status.isBlank()) {
            status = "In Stock";
        }
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public void setPurchaseCost(BigDecimal purchaseCost) {
        this.purchaseCost = purchaseCost;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public LocalDate getWarrantyExpiry() {
        return warrantyExpiry;
    }

    public void setWarrantyExpiry(LocalDate warrantyExpiry) {
        this.warrantyExpiry = warrantyExpiry;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getCustodian() {
        return custodian;
    }

    public void setCustodian(String custodian) {
        this.custodian = custodian;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImagePublicId() {
        return imagePublicId;
    }

    public void setImagePublicId(String imagePublicId) {
        this.imagePublicId = imagePublicId;
    }

    public String getBarcodeValue() {
        return barcodeValue;
    }

    public void setBarcodeValue(String barcodeValue) {
        this.barcodeValue = barcodeValue;
    }

    public String getBarcodeImageDataUri() {
        return barcodeImageDataUri;
    }

    public void setBarcodeImageDataUri(String barcodeImageDataUri) {
        this.barcodeImageDataUri = barcodeImageDataUri;
    }

    public String getQrCodeImageDataUri() {
        return qrCodeImageDataUri;
    }

    public void setQrCodeImageDataUri(String qrCodeImageDataUri) {
        this.qrCodeImageDataUri = qrCodeImageDataUri;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
