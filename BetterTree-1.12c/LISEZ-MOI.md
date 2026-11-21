# Better Tree 1.12

## Compiler
GitHub Actions (`.github/workflows/build.yml`) ou `mvn package` -> `target/BetterTree-1.12.jar` (Java 25, Paper 26.3).
Supprime l'ancien `config.yml` du plugin avant de relancer.

## Changements 1.12
- Champignons géants : un champignon vanilla trop près du bord de la zone générée est laissé intact au lieu d'être reconstruit coupé en deux (pied sans chapeau, demi-chapeau). Même vérification pour les pousses à la poudre d'os.
- Pics de glace : ils ne démarrent plus sur un autre pic (le sol réel est cherché sous la glace), un espacement minimum est respecté entre pics, et un pic à cheval sur deux chunks n'est plus coupé par le nettoyage du chunk voisin.
- Jungle : buissons (`jungle-bushes`) : touffes de feuilles sur une souche, fougères, grandes fougères et lianes.
- Savane : nouvelles acacias (tronc tordu, grandes branches nues en fourche, parasols plats de feuilles).
- Biomes froids : stalactites de glace dans les grottes (`cave-ice`), DÉSACTIVÉ par défaut.
- Arbres vanilla non remplaçables : supprimés (`trees.remove-unfit`), après 4 essais de formes différentes.
- Le jour, aucun monstre ne naît naturellement sous les feuilles (`no-day-spawn-under-leaves`).
- Le jour, aucun monstre ne naît dans les forêts (`no-day-spawn-in-forests`).
- Forêt de bois pâle : seuls les creakings naissent (`pale-garden-only-creakings`).
- Supprime l'ancien `config.yml` pour obtenir les nouvelles options.

## Changements 1.11
- Le plugin s'appelle maintenant **Better Tree** (jar `BetterTree-1.11.jar`, dossier `plugins/BetterTree/`). La commande reste `/arbres` (alias `/bettertree` et `/bt`), la permission reste `arbres.admin`.
- Champignons géants (île champignon) : ils ont maintenant des racines en champignon (pied) qui sortent du sol puis s'enfoncent dedans.

## Changements 1.10
- Grands arbres (taïga, troncs 2x2) : les couronnes sont maintenant centrées sur le tronc (elles étaient décalées d'un demi-bloc).
- Palmiers de plage supprimés (`palms` n'existe plus). Les palmiers restent dans les oasis.
- Moins de rochers (0.10), de rochers de grès (0.12), de ruines (0.02) et d'oasis (0.015).

## Changements 1.9
- Palmiers améliorés : tronc courbé qui penche dans 8 directions, léger renflement au pied, couronne dense avec des palmes qui montent puis retombent (longueur adaptée à la hauteur, bouts pendants, une palme parfois manquante), et parfois un palmier jumeau plus court. Valable sur les plages et dans les oasis.

## Changements 1.8
- Désert : rochers de grès (`sandstone-rocks`) et ruines de grès (`ruins.desert`).
- Jungle : ruines de pierre moussue avec lianes (`ruins.jungle`).
- Les ruines peuvent cacher un coffre enterré : une dalle sculptée au sol marque l'endroit, il faut creuser de 2 blocs. Butin : or, fer, émeraudes, rarement un diamant, plus des objets propres au biome.

## Changements 1.7
- Arbres bugués (troncs coupés, morceaux flottants) : un arbre n'est remplacé que si le nouvel arbre peut être posé EN ENTIER (dans la zone générée, tronc non bloqué par un autre arbre). Sinon l'arbre d'origine est laissé tel quel. Un arbre d'origine qui touche le bord de la zone traitée est aussi laissé intact.

## Changements 1.6
- End : les arbres d'os sont supprimés (code et config `end.trees` / `end.tree-chance` retirés). Seules les tours d'obsidienne restent (`end.towers`). Le plugin ne modifie aucun biome.

## Changements 1.5
- End : arbres d'os désactivés par défaut (`end.trees: false`).
- Nether : les gros arbres sont de retour (`mushrooms.nether-trees: true`). Un fungus qui touche le bord de la zone traitée est laissé tel quel au lieu d'être coupé ; ombrelles un peu plus petites, sans deuxième étage, pour ne plus fusionner en murs.
