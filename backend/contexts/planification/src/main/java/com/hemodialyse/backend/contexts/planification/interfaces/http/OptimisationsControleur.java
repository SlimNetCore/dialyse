package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.planification.application.AppliquerUneProposition;
import com.hemodialyse.backend.contexts.planification.application.ArreterUneOptimisation;
import com.hemodialyse.backend.contexts.planification.application.ConsulterLesOptimisations;
import com.hemodialyse.backend.contexts.planification.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.planification.application.ImprimerLeCalendrierDeLaProposition;
import com.hemodialyse.backend.contexts.planification.application.LancerUneOptimisation;
import com.hemodialyse.backend.contexts.planification.application.OptimisationLue;
import com.hemodialyse.backend.contexts.planification.application.OptimisationLue.ExecutionLue;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.application.SupprimerUneExecution;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.CaseDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.ObjectifDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition.Document;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Adapter entrant HTTP de l'optimisation du planning (slice P8) : lancement asynchrone, suivi, arrêt anticipé, historique,
 * calendrier figé, impression, suppression et application de la proposition. Mêmes URL et JSON que le legacy. Lecture, lancement et
 * arrêt pour l'administration et le secrétariat ; application et suppression pour l'administrateur seul. Bornée au centre de la session.
 * <p>
 * Le corps du lancement est lu APRÈS le contrôle des droits (403 avant 400, décision du 28/10, KB-PLN-93). Codes : 404
 * introuvable, 409 déjà en cours / déjà appliquée / périmée, 422 autres règles, 400 entrée illisible, pagination plafonnée à 100.
 */
@RestController
@RequestMapping("/api/v1/planning/optimisations")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
@ConditionalOnProperty(name = "app.planification.optimisation.moteur", havingValue = "nouveau")
public class OptimisationsControleur {

    private final SessionCourante session;
    private final ObjectMapper lecteur;
    private final HorlogeDesCentres horloge;
    private final LancerUneOptimisation lancer;
    private final ConsulterLesOptimisations consulter;
    private final ArreterUneOptimisation arreter;
    private final AppliquerUneProposition appliquer;
    private final SupprimerUneExecution supprimer;
    private final ImprimerLeCalendrierDeLaProposition imprimer;

    @SuppressWarnings("java:S107")
    public OptimisationsControleur(SessionCourante session, ObjectMapper lecteur, HorlogeDesCentres horloge, LancerUneOptimisation lancer,
                                   ConsulterLesOptimisations consulter, ArreterUneOptimisation arreter,
                                   AppliquerUneProposition appliquer, SupprimerUneExecution supprimer,
                                   ImprimerLeCalendrierDeLaProposition imprimer) {
        this.session = session;
        this.lecteur = lecteur;
        this.horloge = horloge;
        this.lancer = lancer;
        this.consulter = consulter;
        this.arreter = arreter;
        this.appliquer = appliquer;
        this.supprimer = supprimer;
        this.imprimer = imprimer;
    }

