package com.mediatracker.scraping.domain.model;

import java.math.BigDecimal;

public class PlatformPrice {
    private String platform;
    private String storeName;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer discount;
    private String url;
    private String currency;
    private String region;
    private BigDecimal ivaAmount;
    private BigDecimal commissionAmount;
    private BigDecimal totalWithFees;
    private boolean hasFeesCalculated;

    // Getters y Setters
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }

    public Integer getDiscount() { return discount; }
    public void setDiscount(Integer discount) { this.discount = discount; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public BigDecimal getIvaAmount() { return ivaAmount; }
    public void setIvaAmount(BigDecimal ivaAmount) { this.ivaAmount = ivaAmount; }

    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }

    public BigDecimal getTotalWithFees() { return totalWithFees; }
    public void setTotalWithFees(BigDecimal totalWithFees) { this.totalWithFees = totalWithFees; }

    public boolean isHasFeesCalculated() { return hasFeesCalculated; }
    public void setHasFeesCalculated(boolean hasFeesCalculated) { this.hasFeesCalculated = hasFeesCalculated; }
}