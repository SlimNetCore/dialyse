package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDAttestation;
import com.hemodialyse.backend.contexts.patientcouverture.application.GererLesAttestations;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesAttestations;
import com.hemodialyse.backend.contexts.patientcouverture.application.RechercherLesAttestations;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDesAttestations;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
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
 * Adapter entrant HTTP des attestations de droits (slice PC4) : MÊMES URLs que le legacy ({@code /api/v1/pec/attestations**},
 * variables de chemin {@code patientId} et {@code attestationId} comprises : le journal d'audit en dépend) et même JSON. Droits de
 * rôles du legacy INCHANGÉS : aucune restriction par route, tout compte authentifié de centre est admis ; DIRECTION et SUPERADMIN
 * sont refusés plus haut (403 {@code ROLE_SCOPE}). Le corps est lu APRÈS l'autorisation.
 * <p>
 * Écarts décidés (2026-11-04) : garde de centre sur les quatre routes (centre du jeton, 403) ; patient inexistant ou d'un autre
 * centre refusé 422 ; suppression d'un identifiant inconnu 404 {@code ATTESTATION_INTROUVABLE} sans diffusion ; formats
 * illisibles ou manquants 400 ; audit {@code ATTESTATION_CREATION}, {@code ATTESTATION_SUPPRESSION}, {@code
 * ATTESTATION_RECHERCHE} ; recherche : jokers échappés, date illisible 400.
 * <p>
 * ARGENT, reproduit et À DÉCIDER AVEC LES MÉTIERS : aucun contrôle de période entre attestations (début = fin, doublons,
 * chevauchements, échues, futures acceptés), suppression possible sous une PEC validée.
 */
@RestController
@RequestMapping("/api/v1/pec")
public class AttestationsControleur {

    private final GererLesAttestations gerer;
    private final RechercherLesAttestations rechercher;
    private final ListesDAttestationsEnCache listes;
    private final NotificationsDesAttestations notifications;
    private final ObjectMapper lecteur;

    public AttestationsControleur(GererLesAttestations gerer, RechercherLesAttestations rechercher, ListesDAttestationsEnCache listes,
                                  NotificationsDesAttestations notifications, ObjectMapper lecteur) {
        this.gerer = gerer;
        this.rechercher = rechercher;
        this.listes = listes;
        this.notifications = notifications;
        this.lecteur = lecteur;
    }

    // ---- DTO (contrat JSON inchangé)

    record Creation(UUID patientId, UUID centerId, LocalDate dateDebut, LocalDate dateFin) {
    }

    record Recherche(UUID centerId, int page, int size, String search, String code, String nom, String assurance, String debut,
                     String fin) {
    }

    record Creee(UUID id, LocalDate dateDebut, LocalDate dateFin) {
    }

    record Suppression(boolean deleted) {
    }

    /** L'attestation telle que l'entité de domaine d'avant : mêmes champs, mêmes noms (le front les consomme). */
    public record AttestationReponse(UUID id, UUID patientId, UUID centerId, LocalDate dateDebut, LocalDate dateFin,
                                     OffsetDateTime createdAt) {
        static AttestationReponse de(AttestationDroit a) {
            return new AttestationReponse(a.id(), a.patient().valeur(), a.centre().valeur(), a.debut(), a.fin(),
                    a.creeLe() == null ? null : OffsetDateTime.ofInstant(a.creeLe(), ZoneOffset.UTC));
        }
    }

    /**
     * Garde de centre AVANT la lecture complète du corps (comme les droits de rôle : 403 avant 400) : le centre est lu en tête du
     * corps, sans valider le reste. Un appel d'un autre centre (ou d'une session sans centre) reçoit 403 même si le corps est
     * illisible par ailleurs ; un corps dont on ne peut pas lire le centre suit le chemin normal (400 / 415).
     */
    private void garderLeCentre(byte[] corps) {
        UUID demande = null;
        if (corps != null && corps.length > 0) {
            try {
                var centre = lecteur.readTree(corps).path("centerId");
                if (centre.isString()) demande = UUID.fromString(centre.asString());
            } catch (RuntimeException illisible) {
                demande = null;
            }
        }
        gerer.centre(demande);
    }

    // ---- routes

    @PostMapping("/attestations")
    public ResponseEntity<Creee> creer(@RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        garderLeCentre(corps);
        Creation d = CorpsJson.lire(lecteur, requete, corps, Creation.class);
        if (d.centerId() == null) throw new SaisieInvalide("CenterId cannot be null");
        AttestationDroit a = gerer.declarer(d.centerId(), d.patientId(), d.dateDebut(), d.dateFin());
        listes.apresCreation(d.centerId(), a.patient().valeur());
        requete.setAttribute(PecControleur.ATTRIBUT_AUDIT_IDENTIFIANT, a.id().toString());
        notifications.attestationCreee(d.centerId(), a.id(), a.patient().valeur());
        return ResponseEntity.ok(new Creee(a.id(), a.debut(), a.fin()));
    }

    @GetMapping("/attestations/{patientId}")
    public ResponseEntity<List<AttestationReponse>> lister(@PathVariable UUID patientId, @RequestParam UUID centerId) {
        gerer.centre(centerId);
        return ResponseEntity.ok(listes.duPatient(centerId, patientId));
    }

    @DeleteMapping("/attestations/{attestationId}")
    public ResponseEntity<Suppression> supprimer(@PathVariable UUID attestationId, @RequestParam UUID centerId,
                                                 @RequestParam(required = false) UUID patientId) {
        AttestationDroit a = gerer.supprimer(centerId, attestationId, patientId);
        listes.apresSuppression();
        notifications.attestationSupprimee(centerId, attestationId, a.patient().valeur());
        return ResponseEntity.ok(new Suppression(true));
    }

    @PostMapping("/attestations-center/search")
    public ResponseEntity<LectureDesAttestations.Page> rechercher(@RequestBody(required = false) byte[] corps,
                                                                  HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        garderLeCentre(corps);
        Recherche d = CorpsJson.lire(lecteur, requete, corps, Recherche.class);
        if (d.centerId() == null) throw new SaisieInvalide("CenterId cannot be null");
        return ResponseEntity.ok(rechercher.executer(d.centerId(), CriteresDAttestation.de(d.page(), d.size(), d.search(),
                d.code(), d.nom(), d.assurance(), d.debut(), d.fin())));
    }
}
