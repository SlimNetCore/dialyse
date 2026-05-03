package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.util.List;
import java.util.UUID;

public record UpdateUserRequest(
        String email, String fullName, boolean active, String password,
        List<UUID> roleIds, List<UUID> centerIds) {
}

