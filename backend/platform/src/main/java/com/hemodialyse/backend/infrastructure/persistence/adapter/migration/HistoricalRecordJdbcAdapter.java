package com.hemodialyse.backend.infrastructure.persistence.adapter.migration;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RepriseDeLHistoriqueMedical;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUneAttestation;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUnePriseEnCharge;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDeLaReprisePubliees;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationEntity;
import com.hemodialyse.backend.infrastructure.migration.port.HistoricalRecordPort;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Écriture de l'historique repris dans les données métier (état final, sans passer par les services). Plus aucune écriture SQL ici : chaque
 * donnée est reprise par le langage publié de son contexte (séances : Séance, SE7e, ADR 0099 ; attestations et prises en charge : Patient et
 * couverture, RP3a, ADR 0171 ; dossier de base, antécédents, sérologies, abords vasculaires et analyses : Dossier médical, RP3b, ADR 0172),
 * qui contrôle les champs sur sa liste blanche : aucune valeur venant d'un fichier n'est jamais concaténée à une requête.
 */
@Component
public class HistoricalRecordJdbcAdapter implements HistoricalRecordPort {

    /** Nature d'historique médical de chaque donnée reprise par le Dossier médical (liste fermée de son langage publié). */
    static final Map<MigrationEntity, String> NATURES_MEDICALES = new EnumMap<>(MigrationEntity.class);

    static {
        NATURES_MEDICALES.put(MigrationEntity.DOSSIERS_MEDICAUX, RepriseDeLHistoriqueMedical.DOSSIER_DE_BASE);
        NATURES_MEDICALES.put(MigrationEntity.ANTECEDENTS, RepriseDeLHistoriqueMedical.ANTECEDENTS);
        NATURES_MEDICALES.put(MigrationEntity.SEROLOGIES, RepriseDeLHistoriqueMedical.SEROLOGIES);
        NATURES_MEDICALES.put(MigrationEntity.ABORDS_VASCULAIRES, RepriseDeLHistoriqueMedical.ABORDS_VASCULAIRES);
        NATURES_MEDICALES.put(MigrationEntity.ANALYSES, RepriseDeLHistoriqueMedical.ANALYSES);
    }

    private final SeancesDeLaReprisePubliees seances;
    /** Attestations de droits et prises en charge : écrites par le langage publié de Patient et couverture (RP3a, ADR 0171). */
    private final RepriseDeLHistoriqueDeCouverture couverture;
    /** Dossier de base, antécédents, sérologies, abords et analyses : écrits par le langage publié du Dossier médical (RP3b, ADR 0172). */
    private final RepriseDeLHistoriqueMedical medical;

    public HistoricalRecordJdbcAdapter(SeancesDeLaReprisePubliees seances, RepriseDeLHistoriqueDeCouverture couverture,
                                       RepriseDeLHistoriqueMedical medical) {
        this.seances = seances;
        this.couverture = couverture;
        this.medical = medical;
    }

    private static LocalDate date(Map<String, Object> valeurs, String champ) {
        return (LocalDate) valeurs.get(champ);
    }

    private static UUID identifiant(Map<String, Object> valeurs, String champ) {
        return (UUID) valeurs.get(champ);
    }

    private static ReportDUneAttestation attestation(Map<String, Object> valeurs) {
        return new ReportDUneAttestation(date(valeurs, "dateDebut"), date(valeurs, "dateFin"));
    }

    private static ReportDUnePriseEnCharge priseEnCharge(Map<String, Object> valeurs) {
        return new ReportDUnePriseEnCharge(date(valeurs, "dateDebutDemande"), date(valeurs, "dateFinDemande"),
                identifiant(valeurs, "forfaitDemandeId"), (String) valeurs.get("statut"), date(valeurs, "dateDebutEffectif"),
                date(valeurs, "dateFinEffectif"), identifiant(valeurs, "forfaitEffectifId"));
    }

    /** Nature d'historique médical d'une donnée reprise par le Dossier médical ; toute autre donnée n'est pas historisable. */
    static String nature(MigrationEntity entity) {
        String nature = NATURES_MEDICALES.get(entity);
        if (nature == null) throw new IllegalArgumentException("Donnée non historisable : " + entity);
        return nature;
    }

    @Override
    public Optional<UUID> findExisting(CentreId centerId, MigrationEntity entity, Map<String, Object> key) {
        if (entity == MigrationEntity.SEANCES) return seances.seanceDejaPresente(centerId.valeur(), key);
        if (entity == MigrationEntity.ATTESTATIONS) {
            return couverture.attestationDebutee(centerId.valeur(), identifiant(key, "patientId"), date(key, "dateDebut"));
        }
        if (entity == MigrationEntity.PRISES_EN_CHARGE) {
            return couverture.priseEnChargeDemandee(centerId.valeur(), identifiant(key, "patientId"), date(key, "dateDebutDemande"));
        }
        return medical.dejaPresente(centerId.valeur(), nature(entity), key);
    }

    @Override
    public UUID insert(CentreId centerId, MigrationEntity entity, Map<String, Object> values) {
        if (entity == MigrationEntity.SEANCES) return seances.reprendre(centerId.valeur(), values);
        if (entity == MigrationEntity.ATTESTATIONS) {
            return couverture.reprendreUneAttestation(centerId.valeur(), identifiant(values, "patientId"), attestation(values));
        }
        if (entity == MigrationEntity.PRISES_EN_CHARGE) {
            return couverture.reprendreUnePriseEnCharge(centerId.valeur(), identifiant(values, "patientId"), priseEnCharge(values));
        }
        return medical.reprendre(centerId.valeur(), nature(entity), values);
    }

    @Override
    public void update(CentreId centerId, MigrationEntity entity, UUID id, Map<String, Object> values) {
        if (entity == MigrationEntity.SEANCES) {
            seances.modifier(centerId.valeur(), id, values);
            return;
        }
        if (entity == MigrationEntity.ATTESTATIONS) {
            couverture.completerUneAttestation(centerId.valeur(), id, attestation(values));
            return;
        }
        if (entity == MigrationEntity.PRISES_EN_CHARGE) {
            couverture.completerUnePriseEnCharge(centerId.valeur(), id, priseEnCharge(values));
            return;
        }
        medical.completer(centerId.valeur(), nature(entity), id, values);
    }
}
