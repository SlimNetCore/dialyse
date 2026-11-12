package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.PageDuRapport.Genre;
import com.hemodialyse.backend.contexts.pilotagedirection.application.PageDuRapport.Ligne;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Alerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Caisses;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Finances;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneClinique;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneDeCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneFinanciere;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneStock;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Mois;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Rapport;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Repartitions;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Synthese;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Mise en forme du rapport de synthèse de la direction pour l'impression (PL9, ADR 0148). Règles :
 * <ul>
 *   <li>effectif masqué : « &lt; seuil » ; autre valeur absente : « — » ;</li>
 *   <li>montants : {@link BigDecimal}, arrondi à la paire (HALF_EVEN) sans décimale, séparateur de milliers insécable, suffixe « DA »
 *       (KB-PL9-7 corrigé ; nombre de décimales à confirmer par les métiers avant le 2026-12-15, {@link #DECIMALES_DES_MONTANTS}) ;</li>
 *   <li>horodatage : fuseau d'Alger (KB-PL9-1 corrigé) ;</li>
 *   <li>matrice des caisses : paginée par groupes de {@link #CENTRES_PAR_TABLEAU} centres, la colonne « Total société » est répétée dans
 *       chaque tableau (KB-PL9-2 corrigé).</li>
 * </ul>
 */
public final class MiseEnPageDuRapport {

    /** Décimales imprimées pour un montant (question ouverte métiers, 2026-12-15). */
    public static final int DECIMALES_DES_MONTANTS = 0;
    /** Centres par tableau de la matrice des caisses : une cellule de caisse, ce nombre de centres, une cellule de total, 13 au plus. */
    public static final int CENTRES_PAR_TABLEAU = 10;
    static final String INSECABLE = " ";
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<String, String> TRANCHES = new LinkedHashMap<>();

    static {
        TRANCHES.put("0_17", "0-17 ans");
        TRANCHES.put("18_29", "18-29 ans");
        TRANCHES.put("30_44", "30-44 ans");
        TRANCHES.put("45_59", "45-59 ans");
        TRANCHES.put("60_PLUS", "60 ans et +");
        TRANCHES.put("INCONNU", "Non renseigné");
    }

    private MiseEnPageDuRapport() {
    }

    /**
     * @param rapport  le rapport constitué par le Pilotage
     * @param societeImprimee la raison sociale du papier en-tête (vide : celle du rapport)
     * @param fuseau   le fuseau des horodatages (Africa/Algiers)
     */
    public static PageDuRapport mettreEnPage(Rapport rapport, String societeImprimee, ZoneId fuseau) {
        String societe = societeImprimee != null && !societeImprimee.isBlank() ? societeImprimee : texte(rapport.societeNom());
        String horodatage = rapport.genereLe() == null ? "—" : horodatage(rapport.genereLe(), fuseau);
        String seuil = texte(rapport.seuilAnonymat());
        String contexte = societe + " — période " + texte(rapport.debut()) + " au " + texte(rapport.fin()) + " — données au " + horodatage
                + " — agrégats anonymes (seuil d'anonymat : " + seuil + ")";
        String note = "Les effectifs de moins de " + seuil + " patients sont masqués (« < seuil ») et les taux calculés sur moins de "
                + "patients évalués ne sont pas publiés. Cibles cliniques : KDIGO (Kt/V >= 1,2 ; hémoglobine 10 à 11,5 g/dL ; "
                + "phosphore 2,5 à 4,5 mg/dL ; PTH 130 à 600 pg/mL ; albumine >= 4 g/dL).";
        List<Ligne> lignes = new ArrayList<>();
        synthese(lignes, rapport.synthese());
        alertes(lignes, rapport.alertes());
        finances(lignes, rapport.finances());
        evolution(lignes, rapport.evolution());
        qualite(lignes, rapport);
        stock(lignes, rapport);
        repartitions(lignes, rapport.repartitions());
        return new PageDuRapport(texte(rapport.titre()), societe, contexte, horodatage, note, lignes);
    }

    // ───────────────────────────── Formats ─────────────────────────────

    public static String horodatage(OffsetDateTime instant, ZoneId fuseau) {
        return instant.atZoneSameInstant(fuseau).format(HORODATAGE);
    }

    static String texte(Object n) {
        return n == null ? "—" : n.toString();
    }

    /** Effectif de patients : masqué (« < seuil ») quand l'anonymat l'impose. */
    static String effectif(Long n) {
        return n == null ? "< seuil" : n.toString();
    }

    static String nombre(Object n) {
        return n == null ? "—" : n.toString();
    }

    static String pourcentage(BigDecimal n) {
        return n == null ? "—" : n.toString() + " %";
    }

    static String montant(BigDecimal n) {
        if (n == null) return "—";
        BigDecimal arrondi = n.setScale(DECIMALES_DES_MONTANTS, RoundingMode.HALF_EVEN);
        String[] parties = arrondi.abs().toPlainString().split("\\.");
        StringBuilder entiere = new StringBuilder();
        String chiffres = parties[0];
        for (int i = 0; i < chiffres.length(); i++) {
            if (i > 0 && (chiffres.length() - i) % 3 == 0) entiere.append(INSECABLE);
            entiere.append(chiffres.charAt(i));
        }
        String signe = arrondi.signum() < 0 ? "-" : "";
        String decimales = parties.length > 1 ? "," + parties[1] : "";
        return signe + entiere + decimales + INSECABLE + "DA";
    }

    private static BigDecimal enDecimal(double valeur) {
        return BigDecimal.valueOf(valeur);
    }

    private static String partDuMaximum(BigDecimal valeur, BigDecimal maximum) {
        if (valeur == null || maximum.signum() <= 0) return "0 %";
        BigDecimal part = valeur.multiply(BigDecimal.valueOf(100)).divide(maximum, 0, RoundingMode.HALF_EVEN);
        return part.min(BigDecimal.valueOf(100)).toPlainString() + " %";
    }

    // ───────────────────────────── Sections ─────────────────────────────

    private static void tableau(List<Ligne> sortie, String section, String titre, List<String> entete) {
        sortie.add(new Ligne(section, titre, Genre.ENTETE, entete));
    }

    private static void ligne(List<Ligne> sortie, String section, Genre genre, List<String> cellules) {
        sortie.add(new Ligne(section, "", genre, cellules));
    }

    private static void synthese(List<Ligne> sortie, Synthese t) {
        if (t == null) return;
        tableau(sortie, "01", "", List.of("Patients", "Séances", "Factures", "CA HT", "CA TTC", "Encaissé", "Reste à recouvrer",
                "Taux d'encaissement"));
        ligne(sortie, "01", Genre.LIGNE, List.of(effectif(t.patients()), nombre(t.seances()), nombre(t.factures()), montant(t.caHt()),
                montant(t.caTtc()), montant(t.encaisse()), montant(t.resteARecouvrer()), pourcentage(t.tauxEncaissement())));
    }

    private static void alertes(List<Ligne> sortie, List<Alerte> alertes) {
        boolean premiere = true;
        for (Alerte a : alertes) {
            String phrase = texte(a.centre()) + " — " + a.libelle() + (a.valeur() == null ? "" : " (" + a.valeur() + ")");
            sortie.add(new Ligne("02", premiere ? "Alertes" : "", a.critique() ? Genre.TEXTE_CRITIQUE : Genre.TEXTE, List.of(phrase)));
            premiere = false;
        }
    }

    private static void finances(List<Ligne> sortie, Finances finances) {
        if (finances == null) return;
        BigDecimal maximum = BigDecimal.ZERO;
        for (LigneFinanciere c : finances.centres()) {
            if (c.caTtc() != null) maximum = maximum.max(c.caTtc());
        }
        tableau(sortie, "03", "Finances et activité par centre", List.of("Centre", "Patients", "Dont sous KT", "Séances", "Factures",
                "CA HT", "CA TTC", "Encaissé", "Reste à recouvrer", "Taux", "Part du CA"));
        for (LigneFinanciere c : finances.centres()) {
            ligne(sortie, "03", Genre.LIGNE, financiere(texte(c.nom()), c, partDuMaximum(c.caTtc(), maximum)));
        }
        ligne(sortie, "03", Genre.TOTAL, financiere("Total société", finances.total(), ""));
    }

    private static List<String> financiere(String nom, LigneFinanciere c, String part) {
        return List.of(nom, effectif(c.patients()), effectif(c.patientsSousKt()), nombre(c.seances()), nombre(c.factures()),
                montant(c.caHt()), montant(c.caTtc()), montant(c.encaisse()), montant(c.resteARecouvrer()),
                pourcentage(c.tauxEncaissement()), part);
    }

    private static void evolution(List<Ligne> sortie, List<Mois> mois) {
        if (mois.isEmpty()) return;
        BigDecimal maximum = mois.stream().map(m -> enDecimal(m.caTtc())).reduce(BigDecimal.ZERO, BigDecimal::max);
        tableau(sortie, "04", "Évolution mensuelle", List.of("Mois", "Séances", "CA HT", "CA TTC", "CA TTC (part du mois le plus élevé)"));
        for (Mois m : mois) {
            ligne(sortie, "04", Genre.LIGNE, List.of(texte(m.mois()), String.valueOf(m.seances()), montant(enDecimal(m.caHt())),
                    montant(enDecimal(m.caTtc())), partDuMaximum(enDecimal(m.caTtc()), maximum)));
        }
    }

    private static void qualite(List<Ligne> sortie, Rapport rapport) {
        if (rapport.qualite() == null) return;
        tableau(sortie, "05", "Qualité des soins — part des patients dans la cible KDIGO", List.of("Centre", "Kt/V", "Hb", "Phosphore",
                "PTH", "Albumine", "AgHBs +", "Anti-VHC +", "VIH +", "Traitement en retard", "Attente greffe", "Bilans greffe en cours",
                "Greffes (période)"));
        for (LigneClinique c : rapport.qualite().centres()) ligne(sortie, "05", Genre.LIGNE, clinique(texte(c.nom()), c));
        ligne(sortie, "05", Genre.TOTAL, clinique("Total société", rapport.qualite().total()));
    }

    private static List<String> clinique(String nom, LigneClinique k) {
        return List.of(nom, pourcentage(k.ktV()), pourcentage(k.hemoglobine()), pourcentage(k.phosphore()), pourcentage(k.pth()),
                pourcentage(k.albumine()), effectif(k.vhbPositifs()), effectif(k.vhcPositifs()), effectif(k.vihPositifs()),
                effectif(k.patientsObservanceEnRetard()), effectif(k.greffeListeAttente()), effectif(k.greffeBilanEnCours()),
                effectif(k.greffesPeriode()));
    }

    private static void stock(List<Ligne> sortie, Rapport rapport) {
        if (rapport.stock() == null) return;
        tableau(sortie, "06", "Stock", List.of("Centre", "Articles actifs", "Sous le seuil", "Lots périmés", "Péremption < 90 j",
                "Valeur du stock"));
        for (LigneStock s : rapport.stock().centres()) ligne(sortie, "06", Genre.LIGNE, stock(texte(s.nom()), s));
        ligne(sortie, "06", Genre.TOTAL, stock("Total société", rapport.stock().total()));
    }

    private static List<String> stock(String nom, LigneStock s) {
        return List.of(nom, nombre(s.articlesActifs()), nombre(s.articlesSousSeuil()), nombre(s.lotsPerimes()),
                nombre(s.lotsPeremptionProche()), montant(s.valeurStock()));
    }

    private static void repartitions(List<Ligne> sortie, Repartitions b) {
        if (b == null) return; // instantané antérieur aux répartitions : rien à afficher
        tableau(sortie, "07", "Répartition des patients par sexe et par âge", List.of("Centre", "Hommes", "Femmes", "Autre / inconnu"));
        b.sexe().forEach(r -> ligne(sortie, "07", Genre.LIGNE,
                List.of(texte(r.nom()), effectif(r.masculin()), effectif(r.feminin()), effectif(r.autre()))));
        List<String> enteteAge = new ArrayList<>();
        enteteAge.add("Centre");
        enteteAge.addAll(TRANCHES.values());
        tableau(sortie, "08", "", enteteAge);
        b.ages().forEach(r -> {
            List<String> cellules = new ArrayList<>();
            cellules.add(texte(r.nom()));
            for (String code : TRANCHES.keySet()) {
                cellules.add(r.tranches().containsKey(code) ? effectif(r.tranches().get(code)) : "—");
            }
            ligne(sortie, "08", Genre.LIGNE, cellules);
        });
        caisses(sortie, b.caisses());
        anemie(sortie, b);
    }

    private static void caisses(List<Ligne> sortie, Caisses caisses) {
        if (caisses == null) return;
        boolean premier = true;
        int centres = caisses.centres().size();
        int groupes = Math.max(1, (centres + CENTRES_PAR_TABLEAU - 1) / CENTRES_PAR_TABLEAU);
        record Mesure(String nom, Function<LigneDeCaisse, List<String>> parCentre, Function<LigneDeCaisse, String> total) {
        }
        List<Mesure> mesures = List.of(
                new Mesure("Patients", l -> l.patients().stream().map(MiseEnPageDuRapport::effectif).toList(),
                        l -> effectif(l.totalPatients())),
                new Mesure("Séances", l -> l.seances().stream().map(MiseEnPageDuRapport::nombre).toList(),
                        l -> nombre(l.totalSeances())),
                new Mesure("CA HT", l -> l.caHt().stream().map(MiseEnPageDuRapport::montant).toList(),
                        l -> montant(l.totalCaHt())));
        int numero = 0;
        for (Mesure mesure : mesures) {
            for (int g = 0; g < groupes; g++) {
                int debut = g * CENTRES_PAR_TABLEAU;
                int fin = Math.min(centres, debut + CENTRES_PAR_TABLEAU);
                String section = "09" + String.format("%02d", numero++);
                List<String> entete = new ArrayList<>();
                entete.add(mesure.nom() + " — caisse");
                entete.addAll(caisses.centres().subList(debut, fin));
                entete.add("Total société");
                tableau(sortie, section, premier ? "Caisses d'assurance par centre" : "", entete);
                premier = false;
                for (LigneDeCaisse l : caisses.lignes()) {
                    List<String> cellules = new ArrayList<>();
                    cellules.add(l.caisse());
                    cellules.addAll(mesure.parCentre().apply(l).subList(debut, fin));
                    cellules.add(mesure.total().apply(l));
                    ligne(sortie, section, Genre.LIGNE, cellules);
                }
            }
        }
    }

    private static void anemie(List<Ligne> sortie, Repartitions b) {
        if (b.anemie().isEmpty()) return;
        tableau(sortie, "10", "Traitement de l'anémie par centre", List.of("Centre", "Sous EPO (prescrit)", "Sous fer (prescrit)",
                "Patients traités EPO", "Patients traités fer", "Administrations EPO", "Administrations fer", "Non administrées",
                "Taux d'administration"));
        b.anemie().forEach(r -> ligne(sortie, "10", Genre.LIGNE, List.of(texte(r.nom()), effectif(r.patientsSousEpo()),
                effectif(r.patientsSousFer()), effectif(r.patientsEpo()), effectif(r.patientsFer()), nombre(r.administreesEpo()),
                nombre(r.administreesFer()), nombre(r.nonAdministrees()), pourcentage(r.tauxAdministration()))));
    }
}
