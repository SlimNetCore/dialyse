package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.List;
import java.util.UUID;

/**
 * Les indicateurs d'un centre sur la période, ou les totaux de la société quand {@code centreId} est nul. Les effectifs de patients sont
 * conservés BRUTS ; seule leur publication est masquée ({@link #patientsPublies()}).
 * <p>
 * Suppression complémentaire (KB-DIR-01, CORRIGÉ le 2026-11-09, ADR 0112, validation DPO attendue) : le total d'une société est publié
 * {@code null} dès que l'un des centres qui le composent a un effectif masqué (1 à 4), sinon « total moins somme des centres publiés »
 * révélerait l'effectif masqué. Les booléens {@code patientsComposesDeMasques} et {@code patientsSousKtComposesDeMasques} sont calculés
 * AVANT le masque, à la consolidation ; ils sont faux pour le bilan d'un centre.
 */
public record BilanDuCentre(UUID centreId, String nom, boolean actif, long patients, long patientsSousKt, long seances,
                            long factures, ChiffresFinanciers finances, boolean patientsComposesDeMasques,
                            boolean patientsSousKtComposesDeMasques) {

    /** Le bilan d'un centre : aucun de ses effectifs n'est un total. */
    public BilanDuCentre(UUID centreId, String nom, boolean actif, long patients, long patientsSousKt, long seances,
                         long factures, ChiffresFinanciers finances) {
        this(centreId, nom, actif, patients, patientsSousKt, seances, factures, finances, false, false);
    }

    public static BilanDuCentre sansActivite() {
        return new BilanDuCentre(null, null, true, 0, 0, 0, 0, ChiffresFinanciers.zero());
    }

    /** Les totaux de la société : sommes des valeurs brutes des centres, avec l'indication « composé d'un effectif masqué ». */
    public static BilanDuCentre consolider(List<BilanDuCentre> centres) {
        long patients = 0, sousKt = 0, seances = 0, factures = 0;
        boolean patientsMasques = false, sousKtMasques = false;
        var ht = ChiffresFinanciers.montantNul();
        var ttc = ChiffresFinanciers.montantNul();
        var encaisse = ChiffresFinanciers.montantNul();
        for (BilanDuCentre c : centres) {
            patients += c.patients;
            sousKt += c.patientsSousKt;
            patientsMasques |= SeuilDAnonymat.estMasque(c.patients) || c.patientsComposesDeMasques;
            sousKtMasques |= SeuilDAnonymat.estMasque(c.patientsSousKt) || c.patientsSousKtComposesDeMasques;
            seances += c.seances;
            factures += c.factures;
            ht = ht.add(c.finances.caHt());
            ttc = ttc.add(c.finances.caTtc());
            encaisse = encaisse.add(c.finances.encaisse());
        }
        return new BilanDuCentre(null, null, true, patients, sousKt, seances, factures, new ChiffresFinanciers(ht, ttc, encaisse),
                patientsMasques, sousKtMasques);
    }

    public Long patientsPublies() {
        return patientsComposesDeMasques ? null : SeuilDAnonymat.masquer(patients);
    }

    public Long patientsSousKtPublies() {
        return patientsSousKtComposesDeMasques ? null : SeuilDAnonymat.masquer(patientsSousKt);
    }
}
