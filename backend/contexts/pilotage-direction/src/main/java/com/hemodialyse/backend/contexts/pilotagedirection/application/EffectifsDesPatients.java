package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.EffectifDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PatientsDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PrescriptionsDeLAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ProfilDUnPatient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port sortant : l'EFFECTIF d'une période, lu sur les fiches des patients des centres demandés. Un patient est compté s'il a été admis au
 * plus tard le dernier jour de la période et s'il n'était pas déjà sorti avant son premier jour (RG-PAT-032 : la sortie ou la fin de séjour
 * tombant pendant ou après la période compte). Aucune ligne n'identifie un patient. {@code centres} n'est jamais vide.
 */
public interface EffectifsDesPatients {

    /** Patients et patients sous KT par centre ; un centre sans patient est absent. */
    Map<UUID, EffectifDuCentre> parCentre(List<UUID> centres, Periode periode);

    /** Sexe et naissance de chaque patient de l'effectif, avec son centre. */
    List<ProfilDUnPatient> profils(List<UUID> centres, Periode periode);

    /** Patients de l'effectif par centre et par caisse (chaîne payeur, agence, caisse ; code vide sans caisse). */
    List<PatientsDeLaCaisse> parCaisse(List<UUID> centres, Periode periode);

    /** Patients sous EPO et sous fer prescrits (fiche), par centre ; un centre sans patient est absent. */
    Map<UUID, PrescriptionsDeLAnemie> prescriptionsDeLAnemie(List<UUID> centres, Periode periode);
}
