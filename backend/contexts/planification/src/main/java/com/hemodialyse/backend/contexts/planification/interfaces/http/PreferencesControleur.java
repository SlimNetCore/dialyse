package com.hemodialyse.backend.contexts.planification.interfaces.http;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ConsulterLesReglages;
import com.hemodialyse.backend.contexts.planification.application.DefinirLaPreferenceDuPatient;
import com.hemodialyse.backend.contexts.planification.application.DefinirLeProfilDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.application.LectureDesPreferences.LigneDInfirmier;
import com.hemodialyse.backend.contexts.planification.application.LectureDesPreferences.LigneDePatient;
import com.hemodialyse.backend.contexts.planification.application.ListerLesPreferencesDesPatients;
import com.hemodialyse.backend.contexts.planification.application.ListerLesProfilsDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.ModifierLesReglages;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter entrant HTTP des préférences des patients, des profils des infirmiers et des réglages de planification (slice P1) :
 * mêmes URLs et même JSON que le legacy. Lecture et saisie des préférences et profils : administrateur et secrétariat ; écriture
 * des réglages : administrateur. Bornée au centre de la session, jamais mise en cache.
 * <p>
 * Écarts décidés (26/10/2026) : entrées illisibles, champ obligatoire absent et réglages hors bornes 400 au lieu de 500
 * (KB-PLN-01, 02), pagination négative 400 et taille plafonnée à 100 (KB-PLN-08), patient sorti et infirmier inactif refusés en
 * 422 (KB-PLN-07), écriture concurrente 409 (KB-PLN-10), compétences triées (KB-PLN-09).
 */
@RestController
@RequestMapping("/api/v1/planning/preferences")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
public class PreferencesControleur {

    private final SessionCourante session;
    private final ListerLesPreferencesDesPatients listerPatients;
    private final DefinirLaPreferenceDuPatient definirPreference;
    private final ListerLesProfilsDesInfirmiers listerInfirmiers;
    private final DefinirLeProfilDeLInfirmier definirProfil;
    private final ConsulterLesReglages consulterReglages;
    private final ModifierLesReglages modifierReglages;

    @SuppressWarnings("java:S107")
    public PreferencesControleur(SessionCourante session, ListerLesPreferencesDesPatients listerPatients,
                                 DefinirLaPreferenceDuPatient definirPreference, ListerLesProfilsDesInfirmiers listerInfirmiers,
                                 DefinirLeProfilDeLInfirmier definirProfil, ConsulterLesReglages consulterReglages,
                                 ModifierLesReglages modifierReglages) {
        this.session = session;
        this.listerPatients = listerPatients;
        this.definirPreference = definirPreference;
        this.listerInfirmiers = listerInfirmiers;
        this.definirProfil = definirProfil;
        this.consulterReglages = consulterReglages;
        this.modifierReglages = modifierReglages;
    }

    private static <T> T requis(T valeur, String champ) {
        if (valeur == null) throw new SaisieInvalide("Champ obligatoire absent : " + champ);
        return valeur;
    }

