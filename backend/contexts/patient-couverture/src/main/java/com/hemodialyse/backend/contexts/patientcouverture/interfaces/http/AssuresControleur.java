package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.AssureUseCases;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Assure;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssureId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeAssure;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto.AffecterRequest;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto.ModifierRequest;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patient-couverture/assures")
public class AssuresControleur {
    private final AssureUseCases useCases;

    public AssuresControleur(AssureUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping("/affecter-principal")
    public ResponseEntity<AssureId> affecterPrincipal(@RequestBody AffecterRequest req) {
        AssureId id = useCases.affecterPrincipal(new AssureUseCases.AffecterAssurePrincipalCommande(
                new PatientId(req.patientId()), new CentreId(req.centreId()), req.numeroAssurance(),
                req.nom(), req.prenom(), req.sexe(), req.dateNaissance(), req.telPersonnel(), req.telMobile(),
                req.telBureau(), req.adresse(), req.groupeSanguin(),
                req.typeAssure() == null ? TypeAssure.ASSURE_LUI_MEME : TypeAssure.fromString(req.typeAssure()),
                req.auteurId()), Instant.now());
        return ResponseEntity.ok(id);
    }

    @PutMapping("/{numero}")
    public ResponseEntity<Void> modifier(@PathVariable String numero, @RequestParam UUID centreId, @RequestBody ModifierRequest req) {
        useCases.modifier(new AssureUseCases.ModifierAssureCommande(new AssureId(numero), new CentreId(centreId),
                req.nom(), req.prenom(), req.sexe(), req.dateNaissance(), req.telPersonnel(), req.telMobile(),
                req.telBureau(), req.adresse(), req.groupeSanguin(),
                req.typeAssure() == null ? null : TypeAssure.fromString(req.typeAssure())));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{numero}")
    public ResponseEntity<Assure> consulter(@PathVariable String numero, @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new AssureId(numero), new CentreId(centreId)));
    }

    @GetMapping("/principal")
    public ResponseEntity<Assure> consulterPrincipal(@RequestParam UUID patientId, @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulterPrincipal(new PatientId(patientId), new CentreId(centreId)));
    }
}