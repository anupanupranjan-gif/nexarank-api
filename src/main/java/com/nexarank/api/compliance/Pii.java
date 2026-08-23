// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.compliance;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * NR-155: marks a JPA entity field as holding regulated/sensitive data. Purely
 * declarative — read at runtime by {@link PiiClassificationService} to produce
 * a data map for GDPR/HIPAA scoping, not enforced against reads or writes.
 * The prerequisite this closes is "can we even say which fields are
 * sensitive," not encryption or access control (those are separate items).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Pii {
    PiiCategory value();

    /** Optional human-readable note, e.g. why a field is tagged the way it is. */
    String note() default "";
}
