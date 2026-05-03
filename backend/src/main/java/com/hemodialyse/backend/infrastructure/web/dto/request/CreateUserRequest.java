package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.util.List;
import java.util.UUID;

public record CreateUserRequest(
        String username, String password, String email, String fullName,
        boolean active, List<UUID> roleIds, List<UUID> centerIds) {
}

