package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link BilansPreGreffe} : lit des copies, comme une base ; le plus ancien bilan l'emporte. */
public class BilansPreGreffeEnMemoire implements BilansPreGreffe {
    private final List<BilanPreGreffe> lignes = new ArrayList<>();

    /** Comme la colonne {@code NUMERIC(5, 2)} : deux décimales, arrondi à la moitié supérieure. */
    private static java.math.BigDecimal colonne(java.math.BigDecimal pra) {
        return pra == null ? null : pra.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static BilanPreGreffe copie(BilanPreGreffe b) {
        return BilanPreGreffe.reconstituer(b.id(), b.patient(), b.centre(), b.statut(), b.dateDebutBilan(), b.dateInscriptionListeAttente(),
                b.dateGreffe(), b.groupeSanguinConfirme(), b.typageHla(), colonne(b.praClasseI()), colonne(b.praClasseII()), b.contreIndications(),
                b.conclusionNephrologue(),
                b.decisions().stream().sorted(Comparator.comparing(com.hemodialyse.backend.contexts.dossiermedical.domain.DecisionRcp::dateReunion)
                        .reversed().thenComparing(com.hemodialyse.backend.contexts.dossiermedical.domain.DecisionRcp::id)).toList(), b.creeLe(), b.modifieLe());
    }

    @Override
    public Optional<BilanPreGreffe> trouver(CentreId centre, PatientId patient) {
        return lignes.stream().filter(b -> b.centre().equals(centre) && b.patient().equals(patient))
                .min(Comparator.comparing(BilanPreGreffe::creeLe)).map(BilansPreGreffeEnMemoire::copie);
    }

    @Override
    public Optional<BilanPreGreffe> trouverEnVerrouillant(CentreId centre, PatientId patient) {
        return trouver(centre, patient);
    }

    @Override
    public void verrouillerLOuverture(CentreId centre, PatientId patient) {
        // un seul fil dans le double : rien à verrouiller
    }

    @Override
    public void ouvrir(BilanPreGreffe bilan) {
        lignes.add(copie(bilan));
    }

    @Override
    public void enregistrer(BilanPreGreffe bilan) {
        lignes.removeIf(b -> b.id().equals(bilan.id()));
        lignes.add(copie(bilan));
    }

    /** Aide de test : nombre de bilans du patient (le legacy pouvait en créer deux). */
    public long nombreDuPatient(CentreId centre, PatientId patient) {
        return lignes.stream().filter(b -> b.centre().equals(centre) && b.patient().equals(patient)).count();
    }

    public UUID premierIdentifiant() {
        return lignes.getFirst().id();
    }
}
