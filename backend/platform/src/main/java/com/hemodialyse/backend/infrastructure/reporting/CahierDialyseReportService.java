package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDuCahierPubliees;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients.CarteDuPatient;
import com.hemodialyse.backend.application.erreur.BusinessException;
import net.sf.jasperreports.engine.JRParameter;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Cahier de dialyse d'un patient : modèle de document {@value ModeleDocumentCatalog#CAHIER_DIALYSE}, imprimé via
 * {@link ModeleDocumentPrinter}. Le modèle lit lui-même les séances (validées, signées ou facturées), les volets
 * paramédical et médical, les consommables valorisés, le forfait et la prescription en vigueur ; l'identité du patient
 * et la période sont passées en paramètres. Le modèle est PERSONNALISABLE par centre (version active en base, une seule
 * requête embarquée) : sa requête reste donc dans l'état Jasper, lecteur nommé de {@code seances} (ADR 0099, décision 3,
 * résidu E-R3) ; seul le refus {@code CAHIER_VIDE} passe par le langage publié de la Séance.
 */
@Service
public class CahierDialyseReportService {

    static final LocalDate DEBUT_PAR_DEFAUT = LocalDate.of(1900, 1, 1);
    static final LocalDate FIN_PAR_DEFAUT = LocalDate.of(2999, 12, 31);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE);

    private final ModeleDocumentPrinter printer;
    /** Identité du patient : langage publié de Patient &amp; couverture (patientcouverture.v1, ADR 0133 S3). */
    private final FichesDesPatients fiches;
    /** Compte des séances imprimables : langage publié de la Séance (SE7e, ADR 0099). */
    private final SeancesDuCahierPubliees seances;

    public CahierDialyseReportService(ModeleDocumentPrinter printer, FichesDesPatients fiches, SeancesDuCahierPubliees seances) {
        this.printer = printer;
        this.fiches = fiches;
        this.seances = seances;
    }

    private static String ligneDe(String libelle, String valeur) {
        return valeur == null || valeur.isBlank() ? null : libelle + " " + valeur.trim();
    }

    /**
     * Ligne d'identité imprimée sous le titre : patient, code, sexe, naissance, groupe sanguin, assurance, téléphone.
     */
    static String ligneIdentite(CarteDuPatient p) {
        List<String> parts = new ArrayList<>();
        String nom = ((p.prenom() == null ? "" : p.prenom() + " ") + (p.nom() == null ? "" : p.nom())).trim();
        parts.add("Patient : " + nom);
        parts.add(ligneDe("Code", p.code()));
        parts.add(ligneDe("Sexe", p.sexe()));
        parts.add(p.dateNaissance() == null ? null : "Né(e) le " + JOUR.format(p.dateNaissance()));
        parts.add(ligneDe("Groupe", p.groupeSanguin()));
        parts.add(ligneDe("N° assurance", p.numeroAssurance()));
        parts.add(ligneDe("Tél", p.telMobile()));
        return String.join("  ·  ", parts.stream().filter(s -> s != null && !s.isBlank()).toList());
    }

    static Map<String, Object> params(UUID patientId, CarteDuPatient patient, LocalDate from, LocalDate to, ZoneId zone,
                                      String editePar) {
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, Locale.FRANCE);
        p.put("PATIENT_ID", patientId.toString());
        p.put("DATE_DEBUT", Date.valueOf(from == null ? DEBUT_PAR_DEFAUT : from));
        p.put("DATE_FIN", Date.valueOf(to == null ? FIN_PAR_DEFAUT : to));
        p.put("PATIENT_LIGNE", ligneIdentite(patient));
        p.put("EDITE_LE", STAMP.format(ZonedDateTime.now(zone)));
        p.put("EDITE_PAR", editePar);
        return p;
    }

    /**
     * Nombre de séances imprimables (validées, signées ou facturées) du patient sur la période.
     */
    int nombreSeances(UUID centreId, UUID patientId, LocalDate from, LocalDate to) {
        return seances.nombreDeSeancesAImprimer(centreId, patientId, from == null ? DEBUT_PAR_DEFAUT : from,
                to == null ? FIN_PAR_DEFAUT : to);
    }

    /**
     * @param from début de période (incluse), nul = depuis la première séance
     * @param to   fin de période (incluse), nul = jusqu'à la dernière séance
     * @throws BusinessException {@code PATIENT_INTROUVABLE} (patient hors du centre) ou {@code CAHIER_VIDE} (aucune
     *                           séance à imprimer) ou {@code CAHIER_PERIODE_INVALIDE}
     */
    public ModeleDocumentPrinter.Document imprimer(UUID centreId, UUID patientId, LocalDate from, LocalDate to,
                                                   ZoneId zone, String editePar) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("CAHIER_PERIODE_INVALIDE", "Période invalide : le début doit précéder la fin");
        }
        CarteDuPatient patient = fiches.carte(centreId, patientId)
                .orElseThrow(() -> new BusinessException("PATIENT_INTROUVABLE", "Patient introuvable"));
        if (nombreSeances(centreId, patientId, from, to) == 0) {
            throw new BusinessException("CAHIER_VIDE", "Aucune séance validée à imprimer pour ce patient");
        }
        return printer.print(centreId, ModeleDocumentCatalog.CAHIER_DIALYSE, params(patientId, patient, from, to, zone, editePar));
    }
}
