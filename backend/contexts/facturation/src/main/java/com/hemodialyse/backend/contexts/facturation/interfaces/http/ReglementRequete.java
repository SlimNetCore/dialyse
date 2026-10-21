package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.reglement.DemandeDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.ModeDeReglement;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code POST /reglements/{factureId}/paiements} : mêmes champs que le legacy ({@code centerId} et {@code montant}
 * obligatoires, {@code dateReglement}, {@code userId}), plus trois ajouts facultatifs de F4 : {@code modeReglement}
 * ({@code BANQUE, CHEQUE, CAISSE, ESPECES}), {@code referencePaiement} (texte libre) et {@code cleIdempotence} (un renvoi avec la
 * même clé pour la même facture ne crée pas de doublon). {@code userId} est ignoré : l'auteur est l'utilisateur de la session.
 * Un montant négatif est un remboursement.
 */
public record ReglementRequete(
        @NotNull UUID centerId,
        @NotNull BigDecimal montant,
        LocalDate dateReglement,
        String userId,
        String modeReglement,
        String referencePaiement,
        String cleIdempotence) {

    DemandeDeReglement demande() {
        return new DemandeDeReglement(montant, dateReglement, ModeDeReglement.depuis(modeReglement), referencePaiement,
                cleIdempotence);
    }
}
