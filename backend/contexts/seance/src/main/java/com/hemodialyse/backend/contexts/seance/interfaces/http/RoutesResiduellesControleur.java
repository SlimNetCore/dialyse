package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.AjouterUnJourAuCalendrier;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLeCalendrier;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLeJournalDuJour;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLesSeancesRecentes;
import com.hemodialyse.backend.contexts.seance.application.RenseignerLeVoletMedical;
import com.hemodialyse.backend.contexts.seance.application.RetirerUnJourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.JourDuCalendrierDemande;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.VoletMedicalDemande;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter entrant HTTP des routes résiduelles de la Séance (SE7a, ADR 0094) : le journal du jour, le calendrier du centre (jours fériés et
 * fermetures exceptionnelles), le volet médical d'une séance et les séances récentes d'un patient. MÊMES URL, MÊMES noms de champs JSON et
 * MÊMES droits que le legacy. Les écarts décidés (KB-SEA-57, 58, 60) sont ceux de l'ADR 0094 : 400 {@code BAD_REQUEST} (et non 500) pour
 * un paramètre absent ou hors bornes, 409 {@code JOUR_DEJA_AU_CALENDRIER} pour un doublon. 403 avant 400 (garde de l'advice).
 * <p>
 * Routé depuis SE7a : les handlers correspondants du legacy sont retirés (refactoring sur place, ADR 0024 : aucune façade ni drapeau, la
 * bascule est réversible par retour git).
 */
@RestController
@RequestMapping("/api/v1/seances")
public class RoutesResiduellesControleur {

    private final ConsulterLeJournalDuJour journal;
    private final ConsulterLeCalendrier calendrier;
    private final AjouterUnJourAuCalendrier ajouterUnJour;
    private final RetirerUnJourDuCalendrier retirerUnJour;
    private final RenseignerLeVoletMedical renseignerLeVoletMedical;
    private final ConsulterLesSeancesRecentes recentes;

    public RoutesResiduellesControleur(ConsulterLeJournalDuJour journal, ConsulterLeCalendrier calendrier,
                                       AjouterUnJourAuCalendrier ajouterUnJour, RetirerUnJourDuCalendrier retirerUnJour,
                                       RenseignerLeVoletMedical renseignerLeVoletMedical, ConsulterLesSeancesRecentes recentes) {
        this.journal = journal;
        this.calendrier = calendrier;
        this.ajouterUnJour = ajouterUnJour;
        this.retirerUnJour = retirerUnJour;
        this.renseignerLeVoletMedical = renseignerLeVoletMedical;
        this.recentes = recentes;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/journal")
    public ResponseEntity<Map<String, Object>> journal(@RequestParam UUID centerId,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateSeance) {
        var resultat = journal.executer(centerId, dateSeance);
        List<Map<String, Object>> patients = new ArrayList<>();
        for (var p : resultat.patients()) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("seanceId", p.seance().valeur());
            ligne.put("patientId", p.patient().valeur());
            ligne.put("dateSeance", p.jour());
            ligne.put("status", p.statut());
            ligne.put("patientCode", p.code());
            ligne.put("patientNom", p.nom());
            ligne.put("patientPrenom", p.prenom());
            patients.add(ligne);
        }
        List<Map<String, Object>> articles = new ArrayList<>();
        for (var s : resultat.sorties()) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("articleId", s.article());
            ligne.put("articleCode", s.code());
            ligne.put("articleLibelle", s.libelle());
            ligne.put("quantiteTotale", s.quantiteTotale());
            articles.add(ligne);
        }
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("dateSeance", resultat.jour());
        reponse.put("patients", patients);
        reponse.put("sortiesArticles", articles);
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/calendar")
    public ResponseEntity<Map<String, Object>> calendrier(@RequestParam UUID centerId, @RequestParam int year, @RequestParam int month) {
        var mois = calendrier.executer(centerId, year, month);
        List<Map<String, Object>> feries = new ArrayList<>();
        for (var j : mois.joursFeries()) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("id", j.id());
            ligne.put("dayDate", j.jour());
            ligne.put("label", j.libelle());
            feries.add(ligne);
        }
        List<Map<String, Object>> fermetures = new ArrayList<>();
        for (var j : mois.fermetures()) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("id", j.id());
            ligne.put("dayDate", j.jour());
            ligne.put("reason", j.libelle());
            fermetures.add(ligne);
        }
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("year", year);
        reponse.put("month", month);
        reponse.put("holidays", feries);
        reponse.put("closures", fermetures);
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/calendar/holiday")
    public ResponseEntity<Map<String, Object>> ajouterUnJourFerie(@RequestBody JourDuCalendrierDemande demande) {
        return ajouter(NatureDeJour.JOUR_FERIE, demande);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/calendar/closure")
    public ResponseEntity<Map<String, Object>> ajouterUneFermeture(@RequestBody JourDuCalendrierDemande demande) {
        return ajouter(NatureDeJour.FERMETURE_EXCEPTIONNELLE, demande);
    }

    private ResponseEntity<Map<String, Object>> ajouter(NatureDeJour nature, JourDuCalendrierDemande demande) {
        if (demande.centerId() == null) throw new SaisieInvalide("Centre requis");
        var ajoute = ajouterUnJour.executer(demande.centerId(), nature, demande.dayDate(), demande.labelOrReason());
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("id", ajoute.id());
        reponse.put("dayDate", ajoute.jour());
        return ResponseEntity.ok(reponse);
    }

    /** Le retrait d'un jour absent n'est pas une erreur : toujours 200 {@code deleted:true} (KB-SEA-58, reproduit). */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/calendar/holiday/{id}")
    public ResponseEntity<Map<String, Object>> retirerUnJourFerie(@PathVariable UUID id, @RequestParam UUID centerId) {
        retirerUnJour.executer(centerId, NatureDeJour.JOUR_FERIE, id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/calendar/closure/{id}")
    public ResponseEntity<Map<String, Object>> retirerUneFermeture(@PathVariable UUID id, @RequestParam UUID centerId) {
        retirerUnJour.executer(centerId, NatureDeJour.FERMETURE_EXCEPTIONNELLE, id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PutMapping("/{seanceId}/volet-medical")
    public ResponseEntity<Map<String, Object>> renseignerLeVoletMedical(@PathVariable UUID seanceId, @RequestBody VoletMedicalDemande demande) {
        if (demande.centerId() == null) throw new SaisieInvalide("Centre requis");
        var volet = renseignerLeVoletMedical.executer(demande.centerId(), seanceId, demande::observations);
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("id", volet.id());
        reponse.put("seanceId", volet.seance().valeur());
        reponse.put("updatedAt", volet.modifieLe());
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN')")
    @GetMapping("/patient/{patientId}/recentes")
    public List<Map<String, Object>> recentes(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before,
                                              @RequestParam(defaultValue = "3") int limit) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (var r : recentes.executer(centerId, patientId, before, limit)) {
            var constantes = r.constantes();
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("seanceId", r.seance().id().valeur());
            ligne.put("dateSeance", r.seance().jour());
            ligne.put("status", r.seance().statut());
            ligne.put("poidsAvantKg", constantes.map(c -> c.poidsAvantKg()).orElse(null));
            ligne.put("poidsApresKg", constantes.map(c -> c.poidsApresKg()).orElse(null));
            ligne.put("taAvant", constantes.map(c -> c.tensionAvant()).orElse(null));
            ligne.put("taApres", constantes.map(c -> c.tensionApres()).orElse(null));
            ligne.put("dureeMinutes", constantes.map(c -> c.dureeMinutes()).orElse(null));
            ligne.put("ultrafiltrationMl", constantes.map(c -> c.ultrafiltrationMl()).orElse(null));
            lignes.add(ligne);
        }
        return lignes;
    }
}
