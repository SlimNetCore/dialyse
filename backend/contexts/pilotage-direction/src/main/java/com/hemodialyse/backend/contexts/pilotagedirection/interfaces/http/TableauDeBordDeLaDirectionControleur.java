package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.application.SessionDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection.EntreeDHistorique;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Indicateurs;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.Repartition;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.SocieteDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.VueDEnsemble;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP du tableau de bord de la direction (PL10b1, ADR 0151) : {@code /societe}, {@code /overview}, {@code /breakdown},
 * {@code /indicators} et {@code /alerts/history}. Mêmes URL, mêmes rôles (DIRECTION) et mêmes codes que le legacy. La société vient de la
 * session (jamais d'un paramètre), revérifiée à chaque appel, AVANT la lecture des paramètres (403 avant 400, KB-PL10b-1 corrigé). Un
 * {@code Accept} qui n'admet pas le JSON est refusé en 406 et un chemin voisin en 404 (KB-PL10b-3 corrigé) ; chaque consultation est
 * journalisée (KB-PL10b-2 corrigé, action {@code DIRECTION_TABLEAU_DE_BORD_CONSULTATION}). Paramètres stricts (KB-PL10b-6 corrigé) :
 * dates ISO {@code aaaa-mm-jj}, {@code limit} entier décimal ASCII ; un paramètre répété est refusé en 400, un {@code limit} vide aussi.
 */
@RestController
@RequestMapping(value = "/api/v1/direction", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('DIRECTION')")
public class TableauDeBordDeLaDirectionControleur {

    private static final int LIMITE_PAR_DEFAUT = 50;

    private final SessionDeLaDirection session;
    private final TableauDeBordDeLaDirection tableau;
    private final IndicateursDeLaDirection indicateurs;
    private final AlertesDeLaDirection alertes;

    public TableauDeBordDeLaDirectionControleur(SessionDeLaDirection session, TableauDeBordDeLaDirection tableau,
                                                IndicateursDeLaDirection indicateurs, AlertesDeLaDirection alertes) {
        this.session = session;
        this.tableau = tableau;
        this.indicateurs = indicateurs;
        this.alertes = alertes;
    }

    /** Société de la direction connectée et liste de ses centres (pour les filtres). */
    @GetMapping("/societe")
    public ResponseEntity<SocieteDeLaDirection> societe() {
        return aucuneMiseEnCache(tableau.societe(session.societe()));
    }

    /** Vue d'ensemble par centre et consolidée sur la période (par défaut : depuis le 1er janvier). */
    @GetMapping("/overview")
    public ResponseEntity<VueDEnsemble> overview(HttpServletRequest requete) {
        UUID societe = session.societe();
        return aucuneMiseEnCache(tableau.vueDEnsemble(societe, date(requete, "from"), date(requete, "to")));
    }

    /** Répartitions par centre : sexe, âge, caisses d'assurance, traitement de l'anémie (agrégats anonymes). */
    @GetMapping("/breakdown")
    public ResponseEntity<Repartition> breakdown(HttpServletRequest requete) {
        UUID societe = session.societe();
        return aucuneMiseEnCache(tableau.repartition(societe, date(requete, "from"), date(requete, "to")));
    }

    /** Indicateurs médicaux (cibles KDIGO), de stock et alertes : agrégats anonymes par centre et consolidés. */
    @GetMapping("/indicators")
    public ResponseEntity<Indicateurs> indicators(HttpServletRequest requete) {
        UUID societe = session.societe();
        return aucuneMiseEnCache(indicateurs.indicateurs(societe, date(requete, "from"), date(requete, "to")));
    }

    /** Historique récent des alertes, du plus récemment actif au plus ancien ({@code limit} borné à [1, 200] par le Pilotage). */
    @GetMapping("/alerts/history")
    public ResponseEntity<List<EntreeDHistorique>> alertsHistory(HttpServletRequest requete) {
        UUID societe = session.societe();
        return aucuneMiseEnCache(alertes.historique(societe, limite(requete)));
    }

    /**
     * Vrai si un paramètre propre à la route est présent et illisible : le legacy, qui liait les paramètres avant la garde, répondait alors
     * « Accès refusé » (et non « Access Denied ») à un profil sans droit ; message d'origine restauré, 403 inchangé (voir {@code ErreursDuPilotageAdvice}).
     */
    static boolean parametreIllisible(HttpServletRequest requete) {
        try {
            if (requete.getRequestURI().endsWith("/alerts/history")) {
                limite(requete);
            } else {
                date(requete, "from");
                date(requete, "to");
            }
            return false;
        } catch (RapportDeLaDirectionControleur.ParametreIllisible e) {
            return true;
        }
    }

    private static <T> ResponseEntity<T> aucuneMiseEnCache(T corps) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corps);
    }

    /** La valeur du paramètre ; {@code null} s'il est absent ; un paramètre répété est illisible. */
    private static String valeur(HttpServletRequest requete, String nom) {
        String[] valeurs = requete.getParameterValues(nom);
        if (valeurs == null) return null;
        if (valeurs.length > 1) throw new RapportDeLaDirectionControleur.ParametreIllisible(nom);
        return valeurs[0];
    }

    /** Date ISO (aaaa-mm-jj) ; paramètre absent ou vide : {@code null} (le Pilotage applique la période par défaut). */
    static LocalDate date(HttpServletRequest requete, String nom) {
        String brut = valeur(requete, nom);
        if (brut == null || brut.isEmpty()) return null;
        try {
            return LocalDate.from(DateTimeFormatter.ISO_DATE.parse(brut));
        } catch (DateTimeParseException e) {
            throw new RapportDeLaDirectionControleur.ParametreIllisible(nom);
        }
    }

    /** Entier décimal en chiffres ASCII (signe moins admis) ; absent : 50 ; vide, hexadécimal, {@code +7}, espaces, autres chiffres : illisible. */
    private static int limite(HttpServletRequest requete) {
        String brut = valeur(requete, "limit");
        if (brut == null) return LIMITE_PAR_DEFAUT;
        if (!brut.matches("-?[0-9]+")) throw new RapportDeLaDirectionControleur.ParametreIllisible("limit");
        try {
            return Integer.parseInt(brut);
        } catch (NumberFormatException e) {
            throw new RapportDeLaDirectionControleur.ParametreIllisible("limit");
        }
    }
}
