package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.IntervenantVue;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DocumentImprime;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DonneesDuBon;
import com.hemodialyse.backend.contexts.gmao.application.intervention.EvenementVu;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ImpressionDuBon;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.EvenementDeChronologie;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.PrioriteIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;
import net.sf.jasperreports.engine.JRParameter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Impression du bon d'intervention GMAO : adaptateur du port {@link ImpressionDuBon} du module GMAO. Le modèle de document
 * {@value ModeleDocumentCatalog#BON_INTERVENTION} est imprimé via {@link ModeleDocumentPrinter} (version personnalisée du
 * centre si elle existe, identité société + centre posée par le serveur). Les lignes de coût sont lues par la requête du
 * modèle ; le reste est calculé ici à partir des données du module et passé en paramètres. Les dates sont stockées en UTC et
 * mises en forme dans le fuseau demandé par l'utilisateur.
 */
@Service
public class BonInterventionReportService implements ImpressionDuBon {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ModeleDocumentPrinter printer;

    public BonInterventionReportService(ModeleDocumentPrinter printer) {
        this.printer = printer;
    }

    @Override
    public DocumentImprime imprimer(CentreId centre, DonneesDuBon donnees, ZoneId zone, String editePar) {
        ModeleDocumentPrinter.Document document = printer.print(centre.valeur(), ModeleDocumentCatalog.BON_INTERVENTION,
                params(donnees, zone, editePar));
        return new DocumentImprime(document.format(), document.content());
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

    private static String chronologieTexte(List<EvenementVu> events, ZoneId zone) {
        List<String> lignes = new ArrayList<>();
        for (EvenementVu e : events) {
            lignes.add(stamp(e.le(), zone) + " — " + libelle(e.type())
                    + (e.parNom() == null ? "" : " par " + e.parNom())
                    + (e.detail() == null ? "" : " — motif : " + e.detail()));
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

    Map<String, Object> params(DonneesDuBon donnees, ZoneId zone, String editePar) {
        InterventionVue i = donnees.intervention();
        EquipementVue e = donnees.equipement();
        IntervenantVue intervenant = donnees.intervenant();
        List<EvenementVu> chronologie = donnees.chronologie();
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, Locale.FRANCE);
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
        return p;
    }
}
