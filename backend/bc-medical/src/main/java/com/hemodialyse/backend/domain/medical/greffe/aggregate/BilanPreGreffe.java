package com.hemodialyse.backend.domain.medical.greffe.aggregate;

import com.hemodialyse.backend.domain.medical.greffe.entity.DecisionRcp;
import com.hemodialyse.backend.domain.medical.greffe.specification.TransitionStatutBilanGreffeSpecification;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanGreffe;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — dossier de préparation à la greffe rénale du patient receveur (1 par
 * patient, patron {@code DossierMedicalPatient} : get-or-create, upsert).
 * <p>
 * Frontière transactionnelle : le bilan et ses décisions de RCP ({@link DecisionRcp}) forment un
 * seul agrégat — une décision de RCP n'a pas de cycle de vie propre en dehors du bilan qu'elle
 * fait progresser.
 * <p>
 * Invariant : les transitions de statut suivent strictement
 * {@link TransitionStatutBilanGreffeSpecification} (notamment : l'inscription sur liste
 * d'attente exige d'être passé par le statut {@code ELIGIBLE}).
 */
public final class BilanPreGreffe {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final List<DecisionRcp> decisionsRcp;
    private final OffsetDateTime createdAt;
    private StatutBilanGreffe statut;
    private LocalDate dateDebutBilan;
    private LocalDate dateInscriptionListeAttente;
    private LocalDate dateGreffe;
    private String groupeSanguinConfirme;
    private String typageHla;
    private BigDecimal praClasseI;
    private BigDecimal praClasseII;
    private String contreIndications;
    private String conclusionNephrologue;
    private OffsetDateTime updatedAt;

    private BilanPreGreffe(UUID id, UUID patientId, UUID centerId, StatutBilanGreffe statut,
                           LocalDate dateDebutBilan, LocalDate dateInscriptionListeAttente, LocalDate dateGreffe,
                           String groupeSanguinConfirme, String typageHla, BigDecimal praClasseI,
                           BigDecimal praClasseII, String contreIndications, String conclusionNephrologue,
                           List<DecisionRcp> decisionsRcp, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.statut = statut;
        this.dateDebutBilan = dateDebutBilan;
        this.dateInscriptionListeAttente = dateInscriptionListeAttente;
        this.dateGreffe = dateGreffe;
        this.groupeSanguinConfirme = groupeSanguinConfirme;
        this.typageHla = typageHla;
        this.praClasseI = praClasseI;
        this.praClasseII = praClasseII;
        this.contreIndications = contreIndications;
        this.conclusionNephrologue = conclusionNephrologue;
        this.decisionsRcp = new ArrayList<>(decisionsRcp);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static BilanPreGreffe ouvrir(UUID patientId, UUID centerId) {
        if (patientId == null || centerId == null) {
            throw new BusinessException("BILAN_GREFFE_CHAMPS_REQUIS", "Patient et centre sont obligatoires");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return new BilanPreGreffe(UUID.randomUUID(), patientId, centerId, StatutBilanGreffe.NON_DEBUTE,
                null, null, null, null, null, null, null, null, null, new ArrayList<>(), now, now);
    }

    public static BilanPreGreffe reconstituer(UUID id, UUID patientId, UUID centerId, StatutBilanGreffe statut,
                                              LocalDate dateDebutBilan, LocalDate dateInscriptionListeAttente,
                                              LocalDate dateGreffe, String groupeSanguinConfirme, String typageHla,
                                              BigDecimal praClasseI, BigDecimal praClasseII, String contreIndications,
                                              String conclusionNephrologue, List<DecisionRcp> decisionsRcp,
                                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new BilanPreGreffe(id, patientId, centerId, statut, dateDebutBilan, dateInscriptionListeAttente,
                dateGreffe, groupeSanguinConfirme, typageHla, praClasseI, praClasseII, contreIndications,
                conclusionNephrologue, decisionsRcp, createdAt, updatedAt);
    }

    public void changerStatut(StatutBilanGreffe nouveauStatut) {
        if (nouveauStatut == null) {
            throw new BusinessException("BILAN_GREFFE_STATUT_REQUIS", "Le statut est obligatoire");
        }
        if (!TransitionStatutBilanGreffeSpecification.estAutorisee(statut, nouveauStatut)) {
            throw new BusinessException("BILAN_GREFFE_TRANSITION_INVALIDE",
                    "Transition de statut invalide : " + statut + " → " + nouveauStatut);
        }
        this.statut = nouveauStatut;
        if (nouveauStatut == StatutBilanGreffe.BILAN_EN_COURS && dateDebutBilan == null) {
            this.dateDebutBilan = LocalDate.now();
        }
        if (nouveauStatut == StatutBilanGreffe.INSCRIT_LISTE_ATTENTE) {
            this.dateInscriptionListeAttente = LocalDate.now();
        }
        if (nouveauStatut == StatutBilanGreffe.GREFFE_REALISEE) {
            this.dateGreffe = LocalDate.now();
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public void mettreAJourBilanImmunologique(String groupeSanguinConfirme, String typageHla,
                                              BigDecimal praClasseI, BigDecimal praClasseII) {
        this.groupeSanguinConfirme = groupeSanguinConfirme;
        this.typageHla = typageHla;
        this.praClasseI = praClasseI;
        this.praClasseII = praClasseII;
        this.updatedAt = OffsetDateTime.now();
    }

    public void mettreAJourNotes(String contreIndications, String conclusionNephrologue) {
        this.contreIndications = contreIndications;
        this.conclusionNephrologue = conclusionNephrologue;
        this.updatedAt = OffsetDateTime.now();
    }

    public void ajouterDecisionRcp(DecisionRcp decision) {
        if (decision == null) {
            throw new BusinessException("DECISION_RCP_REQUISE", "La décision de RCP est obligatoire");
        }
        this.decisionsRcp.add(decision);
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public StatutBilanGreffe getStatut() {
        return statut;
    }

    public LocalDate getDateDebutBilan() {
        return dateDebutBilan;
    }

    public LocalDate getDateInscriptionListeAttente() {
        return dateInscriptionListeAttente;
    }

    public LocalDate getDateGreffe() {
        return dateGreffe;
    }

    public String getGroupeSanguinConfirme() {
        return groupeSanguinConfirme;
    }

    public String getTypageHla() {
        return typageHla;
    }

    public BigDecimal getPraClasseI() {
        return praClasseI;
    }

    public BigDecimal getPraClasseII() {
        return praClasseII;
    }

    public String getContreIndications() {
        return contreIndications;
    }

    public String getConclusionNephrologue() {
        return conclusionNephrologue;
    }

    public List<DecisionRcp> getDecisionsRcp() {
        return List.copyOf(decisionsRcp);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
