package com.hemodialyse.backend.application.planning.optimisation;

import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.AffectationInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.infirmier.port.RemplacementInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.planning.model.DeplacementTemporaire;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementPatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationDonneesPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationRunRepositoryPort;
import com.hemodialyse.backend.domain.planning.optimisation.service.EmpreinteOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.service.VerificationDeplacementsService;
import com.hemodialyse.backend.domain.planning.port.DeplacementTemporairePort;
import com.hemodialyse.backend.domain.planning.port.PlacementPatientPort;
import com.hemodialyse.backend.domain.planning.service.PlanificationAffectationService.Violation;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Applique une proposition d'optimisation validée par l'administration : déplacement des patients (et nouveaux jours
 * de dialyse choisis), roulement des infirmiers, remplacements ou déplacements temporaires, selon le périmètre. Tout ou
 * rien (une transaction).
 * <p>
 * Garde-fous : la proposition n'est applicable que si le centre n'a pas changé depuis son calcul (empreinte des
 * données lues) et si chaque nouvelle place respecte les règles de la planification, revérifiées par le domaine.
 */
@Service
public class OptimisationApplicationService {

    private final OptimisationRunRepositoryPort runs;
    private final OptimisationDonneesPort donnees;
    private final PlacementPatientPort placements;
    private final AffectationInfirmierRepositoryPort affectations;
    private final RemplacementInfirmierRepositoryPort remplacements;
    private final DeplacementTemporairePort temporaires;
    private final Clock horloge;

    @Autowired
    public OptimisationApplicationService(OptimisationRunRepositoryPort runs, OptimisationDonneesPort donnees,
                                          PlacementPatientPort placements,
                                          AffectationInfirmierRepositoryPort affectations,
                                          RemplacementInfirmierRepositoryPort remplacements,
                                          DeplacementTemporairePort temporaires) {
        this(runs, donnees, placements, affectations, remplacements, temporaires, Clock.systemUTC());
    }

    OptimisationApplicationService(OptimisationRunRepositoryPort runs, OptimisationDonneesPort donnees,
                                   PlacementPatientPort placements, AffectationInfirmierRepositoryPort affectations,
                                   RemplacementInfirmierRepositoryPort remplacements,
                                   DeplacementTemporairePort temporaires, Clock horloge) {
        this.runs = runs;
        this.donnees = donnees;
        this.placements = placements;
        this.affectations = affectations;
        this.remplacements = remplacements;
        this.temporaires = temporaires;
        this.horloge = horloge;
    }

    /**
     * @throws BusinessException {@code OPTIMISATION_INTROUVABLE}, {@code OPTIMISATION_NON_APPLICABLE},
     *                           {@code OPTIMISATION_DEJA_APPLIQUEE}, {@code OPTIMISATION_PERIMEE} (le centre a changé
     *                           depuis le calcul) ou {@code OPTIMISATION_PLACEMENT_<règle>} (nouvelle place incohérente)
     */
    @Transactional
    @CacheEvict(cacheNames = {"patient.byId", "patient.byCenter", "patient.list.summary",
            "patient.list.summary.details"}, allEntries = true)
    public RunOptimisation appliquer(UUID centerId, UUID runId) {
        RunOptimisation run = runs.findById(centerId, runId).orElseThrow(OptimisationPlanningService::introuvable);
        RunOptimisation appliquee = run.appliquer(horloge.instant());
        ParametresOptimisation parametres = run.parametres();
        ResultatOptimisation resultat = run.resultat();

        DonneesOptimisation actuelles = donnees.charger(centerId, parametres.debutSemaine(), parametres.finHorizon());
        if (!EmpreinteOptimisation.calculer(actuelles, parametres).equals(run.empreinte())) {
            throw new BusinessException("OPTIMISATION_PERIMEE",
                    "Le centre a changé depuis le calcul de cette proposition : relancez l'optimisation");
        }

        if (parametres.perimetre().placePatients()) {
            appliquerDeplacements(centerId, actuelles, resultat.deplacements());
        }
        if (parametres.perimetre().planifieRoulement()) {
            appliquerRoulement(centerId, actuelles, resultat.vacations());
        } else if (parametres.perimetre() == PerimetreOptimisation.COUVERTURE) {
            appliquerCouverture(centerId, resultat.vacations());
        } else if (parametres.perimetre() == PerimetreOptimisation.MAINTENANCE) {
            appliquerTemporaires(centerId, resultat.temporaires());
        }
        return runs.save(appliquee);
    }

