package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.ConsulterUneSeance;
import com.hemodialyse.backend.contexts.seance.application.CreerUneSeance;
import com.hemodialyse.backend.contexts.seance.application.FicheDuPatient;
import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public SeancesControleur(CreerUneSeance creer, ScannerUnPatient scanner, ConsulterUneSeance consulter, ListerLesSeances lister,
                             ValiderUneSeance valider, SignerUneSeance signer, DeverrouillerUneSeance deverrouiller) {
        this.creer = creer;
        this.scanner = scanner;
        this.consulter = consulter;
        this.lister = lister;
        this.valider = valider;
        this.signer = signer;
        this.deverrouiller = deverrouiller;
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
