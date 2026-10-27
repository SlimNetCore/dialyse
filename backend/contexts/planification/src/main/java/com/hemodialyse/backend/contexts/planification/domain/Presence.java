package com.hemodialyse.backend.contexts.planification.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Modèle de LECTURE de la présence des infirmiers (slice P4) : pour chaque case (salle, créneau, jour), qui est prévu (roulement,
 * absences déduites, remplaçants ajoutés) face à l'effectif requis par le ratio de sécurité. Les noms de champs sont ceux du JSON
 * historique. Ce n'est pas un agrégat : rien ne s'y écrit, tout se calcule à partir de {@link DonneesDePresence}.
 */
public final class Presence {

    private Presence() {
    }

    public enum StatutDeCase {
        /** Le centre est fermé ce jour-là (jour non ouvert, férié ou fermeture exceptionnelle). */
        FERME,
        /** Aucun patient sur ce créneau : aucun infirmier requis. */
        SANS_PATIENT,
        COUVERT,
        SOUS_EFFECTIF
    }

    public enum TypeDeConflit {
        /** Un infirmier est prévu dans deux salles sur le même créneau le même jour. */
        DOUBLE_AFFECTATION,
        /** Un infirmier non habilité est prévu en salle d'isolement. */
        NON_HABILITE
    }

    public enum RaisonDeRemplacement {
        SALLE_CONNUE,
        CRENEAU_HABITUEL,
        CHARGE_FAIBLE,
        JOUR_LIBRE,
        HABILITE_ISOLEMENT,
        DOUBLE_VACATION
    }

    public record Salle(UUID id, String nom) {
    }

    public record Creneau(UUID id, String libelle, int ordre) {
    }

    /** Place occupée par un patient : premier et dernier jour d'occupation facultatifs (nul = sans limite). */
    public record Occupation(UUID salle, UUID creneau, Set<JourDeLaSemaine> jours, LocalDate premierJour, LocalDate dernierJour) {
        public boolean occupeLe(LocalDate date) {
            return (premierJour == null || !date.isBefore(premierJour)) && (dernierJour == null || !date.isAfter(dernierJour));
        }
    }

    public record Fermeture(LocalDate date, String motif) {
    }

    /** Salles, créneaux, places occupées et calendrier du centre (lus chez Organisation et le planning des places). */
    public record PlanningDesPlaces(List<Salle> salles, List<Creneau> creneaux, List<Occupation> occupations,
                                    Set<JourDeLaSemaine> joursOuverts, Set<UUID> sallesIsolement, List<Fermeture> fermetures) {
    }

    public record InfirmierDeLaPresence(UUID id, String nom, String prenom, Qualification qualification, boolean habiliteIsolement) {
        public String nomAffiche() {
            return prenom == null || prenom.isBlank() ? nom : prenom + " " + nom;
        }
    }

    public record AffectationPrevue(UUID infirmier, UUID salle, UUID creneau, Set<JourDeLaSemaine> jours) {
    }

    public record AbsenceConnue(UUID infirmier, LocalDate debut, LocalDate fin, TypeAbsence type) {
        public boolean couvre(LocalDate date) {
            return !date.isBefore(debut) && !date.isAfter(fin);
        }
    }

    public record RemplacementPose(UUID id, LocalDate date, UUID salle, UUID creneau, UUID infirmier) {
    }

    /** Le personnel du centre sur une période : infirmiers actifs, roulement, absences, remplacements et ratio retenu. */
    public record Personnel(List<InfirmierDeLaPresence> infirmiers, List<AffectationPrevue> affectations,
                            List<AbsenceConnue> absences, List<RemplacementPose> remplacements, RatioDeLaPresence ratio) {
    }

    public record DonneesDePresence(PlanningDesPlaces planning, Personnel personnel) {
        public int patientsParInfirmier() {
            return personnel.ratio().patientsParInfirmier();
        }
    }

    public record Present(UUID infirmierId, String nom, boolean habiliteIsolement, boolean remplacant, UUID remplacementId) {
    }

    public record Absent(UUID infirmierId, String nom, TypeAbsence type) {
    }

    public record CasePresence(UUID salleId, UUID creneauId, JourDeLaSemaine jour, LocalDate date, int patients, int requis,
                               boolean salleIsolement, StatutDeCase statut, int manque, int surplus, List<Present> presents,
                               List<Absent> absents) {
    }

    public record ConflitPresence(TypeDeConflit type, LocalDate date, JourDeLaSemaine jour, UUID salleId, UUID creneauId,
                                  String infirmier) {
    }

    public record JourDuPlanning(JourDeLaSemaine jour, LocalDate date, boolean ouvertHebdomadaire, String fermetureMotif) {
    }

    public record SemainePresence(LocalDate debut, LocalDate fin, List<JourDuPlanning> jours, List<Salle> salles,
                                  List<Creneau> creneaux, List<CasePresence> cases, List<ConflitPresence> conflits,
                                  int patientsParInfirmier, int casesSousEffectif) {
    }

    public record AlertePresence(LocalDate date, JourDeLaSemaine jour, UUID salleId, UUID creneauId, int patients, int requis,
                                 int manque, List<String> absents) {
    }

    /** Infirmiers prévus au-delà de l'effectif requis sur une case (salle sans patient comprise). */
    public record AlerteSureffectif(LocalDate date, JourDeLaSemaine jour, UUID salleId, UUID creneauId, int patients, int requis,
                                    int surplus) {
    }

    /** Un infirmier prévu dans plusieurs salles au même créneau le même jour (KB-PLN-50, signalé comme sur-effectif). */
    public record AlerteDoubleAffectation(LocalDate date, JourDeLaSemaine jour, UUID creneauId, UUID infirmierId, String infirmier,
                                          List<UUID> salles) {
    }

    public record Candidat(UUID infirmierId, String nom, Qualification qualification, boolean habiliteIsolement, int score,
                           List<RaisonDeRemplacement> raisons, int seancesSemaine) {
    }

    public record ChargeInfirmier(UUID infirmierId, String nom, int seances, int remplacements, int joursAbsence, int total) {
    }

    public record ChargeMensuelle(List<ChargeInfirmier> infirmiers, double moyenne) {
    }
}
