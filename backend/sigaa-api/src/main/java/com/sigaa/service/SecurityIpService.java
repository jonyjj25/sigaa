package com.sigaa.service;

import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
public class SecurityIpService {

    private final SecurityPolicyService policyService;

    public SecurityIpService(SecurityPolicyService policyService) {
        this.policyService = policyService;
    }

    public boolean isIpAllowed(String clientIp) {
        return policyService.isIpAllowed(clientIp);
    }

    public boolean isWithinAccessSchedule() {
        return policyService.isWithinAccessSchedule();
    }

    public boolean isIpRestrictionEnabled() {
        return policyService.isIpRestrictionEnabled();
    }

    public boolean isScheduleRestrictionEnabled() {
        return policyService.isScheduleRestrictionEnabled();
    }

    public AccessCheckResult checkAccess(String clientIp) {
        if (!isIpRestrictionEnabled() && !policyService.isScheduleRestrictionEnabled()) {
            return new AccessCheckResult(true, null);
        }

        if (isIpRestrictionEnabled() && !isIpAllowed(clientIp)) {
            return new AccessCheckResult(false, "Direccion IP no permitida: " + clientIp);
        }

        if (policyService.isScheduleRestrictionEnabled() && !isWithinAccessSchedule()) {
            LocalTime start = policyService.getAccessStartTime();
            LocalTime end = policyService.getAccessEndTime();
            return new AccessCheckResult(false,
                    "Acceso no permitido fuera del horario: " + start + " - " + end);
        }

        return new AccessCheckResult(true, null);
    }

    public record AccessCheckResult(boolean allowed, String reason) {
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
    }
}
