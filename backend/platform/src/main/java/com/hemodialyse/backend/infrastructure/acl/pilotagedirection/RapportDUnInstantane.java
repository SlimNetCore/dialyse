package com.hemodialyse.backend.infrastructure.acl.pilotagedirection;

import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis.AlerteDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis.Clinique;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis.IndicateursDeLaPeriode;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis.IndicateursDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis.Stock;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.SourceDuRapport;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.CaisseDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.CaisseDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.PointMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.Repartition;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.RepartitionParAge;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.RepartitionParSexe;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.StatistiquesDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.TraitementDeLAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.Tranche;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.VueDEnsemble;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Traducteur : le contenu FIGÉ d'un instantané mensuel (PD2, hors du lot A ; JSON {@code overview} + {@code indicators} +
 * {@code breakdown}) vers la source du rapport de synthèse du Pilotage. Lecture tolérante comme le rendu qu'il alimente : un nœud absent
 * ou nul devient une valeur nulle (jamais une erreur) ; une section absente ({@code breakdown} d'un instantané antérieur aux
 * répartitions) devient une section nulle. Les nombres décimaux gardent la représentation du JSON relu (« 66.7 », « 100.0 »).
 */
public final class RapportDUnInstantane {

    private RapportDUnInstantane() {
    }

    public static SourceDuRapport source(String titre, OffsetDateTime genereLe, JsonNode overview, JsonNode indicateurs,
                                         JsonNode repartition) {
        return new SourceDuRapport(titre, genereLe, vue(overview), indicateurs(indicateurs),
                absent(repartition) ? null : repartition(repartition));
    }

    // ───────────────────────────── Lecture tolérante ─────────────────────────────

    private static boolean absent(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull();
    }

    private static String texte(JsonNode n) {
        return absent(n) ? null : n.asString();
    }

    private static Long entier(JsonNode n) {
        return absent(n) ? null : n.asLong();
    }

    private static BigDecimal decimal(JsonNode n) {
        return absent(n) ? null : new BigDecimal(n.asString());
    }

    private static UUID identifiant(JsonNode n) {
        try {
            return absent(n) ? null : UUID.fromString(n.asString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate date(JsonNode n) {
        try {
            return absent(n) ? null : LocalDate.parse(n.asString());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static <T> List<T> liste(JsonNode n, java.util.function.Function<JsonNode, T> lecture) {
        if (absent(n)) return null;
        List<T> elements = new ArrayList<>();
        for (JsonNode element : n) elements.add(lecture.apply(element));
        return elements;
    }

    private static <T> List<T> listeOuVide(JsonNode n, java.util.function.Function<JsonNode, T> lecture) {
        List<T> elements = liste(n, lecture);
        return elements == null ? List.of() : elements;
    }

    // ───────────────────────────── Vue d'ensemble ─────────────────────────────

    private static StatistiquesDuCentre statistiques(JsonNode n) {
        if (absent(n)) return null;
        return new StatistiquesDuCentre(identifiant(n.path("centerId")), texte(n.path("nom")),
                !absent(n.path("actif")) && n.path("actif").asBoolean(), entier(n.path("patients")), entier(n.path("patientsSousKt")),
                entier(n.path("seances")), entier(n.path("factures")), decimal(n.path("caHt")), decimal(n.path("caTtc")),
                decimal(n.path("encaisse")), decimal(n.path("resteARecouvrer")), decimal(n.path("tauxEncaissement")));
    }

    private static VueDEnsemble vue(JsonNode o) {
        return new VueDEnsemble(identifiant(o.path("societeId")), texte(o.path("societeNom")), date(o.path("from")), date(o.path("to")),
                null, absent(o.path("seuilAnonymat")) ? null : o.path("seuilAnonymat").asInt(),
                liste(o.path("centres"), RapportDUnInstantane::statistiques), statistiques(o.path("totaux")),
                liste(o.path("mensuel"), p -> new PointMensuel(texte(p.path("mois")), identifiant(p.path("centerId")),
                        entier(p.path("seances")), decimal(p.path("caHt")), decimal(p.path("caTtc")))), null);
    }

    // ───────────────────────────── Indicateurs ─────────────────────────────

    private static BigDecimal cible(JsonNode clinique, String marqueur) {
        return decimal(clinique.path(marqueur).path("pctDansCible"));
    }

    private static IndicateursDuCentre centre(JsonNode c) {
        JsonNode k = c.path("clinique");
        JsonNode s = c.path("stock");
        return new IndicateursDuCentre(texte(c.path("nom")),
                new Clinique(cible(k, "ktV"), cible(k, "hemoglobine"), cible(k, "phosphore"), cible(k, "pth"), cible(k, "albumine"),
                        entier(k.path("vhbPositifs")), entier(k.path("vhcPositifs")), entier(k.path("vihPositifs")),
                        entier(k.path("patientsObservanceEnRetard")), entier(k.path("greffeListeAttente")),
                        entier(k.path("greffeBilanEnCours")), entier(k.path("greffesPeriode"))),
                new Stock(entier(s.path("articlesActifs")), entier(s.path("articlesSousSeuil")), entier(s.path("lotsPerimes")),
                        entier(s.path("lotsPeremptionProche")), decimal(s.path("valeurStock"))));
    }

    private static IndicateursDeLaPeriode indicateurs(JsonNode i) {
        return new IndicateursDeLaPeriode(liste(i.path("centres"), RapportDUnInstantane::centre), centre(i.path("totaux")),
                listeOuVide(i.path("alertes"), a -> new AlerteDeLaDirection(texte(a.path("centre")), texte(a.path("code")),
                        texte(a.path("severity")), decimal(a.path("valeur")))));
    }

    // ───────────────────────────── Répartitions ─────────────────────────────

    private static Repartition repartition(JsonNode b) {
        return new Repartition(identifiant(b.path("societeId")), date(b.path("from")), date(b.path("to")), null, null,
                listeOuVide(b.path("sexe"), r -> new RepartitionParSexe(identifiant(r.path("centerId")), texte(r.path("nom")),
                        entier(r.path("masculin")), entier(r.path("feminin")), entier(r.path("autre")))),
                listeOuVide(b.path("ages"), r -> new RepartitionParAge(identifiant(r.path("centerId")), texte(r.path("nom")),
                        listeOuVide(r.path("tranches"), t -> new Tranche(texte(t.path("code")), entier(t.path("count")))))),
                listeOuVide(b.path("caisses"), r -> new CaisseDuCentre(identifiant(r.path("centerId")), texte(r.path("centre")),
                        texte(r.path("caisseCode")), texte(r.path("caisse")), entier(r.path("patients")), entier(r.path("seances")),
                        decimal(r.path("caHt")))),
                listeOuVide(b.path("caisseTotaux"), t -> new CaisseDeLaSociete(texte(t.path("caisseCode")), texte(t.path("caisse")),
                        entier(t.path("patients")), entier(t.path("seances")), decimal(t.path("caHt")))),
                listeOuVide(b.path("anemie"), r -> new TraitementDeLAnemie(identifiant(r.path("centerId")), texte(r.path("nom")),
                        entier(r.path("patientsEpo")), entier(r.path("patientsFer")), entier(r.path("administreesEpo")),
                        entier(r.path("administreesFer")), entier(r.path("nonAdministrees")), decimal(r.path("tauxAdministration")),
                        entier(r.path("patientsSousEpo")), entier(r.path("patientsSousFer")))));
    }
}
