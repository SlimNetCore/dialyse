package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import net.sf.jasperreports.engine.JRParameter;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * et la période sont passées en paramètres.
 */
@Service
public class CahierDialyseReportService {

    static final LocalDate DEBUT_PAR_DEFAUT = LocalDate.of(1900, 1, 1);
    static final LocalDate FIN_PAR_DEFAUT = LocalDate.of(2999, 12, 31);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE);

    private final ModeleDocumentPrinter printer;
    private final PatientRepositoryPort patients;
    private final JdbcTemplate jdbc;

    public CahierDialyseReportService(ModeleDocumentPrinter printer, PatientRepositoryPort patients, JdbcTemplate jdbc) {
        this.printer = printer;
        this.patients = patients;
        this.jdbc = jdbc;
    }

    private static String ligneDe(String libelle, String valeur) {
        return valeur == null || valeur.isBlank() ? null : libelle + " " + valeur.trim();
    }

    /**
     * Ligne d'identité imprimée sous le titre : patient, code, sexe, naissance, groupe sanguin, assurance, téléphone.
     */
    static String ligneIdentite(Patient p) {
        List<String> parts = new ArrayList<>();
        String nom = ((p.getPrenom() == null ? "" : p.getPrenom() + " ") + (p.getNom() == null ? "" : p.getNom())).trim();
        parts.add("Patient : " + nom);
        parts.add(ligneDe("Code", p.getCodePatient()));
        parts.add(ligneDe("Sexe", p.getSexe()));
        parts.add(p.getDateNaissance() == null ? null : "Né(e) le " + JOUR.format(p.getDateNaissance()));
        parts.add(ligneDe("Groupe", p.getGroupeSanguin()));
        parts.add(p.getNumeroAssurance() == null ? null : ligneDe("N° assurance", p.getNumeroAssurance().value()));
        parts.add(ligneDe("Tél", p.getTelMobile()));
        return String.join("  ·  ", parts.stream().filter(s -> s != null && !s.isBlank()).toList());
    }

    static Map<String, Object> params(Patient patient, LocalDate from, LocalDate to, ZoneId zone, String editePar) {
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, Locale.FRANCE);
        p.put("PATIENT_ID", patient.getId().value().toString());
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
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ? AND patient_id = ? "
                        + "AND statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') AND date_seance BETWEEN ? AND ?",
                Integer.class, centreId, patientId, Date.valueOf(from == null ? DEBUT_PAR_DEFAUT : from),
                Date.valueOf(to == null ? FIN_PAR_DEFAUT : to));
        return n == null ? 0 : n;
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
        Patient patient = patients.findById(PatientId.of(patientId), CenterId.of(centreId))
                .orElseThrow(() -> new BusinessException("PATIENT_INTROUVABLE", "Patient introuvable"));
        if (nombreSeances(centreId, patientId, from, to) == 0) {
            throw new BusinessException("CAHIER_VIDE", "Aucune séance validée à imprimer pour ce patient");
        }
        return printer.print(centreId, ModeleDocumentCatalog.CAHIER_DIALYSE, params(patient, from, to, zone, editePar));
    }
}
