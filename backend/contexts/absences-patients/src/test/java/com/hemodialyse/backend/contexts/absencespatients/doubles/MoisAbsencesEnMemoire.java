package com.hemodialyse.backend.contexts.absencespatients.doubles;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.EntreeDuJournalDuMois;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Double du dépôt des mois d'absences : même contrat que l'adaptateur JDBC (voir {@code MoisAbsencesContrat}). Le verrou n'a pas de
 * sens sans concurrence : {@code verrouiller} crée seulement la ligne ouverte et note le mois pour vérifier l'ordre des verrous.
 */
public class MoisAbsencesEnMemoire implements MoisAbsencesRepository {

    private final Map<String, MoisAbsences> lignes = new HashMap<>();
    private final List<EntreeDuJournalDuMois> journal = new ArrayList<>();
    private final List<YearMonth> verrous = new ArrayList<>();

    private static String cle(CentreId centre, YearMonth mois) {
        return centre.valeur() + "/" + mois;
    }

    private static MoisAbsences copie(MoisAbsences m) {
        return MoisAbsences.reconstituer(m.centre(), m.mois(), m.estClos(), m.closLe(), m.closPar(), m.rouvertLe(),
                m.rouvertPar(), m.motifDeReouverture());
    }

    @Override
    public synchronized MoisAbsences duMois(CentreId centre, YearMonth mois) {
        return copie(lignes.getOrDefault(cle(centre, mois), MoisAbsences.ouvert(centre, mois)));
    }

    @Override
    public synchronized MoisAbsences verrouiller(CentreId centre, YearMonth mois) {
        verrous.add(mois);
        return copie(lignes.computeIfAbsent(cle(centre, mois), k -> MoisAbsences.ouvert(centre, mois)));
    }

    @Override
    public synchronized boolean estOuvert(CentreId centre, YearMonth mois) {
        return !duMois(centre, mois).estClos();
    }

    @Override
    public synchronized boolean estOuvertSousVerrou(CentreId centre, YearMonth mois) {
        return !verrouiller(centre, mois).estClos();
    }

    @Override
    public synchronized List<YearMonth> moisClos(CentreId centre) {
        return lignes.values().stream().filter(m -> m.centre().equals(centre) && m.estClos()).map(MoisAbsences::mois)
                .sorted().toList();
    }

    @Override
    public synchronized List<MoisAbsences> deLAnnee(CentreId centre, int annee) {
        return lignes.values().stream().filter(m -> m.centre().equals(centre) && m.mois().getYear() == annee)
                .sorted(Comparator.comparing(MoisAbsences::mois)).map(MoisAbsencesEnMemoire::copie).toList();
    }

    @Override
    public synchronized void enregistrer(MoisAbsences mois, EntreeDuJournalDuMois entree) {
        lignes.put(cle(mois.centre(), mois.mois()), copie(mois));
        journal.add(entree);
    }

    @Override
    public synchronized List<EntreeDuJournalDuMois> journal(CentreId centre, YearMonth mois) {
        return journal.stream().filter(e -> e.centre().equals(centre) && e.mois().equals(mois)).toList();
    }

    /**
     * Les mois verrouillés, dans l'ordre des appels (pour vérifier l'ordre croissant).
     */
    public synchronized List<YearMonth> moisVerrouilles() {
        return List.copyOf(verrous);
    }

    /**
     * Pose un mois clos sans passer par une clôture (décor des tests).
     */
    public synchronized void poserClos(CentreId centre, YearMonth mois) {
        lignes.put(cle(centre, mois), MoisAbsences.reconstituer(centre, mois, true, Instant.parse("2026-09-01T08:00:00Z"),
                new com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId(new UUID(99, 1)),
                null, null, null));
    }

    /**
     * Remet un mois à l'état ouvert, sans trace (décor des tests).
     */
    public synchronized void poserOuvert(CentreId centre, YearMonth mois) {
        lignes.remove(cle(centre, mois));
    }
}
