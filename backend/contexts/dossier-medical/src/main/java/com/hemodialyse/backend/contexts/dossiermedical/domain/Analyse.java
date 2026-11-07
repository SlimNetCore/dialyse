package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Analyse (bilan biologique à colonnes fixes d'un patient, table {@code resultats_analyses}) : le modèle qui alimente le suivi de
 * l'anémie, KDIGO, les statistiques médicales et l'export FHIR ; il coexiste sans projection avec {@link ObservationBiologique}. Racine
 * d'agrégat à une seule entité.
 * <p>
 * Invariants : la date de prélèvement, par défaut le jour de l'horloge, n'est jamais absente (aucune limite dans le futur : reproduit,
 * KB-MED-79) ; le patient et le centre ne changent jamais (écart décidé KB-MED-74 : le legacy déplaçait le bilan d'un autre patient ou
 * d'un autre centre) ; les mesures tiennent dans leurs colonnes (400).
 * <p>
 * Reproduit (à confirmer avec les métiers et le DPO) : la modification REMPLACE le bilan en entier (une mesure absente est effacée, la
 * date absente redevient le jour) et réinitialise sa date de création (comme l'« enregistrer » du legacy, KB-MED-74) ; plusieurs bilans le
 * même jour sont permis ; aucun événement.
 */
public final class Analyse {
    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private LocalDate datePrelevement;
    private MesuresDuBilan mesures;
    private Instant creeLe;
    private Instant modifieLe;

    private Analyse(UUID id, PatientId patient, CentreId centre, LocalDate datePrelevement, MesuresDuBilan mesures, Instant creeLe,
                    Instant modifieLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.datePrelevement = datePrelevement;
        this.mesures = mesures;
        this.creeLe = creeLe;
        this.modifieLe = modifieLe;
    }

    /** Enregistre un bilan. {@code datePrelevement} absente : {@code aujourdhui} (le jour de l'horloge). */
    public static Analyse enregistrer(UUID id, PatientId patient, CentreId centre, LocalDate datePrelevement, MesuresDuBilan mesures,
                                      LocalDate aujourdhui, Instant maintenant) {
        mesures.verifierLesBornes();
        return new Analyse(id, patient, centre, datePrelevement != null ? datePrelevement : aujourdhui, mesures, maintenant, maintenant);
    }

    public static Analyse reconstituer(UUID id, PatientId patient, CentreId centre, LocalDate datePrelevement, MesuresDuBilan mesures,
                                       Instant creeLe, Instant modifieLe) {
        return new Analyse(id, patient, centre, datePrelevement, mesures, creeLe, modifieLe);
    }

    /** Remplace le bilan en entier (date absente : le jour ; toute mesure absente est effacée) et réinitialise la date de création. */
    public void remplacer(LocalDate datePrelevement, MesuresDuBilan mesures, LocalDate aujourdhui, Instant maintenant) {
        mesures.verifierLesBornes();
        this.datePrelevement = datePrelevement != null ? datePrelevement : aujourdhui;
        this.mesures = mesures;
        this.creeLe = maintenant;
        this.modifieLe = maintenant;
    }

    public UUID id() {
        return id;
    }

    public PatientId patient() {
        return patient;
    }

    public CentreId centre() {
        return centre;
    }

    public LocalDate datePrelevement() {
        return datePrelevement;
    }

    public MesuresDuBilan mesures() {
        return mesures;
    }

    /** Peut être nulle (bilan importé). */
    public Instant creeLe() {
        return creeLe;
    }

    /** Peut être nulle (bilan importé). */
    public Instant modifieLe() {
        return modifieLe;
    }
}
