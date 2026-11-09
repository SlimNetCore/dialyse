package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteOuverte;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.CleDAlerte;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Double en mémoire de l'historique des alertes ; il passe le même contrat que l'adaptateur JDBC. */
public final class HistoriqueDesAlertesEnMemoire implements HistoriqueDesAlertes {

    private static final class Ligne {
        final UUID id = UUID.randomUUID();
        UUID societeId;
        AlerteLevee alerte;
        OffsetDateTime vueLe;
        OffsetDateTime resolueLe;
    }

    private final List<Ligne> lignes = new ArrayList<>();

    public int nombreDeLignes() {
        return lignes.size();
    }

    @Override
    public List<AlerteOuverte> ouvertes(UUID societeId) {
        return lignes.stream().filter(l -> l.societeId.equals(societeId) && l.resolueLe == null)
                .map(l -> new AlerteOuverte(l.id, new CleDAlerte(l.alerte.centreId(), l.alerte.code()))).toList();
    }

    @Override
    public void ouvrir(UUID societeId, AlerteLevee alerte, OffsetDateTime vueLe) {
        Ligne l = new Ligne();
        l.societeId = societeId;
        l.alerte = alerte;
        l.vueLe = vueLe;
        lignes.add(l);
    }

    @Override
    public void resoudre(UUID alerteId, OffsetDateTime resolueLe) {
        lignes.stream().filter(l -> l.id.equals(alerteId)).forEach(l -> l.resolueLe = resolueLe);
    }

    @Override
    public List<Entree> recentes(UUID societeId, int limite) {
        Comparator<Ligne> parActivite = Comparator.comparing((Ligne l) -> l.resolueLe != null ? l.resolueLe : l.vueLe).reversed()
                .thenComparing(Comparator.comparing((Ligne l) -> l.vueLe).reversed());
        return lignes.stream().filter(l -> l.societeId.equals(societeId)).sorted(parActivite).limit(limite)
                .map(l -> new Entree(l.alerte.centreId(), l.alerte.centre(), l.alerte.code(), l.alerte.gravite().name(),
                        l.alerte.valeur(), l.vueLe, l.resolueLe)).toList();
    }
}
