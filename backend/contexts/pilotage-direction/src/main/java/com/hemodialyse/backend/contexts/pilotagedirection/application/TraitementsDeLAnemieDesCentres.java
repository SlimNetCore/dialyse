package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.AdministrationsDeLAnemie;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port sortant : les administrations de traitement de l'anémie (EPO, fer injectable) des centres sur une période, lues dans le langage
 * publié du Dossier médical (DM9, ADR 0101). Comptes bruts ; un centre sans administration est absent.
 */
public interface TraitementsDeLAnemieDesCentres {

    Map<UUID, AdministrationsDeLAnemie> parCentre(List<UUID> centres, Periode periode);
}
