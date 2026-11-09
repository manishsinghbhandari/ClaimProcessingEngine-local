package io.coredge.health.claims.enums;

public enum BulkAction {
    SENT_BACK("Sent Back"),
    REJECT("Reject"),
    UNKNOWN("Unknown");

    private final String bulkAction;

    BulkAction(String bulkAction) {
        this.bulkAction = bulkAction;
    }

    public String getBulkAction() {
        return bulkAction;
    }
}