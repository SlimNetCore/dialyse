package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.planification.application.ClasserLesRemplacants;
import com.hemodialyse.backend.contexts.planification.application.ConsulterLaChargeDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.ConsulterLaChargeDesInfirmiers.ChargeDuMois;
import com.hemodialyse.backend.contexts.planification.application.ConsulterLaPresence;
import com.hemodialyse.backend.contexts.planification.application.ConsulterLaPresence.AlertesDeSureffectif;
import com.hemodialyse.backend.contexts.planification.application.ImprimerLaPresence;
import com.hemodialyse.backend.contexts.planification.application.ImprimerLaPresence.Impression;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AlertePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Candidat;
import com.hemodialyse.backend.contexts.planification.domain.Presence.ChargeInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Presence.SemainePresence;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP de la présence des infirmiers (slice P4) : semaine, alertes de sous-effectif, alertes de sur-effectif (nouvelle
 * route), remplaçants proposés, charge mensuelle et impression. Mêmes URLs, mêmes droits (annotation et filtre de périmètre inchangés)
 * et même JSON que le legacy. Écarts décidés (27/10/2026) : entrées illisibles ou manquantes 400, taille de page plafonnée à 100 avec
 * la taille effective renvoyée, jour lu au fuseau du centre, case des remplaçants validée (422), formats d'impression inconnus 400
 * (KB-PLN-42, 44, 48, 49, 51).
 */
@RestController
@RequestMapping("/api/v1/infirmiers/presence")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class PresenceControleur {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SessionCourante session;
    private final ConsulterLaPresence presence;
    private final ClasserLesRemplacants remplacants;
    private final ConsulterLaChargeDesInfirmiers charge;
    private final ImprimerLaPresence impression;

    public PresenceControleur(SessionCourante session, ConsulterLaPresence presence, ClasserLesRemplacants remplacants,
                              ConsulterLaChargeDesInfirmiers charge, ImprimerLaPresence impression) {
        this.session = session;
        this.presence = presence;
        this.remplacants = remplacants;
        this.charge = charge;
        this.impression = impression;
    }

    @GetMapping("/semaine")
    public ResponseEntity<SemainePresence> semaine(@RequestParam(required = false) UUID centerId,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(presence.semaine(centre, date));
    }

    /**
     * @param jours horizon en jours à partir d'aujourd'hui (jour du centre), de 1 à 60, défaut 14
     */
    @GetMapping("/alertes")
    public ResponseEntity<List<AlertePresence>> alertes(@RequestParam(required = false) UUID centerId,
                                                        @RequestParam(defaultValue = "" + ConsulterLaPresence.HORIZON_PAR_DEFAUT_JOURS) int jours) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(presence.alertes(centre, jours));
    }

    /**
     * Sur-effectifs et infirmiers prévus dans deux salles au même créneau (KB-PLN-50), même horizon que les alertes.
     */
    @GetMapping("/alertes-sureffectif")
    public ResponseEntity<AlertesDeSureffectif> sureffectifs(@RequestParam(required = false) UUID centerId,
                                                             @RequestParam(defaultValue = "" + ConsulterLaPresence.HORIZON_PAR_DEFAUT_JOURS) int jours) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(presence.sureffectifs(centre, jours));
    }

    @GetMapping("/remplacants")
    public ResponseEntity<List<Candidat>> remplacants(@RequestParam(required = false) UUID centerId,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                      @RequestParam UUID salleId, @RequestParam UUID creneauId) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(remplacants.executer(centre, date, new SalleId(salleId), new CreneauId(creneauId)));
    }

    /**
     * @param mois mois voulu au format {@code yyyy-MM} (défaut : mois courant du centre)
     */
    @GetMapping("/charge")
    public ResponseEntity<ChargeReponse> charge(@RequestParam(required = false) UUID centerId,
                                                @RequestParam(required = false) String mois,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "" + Pagination.TAILLE_PAR_DEFAUT) int size) {
        CentreId centre = session.centre(centerId);
        ChargeDuMois lue = charge.executer(centre, lireLeMois(mois), new Pagination(page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ChargeReponse(lue.page().elements(), lue.page().total(),
                lue.page().page(), lue.page().taille(), lue.moyenne()));
    }

    /**
     * Planning de présence imprimable de la semaine (modèle de document du centre, ou modèle par défaut).
     */
    @GetMapping("/imprimer")
    public ResponseEntity<byte[]> imprimer(@RequestParam(required = false) UUID centerId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CentreId centre = session.centre(centerId);
        Impression imprimee = impression.executer(centre, date);
        String base = "presence-infirmiers-" + imprimee.debutDeSemaine();
        return switch (imprimee.format()) {
            case EXCEL -> ResponseEntity.ok().contentType(XLSX)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(base + ".xlsx", StandardCharsets.UTF_8).build().toString())
                    .body(imprimee.contenu());
            case HTML -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(imprimee.contenu());
            case PDF -> ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline().filename(base + ".pdf", StandardCharsets.UTF_8).build().toString())
                    .body(imprimee.contenu());
        };
    }

    private static YearMonth lireLeMois(String mois) {
        if (mois == null || mois.isBlank()) return null;
        try {
            return YearMonth.parse(mois);
        } catch (DateTimeException e) {
            throw new SaisieInvalide("Mois invalide (format attendu : yyyy-MM)");
        }
    }

    public record ChargeReponse(List<ChargeInfirmier> items, long total, int page, int size, double moyenne) {
    }
}