    @GetMapping("/patients")
    public ResponseEntity<PageReponse<LignePatientReponse>> patients(@RequestParam(required = false) UUID centerId,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        PageDeResultats<LigneDePatient> lues = listerPatients.executer(centre, new Pagination(page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(PageReponse.depuis(lues, LignePatientReponse::depuis));
    }

    @PutMapping("/patients/{patientId}")
    public ResponseEntity<PreferenceReponse> enregistrerPreference(@RequestParam(required = false) UUID centerId,
                                                                   @PathVariable UUID patientId,
                                                                   @RequestBody PreferenceDemande demande) {
        CentreId centre = session.centre(centerId);
        PreferenceDePlanification preference = new PreferenceDePlanification(new PatientId(patientId),
                demande.creneauPrefereId() == null ? null : new CreneauId(demande.creneauPrefereId()),
                demande.seancesParSemaine(), requis(demande.joursAChoisir(), "joursAChoisir"));
        return ResponseEntity.ok(PreferenceReponse.depuis(definirPreference.executer(centre, preference)));
    }

    @GetMapping("/infirmiers")
    public ResponseEntity<PageReponse<LigneInfirmierReponse>> infirmiers(@RequestParam(required = false) UUID centerId,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        PageDeResultats<LigneDInfirmier> lues = listerInfirmiers.executer(centre, new Pagination(page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(PageReponse.depuis(lues, LigneInfirmierReponse::depuis));
    }

    @PutMapping("/infirmiers/{infirmierId}")
    public ResponseEntity<ProfilReponse> enregistrerProfil(@RequestParam(required = false) UUID centerId,
                                                           @PathVariable UUID infirmierId,
                                                           @Valid @RequestBody ProfilDemande demande) {
        CentreId centre = session.centre(centerId);
        Set<Competence> competences = new HashSet<>();
        if (demande.competences() != null) {
            for (Competence competence : demande.competences()) {
                if (competence == null) throw new SaisieInvalide("Compétence illisible");
                competences.add(competence);
            }
        }
        ProfilDePlanification profil = new ProfilDePlanification(new InfirmierId(infirmierId), demande.tauxActivite(), competences);
        return ResponseEntity.ok(ProfilReponse.depuis(definirProfil.executer(centre, profil)));
    }

    @GetMapping("/reglages")
    public ResponseEntity<ReglagesReponse> reglages(@RequestParam(required = false) UUID centerId) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ReglagesReponse.depuis(consulterReglages.executer(centre)));
    }

    @PutMapping("/reglages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReglagesReponse> enregistrerReglages(@RequestParam(required = false) UUID centerId,
                                                               @RequestBody ReglagesDemande demande) {
        CentreId centre = session.centre(centerId);
        ReglagesDePlanification reglages = new ReglagesDePlanification(
                requis(demande.replanificationAuto(), "replanificationAuto"),
                requis(demande.heuresParVacation(), "heuresParVacation"),
                requis(demande.heuresHebdoTempsPlein(), "heuresHebdoTempsPlein"),
                requis(demande.reposHebdoMin(), "reposHebdoMin"));
        return ResponseEntity.ok(ReglagesReponse.depuis(modifierReglages.executer(centre, reglages)));
    }

    /** @param joursAChoisir obligatoire : les jours de dialyse sont choisis par l'optimisation ({@code seancesParSemaine} requis) */
    public record PreferenceDemande(UUID creneauPrefereId, Integer seancesParSemaine, Boolean joursAChoisir) {
    }

    public record ProfilDemande(@NotNull Integer tauxActivite, List<Competence> competences) {
    }

    /** Tous les champs sont obligatoires (un champ absent est refusé en 400, KB-PLN-02). */
    public record ReglagesDemande(Boolean replanificationAuto, Integer heuresParVacation, Integer heuresHebdoTempsPlein,
                                  Integer reposHebdoMin) {
    }

    public record PreferenceReponse(UUID patientId, UUID creneauPrefereId, Integer seancesParSemaine, boolean joursAChoisir) {
        static PreferenceReponse depuis(PreferenceDePlanification p) {
            return new PreferenceReponse(p.patient().valeur(), p.creneauPrefere() == null ? null : p.creneauPrefere().valeur(),
                    p.seancesParSemaine(), p.joursAChoisir());
        }
    }

    public record ProfilReponse(UUID infirmierId, int tauxActivite, List<Competence> competences) {
        static ProfilReponse depuis(ProfilDePlanification p) {
            return new ProfilReponse(p.infirmier().valeur(), p.tauxActivite(), p.competences().stream().sorted().toList());
        }
    }

    public record ReglagesReponse(boolean replanificationAuto, int heuresParVacation, int heuresHebdoTempsPlein, int reposHebdoMin) {
        static ReglagesReponse depuis(ReglagesDePlanification r) {
            return new ReglagesReponse(r.replanificationNocturne(), r.heuresParVacation(), r.heuresHebdoTempsPlein(), r.reposHebdoMin());
        }
    }

    public record LignePatientReponse(UUID patientId, String nom, List<JourDeLaSemaine> jours, UUID creneauActuelId,
                                      PreferenceReponse preference) {
        static LignePatientReponse depuis(LigneDePatient l) {
            return new LignePatientReponse(l.patient().valeur(), l.nom(), l.jours().stream().sorted().toList(),
                    l.creneauActuel() == null ? null : l.creneauActuel().valeur(), PreferenceReponse.depuis(l.preference()));
        }
    }

    public record LigneInfirmierReponse(UUID infirmierId, String nom, String qualification, ProfilReponse profil) {
        static LigneInfirmierReponse depuis(LigneDInfirmier l) {
            return new LigneInfirmierReponse(l.infirmier().valeur(), l.nom(), l.qualification(), ProfilReponse.depuis(l.profil()));
        }
    }

    /** Page de résultats : {@code size} est la taille effective (plafonnée), {@code total} le nombre sans pagination. */
    public record PageReponse<T>(List<T> items, int page, int size, long total) {
        static <S, T> PageReponse<T> depuis(PageDeResultats<S> page, java.util.function.Function<S, T> vers) {
            return new PageReponse<>(page.elements().stream().map(vers).toList(), page.page(), page.taille(), page.total());
        }
    }
}
