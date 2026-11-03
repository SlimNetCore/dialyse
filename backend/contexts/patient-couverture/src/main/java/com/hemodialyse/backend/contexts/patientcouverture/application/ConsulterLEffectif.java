package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.ResumeDesPatients.Detail;
import com.hemodialyse.backend.contexts.patientcouverture.application.ResumeDesPatients.LigneDuDetail;
import com.hemodialyse.backend.contexts.patientcouverture.application.ResumeDesPatients.Tranche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Synthèse et détail de l'effectif d'un mois (RG-PAT-032) : un patient est compté s'il est admis au plus tard en fin de mois et
 * n'était pas déjà sorti avant le premier jour (dernier jour occupé de l'état, voir {@link EtatDuPatient#dernierJourOccupe}).
 * Garde de centre sur la lecture ; sans mois, le mois courant du jour du centre.
 */
public class ConsulterLEffectif {

    private final SessionCourante session;
    private final LectureDesPatients lecture;
    private final HorlogeDesCentres horloge;

    public ConsulterLEffectif(SessionCourante session, LectureDesPatients lecture, HorlogeDesCentres horloge) {
        this.session = session;
        this.lecture = lecture;
        this.horloge = horloge;
    }

    /** Mois demandé, ou à défaut le mois courant du centre de la session (utile à la clé du cache de l'interface). */
    public YearMonth mois(UUID centreDemande, YearMonth demande) {
        CentreId centre = session.centre(centreDemande);
        return demande != null ? demande : YearMonth.from(horloge.aujourdhui(centre));
    }

    public ResumeDesPatients resume(UUID centreDemande, YearMonth demande) {
        CentreId centre = session.centre(centreDemande);
        YearMonth mois = demande != null ? demande : YearMonth.from(horloge.aujourdhui(centre));
        Map<String, Long> sexe = compteur("M", "F", "AUTRE");
        Map<String, Long> age = compteur("0_17", "18_29", "30_44", "45_59", "60_PLUS", "INCONNU");
        Map<String, Long> kt = compteur("OUI", "NON");
        LocalDate reference = mois.atEndOfMonth();
        long total = 0;
        for (LigneDEffectif ligne : lecture.effectifDuCentre(centre)) {
            if (!compte(ligne, mois)) continue;
            total++;
            sexe.computeIfPresent(trancheDeSexe(ligne.sexe()), (c, n) -> n + 1);
            age.computeIfPresent(tranchedAge(ligne.dateNaissance(), reference), (c, n) -> n + 1);
            kt.computeIfPresent(tranchedeKt(ligne.sousKt()), (c, n) -> n + 1);
        }
        return new ResumeDesPatients(total,
                tranches(sexe, "M", "Masculin", "F", "Féminin", "AUTRE", "Autre / inconnu"),
                tranches(age, "0_17", "0-17 ans", "18_29", "18-29 ans", "30_44", "30-44 ans", "45_59", "45-59 ans",
                        "60_PLUS", "60 ans et +", "INCONNU", "Non renseigné"),
                tranches(kt, "OUI", "Sous KT", "NON", "Sans KT"));
    }

    public Detail detail(UUID centreDemande, YearMonth demande) {
        CentreId centre = session.centre(centreDemande);
        YearMonth mois = demande != null ? demande : YearMonth.from(horloge.aujourdhui(centre));
        List<LigneDuDetail> lignes = new ArrayList<>();
        for (LigneDEffectif l : lecture.effectifDuCentre(centre)) {
            if (!compte(l, mois)) continue;
            String etat = etatNormalise(l.etatPatient());
            String raison = "PERMANENT".equals(etat) ? "PERMANENT"
                    : l.dateEvenementEtat() != null && YearMonth.from(l.dateEvenementEtat()).equals(mois) ? "EVENT_MONTH"
                    : "ACTIVE_PERIOD";
            lignes.add(new LigneDuDetail(l.id(), affichage(l.codePatient()), affichage(l.nom()), affichage(l.prenom()),
                    trancheDeSexe(l.sexe()), etat, l.dateEvenementEtat(), l.dateAdmission(),
                    "OUI".equals(tranchedeKt(l.sousKt())), raison));
        }
        lignes.sort(Comparator.comparing((LigneDuDetail i) -> Objects.toString(i.nom(), ""))
                .thenComparing(i -> Objects.toString(i.prenom(), ""))
                .thenComparing(i -> Objects.toString(i.codePatient(), "")));
        return new Detail(mois.toString(), lignes.size(), lignes);
    }

    private static boolean compte(LigneDEffectif l, YearMonth mois) {
        if (l.dateAdmission() != null && l.dateAdmission().isAfter(mois.atEndOfMonth())) return false;
        return etatLu(l.etatPatient()).dernierJourOccupe(l.dateEvenementEtat())
                .map(dernier -> !dernier.isBefore(mois.atDay(1))).orElse(true);
    }

    /** État de la colonne ; nul, vide ou hors liste (ligne héritée) : sans limite de place, comme {@code PERMANENT}. */
    private static EtatDuPatient etatLu(String brut) {
        try {
            return EtatDuPatient.valueOf(etatNormalise(brut));
        } catch (IllegalArgumentException e) {
            return EtatDuPatient.PERMANENT;
        }
    }

    private static String etatNormalise(String brut) {
        String valeur = brut == null ? "" : brut.trim().toUpperCase(Locale.ROOT);
        return valeur.isBlank() ? "PERMANENT" : valeur;
    }

    private static String affichage(String brut) {
        return brut == null ? "" : brut.trim();
    }

    private static String trancheDeSexe(String brut) {
        String valeur = brut == null ? "" : brut.trim().toUpperCase(Locale.ROOT);
        return switch (valeur) {
            case "M", "MASCULIN", "H", "HOMME" -> "M";
            case "F", "FEMININ", "FÉMININ", "FEMME" -> "F";
            default -> "AUTRE";
        };
    }

    private static String tranchedAge(LocalDate naissance, LocalDate reference) {
        if (naissance == null || naissance.isAfter(reference)) return "INCONNU";
        int age = Period.between(naissance, reference).getYears();
        if (age < 18) return "0_17";
        if (age < 30) return "18_29";
        if (age < 45) return "30_44";
        if (age < 60) return "45_59";
        return "60_PLUS";
    }

    private static String tranchedeKt(Boolean sousKt) {
        return Boolean.TRUE.equals(sousKt) ? "OUI" : "NON";
    }

    private static Map<String, Long> compteur(String... codes) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String c : codes) m.put(c, 0L);
        return m;
    }

    private static List<Tranche> tranches(Map<String, Long> comptes, String... codesEtLibelles) {
        List<Tranche> tranches = new ArrayList<>();
        for (int i = 0; i < codesEtLibelles.length; i += 2) {
            tranches.add(new Tranche(codesEtLibelles[i], codesEtLibelles[i + 1], comptes.getOrDefault(codesEtLibelles[i], 0L)));
        }
        return tranches;
    }
}
