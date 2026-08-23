// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.compliance;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * NR-155: field-level encryption capability. Opt-in per column —
 * {@code autoApply} is deliberately left false, so annotating this onto a
 * field is the only way it ever activates:
 *
 * <pre>
 *   {@literal @}Convert(converter = EncryptedStringConverter.class)
 *   {@literal @}Column(name = "ssn")
 *   private String ssn;
 * </pre>
 *
 * Storage is a plain VARCHAR/TEXT — the encrypted value is itself a string
 * (Base64 of IV + ciphertext + GCM auth tag), so adopting this on an
 * existing column needs no schema change, only a data migration to
 * re-encrypt what's already there in plaintext.
 *
 * This exists so a HIPAA/PCI-scoped tenant can turn field encryption on for
 * a specific sensitive column without a re-architecture — per NR-155's own
 * framing, the capability existing is the deliverable; nothing in this
 * codebase applies it to a real field yet.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return FieldEncryptionUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FieldEncryptionUtil.decrypt(dbData);
    }
}
