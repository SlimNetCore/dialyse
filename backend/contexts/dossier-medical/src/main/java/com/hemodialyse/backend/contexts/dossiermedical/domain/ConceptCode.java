package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.Objects;

/**
 * Code概念 (terminologie CIM-10, etc.) porté par un antécédent (AGENTS.md §14 Value Object).
 * Le code est obligatoire ; le système et le libellé sont optionnels.
 */
public record ConceptCode(String system, String code, String display) {

    public ConceptCode {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code requis");
        }
    }

    @Override
    public String toString() {
        return "ConceptCode[system=" + system + ", code=" + code + ", display=" + display + "]";
    }
}
