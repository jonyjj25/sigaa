package com.sigaa.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdatePreferencesRequest {

    @NotBlank(message = "El tema es requerido")
    @Size(max = 20, message = "El tema no puede exceder 20 caracteres")
    private String theme;

    @NotBlank(message = "El idioma es requerido")
    @Size(max = 10, message = "El idioma no puede exceder 10 caracteres")
    private String language;

    @NotBlank(message = "La zona horaria es requerida")
    @Size(max = 100, message = "La zona horaria no puede exceder 100 caracteres")
    private String timezone;

    @Size(max = 20, message = "El formato de fecha no puede exceder 20 caracteres")
    private String dateFormat;

    @Size(max = 20, message = "El formato de hora no puede exceder 20 caracteres")
    private String timeFormat;

    @Size(max = 100, message = "La página inicial no puede exceder 100 caracteres")
    private String homePage;

    @Min(value = 5, message = "Los registros por página mínimos son 5")
    @Max(value = 100, message = "Los registros por página máximos son 100")
    private Integer recordsPerPage;

    private Boolean sidebarExpanded;
    private Boolean animationsEnabled;
    private Boolean emailNotifications;
    private Boolean pushNotifications;
    private Boolean soundEnabled;
    private Boolean confirmationsEnabled;

    @Size(max = 2000, message = "El dashboard personalizado no puede exceder 2000 caracteres")
    private String customDashboard;

    @Size(max = 2000, message = "Los accesos rápidos no pueden exceder 2000 caracteres")
    private String quickAccessCards;

    @Size(max = 20, message = "El tamaño de fuente no puede exceder 20 caracteres")
    private String fontSize;

    private Boolean highContrast;

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
}
