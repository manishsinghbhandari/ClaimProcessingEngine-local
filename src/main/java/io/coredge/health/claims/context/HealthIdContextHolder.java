package io.coredge.health.claims.context;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;

@UtilityClass
public class HealthIdContextHolder {
    private static final ThreadLocal<String> USER_ID = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_IP = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> UNIQUE_REQUEST_ID = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> HAS_ADMIN_ROLE = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> JWT_TOKEN = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_SOURCE = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> CLAIM_ID = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> CLAIM_TYPE = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> PATIENT_ID = new InheritableThreadLocal<>();


    // NEW: Expiry Date field
    private static final ThreadLocal<LocalDate> PRE_AUTH_EXPIRY_DATE = new InheritableThreadLocal<>();

    public String userId() {
        return StringUtils.defaultIfEmpty(USER_ID.get(), "");
    }
    public void userId(String userID) {
        USER_ID.set(userID);
    }

    public String clientIp() {
        return StringUtils.defaultIfEmpty(CLIENT_IP.get(),"");
    }
    public void clientIp(String clientIp) {
        CLIENT_IP.set(clientIp);
    }

    public String uniqueRequestId() {
        return StringUtils.defaultIfEmpty(UNIQUE_REQUEST_ID.get(),"");
    }
    public void uniqueRequestId(String uniqueRequestId) {
        UNIQUE_REQUEST_ID.set(uniqueRequestId);
    }

    // checking an admin role exists or not
    public boolean hasAdminRole() {
        return Boolean.parseBoolean(StringUtils.defaultIfEmpty(HAS_ADMIN_ROLE.get(), "false"));
    }
    public void hasAdminRole(boolean hasAdminRole) {
        HAS_ADMIN_ROLE.set(Boolean.toString(hasAdminRole));
    }

    public String jwtToken() {
        return StringUtils.defaultIfEmpty(JWT_TOKEN.get(),"");
    }
    public void jwtToken(String jwtToken) {
        JWT_TOKEN.set(jwtToken);
    }

    public String requestSource() {
        return StringUtils.defaultIfEmpty(REQUEST_SOURCE.get(), "");
    }
    public void requestSource(String userID) {
        REQUEST_SOURCE.set(userID);
    }

    // NEW: Expiry Date Getter/Setter
    public LocalDate preAuthExpiryDate() {
        return PRE_AUTH_EXPIRY_DATE.get();
    }
    public void preAuthExpiryDate(LocalDate expiryDate) {
        PRE_AUTH_EXPIRY_DATE.set(expiryDate);
    }

    public String claimId() {
        return StringUtils.defaultIfEmpty(CLAIM_ID.get(),"");
    }
    public void claimId(String claimId) {
        CLAIM_ID.set(claimId);
    }

    public String claimType() {
        return StringUtils.defaultIfEmpty(CLAIM_TYPE.get(),"");
    }
    public void claimType(String claimType) {
        CLAIM_TYPE.set(claimType);
    }

    public String patientId() {
        return StringUtils.defaultIfEmpty(PATIENT_ID.get(),"");
    }
    public void patientId(String patientId) {
        PATIENT_ID.set(patientId);
    }

    public void remove() {
        USER_ID.remove();
        CLIENT_IP.remove();
        UNIQUE_REQUEST_ID.remove();
        HAS_ADMIN_ROLE.remove();
        JWT_TOKEN.remove();
        PRE_AUTH_EXPIRY_DATE.remove(); // NEW: Clear expiry date
        CLAIM_ID.remove();
        CLAIM_TYPE.remove();
        PATIENT_ID.remove();
    }

    public static void setClaimContext(String claimId, String patientId, String claimType) {
        if (StringUtils.isNotEmpty(claimId)) HealthIdContextHolder.claimId(claimId);
        if (StringUtils.isNotEmpty(patientId)) HealthIdContextHolder.patientId(patientId);
        if (StringUtils.isNotEmpty(claimType)) HealthIdContextHolder.claimType(claimType);
    }

}
