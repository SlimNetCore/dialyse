package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.stock.ComptabiliserLeStock;
import com.hemodialyse.backend.contexts.comptabilite.application.stock.ComptabiliserLeStock.Echec;
import com.hemodialyse.backend.contexts.comptabilite.application.stock.ComptabiliserLeStock.Synchronisation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

    public record PeriodeRequete(UUID centerId, @NotNull LocalDate from, @NotNull LocalDate to) {
    }

    /**
     * {@code echecs} n'apparaît que s'il y en a : sans échec, la réponse est celle du legacy.
     */
    public record SynchronisationReponse(int receptions, int joursSorties, int inventaires, int complements,
                                         int ignorees,
                                         @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Echec> echecs) {
    }
}

