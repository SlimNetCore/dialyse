package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDePec;
import com.hemodialyse.backend.contexts.patientcouverture.application.GererLesPec;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesPec;
import com.hemodialyse.backend.contexts.patientcouverture.application.RechercherLesPec;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.patientcouverture.domain.StatutPec;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDeLaPec;
import jakarta.servlet.http.HttpServletRequest;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter entrant HTTP de la prise en charge (slice PC3) : MÊMES URLs que le legacy ({@code /api/v1/pec/**}, variables de chemin
 * {@code pecId} et {@code patientId} comprises : le journal d'audit en dépend) et même JSON. Les routes d'attestation du même
 * préfixe restent au legacy (PC4). Droits du legacy INCHANGÉS : la validation est réservée à ADMIN, toutes les autres routes
 * sont ouvertes à tout utilisateur authentifié. Le corps est lu APRÈS l'autorisation (403 avant 400).
 * <p>
 * Écarts décidés (2026-11-03) : garde de centre sur toutes les routes (centre du jeton, 403) ; PEC inconnue ou d'un autre centre
 * 404 {@code PEC_INTROUVABLE} ; formats illisibles ou manquants 400 ; jour du centre pour l'attestation ; audit métier
 * ({@code PEC_VALIDATION}, {@code PEC_CLOTURE}) ; nom réel du patient dans les diffusions.
 * <p>
 * ARGENT, reproduit et À DÉCIDER AVEC LES MÉTIERS : deux règles « PEC valide pour une séance » (cette route {@code session-allowed}
 * et la création de séance), aucun contrôle de période, de forfait ni de chevauchement, suppression d'une PEC validée.
 */
@RestController
@RequestMapping("/api/v1/pec")
public class PecControleur {

    static final String ATTRIBUT_AUDIT_IDENTIFIANT = "hemodialyse.audit.entityId";

    private final GererLesPec gerer;
    private final RechercherLesPec rechercher;
    private final ListesDePecEnCache listes;
    private final NotificationsDeLaPec notifications;
    private final ObjectMapper lecteur;

    public PecControleur(GererLesPec gerer, RechercherLesPec rechercher, ListesDePecEnCache listes,
                         NotificationsDeLaPec notifications, ObjectMapper lecteur) {
        this.gerer = gerer;
        this.rechercher = rechercher;
        this.listes = listes;
        this.notifications = notifications;
        this.lecteur = lecteur;
    }

    // ---- DTO (contrat JSON inchangé)

    record Creation(UUID patientId, UUID centerId, String userId, LocalDate dateDebutDemande, LocalDate dateFinDemande,
                    UUID forfaitDemandeId) {
    }

    record Validation(UUID centerId, String userId, LocalDate dateDebutEffectif, LocalDate dateFinEffectif, UUID forfaitEffectifId) {
    }

    record Cloture(UUID centerId) {
    }

    record Recherche(UUID centerId, int page, int size, String search, String code, String nom, String assurance, String debut,
                     String fin, String statut) {
    }

    record Etat(UUID id, StatutPec status) {
    }

    record Autorisation(boolean allowed) {
    }

    record Suppression(boolean deleted) {
    }

    /** La PEC telle que l'entité de domaine d'avant : mêmes champs, mêmes noms (le front les consomme). */
    public record PecReponse(UUID id, UUID patientId, UUID centerId, LocalDate dateDebutDemande, LocalDate dateFinDemande,
                             UUID forfaitDemandeId, LocalDate dateDebutEffectif, LocalDate dateFinEffectif,
                             UUID forfaitEffectifId, StatutPec status, OffsetDateTime createdAt) {
        static PecReponse de(PriseEnCharge p) {
            return new PecReponse(p.id(), p.patient().valeur(), p.centre().valeur(), p.demandeDebut(), p.demandeFin(),
                    p.demandeForfait(), p.accordDebut(), p.accordFin(), p.accordForfait(), p.statut(),
                    p.creeLe() == null ? null : OffsetDateTime.ofInstant(p.creeLe(), ZoneOffset.UTC));
        }
    }

    // ---- routes

    @PostMapping
    public ResponseEntity<Etat> creer(@RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        Creation d = CorpsJson.lire(lecteur, requete, corps, Creation.class);
        if (d.centerId() == null) throw new SaisieInvalide("CenterId cannot be null");
        if (d.patientId() == null) throw new SaisieInvalide("PatientId cannot be null");
        PriseEnCharge pec = gerer.creer(d.centerId(), d.patientId(), d.dateDebutDemande(), d.dateFinDemande(), d.forfaitDemandeId());
        listes.apresEcriture(d.centerId(), pec.patient().valeur());
        requete.setAttribute(ATTRIBUT_AUDIT_IDENTIFIANT, pec.id().toString());
        return ResponseEntity.ok(new Etat(pec.id(), pec.statut()));
    }

    @GetMapping
    public ResponseEntity<List<PecReponse>> listerDuCentre(@RequestParam UUID centerId) {
        gerer.centre(centerId);
        return ResponseEntity.ok(listes.duCentre(centerId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{pecId}/validate")
    public ResponseEntity<Etat> valider(@PathVariable UUID pecId, @RequestBody(required = false) byte[] corps,
                                        HttpServletRequest requete) throws HttpMediaTypeNotSupportedException {
        Validation d = CorpsJson.lire(lecteur, requete, corps, Validation.class);
        if (d.centerId() == null) throw new SaisieInvalide("CenterId cannot be null");
        var validee = gerer.valider(d.centerId(), pecId, d.dateDebutEffectif(), d.dateFinEffectif(), d.forfaitEffectifId());
        PriseEnCharge pec = validee.pec();
        listes.apresEcriture(d.centerId(), pec.patient().valeur());
        notifications.pecValidee(d.centerId(), pecId, pec.patient().valeur(), validee.nomDuPatient());
        return ResponseEntity.ok(new Etat(pec.id(), pec.statut()));
    }

    @PostMapping("/{pecId}/close")
    public ResponseEntity<Etat> cloturer(@PathVariable UUID pecId, @RequestBody(required = false) byte[] corps,
                                         HttpServletRequest requete) throws HttpMediaTypeNotSupportedException {
        Cloture d = CorpsJson.lire(lecteur, requete, corps, Cloture.class);
        if (d.centerId() == null) throw new SaisieInvalide("CenterId cannot be null");
        var cloturee = gerer.cloturer(d.centerId(), pecId);
        PriseEnCharge pec = cloturee.pec();
        listes.apresEcriture(d.centerId(), pec.patient().valeur());
        notifications.pecCloturee(d.centerId(), pecId, pec.patient().valeur(), cloturee.nomDuPatient());
        return ResponseEntity.ok(new Etat(pec.id(), pec.statut()));
    }

    @GetMapping("/{pecId}/session-allowed")
    public ResponseEntity<Autorisation> sessionAllowed(@PathVariable UUID pecId, @RequestParam UUID centerId,
                                                       @RequestParam String userId) {
        return ResponseEntity.ok(new Autorisation(gerer.autoriseUneSeance(centerId, pecId)));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<PecReponse>> listerDuPatient(@PathVariable UUID patientId, @RequestParam UUID centerId) {
        gerer.centre(centerId);
        return ResponseEntity.ok(listes.duPatient(centerId, patientId));
    }

    @DeleteMapping("/{pecId}")
    public ResponseEntity<Suppression> supprimer(@PathVariable UUID pecId, @RequestParam UUID centerId,
                                                 @RequestParam(required = false) UUID patientId) {
        gerer.supprimer(centerId, pecId);
        listes.apresSuppression();
        notifications.pecSupprimee(centerId, pecId, patientId);
        return ResponseEntity.ok(new Suppression(true));
    }

    @PostMapping("/pec-center/search")
    public ResponseEntity<LectureDesPec.Page> rechercher(@RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        Recherche d = CorpsJson.lire(lecteur, requete, corps, Recherche.class);
        if (d.centerId() == null) throw new SaisieInvalide("CenterId cannot be null");
        return ResponseEntity.ok(rechercher.executer(d.centerId(), new CriteresDePec(d.page(), d.size(), d.search(), d.code(),
                d.nom(), d.assurance(), d.debut(), d.fin(), d.statut())));
    }
}
