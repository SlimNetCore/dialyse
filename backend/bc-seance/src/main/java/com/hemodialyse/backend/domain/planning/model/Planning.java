package com.hemodialyse.backend.domain.planning.model;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Modèle du planning d'affectation d'un centre : salles, créneaux (positions horaires), générateurs en état de
 * fonctionner, patients déjà placés, jours d'ouverture, salles d'isolement et prochaines fermetures. Un patient
 * occupe, chaque jour de dialyse, un créneau sur un générateur de sa salle : un générateur ne peut servir qu'un
 * patient par jour et par créneau.
 */
public final class Planning {

    private Planning() {
    }

    /**
     * Motifs d'une proposition (clés traduites côté interface).
     */
    public enum Raison {
        JOURS_IMPOSES_LIBRES,
        JOURS_BIEN_ESPACES,
        CRENEAU_PREFERE,
        SALLE_PREFEREE,
        SALLE_PEU_CHARGEE,
        SALLE_ISOLEMENT
    }

    /**
     * Raisons pour lesquelles aucune proposition ne peut être faite (clés traduites côté interface).
     */
    public enum Alerte {
        AUCUNE_SALLE_ISOLEMENT,
        JOURS_IMPOSES_FERMES
    }

    public record SalleRef(UUID id, String nom) {
    }

    /**
     * @param ordre rang d'affichage (code du créneau) : du plus tôt au plus tard dans la journée
     */
    public record CreneauRef(UUID id, String libelle, int ordre) {
    }

    /**
     * Générateur disponible (en service) installé dans une salle.
     */
    public record GenerateurRef(UUID id, String code, UUID salleId) {
    }

    /**
     * Placement existant d'un patient actif ; {@code generateurId} peut être inconnu (le patient occupe alors
     * une place de la salle sans générateur précis). {@code aRisque} : sérologie positive (VHB, VHC, VIH) imposant
     * l'isolement.
     */
    public record Occupation(UUID patientId, UUID salleId, UUID creneauId, UUID generateurId, Set<JourSemaine> jours,
                             boolean aRisque) {
        public Occupation(UUID patientId, UUID salleId, UUID creneauId, UUID generateurId, Set<JourSemaine> jours) {
            this(patientId, salleId, creneauId, generateurId, jours, false);
        }
    }

    /**
     * Jour de fermeture daté du centre (férié ou fermeture exceptionnelle).
     */
    public record Fermeture(LocalDate date, String motif) {
    }

    /**
     * Données du planning.
     *
     * @param joursOuverts    jours de la semaine où le centre dialyse (jamais proposés sinon)
     * @param sallesIsolement salles réservées aux patients à risque infectieux
     * @param fermetures      fermetures datées à venir (information sur les jours proposés)
     */
    public record DonneesPlanning(
            List<SalleRef> salles,
            List<CreneauRef> creneaux,
            List<GenerateurRef> generateurs,
            List<Occupation> occupations,
            Set<JourSemaine> joursOuverts,
            Set<UUID> sallesIsolement,
            List<Fermeture> fermetures
    ) {
        public DonneesPlanning(List<SalleRef> salles, List<CreneauRef> creneaux, List<GenerateurRef> generateurs,
                               List<Occupation> occupations) {
            this(salles, creneaux, generateurs, occupations, EnumSet.allOf(JourSemaine.class), Set.of(), List.of());
        }
    }

    /**
     * Besoin d'un patient à placer.
     *
     * @param seancesParSemaine nombre de séances par semaine (1 à 7) ; ignoré si des jours sont imposés
     * @param joursImposes      jours obligatoires (vide : le système choisit les jours les mieux espacés)
     * @param creneauPrefere    créneau souhaité (facultatif)
     * @param sallePreferee     salle souhaitée (facultatif)
     * @param patientARisque    patient à isoler : placé uniquement en salle d'isolement et sur un générateur dédié
     */
    public record DemandePlacement(
            int seancesParSemaine,
            Set<JourSemaine> joursImposes,
            UUID creneauPrefere,
            UUID sallePreferee,
            boolean patientARisque
    ) {
        public DemandePlacement(int seancesParSemaine, Set<JourSemaine> joursImposes, UUID creneauPrefere,
                                UUID sallePreferee) {
            this(seancesParSemaine, joursImposes, creneauPrefere, sallePreferee, false);
        }
    }

    /**
     * Prochaine fermeture datée tombant un jour de dialyse proposé.
     */
    public record FermetureJour(JourSemaine jour, LocalDate date, String motif) {
    }

    /**
     * Placement proposé : salle, créneau, générateur et jours. {@code generateursAlternatifs} : autres générateurs
     * libres de la salle pour exactement les mêmes jours et le même créneau.
     */
    public record Proposition(
            SalleRef salle,
            CreneauRef creneau,
            GenerateurRef generateur,
            List<GenerateurRef> generateursAlternatifs,
            List<JourSemaine> jours,
            int score,
            List<Raison> raisons,
            List<FermetureJour> fermetures
    ) {
    }

    public record ResultatProposition(List<Proposition> propositions, List<Alerte> alertes) {
    }

    /**
     * Capacité et occupation d'une salle pour un créneau et un jour (capacité nulle un jour de fermeture
     * hebdomadaire).
     */
    public record CaseGrille(UUID salleId, UUID creneauId, JourSemaine jour, int capacite, int occupes) {
        public int libres() {
            return Math.max(0, capacite - occupes);
        }
    }
}
