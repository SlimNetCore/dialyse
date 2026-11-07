package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDePrescriptions;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionMedicale;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link Prescriptions} (mêmes filtres et mêmes ordres que l'adapter JDBC, jugé par le même contrat). */
public class PrescriptionsEnMemoire implements Prescriptions {

    private final Map<UUID, PrescriptionMedicale> fiches = new LinkedHashMap<>();

    @Override
    public void ajouter(PrescriptionMedicale p) {
        fiches.put(p.id(), copie(p));
    }

    @Override
    public void enregistrerLeRemplacement(PrescriptionMedicale p) {
        fiches.put(p.id(), copie(p));
    }

    @Override
    public Optional<PrescriptionMedicale> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return Optional.ofNullable(fiches.get(id)).filter(p -> p.centre().equals(centre) && p.patient().equals(patient)).map(PrescriptionsEnMemoire::copie);
    }

    @Override
    public void supprimer(CentreId centre, PatientId patient, UUID id) {
        trouverEnVerrouillant(centre, patient, id).ifPresent(p -> fiches.remove(id));
    }

    @Override
    public PageDePrescriptions duPatient(CentreId centre, PatientId patient, Periode periode, int page, int taille) {
        List<PrescriptionMedicale> toutes = duPatientSurLaPeriode(centre, patient, periode);
        int debut = (int) Math.min((long) page * taille, toutes.size());
        return new PageDePrescriptions(toutes.subList(debut, Math.min(debut + taille, toutes.size())), toutes.size(), page, taille);
    }

    @Override
    public List<PrescriptionMedicale> duPatientSurLaPeriode(CentreId centre, PatientId patient, Periode periode) {
        return fiches.values().stream().filter(p -> p.centre().equals(centre) && p.patient().equals(patient))
                .filter(p -> periode.du() == null || !p.contenu().date().isBefore(periode.du()))
                .filter(p -> periode.au() == null || !p.contenu().date().isAfter(periode.au()))
                .sorted(Comparator.<PrescriptionMedicale, java.time.LocalDate>comparing(p -> p.contenu().date())
                        .thenComparing(PrescriptionMedicale::creeeLe).thenComparing(PrescriptionMedicale::id).reversed())
                .map(PrescriptionsEnMemoire::copie).toList();
    }

    @Override
    public List<PrescriptionMedicale> lesPlusRecentesDuCentre(CentreId centre) {
        Map<PatientId, java.time.LocalDate> maximum = new LinkedHashMap<>();
        fiches.values().stream().filter(p -> p.centre().equals(centre))
                .forEach(p -> maximum.merge(p.patient(), p.contenu().date(), (a, b) -> a.isAfter(b) ? a : b));
        return fiches.values().stream().filter(p -> p.centre().equals(centre) && p.contenu().date().equals(maximum.get(p.patient())))
                .map(PrescriptionsEnMemoire::copie).toList();
    }

    private static PrescriptionMedicale copie(PrescriptionMedicale p) {
        return PrescriptionMedicale.reconstituer(p.id(), p.patient(), p.centre(), p.contenu(), p.creeeLe(), p.modifieeLe());
    }
}
