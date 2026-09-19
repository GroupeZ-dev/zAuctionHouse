# Plan d'application des correctifs — zAuctionHouseV4 + addon Redis

> **Objet** : appliquer les 119 constats de `AUDIT-CLUSTER.md` après vérification de chacun contre le code réel.
> **Révisions** : `zAuctionHouseV4` @ `49571d9` (develop) — `zAuctionHouse Redis` @ `d951be2`. Ce sont exactement les révisions auditées : les numéros de ligne de l'audit sont utilisables tels quels.
> **Correctifs prêts à appliquer** : `CORRECTIFS-PRETS.md` (120 étapes, code Java complet). Ce document-ci donne l'**ordre** et les **règles** ; l'autre donne la **matière**.
> **Date** : 6 septembre 2026.

---

## 1. Ce que la vérification a donné

Chaque constat a été rouvert contre le code, avec pour consigne explicite de chercher la raison pour laquelle il serait **faux**.

| | |
|---|---|
| Constats vérifiés | **119 / 119** |
| Confirmés tels quels | **92** |
| Réels mais surévalués, ou doublons | **27** (`PARTIAL`) |
| Déjà corrigés à HEAD | **0** |
| Invalidés | **0** |
| Numéros de ligne erronés | 3 (`C-011`, `C-095`, `C-110`) |
| Volume total des correctifs | **~5 100 LOC**, 120 étapes |

**Aucun constat de l'audit n'est tombé.** C'est le résultat le plus important : l'audit était statique et 47 de ses constats n'avaient jamais été contre-expertisés, mais les 119 décrivent des défauts réellement présents.

### Ce que la vérification a changé

**Trois doublons** — à ne surtout pas corriger deux fois, sous peine de faire cohabiter deux mécanismes concurrents sur la même méthode :

- `C-043` ≡ `C-020` (même méthode `ZStorageManager.createAuctionItem`, même défaut d'atomicité rédigé deux fois)
- `C-069` ≡ `C-068` (même ligne `ConfirmHelper.java:72`, même garde manquante)
- `C-021` ≡ `C-093` (même ré-ajout aveugle dans `ItemListedListener`)

**Sévérités corrigées** — dans les deux sens : `C-036` (repli silencieux en mono-serveur) passe de haute à **critique** ; `C-092`, `C-093`, `C-098`, `C-101`, `C-103`, `C-104` montent de moyenne à haute ; `C-007`, `C-050`, `C-057` redescendent ; `C-087`, `C-097`, `C-106` tombent en basse.

**Trois défauts que l'audit n'a pas vus**, découverts en vérifiant ses voisins :

- `PlayerRepository.select(List)` (`:37-39`) et `ItemRepository.select(List)` (`:94-96`) portent le **même `IN()` non paginé** que `C-001`, et ne sont couverts par aucun constat.
- `Hooks/ZelAuction/ZelAuctionMigrationService.java:372` contient à l'identique le `s.object("item_id", 0)` de `C-033` — même perte d'argent, autre migration.
- `StorageManager.selectSalesHistory(UUID)` est devenu `selectSalesHistory(UUID, long)` entre `deb8f16` et HEAD : **une rupture binaire déjà livrée et non annoncée**. L'addon Redis y survit par chance, pas par conception.

### Neuf correctifs proposés par l'audit sont eux-mêmes dangereux

À ne **pas** appliquer littéralement. Le détail et la contre-proposition sont dans `CORRECTIFS-PRETS.md` ; en résumé :

| Constat | Ce que propose l'audit | Pourquoi c'est pire |
|---|---|---|
| `C-011` | Passer la ligne en `DELETED` | Dans un cluster de versions Minecraft mélangées, un nœud ancien effacerait définitivement des items valides pour les autres |
| `C-083` | `removeListedItem` rend `CompletableFuture<StorageType>` | Méthode publiée (`api/AuctionManager.java:272`) : rupture binaire garantie pour l'addon figé |
| `C-108` | `if ("Unknown".equals(value)) continue;` | Une FK **interdit**, elle n'insère pas. Sauter l'insertion fait rejeter l'item entier sous MySQL et charge l'hôtel des ventes **vide** partout |
| `C-018` | Restaurer `LocalAuctionClusterBridge` au `onDisable` de l'addon | Ré-autorise la vente sous verrous locaux invisibles — exactement le défaut de `C-036` |
| `C-051` | `listedStacks + itemStacks.size() > maxSellPermission` | Redéfinit silencieusement `limit: 5` de « 5 annonces » en « 5 stacks » sur tous les serveurs existants |
| `C-064` | `notifyItemListed` puis `removeItem` | Ouvre une fenêtre d'un aller-retour Redis où un item déjà expiré s'affiche **achetable** partout |
| `C-104` | « `invalidateCategoryCountCache` devient inutile » | Fige chaque compteur de catégorie jusqu'au prochain reload. Il faut supprimer la **mémoïsation**, pas l'invalidation |
| `C-105` | `ConcurrentHashMap` + `computeIfAbsent` | `getOrCompute(ITEMS_EXPIRED)` redescend dans `clearPlayersCache`, soit un `remove()` sur la **même map** pendant son `computeIfAbsent` : le main thread peut boucler indéfiniment |
| `C-007` | `orTimeout` sur `auctionManager.purchaseItem` | À ce stade l'argent est débité et l'item remis ; un timeout déclencherait l'unlock et la restauration de statut **sur un achat commis** |

---

## 2. Le plan : un lot d'instrumentation, puis treize lots

Le plan tient en une idée : **on ne corrige pas ce qu'on ne sait pas mesurer**. Aujourd'hui une duplication n'est détectable que par la plainte d'un joueur. Le LOT 0 répare cela sans changer un seul comportement, et sert de mesure d'ampleur avant/après.

### LOT 0 — Instrumentation seule · aucun changement de comportement · ~120 LOC · risque nul

À livrer en premier, et à laisser tourner une semaine avant le LOT 1.

- **Mesurer avant de toucher au code, sans rien déployer** : la table `%prefix%logs` enregistre déjà chaque livraison. La requête `INV-02` (voir §7) chiffre sur les dumps de production actuels combien d'items ont été livrés deux fois. C'est le chiffre qui justifie tout le reste du programme.
- Propager le rowcount **en journal seul** — `Repository.update` jette la valeur que Sarah calcule déjà (`UpdateRequest.java:61`). 5 lignes, aucun changement de flux.
- Sonde de collision en lecture seule avant chaque `UPDATE` d'item (~15 lignes).
- Capturer le code retour d'`UNLOCK_SCRIPT`, aujourd'hui jeté (`RedisAuctionClusterBridge:257` et `:267`). 3 lignes.
- Compteur `EMPTY-ITEM` au chargement, `op=<uuid>;srv=<nom>` préfixé dans `logs.additional_data` (colonne libre, aucune migration), journalisation du bridge et du type de base au démarrage.
- Tout derrière un unique interrupteur `debug.audit`, une ligne par événement, préfixe `[ZAH-AUDIT]`, paires `clé=valeur` greppables.

### Les treize lots

Cette séquence est la version **corrigée après revue adverse** : trois constats avaient été mécaniquement perdus, et l'ordre initial séparait le compare-and-set de l'inversion commit→remise, ce qui aurait donné une release très risquée sans supprimer une seule duplication. Le détail des corrections est en §2 bis.

| Lot | Objet | Version | Constats | LOC | Risque |
|---|---|---|---|---|---|
| **1** | Identité de l'item, arrêt des saignées immédiates | 4.0.1.4 — **plugin seul** | `C-045` `C-046` `C-013` `C-100` `C-117` `C-054` `C-008` `C-118` `C-116` | ~195 | MED |
| **2** | Réécriture unique d'`adminRemoveItem`, contrat du verrou local, fuites de verrou | 4.0.1.5 + addon | `C-012` `C-042` `C-112` `C-110` `C-111` `C-061` **`C-070` (volet fuite de verrou)** | ~220 | MED |
| **3** | **Socle : compare-and-set universel *et* inversion commit → argent → remise** | 4.0.2.0 | `C-026` `C-094` `C-091` `C-078` `C-006` `C-114` `C-003` `C-119` `C-060` + `C-014` `C-005` `C-039` `C-083` `C-053` **`C-009`** | ~700 | **HIGH** |
| **4** | Chargement fail-closed, quarantaine, index | 4.0.2.1 | `C-001` `C-011` `C-038` `C-090` `C-109` `C-044` `C-073` `C-066` `C-029` | ~345 | MED |
| **R** | **Commande d'audit et de réparation des dégâts déjà en base** | 4.0.2.2 | *(aucun constat — manque du plan initial)* | ~250 | MED |
| **5** | Vente atomique : réserver, publier, rembourser | 4.0.2.3 | `C-020` `C-043` `C-067` `C-037` `C-030` `C-082` `C-080` `C-096` `C-051` `C-097` | ~295 | **HIGH** |
| **6** | Confinement de threads, politique d'arrêt | 4.0.2.4 | `C-022` `C-105` `C-107` `C-089` **`C-084`** *(`C-048`/`C-049` reportés au lot 10)* | ~200 | MED |
| **7** | Couche monétaire : un retrait qui peut dire non | 4.0.2.5 + addon | `C-015` `C-041` `C-017` `C-019` `C-032` `C-102` `C-027` `C-059` | ~380 | **HIGH** |
| **8** | Verrou distribué réel, addon fail-closed | 4.0.3.0 + addon | `C-058` `C-099` `C-002` `C-025` `C-052` `C-077` `C-086` `C-079` `C-007` `C-016` `C-113` `C-036` `C-018` `C-010` `C-031` `C-057` `C-095` **`C-024`** | **~1 100** | **HIGH** |
| **9** | Convergence du bus, TTL des confirmations, expiration planifiée | 4.0.3.1 + addon | `C-050` `C-004` `C-072` `C-021` `C-093` `C-071` `C-092` `C-047` `C-028` `C-023` `C-068` `C-069` `C-065` `C-088` `C-062` `C-063` `C-064` `C-035` `C-076` **`C-070` (plafond + sémaphore)** **`C-078` (volet cluster)** | ~660 | **HIGH** |
| **10** | Caches triés et compteurs : d'abord justes, ensuite rapides | 4.0.3.2 | `C-101` `C-056` **(debounce inclus)** `C-103` `C-098` `C-104` `C-055` `C-074` `C-048` `C-049` | ~280 | MED |
| **11** | Migrations V3 et hygiène des commandes admin | 4.0.3.3 | `C-040` `C-108` `C-075` `C-033` `C-034` `C-087` `C-081` `C-085` `C-115` | ~400 | MED |
| **12** | Fusion `checkAndLock` (suppression du TOCTOU) | 4.1.0.0 | `C-106` | ~80 | MED |

Les estimations de LOC **ne comptent pas** la passe i18n sur six jeux de langue, la passe Docusaurus EN+FR, ni la QA manuelle sur la matrice Paper × Folia × SQLite × MySQL × cluster. Sur treize releases coordonnées entre deux dépôts — dont l'addon n'a **aucune CI** — c'est ce coût-là, et non les LOC, qui est le facteur limitant.

### L'affirmation de l'audit sur les chantiers 1 à 4 est vraie sur le fond, fausse comme contrainte de livraison

L'audit écrit que les chantiers 1 à 4 sont « non négociables et à livrer ensemble ; chacun seul laisse un trou exploitable ». C'est exact **pour la famille des courses** — deux nœuds engageant le même item à la même milliseconde : seul le compare-and-set en base arbitre.

Mais le scénario que l'audit lui-même désigne comme *le plus facile pour un joueur ordinaire* — rouvrir `/ah selling`, se faire acheter l'item, recliquer le slot toujours affiché — **n'est pas une course**. C'est une référence mémoire périmée sans garde d'identité. Vérifié sur pièces : `ZAuctionManager.removeItem(StorageType,int)` (`:253-266`) retire l'objet de la map mais ne touche pas son statut, qui reste `AVAILABLE` ; le `Consumer` de clic garde une référence dure sur cette instance ; `RemoveService.removeSellingItem` (`:198-215`) ne teste que ce statut mémoire ; `executeRemoval` n'a aucune relecture ni garde d'identité.

**Une garde d'identité de six lignes en tête d'`executeRemoval` ferme ce chemin à elle seule**, puisqu'après `removeItem` le store rend `null` :

```java
if (this.plugin.getAuctionManager().getItem(storageType, item.getId()) != item)
    return RemoveResult.failure("stale reference", RemoveFailReason.ITEM_NOT_AVAILABLE);
```

C'est le LOT 1. Coût ~195 LOC, zéro rupture d'API, zéro migration, aucun prérequis. **La dupe la plus signalée se ferme dans la première release**, sans attendre le programme complet.

Deux précisions issues de la revue adverse :

- **Le LOT 1 n'a pas besoin d'être livré avec l'addon.** `ItemBoughtListener` appelle `manager.removeItem(StorageType.LISTED, id)`, et l'implémentation côté plugin (`:253-265`) tient déjà `Item removed = storage.remove(itemId)` : y poser `removed.setStatus(DELETED)` corrige les deux listeners de l'addon **sans le toucher**. La moitié addon proposée par `C-046` (recherche `O(n)` dans le store, dépendance à `C-050`) est inutile et coûteuse.
- **Le LOT 1 n'est pas « LOW ».** Il contient une correction sur l'argent (`C-013`), un changement de sémantique d'une méthode **publiée** (`AuctionManager.removeItem(StorageType,int)` mute désormais l'instance retirée) et un changement de profil TPS (`C-045`, ci-dessous). Il est classé MEDIUM.
- **La garde d'identité ne remplace pas la revalidation base.** Elle ferme le scénario du re-clic local ; elle ne couvre **pas** le cas dominant en cluster, où le message `ItemRemovedMessage` n'est pas encore arrivé : `getItem(LISTED,id)` rend alors toujours la même instance, la garde passe, l'item est rendu deux fois. `C-009` reste indispensable (LOT 3).

