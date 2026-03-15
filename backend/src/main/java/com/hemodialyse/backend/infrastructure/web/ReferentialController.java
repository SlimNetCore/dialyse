package com.hemodialyse.backend.infrastructure.web;

import com.hemodialyse.backend.domain.referential.*;
import com.hemodialyse.backend.domain.referential.ReferentialRepositories.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/referentials")
public class ReferentialController {

    private final CentrePayeurRepository centrePayeurRepo;
    private final AgenceRepository agenceRepo;
    private final CaisseAssuranceRepository caisseRepo;
    private final MedecinRepository medecinRepo;
    private final SalleRepository salleRepo;
    private final PositionCreneauRepository positionRepo;
    private final TransporteurRepository transporteurRepo;
    private final ReferentialRepositories.CategorieTransportRepository categorieTransportRepo;

    public ReferentialController(
        CentrePayeurRepository centrePayeurRepo,
        AgenceRepository agenceRepo,
        CaisseAssuranceRepository caisseRepo,
        MedecinRepository medecinRepo,
        SalleRepository salleRepo,
        PositionCreneauRepository positionRepo,
        TransporteurRepository transporteurRepo,
        ReferentialRepositories.CategorieTransportRepository categorieTransportRepo
    ) {
        this.centrePayeurRepo = centrePayeurRepo;
        this.agenceRepo = agenceRepo;
        this.caisseRepo = caisseRepo;
        this.medecinRepo = medecinRepo;
        this.salleRepo = salleRepo;
        this.positionRepo = positionRepo;
        this.transporteurRepo = transporteurRepo;
        this.categorieTransportRepo = categorieTransportRepo;
    }

    @GetMapping("/centres-payeurs")
    public ResponseEntity<List<CentrePayeur>> getCentresPayeurs(@RequestParam UUID centerId) {
        return ResponseEntity.ok(centrePayeurRepo.findByCenterId(centerId));
    }

    @GetMapping("/agences")
    public ResponseEntity<List<Agence>> getAgences(@RequestParam UUID centerId) {
        return ResponseEntity.ok(agenceRepo.findByCenterId(centerId));
    }

    @GetMapping("/caisses")
    public ResponseEntity<List<CaisseAssurance>> getCaisses(@RequestParam UUID centerId) {
        return ResponseEntity.ok(caisseRepo.findByCenterId(centerId));
    }

    @GetMapping("/medecins")
    public ResponseEntity<List<Medecin>> getMedecins(@RequestParam UUID centerId) {
        return ResponseEntity.ok(medecinRepo.findByCenterId(centerId));
    }

    @GetMapping("/salles")
    public ResponseEntity<List<Salle>> getSalles(@RequestParam UUID centerId) {
        return ResponseEntity.ok(salleRepo.findByCenterId(centerId));
    }

    @GetMapping("/positions")
    public ResponseEntity<List<PositionCreneau>> getPositions(@RequestParam UUID centerId) {
        return ResponseEntity.ok(positionRepo.findByCenterId(centerId));
    }

    @GetMapping("/transporteurs")
    public ResponseEntity<List<Transporteur>> getTransporteurs(@RequestParam UUID centerId) {
        return ResponseEntity.ok(transporteurRepo.findByCenterId(centerId));
    }

    @GetMapping("/categories-transport")
    public ResponseEntity<List<CategorieTransport>> getCategoriesTransport(@RequestParam UUID centerId) {
        return ResponseEntity.ok(categorieTransportRepo.findByCenterId(centerId));
    }
}

