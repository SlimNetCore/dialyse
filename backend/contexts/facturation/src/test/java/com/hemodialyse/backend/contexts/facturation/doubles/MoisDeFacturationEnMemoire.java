package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.EntreeDuJournal;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Double du dépôt des mois de facturation : même contrat que l'adaptateur JDBC (voir {@code MoisDeFacturationContrat}). Le verrou
 * n'a pas de sens sans concurrence : {@code verrouiller} crée seulement la ligne ouverte.
 */
public class MoisDeFacturationEnMemoire implements MoisDeFacturationRepository {

    private final Map<String, MoisDeFacturation> lignes = new HashMap<>();
    private final List<EntreeDuJournal> journal = new ArrayList<>();
    private final List<YearMonth> verrous = new ArrayList<>();

    private static String cle(CentreId centre, YearMonth mois) {
        return centre.valeur() + "/" + mois;
    }

    @Override
    public synchronized MoisDeFacturation duMois(CentreId centre, YearMonth mois) {
        return copie(lignes.getOrDefault(cle(centre, mois), MoisDeFacturation.ouvert(centre, mois)));
    }

    @Override
    public synchronized MoisDeFacturation verrouiller(CentreId centre, YearMonth mois) {
        verrous.add(mois);
        return copie(lignes.computeIfAbsent(cle(centre, mois), k -> MoisDeFacturation.ouvert(centre, mois)));
    }

    @Override
    public synchronized List<MoisDeFacturation> deLAnnee(CentreId centre, int annee) {
        return lignes.values().stream().filter(m -> m.centre().equals(centre) && m.mois().getYear() == annee)
                .sorted(Comparator.comparing(MoisDeFacturation::mois)).map(MoisDeFacturationEnMemoire::copie).toList();
    }

    @Override
    public synchronized void enregistrer(MoisDeFacturation mois, EntreeDuJournal entree) {
        lignes.put(cle(mois.centre(), mois.mois()), copie(mois));
        journal.add(entree);
    }

    @Override
    public synchronized List<EntreeDuJournal> journal(CentreId centre, YearMonth mois) {
        return journal.stream().filter(e -> e.centre().equals(centre) && e.mois().equals(mois)).toList();
    }

    /** Les mois verrouillés, dans l'ordre des appels (pour vérifier l'ordre croissant). */
    public synchronized List<YearMonth> moisVerrouilles() {
        return List.copyOf(verrous);
    }

    /** Pose un mois clos sans passer par une clôture (décor des tests). */
    public synchronized void poserClos(CentreId centre, YearMonth mois) {
        lignes.put(cle(centre, mois), MoisDeFacturation.reconstituer(centre, mois, true,
                java.time.Instant.parse("2026-09-01T08:00:00Z"), java.util.UUID.fromString("99996800-0000-0000-0000-00000000c0ff"),
                null, null, null));
    }

    private static MoisDeFacturation copie(MoisDeFacturation m) {
        return MoisDeFacturation.reconstituer(m.centre(), m.mois(), m.estClos(), m.closLe(), m.closPar(), m.rouvertLe(),
                m.rouvertPar(), m.motifDeReouverture());
    }
}
