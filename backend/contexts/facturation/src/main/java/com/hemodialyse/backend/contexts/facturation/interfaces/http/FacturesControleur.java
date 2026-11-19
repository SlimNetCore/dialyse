package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.consultation.ChampDeTri;
import com.hemodialyse.backend.contexts.facturation.application.consultation.ConsulterLesFactures;
import com.hemodialyse.backend.contexts.facturation.application.consultation.CriteresDeFactures;
import com.hemodialyse.backend.contexts.facturation.application.consultation.TriDesFactures;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;
import com.hemodialyse.backend.contexts.facturation.interfaces.http.dto.DetailDeFactureReponse;
import com.hemodialyse.backend.contexts.facturation.interfaces.http.dto.PageDeFacturesReponse;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter entrant HTTP de la consultation des factures émises (RG-FAC-046, 047), lecture seule, réservé à {@code ADMIN} (comme le calcul de
 * la facturation) ; le médecin « seul » est refusé par {@code RoleScopeFilter}. Le centre demandé est confronté à celui de la session (403
 * sinon). Une facture inconnue ou d'un autre centre : 404 sans corps.
 */
@RestController
@RequestMapping("/api/v1/facturation/factures")
public class FacturesControleur {

    private final SessionCourante session;
    private final ConsulterLesFactures consulter;

    public FacturesControleur(SessionCourante session, ConsulterLesFactures consulter) {
        this.session = session;
        this.consulter = consulter;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<PageDeFacturesReponse> rechercher(
            @RequestParam UUID centerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String numero,
            @RequestParam(required = false) String patient,
            @RequestParam(required = false) UUID caisseId,
            @RequestParam(required = false) String etat,
            @RequestParam(required = false) BigDecimal montantMin,
            @RequestParam(required = false) BigDecimal montantMax) {
        CentreId centre = session.centre(centerId);
        CriteresDeFactures criteres = new CriteresDeFactures(centre, du, au, numero, patient, caisseId, etat(etat), montantMin,
                montantMax, tri(sort));
        return ResponseEntity.ok(PageDeFacturesReponse.depuis(consulter.rechercher(criteres, page, size)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{factureId}/lignes")
    public ResponseEntity<DetailDeFactureReponse> detail(@PathVariable UUID factureId, @RequestParam UUID centerId) {
        CentreId centre = session.centre(centerId);
        return consulter.detail(centre, factureId).map(DetailDeFactureReponse::depuis).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static EtatDeReglement etat(String etat) {
        if (etat == null || etat.isBlank()) return null;
        try {
            return EtatDeReglement.valueOf(etat.trim());
        } catch (IllegalArgumentException e) {
            throw new RequeteInvalide("ETAT_INVALIDE", "Etat de reglement inconnu : " + etat);
        }
    }

    /** {@code champ,asc|desc} ; direction absente : ascendant. */
    private static TriDesFactures tri(String sort) {
        if (sort == null || sort.isBlank()) return null;
        String[] parties = sort.split(",", -1);
        if (parties.length > 2) {
            throw new RequeteInvalide("TRI_INVALIDE", "Tri illisible : " + sort);
        }
        ChampDeTri champ = ChampDeTri.depuis(parties[0].trim())
                .orElseThrow(() -> new RequeteInvalide("TRI_INVALIDE", "Champ de tri inconnu : " + parties[0]));
        String direction = parties.length == 2 ? parties[1].trim().toLowerCase(java.util.Locale.ROOT) : "asc";
        return switch (direction) {
            case "asc", "" -> new TriDesFactures(champ, false);
            case "desc" -> new TriDesFactures(champ, true);
            default -> throw new RequeteInvalide("TRI_INVALIDE", "Direction de tri inconnue : " + parties[1]);
        };
    }
}
