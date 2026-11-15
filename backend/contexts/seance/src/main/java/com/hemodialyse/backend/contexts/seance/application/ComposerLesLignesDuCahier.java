package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.application.DossierEtTraitementDuPatient.Dossier;
import com.hemodialyse.backend.contexts.seance.application.DossierEtTraitementDuPatient.Medicament;
import com.hemodialyse.backend.contexts.seance.application.DossierEtTraitementDuPatient.Traitement;
import com.hemodialyse.backend.contexts.seance.application.SeancesImprimablesDuCahier.SeanceDuCahier;
import com.hemodialyse.backend.contexts.seance.application.SeancesImprimablesDuCahier.VoletMedicalBrut;
import com.hemodialyse.backend.contexts.seance.application.SeancesImprimablesDuCahier.VoletParamedicalBrut;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitId;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PeriodeDuCahier;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compose le jeu de données du cahier de dialyse imprimé (DOC5a, ADR 0193) : jusqu'ici le gabarit {@code cahier_dialyse.jrxml} lisait lui-même
 * les tables de six contextes par une requête SQL embarquée ; la Séance, propriétaire du cahier, compose désormais les mêmes lignes par ses
 * ports (ses séances et volets, le forfait de Patient et couverture, les sorties du Stock, le dossier et la prescription du Dossier médical)
 * et applique la MÊME mise en forme que la requête (tiret pour une valeur absente, virgule décimale, libellés). Une ligne par consommable ; une
 * séance sans consommable donne une ligne sans article. Le volet médical, le dossier et la prescription ne sont même pas lus quand le profil
 * n'y a pas droit (KB-PL5-4).
 */
public class ComposerLesLignesDuCahier {

    private static final String TIRET = "-";

    private final SeancesImprimablesDuCahier seances;
    private final ForfaitDeLaPriseEnCharge priseEnCharge;
    private final ForfaitsDuCentre referentiel;
    private final SortiesDeLaSeance sorties;
    private final DossierEtTraitementDuPatient medical;

    public ComposerLesLignesDuCahier(SeancesImprimablesDuCahier seances, ForfaitDeLaPriseEnCharge priseEnCharge,
                                     ForfaitsDuCentre referentiel, SortiesDeLaSeance sorties, DossierEtTraitementDuPatient medical) {
        this.seances = seances;
        this.priseEnCharge = priseEnCharge;
        this.referentiel = referentiel;
        this.sorties = sorties;
        this.medical = medical;
    }

    public List<LigneDuCahier> composer(CentreId centre, PatientId patient, PeriodeDuCahier periode, boolean voletMedicalVisible) {
        List<SeanceDuCahier> imprimables = seances.lister(centre, patient, periode, voletMedicalVisible);
        Optional<Dossier> dossier = voletMedicalVisible && !imprimables.isEmpty() ? medical.dossier(centre, patient) : Optional.empty();
        List<LigneDuCahier> lignes = new ArrayList<>();
        for (SeanceDuCahier s : imprimables) {
            Optional<Traitement> traitement = voletMedicalVisible ? medical.traitementEnVigueur(centre, patient, s.jour()) : Optional.empty();
            Forfait forfait = forfait(centre, patient, s);
            List<SortiesDeLaSeance.Sortie> consommables = sorties.deLaSeance(centre, s.seance());
            if (consommables.isEmpty()) {
                lignes.add(ligne(s, forfait, null, dossier, traitement));
            } else {
                for (SortiesDeLaSeance.Sortie sortie : consommables) lignes.add(ligne(s, forfait, sortie, dossier, traitement));
            }
        }
        return lignes;
    }

    private record Forfait(String code, String nom, BigDecimal prix) {
    }

    /** Le forfait imposé prime (code et nom recopiés, à défaut ceux du référentiel ; le prix est celui de la séance), sinon celui de la prise en charge. */
    private Forfait forfait(CentreId centre, PatientId patient, SeanceDuCahier s) {
        if (s.forfaitImpose() != null) {
            var impose = s.forfaitImpose();
            Optional<ForfaitDuCentre> reference = referentiel.trouver(centre, new ForfaitId(impose.forfait()));
            return new Forfait(impose.code() != null ? impose.code() : reference.map(ForfaitDuCentre::code).orElse(null),
                    impose.nom() != null ? impose.nom() : reference.map(ForfaitDuCentre::nom).orElse(null), impose.prix());
        }
        return priseEnCharge.duJour(centre, patient, s.jour()).map(f -> new Forfait(f.code(), f.nom(), f.prix()))
                .orElse(new Forfait(null, null, null));
    }

