package com.hemodialyse.backend.domain.medical.greffe.aggregate;

import com.hemodialyse.backend.domain.medical.greffe.valueobject.CategorieEtapeGreffe;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutEtapeGreffe;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aggregate Root — une étape (examen, consultation, sérologie...) de la checklist du bilan
 * pré-greffe d'un patient (AGENTS.md §14). Plusieurs étapes par patient.
 * <p>
 * Référence de façon non contraignante ({@code demandeExamenId}/{@code serologieId}, simples
 * UUID) un enregistrement existant d'un autre agrégat (bc-medical.examen / bc-medical.serologie)
 * plutôt que de dupliquer le résultat — cohérent avec {@code PrescriptionMedicale.epoArticleId}
 * référençant bc-article sans FK applicative.
 * <p>
 * Invariant : une étape marquée {@code FAIT} doit porter une date de réalisation.
 */
public final class EtapeBilanPreGreffe {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final CategorieEtapeGreffe categorie;
    private final String libelle;
    private final OffsetDateTime createdAt;
    private StatutEtapeGreffe statut;
    private LocalDate dateRealisation;
    private String resultat;
    private LocalDate dateExpiration;
    private UUID demandeExamenId;
    private UUID serologieId;
    private OffsetDateTime updatedAt;

    private EtapeBilanPreGreffe(UUID id, UUID patientId, UUID centerId, CategorieEtapeGreffe categorie,
                                String libelle, StatutEtapeGreffe statut, LocalDate dateRealisation, String resultat,
                                LocalDate dateExpiration, UUID demandeExamenId, UUID serologieId,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.categorie = categorie;
        this.libelle = libelle;
        this.statut = statut;
        this.dateRealisation = dateRealisation;
        this.resultat = resultat;
        this.dateExpiration = dateExpiration;
        this.demandeExamenId = demandeExamenId;
        this.serologieId = serologieId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static EtapeBilanPreGreffe creer(UUID patientId, UUID centerId, CategorieEtapeGreffe categorie,
                                            String libelle) {
        if (patientId == null || centerId == null || categorie == null || libelle == null || libelle.isBlank()) {
            throw new BusinessException("ETAPE_GREFFE_CHAMPS_REQUIS",
                    "Patient, centre, catégorie et libellé sont obligatoires");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return new EtapeBilanPreGreffe(UUID.randomUUID(), patientId, centerId, categorie, libelle,
                StatutEtapeGreffe.A_FAIRE, null, null, null, null, null, now, now);
    }

    public static EtapeBilanPreGreffe reconstituer(UUID id, UUID patientId, UUID centerId,
                                                   CategorieEtapeGreffe categorie, String libelle,
                                                   StatutEtapeGreffe statut, LocalDate dateRealisation,
                                                   String resultat, LocalDate dateExpiration, UUID demandeExamenId,
                                                   UUID serologieId, OffsetDateTime createdAt,
                                                   OffsetDateTime updatedAt) {
        return new EtapeBilanPreGreffe(id, patientId, centerId, categorie, libelle, statut, dateRealisation,
                resultat, dateExpiration, demandeExamenId, serologieId, createdAt, updatedAt);
    }

    public void mettreAJour(StatutEtapeGreffe statut, LocalDate dateRealisation, String resultat,
                            LocalDate dateExpiration, UUID demandeExamenId, UUID serologieId) {
        if (statut == null) {
            throw new BusinessException("ETAPE_GREFFE_STATUT_REQUIS", "Le statut est obligatoire");
        }
        if (statut == StatutEtapeGreffe.FAIT && dateRealisation == null) {
            throw new BusinessException("ETAPE_GREFFE_DATE_REALISATION_REQUISE",
                    "Une étape marquée comme faite doit porter une date de réalisation");
        }
        this.statut = statut;
        this.dateRealisation = dateRealisation;
        this.resultat = resultat;
        this.dateExpiration = dateExpiration;
        this.demandeExamenId = demandeExamenId;
        this.serologieId = serologieId;
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

    public CategorieEtapeGreffe getCategorie() {
        return categorie;
    }

    public String getLibelle() {
        return libelle;
    }

    public StatutEtapeGreffe getStatut() {
        return statut;
    }

    public LocalDate getDateRealisation() {
        return dateRealisation;
    }

    public String getResultat() {
        return resultat;
    }

    public LocalDate getDateExpiration() {
        return dateExpiration;
    }

    public UUID getDemandeExamenId() {
        return demandeExamenId;
    }

    public UUID getSerologieId() {
        return serologieId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
