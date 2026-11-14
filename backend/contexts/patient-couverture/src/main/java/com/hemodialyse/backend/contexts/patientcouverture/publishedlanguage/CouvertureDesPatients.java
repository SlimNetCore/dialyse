package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port publié (lecture) : <b>la couverture des patients d'un centre</b> (prise en charge, forfait, attestation de droits ;
 * patientcouverture.v1, PC5). Bornée au centre. Aucune règle nouvelle : ce sont les lectures que Absences, Facturation et Séance
 * faisaient en SQL direct sur {@code prise_en_charge} et {@code attestation_droit}, à l'identique.
 * <p>
 * ARGENT, reproduit et À DÉCIDER AVEC LES MÉTIERS (KB-PAT-25, 26, 46) : le statut de la PEC n'est PAS regardé pour le forfait
 * applicable ; la facturation d'une séance exige une PEC validée ET une attestation couvrant le jour, même pour un vacancier.
 */
public interface CouvertureDesPatients {

    /**
     * Le forfait applicable à une date (règle du legacy, KB-ABS-16) : effectif d'abord, sinon demandé, de la prise en charge dont
     * la période effective ou demandée couvre la date (celle portant un forfait effectif, puis la plus récemment créée) ; à
     * défaut, la plus récente portant un forfait, sans condition de date. Le statut de la PEC n'est jamais regardé.
     */
    Optional<ForfaitApplicable> forfaitApplicable(UUID centre, UUID patient, LocalDate date);

    /**
     * Toutes les prises en charge du patient qui portent un forfait connu du centre (effectif d'abord, sinon demandé), sans ordre
     * garanti, avec leurs périodes : la facturation y applique sa politique de forfait.
     */
    List<PriseEnChargeTarifee> prisesEnChargeTarifees(UUID centre, UUID patient);

    /**
     * Pourquoi une séance de ce jour n'est pas facturable, ou vide si elle l'est. Trois étages, dans cet ordre : aucune PEC
     * porteuse d'un forfait ne couvre le jour ; une PEC couvre mais aucune n'est validée ; une PEC validée couvre mais aucune
     * attestation ne couvre le jour (même pour un vacancier).
     */
    Optional<CauseDeNonFacturation> causeDeNonFacturation(UUID centre, UUID patient, LocalDate jour);

    /** Cette PEC, de ce patient et de ce centre, est-elle au statut {@code VALIDEE} ? */
    boolean pecValidee(UUID centre, UUID patient, UUID pec);

    /** {@code code} : le code du forfait au catalogue du centre (LEC-PAT ii, ADR 0177 : la Séance l'expose avec le forfait courant). */
    record ForfaitApplicable(UUID id, String code, String libelle, BigDecimal prix) {
    }

    /**
     * Une PEC et son forfait ; {@code forfaitEffectif} dit si le forfait est celui de l'accord (sinon celui de la demande) ;
     * les périodes peuvent être nulles.
     */
    record PriseEnChargeTarifee(UUID forfaitId, String libelle, BigDecimal prix, boolean forfaitEffectif, LocalDate debutEffectif,
                                LocalDate finEffectif, LocalDate debutDemande, LocalDate finDemande, Instant creeLe) {
    }

    enum CauseDeNonFacturation {
        AUCUNE_PEC_COUVRANTE,
        PEC_NON_VALIDEE,
        ATTESTATION_ABSENTE
    }
}
