package com.hemodialyse.backend.contexts.absencespatients.interfaces.http;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hemodialyse.backend.contexts.absencespatients.application.CloturerLeMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.ConsulterLesMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.RouvrirLeMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.SessionCourante;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsences;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Adapter entrant HTTP de la clôture mensuelle des absences (slice A6, ADR 0042), sur le modèle de la Facturation (F7). Le centre
 * est celui de la session ({@code centerId} facultatif, 403 s'il diffère) ; l'auteur est l'utilisateur de la session.
 * <ul>
 *   <li>{@code GET /api/v1/absences-patients/mois?annee=AAAA} : les 12 mois de l'année (les quatre profils d'accès : l'état d'un
 *       mois sert à désactiver les écritures d'un mois clos) ; le motif de réouverture et les identifiants d'auteurs ne sont rendus
 *       qu'à l'ADMIN (null pour les autres) ;</li>
 *   <li>{@code POST /api/v1/absences-patients/mois/{AAAA-MM}/cloturer} (ADMIN) : 200 avec l'avertissement chiffré
 *       ({@code absencesAQualifierRestantes}, {@code absencesEnRetard}), 409 {@code MOIS_D_ABSENCES_DEJA_CLOS}, 422
 *       {@code MOIS_D_ABSENCES_NON_TERMINE} ;</li>
 *   <li>{@code POST /api/v1/absences-patients/mois/{AAAA-MM}/rouvrir} (ADMIN, corps {@code {motif}} obligatoire, 500 caractères au
 *       plus) : 200, 400 {@code MOTIF_DE_REOUVERTURE_OBLIGATOIRE}, 409 {@code MOIS_D_ABSENCES_NON_CLOS}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/absences-patients/mois")
public class MoisDAbsencesControleur {

    private static final String ACCES = "hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')";
    private static final Pattern FORMAT_DU_MOIS = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    private static final int PREMIERE_ANNEE = 2000;
    private static final int DERNIERE_ANNEE = 2100;

    private final SessionCourante session;
    private final ConsulterLesMoisDAbsences consulter;
    private final CloturerLeMoisDAbsences cloturer;
    private final RouvrirLeMoisDAbsences rouvrir;

    public MoisDAbsencesControleur(SessionCourante session, ConsulterLesMoisDAbsences consulter, CloturerLeMoisDAbsences cloturer,
                                   RouvrirLeMoisDAbsences rouvrir) {
        this.session = session;
        this.consulter = consulter;
        this.cloturer = cloturer;
        this.rouvrir = rouvrir;
    }

    static YearMonth mois(String texte) {
        if (texte == null || !FORMAT_DU_MOIS.matcher(texte).matches()) {
            throw new RequeteInvalide("MOIS_INVALIDE", "Le mois doit être au format AAAA-MM");
        }
        try {
            YearMonth mois = YearMonth.parse(texte);
            if (mois.getYear() < PREMIERE_ANNEE || mois.getYear() > DERNIERE_ANNEE) {
                throw new RequeteInvalide("MOIS_INVALIDE", "Le mois doit être compris entre 2000-01 et 2100-12");
            }
            return mois;
        } catch (DateTimeParseException e) {
            throw new RequeteInvalide("MOIS_INVALIDE", "Le mois doit être au format AAAA-MM");
        }
    }

    /**
     * Le motif de réouverture et les identifiants des administrateurs ne sont rendus qu'aux ADMIN (les autres profils lisent l'état et les instants).
     */
    private static boolean estAdmin(Authentication authentification) {
        return authentification != null && authentification.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private UtilisateurId auteur() {
        UtilisateurId utilisateur = session.intervenant().utilisateur();
        if (utilisateur == null) {
            throw new AccessDeniedException("Utilisateur de la session introuvable");
        }
        return utilisateur;
    }

    @GetMapping
    @PreAuthorize(ACCES)
    public ResponseEntity<MoisReponse> lister(@RequestParam(required = false) Integer annee,
                                              @RequestParam(required = false) UUID centerId, Authentication authentification) {
        if (annee == null || annee < PREMIERE_ANNEE || annee > DERNIERE_ANNEE) {
            throw new RequeteInvalide("ANNEE_INVALIDE", "L'année doit être comprise entre 2000 et 2100");
        }
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(MoisReponse.depuis(centre, annee,
                consulter.executer(centre, annee), estAdmin(authentification)));
    }

    @PostMapping("/{mois}/cloturer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClotureReponse> cloturer(@PathVariable String mois, @RequestParam(required = false) UUID centerId) {
        YearMonth moisACloturer = mois(mois);
        CentreId centre = session.centre(centerId);
        UtilisateurId auteur = auteur();
        var resultat = cloturer.executer(centre, moisACloturer, auteur);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ClotureReponse(centre.valeur(),
                resultat.mois().toString(), "CLOTURE", auteur.valeur(), resultat.le(), resultat.aQualifierRestantes(),
                resultat.enRetard()));
    }

    @PostMapping("/{mois}/rouvrir")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReouvertureReponse> rouvrir(@PathVariable String mois, @RequestParam(required = false) UUID centerId,
                                                      @RequestBody(required = false) ReouvertureDemande demande) {
        YearMonth moisARouvrir = mois(mois);
        CentreId centre = session.centre(centerId);
        rouvrir.executer(centre, moisARouvrir, auteur(), demande == null ? null : demande.motif());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ReouvertureReponse(centre.valeur(), moisARouvrir.toString(), "OUVERT"));
    }

    /**
     * Corps de la réouverture : le motif est obligatoire (vérifié par le domaine).
     */
    public record ReouvertureDemande(String motif) {
    }

    public record ReouvertureReponse(UUID centerId, String mois, String statut) {
    }

    /**
     * Réponse de la clôture : l'état du mois et l'avertissement chiffré (absences encore « à qualifier » et, parmi elles, en retard).
     */
    public record ClotureReponse(UUID centerId, String mois, String statut, UUID cloturePar, Instant clotureLe,
                                 long absencesAQualifierRestantes, long absencesEnRetard) {
    }

    /**
     * Les 12 mois d'une année. {@code statut} vaut {@code OUVERT} ou {@code CLOTURE} ; les champs de dernière clôture et de dernière
     * réouverture restent renseignés après une réouverture (l'historique complet est au journal).
     */
    public record MoisReponse(UUID centerId, int annee, List<Mois> mois) {

        static MoisReponse depuis(CentreId centre, int annee, List<MoisAbsences> mois, boolean complet) {
            return new MoisReponse(centre.valeur(), annee, mois.stream().map(m -> new Mois(m.mois().toString(),
                    m.estClos() ? "CLOTURE" : "OUVERT", complet ? id(m.closPar()) : null, m.closLe(),
                    complet ? id(m.rouvertPar()) : null, m.rouvertLe(), complet ? m.motifDeReouverture() : null)).toList());
        }

        private static UUID id(UtilisateurId utilisateur) {
            return utilisateur == null ? null : utilisateur.valeur();
        }

        @JsonInclude(JsonInclude.Include.ALWAYS)
        public record Mois(String mois, String statut, UUID cloturePar, Instant clotureLe, UUID rouvertPar,
                           Instant rouvertLe,
                           String motifDeReouverture) {
        }
    }
}
