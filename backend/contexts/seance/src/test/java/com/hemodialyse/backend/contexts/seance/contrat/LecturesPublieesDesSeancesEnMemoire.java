package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.FacturationDesSeancesPubliee;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlacesDesSeancesPubliees;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire des lectures publiées de la Séance : même contrat que l'adapter JDBC (ADR 0096). */
class LecturesPublieesDesSeancesEnMemoire implements FacturationDesSeancesPubliee, PlacesDesSeancesPubliees {

    private record Ligne(String code, String libelle) {
    }

    private record SeanceLue(UUID centre, String statut, UUID salle, UUID creneau) {
    }

    private final Map<UUID, Map<UUID, Ligne>> salles = new HashMap<>();
    private final Map<UUID, Map<UUID, Ligne>> creneaux = new HashMap<>();
    private final Map<UUID, SeanceLue> seances = new HashMap<>();

    void salle(UUID centre, UUID id, String code) {
        salles.computeIfAbsent(centre, c -> new HashMap<>()).put(id, new Ligne(code, null));
    }

    void creneau(UUID centre, UUID id, String code, String libelle) {
        creneaux.computeIfAbsent(centre, c -> new HashMap<>()).put(id, new Ligne(code, libelle));
    }

    void seance(UUID centre, UUID id, String statut, UUID salle, UUID creneau) {
        seances.put(id, new SeanceLue(centre, statut, salle, creneau));
    }

    private Optional<SeanceLue> du(UUID centre, UUID id) {
        return Optional.ofNullable(seances.get(id)).filter(s -> s.centre().equals(centre));
    }

    @Override
    public boolean estFacturee(UUID centre, UUID seance) {
        return du(centre, seance).map(s -> "FACTUREE".equalsIgnoreCase(s.statut())).orElse(false);
    }

    @Override
    public Optional<PlaceDeSeancePubliee> de(UUID centre, UUID seance) {
        return du(centre, seance).map(s -> {
            Ligne salle = s.salle() == null ? null : salles.getOrDefault(centre, Map.of()).get(s.salle());
            Ligne creneau = s.creneau() == null ? null : creneaux.getOrDefault(centre, Map.of()).get(s.creneau());
            return new PlaceDeSeancePubliee(salle == null ? null : salle.code(), creneau == null ? null : creneau.code(),
                    creneau == null ? null : creneau.libelle());
        });
    }
}