---

## 2 bis. Revue adverse : ce que ce plan corrige de sa première version

Le séquencement initial a été soumis à une critique de complétude. Elle a confirmé la couverture (116 / 119 constats ordonnancés au premier jet) et la stratégie de compatibilité, mais a trouvé cinq défauts qui sont désormais intégrés au tableau ci-dessus.

**1. Le compare-and-set sans l'inversion commit→remise ne supprime aucune duplication.** C'était l'erreur d'ordre la plus grave. Vérifié aux quatre sites de retrait (`ZAuctionManager` `:522-523`, `:568-569`, `:602-603`, `:636-637`) : `updateFuture = updateItem(...); giveItem(player, item);` — le `giveItem` est **synchrone et inconditionnel**, avant résolution du future. Le CAS rend l'`UPDATE` refusable, mais l'item est déjà dans l'inventaire du joueur. Entre les deux releases, la course perdue passerait de « silencieuse » à « détectée et journalisée », **la duplication restant intacte** — sur la release la plus risquée du programme. `C-026` et `C-005` sont mutuellement dépendants : l'un rend l'échec détectable, l'autre le rend non destructeur. **Ils sont fusionnés dans le LOT 3.**

**2. Trois constats étaient mécaniquement perdus** — `C-009`, `C-024`, `C-070`. Le plus coûteux est `C-070`, dont le point 3 est le **garde-fou du correctif que le LOT 9 active par défaut** : `tokenHolder.set(token)` est **dans** le `thenCompose` placé **après** `.orTimeout(lockItemTimeoutMs)` (`ExpireService:265-300`). Un timeout après pose du verrou laisse `tokenHolder` à `null`, le `whenComplete` teste `token != null` et **n'appelle jamais `unlockItem`**. C'est exactement `C-111`, sur un troisième site. Son volet « fuite de verrou » rejoint donc le LOT 2 (même patch, six lignes) ; ses volets plafond et sémaphore restent au LOT 9, où ils bornent `C-088`.

