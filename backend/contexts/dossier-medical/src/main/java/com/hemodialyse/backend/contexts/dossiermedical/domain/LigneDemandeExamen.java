package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

public record LigneDemandeExamen(UUID id, String codeSystem, String code, String codeDisplay, String libelle,
                                 String commentaire) {

    public LigneDemandeExamen {
        if (id == null) throw new IllegalArgumentException("id requis");
        if (code == null || code.isBlank()) {
            if (libelle == null || libelle.isBlank()) {
                throw new IllegalArgumentException("code ou libelle requis");
            }
        }
    }

    public static LigneDemandeExamen creer(UUID id, String codeSystem, String code, String codeDisplay, String libelle, String commentaire) {
        return new LigneDemandeExamen(id, codeSystem, code, codeDisplay, libelle, commentaire);
    }
}