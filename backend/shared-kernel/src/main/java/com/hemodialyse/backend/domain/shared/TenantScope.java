package com.hemodialyse.backend.domain.shared;

import java.util.Set;
import java.util.UUID;

/**
 * Value object used by application services to enforce center-scoped access.
 */
public record TenantScope(UUID centerId, String userId, Set<String> roles) {
}

