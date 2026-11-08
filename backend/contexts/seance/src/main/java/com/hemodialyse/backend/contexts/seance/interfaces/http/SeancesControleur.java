package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.AjouterUnConsommable;
import com.hemodialyse.backend.contexts.seance.application.ChangerLeJourDUneSeance;
import com.hemodialyse.backend.contexts.seance.application.SupprimerUneSeance;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.ChangementDeJourDemande;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.SuppressionDeSeanceDemande;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLesRaccourcis;
import com.hemodialyse.backend.contexts.seance.application.ModifierLaQuantiteDUnConsommable;
import com.hemodialyse.backend.contexts.seance.application.RemplacerLesRaccourcis;
import com.hemodialyse.backend.contexts.seance.application.RetirerUnConsommable;
import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.AjoutDeConsommableDemande;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.ModificationDeConsommableDemande;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.RaccourcisDemande;
import com.hemodialyse.backend.contexts.seance.application.ConsulterUneSeance;
import com.hemodialyse.backend.contexts.seance.application.CreerUneSeance;
import com.hemodialyse.backend.contexts.seance.application.FicheDuPatient;
import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance;
import com.hemodialyse.backend.contexts.seance.application.ImposerUnForfait;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.ImpositionDeForfaitDemande;
import com.hemodialyse.backend.contexts.seance.application.DeverrouillerUneSeance;
import com.hemodialyse.backend.contexts.seance.application.ListerLesSeances;
import com.hemodialyse.backend.contexts.seance.application.ScannerUnPatient;
import com.hemodialyse.backend.contexts.seance.application.SignerUneSeance;
import com.hemodialyse.backend.contexts.seance.application.ValiderUneSeance;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.CreationDeSeanceDemande;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.ScanDeSeanceDemande;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.SignatureDeSeanceDemande;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.ValidationDeSeanceDemande;
import jakarta.validation.Valid;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter entrant HTTP de la création, du scan, de la consultation et de la liste des séances (SE1, ADR 0088) : MÊMES URL, MÊMES noms de
 * champs JSON et MÊMES droits que le legacy (création ADMIN et INFIRMIER ; scan ADMIN, INFIRMIER et SECRETAIRE ; détail et liste tous les
 * profils cliniques et administratifs). Les écarts décidés (KB-SEA-01, 02, 04, 06, 07) sont ceux de l'ADR 0088.
 * <p>
 * Routé depuis B2 : les quatre handlers du legacy sont retirés de {@code SeanceRestController} (refactoring sur place, ADR 0024 :
 * aucune façade ni drapeau, la bascule est réversible par retour git).
 * <p>
 * SE3 (ADR 0090) : l'ajout, la modification et le retrait d'un consommable (ADMIN et INFIRMIER) et les raccourcis de consommables (lecture
 * ADMIN, INFIRMIER et SECRETAIRE ; remplacement ADMIN seul), mêmes URL, mêmes champs JSON et mêmes droits que le legacy. Aucune
 * contrainte de bean sur les corps : toute valeur absente ou illisible est un 400 {@code BAD_REQUEST}, 403 avant 400 (KB-SEA-26).
 * <p>
 * SE5 (ADR 0092) : l'imposition d'un forfait à une séance (ADMIN seul), mêmes URL, mêmes champs JSON et mêmes droits que le legacy ; les
 * contraintes de bean du corps (champs absents ou vides : 400 {@code VALIDATION_ERROR}) sont conservées.
 * <p>
 * SE6 (ADR 0093) : le changement de jour (PUT {@code /{id}}) et la suppression (DELETE {@code /{id}}), ADMIN seul, mêmes URL, mêmes champs
 * JSON et mêmes droits que le legacy.
 */
@RestController
@RequestMapping("/api/v1/seances")
public class SeancesControleur {

