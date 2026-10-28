package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.DeplacementTemporaireId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Déplacement temporaire (racine d'agrégat, entité unique, P25, RG-PLN-096) : à une date, un patient dialyse dans une autre case
 * parce que son générateur habituel est indisponible. Il ne touche jamais la place habituelle du patient. Un seul par patient et par
 * date : le dépôt remplace l'existant du même jour. Créé uniquement par l'application d'une proposition {@code MAINTENANCE}.
 * <p>
 * Une cible incohérente (salle ou créneau inconnus du centre, générateur absent des générateurs en service de la salle) est
 * refusée à l'écriture, {@code DEPLACEMENT_TEMPORAIRE_CIBLE_INCOHERENTE} (décision du 28/10, KB-PLN-105) ; les lignes déjà
 * enregistrées sont relues telles quelles ({@link #reconstituer}).
 */
public final class DeplacementTemporaire {

    private final DeplacementTemporaireId id;
    private final CentreId centre;
    private final PatientId patient;
    private final LocalDate date;
    private final SalleId salle;
    private final CreneauId creneau;
    private final GenerateurId generateur;
    private final String motif;

    private DeplacementTemporaire(DeplacementTemporaireId id, CentreId centre, PatientId patient, LocalDate date, SalleId salle,
                                  CreneauId creneau, GenerateurId generateur, String motif) {
        if (id == null || centre == null || patient == null || date == null) {
            throw new SaisieInvalide("Déplacement temporaire incomplet");
        }
        if (salle == null || creneau == null || generateur == null) {
            throw new SaisieInvalide("Place de remplacement requise");
        }
        this.id = id;
        this.centre = centre;
        this.patient = patient;
        this.date = date;
        this.salle = salle;
        this.creneau = creneau;
        this.generateur = generateur;
        this.motif = motif;
    }

    /** @throws RegleDePlanificationViolee {@code DEPLACEMENT_TEMPORAIRE_CIBLE_INCOHERENTE} */
    public static DeplacementTemporaire creer(DeplacementTemporaireId id, CentreId centre, PatientId patient, LocalDate date,
                                              SalleId salle, CreneauId creneau, GenerateurId generateur, String motif,
                                              RessourcesDuCentre ressources) {
        DeplacementTemporaire deplacement = new DeplacementTemporaire(id, centre, patient, date, salle, creneau, generateur, motif);
        boolean cibleCoherente = ressources.contientSalle(salle) && ressources.contientCreneau(creneau)
                && ressources.generateurs().stream().anyMatch(g -> g.id().equals(generateur) && g.salle().equals(salle));
        if (!cibleCoherente) {
            throw new RegleDePlanificationViolee("DEPLACEMENT_TEMPORAIRE_CIBLE_INCOHERENTE",
                    "La place de remplacement n'existe pas dans ce centre (salle, créneau ou générateur en service de cette salle)");
        }
        return deplacement;
    }

    /** Une ligne relue de la persistance (aucune règle rejouée). */
    public static DeplacementTemporaire reconstituer(DeplacementTemporaireId id, CentreId centre, PatientId patient, LocalDate date,
                                                     SalleId salle, CreneauId creneau, GenerateurId generateur, String motif) {
        return new DeplacementTemporaire(id, centre, patient, date, salle, creneau, generateur, motif);
    }

    public DeplacementTemporaireId id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public PatientId patient() {
        return patient;
    }

    public LocalDate date() {
        return date;
    }

    public SalleId salle() {
        return salle;
    }

    public CreneauId creneau() {
        return creneau;
    }

    public GenerateurId generateur() {
        return generateur;
    }

    public Optional<String> motif() {
        return Optional.ofNullable(motif);
    }
}
