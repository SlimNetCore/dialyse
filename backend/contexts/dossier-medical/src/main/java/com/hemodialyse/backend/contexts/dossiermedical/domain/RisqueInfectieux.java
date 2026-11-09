package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Risque infectieux d'un patient pour l'isolement (RG-PLN-012, lu par la Planification) : un patient est à risque si, pour au moins un
 * marqueur surveillé (AgHBs, anti-VHC, ARN du VHC, VIH), sa DERNIÈRE sérologie est {@code POSITIF}, « dernière » au sens du legacy :
 * aucune autre sérologie du même marqueur n'est strictement plus récente par la date de prélèvement, puis par l'horodatage de saisie.
 * Règle CORRIGÉE le 2026-11-09 (ADR 0113, KB-MED-106, RISQUE CLINIQUE, validation médicale attendue avant le 2026-12-15) : seul un résultat
 * POSITIF ou NEGATIF est conclusif ; la décision est celle de la dernière sérologie CONCLUSIVE du marqueur, un DOUTEUX ou un EN_COURS plus
 * récent n'efface plus un positif antérieur (avant : il levait le risque, ADR 0101) ; à égalité totale (même jour, même saisie) le positif l'emporte, car rien n'est « plus récent » que lui
 * (KB-MED-107). Les sérologies fournies sont celles d'UN centre (le cloisonnement se fait par la ligne, KB-MED-108). Cette règle est
 * la forme Java du SQL de {@code PatientsARisqueInfectieuxJdbc} ; le double en mémoire du contrat l'applique.
 */
public final class RisqueInfectieux {

    public static final Set<MarqueurSerologique> MARQUEURS_SURVEILLES =
            Set.of(MarqueurSerologique.AG_HBS, MarqueurSerologique.AC_VHC, MarqueurSerologique.ARN_VHC, MarqueurSerologique.VIH_AC);

    /** Une sérologie telle que posée en base : patient, marqueur, résultat, date de prélèvement et horodatage de saisie. */
    public record SerologieObservee(UUID patient, MarqueurSerologique marqueur, ResultatSerologique resultat, LocalDate prelevement,
                                    Instant saisie) {
    }

    private RisqueInfectieux() {
    }

    public static Set<UUID> patientsARisque(Collection<SerologieObservee> serologiesDuCentre) {
        Set<UUID> aRisque = new HashSet<>();
        for (SerologieObservee s : serologiesDuCentre) {
            if (!MARQUEURS_SURVEILLES.contains(s.marqueur()) || s.resultat() != ResultatSerologique.POSITIF) continue;
            boolean plusRecenteExiste = serologiesDuCentre.stream().anyMatch(n -> n.patient().equals(s.patient()) && n.marqueur() == s.marqueur()
                    && estConclusif(n.resultat())
                    && (n.prelevement().isAfter(s.prelevement())
                    || (n.prelevement().equals(s.prelevement()) && n.saisie().isAfter(s.saisie()))));
            if (!plusRecenteExiste) aRisque.add(s.patient());
        }
        return aRisque;
    }

    /** Seul un résultat POSITIF ou NEGATIF est conclusif : un DOUTEUX ou un EN_COURS n'efface pas un positif antérieur (KB-MED-106). */
    private static boolean estConclusif(ResultatSerologique resultat) {
        return resultat == ResultatSerologique.POSITIF || resultat == ResultatSerologique.NEGATIF;
    }
}
