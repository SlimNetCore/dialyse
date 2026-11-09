package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié du Dossier médical (SE7f, ADR 0100) : la dernière créatinine (mg/dl) connue d'un patient dans un centre, pour
 * l'évaluation KDIGO du candidat à la greffe encore servie par la plateforme ({@code KdigoGreffeQueryService}). Lecture seule, bornée
 * au centre (une analyse du même patient dans un autre centre est ignorée). Reproduit SANS correction (KB-SEA-96, à décider par les
 * médecins au plus tard le 2026-12-15) : parmi les analyses du patient au centre triées par date de prélèvement décroissante seulement,
 * la première qui porte une créatinine non nulle est retenue ; à date égale l'ordre est celui du SGBD (non défini) ; une créatinine à 0
 * est une valeur (retenue), pas une absence.
 */
public interface DerniereCreatininePubliee {

    Optional<BigDecimal> duPatient(UUID centre, UUID patient);
}
