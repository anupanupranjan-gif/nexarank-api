// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;
import com.nexarank.api.security.TenantContext;
import java.util.UUID;

import com.nexarank.api.model.User;
import com.nexarank.api.repository.UserRepository;
import com.nexarank.api.repository.UserGroupMembershipRepository;
import com.nexarank.api.model.UserGroupMembership;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserGroupMembershipRepository membershipRepository;
    private final UserProjectService userProjectService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       UserGroupMembershipRepository membershipRepository,
                       UserProjectService userProjectService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.membershipRepository = membershipRepository;
        this.userProjectService = userProjectService;
    }

    public List<UserGroupMembership> getUserGroups(String userId) {
        return membershipRepository.findByUserId(userId);
    }

    public UserGroupMembership addUserToGroup(String userId, String groupId) {
        if (membershipRepository.findByUserIdAndGroupId(userId, groupId).isPresent()) {
            return membershipRepository.findByUserIdAndGroupId(userId, groupId).get();
        }
        UserGroupMembership m = new UserGroupMembership();
        m.setId(UUID.randomUUID().toString());
        m.setUserId(userId);
        m.setGroupId(groupId);
        return membershipRepository.save(m);
    }

    public void removeUserFromGroup(String userId, String groupId) {
        membershipRepository.deleteByUserIdAndGroupId(userId, groupId);
    }

    public User createUser(String username, String rawPassword, User.Role role, String email, String displayName) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setTenantId(TenantContext.getTenantId());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setEnabled(true);
        User saved = userRepository.save(user);

        // NR-121 step 2: User.role remains authoritative for auth (unchanged,
        // read by JwtAuthFilter/SecurityConfig) — this only additionally
        // populates user_projects for the project-scoped roles, granted on
        // every project in the tenant so a newly-created user behaves
        // identically to the V37 migration's backfill for existing users,
        // rather than needing separate logic. No authorization check reads
        // this yet.
        if (role == User.Role.MERCHANDISER || role == User.Role.APPROVER) {
            userProjectService.grantRoleOnAllTenantProjects(saved.getId(), saved.getTenantId(), role);
        }

        return saved;
    }

    public List<User> getAllUsers() {
        return userRepository.findByTenantId(TenantContext.getTenantId());
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }
}