    private final CreerUneSeance creer;
    private final ScannerUnPatient scanner;
    private final ConsulterUneSeance consulter;
    private final ListerLesSeances lister;
    private final ValiderUneSeance valider;
    private final SignerUneSeance signer;
    private final DeverrouillerUneSeance deverrouiller;
    private final AjouterUnConsommable ajouterUnConsommable;
    private final ModifierLaQuantiteDUnConsommable modifierUnConsommable;
    private final RetirerUnConsommable retirerUnConsommable;
    private final ConsulterLesRaccourcis consulterLesRaccourcis;
    private final RemplacerLesRaccourcis remplacerLesRaccourcis;
    private final ImposerUnForfait imposerUnForfait;
    private final ChangerLeJourDUneSeance changerLeJour;
    private final SupprimerUneSeance supprimer;

    public SeancesControleur(CreerUneSeance creer, ScannerUnPatient scanner, ConsulterUneSeance consulter, ListerLesSeances lister,
                             ValiderUneSeance valider, SignerUneSeance signer, DeverrouillerUneSeance deverrouiller,
                             AjouterUnConsommable ajouterUnConsommable, ModifierLaQuantiteDUnConsommable modifierUnConsommable,
                             RetirerUnConsommable retirerUnConsommable, ConsulterLesRaccourcis consulterLesRaccourcis,
                             RemplacerLesRaccourcis remplacerLesRaccourcis, ImposerUnForfait imposerUnForfait,
                             ChangerLeJourDUneSeance changerLeJour, SupprimerUneSeance supprimer) {
        this.changerLeJour = changerLeJour;
        this.supprimer = supprimer;
        this.imposerUnForfait = imposerUnForfait;
        this.creer = creer;
        this.scanner = scanner;
        this.consulter = consulter;
        this.lister = lister;
        this.valider = valider;
        this.signer = signer;
        this.deverrouiller = deverrouiller;
        this.ajouterUnConsommable = ajouterUnConsommable;
        this.modifierUnConsommable = modifierUnConsommable;
        this.retirerUnConsommable = retirerUnConsommable;
        this.consulterLesRaccourcis = consulterLesRaccourcis;
        this.remplacerLesRaccourcis = remplacerLesRaccourcis;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @PostMapping("/{seanceId}/consommables")
    public ResponseEntity<Map<String, Object>> ajouterUnConsommable(@PathVariable UUID seanceId,
                                                                    @RequestBody AjoutDeConsommableDemande demande) {
        ajouterUnConsommable.executer(demande.centerId(), seanceId, demande.articleId(), demande.quantite());
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("added", true);
        reponse.put("articleId", demande.articleId());
        reponse.put("quantite", demande.quantite());
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @PutMapping("/{seanceId}/consommables/{articleId}")
    public ResponseEntity<Map<String, Object>> modifierLaQuantiteDUnConsommable(@PathVariable UUID seanceId, @PathVariable UUID articleId,
                                                                                @RequestBody ModificationDeConsommableDemande demande) {
        modifierUnConsommable.executer(demande.centerId(), seanceId, articleId, demande.quantite(), demande.userId());
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("updated", true);
        reponse.put("articleId", articleId);
        reponse.put("quantite", demande.quantite());
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @DeleteMapping("/{seanceId}/consommables/{articleId}")
    public ResponseEntity<Map<String, Object>> retirerUnConsommable(@PathVariable UUID seanceId, @PathVariable UUID articleId,
                                                                    @RequestParam UUID centerId,
                                                                    @RequestParam(defaultValue = "system") String userId) {
        retirerUnConsommable.executer(centerId, seanceId, articleId, userId);
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("removed", true);
        reponse.put("articleId", articleId);
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','SECRETAIRE')")
    @GetMapping("/raccourcis-consommables")
    public List<UUID> raccourcis(@RequestParam(required = false) UUID centerId) {
        return consulterLesRaccourcis.executer(centerId).articles().stream().map(ArticleId::valeur).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/raccourcis-consommables")
    public List<UUID> remplacerLesRaccourcis(@RequestParam(required = false) UUID centerId, @RequestBody RaccourcisDemande demande) {
        return remplacerLesRaccourcis.executer(centerId, demande.articleIds()).articles().stream().map(ArticleId::valeur).toList();
    }

    /**
     * SE5 (ADR 0092) : impose un forfait du référentiel à une séance (administrateur seul, KB-SEA-48 reproduit). Réponse du legacy :
     * identifiant, statut, jour et forfait courant (celui qui vient d'être imposé, sept clés).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{seanceId}/forfait")
    public ResponseEntity<Map<String, Object>> imposerUnForfait(@PathVariable UUID seanceId,
                                                                @RequestBody @Valid ImpositionDeForfaitDemande demande) {
        var resultat = imposerUnForfait.executer(demande.centerId(), seanceId, demande.forfaitId(), demande.userId());
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("id", resultat.seance().id().valeur());
        reponse.put("status", resultat.seance().statut());
        reponse.put("dateSeance", resultat.seance().jour());
        reponse.put("forfait", forfaitEnJson(resultat.forfait()));
        return ResponseEntity.ok(reponse);
    }

    /**
     * SE6 (ADR 0093) : change le jour d'une séance (administrateur seul). Réponse du legacy : identifiant, statut et jour. N'écrit que le
     * jour (KB-SEA-49, corrigé : la facture rattachée n'est plus effacée) ; aucune règle de calendrier ni de verrou des jours passés
     * (KB-SEA-54 à 56, reproduits : alertes de l'ADR).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{seanceId}")
    public ResponseEntity<Map<String, Object>> changerLeJour(@PathVariable UUID seanceId,
                                                             @RequestBody @Valid ChangementDeJourDemande demande) {
        Seance seance = changerLeJour.executer(demande.centerId(), seanceId, demande::jour);
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("id", seance.id().valeur());
        reponse.put("status", seance.statut());
        reponse.put("dateSeance", seance.jour());
        return ResponseEntity.ok(reponse);
    }

    /**
     * SE6 (ADR 0093) : supprime une séance (administrateur seul), corps {@code {"motif"}}, 204 sans corps. Le centre est obligatoire
     * (KB-SEA-52, corrigé : le legacy prenait celui de la session s'il manquait).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{seanceId}")
    public ResponseEntity<Void> supprimer(@RequestParam(required = false) UUID centerId, @PathVariable UUID seanceId,
                                          @RequestBody SuppressionDeSeanceDemande demande) {
        if (centerId == null) throw new SaisieInvalide("Centre requis");
        supprimer.executer(centerId, seanceId, demande.motif());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @PostMapping
    public ResponseEntity<Map<String, Object>> creer(@RequestBody CreationDeSeanceDemande demande) {
        // Le corps doit nommer le centre (legacy : 400 « CenterId cannot be null », reproduit sans nom de classe)
        if (demande.centerId() == null) throw new SaisieInvalide("Centre requis");
        var enregistree = creer.executer(demande.centerId(), demande.patientId(), demande.dateSeance());
        return ResponseEntity.ok(reponseDeCreation(enregistree.seance(), enregistree.patient()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','SECRETAIRE')")
    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> scanner(@RequestBody @Valid ScanDeSeanceDemande demande) {
        var resultat = scanner.executer(demande.centerId(), demande.qrCode(), demande.motifHorsPlanning(), demande.precisionHorsPlanning());
        Map<String, Object> reponse = reponseDeCreation(resultat.seance(), resultat.patient());
        reponse.put("patientNom", resultat.patient().nom());
        reponse.put("patientPrenom", resultat.patient().prenom());
        reponse.put("patientCode", resultat.patient().code());
        reponse.put("created", resultat.creee());
        reponse.put("validatedNow", resultat.validee());
        reponse.put("alreadyValidated", resultat.dejaValidee());
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @PostMapping("/{seanceId}/valider")
    public ResponseEntity<Map<String, Object>> valider(@PathVariable UUID seanceId, @RequestBody ValidationDeSeanceDemande demande) {
        if (demande.centerId() == null) throw new SaisieInvalide("Centre requis");
        List<ValiderUneSeance.Ligne> lignes = demande.consommations() == null ? List.of() : demande.consommations().stream()
                .map(c -> c == null ? null : new ValiderUneSeance.Ligne(c.articleId(), c.quantite())).toList();
        Seance seance = valider.executer(demande.centerId(), seanceId, demande.userId(), lignes).seance();
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("id", seance.id().valeur());
        reponse.put("status", seance.statut());
        reponse.put("validatedAt", seance.jalons().valideeLe());
        reponse.put("signedByInfirmierAt", seance.jalons().signeeParLInfirmierLe());
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PostMapping("/{seanceId}/signer-medecin")
    public ResponseEntity<Map<String, Object>> signer(@PathVariable UUID seanceId, @RequestBody SignatureDeSeanceDemande demande) {
        if (demande.centerId() == null) throw new SaisieInvalide("Centre requis");
        Seance seance = signer.executer(demande.centerId(), seanceId, demande.userId());
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("id", seance.id().valeur());
        reponse.put("status", seance.statut());
        reponse.put("signedByMedecinAt", seance.jalons().signeeParLeMedecinLe());
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{seanceId}/deverrouiller-regularisation")
    public ResponseEntity<Map<String, Object>> deverrouiller(@PathVariable UUID seanceId, @RequestParam UUID centerId) {
        Seance seance = deverrouiller.executer(centerId, seanceId);
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("id", seance.id().valeur());
        reponse.put("status", seance.statut());
        reponse.put("regularisationDeverrouilleeAt", seance.jalons().deverrouilleePourRegularisationLe());
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','SECRETAIRE','MEDECIN')")
    @GetMapping("/{seanceId}")
    public ResponseEntity<Map<String, Object>> consulter(@PathVariable UUID seanceId, @RequestParam UUID centerId) {
        var detail = consulter.executer(centerId, seanceId);
        Seance seance = detail.seance();
        FicheDuPatient patient = detail.patient();

        Map<String, Object> seanceJson = new LinkedHashMap<>();
        seanceJson.put("id", seance.id().valeur());
        seanceJson.put("centerId", seance.centre().valeur());
        seanceJson.put("patientId", seance.patient().valeur());
        seanceJson.put("dateSeance", seance.jour());
        seanceJson.put("status", seance.statut());
        seanceJson.put("horsPlanning", seance.horsPlanning());
        seanceJson.put("motifHorsPlanning", seance.derogation().map(d -> d.motif()).orElse(null));
        seanceJson.put("precisionHorsPlanning", seance.derogation().map(d -> d.precision()).orElse(null));
        seanceJson.put("createdAt", seance.creeLe());
        seanceJson.put("validatedAt", seance.jalons().valideeLe());
        seanceJson.put("signedByInfirmierAt", seance.jalons().signeeParLInfirmierLe());
        seanceJson.put("signedByMedecinAt", seance.jalons().signeeParLeMedecinLe());

        Map<String, Object> patientJson = new LinkedHashMap<>();
        patientJson.put("id", patient.id().valeur());
        patientJson.put("codePatient", patient.code());
        patientJson.put("nom", patient.nom());
        patientJson.put("prenom", patient.prenom());
        patientJson.put("sexe", patient.sexe());
        patientJson.put("dateNaissance", patient.dateDeNaissance());
        patientJson.put("numeroAssurance", patient.numeroDAssurance());
        patientJson.put("groupeSanguin", patient.groupeSanguin());
        patientJson.put("telMobile", patient.telephoneMobile());
        patientJson.put("medecinTraitantId", patient.medecinTraitant());
        patientJson.put("salleId", patient.salle());
        patientJson.put("positionId", patient.position());
        patientJson.put("generateurId", patient.generateur());
        patientJson.put("generateurNom", patient.generateurNom());
        patientJson.put("generateurMarque", patient.generateurMarque());
        patientJson.put("generateurEtat", patient.generateurEtat());

        Map<String, Object> paramedical = detail.paramedical().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("poidsAvantKg", v.poidsAvantKg());
            m.put("poidsApresKg", v.poidsApresKg());
            m.put("taAvant", v.tensionAvant());
            m.put("taApres", v.tensionApres());
            m.put("dureeMinutes", v.dureeMinutes());
            m.put("debitSangMlMin", v.debitSangMlMin());
            m.put("ultrafiltrationMl", v.ultrafiltrationMl());
            m.put("anticoagulant", v.anticoagulant());
            m.put("typeDialysat", v.typeDeDialysat());
            m.put("incidents", v.incidents());
            return m;
        }).orElse(null);

        Map<String, Object> medical = detail.medical().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("prescription", v.prescription());
            m.put("toleranceSeance", v.toleranceDeLaSeance());
            m.put("examenClinique", v.examenClinique());
            m.put("resultatsBiologiques", v.resultatsBiologiques());
            m.put("ajustementsTherapeutiques", v.ajustementsTherapeutiques());
            m.put("conclusionMedicale", v.conclusionMedicale());
            return m;
        }).orElse(null);

        List<Map<String, Object>> consommables = new ArrayList<>();
        for (var ligne : detail.consommables()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("articleId", ligne.article());
            m.put("articleCode", ligne.code());
            m.put("articleLibelle", ligne.libelle());
            m.put("articleUnite", ligne.unite());
            m.put("quantite", ligne.quantite());
            m.put("valeurUnitaire", ligne.valeurUnitaire());
            m.put("totalValorise", ligne.totalValorise());
            consommables.add(m);
        }

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("consommables", consommables);
        reponse.put("consommablesTotalValorise", detail.totalValoriseDesConsommables());
        reponse.put("seance", seanceJson);
        reponse.put("patient", patientJson);
        reponse.put("paramedical", paramedical);
        reponse.put("medical", medical);
        reponse.put("forfait", forfaitEnJson(detail.forfait()));
        return ResponseEntity.ok(reponse);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping
    public ResponseEntity<Map<String, Object>> lister(
            @RequestParam UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Boolean deverrouillee) {
        var resultat = lister.executer(new ListerLesSeances.Demande(centerId, from, to, status, q, sortBy, sortDir, deverrouillee, page, size));
        List<Map<String, Object>> items = resultat.items().stream().map(entree -> {
            var l = entree.ligne();
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("id", l.id().valeur());
            ligne.put("centerId", l.centre().valeur());
            ligne.put("patientId", l.patient().valeur());
            ligne.put("patientCode", l.codePatient());
            ligne.put("patientNom", l.nomDuPatient());
            ligne.put("patientPrenom", l.prenomDuPatient());
            ligne.put("dateSeance", l.jour());
            ligne.put("status", l.statut());
            ligne.put("createdAt", l.creeLe());
            ligne.put("validatedAt", l.valideeLe());
            ligne.put("signedByInfirmierAt", l.signeeParLInfirmierLe());
            ligne.put("signedByMedecinAt", l.signeeParLeMedecinLe());
            ligne.put("regularisationDeverrouilleeAt", l.deverrouilleeLe());
            ligne.put("horsPlanning", l.horsPlanning());
            ligne.put("motifHorsPlanning", l.motifHorsPlanning());
            ligne.put("forfait", forfaitEnJson(entree.forfait()));
            return ligne;
        }).toList();
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("items", items);
        reponse.put("total", resultat.total());
        reponse.put("page", resultat.page());
        reponse.put("size", resultat.size());
        return ResponseEntity.ok(reponse);
    }

    private static Map<String, Object> reponseDeCreation(Seance seance, FicheDuPatient patient) {
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("id", seance.id().valeur());
        reponse.put("status", seance.statut());
        reponse.put("dateSeance", seance.jour());
        reponse.put("horsPlanning", seance.horsPlanning());
        reponse.put("motifHorsPlanning", seance.derogation().map(d -> d.motif()).orElse(null));
        reponse.put("generateurId", patient.generateur());
        reponse.put("generateurNom", patient.generateurNom());
        reponse.put("generateurMarque", patient.generateurMarque());
        reponse.put("generateurEtat", patient.generateurEtat());
        return reponse;
    }

    /** Forfait de la prise en charge : cinq clés ; forfait imposé sur la séance : sept (date et auteur de la mise à jour). Aucun : nul. */
    private static Map<String, Object> forfaitEnJson(Optional<ForfaitsDeSeance.ForfaitCourant> forfait) {
        return forfait.map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.id());
            m.put("code", f.code());
            m.put("nom", f.nom());
            m.put("prix", f.prix());
            m.put("nombreSeances", f.nombreDeSeances());
            if (f.surcharge()) {
                m.put("updatedAt", f.misAJourLe());
                m.put("updatedBy", f.misAJourPar());
            }
            return m;
        }).orElse(null);
    }
}
