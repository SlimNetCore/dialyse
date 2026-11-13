package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.CouvertureDeLaReprise;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUneAttestation;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUnePriseEnCharge;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de {@link CouvertureDeLaReprise} sur les tables actuelles (RP3a, ADR 0171) : requêtes d'import de l'outil de migration
 * reprises sans changement de comportement. Seules les valeurs fournies sont écrites ; la date de création est posée à l'ajout, il n'y a
 * pas de date de modification ; tout est borné au centre. Les noms de colonnes sont des littéraux de ce fichier.
 */
public class CouvertureDeLaRepriseJdbc implements CouvertureDeLaReprise {

    private final JdbcTemplate jdbc;

    public CouvertureDeLaRepriseJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Colonnes écrites (valeurs non nulles seulement) : l'ordre est celui de l'appel. */
    private static final class Colonnes {
        private final List<String> noms = new ArrayList<>();
        private final List<Object> valeurs = new ArrayList<>();

        Colonnes avec(String nom, Object valeur) {
            if (valeur != null) {
                noms.add(nom);
                valeurs.add(valeur);
            }
            return this;
        }
    }

    @Override
    public Optional<UUID> attestationDebutee(UUID centre, UUID patient, LocalDate dateDebut) {
        List<UUID> trouvees = dateDebut == null
                ? jdbc.queryForList("SELECT id FROM attestation_droit WHERE center_id = ? AND date_debut IS NULL AND patient_id = ? "
                + "ORDER BY created_at", UUID.class, centre, patient)
                : jdbc.queryForList("SELECT id FROM attestation_droit WHERE center_id = ? AND date_debut = ? AND patient_id = ? "
                + "ORDER BY created_at", UUID.class, centre, dateDebut, patient);
        return trouvees.stream().findFirst();
    }

    @Override
    public UUID ajouterUneAttestation(UUID centre, UUID patient, ReportDUneAttestation report) {
        Colonnes c = new Colonnes().avec("date_debut", report.dateDebut()).avec("date_fin", report.dateFin());
        return ajouter("attestation_droit", centre, patient, c);
    }

    @Override
    public void completerUneAttestation(UUID centre, UUID attestation, ReportDUneAttestation report) {
        Colonnes c = new Colonnes().avec("date_debut", report.dateDebut()).avec("date_fin", report.dateFin());
        if (c.noms.isEmpty()) return;
        List<Object> parametres = new ArrayList<>(c.valeurs);
        parametres.add(attestation);
        parametres.add(centre);
        jdbc.update("UPDATE attestation_droit SET " + affectations(c) + " WHERE id = ? AND center_id = ?", parametres.toArray());
    }

    @Override
    public Optional<UUID> priseEnChargeDemandee(UUID centre, UUID patient, LocalDate dateDebutDemande) {
        List<UUID> trouvees = dateDebutDemande == null
                ? jdbc.queryForList("SELECT id FROM prise_en_charge WHERE center_id = ? AND date_debut_demande IS NULL AND patient_id = ? "
                + "ORDER BY created_at", UUID.class, centre, patient)
                : jdbc.queryForList("SELECT id FROM prise_en_charge WHERE center_id = ? AND date_debut_demande = ? AND patient_id = ? "
                + "ORDER BY created_at", UUID.class, centre, dateDebutDemande, patient);
        return trouvees.stream().findFirst();
    }

    @Override
    public UUID ajouterUnePriseEnCharge(UUID centre, UUID patient, ReportDUnePriseEnCharge report) {
        return ajouter("prise_en_charge", centre, patient, colonnes(report));
    }

    @Override
    public void completerUnePriseEnCharge(UUID centre, UUID priseEnCharge, ReportDUnePriseEnCharge report) {
        Colonnes c = colonnes(report);
        if (c.noms.isEmpty()) return;
        List<Object> parametres = new ArrayList<>(c.valeurs);
        parametres.add(priseEnCharge);
        parametres.add(centre);
        jdbc.update("UPDATE prise_en_charge SET " + affectations(c) + " WHERE id = ? AND center_id = ?", parametres.toArray());
    }

    private static Colonnes colonnes(ReportDUnePriseEnCharge report) {
        return new Colonnes().avec("date_debut_demande", report.dateDebutDemande()).avec("date_fin_demande", report.dateFinDemande())
                .avec("forfait_demande_id", report.forfaitDemande()).avec("statut", report.statut())
                .avec("date_debut_effectif", report.dateDebutEffectif()).avec("date_fin_effectif", report.dateFinEffectif())
                .avec("forfait_effectif_id", report.forfaitEffectif());
    }

    private static String affectations(Colonnes c) {
        return String.join(", ", c.noms.stream().map(nom -> nom + " = ?").toList());
    }

    /** Une seule des deux tables de ce contexte à la fois : {@code table} est un littéral de ce fichier, jamais une valeur d'un fichier. */
    private UUID ajouter(String table, UUID centre, UUID patient, Colonnes c) {
        UUID id = UUID.randomUUID();
        List<String> noms = new ArrayList<>(List.of("id", "center_id", "patient_id"));
        List<Object> parametres = new ArrayList<>(List.of(id, centre, patient));
        noms.addAll(c.noms);
        parametres.addAll(c.valeurs);
        noms.add("created_at");
        parametres.add(OffsetDateTime.now());
        String marques = String.join(", ", noms.stream().map(nom -> "?").toList());
        jdbc.update("INSERT INTO " + table + " (" + String.join(", ", noms) + ") VALUES (" + marques + ")", parametres.toArray());
        return id;
    }
}
