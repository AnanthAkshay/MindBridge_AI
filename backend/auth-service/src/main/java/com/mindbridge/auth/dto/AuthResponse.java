package com.mindbridge.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record AuthResponse(
    String accessToken,
    
    @JsonIgnore
    String refreshToken,
    
    UserInfo user
) {
    public record UserInfo(
        Long id,
        String email,
        String fullName,
        String role,
        boolean anonymous
    ) {}
}
