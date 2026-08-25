// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;
import com.nexarank.api.security.TenantContext;
import java.util.UUID;

import com.nexarank.api.model.User;
import com.nexarank.api.model.UserActionToken;
import com.nexarank.api.repository.UserRepository;
import com.nexarank.api.repository.UserGroupMembershipRepository;
import com.nexarank.api.model.UserGroupMembership;
import java.time.Duration;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    // NR-65: reset links are security-sensitive (short-lived); verification
    // and invite links are lower-stakes and give people time to check email.
    private static final long RESET_TTL_MS = Duration.ofHours(1).toMillis();
    private static final long VERIFY_TTL_MS = Duration.ofHours(48).toMillis();
    private static final long INVITE_TTL_MS = Duration.ofDays(7).toMillis();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserGroupMembershipRepository membershipRepository;
    private final UserProjectService userProjectService;
    private final UserActionTokenService actionTokenService;
    private final UserEmailService emailService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       UserGroupMembershipRepository membershipRepository,
                       UserProjectService userProjectService,
                       UserActionTokenService actionTokenService,
                       UserEmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.membershipRepository = membershipRepository;
        this.userProjectService = userProjectService;
        this.actionTokenService = actionTokenService;
        this.emailService = emailService;
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
        // NR-65: login blocks on email_verified when an email is present —
        // a direct-create account (password already set by the admin) still
        // needs to prove ownership of the email before it can be used.
        boolean hasEmail = email != null && !email.isBlank();
        user.setEmailVerified(!hasEmail);
        User saved = userRepository.save(user);

        // NR-163 fix: MERCHANDISER/APPROVER accounts get NO project access at
        // creation time — a project-scoped role only means something once
        // it's assigned to a specific project via the project-role endpoints
        // (POST /users/{id}/projects/{projectId}, or MyTeam.js's self-service
        // path). This block used to call grantRoleOnAllTenantProjects() here,
        // which silently gave every new MERCHANDISER/APPROVER access to every
        // project in the tenant — the exact global-access hole NR-121 was
        // built to close. See NR-163.

        if (hasEmail) {
            UserActionTokenService.IssuedToken issued = actionTokenService.issue(saved.getId(), UserActionToken.Purpose.VERIFY_EMAIL, VERIFY_TTL_MS);
            emailService.sendVerification(saved, issued.rawToken());
        }

        return saved;
    }

    /**
     * NR-65: admin invites a user by email instead of setting a password
     * directly. The account row exists immediately (visible in the user
     * list as disabled/"Invited") with an unusable placeholder password —
     * disabled + no real password is the SAME mechanism login already
     * checks, so no separate "pending invite" gate is needed anywhere else.
     * Accepting the invite sets a real password, enables the account, and
     * counts as email verification (clicking the link already proves
     * ownership).
     */
    public User inviteUser(String username, String email, User.Role role, String displayName) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required to invite a user");
        }
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setTenantId(TenantContext.getTenantId());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole(role);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setEnabled(false);
        user.setEmailVerified(false);
        User saved = userRepository.save(user);

        // NR-163 fix: see createUser() above — no all-tenant-project grant here either.

        UserActionTokenService.IssuedToken issued = actionTokenService.issue(saved.getId(), UserActionToken.Purpose.INVITE, INVITE_TTL_MS);
        emailService.sendInvite(saved, issued.rawToken());
        return saved;
    }

    public void acceptInvite(String rawToken, String newPassword) {
        UserActionToken row = actionTokenService.findActive(rawToken, UserActionToken.Purpose.INVITE)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invite link"));
        User user = userRepository.findById(row.getUserId())
                .orElseThrow(() -> new IllegalStateException("User no longer exists"));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setEnabled(true);
        user.setEmailVerified(true);
        userRepository.save(user);
        actionTokenService.consume(row);
    }

    /** Always succeeds regardless of whether a match was found — anti-enumeration, matching NR-120's own /logout convention of not leaking account existence. */
    public void requestPasswordReset(String usernameOrEmail) {
        Optional<User> user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findFirstByEmail(usernameOrEmail));
        user.filter(User::isEnabled)
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .ifPresent(u -> {
                    UserActionTokenService.IssuedToken issued = actionTokenService.issue(u.getId(), UserActionToken.Purpose.RESET_PASSWORD, RESET_TTL_MS);
                    emailService.sendPasswordReset(u, issued.rawToken());
                });
    }

    public void resetPassword(String rawToken, String newPassword) {
        UserActionToken row = actionTokenService.findActive(rawToken, UserActionToken.Purpose.RESET_PASSWORD)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));
        User user = userRepository.findById(row.getUserId())
                .orElseThrow(() -> new IllegalStateException("User no longer exists"));
        user.setPassword(passwordEncoder.encode(newPassword));
        // Clicking an emailed reset link is the same proof-of-ownership event
        // as email verification — closes the loop for an unverified account
        // that reset its password before ever verifying.
        user.setEmailVerified(true);
        userRepository.save(user);
        actionTokenService.consume(row);
    }

    public void verifyEmail(String rawToken) {
        UserActionToken row = actionTokenService.findActive(rawToken, UserActionToken.Purpose.VERIFY_EMAIL)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification link"));
        User user = userRepository.findById(row.getUserId())
                .orElseThrow(() -> new IllegalStateException("User no longer exists"));
        user.setEmailVerified(true);
        userRepository.save(user);
        actionTokenService.consume(row);
    }

    /** Public/anti-enumeration by design — login blocks unverified users, so they can't authenticate to request this any other way. */
    public void resendVerification(String usernameOrEmail) {
        Optional<User> user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findFirstByEmail(usernameOrEmail));
        user.filter(u -> !u.isEmailVerified())
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .ifPresent(u -> {
                    UserActionTokenService.IssuedToken issued = actionTokenService.issue(u.getId(), UserActionToken.Purpose.VERIFY_EMAIL, VERIFY_TTL_MS);
                    emailService.sendVerification(u, issued.rawToken());
                });
    }

    /** Self-service profile update. Changing email re-arms verification, same as a fresh signup. */
    public User updateProfile(String userId, String displayName, String email) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (displayName != null) user.setDisplayName(displayName);
        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            boolean hasEmail = !email.isBlank();
            user.setEmailVerified(!hasEmail);
            User saved = userRepository.save(user);
            if (hasEmail) {
                UserActionTokenService.IssuedToken issued = actionTokenService.issue(saved.getId(), UserActionToken.Purpose.VERIFY_EMAIL, VERIFY_TTL_MS);
                emailService.sendVerification(saved, issued.rawToken());
            }
            return saved;
        }
        return userRepository.save(user);
    }

    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findByTenantId(TenantContext.getTenantId());
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
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
