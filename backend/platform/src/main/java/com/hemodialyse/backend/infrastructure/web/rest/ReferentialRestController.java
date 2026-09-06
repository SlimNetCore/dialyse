package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.referential.port.ReferentialUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/referentials")
public class ReferentialRestController {

    private final ReferentialUseCase useCase;

    public ReferentialRestController(ReferentialUseCase useCase) {
        this.useCase = useCase;
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

    @GetMapping("/etats-patients")
    public ResponseEntity<?> etatsPatients(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.etatsPatients(CenterId.of(centerId)));
    }

    @GetMapping("/articles")
    public ResponseEntity<?> articles(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.articles(CenterId.of(centerId)));
    }

    /**
     * Returns all dialysis generators for a centre.
     * Optional {@code salleId} filter: restricts to generators of a specific room.
     */
    @GetMapping("/generateurs")
    public ResponseEntity<?> generateurs(@RequestParam UUID centerId,
                                         @RequestParam(required = false) UUID salleId) {
        if (salleId != null) {
            return ResponseEntity.ok(useCase.generateursBySalle(CenterId.of(centerId), salleId));
        }
        return ResponseEntity.ok(useCase.generateurs(CenterId.of(centerId)));
    }

    @GetMapping("/centres-payeurs-details")
    @Cacheable(cacheNames = "ref.centresPayeursDetails", key = "#centerId.toString()")
    public ResponseEntity<?> centresPayeursDetails(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.centresPayeursDetails(CenterId.of(centerId)));
    }
}

