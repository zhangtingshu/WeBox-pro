package com.webox.common;

public record CurrentUser(Long id, String email, String fullName, String role) {
    public boolean isAdmin() {
        return Constants.ROLE_ADMIN.equals(role);
    }
}
