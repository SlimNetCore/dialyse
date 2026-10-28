package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant : les patients que le programme du centre attend ce jour-là et qui n'ont pas de séance réalisée (RG-ABS-041).
 * Faits lus chez Planification, Patient et couverture, Organisation (SQL direct tant qu'ils ne sont pas extraits). Attendu : jour
 * coché au programme, pas en sommeil, admis, centre ouvert (ni férié ni fermé), et pas sorti. Un patient sorti (décès, greffe,
 * transfert, guérison) n'est plus attendu le jour de l'événement ni après (uniformisé, KB-ABS-24) ; sans date d'événement il n'est jamais
 * attendu (legacy conservé, en attendant que Patient et couverture interdise une sortie sans date). Un séjour limité (occasionnel,
 * vacancier) est attendu jusqu'à sa fin incluse. Un patient dont l'absence existe déjà ce jour-là n'est pas rendu. L'appelant ne demande
 * que des jours terminés. Par identifiant croissant.
 */
public interface PatientsAttendus {

    List<PatientId> sansSeanceRealisee(CentreId centre, LocalDate jour);
}