> **Régression évitée de justesse.** `C-088` livré sans `C-070` : un serveur redémarre après un week-end d'arrêt, 800 annonces ont dépassé leur terme. Le balayage les dispatche toutes ; `expiringItemIds` dédoublonne par id mais n'a **aucun plafond**. Chaque item coûte `checkAvailability` + `lockItem` + `selectItem` (3 requêtes) + `UPDATE` + 2 emprunts Jedis, le tout sur `commonPool` avant le LOT 6. Le pool sature, les `orTimeout` se déclenchent en masse — et par le bug ci-dessus, **chaque chaîne expirée laisse un verrou Redis de 30 s sur un item `LISTED`**. Pendant plusieurs minutes, une fraction de l'hôtel des ventes est ni achetable ni retirable, sur tous les nœuds.

**3. Deux volets orphelins entre documents, à vérifier avant de fermer les fiches :**

- **`C-060` volet SQL** (`where storage_type` pour la cible `PURCHASED`) — le LOT 2 l'exclut, le chantier 5 l'exclut, le chantier 1 dit « déjà incluse » sans le lister. Plausiblement absorbé par le CAS universel, mais **aucun document ne l'affirme**. C'est la garde du chemin d'achat **par défaut** (`give-item: false`). Explicitement rattaché au LOT 3.
- **`C-078` volet cluster** (routage des transitions vers `DELETED`) — renvoyé au chantier 7 par le chantier 1, jamais repris. Rattaché au LOT 9.

**4. Deux chemins mutants sans aucun constat**, à traiter comme des constats à part entière :

- `PlayerPlaceholders:21-22` → `getExpiredItems` / `getPlayerSellingItems` → `getItemIds:417` → `processExpiredItems`. **Une résolution PlaceholderAPI — donc un scoreboard rafraîchi à la seconde, sur le thread principal — déclenche des écritures DB et des `clearPlayersCache`**, et après le LOT 9 des allers-retours Redis avec verrou. À rattacher au LOT 9.
- `CommandAuctionAdminGenerate:211-212` : `applyCategories` + `addItem(LISTED, …)` depuis le thread async, donc mutation de `idsListedByOwner` (`IntArrayList` non thread-safe) pendant que le thread principal l'itère. Le LOT 11 ne couvre que la pré-génération.

**5. Trois erreurs d'ordre mineures, corrigées :** `C-084` (sérialisation des mutations d'économie) passe du lot monétaire au lot de confinement de threads — le verrou strippé ne rend pas légaux les appels Bukkit main-thread-only des providers `LEVEL`/`EXPERIENCE`/`ITEM` depuis `commonPool`, il allonge seulement la rétention d'un worker sous un moniteur. `C-048`/`C-049` glissent au LOT 10, dont ils dépendent via `C-023`. `C-103` exige le **debounce** de `C-056` (`min-rebuild-interval-ms`), que le chantier QUICK rejetait explicitement : sans lui, `C-103` salit le cache trié à chaque ouverture de GUI de confirmation, soit un tri complet de 50 000 items par clic.

### Deux manques du plan initial, désormais des lots

**Le LOT R — réparation des dégâts déjà en base.** Aucun des lots ne livrait de commande d'audit ou de réparation. Après treize releases, un réseau touché serait protégé pour l'avenir et **toujours cassé pour le passé** : items en double, lignes `items` orphelines issues de V3, annonces figées en `IS_*_CONFIRM`, clés Redis `auction:item:<id>` bloquées en `DELETED`. Le LOT R livre `/ah admin audit` (lecture seule, adossé aux invariants du §7) puis `/ah admin repair <invariant>` sous confirmation.

**Les tests.** `src/test/java` existe et contient **zéro fichier**. Le plan déplace ~4 700 lignes touchant l'argent, l'inventaire et la persistance, sur deux dépôts, et serait validé par du test manuel non spécifié. Écrire une poignée de tests sur `ItemRepository` et `ClaimService` **avant** de toucher au compare-and-set n'est pas une amélioration facultative : c'est ce qui attrape le `expectedFrom` faux qui fige les items définitivement. Voir §7.

### Deux risques d'exploitation à assumer explicitement

**Le `return` → `continue` de `C-045` a un coût TPS.** Il fait passer de « quelques joueurs rafraîchis » à N `runAtEntity` + N `updateInventory` **par vente et par retrait**. Sur 300 joueurs et un flux soutenu, c'est un coût main-thread neuf introduit dans la première release, alors que `C-074` et `C-105`, qui l'amortissent, arrivent bien plus tard. Mitigation disponible pour les gros réseaux : `update-inventory-on-action: false` — c'est-à-dire désactiver le rafraîchissement qu'on vient de réparer. À dire dans le changelog.

**Le fail-closed n'a aucune reprise.** `ZAuctionPlugin:160` appelle `loadItems()` sans `try/catch`. Après le LOT 4, un hoquet MySQL de deux secondes pendant un redémarrage tournant **désactive le plugin** sur ce nœud ; après le LOT 8, un hoquet Redis désactive l'addon. Pour un produit commercial déployé chez des tiers, c'est un incident transformé en panne manuelle. **Ajouter une reprise bornée** (n tentatives espacées) avant de livrer l'un ou l'autre.

**Interaction V3 × LOT 4, non vue initialement.** `C-075`/`C-033` produisent aujourd'hui des lignes `items` orphelines (sans `auction_items`) : ce sont exactement celles que le LOT 4 met en quarantaine ou, avec `selectOrFail`, sur lesquelles il **refuse de démarrer**. Un serveur migré depuis V3 peut donc ne plus booter après le LOT 4, dix releases avant que le LOT 11 arrête d'en produire. **Le LOT R doit précéder le durcissement du chargement, ou le LOT 4 doit mettre en quarantaine sans refuser le démarrage.**

---

## 3. Règles d'application non négociables

### 3.1 Correctifs à ne jamais livrer seuls

Trente-sept cas ont été identifiés ; voici ceux qui peuvent mettre un réseau hors service.

- **`C-002` sans le remappage de `removeItem`** — rendre `SOLD`/`REMOVED` terminaux dans `LOCK_SCRIPT` et `checkAvailability` alors que `RedisAuctionClusterBridge.removeItem` écrit `destination == DELETED ? DELETED : REMOVED` rend **tout item expiré non réclamable pendant 24 h** après le déploiement.
- **`C-099` sans `C-058`** — un incident Redis de deux secondes au démarrage condamne le nœud **définitivement** (tous les achats et retraits en `LOCK_FAILED`). C'est la seule correction du catalogue capable de rendre l'hôtel des ventes totalement inutilisable.
- **`C-077` sans la suppression des emprunts imbriqués** — dimensionner l'exécuteur à `pool.max-total` alors que `notifyItemBought`, `notifyItemListed` et `removeItem` prennent une connexion puis appellent `sendMessage` qui en prend une seconde, avec `blockWhenExhausted=true` et `maxWait=-1`, produit un **interblocage définitif et silencieux**.
- **`C-026` avec un `expectedFrom` faux** — le plus dangereux du catalogue. Sur les doubles transitions (`EXPIRED→DELETED`, `PURCHASED→DELETED`) ou les chemins admin, un `expectedFrom` erroné fait matcher 0 ligne pour des transitions **légitimes** : les items se figent définitivement, ni récupérables ni vendables. Livrer avec une surcharge transitoire `expectedFrom = null` pour tout chemin non encore audité.
- **`C-066`/`C-081`/`C-090` : toute migration d'index** — vérifié dans Sarah, `CreateIndexRequest.java:29-48` émet `CREATE INDEX` **sans `IF NOT EXISTS`** et lève une `DatabaseException` que `MigrationManager` relance jusqu'à `onEnable`. Un serveur portant déjà un index de même nom **ne démarre plus**. Une classe `Migration` par index, au minimum.
- **`C-046` (moitié plugin) mal appliqué** — marquer `DELETED` dans `removeItem(StorageType,int)` sans casser la délégation depuis `removeItem(StorageType,Item)` détruit tout achat : `ZAuctionManager:875` fait `removeItem(LISTED, item)` puis `:892` `addItem(PURCHASED, item)` sur la **même instance**, et `purchased-item.give-item: false` est le défaut (`config.yml:810`).
- **`C-059` avant le LOT 3** — déclencher l'auto-claim depuis `ItemBoughtListener` sans compare-and-set ouvre une fenêtre de **double crédit**.
- **`C-088` avant le LOT 3** — une tâche périodique sans rowcount ni élection de leader fait expirer et rendre **le même item sur N nœuds**. Le déclenchement fortuit actuel masque le problème ; la tâche le rendrait systématique.
- **`C-111`/`C-061`/`C-110` avant `C-008`** — le jeton déterministe rend le contrôle de propriété vrai pour tout le monde. `C-111` livré seul **introduit** une régression : l'unlock compensatoire tardif détruit le verrou légitime d'un autre nœud. `C-061` livré seul est inerte. `C-110` livré seul rend `adminRemoveItem` permissif en mono-serveur.
- **`C-057` sans les gardes de nullité** — renvoyer `null` pour une constante d'enum inconnue produit `setStatus(null)` : l'item devient invisible partout et chaque `switch` sur le statut lève une NPE.
- **`C-004` sans `C-072`** — le compare-and-swap transforme une perte de message pub/sub en désynchronisation **permanente**, là où l'application aveugle finissait par converger par hasard.
- **`C-034` avant `C-033`** — poser la sentinelle d'idempotence bloque le rejeu ; sur MySQL les serveurs déjà migrés n'ont **aucune** transaction `PENDING` et l'argent V3 serait perdu définitivement.
- **`C-098`/`C-104` avant `C-101`/`C-103`** — on remplacerait un compteur `O(n)` exact par un compteur `O(1)` durablement **faux**, et ces compteurs sont sur le chemin de rendu du plugin (`inventories/auction.yml:278`, 9 occurrences dans `categories.yml`), pas dans un scoreboard tiers.
- **`C-092` sans `C-045`** — `updateListedItems` ne rafraîchit qu'une partie des spectateurs : le correctif paraît appliqué et ne l'est pas.
- **`C-013` mal présenté** — le patch supprime la *destruction* d'argent ; il laisse entière la *duplication*, puisque `updateStatus` reste sans compare-and-set. Ne pas l'annoncer comme corrigeant le double claim.
- **`C-080` seul** — `saveData()` n'élimine pas la fenêtre, il l'**inverse** : on échange une duplication longue (~5 min) contre une perte courte (~50 ms). Acceptable uniquement avec la réservation de `C-020` dans le même lot.
- **`C-029` phase 2 avant phase 1** — activer l'écriture du préfixe de format avant que **tous** les nœuds sachent lire les deux formats casse le catalogue pendant un redémarrage tournant. Déploiement obligatoirement en deux phases.
- **`C-001` fail-closed** — `ZAuctionPlugin:160` appelle `loadItems()` sans `try/catch` : une panne DB transitoire au boot fera avorter `onEnable`. Les commandes sont enregistrées avant (`:156`) ; vérifier qu'un `/ah` orphelin ne subsiste pas.

