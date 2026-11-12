package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie;

import com.hemodialyse.backend.contexts.pilotagedirection.application.CapturerLesInstantanesDuMoisEcoule;
import com.hemodialyse.backend.contexts.pilotagedirection.application.CapturerUnInstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterUnInstantane;
import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesMensuels.InstantaneFige;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ListerLesInstantanes;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RegleDuPilotageViolee;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Adaptateur entrant du langage publié {@link InstantanesMensuelsDeLaDirection} : appelle les cas d'usage et relit le contenu figé en y supprimant les totaux de patients composés d'un centre masqué ({@link MasqueDesTotauxFiges}). Un
 * contenu illisible est refusé en 422 {@code INSTANTANE_ILLISIBLE} (KB-DIR-06 e corrigé le 2026-11-10 ; il reste listé). {@code breakdown} absent d'un instantané
 * ancien reste un nœud manquant (servi {@code null}, KB-DIR-06 a, reproduit).
 */
public final class InstantanesMensuelsPublies implements InstantanesMensuelsDeLaDirection {

    private final ListerLesInstantanes lister;
    private final ConsulterUnInstantane consulter;
    private final CapturerUnInstantaneMensuel capturer;
    private final CapturerLesInstantanesDuMoisEcoule capturerLeMoisEcoule;
    private final ObjectMapper mapper;

    public InstantanesMensuelsPublies(ListerLesInstantanes lister, ConsulterUnInstantane consulter, CapturerUnInstantaneMensuel capturer,
                                      CapturerLesInstantanesDuMoisEcoule capturerLeMoisEcoule, ObjectMapper mapper) {
        this.lister = lister;
        this.consulter = consulter;
        this.capturer = capturer;
        this.capturerLeMoisEcoule = capturerLeMoisEcoule;
        this.mapper = mapper;
    }

    @Override
    public List<Info> lister(UUID societeId) {
        return lister.execute(societeId).stream().map(i -> new Info(i.mois(), i.genereLe())).toList();
    }

    @Override
    public Instantane lire(UUID societeId, String mois) {
        return traduire(consulter.execute(societeId, mois));
    }

    @Override
    public Instantane figer(UUID societeId, String mois) {
        return traduire(capturer.execute(societeId, mois));
    }

    @Override
    public int figerLeMoisEcoule() {
        return capturerLeMoisEcoule.execute();
    }

    private static boolean contientUneValeurNonFinie(JsonNode noeud) {
        if (noeud.isFloatingPointNumber()) return !Double.isFinite(noeud.doubleValue());
        if (noeud.isObject() || noeud.isArray()) {
            for (JsonNode enfant : noeud) {
                if (contientUneValeurNonFinie(enfant)) return true;
            }
        }
        return false;
    }

    private Instantane traduire(InstantaneFige i) {
        JsonNode racine;
        try {
            racine = mapper.readTree(i.contenu());
        } catch (JacksonException e) {
            // KB-DIR-06 (e) corrigé (lot L6, ADR 0119) : refus métier 422, sans le détail technique de l'analyse du JSON
            throw new RegleDuPilotageViolee("INSTANTANE_ILLISIBLE", "Cet instantané mensuel est illisible");
        }
        // KB-PL10a-2 corrigé (2026-11-12, comme KB-PL9-3) : un contenu vide ou qui n'est pas un objet, ou portant une valeur non finie
        // (1e999999), est illisible : 422, jamais un 200 aux sections nulles ou « Infinity »
        if (racine == null || !racine.isObject() || racine.size() == 0 || contientUneValeurNonFinie(racine)) {
            throw new RegleDuPilotageViolee("INSTANTANE_ILLISIBLE", "Cet instantané mensuel est illisible");
        }
        // KB-DIR-01 / KB-MED-104 corrigés (2026-11-09) : les totaux bruts d'un instantané ancien sont supprimés À LA LECTURE (arbre en
        // mémoire ; le contenu stocké n'est jamais réécrit).
        MasqueDesTotauxFiges.appliquer(racine.path("overview"), racine.path("indicators"), racine.path("breakdown"));
        return new Instantane(i.mois(), i.genereLe(), racine.path("overview"), racine.path("indicators"), racine.path("breakdown"));
    }
}
