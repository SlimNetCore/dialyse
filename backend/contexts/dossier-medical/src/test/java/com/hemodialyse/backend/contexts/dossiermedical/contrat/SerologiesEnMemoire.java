package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeSerologies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link Serologies} : mêmes ordres de lecture que l'adapter JDBC (jugé par le même contrat). */
public class SerologiesEnMemoire implements Serologies {

    private final Map<UUID, Serologie> fiches = new LinkedHashMap<>();

    @Override
    public boolean existeEnVerrouillant(CentreId centre, PatientId patient, MarqueurSerologique marqueur, LocalDate datePrelevement) {
        return fiches.values().stream().anyMatch(s -> s.centre().equals(centre) && s.patient().equals(patient)
                && s.marqueur() == marqueur && s.datePrelevement().equals(datePrelevement));
    }

    @Override
    public void ajouter(Serologie serologie) {
        fiches.put(serologie.id(), copie(serologie));
    }

    @Override
    public void enregistrerLaCorrection(Serologie serologie) {
        fiches.put(serologie.id(), copie(serologie));
    }

    @Override
    public Optional<Serologie> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return Optional.ofNullable(fiches.get(id)).filter(s -> s.centre().equals(centre) && s.patient().equals(patient)).map(
                SerologiesEnMemoire::copie);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        if (trouverEnVerrouillant(centre, patient, id).isEmpty()) return false;
        fiches.remove(id);
        return true;
    }

    @Override
    public PageDeSerologies duPatient(CentreId centre, PatientId patient, int page, int taille) {
        List<Serologie> toutes = toutesDuPatient(centre, patient);
        int debut = (int) Math.min((long) page * taille, toutes.size());
        return new PageDeSerologies(toutes.subList(debut, Math.min(debut + taille, toutes.size())), toutes.size(), page, taille);
    }

    @Override
    public List<Serologie> toutesDuPatient(CentreId centre, PatientId patient) {
        return fiches.values().stream().filter(s -> s.centre().equals(centre) && s.patient().equals(patient))
                .sorted(Comparator.comparing(Serologie::datePrelevement).thenComparing(Serologie::creeeLe).thenComparing(Serologie::id)
                        .reversed()).map(SerologiesEnMemoire::copie).toList();
    }

    private static Serologie copie(Serologie s) {
        return Serologie.reconstituer(s.id(), s.patient(), s.centre(), s.marqueur(), s.resultat(), s.titre(), s.unite(), s.datePrelevement(),
                s.laboratoire(), s.dateProchainControle(), s.conduiteATenir(), s.creeeLe(), s.modifieeLe());
    }
}
