package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.ConsulterLeDetailDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLeTableauDeBord;
import com.hemodialyse.backend.contexts.seance.application.ExporterLeTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.LigneDuDetail;
import com.hemodialyse.backend.contexts.seance.domain.TableauDeBordDesSeances;
import com.hemodialyse.backend.contexts.seance.domain.TypeDeDetail;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter entrant HTTP du tableau de bord mensuel des séances (SE7b, ADR 0095) : {@code /api/v1/seances/dashboard}, {@code /dashboard/details}
 * et {@code /dashboard/export}. MÊMES URL, MÊMES noms de champs JSON, MÊMES droits ({@code ADMIN}, {@code INFIRMIER}, {@code MEDECIN},
 * {@code SECRETAIRE}) et MÊMES fichiers que le legacy. Écart décidé (KB-SEA-63) : 400 {@code BAD_REQUEST} (et non 500) pour un mois hors
 * bornes, une année ou un mois absents ou illisibles, un centre illisible ; 403 avant 400 (garde de l'advice).
 * <p>
 * Les clés françaises mortes de {@code expectedByWeekday} (KB-SEA-62), la fuite d'un patient d'un autre centre dans les répartitions
 * (KB-SEA-65) et le détail nominatif ouvert à la secrétaire (KB-SEA-66) sont REPRODUITS à l'identique : voir l'ADR 0095, section A.
 */
@RestController
@RequestMapping("/api/v1/seances")
public class TableauDeBordDesSeancesControleur {

    private static final String FORMULE = "absences = absences du suivi (declarees ou detectees, hors annulees) ; presences = seances realisees";

    private final ConsulterLeTableauDeBord tableau;
    private final ConsulterLeDetailDuTableauDeBord detail;
    private final ExporterLeTableauDeBord export;

    public TableauDeBordDesSeancesControleur(ConsulterLeTableauDeBord tableau, ConsulterLeDetailDuTableauDeBord detail, ExporterLeTableauDeBord export) {
        this.tableau = tableau;
        this.detail = detail;
        this.export = export;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> tableauDeBord(@RequestParam UUID centerId, @RequestParam int year, @RequestParam int month) {
        TableauDeBordDesSeances t = tableau.executer(centerId, year, month);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("formula", FORMULE);
        details.put("periodStart", t.periodeDebut());
        details.put("periodEnd", t.periodeFin());
        details.put("patientsConsidered", t.attendues().patientsConsideres());
        details.put("blockedDays", t.joursBloques());
        details.put("expectedFromSchedule", t.seancesPrevues());
        details.put("presenceCount", t.presences());
        details.put("absenceCount", t.absences());
        details.put("expectedByWeekday", t.attendues().parJourDeSemaine());

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("year", t.mois().annee());
        reponse.put("month", t.mois().mois());
        reponse.put("expectedSeances", t.seancesPrevues());
        reponse.put("presenceCount", t.presences());
        reponse.put("absenceCount", t.absences());
        reponse.put("totalSeances", t.seancesTotales());
        reponse.put("effectifPatients", t.effectif().patients());
        reponse.put("absenceDetails", details);
        reponse.put("sexeDistribution", t.effectif().parSexe());
        reponse.put("ageDistribution", t.effectif().parTranche());
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/dashboard/details")
    public ResponseEntity<Map<String, Object>> detailDuTableauDeBord(@RequestParam UUID centerId, @RequestParam int year,
                                                                       @RequestParam int month, @RequestParam String kind) {
        var resultat = detail.executer(centerId, year, month, kind);
        boolean presence = resultat.type() == TypeDeDetail.PRESENCE;
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (LigneDuDetail l : resultat.lignes()) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("patientId", l.patient().valeur());
            ligne.put("patientNom", l.nom());
            ligne.put("patientPrenom", l.prenom());
            ligne.put("dateSeance", l.jour());
            ligne.put("weekday", l.jour().getDayOfWeek().name());
            ligne.put("scheduled", true);
            ligne.put("present", presence);
            ligne.put("status", l.statut());
            lignes.add(ligne);
        }
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("year", resultat.mois().annee());
        reponse.put("month", resultat.mois().mois());
        reponse.put("kind", resultat.type().code());
        reponse.put("total", lignes.size());
        reponse.put("items", lignes);
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/dashboard/export")
    public ResponseEntity<byte[]> exporter(@RequestParam UUID centerId, @RequestParam int year, @RequestParam int month,
                                           @RequestParam(defaultValue = "csv") String format) {
        var fichier = export.executer(centerId, year, month, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fichier.nomDuFichier())
                .contentType(MediaType.parseMediaType(fichier.format().typeDeContenu()))
                .body(fichier.contenu());
    }
}
