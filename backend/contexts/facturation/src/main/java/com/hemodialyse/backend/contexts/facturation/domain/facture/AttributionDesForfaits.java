package com.hemodialyse.backend.contexts.facturation.domain.facture;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Attribution, SANS recalcul, des lignes figées d'une facture émise aux séances qui lui sont rattachées (RG-FAC-048). Le forfait n'est pas
 * figé par séance (seulement par ligne) : on le déduit.
 * <ol>
 *   <li>une séance portant une surcharge de forfait reçoit la ligne de même forfait (la première qui a encore des séances non attribuées,
 *   sinon la première de ce forfait) ; aucune ligne de ce forfait : non attribuable ;</li>
 *   <li>les autres séances, dans l'ordre reçu, reçoivent la SEULE ligne qui a encore des séances non attribuées (quantité figée − séances
 *   déjà attribuées) ; s'il n'y en a pas exactement une, le forfait de la séance est non attribuable.</li>
 * </ol>
 */
public final class AttributionDesForfaits {

    /**
     * Une séance rattachée à la facture, telle que lue.
     *
     * @param forfaitSurcharge forfait imposé à la séance, {@code null} sinon
     */
    public record SeanceAAttribuer(UUID seance, LocalDate date, String statut, UUID forfaitSurcharge) {
    }

    private AttributionDesForfaits() {
    }

    public static List<SeanceAttribuee> attribuer(List<LigneFacture> lignes, List<SeanceAAttribuer> seances) {
        int[] restantes = new int[lignes.size()];
        for (int i = 0; i < lignes.size(); i++) {
            restantes[i] = lignes.get(i).nombreDeSeances();
        }
        LigneFacture[] attribuees = new LigneFacture[seances.size()];
        for (int s = 0; s < seances.size(); s++) {
            UUID surcharge = seances.get(s).forfaitSurcharge();
            if (surcharge == null) continue;
            int choisie = -1;
            for (int i = 0; i < lignes.size(); i++) {
                if (!Objects.equals(surcharge, lignes.get(i).forfait())) continue;
                if (restantes[i] > 0) {
                    choisie = i;
                    break;
                }
                if (choisie < 0) choisie = i;
            }
            if (choisie >= 0) {
                attribuees[s] = lignes.get(choisie);
                restantes[choisie]--;
            }
        }
        for (int s = 0; s < seances.size(); s++) {
            if (seances.get(s).forfaitSurcharge() != null) continue;
            int seule = -1;
            int candidates = 0;
            for (int i = 0; i < lignes.size(); i++) {
                if (restantes[i] > 0) {
                    candidates++;
                    seule = i;
                }
            }
            if (candidates == 1) {
                attribuees[s] = lignes.get(seule);
                restantes[seule]--;
            }
        }
        List<SeanceAttribuee> resultat = new ArrayList<>(seances.size());
        for (int s = 0; s < seances.size(); s++) {
            SeanceAAttribuer seance = seances.get(s);
            resultat.add(new SeanceAttribuee(seance.seance(), seance.date(), seance.statut(), attribuees[s]));
        }
        return List.copyOf(resultat);
    }
}
