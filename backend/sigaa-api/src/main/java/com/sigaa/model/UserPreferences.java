package com.sigaa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "theme", nullable = false, length = 20)
    private String theme = "light";

    @Column(name = "language", nullable = false, length = 10)
    private String language = "es";

    @Column(name = "timezone", nullable = false, length = 100)
    private String timezone = "America/Panama";

    @Column(name = "date_format", nullable = false, length = 20)
    private String dateFormat = "DD/MM/YYYY";

    @Column(name = "time_format", nullable = false, length = 20)
    private String timeFormat = "HH:mm";

    @Column(name = "home_page", nullable = false, length = 100)
    private String homePage = "dashboard";

    @Column(name = "records_per_page", nullable = false)
    private Integer recordsPerPage = 20;

    @Column(name = "sidebar_expanded", nullable = false)
    private Boolean sidebarExpanded = true;

    @Column(name = "animations_enabled", nullable = false)
    private Boolean animationsEnabled = true;

    @Column(name = "email_notifications", nullable = false)
    private Boolean emailNotifications = true;

    @Column(name = "push_notifications", nullable = false)
    private Boolean pushNotifications = true;

    @Column(name = "sound_enabled", nullable = false)
    private Boolean soundEnabled = true;

    @Column(name = "confirmations_enabled", nullable = false)
    private Boolean confirmationsEnabled = true;

    @Column(name = "custom_dashboard", columnDefinition = "TEXT")
    private String customDashboard;

    @Column(name = "quick_access_cards", columnDefinition = "TEXT")
    private String quickAccessCards;

    @Column(name = "font_size", nullable = false, length = 20)
    private String fontSize = "medium";

    @Column(name = "high_contrast", nullable = false)
    private Boolean highContrast = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserPreferences() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

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
