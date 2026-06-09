// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.GroupPermission.Permission;
import com.nexarank.api.service.UserGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/groups")
public class UserGroupController {

    private final UserGroupService groupService;

    public UserGroupController(UserGroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ResponseEntity<?> getAllGroups() {
        return ResponseEntity.ok(groupService.getAllGroups());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getGroup(@PathVariable String id) {
        return groupService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createGroup(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String description = (String) body.get("description");

        if (name == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }

        List<Permission> permissions = null;
        if (body.containsKey("permissions")) {
            try {
                List<String> permStrings = (List<String>) body.get("permissions");
                permissions = permStrings.stream()
                        .map(Permission::valueOf)
                        .toList();
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid permission value"));
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.createGroup(name, description, permissions));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateGroup(@PathVariable String id,
                                          @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String description = (String) body.get("description");

        List<Permission> permissions = null;
        if (body.containsKey("permissions")) {
            try {
                List<String> permStrings = (List<String>) body.get("permissions");
                permissions = permStrings.stream()
                        .map(Permission::valueOf)
                        .toList();
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid permission value"));
            }
        }

        return groupService.updateGroup(id, name, description, permissions)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGroup(@PathVariable String id) {
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissions")
    public ResponseEntity<?> listAllPermissions() {
        return ResponseEntity.ok(Permission.values());
    }

    @PostMapping("/seed")
    public ResponseEntity<?> seedDefaultGroups(@RequestParam String tenantId) {
        groupService.seedDefaultGroups(tenantId);
        return ResponseEntity.ok(Map.of("message", "Default groups seeded for tenant: " + tenantId));
    }
}
