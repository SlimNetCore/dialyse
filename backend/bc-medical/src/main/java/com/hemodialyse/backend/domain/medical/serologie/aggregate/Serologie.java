package com.hemodialyse.backend.domain.medical.serologie.aggregate;

import com.hemodialyse.backend.domain.medical.serologie.event.SerologieEvent;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.MarqueurSerologique;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.ResultatSerologique;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — résultat sérologique du patient (AGENTS.md §14).
 * <p>
 * Invariant : un résultat {@link ResultatSerologique#POSITIF} impose une conduite à tenir —
 * un résultat positif sans action documentée est une donnée de sécurité incomplète, en
 * particulier pour l'isolement machine en centre d'hémodialyse.
 */
public final class Serologie {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final MarqueurSerologique marqueur;
    private final BigDecimal titre;
    private final String unite;
    private final LocalDate datePrelevement;
    private final String laboratoire;
    private final LocalDate dateProchainControle;
    private final OffsetDateTime createdAt;
    private final List<SerologieEvent> events = new ArrayList<>();
    private ResultatSerologique resultat;
    private String conduiteATenir;
    private OffsetDateTime updatedAt;

    private Serologie(UUID id, UUID patientId, UUID centerId, MarqueurSerologique marqueur,
                      ResultatSerologique resultat, BigDecimal titre, String unite, LocalDate datePrelevement,
                      String laboratoire, LocalDate dateProchainControle, String conduiteATenir,
                      OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.marqueur = marqueur;
        this.resultat = resultat;
        this.titre = titre;
        this.unite = unite;
        this.datePrelevement = datePrelevement;
        this.laboratoire = laboratoire;
        this.dateProchainControle = dateProchainControle;
        this.conduiteATenir = conduiteATenir;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Serologie enregistrer(UUID patientId, UUID centerId, MarqueurSerologique marqueur,
                                        ResultatSerologique resultat, BigDecimal titre, String unite,
                                        LocalDate datePrelevement, String laboratoire,
                                        LocalDate dateProchainControle, String conduiteATenir) {
        if (patientId == null || centerId == null || marqueur == null || resultat == null || datePrelevement == null) {
            throw new BusinessException("SEROLOGIE_CHAMPS_REQUIS",
                    "Patient, centre, marqueur, résultat et date de prélèvement sont obligatoires");
        }
        if (datePrelevement.isAfter(LocalDate.now())) {
            throw new BusinessException("SEROLOGIE_DATE_FUTURE", "La date de prélèvement ne peut pas être dans le futur");
        }
        validerConduiteATenir(resultat, conduiteATenir);
        OffsetDateTime now = OffsetDateTime.now();
        Serologie serologie = new Serologie(UUID.randomUUID(), patientId, centerId, marqueur, resultat, titre,
                unite, datePrelevement, laboratoire, dateProchainControle, conduiteATenir, now, now);
        if (resultat == ResultatSerologique.POSITIF) {
            serologie.events.add(new SerologieEvent.SerologiePositiveDetectee(serologie.id, patientId, now));
        }
        return serologie;
    }

    public static Serologie reconstituer(UUID id, UUID patientId, UUID centerId, MarqueurSerologique marqueur,
                                         ResultatSerologique resultat, BigDecimal titre, String unite,
                                         LocalDate datePrelevement, String laboratoire,
                                         LocalDate dateProchainControle, String conduiteATenir,
                                         OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new Serologie(id, patientId, centerId, marqueur, resultat, titre, unite, datePrelevement,
                laboratoire, dateProchainControle, conduiteATenir, createdAt, updatedAt);
    }

    private static void validerConduiteATenir(ResultatSerologique resultat, String conduiteATenir) {
        if (resultat == ResultatSerologique.POSITIF && (conduiteATenir == null || conduiteATenir.isBlank())) {
            throw new BusinessException("SEROLOGIE_CONDUITE_REQUISE",
                    "Un résultat positif impose de documenter la conduite à tenir");
        }
    }

    public void corrigerResultat(ResultatSerologique resultat, String conduiteATenir) {
        validerConduiteATenir(resultat, conduiteATenir);
        boolean devientPositive = resultat == ResultatSerologique.POSITIF && this.resultat != ResultatSerologique.POSITIF;
        this.resultat = resultat;
        this.conduiteATenir = conduiteATenir;
        this.updatedAt = OffsetDateTime.now();
        if (devientPositive) {
            this.events.add(new SerologieEvent.SerologiePositiveDetectee(id, patientId, updatedAt));
        }
    }

    public List<SerologieEvent> pullEvents() {
        List<SerologieEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
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

    public MarqueurSerologique getMarqueur() {
        return marqueur;
    }

    public ResultatSerologique getResultat() {
        return resultat;
    }

    public BigDecimal getTitre() {
        return titre;
    }

    public String getUnite() {
        return unite;
    }

    public LocalDate getDatePrelevement() {
        return datePrelevement;
    }

    public String getLaboratoire() {
        return laboratoire;
    }

    public LocalDate getDateProchainControle() {
        return dateProchainControle;
    }

    public String getConduiteATenir() {
        return conduiteATenir;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
