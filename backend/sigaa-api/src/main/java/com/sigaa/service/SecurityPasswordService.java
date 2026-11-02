package com.sigaa.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class SecurityPasswordService {

    private final SecurityPolicyService policyService;

    public SecurityPasswordService(SecurityPolicyService policyService) {
        this.policyService = policyService;
    }

    public PasswordValidationResult validate(String password) {
        List<String> errors = new ArrayList<>();

        int minLength = policyService.getPasswordMinLength();
        int maxLength = policyService.getPasswordMaxLength();

        if (password == null || password.isEmpty()) {
            errors.add("La contrasena no puede estar vacia");
            return new PasswordValidationResult(false, errors);
        }

        if (password.length() < minLength) {
            errors.add("La contrasena debe tener al menos " + minLength + " caracteres");
        }

        if (password.length() > maxLength) {
            errors.add("La contrasena no puede exceder " + maxLength + " caracteres");
        }

        if (policyService.requiresUppercase() && !Pattern.compile("[A-Z]").matcher(password).find()) {
            errors.add("La contrasena debe contener al menos una letra mayuscula");
        }

        if (policyService.requiresLowercase() && !Pattern.compile("[a-z]").matcher(password).find()) {
            errors.add("La contrasena debe contener al menos una letra minuscula");
        }

        if (policyService.requiresDigits() && !Pattern.compile("[0-9]").matcher(password).find()) {
            errors.add("La contrasena debe contener al menos un numero");
        }

        if (policyService.requiresSpecialChars() && !Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]").matcher(password).find()) {
            errors.add("La contrasena debe contener al menos un caracter especial (!@#$%^&*)");
        }

        return new PasswordValidationResult(errors.isEmpty(), errors);
    }

    public boolean isPasswordValid(String password) {
        return validate(password).isValid();
    }

    public int getMaxLoginAttempts() {
        return policyService.getMaxLoginAttempts();
    }

    public long getLockoutDurationMs() {
        return policyService.getLockoutDurationMs();
    }

    public String getLockoutPolicy() {
        return policyService.getLockoutPolicy();
    }

    public boolean isEscalatingLockout() {
        return policyService.isEscalatingLockout();
    }

    public record PasswordValidationResult(boolean valid, List<String> errors) {
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
    }
}
