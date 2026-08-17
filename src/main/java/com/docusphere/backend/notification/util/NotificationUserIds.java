package com.docusphere.backend.notification.util;

import java.util.UUID;

public final class NotificationUserIds {

    private NotificationUserIds() {
    }

    public static UUID fromUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        return UUID.nameUUIDFromBytes(userId.toString().getBytes());
    }

    public static UUID fromEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email cannot be null or blank");
        }
        return UUID.nameUUIDFromBytes(email.toLowerCase().getBytes());
    }
}
