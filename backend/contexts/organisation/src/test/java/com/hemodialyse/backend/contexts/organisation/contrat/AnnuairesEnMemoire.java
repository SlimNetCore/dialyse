package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Faux en mémoire des annuaires : il passe le MÊME contrat ({@link AnnuairesContrat}) que les adapters JDBC. */
final class AnnuairesEnMemoire implements AnnuaireDesCentres, AnnuaireDesSocietes {

    private record Societe(UUID id, String raisonSociale, boolean actif) {
    }

    private record Centre(UUID id, UUID societe, String nom, Boolean actif, String fuseau) {
    }

    private final Map<UUID, Societe> societes = new LinkedHashMap<>();
    private final Map<UUID, Centre> centres = new LinkedHashMap<>();

    void societe(UUID id, String raisonSociale, boolean actif) {
        societes.put(id, new Societe(id, raisonSociale, actif));
    }

    void centre(UUID id, UUID societe, String nom, Boolean actif, String fuseau) {
        centres.put(id, new Centre(id, societe, nom, actif, fuseau));
    }

    @Override
    public List<UUID> actifs() {
        return centres.values().stream().filter(c -> c.actif() == null || c.actif())
                .map(Centre::id).sorted(Comparator.comparing(UUID::toString)).toList();
    }

    @Override
    public List<UUID> actifsAuSensStrict() {
        return centres.values().stream().filter(c -> Boolean.TRUE.equals(c.actif()))
                .map(Centre::id).sorted(Comparator.comparing(UUID::toString)).toList();
    }

    @Override
    public List<UUID> tous() {
        return new ArrayList<>(centres.keySet());
    }

    @Override
    public Optional<ZoneId> fuseauHoraireDe(UUID centre) {
        Centre c = centres.get(centre);
        if (c == null || c.fuseau() == null || c.fuseau().isBlank()) return Optional.empty();
        try {
            return Optional.of(ZoneId.of(c.fuseau().trim()));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<UUID> societesActives() {
        return societes.values().stream().filter(Societe::actif).map(Societe::id).toList();
    }

    @Override
    public Optional<UUID> societeDuCentre(UUID centre) {
        return Optional.ofNullable(centres.get(centre)).map(Centre::societe);
    }

    @Override
    public Optional<SocieteAvecSesCentres> societe(UUID societe) {
        Societe s = societes.get(societe);
        if (s == null) return Optional.empty();
        List<CentreDeLaSociete> deLaSociete = centres.values().stream().filter(c -> c.societe().equals(societe))
                .sorted(Comparator.comparing(Centre::nom))
                .map(c -> new CentreDeLaSociete(c.id(), c.nom(), c.actif() == null || c.actif())).toList();
        return Optional.of(new SocieteAvecSesCentres(s.id(), s.raisonSociale(), deLaSociete));
    }
}
