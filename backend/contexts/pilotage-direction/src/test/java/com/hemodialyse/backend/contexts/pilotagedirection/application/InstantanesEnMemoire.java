package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDInstantane;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link InstantanesMensuels}, validé par le même contrat que l'adaptateur JDBC. */
public final class InstantanesEnMemoire implements InstantanesMensuels {

    private final Map<String, InstantaneFige> figes = new LinkedHashMap<>();
    private final OffsetDateTime horodatage = OffsetDateTime.parse("2026-11-09T10:00:00Z");

    private static String cle(UUID societeId, String mois) {
        return societeId + "|" + mois;
    }

    @Override
    public List<InstantaneListe> lister(UUID societeId) {
        return figes.entrySet().stream().filter(e -> e.getKey().startsWith(societeId + "|"))
                .map(e -> new InstantaneListe(e.getValue().mois(), e.getValue().genereLe()))
                .sorted(Comparator.comparing(InstantaneListe::mois).reversed()).toList();
    }

    @Override
    public Optional<InstantaneFige> trouver(UUID societeId, MoisDInstantane mois) {
        return Optional.ofNullable(figes.get(cle(societeId, mois.texte())));
    }

    @Override
    public boolean enregistrer(InstantaneMensuel instantane) {
        return enregistrer(instantane, horodatage);
    }

    @Override
    public boolean enregistrer(InstantaneMensuel instantane, OffsetDateTime genereLe) {
        String cle = cle(instantane.societeId(), instantane.mois().texte());
        if (figes.containsKey(cle)) return false;
        figes.put(cle, new InstantaneFige(instantane.mois().texte(), genereLe, instantane.contenu().texte()));
        return true;
    }

    /** Insère une ligne telle quelle (mois quelconque), comme un {@code INSERT} direct en base : pour les lignes illisibles déjà présentes. */
    public void inserer(UUID societeId, String mois, String contenu) {
        figes.put(cle(societeId, mois), new InstantaneFige(mois, horodatage, contenu));
    }
}
