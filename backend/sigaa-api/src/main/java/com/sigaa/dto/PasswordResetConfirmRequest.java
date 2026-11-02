package com.sigaa.dto;

public class PasswordResetConfirmRequest {
    private String resetToken;
    private String newPassword;
    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
