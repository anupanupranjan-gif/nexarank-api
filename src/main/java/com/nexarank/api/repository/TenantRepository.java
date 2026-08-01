// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, String> {
    Optional<Tenant> findByIdAndEnabled(String id, boolean enabled);

    /** NR-36: every enabled tenant, for the monthly summary email to loop over. */
    List<Tenant> findByEnabled(boolean enabled);
}
