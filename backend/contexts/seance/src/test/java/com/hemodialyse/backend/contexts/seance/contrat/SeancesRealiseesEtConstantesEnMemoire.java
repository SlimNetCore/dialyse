package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.LiensDExclusionPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesAvecLeurPlacePubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesPubliees;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Double en mémoire des lectures publiées SE7d (ADR 0098, corrigé par l'ADR 0117), fidèle au contrat : statut réalisé en casse exacte, bornes
 * incluses, jours réalisés triés par date puis patient (KB-SEA-77), séances avec leur place par date, création connue d'abord puis
 * identifiant (KB-SEA-78), lien d'exclusion bloquant seulement sur une séance {@code ABSENT} (KB-SEA-81), page des constantes avec la création
 * nulle en premier en tri décroissant (KB-SEA-79), identifiants comparés en octets non signés.
 */
public final class SeancesRealiseesEtConstantesEnMemoire
        implements SeancesRealiseesPubliees, LiensDExclusionPublies, SeancesRealiseesAvecLeurPlacePubliees, ConstantesDesSeancesPubliees {

    private static final Set<String> REALISES = Set.of("VALIDEE", "SIGNEE", "FACTUREE");

    private static final class Ligne {
        final UUID id;
        final UUID centre;
        final UUID patient;
        final LocalDate date;
        final String statut;
        final Instant creeLe;
        UUID salle;
        UUID creneau;
        UUID generateur;
        UUID absence;

        Ligne(UUID id, UUID centre, UUID patient, LocalDate date, String statut, Instant creeLe) {
            this.id = id;
            this.centre = centre;
            this.patient = patient;
            this.date = date;
            this.statut = statut;
            this.creeLe = creeLe;
        }
    }

    private record Fiche(UUID centre, String nom, String prenom) {
    }

    private record Volet(UUID centre, BigDecimal poidsAvant, BigDecimal poidsApres, String tensionAvant, String tensionApres, Integer debit,
                         BigDecimal ultrafiltration, Integer duree) {
    }

    private final Map<UUID, Ligne> seances = new LinkedHashMap<>();
    private final Map<UUID, Fiche> fiches = new HashMap<>();
    private final Map<UUID, Volet> volets = new HashMap<>();
    // ---- semis

    public void patient(UUID id, UUID centre, String nom, String prenom) {
        fiches.put(id, new Fiche(centre, nom, prenom));
    }

    public void ficheDansLeCentre(UUID patient, UUID centre) {
        Fiche f = fiches.get(patient);
        fiches.put(patient, new Fiche(centre, f.nom(), f.prenom()));
    }

    public void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, Instant creeLe) {
        seances.put(id, new Ligne(id, centre, patient, date, statut, creeLe));
    }

    public void place(UUID seance, UUID salle, UUID creneau, UUID generateur) {
        Ligne l = seances.get(seance);
        l.salle = salle;
        l.creneau = creneau;
        l.generateur = generateur;
    }

    public void lienDExclusion(UUID seance, UUID absence) {
        seances.get(seance).absence = absence;
    }

    public void volet(UUID centre, UUID seance, BigDecimal poidsAvant, BigDecimal poidsApres, String tensionAvant, String tensionApres,
                      Integer debit, BigDecimal ultrafiltration, Integer duree) {
        volets.put(seance, new Volet(centre, poidsAvant, poidsApres, tensionAvant, tensionApres, debit, ultrafiltration, duree));
    }

    // ---- lectures

    private static boolean entre(LocalDate jour, LocalDate du, LocalDate au) {
        return !jour.isBefore(du) && !jour.isAfter(au);
    }

    private List<Ligne> realiseesDuCentre(UUID centre) {
        return seances.values().stream().filter(l -> l.centre.equals(centre) && REALISES.contains(l.statut)).toList();
    }

    @Override
    public List<JourRealisePublie> joursRealisesDuCentre(UUID centre, LocalDate du, LocalDate au) {
        Set<JourRealisePublie> distincts = new LinkedHashSet<>();
        realiseesDuCentre(centre).stream().filter(l -> entre(l.date, du, au)).forEach(l -> distincts.add(new JourRealisePublie(l.patient, l.date)));
        return distincts.stream().sorted(Comparator.comparing(JourRealisePublie::jour)
                .thenComparing(JourRealisePublie::patient, SeancesRealiseesEtConstantesEnMemoire::enOctetsNonSignes)).toList();
    }

    @Override
    public List<LocalDate> joursRealisesDuPatient(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return realiseesDuCentre(centre).stream().filter(l -> l.patient.equals(patient) && entre(l.date, du, au)).map(l -> l.date)
                .distinct().sorted().toList();
    }

    @Override
    public boolean estRealisee(UUID centre, UUID patient, LocalDate jour) {
        return realiseesDuCentre(centre).stream().anyMatch(l -> l.patient.equals(patient) && l.date.equals(jour));
    }

    @Override
    public List<UUID> patientsRealisesLe(UUID centre, LocalDate jour) {
        return realiseesDuCentre(centre).stream().filter(l -> l.date.equals(jour)).map(l -> l.patient).toList();
    }

    @Override
    public boolean absenceLiee(UUID centre, UUID absence) {
        return seances.values().stream().anyMatch(l -> l.centre.equals(centre) && absence.equals(l.absence) && "ABSENT".equals(l.statut));
    }

    @Override
    public List<SeanceRealiseeAvecPlacePubliee> duCentre(UUID centre, LocalDate du, LocalDate au) {
        Comparator<Ligne> ordre = Comparator.<Ligne, LocalDate>comparing(l -> l.date)
                .thenComparing(l -> l.creeLe, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                .thenComparing((x, y) -> enOctetsNonSignes(x.id, y.id));
        List<SeanceRealiseeAvecPlacePubliee> lues = new ArrayList<>();
        for (Ligne l : realiseesDuCentre(centre).stream().sorted(ordre).toList()) {
            Fiche fiche = fiches.get(l.patient);
            if (fiche != null && fiche.centre().equals(l.centre) && entre(l.date, du, au)) {
                lues.add(new SeanceRealiseeAvecPlacePubliee(l.patient, l.date, l.salle, l.creneau, l.generateur, fiche.nom(), fiche.prenom()));
            }
        }
        return lues;
    }

    @Override
    public long nombreDeSeances(UUID centre, UUID patient) {
        return seances.values().stream().filter(l -> l.centre.equals(centre) && l.patient.equals(patient)).count();
    }

    /** Ordre des octets non signés (celui de H2 et de PostgreSQL), et non l'ordre signé de {@link UUID#compareTo}. */
    static int enOctetsNonSignes(UUID a, UUID b) {
        int fort = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return fort != 0 ? fort : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    @Override
    public List<ConstantesDeSeancePubliees> page(UUID centre, UUID patient, int taille, long rang) {
        Comparator<Instant> creationDecroissante = Comparator.nullsFirst(Comparator.<Instant>reverseOrder());
        Comparator<Ligne> ordre = Comparator.<Ligne, LocalDate>comparing(l -> l.date, Comparator.reverseOrder())
                .thenComparing(l -> l.creeLe, creationDecroissante)
                .thenComparing((x, y) -> enOctetsNonSignes(y.id, x.id));
        return seances.values().stream().filter(l -> l.centre.equals(centre) && l.patient.equals(patient)).sorted(ordre)
                .skip(rang).limit(taille).map(this::constantesDe).toList();
    }

    private ConstantesDeSeancePubliees constantesDe(Ligne l) {
        Volet v = volets.get(l.id);
        if (v == null || !v.centre().equals(l.centre)) {
            return new ConstantesDeSeancePubliees(l.id, l.date, null, null, null, null, null, null, null);
        }
        return new ConstantesDeSeancePubliees(l.id, l.date, v.poidsAvant(), v.poidsApres(), v.tensionAvant(), v.tensionApres(), v.debit(),
                v.ultrafiltration(), v.duree());
    }
}
