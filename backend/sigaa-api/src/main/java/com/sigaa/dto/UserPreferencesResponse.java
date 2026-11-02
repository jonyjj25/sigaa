package com.sigaa.dto;

import java.time.LocalDateTime;

public class UserPreferencesResponse {

    private Long id;
    private Long usuarioId;
    private String theme;
    private String language;
    private String timezone;
    private String dateFormat;
    private String timeFormat;
    private String homePage;
    private Integer recordsPerPage;
    private Boolean sidebarExpanded;
    private Boolean animationsEnabled;
    private Boolean emailNotifications;
    private Boolean pushNotifications;
    private Boolean soundEnabled;
    private Boolean confirmationsEnabled;
    private String customDashboard;
    private String quickAccessCards;
    private String fontSize;
    private Boolean highContrast;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UserPreferencesResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }

    public String getTimeFormat() { return timeFormat; }
    public void setTimeFormat(String timeFormat) { this.timeFormat = timeFormat; }

    public String getHomePage() { return homePage; }
    public void setHomePage(String homePage) { this.homePage = homePage; }

    public Integer getRecordsPerPage() { return recordsPerPage; }
    public void setRecordsPerPage(Integer recordsPerPage) { this.recordsPerPage = recordsPerPage; }

    public Boolean getSidebarExpanded() { return sidebarExpanded; }
    public void setSidebarExpanded(Boolean sidebarExpanded) { this.sidebarExpanded = sidebarExpanded; }

    public Boolean getAnimationsEnabled() { return animationsEnabled; }
    public void setAnimationsEnabled(Boolean animationsEnabled) { this.animationsEnabled = animationsEnabled; }

    public Boolean getEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(Boolean emailNotifications) { this.emailNotifications = emailNotifications; }

    public Boolean getPushNotifications() { return pushNotifications; }
    public void setPushNotifications(Boolean pushNotifications) { this.pushNotifications = pushNotifications; }

    public Boolean getSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(Boolean soundEnabled) { this.soundEnabled = soundEnabled; }

    public Boolean getConfirmationsEnabled() { return confirmationsEnabled; }
    public void setConfirmationsEnabled(Boolean confirmationsEnabled) { this.confirmationsEnabled = confirmationsEnabled; }

    public String getCustomDashboard() { return customDashboard; }
    public void setCustomDashboard(String customDashboard) { this.customDashboard = customDashboard; }

    public String getQuickAccessCards() { return quickAccessCards; }
    public void setQuickAccessCards(String quickAccessCards) { this.quickAccessCards = quickAccessCards; }

    public String getFontSize() { return fontSize; }
    public void setFontSize(String fontSize) { this.fontSize = fontSize; }

    public Boolean getHighContrast() { return highContrast; }
    public void setHighContrast(Boolean highContrast) { this.highContrast = highContrast; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
