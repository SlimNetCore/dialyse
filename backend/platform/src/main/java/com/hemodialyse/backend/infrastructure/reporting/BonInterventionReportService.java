package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.application.gmao.InterventionSuiviQueryService;
import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.gmao.model.EvenementIntervention;
import com.hemodialyse.backend.domain.gmao.model.Intervenant;
import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.gmao.model.LigneCoutIntervention;
import com.hemodialyse.backend.domain.gmao.model.PrioriteIntervention;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.TypeLigneCout;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.IntervenantRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import net.sf.jasperreports.engine.JRParameter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Bon d'intervention GMAO : modèle de document {@value ModeleDocumentCatalog#BON_INTERVENTION}, imprimé via
 * {@link ModeleDocumentPrinter} (version personnalisée du centre si elle existe, identité société + centre posée
 * par le serveur). Les lignes de coût sont lues par la requête du modèle ; le reste est calculé ici et passé en
 * paramètres. Les dates sont stockées en UTC et mises en forme dans le fuseau demandé par l'utilisateur.
 */
@Service
public class BonInterventionReportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ModeleDocumentPrinter printer;
    private final InterventionRepositoryPort interventionRepository;
    private final EquipementRepositoryPort equipementRepository;
    private final IntervenantRepositoryPort intervenantRepository;
    private final InterventionSuiviQueryService suivi;

    public BonInterventionReportService(
            ModeleDocumentPrinter printer, InterventionRepositoryPort interventionRepository,
            EquipementRepositoryPort equipementRepository, IntervenantRepositoryPort intervenantRepository,
            InterventionSuiviQueryService suivi) {
        this.printer = printer;
        this.interventionRepository = interventionRepository;
        this.equipementRepository = equipementRepository;
        this.intervenantRepository = intervenantRepository;
        this.suivi = suivi;
    }

    private static void couts(Map<String, Object> p, Intervention i) {
        Map<TypeLigneCout, BigDecimal> parType = new HashMap<>();
        for (LigneCoutIntervention l : i.getLignesCout()) {
            parType.merge(l.getType(), l.montant(), BigDecimal::add);
        }
        p.put("COUT_PIECES", money(parType.getOrDefault(TypeLigneCout.PIECE, BigDecimal.ZERO)));
        p.put("COUT_MAIN_OEUVRE", money(parType.getOrDefault(TypeLigneCout.MAIN_OEUVRE, BigDecimal.ZERO)));
        p.put("COUT_INTERVENANT", money(parType.getOrDefault(TypeLigneCout.INTERVENANT, BigDecimal.ZERO)));
        p.put("COUT_AUTRES", money(parType.getOrDefault(TypeLigneCout.AUTRE, BigDecimal.ZERO)));
        p.put("COUT_TOTAL", money(i.coutTotal()));
    }

    private static String constatTravaux(Intervention i) {
        List<String> blocs = new ArrayList<>();
        if (blankToNull(i.getSymptome()) != null) blocs.add("Panne constatée : " + i.getSymptome());
        blocs.add("Description : " + i.getDescription());
        if (blankToNull(i.getCause()) != null) blocs.add("Cause trouvée : " + i.getCause());
        if (blankToNull(i.getActions()) != null) blocs.add("Travaux réalisés : " + i.getActions());
        if (blankToNull(i.getPieceRemplacee()) != null) blocs.add("Pièce remplacée : " + i.getPieceRemplacee());
        return String.join("\n", blocs);
    }

    private static String chronologieTexte(List<InterventionSuiviQueryService.Evenement> events, ZoneId zone) {
        List<String> lignes = new ArrayList<>();
        for (InterventionSuiviQueryService.Evenement e : events) {
            lignes.add(stamp(e.evenement().at(), zone) + " — " + libelle(e.evenement().type())
                    + (e.parNom() == null ? "" : " par " + e.parNom())
                    + (e.evenement().detail() == null ? "" : " — motif : " + e.evenement().detail()));
        }
        lignes.add("Heures exprimées dans le fuseau " + zone.getId() + ".");
        return String.join("\n", lignes);
    }

    private static String libelle(EvenementIntervention.Type type) {
        return switch (type) {
            case CREEE -> "Créée";
            case DEMARREE -> "Démarrée";
            case TERMINEE -> "Terminée";
            case ANNULEE -> "Annulée";
            case RECTIFIEE -> "Rectifiée";
            case MODIFIEE -> "Dernière modification";
        };
    }

    private static String nomEvenement(List<InterventionSuiviQueryService.Evenement> events, EvenementIntervention.Type type) {
        return events.stream().filter(e -> e.evenement().type() == type)
                .map(InterventionSuiviQueryService.Evenement::parNom).filter(n -> n != null).findFirst().orElse(null);
    }

    private static String priorite(PrioriteIntervention p) {
        return switch (p) {
            case NORMALE -> "Normale";
            case HAUTE -> "Haute";
            case URGENTE -> "Urgente";
        };
    }

    private static String etat(StatutEquipement s) {
        return s == null ? null : s.getLabel();
    }

    private static String marque(String fabricant, String modele) {
        String m = ((fabricant == null ? "" : fabricant) + " " + (modele == null ? "" : modele)).trim();
        return m.isEmpty() ? "" : " (" + m + ")";
    }

    private static String duree(Intervention i) {
        if (i.getDateDebut() == null || i.getDateFin() == null || !i.getDateFin().isAfter(i.getDateDebut()))
            return null;
        Duration d = Duration.between(i.getDateDebut(), i.getDateFin());
        long h = d.toHours();
        long m = d.toMinutesPart();
        return h == 0 ? m + " min" : h + " h " + String.format("%02d", m);
    }

    private static String stamp(OffsetDateTime t, ZoneId zone) {
        return t == null ? null : t.atZoneSameInstant(zone).format(STAMP);
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

    /**
     * @param zone fuseau d'affichage des dates (celui du navigateur), UTC à défaut
     */
    public ModeleDocumentPrinter.Document imprimer(UUID interventionId, UUID centreId, ZoneId zone, String editePar) {
        Intervention intervention = interventionRepository.findById(interventionId)
                .filter(i -> i.getCentreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Intervention non trouvée"));
        Equipement equipement = equipementRepository.findById(intervention.getEquipementId())
                .filter(e -> e.getCentreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Équipement non trouvé"));
        Intervenant intervenant = intervention.getIntervenantId() == null ? null
                : intervenantRepository.findById(intervention.getIntervenantId())
                .filter(i -> i.centreId().equals(centreId)).orElse(null);
        List<InterventionSuiviQueryService.Evenement> chronologie = suivi.chronologie(interventionId, centreId);

        return printer.print(centreId, ModeleDocumentCatalog.BON_INTERVENTION,
                params(intervention, equipement, intervenant, chronologie, zone, editePar, OffsetDateTime.now()));
    }

    Map<String, Object> params(
            Intervention i, Equipement e, Intervenant intervenant,
            List<InterventionSuiviQueryService.Evenement> chronologie, ZoneId zone, String editePar,
            OffsetDateTime editeLe) {
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, Locale.FRANCE);
        p.put("INTERVENTION_ID", i.getId().toString());
        p.put("BON_NUMERO", "BI-" + i.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        p.put("BON_TYPE", i.getType().getLabel());
        p.put("BON_STATUT", i.getStatut().getLabel());
        p.put("BON_PRIORITE", priorite(i.getPriorite()));
        p.put("EQUIPEMENT_LIGNE", e.getCode() + " — " + e.getDesignation()
                + marque(e.getFabricant(), e.getModele()));
        p.put("EQUIPEMENT_SERIE", blankToNull(e.getNumeroSerie()));
        p.put("INTERVENANT", intervenant == null ? null
                : intervenant.nom() + " (" + (intervenant.type() == com.hemodialyse.backend.domain.gmao.model.TypeIntervenant.EXTERNE
                ? "externe" : "interne") + ")");
        p.put("DATE_DEBUT", stamp(i.getDateDebut(), zone));
        p.put("DATE_FIN", stamp(i.getDateFin(), zone));
        p.put("DUREE", duree(i));
        p.put("ECHEANCE", stamp(i.getEcheance(), zone));
        p.put("ETAT_AVANT", etat(i.getEtatEquipementAvant()));
        p.put("ETAT_APRES", etat(i.getEtatEquipementApres()));
        p.put("CONSTAT_TRAVAUX", constatTravaux(i));
        couts(p, i);
        p.put("SIGNATAIRE_CREE", nomEvenement(chronologie, EvenementIntervention.Type.CREEE));
        p.put("SIGNATAIRE_CLOTURE", nomEvenement(chronologie, EvenementIntervention.Type.TERMINEE));
        p.put("CHRONOLOGIE", chronologieTexte(chronologie, zone));
        p.put("EDITE_LE", stamp(editeLe, zone));
        p.put("EDITE_PAR", blankToNull(editePar));
        return p;
    }
}
