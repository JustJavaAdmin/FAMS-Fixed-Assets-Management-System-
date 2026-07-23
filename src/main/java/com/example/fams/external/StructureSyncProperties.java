package com.example.fams.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fams.company-structure")
public class StructureSyncProperties {
    private Long companyId = 1L;
    private String baseUrl = "https://justhumanresource-production.up.railway.app/";
    private long syncIntervalMs = 21_600_000L;
    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 10_000;
    private long retryPeriodMs = 200L;
    private long retryMaxPeriodMs = 1_000L;
    private int retryMaxAttempts = 3;

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getSyncIntervalMs() {
        return syncIntervalMs;
    }

    public void setSyncIntervalMs(long syncIntervalMs) {
        this.syncIntervalMs = syncIntervalMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public long getRetryPeriodMs() {
        return retryPeriodMs;
    }

    public void setRetryPeriodMs(long retryPeriodMs) {
        this.retryPeriodMs = retryPeriodMs;
    }

    public long getRetryMaxPeriodMs() {
        return retryMaxPeriodMs;
    }

    public void setRetryMaxPeriodMs(long retryMaxPeriodMs) {
        this.retryMaxPeriodMs = retryMaxPeriodMs;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }
}
