package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.List;
import java.util.UUID;

/**
 * Les indicateurs d'un centre sur la période, ou les totaux de la société quand {@code centreId} est nul. Les effectifs de patients sont
 * conservés BRUTS ; seule leur publication est masquée ({@link #patientsPublies()}). Les totaux de la société se calculent donc sur les
 * bruts : la différence entre le total et la somme des centres publiés révèle un effectif masqué (KB-DIR-01, reproduit, DPO).
 */
public record BilanDuCentre(UUID centreId, String nom, boolean actif, long patients, long patientsSousKt, long seances,
                            long factures, ChiffresFinanciers finances) {

    public static BilanDuCentre sansActivite() {
        return new BilanDuCentre(null, null, true, 0, 0, 0, 0, ChiffresFinanciers.zero());
    }

    /** Les totaux de la société : sommes des valeurs brutes des centres. */
    public static BilanDuCentre consolider(List<BilanDuCentre> centres) {
        long patients = 0, sousKt = 0, seances = 0, factures = 0;
        var ht = ChiffresFinanciers.montantNul();
        var ttc = ChiffresFinanciers.montantNul();
        var encaisse = ChiffresFinanciers.montantNul();
        for (BilanDuCentre c : centres) {
            patients += c.patients;
            sousKt += c.patientsSousKt;
            seances += c.seances;
            factures += c.factures;
            ht = ht.add(c.finances.caHt());
            ttc = ttc.add(c.finances.caTtc());
            encaisse = encaisse.add(c.finances.encaisse());
        }
        return new BilanDuCentre(null, null, true, patients, sousKt, seances, factures, new ChiffresFinanciers(ht, ttc, encaisse));
    }

    public Long patientsPublies() {
        return SeuilDAnonymat.masquer(patients);
    }

    public Long patientsSousKtPublies() {
        return SeuilDAnonymat.masquer(patientsSousKt);
    }
}
