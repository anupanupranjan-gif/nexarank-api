// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.audit;

import com.nexarank.api.service.AuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("execution(* com.nexarank.api.service.MerchRuleService.createRule(..))")
    public Object auditCreateRule(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        try {
            Object rule = result;
            String id = (String) rule.getClass().getMethod("getId").invoke(rule);
            String query = (String) rule.getClass().getMethod("getQuery").invoke(rule);
            auditService.log("RULE_CREATED", "MerchRule", id, "query=" + query);
        } catch (Exception ignored) {}
        return result;
    }

    @Around("execution(* com.nexarank.api.service.MerchRuleService.updateRule(..))")
    public Object auditUpdateRule(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        try {
            if (result instanceof java.util.Optional<?> opt && opt.isPresent()) {
                Object rule = opt.get();
                String id = (String) rule.getClass().getMethod("getId").invoke(rule);
                auditService.log("RULE_UPDATED", "MerchRule", id, null);
            }
        } catch (Exception ignored) {}
        return result;
    }

    @Around("execution(* com.nexarank.api.service.MerchRuleService.approveRule(..))")
    public Object auditApproveRule(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        Object result = pjp.proceed();
        try {
            String id = args.length > 0 ? args[0].toString() : "unknown";
            auditService.log("RULE_APPROVED", "MerchRule", id, null);
        } catch (Exception ignored) {}
        return result;
    }

    @Around("execution(* com.nexarank.api.service.MerchRuleService.rejectRule(..))")
    public Object auditRejectRule(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        Object result = pjp.proceed();
        try {
            String id = args.length > 0 ? args[0].toString() : "unknown";
            auditService.log("RULE_REJECTED", "MerchRule", id, null);
        } catch (Exception ignored) {}
        return result;
    }

    @Around("execution(* com.nexarank.api.service.MerchRuleService.deleteRule(..))")
    public Object auditDeleteRule(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        Object result = pjp.proceed();
        try {
            String id = args.length > 0 ? args[0].toString() : "unknown";
            auditService.log("RULE_DELETED", "MerchRule", id, null);
        } catch (Exception ignored) {}
        return result;
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
