package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.NombreDeSeancesRecentes;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIllisible;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Double en mémoire du port {@link Seances} : le même contrat que la base (H2, PostgreSQL 16) lui est appliqué. */
public class SeancesEnMemoire implements Seances {

    private final Map<SeanceId, Seance> lignes = new LinkedHashMap<>();
    private final Map<SeanceId, CentreId> illisibles = new HashMap<>();

    @Override
    public void ajouter(Seance seance) {
        if (lignes.containsKey(seance.id())) throw new IllegalStateException("Séance déjà enregistrée");
        lignes.put(seance.id(), copie(seance));
    }

    /** Nombre de verrous du jour demandés (le double n'a pas de concurrence : il garde seulement la trace de l'appel). */
    public int verrousDuJourDemandes;

    @Override
    public void verrouillerLeJour(CentreId centre, PatientId patient, LocalDate jour) {
        verrousDuJourDemandes++;
    }

    @Override
    public List<Seance> duJour(CentreId centre, PatientId patient, LocalDate jour) {
        List<Seance> trouvees = new ArrayList<>();
        for (Seance s : lignes.values()) {
            if (s.centre().equals(centre) && s.patient().equals(patient) && s.jour().equals(jour)) trouvees.add(copie(s));
        }
        trouvees.sort(Comparator.comparing(Seance::creeLe, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                .thenComparing(s -> s.id().valeur()));
        return trouvees;
    }

    @Override
    public List<Seance> dernieres(CentreId centre, PatientId patient, LocalDate avant, NombreDeSeancesRecentes nombre) {
        return lignes.values().stream()
                .filter(s -> s.centre().equals(centre) && s.patient().equals(patient) && s.jour().isBefore(avant))
                .sorted(Comparator.comparing(Seance::jour).reversed()
                        .thenComparing(Comparator.comparing(Seance::creeLe, Comparator.nullsFirst(Comparator.<Instant>naturalOrder())).reversed())
                        .thenComparing(s -> s.id().valeur()))
                .limit(nombre.valeur()).map(SeancesEnMemoire::copie).toList();
    }

    @Override
    public Optional<Seance> trouver(CentreId centre, SeanceId id) {
        if (centre.equals(illisibles.get(id))) throw new SeanceIllisible(id, "statut inconnu");
        return Optional.ofNullable(lignes.get(id)).filter(s -> s.centre().equals(centre)).map(SeancesEnMemoire::copie);
    }

    /** Comme une base : on rend une COPIE, une modification n'existe qu'une fois enregistrée (et un retour arrière la défait). */
    private static Seance copie(Seance s) {
        return Seance.restituer(s.id(), s.centre(), s.patient(), s.jour(), s.statut(), s.creeLe(), s.derogation().orElse(null), s.jalons(),
                s.traces(), s.forfaitImpose().orElse(null), s.facture().orElse(null));
    }

    /** Nombre de verrous de ligne demandés (le double n'a pas de concurrence : il garde la trace de l'appel). */
    public int verrousDeLigneDemandes;

    @Override
    public Optional<Seance> trouverPourModifier(CentreId centre, SeanceId id) {
        verrousDeLigneDemandes++;
        return trouver(centre, id);
    }

    @Override
    public void enregistrer(Seance seance) {
        if (!lignes.containsKey(seance.id())) throw new IllegalStateException("Séance inconnue");
        lignes.put(seance.id(), copie(seance));
    }

    /**
     * Comme la base : seul le forfait imposé est écrit, le reste de la ligne enregistrée (statut, jalons) est conservé tel quel.
     */
    @Override
    public void enregistrerLeForfaitImpose(Seance seance) {
        Seance enBase = lignes.get(seance.id());
        if (enBase == null) throw new IllegalStateException("Séance inconnue");
        var impose = seance.forfaitImpose().orElseThrow(() -> new IllegalStateException("Aucun forfait imposé à enregistrer"));
        lignes.put(seance.id(), Seance.restituer(enBase.id(), enBase.centre(), enBase.patient(), enBase.jour(), enBase.statut(),
                enBase.creeLe(), enBase.derogation().orElse(null), enBase.jalons(), enBase.traces(), impose,
                enBase.facture().orElse(null)));
    }

    /** Comme la base : seul le jour est écrit, le reste de la ligne enregistrée (statut, facture, forfait imposé) est conservé tel quel. */
    @Override
    public void enregistrerLeJour(Seance seance) {
        Seance enBase = lignes.get(seance.id());
        if (enBase == null) throw new IllegalStateException("Séance inconnue");
        lignes.put(seance.id(), Seance.restituer(enBase.id(), enBase.centre(), enBase.patient(), seance.jour(), enBase.statut(),
                enBase.creeLe(), enBase.derogation().orElse(null), enBase.jalons(), enBase.traces(), enBase.forfaitImpose().orElse(null),
                enBase.facture().orElse(null)));
    }

    @Override
    public void supprimer(Seance seance) {
        Seance enBase = lignes.get(seance.id());
        if (enBase == null || !enBase.centre().equals(seance.centre())) throw new IllegalStateException("Séance inconnue");
        lignes.remove(seance.id());
    }

    /** Sème une ligne telle qu'elle existe déjà en base (hors règles de création). */
    public void semer(Seance seance) {
        lignes.put(seance.id(), copie(seance));
    }

    /** Sème une ligne dont le statut n'est pas lisible. */
    public void semerUneLigneIllisible(CentreId centre, SeanceId id) {
        illisibles.put(id, centre);
    }

    public List<Seance> toutes() {
        return List.copyOf(lignes.values());
    }

    /** Instantané et retour arrière : sert à prouver qu'un échec défait tout dans la même unité de travail. */
    public Map<SeanceId, Seance> instantane() {
        return new LinkedHashMap<>(lignes);
    }

    public void restaurer(Map<SeanceId, Seance> instantane) {
        lignes.clear();
        lignes.putAll(instantane);
    }
}