    /**
     * @param semaine un jour de la semaine voulue ; défaut : la première semaine du calendrier
     */
    @GetMapping("/{id}/calendrier")
    public ResponseEntity<CalendrierReponse> calendrier(@RequestParam(required = false) UUID centerId, @PathVariable UUID id,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semaine) {
        CentreId centre = session.centre(centerId);
        List<LocalDate> semaines = consulter.semaines(centre, new ExecutionId(id));
        LocalDate choisie = semaine != null ? semaine : (semaines.isEmpty() ? null : semaines.getFirst());
        List<CaseDuCalendrier> cases = choisie == null ? List.of() : consulter.calendrier(centre, new ExecutionId(id), choisie);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CalendrierReponse(semaines, choisie, cases));
    }

    @GetMapping("/{id}/impression")
    public ResponseEntity<byte[]> imprimer(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        CentreId centre = session.centre(centerId);
        Document doc = imprimer.executer(centre, new ExecutionId(id));
        String base = "planning-propose-" + id;
        return switch (doc.format().toUpperCase(Locale.ROOT)) {
            case "EXCEL", "XLS", "XLSX" -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(base + ".xlsx", StandardCharsets.UTF_8).build().toString())
                    .body(doc.contenu());
            case "HTML" -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(doc.contenu());
            default -> ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                            .filename(base + ".pdf", StandardCharsets.UTF_8).build().toString())
                    .body(doc.contenu());
        };
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimer(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        supprimer.executer(session.centre(centerId), new ExecutionId(id));
        return ResponseEntity.noContent().build();
    }

    /**
     * Lance un calcul : réponse immédiate {@code 202} avec l'exécution {@code EN_COURS}, à suivre par {@link #consulter}.
     */
    @PostMapping
    public ResponseEntity<ExecutionLue> lancer(@RequestParam(required = false) UUID centerId,
                                               @RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centerId);
        LancerDemande demande = CorpsJson.lire(lecteur, requete, corps, LancerDemande.class);
        ParametresDOptimisation parametres = demande.versParametres(horloge.aujourdhui(centre));
        String utilisateur = session.utilisateur().valeur().toString();
        ExecutionLue lancee = OptimisationLue.depuis(lancer.executer(centre, parametres, utilisateur));
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore()).body(lancee);
    }

    /**
     * Historique paginé, des plus récentes aux plus anciennes (sans le détail des propositions).
     */
    @GetMapping
    public ResponseEntity<PageReponse> historique(@RequestParam(required = false) UUID centerId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        PageDeResultats<com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation> lues =
                consulter.historique(centre, new Pagination(page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PageReponse(
                lues.elements().stream().map(OptimisationLue::depuis).toList(), lues.total(), lues.page(), lues.taille()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExecutionLue> consulter(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(OptimisationLue.depuis(consulter.execution(centre, new ExecutionId(id))));
    }

    @PostMapping("/{id}/arret")
    public ResponseEntity<ExecutionLue> arreter(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(OptimisationLue.depuis(arreter.executer(centre, new ExecutionId(id))));
    }

    @PostMapping("/{id}/application")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExecutionLue> appliquer(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(OptimisationLue.depuis(appliquer.executer(centre, new ExecutionId(id))));
    }

    /**
     * @param semaines semaines (dates de début) disponibles ; vide si la proposition n'a pas de calendrier
     * @param semaine  semaine des {@code cases}
     */
    public record CalendrierReponse(List<LocalDate> semaines, LocalDate semaine, List<CaseDuCalendrier> cases) {
    }

    public record PageReponse(List<ExecutionLue> items, long total, int page, int size) {
    }

    /**
     * @param debutSemaine           un jour de la première semaine (défaut : semaine en cours au jour du centre)
     * @param nbSemaines             horizon en semaines (défaut 1)
     * @param dureeMaxSecondes       durée maximale de calcul par phase (défaut 20)
     * @param stabilite              0 à 10 (défaut 5)
     * @param objectif               {@code EQUITE} (défaut) ou {@code ECONOMIE}
     * @param maxVacationsParJour    1 à 3 (défaut 2)
     * @param maxVacationsParSemaine 1 à 14 (défaut 6)
     */
    public record LancerDemande(Perimetre perimetre, LocalDate debutSemaine, Integer nbSemaines,
                                Integer dureeMaxSecondes,
                                Integer stabilite, ObjectifDesInfirmiers objectif, Integer maxVacationsParJour,
                                Integer maxVacationsParSemaine) {

        ParametresDOptimisation versParametres(LocalDate aujourdhui) {
            ParametresDOptimisation defauts = ParametresDOptimisation.parDefaut(
                    perimetre == null ? Perimetre.PATIENTS : perimetre, debutSemaine != null ? debutSemaine : aujourdhui);
            if (perimetre == null) {
                throw new com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide("Périmètre requis");
            }
            return new ParametresDOptimisation(perimetre, defauts.debutSemaine(),
                    nbSemaines != null ? nbSemaines : defauts.nbSemaines(),
                    dureeMaxSecondes != null ? dureeMaxSecondes : defauts.dureeMaxSecondes(),
                    stabilite != null ? stabilite : defauts.stabilite(),
                    objectif != null ? objectif : defauts.objectif(),
                    maxVacationsParJour != null ? maxVacationsParJour : defauts.maxVacationsParJour(),
                    maxVacationsParSemaine != null ? maxVacationsParSemaine : defauts.maxVacationsParSemaine());
        }
    }
}
