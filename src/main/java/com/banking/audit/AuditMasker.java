package com.banking.audit;

/**
 * Utility for masking sensitive values before they are written to audit logs.
 * Call these methods on any PII or financial identifiers included in audit detail strings.
 */
public final class AuditMasker {

    private AuditMasker() {}

    /**
     * Masks all but the last four characters of an account number.
     * Example: "8600111122223333" → "************3333"
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        String visible = accountNumber.substring(accountNumber.length() - 4);
        return "*".repeat(accountNumber.length() - 4) + visible;
    }

    /**
     * Masks an email address, preserving the first character and the domain.
     * Example: "john.doe@bank.com" → "j***@bank.com"
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.isEmpty()) {
            return "****" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }

    /**
     * Masks all but the last two digits of a phone number.
     * Example: "+998901234567" → "+**********67"
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 2) {
            return "****";
        }
        String visible = phone.substring(phone.length() - 2);
        return "*".repeat(phone.length() - 2) + visible;
    }
}
