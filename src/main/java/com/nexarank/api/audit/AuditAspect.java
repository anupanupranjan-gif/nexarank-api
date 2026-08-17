// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.audit;

import com.nexarank.api.service.AuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Tier 2 audit advices for non-rule entities.
 *
 * NR-70: MerchRule lifecycle auditing deliberately does NOT live here anymore.
 * Tier 1 needs the rule's before-state to compute a field-level diff, which an
 * around-advice on the service boundary can't see (the entity is already
 * mutated by the time the advice runs), so those writes moved into
 * MerchRuleService itself. Adding them back here would double-log every change.
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("execution(* com.nexarank.api.service.UserService.createUser(..))")
    public Object auditCreateUser(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        Object result = pjp.proceed();
        try {
            String username = args.length > 0 ? args[0].toString() : "unknown";
            auditService.log("USER_CREATED", "User", username, "username=" + username);
        } catch (Exception ignored) {}
        return result;
    }

    @Around("execution(* com.nexarank.api.service.SearchEngineConfigService.saveConfig(..))")
    public Object auditSaveEngineConfig(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        try {
            auditService.log("ENGINE_CONFIG_SAVED", "SearchEngineConfig", null, null);
        } catch (Exception ignored) {}
        return result;
    }

    @Around("execution(* com.nexarank.api.service.FacetConfigService.createFacet(..))")
    public Object auditCreateFacet(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        try {
            String id = (String) result.getClass().getMethod("getId").invoke(result);
            auditService.log("FACET_CREATED", "FacetConfig", id, null);
        } catch (Exception ignored) {}
        return result;
    }
}
