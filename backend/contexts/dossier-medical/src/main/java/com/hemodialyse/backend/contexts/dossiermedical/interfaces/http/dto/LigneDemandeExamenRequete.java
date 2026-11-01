package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.util.UUID;

public record LigneDemandeExamenRequete(UUID id, String codeSystem, String code, String codeDisplay,
                                        String libelle, String commentaire) {
}
