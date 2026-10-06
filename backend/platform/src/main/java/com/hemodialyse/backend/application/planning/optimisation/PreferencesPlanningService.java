package com.hemodialyse.backend.application.planning.optimisation;

import com.hemodialyse.backend.domain.planning.optimisation.model.PreferencePatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ProfilInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.ReglagesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.PreferencePatientPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.ProfilInfirmierPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.ReglagesOptimisationPort;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Données propres à l'optimisation, saisies par le centre : préférences des patients (créneau souhaité, jours à
 * choisir), profils des infirmiers (taux d'activité, compétences) et réglages (contraintes de personnel,
 * replanification automatique). Tout est borné au centre ; un créneau préféré doit appartenir au centre.
 */
@Service
public class PreferencesPlanningService {

    private final PreferencePatientPort preferences;
    private final ProfilInfirmierPort profils;
    private final ReglagesOptimisationPort reglages;
    private final PlanningDonneesPort planning;

    public PreferencesPlanningService(PreferencePatientPort preferences, ProfilInfirmierPort profils,
                                      ReglagesOptimisationPort reglages, PlanningDonneesPort planning) {
        this.preferences = preferences;
        this.profils = profils;
        this.reglages = reglages;
        this.planning = planning;
    }

    public PagedResult<PreferencePatientPort.Ligne> preferencesPatients(UUID centerId, int page, int size) {
        return preferences.lister(centerId, page, size);
    }

    /**
     * @throws BusinessException {@code PREFERENCE_CRENEAU_INCONNU} (créneau étranger au centre) ou
     *                           {@code PREFERENCE_PATIENT_INTROUVABLE}
     */
    public PreferencePatient enregistrerPreference(UUID centerId, PreferencePatient preference) {
        if (preference.creneauPrefereId() != null
                && !planning.creneauxDuCentre(centerId).contains(preference.creneauPrefereId())) {
            throw new BusinessException("PREFERENCE_CRENEAU_INCONNU", "Ce créneau n'appartient pas au centre");
        }
        if (!preferences.enregistrer(centerId, preference)) {
            throw new BusinessException("PREFERENCE_PATIENT_INTROUVABLE", "Patient introuvable");
        }
        return preference;
    }

    public PagedResult<ProfilInfirmierPort.Ligne> profilsInfirmiers(UUID centerId, int page, int size) {
        return profils.lister(centerId, page, size);
    }

    /**
     * @throws BusinessException {@code PROFIL_INFIRMIER_INTROUVABLE}
     */
    public ProfilInfirmier enregistrerProfil(UUID centerId, ProfilInfirmier profil) {
        if (!profils.enregistrer(centerId, profil)) {
            throw new BusinessException("PROFIL_INFIRMIER_INTROUVABLE", "Infirmier introuvable");
        }
        return profil;
    }

    public ReglagesOptimisation reglages(UUID centerId) {
        return reglages.lire(centerId);
    }

    public ReglagesOptimisation enregistrerReglages(UUID centerId, ReglagesOptimisation nouveaux) {
        reglages.enregistrer(centerId, nouveaux);
        return nouveaux;
    }
}
