package com.hemodialyse.backend.domain.planning.model;

import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Planning réel d'une semaine (du dimanche au samedi) : qui dialyse où (salle, créneau, générateur), jour par jour,
 * avec les fermetures et les conflits à corriger.
 */
public final class PlanningSemaine {

    private PlanningSemaine() {
    }

    public enum TypeConflit {
        GENERATEUR_DOUBLE,
        SALLE_SURCHARGEE,
        GENERATEUR_INDISPONIBLE,
        SANS_GENERATEUR,
        ISOLEMENT_NON_RESPECTE,
        GENERATEUR_MIXTE
    }

    /**
     * Données lues pour une semaine : planning du centre (placements de tous les patients actifs), noms des patients
     * et fermetures datées tombant dans la semaine.
     */
    public record DonneesSemaine(DonneesPlanning planning, Map<UUID, String> nomsPatients, List<Fermeture> fermetures) {
    }

    /**
     * @param ouvertHebdomadaire le centre dialyse ce jour de la semaine
     * @param fermetureMotif     motif d'une fermeture datée ce jour-là (férié, fermeture exceptionnelle), sinon null
     */
    public record JourPlanning(JourSemaine jour, LocalDate date, boolean ouvertHebdomadaire, String fermetureMotif) {
        public boolean ferme() {
            return !ouvertHebdomadaire || fermetureMotif != null;
        }
    }

    /**
     * @param libereLe premier jour où la place est libérée (transfert, décès, greffe ou guérison daté), sinon null
     */
    public record OccupantPlanning(UUID patientId, String nom, String generateurCode, boolean aRisque,
                                   LocalDate libereLe) {
        public OccupantPlanning(UUID patientId, String nom, String generateurCode, boolean aRisque) {
            this(patientId, nom, generateurCode, aRisque, null);
        }
    }

    public record CellulePlanning(UUID salleId, UUID creneauId, JourSemaine jour, int capacite,
                                  List<OccupantPlanning> occupants) {
    }

    /**
     * Anomalie du planning ; {@code salleId}, {@code creneauId} et {@code jour} sont nuls pour un conflit qui ne
     * dépend pas d'une case (générateur mixte).
     */
    public record Conflit(TypeConflit type, UUID salleId, UUID creneauId, JourSemaine jour, List<String> patients) {
    }

    public record SemainePlanning(
            LocalDate debut,
            LocalDate fin,
            List<JourPlanning> jours,
            List<SalleRef> salles,
            List<CreneauRef> creneaux,
            List<CellulePlanning> cellules,
            List<Conflit> conflits,
            int patientsAReplanifier
    ) {
    }
}