    private void appliquerDeplacements(UUID centerId, DonneesOptimisation actuelles, List<DeplacementPatient> deplacements) {
        Map<UUID, List<Violation>> refus = VerificationDeplacementsService.verifier(actuelles, deplacements);
        if (!refus.isEmpty()) {
            Violation premiere = refus.values().iterator().next().getFirst();
            throw new BusinessException("OPTIMISATION_PLACEMENT_" + premiere.name(),
                    "Une nouvelle place proposée enfreint les règles de la planification : " + refus.values());
        }
        for (DeplacementPatient d : deplacements) {
            if (d.jours() != null) placements.definirJours(centerId, d.patientId(), EnumSet.copyOf(d.jours()));
            placements.deplacer(centerId, d.patientId(), d.vers().salleId(), d.vers().creneauId(), d.vers().generateurId());
        }
    }

    /**
     * Enregistre les déplacements temporaires d'une séance datée ; la place habituelle du patient ne change pas.
     */
    private void appliquerTemporaires(UUID centerId, List<DeplacementTemporairePropose> proposes) {
        List<DeplacementTemporaire> nouveaux = new ArrayList<>();
        for (DeplacementTemporairePropose t : proposes) {
            nouveaux.add(DeplacementTemporaire.creer(centerId, t.patientId(), t.date(), t.vers().salleId(),
                    t.vers().creneauId(), t.vers().generateurId(), t.motif()));
        }
        if (!nouveaux.isEmpty()) temporaires.enregistrer(nouveaux);
    }

    /**
     * Remplace le roulement des infirmiers actifs par celui de la proposition : les affectations identiques sont gardées,
     * les autres modifiées, créées ou supprimées.
     */
    private void appliquerRoulement(UUID centerId, DonneesOptimisation actuelles, List<VacationPlanifiee> vacations) {
        Map<String, Set<JourSemaine>> souhaites = new LinkedHashMap<>();
        Map<String, VacationPlanifiee> exemples = new HashMap<>();
        for (VacationPlanifiee v : vacations) {
            String cle = v.infirmierId() + "|" + v.salleId() + "|" + v.creneauId();
            souhaites.computeIfAbsent(cle, k -> EnumSet.noneOf(JourSemaine.class)).add(v.jour());
            exemples.putIfAbsent(cle, v);
        }
        List<UUID> infirmiers = actuelles.presence().infirmiers().stream().map(InfirmierRef::id).toList();
        List<AffectationInfirmier> existantes = affectations.findByInfirmierIds(centerId, infirmiers);
        Set<String> traitees = new java.util.HashSet<>();
        for (AffectationInfirmier a : existantes) {
            String cle = a.infirmierId() + "|" + a.salleId() + "|" + a.creneauId();
            Set<JourSemaine> jours = souhaites.get(cle);
            if (jours == null || !traitees.add(cle)) {
                affectations.delete(centerId, a.id());
            } else if (!jours.equals(a.jours())) {
                affectations.save(a.modifier(a.salleId(), a.creneauId(), jours));
            }
        }
        for (Map.Entry<String, Set<JourSemaine>> souhait : souhaites.entrySet()) {
            if (traitees.contains(souhait.getKey())) continue;
            VacationPlanifiee v = exemples.get(souhait.getKey());
            affectations.save(AffectationInfirmier.creer(centerId, v.infirmierId(), v.salleId(), v.creneauId(),
                    souhait.getValue()));
        }
    }

    /**
     * Crée les remplacements des vacations que le roulement ne couvrait pas (celles déjà tenues sont ignorées).
     */
    private void appliquerCouverture(UUID centerId, List<VacationPlanifiee> vacations) {
        List<RemplacementInfirmier> nouveaux = new ArrayList<>();
        for (VacationPlanifiee v : vacations) {
            if (!v.existante()) {
                nouveaux.add(RemplacementInfirmier.creer(centerId, v.date(), v.salleId(), v.creneauId(), v.infirmierId(),
                        null));
            }
        }
        nouveaux.forEach(remplacements::save);
    }
}