    private static LigneDuCahier ligne(SeanceDuCahier s, Forfait forfait, SortiesDeLaSeance.Sortie sortie, Optional<Dossier> dossier,
                                       Optional<Traitement> traitement) {
        VoletParamedicalBrut p = s.paramedical();
        VoletMedicalBrut m = s.medical();
        Dossier d = dossier.orElse(null);
        Traitement t = traitement.orElse(null);
        return new LigneDuCahier(s.seance().valeur().toString(), s.jour(), statut(s.statut()), forfait.code(), forfait.nom(), forfait.prix(),
                p == null ? TIRET : decimal(p.poidsAvantKg()), p == null ? TIRET : decimal(p.poidsApresKg()),
                p == null ? TIRET : texte(p.tensionAvant()), p == null ? TIRET : texte(p.tensionApres()),
                p == null ? TIRET : entier(p.dureeMinutes()), p == null ? TIRET : entier(p.debitSangMlMin()),
                p == null ? TIRET : decimal(p.ultrafiltrationMl()), p == null ? TIRET : texte(p.typeDeDialysat()),
                p == null ? TIRET : texte(p.anticoagulant()), p == null ? TIRET : texte(p.incidents()),
                sortie == null ? null : sortie.code(), sortie == null ? null : sortie.libelle(), sortie == null ? null : sortie.unite(),
                sortie == null ? null : sortie.quantite(), sortie == null ? null : sortie.prixUnitaire(),
                sortie == null ? null : sortie.totalValorise(),
                d == null ? TIRET : texte(d.nephropathieInitiale()), d == null ? null : d.dateMiseEnDialyse(),
                d == null ? TIRET : hepatite(d.statutHepatiteB()), d == null ? TIRET : hepatite(d.statutHepatiteC()),
                d == null ? TIRET : texte(d.observationGlobale()), d == null ? TIRET : texte(d.conclusionMedicale()),
                t == null || t.poidsSecCibleKg() == null ? TIRET : decimal(t.poidsSecCibleKg()) + " kg",
                t == null ? TIRET : traitement(t.epo(), "UI"), t == null ? TIRET : traitement(t.fer(), "mg"),
                m == null ? TIRET : texte(m.prescription()), m == null ? TIRET : texte(m.tolerance()),
                m == null ? TIRET : texte(m.examenClinique()), m == null ? TIRET : texte(m.resultatsBiologiques()),
                m == null ? TIRET : texte(m.ajustementsTherapeutiques()), m == null ? TIRET : texte(m.conclusionMedicale()));
    }

    // ------------------------------------------------------------------ mise en forme (celle de l'ancienne requête)

    /** Texte sans les espaces de début et de fin ; tiret s'il est absent ou blanc (seul l'espace est retiré, comme le TRIM du SQL). */
    static String texte(String valeur) {
        if (valeur == null) return TIRET;
        int debut = 0;
        int fin = valeur.length();
        while (debut < fin && valeur.charAt(debut) == ' ') debut++;
        while (fin > debut && valeur.charAt(fin - 1) == ' ') fin--;
        return debut == fin ? TIRET : valeur.substring(debut, fin);
    }

    static String entier(Integer valeur) {
        return valeur == null ? TIRET : valeur.toString();
    }

    /** Décimal sans zéros inutiles à droite de la virgule, virgule décimale ; tiret s'il est absent. */
    static String decimal(BigDecimal valeur) {
        if (valeur == null) return TIRET;
        String brut = valeur.toPlainString();
        if (brut.indexOf('.') >= 0) {
            int fin = brut.length();
            while (fin > 0 && brut.charAt(fin - 1) == '0') fin--;
            if (fin > 0 && brut.charAt(fin - 1) == '.') fin--;
            brut = brut.substring(0, fin);
        }
        return brut.replace('.', ',');
    }

    static String statut(String statut) {
        return switch (statut) {
            case "VALIDEE" -> "Validée";
            case "SIGNEE" -> "Signée";
            case "FACTUREE" -> "Facturée";
            default -> statut;
        };
    }

    static String hepatite(String statut) {
        if (statut == null) return TIRET;
        return switch (statut) {
            case "NEGATIF" -> "Négatif";
            case "PORTEUR" -> "Porteur";
            case "VACCINE" -> "Vacciné";
            case "IMMUNE" -> "Immunisé";
            case "INCONNU" -> "Inconnu";
            default -> texte(statut);
        };
    }

    /** « Article — dose unité voie fréquence/période » ; l'article seul si la dose est absente ou nulle ; tiret si aucun article. */
    static String traitement(Medicament medicament, String uniteDeDose) {
        String article = medicament == null || medicament.article() == null ? TIRET : medicament.article();
        if (medicament == null || medicament.dose() == null || medicament.dose() <= 0) return article;
        return article + " — " + medicament.dose() + " " + uniteDeDose + " " + vide(medicament.voie()) + " "
                + (medicament.frequenceValeur() == null ? "" : medicament.frequenceValeur()) + "/" + periode(medicament.frequenceUnite());
    }

    private static String periode(String unite) {
        if (unite == null) return "";
        return switch (unite) {
            case "HEURE" -> "heure";
            case "JOUR" -> "jour";
            case "SEMAINE" -> "semaine";
            case "MOIS" -> "mois";
            case "ANNEE" -> "année";
            default -> unite;
        };
    }

    private static String vide(String valeur) {
        return valeur == null ? "" : valeur;
    }
}
