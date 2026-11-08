package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.NombreDeSeancesRecentes;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rappel des dernières séances d'un patient avec leurs constantes (GET /patient/{id}/recentes, poste infirmier). Séances STRICTEMENT avant
 * le jour demandé (par défaut le jour du centre, {@link HorlogeDuCentre}, jamais celui de la JVM), de la plus récente à la plus ancienne,
 * au plus {@link NombreDeSeancesRecentes#MAXIMUM}, au moins une (0 ou négatif ramené à 1, sans alerte). TOUS les statuts, y compris les
 * séances non validées, dont les constantes sont exposées (KB-SEA-60, reproduit avec alerte de confidentialité, ADR 0094). Un patient inconnu
 * ou d'un autre centre : liste vide (pas de 404).
 */
public class ConsulterLesSeancesRecentes {

    private final SessionCourante session;
    private final Seances seances;
    private final VoletsDeLaSeance volets;
    private final HorlogeDuCentre horloge;

    public ConsulterLesSeancesRecentes(SessionCourante session, Seances seances, VoletsDeLaSeance volets, HorlogeDuCentre horloge) {
        this.session = session;
        this.seances = seances;
        this.volets = volets;
        this.horloge = horloge;
    }

    public List<SeanceRecente> executer(UUID centreDemande, UUID patient, LocalDate avant, int limiteDemandee) {
        CentreId centre = session.centre(centreDemande);
        LocalDate jour = avant != null ? avant : horloge.aujourdhui(centre);
        return seances.dernieres(centre, new PatientId(patient), jour, NombreDeSeancesRecentes.demande(limiteDemandee)).stream()
                .map(s -> new SeanceRecente(s, volets.paramedical(centre, s.id())))
                .toList();
    }

    /** Une séance et ses constantes (le volet paramédical, s'il a été saisi). */
    public record SeanceRecente(Seance seance, Optional<VoletsDeLaSeance.VoletParamedical> constantes) {
    }
}
