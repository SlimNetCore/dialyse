package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.IntervenantVue;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DocumentImprime;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DonneesDuBon;
import com.hemodialyse.backend.contexts.gmao.application.intervention.EvenementVu;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ImpressionDuBon;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.EvenementDeChronologie;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.PrioriteIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ACL vers Documents (documents.v1, PL6) : le bon d'intervention est rendu par le port {@link RenduDeDocument}, type
 * {@value #TYPE_DE_DOCUMENT}, avec le modèle actif du centre (format du modèle). Traduit les données du bon en paramètres du
 * modèle (libellés, dates dans le fuseau demandé, coûts, chronologie) ; DOC5b (ADR 0194) : les lignes de coût sont FOURNIES au
 * gabarit ({@code LIGNES}, {@link #lignesDuBon}), le gabarit n'embarque plus de requête (un modèle stocké qui porte encore l'ancienne
 * requête reçoit les mêmes lignes). Documents pose le papier en-tête. Les refus du rendu (modèle désactivé, génération impossible)
 * remontent tels quels.
 * Reproduit (KB-GMAO-70) : la première impression crée le modèle du centre.
 */
public class BonRenduParDocuments implements ImpressionDuBon {

    public static final String TYPE_DE_DOCUMENT = "BON_INTERVENTION";

    private static final String TYPE_TABLEUR = "spreadsheetml";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final RenduDeDocument documents;

    public BonRenduParDocuments(RenduDeDocument documents) {
        this.documents = documents;
    }

    @Override
    public DocumentImprime imprimer(CentreId centre, DonneesDuBon donnees, ZoneId zone, String editePar) {
        DocumentRendu rendu = documents.rendre(centre.valeur(), TYPE_DE_DOCUMENT, params(donnees, zone, editePar), null);
        return new DocumentImprime(format(rendu.typeMime()), rendu.octets());
    }

    static String format(String typeMime) {
        if (typeMime != null && typeMime.contains(TYPE_TABLEUR)) return "EXCEL";
        if (typeMime != null && typeMime.startsWith("text/html")) return "HTML";
        return "PDF";
    }

    private static void couts(Map<String, Object> p, InterventionVue i) {
        Map<TypeLigneCout, BigDecimal> parType = new HashMap<>();
        for (InterventionVue.LigneVue l : i.lignes()) {
            parType.merge(l.type(), l.montant(), BigDecimal::add);
        }
        p.put("COUT_PIECES", money(parType.getOrDefault(TypeLigneCout.PIECE, BigDecimal.ZERO)));
        p.put("COUT_MAIN_OEUVRE", money(parType.getOrDefault(TypeLigneCout.MAIN_OEUVRE, BigDecimal.ZERO)));
        p.put("COUT_INTERVENANT", money(parType.getOrDefault(TypeLigneCout.INTERVENANT, BigDecimal.ZERO)));
        p.put("COUT_AUTRES", money(parType.getOrDefault(TypeLigneCout.AUTRE, BigDecimal.ZERO)));
        p.put("COUT_TOTAL", money(i.coutTotal()));
    }

    private static String constatTravaux(InterventionVue i) {
        List<String> blocs = new ArrayList<>();
        if (blankToNull(i.symptome()) != null) blocs.add("Panne constatée : " + i.symptome());
        blocs.add("Description : " + i.description());
        if (blankToNull(i.cause()) != null) blocs.add("Cause trouvée : " + i.cause());
        if (blankToNull(i.actions()) != null) blocs.add("Travaux réalisés : " + i.actions());
        if (blankToNull(i.pieceRemplacee()) != null) blocs.add("Pièce remplacée : " + i.pieceRemplacee());
        return String.join("\n", blocs);
    }

    /**
     * Ligne de temps du bon. Décidé (KB-PL6-6, 2026-11-12) : une clôture rejouée par une rectification n'est imprimée qu'une fois
     * (même instant, même auteur) et un motif blanc n'est pas imprimé.
     */
    static String chronologieTexte(List<EvenementVu> events, ZoneId zone) {
        List<String> lignes = new ArrayList<>();
        Set<String> clotures = new HashSet<>();
        for (EvenementVu e : events) {
            if (e.type() == EvenementDeChronologie.Type.TERMINEE && !clotures.add(e.le() + "|" + e.par())) continue;
            lignes.add(stamp(e.le(), zone) + " — " + libelle(e.type())
                    + (e.parNom() == null ? "" : " par " + e.parNom())
                    + (blankToNull(e.detail()) == null ? "" : " — motif : " + e.detail()));
        }
        lignes.add("Heures exprimées dans le fuseau " + zone.getId() + ".");
        return String.join("\n", lignes);
    }

    private static String libelle(EvenementDeChronologie.Type type) {
        return switch (type) {
            case CREEE -> "Créée";
            case DEMARREE -> "Démarrée";
            case TERMINEE -> "Terminée";
            case ANNULEE -> "Annulée";
            case RECTIFIEE -> "Rectifiée";
            case MODIFIEE -> "Dernière modification";
        };
    }

    private static String nomEvenement(List<EvenementVu> events, EvenementDeChronologie.Type type) {
        return events.stream().filter(e -> e.type() == type)
                .map(EvenementVu::parNom).filter(n -> n != null).findFirst().orElse(null);
    }

    private static String type(TypeIntervention t) {
        return switch (t) {
            case PREVENTIVE -> "Maintenance préventive";
            case CURATIVE -> "Maintenance curative";
            case URGENTE -> "Intervention urgente";
            case CONTROLE -> "Contrôle technique";
            case INSTALLATION -> "Installation";
            case DEINSTALLATION -> "Désinstallation";
            case REMPLACEMENT_PIECE -> "Remplacement de pièce";
            case REVISION_COMPLETE -> "Révision complète";
        };
    }

    private static String statut(StatutIntervention s) {
        return switch (s) {
            case PLANIFIEE -> "Planifiée";
            case EN_COURS -> "En cours";
            case TERMINEE -> "Terminée";
            case ANNULEE -> "Annulée";
            case EN_ATTENTE_VALIDATION -> "En attente de validation";
        };
    }

    private static String priorite(PrioriteIntervention p) {
        return switch (p) {
            case NORMALE -> "Normale";
            case HAUTE -> "Haute";
            case URGENTE -> "Urgente";
        };
    }

    private static String etat(StatutEquipement s) {
        if (s == null) return null;
        return switch (s) {
            case EN_SERVICE -> "En service";
            case EN_MAINTENANCE -> "En maintenance";
            case EN_ATTENTE_PIECE -> "En attente de pièce";
            case HORS_SERVICE -> "Hors service";
            case DESACTIF -> "Désactivé";
            case A_REFORMER -> "À réformer";
            case REFORME -> "Réformé";
        };
    }

    private static String marque(String fabricant, String modele) {
        String m = ((fabricant == null ? "" : fabricant) + " " + (modele == null ? "" : modele)).trim();
        return m.isEmpty() ? "" : " (" + m + ")";
    }

    private static String duree(InterventionVue i) {
        if (i.debut() == null || i.fin() == null || !i.fin().isAfter(i.debut())) return null;
        Duration d = Duration.between(i.debut(), i.fin());
        long h = d.toHours();
        long m = d.toMinutesPart();
        return h == 0 ? m + " min" : h + " h " + String.format("%02d", m);
    }

    private static String stamp(Instant t, ZoneId zone) {
        return t == null ? null : t.atZone(zone).format(STAMP);
    }

    private static String money(BigDecimal v) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(v.setScale(2, RoundingMode.HALF_UP)) + " DA";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static Map<String, Object> params(DonneesDuBon donnees, ZoneId zone, String editePar) {
        InterventionVue i = donnees.intervention();
        EquipementVue e = donnees.equipement();
        IntervenantVue intervenant = donnees.intervenant();
        List<EvenementVu> chronologie = donnees.chronologie();
        Map<String, Object> p = new HashMap<>();
        p.put("REPORT_LOCALE", Locale.FRANCE);
        p.put("INTERVENTION_ID", i.id().toString());
        p.put("BON_NUMERO", "BI-" + i.id().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        p.put("BON_TYPE", type(i.type()));
        p.put("BON_STATUT", statut(i.statut()));
        p.put("BON_PRIORITE", priorite(i.priorite()));
        p.put("EQUIPEMENT_LIGNE", e.code() + " — " + e.designation() + marque(e.fabricant(), e.modele()));
        p.put("EQUIPEMENT_SERIE", blankToNull(e.numeroSerie()));
        p.put("INTERVENANT", intervenant == null ? null
                : intervenant.nom() + " (" + (intervenant.nature() == NatureIntervenant.EXTERNE ? "externe" : "interne") + ")");
        p.put("DATE_DEBUT", stamp(i.debut(), zone));
        p.put("DATE_FIN", stamp(i.fin(), zone));
        p.put("DUREE", duree(i));
        p.put("ECHEANCE", stamp(i.echeance(), zone));
        p.put("ETAT_AVANT", etat(i.etatAvant()));
        p.put("ETAT_APRES", etat(i.etatApres()));
        p.put("CONSTAT_TRAVAUX", constatTravaux(i));
        couts(p, i);
        p.put("SIGNATAIRE_CREE", nomEvenement(chronologie, EvenementDeChronologie.Type.CREEE));
        p.put("SIGNATAIRE_CLOTURE", nomEvenement(chronologie, EvenementDeChronologie.Type.TERMINEE));
        p.put("CHRONOLOGIE", chronologieTexte(chronologie, zone));
        p.put("EDITE_LE", stamp(donnees.editeLe(), zone));
        p.put("EDITE_PAR", blankToNull(editePar));
        p.put("LIGNES", lignesDuBon(i));
        return p;
    }

    /**
     * Le jeu de données du bon (DOC5b, ADR 0194) : une ligne par ligne de coût de l'intervention, dans l'ordre croissant des libellés
     * (comme l'ancienne requête, {@code ORDER BY l.libelle} ; à libellé égal, l'ordre de l'intervention). Une intervention SANS ligne
     * fournit UN enregistrement vide : le gabarit imprime alors sa fiche (titre, équipement, constat) comme le faisait la jointure
     * externe de l'ancienne requête ; sans enregistrement, la fiche ne serait pas imprimée.
     */
    static List<Map<String, Object>> lignesDuBon(InterventionVue i) {
        if (i.lignes().isEmpty()) {
            Map<String, Object> vide = new HashMap<>();
            vide.put("LIGNE_TYPE", null);
            vide.put("LIGNE_LIBELLE", null);
            vide.put("LIGNE_QUANTITE", null);
            vide.put("LIGNE_PRIX", null);
            vide.put("LIGNE_MONTANT", null);
            return List.of(vide);
        }
        return i.lignes().stream().sorted(Comparator.comparing(InterventionVue.LigneVue::libelle)).map(l -> {
            Map<String, Object> c = new HashMap<>();
            c.put("LIGNE_TYPE", libelleDuType(l.type()));
            c.put("LIGNE_LIBELLE", l.libelle());
            c.put("LIGNE_QUANTITE", l.quantite());
            c.put("LIGNE_PRIX", l.prixUnitaire());
            c.put("LIGNE_MONTANT", l.montant());
            return c;
        }).toList();
    }

    private static String libelleDuType(TypeLigneCout type) {
        return switch (type) {
            case PIECE -> "Pièce";
            case MAIN_OEUVRE -> "Main d'œuvre";
            case INTERVENANT -> "Honoraires intervenant";
            case AUTRE -> "Autre";
        };
    }
}
