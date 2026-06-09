// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.UserGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserGroupMembershipRepository extends JpaRepository<UserGroupMembership, String> {
    List<UserGroupMembership> findByUserId(String userId);
    List<UserGroupMembership> findByGroupId(String groupId);
    Optional<UserGroupMembership> findByUserIdAndGroupId(String userId, String groupId);
    void deleteByUserIdAndGroupId(String userId, String groupId);
    void deleteByUserId(String userId);
}
