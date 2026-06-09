// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.GroupPermission;
import com.nexarank.api.model.GroupPermission.Permission;
import com.nexarank.api.model.UserGroup;
import com.nexarank.api.repository.GroupPermissionRepository;
import com.nexarank.api.repository.UserGroupRepository;
import com.nexarank.api.security.TenantContext;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserGroupService {

    private final UserGroupRepository groupRepository;
    private final GroupPermissionRepository permissionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public UserGroupService(UserGroupRepository groupRepository,
                            GroupPermissionRepository permissionRepository) {
        this.groupRepository = groupRepository;
        this.permissionRepository = permissionRepository;
    }

    public List<UserGroup> getAllGroups() {
        return groupRepository.findByTenantId(TenantContext.getTenantId());
    }

    public Optional<UserGroup> getById(String id) {
        return groupRepository.findById(id);
    }

    @Transactional
    public UserGroup createGroup(String name, String description, List<Permission> permissions) {
        UserGroup group = new UserGroup();
        group.setId(UUID.randomUUID().toString());
        group.setTenantId(TenantContext.getTenantId());
        group.setName(name);
        group.setDescription(description);
        group.setCreatedAt(Instant.now());
        UserGroup saved = groupRepository.save(group);
        savePermissions(saved.getId(), permissions);
        entityManager.flush();
        entityManager.clear();
        return groupRepository.findById(saved.getId()).orElse(saved);
    }

    @Transactional
    public Optional<UserGroup> updateGroup(String id, String name, String description,
                                            List<Permission> permissions) {
        return groupRepository.findById(id).map(group -> {
            if (name != null) group.setName(name);
            if (description != null) group.setDescription(description);
            groupRepository.save(group);
            if (permissions != null) {
                permissionRepository.deleteByGroupId(id);
                savePermissions(id, permissions);
            }
            return groupRepository.findById(id).orElse(group);
        });
    }

    @Transactional
    public void deleteGroup(String id) {
        permissionRepository.deleteByGroupId(id);
        groupRepository.deleteById(id);
    }

    private void savePermissions(String groupId, List<Permission> permissions) {
        if (permissions == null) return;
        for (Permission p : permissions) {
            GroupPermission gp = new GroupPermission();
            gp.setId(UUID.randomUUID().toString());
            gp.setGroupId(groupId);
            gp.setPermission(p);
            permissionRepository.save(gp);
        }
    }

    @Transactional
    public void seedDefaultGroups(String tenantId) {
        if (!groupRepository.findByTenantId(tenantId).isEmpty()) return;

        String saved = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);

        // Super Admin — all permissions
        createDefaultGroup(tenantId, "Super Admin", "Full access to everything",
                Arrays.asList(Permission.values()));

        // Search Admin
        createDefaultGroup(tenantId, "Search Admin", "Engine config, click intelligence, search quality",
                List.of(Permission.ENGINE_CONFIG_VIEW, Permission.ENGINE_CONFIG_MANAGE,
                        Permission.CLICK_INTELLIGENCE_VIEW,
                        Permission.SEARCH_QUALITY_VIEW, Permission.SEARCH_QUALITY_RUN,
                        Permission.AUDIT_LOG_VIEW));

        // Merchandiser
        createDefaultGroup(tenantId, "Merchandiser", "Create and edit rules, view facets",
                List.of(Permission.RULES_VIEW, Permission.RULES_CREATE, Permission.RULES_EDIT,
                        Permission.FACET_VIEW));

        // Approver
        createDefaultGroup(tenantId, "Approver", "Approve rules, view and manage facets",
                List.of(Permission.RULES_VIEW, Permission.RULES_CREATE, Permission.RULES_EDIT,
                        Permission.RULES_APPROVE, Permission.FACET_VIEW, Permission.FACET_MANAGE));

        // Analyst
        createDefaultGroup(tenantId, "Analyst", "Click intelligence, search quality, audit log",
                List.of(Permission.CLICK_INTELLIGENCE_VIEW,
                        Permission.SEARCH_QUALITY_VIEW, Permission.SEARCH_QUALITY_RUN,
                        Permission.AUDIT_LOG_VIEW));

        // Viewer
        createDefaultGroup(tenantId, "Viewer", "Read-only access to rules",
                List.of(Permission.RULES_VIEW));

        TenantContext.setTenantId(saved);
    }

    private void createDefaultGroup(String tenantId, String name, String description,
                                     List<Permission> permissions) {
        UserGroup group = new UserGroup();
        group.setId(UUID.randomUUID().toString());
        group.setTenantId(tenantId);
        group.setName(name);
        group.setDescription(description);
        group.setDefault(true);
        group.setCreatedAt(Instant.now());
        UserGroup saved = groupRepository.save(group);
        savePermissions(saved.getId(), permissions);
    }
}
