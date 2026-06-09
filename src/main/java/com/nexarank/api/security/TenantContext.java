// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.security;

public class TenantContext {

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<String> currentProject = new ThreadLocal<>();

    public static void setTenantId(String tenantId) {
        currentTenant.set(tenantId);
    }

    public static String getTenantId() {
        String tenantId = currentTenant.get();
        return tenantId != null ? tenantId : "default";
    }

    public static void setProjectId(String projectId) {
        currentProject.set(projectId);
    }

    public static String getProjectId() {
        String projectId = currentProject.get();
        return projectId != null ? projectId : "main";
    }

    public static void clear() {
        currentTenant.remove();
        currentProject.remove();
    }
}
