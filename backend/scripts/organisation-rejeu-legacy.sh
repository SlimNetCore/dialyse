#!/bin/bash
# Rejeu REPRODUCTIBLE du filet de caracterisation d'Organisation contre le LEGACY : prouve que les goldens legacy archives sont exacts.
#
# 1. cree un worktree jetable HORS du depot sur b365a63a^ (dernier commit ou le legacy d'Organisation existe encore) ;
# 2. y copie le filet (tests Java `characterization/organisation` et leur support) et les goldens legacy :
#    - un golden sans variante `_nouveau_code` est un golden legacy tel quel ;
#    - pour chaque `X_nouveau_code.approved.json` (ecart decide), le golden legacy est l'archive `_ecarts-legacy/X.approved.json` ;
# 3. execute le filet en mode « verifier » (jamais `-Dcharacterization.approve`) : tout ecart fait echouer le test ;
# 4. supprime le worktree (et son dossier), meme en cas d'echec.
# Resultat attendu : code de sortie 0 = chaque golden legacy est reproduit par le legacy reel (aucun golden n'a ete invente).
# A lancer en arriere-plan avec un log : backend/scripts/organisation-rejeu-legacy.sh > rejeu-legacy-organisation.log 2>&1 &
# Aucune base partagee, jamais le port 5432 (H2 en memoire). Variable : COMMIT_LEGACY (defaut b365a63a^).
set -uo pipefail
COMMIT="${COMMIT_LEGACY:-b365a63a^}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
PARENT="$(cd "$RACINE/.." && pwd)"
WT="$PARENT/_wt-organisation-rejeu-legacy-$$"
TESTS=backend/platform/src/test/java/com/hemodialyse/backend/characterization/organisation
GOLDENS=backend/platform/src/test/resources/characterization/organisation
nettoyer() {
  git -C "$RACINE" worktree remove --force "$WT" >/dev/null 2>&1 || true
  rm -rf "$WT"
  git -C "$RACINE" worktree prune >/dev/null 2>&1 || true
}
trap nettoyer EXIT

echo "=== Worktree jetable sur $COMMIT : $WT ==="
git -C "$RACINE" worktree add --detach "$WT" "$COMMIT" >/dev/null 2>&1 || { echo "ECHEC : worktree impossible"; exit 3; }
[ -f "$WT/backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/SocieteRestController.java" ] \
  || { echo "ECHEC : $COMMIT ne contient pas le legacy d'Organisation"; exit 3; }

echo "=== Copie du filet et des goldens legacy ==="
mkdir -p "$WT/$TESTS" "$WT/$GOLDENS"
cp "$RACINE/$TESTS/"*.java "$WT/$TESTS/" || exit 3
N=0
while IFS= read -r f; do
  rel="${f#"$RACINE/$GOLDENS/"}"
  case "$rel" in
    */_ecarts-legacy/*) continue ;;                 # archives : copiees sous le nom de leur golden ci-dessous
    *_nouveau_code.approved.json) continue ;;       # golden du nouveau code : remplace par l'archive legacy
  esac
  mkdir -p "$WT/$GOLDENS/$(dirname "$rel")"
  cp "$f" "$WT/$GOLDENS/$rel"; N=$((N+1))
done < <(find "$RACINE/$GOLDENS" -name '*.approved.json')
A=0
while IFS= read -r f; do
  rel="${f#"$RACINE/$GOLDENS/"}"                    # ex. o3-referentiels/_ecarts-legacy/salles-du-centre-a.approved.json
  dossier="$(dirname "$(dirname "$rel")")"
  mkdir -p "$WT/$GOLDENS/$dossier"
  cp "$f" "$WT/$GOLDENS/$dossier/$(basename "$rel")"; A=$((A+1))
done < <(find "$RACINE/$GOLDENS" -path '*/_ecarts-legacy/*' -name '*.approved.json')
echo "goldens legacy copies : $N tels quels + $A archives d'ecarts decides"

echo "=== Execution du filet contre le LEGACY (mode verifier) ==="
cd "$WT/backend" || exit 3
./mvnw -o -pl platform -am test \
  -Dtest='com.hemodialyse.backend.characterization.organisation.*IntegrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
RC=$?
if [ "$RC" -eq 0 ]; then echo "=== OK : tous les goldens legacy sont reproduits par le legacy reel ==="; else echo "=== ECART : un golden legacy n'est pas exact (voir *.received.json dans le worktree, supprime ensuite) ==="; fi
echo "=== Code de sortie : $RC ==="
exit $RC
