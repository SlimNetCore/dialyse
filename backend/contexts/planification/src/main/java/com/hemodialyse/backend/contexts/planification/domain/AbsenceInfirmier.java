package com.hemodialyse.backend.contexts.planification.domain;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Absence d'un infirmier sur une période (racine d'agrégat, entité unique, RG-INF-030 à 032) : elle se superpose à son roulement.
 * Invariants : période valide, type obligatoire, motif de 255 caractères au plus (blanc = absent). Le non-chevauchement avec les autres
 * absences du même infirmier est une règle d'ensemble, jugée par {@link AbsencesSansChevauchement} sous le verrou de la fiche.
 * L'auteur (compte qui a saisi l'absence) est conservé : un infirmier ne retire que ce qu'il a déclaré lui-même (KB-PLN-39).
 */
public final class AbsenceInfirmier {
    public static final int MOTIF_MAX = 255;

    private final AbsenceId id;
    private final CentreId centre;
    private final InfirmierId infirmier;
    private final PeriodeDAbsence periode;
    private final TypeAbsence type;
    private final String motif;
    private final CompteId auteur;

    private AbsenceInfirmier(AbsenceId id, CentreId centre, InfirmierId infirmier, PeriodeDAbsence periode, TypeAbsence type,
                             String motif, CompteId auteur) {
        if (id == null || centre == null || infirmier == null || periode == null || type == null) {
            throw new SaisieInvalide("Absence incomplète");
        }
        this.id = id;
        this.centre = centre;
        this.infirmier = infirmier;
        this.periode = periode;
        this.type = type;
        this.motif = normaliser(motif);
        this.auteur = auteur;
    }

    /**
     * Le motif est-il utilisable ? (400 sinon) Appelé en premier par les cas d'usage : les entrées illisibles passent avant les 422.
     */
    public static void exigerUnMotifValide(String motif) {
        normaliser(motif);
    }

    private static String normaliser(String motif) {
        if (motif == null || motif.isBlank()) {
            return null;
        }
        String net = motif.trim();
        if (net.length() > MOTIF_MAX) {
            throw new SaisieInvalide("Le motif ne peut pas dépasser " + MOTIF_MAX + " caractères");
        }
        return net;
    }

    /**
     * Une absence saisie par l'administration ou le secrétariat (période quelconque, même passée).
     */
    public static AbsenceInfirmier declarer(AbsenceId id, CentreId centre, InfirmierId infirmier, PeriodeDAbsence periode,
                                            TypeAbsence type, String motif, CompteId auteur) {
        return new AbsenceInfirmier(id, centre, infirmier, periode, type, motif, auteur);
    }

    /**
     * Une absence que l'infirmier déclare lui-même : à venir ou en cours (fin non antérieure à aujourd'hui), {@code ABSENCE_PASSEE} sinon.
     */
    public static AbsenceInfirmier declarerParLInfirmier(AbsenceId id, CentreId centre, InfirmierId infirmier, PeriodeDAbsence periode,
                                                         TypeAbsence type, String motif, CompteId compte, LocalDate aujourdhui) {
        if (compte == null) {
            throw new SaisieInvalide("Compte requis");
        }
        AbsenceInfirmier absence = new AbsenceInfirmier(id, centre, infirmier, periode, type, motif, compte);
        if (periode.fin().isBefore(aujourdhui)) {
            throw new RegleDePlanificationViolee("ABSENCE_PASSEE", "Une absence passée ne peut être saisie que par l'administration");
        }
        return absence;
    }

    /**
     * Une absence relue (aucune règle rejouée).
     */
    public static AbsenceInfirmier reconstituer(AbsenceId id, CentreId centre, InfirmierId infirmier, PeriodeDAbsence periode,
                                                TypeAbsence type, String motif, CompteId auteur) {
        return new AbsenceInfirmier(id, centre, infirmier, periode, type, motif, auteur);
    }

    /**
     * L'infirmier ne retire que ses absences à venir (début strictement postérieur à aujourd'hui) et déclarées par lui ; une absence
     * saisie par l'administration, commencée ou passée relève de l'administration. Une absence sans auteur connu (antérieure à la
     * conservation de l'auteur) reste retirable par l'infirmier concerné.
     */
    public void exigerRetirablePar(CompteId compteDeLInfirmier, LocalDate aujourdhui) {
        if (!periode.debut().isAfter(aujourdhui)) {
            throw new RegleDePlanificationViolee("ABSENCE_NON_ANNULABLE",
                    "Une absence commencée ne peut être retirée que par l'administration");
        }
        if (auteur != null && !auteur.equals(compteDeLInfirmier)) {
            throw new RegleDePlanificationViolee("ABSENCE_NON_ANNULABLE",
                    "Une absence saisie par l'administration ne peut être retirée que par l'administration");
        }
    }

    public boolean couvre(LocalDate date) {
        return periode.couvre(date);
    }

    public AbsenceId id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public InfirmierId infirmier() {
        return infirmier;
    }

    public PeriodeDAbsence periode() {
        return periode;
    }

    public TypeAbsence type() {
        return type;
    }

    /**
     * Le motif, absent si blanc.
     */
    public Optional<String> motif() {
        return Optional.ofNullable(motif);
    }

    /**
     * Le compte qui a saisi l'absence, absent pour une absence antérieure à la conservation de l'auteur.
     */
    public Optional<CompteId> auteur() {
        return Optional.ofNullable(auteur);
    }
}
