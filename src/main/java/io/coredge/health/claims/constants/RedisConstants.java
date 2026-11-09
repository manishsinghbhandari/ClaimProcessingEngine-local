package io.coredge.health.claims.constants;

import org.springframework.stereotype.Component;

@Component
public class RedisConstants {
    public static String EMERGENCY_CHANNEL = "emergencyClaimsChannel";
    public static String NORMAL_CHANNEL = "normalClaimsChannel";
    public static String PRIORITY_QUEUE = "priorityQueue";
    public static String NOTIFICATION_QUEUE = "notificationQueue";
    public static String NOTIFICATION_DEDUP_SET = "notificationDeDupQueueSet";
    public static String NOTIFICATION_DLQ = "notificationDlqQueue";
    public static final String GLOBAL_PROPERTY_CHANNEL = "globalPropertySyncChannel";
}
