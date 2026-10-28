package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs.Compte;
import com.hemodialyse.backend.contexts.planification.application.CreerUnCompte;
import com.hemodialyse.backend.contexts.planification.application.CreerUnCompte.CompteCree;
import com.hemodialyse.backend.contexts.planification.application.DelierLeCompte;
import com.hemodialyse.backend.contexts.planification.application.DesactiverUnInfirmier;
import com.hemodialyse.backend.contexts.planification.application.EnregistrerUnInfirmier;
import com.hemodialyse.backend.contexts.planification.application.FicheDetaillee;
import com.hemodialyse.backend.contexts.planification.application.LierUnCompte;
import com.hemodialyse.backend.contexts.planification.application.ListerLesComptesLiables;
import com.hemodialyse.backend.contexts.planification.application.ListerLesInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.ModifierUnInfirmier;
import com.hemodialyse.backend.contexts.planification.application.ReactiverUnInfirmier;
import com.hemodialyse.backend.contexts.planification.application.RoulementsDesInfirmiers.AffectationLue;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.AdresseEmail;
import com.hemodialyse.backend.contexts.planification.domain.IdentiteDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Matricule;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Adapter entrant HTTP du référentiel des infirmiers et de leurs comptes (slice P2) : mêmes URLs, mêmes droits et même JSON que le
 * legacy, bornées au centre de la session, jamais mises en cache pour les lectures. Lecture : administrateur, secrétariat et la
 * combinaison médecin + infirmier (RG-INF-004 corrigée : le médecin seul et l'infirmier seul sont refusés par le filtre de rôles) ;
 * écriture et comptes liables : administrateur. Les trois routes d'affectations du même chemin restent dans le legacy (P3).
 * <p>
 * Écarts décidés (26/10/2026) : corps ou paramètres illisibles, tailles dépassées, e-mail invalide et pagination négative 400
 * {@code BAD_REQUEST} au lieu de 500 ou 200 ; type de contenu hors JSON 415 ; droits vérifiés avant la lecture du corps ;
 * {@code size} plafonnée à 100 (taille effective renvoyée) ; {@code habiliteIsolement} facultatif (faux par défaut) ; matricule en
 * majuscules ; écriture concurrente 409 ; états incohérents du compte 422 (KB-PLN-12, 13, 18, 19, 20, 22, 23).
 */
@RestController
@RequestMapping("/api/v1/infirmiers")
public class InfirmiersControleur {

    /** Attribut de requête lu par le journal d'audit : identifiant de la fiche écrite quand le chemin n'en porte pas. */
    static final String ATTRIBUT_AUDIT_IDENTIFIANT = "hemodialyse.audit.entityId";

    private static final String LECTURE = "hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')";
    private static final String ECRITURE = "hasRole('ADMIN')";

    private final SessionCourante session;
    private final ObjectMapper lecteur;
    private final ListerLesInfirmiers lister;
    private final EnregistrerUnInfirmier enregistrer;
    private final ModifierUnInfirmier modifier;
    private final DesactiverUnInfirmier desactiver;
    private final ReactiverUnInfirmier reactiver;
    private final ListerLesComptesLiables listerComptes;
    private final LierUnCompte lierCompte;
    private final CreerUnCompte creerCompte;
    private final DelierLeCompte delierCompte;

    @SuppressWarnings("java:S107")
    public InfirmiersControleur(SessionCourante session, ObjectMapper lecteur, ListerLesInfirmiers lister,
                                EnregistrerUnInfirmier enregistrer, ModifierUnInfirmier modifier, DesactiverUnInfirmier desactiver,
                                ReactiverUnInfirmier reactiver, ListerLesComptesLiables listerComptes, LierUnCompte lierCompte,
                                CreerUnCompte creerCompte, DelierLeCompte delierCompte) {
        this.session = session;
        this.lecteur = lecteur;
        this.lister = lister;
        this.enregistrer = enregistrer;
        this.modifier = modifier;
        this.desactiver = desactiver;
        this.reactiver = reactiver;
        this.listerComptes = listerComptes;
        this.lierCompte = lierCompte;
        this.creerCompte = creerCompte;
        this.delierCompte = delierCompte;
    }

    @GetMapping
    @PreAuthorize(LECTURE)
    public ResponseEntity<PageReponse<InfirmierReponse>> infirmiers(@RequestParam(required = false) UUID centerId,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        PageDeResultats<FicheDetaillee> lues = lister.executer(centre, new Pagination(page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageReponse.depuis(lues, InfirmierReponse::depuis));
    }

    @PostMapping
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierReponse> enregistrer(@RequestParam(required = false) UUID centerId,
                                                        @RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centerId);
        InfirmierDemande demande = CorpsJson.lire(lecteur, requete, corps, InfirmierDemande.class);
        FicheDetaillee fiche = enregistrer.executer(centre, Matricule.depuisLaSaisie(demande.matricule()), demande.identite(),
                Qualification.depuisLaSaisie(demande.qualification()), demande.habilite());
        requete.setAttribute(ATTRIBUT_AUDIT_IDENTIFIANT, fiche.id().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(InfirmierReponse.depuis(fiche));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierReponse> modifier(@RequestParam(required = false) UUID centerId, @PathVariable UUID id,
                                                     @RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centerId);
        InfirmierDemande demande = CorpsJson.lire(lecteur, requete, corps, InfirmierDemande.class);
        return ResponseEntity.ok(InfirmierReponse.depuis(modifier.executer(centre, new InfirmierId(id),
                Matricule.depuisLaSaisie(demande.matricule()), demande.identite(),
                Qualification.depuisLaSaisie(demande.qualification()), demande.habilite())));
    }

    @PostMapping("/{id}/desactiver")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierReponse> desactiver(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(InfirmierReponse.depuis(desactiver.executer(centre, new InfirmierId(id))));
    }

    @PostMapping("/{id}/reactiver")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierReponse> reactiver(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(InfirmierReponse.depuis(reactiver.executer(centre, new InfirmierId(id))));
    }

    /** Comptes INFIRMIER du centre qu'aucune fiche n'utilise encore (liste déroulante de la liaison). */
    @GetMapping("/comptes-liables")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<PageReponse<CompteReponse>> comptesLiables(@RequestParam(required = false) UUID centerId,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        PageDeResultats<Compte> lus = listerComptes.executer(centre, new Pagination(page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageReponse.depuis(lus, CompteReponse::depuis));
    }

    @PostMapping("/{id}/compte/lier")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierReponse> lierCompte(@RequestParam(required = false) UUID centerId, @PathVariable UUID id,
                                                       @RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centerId);
        LierCompteDemande demande = CorpsJson.lire(lecteur, requete, corps, LierCompteDemande.class);
        if (demande.userId() == null) {
            throw new SaisieInvalide("Champ obligatoire absent : userId");
        }
        return ResponseEntity.ok(InfirmierReponse.depuis(
                lierCompte.executer(centre, new InfirmierId(id), new CompteId(demande.userId()))));
    }

    @PostMapping("/{id}/compte/creer")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<CompteCreeReponse> creerCompte(@RequestParam(required = false) UUID centerId, @PathVariable UUID id,
                                                         @RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centerId);
        CreerCompteDemande demande = CorpsJson.lire(lecteur, requete, corps, CreerCompteDemande.class);
        if (demande.identifiant() == null || demande.identifiant().isBlank()) {
            throw new SaisieInvalide("Champ obligatoire absent : identifiant");
        }
        CompteCree cree = creerCompte.executer(centre, new InfirmierId(id), demande.identifiant(),
                AdresseEmail.depuisLaSaisie(demande.email()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new CompteCreeReponse(InfirmierReponse.depuis(cree.fiche()), cree.motDePasseTemporaire()));
    }

    @DeleteMapping("/{id}/compte")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierReponse> delierCompte(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(InfirmierReponse.depuis(delierCompte.executer(centre, new InfirmierId(id))));
    }

    /** Fiche saisie : tout est lu comme du texte, les valeurs sont jugées par le domaine (400 si inutilisables). */
    public record InfirmierDemande(String matricule, String nom, String prenom, String telephone, String qualification,
                                   Boolean habiliteIsolement) {
        IdentiteDeLInfirmier identite() {
            return new IdentiteDeLInfirmier(nom, prenom, telephone);
        }

        boolean habilite() {
            return Boolean.TRUE.equals(habiliteIsolement);
        }
    }

    public record LierCompteDemande(UUID userId) {
    }

    public record CreerCompteDemande(String identifiant, String email) {
    }

    public record AffectationReponse(UUID id, UUID salleId, UUID creneauId, List<JourDeLaSemaine> jours,
                                     List<JourDeLaSemaine> joursEnSureffectif) {
        static AffectationReponse depuis(AffectationLue a) {
            return new AffectationReponse(a.id(), a.salle().valeur(), a.creneau().valeur(), a.jours(), List.of());
        }
    }

    public record CompteReponse(UUID id, String username, String nomComplet, boolean actif) {
        static CompteReponse depuis(Compte c) {
            return c == null ? null : new CompteReponse(c.id().valeur(), c.identifiant(), c.nomComplet(), c.actif());
        }
    }

    /** @param compte le compte lié à la fiche, absent si la fiche n'a pas d'accès à l'application */
    public record InfirmierReponse(UUID id, String matricule, String nom, String prenom, String telephone,
                                   Qualification qualification, boolean habiliteIsolement, boolean actif,
                                   List<AffectationReponse> affectations, CompteReponse compte) {
        static InfirmierReponse depuis(FicheDetaillee f) {
            return new InfirmierReponse(f.id(), f.matricule(), f.nom(), f.prenom(), f.telephone(), f.qualification(),
                    f.habiliteIsolement(), f.actif(), f.roulement().stream().map(AffectationReponse::depuis).toList(),
                    CompteReponse.depuis(f.compte()));
        }
    }

    /** @param motDePasseTemporaire communiqué une seule fois : il n'est jamais relisible ensuite */
    public record CompteCreeReponse(InfirmierReponse infirmier, String motDePasseTemporaire) {
    }

    /** Page de résultats : {@code size} est la taille effective (plafonnée), {@code total} le nombre sans pagination. */
    public record PageReponse<T>(List<T> items, int page, int size, long total) {
        static <S, T> PageReponse<T> depuis(PageDeResultats<S> page, Function<S, T> vers) {
            return new PageReponse<>(page.elements().stream().map(vers).toList(), page.page(), page.taille(), page.total());
        }
    }
}
