package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.AdmettreUnPatient;
import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLEffectif;
import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDeRecherche;
import com.hemodialyse.backend.contexts.patientcouverture.application.GererLesAssures;
import com.hemodialyse.backend.contexts.patientcouverture.application.ModifierLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.PageDePatients;
import com.hemodialyse.backend.contexts.patientcouverture.application.PatientEnregistre;
import com.hemodialyse.backend.contexts.patientcouverture.application.RechercherLesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.application.ResumeDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.application.SaisieDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP de la fiche du patient (slice PC1) : MÊMES URLs que le legacy ({@code /api/v1/patients/**}, noms des
 * variables de chemin compris : le journal d'audit en dépend) et même JSON. Droits du legacy inchangés : lecture ouverte à tout
 * utilisateur authentifié ; création et modification de la fiche : ADMIN, SECRETAIRE, INFIRMIER ; écritures d'assuré : tout sauf
 * le médecin « seul ». Le corps est lu APRÈS l'autorisation (403 avant 400). Garde de centre sur toute lecture et toute écriture
 * (la seule restriction nouvelle : une session sans centre est refusée).
 * <p>
 * Écarts décidés (02/11/2026) : entrées illisibles ou manquantes 400 {@code BAD_REQUEST} (KB-PAT-01, 02), patient inconnu 404
 * (KB-PAT-18), garde de centre des lectures et des écritures d'assuré (KB-PAT-06, 07), états en liste fermée 422 (KB-PAT-04),
 * unicité du numéro d'assurance aussi à la modification (KB-PAT-03), dates incohérentes 422 (KB-PAT-08), réponse de la fiche par
 * DTO (KB-PAT-12), taille de page de 1 à 100 (KB-PAT-13).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientsControleur {

    /**
     * Droits du LEGACY restaurés à l'identique (décision du 2026-11-03, revue M1) : les lectures n'ont aucun
     * {@code @PreAuthorize} (tout utilisateur authentifié, avec la garde de centre) ; la création et la modification de la fiche
     * sont en liste blanche de rôles ; les écritures d'assuré refusent le médecin « seul » mais laissent passer les autres
     * profils, rôles personnalisés compris (PHARMACIEN, DIRECTEUR_ADMINISTRATIF…).
     */
    private static final String ECRITURE = "hasAnyRole('ADMIN','SECRETAIRE','INFIRMIER')";
    private static final String ECRITURE_ASSURE = "!hasRole('MEDECIN') or hasAnyRole('ADMIN','SECRETAIRE','INFIRMIER')";
    private static final String CACHES = "patient.list.summary";

    private final AdmettreUnPatient admettre;
    private final ModifierLaFiche modifier;
    private final ConsulterLaFiche consulter;
    private final RechercherLesPatients rechercher;
    private final ConsulterLEffectif effectif;
    private final ResumesEnCache resumes;
    private final GererLesAssures assures;
    private final NotificationsDeLaFiche notifications;
    private final ObjectMapper lecteur;

    @SuppressWarnings("java:S107")
    public PatientsControleur(AdmettreUnPatient admettre, ModifierLaFiche modifier, ConsulterLaFiche consulter,
                              RechercherLesPatients rechercher, ConsulterLEffectif effectif, ResumesEnCache resumes,
                              GererLesAssures assures, NotificationsDeLaFiche notifications, ObjectMapper lecteur) {
        this.admettre = admettre;
        this.modifier = modifier;
        this.consulter = consulter;
        this.rechercher = rechercher;
        this.effectif = effectif;
        this.resumes = resumes;
        this.assures = assures;
        this.notifications = notifications;
        this.lecteur = lecteur;
    }

    // ---- fiche

    record Enregistre(UUID id, UUID centerId, String typePatient, String codePatient) {
        static Enregistre de(PatientEnregistre p) {
            return new Enregistre(p.id().valeur(), p.centre().valeur(), p.vacancier() ? "VACANCIER" : "NON_VACANCIER", p.code());
        }
    }

    /**
     * La fiche est enregistrée mais sa demande de PEC est refusée (identifiant déjà pris) : 409 avec le code stable du refus et
     * l'identité de la fiche enregistrée (décision du 2026-11-03, KB-PAT-35 : plus de 500 qui annule la fiche).
     */
    private static ResponseEntity<?> reponse(PatientEnregistre p) {
        if (p.refusDeLaPec() == null) return ResponseEntity.ok(Enregistre.de(p));
        org.springframework.http.ProblemDetail probleme = org.springframework.http.ProblemDetail
                .forStatus(org.springframework.http.HttpStatus.CONFLICT);
        probleme.setTitle("Demande de prise en charge refusee");
        probleme.setDetail("La fiche est enregistrée mais la prise en charge n'a pas été créée : identifiant déjà utilisé");
        probleme.setProperty("code", p.refusDeLaPec());
        probleme.setProperty("id", p.id().valeur());
        probleme.setProperty("centerId", p.centre().valeur());
        probleme.setProperty("codePatient", p.code());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(probleme);
    }

    @PostMapping
    @CacheEvict(cacheNames = {"patient.list.summary", "patient.list.summary.details", "patient.pec.byPatient", "patient.pec.byCenter"},
            allEntries = true)
    @PreAuthorize(ECRITURE)
    public ResponseEntity<?> admettre(@RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        FicheDemande demande = CorpsJson.lire(lecteur, requete, corps, FicheDemande.class);
        SaisieDeLaFiche saisie = demande.enSaisie();
        PatientEnregistre admis = admettre.executer(saisie);
        notifications.ficheCreee(admis.centre().valeur(), admis.id().valeur(), admis.code(), demande.nom(), demande.prenom());
        return reponse(admis);
    }

    @PutMapping("/{id}")
    @CacheEvict(cacheNames = {"patient.list.summary", "patient.list.summary.details", "patient.pec.byPatient", "patient.pec.byCenter"},
            allEntries = true)
    @PreAuthorize(ECRITURE)
    public ResponseEntity<?> modifier(@PathVariable UUID id, @RequestBody(required = false) byte[] corps,
                                               HttpServletRequest requete) throws HttpMediaTypeNotSupportedException {
        FicheDemande demande = CorpsJson.lire(lecteur, requete, corps, FicheDemande.class);
        SaisieDeLaFiche saisie = demande.enSaisie();
        PatientEnregistre modifie = modifier.executer(id, saisie);
        notifications.ficheModifiee(modifie.centre().valeur(), modifie.id().valeur(), modifie.code(), demande.nom(),
                demande.prenom());
        return reponse(modifie);
    }

    @SuppressWarnings("unused")
    @GetMapping("/{id}")
    public ResponseEntity<FicheReponse> fiche(@PathVariable UUID id, @RequestParam UUID centerId,
                                              @RequestParam(required = false) String userId) {
        return ResponseEntity.ok(FicheReponse.depuis(consulter.executer(centerId, id)));
    }

    // ---- recherche et synthèses

    record RechercheDemande(UUID centerId, int page, int size, String code, String nom, String prenom, String sexe,
                            LocalDate dateAdmissionFrom, LocalDate dateAdmissionTo, String numeroAssurance, String etatPatient,
                            Boolean nonFacturable, String medecinTraitantId, String positionId, String transporteurAllerId,
                            String transporteurRetourId, String pecForfaitId, String sortBy, String sortDirection) {
    }

    @PostMapping("/search")
    public ResponseEntity<PageDePatients> rechercher(@RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        RechercheDemande d = CorpsJson.lire(lecteur, requete, corps, RechercheDemande.class);
        if (d.centerId() == null) throw new SaisieInvalide("CenterId cannot be null");
        PageDePatients page = rechercher.executer(d.centerId(), new CriteresDeRecherche(d.page(), d.size(), d.code(), d.nom(),
                d.prenom(), d.sexe(), d.dateAdmissionFrom(), d.dateAdmissionTo(), d.numeroAssurance(), d.etatPatient(),
                d.nonFacturable(), d.medecinTraitantId(), d.positionId(), d.transporteurAllerId(), d.transporteurRetourId(),
                d.pecForfaitId(), d.sortBy(), d.sortDirection()));
        return ResponseEntity.ok(page);
    }

    @GetMapping("/summary")
    public ResponseEntity<ResumeDesPatients> resume(@RequestParam UUID centerId, @RequestParam(required = false) String month) {
        YearMonth mois = effectif.mois(centerId, lireLeMois(month));
        return ResponseEntity.ok(resumes.resume(centerId, mois));
    }

    @GetMapping("/summary/details")
    public ResponseEntity<ResumeDesPatients.Detail> detail(@RequestParam UUID centerId,
                                                           @RequestParam(required = false) String month) {
        YearMonth mois = effectif.mois(centerId, lireLeMois(month));
        return ResponseEntity.ok(resumes.detail(centerId, mois));
    }

    private static YearMonth lireLeMois(String month) {
        if (month == null || month.isBlank()) return null;
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new SaisieInvalide("Paramètre illisible : month");
        }
    }

    // ---- assurés et affectations

    record AssureReponse(String numeroAssurance, String nom, String prenom, String sexe, LocalDate dateNaissance,
                         String telPersonnel, String telMobile, String telBureau, String adresse, String groupeSanguin) {
        static AssureReponse de(AssuresDuCentre.Assure a) {
            return new AssureReponse(a.numeroAssurance(), a.nom(), a.prenom(), a.sexe(), a.dateNaissance(), a.telPersonnel(),
                    a.telMobile(), a.telBureau(), a.adresse(), a.groupeSanguin());
        }
    }

    record HistoriqueReponse(UUID id, UUID patientId, String numeroAssurance, boolean isPrimary, boolean actif,
                             OffsetDateTime dateAffectation, LocalDate dateDebutAffectation, LocalDate dateFinAffectation,
                             String nom, String prenom, String sexe) {
    }

    record AffecteReponse(boolean assigned, UUID id, String numeroAssurance, LocalDate dateDebutAffectation, String nom,
                          String prenom) {
    }

    record AffectationModifiee(boolean updated, UUID id) {
    }

    record AffectationDemande(LocalDate dateDebutAffectation, LocalDate dateFinAffectation) {
    }

    record AssureDemande(String nom, String prenom, String sexe, String dateNaissance, String telPersonnel, String telMobile,
                         String telBureau, String adresse, String groupeSanguin) {
    }

    @GetMapping("/{id}/assures/history")
    public ResponseEntity<List<HistoriqueReponse>> historiqueDesAssures(@PathVariable UUID id, @RequestParam UUID centerId) {
        var lignes = assures.historique(centerId, id).stream().map(l -> {
            var a = l.affectation();
            var assure = l.assure();
            return new HistoriqueReponse(a.id(), id, a.numeroAssurance(), a.principal(), a.actif(), a.dateAffectation(),
                    a.dateDebut(), a.dateFin(), assure == null ? null : assure.nom(), assure == null ? null : assure.prenom(),
                    assure == null ? null : assure.sexe());
        }).toList();
        return ResponseEntity.ok(lignes);
    }

    @GetMapping("/assures")
    public ResponseEntity<List<AssureReponse>> listerLesAssures(@RequestParam UUID centerId,
                                                                @RequestParam(required = false) String q) {
        return ResponseEntity.ok(assures.lister(centerId, q).stream().map(AssureReponse::de).toList());
    }

    @PostMapping("/{id}/assures/{numeroAssurance}/affecter")
    @PreAuthorize(ECRITURE_ASSURE)
    public ResponseEntity<AffecteReponse> affecter(@PathVariable UUID id, @PathVariable String numeroAssurance,
                                                   @RequestParam UUID centerId) {
        var affecte = assures.affecter(centerId, id, numeroAssurance);
        var a = affecte.assure();
        return ResponseEntity.ok(new AffecteReponse(true, affecte.id(), numeroAssurance, affecte.dateDebut(),
                a.nom() == null ? "" : a.nom(), a.prenom() == null ? "" : a.prenom()));
    }

    @PutMapping("/{patientId}/assures/assignments/{assignmentId}")
    @PreAuthorize(ECRITURE_ASSURE)
    public ResponseEntity<AffectationModifiee> modifierLAffectation(@PathVariable UUID patientId,
                                                                    @PathVariable UUID assignmentId,
                                                                    @RequestParam UUID centerId,
                                                                    @RequestBody(required = false) byte[] corps,
                                                                    HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        AffectationDemande d = CorpsJson.lire(lecteur, requete, corps, AffectationDemande.class);
        assures.modifierLAffectation(centerId, patientId, assignmentId, d.dateDebutAffectation(), d.dateFinAffectation());
        return ResponseEntity.ok(new AffectationModifiee(true, assignmentId));
    }

    @PutMapping("/assures/{numeroAssurance}")
    @PreAuthorize(ECRITURE_ASSURE)
    public ResponseEntity<AssureReponse> modifierUnAssure(@PathVariable String numeroAssurance, @RequestParam UUID centerId,
                                                          @RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        AssureDemande d = CorpsJson.lire(lecteur, requete, corps, AssureDemande.class);
        LocalDate naissance = null;
        if (d.dateNaissance() != null && !d.dateNaissance().isBlank()) {
            try {
                naissance = LocalDate.parse(d.dateNaissance().substring(0, Math.min(10, d.dateNaissance().length())));
            } catch (DateTimeParseException e) {
                throw new SaisieInvalide("Date de naissance illisible");
            }
        }
        var modifie = assures.modifierUnAssure(centerId, numeroAssurance, new AssuresDuCentre.Assure(numeroAssurance, d.nom(),
                d.prenom(), d.sexe(), naissance, d.telPersonnel(), d.telMobile(), d.telBureau(), d.adresse(),
                d.groupeSanguin(), null));
        return ResponseEntity.ok(AssureReponse.de(modifie));
    }
}
