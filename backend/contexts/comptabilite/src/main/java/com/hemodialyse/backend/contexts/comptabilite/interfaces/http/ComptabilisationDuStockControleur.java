package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.stock.ComptabiliserLeStock;
import com.hemodialyse.backend.contexts.comptabilite.application.stock.ComptabiliserLeStock.Synchronisation;
import com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto.PeriodeRequete;
import com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto.SynchronisationReponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Adaptateur HTTP entrant de la slice C6 : comptabilisation du stock à la demande, sur 1 à 366 jours (borne historique :
 * {@code from + 366 >= to}, KB-CPT-43).
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class ComptabilisationDuStockControleur {

    /**
     * Plus longue période qu'une demande peut couvrir.
     */
    static final int JOURS_SYNCHRONISATION_MAX = 366;

    private final SessionCourante session;
    private final ComptabiliserLeStock comptabilisation;
    private final Clock horloge;

    public ComptabilisationDuStockControleur(SessionCourante session, ComptabiliserLeStock comptabilisation, Clock horloge) {
        this.session = session;
        this.comptabilisation = comptabilisation;
        this.horloge = horloge;
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/stock/synchroniser")
    public ResponseEntity<SynchronisationReponse> synchroniser(@RequestBody @Valid PeriodeRequete requete) {
        if (requete.to().isBefore(requete.from())
                || requete.from().plusDays(JOURS_SYNCHRONISATION_MAX).isBefore(requete.to())) {
            throw new IllegalArgumentException("La période à comptabiliser doit couvrir de 1 à "
                    + JOURS_SYNCHRONISATION_MAX + " jours");
        }
        Synchronisation s = comptabilisation.synchroniser(session.centre(requete.centerId()), requete.from(),
                requete.to(), LocalDate.now(horloge));
        return ResponseEntity.ok(new SynchronisationReponse(s.receptions(), s.joursSorties(), s.inventaires(),
                s.complements(), s.ignorees(), s.echecs()));
    }
}

