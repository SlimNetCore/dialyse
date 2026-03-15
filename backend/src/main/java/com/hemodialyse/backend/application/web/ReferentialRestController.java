package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.application.port.in.ReferentialUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/referentials")
public class ReferentialRestController {

    private final ReferentialUseCase useCase;

    public ReferentialRestController(ReferentialUseCase useCase) { this.useCase = useCase; }

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
}
