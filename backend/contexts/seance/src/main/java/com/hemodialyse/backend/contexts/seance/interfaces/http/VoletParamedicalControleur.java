package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.RenseignerLeVoletParamedical;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.VoletParamedicalDemande;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter entrant HTTP du volet paramédical (SE7f, ADR 0100) : MÊME URL, MÊMES champs JSON, MÊMES droits (administrateur, infirmier,
 * secrétaire) et MÊMES codes que {@code VoletParamedicalRestController} (plateforme, retiré) : 403 avant 400 (centre du corps), 400
 * « Seance introuvable » / « Patient introuvable » / « CenterId cannot be null », 422 (facturée, mesures), 500 {@code DATA_ACCESS_ERROR}
 * (unicité par séance, KB-SEA-94 ; colonne trop courte) et 500 {@code INTERNAL_ERROR} sur un corps illisible (reproduit : les erreurs de
 * lecture du corps ne sont PAS reprises par {@link ErreursDuVoletParamedicalAdvice}, parité DM1). Routé depuis SE7f : le handler legacy est
 * retiré (refactoring sur place, ADR 0024).
 */
@RestController
@RequestMapping("/api/v1/seances")
public class VoletParamedicalControleur {

    private final RenseignerLeVoletParamedical renseigner;

    public VoletParamedicalControleur(RenseignerLeVoletParamedical renseigner) {
        this.renseigner = renseigner;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','SECRETAIRE')")
    @PutMapping("/{seanceId}/volet-paramedical")
    public ResponseEntity<Map<String, Object>> renseigner(@PathVariable UUID seanceId, @RequestBody VoletParamedicalDemande demande) {
        if (demande.centerId() == null) throw new SaisieInvalide("CenterId cannot be null");
        var volet = renseigner.executer(demande.centerId(), seanceId, demande::mesures);
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("id", volet.id());
        reponse.put("seanceId", volet.seance().valeur());
        reponse.put("updatedAt", volet.modifieLe());
        return ResponseEntity.ok(reponse);
    }
}
