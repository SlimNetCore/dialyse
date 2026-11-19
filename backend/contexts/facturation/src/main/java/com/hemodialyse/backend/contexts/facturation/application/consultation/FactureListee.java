package com.hemodialyse.backend.contexts.facturation.application.consultation;

import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une facture émise telle que la liste la présente (RG-FAC-046) : montants figés de la facture, réglé (somme signée des règlements),
 * reste, trop-perçu et état de règlement calculés à la lecture. Patient : fiche du centre sinon instantané de la facture ; caisse :
 * chaîne figée puis courante ({@code caisseId} nul et libellé « Non renseignee » si la chaîne est rompue). Montants à 2 décimales.
 */
public record FactureListee(UUID factureId, String numeroFacture, LocalDate dateFacturation, LocalDate periodeDebut,
                            LocalDate periodeFin, UUID patientId, String numeroAssurance, String patientNom, String patientPrenom,
                            UUID caisseId, String caisse, long nombreDeSeances, BigDecimal tauxTva, BigDecimal montantHt,
                            BigDecimal montantTva, BigDecimal montantTtc, BigDecimal montantRegle, BigDecimal reste,
                            BigDecimal tropPercu, EtatDeReglement etat) {
}
