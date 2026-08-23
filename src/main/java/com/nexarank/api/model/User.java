// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nexarank.api.compliance.Pii;
import com.nexarank.api.compliance.PiiCategory;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Pii(value = PiiCategory.DIRECT_IDENTIFIER, note = "login identifier, often a real name or work email alias")
    @Column(nullable = false)
    private String username;

    @Pii(value = PiiCategory.CREDENTIAL, note = "bcrypt hash, not plaintext, still access-granting")
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Pii(PiiCategory.CONTACT_INFO)
    @Column
    private String email;

    @Pii(PiiCategory.DIRECT_IDENTIFIER)
    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column
    private Role role = Role.VIEWER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "group_id")
    private String groupId;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    /** NR-121: which project this user last activated (login or explicit switch). Null until first resolved. */
    @Column(name = "last_active_project_id")
    private String lastActiveProjectId;

    /** NR-65: an account with no email is treated as verified (nothing to verify) — see UserService. */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = true;

    public enum Role {
        STAKEHOLDER, VIEWER, MERCHANDISER, APPROVER, ADMIN, TENANT_ADMIN, SUPER_ADMIN
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getLastActiveProjectId() { return lastActiveProjectId; }
    public void setLastActiveProjectId(String lastActiveProjectId) { this.lastActiveProjectId = lastActiveProjectId; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
}
