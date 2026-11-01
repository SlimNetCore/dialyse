package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.cloture.CloturerLeMois;
import com.hemodialyse.backend.contexts.facturation.application.cloture.ConsulterLesPeriodes;
import com.hemodialyse.backend.contexts.facturation.application.cloture.RouvrirLeMois;
import com.hemodialyse.backend.contexts.facturation.interfaces.http.dto.CentreDemandeRequete;
import com.hemodialyse.backend.contexts.facturation.interfaces.http.dto.ReouvertureReponse;
import com.hemodialyse.backend.contexts.facturation.interfaces.http.dto.ReouvertureRequete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Adapter entrant HTTP de la clôture mensuelle de facturation (slice F7, ADR 0037). Réservé à {@code ADMIN}. Le centre du corps ou
 * de la requête (facultatif : celui de la session par défaut) est confronté à celui de la session (403 sinon) ; l'auteur est
 * l'utilisateur de la session.
 * <ul>
 *   <li>{@code GET /api/v1/facturation/periodes?annee=YYYY} : les 12 mois de l'année ;</li>
 *   <li>{@code POST /api/v1/facturation/periodes/{mois}/cloturer} (mois {@code YYYY-MM}) : 200 avec l'avertissement chiffré,
 *       409 {@code PERIODE_DEJA_CLOTUREE} si le mois est déjà clos ;</li>
 *   <li>{@code POST /api/v1/facturation/periodes/{mois}/rouvrir} (corps {@code {motif}} obligatoire) : 200, 400
 *       {@code MOTIF_DE_REOUVERTURE_OBLIGATOIRE}, 409 {@code PERIODE_NON_CLOTUREE}, 422 {@code PERIODE_COMPTABLE_CLOTUREE}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/facturation/periodes")
public class PeriodesDeFacturationControleur {

    private static final Pattern FORMAT_DU_MOIS = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    private static final int PREMIERE_ANNEE = 2000;
    private static final int DERNIERE_ANNEE = 2100;

    private final SessionCourante session;
    private final ConsulterLesPeriodes consulter;
    private final CloturerLeMois cloturer;
    private final RouvrirLeMois rouvrir;

    public PeriodesDeFacturationControleur(SessionCourante session, ConsulterLesPeriodes consulter, CloturerLeMois cloturer,
                                           RouvrirLeMois rouvrir) {
        this.session = session;
        this.consulter = consulter;
        this.cloturer = cloturer;
        this.rouvrir = rouvrir;
    }

    static YearMonth mois(String texte) {
        if (texte == null || !FORMAT_DU_MOIS.matcher(texte).matches()) {
            throw new RequeteInvalide("MOIS_INVALIDE", "Le mois doit etre au format AAAA-MM");
        }
        try {
            YearMonth mois = YearMonth.parse(texte);
            if (mois.getYear() < PREMIERE_ANNEE || mois.getYear() > DERNIERE_ANNEE) {
                throw new RequeteInvalide("MOIS_INVALIDE", "Le mois doit etre compris entre 2000-01 et 2100-12");
            }
            return mois;
        } catch (DateTimeParseException e) {
            throw new RequeteInvalide("MOIS_INVALIDE", "Le mois doit etre au format AAAA-MM");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<PeriodesReponse> lister(@RequestParam(required = false) Integer annee, @RequestParam(required = false) UUID centerId) {
        if (annee == null || annee < PREMIERE_ANNEE || annee > DERNIERE_ANNEE) {
            throw new RequeteInvalide("ANNEE_INVALIDE", "L'annee doit etre comprise entre 2000 et 2100");
        }
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(PeriodesReponse.depuis(centre, annee, consulter.executer(centre, annee)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{mois}/cloturer")
    public ResponseEntity<ClotureReponse> cloturer(@PathVariable String mois,
                                                   @RequestBody(required = false) CentreDemandeRequete requete) {
        YearMonth moisACloturer = mois(mois);
        CentreId centre = session.centre(requete == null ? null : requete.centerId());
        UUID auteur = session.utilisateur();
        return ResponseEntity.ok(ClotureReponse.depuis(centre, auteur, cloturer.executer(centre, moisACloturer, auteur)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{mois}/rouvrir")
    public ResponseEntity<ReouvertureReponse> rouvrir(@PathVariable String mois,
                                                      @RequestBody(required = false) ReouvertureRequete requete) {
        YearMonth moisARouvrir = mois(mois);
        CentreId centre = session.centre(requete == null ? null : requete.centerId());
        rouvrir.executer(centre, moisARouvrir, session.utilisateur(), requete == null ? null : requete.motif());
        return ResponseEntity.ok(new ReouvertureReponse(centre.valeur(), moisARouvrir.toString(), "OUVERT"));
    }
}
