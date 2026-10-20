package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.facturation.application.simulation.AbsencesDesPatients;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.seance.MotifDAbsence;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Double des absences de patients : garde les absences déclarées (avec leur motif, commentaire et auteur) et peut refuser la
 * déclaration comme le fait le module Absences.
 */
public class AbsencesEnMemoire implements AbsencesDesPatients {

    private final Map<UUID, Declaration> declarees = new LinkedHashMap<>();
    private final List<UUID> retirees = new ArrayList<>();
    private RuntimeException refus;

    public void refuserLaDeclaration(RuntimeException refus) {
        this.refus = refus;
    }

    @Override
    public UUID declarer(CentreId centre, UUID patient, LocalDate dateDeSeance, MotifDAbsence motif, String commentaire,
                         UUID auteur) {
        if (refus != null) {
            throw refus;
        }
        UUID id = UUID.randomUUID();
        declarees.put(id, new Declaration(centre, patient, dateDeSeance, motif, commentaire, auteur));
        return id;
    }

    @Override
    public void retirer(CentreId centre, UUID absence) {
        declarees.remove(absence);
        retirees.add(absence);
    }

    public Map<UUID, Declaration> declarees() {
        return declarees;
    }

    public List<UUID> retirees() {
        return retirees;
    }

    public record Declaration(CentreId centre, UUID patient, LocalDate dateDeSeance, MotifDAbsence motif,
                              String commentaire,
                              UUID auteur) {
    }
}
