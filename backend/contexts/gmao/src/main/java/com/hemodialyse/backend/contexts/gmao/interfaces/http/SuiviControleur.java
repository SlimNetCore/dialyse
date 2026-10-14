package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.SessionCourante;
import com.hemodialyse.backend.contexts.gmao.application.equipement.ConsulterLaFiche;
import com.hemodialyse.backend.contexts.gmao.application.equipement.VerifierLaDisponibilite;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ConsulterLaChronologie;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ConsulterLesIndicateurs;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DocumentImprime;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ImprimerLeBon;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.ConsulterLesStatistiques;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.interfaces.http.SuiviReponses.DisponibilitePatientReponse;
import com.hemodialyse.backend.contexts.gmao.interfaces.http.SuiviReponses.EvenementReponse;
import com.hemodialyse.backend.contexts.gmao.interfaces.http.SuiviReponses.FicheReponse;
import com.hemodialyse.backend.contexts.gmao.interfaces.http.SuiviReponses.IndicateursReponse;
import com.hemodialyse.backend.contexts.gmao.interfaces.http.SuiviReponses.StatistiquesReponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Adapter entrant HTTP de la consultation et du pilotage de la GMAO, en lecture seule et borné au centre de la session,
 * réservé au rôle {@code ADMIN} : bon, indicateurs et chronologie d'une intervention, fiche et disponibilité d'un équipement,
 * statistiques. Mêmes URLs, mêmes corps et mêmes codes d'erreur que le legacy (400 pour un identifiant inconnu, 500 pour un
 * identifiant illisible de l'intervention, 400 pour celui de l'équipement : KB-GMAO-75).
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class SuiviControleur {

    private static final String INTERVENTIONS = "/api/v1/gmao/interventions/{id}";
    private static final String EQUIPEMENTS = "/api/v1/gmao/equipements/{id}";

    private final SessionCourante session;
    private final ImprimerLeBon imprimerLeBon;
    private final ConsulterLesIndicateurs indicateurs;
    private final ConsulterLaChronologie chronologie;
    private final ConsulterLaFiche fiche;
    private final VerifierLaDisponibilite disponibilite;
    private final ConsulterLesStatistiques statistiques;

    public SuiviControleur(SessionCourante session, ImprimerLeBon imprimerLeBon, ConsulterLesIndicateurs indicateurs,
                           ConsulterLaChronologie chronologie, ConsulterLaFiche fiche, VerifierLaDisponibilite disponibilite,
                           ConsulterLesStatistiques statistiques) {
        this.session = session;
        this.imprimerLeBon = imprimerLeBon;
        this.indicateurs = indicateurs;
        this.chronologie = chronologie;
        this.fiche = fiche;
        this.disponibilite = disponibilite;
        this.statistiques = statistiques;
    }

    /** Fuseau d'affichage des dates du bon : celui du navigateur (IANA, casse exacte), UTC à défaut ou s'il est invalide. */
    static ZoneId zone(String tz) {
        if (tz == null || tz.isBlank()) return ZoneOffset.UTC;
        try {
            return ZoneId.of(tz.trim());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    /** Bon d'intervention imprimable : PDF en ligne, ou le format du modèle de document du centre (Excel, HTML). */
    @GetMapping(INTERVENTIONS + "/bon")
    public ResponseEntity<byte[]> bon(@PathVariable UUID id, @RequestParam(required = false) String tz) {
        DocumentImprime document = imprimerLeBon.executer(new InterventionId(id), session.centre(), zone(tz),
                session.identifiant());
        String base = "bon-intervention-" + id.toString().substring(0, 8);
        return switch (document.format().toUpperCase(Locale.ROOT)) {
            case "EXCEL", "XLS", "XLSX" -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(base + ".xlsx", StandardCharsets.UTF_8).build().toString())
                    .body(document.contenu());
            case "HTML" -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(document.contenu());
            default -> ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline().filename(base + ".pdf", StandardCharsets.UTF_8).build().toString())
                    .body(document.contenu());
        };
    }

    @GetMapping(INTERVENTIONS + "/indicateurs")
    public IndicateursReponse indicateurs(@PathVariable UUID id) {
        return IndicateursReponse.depuis(indicateurs.executer(new InterventionId(id), session.centre()));
    }

    @GetMapping(INTERVENTIONS + "/chronologie")
    public List<EvenementReponse> chronologie(@PathVariable UUID id) {
        return chronologie.executer(new InterventionId(id), session.centre()).stream().map(EvenementReponse::depuis).toList();
    }

    @GetMapping(EQUIPEMENTS + "/fiche")
    public FicheReponse fiche(@PathVariable String id) {
        return FicheReponse.depuis(fiche.executer(new EquipementId(UUID.fromString(id)), session.centre()));
    }

    @GetMapping(EQUIPEMENTS + "/disponibilite-patient")
    public DisponibilitePatientReponse disponibilitePatient(@PathVariable String id) {
        return DisponibilitePatientReponse.depuis(
                disponibilite.executer(new EquipementId(UUID.fromString(id)), session.centre()));
    }

    @GetMapping("/api/v1/gmao/stats")
    public StatistiquesReponse statistiques() {
        return StatistiquesReponse.depuis(statistiques.executer(session.centre()));
    }
}