### 3.2 Conflits d'édition : une seule passe, pas des patchs successifs

Vingt-sept zones ont été identifiées. Les plus chargées :

| Zone | Constats | Règle |
|---|---|---|
| `ZAuctionManager.java:679-709` (`adminRemoveItem`) | `C-012` `C-042` `C-060` `C-112` `C-005` | **Une réécriture unique au LOT 2**, pas quatre patchs. Le LOT 3 n'y reporte ensuite que l'inversion commit→remise |
| `ZAuctionManager.java:505-645` + `RemoveService.java:250-312` | `C-014` `C-005` `C-039` `C-053` `C-083` | Une seule passe, LOT 3 |
| `SellService.java:104-131` | `C-037` `C-030` `C-082` `C-080` `C-096` | Un seul bloc, ordre imposé `C-037 → C-030 → C-082 → C-080 → C-096` |
| `RedisAuctionClusterBridge` | `C-058` `C-099` `C-002` `C-025` `C-052` `C-077` | Ordre strict `C-058 → C-099 → C-002 → C-025 → C-052+C-077`. Appliquer `C-002` avant `C-099` fait jeter sa modification du repli non atomique |
| `PurchaseService.java` (le fichier le plus disputé : lots 2, 3, 8, 9) | | Ordre intra-fichier `C-111 → C-006/C-114 → C-079 → C-007 → C-035 → C-076` |
| `ItemLoaderUtils.createAuctionItem` + `ZAuctionItem` | `C-001` `C-011` `C-038` | Une seule édition. **Le point 3 de `C-001` *est* le correctif de `C-011`** |
| `ItemStatusListener.java:45-56` | `C-004` `C-050` `C-072` | Résolution `O(1)`, compare-and-swap et horodatage sur le même bloc |
| `ConfirmHelper` | `C-068` `C-069` | Extraire **un** helper privé portant la garde et la purge, pas deux patchs |
| `AuctionManager` (API) | `C-055` `C-098` `C-104` | Fusionner les ajouts d'API dans un seul commit |
| `CommandAuctionAdminAdd` | `C-062` `C-063` `C-064` | Ordre imposé `C-062 → C-063 → C-064` |

