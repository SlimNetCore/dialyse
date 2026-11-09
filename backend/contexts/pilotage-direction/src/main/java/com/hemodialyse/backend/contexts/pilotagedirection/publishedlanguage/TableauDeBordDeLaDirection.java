package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié du Pilotage direction ({@code pilotagedirection.v1}, ADR 0102, PD1) : le tableau de bord de la direction d'une société.
 * Agrégats ANONYMES seulement (aucune ligne de patient) : tout effectif inférieur au seuil d'anonymat est publié {@code null}, zéro reste
 * zéro. Les totaux de la société sont calculés sur les effectifs BRUTS (KB-DIR-01, reproduit à l'identique ; à trancher par le DPO).
 * <p>
 * Les composants des enregistrements portent les noms du JSON servi par {@code /api/v1/direction/*} et figé dans les instantanés
 * mensuels : NE PAS les renommer sans migrer les instantanés (RG-DIR-090). Le périmètre de la société est fourni par l'appelant
 * (le jeton de la session, revérifié par la plateforme) ; ce port ne contrôle aucun droit.
 */
public interface TableauDeBordDeLaDirection {

    /** La société et la liste de ses centres, triés par nom (les centres inactifs compris). */
    SocieteDeLaDirection societe(UUID societeId);

    /** La vue d'ensemble par centre et consolidée ; {@code from} / {@code to} nuls : défauts du 1er janvier et d'aujourd'hui. */
    VueDEnsemble vueDEnsemble(UUID societeId, LocalDate from, LocalDate to);

    /** Les répartitions par centre : sexe, âge, caisse d'assurance, anémie. */
    Repartition repartition(UUID societeId, LocalDate from, LocalDate to);

    record CentreDeLaSociete(UUID id, String nom, boolean actif) {
    }

    record SocieteDeLaDirection(UUID id, String raisonSociale, List<CentreDeLaSociete> centres) {
    }

    /** Indicateurs d'un centre, ou totaux de la société quand {@code centerId} est {@code null}. */
    record StatistiquesDuCentre(UUID centerId, String nom, boolean actif, Long patients, Long patientsSousKt, Long seances,
                                Long factures, BigDecimal caHt, BigDecimal caTtc, BigDecimal encaisse,
                                BigDecimal resteARecouvrer, BigDecimal tauxEncaissement) {
    }

    record PointMensuel(String mois, UUID centerId, Long seances, BigDecimal caHt, BigDecimal caTtc) {
    }

    /** Totaux de la période de même durée précédant immédiatement la période affichée (activité et finances seulement). */
    record PeriodePrecedente(LocalDate from, LocalDate to, Long seances, BigDecimal caHt, BigDecimal caTtc,
                             BigDecimal encaisse, BigDecimal resteARecouvrer, BigDecimal tauxEncaissement) {
    }

    record VueDEnsemble(UUID societeId, String societeNom, LocalDate from, LocalDate to, OffsetDateTime generatedAt,
                        Integer seuilAnonymat, List<StatistiquesDuCentre> centres, StatistiquesDuCentre totaux,
                        List<PointMensuel> mensuel, PeriodePrecedente periodePrecedente) {
    }

    record RepartitionParSexe(UUID centerId, String nom, Long masculin, Long feminin, Long autre) {
    }

    record Tranche(String code, Long count) {
    }

    record RepartitionParAge(UUID centerId, String nom, List<Tranche> tranches) {
    }

    /** {@code caisseCode} vide : patients sans caisse renseignée. */
    record CaisseDuCentre(UUID centerId, String centre, String caisseCode, String caisse, Long patients, Long seances,
                          BigDecimal caHt) {
    }

    record CaisseDeLaSociete(String caisseCode, String caisse, Long patients, Long seances, BigDecimal caHt) {
    }

    record TraitementDeLAnemie(UUID centerId, String nom, Long patientsEpo, Long patientsFer, Long administreesEpo,
                               Long administreesFer, Long nonAdministrees, BigDecimal tauxAdministration,
                               Long patientsSousEpo, Long patientsSousFer) {
    }

    record Repartition(UUID societeId, LocalDate from, LocalDate to, OffsetDateTime generatedAt, Integer seuilAnonymat,
                       List<RepartitionParSexe> sexe, List<RepartitionParAge> ages, List<CaisseDuCentre> caisses,
                       List<CaisseDeLaSociete> caisseTotaux, List<TraitementDeLAnemie> anemie) {
    }
}
