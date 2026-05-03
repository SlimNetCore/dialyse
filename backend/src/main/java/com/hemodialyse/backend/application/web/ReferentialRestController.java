package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.domain.referential.port.ReferentialUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/referentials")
public class ReferentialRestController {

    private final ReferentialUseCase useCase;
    private final JdbcTemplate jdbc;

    public ReferentialRestController(ReferentialUseCase useCase, JdbcTemplate jdbc) {
        this.useCase = useCase;
        this.jdbc = jdbc;
    }

    @GetMapping("/centres-payeurs")
    public ResponseEntity<?> centresPayeurs(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.centresPayeurs(CenterId.of(centerId)));
    }

    @GetMapping("/agences")
    public ResponseEntity<?> agences(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.agences(CenterId.of(centerId)));
    }

    @GetMapping("/caisses")
    public ResponseEntity<?> caisses(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.caisses(CenterId.of(centerId)));
    }

    @GetMapping("/medecins")
    public ResponseEntity<?> medecins(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.medecins(CenterId.of(centerId)));
    }

    @GetMapping("/salles")
    public ResponseEntity<?> salles(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.salles(CenterId.of(centerId)));
    }

    @GetMapping("/positions")
    public ResponseEntity<?> positions(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.positions(CenterId.of(centerId)));
    }

    @GetMapping("/transporteurs")
    public ResponseEntity<?> transporteurs(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.transporteurs(CenterId.of(centerId)));
    }

    @GetMapping("/categories-transport")
    public ResponseEntity<?> categoriesTransport(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.categoriesTransport(CenterId.of(centerId)));
    }

    @GetMapping("/forfaits")
    public ResponseEntity<?> forfaits(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.forfaits(CenterId.of(centerId)));
    }

    @GetMapping("/centres-payeurs-details")
    @Cacheable(cacheNames = "ref.centresPayeursDetails", key = "#centerId.toString()")
    public ResponseEntity<?> centresPayeursDetails(@RequestParam UUID centerId) {
        var rows = jdbc.queryForList(
            "SELECT cp.id, cp.code AS code_centre_payeur, cp.nom AS libelle_centre_payeur, cp.adresse AS adresse_centre_payeur, " +
            "ag.code AS code_agence, ag.nom AS libelle_agence, ca.code AS code_caisse, ca.nom AS libelle_caisse " +
            "FROM centre_payeur cp " +
            "LEFT JOIN agence ag ON ag.id = cp.agence_id " +
            "LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id " +
            "WHERE cp.center_id = ? ORDER BY cp.nom",
            centerId
        );
        return ResponseEntity.ok(rows);
    }
}