**Collision de nom à arbitrer** : le drapeau `staleDetected` de `RemovalContext` est introduit avec deux sémantiques différentes par le LOT 3 (réaction après échec d'écriture) et par `C-009` (refus préemptif). Livrer le LOT 3 d'abord et faire reposer `C-009` sur le drapeau existant.

---

## 4. Compatibilité : ne jamais changer une signature publiée

Le module `api/` est publié sur `repo.groupez.dev` et consommé par des plugins tiers. L'addon Redis est compilé contre un **SHA git figé** (`deb8f16`, trois mois) et implémente `AuctionClusterBridge`. La stratégie est uniforme et non négociable : **conserver chaque signature existante, ajouter des méthodes `default` portant un nom différent.**

| Symbole | Ce que veut le plan | Stratégie retenue |
|---|---|---|
| `AuctionEconomy.withdraw/deposit` → `boolean` | Un retrait doit pouvoir dire non | **Ne pas y toucher.** Ajouter `default boolean withdrawChecked(...)`, `depositChecked(...)`, `supportsOfflineDeposit()` |
| `AuctionClusterBridge.unlockItem` → `Boolean` | Récupérer le verdict du script Lua | **La rupture la plus dangereuse.** Conserver `unlockItem`, ajouter `default CompletableFuture<Boolean> releaseLock(...)` |
| `LockToken.of(Item)` | Jeton unique par acquisition | Ajouter `issue(Item, UUID)`, garder `of`. **Levier le plus puissant du programme** : le shadowJar du plugin ne relocalise **pas** `fr.maxlego08.zauctionhouse.api.*` et l'addon la déclare en `compileOnly` — le binaire addon déjà installé chez les clients résout la classe du plugin. `C-008` est donc **déployable en une seule release du plugin**, sans toucher à l'addon |
| `renewLock`, `isHeldBy`, `lockLeaseDuration`, `checkAndLock` | Bail vivant, fusion du pré-contrôle | Toutes en `default`, avec des défauts reproduisant **exactement** le comportement actuel (`lockLeaseDuration()` → `Duration.ZERO`, `renewLock` → `false`, `isHeldBy` → `true`) |
| `PerformanceConfiguration` (record) | Ajouter `lock-lease-ms` | **Ne pas y toucher** : une composante de plus change le constructeur canonique. Créer un record `MaintenanceConfiguration` + `default getMaintenance()` |
| `TaxResult` (record) | Ajouter `appliedType` | Déclarer explicitement un constructeur `@Deprecated` à 7 arguments : descripteur JVM identique, donc la construction depuis un binaire ancien continue de se lier |
| `StorageManager.updateItem/updateItems` | Remonter le rowcount | Surcharges `default` rendant `Integer`, signatures historiques conservées |
| `StorageManager.selectItem(int)` | Distinguer les trois causes de `null` | Conserver, ajouter `default selectItemState(int)`. L'addon **exploite délibérément** le `null` sur ligne `DELETED` (`ItemBoughtListener:38-40`) |

### Ordre de publication

1. **Pré-vol** — vérifier que `repo.groupez.dev` expose bien des versions sémantiques de `zauctionhousev4-api`. Le cache Gradle local en contient déjà deux (`4.0.0.3` et `deb8f16`) : le pin sémantique est **possible dès aujourd'hui**, contrairement à ce que suggère `C-095`.
2. **Dépôt plugin, lot API purement additif** (LockToken, `default` d'`AuctionClusterBridge`) — livrable seul.
3. Le push sur `develop` déclenche la CI (`project-to-publish: "API:publish"`). **Ne pas passer à la suite avant d'avoir constaté l'artefact publié.**
4. **Déploiement du plugin seul.** À ce stade le binaire addon déjà installé est **déjà corrigé** sur `C-008`.
5. **Dépôt addon** : bumper le pin (`REDIS/build.gradle.kts:49`), surcharger les `default`.
6. **Ordre runtime invariant sur toute la séquence : plugin d'abord, addon ensuite.** Un addon neuf sur un plugin ancien lève `NoSuchMethodError`.

L'addon **n'a aucune CI** (`.github` absent) : son build est manuel et sa publication passe par `System.setProperty` dans le script Gradle. À industrialiser avant le LOT 8, qui le modifie lourdement.

---

## 5. Migrations de schéma

Six migrations, toutes additives et réversibles par downgrade de jar — **sauf** les lignes sentinelles V3.

| Migration | Lot | Réversible | Piège |
|---|---|---|---|
| `claim_token` + `claim_reserved_at` | 3 | oui | **`MigrationManager` n'exécute une migration que si son nom n'est pas déjà enregistré.** Passer `CreateTransactionsMigration` de `create` à `createOrAlter` ne suffit pas si le nom est inchangé : la colonne ne serait **jamais** ajoutée sur une base existante |
| `pending_publish` sur `items` | 6 | oui | `items` est la plus grosse table : `ADD COLUMN` déclenche un rebuild in-place de plusieurs minutes sur MySQL 5.7 |
| `fence BIGINT` sur `items` | 8 | oui | Le chemin `ALTER` de Sarah émet `addColumn().nullable()` **sans valeur par défaut** : toutes les lignes existantes auront `fence IS NULL`. Toute clause `WHERE fence < ?` doit en tenir compte |
| Index (`items(storage_type)`, `items(seller_unique_id, storage_type)`, `transactions(player_unique_id, status)`, `logs(created_at)`, `players(name)`) | 4 | oui | **Risque élevé** : `CREATE INDEX` sans `IF NOT EXISTS` dans Sarah → le plugin ne démarre plus si l'index existe. **Une classe `Migration` par index.** `C-081` ne doit pas recréer `players(name)`. La partie index de `C-090` est à abandonner : `items.expired_at` n'apparaît dans aucune clause `WHERE` du projet |
| Table `migration_state` | 12 | oui | `CREATE TABLE IF NOT EXISTS` — race-safe. À enregistrer **en dernier** dans `ZStorageManager` |
| Sentinelles V3 (remplacement de `item_id = 0`) | 12 | **non** | Pas de DDL, mais les lignes subsistent après un downgrade. Sous SQLite les FK ne sont pas appliquées (pas de `PRAGMA foreign_keys=ON`) : le symptôme bascule de « argent perdu » sous MySQL à « argent doublable si on rejoue » sous SQLite |

---

## 6. Configuration, traductions, documentation

### Six jeux de configuration, pas quatre

`ls src/main/resources/` rend la racine (anglais) plus **`es/`, `fr/`, `id/` (indonésien), `it/`, `th/` (thaï)**. Le `CLAUDE.md` du projet annonce quatre langues : **à corriger dans le premier lot qui touche une clé de configuration**, sinon chaque lot suivant reproduira l'oubli.

**Le mécanisme rend l'oubli invisible.** `ZAuctionPlugin.saveFile` (`:583-593`) ne retombe sur la racine anglaise que si le fichier localisé **n'existe pas** dans le jar. Or `fr/config.yml` existe : une clé absente de ce fichier ne retombe donc **pas** sur l'anglais, elle retombe sur la constante Java codée en dur. Conséquence par type de fichier :

- `config.yml` / `economies.yml` — échec **totalement silencieux**. L'administrateur ne voit jamais la clé et ne peut pas la régler. Aucun log, aucun symptôme.
- `messages.yml` — **huit clés nouvelles** à travers les chantiers, chacune valant une constante dans `Message.java` **plus six fichiers** : `item-no-longer-available`, `admin-item-not-available`, `remove-all-items-partial` (avec `%amount%`), `sell-error-invalid-item`, `sell-inventory-full`, `migration-already-done`, `server-shutting-down`, et la clé d'indisponibilité du cluster.
- **Piège `id/` et `th/`** : ne pas y déposer le texte anglais « en attendant ». Une clé présente mais non traduite ne déclenche **aucun** avertissement, contrairement à une clé absente — la régression linguistique devient invisible et permanente.

**À mettre en place** : une comparaison mécanique des jeux de clés entre la racine et les cinq dossiers, au build ou en hook de pre-commit. La réplication est aujourd'hui purement manuelle et rien ne la contrôle.

Côté addon Redis : un seul `config.yml`, non traduit — les clés du LOT 8 n'ont aucun coût multilingue.

### Changelog et documentation

Obligation du `CLAUDE.md` : `changelogs.md` (section `# Unreleased` déjà présente en tête) **dans le même commit que le code**, puis la doc Docusaurus externe en **EN** (`plugins/zauctionhouse/docs/`) **et FR** (`i18n/fr/docusaurus-plugin-content-docs-zauctionhouse/current/`).

À mettre **en tête du changelog** : le refus de `storage-type: SQLITE` + addon Redis. Un réseau aujourd'hui dans cette configuration — cassée mais silencieuse — perdra sa pseudo-synchronisation au premier redémarrage.

À documenter aussi : la **matrice de compatibilité** « zAuctionHouse ≥ X exige zAuctionHouseRedis ≥ Y », que rien n'impose aujourd'hui (l'addon est figé sur un SHA d'API vieux de trois mois et son `VersionChecker` est inopérant) ; et la **procédure de remédiation V3**, qu'aucun correctif ne remplace : les installations MySQL déjà migrées portent leurs items mais **aucune** transaction `PENDING`.

---

## 7. Validation

### Le banc d'essai

- **Trois nœuds, pas deux.** `RedisSubscriberRunnable:121` filtre les messages par `serverId` : l'émetteur ne reçoit pas les siens. Deux serveurs suffisent pour une course acheteur/acheteur, pas pour observer les fantômes.
- **Une seule instance MySQL/MariaDB partagée. SQLite est interdit** sur le banc cluster : les ids d'items entreraient en collision et aucun test ne signifierait rien.
- **Redis dédié**, `lock-ttl-seconds: 10` au lieu de 30 pour rendre les scénarios de bail jouables à la main.
- **Injection de latence, sans aucun outil** : `redis-cli DEBUG SLEEP 40` gèle tout le cluster (Redis est mono-thread) ; `LOCK TABLES zauctionhousev4_items WRITE;` depuis une session MySQL tierce est **l'outil le plus important du banc** et ne demande aucun code.
- **Crash au bon moment** : un jar `-test` portant `if (System.getProperty("zah.test.crash","").equals(<étape>)) Runtime.getRuntime().halt(1);` à quatre endroits précis.
- **Deux comptes réels ou un compte + un bot** : zMenu applique un cooldown de clic de 100 ms par joueur, qui avale le double-clic humain. Toute course d'achat doit être jouée par deux clients.
- **Économies de test** : Vault + une économie `LEVEL` et une `ITEM` déclarées dans `economies.yml` — ce sont elles qui portent `C-032`.
- Un **serveur Folia** en plus pour les LOTS 2 et 8 : les deux dépôts déclarent `folia-supported: true`.
- `mysqldump` complet avant chaque lot.

**60 tests** ont été rédigés, répartis par lot, chacun avec son résultat **avant** correctif — un test qui passe déjà avant ne prouve rien. Détail dans les sections `Validation` de `CORRECTIFS-PRETS.md`.

### Dix invariants SQL

À faire tourner en continu. Trois des cinq requêtes proposées par l'audit étaient fausses ou inexploitables et ont été remplacées ; cinq requêtes nouvelles ont été ajoutées, dont :

- **`INV-02` — item livré physiquement plus d'une fois.** *Le* détecteur de duplication. La version de l'audit groupait mal. **À exécuter une fois sur chaque dump client avant le premier correctif** : c'est la mesure d'ampleur du problème.
- **`INV-05` — conservation monétaire.** Remplace la quatrième requête de l'audit, inexploitable.
- **`INV-07` — réservation de claim bloquée**, obligatoire dès le LOT 3 (le chantier documente lui-même un trou résiduel).
- **`INV-09` — arriéré d'expiration**, qui chiffre `C-088`, défaut que l'audit décrit sans jamais le mesurer.

### Tests automatisés : oui, et le squelette existe déjà

`src/test/java` et `src/test/resources` **existent sur disque, vides et non suivis par git**. `build.gradle.kts` ne déclare aucune dépendance de test. Le coût d'amorçage est d'une dizaine de lignes de build, pas d'un chantier — et le précédent est dans le même workspace : **Sarah** possède 12 classes JUnit 5 et une `DatabaseTestBase` qui monte un SQLite réel en `@BeforeEach`.

Ce qui est testable sans serveur Bukkit, par valeur décroissante :

- **La couche de stockage contre un vrai SQLite** — c'est là que vit le LOT 3, le socle de tout le plan, et c'est déterministe. Notamment : le rowcount d'`UpdateBatchRequest` est un **point d'incertitude explicite** (certains pilotes rendent `SUCCESS_NO_INFO` = −2, ce qui rendrait le détecteur de course inutilisable) — un test de 15 lignes sur `sqlite-jdbc` **et** `mariadb-java-client` tranche la question, là où l'inspection de code ne le peut pas.
- La course sur `claim_token` : deux connexions, deux threads, un seul gagnant.
- `CreateTransactionsMigration` en `createOrAlter` ajoute-t-elle vraiment la colonne sur une base préexistante.
- La pagination du `IN()` au-delà de 65 000 ids.

**Le projet est le seul de son workspace à livrer de l'argent de joueur sans un seul test.**

---

## 8. Ce qui change pour un administrateur de serveur

À annoncer avant de livrer, pas après.

- **Refus de démarrer en `SQLITE` + cluster** (`C-010`). La combinaison est objectivement indéfendable, mais un réseau en production dans cet état perdra sa pseudo-synchronisation au premier redémarrage. **Trajectoire recommandée en trois temps** : release N, quatre lignes `SEVERE` explicites (constat, conséquence, marche à suivre) et rien d'autre ; release N+1, bridge ignoré côté plugin et auto-désactivation côté addon ; release N+2, refus dur éventuel.
- **Mode fail-closed du cluster** (`C-036`, `C-018`, `C-099`) — changement d'exploitation le plus lourd après le précédent. Un mono-serveur ayant installé l'addon « au cas où » et perdant Redis ne pourra plus ni vendre ni acheter, alors qu'il fonctionne correctement aujourd'hui puisqu'il est seul.
- **Messages d'erreur nouveaux sur des opérations qui « marchaient »** : le compare-and-set fait échouer avec un message ce qui réussissait en écrasant silencieusement le travail d'un autre serveur. C'est l'objectif, mais il faut le dire.
- **Changements économiques chiffrables** : les règles de taxe par item s'appliquent désormais à l'achat même sur une économie `type: SELL` (le défaut d'`economies.yml:136`). Un serveur ayant laissé une règle item `PURCHASE 10 %` en la croyant inerte la verra s'activer.
- **Disparition visible d'items** (`C-011`, `C-029`) : un item au contenu Base64 illisible cesse d'être affiché en `BARRIER` cliquable au prix du vendeur. Les lignes restent intactes en base et un `SEVERE` les identifie, mais les exploitants le signaleront.
- **Migration d'index au démarrage** : sur une table de plusieurs centaines de milliers de lignes, plusieurs dizaines de secondes à plusieurs minutes **par index**, serveur figé sur « Enabling zAuctionHouse ».
- **Tâche d'expiration activée par défaut** (`C-088`) : c'est le comportement correct, mais sur un serveur avec un gros arriéré, des centaines d'items basculent en `EXPIRED` dans les minutes suivant la mise à jour.
- **Ce qui ne change pas, et qu'il faut dire aussi** : tant que le fencing en base et la propagation du rowcount ne sont pas livrés ensemble, le verrou distribué reste une **optimisation de contention**, pas une preuve d'exclusion mutuelle.

---

## 9. Décisions qui appartiennent au mainteneur

Ces arbitrages ne peuvent pas être pris à sa place ; ils conditionnent plusieurs lots.

1. **`C-010` : refus dur ou avertissement ?** La trajectoire en trois temps est recommandée, mais elle étale la fermeture du trou sur trois releases.
2. **Mode fail-closed pour un mono-serveur ayant l'addon** — faut-il une clé `cluster.required` explicite plutôt qu'une déduction ?
3. **`C-051` : la clé `limit` compte-t-elle des annonces ou des stacks ?** Changer la sémantique est une régression commerciale ; la garder laisse la limite contournable d'un facteur 36.
4. **Suppression ou conservation des méthodes dépréciées** de l'API une fois les appelants migrés.
5. **Volet cluster de `C-078`** : router les transitions vers `DELETED` par verrou + relecture + diffusion coûte 1 verrou, 3 requêtes et 2 emprunts Jedis **par item expiré**. Acceptable ?
6. **`C-009` vs LOT 3** : l'audit propose de supprimer la relecture `selectItem` de `PurchaseService:118-125` au motif que le CAS la rend redondante. L'architecte du chantier 1 a tranché **contre** — à confirmer.
7. **Amorcer `src/test`** — dix lignes de build, et le LOT 3 devient vérifiable automatiquement. *(Recommandation ferme : à faire avant le LOT 3, pas après.)*
8. **Périmètre du LOT R** — `/ah admin audit` en lecture seule suffit-il pour une première livraison, ou faut-il la réparation automatique dès le départ ? La réparation touche des items appartenant à des joueurs.
9. **Politique de reprise du fail-closed** — combien de tentatives, à quel intervalle, avant de désactiver le plugin ou l'addon ? Le choix entre « panne visible » et « fonctionnement dégradé silencieux » est un arbitrage produit, pas technique.
10. **Coût TPS de `C-045`** — l'assumer et le documenter, ou livrer `C-074` et `C-105` dans le même lot pour l'amortir immédiatement (au prix d'un LOT 1 nettement plus gros et plus risqué) ?

---

## 10. Par où commencer

```
Avant tout   Amorcer src/test (≈10 lignes de build, DatabaseTestBase copiée de Sarah).
             Sans cela, le LOT 3 — le plus risqué du programme — est invérifiable.

LOT 0        Instrumentation seule. Aucun changement de comportement.
             ➜ exécuter INV-02 sur les dumps de production : chiffrer le problème
               avant d'écrire une ligne de correctif.
             Laisser tourner une semaine.

LOT 1        Garde d'identité + arrêt des saignées. ~195 LOC, plugin seul.
             ➜ ferme la dupe la plus signalée, sans rupture d'API ni migration.
             Annoncer le coût TPS de C-045 dans le changelog.

LOT 2        Réécriture unique d'adminRemoveItem + les trois fuites de verrou
             (C-111 sur deux sites, C-070 sur le troisième — même patch).

LOT 3        SOCLE. Compare-and-set universel ET inversion commit → argent → remise,
             dans la même release. ~700 LOC, la plus risquée du programme.
             ➜ ne pas la scinder : le CAS seul détecte la course après que l'item
               a quitté la base pour l'inventaire du joueur.
             ➜ tests d'abord : ItemRepository (le rowcount d'UpdateBatchRequest
               peut rendre SUCCESS_NO_INFO = −2 selon le pilote) et ClaimService.

LOT 4        Chargement fail-closed — avec reprise bornée, et sans refuser le
             démarrage sur les lignes orphelines héritées de V3.
LOT R        Commande d'audit puis de réparation des dégâts déjà en base.
             ➜ à livrer avant, ou avec, le durcissement du chargement.

Puis         LOTS 5 à 12 dans l'ordre du tableau §2.
```

Chaque étape de `CORRECTIFS-PRETS.md` est de taille d'un commit, porte son code Java complet, et laisse le projet compilable. Les prérequis inter-lots sont dans la colonne « bloqué par » de chaque lot, et les zones à réécrire en une seule passe sont au §3.2.

**Le seul point où ce plan peut échouer silencieusement** : `C-026` avec un `expectedFrom` faux sur une double transition ou un chemin admin fige les items définitivement, ni récupérables ni vendables. C'est le correctif le plus dangereux du catalogue, il est dans la release la plus chargée, et rien dans le dépôt ne l'attraperait aujourd'hui. Livrer avec une surcharge transitoire `expectedFrom = null` pour tout chemin non encore audité, et un test par transition.

---

# Annexe — Revue adverse du plan (texte intégral)

> Critique de complétude menée contre le séquencement initial, avec pour consigne unique : « qu'est-ce qui manque ou qu'est-ce qui est faux ? ».
> Ses conclusions actionnables sont intégrées au §2 bis ; ce texte est conservé pour le détail des scénarios de régression.
> **Attention** : les numéros de lot cités ci-dessous sont ceux du séquencement *initial*, celui que cette revue critique.
> Correspondance avec le tableau corrigé du §2 : ancien lot 5 (inversion) → fusionné dans le LOT 3 · ancien 6 → 5 · ancien 10 (threads) → 6 ·
> ancien 7 (monétaire) → 7 · ancien 11 (caches) → 10 · ancien 12 (V3) → 11 · ancien 13 → 12.

## 1. Constats non couverts

**Vérification mécanique : 116 des 119 ids sont ordonnancés. Trois manquent.**

| id | sév. | fichier | pourquoi ça compte |
|---|---|---|---|
| **C-009** | HAUTE | `services/RemoveService.java:200` | Aucun lot ne planifie la revalidation autoritaire sous verrou du chemin de retrait. Le lot 1 prend la garde d'identité (C-046), le lot 2 prend `adminRemoveItem` — la relecture DB et le drapeau `staleDetected` du chantier 5 tombent entre les deux. Vérifié à HEAD : `executeRemoval` (l.194) enchaîne checkAvailability → lock → statut → suppression locale **sans une seule lecture base**. La garde d'identité ne couvre PAS le cas dominant en cluster : quand le message `ItemRemovedMessage` n'est pas encore arrivé, `getItem(LISTED,id)` rend toujours la même instance, la garde passe, l'item est rendu deux fois. |
| **C-024** | HAUTE | `REDIS/ZAuctionHouseRedis.java:199-210` | Ses `dep` sont C-016 et C-036, tous deux au lot 8 — il a simplement été perdu. Vérifié : l'échec d'enregistrement n'est qu'un `warning`, et le heartbeat est un `jedis.expire(key, TTL)` nu : si Redis redémarre ou évince la clé, `EXPIRE` rend 0 et **rien ne la recrée** — le nœud se croit enregistré à vie sans l'être, et un conflit d'UUID n'est jamais détecté. |
| **C-070** | MOYENNE | `services/ExpireService.java:265-300` | Aucun plafond global sur les expirations clusterées en vol, et surtout : `tokenHolder.set(token)` est **dans** le `thenCompose` placé **après** `.orTimeout(lockItemTimeoutMs)`. Un timeout après pose du verrou laisse `tokenHolder` à `null`, le `whenComplete` teste `token != null` et **n'appelle jamais `unlockItem`**. C'est exactement C-111, sur un troisième site que le lot 2 ne touche pas. |

**Deux volets orphelins entre documents :**
- **C-060 volet SQL** (`where storage_type` pour la cible PURCHASED) : le lot 2 l'exclut explicitement, le chantier 5 l'exclut explicitement, le chantier 1 dit « déjà incluse ici » mais ne le liste ni dans `covers` ni dans les findings du lot 3. Il est plausiblement absorbé par le « CAS universel » de l'étape 7, mais **aucun document ne l'affirme** — à vérifier avant de fermer la fiche, c'est la garde du chemin d'achat **par défaut** (`give-item: false`).
- **C-078 volet cluster** (routage des transitions vers DELETED) : le lot 3 le renvoie « au chantier 7 avec C-070 », et le chantier 7 (lot 9) ne liste ni l'un ni l'autre.

**Chemins mutants sans aucun id de constat, nommés dans les openQuestions puis jamais assignés :**
- `PlayerPlaceholders:21-22` → `getExpiredItems` / `getPlayerSellingItems` → `getItemIds:417` → `processExpiredItems`. **Une résolution PlaceholderAPI (scoreboard rafraîchi à la seconde, thread principal) déclenche des écritures DB et des `clearPlayersCache`** — et, après le lot 9, des allers-retours Redis avec verrou. Aucun lot.
- `CommandAuctionAdminGenerate:211-212` : `applyCategories` + `addItem(LISTED, …)` depuis le thread async, donc mutation de `idsListedByOwner` (`IntArrayList` non thread-safe) pendant que le thread principal l'itère. Le lot 12 ne couvre que la pré-génération et la rétention de `Player`.

## 2. Erreurs d'ordre

**La plus grave — C-026 (lot 3) avant C-014/C-005 (lot 5).** Vérifié aux quatre sites de retrait (`ZAuctionManager` l.522-523, 568-569, 602-603, 636-637) : `updateFuture = updateItem(...); giveItem(player, item);` — le `giveItem` est **synchrone et inconditionnel**, avant résolution du future. Le lot 3 rend l'UPDATE refusable, mais l'item est déjà dans l'inventaire du joueur. Résultat entre lot 3 et lot 5 : la course perdue passe de « silencieuse » à « détectée et journalisée », **la duplication reste intacte**. Le `criticalPath` présente le lot 3 comme « le seul véritable goulot » qui arbitre les courses : c'est faux pour le chemin de retrait. C-026 et C-005 sont mutuellement dépendants (l'un rend l'échec détectable, l'autre le rend non destructeur) et doivent partir dans la même release, avec un lot 4 qui ne s'intercale pas.

**C-088 (lot 9) sans C-070.** Le lot 9 active un balayage périodique qui multiplie systématiquement le chemin clusterisé que C-070 devait borner — sémaphore, taille de lot, et correction de la fuite de verrou. C-070 n'est nulle part. Voir §3.

**C-084 (lot 7) avant le confinement de threads (lot 10).** Le verrou strippé sérialise les mutations des providers LEVEL/EXPERIENCE/ITEM mais ne rend pas légaux leurs appels Bukkit main-thread-only depuis `commonPool` — il allonge seulement la rétention d'un worker sous un moniteur. L'ordre utile est lot 10 puis lot 7.

**C-103 (lot 11) sans le debounce de C-056.** Le chantier QUICK ne retient de C-056 que « les parties gratuites » et **rejette explicitement** `min-rebuild-interval-ms`. Le lot 11 liste pourtant C-056 comme couvert. Sur 50 000 items, C-103 salit le cache trié à chaque ouverture de GUI de confirmation → un tri complet par clic de confirmation, sans coalescence.

**Le lot 1 n'a pas besoin d'être cross-repo.** Le plan impose une release conjointe « obligatoire : C-046 est crossRepo ». Or `ItemBoughtListener` étape 1 appelle `manager.removeItem(StorageType.LISTED, id)`, et l'implémentation plugin (l.253-265) tient déjà `Item removed = storage.remove(itemId)` : y poser `removed.setStatus(DELETED)` corrige les deux listeners de l'addon **sans le toucher**. La moitié addon proposée par la fiche (recherche O(n) dans le store, dépendance à C-050 du lot 9) est inutile et coûteuse. Retirer cette contrainte de coordination de la toute première release.

## 3. Régressions introduites par le plan

**1. Tempête d'expiration + fuite de verrous (lot 9, C-088 sans C-070).** Scénario concret : un serveur redémarre après un week-end d'arrêt, 800 annonces ont dépassé leur terme. Le balayage les dispatche toutes ; `expiringItemIds` dédoublonne par id mais n'a **aucun plafond**. Chaque item = checkAvailability + lockItem + `selectItem` (3 requêtes SQL) + UPDATE + 2 emprunts Jedis. Avant le lot 10, tout part sur `ForkJoinPool.commonPool`. Le pool sature, les `orTimeout` se déclenchent en masse — et par le bug `tokenHolder`-après-`orTimeout` (C-070, non planifié), **chaque chaîne expirée laisse un verrou Redis de 30 s sur un item LISTED**. Pendant plusieurs minutes, une fraction significative de l'hôtel des ventes est ni achetable ni retirable, sur tous les nœuds.

**2. Coût TPS livré en premier, correctif dix releases plus tard (lot 1, C-045).** Vérifié l.956 : `if (onlinePlayer == ignoredPlayer) return;`. Le `continue` fait passer de « quelques joueurs rafraîchis » à N `runAtEntity` + N `updateInventory` par vente et par retrait. Sur 300 joueurs et un flux soutenu, c'est un coût main-thread neuf dans la release la plus « LOW risk » du plan, alors que C-074 (lot 11) et C-105 (lot 10) qui l'amortissent arrivent bien plus tard. La seule mitigation est de dire aux exploitants de passer `update-inventory-on-action: false`, c'est-à-dire de désactiver le rafraîchissement qu'on vient de réparer.

**3. Fail-closed sans reprise (lot 4 C-001 + lot 8 C-036).** `ZAuctionPlugin:160` appelle `loadItems()` sans try/catch. Après le lot 4, un hoquet MySQL de deux secondes pendant un redémarrage tournant **désactive le plugin** sur ce nœud ; après le lot 8, un hoquet Redis désactive l'addon. Aucun des deux lots ne prévoit de reprise bornée (n tentatives espacées) — seulement le refus. Pour un produit commercial déployé chez des tiers, c'est un incident d'exploitation transformé en panne manuelle.

**4. Le lot 2 change le code d'échec en mono-serveur avant que l'appelant sache le traiter.** C-110 fait passer `LocalAuctionClusterBridge.lockItem` de `failedFuture` à `LockToken.noop()`, donc `INTERNAL_ERROR` → `LOCK_FAILED` pour tout retrait contendu. Le traitement correct de ces codes dans le retrait en masse (C-053) n'arrive qu'au lot 5.

**5. Le plan ne prévoit rien pour les dégâts déjà en base.** La fiche C-083 le dit pour son propre périmètre (« les items déjà bloqués sur les serveurs en production ne sont pas débloqués par le correctif »), mais aucun des 13 lots ne livre de commande d'audit ou de réparation : items en double, lignes `items` orphelines issues de V3, annonces figées en `IS_*_CONFIRM`, clés Redis `auction:item:<id>` à `DELETED`. Après 13 releases, un réseau touché est protégé pour l'avenir et toujours cassé pour le passé.

## 4. Trous de raisonnement

- **Il n'y a aucun test.** `src/test` existe et contient **zéro fichier `.java`**. Le plan déplace ~4 700 lignes touchant l'argent, l'inventaire et la persistance sur deux dépôts, et ne mentionne à aucun endroit ni scaffolding de test, ni protocole de validation, ni matrice de QA. Les 13 releases seront validées par du test manuel non spécifié.
- **Folia n'est vérifié que d'un côté.** Le plan confine `giveItem` par `runAtEntity` (lot 5) mais ne dit jamais si `player.saveData()` (lot 6, C-080) est légal depuis le thread de région Folia, alors que le flux `SellService` est asynchrone au moment où il faudrait l'appeler.
- **SQLite mono-serveur — le stockage par DÉFAUT — n'est jamais chiffré.** Le lot 3 ajoute une réservation `claim_token`, le lot 6 une insertion en deux phases sur le chemin de vente, le lot 3 un `SELECT COUNT` de recomptage (C-096). Or `SqliteConnection.getConnection()` ouvre une nouvelle connexion par requête. Personne n'a mesuré la latence ajoutée à `/ah sell` sur la configuration majoritaire.
- **Cluster 1.20 / 1.21 : le marqueur de format ne restaure rien.** Le déploiement en deux phases de C-029 est correct, mais un nœud 1.20.4 reste structurellement incapable de décoder une charge écrite en 1.20.5+ : le marqueur lui permet seulement de la mettre en quarantaine proprement. Conséquence non annoncée : sur un parc hétérogène, une partie du catalogue **disparaît de l'affichage** du nœud ancien au lieu de s'y afficher cassée. Aucune entrée de changelog ne le dit.
- **Interaction V3 × lot 4 non vue.** Le lot 12 (migration V3) est placé en dernier et déclaré parallélisable. Mais C-075/C-033 produisent aujourd'hui des lignes `items` orphelines (sans `auction_items`) : ce sont exactement celles que le lot 4 met en quarantaine ou, avec `selectOrFail`, sur lesquelles il **refuse de démarrer**. Un serveur migré depuis V3 avec le code actuel peut donc ne plus booter après le lot 4, dix releases avant que le lot 12 arrête d'en produire.
- **La population mono-serveur sans addon n'est jamais isolée dans l'analyse de risque.** Elle subit C-110, C-088, C-072, C-045, C-080 sans bénéficier d'aucun des correctifs cluster. C'est probablement la majorité des licences.

## 5. Sous-estimations

- **Lot 1 : « LOW / 195 LOC » est faux.** Il contient une correction sur l'argent (C-013), un changement de **sémantique d'une méthode publiée** (`AuctionManager.removeItem(StorageType,int)` mute désormais l'instance retirée), et un changement de profil TPS (C-045). Aucun de ces trois n'est LOW.
- **Lot 8 : 740 LOC pour 17 constats, deux dépôts, réécriture complète de `lockItem`/`unlockItem`/`checkAvailability`/`removeItem`/`notify*` + démarrage fail-closed + dimensionnement de pool.** Compter 1 000 à 1 200, et c'est la seule release qui change l'exploitation chez tous les clients cluster.
- **Lot 3 : 470 LOC dont une étape 11 HIGH qui rebranche « 8 sites d'écriture » plus `RemovalContext` plus une migration.** Le catalogue qualifie lui-même C-026 de « correctif le plus dangereux de la grappe » (un `expectedFrom` faux fige les items définitivement) — sans un seul test automatisé pour l'attraper.
- **Le coût par release est absent du chiffrage.** Chaque lot exige une passe i18n sur **six** jeux de langue, une passe Docusaurus EN+FR, et une QA manuelle sur Paper × Folia × SQLite × MySQL × cluster. Aucun `estimatedLoc` ne l'inclut. 13 releases coordonnées sur deux dépôts, dont l'addon **n'a aucune CI** (pas de `.github`), c'est le vrai facteur limitant.

## 6. Verdict

Le plan est d'une qualité analytique inhabituelle : la couverture est à 97,5 %, les conflits textuels sont recensés fichier par fichier, la stratégie de compatibilité (tout en `default`, aucune signature publiée touchée, exploitation du fait que `api.*` n'est pas relocalisé pour corriger le binaire addon déployé via `LockToken.of`) est correcte et vérifiée. Le `dangerousAlone` est le meilleur morceau du dossier : il rejette explicitement une dizaine de correctifs d'audit qui auraient introduit des régressions pires que les bugs. Mais trois constats sont mécaniquement perdus, et l'un d'eux — C-070 — est le garde-fou du correctif que le lot 9 active par défaut : livrer C-088 sans lui remplace une expiration paresseuse par une tempête qui verrouille l'hôtel des ventes. C-009 est le second trou coûteux : le `criticalPath` a raison de dire que la garde d'identité ferme le scénario nommé dans le brief, mais il en déduit à tort que la revalidation base devient optionnelle, alors qu'elle est la seule protection du retrait contre une vente distante dont le message n'est pas encore arrivé. L'erreur d'ordonnancement principale est de séparer C-026 de C-005 : le CAS sans l'inversion commit→remise détecte la course perdue **après** que l'item a quitté la base de données pour l'inventaire du joueur, ce qui ne supprime aucune duplication et donne une fausse impression de progrès sur la release la plus risquée du programme. Enfin, le plan promet la sûreté de ~4 700 lignes de changements sur l'argent et l'inventaire sans jamais mentionner qu'il n'existe **aucun test** dans le dépôt, sans protocole de validation, et sans aucune commande de réparation pour les dégâts déjà présents en production. Avant d'ouvrir la première branche : rapatrier C-070 point 3 dans le lot 2 (même patch que C-111, six lignes), placer C-009 et C-024 dans les lots 5 et 8, fusionner lot 3 et lot 5 en une seule release, vérifier explicitement que le volet SQL de C-060 atterrit quelque part, et écrire une poignée de tests sur `ItemRepository`/`ClaimService` avant de toucher au compare-and-set.