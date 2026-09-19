# Audit anti-duplication — zAuctionHouseV4 + addon Redis

> **Portée** : liaison inter-serveurs de l’hôtel des ventes — verrous distribués, bus pub/sub, atomicité de la base, cohérence des caches — face à des joueurs qui agissent simultanément sur plusieurs serveurs pour dupliquer des items.  
> **Révisions auditees** : `zAuctionHouseV4` branche `develop` @ `49571d9` — `zAuctionHouse Redis` @ `d951be2`.  
> **Date** : 5 septembre 2026.  
> **Version en ligne** (catalogue filtrable) : https://claude.ai/code/artifact/7a6e66bc-85bb-4fe7-8750-7b5d7c3e9cce

| | Constats |
|---|---|
| Critiques | **16** |
| Hauts | 45 |
| Moyens | 48 |
| Bas | 10 |
| **Total retenu** | **119** |
| Confirmés par contre-expertise adversariale | 68 |
| Non soumis à la contre-expertise (à traiter comme des pistes) | 47 |

**Comment lire ce document.** La partie 1 est l’analyse : le verdict, les huit causes racines et le plan de durcissement. La partie 2 est le catalogue des 119 constats, chacun avec sa preuve dans le code, la chronologie multi-serveurs qui le déclenche et le correctif à appliquer — sous forme de cases à cocher. La partie 3 indexe les constats par fichier, pour corriger fichier par fichier.

**Limites.** Audit entièrement statique : aucun correctif n’a été appliqué, aucun test exécuté, aucun scénario reproduit sur un serveur réel — tous sont déduits de la lecture du code. Vérifiez qu’une ligne citée n’a pas bougé avant d’appliquer un correctif.

---

## Sommaire

**Partie 1 — Analyse**

1. [Verdict](#1-verdict)
2. [Causes racines](#2-causes-racines)
3. [Plan de durcissement priorisé](#3-plan-de-durcissement-priorisé)
4. [Corrections rapides](#4-corrections-rapides-moins-de-10-lignes-sans-risque-applicables-immédiatement)
5. [Gains de performance gratuits](#5-gains-de-performance-gratuits)
6. [Observabilité](#6-observabilité)

**Partie 2 — Catalogue des constats et correctifs** — 16 critiques, 45 hauts, 48 moyens, 10 bas

**Partie 3 — Index par fichier** — 50 fichiers concernés

---

## Les 16 constats critiques

Duplication d’item ou d’argent réellement atteignable, ou perte de données. À traiter avant tout le reste.

| | Constat | Fichier |
|---|---|---|
| [`C-001`](#c-001) | Au-dela d'environ 65 000 items, le IN(...) de AuctionItemRepository.select echoue, l'echec est avale par Repository.select et TOUS les items du reseau sont charges sans aucun contenu | `storage/repository/repositories/AuctionItemRepository.java:41` |
| [`C-002`](#c-002) | Côté Redis, SOLD et REMOVED sont considérés disponibles et DELETED s'auto-détruit au bout de 24 h : le verrou ne protège que la simultanéité, jamais la séquence | `REDIS/RedisAuctionClusterBridge.java:163` |
| [`C-003`](#c-003) | Double claim inter-serveurs : ClaimService.claimMoney depose l'argent AVANT un UPDATE non conditionnel dont le resultat est jete | `services/ClaimService.java:71` |
| [`C-004`](#c-004) | ItemStatusListener applique le statut recu sans comparer oldStatus : un item deja PURCHASED redevient IS_PURCHASE_CONFIRM et n'est plus jamais reclamable | `REDIS/listener/listeners/ItemStatusListener.java:54` |
| [`C-005`](#c-005) | L'argent et l'item sont livrés avant que l'écriture DB soit durable : un échec ou un crash duplique l'item et paie deux fois | `ZAuctionManager.java:883` |
| [`C-006`](#c-006) | La revalidation d'achat sous verrou ne teste pas storage_type : un item déjà passé en EXPIRED reste achetable | `services/PurchaseService.java:121` |
| [`C-007`](#c-007) | Le bail Redis de 30 s n'est jamais renouvelé et la section critique n'a aucun timeout : le verrou expire en plein commit | `services/PurchaseService.java:134` |
| [`C-008`](#c-008) | Le jeton de verrou est déterministe ("item:<id>") : la vérification de propriété est inopérante et un serveur libère le verrou d'un autre | `API/api/cluster/LockToken.java:34` |
| [`C-009`](#c-009) | RemoveService ne revalide JAMAIS la base sous verrou, contrairement à PurchaseService : tout fantôme mémoire devient un item gratuit | `services/RemoveService.java:200` |
| [`C-010`](#c-010) | SQLITE reste accepté avec un bridge cluster distribué : chaque serveur a sa propre base, les ids se recoupent et toute la protection anti-duplication opère sur des objets différents | `storage/ZStorageManager.java:49` |
| [`C-011`](#c-011) | Un item dont la liste d'ItemStacks est vide reste LISTED, s'affiche comme un lot normal et reste achetable : l'acheteur est debite du prix plein et ne recoit RIEN | `items/ZAuctionItem.java:68` |
| [`C-012`](#c-012) | adminRemoveItem diffuse une suppression non terminale (REMOVED, destination null) AVANT d'ecrire en base : les autres serveurs remettent l'item en vente | `ZAuctionManager.java:689` |
| [`C-013`](#c-013) | claimMoney marque toutes les transactions RETRIEVED meme quand aucun depot n'a eu lieu | `services/ClaimService.java:78` |
| [`C-014`](#c-014) | giveItem() ecrit dans l'inventaire du joueur et fait spawner des entites depuis ForkJoinPool.commonPool | `ZAuctionManager.java:931` |
| [`C-015`](#c-015) | withdraw() ne rend aucun resultat et n'est jamais verifie : entre le has() et le debit, l'acheteur peut obtenir l'item sans payer et le vendeur est credite a partir de rien | `economy/ZAuctionEconomy.java:92` |
| [`C-016`](#c-016) | L'echec de loadServerUUID() n'interrompt pas onEnable : le noeud installe le bridge Redis avec INSTANCE_UUID null et devient sourd et muet sur le bus | `REDIS/ZAuctionHouseRedis.java:59` |

---

# Partie 1 — Analyse

## 1. Verdict

Non. Le système **ne prévient pas** la duplication : il la rend improbable dans le cas nominal mono-serveur, et laisse trois classes de trous ouverts en cluster. Une seule des trois chaînes mutantes (achat) revalide la base sous verrou ; le chemin de **retrait** — celui qui rend physiquement l'objet — n'a ni revalidation, ni compare-and-set, ni verrou identifiable (`RemoveService.java:200`, `grep selectItem` = 0). Toute la sûreté repose donc sur Redis, alors que Redis est ici un bus best-effort sans rejeu, un verrou sans identité (`LockToken.of()` = `"item:"+id`), un bail de 30 s jamais renouvelé et des états `SOLD`/`REMOVED` déclarés « disponibles » (`RedisAuctionClusterBridge.java:163-175`). La base, désignée comme source de vérité, n'arbitre rien : `UPDATE ... WHERE id=?` aveugle et rowcount systématiquement jeté (`ItemRepository.java:71-92`, `Repository.java:242-245`).

**Risque résiduel dominant** : ce n'est pas la course micro-seconde entre deux joueurs, c'est la **divergence durable**. Un message pub/sub perdu, un redémarrage, un `UPDATE` échoué, un clic admin — et un fantôme mémoire s'installe *définitivement* (aucune tâche de réconciliation n'existe) puis devient un item gratuit au premier clic. S'ajoute un risque **mono-serveur** : argent et objet sont livrés avant que l'écriture soit durable (`ZAuctionManager.java:883-884`).

**Scénario le plus facile pour un joueur ordinaire** : ouvrir `/ah selling` sur le serveur B, laisser l'inventaire ouvert ; se faire acheter/expirer un item pendant ce temps ; recliquer le slot toujours affiché. `RemoveService` ne relit jamais la base, `checkAvailability` accepte `SOLD`/`REMOVED`, le verrou est accordé, `giveItem` livre une seconde copie. Aucun outil, aucun timing serré, aucune permission.

---

## 2. Causes racines

### C1 — La base ne peut arbitrer aucune course : pas de compare-and-set, rowcount jeté

**Mécanisme.** `ItemRepository.createUpdateSchema` (`ItemRepository.java:71-92`) ne pose une garde de transition que pour la cible `EXPIRED` (`where storage_type = LISTED`) ; `PURCHASED`, `DELETED` et toutes les autres s'écrivent en `WHERE id = ?` nu. Et même la garde existante est inobservable : `Repository.update(Consumer)` et `update(List<Schema>)` (`Repository.java:242-245`) renvoient `void` et jettent le nombre de lignes affectées, que Sarah remonte pourtant (`UpdateRequest` → `executeUpdate()`). Le même trou existe côté monnaie : `TransactionRepository.updateStatus` (`:46-57`) écrit `status='RETRIEVED'` sans `where status='PENDING'`, donc deux serveurs gagnent la même course.

**Explique** : écrasement d'une vente par un retrait (#1 tour 1) ; `adminRemoveItem` qui écrase une ligne `PURCHASED` (B: `ZAuctionManager.java:693`) ; expiration cluster qui prend « 0 ligne modifiée » pour un succès et déplace l'item en mémoire avec un `expiredAt` jamais persisté (B: `ExpireService.java:355-364`) ; double claim inter-serveurs (B: `ClaimService.java:71/84`) ; `clearPendingTransactions` (B: `ClaimService.java:210`) ; transition `EXPIRED/PURCHASED → DELETED` sans aucune garde (B: `ExpireService.java:102-106`, `:227-236`).

**Principe.** *Chaque transition d'état est un compare-and-set dont le rowcount pilote la suite.* `rows != 1` n'est pas un warning : c'est un abandon — pas de `giveItem`, pas de mouvement d'argent, purge du fantôme local.

---

### C2 — L'ordre est inversé et rien n'est atomique : on livre avant de committer

**Mécanisme.** Dans `purchaseAuctionItem`, l'argent bouge ~80 lignes avant toute mutation d'item (`ZAuctionManager.java:802` withdraw, `:823` deposit), puis `updateItem(...)` est **soumis** et `giveItem(...)` s'exécute aussitôt (`:883-884`), le future n'étant jamais joint (retourné tel quel `:918`). Le couple non séquencé existe à **six** sites (`522-523`, `568-569`, `602-603`, `636-637`, `693-696`, `883-884`). Aucune transaction SQL n'existe dans tout le projet : `DatabaseConnection.beginTransaction()` de Sarah n'est appelé **nulle part**. La création d'une vente est un `1 + N` INSERT en autocommit sur N connexions distinctes (`ZStorageManager.java:140-145`, `AuctionItemRepository.java:30-38`), après que `SellService.java:105` a déjà vidé l'inventaire du joueur.

**Explique** : dupe sur incident MySQL transitoire et au redémarrage même en mono-serveur (#2 tour 1) ; dupe sur `onDisable` (#13) ; ligne `items` orpheline `LISTED` sans contenu, achetable et non livrable (B: `ZStorageManager.java:141`, `ZAuctionItem.java:68-82`) ; `SellService.exceptionally` qui rembourse alors que `postSell` a déjà commité et diffusé (B: `SellService.java:111-130`) ; items retirés de l'inventaire avant l'INSERT, sans `saveData` (B: `SellService.java:105`).

**Principe.** *Commit d'abord, argent ensuite, remise physique en dernier et chaînée.* Toute compensation est explicite (rollback DB + rollback mémoire), jamais implicite. Une annonce ne devient visible qu'une fois son contenu écrit.

---

### C3 — Le verrou distribué n'a ni identité, ni bail vivant, ni états terminaux

**(a) Pas d'identité.** `LockToken.of()` (`API/.../cluster/LockToken.java:34-36`) renvoie `"item:" + id` : valeur identique sur tous les nœuds, et `lockerId` n'est jamais lu (`RedisAuctionClusterBridge.java:181-187`). Le contrôle de propriété de `UNLOCK_SCRIPT` (`:79-82`) est donc vrai pour tout le monde : un unlock retardataire détruit le verrou d'un autre nœud, et `unlockItem` **ne renvoie même pas** le code retour du script (`:257`, contrat `AuctionClusterBridge.java:45` = `CompletableFuture<Void>`) — la perte de verrou est produite par Redis puis immédiatement jetée.

**(b) Pas de bail vivant, pas de fencing.** TTL posé une fois (`:188`, 30 s), aucun `PEXPIRE`, aucun `isHeldBy`. Pire, `LOCK_SCRIPT:55` **reprend délibérément** un verrou dont la clé a expiré (`if currentState == 'LOCKED' and EXISTS(lockKey) == 1 then return 0`) : un détenteur lent (GC, `getPlayerName` JDBC synchrone `ZAuctionManager.java:784`) est indiscernable d'un détenteur mort, et rien ne l'empêche d'écrire après expiration.

**(c) Pas d'états terminaux.** `checkAvailability:163-175` ne refuse que `DELETED` ; `SOLD` et `REMOVED` sont commentés « settled states » et retournent `true`. `LOCK_SCRIPT:47` idem. Et `DELETED` lui-même porte un `EXPIRE` de 24 h (`item-state-ttl-seconds`) : le seul état bloquant s'auto-détruit. Le repli non atomique (`:220-238`, activé dès qu'un `SCRIPT LOAD` échoue) peut en plus **écraser** un `DELETED` par `LOCKED` puis `AVAILABLE`.

**Explique** : #4, #6, #7, #10, #20, #21, #22 (tour 1) ; B: fencing absent, repli non atomique, `unlockItem` muet, `adminRemoveItem` sans test `noop`.

**Principe.** *Le verrou est une optimisation de contention, jamais la garantie.* Token = `item:<id>:<serverUuid>:<nonce>` ; fence monotone (`HINCRBY`) porté jusqu'au `WHERE fence < ?` ; états terminaux réellement terminaux et sans TTL.

---

### C4 — La vérité est en mémoire locale et le bus est « au plus une fois »

**Mécanisme.** `ItemStatus` n'a **aucune colonne** en base (`ItemDTO` ne porte que `storage_type` / `buyer_unique_id`) ; il est redérivé au boot (`ItemLoaderUtils.java:43-48`). Or les quatre gardes de `RemoveService` (`:55`, `:96`, `:134`, `:172`) ne testent que ce champ mémoire. En face, le transport n'offre aucune reprise : `ZAuctionHouseRedis.sendMessage:274-282` avale l'échec de publication et complète le future **normalement** ; `RedisSubscriberRunnable:62-97` se reconnecte avec un backoff jusqu'à 60 s **sans rejeu** ; le contrat `AuctionClusterBridge` n'a aucun point de reprise ; aucune tâche périodique ne resynchronise mémoire↔base (`grep runTimer` → `BossBarAnimation`, `Metrics`).

Pire, les listeners eux-mêmes fabriquent des divergences : `ItemStatusListener.java:54` applique `newStatus` sans jamais comparer `oldStatus` (un item `PURCHASED` redevient `IS_PURCHASE_CONFIRM` et n'est plus jamais réclamable) ; `ItemListedListener.java:31-48` réinjecte en `LISTED` le résultat d'un `selectItem` en vol, sans vérifier le statut ; `ItemRemovedListener.java:64-89` re-ajoute sur une destination figée à l'émission ; `ItemBoughtListener`/`ItemRemovedListener` n'appellent jamais `updateListedItems`, donc les slots restent cliquables. Enfin les statuts transitoires (`IS_*_CONFIRM`) n'ont **ni propriétaire ni TTL** : une déconnexion pendant une confirmation gèle l'item sur tout le cluster (`ConfirmHelper.java:37-38` dépend de `ITEM_SHOW`, détruit par `PlayerListener.java:43`).

**Explique** : #3, #9, #17, #19 (tour 1) ; B: bus au plus une fois, ItemStatusListener sans CAS, listeners qui réinjectent, statuts gelés, `/ah admin cache clear`, `loadItems()` purement additif, `SortedItemsCache` jamais invalidé par un changement de statut.

**Principe.** *Toute décision engageante se prend sur une relecture de la base sous verrou, jamais sur un champ mémoire.* Le bus est un accélérateur de convergence, pas une source de vérité : il lui faut un numéro de séquence, une détection de trou et une réconciliation.

---

### C5 — La couche monétaire est aveugle : aucun mouvement d'argent n'est vérifiable ni réservable

**Mécanisme.** `AuctionEconomy.deposit/withdraw` (`API/.../economy/AuctionEconomy.java:94`, `:103`) sont déclarés `void` — le trou est dans le **contrat**, pas dans l'implémentation. `ZAuctionEconomy.java:86-94` délègue à `CurrencyProvider` (également `void`), et `VaultProvider.withdraw` jette l'`EconomyResponse` : un solde insuffisant renvoie `FAILURE` **sans exception**, donc le `try/catch` de `ZAuctionManager.java:801-806` est structurellement mort. `has()` (`:81-84`) n'est qu'une lecture non réservante, et `get()` (`:77-79`) est un faux async (`completedFuture(provider.getBalance(...))`) : une exception du provider s'échappe **synchroniquement** de `purchaseAuctionItem` après le débit acheteur, sautant `setBuyer`/`removeItem`/`updateItem`/`giveItem`.

S'ajoutent trois défauts indépendants : la taxe est calculée **deux fois avec des arguments différents** — `PurchaseService.java:49` passe `itemStack = null` (donc ignore les règles item) tandis que `ZAuctionManager.java:744` passe l'item réel ; `TaxResult` ne transporte pas le type réellement appliqué, donc `ZAuctionManager.java:756` réinterprète un montant `CAPITALISM` sous la sémantique de l'économie et **crée de la monnaie** ; `ClaimService` dépose avant de marquer, sans CAS ni verrou (seul flux d'argent sans `clusterBridge`) ; la dette `PENDING` — unique représentation de l'argent du vendeur en cluster — est écrite en fire-and-forget sur un scheduler **non drainé** par `onDisable` (`ZStorageManager.java:170-173` → `async()` → `plugin.getScheduler()`, alors que seul `asyncExecutor` est drainé `ZAuctionPlugin.java:196-209`).

**Explique** : #11, #16 (tour 1) ; B: withdraw non vérifié, `get()` faux async, double claim, mint CAPITALISM, `requiredBalance` sans ItemStack, dette PENDING perdue, providers LEVEL/EXPERIENCE/ITEM/ZMENUITEMS qui détruisent silencieusement le paiement d'un vendeur hors ligne, taxe de vente `has`→`withdraw` non atomique.

**Principe.** *Un mouvement d'argent doit renvoyer un booléen, et un droit à percevoir doit être réservé avant d'être payé.* Le montant dû se calcule **une fois**, avec l'item réel, et se transmet ; il ne se recalcule jamais.

---

### C6 — Les erreurs sont avalées à tous les étages : un échec se présente comme un succès

**Mécanisme.** `Repository.select(Class, Consumer)` (`Repository.java:172-181`) attrape `Exception` et renvoie **une liste vide**. `Repository.insert` (`:124-130`) et `insertSync` (`:135-141`) n'attrapent que `SQLException`, alors que Sarah lève une `DatabaseException extends SarahException extends RuntimeException` (`InsertRequest.java:72-75`) : ces `catch` sont du **code mort** et le `return -1` est inatteignable. Conséquence directe et spectaculaire : le `IN(?,?,…)` non paginé de `AuctionItemRepository.java:40-42` casse au-delà du plafond de paramètres du pilote (~32 766 SQLite, 65 535 MySQL) et l'échec devient une liste vide — **tous** les items du réseau se chargent avec un contenu vide, s'affichent comme des lots normaux (`ZAuctionItem.java:81`), se vendent et ne livrent rien.

Même schéma partout : `sendMessage` avale l'échec de publication ; `V3MigrationProvider.java:126-132` mappe inconditionnellement vers `MigrationResult.success` et rend la branche d'erreur de l'appelant morte ; `loadScripts` échoue une fois et `reloadScriptsIfNeeded` **n'a aucun appelant** (`RedisAuctionClusterBridge.java:132`) ; `Base64ItemStack.encode` peut rendre `null` (contrat javadoc) ou NPE (`ItemStackUtils.java:37` déréférence une variable restée nulle) et cette valeur part dans une colonne `NOT NULL`.

**Explique** : B: falaise du `IN()`, items à contenu vide, migration V3 « réussie », scripts Lua jamais rechargés, `printStackTrace()` invisible, `exceptionally` incapables d'attraper un throw synchrone.

**Principe.** *Un échec d'IO doit échouer.* Une liste vide et un `-1` sont des valeurs métier légitimes : elles ne doivent jamais signifier « erreur ».

---

### C7 — Aucun confinement de threads : logique Bukkit et IO bloquant partagent `ForkJoinPool.commonPool`

**Mécanisme.** `ZStorageManager.selectItem:176-177` et `findUniqueId:215` sont les **deux seuls** `supplyAsync` du fichier sans `Executor` (les quatre autres passent `getExecutorService()`), et les sept méthodes du bridge Redis n'en passent aucune. Comme `PurchaseService` chaîne en `thenCompose` non-`Async`, toute la chaîne d'achat/retrait tourne sur `commonPool` — **y compris en mono-serveur**. Au bout : `giveItem` (`ZAuctionManager.java:926-935`) écrit dans l'inventaire et fait spawner des entités hors tick, avec `folia-supported: true`. Idem `SellService.java:120` (remboursement), `ListedItemsButton.java:229-234` et `:126-135` (ouverture d'inventaire depuis `thenRun`, sans `exceptionally`). `ZPlayerCache` est un `EnumMap` nu (`:14`) muté depuis ces threads alors que son contrat d'API promet la thread-safety, et `getOrCompute` (`:52-60`) est un test-puis-écriture non atomique. Aucune sérialisation par compte n'existe (`grep synchronized` = 0).

**Effet perf.** `commonPool` = `cores-1` workers (3 sur 4 cœurs) saturés par de l'IO Jedis/JDBC bloquant, pendant que `pool.max-total: 64` reste inatteignable et que `maxWait` n'est **jamais configuré** (`RedisConnectionFactory.java:66-71` → attente infinie), avec un double emprunt imbriqué dans la branche NOSCRIPT. Les `orTimeout` se déclenchent alors **au milieu** des sections critiques, verrou détenu.

**Explique** : #8, #14, #15 (tour 1) ; B: pool Jedis sans `maxWait`, `ZPlayerCache` non concurrent, ouvertures d'inventaire hors thread, expiration cluster non bornée, tempêtes de rebuild.

**Principe.** *Un pool dédié par nature d'IO ; toute mutation Bukkit passe par un hop scheduler et est chaînée dans le future.*

---

### C8 — Compensations non conditionnelles, cycle de vie et déploiement fail-open

**Mécanisme.** Le motif est constant : la compensation agit sur l'ensemble *présumé*. `ClaimService.java:78` incrémente `totalClaimed` **hors** du `if (player.isOnline())` puis marque tout `RETRIEVED` ; `RemoveService.java:250` pose `localRemovalCompleted` *dans* le `thenCompose`, donc un `giveItem` qui lève ressuscite l'item sur tout le cluster ; `PurchaseService.java:176` teste `IS_BEING_PURCHASED` sans savoir si **cette** chaîne l'a posé, et rediffuse un statut d'UI après avoir constaté que l'item est vendu ; `RemoveService.java:287` transforme un incident *post*-remise en `INTERNAL_ERROR`, ce qui abandonne silencieusement le reste d'un lot « tout récupérer » et sous-compte les items rendus.

Côté déploiement, tout est fail-open : `storage-type: SQLITE` + addon Redis est **accepté sans un mot** (`ZAuctionPlugin.java:419-422`), chaque nœud ayant sa base locale et des ids qui se recoupent ; l'addon s'installe sur un plugin principal désactivé (`ZAuctionHouseRedis.java:62`, jamais `isEnabled()`) ; le thread abonné démarre **avant** l'enregistrement des listeners (`:83-85` vs `:101-104`) et les messages de cette fenêtre sont jetés sans log ; l'unicité de `INSTANCE_UUID` est best-effort (exception avalée, heartbeat en simple `EXPIRE` sur une clé qui peut avoir disparu) ; l'API est consommée via un **SHA git figé** (`build.gradle.kts:48`) sans aucun contrôle de compatibilité ; `onDisable` de l'addon laisse un bridge zombie, et `onDisable` du plugin sort avant de fermer la base quand `onEnable` a échoué.

**Explique** : #12, #17, #18, #19, #22 (tour 1) ; B: SQLITE+cluster, addon sur plugin désactivé, subscriber avant listeners, UUID collisions, contrat API non versionné, retrait de masse interrompu, `updateListedItems` `return` au lieu de `continue`.

**Principe.** *Ne jamais marquer, restaurer ou libérer plus que ce qui a effectivement eu lieu.* Et : quand le cluster est requis, refuser de démarrer plutôt que de vendre sans verrou partagé.

---

## 3. Plan de durcissement priorisé

> Ordre = (impact anti-duplication) / (risque de régression). Les chantiers 1→4 sont non négociables et à livrer ensemble ; chacun seul laisse un trou exploitable.

### Chantier 1 — Compare-and-set universel + propagation du rowcount  P0 · risque faible · **perf positive**

**Fichiers** : `API/.../api/storage/Repository.java`, `storage/repository/repositories/ItemRepository.java`, `TransactionRepository.java`, `storage/ZStorageManager.java`, puis les 8 appelants.

```java
// Repository.java — à côté de update(Consumer<Schema>)
protected int updateReturning(Consumer<Schema> consumer) {
    // DatabaseException (RuntimeException) remonte volontairement : le future DOIT échouer.
    return SchemaBuilder.update(getTableName(), consumer).execute(this.connection, this.logger);
}
```
```java
// ItemRepository.java — remplace updateItem(Item, StorageType)
public int updateItem(Item item, StorageType from, StorageType to) {
    return updateReturning(schema -> {
        schema.where("id", item.getId());
        schema.where("storage_type", from.name());               // garde universelle
        if (to == StorageType.PURCHASED) schema.whereNull("buyer_unique_id");
        schema.string("storage_type", to.name());
        if (to != StorageType.DELETED) schema.object("expired_at", item.getExpiredAt());
        if ((to == StorageType.PURCHASED || to == StorageType.DELETED) && item.getBuyerUniqueId() != null)
            schema.uuid("buyer_unique_id", item.getBuyerUniqueId());
    });
}
```
```java
// ZStorageManager.java:156 — la perte de course devient un échec observable
public CompletableFuture<Void> updateItem(Item item, StorageType from, StorageType to) {
    return CompletableFuture.runAsync(() -> {
        int rows = with(ItemRepository.class).updateItem(item, from, to);
        if (rows != 1) throw new StaleItemException(item.getId(), from, to, rows);
    }, this.plugin.getExecutorService());
}
```
Même traitement pour l'argent — `TransactionRepository.java:46-57` :
```java
schema.where("id", transactionId);
schema.where("status", TransactionStatus.PENDING.name());   // CAS : le perdant matche 0 ligne
```
Chaque appelant traite `StaleItemException` comme « j'ai perdu la course » : **aucun `giveItem`, aucun mouvement d'argent**, purge du fantôme (`auctionManager.removeItem(from, id)` + `clearPlayersCache`), message `ITEM_NOT_AVAILABLE`. `whereNull` et le retour `int` d'`execute()` existent déjà dans Sarah : aucune modification de la lib.

**Perf : positive.** Le CAS rend redondante la revalidation `selectItem` de `PurchaseService.java:118-125`, qui coûte **3 requêtes** (`ZStorageManager.java:176-207`) : −3 allers-retours DB par achat, verrou détenu d'autant moins longtemps. La version sûre est *plus rapide*.

**Test** : deux serveurs, même MySQL, `SLEEP` injecté entre lock et update d'un nœud, deux achats du même item → exactement un `rows == 1`. Reproduire avant correction : `UPDATE items SET storage_type='PURCHASED' WHERE id=42` à la main pendant qu'un retrait est en vol sur B → aujourd'hui le retrait écrase et livre.

---

### Chantier 2 — Inverser l'ordre : commit → argent → remise, et rendre la vente atomique  P0 · risque moyen · perf neutre

**Fichiers** : `ZAuctionManager.java` (6 sites : `522-523`, `568-569`, `602-603`, `636-637`, `693-696`, `883-884`), `SellService.java:105-130`, `ZStorageManager.java:140-145`.

```java
// giveItem devient chaînable et thread-safe
public CompletableFuture<Integer> giveItem(Player player, Item item) {
    var future = new CompletableFuture<Integer>();
    this.plugin.getScheduler().runAtEntity(player, w -> {
        try {
            if (!(item instanceof AuctionItem ai)) { future.complete(0); return; }
            int given = 0;
            for (ItemStack is : ai.getItemStacks()) {
                if (is == null) { this.plugin.getLogger().severe("null stack on item " + item.getId()); continue; }
                player.getInventory().addItem(is.clone())
                      .forEach((s, drop) -> player.getWorld().dropItem(player.getLocation(), drop));
                given++;
            }
            future.complete(given);
        } catch (Throwable t) { future.completeExceptionally(t); }
    });
    return future;
}
```
```java
// purchaseAuctionItem — nouvelle séquence
return storageManager.updateItem(auctionItem, StorageType.LISTED, target)     // 1. CAS durable
    .thenCompose(v -> {
        if (!auctionEconomy.withdraw(player.getUniqueId(), buyerPays, reason)) // 2. argent VÉRIFIÉ (ch. 3)
            return compensateResale(auctionItem);                              //    rollback DB
        depositOrDefer(seller, sellerReceives);
        applyMemoryMutations(auctionItem);
        return purchasedConfiguration.giveItem()                               // 3. remise chaînée
             ? giveItem(player, auctionItem).thenApply(n -> null)
             : CompletableFuture.completedFuture(null);
    });
```
`compensateResale` = `UPDATE items SET storage_type='LISTED', buyer_unique_id=NULL WHERE id=? AND storage_type=?`.

**Atomicité de la vente** — sans toucher à Sarah, publier après le contenu :
```java
// ZStorageManager.createAuctionItem
int itemId = with(ItemRepository.class).create(seller, AUCTION, price, expiredAt, eco, /*storage*/ DELETED);
with(AuctionItemRepository.class).create(...);                       // N contenus
int rows = with(ItemRepository.class).publish(itemId);               // UPDATE ... SET storage_type='LISTED'
                                                                     //   WHERE id=? AND storage_type='DELETED'
if (rows != 1) throw new IllegalStateException("listing not published " + itemId);
```
Corriger dans la foulée `RemoveService.java:250` (poser le flag **avant** l'invocation du supplier) et envelopper le bloc `exceptionally` de `SellService.java:111-130` dans `runAtEntity` — en ne remboursant **jamais** après commit (séparer `whenComplete` sur l'INSERT et `try/catch` autour de `postSell`).

**Perf : neutre.** L'`UPDATE` était déjà asynchrone, on l'attend au lieu de l'oublier (+1 aller-retour, ~1-5 ms LAN), largement compensé par le chantier 1.
**Test** : `kill -9` entre CAS et `giveItem` → au redémarrage, ligne `PURCHASED` avec `buyer` renseigné, jamais `LISTED`. Débrancher MySQL au moment du CAS → aucun débit, aucune remise.

---

### Chantier 3 — Rendre la couche monétaire vérifiable  P0 · risque faible · perf neutre

**Fichiers** : `API/.../api/economy/AuctionEconomy.java`, `economy/ZAuctionEconomy.java`, `ZAuctionManager.java:731-780`, `PurchaseService.java:45-53`, `API/.../api/tax/TaxResult.java`.

```java
// AuctionEconomy — le contrat doit pouvoir dire NON (garder les void @Deprecated pour les addons)
boolean withdraw(UUID playerId, BigDecimal value, String reason);
boolean deposit(UUID playerId, BigDecimal value, String reason);
default boolean supportsOfflineDeposit() { return true; }
```
```java
// ZAuctionEconomy — tant que CurrencyProvider est void, vérifier par delta de solde
@Override public boolean withdraw(UUID id, BigDecimal v, String reason) {
    BigDecimal before = this.currencyProvider.getBalance(id);
    if (before.compareTo(v) < 0) return false;
    this.currencyProvider.withdraw(id, v, reason);
    return before.subtract(this.currencyProvider.getBalance(id)).compareTo(v) >= 0;
}
@Override public CompletableFuture<BigDecimal> get(UUID id) {          // plus de faux async
    return CompletableFuture.supplyAsync(() -> this.currencyProvider.getBalance(id),
                                         this.plugin.getExecutorService());
}
@Override public boolean supportsOfflineDeposit() {                    // LEVEL/EXP/ITEM = no-op offline
    return !(currencyProvider instanceof ItemProvider)
        && !(currencyProvider instanceof LevelProvider)
        && !(currencyProvider instanceof ExperienceProvider);
}
```
**Un seul calcul de taxe.** Ajouter `TaxType appliedType` au record `TaxResult`, exposer `buyerPays()` / `sellerReceives()` dérivés de `appliedType`, et faire calculer le montant **une fois** avec l'ItemStack représentatif dans `PurchaseService`, puis le passer à `purchaseItem` — `ZAuctionManager.java:755-780` ne rebranche plus jamais sur `taxConfig.getTaxType()`. Garde-fou avant le débit : `if (sellerReceives.compareTo(buyerPays) > 0) throw new IllegalStateException("tax config would mint money");`.

**Diffusion différée** : `ZAuctionManager.java:812` devient
`|| (!sellerOnThisServer && !auctionEconomy.supportsOfflineDeposit())`.

**Test** : provider Vault avec solde exactement égal, `/pay` depuis un second serveur pendant la fenêtre → l'achat doit échouer et rien ne doit être livré. Config `type: PURCHASE` + règle item `CAPITALISM 15%` → `buyerPays == sellerReceives + taxe`, jamais l'inverse.

---

### Chantier 4 — Identité, fencing et états terminaux du verrou  P0/P1 · risque faible · perf neutre

**Fichiers** : `API/.../cluster/LockToken.java`, `RedisAuctionClusterBridge.java`, `cluster/LocalAuctionClusterBridge.java`.

```java
public static LockToken issue(Item item, UUID ownerId) {   // remplace of(Item), devinable
    return new LockToken("item:" + item.getId() + ":" + ownerId + ":" + UUID.randomUUID());
}
```
```lua
-- LOCK_SCRIPT : fence monotone + états réellement terminaux
local st = redis.call('HGET', itemKey, 'state')
if st == 'DELETED' or st == 'SOLD' or st == 'REMOVED' then return -1 end
if st == 'LOCKED' and redis.call('EXISTS', lockKey) == 1 then return 0 end
if not redis.call('SET', lockKey, tokenValue, 'NX', 'PX', ttlMs) then return 0 end
local fence = redis.call('HINCRBY', itemKey, 'fence', 1)
redis.call('HSET', itemKey, 'state', 'LOCKED'); redis.call('HSET', itemKey, 'lock', tokenValue)
return fence
```
`-1` → `ITEM_NOT_AVAILABLE`, `0` → `LOCK_FAILED`, `> 0` → acquis avec fence : **`checkAvailability` devient inutile avant `lockItem`** (−1 emprunt Jedis et −1 RTT sur les 4 chemins chauds ; le garder comme méthode d'affichage documentée non engageante). `unlockItem` doit renvoyer `CompletableFuture<Boolean>` (le script rend déjà 0/1, `:257` le jette) et journaliser en `SEVERE` tout `false` : c'est le signal « verrou perdu ».

**Corollaire obligatoire dans le même commit** — sinon plus aucun item expiré n'est réclamable :
```java
// removeItem:354 — une destination encore réclamable ne doit pas écrire REMOVED
String state = destination == StorageType.DELETED ? STATE_DELETED
             : destination == StorageType.EXPIRED ? STATE_AVAILABLE : STATE_REMOVED;
jedis.hset(itemKey, FIELD_STATE, state);
if (STATE_DELETED.equals(state)) jedis.persist(itemKey);   // un terminal n'expire jamais
else if (itemStateTtl != null) jedis.expire(itemKey, itemStateTtl.toSeconds());
```
Supprimer le repli non atomique (`:220-238`) : sans script, refuser le verrou (`LockToken.noop()`) plutôt que de verrouiller à tort — et appeler enfin `reloadScriptsIfNeeded()` (`:132`, aujourd'hui **sans appelant**) en tête de `lockItem`/`unlockItem`, avec un limiteur de 5 s.

**Test** : TTL abaissé à 2 s, pause de 5 s injectée dans la section critique de A → l'unlock de A rend `false`, le verrou de B reste intact (`TTL auction:lock:<id>`). Puis `DEL auction:item:<id>` sur un item vendu : le CAS du chantier 1 doit encore refuser toute reprise — c'est le test qui prouve que Redis n'est plus l'unique rempart.

---

### Chantier 5 — Revalidation autoritaire sous verrou sur le chemin de retrait P1 · risque faible

**Fichier** : `RemoveService.java`, étape insérée entre `changeStatusAndNotifyStep` et `executeLocalRemovalStep` ; `ZAuctionManager.adminRemoveItem:679-703`.

```java
private CompletableFuture<Void> revalidateUnderLockStep(RemovalContext ctx, PerformanceConfiguration cfg) {
    return this.plugin.getStorageManager().selectItem(ctx.item.getId())
        .orTimeout(cfg.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
        .thenCompose(dbItem -> {
            ItemStatus expected = switch (ctx.storageType) {         // cf. ItemLoaderUtils:43-48
                case LISTED -> ItemStatus.AVAILABLE; case EXPIRED -> ItemStatus.REMOVED;
                case PURCHASED -> ItemStatus.PURCHASED; default -> null; };
            boolean stale = dbItem == null || dbItem.getStatus() != expected
                || (ctx.storageType == StorageType.LISTED && dbItem.getBuyerUniqueId() != null)
                || (dbItem instanceof AuctionItem ai && ai.getItemStacks().isEmpty());
            if (stale) {
                this.plugin.getAuctionManager().removeItem(ctx.storageType, ctx.item.getId());
                this.plugin.getAuctionManager().clearPlayersCache(PlayerCacheKey.values());
                ctx.onUnavailable.run();
                ctx.result = RemoveResult.failure("Already processed elsewhere", RemoveFailReason.ITEM_NOT_AVAILABLE);
                return failedFuture(new IllegalStateException("stale item " + ctx.item.getId()));
            }
            ctx.item.setExpiredAt(dbItem.getExpiredAt());
            return CompletableFuture.<Void>completedFuture(null);
        });
}
```
Ajouter une **garde d'identité** en tête de `executeRemoval` (couvre les références capturées par un bouton déjà rendu, indépendamment du chemin) :
`if (auctionManager.getItem(storageType, item.getId()) != item) return failure(ITEM_NOT_AVAILABLE);`
— avec un accesseur `O(1)` `getItem(StorageType, int)` à ajouter à `AuctionManager` (la map `storageItemsById` est déjà indexée par id).

`adminRemoveItem` doit être réécrit sur le même patron : tester `LockToken.noop()`, **écrire d'abord** (`updateItem(LISTED → DELETED)` en CAS), diffuser ensuite avec la surcharge **3 arguments** et une destination terminale, livrer en dernier, relâcher le verrou dans un `whenComplete`.

**Perf** : légèrement négative sur les retraits unitaires (+1 `selectItem`). **Désactiver** cette étape dans les retraits de masse (« tout récupérer ») où le CAS du chantier 1 suffit et où le coût serait ×N.
**Test** : vendre sur A, couper le subscriber de B, retirer sur B → `ITEM_NOT_AVAILABLE` et disparition du fantôme.

---

### Chantier 6 — Cesser d'avaler les erreurs, et fail-closed au démarrage P1 · risque faible

**Fichiers** : `Repository.java`, `AuctionItemRepository.java`, `ZAuctionPlugin.java`, `ZAuctionHouseRedis.java`, `V3MigrationProvider.java`.

```java
// AuctionItemRepository.java:40 — paginer le IN (plafond pilote : 32 766 SQLite / 65 535 MySQL)
public List<AuctionItemDTO> select(List<String> ids) {
    if (ids.isEmpty()) return List.of();
    List<AuctionItemDTO> out = new ArrayList<>(ids.size());
    for (int i = 0; i < ids.size(); i += 500) {
        List<String> chunk = ids.subList(i, Math.min(i + 500, ids.size()));
        out.addAll(select(AuctionItemDTO.class, s -> s.whereIn("item_id", chunk)));
    }
    return out;
}
```
```java
// Repository.java:172 — un échec d'IO doit échouer, pas rendre une liste vide
} catch (Exception e) {
    this.logger.severe("select failed on " + getTableName() + ": " + e.getMessage());
    throw new IllegalStateException("select failed on " + getTableName(), e);
}
```
```java
// ZAuctionPlugin.setAuctionClusterBridge — SQLITE + cluster = configuration impossible
if (bridge != null && bridge.isDistributed()
        && storageManager.getDatabaseConnection().getDatabaseConfiguration().getDatabaseType() == DatabaseType.SQLITE) {
    getLogger().severe("[ZAH] Cluster bridge with storage-type SQLITE: each server has its own database, "
                     + "item ids collide, cluster locks are meaningless. Items WILL be duplicated.");
    getLogger().severe("[ZAH] Switch to MYSQL/MARIADB or remove the cluster addon.");
    Bukkit.getPluginManager().disablePlugin((Plugin) bridgeOwnerOrSelf()); return;
}
getLogger().info("[ZAH] Cluster bridge = " + bridge.getClass().getSimpleName()
               + " (distributed=" + bridge.isDistributed() + ")");
```
Compléter : `ItemLoaderUtils` refuse de publier un item AUCTION dont `currentAuctionItems` est vide (log `SEVERE` + quarantaine) ; `V3MigrationProvider.java:126` propage `result.isSuccess()` comme le font déjà les trois providers frères ; `ZAuctionHouseRedis` teste `isEnabled()` sur le plugin principal et **enregistre les listeners avant** de démarrer le thread abonné.

**Test** : `/ah admin generate 40000` sur SQLite → aujourd'hui l'hôtel des ventes entier se charge vide et vend du néant ; après correction, le chargement pagine et réussit. Couper MySQL au boot → le plugin refuse de démarrer proprement et ferme son pool.

---

### Chantier 7 — Réconciliation du bus et expiration des statuts transitoires P2 · risque faible

- **Numéro de séquence par serveur** dans `RedisMessage` ; un trou détecté par `RedisSubscriberRunnable` déclenche `reconcileItemsSince(disconnectedAt)` : `SELECT id, storage_type, buyer_unique_id FROM items WHERE updated_at >= ?` puis repositionnement/purge en mémoire. Ajouter le point d'entrée au contrat (`default CompletableFuture<Void> onTransportRecovered(long)`).
- **CAS côté receveur** : `ItemStatusListener.java:54` ne doit appliquer `newStatus` que si `item.getStatus() == message.oldStatus()`, et refuser tout statut du cycle `LISTED` sur un item détenu en `PURCHASED`/`EXPIRED`.
- **TTL des statuts de confirmation** : horodater `setStatus`, tâche `runTimerAsync(10 s)` qui remet `AVAILABLE` tout `IS_*_CONFIRM` de plus de 60 s ; et libération explicite dans `PlayerListener.onQuit` **avant** `removeCache`.
- **Tâche d'expiration planifiée** (`runTimerAsync(60 s)`, lot borné à 50, sous verrou distribué ou nœud élu par `SET NX` sur `auction:expire:leader`) : aujourd'hui l'expiration n'est déclenchée que par `getItemIds`, que la liste principale n'emprunte jamais.
- **`ZAuctionHouseRedis.onDisable`** : restaurer le bridge par défaut **avant** `jedisPool.close()`.

---

### Chantier 8 — Confinement de threads et pools dédiés P2 · risque faible · **perf franchement positive**

- Pool dédié dans l'addon (`Executors.newFixedThreadPool(pool.max-total)`) passé au bridge, utilisé aux 7 sites `supplyAsync`/`runAsync`, fermé dans `onDisable` ; `poolConfig.setMaxWait(Duration.ofMillis(config.getTimeout()))` (aujourd'hui **attente infinie**) ; supprimer le double emprunt imbriqué en rechargeant les scripts sur la connexion déjà tenue.
- Passer `getExecutorService()` aux deux `supplyAsync` orphelins (`ZStorageManager.java:177`, `:215`), et basculer `log`/`createTransaction`/`upsertPlayer`/`markPurchaseLogAsRead` de `async()` (scheduler non drainé) vers cet executor — c'est ce qui perd la dette `PENDING` du vendeur à l'arrêt.
- `ZPlayerCache` : `ConcurrentHashMap` + `computeIfAbsent` atomique ; `caches` indexé par `UUID` et non par `Player` ; `peekCache()` non créant pour les chemins post-déconnexion.
- Sérialiser les mutations d'économie par compte, ou les exécuter via `runNextTick` (les providers `LevelProvider`/`ExperienceProvider`/`ItemProvider` font du read-modify-write nu sur des API main-thread-only).
- `volatile boolean shuttingDown` testé à l'entrée de `PurchaseService`/`RemoveService`/`SellService`, et arrêt de l'executor seulement après compteur d'opérations en vol à zéro.

---

## 4. Corrections rapides (moins de 10 lignes, sans risque, applicables immédiatement)

```diff
# ZAuctionManager.java:956 — sort de TOUTE la boucle : la moitié des joueurs garde un GUI périmé
-                        if (onlinePlayer == ignoredPlayer) return;
+                        if (onlinePlayer == ignoredPlayer) continue;
```
```diff
# ZAuctionManager.java:689 — la surcharge 2 args diffuse REMOVED (non terminal) : les autres serveurs remettent l'item en vente
-        }).thenCompose(lockToken -> clusterBridge.removeItem(item, storageType).thenApply(v -> lockToken))
+        }).thenCompose(lockToken -> {
+            if (LockToken.noop().value().equals(lockToken.value()))
+                return CompletableFuture.failedFuture(new IllegalStateException("locked elsewhere"));
+            return clusterBridge.removeItem(item, storageType, StorageType.DELETED).thenApply(v -> lockToken);
+        })
```
```diff
# ClaimService.java:78 — l'argent est compté (et marqué RETRIEVED) même sans dépôt
-                    }
-                    totalClaimed = totalClaimed.add(economyTotal);
+                        totalClaimed = totalClaimed.add(economyTotal);
+                        claimedIds.addAll(economyTransactions.stream().map(TransactionDTO::id).toList());
+                    }
-            repository.updateStatus(transactionIds, TransactionStatus.RETRIEVED);
+            if (!claimedIds.isEmpty()) repository.updateStatus(claimedIds, TransactionStatus.RETRIEVED);
```
*(dupliquer à l'identique dans `clearPendingTransactions`, `ClaimService.java:200-210`, en ajoutant le `continue` manquant après l'échec du dépôt)*

```diff
# PurchaseService.java:121 — une ligne EXPIRED (destination par défaut d'un retrait) passe la garde
-                                if (dbItem == null || dbItem.getBuyerUniqueId() != null) {
+                                if (dbItem == null || dbItem.getBuyerUniqueId() != null
+                                        || dbItem.getStatus() != ItemStatus.AVAILABLE) {
```
```diff
# ConfirmHelper.java:70 — onInventoryClose a la garde, onBackClick ne l'a pas
         if (processIfExpired(player, item)) return;
+        if (item.getStatus() != this.previous) return;   // opération déjà engagée ailleurs
         item.setStatus(this.next);
```
```diff
# ItemStatusListener.java:54 (addon) — applique un statut périmé sans jamais lire oldStatus
+            if (message.oldStatus() != null && item.getStatus() != message.oldStatus()) return;
             item.setStatus(message.newStatus());
```
```diff
# LocalAuctionClusterBridge.java:24 — contrat divergent : un échec bénin devient INTERNAL_ERROR et coupe les retraits de masse
-        return CompletableFuture.failedFuture(new IllegalStateException("Item already locked"));
+        return CompletableFuture.completedFuture(LockToken.noop());
```
```diff
# ZStorageManager.java:177 et :215 — seuls supplyAsync sans executor : JDBC bloquant sur commonPool
-        return CompletableFuture.supplyAsync(() -> {
+        return CompletableFuture.supplyAsync(() -> {
             ...
-        });
+        }, this.plugin.getExecutorService());
```
```diff
# AdminLogsButton.java:143 — les annonces multi-stacks sont écrites avec ';' et relues d'un bloc : audit vide
-                ItemStack decoded = Base64ItemStack.decode(log.itemstack());
-                if (decoded != null) itemStacks.add(decoded);
+                for (String part : log.itemstack().split(";")) {
+                    if (part.isBlank()) continue;
+                    ItemStack d = Base64ItemStack.decode(part);
+                    if (d != null) itemStacks.add(d);
+                }
```
```diff
# ItemStackUtils.java:37 — récursion mutuelle avec Base64ItemStack.decode (StackOverflowError, non rattrapable)
-        } catch (Exception localException) { localException.printStackTrace(); }
-        return Base64.encode(localByteArrayOutputStream.toByteArray());
+        } catch (Exception e) { logger.severe("serializeItemStack failed: " + e.getMessage()); return null; }
+        return Base64.encode(localByteArrayOutputStream.toByteArray());
```
```diff
# ZAuctionHouseRedis.java:83-104 — les listeners doivent être enregistrés AVANT le démarrage du thread abonné
+        this.enableDebug = getConfig().getBoolean("debug");
+        this.redisSubscriberRunnable.registerListener(ItemListedMessage.class, new ItemListedListener(this));
+        ... (les 4 registerListener)
         this.subscriberThread.start();
```
Ajouter enfin : `private volatile AuctionClusterBridge auctionClusterBridge;` (`ZAuctionPlugin.java:100`) et `private volatile JedisPubSub jedisPubSub;` (`RedisSubscriberRunnable.java:32`).

---

## 5. Gains de performance gratuits

1. **Le CAS remplace la revalidation (ch. 1)** — `selectItem` coûte 3 requêtes (`ZStorageManager.java:176-207`). **−3 allers-retours DB par achat** (~5-20 ms) et verrou détenu d'autant moins longtemps. La version sûre est plus rapide.
2. **`checkAvailability` supprimé du chemin engageant (ch. 4)** — il est strictement redondant avec `LOCK_SCRIPT` (mêmes deux refus) : **−1 emprunt Jedis et −1 RTT** sur les 4 chemins chauds (achat, retrait, expiration, admin), soit ~33 % des emprunts de pool.
3. **Executors dédiés + `maxWait` (ch. 8)** — `commonPool` plafonne la concurrence à `cores-1` (3 workers) alors que `pool.max-total: 64` est configuré et inutilisé : **×10 à ×20 de débit** sous charge, et fin des `orTimeout` déclenchés au milieu des sections critiques (gain de perf **et** de sûreté).
4. **Sortir `getPlayerName` de la section critique** (`ZAuctionManager.java:784` → JDBC 100 % synchrone) : temps de détention du verrou par item réduit de plusieurs ms à ~0.
5. **`updateItems` doit utiliser le batch existant** — `ItemRepository.java:58-69` boucle des `update()` unitaires alors que `Repository.update(List<Schema>)` est câblé sur `UpdateBatchRequest` (déjà utilisé par `TransactionRepository`). Expiration de masse : N allers-retours → 1. Gain quasi linéaire en N.
6. **Chargement `O(n×m)` → `O(n+m)`** — `ItemLoaderUtils.java:31-33` refait un `stream().filter()` sur toute la liste pour chaque item : 20 000 × 25 000 = 500 M de comparaisons à chaque boot. Remplacer par un `Collectors.groupingBy(AuctionItemDTO::item_id)` avant la boucle.
7. **Compteurs servis depuis `SortedItemsCache`** — `ZCategoryManager.computeCategoryCount:253-271` et `GlobalPlaceholders.java:18` recopient tout le store `LISTED` (`getItems()` alloue une `ArrayList` complète) alors que `getTotalCount()` est en `O(1)` avec exactement le même filtre. Un scoreboard suffit aujourd'hui à consommer des dizaines de ms/s de main thread.
8. **Index manquants** — aucune migration n'en crée. Ajouter `items(storage_type)`, `items(seller_unique_id, storage_type)`, `transactions(player_unique_id, status)`, `logs(created_at)` : `selectByPlayerAndStatus` est aujourd'hui un scan complet à chaque connexion.
9. **`return` → `continue` (`:956`)** : corrige la justesse *et* la perf (les joueurs non rafraîchis re-sollicitent le cache trié et rouvrent des inventaires sur des données mortes).
10. **Enrichir `ItemListedMessage`** avec les champs que `selectItem` reconstruit : supprime N−1 × 3 requêtes SQL par événement cluster (240 req/s à 20 événements/s sur 5 nœuds).

---

## 6. Observabilité

### État actuel : une duplication est **indétectable**

Le rowcount, seul signal de course, est jeté (`Repository.java:242-245`). Les erreurs SQL partent en `printStackTrace()` sur `System.err`. `ClaimService` ne logge rien quand `player.isOnline()` est faux : l'argent est détruit en silence. `ZAuctionHouseRedis.sendMessage` avale l'échec de publication et renvoie un future **réussi**. `V3MigrationProvider` annonce un succès sur un échec. `unlockItem` jette le `0` du script Lua. Aucun identifiant de corrélation ne relie un clic, un verrou, un `UPDATE` et une transaction.
**Réponse honnête à « comment saurait-on qu'une dupe a eu lieu ? » : par la plainte d'un joueur, ou pas du tout.**

### Instrumentation à mettre en place

**a) Le signal précurseur : le CAS perdu.** Une fois le chantier 1 livré, `rows == 0` est *l'événement* à surveiller — c'est la métrique unique qui remplace tous les diagnostics a posteriori :
```
[CAS-LOST] item=42 from=LISTED to=DELETED rows=0 server=survie-2 op=<uuid> actor=<uuid> path=REMOVE_SELLING
```
Zéro `CAS-LOST` = aucune course perdue. Un pic = une divergence en cours. À exporter en compteur (bStats ou log structuré).

**b) Trois autres compteurs à zéro-attendu** : `UNLOCK-DENIED` (unlock refusé → un retardataire aurait volé un verrou dans l'ancien code, ch. 4) ; `BUS-GAP` (trou de séquence détecté par le subscriber, ch. 7) ; `EMPTY-ITEM` (item chargé sans contenu — signature d'un INSERT partiel ou du `IN()` cassé).

**c) `operation_id` (UUID) de bout en bout** — généré au clic, porté par la chaîne `CompletableFuture`, inséré dans le `LockToken` (il y est déjà via le nonce du ch. 4), dans les `RedisMessage`, dans `logs` et dans `transactions`. Sans lui, corréler un achat sur A et un retrait sur B est impossible.

**d) Journal d'audit des transitions** — `LogRepository` existe déjà : ajouter `LogType.STATE_TRANSITION` portant `from`, `to`, `rows`, `server_name`, `lock_token`, `operation_id`. La colonne `server_name` est déjà écrite dans `items` (`ItemRepository.java:39`) et jamais relue : l'exploiter pour savoir *quel nœud* a fait quoi.

**e) Requêtes de contrôle — invariants à faire tourner en tâche d'entretien** :
```sql
-- INVARIANT CENTRAL : doit retourner ZÉRO ligne en permanence
SELECT * FROM zauctionhouse_items WHERE storage_type = 'LISTED' AND buyer_unique_id IS NOT NULL;

-- Un item remis plusieurs fois : signature exacte d'une duplication
SELECT item_id, COUNT(*) c FROM zauctionhouse_logs
WHERE log_type IN ('REMOVE_LISTED','REMOVE_SELLING','REMOVE_EXPIRED','REMOVE_PURCHASED','PURCHASE')
GROUP BY item_id HAVING c > 1;

-- Annonces fantômes : ligne vivante sans aucun contenu (INSERT partiel, IN() cassé, encode null)
SELECT i.id, i.storage_type, i.seller_unique_id, i.price FROM zauctionhouse_items i
LEFT JOIN zauctionhouse_auction_items a ON a.item_id = i.id
WHERE i.item_type = 'AUCTION' AND i.storage_type <> 'DELETED' AND a.id IS NULL;

-- Argent détruit : transaction RETRIEVED sans dépôt journalisé
SELECT t.* FROM zauctionhouse_transactions t
WHERE t.status = 'RETRIEVED'
  AND NOT EXISTS (SELECT 1 FROM zauctionhouse_logs l WHERE l.item_id = t.item_id AND l.log_type = 'CLAIM');

-- Dette figée : PENDING d'un vendeur en ligne depuis > 24 h (bus perdu ou scheduler non drainé)
SELECT player_unique_id, COUNT(*), SUM(value) FROM zauctionhouse_transactions
WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL 1 DAY GROUP BY player_unique_id;
```

**f) Mode d'exécution visible.** Logger `auctionClusterBridge.getClass().getSimpleName()` au démarrage **et** à chaque `setAuctionClusterBridge`, l'exposer en placeholder `%zauctionhouse_cluster_mode%`, et prévoir `cluster.required: true` → si `isDistributed()` est faux 10 s après le boot, bloquer ventes/achats/retraits plutôt que de vendre sans verrou partagé. Un admin doit pouvoir vérifier en 5 secondes que ses 3 nœuds sont bien tous en `RedisAuctionClusterBridge` et tous sur MySQL.

**g) Prérequis technique** : remplacer les `printStackTrace()` des 6 méthodes de `Repository` par `logger.log(Level.SEVERE, ...)`, et faire échouer le future de `sendMessage` quand `publish` échoue. Sans cela, **aucun** des points ci-dessus n'atteindra les logs collectés.

---

# Partie 2 — Catalogue des constats et correctifs

Chaque constat porte un identifiant stable (`C-001`…). La case à cocher sert de suivi d’application.

## Constats de sévérité critique (16)

<a id="c-001"></a>

### `C-001` — Au-dela d'environ 65 000 items, le IN(...) de AuctionItemRepository.select echoue, l'echec est avale par Repository.select et TOUS les items du reseau sont charges sans aucun contenu

- [ ] **Corrigé**
- **Fichier** : `storage/repository/repositories/AuctionItemRepository.java:41`
- **Catégorie** : Perte d’argent — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
AuctionItemRepository.java:40-42 -- un seul IN, un placeholder par item, aucune pagination :
    public List<AuctionItemDTO> select(List<String> ids) {
        return ids.isEmpty() ? List.of() : select(AuctionItemDTO.class, schema -> schema.whereIn("item_id", ids));
    }

Sarah/.../conditions/WhereCondition.java:41-43 -- un '?' par identifiant :
        if (this.whereAction == WhereAction.IN) {
            return this.column + " IN (" + values.stream().map(id -> "?").collect(Collectors.joining(",")) + ")";
        }

API/.../api/storage/Repository.java:172-181 -- TOUTE exception est avalee et remplacee par une liste VIDE :
    protected <T> List<T> select(Class<T> clazz, Consumer<Schema> consumer) {
        Schema schema = SchemaBuilder.select(getTableName());
        consumer.accept(schema);
        try {
            return schema.executeSelect(clazz, this.connection, this.logger);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return new ArrayList<>();
    }

ItemLoaderUtils.java:58-60 -- le resultat est utilise tel quel, aucun controle de coherence :
        long auctionItemsStartTime = performanceDebug.start();
        var auctionItems = storageManager.with(AuctionItemRepository.class).select(getIDS(items, ItemType.AUCTION));
        performanceDebug.end("loadItems.loadAuctionItemsFromDB", auctionItemsStartTime, "count=" + auctionItems.size());

CommandAuctionAdminGenerate.java:57 -- le seuil est atteignable d'une seule commande fournie par le plugin :
        if (amount < 1 || amount > 100000) {
```

**Chronologie**

T0 - Le reseau grossit, ou un admin lance `/ah admin generate 100000` (CommandAuctionAdminGenerate.java:57 autorise 100 000 en une commande). Le nombre de lignes `items` avec `storage_type <> 'DELETED'` depasse 65 535. La base est PARTAGEE entre les serveurs A, B et C.

T1 - Serveur A redemarre. AuctionLoader -> ItemRepository.select() charge les 70 000 ItemDTO ; ItemLoaderUtils.java:59 appelle AuctionItemRepository.select avec 70 000 identifiants.

T2 - WhereCondition.java:42 genere `... WHERE item_id IN (?,?,...)` avec 70 000 marqueurs. Le protocole de preparation MySQL/MariaDB code le nombre de parametres sur 16 bits : la preparation echoue (ou l'execution echoue sur max_allowed_packet selon le pilote).

T3 - Repository.select l.177 : `catch (Exception exception) { exception.printStackTrace(); }` puis `return new ArrayList<>();`. Le chargement continue comme si de rien n'etait ; le seul indice est une stacktrace en console.

T4 - ItemLoaderUtils.java:84 `getAuctionItems(auctionItems, dto.id())` rend une liste vide pour CHAQUE item ; l.39 produit `itemStacks = []` pour les 70 000 items, tous ajoutes aux stores LISTED/EXPIRED/PURCHASED.

T5 - Le serveur A demarre avec un hotel des ventes complet a l'affichage (icone `special-items.auction-item` pour chacun, ZAuctionItem.java:81) mais entierement vide en contenu.

T6 - Serveurs B et C, non redemarres, continuent d'afficher les vrais contenus : les noeuds divergent totalement sur la meme base. Chaque achat effectue sur A debite l'acheteur (ZAuctionManager.java:802) et ne livre rien (l.930, boucle vide) ; chaque retrait sur A marque la ligne DELETED et ne rend rien -- l'item est detruit pour B et C aussi.

**Impact** — Falaise de montee en charge franchie silencieusement, avec destruction massive : sur le noeud redemarre, tout achat prend l'argent sans livrer et tout retrait detruit definitivement l'item pour tout le reseau. Aucun mecanisme de detection : l'exception est avalee, aucun compteur n'est compare.

**Précision apportée par la contre-expertise**

Le constat est confirme, mais deux points doivent etre reformules :

A) LE SEUIL DEPEND DU MODE DE STOCKAGE, il n'est pas "environ 65 000" partout.

Sarah n'active les prepares SERVEUR que pour MYSQL, jamais pour MARIADB (`Sarah/.../HikariDatabaseConnection.java:82-86`) :

```java

if (databaseType == DatabaseType.MYSQL) {

commonProps.put("cachePrepStmts", "true");

...

commonProps.put("useServerPrepStmts", "true");

```

- `storage-type: MYSQL` -> prepare serveur -> plafond dur de 65 535 marqueurs (ER_PS_MANY_PARAM 1390, "Prepared statement contains too many placeholders"). C'est exactement le scenario decrit.

- `storage-type: MARIADB` -> aucune propriete `useServerPrepStmts` n'est posee, et mariadb-java-client 3.5.6 (le SEUL pilote declare, `plugin.yml:19 - org.mariadb.jdbc:mariadb-java-client:3.5.6`) prepare cote client par defaut : pas de plafond 16 bits. 70 000 identifiants courts font ~600 Ko, donc sous max_allowed_packet : la requete passerait probablement. Le scenario ne se declenche PAS a 65 535 dans ce mode.

- `storage-type: SQLITE` (defaut, `config.yml:129`) -> le plafond est PLUS BAS que celui annonce : SQLITE_MAX_VARIABLE_NUMBER = 32 766 sur sqlite-jdbc recent (999 sur tres ancien). La falaise est franchie vers ~32 000 items, pas 65 000.

Formuler donc : "le IN non paginé casse a un plafond de parametres dependant du pilote : ~32 766 en SQLITE, 65 535 en MYSQL (prepare serveur force par Sarah), et seulement au max_allowed_packet en MARIADB".

B) LE POINT 4 DE LA CORRECTION PROPOSEE EST INEXACT POUR `auction_items.item_id`.

`storage/migrations/CreateAuctionItemMigration.java` declare deja :

```java

table.integer("item_id").foreignKey(Tables.ITEMS, "id", true);

```

Sous InnoDB (MySQL/MariaDB), une cle etrangere cree automatiquement un index sur la colonne referencante : l'index existe donc deja en MySQL/MariaDB (il manque en revanche en SQLite, ou les FK n'indexent pas). L'absence d'index reste vraie et pertinente pour `items.storage_type`, filtre par `ItemRepository.select()` sur toute la table.

Le reste du constat (IN unique non pagine, un `?` par id, exception avalee et remplacee par une liste vide, absence totale de controle de coherence, hotel des ventes affiche mais vide, achat qui debite sans livrer, retrait qui detruit l'item pour tout le reseau) est integralement verifie ligne par ligne et reste valable. Les corrections 1, 2 et 3 proposees sont pertinentes telles quelles ; ajouter que la taille de lot doit rester <= 999 si l'on veut couvrir aussi les vieilles versions de SQLite, et que la variante jointure (`SELECT a.* FROM auction_items a JOIN items i ON i.id = a.item_id WHERE i.storage_type <> 'DELETED'`) est preferable car elle supprime totalement la dependance au nombre de parametres.

**Correctif**

````java
1) Paginer AuctionItemRepository.select par lots :
```java
public List<AuctionItemDTO> select(List<String> ids) {
    if (ids.isEmpty()) return List.of();
    List<AuctionItemDTO> result = new ArrayList<>(ids.size());
    for (int i = 0; i < ids.size(); i += 1000) {
        List<String> chunk = ids.subList(i, Math.min(i + 1000, ids.size()));
        result.addAll(select(AuctionItemDTO.class, schema -> schema.whereIn("item_id", chunk)));
    }
    return result;
}
```
ou mieux, remplacer le IN par une jointure : `SELECT a.* FROM auction_items a JOIN items i ON i.id = a.item_id WHERE i.storage_type <> 'DELETED'`.
2) Repository.select ne doit pas convertir une exception en liste vide : remonter l'erreur (ou au minimum logger en SEVERE et lever), et AuctionLoader.loadItems doit refuser de demarrer plutot que d'exposer un hotel des ventes vide.
3) Controle de coherence au chargement : si `auctionItems.isEmpty()` alors que `items` contient des lignes de type AUCTION, journaliser en SEVERE et interrompre le chargement.
4) Ajouter un index sur `items.storage_type` et sur `auction_items.item_id` (aucun n'existe dans les migrations).
````

---

<a id="c-002"></a>

### `C-002` — Côté Redis, SOLD et REMOVED sont considérés disponibles et DELETED s'auto-détruit au bout de 24 h : le verrou ne protège que la simultanéité, jamais la séquence

- [ ] **Corrigé**
- **Fichier** : `REDIS/RedisAuctionClusterBridge.java:163`
- **Catégorie** : Duplication d’item — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```lua
checkAvailability (vérifié l.158-177) :

    String state = jedis.hget(key, FIELD_STATE);
    if (STATE_DELETED.equals(state)) {
        return false;
    }
    // Only LOCKED means an operation is in progress.
    // null, AVAILABLE, SOLD, REMOVED are all settled states.
    if (!STATE_LOCKED.equals(state)) {
        return true;
    }
    return !jedis.exists(lockKey(item));

LOCK_SCRIPT (vérifié l.46-56) : seul 'DELETED' renvoie 0.

    local currentState = redis.call('HGET', itemKey, 'state')
    if currentState == 'DELETED' then
        return 0
    end

removeItem (vérifié l.350-360) : l'état terminal porte un TTL, et la destination null produit REMOVED.

    String state = destinationStorageType == StorageType.DELETED ? STATE_DELETED : STATE_REMOVED;
    jedis.hset(itemKey, FIELD_STATE, state);
    if (this.itemStateTtl != null) {
        jedis.expire(itemKey, this.itemStateTtl.toSeconds());
    }

config.yml:51 -> item-state-ttl-seconds: 86400.
Une clé absente (HGET -> nil) est traitée comme disponible par les deux mécanismes.
Enfin, le chemin d'achat n'appelle JAMAIS removeItem : notifyItemBought (l.295) laisse l'état à 'SOLD', jamais 'DELETED'.
```

**Chronologie**

Trois portes ouvertes par le même défaut.

(a) SOLD — T0 serveur A : achat terminé de l'item 42, notifyItemBought pose state='SOLD', unlockItem supprime auction:lock:42 sans toucher à l'état. T1 serveur B : le vendeur déclenche un retrait. checkAvailability lit 'SOLD' → branche `!STATE_LOCKED` → TRUE. LOCK_SCRIPT ne refuse que DELETED → SET NX réussit → verrou accordé sur un item VENDU. T2 : RemoveService ne revalide pas → l'item est rendu au vendeur. Duplication.

(b) REMOVED — une expiration LISTED→EXPIRED (removeItem l.354, destination != DELETED) écrit 'REMOVED', qui ne bloque rien dès l'instant zéro. Idem pour adminRemoveItem qui appelle la surcharge à 2 arguments (destination null → REMOVED) alors que la suppression est définitive.

(c) DELETED expirant — T0 serveur A : le vendeur réclame l'item 42 expiré. giveItem, ligne DB → DELETED, HSET state='DELETED' + EXPIRE 86400, publish. T1 : le message n'atteint pas B (subscriber en backoff, jusqu'à 60 s) : B garde l'item 42 dans son store EXPIRED, status REMOVED, expiredAt = +7 jours. T2 (T0 + 24 h et 1 s) : auction:item:42 expire ; HGET state rend nil → checkAvailability TRUE, LOCK_SCRIPT accorde. T3 : le vendeur clique sur B → giveItem → SECOND EXEMPLAIRE.

**Impact** — Le verrou distribué ne protège que la SIMULTANÉITÉ : un serveur arrivant après coup obtient légitimement le verrou sur un item déjà vendu, déjà retiré, ou détruit depuis plus de 24 h. C'est la brique Redis qui rend exploitable l'absence de revalidation DB dans RemoveService. Le champ FIELD_BUYER déclaré l.29 n'est d'ailleurs jamais ni écrit ni lu.

**Précision apportée par la contre-expertise**

Deux imprecisions de redaction a corriger, sans changer le fond ni la severite :

1) Le point (b), premiere moitie, n'est PAS un defaut actif aujourd'hui. Ecrire STATE_REMOVED pour une transition LISTED -> EXPIRED (ExpireService.java:293 `clusterBridge.removeItem(item, StorageType.LISTED, StorageType.EXPIRED)`) est fonctionnellement correct dans l'etat actuel : l'item doit rester reclamable depuis le bucket EXPIRED, et REMOVED ne bloque rien. Ce n'est pas une porte ouverte en soi, c'est un COROLLAIRE OBLIGATOIRE du correctif 1 : des l'instant ou REMOVED devient terminal dans checkAvailability et LOCK_SCRIPT, cette ligne casse toute reclamation d'item expire. Le correctif 2 propose (EXPIRED -> STATE_AVAILABLE) est donc indispensable et doit etre applique DANS LE MEME COMMIT, jamais apres. Seule la seconde moitie du point (b) — adminRemoveItem (ZAuctionManager.java:689) qui ecrit REMOVED en Redis pendant que la base passe a DELETED (l.693) — est une incoherence reelle et autonome.

2) "unlockItem supprime auction:lock:42 sans toucher a l'etat" est imprecis : UNLOCK_SCRIPT (l.86-89) touche bien l'etat, `local currentState = redis.call('HGET', itemKey, 'state') if currentState == 'LOCKED' then redis.call('HSET', itemKey, 'state', 'AVAILABLE') end`. La formulation exacte est : parce que notifyItemBought a deja ecrit SOLD avant l'unlock (PurchaseService.java:135 puis :137), la condition `currentState == 'LOCKED'` est fausse et l'etat reste SOLD — donc l'unlock ne repasse pas l'item en AVAILABLE, mais il le laisse dans un etat que checkAvailability considere quand meme comme disponible. Le resultat est le meme, la mecanique est differente. A noter au passage, hors perimetre du constat : si le HSET SOLD echoue ou expire (`orTimeout(notifyItemActionTimeoutMs)` l.136), la branche `exceptionally` l.169 appelle unlockItem sur un etat encore LOCKED, qui repasse alors explicitement a AVAILABLE — ce qui renforce l'argument du correctif 4 (fusionner SOLD + unlock + publish en un seul script Lua).

Formulation corrigee du titre : "Cote Redis, SOLD et REMOVED sont consideres disponibles par checkAvailability et LOCK_SCRIPT, et l'etat terminal DELETED s'auto-detruit apres 24 h (item-state-ttl-seconds) : le verrou distribue ne protege que la simultaneite, jamais la sequence — et c'est le seul rempart du chemin de retrait, qui, contrairement a PurchaseService et ExpireService, ne revalide jamais en base."

**Correctif**

```lua
1) Rendre les états terminaux réellement terminaux.

// checkAvailability
if (STATE_DELETED.equals(state) || STATE_SOLD.equals(state) || STATE_REMOVED.equals(state)) {
    return false;
}
if (!STATE_LOCKED.equals(state)) return true;
return !jedis.exists(lockKey(item));

// LOCK_SCRIPT (et le repli non atomique l.220-226, à l'identique)
local currentState = redis.call('HGET', itemKey, 'state')
if currentState == 'DELETED' or currentState == 'SOLD' or currentState == 'REMOVED' then
    return 0
end

2) Corollaire indispensable : une transition vers un bucket encore réclamable ne doit plus écrire REMOVED. Dans removeItem :

String state = destinationStorageType == StorageType.DELETED ? STATE_DELETED
             : destinationStorageType == StorageType.EXPIRED ? STATE_AVAILABLE   // vit dans EXPIRED, reste réclamable
             : STATE_REMOVED;

3) Ne jamais faire expirer un état terminal :

jedis.hset(itemKey, FIELD_STATE, state);
if (STATE_DELETED.equals(state)) { jedis.persist(itemKey); }
else if (this.itemStateTtl != null) { jedis.expire(itemKey, this.itemStateTtl.toSeconds()); }

Une entrée DELETED coûte quelques dizaines d'octets ; sa purge doit être une tâche d'entretien alignée sur la rétention de la table items, pas un TTL de 24 h.

4) Rendre notifyItemBought atomique et terminal : un unique script Lua qui pose state='SOLD', supprime le champ lock et la clé auction:lock:<id>, et publie — l'unlock devient superflu après une vente et ne peut plus remettre 'AVAILABLE'.

5) Écrire enfin le champ buyer (`jedis.hset(key, FIELD_BUYER, player.getUniqueId().toString())`) pour permettre un diagnostic sans aller-retour DB.
```

---

<a id="c-003"></a>

### `C-003` — Double claim inter-serveurs : ClaimService.claimMoney depose l'argent AVANT un UPDATE non conditionnel dont le resultat est jete

- [ ] **Corrigé**
- **Fichier** : `services/ClaimService.java:71`
- **Catégorie** : Duplication d’argent — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```sql
ClaimService.java:69-84 (lu ligne a ligne) :
                if (economyTotal.compareTo(BigDecimal.ZERO) > 0) {
                    if (player.isOnline()) {
                        try {
                            economy.deposit(player.getUniqueId(), economyTotal, depositReason);
...
            // Mark all transactions as retrieved synchronously to prevent double claims
            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            repository.updateStatus(transactionIds, TransactionStatus.RETRIEVED);

TransactionRepository.java:46-57 : AUCUN where("status", PENDING), donc aucun compare-and-set :
    public void updateStatus(Collection<Integer> transactionIds, TransactionStatus status) {
        if (transactionIds == null || transactionIds.isEmpty()) return;
        var schemas = transactionIds.stream()
                .map(transactionId -> createUpdateSchema(schema -> {
                    schema.where("id", transactionId);
                    schema.string("status", status.name());
                }))
                .toList();
        update(schemas);
    }

API/.../api/storage/Repository.java:242-245 : le nombre de lignes affectees est jete (retour void, aucun try/catch) :
    protected void update(List<Schema> schemas) {
        UpdateBatchRequest updateBatchRequest = new UpdateBatchRequest(schemas);
        updateBatchRequest.execute(this.connection, this.connection.getDatabaseConfiguration(), this.logger);
    }

ClaimService.java:117-121 : la lecture est asynchrone et non reservante :
        return CompletableFuture.supplyAsync(() -> {
            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            return repository.selectByPlayerAndStatus(playerUniqueId, TransactionStatus.PENDING);
        }, this.plugin.getExecutorService());

Aucune occurrence de clusterBridge dans tout ClaimService.java : ce flux monetaire n'est protege par AUCUN verrou distribue.
```

**Chronologie**

Pre-requis : reseau Velocity/Bungee, MySQL partagee. ZAuctionManager.java:810-818 force deferDeposit des que le vendeur n'est pas sur le serveur de l'acheteur (`(!sellerOnThisServer && clusterBridge.isDistributed())`), donc TOUTE vente inter-serveurs produit une ligne transactions status='PENDING' : le stock de lignes exploitables est permanent.

T0 - Le joueur J a 1 ligne PENDING de 100 000 (economie vault).

T1 - Serveur A : J se connecte. PlayerListener -> ClaimService.handlePlayerJoin (l.125) -> auto-claim -> claimMoney(J). getPendingTransactions (l.117-121) part sur l'executor et execute `SELECT ... WHERE player_unique_id=J AND status='PENDING'` -> renvoie la ligne #501. Rien n'est reserve.

T2 - Pendant que la requete de A est en vol (5-300 ms sur MySQL), J est transfere par le proxy sur le serveur B (comportement standard d'un hub). Sur B : PlayerJoinEvent -> handlePlayerJoin -> claimMoney -> MEME SELECT -> renvoie EGALEMENT la ligne #501, toujours PENDING.

T3 - Serveur A : ligne 71 `economy.deposit(J, 100000)` -> +100 000.

T4 - Serveur B : ligne 71 `economy.deposit(J, 100000)` -> +100 000. Total credite : 200 000 pour une seule vente.

T5 - Serveur A : ligne 84 `UPDATE transactions SET status='RETRIEVED' WHERE id=501`.

T6 - Serveur B : le MEME UPDATE, qui reussit aussi (pas de `AND status='PENDING'`), et son compte de lignes est jete par Repository.update(List). Aucun des deux serveurs ne peut savoir qu'il a perdu la course.

Variante mono-serveur, sans deuxieme serveur : le seul garde-fou est le cooldown de commande (default-cooldown 100 ms) ; deux `/ah claim` espaces de 110 ms partent tous deux sur l'executor et lisent la meme ligne PENDING. ClaimButton n'est soumis a aucun rate-limit du tout.

Variante sans concurrence : si l'UPDATE l.84 echoue, UpdateBatchRequest leve une DatabaseException (RuntimeException) que Repository.update(List<Schema>) n'attrape PAS ; l'argent est deja depose l.71, les lignes restent PENDING, et le joueur reclame de nouveau au prochain join.

**Impact** — Duplication d'argent illimitee et trivialement reproductible (double session, transfert de serveur au join, ou macro-clavier). Le commentaire ligne 82 revendique explicitement une protection anti-double-claim que le SQL genere n'implemente pas. C'est le seul flux monetaire du plugin qui n'a ni verrou cluster ni garde d'etat.

**Précision apportée par la contre-expertise**

Le defaut est reel et la correction proposee est valide, mais TROIS points de la description doivent etre rectifies :

1) LE SCENARIO T1-T4 IGNORE LA GARDE `player.isOnline()` (ClaimService.java:69). Le depot l.71 est enferme dans `if (player.isOnline())`. Sur le serveur A, une fois le transfert proxy termine, `isOnline()` est faux et A NE DEPOSE PAS. Le double depot inter-serveurs exige donc que le joueur soit en ligne SIMULTANEMENT sur les deux backends - fenetre reelle mais etroite (Velocity/Bungee complete le join sur B avant de couper A), et encore reduite par les deux re-verifications `player.isOnline()` de handlePlayerJoin (l.130 et l.150, avec `delay-ticks: 20` par defaut). Le scenario tel qu'ecrit ("J est transfere sur B" puis "T3 - Serveur A deposit") est donc faux en l'etat.

2) LE VRAI VECTEUR, PLUS ROBUSTE, EST MONO-SERVEUR ET N'EXIGE NI PROXY NI DOUBLE SESSION. `Executors.newFixedThreadPool(4)` (ZAuctionPlugin.java:82) fait que deux declencheurs de claim sur UN SEUL serveur executent leur SELECT->depot->UPDATE en parallele sur deux threads du pool, le joueur etant incontestablement en ligne pour les deux. Les points d'entree ne partagent aucun etat mutuel : auto-claim au join (`auto-claim.enable: true`, `delay-ticks: 20` par defaut, PlayerListener.java:24) contre `/ah claim` (CommandAuctionClaim), ou `/ah claim` contre un clic ClaimButton. Les deux lisent la meme ligne PENDING et deposent tous les deux. C'est ce chemin qu'il faut mettre en avant.

3) « ClaimButton n'est soumis a aucun rate-limit du tout » EST FAUX. ClaimButton.onClick (l.106-120) lit `PENDING_MONEY_DATA` dans le cache, sort si null/vide, puis appelle `cache.remove(PlayerCacheKey.PENDING_MONEY_DATA, PlayerCacheKey.PENDING_MONEY_LOADING)` AVANT `claimMoney(player)`. onClick s'executant sur le thread principal, un double-clic sur le seul bouton est effectivement bloque par ce vidage de cache. Le bouton n'est dangereux que croise avec un AUTRE point d'entree (commande / auto-claim), qui ne partage pas cet etat.

A AJOUTER AU PERIMETRE DE LA CORRECTION : `ClaimService.clearPendingTransactions` (l.175-211) porte exactement le meme defaut (depot l.200, `repository.updateStatus(transactionIds, RETRIEVED)` inconditionnel l.210) et doit etre corrige par le meme patch.

BUG CONNEXE DECOUVERT EN RELISANT (perte d'argent, sens inverse) : l.78 `totalClaimed = totalClaimed.add(economyTotal);` est HORS du bloc `if (player.isOnline())` mais dans le bloc `economyTotal > 0`. Si le joueur est hors ligne, aucun depot n'a lieu, mais l.84 marque quand meme les lignes RETRIEVED et l.86-88 renvoie un ClaimResult.success : l'argent est definitivement perdu. La reservation atomique proposee doit aussi traiter ce cas (ne finaliser que les lignes reellement payees).

**Correctif**

````java
Inverser l'ordre (reserver d'abord, payer ensuite) et rendre le marquage atomique et attribuable.
1) Exposer le compte de lignes dans API/.../storage/Repository.java :
```java
protected int updateReturning(Consumer<Schema> consumer) {
    try { return SchemaBuilder.update(getTableName(), consumer).execute(this.connection, this.logger); }
    catch (Exception e) { this.logger.severe("update failed on " + getTableName() + ": " + e.getMessage()); return -1; }
}
```
(Sarah remonte deja `preparedStatement.getUpdateCount()` dans UpdateRequest.)
2) Migration : ajouter `claim_token VARCHAR(36) NULL` a %prefix%transactions (MigrationManager ajoute les colonnes manquantes).
3) TransactionRepository :
```java
public int reservePending(UUID playerUniqueId, String token) {
    return updateReturning(schema -> {
        schema.where("player_unique_id", playerUniqueId.toString());
        schema.where("status", TransactionStatus.PENDING.name());   // compare-and-set
        schema.string("status", "CLAIMING");
        schema.string("claim_token", token);
    });
}
public List<TransactionDTO> selectByClaimToken(String token) {
    return select(TransactionDTO.class, s -> s.where("claim_token", token));
}
public int finishClaim(String token, TransactionStatus finalStatus) {
    return updateReturning(schema -> { schema.where("claim_token", token); schema.string("status", finalStatus.name()); });
}
```
4) ClaimService.claimMoney :
```java
String token = UUID.randomUUID().toString();
int won = repository.reservePending(player.getUniqueId(), token);
if (won <= 0) { message(this.plugin, player, Message.CLAIM_NO_PENDING); return ClaimResult.nothingToClaim("No pending transactions"); }
var mine = repository.selectByClaimToken(token);   // exactement les lignes que CE serveur a gagnees
// ... regrouper `mine` par economie et deposer ...
repository.finishClaim(token, TransactionStatus.RETRIEVED);
// si un depot echoue : repasser CES lignes en PENDING et claim_token = NULL
```
L'UPDATE atomique de MySQL/SQLite garantit qu'un seul serveur remporte les lignes ; le perdant obtient won==0 et ne verse rien.
5) Ajouter en complement un dedoublonnage par joueur, sur le modele deja present dans ExpireService.java:31 (`private final Set<Integer> expiringItemIds = ConcurrentHashMap.newKeySet();`) : `Set<UUID> claimingPlayers` teste en tete de claimMoney et libere dans un whenComplete.
````

---

<a id="c-004"></a>

### `C-004` — ItemStatusListener applique le statut recu sans comparer oldStatus : un item deja PURCHASED redevient IS_PURCHASE_CONFIRM et n'est plus jamais reclamable

- [ ] **Corrigé**
- **Fichier** : `REDIS/listener/listeners/ItemStatusListener.java:54`
- **Catégorie** : Perte d’item — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ItemStatusListener.java:16 et 43-54 :
    private static final List<StorageType> SEARCHABLE_STORAGE_TYPES = List.of(StorageType.LISTED, StorageType.EXPIRED, StorageType.PURCHASED);
    ...
            Item item = null;
            for (StorageType storageType : SEARCHABLE_STORAGE_TYPES) {
                var optional = manager.getItems(storageType).stream().filter(e -> e.getId() == id).findFirst();
                if (optional.isPresent()) { item = optional.get(); break; }
            }

            if (item == null) return;

            item.setStatus(message.newStatus());

Le message transporte pourtant oldStatus, jamais lu (listener/messages/ItemStatusMessage.java) ; et la reclamation exige un statut exact (RemoveService.java:172) :
        if (item.getStatus() != ItemStatus.PURCHASED) { ... return failure(INVALID_ITEM_STATUS); }
```

**Chronologie**

Config purchased-item.give-item=false (l'item achete atterrit dans l'onglet PURCHASED de l'acheteur).

T0 - Item #42 LISTED/AVAILABLE sur A et B.

T1 - Acheteur1 clique l'item sur A : diffusion AVAILABLE -> IS_PURCHASE_CONFIRM.

T2 - Acheteur2 clique le meme item sur B avant application du message de T1 (notifyItemStatusChange est un simple PUBLISH, aucun CAS, aucune ecriture Redis).

T3 - Acheteur1 confirme sur A : ZAuctionManager:864 setStatus(PURCHASED), :892 addItem(PURCHASED), :893 updateItem(PURCHASED), notifyItemBought.

T4 - B recoit ItemBoughtMessage, retire de LISTED puis re-ajoute en PURCHASED. L'objet perime retenu par le cache ITEM_SHOW d'Acheteur2 garde IS_PURCHASE_CONFIRM.

T5 - Acheteur2 confirme sur B : PurchaseService:68 passe, le verrou est accorde (SOLD n'est pas terminal), :107 diffuse IS_BEING_PURCHASED, puis la revalidation DB detecte buyer != null et avorte.

T6 - PurchaseService:176-183 restaure previousStatusHolder (IS_PURCHASE_CONFIRM) et le DIFFUSE.

T7 - Serveur A : ItemStatusListener cherche l'item dans LISTED, EXPIRED puis PURCHASED, le TROUVE dans PURCHASED et fait setStatus(IS_PURCHASE_CONFIRM) sans verifier que oldStatus du message (IS_BEING_PURCHASED) correspond au statut local (PURCHASED).

T8 - Acheteur1 ouvre son onglet purchased : l'item apparait mais chaque clic est refuse par RemoveService:172. ITEM_SHOW ayant ete purge a l'achat (ZAuctionManager:896), ConfirmHelper ne peut rien restaurer.

**Impact** — L'acheteur a paye et ne peut plus jamais recuperer son item jusqu'au redemarrage du serveur (ItemLoaderUtils rederive le statut depuis storage_type au boot). Si purchase-expiration > 0, ExpireService.processExpiredItems (branche else, l.230) passe la ligne en DELETED sans rien rendre : perte definitive de l'item ET de l'argent. Le meme mecanisme casse EXPIRED (RemoveService:134 exige REMOVED) et SELLING (:96 exige AVAILABLE).

**Précision apportée par la contre-expertise**

Le constat est reel et la correction proposee est valide (le CAS sur oldStatus suffit deja a bloquer les deux scenarios ; la garde "transitoire hors LISTED" est une seconde barriere utile). Deux precisions rendent la description plus exacte et la severite mieux justifiee :

1) DECLENCHEUR BEAUCOUP PLUS SIMPLE QUE LA COURSE A DEUX ACHETEURS. Pas besoin d'un second acheteur ni de la revalidation DB de PurchaseService. ConfirmPurchaseButton.java:17 fait `super(plugin, ItemStatus.IS_PURCHASE_CONFIRM, ItemStatus.AVAILABLE)`, et ConfirmHelper.java:41-52 (onInventoryClose) / :70-80 (onBackClick) rediffusent cette transition depuis la reference stale de ITEM_SHOW :

if (item.getStatus() == this.previous) {

...

item.setStatus(this.next);

this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, this.previous, this.next)

Or cet objet SURVIT au ItemBoughtListener : celui-ci fait `manager.removeItem(StorageType.LISTED, id)` (retrait du magasin par id) et re-ajoute un objet NEUF en PURCHASED, sans jamais toucher au cache ITEM_SHOW du joueur. Scenario minimal : T0 item #42 LISTED sur A et B ; T1 joueur X sur B clique (ListedItemsButton:130-134 diffuse AVAILABLE->IS_PURCHASE_CONFIRM, ouvre le GUI, ITEM_SHOW = objet LISTED) ; T2 joueur Y achete reellement sur A (ZAuctionManager:864/892 -> PURCHASED, notifyItemBought) ; T3 B retire de LISTED, l'objet de X reste a IS_PURCHASE_CONFIRM ; T4 X FERME simplement le GUI sans acheter -> ConfirmHelper:41-52 diffuse (IS_PURCHASE_CONFIRM -> AVAILABLE) ; T5 A trouve #42 dans PURCHASED et fait setStatus(AVAILABLE). Un acheteur + un simple curieux qui ouvre/ferme un menu suffisent.

2) IMPACT SUPPLEMENTAIRE NON MENTIONNE : ITEM FANTOME REVENDU EN VITRINE. Quand le statut rediffuse est AVAILABLE (cas ci-dessus, le plus frequent), ItemStatusListener:59-61 declenche en plus :

} else if (message.newStatus() == ItemStatus.AVAILABLE) {

manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING);

manager.updateListedItems(item, true, null);

`updateListedItems(item, true, null)` (ZAuctionManager:938-975) reinjecte l'item DEJA VENDU dans tous les inventaires d'hotel des ventes ouverts sur A (listedItemsButton.updateInventory(..., added=true, ...)). Ce n'est pas une reinsertion dans le magasin LISTED (le store n'est pas modifie, un rafraichissement complet le fait disparaitre), mais c'est un affichage fantome cliquable d'un item vendu, en plus de l'achat devenu irreclamable via RemoveService:172.

Nuance mineure sur l'impact annonce : la suppression par ExpireService.java:227-236 (branche else, DELETED sans restitution) surviendrait de toute facon pour un item achete non reclame passe purchase-expiration ; c'est bien le bug qui rend la reclamation impossible et donc la perte certaine, mais la ligne 230 n'est pas elle-meme le defaut.

**Correctif**

```java
Faire de l'application du statut un compare-and-swap et interdire les statuts transitoires hors du magasin LISTED. Remplacer le bloc l.43-54 par :

    Item item = null; StorageType found = null;
    for (StorageType st : SEARCHABLE_STORAGE_TYPES) {
        var opt = manager.getItems(st).stream().filter(e -> e.getId() == id).findFirst();
        if (opt.isPresent()) { item = opt.get(); found = st; break; }
    }
    if (item == null) return;
    if (message.oldStatus() != null && item.getStatus() != message.oldStatus()) {
        plugin.debug("Stale status message for " + id + ": local=" + item.getStatus() + ", expected=" + message.oldStatus());
        return;
    }
    boolean isTransient = message.newStatus() == ItemStatus.IS_PURCHASE_CONFIRM || message.newStatus() == ItemStatus.IS_REMOVE_CONFIRM
            || message.newStatus() == ItemStatus.IS_BEING_PURCHASED || message.newStatus() == ItemStatus.IS_BEING_REMOVED
            || message.newStatus() == ItemStatus.AVAILABLE;
    if (isTransient && found != StorageType.LISTED) return;
    item.setStatus(message.newStatus());
```

---

<a id="c-005"></a>

### `C-005` — L'argent et l'item sont livrés avant que l'écriture DB soit durable : un échec ou un crash duplique l'item et paie deux fois

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:883`
- **Catégorie** : Duplication d’item — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZAuctionManager.java:876-918 (vérifié) — l'UPDATE est SOUMIS, la remise s'exécute immédiatement, sans attendre :

        if (purchasedConfiguration.giveItem()) {

            updateFuture = storageManager.updateItem(auctionItem, StorageType.DELETED);
            giveItem(player, auctionItem);

        } else {
            ...
            addItem(StorageType.PURCHASED, auctionItem);
            updateFuture = storageManager.updateItem(auctionItem, StorageType.PURCHASED);
        }
        ...
        return updateFuture;

Et l'argent bouge ~80 lignes AVANT toute mutation d'item : ZAuctionManager.java:802 `auctionEconomy.withdraw(...)`, :823 `deposit(seller...)`, alors que removeItem(LISTED) n'arrive qu'à :875.

ZStorageManager.java:156-158 : `CompletableFuture.runAsync(() -> with(ItemRepository.class).updateItem(item, storageType), this.plugin.getExecutorService());`

Même couple non séquencé aux 5 autres sites : ZAuctionManager.java:522-523, :568-569, :602-603, :636-637, :693-696.

L'échec DB remonte bien : Sarah UpdateRequest.java:64 lève DatabaseException (RuntimeException) que Repository.update — qui n'attrape que SQLException — laisse traverser.
```

**Chronologie**

T0 — Serveur A : P1 confirme l'achat de l'item 42 (give-item = true). Verrou pris, revalidation OK.

T1 — ZAuctionManager:802 : P1 est débité.

T2 — ZAuctionManager:883 : l'UPDATE part sur le pool de 4 threads. Ligne 884 : giveItem(P1) s'exécute IMMÉDIATEMENT — P1 a physiquement l'item.

T3 — MySQL échoue (deadlock InnoDB, lock wait timeout, pool Hikari épuisé) OU le serveur meurt (kill -9, OOM). La ligne items id=42 reste storage_type='LISTED', buyer_unique_id NULL.

T4 — Comme updateFuture a échoué, PurchaseService:135 `notifyItemBought` n'est JAMAIS appelé : aucun HSET state=SOLD, aucun ItemBoughtMessage publié. Le bloc exceptionally:156 se contente d'appeler unlockItem, dont UNLOCK_SCRIPT remet state='AVAILABLE'.

T5 — Les serveurs B, C, D ont toujours l'item 42 dans leur store LISTED et l'affichent normalement. P2 clique et confirme.

T6 — Serveur B : checkAvailability → AVAILABLE → true. lockItem → OK. selectItem(42) → ligne LISTED, buyer NULL → la revalidation autoritaire PASSE. B débite P2 et lui donne l'item.

Résultat : deux exemplaires, deux paiements encaissés, le vendeur crédité deux fois.

Variante give-item = false : à T2 l'item est mis dans le store PURCHASED de A en mémoire seulement, la ligne reste LISTED. Au redémarrage, AuctionLoader la recharge comme LISTED (ItemRepository.select() ne filtre que DELETED) : l'item retourne en vente alors que P1 l'a payé.

Variante crash pur : A meurt entre T2 et T3. La ligne reste LISTED, Redis garde state='LOCKED' avec auction:lock:42 vivant. 30 s plus tard le TTL expire, LOCK_SCRIPT réacquiert (branche de récupération de verrou périmé), selectItem confirme LISTED/buyer NULL, un second acheteur paie et reçoit le même item.

**Impact** — Duplication d'item et d'argent sur tout incident base transitoire ou tout crash pendant un achat ou un retrait, y compris en mono-serveur (l'item revient à la vente après redémarrage). C'est le seul point du flux où la garantie « un item = une remise » est confiée à un future non attendu. Le protocole actuel traite l'échec DB comme un simple échec de notification alors que l'argent et l'item ont déjà bougé de façon irréversible.

**Précision apportée par la contre-expertise**

Le constat est exact ; deux precisions qui le renforcent plutot qu'elles ne le corrigent.

(a) La formulation « le bloc exceptionally se contente d'appeler unlockItem » est correcte mais merite sa justification : la branche de restauration de statut PurchaseService.java:176 `if (item.getStatus() == ItemStatus.IS_BEING_PURCHASED)` est FAUSSE a ce moment, parce que ZAuctionManager.java:864 a deja execute `auctionItem.setStatus(ItemStatus.PURCHASED);`. Aucune compensation n'a donc lieu : ni remboursement de l'acheteur, ni reprise de l'item remis, ni remise de l'item dans le store LISTED du serveur A (le `removeItem(StorageType.LISTED, auctionItem)` de :875 n'est jamais annule).

(b) Consequence supplementaire non mentionnee : meme en MONO-SERVEUR, apres l'echec le serveur A a perdu l'item de son store LISTED en memoire alors que la ligne DB est restee 'LISTED'. Au prochain redemarrage, AuctionLoader.java:47 `this.storageManager.with(ItemRepository.class).select()` la recharge (filtre `!= DELETED` uniquement) et ItemLoaderUtils.java:93 la replace en vente : l'item revient au marche alors que l'acheteur l'a deja recu et paye. La duplication ne necessite donc ni Redis ni multi-serveurs.

(c) Le site admin (ZAuctionManager.java:693) est le pire des six : le CompletableFuture n'y est meme pas capture (`this.plugin.getStorageManager().updateItem(item, StorageType.DELETED);` sur une ligne isolee), donc l'echec DB est totalement invisible — aucun `exceptionally` de la chaine ne le verra, et clusterBridge.unlockItem (:703) sera appele comme si tout s'etait bien passe.

Severite critique confirmee : c'est le seul endroit du flux ou la garantie « un item = une remise » repose sur un future non attendu, et l'argent comme l'item ont deja bouge de facon irreversible au moment ou l'ecriture est seulement soumise.

**Correctif**

```java
Inverser l'ordre : la base valide AVANT tout mouvement.

1) Commit d'abord, dans purchaseAuctionItem :

return storageManager.updateItem(auctionItem, StorageType.LISTED, target)   // CAS, cf. constat précédent
    .thenCompose(rows -> {
        if (rows != 1) {                                   // un autre serveur a déjà vendu
            message(player, Message.ITEM_NOT_AVAILABLE);
            removeItem(StorageType.LISTED, auctionItem);
            return failedFuture(new IllegalStateException("CAS lost for item " + auctionItem.getId()));
        }
        // 2) seulement maintenant : withdraw acheteur, deposit/PENDING vendeur, mutations mémoire
        ...
        // 3) puis la remise, sur le thread de l'entité
        var given = new CompletableFuture<Void>();
        this.plugin.getScheduler().runAtEntity(player, w -> {
            if (!player.isOnline()) { given.complete(null); return; }
            giveItem(player, auctionItem);
            player.saveData();                              // ferme la fenêtre d'autosave
            given.complete(null);
        });
        return given;
    });

2) Si le withdraw échoue après un CAS réussi, compenser : `UPDATE items SET storage_type='LISTED', buyer_unique_id=NULL WHERE id=? AND buyer_unique_id=?`.

3) Appliquer strictement le même patron aux 5 autres sites (removeListedItem:522, removeSellingItem:568, removeExpiredItem:602, removePurchasedItem:636, adminRemoveItem:693), avec rollback mémoire (`addItem(source, item)`) si l'écriture échoue.

4) Grouper l'UPDATE items et les 2 INSERT transactions dans une transaction Sarah (`DatabaseConnection.beginTransaction()`, jamais utilisé dans le projet), pour que la dette envers le vendeur soit atomique avec la vente.
```

---

<a id="c-006"></a>

### `C-006` — La revalidation d'achat sous verrou ne teste pas storage_type : un item déjà passé en EXPIRED reste achetable

- [ ] **Corrigé**
- **Fichier** : `services/PurchaseService.java:121`
- **Catégorie** : Duplication d’item — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
PurchaseService.java:119-127 (vérifié) :

    .thenCompose(dbItem -> {
        if (dbItem == null || dbItem.getBuyerUniqueId() != null) {
            inventoryManager.updateInventory(player);
            resultHolder.set(PurchaseResult.failure("Item already sold", PurchaseFailReason.ITEM_NOT_AVAILABLE));
            return failedFuture(new IllegalStateException("Item already sold on another server"));
        }
        return auctionEconomy.has(player.getUniqueId(), requiredBalance);
    });

Or ItemRepository.select(int) ne filtre que DELETED :
    schema.where("id", id);
    schema.where("storage_type", "!=", StorageType.DELETED.name());

Une ligne EXPIRED est donc renvoyée non nulle avec buyer_unique_id NULL : les deux conditions du test sont fausses et l'achat continue.
ItemLoaderUtils:43-48 fournit pourtant déjà le test manquant (LISTED -> ItemStatus.AVAILABLE).
```

**Chronologie**

Config par défaut `action.remove-listed-item.give-item: false` : un retrait vendeur envoie l'item vers EXPIRED, pas DELETED.

T0 — Serveur B : le vendeur S retire son item #42. RemoveService:63 destination = EXPIRED → ZAuctionManager:532 `updateItem(item, EXPIRED)` → la ligne DB devient 'EXPIRED', l'item apparaît dans l'onglet « objets expirés » de S. Puis RedisAuctionClusterBridge:354 écrit state='REMOVED' et publie ItemRemovedMessage.

T1 — Serveur A : le message est perdu (subscriber en reconnexion, backoff jusqu'à 60 s ; ou le publish a échoué et ZAuctionHouseRedis.sendMessage:279-281 avale l'exception en rendant un future complété normalement). L'item 42 est TOUJOURS dans le store LISTED de A, statut AVAILABLE, affiché dans l'HDV.

T2 — Serveur A : l'acheteur P clique et confirme.

- `item.isExpired()` (PurchaseService:62) : faux, l'objet d'A a l'expiredAt d'origine.

- `getStatus() != IS_PURCHASE_CONFIRM` (:68) : passe, A n'a rien reçu.

- checkAvailability : state='REMOVED' → TRUE (seul DELETED bloque).

- lockItem : LOCK_SCRIPT ne refuse que DELETED → verrou accordé.

- selectItem(42) : ligne EXPIRED renvoyée, buyer NULL → LA GARDE PASSE.

T3 — L'achat s'exécute : withdraw sur P, crédit/PENDING pour S, `updateItem(auctionItem, PURCHASED)` écrase en aveugle la ligne EXPIRED, giveItem(P).

T4 — S ouvre son onglet expirés (l'item y est toujours) et le réclame : RemoveService:134 exige status == REMOVED → vrai, aucune revalidation → giveItem(S).

Résultat : l'acheteur a l'item ET a payé, le vendeur a l'item ET a été payé. Item dupliqué et monnaie créée.

Variante sans perte de message : une dérive d'horloge entre serveurs (aucune source de temps partagée) suffit — A expire l'item pendant que B le juge encore valide.

**Impact** — La revalidation autoritaire sous verrou est la SEULE protection restante une fois le verrou pris ; elle laisse passer l'état EXPIRED, qui est précisément la destination par défaut d'un retrait vendeur. Duplication d'item plus création de monnaie, sans timing exotique.

**Précision apportée par la contre-expertise**

Le constat est exact ; deux precisions le renforcent (aucune ne l'affaiblit) :

(a) AUCUNE PERTE DE MESSAGE N'EST NECESSAIRE. Le scenario T1 (« le message est perdu ») n'est qu'un elargissement de la fenetre, pas une condition. Verifie dans `RemoveService.executeRemoval:193-265` : le verrou Redis est detenu pendant toute la suppression (`unlockAndCompleteStep` est la derniere etape), et `RedisAuctionClusterBridge.removeItem:350-364` positionne state='REMOVED' AVANT l'unlock — l'`UNLOCK_SCRIPT:86-89` ne remet AVAILABLE que si l'etat vaut encore 'LOCKED', donc REMOVED persiste. Une fois B deverrouille, il reste une fenetre de plusieurs dizaines/centaines de ms pendant laquelle le `ItemRemovedMessage` est en vol et ou `ItemRemovedListener.onMessage:34` n'a pas encore execute son `runNextTick`. Un clic de P sur A dans cette fenetre suffit : checkAvailability(REMOVED)=true, LOCK_SCRIPT accorde, selectItem renvoie la ligne EXPIRED sans buyer -> achat. La duplication est donc atteignable par simple concurrence normale, sans panne Redis ni derive d'horloge.

(b) L'ETAPE T4 FONCTIONNE MEME SI LE SERVEUR B RECOIT NORMALEMENT LE ItemBoughtMessage. `zAuctionHouse Redis/.../listener/listeners/ItemBoughtListener.java:47-50` :

```

// The ghost only ever lives in LISTED (that is what the selling tab reads).

// Removing only LISTED avoids clobbering a legitimate PURCHASED/EXPIRED entry.

manager.removeItem(StorageType.LISTED, id);

```

L'entree du store EXPIRED de B n'est donc jamais retiree ; pire, l'etape 2 (:96-108) relit la ligne desormais PURCHASED et fait `manager.addItem(StorageType.PURCHASED, item)`, laissant B avec DEUX entrees memoire pour l'id 42 (une EXPIRED statut REMOVED, une PURCHASED). Le vendeur clique celle de l'onglet expires, `RemoveService.removeExpiredItem:134` (`status == REMOVED`) passe, `ZAuctionManager.removeExpiredItem` fait `giveItem(player, item)` + `updateItem(item, DELETED)` sans relecture. L'acheteur a l'item et a paye, le vendeur a l'item et a ete credite.

La correction proposee (test `dbItem.getStatus() != ItemStatus.AVAILABLE` + resynchronisation de `expiredAt` + compare-and-set sur l'UPDATE final) est pertinente et suffisante pour la fenetre d'achat. Il faut y ajouter le pendant manquant cote reclamation : une revalidation DB sous verrou dans `RemoveService.removeExpiredItem` / `removePurchasedItem` (aujourd'hui il n'y en a aucune), et etendre la clause `where storage_type` de `ItemRepository.createUpdateSchema:70-88` aux transitions PURCHASED et DELETED, pas seulement EXPIRED.

**Correctif**

```java
Rendre la revalidation exhaustive sur l'état du stock :

.thenCompose(dbItem -> {
    if (dbItem == null
            || dbItem.getBuyerUniqueId() != null
            || dbItem.getStatus() != ItemStatus.AVAILABLE) {   // AVAILABLE <=> storage_type = LISTED
        // reconcilier la mémoire locale : la ligne n'est plus LISTED
        auctionManager.removeItem(StorageType.LISTED, item.getId());
        auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
        inventoryManager.updateInventory(player);
        resultHolder.set(PurchaseResult.failure("Item no longer listed", PurchaseFailReason.ITEM_NOT_AVAILABLE));
        return failedFuture(new IllegalStateException("Item no longer LISTED on the database"));
    }
    // resynchroniser l'objet local sur la vérité DB avant de facturer
    item.setExpiredAt(dbItem.getExpiredAt());
    if (item.isExpired()) {
        resultHolder.set(PurchaseResult.failure("Item expired", PurchaseFailReason.ITEM_EXPIRED));
        return failedFuture(new IllegalStateException("Item expired"));
    }
    return auctionEconomy.has(player.getUniqueId(), requiredBalance);
});

À combiner impérativement avec le compare-and-set de l'UPDATE final (`where storage_type='LISTED'` + `whereNull("buyer_unique_id")` pour les cibles PURCHASED/DELETED issues d'un achat) : la revalidation ferme la fenêtre de lecture, le CAS ferme la fenêtre d'écriture.
```

---

<a id="c-007"></a>

### `C-007` — Le bail Redis de 30 s n'est jamais renouvelé et la section critique n'a aucun timeout : le verrou expire en plein commit

- [ ] **Corrigé**
- **Fichier** : `services/PurchaseService.java:134`
- **Catégorie** : Duplication d’item — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
PurchaseService.java:126-138 (vérifié) — toutes les étapes ont un .orTimeout SAUF le contrôle de solde et l'achat lui-même :

        return auctionEconomy.has(player.getUniqueId(), requiredBalance);   // ligne 126 : AUCUN orTimeout
    });

}).thenCompose(hasMoney -> {
    var token = tokenHolder.get();
    if (hasMoney) {
        return auctionManager.purchaseItem(player, item)                    // ligne 134 : AUCUN orTimeout
                .thenCompose(v -> clusterBridge.notifyItemBought(player, item)

RedisAuctionClusterBridge.java:188 — TTL fixe, posé une seule fois :
    long ttlMs = lockTtl != null ? lockTtl.toMillis() : 30000;

`grep -rn "pexpire\|renew\|extendLock\|watchdog"` sur le package redis : aucun résultat. Le contrat AuctionClusterBridge.java:35 ne rend jamais la date d'expiration du bail et n'offre aucun moyen de vérifier qu'on le détient encore.

Appels bloquants prouvés à l'intérieur de la section critique :
  ZAuctionManager.java:784 `storageManager.getPlayerName(...)` -> ZStorageManager.java:219-221, JDBC synchrone
  ZAuctionManager.java:802/823 `withdraw`/`deposit` -> ZAuctionEconomy.java:87-94, appels 100 % synchrones du provider
Et ZStorageManager.java:176-177 `CompletableFuture.supplyAsync(() -> {` SANS executor bascule toute la chaîne sur ForkJoinPool.commonPool.
```

**Chronologie**

T0 — Serveur A : P1 confirme l'achat de l'item 42. lockItem pose auction:lock:42 avec PX 30000.

T1 — A franchit selectItem (protégé, 5 s max) puis entre dans `auctionEconomy.has` (non protégé) puis dans `auctionManager.purchaseItem` (non protégé). À l'intérieur : getPlayerName = requête JDBC synchrone sur commonPool, puis withdraw = appel synchrone au provider d'économie (Vault adossé à MySQL, CoinsEngine, PlayerPoints).

T2 — Le pool Hikari est saturé ou la base attend un verrou InnoDB : getPlayerName met 20 s, withdraw 15 s. Total 35 s > 30 s. auction:lock:42 EXPIRE alors que A est entre le débit de l'acheteur (:802) et l'écriture de la ligne (:883/:893).

T3 — Serveur B, même item 42 (toujours LISTED en base, toujours affiché) : checkAvailability voit state=LOCKED mais EXISTS=0 → TRUE (branche de récupération volontaire du verrou périmé, commentée l.51-57 du LOCK_SCRIPT) ; LOCK_SCRIPT tombe sur le SET NX → acquisition accordée ; selectItem confirme LISTED/buyer NULL → B commit son propre achat.

T4 — A termine, écrit sa ligne, puis unlockItem détruit le verrou de B (cf. jeton non discriminant).

Deux acheteurs ont payé, deux exemplaires existent.

Le commentaire du script assume que « lock key expire => le détenteur a crashé ». C'est faux : rien ne prolonge le bail et la section critique n'a aucune borne de durée. Il suffit d'un pic de latence base ou économie de plus de lock-ttl-seconds, situation courante en heure de pointe.

**Impact** — Le verrou distribué ne couvre pas réellement la transaction qu'il est censé protéger. Combiné à la collision de jetons, c'est le chemin principal de duplication sous charge : le service ne peut même pas savoir qu'il a perdu le droit d'écrire.

**Précision apportée par la contre-expertise**

Le bail Redis (lock-ttl-seconds, 30 s par defaut) n'est jamais prolonge et la section critique contient des appels SYNCHRONES BLOQUANTS qu'aucun `.orTimeout` ne peut borner : `auctionEconomy.has` renvoie un future deja complete (ZAuctionEconomy `get` = `completedFuture(currencyProvider.getBalance(...))`) et `auctionManager.purchaseItem` execute tout son corps en ligne — getPlayerName JDBC synchrone (ZAuctionManager:784 -> ZStorageManager:219), withdraw (:802) et deposit (:823) — avant de ne retourner que `updateFuture` (:918). Le probleme n'est donc pas un `.orTimeout` oublie (PurchaseService:126 et :134) mais une section critique structurellement non bornable, executee de surcroit sur ForkJoinPool.commonPool (ZStorageManager:177 `supplyAsync` sans executor). Si cette section depasse le TTL, `auction:lock:<id>` expire : un second serveur voit `checkAvailability` renvoyer true (RedisAuctionClusterBridge:171 `!jedis.exists(lockKey(item))`), le LOCK_SCRIPT re-acquiert par la branche de recuperation de verrou perime (l.55-57), sa re-validation en base passe (la ligne est encore LISTED/buyer NULL) et il commit un second achat. Deux facteurs aggravants confirmes en lecture : (a) aucun compare-and-set en base pour la transition d'achat — ItemRepository.createUpdateSchema (l.71-92) ne pose la clause `where storage_type = LISTED` que pour EXPIRED, l'UPDATE PURCHASED/DELETED est un simple `where id = ?` qui ecrase le commit precedent ; (b) LockToken.of = `"item:" + id`, identique sur tous les serveurs, donc le controle de propriete du UNLOCK_SCRIPT est inoperant et le unlock du premier serveur detruit le verrou du second. Le verrou distribue est l'unique garantie, et il n'est ni renouvele, ni verifiable, ni double d'une garde SQL.

**Correctif**

```lua
1) Ajouter au contrat de quoi prolonger et vérifier le bail :

CompletableFuture<Boolean> renewLock(Item item, LockToken token, StorageType st, Duration extension);
CompletableFuture<Boolean> isHeldBy(Item item, LockToken token);

Implémentation Redis de renewLock (Lua atomique, même forme qu'UNLOCK_SCRIPT) :
local t = redis.call('GET', KEYS[1])
if t ~= ARGV[1] then return 0 end
redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2]))
return 1

2) Watchdog : tant que le token est détenu, `getScheduler().runTimerAsync(() -> clusterBridge.renewLock(item, token, st, lockTtl), ttl/3, ttl/3, ...)`, annulé dans le whenComplete terminal de la chaîne.

3) Borner la section critique et vérifier la détention juste avant l'écriture engageante :

return auctionEconomy.has(player.getUniqueId(), requiredBalance)
        .orTimeout(performanceConfig.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS);
...
if (hasMoney) {
    return clusterBridge.isHeldBy(item, token)
        .thenCompose(stillMine -> {
            if (!stillMine) {                    // bail perdu : rien n'a été débité, on abandonne
                resultHolder.set(PurchaseResult.failure("Lock lost", PurchaseFailReason.LOCK_FAILED));
                return failedFuture(new IllegalStateException("Lease expired before commit"));
            }
            return auctionManager.purchaseItem(player, item)
                    .orTimeout(performanceConfig.notifyItemActionTimeoutMs() * 4L, TimeUnit.MILLISECONDS);
        })...
}

4) Sortir les appels bloquants de la section critique : résoudre le nom du vendeur AVANT lockItem, et exécuter withdraw/deposit sur l'executor dédié du plugin plutôt que sur commonPool.

La vraie protection de fond reste le compare-and-set en base : le verrou doit être une optimisation de contention, jamais l'unique garantie.
```

---

<a id="c-008"></a>

### `C-008` — Le jeton de verrou est déterministe ("item:<id>") : la vérification de propriété est inopérante et un serveur libère le verrou d'un autre

- [ ] **Corrigé**
- **Fichier** : `API/api/cluster/LockToken.java:34`
- **Catégorie** : Duplication d’item — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```lua
LockToken.java (fichier lu en entier) :

    public static LockToken of(Item auctionItem) {
        return new LockToken("item:" + auctionItem.getId());
    }

Aucun UUID de serveur, aucun nonce, aucun horodatage : pour l'item 42, TOUS les serveurs produisent exactement "item:42".

RedisAuctionClusterBridge.java:181-187 — le paramètre lockerId du contrat n'est JAMAIS lu :

    public CompletableFuture<LockToken> lockItem(Item item, UUID lockerId, StorageType storageType) {
        return CompletableFuture.supplyAsync(() -> {
            ...
                LockToken token = LockToken.of(item);

UNLOCK_SCRIPT (vérifié l.74-91) valide pourtant l'appartenance sur cette valeur :

    local currentToken = redis.call('HGET', itemKey, 'lock')
    if currentToken ~= tokenValue then
        return 0
    end
    redis.call('DEL', lockKey)

LocalAuctionClusterBridge.java:34-37 (fichier lu) ne vérifie même pas le jeton : `itemLocks.remove(item.getId());`
```

**Chronologie**

T0 — Serveur A : achat de l'item 42. LOCK_SCRIPT pose SET auction:lock:42 "item:42" NX PX 30000, HSET state=LOCKED lock="item:42". A entre dans purchaseAuctionItem, qui n'est protégé par AUCUN orTimeout (PurchaseService:134).

T1 (T0+31s) — A est toujours dedans (GC, getPlayerName JDBC bloquant ZAuctionManager:784, provider d'économie lent, runAtEntity en attente d'un tick). auction:lock:42 a expiré par TTL, mais le champ state vaut toujours LOCKED.

T2 — Serveur B : un second joueur confirme l'achat du même item 42. checkAvailability : state == LOCKED mais EXISTS == 0 → true. LOCK_SCRIPT : même condition → SET NX → B acquiert, et écrit lock="item:42", VALEUR IDENTIQUE à celle de A.

T3 — B exécute selectItem(42) : A n'a pas encore atteint :883/:893, la ligne est encore LISTED avec buyer NULL → B passe la revalidation et achète.

T4 — A termine et appelle unlockItem avec son jeton "item:42". UNLOCK_SCRIPT : HGET lock == "item:42" == le jeton de A → la garde de propriété PASSE. A exécute DEL auction:lock:42, c'est-à-dire qu'il SUPPRIME LE VERROU DE B, fait HDEL lock, et remet state='AVAILABLE'.

T5 — Serveur C peut immédiatement acquérir le verrou pendant que B est encore en pleine section critique, et refaire la même chose.

RÉSULTAT : l'item 42 est donné à deux (voire trois) acheteurs, le vendeur est crédité autant de fois, et les UPDATE finaux s'écrasent silencieusement.

Le même vol se produit SANS expiration de TTL : un unlock retardataire (timeout sur unlockItem alors que la commande a abouti, puis second unlock émis par releaseLockOnError, RemoveService:293-300) libère le verrou fraîchement acquis par un autre nœud.

**Impact** — La garde de propriété du verrou — seul mécanisme empêchant un retardataire de casser l'exclusion mutuelle — ne remplit aucune fonction : elle est vraie pour tout le monde. C'est le défaut fondamental de l'implémentation du verrou distribué, et l'exclusion mutuelle Redis est la SEULE barrière inter-serveurs (aucun SELECT ... FOR UPDATE nulle part).

**Précision apportée par la contre-expertise**

Le constat est exact sur le fond ; deux precisions sur la HIERARCHIE des causes et deux corrections sur la REMEDIATION proposee.

A) Hierarchie (a reformuler pour ne pas fusionner deux defauts) : le jeton deterministe n'est pas a lui seul generateur de deux detenteurs — il SUPPRIME LE FENCING qui devrait rattraper les deux fenetres ou deux detenteurs apparaissent :

- fenetre 1 (TTL) : LOCK_SCRIPT:51-57 autorise volontairement la re-acquisition sur etat LOCKED + cle expiree, alors que PurchaseService:134 ne borne pas la section critique ;

- fenetre 2 (SANS TTL, la plus directe) : double-unlock PurchaseService:137-138 puis :167-173 (et RemoveService:293-300) — l'unlock retardataire libere le verrou fraichement acquis par un autre noeud car les deux jetons sont litteralement egaux.

Avec un jeton unique, la fenetre 2 est entierement fermee et la fenetre 1 cesse de se propager en cascade (le T4/T5 du scenario disparait). Le scenario decrit reste donc valide, mais son point de rupture est l'etape T4, pas T2.

B) Remediation, point 2 : il n'y a QU'UN SEUL `LockToken.of` dans RedisAuctionClusterBridge (`grep -c` = 1, ligne 187) ; le chemin de repli reutilise cette meme variable `token` (l.230/236). La correction est donc une seule ligne (l.187), pas deux — il n'y a rien a changer a la l.226.

C) Remediation, point 5 : `itemLocks.remove(item.getId(), buyerId)` NE COMPILE PAS. La signature reelle est `unlockItem(Item item, LockToken lockToken, StorageType storageType)` (LocalAuctionClusterBridge.java:35) — aucun `buyerId` n'y est disponible. Il faut soit stocker le jeton dans la map (`ConcurrentHashMap<Integer, String>` alimentee avec `token.value()` et libere par `itemLocks.remove(item.getId(), lockToken.value())`), soit faire porter le proprietaire par le jeton et le comparer explicitement.

Severite maintenue a critique : la garde de propriete est inoperante a 100 % (pas partiellement), l'issue est une duplication d'item et d'argent, et aucune barriere de repli n'existe (ni verrou SQL, ni UPDATE conditionnel sur buyer_unique_id, cf. ItemRepository:71-92).

**Correctif**

```lua
1) Rendre le jeton unique par acquisition ET par serveur — aucune autre signature n'est à changer, le token est déjà transporté de bout en bout :

// API/.../api/cluster/LockToken.java
public static LockToken issue(Item item, UUID ownerId) {
    return new LockToken("item:" + item.getId() + ":" + ownerId + ":" + UUID.randomUUID());
}

2) Dans RedisAuctionClusterBridge.lockItem (l.187 et le repli l.226), remplacer `LockToken.of(item)` par `LockToken.issue(item, lockerId)` : le paramètre lockerId devient enfin utilisé, et UNLOCK_SCRIPT redevient discriminant sans aucune modification du Lua. Le test `LockToken.noop().value().equals(token.value())` reste valide.

3) Ne PAS conserver `LockToken.of(Item)` comme méthode publique : elle rend le jeton devinable par tout code appelant l'API.

4) Durcir UNLOCK_SCRIPT en validant aussi la valeur de la clé de verrou, pas seulement le champ du hash :

local keyToken = redis.call('GET', lockKey)
if keyToken and keyToken ~= tokenValue then return 0 end
local hashToken = redis.call('HGET', itemKey, 'lock')
if hashToken ~= tokenValue then return 0 end

5) Corriger symétriquement LocalAuctionClusterBridge.unlockItem : `itemLocks.remove(item.getId(), buyerId)` (variante conditionnelle à deux arguments).
```

---

<a id="c-009"></a>

### `C-009` — RemoveService ne revalide JAMAIS la base sous verrou, contrairement à PurchaseService : tout fantôme mémoire devient un item gratuit

- [ ] **Corrigé**
- **Fichier** : `services/RemoveService.java:200`
- **Catégorie** : Duplication d’item — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
RemoveService.executeRemoval (vérifié l.200) — la chaîne complète, sans aucun selectItem :

        return checkAvailabilityStep(...).thenCompose(available -> acquireLockStep(...)).thenCompose(token -> changeStatusAndNotifyStep(...)).thenCompose(v -> executeLocalRemovalStep(...)).thenCompose(v -> unlockAndCompleteStep(...)).exceptionally(throwable -> handleRemovalException(...));

`grep -c selectItem RemoveService.java` => 0.

Alors que PurchaseService.java:118-125 le fait explicitement, et que son commentaire décrit exactement le danger :

    // Authoritative re-validation UNDER LOCK before charging anything.
    // The in-memory item may be stale across servers; in particular, a
    // purchase that completed on another server but crashed before its SOLD
    // broadcast leaves a stale lock that lock recovery would let us re-acquire.

Toutes les gardes de removeListedItem (:48-60), removeSellingItem (:84-101), removeExpiredItem (:122-139) et removePurchasedItem (:160-177) portent uniquement sur `item.getStatus()`, un champ purement mémoire (ItemStatus n'a aucune colonne dans %prefix%items) donc invisible des autres serveurs.
```

**Chronologie**

Config `action.purchased-item.give-item: false` (défaut). Item #42, vendeur V, acheteur A.

T0 — Serveur A : A confirme. PurchaseService:92 lockItem OK ; :118 selectItem revalide (LISTED, buyer null) → OK.

T1 — Serveur A : ZAuctionManager:802 withdraw, :863 setBuyer, :875 removeItem(LISTED), :893 updateItem(PURCHASED) → la ligne DB vaut storage_type='PURCHASED', buyer_unique_id=A.

T2 — Serveur A : notifyItemBought → HSET auction:item:42 state='SOLD' + publish. unlockItem → DEL auction:lock:42 (l'état reste SOLD car != 'LOCKED').

T3 — Serveur B : le vendeur V a son inventaire /ah selling déjà affiché ; son objet Item a encore status=AVAILABLE (les listeners Redis ne modifient jamais le statut, ils font seulement removeItem). Il clique.

T4 — RemoveService.removeSellingItem:84-101 : isExpired() faux, getStatus() == AVAILABLE → toutes les gardes passent.

T5 — checkAvailabilityStep : RedisAuctionClusterBridge:168 `if (!STATE_LOCKED.equals(state)) return true;` avec state='SOLD' → TRUE.

T6 — acquireLockStep : LOCK_SCRIPT, 'SOLD' n'est ni 'DELETED' ni 'LOCKED', le SET NX sur auction:lock:42 (supprimé en T2) réussit → jeton valide.

T7 — executeLocalRemovalStep → ZAuctionManager:569 giveItem(V, item) : LE VENDEUR REÇOIT PHYSIQUEMENT L'ITEM ; :568 updateItem(DELETED) écrase le PURCHASED posé en T1.

T8 — L'acheteur A ouvre sa liste 'purchased' sur A (l'item y est toujours en mémoire), clique → ZAuctionManager:637 giveItem → IL REÇOIT UNE SECONDE COPIE.

Résultat : 1 item → 2 items, l'argent est parti chez le vendeur.

Aucun message pub/sub n'a besoin d'être perdu : il suffit que le clic de retrait parte avant que B n'ait rafraîchi l'objet Item — soit toute la durée pendant laquelle un inventaire est ouvert.

**Impact** — Vecteur de duplication le plus direct du plugin. L'asymétrie est totale : le chemin d'achat (un point d'entrée) est protégé, le chemin de retrait (4 méthodes publiques + 3 boutons « tout récupérer », de loin le plus cliqué) ne l'est pas du tout, alors que c'est lui qui rend physiquement l'item au joueur. La protection existe déjà dans le projet, elle n'a simplement pas été portée.

**Précision apportée par la contre-expertise**

Description exacte a substituer :

« RemoveService est le seul chemin mutant du plugin sans revalidation autoritaire de la base sous verrou (PurchaseService.java:118-125 et ExpireService.java:280-287 l'ont, RemoveService.java:200 ne l'a pas). Ses quatre gardes (RemoveService.java:56, 97, 135, 173) ne portent que sur `item.getStatus()`, un champ memoire absent de la table items. Comme checkAvailability et LOCK_SCRIPT laissent passer les etats 'SOLD' et 'REMOVED', et comme ItemRepository.createUpdateSchema:79-81 ne protege par un WHERE que la transition vers EXPIRED, toute reference Item perimee qui franchit la garde de statut aboutit a `giveItem` + `updateItem(..., DELETED)` qui ecrase la ligne PURCHASED d'un autre serveur. »

Le scenario a substituer (il ne necessite AUCUNE perte de message pub/sub) :

T0 - Serveur A : l'acheteur ouvre la confirmation d'achat, statut IS_PURCHASE_CONFIRM diffuse.

T1 - Serveur A : il confirme. lockItem (state=LOCKED), setStatus(IS_BEING_PURCHASED) + diffusion (PurchaseService.java:106-108), selectItem revalide OK, achat effectue : DB storage_type='PURCHASED', buyer_unique_id=A ; notifyItemBought pose state='SOLD' ; unlockItem supprime la cle de verrou en laissant 'SOLD'.

T2 - Serveur B : ItemStatusListener.java:54 a bien applique IS_BEING_PURCHASED a l'objet Item du store LISTED. L'item est ENCORE dans LISTED tant que ItemBoughtMessage n'est pas traite (fenetre = duree de l'achat sur A : economie + ecritures DB).

T3 - Serveur B, dans cette fenetre : le VENDEUR clique son propre item dans /ah. ListedItemsButton.java:118-133 n'a AUCUNE garde de statut avant :

`this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, ItemStatus.AVAILABLE, ItemStatus.IS_REMOVE_CONFIRM).thenRun(() -> { item.setStatus(ItemStatus.IS_REMOVE_CONFIRM); ... });`

Le statut IS_BEING_PURCHASED est donc ECRASE par IS_REMOVE_CONFIRM, et la reference est memorisee dans le cache : `cache.set(PlayerCacheKey.ITEM_SHOW, item)` (ListedItemsButton.java:126).

T4 - Serveur B : ItemBoughtMessage arrive, ItemBoughtListener.java:49-50 fait `manager.removeItem(StorageType.LISTED, id)` et vide ITEMS_LISTED / ITEMS_SELLING / ITEMS_SEARCH — mais PAS ITEM_SHOW, et ne touche PAS au statut. L'objet reste joignable via l'inventaire de confirmation, statut IS_REMOVE_CONFIRM.

T5 - Le vendeur confirme : ConfirmRemoveListedButton.java:23 -> removeListedItem. isExpired() faux ; RemoveService.java:56 `if (item.getStatus() != ItemStatus.AVAILABLE && item.getStatus() != ItemStatus.IS_REMOVE_CONFIRM)` ACCEPTE explicitement IS_REMOVE_CONFIRM.

T6 - checkAvailabilityStep : state='SOLD' -> true. acquireLockStep : LOCK_SCRIPT, 'SOLD' n'est ni DELETED ni LOCKED, cle absente -> jeton valide.

T7 - executeLocalRemovalStep -> ZAuctionManager.removeListedItem:507-549. Avec `remove-listed-item.give-item: false` (defaut, config.yml:788) : `addItem(StorageType.EXPIRED, item)` + `updateItem(item, EXPIRED)` (bloque en DB par le WHERE storage_type=LISTED, mais PAS en memoire) -> l'item vendu reapparait dans les « items expires » du vendeur ; il le reclame ensuite via removeExpiredItem (garde REMOVED, state Redis='REMOVED' -> disponible, aucun WHERE sur DELETED) -> `giveItem(vendeur)` + `updateItem(..., DELETED)` qui ecrase la ligne PURCHASED de l'acheteur.

Avec `give-item: true`, ou via /ah selling (removeSellingItem, ZAuctionManager.java:557-585, giveItem inconditionnel + destination DELETED), l'item est rendu physiquement au vendeur des T7.

Resultat : l'acheteur a paye et se retrouve soit avec une copie en plus (s'il a deja reclame sur A), soit deposseille ; le vendeur a l'argent ET l'item.

Deuxieme voie de declenchement, sans course de timing : RedisSubscriberRunnable.java:81-96 perd definitivement tout message publie pendant sa boucle de reconnexion (backoff jusqu'a 60 s) et il n'existe aucune reconciliation au retour. Un serveur ayant manque ItemStatusMessage + ItemBoughtMessage garde un fantome LISTED/AVAILABLE indefiniment : le scenario original du constat devient alors exact tel quel.

La correction proposee reste valable et suffisante (revalidation selectItem sous verrou inseree entre changeStatusAndNotifyStep et executeLocalRemovalStep, calquee sur ExpireService.java:280-287), a deux nuances : (a) `localRemovalCompleted` est bien encore false a cet endroit, le rollback de restoreStatusOnError est donc correct ; (b) il faut aussi ajouter une garde de statut dans ListedItemsButton.java:118 avant de poser IS_REMOVE_CONFIRM, sinon ce bouton continuera d'effacer les statuts transitoires propages par le cluster.

**Correctif**

```java
Insérer une étape de revalidation autoritaire sous verrou, entre acquireLockStep/changeStatusAndNotifyStep et executeLocalRemovalStep :

private CompletableFuture<Void> revalidateUnderLockStep(RemovalContext ctx, PerformanceConfiguration cfg) {
    return this.plugin.getStorageManager().selectItem(ctx.item.getId())
            .orTimeout(cfg.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
            .thenCompose(dbItem -> {
                // mapping ItemLoaderUtils:43-48 : LISTED->AVAILABLE, EXPIRED->REMOVED, PURCHASED->PURCHASED
                ItemStatus expected = switch (ctx.storageType) {
                    case LISTED    -> ItemStatus.AVAILABLE;
                    case EXPIRED   -> ItemStatus.REMOVED;
                    case PURCHASED -> ItemStatus.PURCHASED;
                    default        -> null;
                };
                boolean stale = dbItem == null                                        // ligne DELETED : select(int) la filtre
                        || dbItem.getStatus() != expected                               // bucket changé ailleurs
                        || (ctx.storageType == StorageType.LISTED && dbItem.getBuyerUniqueId() != null); // déjà vendu
                if (stale) {
                    this.plugin.getAuctionManager().removeItem(ctx.storageType, ctx.item.getId()); // purge du fantôme
                    this.plugin.getAuctionManager().clearPlayersCache(PlayerCacheKey.values());
                    ctx.onUnavailable.run();
                    ctx.result = RemoveResult.failure("Item already processed on another server", RemoveFailReason.ITEM_NOT_AVAILABLE);
                    return failedFuture(new IllegalStateException("stale item " + ctx.item.getId()));
                }
                ctx.item.setExpiredAt(dbItem.getExpiredAt());   // resynchronisation
                return CompletableFuture.<Void>completedFuture(null);
            });
}

Chaînage : `...acquireLockStep(...)).thenCompose(token -> changeStatusAndNotifyStep(...)).thenCompose(v -> revalidateUnderLockStep(context, cfg)).thenCompose(v -> executeLocalRemovalStep(...))`. localRemovalCompleted est encore false à ce stade, donc restoreStatusOnError fera correctement le rollback.

Ajouter en défense en profondeur une garde d'appartenance O(1) en tête de executeRemoval : `if (auctionManager.getItem(storageType, item.getId()) != item) { onUnavailable.run(); return completedFuture(RemoveResult.failure("Stale item reference", ITEM_NOT_AVAILABLE)); }` (nécessite l'accesseur getItem proposé plus bas).
```

---

<a id="c-010"></a>

### `C-010` — SQLITE reste accepté avec un bridge cluster distribué : chaque serveur a sa propre base, les ids se recoupent et toute la protection anti-duplication opère sur des objets différents

- [ ] **Corrigé**
- **Fichier** : `storage/ZStorageManager.java:49`
- **Catégorie** : Cycle de vie — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZStorageManager.java:49-50 (vérifié) :

    var isSqlite = databaseConfiguration.getDatabaseType() == DatabaseType.SQLITE;
    this.databaseConnection = isSqlite ? new SqliteConnection(databaseConfiguration, this.plugin.getDataFolder(), sarahLogger) : new HikariDatabaseConnection(databaseConfiguration, sarahLogger);

ZAuctionPlugin.java:420-422 (vérifié) : le setter installe n'importe quel bridge sans jamais regarder le type de base.

    public void setAuctionClusterBridge(AuctionClusterBridge auctionClusterBridge) {
        this.auctionClusterBridge = auctionClusterBridge;
    }

src/main/resources/config.yml:129 (vérifié) : `storage-type: SQLITE` est le défaut livré. Le seul garde-fou est un commentaire ligne 128 : « Note: For multi-server synchronization, use MySQL/MariaDB with the Redis addon. »

ZAuctionHouseRedis.java:99 (vérifié) appelle ce setter avec un RedisAuctionClusterBridge dont isDistributed() renvoie true (RedisAuctionClusterBridge.java:340-342), sans jamais consulter le type de stockage.
```

**Chronologie**

T0 — L'admin installe zAuctionHouse + zAuctionHouseRedis sur les serveurs A et B, configure Redis correctement et laisse `storage-type: SQLITE` (valeur par défaut). Aucun avertissement au démarrage.

T1 — A et B ouvrent chacun leur propre fichier plugins/zAuctionHouse/database.db. Les auto-increment sont indépendants : « item 42 » de A et « item 42 » de B sont deux annonces sans rapport.

T2 — Un joueur retire son item 42 sur le serveur B. RedisAuctionClusterBridge.removeItem écrit `auction:item:42` -> STATE_REMOVED et publie ItemRemovedMessage(id=42).

T3 — Sur A, ItemRemovedListener retire de la mémoire l'item 42 de A (une annonce toujours vivante en base sur A) : A a maintenant un fantôme mémoire.

T4 — Le vendeur de l'item 42 sur A ouvre son inventaire de mise en vente. RemoveService ne revalide jamais la base sous verrou (constat déjà connu) : il reçoit ses ItemStacks alors que la ligne existe encore, et la ligne restée LISTED redevient visible au prochain redémarrage de A -> l'item est dupliqué.

T5 — Symétriquement, `checkAvailability(42)` sur A renvoie false parce que B a verrouillé SON item 42 : des achats légitimes sont refusés sans explication.

**Impact** — Duplication d'items réellement atteignable, et surtout : l'administrateur croit avoir un cluster protégé alors que verrous Redis, messages pub/sub et revalidations DB portent tous sur des espaces d'ids disjoints. Aucune protection anti-duplication n'est active. Point secondaire vérifié sur la même chaîne : Sarah SqliteConnection ne pose pas `PRAGMA foreign_keys=ON`, donc les FK déclarées dans les migrations (items->players, auction_items->items, logs->items, transactions->items) ne sont jamais appliquées, même en mono-serveur.

**Précision apportée par la contre-expertise**

Constat réel et bien localisé ; seule la formulation de l'IMPACT mérite d'être resserrée, la « duplication » n'étant pas la conséquence la plus directe.

Conséquences CERTAINES et directes (prouvées) :

- Perte définitive d'items / blocage permanent. RedisAuctionClusterBridge.removeItem (lignes 350-360) écrit `state = destinationStorageType == StorageType.DELETED ? STATE_DELETED : STATE_REMOVED` sur la clé partagée `auction:item:<id>`. Or checkAvailability (lignes 162-165) renvoie false pour DELETED de manière INCONDITIONNELLE — sans l'échappatoire « lock expiré » dont bénéficie l'état LOCKED (lignes 168-175). Donc dès que B supprime définitivement SON item 42, l'item 42 de A — annonce vivante et sans rapport — devient à jamais ni achetable ni récupérable par son vendeur : chaque tentative échoue en RemoveFailReason.ITEM_NOT_AVAILABLE (RemoveService:214-218) et en PurchaseService par le même checkAvailabilityStep. Les items du vendeur de A sont perdus.

- Items fantômes ressuscités. ItemRemovedListener ne touche QUE la mémoire (manager.removeItem + clearPlayersCache) et jamais la base de A : la ligne 42 de A reste LISTED et réapparaît au prochain redémarrage de A.

- Refus d'achats inexpliqués (T5) : exact tel que décrit.

Conséquence ATTEIGNABLE mais conditionnelle :

- La duplication d'items (T4) est réelle mais transite par le constat séparé « RemoveService ne revalide pas la base sous verrou » — vérifié ci-dessus, executeRemoval ne relit jamais la ligne. Elle n'est donc pas la conséquence de premier ordre ; à présenter comme telle plutôt qu'en titre.

Le cœur du constat — aucune garde, protections de cluster opérant sur des espaces d'ids disjoints, admin persuadé d'être protégé — est intégralement confirmé, et la sévérité critique se justifie même sans l'argument de duplication (perte d'items garantie).

Correction proposée valide et compilable. Deux ajustements recommandés :

1. La garde dans setAuctionClusterBridge ne couvre que le branchement tardif de l'addon. Ajouter aussi un contrôle symétrique CÔTÉ ADDON, dans ZAuctionHouseRedis.onEnable avant la ligne 99, afin que le message d'erreur nomme le plugin fautif et que l'addon se désactive lui-même plutôt que de faire tomber zAuctionHouse (le désactiver depuis son propre setter, appelé pendant l'onEnable d'un AUTRE plugin, est fragile : Bukkit est déjà en train d'énumérer les plugins).

2. Utiliser une désactivation de l'addon (ou un mode dégradé forçant LocalAuctionClusterBridge) plutôt que disablePlugin(zAuctionHouse) : couper l'hôtel des ventes entier sur une erreur de configuration prive les joueurs de l'accès à leurs items déjà en base, alors que retomber en mono-serveur reste sûr.

Le sous-constat Sarah (SqliteConnection sans PRAGMA foreign_keys/journal_mode/busy_timeout) est confirmé mot pour mot et mérite d'être traité séparément — c'est un correctif de la bibliothèque Sarah, pas de zAuctionHouseV4, et il impacte tous les consommateurs de Sarah. Note complémentaire observée au passage dans le même fichier : SqliteConnection.getConnection() (lignes 49-57) appelle connectToDatabase() à CHAQUE invocation, ouvrant une nouvelle connexion à chaque requête ; poser les PRAGMA dans connectToDatabase est donc bien le bon emplacement, mais ce comportement mérite son propre constat.

**Correctif**

```java
Refuser explicitement la combinaison dans ZAuctionPlugin.setAuctionClusterBridge :

@Override
public void setAuctionClusterBridge(AuctionClusterBridge bridge) {
    if (bridge != null && bridge.isDistributed()
            && this.storageManager.getDatabaseConnection().getDatabaseConfiguration().getDatabaseType() == DatabaseType.SQLITE) {
        getLogger().severe("[ZAH] Cluster bridge " + bridge.getClass().getSimpleName() + " registered while storage-type is SQLITE.");
        getLogger().severe("[ZAH] Each server uses its own local database file: item ids are NOT shared, cluster locks are meaningless and items WILL be duplicated.");
        getLogger().severe("[ZAH] Switch storage-type to MYSQL/MARIADB, or remove the cluster addon. Disabling zAuctionHouse.");
        Bukkit.getPluginManager().disablePlugin(this);
        return;
    }
    this.auctionClusterBridge = bridge;
}

Et côté Sarah, dans SqliteConnection.connectToDatabase, exécuter `PRAGMA foreign_keys = ON`, `PRAGMA journal_mode = WAL`, `PRAGMA busy_timeout = 5000` juste après DriverManager.getConnection.
```

---

<a id="c-011"></a>

### `C-011` — Un item dont la liste d'ItemStacks est vide reste LISTED, s'affiche comme un lot normal et reste achetable : l'acheteur est debite du prix plein et ne recoit RIEN

- [ ] **Corrigé**
- **Fichier** : `items/ZAuctionItem.java:68`
- **Catégorie** : Perte d’argent — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZAuctionItem.java:68-82 -- une liste vide donne size() != 1, donc on tombe dans la branche "lot multiple", sans aucune garde :
    private ItemStack getItemStack(Player player) {
        if (this.itemStacks.size() == 1) {
            var first = this.itemStacks.getFirst();
            if (first == null) {
                this.plugin.getLogger().warning("Item #" + this.id + " has a null ItemStack, the item data may be corrupted.");
                return new ItemStack(org.bukkit.Material.BARRIER);
            }
            ...
        }
        return this.plugin.getConfiguration().getSpecialItems().auctionItem().build(player).clone();
    }

ItemLoaderUtils.java:38-41 -- aucune verification que currentAuctionItems est non vide :
    protected AuctionItem createAuctionItem(AuctionPlugin plugin, ItemDTO dto, String sellerName, List<AuctionItemDTO> currentAuctionItems, AuctionEconomy auctionEconomy) {
        var itemStacks = currentAuctionItems.stream().map(e -> Base64ItemStack.decode(e.itemstack())).toList();
        var auctionItem = new ZAuctionItem(plugin, dto.id(), dto.server_name(), ..., itemStacks);

ZAuctionManager.java:926-935 -- la boucle de livraison ne s'execute jamais sur une liste vide, et rien ne le detecte :
    public void giveItem(Player player, Item item) {
        if (item instanceof AuctionItem auctionItem) {
            var itemStacks = auctionItem.getItemStacks();
            for (ItemStack itemStack : itemStacks) {
                player.getInventory().addItem(itemStack).forEach((slot, dropItemStack) -> player.getWorld().dropItem(player.getLocation(), dropItemStack));
            }
        } else plugin.getLogger().severe("give item not implemented");
    }

ZAuctionManager.java:740-741 -- le code gere explicitement la liste vide sans jamais s'en alarmer :
        var representativeItem = itemStacks != null && !itemStacks.isEmpty() ? itemStacks.getFirst() : null;
```

**Chronologie**

Precondition realiste : AuctionItemRepository.create (l.30-37) insere la ligne parente %prefix%items PUIS boucle les inserts %prefix%auction_items ; il n'existe aucune transaction SQL dans tout zAuctionHouseV4 (DatabaseConnection.beginTransaction() n'est appele nulle part). Un echec sur le premier enfant, ou l'echec massif decrit par le constat `IN(...)`, laisse une ligne LISTED sans aucun enfant.

T0 - Serveur A : une vente echoue apres l'INSERT dans `items` mais avant le premier `auction_items`. La ligne #4271 reste LISTED avec ZERO enfant. Le vendeur a deja recupere/garde sa marchandise cote inventaire.

T1 - Serveur B redemarre. ItemLoaderUtils.java:59 `AuctionItemRepository.select(...)` ne rend aucune ligne pour #4271 ; l.39 produit `itemStacks = []`.

T2 - Serveur B : `categoryManager.applyCategories` (ItemLoaderUtils.java:91) boucle sur 0 stack -> l'item finit dans `misc` sans erreur ; `biConsumer.accept(LISTED, auctionItem)` l'insere dans le store LISTED.

T3 - Serveur B : un joueur ouvre l'HDV. ZAuctionItem.getItemStack l.69 voit `size()==1` faux et renvoie l.81 l'icone configuree `special-items.auction-item` : a l'ecran, un lot multi-items parfaitement credible, au prix affiche.

T4 - Serveur B : l'acheteur confirme. PurchaseService.java:56 `canReceiveItem` passe, l.118-124 la revalidation DB passe (la ligne existe, buyer_unique_id est NULL), l.126 `has()` passe.

T5 - ZAuctionManager.java:802 `withdraw(buyerPays)` : L'ARGENT EST PRIS. l.883-884 `updateItem(DELETED)` puis `giveItem` : la boucle l.930 ne s'execute pas.

T6 - L'acheteur recoit zero item, l.861 lui affiche ITEM_BOUGHT_BUYER avec %items% vide, et le vendeur est credite (ou une ligne PENDING est creee). Aucun log d'erreur nulle part.

**Impact** — Perte d'argent silencieuse cote acheteur ET creation monetaire nette cote vendeur, sans aucune trace exploitable (message %items% vide, log SALE/PURCHASE avec itemstack vide). Le meme item vide traverse aussi les flux EXPIRED et PURCHASED : un removeExpiredItem marque DELETED et ne rend rien. Combine au constat sur la limite du IN(...), le defaut peut toucher l'integralite de l'hotel des ventes d'un coup.

**Précision apportée par la contre-expertise**

Le constat est reel et sa conclusion est exacte, mais trois points doivent etre rectifies/precises.

1) NUMEROS DE LIGNE : la citation ItemLoaderUtils est decalee d'une ligne. Le vrai emplacement est ItemLoaderUtils.java:39-42 (`createAuctionItem` l.39, `var itemStacks = ...toList()` l.40, `new ZAuctionItem(...)` l.42), et non 38-41.

2) LA PRECONDITION EST PLUS FACILE A ATTEINDRE QUE DECRIT (le constat exige un "echec/crash entre les deux inserts" ; en realite aucun crash n'est necessaire). Repository.java:124-130 avale integralement l'erreur :

protected void insert(Consumer<Schema> consumer, Consumer<Integer> consumerResult) {

try { consumerResult.accept(SchemaBuilder.insert(getTableName(), consumer).execute(this.connection, this.logger));

} catch (SQLException exception) { exception.printStackTrace(); }

}

C'est exactement la methode appelee dans la boucle enfants de AuctionItemRepository.java:31-36. Consequence : un simple INSERT enfant en echec (coupure de connexion, deadlock, packet trop gros, FK) ne remonte JAMAIS ; le CompletableFuture de ZStorageManager.java:141-144 se termine en SUCCES, donc :

- SellService.java:105 `removeItemsFromSlots(player, validSlotItems)` a DEJA retire la marchandise du vendeur AVANT l'insert l.108 ;

- le bloc de remboursement SellService.java:111-123 (`.exceptionally(...)` qui rend les items) ne se declenche pas ;

- postSell (l.109) diffuse l'item au cluster via notifyItemListed comme si tout allait bien.

Deux autres chemins SANS aucune erreur ni crash :

- V3MigrationService.java:299-310 `insertAuctionItem` attrape la SQLException et se contente d'un WARNING, tandis que migrateItems l.238 compte quand meme l'item comme migre : ligne parente LISTED sans enfant, migration declaree reussie ;

- V3MigrationService.java:285-292 : pour un item V3 de type INVENTORY dont l'itemstack ne contient que des segments vides (ex. ";"), ZERO enfant est insere sans la moindre erreur.

Enfin, l'addon Redis propage le fantome a tous les serveurs : ItemListedListener.java:31-48 fait `selectItem(id)` et ne journalise en SEVERE que si `item == null` ; un item a stacks vides passe le filtre et est ajoute au store LISTED de chaque serveur.

3) DETAIL DU SCENARIO T3/T4 : l'icone affichee est bien celle des lots multiples (ZAuctionItem.java:81), mais le clic emprunte le chemin MONO-item : ListedItemsButton.java:142 calcule `isMultipleAuctionItem = auctionItem.getItemStacks().size() > 1` -> faux pour une liste vide -> l.143 ouvre `Inventories.PURCHASE_CONFIRM` et non PURCHASE_INVENTORY_CONFIRM. Incoherence purement cosmetique, sans effet sur l'issue (l'achat aboutit et le paiement a lieu).

CORRECTION A APPORTER A LA CORRECTION PROPOSEE : le garde-fou n° 2 doit porter sur l'item EN MEMOIRE (`item`), pas uniquement sur `dbItem`. C'est ZAuctionManager.java:929 (`auctionItem.getItemStacks()` de l'instance en memoire) qui alimente la livraison, et sur le serveur vendeur l'instance en memoire contient encore les stacks que la base ne possede plus : verifier seulement `dbItem` laisserait passer le cas inverse (acheteur servi avec des stacks absents de la base). Verifier les deux.

**Correctif**

````java
1) ItemLoaderUtils.createAuctionItem (l.38) et ZStorageManager.selectItem : si `currentAuctionItems.isEmpty()` ou si un `Base64ItemStack.decode` rend null, NE PAS construire l'item -- journaliser en SEVERE avec l'id et passer la ligne en DELETED (ou dans un storage_type de quarantaine) plutot que de l'exposer a la vente.
2) Garde d'ultime recours au coeur du flux d'argent, dans PurchaseService.purchaseItem juste apres la revalidation DB l.118-124 :
```java
if (dbItem instanceof AuctionItem ai && ai.getItemStacks().isEmpty()) {
    logger.severe("Item " + item.getId() + " has no content, refusing purchase");
    resultHolder.set(PurchaseResult.failure("Item has no content", PurchaseFailReason.ITEM_NOT_AVAILABLE));
    return failedFuture(new IllegalStateException("empty item " + item.getId()));
}
```
3) Faire de `giveItem` (ZAuctionManager.java:926) une methode qui RETOURNE le nombre de stacks effectivement remis, et annuler/compenser si ce nombre est 0.
4) Persister le nombre de stacks attendu (colonne `stack_count` sur %prefix%items, renseignee a l'insertion) et le comparer au chargement.
````

---

<a id="c-012"></a>

### `C-012` — adminRemoveItem diffuse une suppression non terminale (REMOVED, destination null) AVANT d'ecrire en base : les autres serveurs remettent l'item en vente

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:689`
- **Catégorie** : Duplication d’item — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// ZAuctionManager.java:689-696 -- diffusion AVANT l'UPDATE, future d'ecriture ignore
}).thenCompose(lockToken -> clusterBridge.removeItem(item, storageType).thenApply(v -> lockToken)).thenAccept(lockToken -> {
    removeItem(storageType, item);
    this.plugin.getStorageManager().updateItem(item, StorageType.DELETED);   // future jete
    clearPlayersCache(...);
    giveItem(admin, item);

// RedisAuctionClusterBridge.java:345-347 -- la surcharge 2 args passe destination = null
public CompletableFuture<Void> removeItem(Item item, StorageType storageType) {
    return removeItem(item, storageType, null);
}

// RedisAuctionClusterBridge.java:354-361
String state = destinationStorageType == StorageType.DELETED ? STATE_DELETED : STATE_REMOVED;
jedis.hset(itemKey, FIELD_STATE, state);
this.plugin.sendMessage(new ItemRemovedMessage(String.valueOf(item.getId()), sourceStorageType, destinationStorageType));

// ItemRemovedListener.java:64-88 -- branche destination == null
} else if (destination == null) {
    auctionPlugin.getStorageManager().selectItem(id).whenComplete((item, throwable) -> {
        StorageType effectiveDestination = switch (item.getStatus()) {
            case REMOVED -> StorageType.EXPIRED;
            case PURCHASED -> StorageType.PURCHASED;
            case AVAILABLE -> StorageType.LISTED;
            ...
        if (effectiveDestination != StorageType.DELETED) {
            auctionPlugin.getScheduler().runNextTick(w -> { manager.addItem(effectiveDestination, item); ... });

// ItemLoaderUtils.java:44 -- une ligne storage_type=LISTED se recharge en AVAILABLE
case LISTED -> ItemStatus.AVAILABLE;
```

**Chronologie**

T0 -- Serveur A : un admin supprime l'item 42 (LISTED) via /ah admin. A verrouille puis appelle clusterBridge.removeItem(item, LISTED) : Redis passe a state=REMOVED (et non DELETED, car destinationStorageType vaut null) et publie ItemRemovedMessage(source=LISTED, destination=null).

T1 -- Serveur B recoit le message. L'UPDATE de A (ZAuctionManager.java:693) part sur l'executor et son future est jete, donc la ligne MySQL est encore storage_type=LISTED. B execute selectItem(42) : la ligne est LISTED -> ItemLoaderUtils.java:44 la recharge en ItemStatus.AVAILABLE -> effectiveDestination = LISTED -> B fait addItem(LISTED, item) : l'item est REMIS EN VENTE sur B.

T2 -- A donne physiquement l'item a l'admin (giveItem, ZAuctionManager.java:696).

T3 -- Un joueur sur B achete l'item 42. checkAvailability : state=REMOVED, ni DELETED ni LOCKED -> true (RedisAuctionClusterBridge.java:163-168). LOCK_SCRIPT : state != 'DELETED' -> verrou acquis. Revalidation PurchaseService.java:121 : si l'UPDATE de A n'a pas encore atterri (ou a echoue, personne ne l'observe), la ligne est encore LISTED avec buyer_unique_id NULL -> l'achat est valide.

T4 -- L'acheteur recoit une copie de l'item que l'admin possede deja, et le vendeur est credite. Meme sans achat, le vendeur peut reprendre l'item via RemoveService, qui ne revalide jamais la base.

**Impact** — Duplication d'item (admin + acheteur, ou admin + vendeur) declenchable a chaque suppression admin en cluster, plus argent preleve a un acheteur pour un item qui n'existe plus. L'echec eventuel de l'UPDATE n'est jamais detecte car le future est jete.

**Précision apportée par la contre-expertise**

Le constat est exact ; deux precisions sur la hierarchie des deux voies de duplication.

1) VOIE PRINCIPALE (durable, sans fenetre de course apres T1) — a mettre en avant : la reprise par le vendeur, pas l'achat. Une fois la course T1 gagnee, l'etat Redis reste `REMOVED` DEFINITIVEMENT : `unlockItem` (RedisAuctionClusterBridge.java:74-90, UNLOCK_SCRIPT) ne repasse l'etat a AVAILABLE que `if currentState == 'LOCKED'`, or `removeItem` a deja ecrase l'etat par REMOVED. Ensuite `checkAvailability` (lignes 158-177) renvoie true pour REMOVED et LOCK_SCRIPT (41-67) ne refuse que 'DELETED'. Comme RemoveService ne revalide JAMAIS la base (RemoveService.java:206-265 : uniquement checkAvailability + lockItem), le vendeur peut reprendre l'item sur B a n'importe quel moment ulterieur — des heures apres. Duplication garantie, sans fenetre.

2) VOIE SECONDAIRE (fenetre etroite) — l'achat en T3 est plus difficile que decrit : `ItemRepository.java:51-56` filtre `storage_type != DELETED`, donc des que l'UPDATE de A atterrit, `selectItem` renvoie null et la revalidation sous verrou de `PurchaseService.java:118-125` (`if (dbItem == null || dbItem.getBuyerUniqueId() != null)`) avorte proprement l'achat. Cette voie n'est exploitable que pendant les quelques millisecondes ou l'UPDATE de A n'a pas encore atterri — ou indefiniment si l'UPDATE echoue, echec que personne n'observe puisque le future est jete (ZAuctionManager.java:693).

3) DEFAUT SUPPLEMENTAIRE non mentionne dans le titre, mais bien couvert par la correction proposee : adminRemoveItem n'ecarte pas le jeton `LockToken.noop()`, contrairement a tous les autres chemins (RemoveService.java:232-236, PurchaseService.java:100-104). Un echec d'acquisition du verrou (item deja verrouille par un autre serveur) laisse quand meme adminRemoveItem donner l'item a l'admin et diffuser la suppression.

4) Argument de conformite pour la correction : le patch propose ne fait qu'aligner adminRemoveItem sur le motif deja applique par le commit 49571d9 — `removeSellingItem` (ZAuctionManager.java:557-587) retourne `updateFuture`, que `RemoveService.executeLocalRemovalStep` (RemoveService.java:248-252) chaine via `.thenCompose(v -> clusterBridge.removeItem(item, source, destination))`. adminRemoveItem est le dernier appelant de la surcharge 2 arguments. Le compare-and-set suggere doit etre ajoute dans `ItemRepository.createUpdateSchema` (lignes 71-92), qui ne pose aujourd'hui de clause `where storage_type` que pour la transition vers EXPIRED.

**Correctif**

```java
Ecrire en base D'ABORD, diffuser un etat terminal ensuite, et refuser le jeton noop :

}).thenCompose(lockToken -> {
    if (LockToken.noop().value().equals(lockToken.value())) {
        return failedFuture(new IllegalStateException("Item verrouille par un autre serveur"));
    }
    item.setStatus(ItemStatus.DELETED);
    return this.plugin.getStorageManager().updateItem(item, StorageType.DELETED)          // 1. durabilite
            .thenCompose(v -> clusterBridge.removeItem(item, storageType, StorageType.DELETED)) // 2. etat TERMINAL
            .thenApply(v -> lockToken);
}).thenAccept(lockToken -> {
    removeItem(storageType, item);
    this.plugin.getScheduler().runAtEntity(admin, w -> giveItem(admin, item));            // 3. livraison
    ...
    clusterBridge.unlockItem(item, lockToken, storageType);
});

Et ajouter un compare-and-set sur l'UPDATE (where storage_type = <source>) pour que la base arbitre.
```

---

<a id="c-013"></a>

### `C-013` — claimMoney marque toutes les transactions RETRIEVED meme quand aucun depot n'a eu lieu

- [ ] **Corrigé**
- **Fichier** : `services/ClaimService.java:78`
- **Catégorie** : Perte d’argent — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// ClaimService.java:68-84
                if (economyTotal.compareTo(BigDecimal.ZERO) > 0) {
                    if (player.isOnline()) {
                        try {
                            economy.deposit(player.getUniqueId(), economyTotal, depositReason);
                        } catch (Exception e) {
                            this.plugin.getLogger().severe("Failed to deposit " + economyTotal + " to " + player.getName() + " for economy " + economyName + ": " + e.getMessage());
                            continue;
                        }
                        message(this.plugin, player, Message.CLAIM_ECONOMY_SUCCESS, "%amount%", economyManager.format(economy, economyTotal), "%economy%", economy.getDisplayName());
                    }
                    totalClaimed = totalClaimed.add(economyTotal);
                }
            }

            // Mark all transactions as retrieved synchronously to prevent double claims
            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            repository.updateStatus(transactionIds, TransactionStatus.RETRIEVED);

// ClaimService.java:117-122 -- le bloc ci-dessus tourne sur l'asyncExecutor, apres un aller-retour DB
    public CompletableFuture<List<TransactionDTO>> getPendingTransactions(UUID playerUniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            return repository.selectByPlayerAndStatus(playerUniqueId, TransactionStatus.PENDING);
        }, this.plugin.getExecutorService());

// ClaimService.java:199-210 -- meme defaut dans clearPendingTransactions
                        } catch (Exception e) {
                            this.plugin.getLogger().severe("Failed to deposit " + economyTotal + " to " + playerUniqueId + ...);
                        }
...
            repository.updateStatus(transactionIds, TransactionStatus.RETRIEVED);
```

**Chronologie**

T0 serveur A : le joueur se connecte, PlayerListener appelle ClaimService.handlePlayerJoin (l.125) puis, auto-claim active, claimMoney (l.147 ou l.151). T1 A : getPendingTransactions (l.118-121) part sur plugin.getExecutorService() (pool fixe de 4 threads, ZAuctionPlugin.java:82) et execute SELECT ... WHERE player_unique_id = ? AND status = 'PENDING' ; sous charge MySQL, sans index sur status, ce SELECT prend plusieurs centaines de ms. T2 : pendant ce temps le joueur bascule sur le serveur B via le proxy (ou est kicke, ou se deconnecte). T3 A : le thenApply (l.31) s'execute enfin sur le thread du pool. La condition player.isOnline() (l.69) rend false : aucun economy.deposit n'est appele. Mais totalClaimed = totalClaimed.add(economyTotal) (l.78) est HORS du bloc isOnline et incremente quand meme. T4 A : la ligne 84 repository.updateStatus(transactionIds, RETRIEVED) marque TOUTES les lignes PENDING comme encaissees, sans qu'un centime n'ait ete verse. T5 : la methode retourne meme ClaimResult.success (l.86-88) avec totalClaimed > 0. T6 : de retour sur B, le joueur fait /ah claim ; selectByPlayerAndStatus(..., PENDING) ne renvoie plus rien. L'argent de ses ventes est detruit. Variante T3-bis : le joueur est en ligne mais economy.deposit leve (tres probable puisque l'appel se fait hors thread principal, cf. le constat sur withdraw/deposit) ; le continue de la l.74 saute le depot pour cette economie, mais la l.84 marque quand meme ses transactions RETRIEVED.

**Impact** — Destruction definitive et silencieuse de l'argent en attente. L'exposition est maximale en cluster : ZAuctionManager.java:812-814 (deferDeposit avec clusterBridge.isDistributed()) route TOUT paiement a un vendeur hors-ligne vers ce circuit PENDING. Aucun log n'est emis dans le cas isOnline == false, et TransactionRepository.updateStatus (l.46-57) ne relit pas le nombre de lignes affectees, donc rien ne permet de detecter la perte a posteriori.

**Correctif**

1) Sortir tot si le joueur n'est plus la : if (!player.isOnline()) return ClaimResult.nothingToClaim("Player offline"); en tete du thenApply, AVANT toute lecture de transactionIds. 2) Ne marquer RETRIEVED que ce qui a reellement ete credite : construire List<Integer> claimedIds et n'y ajouter economyTransactions.stream().map(TransactionDTO::id).toList() qu'apres le retour normal de economy.deposit, puis if (!claimedIds.isEmpty()) repository.updateStatus(claimedIds, TransactionStatus.RETRIEVED);. 3) Supprimer l.78 du chemin non-credite (deplacer totalClaimed = totalClaimed.add(economyTotal) a l'interieur du bloc qui suit le deposit reussi). 4) Appliquer le meme traitement a clearPendingTransactions (l.199-210), ou l'exception de depot est actuellement avalee avant le marquage global.

---

<a id="c-014"></a>

### `C-014` — giveItem() ecrit dans l'inventaire du joueur et fait spawner des entites depuis ForkJoinPool.commonPool

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:931`
- **Catégorie** : Duplication d’item — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// ZAuctionManager.java:926-935 -- aucun hop scheduler
public void giveItem(Player player, Item item) {
    if (item instanceof AuctionItem auctionItem) {

        var itemStacks = auctionItem.getItemStacks();
        for (ItemStack itemStack : itemStacks) {
            player.getInventory().addItem(itemStack).forEach((slot, dropItemStack) -> player.getWorld().dropItem(player.getLocation(), dropItemStack));
        }

    } else plugin.getLogger().severe("give item not implemented");
}

// REDIS/RedisAuctionClusterBridge.java:159, 182, 245, 292, 318, 336, 351 -- AUCUN Executor
return CompletableFuture.supplyAsync(() -> {
    try (Jedis jedis = jedisPool.getResource()) {

// REDIS/RedisAuctionClusterBridge.java:336
return CompletableFuture.runAsync(() -> this.plugin.sendMessage(new ItemStatusMessage(String.valueOf(item.getId()), oldStatus, newStatus)));

// V4/storage/ZStorageManager.java:176-177 -- selectItem AUSSI sans executor
public CompletableFuture<Item> selectItem(int id) {
    return CompletableFuture.supplyAsync(() -> {

// V4/services/PurchaseService.java:129-138 -- thenCompose SANS Async : herite du thread qui complete
}).thenCompose(hasMoney -> {
    var token = tokenHolder.get();
    if (hasMoney) {
        return auctionManager.purchaseItem(player, item)

// V4/src/main/resources/plugin.yml
folia-supported: true
```

**Chronologie**

T0 serveur A, thread principal : ConfirmPurchaseButton.java:22 appelle PurchaseService.purchaseItem. T1 A : PurchaseService.java:82 clusterBridge.checkAvailability(item) -> RedisAuctionClusterBridge.java:159 CompletableFuture.supplyAsync SANS executor -> la tache part sur ForkJoinPool.commonPool. T2 A : le worker commonPool-worker-1 termine le HGET et complete le futur ; comme PurchaseService.java:84 utilise thenCompose (et non thenComposeAsync avec un executor), la suite s'execute DIRECTEMENT sur commonPool-worker-1. T3 A : lockItem (bridge l.182), notifyItemStatusChange (bridge l.336) et selectItem (ZStorageManager.java:177) rechainent tous sur commonPool. T4 A : PurchaseService.java:134 auctionManager.purchaseItem(player, item) s'execute donc sur un worker commonPool, et avec lui tout ZAuctionManager.purchaseAuctionItem (l.720-919). T5 A : ZAuctionManager.java:884 giveItem(player, auctionItem) -> l.931 player.getInventory().addItem(itemStack) modifie le tableau de slots du joueur pendant que le thread principal traite le meme tick, potentiellement l'InventoryClickEvent suivant du meme joueur ; puis player.getWorld().dropItem(...) fait spawner une entite hors tick. T6 A : aucun paquet de resynchronisation n'est envoye, le client et le serveur divergent sur le contenu des slots. Le meme chemin existe pour les retraits : RemoveService.java:250 context.onLocalRemoval.get() est invoque dans le thenCompose qui suit notifyItemStatusChange (bridge l.336, commonPool), donc ZAuctionManager.java:569, :603, :637 appellent giveItem sur commonPool, ainsi que ZAuctionManager.java:696 (adminRemoveItem).

**Impact** — Ecriture concurrente non synchronisee sur l'inventaire du joueur, c'est le vecteur classique de duplication/perte d'item. Sur Paper, player.getWorld().dropItem hors thread principal est intercepte par l'AsyncCatcher et leve une IllegalStateException : le surplus est detruit alors que ZAuctionManager.java:883 a deja lance updateItem(..., DELETED). Sur Folia (plugin.yml declare folia-supported: true), commonPool n'est jamais le thread de region du joueur : l'appel est illegal par construction. Le reste du projet respecte pourtant la regle (ZAuctionManager.java:161-162 updateInventory, :1014-1019 callEvent, :958 updateListedItems, loader/ZInventoriesLoader.java:270 openInventory hoppent tous via runAtEntity/runNextTick) : giveItem est le seul trou.

**Correctif**

1) Rendre giveItem asynchrone-safe et chainable : public CompletableFuture<Void> giveItem(Player player, Item item) { CompletableFuture<Void> f = new CompletableFuture<>(); plugin.getScheduler().runAtEntity(player, w -> { try { for (ItemStack is : ((AuctionItem) item).getItemStacks()) player.getInventory().addItem(is).forEach((s, d) -> player.getWorld().dropItem(player.getLocation(), d)); f.complete(null); } catch (Throwable t) { f.completeExceptionally(t); } }); return f; }. 2) Chainer les 6 sites d'appel dans l'ordre DB puis remise : storageManager.updateItem(item, DELETED).thenCompose(v -> giveItem(player, item)) aux lignes ZAuctionManager.java:522-523, 568-569, 602-603, 636-637, 693-696, 883-884. 3) En complement, passer un Executor explicite a tous les supplyAsync/runAsync de RedisAuctionClusterBridge (l.159, 182, 245, 292, 318, 336, 351) et de ZStorageManager (l.177, 215) pour que la chaine ne s'execute plus jamais sur commonPool.

---

<a id="c-015"></a>

### `C-015` — withdraw() ne rend aucun resultat et n'est jamais verifie : entre le has() et le debit, l'acheteur peut obtenir l'item sans payer et le vendeur est credite a partir de rien

- [ ] **Corrigé**
- **Fichier** : `economy/ZAuctionEconomy.java:92`
- **Catégorie** : Duplication d’argent — **Sévérité** : Critique — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZAuctionEconomy.java:80-94 -- has() est une simple lecture, withdraw()/deposit() ne rendent rien :
    @Override
    public CompletableFuture<Boolean> has(UUID playerId, BigDecimal price) {
        return get(playerId).thenApply(balance -> balance.compareTo(price) >= 0);
    }

    @Override
    public void deposit(UUID playerId, BigDecimal value, String reason) {
        this.currencyProvider.deposit(playerId, value, reason);
    }

    @Override
    public void withdraw(UUID playerId, BigDecimal value, String reason) {
        this.currencyProvider.withdraw(playerId, value, reason);
    }

CurrenciesAPI CurrencyProvider.java:20/29 -- la signature elle-meme est sans retour :
    void deposit(UUID playerId, BigDecimal amount, String reason);
    void withdraw(UUID playerId, BigDecimal amount, String reason);

CurrenciesAPI VaultProvider.java:34-38 -- l'EconomyResponse de Vault est jete :
    @Override
    public void withdraw(UUID playerId, BigDecimal amount, String reason) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        this.getEconomy().withdrawPlayer(offlinePlayer, amount.doubleValue());
    }

ZAuctionManager.java:800-806 -- le seul filet est un try/catch, or Vault signale l'echec par valeur de retour, jamais par exception :
        try {
            auctionEconomy.withdraw(player.getUniqueId(), buyerPays, args(auctionEconomy.getWithdrawReason(), "%seller%", sellerName, "%items%", items));
        } catch (Exception e) {
            this.plugin.getLogger().severe("Failed to withdraw " + buyerPays + " from buyer " + ...);
            throw new RuntimeException("Failed to withdraw buyer payment", e);
        }

PurchaseService.java:126 -- la seule verification, faite bien avant le prelevement :
                                return auctionEconomy.has(player.getUniqueId(), requiredBalance);
```

**Chronologie**

Le verrou pris par PurchaseService (l.90) est par ITEM, jamais par JOUEUR : rien ne serialise deux achats du meme acheteur, ni deux serveurs sur la meme economie partagee.

T0 - Serveur A : le joueur J a exactement 100 000. Il confirme l'achat de l'item X a 100 000.

T1 - Serveur A : PurchaseService.java:81-126 enchaine checkAvailability (Redis), lockItem (script Lua Redis), notifyItemStatusChange (publish Redis) puis selectItem (aller-retour MySQL) : 4 aller-retours reseau. Ligne 126 `has(J, 100000)` -> true.

T2 - Serveur A : ZAuctionManager.purchaseAuctionItem allonge encore la fenetre : l.784 `storageManager.getPlayerName(...)` est une requete JDBC BLOQUANTE, executee precisement dans le cas inter-serveurs (le log l.786 dit lui-meme "cross-server purchase").

T3 - Serveur B, pendant cette fenetre : J (economie partagee MySQL/RedisEconomy/CoinsEngine) depense son solde -- un `/pay`, un achat en boutique, ou simplement la taxe de vente prelevee par SellService.java:346 sur un `/ah sell` concurrent. Solde reel : 0.

T4 - Serveur A : ZAuctionManager.java:802 `withdraw(J, 100000)`. VaultProvider appelle `withdrawPlayer`, qui retourne `EconomyResponse(FAILURE, "Insufficient funds")` -- AUCUNE exception, donc le try/catch l.803 ne se declenche pas, et la valeur de retour est jetee.

T5 - Serveur A : le flux continue comme si le paiement avait eu lieu -- l.823 `deposit(vendeur, 100000)` (ou l.848 une ligne PENDING de 100 000), l.875 `removeItem(LISTED)`, l.884 `giveItem(acheteur)`.

Bilan : l'acheteur obtient l'item gratuitement et 100 000 sont credites au vendeur a partir de rien.

Variante 100 % mono-serveur, declenchable a volonte : ConfirmPurchaseButton n'attend pas le future et ne ferme pas l'inventaire ; J confirme l'item 42 (1000), revient en arriere, confirme l'item 77 (1000). Deux items differents = deux verrous differents = aucune exclusion ; les deux chaines voient `has(1000)` true avant que la premiere n'atteigne la l.802. Deux items pour un seul paiement.

**Impact** — Item gratuit ET creation monetaire au profit du vendeur, sans aucun log d'erreur : la transaction acheteur est meme enregistree avec un `after` recalcule par une nouvelle lecture de solde (ZAuctionManager.java:839-841), ce qui masque l'anomalie dans l'historique. Le meme aveuglement frappe tous les depots (l.823 vendeur, l.827 remboursement acheteur, ClaimService.java:71) : un remboursement qui echoue silencieusement est indetectable.

**Précision apportée par la contre-expertise**

Constat confirme. Deux precisions a apporter a la redaction, sans changer le fond ni la severite :

(a) Correction de reference : `VaultProvider.withdraw` est aux lignes 35-39 (et non 34-38) ; `ZAuctionEconomy.withdraw` aux lignes 91-94. Ajouter que le trou est au niveau de l'INTERFACE publique, pas seulement de l'implementation : `API/src/main/java/fr/maxlego08/zauctionhouse/api/economy/AuctionEconomy.java:94` et `:103` declarent deja `void deposit(...)` / `void withdraw(...)`. La correction proposee (point 1, passage a `boolean` avec surcharges `void` @Deprecated) reste donc la bonne porte d'entree.

(b) Renfort important pour la variante mono-serveur, absent du constat : `ZStorageManager.java:176-177` -- `public CompletableFuture<Item> selectItem(int id) { return CompletableFuture.supplyAsync(() -> {` . Comme la re-validation sous verrou passe par ce `supplyAsync`, tout ce qui suit dans `PurchaseService` (le `has()` l.126, le `thenCompose` l.129, puis `ZAuctionManager.purchaseAuctionItem` et son `withdraw` l.802) s'execute sur un thread ForkJoinPool et non sur le thread principal du serveur. Deux achats du meme joueur sur deux items differents ne sont donc serialises par AUCUN mecanisme -- ni le thread principal, ni un verrou (verifie : `auction:lock:<item>` cote Redis, `ConcurrentHashMap<Integer, UUID> itemLocks` cote `LocalAuctionClusterBridge.java:16`, tous deux par item). Cela rend la variante mono-serveur declenchable sans dependre d'une economie partagee, et rend le point 5 de la correction (set `purchasing` par UUID dans `PurchaseService`) necessaire et pas seulement defensif.

Le reste du constat -- scenario inter-serveurs, try/catch inoperant face a l'`EconomyResponse(FAILURE)` de Vault, transaction acheteur falsifiee par recalcul de solde (`ZAuctionManager.java:839-841`), et la meme cecite sur `:823`, `:827`, `ClaimService.java:71`, `SellService.java:346` -- est verifie exact ligne a ligne.

**Correctif**

````java
Rendre le mouvement d'argent verifiable de bout en bout.
1) API AuctionEconomy : passer a `boolean withdraw(UUID, BigDecimal, String)` / `boolean deposit(...)` (garder les surcharges void @Deprecated pour la compat des addons).
2) ZAuctionEconomy, tant que CurrencyProvider reste `void`, verifier par delta de solde :
```java
@Override
public boolean withdraw(UUID playerId, BigDecimal value, String reason) {
    BigDecimal before = this.currencyProvider.getBalance(playerId);
    if (before.compareTo(value) < 0) return false;
    this.currencyProvider.withdraw(playerId, value, reason);
    return before.subtract(this.currencyProvider.getBalance(playerId)).compareTo(value) >= 0;
}
```
(a terme, faire remonter l'EconomyResponse dans CurrenciesAPI : `VaultProvider.withdraw` doit retourner `this.getEconomy().withdrawPlayer(p, v).transactionSuccess()`).
3) ZAuctionManager.java:801-806 :
```java
if (!auctionEconomy.withdraw(player.getUniqueId(), buyerPays, ...)) {
    message(this.plugin, player, Message.NOT_ENOUGH_MONEY);
    throw new IllegalStateException("buyer withdraw refused for item " + auctionItem.getId());
}
```
L'exception remonte dans PurchaseService.java:156 qui relache deja le verrou et restaure le statut : l'item reste vendable, rien n'est donne, rien n'est credite.
4) Idem l.823 : si `deposit(vendeur)` rend false, rembourser l'acheteur (le code du remboursement existe deja l.827) et propager.
5) Serialiser les achats par joueur dans PurchaseService, sur le modele d'ExpireService.java:31 :
```java
private final Set<UUID> purchasing = ConcurrentHashMap.newKeySet();
if (!purchasing.add(player.getUniqueId())) return CompletableFuture.completedFuture(PurchaseResult.failure("Purchase already in progress", PurchaseFailReason.LOCK_FAILED));
// ... whenComplete((r, t) -> purchasing.remove(player.getUniqueId()))
```
6) `has()` (PurchaseService.java:126) redevient un simple filtre d'UX : la verite est le retour de withdraw().
````

---

<a id="c-016"></a>

### `C-016` — L'echec de loadServerUUID() n'interrompt pas onEnable : le noeud installe le bridge Redis avec INSTANCE_UUID null et devient sourd et muet sur le bus

- [ ] **Corrigé**
- **Fichier** : `REDIS/ZAuctionHouseRedis.java:59`
- **Catégorie** : Duplication d’item — **Sévérité** : Critique — **Contre-expertise** : contesté

**Preuve dans le code**

```java
ZAuctionHouseRedis.java:59 (valeur de retour inexistante, resultat non teste)
        this.loadServerUUID();

ZAuctionHouseRedis.java:144-165 (private void : le `return` ne sort que de loadServerUUID)
    private void loadServerUUID() {
        ...
            try {
                String uuidString = new String(Files.readAllBytes(serverInfoFile.toPath())).trim();
                INSTANCE_UUID = UUID.fromString(uuidString);
            } catch (IOException | IllegalArgumentException e) {
                getLogger().severe("Failed to read UUID from server.info file.");
                this.getServer().getPluginManager().disablePlugin(this);
                return;
            }

REDIS/.../listener/RedisSubscriberRunnable.java:121 (reception)
            if (ZAuctionHouseRedis.INSTANCE_UUID == null || redisMessage.serverId().equals(ZAuctionHouseRedis.INSTANCE_UUID)) return;

REDIS/.../ZAuctionHouseRedis.java:274-275 (emission : serverId null serialise explicitement, cf. getGsonBuilder() ligne 261 qui active serializeNulls())
    public <T> void sendMessage(T message) {
        String jsonMessage = this.gson.toJson(new RedisMessage<>(INSTANCE_UUID, message, message.getClass().getName()));
```

**Chronologie**

T0 - Le fichier plugins/zAuctionHouseRedis/server.info du serveur B est vide ou corrompu (createNewFile() ligne 148 a reussi mais Files.write ligne 150 a echoue lors d'un boot precedent, disque plein, crash entre les deux, ou fichier tronque par un admin). UUID.fromString("") leve IllegalArgumentException, le SEVERE est logge et INSTANCE_UUID reste null.

T1 - onEnable CONTINUE ligne 61 : loadRedis() reussit, validateAndRegisterUUID() enregistre la cle "auction:server:null", le thread subscriber demarre, et la ligne 99 installe le RedisAuctionClusterBridge. Cote plugin principal, isDistributed() renvoie true : B se croit en cluster.

T2 - Un joueur achete l'item 42 sur A. A publie ItemBoughtMessage. Le handleMessage de B atteint la ligne 121 : la premiere clause `INSTANCE_UUID == null` est vraie -> return. B jette CE message et TOUS les autres, silencieusement (aucun log, meme pas en debug puisque le debug ne trace que la reception brute).

T3 - L'item 42 reste en StorageType.LISTED dans la memoire de B et dans le cache trie de B.

T4 - Symetriquement, B vend l'item 77 et publie {"serverId":null,...}. Sur A, RedisSubscriberRunnable.java:121 execute redisMessage.serverId().equals(...) sur un UUID null -> NullPointerException, attrapee ligne 147, SEVERE + stacktrace, message abandonne. A ne voit jamais les annonces, ventes ni suppressions de B.

T5 - Le vendeur de l'item 42, connecte sur B, clique sur son annonce fantome. RemoveService ne relit pas la base : checkAvailability interroge Redis dont l'etat est SOLD, considere disponible (RedisAuctionClusterBridge.java:164-167), le verrou s'acquiert, et l'item lui est rendu alors que l'acheteur de A l'a deja recu. Item duplique.

**Impact** — Un fichier server.info illisible transforme un noeud en trou noir : il publie des messages que personne ne peut decoder et ignore tous les messages entrants, tout en annoncant isDistributed() = true. La divergence memoire s'accumule indefiniment et chaque annonce vendue ailleurs devient un item gratuit sur ce noeud. Le seul indice est un SEVERE isole au demarrage, noye dans les logs, suivi d'un demarrage apparemment normal ("Redis connected: ...").

**Correctif**

1) Transformer loadServerUUID en `private boolean loadServerUUID()` renvoyant false sur echec, et ecrire `if (!this.loadServerUUID()) return;` ligne 59. 2) Ajouter une garde defensive juste avant la ligne 99 : `if (INSTANCE_UUID == null) { getLogger().severe("..."); getServer().getPluginManager().disablePlugin(this); return; }`. 3) Rendre l'ecriture du fichier atomique : ecrire dans server.info.tmp puis Files.move(..., ATOMIC_MOVE), et traiter un contenu vide comme un fichier absent (regeneration d'un UUID) plutot que comme une erreur fatale. 4) Cote reception, inverser le test pour eliminer la NPE et tracer le rejet : `UUID senderId = redisMessage.serverId(); if (senderId == null) { logger.warning("Message sans serverId, ignore"); return; } if (senderId.equals(INSTANCE_UUID)) return;`.

---

## Constats de sévérité haute (45)

<a id="c-017"></a>

### `C-017` — Achat CAPITALISM : la verification de solde ignore les regles de taxe par item, l'acheteur est preleve plus que le montant verifie

- [ ] **Corrigé**
- **Fichier** : `services/PurchaseService.java:49`
- **Catégorie** : Duplication d’argent — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
PurchaseService.java:45-53 - itemStack = null et test sur le type de l'ECONOMIE uniquement :
        var price = item.getPrice();
        var taxConfig = auctionEconomy.getTaxConfiguration();
        final BigDecimal requiredBalance;
        if (taxConfig.isEnabled() && taxConfig.getTaxType() == TaxType.CAPITALISM) {
            var taxResult = auctionEconomy.calculatePurchaseTax(player, price, null);
            requiredBalance = taxResult.hasTax() ? taxResult.finalPrice() : price;
        } else {
            requiredBalance = price;
        }

ZTaxConfiguration.java:127-128 - les regles item sont sautees des que itemStack est null :
        // Check for item-specific rules first
        if (hasItemRules() && itemStack != null) {

ZAuctionManager.java:739-744 - le prelevement reel, lui, utilise le vrai ItemStack :
        var itemStacks = auctionItem.getItemStacks();
        var representativeItem = itemStacks != null && !itemStacks.isEmpty() ? itemStacks.getFirst() : null;

        if (taxConfig.isEnabled() && (taxType == TaxType.PURCHASE || taxType == TaxType.BOTH || taxType == TaxType.CAPITALISM)) {
            taxResult = auctionEconomy.calculatePurchaseTax(player, price, representativeItem);
```

**Chronologie**

Configuration livree dans economies.yml : economie type CAPITALISM amount 5 (5 % de TVA), item-rules.enabled true avec une regle rare_items type CAPITALISM amount 15.

T0 - Un item Rare est liste a 100000 sur le serveur A.

T1 - L'acheteur B, qui possede exactement 105000, confirme l'achat sur le serveur B.

T2 - PurchaseService:49 appelle calculatePurchaseTax(player, 100000, null). itemStack == null -> ZTaxConfiguration:128 saute entierement le bloc des regles item et applique le taux par defaut : 5 % -> requiredBalance = 105000.

T3 - PurchaseService:126 has(B, 105000) -> true. Le verrou Redis est deja pris (l.92), le statut est deja IS_BEING_PURCHASED (l.107) et diffuse a tout le cluster (l.108).

T4 - ZAuctionManager:744 recalcule la taxe avec representativeItem non nul : la regle rare_items matche -> 15 % -> buyerPays = 115000.

T5 - ZAuctionManager:802 withdraw(B, 115000) alors que B n'a que 105000. La valeur de retour du provider est ignoree -> l'achat se poursuit et le vendeur est credite. Avec un provider qui autorise le negatif, le solde de B passe a -10000.

Seconde variante, sans regle item : economie type PURCHASE + regle item type CAPITALISM. PurchaseService:48 teste le type de l'economie -> faux -> requiredBalance = price, alors que ZAuctionManager appliquera bien la TVA de la regle.

**Impact** — Le controle de solde qui protege l'achat porte sur un montant different de celui qui est reellement preleve. Selon le provider, cela donne un solde negatif (creation de monnaie) ou un withdraw en echec silencieux qui laisse l'achat se terminer et crediter le vendeur. L'acheteur n'a jamais vu le montant reel et ne recoit aucun message NOT_ENOUGH_MONEY.

**Précision apportée par la contre-expertise**

Le constat est valide sur le fond et sur son scenario principal, mais deux points de sa description doivent etre corriges.

A) LA "SECONDE VARIANTE" EST FAUSSE, A RETIRER.

Le constat affirme : "economie type PURCHASE + regle item type CAPITALISM -> requiredBalance = price, alors que ZAuctionManager appliquera bien la TVA de la regle". C'est inexact. ZAuctionManager.java:736 lit `TaxType taxType = taxConfig.getTaxType()` (le type de l'ECONOMIE) et l'aiguillage l.755-769 se fait sur cette variable, pas sur le type de la regle :

if (taxResult.hasTax()) {

if (taxType == TaxType.CAPITALISM) {

buyerPays = taxResult.finalPrice();

sellerReceives = price;

} else {

buyerPays = price;

sellerReceives = taxResult.finalPrice();

Avec une economie PURCHASE, l.756 est faux -> l.768 `buyerPays = price`, exactement egal a `requiredBalance`. L'acheteur n'est donc PAS sur-preleve dans cette variante. (Elle contient un autre bug, distinct : l.769 `sellerReceives = taxResult.finalPrice()` vaut ici `price + tax` car ZTaxConfiguration.java:162-172 a construit le TaxResult en semantique CAPITALISM -> le VENDEUR touche plus que le prix.)

B) VARIANTE MANQUANTE, PLUS GRAVE, MEME CAUSE RACINE : economie CAPITALISM + regle item de type PURCHASE (ou BOTH).

ZTaxConfiguration.java:133 accepte la regle (`ruleTaxType == TaxType.PURCHASE || ... || CAPITALISM`) et appelle `calculatePurchaseTaxInternal(..., ruleTaxType)`. Avec ruleTaxType = PURCHASE, l.173-184 renvoie `finalPrice = price - taxAmount` (semantique "ce que touche le vendeur"). De retour dans ZAuctionManager.java:756, `taxType` (celui de l'economie) vaut CAPITALISM -> l.758 interprete ce meme champ en semantique "ce que paie l'acheteur" :

item a 100000, regle PURCHASE 10 % -> buyerPays = 90000, sellerReceives = 100000.

Le plugin cree 10000 par achat, sans qu'aucune verification de solde ne soit en cause. La cause racine commune est donc double : (1) `requiredBalance` calcule sans l'ItemStack, (2) le TaxResult produit sous le type de la REGLE est reinterprete sous le type de l'ECONOMIE.

C) PORTEE ELARGIE : le meme calcul fautif est duplique dans le pre-controle de l'ecran de confirmation, ListedItemsButton.java:185-193 :

if (taxConfig.isEnabled() && taxConfig.getTaxType() == TaxType.CAPITALISM) {

var taxResult = economy.calculatePurchaseTax(player, price, null);

requiredBalance = taxResult.hasTax() ? taxResult.finalPrice() : price;

} else {

requiredBalance = price;

}

economy.has(player.getUniqueId(), requiredBalance).whenComplete(...

Toute correction doit couvrir ce site aussi, sinon le joueur voit encore un montant errone avant confirmation.

D) IMPACT REEL A PRECISER. Le constat evoque "un provider qui autorise le negatif". Le cas dominant est plus grave et silencieux : `withdraw` est `void` sur toute la chaine (ZAuctionEconomy.java:92-94 -> CurrencyProvider.java:29 -> VaultProvider.java:36-39 qui jette l'`EconomyResponse` de `withdrawPlayer`). Sous Vault/EssentialsX un solde insuffisant renvoie FAILURE sans debiter et sans exception : le try/catch de ZAuctionManager.java:801-806 ne se declenche pas, l'achat se poursuit, le vendeur est credite (l.823) et l'acheteur recoit l'item sans avoir paye. C'est de la creation nette de monnaie, pas seulement un solde negatif.

E) CORRECTION RECOMMANDEE. La proposition 1) du constat est insuffisante : elle corrige l'ItemStack manquant mais conserve le test `taxConfig.getTaxType() == TaxType.CAPITALISM` cote appelant et laisse entier le probleme B (semantique de `finalPrice` decidee par le type de l'economie alors que le montant a ete calcule avec le type de la regle). Retenir la proposition 2), en la renforcant :

- Faire porter par TaxResult (ou par un record dedie) le type de taxe EFFECTIVEMENT applique (regle ou defaut), au lieu de le rededuire de `taxConfig.getTaxType()` en ZAuctionManager.java:756 ;

- Extraire une methode unique `computePurchaseAmounts(Player, AuctionItem)` renvoyant `(buyerPays, sellerReceives, taxResult)`, appelee par PurchaseService.java:45-53, ListedItemsButton.java:185-188 et ZAuctionManager.java:734-780, ou mieux calculee une seule fois et passee a `purchaseItem` ;

- Verifier le solde sur `buyerPays` exactement, et non sur une valeur derivee separement.

En complement, faire remonter l'echec du retrait : le contrat `void withdraw(...)` (AuctionEconomy.java:103) rend structurellement impossible la detection d'un debit refuse ; a defaut de le changer, relire le solde apres retrait et annuler la transaction si le debit n'a pas eu lieu.

**Correctif**

```java
Calculer le montant du une seule fois, avec le vrai ItemStack, et le transmettre au flux d'achat.
1) Dans PurchaseService:45-53 :
    ItemStack representative = (item instanceof AuctionItem ai && ai.getItemStacks() != null && !ai.getItemStacks().isEmpty())
            ? ai.getItemStacks().getFirst() : null;
    TaxResult taxResult = taxConfig.isEnabled()
            ? auctionEconomy.calculatePurchaseTax(player, price, representative)
            : TaxResult.disabled(price);
    final BigDecimal requiredBalance = taxResult.hasTax() && taxConfig.getTaxType() == TaxType.CAPITALISM ? taxResult.finalPrice() : price;
2) Mieux : extraire ZAuctionManager.computePurchaseAmounts(Player, AuctionItem) renvoyant un record (buyerPays, sellerReceives, taxResult) et l'appeler des deux cotes avec exactement les memes arguments, ou passer le TaxResult a purchaseItem pour que ZAuctionManager:734-780 ne recalcule plus rien.
```

---

<a id="c-018"></a>

### `C-018` — Bridge zombie apres onDisable de l'addon : le plugin principal conserve un RedisAuctionClusterBridge dont le pool Jedis est ferme, sans aucun chemin de recuperation

- [ ] **Corrigé**
- **Fichier** : `REDIS/ZAuctionHouseRedis.java:137`
- **Catégorie** : Fiabilité — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZAuctionHouseRedis.java:136-142 (fin de onDisable : aucun appel a setAuctionClusterBridge)
        // Close Jedis pool
        if (this.jedisPool != null) {
            this.jedisPool.close();
        }

        context.shutdown();
    }

Bytecode de redis.clients.jedis.util.Pool (jedis-5.2.0.jar, la version declaree dans plugin.yml) :
  public void close();  ->  invokevirtual destroy()  ->  GenericObjectPool.close()
  public T getResource();
     0: invokespecial GenericObjectPool.borrowObject()
     ...
     8: astore_1
     9: new  redis/clients/jedis/exceptions/JedisException
    13: ldc  "Could not get a resource from the pool"
    19: athrow
    Exception table: from 0 to 4 target 8 Class java/lang/Exception

Toutes les methodes du bridge commencent par cet emprunt, ex. RedisAuctionClusterBridge.java:158-160 :
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {

RedisAuctionClusterBridge.java:340-342 (l'etat annonce ne change jamais)
    public boolean isDistributed() {
        return true;
    }
```

**Chronologie**

T0 - A et B tournent en cluster Redis. Un admin fait `plugman unload zAuctionHouseRedis` sur A (ou l'addon est desactive par une erreur d'un autre plugin, ou le serveur A recharge ses plugins). onDisable ferme le pool ligne 138 mais ne restaure rien : ZAuctionPlugin.auctionClusterBridge pointe toujours sur le RedisAuctionClusterBridge devenu inutilisable.

T1 - Sur A, un joueur clique "acheter" : PurchaseService.java:82 appelle checkAvailability -> jedisPool.getResource() sur un GenericObjectPool ferme -> JedisException -> le future echoue -> PurchaseService.java:156 .exceptionally -> PurchaseResult INTERNAL_ERROR. Tous les achats de A sont refuses, definitivement.

T2 - Idem pour RemoveService.java:208 (checkAvailabilityStep) : plus aucun retrait, plus aucun "remove all" (chaque echec INTERNAL_ERROR stoppe la chaine, ZAuctionManager.java:1128).

T3 - isDistributed() renvoie toujours true : ExpireService.java:51 route toute expiration LISTED vers expireListedItemClustered, dont le checkAvailability echoue aussi. ExpireService.java:300-302 logge un WARNING et l'item n'est jamais expire. Comme l'expiration est declenchee par le rendu des inventaires, chaque ouverture de l'hotel des ventes reproduit le warning.

T4 - Sur A, une vente reussit encore (SellService n'a pas de garde bridge) mais SellService.java:391 echoue : B ne verra jamais l'annonce. Symetriquement le thread subscriber de A a ete arrete (onDisable ligne 117) : A n'apprend plus rien de B.

T5 - L'admin refait `plugman load zAuctionHouseRedis`. Un NOUVEAU bridge est installe, mais rien ne reconcilie l'etat memoire : les items vendus sur B pendant la coupure sont toujours en LISTED dans la memoire de A, et l'etat Redis de ces items vaut SOLD, que checkAvailability (RedisAuctionClusterBridge.java:164-167) considere comme disponible. Le vendeur qui clique sur son annonce fantome sur A la recupere alors qu'elle a deja ete livree a un acheteur sur B.

**Impact** — Un simple unload/reload de l'addon (ou toute erreur qui declenche son onDisable) gele definitivement les achats, les retraits et les expirations du serveur, avec pour seul remede un redemarrage complet du serveur Minecraft. Aucun code ne detecte ni ne repare cet etat : il n'existe ni commande de reload cote addon, ni verification periodique de sante du bridge, ni resynchronisation de la memoire au (re)branchement du bus. Les verrous encore detenus par ce noeud ne sont jamais relaches explicitement : ils ne disparaissent qu'au bout du lock-ttl-seconds.

**Correctif**

1) Ajouter a l'API `AuctionPlugin` une methode `resetAuctionClusterBridge()` qui reinstalle le bridge par defaut, et memoriser ce dernier dans un champ final de ZAuctionPlugin (`private final AuctionClusterBridge defaultClusterBridge = new LocalAuctionClusterBridge();`). L'appeler en premiere instruction de ZAuctionHouseRedis.onDisable, AVANT jedisPool.close(). 2) Avant de fermer le pool, relacher les verrous detenus par ce noeud (garder un Set<Integer> des ids verrouilles par le bridge et faire un UNLOCK_SCRIPT sur chacun). 3) Ajouter dans le bridge un compteur d'echecs consecutifs : au-dela d'un seuil, logger SEVERE et marquer le bridge degrade pour que le plugin principal puisse decider de refuser les actions plutot que de tourner en rond. 4) Prevoir une resynchronisation au (re)branchement du subscriber : recharger depuis la base les items LISTED modifies recemment plutot que de faire confiance a la memoire locale.

---

<a id="c-019"></a>

### `C-019` — Creation d'argent deterministe : une regle de taxe par item de type CAPITALISM sur une economie PURCHASE/BOTH fait toucher au vendeur price + taxe pendant que l'acheteur ne paie que price

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:769`
- **Catégorie** : Duplication d’argent — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZAuctionManager.java:743-769 -- la branche est choisie sur le type de l'ECONOMIE (`taxType`, lu l.735 `taxConfig.getTaxType()`), jamais sur celui de la regle item :
        if (taxConfig.isEnabled() && (taxType == TaxType.PURCHASE || taxType == TaxType.BOTH || taxType == TaxType.CAPITALISM)) {
            taxResult = auctionEconomy.calculatePurchaseTax(player, price, representativeItem);
...
        if (taxResult.hasTax()) {
            if (taxType == TaxType.CAPITALISM) {
                buyerPays = taxResult.finalPrice(); // This is price + tax for CAPITALISM
                sellerReceives = price;
            } else {
                // PURCHASE or BOTH: buyer pays full price, seller receives price - tax
                buyerPays = price;
                sellerReceives = taxResult.finalPrice(); // This is price - tax for PURCHASE/BOTH

ZTaxConfiguration.java:127-136 -- la regle item impose SON type au calcul :
        if (hasItemRules() && itemStack != null) {
            ItemTaxRule matchingRule = findMatchingItemRule(itemStack);
            if (matchingRule != null) {
                TaxType ruleTaxType = matchingRule.getTaxType();
                if (ruleTaxType == TaxType.PURCHASE || ruleTaxType == TaxType.BOTH || ruleTaxType == TaxType.CAPITALISM) {
                    return calculatePurchaseTaxInternal(player, price, matchingRule.getAmountType(),
                            matchingRule.getAmount(), ruleTaxType);

ZTaxConfiguration.java:162-172 -- finalPrice devient price + taxe :
        if (calcTaxType == TaxType.CAPITALISM) {
            BigDecimal buyerPays = price.add(baseResult.taxAmount());
            return new TaxResult(baseResult.taxAmount(), baseResult.taxPercentage(), price, buyerPays, false, baseResult.isReduced(), baseResult.reductionPercentage());

API/.../api/tax/TaxResult.java : le record ne transporte AUCUN champ de type applique --
        BigDecimal taxAmount, double taxPercentage, BigDecimal originalPrice, BigDecimal finalPrice, boolean isBypassed, boolean isReduced, double reductionPercentage

src/main/resources/economies.yml:136 `type: SELL` (defaut de l'economie) et :192 `#   type: CAPITALISM` -- la doc livree propose explicitement une regle item CAPITALISM sur une economie d'un autre type.
```

**Chronologie**

Le bug est DETERMINISTE : il ne demande ni concurrence ni multi-serveur, mais il se propage identiquement sur tout le reseau (chaque noeud recalcule la taxe a l'achat, et en cluster le montant errone est fige dans la ligne transactions PENDING).

Configuration : economie `vault` avec `tax.type: PURCHASE`, `tax.item-rules.enabled: true`, et une regle `rare_items` de type CAPITALISM a 15 %.

T0 - Serveur A : le vendeur V liste a 100 000 un item portant le lore que matche `rare_items`.

T1 - Serveur B : l'acheteur B confirme. PurchaseService.java:47-53 : `taxConfig.getTaxType()` vaut PURCHASE, donc `requiredBalance = price` = 100 000 ; le `has()` l.126 ne verifie que 100 000.

T2 - ZAuctionManager.java:743 : la garde passe (taxType == PURCHASE) -> l.744 `calculatePurchaseTax(player, 100000, representativeItem)`.

T3 - ZTaxConfiguration.java:129-135 : `rare_items` matche, ruleTaxType == CAPITALISM -> calculatePurchaseTaxInternal(..., CAPITALISM) -> l.163 `buyerPays = price.add(taxAmount)` -> TaxResult.finalPrice = 115 000. Le record ne dit nulle part que le calcul etait CAPITALISM.

T4 - Retour en ZAuctionManager.java:756 : `taxType` (celui de l'ECONOMIE) vaut PURCHASE, pas CAPITALISM -> branche `else` l.766 -> `buyerPays = 100000` (l.768) et `sellerReceives = taxResult.finalPrice()` = 115 000 (l.769).

T5 - l.802 `withdraw(acheteur, 100000)`. l.823 `deposit(vendeur, 115000)`, ou l.848 une ligne transactions PENDING de 115 000 si le vendeur est sur un autre serveur.

Bilan : 15 000 crees ex nihilo a chaque vente matchant la regle. Deux comptes complices sur deux serveurs automatisent une inflation illimitee.

Cas symetrique, meme cause : economie `type: CAPITALISM` + regle item `type: PURCHASE` -> finalPrice = price - taxe, ZAuctionManager.java:756 prend la branche CAPITALISM -> `buyerPays = price - taxe` et `sellerReceives = price` : l'acheteur paie moins que ce que touche le vendeur.

**Impact** — Creation monetaire nette a chaque achat, sans aucune condition de course, a partir d'une configuration explicitement documentee dans le fichier livre. En cluster le montant errone est en plus fige dans la ligne transactions PENDING (ZAuctionManager.java:848) et reclame plus tard tel quel. Corollaire de la meme cause : une regle item CAPITALISM sur une economie `type: SELL` (le defaut d'economies.yml:136) est silencieusement ignoree a l'achat, car la garde l.743 ne teste que le type de l'economie.

**Précision apportée par la contre-expertise**

Le mecanisme decrit est exact ligne par ligne. Deux precisions de description, dont une qui justifie de ramener la severite de "critique" a "haute" :

1) ATTEIGNABILITE (correction principale). L'impact affirme "a partir d'une configuration explicitement documentee dans le fichier livre" surestime la portee. La combinaison effectivement livree dans economies.yml est `type: SELL` (l.136) + `item-rules.enabled: false` (l.170) + `rules: []` (l.175), l'exemple `rare_items` CAPITALISM (l.190-194) etant commente. Sur cette configuration livree la creation monetaire N'A PAS lieu : avec taxType == SELL la garde ZAuctionManager.java:743 est fausse, taxResult reste `TaxResult.disabled(price)` et l'on tombe dans le `else` l.777-779 (`buyerPays = sellerReceives = price`). C'est le COROLLAIRE du constat (regle CAPITALISM silencieusement ignoree) qui se produit alors, pas la frappe de monnaie. Pour minter, l'administrateur doit en plus basculer l'economie sur `type: PURCHASE` ou `BOTH` tout en activant une regle item CAPITALISM -- une combinaison plausible, non validee et non signalee comme dangereuse, mais qui n'est pas l'etat par defaut. Aucun joueur ne peut declencher le bug seul sur une installation par defaut : c'est une bombe a retardement de configuration, pas un exploit joueur immediat. D'ou "haute" plutot que "critique". Une fois la configuration en place, en revanche, tout le reste du constat tient : deterministe, sans concurrence, exploitable en boucle par deux comptes complices, et fige en PENDING en cluster.

2) LIEN AVEC LE MULTI-SERVEUR. Le titre range le constat en "duplication-argent" multi-serveurs ; le multi-serveur n'est ni necessaire ni aggravant sur le calcul lui-meme (chaque noeud recalcule la meme valeur erronee). Il n'ajoute que la persistance : ZAuctionManager.java:848 ecrit `finalSellerReceives` errone dans une ligne transactions PENDING, que ClaimService redepose ensuite verbatim. Le constat le dit correctement dans son scenario ; il faut juste ne pas lire le titre comme impliquant une condition de course.

3) ELEMENT A AJOUTER A LA CORRECTION PROPOSEE. Le point 6 vise juste mais est incomplet : PurchaseService.java:49 appelle `calculatePurchaseTax(player, price, null)` avec un itemStack NUL, donc la pre-verification de solde ne voit jamais aucune regle item, meme dans une configuration parfaitement coherente (economie CAPITALISM + regle CAPITALISM a un taux different -> requiredBalance calcule au taux de l'economie et non de la regle). La correction doit donc aussi faire remonter l'ItemStack representatif jusqu'a PurchaseService, pas seulement remplacer le test de type. Meme remarque pour l'affichage : ListedItemsButton.java:187 passe egalement `null`, donc le prix affiche a l'acheteur ignore les regles item.

Les points 1 a 5 et 7 de la correction proposee sont valides et suffisants pour supprimer la creation monetaire.

**Correctif**

Le TaxResult doit transporter le type REELLEMENT applique, et ZAuctionManager ne doit plus jamais rebrancher sur le type de l'economie.
1) API/.../api/tax/TaxResult.java : ajouter `TaxType appliedType` au record (null pour `bypassed()` / `disabled()`).
2) ZTaxConfiguration.calculatePurchaseTaxInternal : renseigner `appliedType = calcTaxType` dans les deux `new TaxResult(...)` (l.164 et l.176).
3) Exposer directement le resultat metier pour supprimer toute reinterpretation :
```java
public BigDecimal buyerPays()      { return appliedType == TaxType.CAPITALISM ? originalPrice.add(taxAmount) : originalPrice; }
public BigDecimal sellerReceives() { return appliedType == TaxType.CAPITALISM ? originalPrice : originalPrice.subtract(taxAmount); }
```
4) ZAuctionManager.java:755-780 devient `buyerPays = taxResult.buyerPays(); sellerReceives = taxResult.sellerReceives();`, le choix du message TAX_CAPITALISM_INFO / TAX_PURCHASE_APPLIED se faisant sur `taxResult.appliedType()`.
5) Remplacer la garde l.743 par `if (taxConfig.isEnabled())` : calculatePurchaseTax sait deja rendre `disabled()` quand rien ne s'applique (ZTaxConfiguration.java:145-147).
6) PurchaseService.java:47-53 doit lui aussi calculer requiredBalance via `taxResult.buyerPays()` au lieu de tester `taxConfig.getTaxType() == CAPITALISM`.
7) Ajouter un garde-fou juste avant le withdraw : `if (sellerReceives.compareTo(buyerPays) > 0) { logger.severe(...); throw new IllegalStateException("tax config would mint money"); }`

---

<a id="c-020"></a>

### `C-020` — Creation d'une vente : 1 + N INSERT hors transaction, la ligne items existe avant son contenu

- [ ] **Corrigé**
- **Fichier** : `storage/ZStorageManager.java:140`
- **Catégorie** : Duplication d’item — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```sql
ZStorageManager.java:140-145 :
    public CompletableFuture<AuctionItem> createAuctionItem(Player seller, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        return CompletableFuture.supplyAsync(() -> {
            int itemId = with(ItemRepository.class).create(seller, ItemType.AUCTION, price, expiredAt, auctionEconomy);
            return with(AuctionItemRepository.class).create(seller, itemId, price, expiredAt, itemStacks, auctionEconomy);
        }, this.plugin.getExecutorService());
    }

AuctionItemRepository.java:31-37 - une ligne par ItemStack, chacune en autocommit :
        for (ItemStack itemStack : itemStacks) {
            insert(schema -> {
                schema.object("item_id", itemId);
                schema.string("itemstack", Base64ItemStack.encode(itemStack));
            });
        }
```

**Chronologie**

T0 - Serveur A : le joueur vend un lot de 3 stacks. SellService retire les 3 stacks de son inventaire.

T1 - INSERT INTO items (... storage_type='LISTED') reussit, id=42.

T2 - Les 2 premiers INSERT INTO auction_items reussissent. Le troisieme echoue (connexion MySQL coupee, max_allowed_packet depasse par un shulker plein encode en Base64, timeout du pool). Repository.insert(Consumer) ne rattrape que SQLException ; Sarah leve une DatabaseException (RuntimeException) qui traverse et fait echouer le future.

T3 - SellService.exceptionally rend les 3 ItemStack au joueur.

T4 - Etat final : le joueur a ses 3 stacks ET la base contient l'item 42 en LISTED avec 2 stacks. Au demarrage suivant, ou sur le serveur B qui charge la meme base, l'item 42 apparait en vente avec 2 stacks. Duplication de 2 stacks.

Variante : crash du process entre T1 et T2. La ligne items reste LISTED avec ZERO ligne auction_items. Au boot, ZAuctionItem.getItemStack prend la branche size() != 1 et affiche l'item special 'bulk', donc l'item est achetable ; a l'achat, ZAuctionManager.giveItem:929-931 boucle sur une liste vide : l'acheteur paie et ne recoit rien.

**Impact** — Duplication d'items lors d'un echec en milieu de boucle, perte seche d'argent pour l'acheteur d'une ligne orpheline, et pollution permanente de la table (aucun DELETE FROM items n'existe dans le projet). La fenetre est aussi observable en lecture : un selectItem(42) concurrent depuis un autre serveur (ItemListedListener:31) peut renvoyer un item aux itemStacks vides.

**Précision apportée par la contre-expertise**

Le constat est valide sur le fond, mais TROIS points doivent etre rectifies.

A) FAUX — la fenetre n'est PAS observable via ItemListedListener. Le constat ecrit : "La fenetre est aussi observable en lecture : un selectItem(42) concurrent depuis un autre serveur (ItemListedListener:31) peut renvoyer un item aux itemStacks vides." C'est incorrect. `notifyItemListed` n'a qu'un seul appelant, SellService.java:391, situe dans `postSell(...)`, lui-meme appele uniquement depuis le `.thenAccept` du chemin de SUCCES (SellService.java:108-110). Sur le chemin d'echec, aucun message Redis n'est publie, donc ItemListedListener.onMessage (ItemListedListener.java:31) ne se declenche jamais pour l'item 42. Le vecteur reel n'est pas la lecture concurrente mais le RECHARGEMENT : la ligne partielle est ramassee au demarrage suivant de n'importe quel serveur par AuctionLoader.loadItems -> ItemRepository.select() (qui ne filtre que `storage_type != DELETED`) -> ItemLoaderUtils.createItems. Cette formulation-la est correcte et suffit a etablir la duplication.

B) IMPRECIS — le correctif propose ne compile pas contre l'API Sarah actuelle. `with(ItemRepository.class).createOn(tx.getConnection(), ...)` n'existe pas et ne peut pas exister tel quel : Sarah/database/Executor.java:17 declare `int execute(DatabaseConnection, DatabaseConfiguration, Logger)` — il n'y a AUCUNE surcharge acceptant un `java.sql.Connection`. Pire, InsertRequest.java:54 ouvre sa propre connexion (`try (Connection connection = databaseConnection.getConnection(); ...)`), donc meme en ouvrant une Transaction, chaque INSERT emprunterait une AUTRE connexion du pool et resterait hors transaction : la classe `Transaction` est aujourd'hui inutilisable avec SchemaBuilder/Repository. Appliquer la transaction exige d'abord d'ajouter du plumbing dans Sarah (Executor/Schema acceptant une Connection fournie) puis dans Repository. En revanche, la "parade minimale" proposee (INSERT items en storage_type='DELETED', puis les N contenus, puis `UPDATE items SET storage_type='LISTED' WHERE id=? AND storage_type='DELETED'`) EST implementable en l'etat et se marie bien avec ItemRepository.select() qui exclut deja DELETED, et avec le garde conditionnel deja present dans ItemRepository.createUpdateSchema. C'est le correctif a retenir en priorite.

C) A NUANCER — le declencheur "max_allowed_packet depasse par un shulker plein encode en Base64" est plus faible qu'annonce. CreateAuctionItemMigration.java declare `table.longText("itemstack")` (LONGTEXT, 4 Go), donc il n'y a pas de debordement de colonne exploitable a la demande par un joueur. Les declencheurs realistes restent : perte de connexion MySQL, timeout/epuisement du pool Hikari, deadlock, redemarrage MySQL, et surtout CRASH ou kill du process entre l'INSERT items et la fin de la boucle — fenetre d'autant plus large que la boucle fait N allers-retours SQL sequentiels, chacun avec acquisition de connexion (jusqu'a plusieurs dizaines de stacks depuis la sell-inventory).

D) BONNE NOUVELLE non mentionnee, qui borne le rayon d'action : le meme decalage SQLException/DatabaseException s'applique a `Repository.insertSync` (Repository.java:135-141), dont le `return -1` est donc aussi du code mort. Consequence : si c'est le PREMIER INSERT (table items) qui echoue, la DatabaseException remonte immediatement et aucune ligne auction_items n'est ecrite avec un `item_id = -1`. L'etat est alors propre cote base (le joueur recupere ses items, rien en base). Le probleme se limite strictement a un echec survenant APRES la creation de la ligne items.

Severite : je descends "critique" a "haute". Il s'agit d'un defaut d'atomicite/durabilite mono-serveur, pas de la course multi-serveurs qui est le coeur de l'audit ; il n'est pas reproductible a volonte par un joueur (aucun declencheur sous son controle une fois le point C corrige) et exige une panne d'infrastructure ou un crash. Mais quand il se declenche, la consequence est bien une duplication d'items reelle, une ligne fantome achetable qui vole l'argent de l'acheteur, et une pollution definitive de la base sans aucun chemin de nettoyage — d'ou "haute" et non "moyenne".

Ajout recommande, non present dans le code : dans ItemLoaderUtils.createItems, pour `case AUCTION`, logger SEVERE et `continue` si `currentAuctionItems.isEmpty()` — cela neutralise a lui seul le scenario "l'acheteur paie et ne recoit rien" pour toutes les lignes orphelines deja presentes en production.

**Correctif**

```java
Envelopper les N+1 INSERT dans une transaction Sarah :

    return CompletableFuture.supplyAsync(() -> {
        try (Transaction tx = this.databaseConnection.beginTransaction()) {
            int itemId = with(ItemRepository.class).createOn(tx.getConnection(), seller, ItemType.AUCTION, price, expiredAt, auctionEconomy);
            var item = with(AuctionItemRepository.class).createOn(tx.getConnection(), seller, itemId, price, expiredAt, itemStacks, auctionEconomy);
            tx.commit();
            return item;
        }
    }, this.plugin.getExecutorService());

(Transaction.close() fait deja le rollback si commit n'a pas ete appele.) A defaut de transaction, la parade minimale : creer la ligne items avec storage_type='DELETED', inserer les N contenus, puis un dernier UPDATE items SET storage_type='LISTED' WHERE id=? AND storage_type='DELETED' qui ne rend la vente visible que quand son contenu est complet. Ajouter en outre dans AuctionLoader un log SEVERE + skip pour tout item AUCTION dont la liste d'auction_items est vide.
```

---

<a id="c-021"></a>

### `C-021` — ItemListedListener re-ajoute l'item en LISTED apres un aller-retour DB non ordonne : un item vendu entre-temps ressuscite en memoire

- [ ] **Corrigé**
- **Fichier** : `zAuctionHouse Redis/redis/listener/listeners/ItemListedListener.java:31`
- **Catégorie** : Cohérence de cache — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// ItemListedListener.java:31-48 -- aucune verification que l'item est toujours LISTED au moment du re-ajout
auctionPlugin.getStorageManager().selectItem(id).thenAccept(item -> {

    if (item == null) {
        this.plugin.getLogger().severe("Unable to find the item " + message.itemId());
        return;
    }

    var manager = auctionPlugin.getAuctionManager();
    auctionPlugin.getCategoryManager().applyCategories(item);

    // Execute cache operations on the main thread for thread safety
    auctionPlugin.getScheduler().runNextTick(wrappedTask -> {

        manager.addItem(StorageType.LISTED, item);
        manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);

        manager.updateListedItems(item, true, null);
    });
})

// ZStorageManager.java:176-177 -- selectItem part sur ForkJoinPool.commonPool, latence non bornee
public CompletableFuture<Item> selectItem(int id) {
    return CompletableFuture.supplyAsync(() -> {

// ItemBoughtListener.java:44-50 -- la suppression concurrente s'applique, elle, des le tick suivant
auctionPlugin.getScheduler().runNextTick(wrappedTask -> {
    var manager = auctionPlugin.getAuctionManager();
    manager.removeItem(StorageType.LISTED, id);
```

**Chronologie**

Les deux listeners appliquent leurs effets par des chemins de latence differentes -- suppression immediate au tick suivant contre re-ajout apres un SELECT JDBC -- sans aucun ordonnancement entre eux.

T0 -- Serveur A : un joueur met l'item 42 en vente. notifyItemListed publie ItemListedMessage.

T1 -- Serveur B recoit le message et lance selectItem(42) sur le commonPool. Le pool est sature (il porte deja tout l'IO Jedis et JDBC du bridge), la reponse met plusieurs secondes.

T2 -- Serveur C achete l'item 42. La ligne passe a DELETED et ItemBoughtMessage est publie.

T3 -- Serveur B recoit ItemBoughtMessage et execute manager.removeItem(StorageType.LISTED, 42) au tick suivant : l'item n'est pas encore present, la suppression ne fait rien.

T4 -- Le selectItem de B revient enfin (il avait lu la ligne AVANT le passage a DELETED, ou la lit apres et retourne null selon le timing) et, dans le cas ou il retourne un item, execute addItem(StorageType.LISTED, item) : l'item 42, deja vendu et livre, redevient une annonce vivante sur B, et updateListedItems le REAFFICHE dans les inventaires ouverts.

T5 -- Le vendeur, connecte sur B, retire ce fantome via son onglet "en vente". RemoveService ne relit jamais la base sous verrou et Redis considere SOLD comme disponible : il recupere un second exemplaire de l'item deja livre a l'acheteur.

**Impact** — Fantome LISTED persistant sur un noeud, exploitable en item gratuit via le chemin de retrait qui ne revalide pas. Ni la suppression ni le re-ajout ne portent d'horodatage ou de generation permettant d'ecarter le message perime.

**Précision apportée par la contre-expertise**

Le bug est réel mais son scénario dominant n'est pas celui décrit, et son exploitabilité est conditionnelle.

A) Le cas large n'exige aucune course serrée (le constat le sous-estime).

`ItemRepository.java:51-56` :

```java

public Optional<ItemDTO> select(int id) {

return select(ItemDTO.class, schema -> {

schema.where("id", id);

schema.where("storage_type", "!=", StorageType.DELETED.name());

}).stream().findFirst();

}

```

Avec `purchased-item.give-item: false`, l'achat met la ligne en PURCHASED (ZAuctionManager.java:891-893 `addItem(StorageType.PURCHASED, ...)` + `updateItem(auctionItem, StorageType.PURCHASED)`), pas en DELETED. `selectItem` retourne donc TOUJOURS l'item ; `ItemLoaderUtils.createAuctionItem` (ligne 42-47) lui pose `ItemStatus.PURCHASED` et `ZStorageManager.java:198-200` renseigne l'acheteur — et ItemListedListener fait quand même `addItem(StorageType.LISTED, item)`. Il suffit que le `selectItem` en vol se résolve APRÈS le commit de l'achat : pas d'entrelacement au tick près. À l'inverse, la branche « retourne null » que le constat évoque comme aggravante est en fait la branche SÛRE : avec `give-item: true` la ligne est DELETED, `select` la filtre, le listener sort avec un log SEVERE et aucun fantôme n'est créé.

B) L'escalade T5 (second exemplaire gratuit) n'est PAS inconditionnelle.

Elle ne vaut que pour le sous-cas étroit où le SELECT a lu la ligne encore LISTED → statut AVAILABLE, acheteur null. Dans le cas large (statut PURCHASED), `RemoveService.java:96` (`removeSellingItem`) et `:55` (`removeListedItem`) rejettent avec `RemoveFailReason.INVALID_ITEM_STATUS`, et `SortedItemsCache.java:305` (`if (item.getStatus() == ItemStatus.AVAILABLE && !item.isExpired())`) exclut le fantôme de la liste publique. L'impact s'y limite alors à un fantôme PERSISTANT et IRRETIRABLE dans l'onglet « en vente » du vendeur — `getPlayerSellingItems` (ZAuctionManager.java:314) ne filtre que `getStatus() != ItemStatus.DELETED` — plus des compteurs de catégorie faussés, jusqu'au redémarrage.

Dans le sous-cas étroit (statut AVAILABLE), en revanche, l'escalade décrite se vérifie entièrement : `checkAvailability` renvoie true sur SOLD, le LOCK_SCRIPT accorde le verrou (SOLD n'est ni DELETED ni LOCKED), `createUpdateSchema` ne garde pas la transition DELETED, donc `executeRemoval` va au bout et `giveItem` livre un second exemplaire d'un item déjà remis à l'acheteur ; le fantôme étant AVAILABLE il réintègre aussi `SortedItemsCache` et peut être RACHETÉ par un tiers sur B (double encaissement pour le vendeur).

C) Correctif : la garde proposée est bonne mais incomplète. Tester `item.getStatus() != ItemStatus.AVAILABLE || item.getBuyerUniqueId() != null` sur le thread principal ferme bien le cas large (A). Il faut y ajouter le tombstone (`isRecentlyRemoved`) ou la sérialisation par identifiant d'item pour fermer le sous-cas étroit (B), où l'item relu est légitimement AVAILABLE au moment du SELECT. Deux mesures complémentaires valent le détour : passer `selectItem` sur `this.plugin.getExecutorService()` comme le font les autres méthodes de ZStorageManager (supprime la latence non bornée du commonPool), et étendre la garde de `createUpdateSchema` à la destination DELETED (`where storage_type = <source attendue>`), qui bloquerait la duplication en base même si un fantôme mémoire subsiste.

**Correctif**

```java
Verifier l'etat au moment du re-ajout, sur le thread principal, et non au moment du SELECT :

auctionPlugin.getStorageManager().selectItem(id).thenAccept(item -> {
    if (item == null) return;
    auctionPlugin.getCategoryManager().applyCategories(item);
    auctionPlugin.getScheduler().runNextTick(w -> {
        // l'item doit toujours etre LISTED et sans acheteur au moment ou on l'insere
        if (item.getStatus() != ItemStatus.AVAILABLE || item.getBuyerUniqueId() != null) return;
        // et ne pas avoir ete supprime entre-temps par un autre message
        if (auctionPlugin.getAuctionManager().isRecentlyRemoved(id)) return;
        manager.addItem(StorageType.LISTED, item);
        ...
    });
});

ou serialiser tous les listeners Redis d'un meme identifiant d'item derriere une file par item (par exemple un ConcurrentHashMap<Integer, CompletableFuture<Void>> chaine), de sorte que les effets s'appliquent dans l'ordre d'arrivee des messages.
```

---

<a id="c-022"></a>

### `C-022` — L'achat en vol survit a onDisable : asyncExecutor est ferme mais la chaine tourne sur commonPool

- [ ] **Corrigé**
- **Fichier** : `ZAuctionPlugin.java:196`
- **Catégorie** : Perte d’argent — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// ZAuctionPlugin.java:193-209
        this.auctionManager.shutdown();

        // Shutdown the async executor service
        this.asyncExecutor.shutdown();
        try {
            if (!this.asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                this.asyncExecutor.shutdownNow();
...
        this.storageManager.onDisable();

// ZStorageManager.java:156-158 -- soumission sur l'executeur ferme
    public CompletableFuture<Void> updateItem(Item item, StorageType storageType) {
        return CompletableFuture.runAsync(() -> with(ItemRepository.class).updateItem(item, storageType), this.plugin.getExecutorService());
    }

// ZAuctionManager.java:800-802 puis 875-893
        // On retire l'argent de l'acheteur
        try {
            auctionEconomy.withdraw(player.getUniqueId(), buyerPays, args(auctionEconomy.getWithdrawReason(), "%seller%", sellerName, "%items%", items));
...
        removeItem(StorageType.LISTED, auctionItem);
...
            updateFuture = storageManager.updateItem(auctionItem, StorageType.DELETED);
            giveItem(player, auctionItem);
...
            addItem(StorageType.PURCHASED, auctionItem);
            updateFuture = storageManager.updateItem(auctionItem, StorageType.PURCHASED);

// ItemRepository.java:43-45 -- au reboot, tout ce qui n'est pas DELETED revient
    public List<ItemDTO> select() {
        return select(ItemDTO.class, schema -> schema.where("storage_type", "!=", StorageType.DELETED.name()));
    }
```

**Chronologie**

T0 serveur A : un joueur confirme un achat. La chaine bascule sur ForkJoinPool.commonPool des le premier appel au bridge Redis. T1 A : un restart planifie ou /stop demarre ; ZAuctionPlugin.onDisable l.196 appelle asyncExecutor.shutdown(). commonPool est un pool global de la JVM que le plugin n'arrete pas : la chaine d'achat continue de tourner. T2 A (commonPool) : ZAuctionManager.java:802 auctionEconomy.withdraw retire l'argent de l'acheteur. T3 A (commonPool) : l.863-875, setBuyer, setStatus(PURCHASED) et removeItem(StorageType.LISTED, auctionItem) sont appliques en memoire. T4 A (commonPool) : l.883 storageManager.updateItem(auctionItem, DELETED) execute CompletableFuture.runAsync(runnable, asyncExecutor), qui appelle asyncExecutor.execute(...) sur un ThreadPoolExecutor deja shutdown() : la politique par defaut d'Executors.newFixedThreadPool est AbortPolicy, donc RejectedExecutionException est levee SYNCHRONEMENT par runAsync. T5 A : l.884 giveItem n'est jamais atteint. L'exception remonte a PurchaseService.exceptionally (l.156) qui log et retourne INTERNAL_ERROR ; ConfirmPurchaseButton.java:22 ignore totalement ce resultat, aucun message n'est envoye au joueur. exceptionally l.176 teste IS_BEING_PURCHASED mais le statut vaut deja PURCHASED, donc aucune restauration. T6 : bilan a l'arret -- argent debite, aucun item remis, ligne items toujours storage_type = 'LISTED'. T7 au redemarrage, AuctionLoader/ItemRepository.select() (WHERE storage_type != 'DELETED') recharge l'item : il est de nouveau en vente sur A, B et C. Un second acheteur peut l'acheter. Meme scenario dans la branche differee (l.892-893) : addItem(StorageType.PURCHASED) en memoire, updateItem(PURCHASED) rejete, l'item revient LISTED au reboot alors que l'acheteur a paye.

**Impact** — A chaque redemarrage sous charge : argent detruit cote acheteur et item deja paye remis en vente. Il n'existe aucun drapeau d'arret teste par PurchaseService/RemoveService/SellService, et onDisable n'attend pas les chaines en vol (il n'attend que les taches deja soumises a asyncExecutor, pas celles de commonPool qui vont encore soumettre).

**Correctif**

1) Chainer strictement la persistance avant la livraison dans purchaseAuctionItem : storageManager.updateItem(auctionItem, DELETED).thenCompose(v -> giveItem(player, auctionItem)) et n'appeler withdraw qu'apres confirmation de l'ecriture. 2) Ajouter un volatile boolean shuttingDown positionne en tete de onDisable et teste en entree de PurchaseService.purchaseItem, RemoveService.executeRemoval et SellService.sellAuctionItems, avec un message d'echec explicite au joueur. 3) Rendre la soumission robuste : public CompletableFuture<Void> updateItem(Item item, StorageType storageType) { try { return CompletableFuture.runAsync(..., this.plugin.getExecutorService()); } catch (RejectedExecutionException e) { return CompletableFuture.failedFuture(e); } } -- au minimum l'echec devient observable au lieu d'etre une exception synchrone en plein milieu de purchaseAuctionItem. 4) Ne fermer asyncExecutor qu'apres avoir attendu les chaines cluster en cours (compteur d'operations en vol).

---

<a id="c-023"></a>

### `C-023` — L'etat de confirmation n'a ni proprietaire ni TTL et sa seule liberation depend d'un onInventoryClose que zMenu differe d'un tick ou saute completement

- [ ] **Corrigé**
- **Fichier** : `buttons/confirm/ConfirmHelper.java:38`
- **Catégorie** : Perte d’item — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ConfirmHelper.java:35-38 - la restauration depend entierement du cache du joueur :
        var cache = manager.getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) return;

PlayerListener.java:41-43 - le cache est detruit des le quit :
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.getAuctionManager().removeCache(event.getPlayer());

zMenu VInventoryManager.onInventoryClose:189-203 - dispatch saute si le joueur est mort, et differe d'un tick sinon :
        if (player.isDead()) return;
        ...
            this.plugin.getScheduler().runAtEntityLater(player, () -> { ... oldInventoryEngine.onPreClose(event, this.plugin, player); }, 1);

Les seuls retours vers AVAILABLE de tout le projet sont ConfirmHelper.java:47 et :72. Aucune tache planifiee, aucun TTL, aucun balayage au demarrage, aucune remise a zero au join/quit/reload.
```

**Chronologie**

T0 - Item #42 LISTED/AVAILABLE sur A, B et C.

T1 - Un joueur sur A clique l'item : diffusion AVAILABLE -> IS_PURCHASE_CONFIRM. B et C appliquent le statut puis masquent l'item. RedisAuctionClusterBridge.notifyItemStatusChange n'ecrit AUCUNE cle Redis (simple publish) : l'etat n'existe nulle part ailleurs qu'en memoire de chaque JVM, sans TTL.

T2 - Le joueur se deconnecte (ou est kick, ou change de serveur via le proxy) alors que la fenetre de confirmation est ouverte. Bukkit emet InventoryCloseEvent puis PlayerQuitEvent dans le meme tick.

T3 - PlayerListener:43 execute removeCache(player) : ITEM_SHOW est detruit immediatement.

T4 - Un tick plus tard, la lambda differee de zMenu appelle ConfirmHelper.onInventoryClose : cache.get(ITEM_SHOW) rend null, ligne 38 return. Personne ne rediffuse AVAILABLE.

T5 - Variante sans deconnexion : le joueur meurt avec la fenetre ouverte. VInventoryManager:190 court-circuite tout le dispatch, les boutons ne recoivent jamais onInventoryClose.

T6 - Variante sans joueur : le serveur A crashe entre T1 et T2. Aucun mecanisme de reprise.

T7 - Sur A, B et C, l'item #42 reste indefiniment en IS_PURCHASE_CONFIRM : exclu de SortedItemsCache, saute par ListedItemsButton.onRender (if (!item.isActivelyListed()) continue;), et RemoveService:96 refuse au vendeur de le retirer.

**Impact** — Un item disparait de l'hotel des ventes de tout le reseau sans que personne ne puisse plus le vendre ni le retirer, sur un simple /quit ou une mort pendant la confirmation. Le vendeur ne le recupere qu'a l'expiration naturelle de l'annonce, donc apres plusieurs jours selon la config, et la vente est perdue. Le meme blocage frappe IS_REMOVE_CONFIRM.

**Précision apportée par la contre-expertise**

Constat confirmé. Quatre précisions à apporter à la rédaction :

a) Le bug n'est PAS spécifique au cluster. ListedItemsButton.java:132 et :231 posent `item.setStatus(IS_REMOVE_CONFIRM / IS_PURCHASE_CONFIRM)` en mémoire locale dans tous les cas, et LocalAuctionClusterBridge.java:51-53 `notifyItemStatusChange` est un no-op. Un serveur unique subit donc exactement le même blocage ; Redis ne fait qu'étendre le blocage à tout le réseau.

b) Point d'entrée supplémentaire non listé : PurchaseService.java:147-152 (fonds insuffisants) et :176-182 (exception/timeout) restaurent `previousStatusHolder.get()`, c'est-à-dire IS_PURCHASE_CONFIRM (capturé ligne 76 après le contrôle ligne 68), et non AVAILABLE. Un achat échoué replace donc l'item en état de confirmation, dont la libération dépend elle aussi uniquement de la fermeture d'inventaire.

c) Atténuation partielle omise : le statut n'est pas persisté. ItemLoaderUtils.java:43-47 le dérive de `storage_type` (`case LISTED -> ItemStatus.AVAILABLE`), donc un redémarrage complet d'un serveur remet ses items à AVAILABLE. Mais cela ne vaut que pour le serveur redémarré : B et C restent bloqués, et `/ah admin reload` ne recharge pas les items (ZAuctionPlugin.reload():213-232), donc il n'existe aucune remédiation à chaud.

d) Sur la variante T2, préciser le mécanisme exact : zMenu possède bien un second dispatch au quit (VInventoryManager.java:324-329, `onPreClose(null, ...)` synchrone), mais il est gardé par `topInventory.getHolder() instanceof VInventory`, or CraftBukkit remet `containerMenu = inventoryMenu` via `closeContainer()` avant de lancer PlayerQuitEvent. Ce filet ne rattrape donc pas le menu de confirmation, et le seul dispatch restant est la lambda différée d'un tick — postérieure au `removeCache` de PlayerListener:43.

La correction proposée (libération explicite dans onQuit avant removeCache + garde-fou temporel + à terme ne plus diffuser les états d'IHM) reste valide ; ajouter le même filet dans un handler de PlayerDeathEvent (ou supprimer la dépendance à ITEM_SHOW en stockant le propriétaire du verrou sur l'item lui-même) pour couvrir la variante T5.

**Correctif**

```java
1) Liberer explicitement a la deconnexion, dans PlayerListener.onQuit AVANT removeCache :
    var cache = this.plugin.getAuctionManager().getCache(player);
    Item shown = cache.get(PlayerCacheKey.ITEM_SHOW);
    if (shown != null && (shown.getStatus() == ItemStatus.IS_PURCHASE_CONFIRM || shown.getStatus() == ItemStatus.IS_REMOVE_CONFIRM)) {
        var old = shown.getStatus();
        shown.setStatus(ItemStatus.AVAILABLE);
        this.plugin.getAuctionClusterBridge().notifyItemStatusChange(shown, old, ItemStatus.AVAILABLE);
        this.plugin.getAuctionManager().clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
    }
    this.plugin.getAuctionManager().removeCache(player);
2) Poser un garde-fou temporel : memoriser l'instant d'entree en IS_*_CONFIRM et remettre AVAILABLE + diffuser au-dela de N secondes (cf. le constat sur les statuts transitoires cote receveur).
3) A terme, ne plus diffuser du tout ces etats d'interface au cluster.
```

---

<a id="c-024"></a>

### `C-024` — L'unicite de l'INSTANCE_UUID est best-effort : exception avalee au demarrage, heartbeat en simple EXPIRE, et tache portee par le scheduler du plugin principal

- [ ] **Corrigé**
- **Fichier** : `REDIS/ZAuctionHouseRedis.java:199`
- **Catégorie** : Cluster — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZAuctionHouseRedis.java:181-212 :
            String result = jedis.set(registryKey, getServer().getName(), new redis.clients.jedis.params.SetParams().nx().ex(REGISTRY_TTL_SECONDS));
            if (result == null) {
                ...
                INSTANCE_UUID = UUID.randomUUID();
                saveServerUUID();
                ...
            }
        } catch (Exception e) {
            getLogger().warning("Failed to validate UUID uniqueness in Redis: " + e.getMessage());
        }

        // Heartbeat to keep the registration alive
        this.heartbeatTask = this.auctionPlugin.getScheduler().runTimerAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.expire(REGISTRY_KEY_PREFIX + INSTANCE_UUID, REGISTRY_TTL_SECONDS);
            } catch (Exception e) {
                getLogger().warning("Failed to refresh server UUID registration: " + e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);

Et le filtre de reception repose entierement sur cet UUID (RedisSubscriberRunnable.java:121) :
            if (ZAuctionHouseRedis.INSTANCE_UUID == null || redisMessage.serverId().equals(ZAuctionHouseRedis.INSTANCE_UUID)) return;
```

**Chronologie**

Deux chemins prouves menent deux serveurs au MEME INSTANCE_UUID, ce que la detection est censee empecher.

Chemin 1 (exception avalee) : T0 - le serveur B, clone depuis le template de A (server.info identique), demarre pendant une micro-coupure Redis. loadRedis a reussi son ping mais le SET de validateAndRegisterUUID leve (timeout de 2000 ms). Le catch l.199-201 se contente d'un warning : B poursuit avec l'UUID de A, sans jamais retenter.

Chemin 2 (cle expiree) : T0 - le serveur A tourne avec l'UUID X. T1 - Redis redemarre sans persistance (ou un FLUSHDB est execute) : auction:server:X disparait. Le heartbeat de A ne fait qu'un EXPIRE sur une cle inexistante (retour 0, non teste) : la cle n'est jamais recreee. T2 - le serveur B clone demarre : son SET NX reussit puisque la cle n'existe plus, aucun doublon n'est detecte. A et B partagent l'UUID X. Variante : le heartbeat etant enregistre sur le scheduler du PLUGIN PRINCIPAL, un reload/disable de zAuctionHouse annule la tache et la cle expire au bout de 120 s, meme issue.

T3 - Chaque noeud filtre les messages de l'autre comme etant les siens (RedisSubscriberRunnable:121). A et B deviennent mutuellement aveugles tout en ecrivant dans la meme base et en partageant les memes verrous.

T4 - Le vendeur V retire l'item 42 sur A : giveItem + UPDATE DELETED + HSET state='DELETED' + publish (ignore par B). Le store LISTED de B contient toujours l'item 42.

T5 - 24 h plus tard (item-state-ttl-seconds), auction:item:42 expire. V clique l'item 42 sur B : checkAvailability -> HGET nil -> disponible, LOCK_SCRIPT -> verrou accorde, RemoveService ne revalide pas la DB -> V recupere l'item une seconde fois. Item duplique.

**Impact** — La protection annoncee contre les server.info copies - le cas le plus frequent en hebergement par template - ne couvre en realite que le cas ideal ou Redis repond et ou la cle de l'autre noeud est encore vivante. Dans les deux chemins ci-dessus, deux serveurs deviennent silencieusement invisibles l'un a l'autre, ce qui est exactement l'etat dans lequel le cluster duplique.

**Précision apportée par la contre-expertise**

Le constat est exact ; trois precisions renforcent son diagnostic sans le contredire.

(a) Aggravation du Chemin 1 : le heartbeat est enregistre a l'exterieur du bloc try (l.204 vs try l.181-201). Apres une exception avalee, le noeud n'a donc AUCUNE cle `auction:server:<uuid>` et son heartbeat, un simple `EXPIRE` sur cle inexistante, ne la creera jamais. L'invisibilite n'est pas temporaire : elle dure toute la vie du processus, et tout futur clone/redemarrage passera lui aussi le `SET NX` sans detection.

(b) Declencheur supplementaire du Chemin 2, plus banal qu'un restart : `auction:server:<uuid>` est une cle ordinaire a TTL, donc eligible a l'eviction sous `maxmemory-policy allkeys-lru`/`allkeys-random`. Une simple pression memoire Redis suffit a la faire disparaitre, avec la meme issue.

(c) Nuance sur le scenario T4/T5 : le dupe passe soit par la reclamation directe decrite, soit — si l'item a entre-temps expire cote B — par ExpireService puis `removeExpiredItem`. Dans ce second cas la clause `where("storage_type", LISTED)` d'ItemRepository:78-80 fait bien echouer l'UPDATE vers EXPIRED en base, mais le deplacement EN MEMOIRE cote B a lieu quand meme, et la reclamation qui suit appelle `updateItem(item, DELETED)`, UPDATE inconditionnel sur `id` sans controle de lignes affectees, suivi de `giveItem`. La garde EXPIRED existante ne couvre donc pas ce chemin.

Sur les correctifs proposes : les points 1 et 3 sont pertinents tels quels. Pour le point 2, ajouter que le nonce de session doit etre distinct de l'INSTANCE_UUID (sinon deux noeuds clones ecrivent la meme valeur et le test `cur ~= expected` ne detecte rien) — typiquement un UUID tire a chaque onEnable et stocke comme valeur de la cle, l'UUID restant la cle. A signaler enfin comme constat connexe distinct : `LockToken.of(item)` (API/.../cluster/LockToken.java:34-36) vaut `"item:" + id`, sans identifiant de serveur, donc le controle de propriete de UNLOCK_SCRIPT (`currentToken ~= tokenValue`) est inoperant entre serveurs — n'importe quel noeud peut liberer le verrou d'un autre.

**Correctif**

```lua
1) Rendre l'echec de validation bloquant : dans le catch l.199, installer un bridge indisponible et reessayer validateAndRegisterUUID toutes les 5 s (max 12 tentatives) avant d'autoriser l'installation du RedisAuctionClusterBridge.
2) Faire du heartbeat une reaffirmation de propriete avec un nonce de session, pas un simple EXPIRE :
    local expected = ARGV[1]
    local cur = redis.call('GET', KEYS[1])
    if cur == false then redis.call('SET', KEYS[1], expected, 'EX', 120); return 1 end
    if cur ~= expected then return 0 end
    redis.call('EXPIRE', KEYS[1], 120); return 1
   Si le script renvoie 0, un autre noeud utilise notre UUID : logger SEVERE, desactiver le bridge et regenerer l'UUID.
3) Enregistrer le heartbeat sur le scheduler de l'ADDON (Bukkit.getScheduler().runTaskTimerAsynchronously(this, ...)) et non sur celui du plugin principal.
```

---

<a id="c-025"></a>

### `C-025` — LOCK_SCRIPT reprend un verrou perime tant que l'etat vaut LOCKED, sans jeton de fencing : un detenteur lent conserve son droit d'ecriture

- [ ] **Corrigé**
- **Fichier** : `zAuctionHouse Redis/redis/RedisAuctionClusterBridge.java:51`
- **Catégorie** : Verrou distribué — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```lua
// RedisAuctionClusterBridge.java:51-66 -- la reprise du verrou perime est un choix explicite, sans compteur de generation
-- A LOCKED state only blocks acquisition while the lock key still exists.
-- If the lock key has expired (TTL), the previous holder crashed without
-- unlocking: the LOCKED state is stale and must not brick the item until the
-- item hash TTL expires (up to 30 days). Fall through to re-acquire in that case.
if currentState == 'LOCKED' and redis.call('EXISTS', lockKey) == 1 then
    return 0
end

local acquired = redis.call('SET', lockKey, tokenValue, 'NX', 'PX', ttlMs)
if not acquired then
    return 0
end

redis.call('HSET', itemKey, 'state', 'LOCKED')
redis.call('HSET', itemKey, 'lock', tokenValue)
return 1

// RedisAuctionClusterBridge.java:187-188 -- aucune generation/epoque dans le jeton ni dans le TTL
LockToken token = LockToken.of(item);
long ttlMs = lockTtl != null ? lockTtl.toMillis() : 30000;
```

**Chronologie**

Le commentaire assume que "lock key expiree = detenteur crashe". C'est faux : un detenteur simplement LENT (GC, JDBC bloquant, attente d'un tick Folia) est indistinguable d'un detenteur mort, et rien ne l'empeche d'ecrire apres l'expiration de son bail -- ni cote Redis (aucun numero de generation) ni cote base (aucun compare-and-set, ItemRepository.java:71-90 ne pose de where storage_type que pour EXPIRED).

T0 -- Serveur A acquiert le verrou de l'item 42 : SET auction:lock:42 ... NX PX 30000, HSET state=LOCKED.

T1 (T0+31s) -- A est toujours dans purchaseAuctionItem (getPlayerName synchrone ZAuctionManager.java:784, runAtEntity en attente d'un tick). auction:lock:42 a expire ; state vaut toujours LOCKED.

T2 -- Serveur B tente l'achat du meme item. LOCK_SCRIPT ligne 55 : state == LOCKED mais EXISTS lockKey == 0 -> on tombe dans le SET NX -> B acquiert. A et B sont simultanement "detenteurs" et aucun des deux ne le sait.

T3 -- La revalidation de B (PurchaseService.java:121) lit la base : A n'a pas encore ecrit son UPDATE, la ligne est encore LISTED sans acheteur -> B passe et achete.

T4 -- A reprend et ecrit son UPDATE non conditionnel. Les deux acheteurs sont servis, le vendeur est credite deux fois.

Remarque : c'est le pendant cote script du bail non renouvele. Meme si le client renouvelait son bail, ce script laisserait toujours un retardataire agir apres reprise, faute de fencing.

**Impact** — Le verrou distribue ne garantit que l'exclusion instantanee, jamais l'exclusion sur la duree de la section critique. Toute pause superieure au TTL ouvre une fenetre de double achat / double retrait que ni Redis ni MySQL ne peuvent arbitrer.

**Précision apportée par la contre-expertise**

Le constat est reel mais SOUS-ESTIME le probleme sur trois points, a corriger dans la description :

1) Le jeton n'est meme pas unique par serveur. `LockToken.of(item)` (API/.../cluster/LockToken.java:34-36) renvoie `new LockToken("item:" + auctionItem.getId())` : la valeur est identique sur TOUS les serveurs. Consequence : la verification de propriete d'UNLOCK_SCRIPT (RedisAuctionClusterBridge.java:79-82, `local currentToken = redis.call('HGET', itemKey, 'lock') ; if currentToken ~= tokenValue then return 0 end`) est un no-op inter-serveurs. Le retardataire A, en terminant, fait `DEL lockKey` + `HSET state=AVAILABLE` sur le verrou VIVANT de B. La prise de controle croisee ne necessite donc meme pas l'expiration du TTL : elle est atteignable des que deux serveurs manipulent le meme item. C'est un durcissement du scenario, pas une refutation.

2) La fenetre est plus large qu'annonce : l'ecriture terminale est elle-meme repoussee sur un executor (ZStorageManager.java:156-158, `CompletableFuture.runAsync(() -> with(ItemRepository.class).updateItem(item, storageType), this.plugin.getExecutorService())`). Meme apres que A ait repris, l'UPDATE reste en file, ce qui allonge encore l'intervalle T3-T4 pendant lequel la revalidation de B lit une ligne encore LISTED sans acheteur.

3) Le defaut n'est pas limite au script Lua : le chemin de repli non atomique (RedisAuctionClusterBridge.java:220-238) reproduit exactement la meme reprise de verrou perime (`if (STATE_LOCKED.equals(currentState) && jedis.exists(lockKeyStr)) return LockToken.noop();`). Un correctif applique uniquement au LOCK_SCRIPT laisserait le trou ouvert des que `scriptsLoaded == false`.

La correction proposee reste la bonne, avec deux ajouts : (a) le fence doit aussi etre applique dans le chemin de repli non atomique (ou, mieux, supprimer ce repli et echouer fermement) ; (b) rendre le jeton reellement unique (serverUuid + itemId + fence) est un prerequis, pas un effet de bord, car sans cela UNLOCK_SCRIPT permet deja a n'importe quel serveur de liberer le verrou d'un autre. Cote base, la clause `WHERE id=? AND fence < ?` doit couvrir toutes les transitions terminales (PURCHASED, DELETED, REMOVED), pas seulement PURCHASED, puisque createUpdateSchema ne garde aujourd'hui que EXPIRED.

**Correctif**

```lua
Introduire un jeton de fencing monotone et le faire verifier par le stockage :

-- LOCK_SCRIPT
local fence = redis.call('HINCRBY', itemKey, 'fence', 1)
redis.call('SET', lockKey, tokenValue, 'NX', 'PX', ttlMs)
redis.call('HSET', itemKey, 'state', 'LOCKED')
redis.call('HSET', itemKey, 'lock', tokenValue)
return fence

lockItem retourne alors un LockToken(serverUuid + ":" + itemId + ":" + fence), et :
- UNLOCK_SCRIPT compare la valeur complete (le jeton devient reellement unique) ;
- chaque ecriture terminale porte le fence : UPDATE items SET storage_type=?, fence=? WHERE id=? AND fence < ?, de sorte qu'un retardataire (fence plus petit) voie 0 ligne modifiee et abandonne au lieu d'ecraser.
```

---

<a id="c-026"></a>

### `C-026` — La base ne peut arbitrer aucune course : UPDATE items sans compare-and-set et nombre de lignes affectées systématiquement jeté

- [ ] **Corrigé**
- **Fichier** : `storage/repository/repositories/ItemRepository.java:71`
- **Catégorie** : Duplication d’item — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ItemRepository.createUpdateSchema (vérifié l.71-91) : la seule garde de transition existe pour la cible EXPIRED.

    schema.where("id", item.getId());
    if (storageType == StorageType.EXPIRED) {
        schema.where("storage_type", StorageType.LISTED.name());
    }
    schema.string("storage_type", storageType.name());

Toutes les cibles DELETED et PURCHASED sont donc des UPDATE aveugles.

API/.../api/storage/Repository.java:100-106 (vérifié) : le int rendu par Sarah est jeté.

    protected void update(Consumer<Schema> consumer) {
        try {
            SchemaBuilder.update(getTableName(), consumer).execute(this.connection, this.logger);
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

Repository.java:242-245 : update(List<Schema>) est void lui aussi, et n'a AUCUN try/catch.
Sarah UpdateRequest.java:60-61 remonte pourtant `return preparedStatement.getUpdateCount();`.
```

**Chronologie**

Deux conséquences distinctes du même défaut.

(a) UPDATE aveugle — T0 serveur A : achat de l'item 42 terminé, ZAuctionManager:893 écrit storage_type='PURCHASED', buyer_unique_id=acheteur. T1 serveur B : le vendeur retire son annonce (fantôme mémoire) → ZAuctionManager:602 `updateItem(item, DELETED)` → `UPDATE items SET storage_type='DELETED' WHERE id=42` : l'achat est écrasé, aucune trace. L'acheteur réclame sur A, le vendeur a déjà l'item : duplication.

(b) 0 ligne = succès silencieux — T0 serveur C (non distribué) : l'item 42 est déjà PURCHASED en base. ExpireService:193 émet `UPDATE ... WHERE id=42 AND storage_type='LISTED'` → 0 ligne. Aucune exception, le future se complète normalement, et la mémoire de C a DÉJÀ fait `addItem(StorageType.EXPIRED, item)` : l'item apparaît dans l'onglet expirés du vendeur alors que la base dit PURCHASED. Le vendeur clique → `updateItem(DELETED)` sans garde → écrase PURCHASED → il reçoit l'item, l'acheteur aussi.

Le seul compare-and-set du projet protège donc la ligne SQL mais laisse la mémoire du perdant diverger, parce que personne ne peut savoir qu'il a perdu la course.

**Impact** — C'est la cause racine qui rend exploitables presque toutes les autres failles de duplication : la base, désignée comme source de vérité partagée, n'oppose aucune barrière aux transitions concurrentes et ne signale jamais un conflit. Tous les correctifs proposés ailleurs (CAS sur items, CAS sur transactions) sont inopérants tant que ce retour n'est pas propagé.

**Précision apportée par la contre-expertise**

Titre corrige : "Les transitions vers DELETED/PURCHASED sont des UPDATE sans compare-and-set et le nombre de lignes affectees est systematiquement jete : un fantome memoire peut ecraser une vente en base et rendre l'item au vendeur".

Corrections factuelles a apporter au constat :

(a) La reference `ZAuctionManager:602` est fausse pour "le vendeur retire son annonce" : la ligne 602 est dans `removeExpiredItem` (message ITEM_REMOVE_EXPIRED). Le retrait d'une annonce est ZAuctionManager.java:568 (`removeSellingItem`) et :522 (`removeListedItem`, branche giveItem). Les six sites d'UPDATE aveugle sont : 522, 568, 602, 636, 693 (admin) et 893 (PURCHASED), plus CommandAuctionAdminAdd.java:117 et :129.

(b) "La base ne peut arbitrer AUCUNE course" est surestime. Il existe trois arbitrages reels, que le constat ignore :

- le CAS EXPIRED (ItemRepository.java:79-81), que le constat mentionne ;

- PurchaseService.java:118-125 : relecture autoritaire SOUS VERROU (`selectItem(item.getId())` puis `if (dbItem == null || dbItem.getBuyerUniqueId() != null) -> failedFuture("Item already sold on another server")`). Le chemin d'ACHAT est donc protege, contrairement a ce qu'affirme l'impact ("rend exploitables presque toutes les autres failles") ;

- ExpireService.java:280-289 : meme relecture sous verrou dans le chemin d'expiration distribue, qui supprime le fantome local au lieu de l'expirer.

La zone reellement non arbitree est le chemin de RETRAIT (RemoveService/ZAuctionManager 522/568/602/636/693) et le retrait admin.

(c) Le scenario (b) est mal cadre. ExpireService.java:127-133 route explicitement LISTED->EXPIRED vers le chemin cluster (`if (storageType == StorageType.LISTED && ...isDistributed()) { expireListedItemClustered(item); return; }`). Le lot de la l.193 n'est donc atteint QUE si `isDistributed() == false`. Sur un vrai mono-serveur, memoire et base ne peuvent pas diverger de la maniere decrite ; le scenario n'existe que dans une configuration multi-serveurs partageant la meme base SANS l'addon Redis. Le defaut de fond (l.189-193 fait `addItem(StorageType.EXPIRED, item)` AVANT l'UPDATE et ignore le 0 ligne) reste vrai, mais sa portee est bien plus etroite qu'annonce.

(d) Le point 4 de la correction proposee (remplacer `printStackTrace()`) est hors cible pour `update()`. Sarah leve `DatabaseException extends SarahException extends RuntimeException` (Sarah/.../exceptions/SarahException.java:6, DatabaseException.java:9), pas `SQLException` : UpdateRequest.java:62-65 fait `catch (SQLException exception) { ... throw new DatabaseException(...); }`. Le `catch (SQLException)` de Repository.java:103 est donc du code mort en pratique, et une erreur SQL remonte deja en exception runtime qui complete exceptionnellement le future. Le vrai probleme reste uniquement le 0 ligne silencieux.

Les points 1, 2 et 3 de la correction proposee (remonter le compte, CAS universel `where storage_type = from`, future exceptionnel quand rows == 0 avec abandon cote appelant) restent valides et sont le correctif adapte, en priorite sur les chemins de retrait.

**Correctif**

```java
1) Faire remonter le compte dans Repository (module API) :

protected int updateReturning(Consumer<Schema> consumer) {
    try {
        return SchemaBuilder.update(getTableName(), consumer).execute(this.connection, this.logger);
    } catch (SQLException e) {
        getPlugin().getLogger().log(Level.SEVERE, "update failed on " + getTableName(), e);
        return -1; // -1 = erreur, distinct de 0 = course perdue
    }
}
protected int updateReturning(List<Schema> schemas) {
    return new UpdateBatchRequest(schemas).execute(this.connection, this.connection.getDatabaseConfiguration(), this.logger);
}

2) Compare-and-set sur TOUTES les transitions, en passant l'état source attendu :

public int updateItem(Item item, StorageType from, StorageType to) {
    return updateReturning(schema -> {
        schema.where("id", item.getId());
        schema.where("storage_type", from.name());              // garde universelle
        if (to == StorageType.PURCHASED) schema.whereNull("buyer_unique_id");
        schema.string("storage_type", to.name());
        if (to != StorageType.DELETED) schema.object("expired_at", item.getExpiredAt());
        if ((to == StorageType.PURCHASED || to == StorageType.DELETED) && item.getBuyerUniqueId() != null)
            schema.uuid("buyer_unique_id", item.getBuyerUniqueId());
    });
}

3) `StorageManager.updateItem` devient `CompletableFuture<Integer>` et complète exceptionnellement quand rows == 0 ; tous les appelants (achat, 4 retraits, admin, expiration) abandonnent sans donner d'item ni débiter dans ce cas.

4) Remplacer les `exception.printStackTrace()` des 6 méthodes de Repository par le logger du plugin : une stacktrace sur System.err est invisible dans les logs filtrés et rend tout diagnostic de duplication impossible en production.
```

---

<a id="c-027"></a>

### `C-027` — La dette envers le vendeur (ligne transactions PENDING) est ecrite en fire-and-forget sur un scheduler non draine a l'arret : l'acheteur est preleve, le vendeur n'est jamais paye

- [ ] **Corrigé**
- **Fichier** : `storage/ZStorageManager.java:171`
- **Catégorie** : Perte d’argent — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZStorageManager.java:170-173 -- aucun future, aucun retour, aucun catch :
    @Override
    public void createTransaction(Item item, UUID playerUniqueId, String economyName, BigDecimal before, BigDecimal after, BigDecimal value, TransactionStatus status) {
        async(() -> with(TransactionRepository.class).create(item, playerUniqueId, economyName, before, after, value, status));
    }

ZStorageManager.java:107-109 -- `async` passe par le scheduler FoliaLib, PAS par l'executor du plugin :
    protected void async(Runnable runnable) {
        this.plugin.getScheduler().runAsync(wrappedTask -> runnable.run());
    }

ZAuctionPlugin.java:195-209 -- onDisable ne draine QUE asyncExecutor, jamais les taches du scheduler, puis ferme la connexion DB :
        this.asyncExecutor.shutdown();
        try {
            if (!this.asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) { ... }
        } catch (InterruptedException e) { ... }
        this.storageManager.onDisable();

API/.../api/storage/Repository.java:124-130 -- l'echec de l'insert se resume a une stacktrace :
    protected void insert(Consumer<Schema> consumer, Consumer<Integer> consumerResult) {
        try {
            consumerResult.accept(SchemaBuilder.insert(getTableName(), consumer).execute(this.connection, this.logger));
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

ZAuctionManager.java:846-852 -- le seul enregistrement du du vendeur, jamais chaine au future d'achat retourne l.918 :
        auctionEconomy.get(seller.getUniqueId()).thenAccept(sellerBalance -> {
            var beforeBalance = deferred ? sellerBalance : sellerBalance.subtract(finalSellerReceives);
            storageManager.createTransaction(auctionItem, seller.getUniqueId(), economyName, beforeBalance, sellerBalance, finalSellerReceives, transactionStatus);
        }).exceptionally(throwable -> { ... return null; });
```

**Chronologie**

En cluster, cette ligne PENDING est la SEULE representation de la dette envers le vendeur : aucun depot n'a eu lieu, rien n'est garde en memoire, rien dans Redis.

T0 - Serveur A, addon Redis actif. Le vendeur V est en ligne sur le serveur B, donc `seller.isOnline()` est false sur A -> ZAuctionManager.java:810-814 `deferDeposit = true` (branche `!sellerOnThisServer && clusterBridge.isDistributed()`) -> l.818 `transactionStatus = PENDING`.

T1 - Serveur A : l.802 `withdraw(acheteur, 100000)` -- l'acheteur est preleve immediatement et de facon synchrone.

T2 - Serveur A : l.848 `createTransaction(..., PENDING)` -> ZStorageManager.java:171 poste la tache sur le scheduler ; le future d'achat retourne l.918 ne l'attend pas. PurchaseService.java:134-141 enchaine notifyItemBought puis unlockItem et declare l'achat REUSSI.

T3 - Serveur A : la tache echoue (pool HikariCP epuise, wait_timeout MySQL, deadlock InnoDB) -- Repository.insert imprime une stacktrace et rend la main -- OU le serveur A s'arrete entre T2 et T3 : onDisable draine asyncExecutor mais PAS les taches du scheduler, puis ferme la connexion DB, de sorte qu'une tache survivante s'executerait contre une connexion fermee.

T4 - Serveur A : l'item est marque PURCHASED/DELETED en base (l.883 ou 893). L'acheteur a son item, l'acheteur a paye -- et il n'existe AUCUNE ligne PENDING pour V.

T5 - Serveur B : V fait `/ah claim`. ClaimService.java:117-121 ne lit que %prefix%transactions et ne trouve rien -> CLAIM_NO_PENDING. Les 100 000 sont detruits, sans meme un log explicite.

Aggravant : l.846 `auctionEconomy.get(seller.getUniqueId())` est evalue AVANT le createTransaction ; si ce `get` echoue (VaultProvider.java:23 leve NullPointerException quand Vault n'est pas enregistre), le `.thenAccept` n'est jamais execute et la transaction du vendeur n'est meme pas TENTEE.

**Impact** — L'ecriture de la dette envers le vendeur est la seule etape de tout le flux d'achat a n'etre ni attendue, ni verifiee, ni rejouee, alors qu'elle est la seule trace de l'argent en mode cluster. Chaque incident base et chaque arret de serveur avec des ventes en vol = un vendeur non paye pour un acheteur preleve. Aucune reconciliation au redemarrage n'existe. Le meme defaut frappe log() (ZStorageManager.java:166-168), donc l'historique de vente.

**Précision apportée par la contre-expertise**

Le constat est valide et sa correction proposee est bonne. Trois precisions factuelles a apporter :

(a) PORTEE PLUS LARGE QUE LE CLUSTER. ZAuctionManager.java:812-814 : `boolean deferDeposit = !auctionEconomy.isAutoClaim() || (!sellerOnThisServer && auctionEconomy.mustBeOnline()) || (!sellerOnThisServer && clusterBridge.isDistributed());`. La premiere clause `!auctionEconomy.isAutoClaim()` declenche PENDING meme sur un serveur UNIQUE sans Redis. Le defaut n'est donc pas cantonne au mode cluster : toute economie configuree sans auto-claim est exposee.

(b) L'« AGGRAVANT » EST MAL DECRIT — ET IL EST PIRE. Le constat suppose un future qui echoue et un `.exceptionally` qui absorbe. En realite ZAuctionEconomy.java:76-79 :

public CompletableFuture<BigDecimal> get(UUID playerId) {

return CompletableFuture.completedFuture(this.currencyProvider.getBalance(playerId));

}

La lecture de solde est SYNCHRONE et deja terminee. Consequences :

- les `.exceptionally(...)` de ZAuctionManager.java:841-844 et 849-852 sont du CODE MORT : le future n'est jamais complete exceptionnellement, ils ne peuvent rien rattraper ;

- si `getBalance` leve (le NPE cite est bien reel mais se trouve dans un AUTRE projet : D:/Users/Maxlego08/workspace2.0/CurrenciesAPI/src/main/java/fr/traqueur/currencies/providers/VaultProvider.java:23 `throw new NullPointerException("Vault Economy interface not found");`, pas dans zAuctionHouseV4), l'exception remonte SYNCHRONEMENT hors de `purchaseAuctionItem`, apres le `withdraw` de l'acheteur (l.802). Ni la transaction acheteur (l.840) ni celle vendeur (l.848) ne sont ecrites, ET les l.863-893 ne sont jamais atteintes : l'item n'est pas marque PURCHASED/DELETED. L'acheteur est preleve, ne recoit rien, et l'item reste en vente. Le point 6 de la correction proposee (lecture defensive, before/after purement informatifs) reste donc le bon remede, mais il faut aussi envelopper l'appel dans un try/catch synchrone, les `.exceptionally` ne suffiront jamais.

(c) SURFACE DU MEME DEFAUT `async(...)`. Au-dela de `log()` (l.166-168) cite par le constat, ZStorageManager expose exactement quatre methodes non attendues et non drainees : `upsertPlayer(Player)` l.130-132, `upsertPlayer(UUID,String)` l.135-137, `log(...)` l.166-168, `createTransaction(...)` l.170-173, plus `markPurchaseLogAsRead(...)` l.251-253. Le point 5 de la correction proposee doit couvrir les cinq.

**Correctif**

1) Changer la signature dans StorageManager : `CompletableFuture<Integer> createTransaction(...)`, et dans ZStorageManager.java:171 :
```java
return CompletableFuture.supplyAsync(() -> with(TransactionRepository.class).createReturningId(...), this.plugin.getExecutorService());
```
(TransactionRepository.create doit passer par `insertSync` et remonter l'id genere.)
2) Repository : cesser d'avaler l'echec des inserts de transactions -- remplacer `catch (SQLException e) { e.printStackTrace(); }` par une remontee d'erreur.
3) ZAuctionManager.purchaseAuctionItem doit CHAINER ce future dans `updateFuture` retourne l.918 :
```java
CompletableFuture<Void> sellerTx = storageManager.createTransaction(auctionItem, seller.getUniqueId(), economyName, beforeBalance, sellerBalance, finalSellerReceives, transactionStatus).thenAccept(id -> {});
...
return CompletableFuture.allOf(updateFuture, sellerTx);
```
ainsi PurchaseService ne declare l'achat reussi qu'apres l'ecriture reelle de la dette.
4) Quand `deferDeposit == true`, ecrire la ligne PENDING AVANT le `withdraw` de l'acheteur (l.802) ; en cas d'echec de l'insert, abandonner l'achat sans rien prelever (l'item n'a pas encore bouge, le verrou est encore detenu).
5) Basculer log/createTransaction/upsertPlayer de `async(...)` (scheduler) vers `this.plugin.getExecutorService()`, qui EST draine 5 s par onDisable.
6) Remplacer `auctionEconomy.get(...).thenAccept(...)` (l.839 et 846) par une lecture defensive : les colonnes before/after ne sont qu'informatives et ne doivent jamais conditionner l'ecriture de value/status.

---

<a id="c-028"></a>

### `C-028` — Le bus pub/sub est en "au plus une fois" : l'echec de publication est avale et aucun rattrapage n'existe apres une coupure Redis

- [ ] **Corrigé**
- **Fichier** : `zAuctionHouse Redis/redis/ZAuctionHouseRedis.java:274`
- **Catégorie** : Cohérence de cache — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// ZAuctionHouseRedis.java:274-282 -- l'echec de publication est logge puis oublie, le future se termine NORMALEMENT
public <T> void sendMessage(T message) {
    String jsonMessage = this.gson.toJson(new RedisMessage<>(INSTANCE_UUID, message, message.getClass().getName()));
    this.debug("Send: " + jsonMessage);
    try (Jedis jedis = jedisPool.getResource()) {
        jedis.publish(this.channelName, jsonMessage);
    } catch (Exception e) {
        getLogger().log(Level.WARNING, "Failed to send Redis message", e);
    }
}

// RedisAuctionClusterBridge.java:291-312 -- l'appelant croit la diffusion reussie
@Override
public CompletableFuture<Void> notifyItemBought(Player player, Item item) {
    return CompletableFuture.runAsync(() -> {
        ...
        this.plugin.sendMessage(new ItemBoughtMessage(...));
    });
}

// RedisSubscriberRunnable.java:66-97 -- reconnexion avec backoff, mais AUCUN rattrapage des messages perdus
while (running.get()) {
    try (Jedis jedis = this.plugin.getJedisPool().getResource()) {
        ...
        jedis.subscribe(jedisPubSub, this.plugin.getChannelName());
    } catch (Exception exception) {
        ...
        Thread.sleep(backoffMs);
        backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
```

**Chronologie**

Redis pub/sub n'offre aucune garantie de livraison : un abonne deconnecte perd definitivement les messages publies pendant sa coupure, et l'emetteur ne le sait pas.

T0 -- Le lien reseau entre le serveur B et Redis tombe. RedisSubscriberRunnable entre en boucle de reconnexion avec un backoff qui monte jusqu'a 60 s (ligne 95).

T1 -- Serveur A : un joueur achete l'item 42. notifyItemBought passe l'etat Redis a SOLD (l'ecriture de cle, elle, est durable) et publie ItemBoughtMessage. B ne le recoit jamais.

T2 -- Si c'est A qui perd Redis, c'est pire : jedis.publish leve, sendMessage attrape et logge, le future se termine NORMALEMENT et PurchaseService.java:135-138 enchaine sur unlockItem comme si tout allait bien. L'achat est finalise sans qu'aucun serveur n'ait ete prevenu.

T3 -- B garde l'item 42 dans ses LISTED en memoire, sans limite de temps : aucune resynchronisation periodique n'existe, seul un redemarrage complet de B rechargerait la base.

T4 -- Le vendeur, connecte sur B, ouvre son onglet "en vente", voit l'item et le retire. RemoveService ne relit jamais la base sous verrou et Redis considere SOLD comme disponible : le retrait aboutit et le vendeur recupere un item deja livre a l'acheteur.

T5 -- Symetriquement, un ItemStatusMessage perdu laisse un item bloque en IS_BEING_PURCHASED pour toujours sur les serveurs qui l'ont manque : invendable et inachetable jusqu'au redemarrage.

**Impact** — Toute coupure Redis, meme de quelques secondes, laisse des fantomes memoire permanents sur les noeuds concernes. Combines a l'absence de revalidation dans RemoveService, ces fantomes deviennent des items gratuits ; l'echec de publication cote emetteur n'est meme pas remonte a l'appelant.

**Précision apportée par la contre-expertise**

Le constat est réel mais l'étape **T2 est fausse** et doit être remplacée.

**Ce que T2 prétend** : « Si c'est A qui perd Redis […] `jedis.publish` lève, `sendMessage` attrape et logge, le future se termine NORMALEMENT et `PurchaseService.java:135-138` enchaîne sur `unlockItem` comme si tout allait bien. »

**Ce que dit le code** : dans `RedisAuctionClusterBridge.notifyItemBought` (l.291-313), `sendMessage` n'est PAS la première opération Redis du bloc :

```java

return CompletableFuture.runAsync(() -> {

try (Jedis jedis = jedisPool.getResource()) {   // l.293 — lève si Redis est injoignable

String key = itemKey(item);

jedis.hset(key, FIELD_STATE, STATE_SOLD);   // l.295 — lève si le lien est coupé

...

this.plugin.sendMessage(new ItemBoughtMessage(...));  // l.302 — jamais atteint

}

});

```

Il n'y a **aucun `catch`** autour de ce `try-with-resources`. Si A perd Redis, `jedisPool.getResource()` ou le `hset` lève AVANT `sendMessage`, le `runAsync` propage l'exception et le future se termine **exceptionnellement** — la chaîne de `PurchaseService` part donc dans son `exceptionally` (l.158+) et non dans `unlockItem` (l.137). Idem pour `notifyItemListed` (l.316-332) et `removeItem` (l.350-364). L'affirmation « le future se termine NORMALEMENT » est donc invalide pour ces trois méthodes, et la conclusion « l'écriture de clé est durable » de T1/T2 est contradictoire avec un Redis injoignable côté A.

**Portée réelle de l'avalement (à substituer à T2)** : une seule méthode du bridge appelle `sendMessage` sans opération Jedis englobante — `notifyItemStatusChange` :

```java

// RedisAuctionClusterBridge.java:335-337

public CompletableFuture<Void> notifyItemStatusChange(Item item, ItemStatus oldStatus, ItemStatus newStatus) {

return CompletableFuture.runAsync(() -> this.plugin.sendMessage(new ItemStatusMessage(String.valueOf(item.getId()), oldStatus, newStatus)));

}

```

Là, et là seulement, l'échec est avalé et le future se termine normalement : l'appelant (`RemoveService.java:242`, `PurchaseService.java:108`, et les restaurations de statut `RemoveService.java:310` / `PurchaseService.java:180+`) croit la diffusion réussie. C'est ce qui alimente T5 (statut figé) et non T4.

**Second point à préciser (T0→T4)** : tant que B est effectivement déconnecté de Redis, le retrait sur B échoue lui aussi — `checkAvailabilityStep` (`RemoveService.java:206-209`) appelle `checkAvailability`, dont le `jedisPool.getResource()` lève, ce qui part dans `handleRemovalException` (l.270). La fenêtre de duplication ne s'ouvre donc **qu'après la reconnexion de B** : les opérations verrou/état redeviennent fonctionnelles alors que le message perdu ne sera jamais rejoué. Cela ne réduit pas l'impact (une coupure de quelques secondes suffit), mais la chronologie doit le dire explicitement.

**Le vrai mécanisme de duplication (T4), à formuler ainsi)** : après reconnexion de B, l'item 42 fantôme est encore en `LISTED` mémoire avec `status = AVAILABLE`. Le vendeur le retire :

- `RemoveService.java:96` teste `item.getStatus()` — valeur mémoire périmée, passe ;

- `checkAvailability` renvoie `true` car l'état Redis est `SOLD` et seul `LOCKED`/`DELETED` bloque (`RedisAuctionClusterBridge.java:163-175`) ;

- `LOCK_SCRIPT` accorde le verrou sur un `SOLD` (l.47-57) ;

- `RemoveService` ne relit jamais la base (contrairement à `PurchaseService.java:119-127`) ;

- `ItemRepository.createUpdateSchema` n'ajoute pas de `where storage_type = LISTED` pour la destination `DELETED` (l.108-110 : garde réservé à `EXPIRED`), donc l'UPDATE écrase la ligne vendue ;

- `ZAuctionManager.removeSellingItem` (l.557-588) fait `giveItem(player, item)`.

→ le vendeur récupère une copie physique d'un item déjà livré à l'acheteur.

**Correction proposée — ajustements** :

1. Faire remonter l'échec de publication reste utile, mais uniquement décisif pour `notifyItemStatusChange` ; pour les trois autres méthodes l'échec remonte déjà.

2. et 3. (rattrapage par Stream/table d'événements, ou resync périodique) restent valides et sont les vrais correctifs.

4. **À ajouter, et c'est le correctif le moins coûteux** : aligner `RemoveService` sur `PurchaseService` en insérant une revalidation en base sous verrou (`selectItem(item.getId())` avec abandon si `null` ou `getBuyerUniqueId() != null`) entre `changeStatusAndNotifyStep` et `executeLocalRemovalStep` ; et étendre le garde SQL de `ItemRepository.createUpdateSchema` à la transition vers `DELETED` (`where storage_type in (LISTED)` selon le chemin), pour que l'UPDATE matche 0 ligne sur un item déjà vendu.

5. **À ajouter** : faire renvoyer `false` à `checkAvailability` (et refuser le verrou dans `LOCK_SCRIPT`) pour les états terminaux `SOLD` et `REMOVED`, pas seulement `DELETED`.

**Correctif**

```java
1) Remonter l'echec de publication au lieu de l'avaler, pour que l'appelant puisse annuler ou reessayer :

public <T> void sendMessage(T message) {
    String jsonMessage = this.gson.toJson(new RedisMessage<>(INSTANCE_UUID, message, message.getClass().getName()));
    try (Jedis jedis = jedisPool.getResource()) {
        jedis.publish(this.channelName, jsonMessage);
    } catch (Exception e) {
        throw new IllegalStateException("Failed to publish Redis message", e);   // le future du bridge echoue vraiment
    }
}

2) Ajouter un rattrapage : journaliser les evenements dans un Redis Stream (XADD) ou une table events horodatee, memoriser le dernier identifiant consomme, et rejouer depuis ce point a chaque reconnexion du subscriber.
3) A defaut, planifier une resynchronisation periodique (toutes les N minutes) qui relit en base les identifiants LISTED et purge de la memoire tout item absent ou non LISTED.
```

---

<a id="c-029"></a>

### `C-029` — Le format de serialisation des ItemStack depend de la version Minecraft du serveur LOCAL : dans un cluster 1.20.x + 1.21.x, la meme colonne contient deux encodages mutuellement illisibles

- [ ] **Corrigé**
- **Fichier** : `API/api/utils/Base64ItemStack.java:30`
- **Catégorie** : Perte d’item — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
Base64ItemStack.java:28-32 et 54-58 - la branche depend de la version du serveur qui execute le code :
    public static String encode(ItemStack itemStack) {
        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            return ItemStackUtils.serializeItemStack(itemStack);
        }
        ...
    public static ItemStack decode(String data) {
        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            return ItemStackUtils.safeDeserializeItemStack(data);
        }

Base64ItemStack.java:69-72 - un decodage rate rend null, silencieusement :
        } catch (IOException | ClassNotFoundException exception) {
            exception.printStackTrace();
            return null;
        }

NmsVersion.java:167-169 :
    public boolean isAttributItemStack() {
        return version >= 1205;
    }

Et ItemStackUtils.safeDeserializeItemStack:40-45 rappelle Base64ItemStack.decode dans son catch, qui sur < 1.20.5 rappelle safeDeserializeItemStack : recursion infinie possible (StackOverflowError) sur une donnee au mauvais format.
```

**Chronologie**

T0 - Reseau a deux noeuds partageant une base MySQL et l'addon Redis : serveur SURVIE en 1.20.4 (isAttributItemStack() == false) et serveur PVP en 1.21 (== true). plugin.yml declare api-version: '1.20', donc les deux sont explicitement supportes.

T1 - Sur SURVIE, un joueur vend une epee. AuctionItemRepository:34 prend la branche NBT (ItemStackUtils.serializeItemStack). La ligne auction_items est ecrite, notifyItemListed publie.

T2 - Sur PVP, ItemListedListener appelle selectItem(id) -> ItemLoaderUtils -> Base64ItemStack.decode prend la branche moderne : Base64.getDecoder().decode reussit (meme alphabet), GZIPInputStream reussit (les donnees NBT sont gzippees), puis BukkitObjectInputStream ne trouve pas l'en-tete de serialisation Java -> StreamCorruptedException -> decode renvoie NULL.

T3 - Sur PVP l'item est construit avec itemStacks = [null] : ZAuctionItem.getItemStack:70-73 logge un warning et affiche un BARRIER, applyCategories le classe en 'misc'. L'item est LISTE et CLIQUABLE au prix du vendeur.

T4 - Un joueur de PVP achete le BARRIER : ZAuctionManager:731 getItemsAsString() -> NPE. L'achat echoue en boucle, l'item reste affiche et definitivement inachetable.

T5 - Symetriquement, tout item vendu sur PVP est illisible sur SURVIE.

**Impact** — Sur un reseau heterogene en versions Minecraft - configuration explicitement autorisee par api-version: '1.20' et par la vocation multi-serveurs de l'addon Redis - la moitie du catalogue devient des BARRIER inachetables sur l'autre moitie des serveurs, avec des NPE en boucle. Le meme probleme se declenche lors d'une mise a jour progressive du reseau (un noeud passe en 1.20.6 alors que les autres sont en 1.20.4).

**Précision apportée par la contre-expertise**

Le constat est reel et bien decrit. Trois precisions issues de la lecture du code :

A) DECLENCHEUR PRINCIPAL SOUS-ESTIME - ce n'est pas d'abord le cluster heterogene, c'est la simple MISE A JOUR D'UN SERVEUR UNIQUE a travers le seuil 1.20.5. Un serveur 1.20.4 seul, sans Redis, qui passe en 1.20.6/1.21 : toutes les lignes auction_items preexistantes sont au format NBT, le serveur redemarre du cote moderne de Base64ItemStack.java:30/56, et 100% du catalogue existant devient BARRIER inachetable. Aucune migration de format n'existe (aucune classe sous migration/ ne touche a la colonne itemstack ; ZMigrationRegistry ne couvre que l'import V3). Cela elargit fortement la population touchee et confirme la severite "haute".

B) PAS DE PERTE D'ARGENT NI DE VERROU FUITE au T4 (point important pour un audit anti-dupe). ZAuctionManager.java:731 "String items = auctionItem.getItemsAsString();" est du code lineaire au TOUT DEBUT de purchaseAuctionItem, avant clusterBridge.checkAvailability/lockItem et avant tout appel a l'economie. La NPE est donc levee de maniere SYNCHRONE, remonte a l'appelant de purchaseItem (le handler de clic), et n'est meme pas capturee dans un CompletableFuture. Consequence : l'achat avorte proprement, aucun retrait d'argent, aucun verrou Redis acquis puis non relache. Le T4 decrit ("achat echoue en boucle, item definitivement inachetable") est correct, mais il faut preciser que c'est une indisponibilite, pas une perte monetaire ni un dupe.

C) LA CORRECTION PROPOSEE N°1 EST INCOMPLETE. La phrase "les anciennes valeurs sans prefixe restent traitees par la voie NMS" ne peut pas fonctionner sur un serveur 1.20.5+ : ItemStackUtils.EnumReflectionItemStack.getClassz() fait

String nmsPackage = Bukkit.getServer().getClass().getPackage().getName();

String nmsVersion = nmsPackage.replace(".", ",").split(",")[3];

Sur Paper >= 1.20.5, le package CraftBukkit n'est plus relocalise par version ("org.bukkit.craftbukkit", 3 segments), donc split(",")[3] leve ArrayIndexOutOfBoundsException ; et les noms de methodes obfusques "b"/"a" ne sont plus valides sur un serveur mojang-mapped. Autrement dit la voie NMS legacy est structurellement inutilisable sur un lecteur moderne : un marqueur de format seul ne suffit pas, il faut un lecteur NBT independant de la version (ou une migration one-shot qui reencode toutes les lignes au demarrage apres detection du franchissement du seuil).

D) Precision sur le point 4 du correctif : la recursion safeDeserializeItemStack <-> Base64ItemStack.decode produit un StackOverflowError, qui est un Error et non une Exception ; le "catch (Exception)" de safeDeserializeItemStack:44 ne l'arrete donc pas. Il s'echappe de ItemLoaderUtils.createAuctionItem (au milieu d'un stream .map) et peut faire echouer tout le lot de chargement, pas seulement l'item fautif - c'est plus grave qu'un simple null cote serveur legacy.

**Correctif**

1) Prefixer la charge utile par un marqueur de format a l'ecriture ('V2:' pour BukkitObjectStream, 'NBT:' pour la voie NMS) et faire dispatcher decode sur ce prefixe, JAMAIS sur NmsVersion.getCurrentVersion() du serveur lecteur ; les anciennes valeurs sans prefixe restent traitees par la voie NMS.
2) Alternative : stocker le format dans une colonne auction_items.format (migration additive, MigrationManager de Sarah ajoute les colonnes manquantes).
3) Quand le decodage echoue, ne PAS renvoyer null : lever ou renvoyer un marqueur d'erreur, et faire en sorte qu'ItemLoaderUtils/selectItem refuse de publier l'item plutot que de l'exposer en BARRIER achetable.
4) Corriger la recursion ItemStackUtils.safeDeserializeItemStack <-> Base64ItemStack.decode.
5) Verifier au demarrage que tous les noeuds du cluster sont du meme cote du seuil 1.20.5 et refuser/avertir sinon.

---

<a id="c-030"></a>

### `C-030` — Le remboursement d'une vente echouee reecrit l'inventaire depuis l'asyncExecutor

- [ ] **Corrigé**
- **Fichier** : `services/SellService.java:120`
- **Catégorie** : Perte d’item — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// SellService.java:104-130
            // Remove items from their slots
            removeItemsFromSlots(player, validSlotItems);

            var storageManager = this.plugin.getStorageManager();
            storageManager.createAuctionItem(player, price, expiredAt, itemsToSell, auctionEconomy).thenAccept(auctionItem -> {
                this.postSell(player, auctionItem, auctionEconomy, taxResult);
                resultFuture.complete(SellResult.success("Item listed successfully", auctionItem));
            }).exceptionally(throwable -> {
                this.plugin.getLogger().severe("Unable to sell item: " + throwable.getMessage());
...
                // Return items to player safely
                if (player.isOnline() && itemsToSell != null) {
                    itemsToSell.forEach(itemStack -> {
                        if (itemStack != null) {
                            player.getInventory().addItem(itemStack);
                        }
                    });
                }
                // Refund the tax if the sale failed
                if (taxResult.hasTax()) {
                    auctionEconomy.deposit(player.getUniqueId(), taxResult.taxAmount(), "Refund sell tax (sale failed)");
                }

// ZStorageManager.java:140-145 -- deux inserts hors transaction, sur l'asyncExecutor
        return CompletableFuture.supplyAsync(() -> {
            int itemId = with(ItemRepository.class).create(seller, ItemType.AUCTION, price, expiredAt, auctionEconomy);
            return with(AuctionItemRepository.class).create(seller, itemId, price, expiredAt, itemStacks, auctionEconomy);
        }, this.plugin.getExecutorService());
```

**Chronologie**

T0 serveur A : le joueur valide sa vente. SellService.java:83 repasse bien sur le thread entite (runAtEntity) et l.105 removeItemsFromSlots retire les items de l'inventaire : c'est correct. T1 A : l.108 createAuctionItem part sur plugin.getExecutorService(). ZStorageManager.java:141-143 execute d'abord INSERT INTO items (storage_type = 'LISTED', l'id est retourne) puis N INSERT INTO auction_items, en autocommit, sans transaction. T2 A : le second insert echoue (deadlock InnoDB, connexion coupee pendant un failover, colonne LONGTEXT saturee). T3 A : le bloc exceptionally l.111-130 s'execute SUR LE THREAD DE L'ASYNCEXECUTOR, pas sur le thread entite. l.120 player.getInventory().addItem(itemStack) reecrit l'inventaire du joueur en concurrence avec le tick qui traite ses clics ; aucun paquet de resynchronisation n'est envoye. Sur Folia (folia-supported: true) ce thread n'est pas le thread de region du joueur. T4 A : la ligne items est deja committee en LISTED et n'est jamais annulee. Au prochain demarrage, ItemRepository.select() (WHERE storage_type != 'DELETED') la recharge : l'item est en vente sur A, B et C, avec eventuellement une liste d'ItemStack partielle ou vide, PENDANT que le joueur a recupere ses items en main. T5 serveur B : un acheteur l'achete ; PurchaseService.java:118-125 revalide, dbItem existe et buyer est null, l'achat passe. Le joueur a garde ses items ET encaisse le prix.

**Impact** — Sur le chemin de compensation -- exactement la ou la fiabilite compte -- l'inventaire est reecrit hors du thread entite, et la ligne items orpheline restee LISTED transforme un echec de vente en item vendable en double. Les callers ignorent le SellResult (SellConfirmButton.java:76 et CommandAuctionSell.java:100 ne consomment pas le futur), donc rien ne remonte l'incoherence.

**Correctif**

1) Envelopper l'integralite du bloc exceptionally dans this.plugin.getScheduler().runAtEntity(player, w -> { ... }) afin que addItem et le remboursement de taxe s'executent sur le bon thread. 2) Rendre createAuctionItem atomique : soit utiliser la transaction Sarah existante (DatabaseConnection.beginTransaction(), aujourd'hui jamais appelee dans tout le projet), soit, en cas d'echec du second insert, marquer immediatement la ligne items en DELETED avant de rendre les items au joueur. 3) Prevoir aussi le cas ou le joueur s'est deconnecte (player.isOnline() == false l.117) : aujourd'hui les items sont purement et simplement perdus, il faudrait les recreer en EXPIRED plutot que de les abandonner.

---

<a id="c-031"></a>

### `C-031` — Le thread abonne demarre et le bridge Redis est installe AVANT l'enregistrement des listeners : tous les messages de cette fenetre sont jetes sans aucun log

- [ ] **Corrigé**
- **Fichier** : `REDIS/ZAuctionHouseRedis.java:83`
- **Catégorie** : Cluster — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZAuctionHouseRedis.java:83-106 (ordre reel de onEnable) :
        this.subscriberThread = new Thread(this.redisSubscriberRunnable, "zAuctionHouse-Redis-Subscriber");
        this.subscriberThread.setDaemon(true);
        this.subscriberThread.start();
        ...
        this.auctionPlugin.setAuctionClusterBridge(new RedisAuctionClusterBridge(this, this.jedisPool, this.lockTtl, itemStateTtl, itemListedTtl));

        this.redisSubscriberRunnable.registerListener(ItemListedMessage.class, new ItemListedListener(this));
        this.redisSubscriberRunnable.registerListener(ItemRemovedMessage.class, new ItemRemovedListener(this));
        this.redisSubscriberRunnable.registerListener(ItemBoughtMessage.class, new ItemBoughtListener(this));
        this.redisSubscriberRunnable.registerListener(ItemStatusMessage.class, new ItemStatusListener(this));

        this.enableDebug = getConfig().getBoolean("debug");

RedisSubscriberRunnable.java:134-143 - aucun else, aucun log si le listener manque :
            RedisListener<?> listener = getListener(messageClass);
            ...
            if (listener != null) {
                listener.message(result);
            }
```

**Chronologie**

T0 - Reseau de 5 serveurs, trafic normal. Le serveur B redemarre.

T1 - Sur B, zAuctionHouse s'active et charge tous les items depuis la base : le store LISTED de B est un instantane de la base a T1.

T2 - zAuctionHouseRedis s'active. Ligne 85 : le thread abonne demarre et s'abonne au canal. B recoit desormais les messages.

T3 - Ligne 99 : construction du RedisAuctionClusterBridge, dont le constructeur execute deux SCRIPT LOAD bloquants. Pendant ces deux allers-retours (plus les getConfig().getInt des l.88-97), le serveur A vend l'item #77. B recoit bien l'ItemBoughtMessage sur le thread abonne.

T4 - handleMessage passe le filtre de canal, le filtre serverId et la whitelist, construit l'instance, puis l.134 getListener(ItemBoughtMessage.class) renvoie null car les registerListener ne sont qu'aux l.101-104. L.141 if (listener != null) -> le message est jete. Aucun warning : les seules traces sont des plugin.debug, et enableDebug n'est lu qu'a la l.106, donc il vaut encore false.

T5 - B garde #77 en LISTED. Le fantome est ensuite exploitable comme decrit dans les constats sur les gardes memoire (le vendeur le reclame sur B alors qu'il a deja encaisse la vente sur A).

Aggravant : le bridge Redis est deja installe a la l.99, donc B commence a poser des verrous et a publier ses propres messages avant meme d'etre capable d'en recevoir.

**Impact** — A chaque redemarrage de serveur, une fenetre de plusieurs allers-retours Redis pendant laquelle tous les messages recus sont jetes silencieusement. Sur un reseau actif cela produit des items fantomes des le demarrage, donc du duplicat potentiel, et surtout aucune trace exploitable pour diagnostiquer.

**Précision apportée par la contre-expertise**

Le defaut est reel mais MAL CADRE, et la correction proposee est insuffisante.

1) La fenetre reelle est bien plus large que celle decrite. plugin.yml de l'addon contient "depend: zAuctionHouse", donc le onEnable de zAuctionHouse — y compris l'instantane DB (ZAuctionPlugin.java:160 this.storageManager.loadItems()) — se termine AVANT que le onEnable de l'addon ne commence. Le vrai trou n'est pas l.85 -> l.101 (quelques centaines de microsecondes a quelques ms) mais "instantane DB -> SUBSCRIBE effectif", qui couvre toute la fin du demarrage de zAuctionHouse plus saveDefaultConfig, loadServerUUID, VersionChecker, Metrics, le PING de loadRedis et validateAndRegisterUUID : des secondes. Consequence directe : la correction 1) proposee (reordonner onEnable) NE FERME PAS le trou principal. Il faut une reconciliation apres l'abonnement (relecture DB des items LISTED, ou comparaison avec l'etat Redis auction:item:*) declenchee une fois le SUBSCRIBE etabli.

2) Defaut supplementaire non vu, dans le meme code : RedisSubscriberRunnable.java:30 "private final Map<Class<?>, RedisListener<?>> listeners = new HashMap<>();" est ecrit par le thread principal aux l.101-104, c'est-a-dire APRES le Thread.start() de la l.85, et lu par le thread abonne a la l.43 (listeners.get) sans aucune synchronisation. Il n'existe donc aucune relation happens-before : c'est une data race permanente (visibilite non garantie + HashMap non thread-safe), pas seulement un probleme d'ordre au demarrage. Meme probleme pour enableDebug (l.43/106, boolean non volatile) lu depuis le thread abonne dans debug(). Correctif : ConcurrentHashMap pour listeners et volatile pour enableDebug, en plus du reordonnancement.

3) Precision sur l'impact : le fantome n'est PAS exploitable par le chemin d'achat. PurchaseService.java:81-125 re-valide en base SOUS VERROU avant tout debit : "if (dbItem == null || dbItem.getBuyerUniqueId() != null) { ... failedFuture(new IllegalStateException(\"Item already sold on another server\")); }". Il EST exploitable par le chemin de reclamation vendeur, non garde : RedisAuctionClusterBridge.checkAvailability (l.158-176) renvoie true pour l'etat SOLD, et ZAuctionManager.removeListedItem (l.507-550) rend l'item sans aucune verification DB. L'impact "duplicat d'item" doit donc etre attribue a ces deux emplacements, pas au simple ordre de onEnable.

4) La correction 2) proposee (logger un warning quand listener == null) reste valable et utile telle quelle.

**Correctif**

```java
1) Ordre cible dans onEnable : loadRedis -> validateAndRegisterUUID -> lecture de enableDebug -> les quatre registerListener -> setAuctionClusterBridge -> demarrage du thread abonne. Un noeud ne doit jamais publier ni verrouiller avant d'etre capable de recevoir.
2) Dans RedisSubscriberRunnable.handleMessage, remplacer if (listener != null) { listener.message(result); } par :
    if (listener == null) { plugin.getLogger().warning("No listener registered for " + className + ", message dropped: " + message); return; }
    listener.message(result);
3) Idealement, ajouter un AtomicBoolean ready dans RedisSubscriberRunnable, mis a true apres l'enregistrement des listeners, et faire attendre run() sur ce flag avant le premier jedis.subscribe.
```

---

<a id="c-032"></a>

### `C-032` — Les economies liees au joueur (ITEM, ZMENUITEMS, LEVEL, EXPERIENCE) detruisent silencieusement le paiement du vendeur hors ligne, et la transaction est enregistree RETRIEVED

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:812`
- **Catégorie** : Perte d’argent — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZAuctionManager.java:810-823 -- rien ne teste si l'economie sait payer un joueur absent :
        boolean sellerOnThisServer = seller.isOnline();
        boolean deferDeposit = !auctionEconomy.isAutoClaim()
                || (!sellerOnThisServer && auctionEconomy.mustBeOnline())
                || (!sellerOnThisServer && clusterBridge.isDistributed());

        if (deferDeposit) {
            transactionStatus = TransactionStatus.PENDING;
        } else {
            transactionStatus = TransactionStatus.RETRIEVED;
            try {
                auctionEconomy.deposit(seller.getUniqueId(), sellerReceives, ...);

CurrenciesAPI LevelProvider.java:12-19 -- no-op TOTAL hors ligne : aucun log, aucune exception :
    @Override
    public void deposit(UUID playerId, BigDecimal amount, String reason) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            int level = player.getLevel();
            player.setLevel(level + amount.intValue());
        }
    }

CurrenciesAPI ItemProvider.java:23-31 -- un SEVERE, mais aucune exception, donc le try/catch l.824 ne voit rien :
    public void deposit(UUID playerId, BigDecimal amount, String reason) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) { giveItem(player, amount.intValue(), this.itemStack); }
        else { this.plugin.getLogger().severe("Deposit items to " + playerId + " but is offline"); }
    }

CurrenciesAPI LevelProvider.java:30-33 et ItemProvider.java:42-48 -- getBalance rend 0 hors ligne, donc les colonnes before/after de la transaction sont fausses.

ZEconomyManager.java:301 -- le seul garde-fou est un opt-in desactive par defaut :
        boolean mustBeOnline = accessor.getBoolean("must-be-online", false);
src/main/resources/economies.yml:77 -- `must-be-online: false`.
```

**Chronologie**

Configuration : une economie de type LEVEL / EXPERIENCE / ITEM / ZMENUITEMS (types supportes et documentes) laissee aux valeurs par defaut `auto-claim: true` et `must-be-online: false`.

T0 - Serveur A du reseau, sur lequel l'addon Redis N'EST PAS charge (LocalAuctionClusterBridge -> isDistributed() == false). Le vendeur V liste un item au prix de 30 niveaux, puis se deconnecte de tout le reseau.

T1 - Serveur A : l'acheteur achete. ZAuctionManager.java:802 `withdraw(acheteur, 30)` -> l'acheteur perd bien 30 niveaux.

T2 - Serveur A, l.810-814 : `!isAutoClaim()` false, `mustBeOnline()` false, `isDistributed()` false -> `deferDeposit = false`.

T3 - Serveur A, l.821-823 : `transactionStatus = RETRIEVED` puis `deposit(V, 30)` -> LevelProvider.java:14 `Bukkit.getPlayer(V)` renvoie null -> la methode ne fait STRICTEMENT RIEN, ne leve rien, ne logge rien. Le try/catch l.824 ne detecte aucune erreur.

T4 - Serveur A, l.848 : `createTransaction(..., RETRIEVED)` avec before/after = 0/0 (getBalance rend ZERO hors ligne). La base enregistre que V a bien recu ses 30 niveaux.

T5 - V se reconnecte, sur n'importe quel serveur. ClaimService.java:117-121 ne filtre que sur PENDING : aucune ligne a reclamer. Les 30 niveaux ont disparu du reseau ; l'acheteur a paye, le vendeur n'a rien.

La protection `isDistributed()` ne couvre que le cas "vendeur sur un autre serveur du cluster" : elle ne protege PAS le noeud qui n'a pas l'addon Redis charge, ni un deploiement mono-serveur.

**Impact** — Perte definitive et silencieuse pour le vendeur a chaque vente hors ligne sur ces economies, avec une ligne transactions RETRIEVED aux colonnes before/after fausses (0 -> 0) qui rend tout audit a posteriori impossible. Le seul garde-fou (`must-be-online`) est un opt-in que l'admin doit deviner.

**Précision apportée par la contre-expertise**

Le constat est REEL et prouve, mais trois points de description doivent etre corriges :

A) INEXACTITUDE sur les colonnes de la transaction (T4 / impact). Le constat annonce `before/after = 0/0`. Le code reel (ZAuctionManager.java:845-849) est :

auctionEconomy.get(seller.getUniqueId()).thenAccept(sellerBalance -> {

var beforeBalance = deferred ? sellerBalance : sellerBalance.subtract(finalSellerReceives);

storageManager.createTransaction(auctionItem, seller.getUniqueId(), economyName, beforeBalance, sellerBalance, finalSellerReceives, transactionStatus);

Signature verifiee : StorageManager.java:147 `createTransaction(item, uuid, economyName, before, after, value, status)`.

Dans le cas non-defere avec vendeur hors ligne, getBalance() renvoie 0, donc before = 0 - 30 = **-30** et after = **0**, avec value = +30 et status = RETRIEVED. La ligne enregistree est donc encore plus incoherente que decrite : un solde AVANT negatif, un solde APRES inferieur au solde avant, et une valeur positive. Le constat sous-estime l'anomalie plutot qu'il ne l'exagere.

B) NUANCE sur "silencieux". Pour LEVEL et EXPERIENCE la perte est effectivement 100% silencieuse (aucun log, aucune exception). Pour ITEM et ZMENUITEMS un `severe("Deposit items to <uuid> but is offline")` est bien emis en console (ItemProvider.java:29, ZMenuItemProvider.java:44) : la perte reste non recuperable et la transaction reste faussement RETRIEVED, mais un admin qui lit ses logs a une trace. Formuler l'impact comme "totalement silencieux pour LEVEL/EXPERIENCE, trace console seulement pour ITEM/ZMENUITEMS".

C) PORTEE DE LA CONFIGURATION. Le economies.yml livre ne contient qu'une seule economie (`- type: VAULT`, is-enable: true, l.42-52) pour laquelle le depot hors ligne fonctionne. Le bug ne se declenche donc PAS sur une installation par defaut : il faut que l'admin ajoute lui-meme une economie LEVEL / EXPERIENCE / ITEM / ZMENUITEMS (types documentes comme supportes, economies.yml:25-27). En revanche des qu'il le fait, le defaut de code (ZEconomyManager.java:301 `must-be-online` -> false) s'applique et CHAQUE vente hors ligne detruit le paiement. Le commentaire d'aide economies.yml:74-76 ("Useful for economy plugins that don't support offline transactions") n'indique nulle part quels types sont concernes. La severite "haute" reste justifiee (perte definitive, repetee, non auditable), mais l'exploitation exige une config admin, pas les valeurs livrees.

D) COMPLEMENT sur la correction proposee #3. Le point 3 vise `ClaimService.java:69 if (player.isOnline())` : cette condition est en pratique toujours vraie (claimMoney(Player) n'est appele que pour un joueur connecte, ClaimService.java:130/150/159). Le vrai defaut de ce fichier est ailleurs et devrait etre ajoute a la correction : ligne 84 `repository.updateStatus(transactionIds, TransactionStatus.RETRIEVED);` est execute INCONDITIONNELLEMENT, y compris quand le deposit a leve une exception (l.72-75, le `continue` saute juste le message mais pas le marquage) ou quand `player.isOnline()` etait faux. Meme probleme en l.199-210 dans clearPendingTransactions. Il faut ne marquer RETRIEVED que les ids effectivement credites, pas la liste complete.

Le reste du constat (fichier, lignes 810-823, chaine d'appel, absence totale de garde, non-reclamabilite via ClaimService.java:117-121, et l'approche de correction 1/2/4) est verifie exact.

**Correctif**

````java
Ne jamais tenter un depot immediat vers un joueur absent du serveur, quelle que soit la configuration.
1) Ajouter au contrat API un predicat de capacite -- dans AuctionEconomy : `default boolean supportsOfflineDeposit() { return true; }`, implemente dans ZAuctionEconomy :
```java
@Override
public boolean supportsOfflineDeposit() {
    return !(this.currencyProvider instanceof ItemProvider)       // couvre ZMenuItemProvider (extends ItemProvider)
        && !(this.currencyProvider instanceof LevelProvider)
        && !(this.currencyProvider instanceof ExperienceProvider);
}
```
2) ZAuctionManager.java:811-814 devient :
```java
boolean deferDeposit = !auctionEconomy.isAutoClaim()
        || (!sellerOnThisServer && auctionEconomy.mustBeOnline())
        || (!sellerOnThisServer && clusterBridge.isDistributed())
        || (!sellerOnThisServer && !auctionEconomy.supportsOfflineDeposit());
```
3) Meme garde dans ClaimService.java:69 : remplacer `if (player.isOnline())` par `if (player.isOnline() || economy.supportsOfflineDeposit())`, et ne rien marquer RETRIEVED sinon.
4) Avertissement au demarrage dans ZEconomyManager.loadEconomy : si `!supportsOfflineDeposit() && !mustBeOnline`, logger un WARNING et forcer `mustBeOnline = true`.
````

---

<a id="c-033"></a>

### `C-033` — Migration V3 : l'insert de log avec item_id = 0 viole la cle etrangere sous MySQL et leve une DatabaseException que catch (SQLException) ne voit pas -- tout l'argent PENDING V3 est perdu

- [ ] **Corrigé**
- **Fichier** : `migration/v3/V3MigrationService.java:345`
- **Catégorie** : Perte d’argent — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
V3MigrationService.createLogEntry (l.345-368) insere item_id = 0 et n'attrape que SQLException :
    private void createLogEntry(V3Transaction v3Trans) {
        try {
            Schema schema = SchemaBuilder.insert(Tables.LOGS, s -> {
                s.string("log_type", LogType.PURCHASE.name());
                s.object("item_id", 0); // No direct mapping, use 0
                ...
            schema.execute(plugin.getStorageManager().with(PlayerRepository.class).getConnection(), logger);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create log entry: " + e.getMessage());
        }
    }

V3MigrationService.migrateTransactions (l.318-326) : l'argent est ecrit APRES le log, dans le meme try :
                createLogEntry(v3Trans);
                if (v3Trans.isNeedMoney()) {
                    createPendingTransaction(v3Trans);
                }

CreateLogsMigration.java:12 -- la FK existe reellement :
            table.integer("item_id").foreignKey(Tables.ITEMS, "id", true);
Sarah/.../SchemaBuilder.java:375 la traduit en `FOREIGN KEY (%s) REFERENCES %s(%s) ON DELETE CASCADE`, et Sarah/.../requests/CreateRequest.java:58 force ` ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` : la contrainte est appliquee.

Sarah/.../requests/InsertRequest.java:72-75 -- l'echec ne produit JAMAIS de SQLException :
        } catch (SQLException exception) {
            logger.info("Insert operation failed on table: " + this.schema.getTableName() + " - " + exception.getMessage());
            throw new DatabaseException("insert", this.schema.getTableName(), exception);
        }
Sarah/.../exceptions/SarahException.java:6 -- `public class SarahException extends RuntimeException`.

Aucune occurrence de `foreign_keys` dans Sarah/src : le PRAGMA n'est jamais emis, donc les FK ne sont PAS appliquees sous SQLite.
```

**Chronologie**

1) items.id est AUTO_INCREMENT : aucune ligne n'a jamais id = 0. Sous MySQL/MariaDB, l'insert `s.object("item_id", 0)` de createLogEntry (l.349) viole la FK -> ERROR 1452.

2) InsertRequest.java:74 leve une DatabaseException, qui etend RuntimeException : elle TRAVERSE le `catch (SQLException e)` l.366 et sort de createLogEntry.

3) migrateTransactions:322 `createPendingTransaction(v3Trans)` N'EST DONC JAMAIS ATTEINT. L'argent en attente de chaque vente V3 non reclamee n'est jamais ecrit dans %prefix%transactions.

4) L'exception est ramassee par le `catch (Exception e)` de migrateTransactions (l.333) qui n'emet qu'un warning et incremente `errors` ; la commande affiche malgre tout un succes global.

5) Le defaut est INVISIBLE en test : sous SQLite, Sarah n'active jamais les FK, donc l'insert passe. Il n'apparait qu'en production MySQL -- exactement le mode d'un reseau multi-serveurs.

6) Corollaire de la meme cause : createPendingTransaction (l.373) insere lui aussi `s.object("item_id", 0)` alors que CreateTransactionsMigration.java:12 declare `table.integer("item_id").foreignKey(Tables.ITEMS, "id", true)` -- meme si l'ordre etait inverse, cet insert echouerait pour la meme raison.

7) Les quatre `catch (SQLException)` de V3MigrationService (l.271, l.307, l.366, l.385) sont morts pour ce cas, et le test `if (itemId == -1)` de migrateItems (l.230) est du code mort.

**Impact** — Sur toute installation MySQL/MariaDB, la totalite de l'argent V3 en attente de reclamation est perdue silencieusement, et aucun log d'achat V3 n'est importe, alors que la commande annonce un succes. Les vendeurs V3 ne retrouvent jamais leurs gains.

**Précision apportée par la contre-expertise**

Le constat est exact sur le fond. Quatre precisions/ajouts apres verification :

1) NUMEROS DE LIGNE legerement decales (le fond est exact) : le `catch (SQLException e)` de createLogEntry est a la ligne 363 (et non 366) ; createPendingTransaction commence l.371 avec `s.object("item_id", 0)` l.374 (et non 373) ; les quatre catch reels sont aux lignes 272, 307, 363 et 385 (le constat annonce 271, 307, 366, 385).

2) NUANCE sur "silencieusement" : messages.yml:563-568 (`migration-success`) affiche bien un champ `Errors: %errors%`, alimente par V3MigrationResult.getErrors(). L'admin voit donc un compteur d'erreurs non nul — mais le message declare explicitement "Migration from %source% completed successfully!" et `Transactions: 0`. La perte n'est pas totalement invisible, elle est simplement presentee comme un succes.

3) IMPACT AGGRAVANT non mentionne par le constat : migrateItems() (l.223-251) s'execute AVANT migrateTransactions() et reussit. Apres correction du bug, re-lancer `/ah admin migrate zauctionhousev3 confirm` re-inserera l'integralite des items V3 une seconde fois (aucune idempotence : createItem fait un INSERT pur, aucun controle d'existence). L'administrateur ne peut donc pas simplement rejouer la migration pour recuperer l'argent sans dupliquer tous les items deja importes — il faut d'abord purger les tables. Ce point doit figurer dans la procedure de remediation.

4) RESERVE sur la correction proposee n°1 : rendre `item_id` nullable "dans une nouvelle migration" ne suffit pas — MigrationManager de Sarah ne sait qu'AJOUTER des colonnes manquantes (il ne fait pas d'ALTER ... MODIFY sur une colonne existante, cf. MigrationManager.java:113 qui ne lit que `PRAGMA table_info`), donc les installations existantes garderont `item_id INT NOT NULL` + la FK. Sur les bases deja creees il faut un ALTER TABLE explicite (`MODIFY item_id INT NULL` + DROP/ADD de la contrainte). Les points 2, 3 et 4 de la correction proposee (catch elargi, ordre argent-avant-log avec try/catch separes, echec de la commande si errors > 0) sont valides et suffisent a eux seuls a supprimer la perte d'argent uniquement si le point 1 est aussi applique, puisque createPendingTransaction ecrit lui aussi item_id = 0 dans %prefix%transactions qui porte la meme FK (CreateTransactionsMigration.java:12).

Portee exacte : defaut ponctuel au moment de la migration V3 -> V4 sur MySQL/MariaDB uniquement. Ce n'est PAS un vecteur de duplication multi-serveurs, mais bien une perte d'argent (tout le PENDING V3 non reclame) et une perte d'historique (aucun log d'achat V3 importe).

**Correctif**

````java
1) Ne plus ecrire item_id = 0 : rendre la colonne nullable dans une nouvelle migration (`table.integer("item_id").nullable().foreignKey(...)`) et ecrire NULL pour les entrees sans item, sur %prefix%logs ET %prefix%transactions.
2) Remplacer les quatre `catch (SQLException e)` de V3MigrationService par `catch (Exception e)` (ou `catch (SQLException | SarahException e)`), sans quoi aucune erreur d'insert n'est jamais rattrapee au bon niveau.
3) Inverser l'ordre dans migrateTransactions (l.318-326) : creer D'ABORD `createPendingTransaction` (l'argent) puis `createLogEntry` (l'historique), et entourer chaque appel de son propre try/catch afin qu'un echec de log ne fasse jamais perdre l'argent :
```java
try { if (v3Trans.isNeedMoney()) createPendingTransaction(v3Trans); }
catch (Exception e) { logger.severe("LOST V3 money for " + v3Trans.getSeller() + ": " + e.getMessage()); errors.incrementAndGet(); }
try { createLogEntry(v3Trans); } catch (Exception e) { logger.warning("log entry failed: " + e.getMessage()); }
```
4) Faire echouer la commande (et non afficher un succes) des que `errors` est non nul.
````

---

<a id="c-034"></a>

### `C-034` — Migration V3 sans aucune idempotence : rejouer la commande duplique tous les items ET tout l'argent PENDING

- [ ] **Corrigé**
- **Fichier** : `migration/v3/V3MigrationService.java:107`
- **Catégorie** : Duplication d’argent — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
V3MigrationService.migrate (l.107-171, lu en entier) : la methode teste la connexion, compte les items/transactions, lit les donnees et insere. Elle ne consulte JAMAIS l'etat de la base V4 -- aucun marqueur "deja migre", aucune cle unique, aucune transaction SQL :
    public CompletableFuture<V3MigrationResult> migrate(V3DataReader reader) {
        return CompletableFuture.supplyAsync(() -> {
            ...
                Boolean connected = reader.testConnection().join();
                if (!connected) return V3MigrationResult.failure("Failed to connect to V3 data source");
                int itemCount = reader.getItemCount().join();
                ...
                if (itemCount == 0 && transactionCount == 0) return V3MigrationResult.failure("No data found to migrate");

V3MigrationService.createItem (l.256-269) : l'identifiant V3 (`v3Item.getId()`) n'est ecrit NULLE PART dans V4, alors que items.id est un auto-increment -- il n'existe donc structurellement aucun moyen de correler un item V3 a un item deja migre :
            Schema schema = SchemaBuilder.insert(Tables.ITEMS, s -> {
                s.string("item_type", ItemType.AUCTION.name());
                s.uuid("seller_unique_id", v3Item.getSeller());
                ...

V3MigrationService.createPendingTransaction (l.371-386) : chaque execution reinsere une ligne PENDING :
            Schema schema = SchemaBuilder.insert(Tables.TRANSACTIONS, s -> {
                s.object("item_id", 0);
                s.uuid("player_unique_id", v3Trans.getSeller());
                ...
                s.decimal("value", BigDecimal.valueOf(v3Trans.getPrice()));
                s.string("status", TransactionStatus.PENDING.name());
```

**Chronologie**

T0 - Les serveurs A, B et C partagent la meme base V4 MySQL. L'admin tape `/ah admin migrate zauctionhousev3 confirm` sur A. La commande ne verifie que le litteral "confirm".

T1 - A insere N lignes %prefix%items (storage_type issu de V3) + M lignes %prefix%transactions status='PENDING' correspondant a l'argent V3 non reclame.

T2 - A recharge son cache local. B et C n'apprennent RIEN : aucun message cluster n'est emis par la migration (le seul appelant de notifyItemListed dans tout le depot est SellService). Les joueurs connectes sur B et C ne voient aucun item migre.

T3 - L'admin, constatant que B ne montre rien, retape exactement la meme commande sur B (ou relance sur A apres un doute). Rien ne s'y oppose : ni marqueur en base, ni contrainte d'unicite, ni garde dans migrate().

T4 - N items identiques supplementaires (nouveaux ids auto-increment) et M transactions PENDING supplementaires sont ecrits.

T5 - Chaque vendeur V3 fait `/ah claim` et encaisse DEUX FOIS le montant de sa vente V3 ; chaque item V3 existe en deux exemplaires reclamables et vendables sur tout le reseau.

**Impact** — Duplication integrale de l'inventaire V3 et doublement de tout l'argent en attente, a partir d'une commande qu'un admin est naturellement pousse a relancer quand le resultat parait incomplet sur les autres noeuds. Irreversible sans nettoyage SQL manuel, puisqu'aucune colonne ne permet de distinguer l'original du doublon.

**Précision apportée par la contre-expertise**

Titre exact : "Migration V3 sans aucune idempotence : rejouer la commande duplique integralement l'inventaire V3 (l'argent PENDING, lui, n'est jamais migre sur MySQL)".

Categorie exacte : duplication-item (et non duplication-argent).

Description exacte du defaut reel :

`V3MigrationService.migrate` (V3MigrationService.java:107) ne consulte jamais l'etat de la base V4 : pas de marqueur d'execution, pas de cle unique, pas de transaction SQL englobante. Le seul appelant, `CommandAuctionAdminMigrate.perform` (CommandAuctionAdminMigrate.java:66), ne verifie que le litteral "confirm" — ni garde "deja migre", ni verrou "migration en cours". `createItem` (l.256-276) n'ecrit nulle part `v3Item.getId()` et `%prefix%items.id` est un auto-increment sans unicite metier (CreateItemMigration.java:12), donc aucune correlation V3<->V4 n'est possible.

Consequence reelle et prouvee : chaque nouvelle execution de `/ah admin migrate zauctionhousev3 confirm` reinsere N items V3 supplementaires, immediatement visibles et achetables par tout le reseau. L'admin y est activement pousse car la migration n'emet aucune notification cluster (`notifyItemListed` n'est appele que depuis SellService.java:391) et ne recharge le cache que du noeud migrant (CommandAuctionAdminMigrate.java:92) : les autres serveurs paraissent vides. Irreversible sans nettoyage SQL manuel.

Ce qu'il faut RETIRER du constat : la partie "doublement de tout l'argent PENDING" et l'etape T5. Sur MySQL/MariaDB — le deploiement du scenario — `createLogEntry` (l.349) et `createPendingTransaction` (l.374) inserent `item_id = 0` dans deux tables portant une FK InnoDB vers `items(id)` (CreateLogsMigration.java:12, CreateTransactionsMigration.java:36, FK reellement emise par SchemaBuilder.java:385 et CreateRequest.java:50-59 avec ENGINE=InnoDB force). Aucune ligne items.id=0 n'existant, chaque insert echoue en erreur 1452 ; l'exception est une `DatabaseException extends SarahException extends RuntimeException` (InsertRequest.java:71) que le `catch (SQLException)` local n'intercepte pas, et comme createLogEntry precede createPendingTransaction, ce dernier n'est jamais atteint. Zero transaction PENDING migree des le premier run : rien a doubler.

=> Cela revele en revanche un SECOND defaut, distinct et a signaler separement : sur MySQL, l'historique et surtout TOUT l'argent V3 non reclame sont silencieusement perdus a la migration (100 % d'echec des transactions, compte en "errors", et `MigrationResult.success` est quand meme renvoye par V3MigrationProvider.java:126). Correctif : creer une vraie ligne items ou rendre `item_id` nullable et passer NULL au lieu de 0.

Correctifs proposes : ceux du constat restent pertinents (v3_id UNIQUE sur items, sentinelle dans %prefix%migrations + flag --force, notification/rechargement cluster en fin de migration). Ajouter : englober la migration dans une transaction SQL, et corriger `item_id = 0` avant meme de parler d'idempotence des transactions/logs, puisque leur insert echoue actuellement de toute facon.

**Correctif**

````java
1) Ajouter une migration V4 creant `v3_id VARCHAR(36) NULL` sur %prefix%items avec un index UNIQUE, et `v3_transaction_id INT NULL UNIQUE` sur %prefix%transactions.
2) V3MigrationService.createItem : ecrire `s.string("v3_id", v3Item.getId().toString())` ; avant l'insert, `SELECT id FROM items WHERE v3_id = ?` et sauter l'item s'il existe (compter en "skipped").
3) Idem pour createPendingTransaction / createLogEntry avec `v3_transaction_id`.
4) A minima, en tete de migrate() : ecrire une ligne sentinelle dans %prefix%migrations (ex. 'v3-data-import') et refuser toute nouvelle execution si elle existe, avec un flag explicite `--force` :
```java
if (migrationAlreadyDone("v3-data-import") && !force) {
    return V3MigrationResult.failure("V3 data was already migrated. Re-run with --force to import again.");
}
```
5) Emettre les notifications cluster (ou demander explicitement un reload des autres noeuds) a la fin de la migration, pour supprimer la raison meme qui pousse l'admin a relancer la commande.
````

---

<a id="c-035"></a>

### `C-035` — PurchaseService rediffuse IS_PURCHASE_CONFIRM au cluster apres avoir constate en base que l'item est deja vendu

- [ ] **Corrigé**
- **Fichier** : `services/PurchaseService.java:178`
- **Catégorie** : Cohérence de cache — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
PurchaseService.java:76 - on capture l'etat d'UI local comme etat a restaurer :
        final AtomicReference<ItemStatus> previousStatusHolder = new AtomicReference<>(item.getStatus()); // == IS_PURCHASE_CONFIRM, garanti par la garde l.68

PurchaseService.java:120-125 - on constate que l'item est vendu ailleurs :
                            .thenCompose(dbItem -> {
                                if (dbItem == null || dbItem.getBuyerUniqueId() != null) {
                                    inventoryManager.updateInventory(player);
                                    resultHolder.set(PurchaseResult.failure("Item already sold", PurchaseFailReason.ITEM_NOT_AVAILABLE));
                                    return failedFuture(new IllegalStateException("Item already sold on another server"));
                                }

PurchaseService.java:176-183 - et on rediffuse quand meme un etat d'UI perime a TOUT le cluster :
                    if (item.getStatus() == ItemStatus.IS_BEING_PURCHASED) {
                        var previousStatus = previousStatusHolder.get();
                        item.setStatus(previousStatus);
                        clusterBridge.notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, previousStatus)
                                .exceptionally(restoreError -> { ... });
                    }
```

**Chronologie**

T0 - Item #42 vendu et regle sur le serveur A (etat Redis SOLD, ligne DB PURCHASED avec buyer renseigne).

T1 - Sur le serveur B, un second acheteur avait deja ouvert sa confirmation : son objet memoire est en IS_PURCHASE_CONFIRM, retenu par le cache ITEM_SHOW.

T2 - Il clique Confirmer. PurchaseService:68 passe. checkAvailability renvoie true car SOLD n'est pas un etat bloquant (RedisAuctionClusterBridge:167-169) et LOCK_SCRIPT accorde le verrou car seul DELETED est terminal.

T3 - PurchaseService:107 pose IS_BEING_PURCHASED et le diffuse.

T4 - PurchaseService:118-125 relit la base, voit buyer != null et avorte proprement : aucun argent debite. C'est le bon comportement.

T5 - MAIS le bloc exceptionally l.176-183 constate IS_BEING_PURCHASED et restaure previousStatusHolder, c'est-a-dire IS_PURCHASE_CONFIRM - un etat d'INTERFACE, local a un joueur, sans proprietaire ni TTL - puis le DIFFUSE a tout le reseau.

T6 - Sur A et sur tous les autres noeuds, ItemStatusListener applique aveuglement ce statut a leur copie de l'item. L'item vendu porte desormais IS_PURCHASE_CONFIRM sur les serveurs qui le detiennent en PURCHASED ou EXPIRED.

T7 - Sur B lui-meme, rien ne remettra AVAILABLE tant que le joueur ne ferme pas sa GUI ; si sa fenetre est fermee par une deconnexion, l'etat reste fige.

**Impact** — Un achat qui echoue CORRECTEMENT produit malgre tout une pollution d'etat globale : il repose sur tout le cluster un statut de confirmation qui n'appartient a personne, qui n'expire jamais et qui bloque les chemins de reclamation (RemoveService:96, :134, :172). Le mecanisme de securite est lui-meme le vecteur de la corruption.

**Précision apportée par la contre-expertise**

Le constat est REEL mais mal attribue sur trois points, et son correctif propose est dangereux.

1) LE BLOC :176-184 N'EST PAS LA SOURCE DE LA POLLUTION. Elle est deja emise a PurchaseService.java:107-108, AVANT la revalidation base :

item.setStatus(ItemStatus.IS_BEING_PURCHASED);

return clusterBridge.notifyItemStatusChange(item, previousStatusHolder.get(), ItemStatus.IS_BEING_PURCHASED)

IS_BEING_PURCHASED est donc deja parti sur tout le cluster et applique a l'aveugle (ItemStatusListener:54) aux copies distantes detenues en PURCHASED/EXPIRED. Le bloc :176-184 ne cree pas la corruption : il remplace un statut faux par un autre statut faux.

2) T7 EST FAUX : l'etat n'est pas fige. ConfirmHelper.java:41-52 (`onInventoryClose`) verifie `item.getStatus() == this.previous` (= IS_PURCHASE_CONFIRM pour ConfirmPurchaseButton:17), remet AVAILABLE et le rediffuse. ITEM_SHOW est toujours en cache car il n'est retire qu'en cas de succes (ZAuctionManager.java:896 `cache.remove(PlayerCacheKey.ITEM_SHOW)`). Le serveur B converge donc — mais vers AVAILABLE, tout aussi faux pour un item vendu, et ConfirmHelper:55 appelle en plus `updateListedItems(item, true, player)` qui reinjecte l'item vendu dans les GUI d'hotel des ventes ouvertes. La sortie n'est pas un blocage, c'est une convergence vers une autre valeur fausse.

3) LA RECOMMANDATION 3 CONTREDIT LE DESIGN EXISTANT. IS_PURCHASE_CONFIRM est diffuse VOLONTAIREMENT au cluster a ListedItemsButton.java:229-231 a l'ouverture de la confirmation, et ConfirmHelper:48/73 diffuse le retour a AVAILABLE. C'est la reservation souple inter-serveurs qui empeche deux joueurs de confirmer le meme item. La supprimer retirerait ce garde-fou.

4) LE CORRECTIF PROPOSE AGGRAVE LE CAS DISTANT. Dans la branche `itemGoneHolder`, il n'emet AUCUNE notification de statut : le broadcast IS_BEING_PURCHASED deja parti a :108 ne serait alors JAMAIS corrige, laissant les copies distantes bloquees sur IS_BEING_PURCHASED de facon permanente. Toute correction doit soit emettre un statut terminal correctif, soit — et c'est la vraie racine — durcir ItemStatusListener.java:54 pour refuser d'appliquer un statut du cycle LISTED (AVAILABLE / IS_*_CONFIRM / IS_BEING_*) a un item detenu en PURCHASED ou EXPIRED.

5) IMPACT SOUS-ESTIME, dans le meme bloc exceptionally : `clusterBridge.unlockItem(item, token, StorageType.LISTED)` a PurchaseService.java:169 EFFACE LE MARQUEUR SOLD DANS REDIS. LOCK_SCRIPT a ecrase l'etat SOLD par LOCKED (RedisAuctionClusterBridge:64 `redis.call('HSET', itemKey, 'state', 'LOCKED')`), puis UNLOCK_SCRIPT:86-89 fait `if currentState == 'LOCKED' then HSET state 'AVAILABLE'`. L'item vendu repasse donc AVAILABLE dans l'etat Redis autoritaire : tous les checkAvailability/lockItem ulterieurs reussiront. Seule la relecture base :118-125 empeche encore la duplication d'argent.

PRECONDITION A AJOUTER AU SCENARIO : la pollution distante ne se materialise qu'avec `purchased-item.give-item=false` (ligne DB PURCHASED conservee, item re-ajoute au store PURCHASED par ItemBoughtListener:104-108). Avec give-item=true la ligne est DELETED, ItemBoughtListener:49 a retire l'item partout, et ItemStatusListener:52 sort sur `if (item == null) return;` : aucune copie n'est polluee.

SEVERITE MAINTENUE A HAUTE : pas de duplication d'objet ni d'argent (la garde :118-125 tient), mais l'acheteur legitime ne peut plus reclamer son item PURCHASED (RemoveService:172) jusqu'au redemarrage, et l'etat SOLD du cluster est detruit.

**Correctif**

```java
Differencier les deux cas d'echec : quand l'abandon est cause par 'already sold', ne restaurer AUCUN statut mais converger vers la verite base.
1) Ajouter final AtomicBoolean itemGoneHolder = new AtomicBoolean(false); positionne a true dans le bloc l.121-125.
2) Remplacer le bloc l.176-184 par :
    if (itemGoneHolder.get()) {
        auctionManager.removeItem(StorageType.LISTED, item);
        auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH, PlayerCacheKey.ITEMS_SELLING);
        auctionManager.getCache(player).remove(PlayerCacheKey.ITEM_SHOW);
    } else if (item.getStatus() == ItemStatus.IS_BEING_PURCHASED) {
        item.setStatus(ItemStatus.AVAILABLE);   // et non IS_PURCHASE_CONFIRM
        clusterBridge.notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, ItemStatus.AVAILABLE)...;
    }
3) De facon generale, ne jamais diffuser IS_PURCHASE_CONFIRM / IS_REMOVE_CONFIRM au cluster : ce sont des etats d'UI par joueur.
```

---

<a id="c-036"></a>

### `C-036` — Repli silencieux en mono-serveur : si l'addon Redis n'atteint jamais la ligne 99, le noeud garde LocalAuctionClusterBridge sans le moindre avertissement

- [ ] **Corrigé**
- **Fichier** : `REDIS/ZAuctionHouseRedis.java:77`
- **Catégorie** : Duplication d’item — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZAuctionHouseRedis.java:77-79
        if (!this.loadRedis()) {
            return;
        }

ZAuctionHouseRedis.java:99 (seul point d'installation du bridge, situe APRES le return ci-dessus)
        this.auctionPlugin.setAuctionClusterBridge(new RedisAuctionClusterBridge(this, this.jedisPool, this.lockTtl, itemStateTtl, itemListedTtl));

V4/src/main/java/fr/maxlego08/zauctionhouse/ZAuctionPlugin.java:100
    private AuctionClusterBridge auctionClusterBridge = new LocalAuctionClusterBridge();

V4/src/main/java/fr/maxlego08/zauctionhouse/cluster/LocalAuctionClusterBridge.java:19-21
    public CompletableFuture<Boolean> checkAvailability(Item item) {
        return CompletableFuture.completedFuture(!itemLocks.containsKey(item.getId()));
    }

V4/API/src/main/java/fr/maxlego08/zauctionhouse/api/cluster/AuctionClusterBridge.java:104 (non redefini par LocalAuctionClusterBridge)
    default boolean isDistributed() {
        return false;
    }
```

**Chronologie**

T0 - Serveurs A, B, C partagent la meme base MySQL. Sur C, Redis est injoignable au boot (maintenance, mauvais host, mot de passe change) : loadRedis() logge "Failed to connect to Redis", appelle disablePlugin(this) et renvoie false ; onEnable sort ligne 78 sans jamais executer la ligne 99. Le plugin principal de C reste sur l'instance creee ligne 100 de ZAuctionPlugin. AUCUN log n'est emis cote zAuctionHouse (grep : les seules occurrences de LocalAuctionClusterBridge dans src sont l'import et l'initialisation du champ), et /ah continue de fonctionner normalement.

T1 - Sur A, un joueur confirme l'achat de l'item 42 : PurchaseService.java:92 prend le verrou Redis (auction:lock:42), l'etat passe a LOCKED, puis PurchaseService.java:118 relit la ligne 42 (encore LISTED, buyer NULL) et la chaine continue.

T2 - Avant que A n'ait execute ZAuctionManager.java:883 (updateItem), un joueur de C confirme l'achat du meme item 42. checkAvailability de C interroge sa ConcurrentHashMap locale, vide -> true. lockItem de C fait putIfAbsent dans cette meme map locale -> succes immediat. Le verrou Redis pose par A est totalement invisible.

T3 - C relit lui aussi la ligne 42 (PurchaseService.java:118) : elle est encore LISTED avec buyer NULL, donc la revalidation passe.

T4 - A et C executent tous les deux ZAuctionManager.java:802 (withdraw acheteur) puis ZAuctionManager.java:884 (giveItem). Deux acheteurs paient, l'item est remis deux fois. Les deux UPDATE finaux sur la ligne 42 s'ecrasent l'un l'autre.

T5 - En prime, isDistributed() vaut false sur C : ZAuctionManager.java:812-814 (deferDeposit) fait un deposit immediat au vendeur hors-ligne au lieu de creer une transaction PENDING, et notifyItemBought/notifyItemListed/removeItem de C sont des completedFuture(null) : A et B n'apprendront jamais rien de ce que fait C.

**Impact** — Duplication d'item et double paiement des l'instant ou UN SEUL noeud du reseau perd Redis au demarrage. Le mode dans lequel tourne le plugin (mono-serveur vs cluster) n'est ecrit nulle part : ni log de demarrage, ni placeholder, ni commande de diagnostic. Un admin qui voit "zAuctionHouse has just been loaded successfully!" sur les trois serveurs n'a aucun moyen de savoir que C vend sans verrou partage. Aggravation supplementaire : sur C les items expires suivent le chemin mono-serveur (ExpireService.java:51 teste isDistributed()), et les depots d'argent aux vendeurs hors-ligne ne passent plus par le systeme de claim.

**Correctif**

1) Rendre le mode explicite et verifiable cote plugin principal : dans ZAuctionPlugin.onEnable, logger la classe du bridge actif (this.auctionClusterBridge.getClass().getSimpleName()) et re-logger a chaque setAuctionClusterBridge. 2) Ajouter dans config.yml une cle `cluster.required: false` ; quand elle vaut true, planifier a la fin de onEnable un controle differe (ex. 10 s, apres le enable de tous les plugins) qui, si getAuctionClusterBridge().isDistributed() est false, logge SEVERE et bascule un flag `clusterDegraded` bloquant SellService/PurchaseService/RemoveService avec un message joueur ("hotel des ventes temporairement indisponible") plutot que de laisser vendre sans verrou. 3) Cote addon, en cas d'echec de loadRedis(), appeler explicitement `this.auctionPlugin.setAuctionClusterBridge(bridgeRefusant)` : un bridge qui echoue systematiquement (failedFuture) est infiniment plus sur qu'un bridge local silencieux sur un reseau multi-serveurs.

---

<a id="c-037"></a>

### `C-037` — SellService : le bloc .exceptionally rembourse les items alors que postSell a deja commite et diffuse la mise en vente

- [ ] **Corrigé**
- **Fichier** : `services/SellService.java:111`
- **Catégorie** : Duplication d’item — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// SellService.java:105-130
removeItemsFromSlots(player, validSlotItems);

var storageManager = this.plugin.getStorageManager();
storageManager.createAuctionItem(player, price, expiredAt, itemsToSell, auctionEconomy).thenAccept(auctionItem -> {
    this.postSell(player, auctionItem, auctionEconomy, taxResult);
    resultFuture.complete(SellResult.success("Item listed successfully", auctionItem));
}).exceptionally(throwable -> {
    this.plugin.getLogger().severe("Unable to sell item: " + throwable.getMessage());
    ...
    // Return items to player safely
    if (player.isOnline() && itemsToSell != null) {
        itemsToSell.forEach(itemStack -> {
            if (itemStack != null) {
                player.getInventory().addItem(itemStack);
            }
        });
    }

// SellService.java:375-396 -- ce que postSell a deja fait avant de pouvoir lever
this.manager.addItem(StorageType.LISTED, auctionItem);
...
String encodedItemStack = auctionItem.getItemStacks().stream().map(Base64ItemStack::encode).collect(Collectors.joining(";"));
...
this.plugin.getAuctionClusterBridge().notifyItemListed(auctionItem)...

// SellService.java:399-407 -- code post-diffusion qui peut lever
if (this.plugin instanceof ZAuctionPlugin zAuctionPlugin) {
    var discordService = zAuctionPlugin.getDiscordWebhookService();
    if (discordService != null && discordService.isEnabled()) { discordService.notifyItemSold(player, auctionItem); }
    zAuctionPlugin.getBroadcastService().broadcastSell(player, auctionItem);
}
```

**Chronologie**

Le .exceptionally est chaine sur le future retourne par thenAccept : il attrape donc aussi toute exception levee A L'INTERIEUR de postSell, c'est-a-dire APRES le commit.

T0 -- Serveur A : le joueur vend un lot. createAuctionItem (ZStorageManager.java:141-144) commite la ligne items + les lignes auction_items dans le MySQL PARTAGE. La vente est desormais un fait pour tout le cluster.

T1 -- postSell fait addItem(StorageType.LISTED, ...) puis notifyItemListed : Redis passe l'item en AVAILABLE et publie ItemListedMessage.

T2 -- Serveur B recoit le message, charge l'item depuis MySQL et l'ajoute a ses LISTED. L'annonce est vivante sur tout le reseau.

T3 -- Toujours sur A, postSell leve plus loin : Base64ItemStack.encode (ligne 384) sur un ItemStack non serialisable, ou un NPE dans getBroadcastService()/discordService (lignes 399-406), ou getItemDisplay sur un item exotique.

T4 -- .exceptionally s'execute et REND les items dans l'inventaire du vendeur (lignes 117-123).

RESULTAT : le vendeur a recupere ses items ET l'annonce reste vendable sur A comme sur B. Le premier acheteur paie et recoit une copie.

**Impact** — Duplication d'item deterministe des que postSell leve : le vendeur garde la marchandise et l'annonce reste active dans la base partagee et sur tous les serveurs. La taxe de vente est en plus remboursee (ligne 125-127) alors que la vente est comptabilisee.

**Précision apportée par la contre-expertise**

Le constat est REEL mais partiellement mal etaye ; description exacte :

1) MECANIQUE (confirmee) : `storageManager.createAuctionItem(...).thenAccept(postSell).exceptionally(refund)`. L'`exceptionally` couvre deux cas indistinguables : (a) echec de l'INSERT (compensable) et (b) exception dans `postSell` APRES commit (non compensable). Dans le cas (b) le vendeur recupere ses items (lignes 117-123) + sa taxe (125-127) alors que les lignes `items`/`auction_items` existent dans le MySQL partage et que l'annonce est deja dans les LISTED de A (ligne 375) et potentiellement diffusee au cluster (ligne 391).

2) DEUX DECLENCHEURS CITES SONT FAUX — a corriger dans la preuve :

- `Base64ItemStack.encode` (ligne 384) ne peut pas faire lever : il `catch (IOException)` et retourne `null` (API/.../utils/Base64ItemStack.java:44-46), et `Collectors.joining(";")` sur un element null n'NPE pas (`StringJoiner.add` -> `StringBuilder.append((CharSequence) null)` ajoute le litteral "null").

- `getBroadcastService()` ne peut pas etre null : assigne inconditionnellement dans `onEnable` (`ZAuctionPlugin.java:141`), jamais remis a null ; et `discordService` est deja null-garde ligne 401.

3) LES VRAIS DECLENCHEURS (a substituer) : `postSell` s'execute sur `asyncExecutor` (le `thenAccept` sans executor s'execute sur le thread qui complete le future de `supplyAsync`), donc HORS thread entite. Les points qui peuvent reellement lever :

- ligne 373 `applyCategories` -> `ZCategoryManager.getCategoriesFor` (ligne 179-189) -> `category.matches(context)` (ligne 183), qui evalue les regles des `Hooks/` (ItemsAdder, MMOItems, Nexo, Oraxen, Slimefun, CraftEngine, ExecutableItems) depuis un thread async — API tierces non thread-safe.

- ligne 382 `message(...)` -> `MessageUtils.sendTchatMessage` -> `papi(getString(message, args), sender)` : PlaceholderAPI resolu hors thread principal, sur une chaine contenant le nom d'affichage de l'item controle par le joueur (via `getItemDisplay()`), ce qui rend le declenchement partiellement influencable par un attaquant.

- ligne 406 `broadcastSell` : boucle sur `getOnlinePlayers()` + `getItemDisplay()` + `papi` hors thread entite (BroadcastService.java:31-45).

4) NUANCE PLATEFORME IMPORTANTE, absente du constat : `plugin.yml:8` declare `folia-supported: true`. Le remboursement ligne 120 (`player.getInventory().addItem`) tourne lui aussi sur le thread async. Sur Folia cet appel leve `IllegalStateException` : le resultat n'est alors PAS une duplication mais une PERTE d'items (l'annonce reste, le joueur ne recupere rien, l'exception est avalee par le `exceptionally`). La duplication decrite est le comportement Paper/Spigot. Le correctif propose (`runAtEntity` autour du refund) corrige aussi ce second defaut et doit etre conserve.

5) SEVERITE : ramenee de "critique" a "haute". La duplication n'est pas sur le chemin nominal ni forcable de maniere fiable par un joueur : elle exige qu'une exception soit levee dans le code post-commit. Le defaut de code, lui, est certain. Le correctif propose (`whenComplete` + try/catch autour de `postSell` + ne JAMAIS rembourser apres commit) est correct ; ajouter le `runAtEntity` sur le chemin de remboursement et logger `auctionItem.getId()` pour reconciliation.

**Correctif**

```java
Separer strictement la compensation de l'INSERT de la suite non compensable :

storageManager.createAuctionItem(player, price, expiredAt, itemsToSell, auctionEconomy)
    .whenComplete((auctionItem, throwable) -> {
        if (throwable != null) {                       // seul cas ou l'INSERT n'a PAS eu lieu
            this.plugin.getScheduler().runAtEntity(player, t -> refundItems(player, itemsToSell, taxResult, auctionEconomy));
            resultFuture.complete(SellResult.failure("Database error", SellFailReason.DATABASE_ERROR));
            return;
        }
        try {
            this.postSell(player, auctionItem, auctionEconomy, taxResult);
        } catch (Exception e) {                        // NE JAMAIS rembourser ici : la vente est commitee
            this.plugin.getLogger().severe("postSell failed for item " + auctionItem.getId() + ": " + e.getMessage());
        }
        resultFuture.complete(SellResult.success("Item listed successfully", auctionItem));
    });
```

---

<a id="c-038"></a>

### `C-038` — Un ItemStack null rend l'item inachetable a vie et destructeur au retrait : la ligne passe en DELETED avant giveItem, dont la boucle s'interrompt au milieu

- [ ] **Corrigé**
- **Fichier** : `items/ZAuctionItem.java:155`
- **Catégorie** : Perte d’item — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZAuctionItem.java:154-157 - aucune tolerance au null, alors que getItemStack() en gere un (l.70-73) :
    @Override
    public String getItemsAsString() {
        return this.itemStacks.stream().map(i -> "x" + i.getAmount() + " " + i.getType().name()).collect(Collectors.joining(", "));
    }

ZAuctionManager.java:731-732 - appele en tout premier dans l'achat :
        String items = auctionItem.getItemsAsString();
        var itemsDisplay = auctionItem.getItemDisplay();

ZAuctionManager.java:568-569 - au retrait, la base est ecrasee AVANT la livraison :
        var updateFuture = storageManager.updateItem(item, StorageType.DELETED);
        giveItem(player, item);

ZAuctionManager.java:925-933 - la boucle n'est ni null-safe ni transactionnelle :
    public void giveItem(Player player, Item item) {
        if (item instanceof AuctionItem auctionItem) {
            var itemStacks = auctionItem.getItemStacks();
            for (ItemStack itemStack : itemStacks) {
                player.getInventory().addItem(itemStack).forEach((slot, dropItemStack) -> player.getWorld().dropItem(player.getLocation(), dropItemStack));
            }
```

**Chronologie**

T0 - Serveur B charge un lot de 3 stacks ecrit par le serveur A : Base64ItemStack.decode rend null pour l'un d'eux (cluster de versions Minecraft melangees, ou ligne corrompue). itemStacks = [stackOk, null, stackOk].

T1 - L'item s'affiche normalement sur B (getItemStack l.81 : icone de lot multiple car size() != 1).

T2 - Achat : ZAuctionManager:731 getItemsAsString() -> i.getAmount() sur l'element null -> NullPointerException levee dans le thenCompose de PurchaseService:134. Le future part en echec, exceptionally libere le verrou. Resultat pour le joueur : 'Internal error', en boucle, indefiniment. L'item reste liste et invendable pour toujours.

T3 - Le vendeur tente de le retirer sur B : ZAuctionManager.removeSellingItem:562-563 pose DELETED en memoire et retire du store, puis :568 lance updateItem(item, DELETED) - la ligne DB passera en DELETED quoi qu'il arrive, son future n'est jamais chaine a la livraison.

T4 - :569 giveItem : la boucle remet le stack #1, puis appelle addItem avec l'element null que l'API Bukkit rejette. L'exception interrompt la boucle : le stack #3 n'est JAMAIS remis.

T5 - L'exception remonte dans RemoveService.executeLocalRemovalStep, ou localRemovalCompleted est deja positionne : restoreStatusOnError (l.307-311) ne restaure RIEN.

T6 - Bilan : ligne DB DELETED, joueur servi a 1/3, item disparu de tous les noeuds au prochain ItemRemovedMessage. Deux stacks detruits, sans message d'erreur.

**Impact** — Perte definitive d'items pour le vendeur et item durablement inachetable pour tout le reseau, a partir d'un seul element null. L'ordre updateItem(DELETED) puis giveItem garantit que la base est purgee avant la livraison, sur les quatre chemins de retrait (ZAuctionManager:568-569, 602-603, 636-637, 883-884).

**Précision apportée par la contre-expertise**

Description exacte du defaut reel :

1) Origine (prouvee) : Base64ItemStack.decode (API/.../utils/Base64ItemStack.java:66-70) retourne null quand la deserialisation echoue, et ItemLoaderUtils.java:40 comme ZStorageManager.selectItem (ZStorageManager.java:195-196) inserent ce null tel quel dans la liste d'ItemStack de ZAuctionItem. Rien ne met l'item en quarantaine. La categorisation etant null-safe (ZCategoryManager.java:176 et :226), l'item est publie normalement.

2) Achat definitivement impossible : ZAuctionManager.java:731-732 appelle getItemsAsString() (ZAuctionItem.java:155-156) puis getItemDisplay() (ZAuctionItem.java:110-151), aucun des deux n'etant null-safe, contrairement a getItemStack (l.70-74), getAmount (l.93-98) et getTranslationKey (l.102-107). NPE dans le thenCompose de PurchaseService.java:129, capturee par exceptionally (l.156) : message "Internal error", verrou relache, item toujours LISTED. Rejouable a l'infini. A noter : l'NPE precede le withdraw de ZAuctionManager.java:802, donc l'acheteur n'est jamais debite - c'est un deni de vente, pas une perte d'argent.

3) Retrait destructeur : giveItem (ZAuctionManager.java:926-933) parcourt les stacks sans garde null et sans clone ; Inventory#addItem rejette un element null, l'exception coupe la boucle et les stacks suivants ne sont jamais rendus. Le probleme d'ordonnancement est reel et concerne SIX sites, pas quatre : updateItem(..., DELETED) est emis AVANT giveItem en :522-523, :568-569, :602-603, :636-637, :883-884 et :693-696, et son future n'est jamais chaine a la livraison. Toute exception dans giveItem laisse donc la ligne purgee et le joueur partiellement servi.

4) Correction de la chaine d'erreur (le constat se trompe ici) : dans RemoveService.java:250-252, `context.onLocalRemoval.get()` s'execute de facon synchrone, donc l'exception de giveItem survient AVANT `context.localRemovalCompleted = true`. restoreStatusOnError (l.307-311) restaure donc le statut et rediffuse un notifyItemStatusChange, et clusterBridge.removeItem n'est jamais appele : aucun ItemRemovedMessage n'est emis. L'etat final n'est pas "item disparu de tous les noeuds" mais l'inverse - ligne DB DELETED + item retire du store local, alors que les autres serveurs conservent un fantome AVAILABLE jusqu'au prochain redemarrage (fantome non achetable grace a la re-lecture sous verrou de PurchaseService.java:118-125).

Correctifs proposes valides, avec ces precisions :

- rendre giveItem null-safe, cloner chaque stack avant addItem, ne pas interrompre la boucle sur un element fautif, et n'emettre updateItem(..., DELETED) qu'apres livraison complete - aux SIX sites listes, dont adminRemoveItem:693-696 oublie par le constat ;

- rendre getItemsAsString (l.155-156), getItemDisplay (l.116-149) et ItemContentButton.java:44 (`.map(ItemStack::clone)`) null-safe, comme l'est deja getItemStack ;

- filtrer/mettre en quarantaine des ItemLoaderUtils.java:40 et ZStorageManager.selectItem, avec un log SEVERE portant l'id de l'item, plutot que d'exposer un lot contenant un null.

**Correctif**

1) Rendre giveItem tolerant et transactionnel : ignorer/journaliser les elements null, cloner chaque stack avant addItem, renvoyer le nombre de stacks reellement remis, et n'enchainer updateItem(..., DELETED) qu'APRES un giveItem complet (aujourd'hui l'ordre est inverse aux quatre endroits cites).
2) Rendre getItemsAsString et getItemDisplay null-safe (filtrer Objects::nonNull comme le fait deja getItemStack l.70-73), ainsi que ItemContentButton:44.
3) Ne jamais publier un item contenant un stack null : au chargement et dans selectItem, si un Base64ItemStack.decode rend null, mettre l'item en quarantaine et le signaler en SEVERE avec son id plutot que de l'exposer.

---

<a id="c-039"></a>

### `C-039` — Une exception dans la remise locale ressuscite l'item sur tout le cluster alors que sa ligne est deja DELETED

- [ ] **Corrigé**
- **Fichier** : `services/RemoveService.java:250`
- **Catégorie** : Duplication d’item — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```lua
// RemoveService.java:248-254
    private CompletableFuture<Void> executeLocalRemovalStep(RemovalContext context, AuctionClusterBridge clusterBridge, PerformanceConfiguration config) {

        return context.onLocalRemoval.get().thenCompose(v -> {
            context.localRemovalCompleted = true;
            return clusterBridge.removeItem(context.item, context.storageType, context.destinationStorageType).orTimeout(config.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS);
        });
    }

// RemoveService.java:307-312
    private void restoreStatusOnError(RemovalContext context, AuctionClusterBridge clusterBridge) {
        if (context.statusChanged && !context.localRemovalCompleted) {
            context.item.setStatus(context.oldStatus);
            clusterBridge.notifyItemStatusChange(context.item, context.targetStatus, context.oldStatus);
        }
    }

// ZAuctionManager.java:562-569 -- la DB part AVANT giveItem, et giveItem est hors thread principal
        item.setStatus(ItemStatus.DELETED);
        removeItem(StorageType.LISTED, item);

        this.updateListedItems(item, false, player);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);

        var updateFuture = storageManager.updateItem(item, StorageType.DELETED);
        giveItem(player, item);

// REDIS/RedisAuctionClusterBridge.java:79-89 (UNLOCK_SCRIPT) -- remet AVAILABLE
            redis.call('DEL', lockKey)
            redis.call('HDEL', itemKey, 'lock')
            local currentState = redis.call('HGET', itemKey, 'state')
            if currentState == 'LOCKED' then
                redis.call('HSET', itemKey, 'state', 'AVAILABLE')
            end
```

**Chronologie**

T0 serveur A : le vendeur ouvre selling-items et clique sur sa vente composee de 2 ItemStack (shulker + contenu), inventaire quasi plein. SellingItemsButton -> RemoveService.removeSellingItem -> executeRemoval : checkAvailability OK, lockItem OK, statut IS_BEING_REMOVED pose (l.239) et diffuse. T1 A (commonPool, cf. le constat sur le confinement de threads) : ZAuctionManager.java:563 removeItem(StorageType.LISTED, item) retire l'item du store memoire. T2 A : l.568 storageManager.updateItem(item, StorageType.DELETED) est lance immediatement sur l'asyncExecutor -- l'UPDATE partira et sera committe quoi qu'il arrive ensuite. T3 A : l.569 giveItem -> ZAuctionManager.java:931 : le 1er ItemStack entre dans l'inventaire, le 2e ne rentre pas, donc player.getWorld().dropItem(...) est appele depuis commonPool et leve (AsyncCatcher Paper / thread de region Folia). T4 A : l'exception traverse removeSellingItem avant son return updateFuture (l.587). Elle est levee A L'INTERIEUR de la lambda de RemoveService.java:250, donc context.localRemovalCompleted reste false (il n'est positionne qu'a la l.251, dans le thenCompose du futur retourne) et clusterBridge.removeItem(...) de la l.252 n'est JAMAIS appele : Redis ne recoit ni l'etat DELETED/REMOVED ni le message ItemRemovedMessage. T5 A : handleRemovalException (l.270) -> releaseLockOnError (l.293) -> UNLOCK_SCRIPT remet state = AVAILABLE (l.87-89) ; puis restoreStatusOnError (l.307-311) : statusChanged == true et localRemovalCompleted == false -> item.setStatus(AVAILABLE) et notifyItemStatusChange(IS_BEING_REMOVED -> AVAILABLE) diffuse a tout le cluster. T6 serveurs B et C : ItemStatusListener.java:60-62 recoit AVAILABLE, fait manager.updateListedItems(item, true, null) : l'item reapparait dans l'hotel des ventes de tous les autres serveurs, alors que sa ligne items vaut DELETED et que le vendeur a deja recupere 1 des 2 stacks. T7 serveur B : le vendeur reclique dessus. RemoveService ne revalide jamais la base ; checkAvailability repond true (state AVAILABLE), le verrou est obtenu, ZAuctionManager.java:520-523 lui redonne LES DEUX stacks. Le 1er stack est duplique.

**Impact** — Duplication d'item reproductible : il suffit de remplir son inventaire avant de retirer une vente multi-stacks. Plus generalement, TOUTE exception levee par le supplier de remise locale apres la ligne qui lance updateItem produit le meme resultat : ligne DB DELETED, item absent des stores memoire locaux, mais statut AVAILABLE rediffuse a tout le cluster et etat Redis remis a AVAILABLE.

**Correctif**

1) Positionner context.localRemovalCompleted = true AVANT d'invoquer le supplier, ou capturer l'exception synchrone : CompletableFuture<Void> local; try { local = context.onLocalRemoval.get(); } catch (Throwable t) { context.localRemovalCompleted = true; return CompletableFuture.failedFuture(t); }. 2) Corriger l'ordre dans ZAuctionManager.removeSellingItem/removeExpiredItem/removePurchasedItem/removeListedItem : chainer storageManager.updateItem(...).thenCompose(v -> giveItem(player, item)) au lieu de lancer les deux en parallele (l.522-523, 568-569, 602-603, 636-637). 3) Ne jamais rediffuser AVAILABLE sans avoir relu la ligne sous verrou (selectItem), comme le fait deja PurchaseService.java:118-125.

---

<a id="c-040"></a>

### `C-040` — V3MigrationProvider transforme tout échec de migration en succès : `thenApply` mappe systématiquement vers MigrationResult.success et jette isSuccess()/getErrorMessage()

- [ ] **Corrigé**
- **Fichier** : `migration/v3/V3MigrationProvider.java:126`
- **Catégorie** : Perte de données — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
V3MigrationProvider.java:126-132 (vérifié) :

        return migrationFuture.thenApply(result -> MigrationResult.success(
                result.getPlayersImported(),
                result.getItemsImported(),
                result.getTransactionsImported(),
                result.getErrors(),
                result.getDurationMs()
        )).exceptionally(throwable -> MigrationResult.failure(throwable.getMessage()));

`result.isSuccess()` et `result.getErrorMessage()` ne sont JAMAIS lus. Or V3MigrationService.java:164-166 (vérifié) produit précisément un résultat en échec, sans faire échouer le future :

            } catch (Exception e) {
                plugin.getLogger().severe("Migration failed: " + e.getMessage());
                return V3MigrationResult.failure("Migration failed: " + e.getMessage());
            } finally {
                reader.close();
            }

V3MigrationResult.failure (V3MigrationResult.java:30-32, vérifié) renvoie (false, 0, 0, 0, 0, 0, message) : le future se complète NORMALEMENT, donc l'`exceptionally` ligne 132 n'est jamais déclenché, et MigrationResult.success(0,0,0,0,0) est renvoyé à l'appelant (MigrationResult.java:67-69 : `new SimpleMigrationResult(true, ...)`).
```

**Chronologie**

T0 — L'admin lance `/ah admin migrate mysql confirm` pour importer sa base V3.

T1 — Au milieu de migrateItems, la connexion MySQL V3 tombe (ou un SELECT échoue) : l'exception remonte au catch de V3MigrationService.java:164, qui logge « Migration failed: ... » et renvoie V3MigrationResult.failure.

T2 — V3MigrationProvider.java:126 mappe ce résultat en MigrationResult.success(0, 0, 0, 0, 0). Le message d'erreur est perdu.

T3 — La commande annonce une migration réussie avec 0 joueur / 0 item / 0 transaction importés. L'admin, croyant la bascule faite, supprime la base V3 ou désinstalle zAuctionHouseV3.

T4 — Les annonces, l'argent en attente et l'historique de tous les joueurs sont définitivement perdus, alors que la seule trace de l'échec est une ligne severe noyée dans la console.

**Impact** — Perte définitive des données V3 : une migration ratée est présentée comme réussie. C'est le pire mode de défaillance possible pour un outil de migration, car il incite l'administrateur à détruire la source.

**Précision apportée par la contre-expertise**

Le bug est réel et le correctif proposé est le bon (il reproduit le pattern déjà présent dans les 3 providers frères). Deux points de la description doivent être rectifiés :

A) Le scénario T1 est FAUX sur le chemin d'exécution, et le bug est en réalité PLUS facile à déclencher que décrit. Une exception « au milieu de migrateItems » n'atteint PAS le catch de V3MigrationService.java:164 : `migrateItems` possède son propre try/catch par item (V3MigrationService.java:244-247 : `catch (Exception e) { ...warning("Failed to migrate item "...); errors.incrementAndGet(); }`) qui l'avale et se contente d'incrémenter `errors` ; la migration retourne alors un `success(..., errors>0)` légitime. Les chemins réellement générateurs d'un résultat en échec sont :

- V3MigrationService.java:117 `return V3MigrationResult.failure("Failed to connect to V3 data source");` — AUCUNE exception requise ;

- V3MigrationService.java:127 `return V3MigrationResult.failure("No data found to migrate");` — AUCUNE exception requise ;

- V3MigrationService.java:166, alimenté par les `.join()` de la phase de lecture (lignes 115, 122, 123, 132, 136).

Le scénario le plus probable n'est donc pas une coupure MySQL en cours de route, mais le plus banal : un host/identifiant erroné dans `migration.zauctionhouse-v3`, ou une base V3 vide. L'admin lance `/ah admin migrate v3 confirm`, la connexion échoue immédiatement, et il lit « Migration from zAuctionHouse V3 completed successfully! Players: 0, Items: 0, Transactions: 0 ». À noter aussi : sur ce faux succès, `CommandAuctionAdminMigrate.java:92` exécute quand même `plugin.getStorageManager().loadItems()` (sans conséquence, mais c'est du travail inutile sur un échec).

B) L'impact « perte définitive des données V3 » est surévalué. zAuctionHouseV4 n'écrit JAMAIS dans la source V3 : un grep de `insert|update|delete|drop|truncate` sur `migration/v3/reader/` ne retourne aucun résultat — les readers sont strictement en lecture. Aucune donnée n'est détruite par le plugin. La perte suppose une action indépendante de l'administrateur (suppression de sa base V3), qu'aucun message du plugin ne suggère (messages.yml:563-569 n'invite pas à supprimer la source). De plus l'échec reste doublement visible : les compteurs affichés à 0/0/0 dans le message de succès lui-même, et le `plugin.getLogger().severe("Migration failed: ...")` en console (ligne 165). Il s'agit donc d'un bug de RAPPORT MENSONGER (le verdict d'échec est perdu et la branche d'erreur de l'appelant est rendue morte), pas d'un bug destructeur de données.

C) Hors périmètre de l'audit anti-duplication : commande admin (`Permission.ZAUCTIONHOUSE_ADMIN`, CommandAuctionAdminMigrate.java:27), ponctuelle, non atteignable par un joueur, sans lien avec la synchronisation Redis multi-serveurs ni avec un vecteur de duplication.

Pour ces raisons : sévérité ramenée de « critique » à « haute ». Correctif recommandé : appliquer strictement le pattern des frères (CrazyAuctionsMigrationProvider.java:93-104) à V3MigrationProvider.java:126-132. Le second volet proposé (message distinct si `errors > 0`) est une amélioration légitime mais devrait alors être appliqué aux 4 providers pour rester cohérent, et relève d'une évolution séparée de la correction du bug.

**Correctif**

```java
Propager le verdict réel :

        return migrationFuture.thenApply(result -> {
            if (!result.isSuccess()) {
                return MigrationResult.failure(result.getErrorMessage());
            }
            if (result.getErrors() > 0) {
                plugin.getLogger().warning("V3 migration completed with " + result.getErrors() + " partial errors.");
            }
            return MigrationResult.success(result.getPlayersImported(), result.getItemsImported(),
                    result.getTransactionsImported(), result.getErrors(), result.getDurationMs());
        }).exceptionally(throwable -> MigrationResult.failure(throwable.getMessage()));

Ajouter en complément un seuil : si `errors > 0`, la commande doit afficher un message distinct (et non le message de succès) et ne surtout pas inviter à supprimer la source.
```

---

<a id="c-041"></a>

### `C-041` — ZAuctionEconomy.get() est un faux async : une exception du provider s'echappe de purchaseAuctionItem apres le debit acheteur et le credit vendeur, l'item reste LISTED

- [ ] **Corrigé**
- **Fichier** : `economy/ZAuctionEconomy.java:77`
- **Catégorie** : Duplication d’argent — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// ZAuctionEconomy.java:77-84 -- getBalance() est evalue AVANT completedFuture : toute exception est SYNCHRONE
@Override
public CompletableFuture<BigDecimal> get(UUID playerId) {
    return CompletableFuture.completedFuture(this.currencyProvider.getBalance(playerId));
}

@Override
public CompletableFuture<Boolean> has(UUID playerId, BigDecimal price) {
    return get(playerId).thenApply(balance -> balance.compareTo(price) >= 0);
}

// ZAuctionManager.java:802 -- l'acheteur est deja debite
auctionEconomy.withdraw(player.getUniqueId(), buyerPays, args(auctionEconomy.getWithdrawReason(), "%seller%", sellerName, "%items%", items));

// ZAuctionManager.java:823 -- le vendeur est deja credite
auctionEconomy.deposit(seller.getUniqueId(), sellerReceives, args(auctionEconomy.getDepositReason(), "%buyer%", player.getName(), "%items%", items));

// ZAuctionManager.java:839-844 -- appel synchrone deguise, le .exceptionally ne peut rien attraper
auctionEconomy.get(player.getUniqueId()).thenAccept(buyerBalance -> {
    storageManager.createTransaction(...);
}).exceptionally(throwable -> { ... return null; });

// ZAuctionManager.java:863-893 -- jamais atteints si get() leve
auctionItem.setBuyer(player);
auctionItem.setStatus(ItemStatus.PURCHASED);
...
removeItem(StorageType.LISTED, auctionItem);
...
updateFuture = storageManager.updateItem(auctionItem, StorageType.DELETED);
giveItem(player, auctionItem);
```

**Chronologie**

T0 -- Serveur A : un joueur achete l'item 42. buyerPays est retire de l'acheteur (ligne 802) puis sellerReceives est verse au vendeur (ligne 823, vendeur connecte sur A donc deferDeposit=false).

T1 -- Ligne 839 : le provider d'economie (CoinsEngine, PlayerPoints, un backend Vault adosse a SQL...) leve dans getBalance -- perte de connexion, ligne de compte absente, joueur jamais initialise. Comme getBalance est evalue AVANT completedFuture, l'exception remonte SYNCHRONIQUEMENT et le .exceptionally de la ligne 841 ne la voit jamais.

T2 -- purchaseAuctionItem est interrompu avant la ligne 863 : pas de setBuyer, pas de removeItem(LISTED), pas d'UPDATE en base, pas de giveItem. La ligne MySQL est toujours storage_type=LISTED, buyer_unique_id NULL.

T3 -- PurchaseService.exceptionally (PurchaseService.java:156-189) se contente de logger, relacher le verrou Redis et restaurer le statut. Aucun remboursement de l'acheteur.

T4 -- Serveur B : un autre joueur achete l'item 42. checkAvailability est vrai (le verrou a ete relache), la revalidation sous verrou (PurchaseService.java:121) voit une ligne LISTED sans acheteur -> l'achat aboutit. Le vendeur est credite UNE SECONDE FOIS et l'item est livre.

T5 -- L'operation est rejouable a l'infini tant que le provider leve : le vendeur encaisse a chaque tentative.

**Impact** — Duplication d'argent : le vendeur est paye autant de fois que l'achat est retente, l'argent du premier acheteur est detruit sans contrepartie, et l'item reste en vente. Aucun log ne signale l'incoherence puisque l'exception est traitee comme un simple echec d'achat.

**Précision apportée par la contre-expertise**

Le defaut est reel et bien localise (ZAuctionManager.java:839-852 non protege, entre le mouvement d'argent et la transition d'etat de l'item), mais sa description doit etre corrigee sur trois points :

1) MAUVAISE LIGNE DE DECLENCHEMENT. Le constat cite :839 (solde de l'ACHETEUR) comme declencheur. Or ce meme appel est deja effectue quelques millisecondes plus tot, SOUS LE MEME VERROU, par PurchaseService.java:126 :

return auctionEconomy.has(player.getUniqueId(), requiredBalance);

qui delegue a ZAuctionEconomy.has() -> get() -> getBalance(). Les causes citees dans le scenario ("ligne de compte absente", "joueur jamais initialise") feraient donc lever a la ligne 126, AVANT tout debit, avec un abandon propre via l'exceptionally de :156. Seule une panne TRANSITOIRE (perte de connexion entre :126 et :839) atteint la fenetre par l'acheteur.

La ligne reellement non couverte est ZAuctionManager.java:846 :

auctionEconomy.get(seller.getUniqueId()).thenAccept(sellerBalance -> {

Le solde du VENDEUR n'est jamais interroge auparavant dans tout le flux d'achat. Un vendeur hors-ligne, sur un autre serveur, jamais initialise dans CoinsEngine/PlayerPoints, est le declencheur realiste et DETERMINISTE : chaque tentative d'achat de ses items echoue au meme endroit. (La correction proposee #2, qui encapsule 839-852, couvre bien cette ligne -- c'est la description du scenario, pas le fix, qui est a corriger.)

2) IMPACT MAL QUALIFIE. Dans le cas dominant multi-serveurs, le vendeur est hors-ligne, donc ZAuctionManager.java:812-814 met deferDeposit=true :

boolean deferDeposit = !auctionEconomy.isAutoClaim()

|| (!sellerOnThisServer && auctionEconomy.mustBeOnline())

|| (!sellerOnThisServer && clusterBridge.isDistributed());

Le deposit de :823 n'a alors PAS lieu, et la transaction PENDING de :848 n'est jamais ecrite car elle se trouve dans le thenAccept avale par l'exception. Consequence reelle : l'argent de l'acheteur est DETRUIT (retire en :802, jamais rembourse, jamais credite a personne), l'item reste LISTED et re-vendable a la victime suivante, et aucune ligne de transaction ne trace l'incoherence. Ce n'est donc pas une "duplication d'argent" mais une destruction repetable de monnaie cote acheteurs.

Le double credit du vendeur decrit en T4/T5 n'existe que dans le cas etroit vendeur-en-ligne + autoClaim=true + bridge non distribue -- cas ou le deposit de :823 vient justement de reussir, ce qui rend un throw sur le solde de ce meme vendeur en :846 nettement moins probable. Le titre "duplication-argent / le vendeur est paye autant de fois que l'achat est retente" doit donc etre reformule en "perte d'atomicite : argent debite sans contrepartie, item non transitionne et re-vendable".

3) SEVERITE. "critique" est surevalue : le declenchement n'est pas a la main d'un joueur, il requiert une exception du provider d'economie (panne SQL, compte absent, currency mal configuree). En revanche l'absence totale de compensation, l'incoherence permanente en base et le silence des logs metier justifient "haute".

Les trois correctifs proposes restent valides et suffisants ; ordre de priorite suggere : (2) envelopper 835-861 dans un try/catch d'abord (protege immediatement les deux lignes 839 ET 846 et rend la comptabilite non bloquante pour la livraison), puis (1) rendre get() reellement async, puis (3) reordonner le credit vendeur apres le succes de l'UPDATE.

**Correctif**

```java
1) Rendre get() reellement asynchrone pour que l'erreur se propage dans le future :

@Override
public CompletableFuture<BigDecimal> get(UUID playerId) {
    return CompletableFuture.supplyAsync(() -> this.currencyProvider.getBalance(playerId), this.plugin.getExecutorService());
}

2) Rendre la comptabilite non bloquante pour la livraison dans purchaseAuctionItem : encapsuler les lignes 839-852 dans un try/catch afin qu'aucun echec de createTransaction ne puisse interrompre la sequence setBuyer / removeItem / updateItem / giveItem.
3) Ordonner : ne crediter le vendeur (ligne 823) qu'apres le succes de l'UPDATE de l'item, ou compenser explicitement l'acheteur dans un catch englobant tout le bloc.
```

---

<a id="c-042"></a>

### `C-042` — adminRemoveItem ne teste jamais le jeton noop : la suppression admin s'execute meme quand un autre serveur detient le verrou de l'item

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:689`
- **Catégorie** : Duplication d’item — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ZAuctionManager.java:687-703 (le lockToken est transporte tel quel, jamais compare a LockToken.noop())
            return clusterBridge.lockItem(item, admin.getUniqueId(), storageType);

        }).thenCompose(lockToken -> clusterBridge.removeItem(item, storageType).thenApply(v -> lockToken)).thenAccept(lockToken -> {

            removeItem(storageType, item);

            this.plugin.getStorageManager().updateItem(item, StorageType.DELETED);
            clearPlayersCache(...);

            giveItem(admin, item);

A comparer avec les deux autres appelants, qui eux testent :
V4/services/PurchaseService.java:100
                    if (LockToken.noop().value().equals(token.value())) {
V4/services/RemoveService.java:232
        if (LockToken.noop().value().equals(token.value())) {

Et avec le contrat reel du bridge Redis, qui signale l'echec par un jeton noop et non par un future en erreur :
REDIS/.../RedisAuctionClusterBridge.java:196-200
                        if (result instanceof Long && (Long) result == 1) {
                            return token;
                        }
                        return LockToken.noop();
```

**Chronologie**

T0 - Serveur A : un joueur confirme l'achat de l'item 42. PurchaseService.java:92 acquiert le verrou : auction:lock:42 existe, auction:item:42 a state=LOCKED, et la chaine avance jusqu'a ZAuctionManager.java:802 (withdraw de l'acheteur).

T1 - Serveur B, pendant les quelques centaines de millisecondes de cette section critique : un admin ouvre l'inventaire admin des ventes du vendeur et clique sur l'item 42 (AdminSellingItemsButton.java:47).

T2 - checkAvailability sur B (ZAuctionManager.java:679) lit state=LOCKED et EXISTS auction:lock:42 = 1 -> renvoie false -> la chaine est correctement interrompue... sauf si le verrou de A a ete pose il y a plus de lock-ttl-seconds (30 s par defaut, jamais renouvele) ou si le hash a expire : dans ce cas checkAvailability renvoie true.

T3 - B appelle lockItem : le LOCK_SCRIPT voit state=LOCKED avec un lockKey encore present -> renvoie 0 -> le bridge renvoie LockToken.noop(). Aucun future en erreur.

T4 - La ligne 689 ignore totalement ce noop : elle enchaine directement clusterBridge.removeItem(item, storageType), qui ecrit state=REMOVED dans Redis et publie ItemRemovedMessage, puis le thenAccept ligne 690 retire l'item du store local, ecrit storage_type='DELETED' en base et fait giveItem(admin, item) ligne 696.

T5 - A termine son achat : ZAuctionManager.java:883 ecrit a son tour storage_type='DELETED' (aucun compare-and-set) et ZAuctionManager.java:884 donne l'item a l'acheteur. L'acheteur a paye et recu l'item, l'admin a recu le meme item : l'item existe en double et la ligne 42 est marquee DELETED une seule fois.

T6 - Enfin, ZAuctionManager.java:703 appelle unlockItem avec le jeton noop : le bridge Redis sort immediatement (RedisAuctionClusterBridge.java:246), donc le verrou de A n'est pas casse, mais rien n'a empeche la suppression.

**Impact** — Le seul chemin admin qui pretend passer par le verrou cluster (checkAvailability -> lockItem -> removeItem -> unlockItem) ne tire aucune consequence de l'echec du verrou. Un clic admin pendant une operation concurrente duplique l'item, ou le detruit si l'ordre s'inverse (l'admin le prend, l'acheteur paie et ne recoit rien puisque la ligne est deja DELETED et que removeItem l'a sorti du store LISTED).

**Correctif**

Aligner adminRemoveItem sur PurchaseService : remplacer la ligne 689 par un thenCompose qui teste d'abord le jeton, par exemple `.thenCompose(lockToken -> { if (LockToken.noop().value().equals(lockToken.value())) { message(plugin, admin, Message.ADMIN_ITEM_LOCKED); return failedFuture(new IllegalStateException("Item deja verrouille")); } return clusterBridge.removeItem(item, storageType).thenApply(v -> lockToken); })`. Ajouter dans la foulee la revalidation base sous verrou (selectItem(item.getId()) : abandon si null ou buyer non null) et chainer updateItem(DELETED) AVANT giveItem, comme le fait deja PurchaseService.

---

<a id="c-043"></a>

### `C-043` — createAuctionItem enchaine deux INSERT hors transaction : un echec partiel laisse une annonce vide et achetable en base partagee

- [ ] **Corrigé**
- **Fichier** : `storage/ZStorageManager.java:141`
- **Catégorie** : Perte d’argent — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// ZStorageManager.java:139-145 -- deux ecritures independantes, aucune transaction, aucun rollback
@Override
public CompletableFuture<AuctionItem> createAuctionItem(Player seller, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
    return CompletableFuture.supplyAsync(() -> {
        int itemId = with(ItemRepository.class).create(seller, ItemType.AUCTION, price, expiredAt, auctionEconomy);
        return with(AuctionItemRepository.class).create(seller, itemId, price, expiredAt, itemStacks, auctionEconomy);
    }, this.plugin.getExecutorService());
}

// ItemRepository.java:29-41 -- la premiere ligne est deja LISTED des sa creation
return insertSync(schema -> {
    ...
    schema.object("storage_type", StorageType.LISTED.name());

// ItemLoaderUtils.java:39-42 -- au rechargement, une ligne items sans auction_items donne un item a CONTENU VIDE
protected AuctionItem createAuctionItem(AuctionPlugin plugin, ItemDTO dto, String sellerName, List<AuctionItemDTO> currentAuctionItems, AuctionEconomy auctionEconomy) {
    var itemStacks = currentAuctionItems.stream().map(e -> Base64ItemStack.decode(e.itemstack())).toList();
    var auctionItem = new ZAuctionItem(plugin, dto.id(), ..., itemStacks);

// ZAuctionManager.java:929-932 -- giveItem sur une liste vide ne donne rien, sans erreur
var itemStacks = auctionItem.getItemStacks();
for (ItemStack itemStack : itemStacks) {
    player.getInventory().addItem(itemStack)...
```

**Chronologie**

T0 -- Serveur A : le joueur vend un lot. Le premier INSERT (table items) reussit : la ligne existe deja avec storage_type = LISTED.

T1 -- Le second INSERT (table auction_items, qui porte le contenu encode) echoue : deconnexion MySQL, paquet trop gros pour un shulker rempli (max_allowed_packet), timeout du pool Hikari.

T2 -- L'exception remonte dans SellService.exceptionally (SellService.java:111) : les items sont rendus au vendeur, la taxe est remboursee. Rien ne supprime la ligne items orpheline, qui reste LISTED dans la base PARTAGEE.

T3 -- Au prochain demarrage de n'importe quel serveur du reseau, AuctionLoader charge cette ligne : getAuctionItems ne trouve aucune ligne fille, itemStacks est vide, l'item est cree en ItemStatus.AVAILABLE (ItemLoaderUtils.java:44) et publie dans l'hotel des ventes.

T4 -- Un joueur achete cette annonce : il est debite (ZAuctionManager.java:802), le vendeur est credite (ligne 823), puis giveItem boucle sur une liste vide et ne livre rien. L'acheteur a paye pour du vide, sans message d'erreur.

**Impact** — Annonces fantomes permanentes dans la base partagee, argent preleve a des acheteurs sans contrepartie, et bruit dans l'affichage de tous les serveurs. Aucun mecanisme de nettoyage n'existe.

**Précision apportée par la contre-expertise**

Le bug est reel mais MAL DECRIT sur trois points, et la correction proposee ne compile pas utilement.

1) L'EXCEPTION NE REMONTE JAMAIS (T2 est faux). `AuctionItemRepository.create` utilise `insert(...)` et non `insertSync(...)`, et `Repository.insert` avale la SQLException :

// API/src/main/java/fr/maxlego08/zauctionhouse/api/storage/Repository.java:124-130

protected void insert(Consumer<Schema> consumer, Consumer<Integer> consumerResult) {

try {

consumerResult.accept(SchemaBuilder.insert(getTableName(), consumer).execute(this.connection, this.logger));

} catch (SQLException exception) {

exception.printStackTrace();   // avalee, jamais relancee

}

}

Donc `SellService.exceptionally` (SellService.java:111) NE SE DECLENCHE PAS sur un echec SQL. Contrairement a T2 : les items ne sont PAS rendus au vendeur, la taxe n'est PAS remboursee.

2) LE RESULTAT REEL EST PIRE. `removeItemsFromSlots(player, validSlotItems)` a deja retire les items de l'inventaire AVANT l'appel a `createAuctionItem` (SellService.java, juste avant la ligne 107) : le vendeur perd definitivement ses items. `AuctionItemRepository.create` construit et retourne quand meme un `ZAuctionItem` complet a partir de la liste en memoire, quel que soit le succes de l'insert :

// AuctionItemRepository.java:31-37

for (ItemStack itemStack : itemStacks) {

insert(schema -> { schema.object("item_id", itemId); schema.string("itemstack", Base64ItemStack.encode(itemStack)); });

}

return new ZAuctionItem(this.plugin, itemId, ..., itemStacks);

Le future se complete donc en SUCCES, `postSell` s'execute : message ITEM_SOLD au vendeur, ajout en memoire AVEC contenu sur le serveur A, et `notifyItemListed` diffuse sur Redis.

3) LA CONTAMINATION EST IMMEDIATE, PAS AU REDEMARRAGE (T3 est trop faible). `ItemListedListener.onMessage` (zAuctionHouse Redis, listener/listeners/ItemListedListener.java) appelle `auctionPlugin.getStorageManager().selectItem(id)`, qui fait `with(AuctionItemRepository.class).select(...)` (ZStorageManager.java:195) et ne trouve aucune ligne fille -> itemStacks vide -> `manager.addItem(StorageType.LISTED, item)`. L'annonce fantome vide est donc publiee sur TOUS les autres serveurs du reseau des la vente, sans attendre le moindre redemarrage. Etat asymetrique : achetable avec contenu reel sur le serveur A, achetable et VIDE sur tous les autres.

4) LA CORRECTION PROPOSEE EST DU CODE MORT. `catch (RuntimeException e)` n'attrapera jamais rien puisque la SQLException est avalee dans `Repository.insert` et jamais relancee. Tout correctif doit D'ABORD faire remonter l'echec depuis la couche Repository (retourner un booleen/nombre de lignes, ou relancer en RuntimeException) ; sans cela ni la transaction ni la compensation ne sont atteignables.

5) DEFAUT CONNEXE NON MENTIONNE : `insertSync` retourne -1 en cas d'echec (Repository.java:138-144). Si c'est le PREMIER insert qui echoue, `itemId = -1`, les lignes filles sont inserees avec `item_id = -1`, le `ZAuctionItem` est construit avec l'id -1 et diffuse sur Redis. Ce sens d'echec est lui aussi totalement non gere.

La correction de fond reste valable dans son esprit (statut PENDING bascule en LISTED seulement apres insertion du contenu, plus purge au demarrage des lignes `items` sans ligne `auction_items`), mais elle doit etre precedee de la remontee effective des erreurs SQL.

**Correctif**

```java
Rendre les deux INSERT atomiques via une transaction Sarah, ou a defaut compenser explicitement :

return CompletableFuture.supplyAsync(() -> {
    int itemId = with(ItemRepository.class).create(seller, ItemType.AUCTION, price, expiredAt, auctionEconomy);
    try {
        return with(AuctionItemRepository.class).create(seller, itemId, price, expiredAt, itemStacks, auctionEconomy);
    } catch (RuntimeException e) {
        with(ItemRepository.class).delete(schema -> schema.where("id", itemId));   // compensation obligatoire
        throw e;
    }
}, this.plugin.getExecutorService());

En complement, inserer la ligne items avec storage_type = 'PENDING' et ne la basculer en 'LISTED' qu'apres l'insertion du contenu : une ligne PENDING n'est ni chargee ni achetable, et un nettoyage au demarrage peut la purger.
```

---

<a id="c-044"></a>

### `C-044` — loadItems() est purement additif : appele a chaud par /ah admin migrate, il remet tous les statuts a AVAILABLE et ne purge aucun fantome

- [ ] **Corrigé**
- **Fichier** : `storage/AuctionLoader.java:34`
- **Catégorie** : Cohérence de cache — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
AuctionLoader.java:34-50 - aucun vidage prealable, seulement des addItem :
    public void loadItems() {
        ...
        var items = this.storageManager.with(ItemRepository.class).select();
        ...
        var result = this.createItems(this.plugin, players, items, performanceDebug, auctionManager::addItem);

ItemLoaderUtils.java:43-48 - chaque ligne relue recree un objet avec un statut derive :
        auctionItem.setStatus(switch (dto.storage_type()) {
            case LISTED -> ItemStatus.AVAILABLE;
            case PURCHASED -> ItemStatus.PURCHASED;
            case EXPIRED -> ItemStatus.REMOVED;
            case DELETED -> ItemStatus.DELETED;
        });

CommandAuctionAdminMigrate.java:92 l'appelle sur un serveur vivant :
                    plugin.getStorageManager().loadItems();

ZAuctionManager.addItem:238 remplace l'entree existante : storage.put(item.getId(), item);
ZAuctionManager.addToIndex:472 n'a aucun dedoublonnage : index.computeIfAbsent(owner, uuid -> new IntArrayList()).add(itemId);
ZAuctionManager.removeFromIndex:479 n'enleve qu'UNE occurrence : ids.rem(itemId);
```

**Chronologie**

T0 - Serveur A : le joueur P1 est dans l'inventaire de confirmation d'achat de l'item 42. IS_PURCHASE_CONFIRM a deja ete diffuse a tout le cluster et pose sur l'objet O1 present dans storageItemsById[LISTED].

T1 - Serveur A : la migration se termine, loadItems() s'execute. ItemRepository.select() relit la ligne 42, toujours storage_type='LISTED' (aucune ecriture n'a encore eu lieu). ItemLoaderUtils construit un NOUVEL objet O2 avec setStatus(AVAILABLE), et addItem:238 REMPLACE O1 par O2.

T2 - L'HDV de A reaffiche l'item 42 comme disponible ; la garde PurchaseService:68 de P1 porte sur O1, un objet qui n'est plus dans le store. Tous les items en cours d'operation (IS_BEING_PURCHASED pose par PurchaseService:107, IS_BEING_REMOVED pose par RemoveService:239) redeviennent AVAILABLE : le seul marqueur d'operation en cours est efface, et l'objet mute par l'operation en vol n'est plus celui que lisent les gardes et les rendus.

T3 - Symetriquement, rien n'est jamais retire : un item vendu sur le serveur B dont le message pub/sub n'est pas arrive (ligne desormais DELETED) reste dans le store LISTED de A apres le 'rechargement', car le SELECT l'exclut mais loadItems ne supprime rien.

T4 - Effet secondaire mesurable : chaque loadItems() laisse une entree d'index morte par item (addToIndex sans dedoublonnage, removeFromIndex qui n'enleve qu'une occurrence), definitivement.

**Impact** — Une commande d'administration remet a zero l'etat transitoire de tous les items en cours d'achat/retrait sur le serveur, sans purger les fantomes ni les index. L'HDV reaffiche comme disponibles des items en pleine section critique, et loadItems() ne peut structurellement pas servir de resynchronisation, contrairement a ce que suggere son usage dans la commande de migration.

**Précision apportée par la contre-expertise**

Le constat est reel et bien decrit. Quatre precisions a apporter (aucune ne l'invalide) :

A) Numeros de ligne a corriger : `addToIndex` — le corps `index.computeIfAbsent(owner, uuid -> new IntArrayList()).add(itemId);` est a ZAuctionManager.java:471 (signature l.468), pas 472. `removeFromIndex` — `ids.rem(itemId);` est a la ligne 480 (signature l.474), pas 479. Le texte cite est exact, seul l'ancrage derive.

B) La correction proposee contient un element redondant : "invalider sortedItemsCache" est DEJA fait — AuctionLoader.java:57 `auctionManager.rebuildSortedItemsCache()` et, a chaque addItem, ZAuctionManager.java:243 `this.sortedItemsCache.invalidate();`. En revanche il manque un element a la correction : les caches par joueur ne sont jamais purges non plus. `loadItems()` ne touche pas la map `caches` de ZAuctionManager, donc PlayerCacheKey.ITEMS_LISTED / ITEMS_SELLING gardent des IntList d'ids perimes, et surtout ITEM_SHOW conserve la reference directe vers O1 (ConfirmHelper.java:37/65/88) — c'est precisement ce qui rend le scenario T2 exploitable. Une vraie reinitialisation doit donc aussi appeler `clearPlayersCache(...)` sur ces cles.

C) T3 est plus grave que decrit : le constat ne mentionne que les lignes DELETED restees fantomes en LISTED. Or ItemLoaderUtils.java:93 range le nouvel objet selon `dto.storage_type()` SANS retirer l'ancien. Une ligne passee LISTED -> PURCHASED sur le serveur B (vente avec give-item=false, message pub/sub perdu) est donc reinseree dans la map PURCHASED de A tout en restant dans sa map LISTED : le meme id existe simultanement dans deux storages, visible a la fois dans l'HDV et dans les "items achetes".

D) Calibrage de l'exploitabilite en duplication (la severite "haute" reste justifiee) : le chemin ACHAT est partiellement protege — PurchaseService.java:110-125 refait une revalidation autoritaire SOUS VERROU (`this.plugin.getStorageManager().selectItem(item.getId())` puis `if (dbItem == null || dbItem.getBuyerUniqueId() != null)` -> abort), donc racheter un item ressuscite echoue proprement. Le chemin RETRAIT n'a AUCUN equivalent : RemoveService.executeRemoval (l.200) enchaine uniquement checkAvailability -> lockItem -> setStatus -> removal local, sans aucun `selectItem` de revalidation, et ItemRepository.createUpdateSchema ne pose son garde `where storage_type = LISTED` que pour la transition vers EXPIRED (l.79-80). Un item vendu ailleurs puis ressuscite en AVAILABLE par loadItems() peut donc etre retire par son vendeur, qui recupere l'ItemStack alors que l'acheteur l'a deja recu : duplication reelle, via le chemin retrait et non le chemin achat.

**Correctif**

Faire de loadItems() une reinitialisation reelle : avant de recharger, vider les trois maps de storageItemsById, les trois index idsListedByOwner/idsExpiredByOwner/idsPurchasedByBuyer et invalider sortedItemsCache. Et surtout ne plus l'appeler a chaud : dans CommandAuctionAdminMigrate:92, remplacer loadItems() par l'ajout cible des seules lignes nouvellement inserees (addItem + clusterBridge.notifyItemListed(item) pour chacune), ou refuser la migration si des joueurs sont connectes. Corriger aussi addToIndex pour ne pas inserer un id deja present.

---

<a id="c-045"></a>

### `C-045` — updateListedItems : un return au lieu d'un continue interrompt le rafraichissement pour tous les joueurs suivants

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:956`
- **Catégorie** : Cohérence de cache — **Sévérité** : Haute — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// ZAuctionManager.java:953-975
this.sortedItemsCache.ensureCacheValidAsync().thenRun(() -> {
    for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {

        if (onlinePlayer == ignoredPlayer) return;

        this.plugin.getScheduler().runAtEntity(onlinePlayer, w -> {

            var topInventory = CompatibilityUtil.getTopInventory(onlinePlayer);
            if (topInventory == null) return;

            var holder = topInventory.getHolder();
            if (holder instanceof InventoryEngine inventoryEngine) {
                var buttons = inventoryEngine.getMenuInventory().getButtons(ListedItemsButton.class);
                if (buttons.isEmpty()) return;

                var listedItemsButton = buttons.getFirst();
                listedItemsButton.updateInventory(onlinePlayer, inventoryEngine, item, added, this);
            }

            if (!added) removeFromCache(onlinePlayer, item);
        });
    }
});
```

**Chronologie**

T0 serveur A : 30 joueurs consultent l'hotel des ventes, l'item 42 y est affiche. Le joueur Kevin occupe la 12e position dans l'iteration de getOnlinePlayers(). T1 A : Kevin achete l'item 42 ; ZAuctionManager.java:866 appelle updateListedItems(auctionItem, false, Kevin). T2 A : la boucle l.954 met a jour les 11 premiers joueurs, puis atteint onlinePlayer == Kevin -> le return de la ligne 956 sort du Runnable passe a thenRun, donc de la boucle entiere. Les 18 joueurs restants ne recoivent ni le listedItemsButton.updateInventory (l.969) ni le removeFromCache (l.972). T3 A : leur cache ITEMS_LISTED contient toujours l'id 42 et leur GUI affiche toujours l'item vendu. T4 A : la joueuse Alice (25e position) clique sur cet item fantome. ListedItemsButton.createClick reutilise l'instance Item capturee au rendu ; processPurchase atteint la l.229-231 et ecrase item.setStatus(ItemStatus.IS_PURCHASE_CONFIRM) sur un item dont le statut valait PURCHASED, puis diffuse ce statut a tout le cluster via notifyItemStatusChange. T5 serveur B et C : ItemStatusListener.java:54 applique IS_PURCHASE_CONFIRM sur leur exemplaire de l'item et l.58-59 le masquent de tous les hotels des ventes. T6 A : PurchaseService.java:118-125 revalide en base, dbItem.getBuyerUniqueId() != null -> abandon ; mais exceptionally (l.176-178) ne restaure que previousStatusHolder, qui vaut IS_PURCHASE_CONFIRM (capture l.76). L'item reste definitivement bloque dans cet etat sur tous les serveurs.

**Impact** — Apres chaque vente ou retrait, la majorite des joueurs en ligne continue de voir et de pouvoir cliquer l'item disparu, et leur cache ITEMS_LISTED reste desynchronise (ce qui decale ensuite resolveItemsForPage et fait afficher un item a la place d'un autre). Chaque clic sur un fantome ecrase le statut reel de l'item et diffuse cet etat parasite a tout le cluster, ou il n'est jamais nettoye. C'est le declencheur pratique le plus courant des scenarios d'achat/retrait sur item perime.

**Correctif**

Remplacer le return par un continue a la ligne 956 : if (onlinePlayer == ignoredPlayer) continue;. Verifier au passage la branche l.940-947 (updateInventoryOnAction == false) qui, elle, itere correctement sans exclusion.

---

<a id="c-046"></a>

### `C-046` — ItemBoughtListener/ItemRemovedListener sortent l'item du store sans jamais changer son statut : les boutons deja rendus gardent une reference AVAILABLE et passent toutes les gardes de RemoveService

- [ ] **Corrigé**
- **Fichier** : `REDIS/listener/listeners/ItemBoughtListener.java:49`
- **Catégorie** : Duplication d’item — **Sévérité** : Haute — **Contre-expertise** : contesté

**Preuve dans le code**

```java
ItemBoughtListener.java:49-50 :
            manager.removeItem(StorageType.LISTED, id);
            manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);

ItemRemovedListener.java:37-43 :
            for (StorageType st : StorageType.values()) {
                if (st != StorageType.DELETED) {
                    manager.removeItem(st, id);
                }
            }

Aucun des deux n'appelle setStatus(). Cote V4, SellingItemsButton.java:40-45 capture l'objet :
        paginate(items, inventoryEngine, (slot, item) -> {
            boolean available = item.getStatus() == ItemStatus.AVAILABLE;
            inventoryEngine.addItem(...).setClick(event -> {
                this.plugin.getAuctionManager().getRemoveService().removeSellingItem(player, item);
            });

RemoveService.java:96 :
        if (item.getStatus() != ItemStatus.AVAILABLE) { ... return failure(INVALID_ITEM_STATUS); }

ZAuctionManager.updateListedItems:964 ne cible QUE ListedItemsButton :
                        var buttons = inventoryEngine.getMenuInventory().getButtons(ListedItemsButton.class);
```

**Chronologie**

T0 - Serveur B : le vendeur ouvre /ah selling. SellingItemsButton.onRender enregistre un setClick qui capture l'objet Item O_B (statut AVAILABLE, present dans le store LISTED de B).

T1 - Serveur A : un acheteur achete l'item #42. Toute la sequence se deroule normalement, notifyItemBought publie.

T2 - Serveur B : ItemBoughtListener recoit le message SANS AUCUN retard et execute removeItem(LISTED, 42) + clearPlayersCache. L'objet O_B sort du store mais (a) son champ itemStatus vaut toujours AVAILABLE, (b) le setClick du bouton deja rendu pointe toujours sur O_B, (c) aucun rafraichissement du GUI 'selling' n'est declenche car updateListedItems ne cible que ListedItemsButton et n'est de toute facon pas appele ici.

T3 - Serveur B : le vendeur clique sur la case toujours affichee. RemoveService.removeSellingItem:96 teste item.getStatus() != AVAILABLE sur O_B -> AVAILABLE -> la garde PASSE. checkAvailability lit l'etat Redis SOLD, qui n'est pas bloquant (RedisAuctionClusterBridge:167), le verrou est accorde, et ZAuctionManager.removeSellingItem:568-569 fait updateItem(DELETED) puis giveItem(vendeur).

T4 - L'acheteur a paye et recu l'item sur A, le vendeur a recu le meme item sur B. Duplication.

**Impact** — Duplication d'item sans aucune perte de message pub/sub : la fenetre n'est pas la latence Redis mais toute la duree pendant laquelle un inventaire est ouvert (plusieurs minutes). S'applique identiquement a ExpiredItemsButton, PurchasedItemsButton et CombinedItemsButton, dont les gardes RemoveService (lignes 96, 134, 172) ne consultent que l'objet memoire.

**Précision apportée par la contre-expertise**

TITRE EXACT : "Expiration cluster et retrait admin diffusent removeItem sans notifyItemStatusChange : l'objet Item capture par un bouton deja rendu reste AVAILABLE et passe les gardes de RemoveService (duplication)".

CE QUI EST PROUVE ET CONSERVE :

- SellingItemsButton.java:40-45 capture l'objet dans le setClick ; ZAuctionManager.resolveItems:349-356 et getItems:222 renvoient les MEMES references issues de storageItemsById (ligne 63), donc l'objet capture est bien l'objet partage.

- RemoveService.executeRemoval:200 n'effectue AUCUNE re-validation DB autoritaire, contrairement a PurchaseService.java:118-125 qui re-lit selectItem() sous verrou et abandonne si dbItem == null || getBuyerUniqueId() != null. C'est l'asymetrie centrale.

- RedisAuctionClusterBridge.checkAvailability:163-175 et LOCK_SCRIPT:47-57 ne bloquent que DELETED et un LOCKED encore vivant ; STATE_REMOVED et STATE_SOLD passent et le verrou est accorde.

- ZAuctionManager.removeSellingItem:562-569 : setStatus(DELETED), updateItem(DELETED) inconditionnel, puis giveItem(player, item).

- updateListedItems:964 ne cible que ListedItemsButton ; clearPlayersCache:492-494 ne fait que cache.remove(keys) et ne redessine rien ; aucun updateInterval dans src/main/resources/inventories/.

SCENARIO CORRIGE (expiration naturelle, aucun admin, aucune perte de message) :

T0 - Serveur B : le vendeur ouvre /ah selling ; SellingItemsButton enregistre un setClick capturant O_B (AVAILABLE, present dans LISTED de B).

T1 - Serveur A gagne la course d'expiration : ExpireService.expireListedItemClustered:265-294 fait checkAvailability, lockItem, selectItem, puis performListedToExpired(item) et clusterBridge.removeItem(item, StorageType.LISTED, StorageType.EXPIRED) ligne 293. Aucun notifyItemStatusChange dans toute cette chaine.

T2 - Bridge:354 : destination EXPIRED != DELETED donc l'etat Redis devient STATE_REMOVED. Sur B, ItemRemovedListener:37-43 retire O_B de tous les stores, puis Step 2 re-ajoute un OBJET NEUF issu de selectItem() dans EXPIRED avec le statut REMOVED (ItemLoaderUtils.java:44 `case EXPIRED -> ItemStatus.REMOVED`). O_B, lui, conserve AVAILABLE et reste reference par le setClick ; aucun redessin.

T3 - Le vendeur clique la case toujours affichee : RemoveService:96 lit AVAILABLE sur O_B -> garde PASSE ; checkAvailability lit STATE_REMOVED -> true ; LOCK_SCRIPT accorde le verrou ; removeSellingItem:568-569 fait updateItem(DELETED) + giveItem(vendeur).

T4 - Le vendeur detient AUSSI l'entree EXPIRED fraiche, reclamable via removeExpiredItem (garde ligne 134 exige REMOVED : elle correspond). Deux exemplaires du meme item.

SECOND CHEMIN, identique : ZAuctionManager:679-703 (retrait admin) appelle clusterBridge.removeItem(item, storageType) a 2 arguments -> removeItem(item, storageType, null) -> STATE_REMOVED, sans aucun notifyItemStatusChange, puis giveItem(admin, item):697. Le vendeur avec un GUI ouvert sur B reprend une copie de l'item que l'admin a deja recuperee.

CORRECTIF : la proposition 2 du constat (garde d'identite en tete de executeRemoval) est la bonne et couvre les deux chemins de maniere independante du chemin d'appel ; elle doit s'appliquer aux quatre entrees (removeListedItem, removeSellingItem, removeExpiredItem, removePurchasedItem). La proposition 1 (setStatus dans les listeners) fonctionne aussi puisque l'expiration et le retrait admin transitent tous deux par ItemRemovedListener, mais elle reste palliative. Complement recommande : ajouter dans expireListedItemClustered et dans le retrait admin un notifyItemStatusChange, et/ou une re-validation DB dans executeRemoval a l'image de PurchaseService:118-125.

**Correctif**

```java
1) Cote addon, marquer l'objet comme terminal AVANT de le sortir du store, dans ItemBoughtListener (avant la l.49) et ItemRemovedListener (avant la l.37) :

    for (StorageType st : List.of(StorageType.LISTED, StorageType.EXPIRED, StorageType.PURCHASED)) {
        manager.getItems(st).stream().filter(e -> e.getId() == id).findFirst()
                .ifPresent(existing -> existing.setStatus(ItemStatus.DELETED));
    }
    manager.removeItem(StorageType.LISTED, id);

2) Cote plugin, ajouter un accesseur O(1) et une garde d'identite au debut de RemoveService.executeRemoval :

    @Override public Item getItem(StorageType storageType, int id) {
        var storage = this.storageItemsById.get(storageType);
        return storage == null ? null : storage.get(id);
    }

    if (this.plugin.getAuctionManager().getItem(storageType, item.getId()) != item) {
        context.onUnavailable.run();
        return CompletableFuture.completedFuture(RemoveResult.failure("Stale item reference", RemoveFailReason.ITEM_NOT_AVAILABLE));
    }
```

---

<a id="c-047"></a>

### `C-047` — Aucune resynchronisation apres une coupure du bus Redis : le contrat n'offre aucun point de reprise et le subscriber se reconnecte en aveugle

- [ ] **Corrigé**
- **Fichier** : `REDIS/listener/RedisSubscriberRunnable.java:66`
- **Catégorie** : Contrat API — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
RedisSubscriberRunnable.java:62-97 (verifie) - reconnexion sans aucun rattrapage :

    public void run() {
        int backoffMs = 1000;
        final int maxBackoffMs = 60000;
        while (running.get()) {
            try (Jedis jedis = this.plugin.getJedisPool().getResource()) {
                backoffMs = 1000; // Reset backoff on successful connection
                ...
                jedis.subscribe(jedisPubSub, this.plugin.getChannelName());
            } catch (Exception exception) {
                ...
                Thread.sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, maxBackoffMs);

Aucune API de reprise dans le contrat : AuctionClusterBridge.java (verifie l.16-107) ne declare que checkAvailability / lockItem / unlockItem / notifyItemBought / notifyItemListed / notifyItemStatusChange / removeItem / removeItem(3 args) / isDistributed(). Rien pour reconcilier memoire<->base.

Et l'emetteur ne sait meme pas qu'il n'a pas emis - ZAuctionHouseRedis.java:274-282 (verifie) :

    public <T> void sendMessage(T message) {
        ...
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(this.channelName, jsonMessage);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to send Redis message", e);
        }
    }

Le pub/sub Redis n'a ni file d'attente ni rejeu : tout message publie pendant que B n'est pas abonne est definitivement perdu.
```

**Chronologie**

T0 - Serveurs A et B, memes MySQL et Redis. Le lien Redis de B tombe (bascule Sentinel, redemarrage du Redis, hoquet reseau). B entre dans la boucle catch l.81-96 ; le backoff peut atteindre 60 s (l.95).

T1 - Pendant cette fenetre, sur A, l'acheteur P achete l'item 42 du vendeur V. Le verrou est pris puis relache, la ligne SQL passe a PURCHASED (ou DELETED si give-item: true), l'etat Redis passe a SOLD (RedisAuctionClusterBridge.notifyItemBought, l.292-296). A publie ItemBoughtMessage : personne ne le recoit chez B, et A n'en saura jamais rien (le publish est fire-and-forget et l'exception est avalee l.279-281).

T2 - B se reconnecte l.67. Rien dans le contrat ne lui dit "tu as ete sourd, recharge" : il ne relit pas la base, ne redemande rien. L'item 42 est toujours dans son store LISTED, statut AVAILABLE.

T3 - Sur B, le vendeur V ouvre son onglet "En vente" et clique sur l'item 42 pour le recuperer. checkAvailability lit state=SOLD : RedisAuctionClusterBridge.java:167-175 ne refuse que DELETED et LOCKED -> disponible. LOCK_SCRIPT (l.166-186) ne bloque lui aussi que sur DELETED et LOCKED -> le verrou est accorde. RemoveService enchaine jusqu'a la remise de l'item.

T4 - V recoit une copie de l'item deja livre a P, et garde l'argent de la vente. Duplication. La fenetre n'est bornee que par le backoff, soit jusqu'a 60 s par tentative, indefiniment si Redis reste indisponible.

**Impact** — Chaque coupure du bus - evenement banal en exploitation - ouvre une fenetre de divergence memoire non bornee pendant laquelle chaque noeud sourd garde des annonces fantomes affichees, cliquables et reclamables. Aucun operateur ne peut detecter la derive : cote emetteur le publish rate est un simple WARNING, cote recepteur la reconnexion est annoncee comme un succes.

**Correctif**

```java
1) Ajouter un point de reprise au contrat, appele par le transport apres chaque re-souscription reussie :

    /**
     * Signale que ce noeud a pu manquer des notifications entre {@code disconnectedAtMillis} et maintenant.
     * L'implementation doit declencher une reconciliation memoire<->base.
     */
    default CompletableFuture<Void> onTransportRecovered(long disconnectedAtMillis) {
        return CompletableFuture.completedFuture(null);
    }

2) Exposer la reconciliation cote plugin principal (AuctionPlugin) :

    /** Recharge depuis la base tous les items modifies depuis l'instant donne et purge les fantomes memoire. */
    CompletableFuture<Void> reconcileItemsSince(long sinceEpochMillis);

Implementation : SELECT id, storage_type, buyer_unique_id FROM %prefix%items WHERE updated_at >= ? via un ItemRepository.selectUpdatedSince(long) ; pour chaque ligne, repositionner l'item dans le bon store memoire (ou le retirer si DELETED/PURCHASED/EXPIRED) puis clearPlayersCache(...). Ajouter un index sur updated_at, il n'en existe aucun.

3) Dans RedisSubscriberRunnable.run, memoriser l'instant de la deconnexion et appeler le point de reprise apres la re-souscription, pas a l'emprunt de la connexion :

    long disconnectedAt = 0L;
    while (running.get()) {
        try (Jedis jedis = this.plugin.getJedisPool().getResource()) {
            backoffMs = 1000;
            ...
            plugin.getLogger().info("Subscribed to Redis channel: " + plugin.getChannelName());
            if (disconnectedAt > 0L) this.plugin.getBridge().onTransportRecovered(disconnectedAt);
            jedis.subscribe(jedisPubSub, this.plugin.getChannelName());
        } catch (Exception exception) {
            if (disconnectedAt == 0L) disconnectedAt = System.currentTimeMillis();
            ...

4) Faire echouer le future de sendMessage quand le publish echoue (au lieu de le completer normalement) pour que notifyItemBought/notifyItemListed/notifyItemStatusChange puissent journaliser une desynchronisation certaine.
```

---

<a id="c-048"></a>

### `C-048` — Confirmation d'achat : `thenRun` sur notifyItemStatusChange ouvre l'inventaire Bukkit depuis ForkJoinPool.commonPool, sans aucun `exceptionally`

- [ ] **Corrigé**
- **Fichier** : `buttons/list/ListedItemsButton.java:229`
- **Catégorie** : Fiabilité — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ListedItemsButton.processPurchase, lignes 224-234 (vérifié) :

            cache.set(PlayerCacheKey.ITEM_SHOW, item);
            cache.set(PlayerCacheKey.CURRENT_PAGE, this.plugin.getInventoriesLoader().getInventoryManager().getPage(player));
            cache.set(PlayerCacheKey.PURCHASE_ITEM, false);

            // Notify cluster first, then update status to ensure atomicity
            this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, ItemStatus.AVAILABLE, ItemStatus.IS_PURCHASE_CONFIRM)
                    .thenRun(() -> {
                        item.setStatus(ItemStatus.IS_PURCHASE_CONFIRM);
                        manager.updateListedItems(item, false, player);
                        this.plugin.getInventoriesLoader().openInventory(player, inventories);
                    });

Le future vient de RedisAuctionClusterBridge.java:335-337 (vérifié) :

    public CompletableFuture<Void> notifyItemStatusChange(Item item, ItemStatus oldStatus, ItemStatus newStatus) {
        return CompletableFuture.runAsync(() -> this.plugin.sendMessage(new ItemStatusMessage(String.valueOf(item.getId()), oldStatus, newStatus)));
    }

`runAsync` sans exécuteur -> ForkJoinPool.commonPool. Le future n'étant pas encore complété au moment du `thenRun`, le corps du `thenRun` s'exécute sur commonPool, PAS sur le thread principal. Aucun `exceptionally` n'est chaîné, et l'appelant ignore le future retourné.
À comparer avec ConfirmHelper.java:47-52, où le MÊME appel est correctement suivi d'un `.exceptionally(throwable -> { ... })`.
```

**Chronologie**

T0 — Le joueur clique sur un item pour l'acheter ; `economy.has(...)` réussit et l'on entre ligne 229.

T1 — Redis est momentanément lent (ou le pool est saturé, cf. le constat sur maxWait). `sendMessage` ne rend pas la main immédiatement.

T2 — Quand le future se complète, `openInventory(player, inventories)` s'exécute sur un thread de ForkJoinPool.commonPool : ouverture d'un inventaire Bukkit HORS du thread principal, ce que l'API interdit formellement (mutation des collections du joueur et envoi de paquets depuis un thread arbitraire). Sur Folia, c'est encore plus net : le joueur appartient à une région dont ce thread ne détient pas le verrou.

T3 — Variante d'échec : si `sendMessage` lève (pool Jedis épuisé, JedisConnectionException), le future se complète en erreur, le `thenRun` est purement sauté et RIEN ne se produit : pas de statut posé, pas d'inventaire ouvert, pas de message d'erreur, aucune trace en console. Le joueur voit son clic sans effet et le répète.

T4 — À l'inverse, si le statut IS_PURCHASE_CONFIRM est bien posé mais que l'ouverture échoue, l'item reste bloqué dans un statut de confirmation : ConfirmHelper.onInventoryClose, qui est le seul chemin de restauration du statut, ne sera jamais appelé puisqu'aucun inventaire n'a été ouvert. L'item devient inachetable pour tout le cluster.

**Impact** — Deux défauts sur la même expression : violation de la sécurité de thread Bukkit/Folia (corruption d'inventaire, CME, paquets hors ordre) et perte silencieuse totale de toute panne du bridge, avec des items abandonnés en statut de confirmation que rien ne restaure. Noter que le garde anti-double-clic est de plus relâché (`cache.set(PURCHASE_ITEM, false)`, ligne 226) AVANT que l'inventaire de confirmation soit ouvert.

**Correctif**

```java
Revenir sur le thread du joueur et traiter l'échec :

            this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, ItemStatus.AVAILABLE, ItemStatus.IS_PURCHASE_CONFIRM)
                    .thenRun(() -> this.plugin.getScheduler().runAtEntity(player, w -> {
                        if (!player.isOnline()) return;
                        item.setStatus(ItemStatus.IS_PURCHASE_CONFIRM);
                        manager.updateListedItems(item, false, player);
                        this.plugin.getInventoriesLoader().openInventory(player, inventories);
                    }))
                    .exceptionally(throwable -> {
                        this.plugin.getLogger().warning("Failed to notify purchase confirm status for item " + item.getId() + ": " + throwable.getMessage());
                        this.plugin.getScheduler().runAtEntity(player, w -> manager.message(player, Message.INVENTORY_ERROR));
                        return null;
                    });

Déplacer par ailleurs `cache.set(PlayerCacheKey.PURCHASE_ITEM, false)` dans les deux branches terminales (après ouverture réussie, et dans l'exceptionally), et non avant l'appel.
```

---

<a id="c-049"></a>

### `C-049` — Confirmation de retrait : deuxième occurrence du même `thenRun` non protégé, qui ouvre REMOVE_CONFIRM depuis commonPool (site distinct de processPurchase)

- [ ] **Corrigé**
- **Fichier** : `buttons/list/ListedItemsButton.java:130`
- **Catégorie** : Fiabilité — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ListedItemsButton.createClick, lignes 126-135 (vérifié) — code distinct de processPurchase, à corriger séparément :

                    var cache = manager.getCache(player);
                    cache.set(PlayerCacheKey.ITEM_SHOW, item);
                    cache.set(PlayerCacheKey.CURRENT_PAGE, this.plugin.getInventoriesLoader().getInventoryManager().getPage(player));

                    // Notify cluster first, then update status to ensure atomicity
                    this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, ItemStatus.AVAILABLE, ItemStatus.IS_REMOVE_CONFIRM)
                            .thenRun(() -> {
                                item.setStatus(ItemStatus.IS_REMOVE_CONFIRM);
                                this.plugin.getInventoriesLoader().openInventory(player, isMultipleAuctionItem ? Inventories.REMOVE_INVENTORY_CONFIRM : Inventories.REMOVE_CONFIRM);
                            });

Même producteur de future asynchrone (RedisAuctionClusterBridge.java:336, `CompletableFuture.runAsync`), donc même exécution du corps sur ForkJoinPool.commonPool, et toujours aucun `exceptionally`.
```

**Chronologie**

T0 — Le vendeur V clique sur sa propre annonce pour la retirer, avec `actions.listed.open-confirm-inventory: true`.

T1 — Le bridge Redis met quelques dizaines de millisecondes (ou lève, pool saturé).

T2 — Cas nominal lent : `openInventory(player, REMOVE_CONFIRM)` s'exécute sur commonPool -> ouverture d'inventaire hors thread principal, interdite par l'API Bukkit et incompatible avec le modèle régional de Folia.

T3 — Cas d'échec : le `thenRun` est sauté en silence. Le clic de V n'a aucun effet visible, aucune ligne en console. V reclique ; chaque clic republie un ItemStatusMessage vers les autres serveurs, qui basculent l'item en IS_REMOVE_CONFIRM dans leur mémoire, tandis que le serveur de V ne l'a JAMAIS fait (le setStatus est dans le thenRun sauté).

T4 — Résultat : sur les serveurs B et C l'item est invisible/non achetable, sur A il reste AVAILABLE et achetable. La divergence persiste jusqu'au redémarrage puisque plus aucun événement ne repassera par là.

**Impact** — Divergence d'état durable entre les nœuds du cluster provoquée par un simple aléa réseau, plus une violation de la sécurité de thread Bukkit/Folia. C'est le même défaut que dans processPurchase mais à un autre endroit du fichier : les deux doivent être corrigés.

**Correctif**

```java
Symétrique à la correction de processPurchase : ne poser le statut local et n'ouvrir la GUI que sur le thread du joueur, et journaliser/avertir en cas d'échec.

                    this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, ItemStatus.AVAILABLE, ItemStatus.IS_REMOVE_CONFIRM)
                            .thenRun(() -> this.plugin.getScheduler().runAtEntity(player, w -> {
                                if (!player.isOnline()) return;
                                item.setStatus(ItemStatus.IS_REMOVE_CONFIRM);
                                this.plugin.getInventoriesLoader().openInventory(player, isMultipleAuctionItem ? Inventories.REMOVE_INVENTORY_CONFIRM : Inventories.REMOVE_CONFIRM);
                            }))
                            .exceptionally(throwable -> {
                                this.plugin.getLogger().warning("Failed to notify remove confirm status for item " + item.getId() + ": " + throwable.getMessage());
                                this.plugin.getScheduler().runAtEntity(player, w -> manager.message(player, Message.INVENTORY_ERROR));
                                return null;
                            });

À terme, factoriser ces deux appels dans une méthode utilitaire unique (par exemple `openConfirmInventory(player, item, oldStatus, newStatus, inventory)`) pour que le défaut ne puisse pas réapparaître à un troisième endroit.
```

---

<a id="c-050"></a>

### `C-050` — ItemStatusListener recopie jusqu'a trois stores memoire entiers et les scanne lineairement, sur le thread principal, a chaque message de statut

- [ ] **Corrigé**
- **Fichier** : `REDIS/listener/listeners/ItemStatusListener.java:45`
- **Catégorie** : Performance — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ItemStatusListener.java:39-52 (verifie) - execute dans runNextTick, donc sur le THREAD PRINCIPAL :
        auctionPlugin.getScheduler().runNextTick(wrappedTask -> {
            var manager = auctionPlugin.getAuctionManager();

            // Search across all storage types, not just LISTED
            Item item = null;
            for (StorageType storageType : SEARCHABLE_STORAGE_TYPES) {
                var optional = manager.getItems(storageType).stream().filter(e -> e.getId() == id).findFirst();
                if (optional.isPresent()) { item = optional.get(); break; }
            }

            if (item == null) return;

ItemStatusListener.java:16 : SEARCHABLE_STORAGE_TYPES = List.of(LISTED, EXPIRED, PURCHASED).

ZAuctionManager.java:220-223 (verifie) - chaque appel ALLOUE une copie complete du store :
    @Override
    public List<Item> getItems(StorageType storageType) {
        return new ArrayList<>(this.storageItemsById.getOrDefault(storageType, Map.of()).values());
    }

Alors que la structure est deja indexee par id, ZAuctionManager.java:64 (verifie) :
    private final Map<StorageType, Map<Integer, Item>> storageItemsById = new EnumMap<>(StorageType.class);

Verifie : API/.../api/AuctionManager.java n'expose AUCUN acces O(1) par id ; les seules signatures sont getItems(StorageType) l.92, getItems(StorageType, Predicate) l.111 et getItems(StorageType, Predicate, Comparator) l.122.
```

**Chronologie**

Charge : cluster de 5 serveurs, hotel de 100 000 items (le plafond de /ah admin generate), 20 confirmations ouvertes par seconde sur l'ensemble du reseau.

T0 serveur A : un joueur ouvre une confirmation d'achat -> ListedItemsButton diffuse un ItemStatusMessage ; sa fermeture en diffuse un second (ConfirmHelper) ; un achat mene a terme en ajoute deux (PurchaseService).

T1 : les 4 autres serveurs recoivent chacun tous ces messages sur leur thread subscriber, puis basculent en runNextTick.

T2, MAIN THREAD de chaque serveur receveur : new ArrayList<>(LISTED.values()) alloue et copie 100 000 references (~400 a 800 Ko selon compressed oops), puis un stream().filter().findFirst() les parcourt. Si l'item n'est pas dans LISTED - cas frequent, il vient d'etre vendu ou retire localement - on recommence sur EXPIRED puis sur PURCHASED.

T3 : pire cas, item absent des trois stores : les TROIS copies completes sont payees pour rien avant le `return` de la l.52.

T4 : a ~20 messages/s par serveur, cela represente des dizaines de copies de 100 000 elements par seconde sur le thread de tick, la ou une lecture de HashMap par id couterait O(1). Le retard d'application des statuts qui en resulte elargit mecaniquement les fenetres de course entre serveurs.

**Impact** — Pics de latence de tick proportionnels a la taille de l'hotel des ventes, sur tous les serveurs du reseau, declenches par une action banale de n'importe quel joueur. Pression GC importante (allocation puis abandon immediat de listes de dizaines de milliers d'elements plusieurs fois par seconde), et retard d'application des statuts qui aggrave les courses multi-serveurs.

**Correctif**

```java
1) Exposer un acces O(1) dans l'API, API/src/main/java/fr/maxlego08/zauctionhouse/api/AuctionManager.java (a cote de getItems(StorageType) l.92) :
    /** @return l'item de ce store portant cet id, ou null. Lookup O(1), sans copie defensive. */
    Item getItem(StorageType storageType, int itemId);

2) ZAuctionManager :
    @Override
    public Item getItem(StorageType storageType, int itemId) {
        Map<Integer, Item> storage = this.storageItemsById.get(storageType);
        return storage == null ? null : storage.get(itemId);
    }

3) ItemStatusListener.java:43-52 :
    Item item = null;
    for (StorageType storageType : SEARCHABLE_STORAGE_TYPES) {
        item = manager.getItem(storageType, id);
        if (item != null) break;
    }
    if (item == null) return;

4) Sortir la resolution du runNextTick : la lecture d'un ConcurrentHashMap est sure hors thread principal ; seules les mutations (setStatus, clearPlayersCache, updateListedItems) doivent y rester. On evite ainsi de consommer un slot de tick pour un message abandonne l.52.

5) Court-circuiter les messages sans effet : si item.getStatus() == message.newStatus(), sortir avant clearPlayersCache et updateListedItems.

6) Appliquer le meme remplacement partout ou getItems(...).stream() sert uniquement a retrouver un item par id.
```

---

<a id="c-051"></a>

### `C-051` — La limite de mise en vente compte des ANNONCES alors qu'une annonce peut contenir 36 ItemStacks : la limite de grade est contournable d'un facteur 36 sans aucun timing

- [ ] **Corrigé**
- **Fichier** : `services/SellService.java:256`
- **Catégorie** : Sécurité — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
SellService.validateItems, ligne 256-261 (vérifié) :

        long listedItems = manager.getPlayerSellingItems(player).size();
        long maxSellPermission = configuration.getPermission().getLimit(ItemType.AUCTION, player);
        if (listedItems >= maxSellPermission) {
            message(plugin, player, Message.LISTED_ITEMS_LIMIT, "%max-items%", String.valueOf(maxSellPermission));
            return SellFailReason.LISTING_LIMIT_REACHED;
        }

`getPlayerSellingItems` renvoie une liste d'Item (une annonce = un Item), pas une liste d'ItemStacks. Aucun autre contrôle n'existe : la boucle qui suit (SellService.java:270+) ne fait que valider chaque ItemStack (air, blacklist, whitelist) sans jamais en compter le nombre.

Côté sélection, aucune borne non plus — SellShowItemButton.onInventoryClick, lignes 111-117 (vérifié) :

        if (sellItems.containsKey(clickedSlot)) {
            sellItems.remove(clickedSlot);
            manager.message(player, Message.SELL_ITEM_REMOVED);
        } else {
            sellItems.put(clickedSlot, clickedItem.clone());
            manager.message(player, Message.SELL_ITEM_ADDED);
        }
```

**Chronologie**

T0 — Le joueur X a la permission zauctionhouse.limit.default avec `limit: 5`.

T1 — X ouvre le GUI de vente et clique sur les 36 slots de son inventaire : la map SELL_ITEMS (PlayerCacheKey.SELL_ITEMS) contient 36 ItemStacks.

T2 — Il confirme. validateItems ligne 256 lit getPlayerSellingItems().size() = 0, ce qui est < 5 : validation acceptée. Une SEULE ligne `items` est créée, avec 36 lignes `auction_items`.

T3 — X recommence quatre fois. Il a 5 annonces (limite formellement respectée, SellLimitButton et le placeholder selling_items affichent 5/5) mais 180 ItemStacks en vente.

T4 — En cluster, chaque annonce multi-stacks est un unique ItemListedMessage, donc la limite est contournée de façon identique sur tous les serveurs.

**Impact** — La limite de mise en vente — argument commercial des grades VIP et principal levier anti-flood de l'hôtel des ventes — est contournable d'un facteur 36 à 40 sans aucune condition de course ni outil externe. N'importe quel joueur le découvre en jouant normalement.

**Correctif**

```java
Compter les stacks et non les annonces dans SellService.validateItems :

        long listedStacks = manager.getPlayerSellingItems(player).stream()
                .mapToInt(i -> i instanceof AuctionItem auctionItem ? auctionItem.getItemStacks().size() : 1)
                .sum();
        long maxSellPermission = configuration.getPermission().getLimit(ItemType.AUCTION, player);
        if (listedStacks + itemStacks.size() > maxSellPermission) {
            message(plugin, player, Message.LISTED_ITEMS_LIMIT, "%max-items%", String.valueOf(maxSellPermission));
            return SellFailReason.LISTING_LIMIT_REACHED;
        }

Si l'on tient à conserver la sémantique « annonces », ajouter une clé `max-stacks-per-listing` contrôlée au même endroit — et la répliquer dans config.yml ainsi que dans fr/, es/ et it/ conformément à la règle du projet.
```

---

<a id="c-052"></a>

### `C-052` — Pool Jedis : attente INFINIE à l'épuisement (maxWait jamais configuré), aggravée par un double emprunt imbriqué de connexion dans la branche NOSCRIPT

- [ ] **Corrigé**
- **Fichier** : `REDIS/connection/RedisConnectionFactory.java:66`
- **Catégorie** : Fiabilité — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
RedisConnectionFactory.java:66-71 (vérifié, méthode complète) :

    private static JedisPoolConfig buildPoolConfig(RedisConfig config) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getMaxTotal());
        poolConfig.setMaxIdle(config.getMaxIdle());
        poolConfig.setMinIdle(config.getMinIdle());
        return poolConfig;
    }

Ni `setMaxWait(...)` ni `setBlockWhenExhausted(...)` : on garde les défauts commons-pool2, soit blockWhenExhausted=true et maxWait=-1, c'est-à-dire un blocage SANS limite de durée dans getResource().

Double emprunt imbriqué, RedisAuctionClusterBridge.java:182 puis :204 (vérifié) — loadScripts() ré-emprunte une connexion alors que le thread en détient déjà une :

        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {     // 1re connexion
                ...
                        if (isNoScriptError(e)) {
                            scriptsLoaded = false;
                            loadScripts();                    // -> jedisPool.getResource() : 2e connexion

Même schéma pour unlockItem (RedisAuctionClusterBridge.java:252 puis :264).
Une connexion supplémentaire est monopolisée en permanence par le thread abonné (RedisSubscriberRunnable.java:67, `jedis.subscribe(...)` bloque tant que l'abonnement vit).
```

**Chronologie**

T0 — `redis-config.pool.max-total` vaut 64 dans le config.yml livré, mais RedisConfig.java:65 retient 10 en défaut de code si la clé est absente (config existante non régénérée après mise à jour). Une connexion est déjà prise à vie par le subscriber : il en reste 9.

T1 — Redis subit un FLUSHALL / SCRIPT FLUSH (maintenance, redémarrage sans persistance). Tous les evalsha en vol renvoient NOSCRIPT.

T2 — Un `remove all` sur 200 annonces plus quelques achats simultanés lancent 9 lockItem en parallèle. Chacun détient sa connexion et entre dans la branche NOSCRIPT ligne 204, qui appelle loadScripts() -> getResource() pour une 2e connexion.

T3 — Le pool est vide et les 9 threads attendent une connexion que seuls eux-mêmes pourraient rendre : interblocage classique par emprunt imbriqué. Avec maxWait=-1, l'attente est ÉTERNELLE — aucun timeout ne la rompt.

T4 — Les threads bloqués sont ceux de ForkJoinPool.commonPool (RedisAuctionClusterBridge.java:181, CompletableFuture.supplyAsync sans exécuteur). commonPool est dimensionné cœurs-1 : sur un VPS 4 vCPU, 3 threads suffisent à le saturer entièrement, et TOUTES les opérations cluster du serveur (achats, retraits, expirations) se figent définitivement, verrous Redis posés — verrous que plus aucun code ne pourra relâcher, seule leur TTL de 30 s les libérera pour les autres serveurs, sans que le nœud A ne l'apprenne jamais.

**Impact** — Interblocage permanent et non récupérable de toute la couche cluster d'un nœud, déclenché par un simple SCRIPT FLUSH côté Redis. Les items restent dans un état intermédiaire (statut de confirmation, LOCKED), et les autres serveurs les récupèrent à l'expiration du bail sans savoir que A croit toujours les traiter.

**Correctif**

```java
1) Borner l'attente dans buildPoolConfig :

        poolConfig.setMaxWait(Duration.ofMillis(config.getTimeout()));   // 2000 ms par défaut
        poolConfig.setTestOnBorrow(true);

Un `JedisExhaustedPoolException` remonté au CompletableFuture est infiniment préférable à un thread figé pour toujours.
2) Supprimer l'emprunt imbriqué : recharger les scripts SUR LA CONNEXION DÉJÀ EMPRUNTÉE plutôt que d'en demander une seconde :

    private void loadScriptsOn(Jedis jedis) {
        synchronized (scriptLoadLock) {
            this.lockScriptSha = jedis.scriptLoad(LOCK_SCRIPT);
            this.unlockScriptSha = jedis.scriptLoad(UNLOCK_SCRIPT);
            this.scriptsLoaded = true;
        }
    }

et appeler loadScriptsOn(jedis) aux lignes 204 et 264.
3) Donner un exécuteur dédié aux supplyAsync/runAsync du bridge plutôt que commonPool, pour que l'IO Redis ne puisse pas assécher le pool commun de la JVM.
```

---

<a id="c-053"></a>

### `C-053` — Retrait en masse : un échec survenu APRÈS la remise de l'item renvoie INTERNAL_ERROR, ce qui abandonne silencieusement le reste du lot et sous-compte les items rendus

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:1128`
- **Catégorie** : Fiabilité — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ZAuctionManager.processBulkItems, lignes 1113-1133 (vérifié) :

        for (Item item : items) {
            future = future.thenCompose(progress -> {
                if (progress.stopped()) return CompletableFuture.completedFuture(progress);
                return runBulkRemovalOnPlayerThread(player, item, removal).handle((result, throwable) -> {
                    ...
                    boolean success = result != null && result.isSuccess() && result.isItemGiven();
                    boolean stopped = result == null || result.getFailReason() == RemoveFailReason.INSUFFICIENT_SPACE || result.getFailReason() == RemoveFailReason.INTERNAL_ERROR;
                    return new BulkRemovalProgress(progress.given() + (success ? 1 : 0), stopped);
                });
            });
        }

RemoveService.executeRemoval (RemoveService.java:200, vérifié) enchaîne 5 étapes, et la remise locale n'est que la 4e :

        return checkAvailabilityStep(...).thenCompose(available -> acquireLockStep(...)).thenCompose(token -> changeStatusAndNotifyStep(...)).thenCompose(v -> executeLocalRemovalStep(...)).thenCompose(v -> unlockAndCompleteStep(...)).exceptionally(throwable -> handleRemovalException(context, throwable, clusterBridge, logger));

RemoveService.java:249-253 (vérifié) : `context.result` n'est renseigné qu'à l'étape 5, APRÈS un unlockItem qui peut expirer :

    private CompletableFuture<Void> executeLocalRemovalStep(...) {
        return context.onLocalRemoval.get().thenCompose(v -> {
            context.localRemovalCompleted = true;
            return clusterBridge.removeItem(context.item, context.storageType, context.destinationStorageType).orTimeout(config.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS);
        });
    }

RemoveService.java:287 (vérifié) : `localRemovalCompleted` n'est jamais consulté à la sortie :

        return context.result != null ? context.result : RemoveResult.failure("Internal error", RemoveFailReason.INTERNAL_ERROR);
```

**Chronologie**

T0 — Un joueur avec 200 annonces clique sur RemoveAllSellingButton. processBulkItems chaîne les 200 items EN SÉRIE via thenCompose ; chaque item coûte 5 allers-retours Redis (checkAvailability, lockItem, notifyItemStatusChange, removeItem, unlockItem) plus un hop runAtEntity, soit ~1000 allers-retours strictement séquentiels.

T1 — À l'item n°37, le lien Redis a un hoquet. `context.onLocalRemoval.get()` a déjà rendu ses ItemStacks au joueur et écrit la ligne en base : localRemovalCompleted = true. Puis `removeItem(...).orTimeout(notifyItemActionTimeoutMs)` (5000 ms par défaut) expire.

T2 — handleRemovalException est appelé avec context.result toujours null -> RemoveResult.failure("Internal error", INTERNAL_ERROR), alors que l'item 37 A BIEN ÉTÉ rendu.

T3 — Dans processBulkItems : success=false (l'item 37 n'est pas compté) ET stopped=true.

T4 — Les items 38 à 200 ne sont jamais traités. finishBulkRemoval (ZAuctionManager.java:1164-1170) affiche Message.REMOVE_ALL_ITEMS avec %amount% = 36 alors que 37 items ont été rendus, et 163 annonces restent en place SANS le moindre message d'erreur.

En mono-serveur, le déclencheur est encore plus banal : un double-clic lance deux chaînes ; la seconde tombe sur LocalAuctionClusterBridge.lockItem (LocalAuctionClusterBridge.java:24-30) qui renvoie `CompletableFuture.failedFuture(new IllegalStateException(...))` — et non le LockToken.noop() renvoyé par le bridge Redis — donc INTERNAL_ERROR, donc l'une des deux chaînes s'arrête net au premier item.

**Impact** — Un incident réseau transitoire abandonne silencieusement 80 % d'un lot de retraits, avec un décompte faux affiché au joueur et aucun canal d'erreur. Le joueur croit ses annonces retirées ; le support est saisi ; et la divergence entre ce qui a été rendu et ce qui est compté rend tout diagnostic a posteriori impossible.

**Correctif**

```java
1) Ne pas transformer en échec fatal un incident survenu après le point de non-retour (RemoveService.java:287) :

        if (context.result != null) return context.result;
        return context.localRemovalCompleted
                ? RemoveResult.success("Removed, cluster notification failed", true)
                : RemoveResult.failure("Internal error", RemoveFailReason.INTERNAL_ERROR);

2) N'arrêter la chaîne de lot que sur INSUFFICIENT_SPACE (ZAuctionManager.java:1128) :

        boolean stopped = result == null || result.getFailReason() == RemoveFailReason.INSUFFICIENT_SPACE;

et dans finishBulkRemoval, si `given < items.size()`, envoyer un message indiquant le nombre d'annonces non traitées.
3) Uniformiser l'échec de verrou entre les deux bridges — LocalAuctionClusterBridge.lockItem doit renvoyer `CompletableFuture.completedFuture(LockToken.noop())` comme le bridge Redis, pour produire LOCK_FAILED (non bloquant) et non INTERNAL_ERROR.
```

---

<a id="c-054"></a>

### `C-054` — Récursion mutuelle infinie entre ItemStackUtils.safeDeserializeItemStack et Base64ItemStack.decode sur tout serveur antérieur à 1.20.5 : StackOverflowError qu'aucun catch du projet ne rattrape

- [ ] **Corrigé**
- **Fichier** : `API/api/utils/ItemStackUtils.java:41`
- **Catégorie** : Fiabilité — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ItemStackUtils.java:41-47 (vérifié) :

    public static ItemStack safeDeserializeItemStack(String paramString) {
        try {
            return tryDeserializeItemStack(paramString);
        } catch (Exception exception) {
            return Base64ItemStack.decode(paramString);
        }
    }

Base64ItemStack.java:54-58 (vérifié) — qui rappelle immédiatement la méthode précédente :

    public static ItemStack decode(String data) {

        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            return ItemStackUtils.safeDeserializeItemStack(data);
        }

NmsVersion.java:167-169 (vérifié) : `isAttributItemStack()` renvoie `version >= 1205`. Sur toute version antérieure à 1.20.5, la condition est vraie et le cycle safeDeserializeItemStack -> tryDeserializeItemStack (échec) -> decode -> safeDeserializeItemStack est infini.
src/main/resources/plugin.yml:4 (vérifié) : `api-version: '1.20'` — le plugin se déclare compatible 1.20.0 à 1.20.4, où version < 1205.
ZAuctionPlugin.java:160 (vérifié) : `this.storageManager.loadItems();` n'est protégé par aucun try/catch dans onEnable. Un StackOverflowError est un Error, jamais rattrapé par les `catch (Exception)` du projet.
```

**Chronologie**

T0 — Le serveur tourne en 1.20.4 (api-version 1.20 déclarée compatible).

T1 — Une seule ligne `items` contient un payload Base64 illisible : donnée tronquée par un `TEXT` MySQL trop court, item d'un plugin custom désinstallé, ou simplement une ligne migrée depuis V3 dans un autre format.

T2 — Au chargement, tryDeserializeItemStack lève (ItemStackUtils.java:97-106, getClassz() peut même renvoyer null après avoir avalé une ClassNotFoundException, ce qui garantit une NullPointerException juste après).

T3 — Le catch appelle Base64ItemStack.decode, qui rappelle safeDeserializeItemStack, qui rappelle decode... jusqu'à StackOverflowError.

T4 — L'Error remonte à travers tous les `catch (Exception)` du projet, y compris celui d'AdminLogsButton.java:147. Selon le chemin : loadItems() interrompt onEnable et le plugin ne démarre pas ; ou le thread d'un achat meurt en plein milieu, verrou Redis posé et jamais relâché, item ni rendu ni remboursé.

**Impact** — Une seule ligne corrompue rend le plugin indémarrable, ou tue silencieusement un thread d'achat en pleine section critique sur les serveurs 1.20.0-1.20.4 officiellement supportés. Le mode de défaillance (Error non rattrapable par le code existant) est le plus violent possible.

**Correctif**

```java
Casser le cycle : la branche de repli de `decode` ne doit jamais rappeler la méthode qui l'a appelée. Introduire une méthode privée non récursive et l'utiliser des deux côtés :

// Base64ItemStack
    public static ItemStack decode(String data) {
        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            try {
                return ItemStackUtils.tryDeserializeItemStack(data);   // méthode qui LÈVE, pas la variante "safe"
            } catch (Exception e) {
                return null;
            }
        }
        ...
    }

// ItemStackUtils
    public static ItemStack safeDeserializeItemStack(String paramString) {
        try {
            return tryDeserializeItemStack(paramString);
        } catch (Exception exception) {
            return decodeBukkitBase64(paramString);   // le corps direct de Base64ItemStack.decode, sans le test de version
        }
    }

Et entourer `this.storageManager.loadItems()` (ZAuctionPlugin.java:160) d'un `catch (Throwable)` qui désactive proprement le plugin plutôt que de laisser une Error remonter au PluginManager.
```

---

<a id="c-055"></a>

### `C-055` — SearchService reconstruit une HashMap de tout le store LISTED a chaque recalcul de recherche, sur le thread principal

- [ ] **Corrigé**
- **Fichier** : `services/SearchService.java:48`
- **Catégorie** : Performance — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
SearchService.java:45-58 (verifie) :
        IntList allIds = sortedItemsCache.getSortedIds(category, sort);
        IntList results = new IntArrayList();

        var storage = this.plugin.getAuctionManager().getItems(StorageType.LISTED);   // copie ArrayList de N items
        var itemMap = new java.util.HashMap<Integer, Item>(storage.size());           // + HashMap de N entrees
        for (Item item : storage) {
            itemMap.put(item.getId(), item);
        }

        String lowerValue = parsedQuery.value().toLowerCase();

        for (int id : allIds) {
            Item item = itemMap.get(id);

Appele sur le MAIN THREAD depuis le rendu, ZAuctionManager.java:288-291 (verifie) :
        String searchQuery = cache.get(PlayerCacheKey.SEARCH_QUERY);
        if (searchQuery != null && !searchQuery.isBlank()) {
            IntList ids = cache.getOrCompute(PlayerCacheKey.ITEMS_SEARCH, () -> searchService.search(sortedItemsCache, searchQuery, sort, category));

Et ITEMS_SEARCH est vide par les listeners cluster : ItemListedListener.java:45 (verifie) `manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);`

La HashMap est du gaspillage pur : storageItemsById.get(LISTED) EST deja une Map<Integer, Item> (ZAuctionManager.java:64).
```

**Chronologie**

Charge : 50 000 items LISTED, 10 joueurs avec une recherche active sur le serveur B, cluster emettant 10 ItemListedMessage et 5 ItemBoughtMessage par seconde.

T0 serveur A : un joueur vend un item -> notifyItemListed.

T1 serveur B : ItemListedListener.java:45 clearPlayersCache(ITEMS_LISTED, ITEMS_SEARCH) : les 10 joueurs en recherche perdent leur resultat cache.

T2 serveur B, main thread : au rendu suivant (onRender puis getPaginationSize, soit DEUX appels par ouverture de page), chaque joueur recalcule search() : une ArrayList de 50 000 references (200 Ko), puis une HashMap de 50 000 entrees (~3 Mo avec les Integer boxes et les Node), puis un parcours de allIds avec, par item, plusieurs toLowerCase() (getTranslationKey, getType().name(), getDisplayName(), et une boucle sur chaque ligne de lore).

T3 : 10 joueurs x 2 appels par rendu x 15 evenements cluster/s -> jusqu'a 300 recalculs/s, soit de l'ordre du Go par seconde d'allocation sur le main thread. Le serveur passe son temps en GC pendant que les autres noeuds continuent d'emettre.

**Impact** — La recherche devient le point chaud du main thread des que le cluster est actif, alors que la structure d'acces O(1) existe deja juste a cote. Le toLowerCase() est de plus recalcule sur chaque champ de chaque item a chaque recherche, sans aucune memorisation.

**Correctif**

```java
1) Supprimer la copie et la HashMap (SearchService.java:48-58), en utilisant l'accesseur O(1) a ajouter sur AuctionManager :
    var manager = this.plugin.getAuctionManager();
    String lowerValue = parsedQuery.value().toLowerCase();
    for (int id : allIds) {
        Item item = manager.getItem(StorageType.LISTED, id);   // O(1), aucune copie
        if (item == null) continue;
        ...
    }

2) Ne pas vider ITEMS_SEARCH pour un evenement portant sur un seul item : ajouter dans ZAuctionManager, a cote de removeFromCache (l.978-986) :
    private void removeFromSearchCache(Player player, Item item) {
        var cache = this.caches.get(player);
        if (cache == null) return;
        IntList ids = cache.get(PlayerCacheKey.ITEMS_SEARCH);
        if (ids != null && !ids.isEmpty()) ids.rem(item.getId());
    }
et l'appeler depuis updateListedItems, puis retirer PlayerCacheKey.ITEMS_SEARCH des clearPlayersCache de ItemListedListener.java:45 et ItemBoughtListener.java:50.

3) Memoriser la forme minuscule des champs cherchables dans ZAuctionItem (calculee une fois a la construction : les ItemStack d'une annonce sont immuables) pour eviter les toLowerCase() repetes de matchDefault.
```

---

<a id="c-056"></a>

### `C-056` — Tempete de rebuild du SortedItemsCache : chaque item liste sur le cluster declenche un retri complet, et updateListedItems attend ce rebuild avant d'afficher

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:952`
- **Catégorie** : Performance — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ZAuctionManager.java:236-245 (verifie) - tout addItem LISTED salit le cache trie :
    @Override
    public void addItem(StorageType storageType, Item item) {
        var storage = this.storageItemsById.get(storageType);
        storage.put(item.getId(), item);
        this.indexItem(storageType, item);

        if (storageType == StorageType.LISTED) {
            this.plugin.getCategoryManager().invalidateCategoryCountCache();
            this.sortedItemsCache.invalidate();
        }
    }

ZAuctionManager.java:950-953 (verifie) - et l'affichage ATTEND la reconstruction :
        // Wait for the sorted cache to be rebuilt before updating inventories,
        // otherwise players get stale data cached from a dirty sorted cache
        this.sortedItemsCache.ensureCacheValidAsync().thenRun(() -> {

ItemListedListener.java:44-47 (verifie, addon Redis) - les deux sont enchaines a chaque message recu :
                manager.addItem(StorageType.LISTED, item);
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
                manager.updateListedItems(item, true, null);

SortedItemsCache.java:335-372 (verifie) - le cout d'un rebuild : 2 tris globaux (Arrays.parallelSort si itemCount >= parallelSortThreshold) + buildCategorySortedLists (l.408-438) qui refait 2 tris PAR categorie.
config.yml:975 (verifie) : parallel-sort-threshold: 10000.

SortedItemsCache.java:48/51/54 (verifie) - DEUX verrous de rebuild concurrents :
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final AtomicBoolean rebuildInProgress = new AtomicBoolean(false);   // utilise par triggerRebuildIfNeeded l.131
    private final AtomicReference<CompletableFuture<Void>> ongoingRebuild = new AtomicReference<>(null);  // utilise par ensureCacheValidAsync l.249
```

**Chronologie**

Charge : 50 000 items LISTED, 9 categories, cluster de 4 serveurs, 10 mises en vente par seconde sur l'ensemble du reseau.

T0 serveur A : un joueur vend -> SellService -> addItem(LISTED) (invalidate) + notifyItemListed sur le bus.

T1 serveurs B, C, D : ItemListedListener:44 addItem(LISTED) -> sortedItemsCache.invalidate() -> dirty = true. Puis l.47 updateListedItems -> ensureCacheValidAsync() -> dirty -> lancement d'un rebuild complet.

T2 : rebuildCache refait a chaque fois une passe de filtrage/groupement sur 50 000 items, 2 tris globaux de 50 000 items, puis 2 tris par categorie x 9 categories, soit ~20 tris par rebuild.

T3 : ensureCacheValidAsync coalesce les demandes concurrentes via ongoingRebuild, donc le rythme est borne par la DUREE d'un rebuild. A 10 mises en vente/s, il y a toujours au moins une invalidation en attente a la fin de chaque rebuild : les rebuilds s'enchainent DOS A DOS, en permanence, sur B, C et D.

T4 : avec 50 000 items > parallel-sort-threshold (10000), c'est Arrays.parallelSort qui est utilise, donc ForkJoinPool.commonPool - le meme pool que les appels Jedis bloquants du bridge. Les deux se degradent mutuellement.

T5 : triggerRebuildIfNeeded (garde rebuildInProgress) et ensureCacheValidAsync (garde ongoingRebuild) peuvent lancer DEUX rebuilds en parallele ; le second se gare sur le writeLock en immobilisant un thread async supplementaire.

T6 : comme updateListedItems n'agit qu'apres completion du rebuild, l'affichage des inventaires des joueurs de B, C et D est systematiquement en retard sur l'etat reel du cluster.

**Impact** — Un coeur entier consomme en permanence par la reconstruction du cache trie sur CHAQUE serveur, alors que l'evenement declencheur est l'ajout d'UN seul item. Contention sur le commonPool partage avec le bridge Redis, retard systematique de l'affichage, et possibilite de deux rebuilds concurrents immobilisant deux threads async.

**Correctif**

```java
1) Coalescer les invalidations dans le temps, dans SortedItemsCache :
private volatile long lastRebuildTime;
private static final long MIN_REBUILD_INTERVAL_MS = 250; // a rendre configurable
private void triggerRebuildIfNeeded() {
    if (!dirty.get()) return;
    if (System.currentTimeMillis() - lastRebuildTime < MIN_REBUILD_INTERVAL_MS) return;
    ...
}
et planifier un rebuild garanti toutes les MIN_REBUILD_INTERVAL_MS via plugin.getScheduler().runTimerAsync, pour qu'aucune invalidation ne soit perdue et que le rythme soit borne (4 rebuilds/s au lieu d'un rebuild permanent).

2) Ne PAS attendre le rebuild pour rafraichir la GUI. ZAuctionManager.java:952 : remplacer ensureCacheValidAsync().thenRun(...) par un simple sortedItemsCache.invalidate() suivi de la boucle ; getSortedIds est deja documentee comme non bloquante et l'insertion visuelle est deja geree item par item par processAdd/processRemove.

3) Unifier les deux verrous : faire de ensureCacheValidAsync le seul point d'entree et supprimer rebuildInProgress (SortedItemsCache.java:51, utilise l.131/137).

4) Soumettre les tris globaux au forkJoinPool prive deja cree dans SortedItemsCache plutot qu'au commonPool partage avec le bridge Redis :
forkJoinPool.submit(() -> Arrays.parallelSort(itemArray, SortItem.ASCENDING_DATE.getComparator())).get();
```

---

<a id="c-057"></a>

### `C-057` — Un seul champ non convertible detruit tout le message de synchronisation : constante d'enum inconnue ou parametre primitif manquant -> message integralement abandonne

- [ ] **Corrigé**
- **Fichier** : `REDIS/utils/Utils.java:95`
- **Catégorie** : Contrat API — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
Utils.convertToRequiredType:95-100 (verifie) - l'echec d'enum est loggue puis IGNORE, on tombe jusqu'au `return value` l.145 qui renvoie la String brute :

        } else if (type.isEnum()) {
            try {
                return Enum.valueOf((Class<Enum>) type, (String) value);
            } catch (IllegalArgumentException exception) {
                logger.log(Level.SEVERE, String.format("Failed to convert '%s' to enum type '%s'", value, type.getName()), exception);
            }
        }

Utils.createInstanceFromMap:43-50 (verifie) - Number.class.isAssignableFrom(int.class) vaut FALSE, et convertToRequiredType (qui saurait rendre 0, l.83-93) n'est jamais appele quand value est null a cause du garde l.50 `if (value != null)` :

                Object value = map.containsKey(paramName) ? map.get(paramName) : map.get(configKey);
                if (value == null && Number.class.isAssignableFrom(paramType)) { value = 0; }
                if (value == null && Boolean.class.isAssignableFrom(paramType)) { value = false; }
                if (value != null) { ... value = convertToRequiredType(logger, value, paramType); ... }

Utils.createInstanceFromMap:72-78 (verifie) - toute erreur de newInstance devient une RuntimeException :

            return constructor.newInstance(arguments);
        } catch (Exception exception) {
            ...
            throw new RuntimeException("Failed to create instance from map with constructor " + constructor, exception);
        }

RedisSubscriberRunnable.java:135 puis 147-150 (verifie) - la RuntimeException fait perdre le message entier, y compris les champs parfaitement lisibles :

            Object result = Utils.createInstanceFromMap(plugin.getLogger(), messageClass.getConstructors()[0], map);
        ...
        } catch (Exception exception) {
            plugin.getLogger().severe("Error processing Redis message: " + exception.getMessage());

Deux des quatre messages transportent des enums (verifie) : ItemRemovedMessage(String itemId, StorageType storageType, StorageType destinationStorageType) et ItemStatusMessage(String itemId, ItemStatus oldStatus, ItemStatus newStatus).

Le javadoc promet pourtant l'inverse - ItemBoughtMessage.java:12-15 (verifie) : "Missing keys from old-format payloads deserialize to null, so this stays backward compatible during a rolling upgrade."
```

**Chronologie**

T0 - Reseau de 4 serveurs. On deploie zAuctionHouseV4 4.2 sur A pendant que B, C, D restent en 4.1. La nouvelle version ajoute une constante a l'enum ItemStatus (ou a StorageType) de l'API partagee. L'addon Redis etant compileOnly sur l'API, la classe d'enum reellement chargee a l'execution est celle du zAuctionHouse installe sur chaque serveur : celle de B ne connait pas la nouvelle constante.

T1 - Sur A, un joueur ouvre la confirmation d'achat de l'item 55. notifyItemStatusChange publie {"itemId":"55","oldStatus":"AVAILABLE","newStatus":"<NOUVELLE_CONSTANTE>"}.

T2 - Sur B, convertToRequiredType execute Enum.valueOf(ItemStatus.class, "<NOUVELLE_CONSTANTE>") -> IllegalArgumentException, loggee SEVERE l.99, puis la methode poursuit et rend la String brute (l.145).

T3 - constructor.newInstance recoit une String la ou un ItemStatus est attendu -> IllegalArgumentException: argument type mismatch -> RuntimeException l.77 -> RedisSubscriberRunnable:147 -> message ABANDONNE. Le champ itemId, pourtant lisible, est perdu avec le reste.

T4 - Le meme mecanisme s'applique a ItemRemovedMessage.storageType / destinationStorageType : B n'apprend jamais qu'un item a ete retire ou vendu, et garde un fantome permanent dans son store LISTED - fantome que le chemin de retrait (RemoveService) sait transformer en item gratuit.

Variante 2 (evolution de l'addon) : on ajoute un champ `int amount` a ItemBoughtMessage en se fiant au javadoc l.12-15. Un serveur reste en ancienne version et publie sans la cle : value = null, le garde-fou l.43 ne s'applique pas (Number.class.isAssignableFrom(int.class) est FALSE), newInstance recoit null pour un int -> exception -> TOUS les ItemBoughtMessage venant des noeuds non encore mis a jour sont jetes pendant toute la duree du deploiement progressif.

**Impact** — Perte totale et silencieuse (hors logs SEVERE noyes) des messages de synchronisation pendant toute mise a jour progressive du plugin principal ou de l'addon, exactement la periode ou les noeuds divergent le plus. Resultat : items fantomes en masse sur les noeuds restes en arriere, donc duplication par le chemin de retrait. Le contrat de compatibilite ecrit dans le javadoc des records n'est vrai que pour les types reference et ne couvre pas du tout l'ajout d'une constante a un enum existant.

**Correctif**

```java
1) Ne jamais renvoyer une valeur non convertie. Dans Utils.convertToRequiredType, remplacer le catch enum l.98-100 (et faire de meme pour BigDecimal l.104, UUID l.110, Double/Long/Float/Boolean) par :

            } catch (IllegalArgumentException exception) {
                logger.log(Level.WARNING, String.format("Unknown constant '%s' for enum '%s', field left null", value, type.getName()));
                return null;
            }

Un champ non convertible doit devenir null - les listeners savent deja traiter un champ null - et non une String qui fera exploser newInstance et emportera tout le message.

2) Gerer les primitifs manquants dans createInstanceFromMap, en remplacant les l.43-48 par :

                if (value == null && paramType.isPrimitive()) {
                    value = Array.get(Array.newInstance(paramType, 1), 0); // 0, 0L, 0.0, false...
                } else if (value == null && (Number.class.isAssignableFrom(paramType) || Boolean.class.isAssignableFrom(paramType))) {
                    value = Number.class.isAssignableFrom(paramType) ? (Object) 0 : (Object) Boolean.FALSE;
                }

3) Ne plus dependre de getConstructors()[0] (RedisSubscriberRunnable:135), fragile des qu'un constructeur de confort est ajoute :

            Constructor<?> canonical = messageClass.getDeclaredConstructor(
                    Arrays.stream(messageClass.getRecordComponents()).map(RecordComponent::getType).toArray(Class[]::new));

4) Regle a inscrire dans le javadoc des 4 records : jamais de type primitif dans un message ; pour toute valeur d'enum, transporter la String et resoudre l'enum cote listener avec un repli explicite, de sorte qu'une valeur inconnue ne fasse perdre que le champ concerne et jamais l'itemId.
```

---

<a id="c-058"></a>

### `C-058` — Un seul échec de loadScripts() condamne le nœud au chemin de verrouillage non atomique à vie : reloadScriptsIfNeeded() n'a aucun appelant

- [ ] **Corrigé**
- **Fichier** : `REDIS/RedisAuctionClusterBridge.java:132`
- **Catégorie** : Fiabilité — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
RedisAuctionClusterBridge.java:115-136 (vérifié) :

    private void loadScripts() {
        synchronized (scriptLoadLock) {
            try (Jedis jedis = jedisPool.getResource()) {
                ...
                this.scriptsLoaded = true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load Lua scripts: " + e.getMessage() + ". Falling back to non-atomic operations.");
                this.scriptsLoaded = false;
            }
        }
    }

    private void reloadScriptsIfNeeded() {
        if (!scriptsLoaded) {
            loadScripts();
        }
    }

`grep -rn "reloadScriptsIfNeeded" src/` sur tout l'addon ne retourne QUE sa déclaration ligne 132 : la méthode est morte. loadScripts() n'est appelée que depuis le constructeur (ligne 109) et depuis les deux branches NOSCRIPT (lignes 204 et 264).

RedisAuctionClusterBridge.java:190 et :254 (vérifié) : `if (scriptsLoaded) { ... }` — quand le drapeau est faux, on ne tente même plus evalsha, donc la branche NOSCRIPT ne peut plus se déclencher.
RedisAuctionClusterBridge.java:220 (vérifié) : le repli est explicitement documenté comme dangereux — `// Fallback to non-atomic operation (with race condition risk)`.
RedisAuctionClusterBridge.java:203 et :263 (vérifié) : `scriptsLoaded = false;` est écrit HORS du bloc synchronized.
```

**Chronologie**

T0 — Le serveur A démarre. ZAuctionHouseRedis.loadRedis() valide la connexion par un PING qui réussit, puis le constructeur du bridge (ligne 109) appelle loadScripts().

T1 — À cet instant Redis répond LOADING (dataset en cours de chargement après redémarrage) ou bascule sur un sentinel. scriptLoad lève. UN warning est loggé, scriptsLoaded reste false.

T2 — Redis redevient sain 3 secondes plus tard. Rien ne le détecte : le test `if (scriptsLoaded)` ligne 190 est faux, donc aucun evalsha n'est tenté, donc aucun NOSCRIPT ne peut survenir, donc loadScripts() ne sera plus jamais rappelée de toute la vie du serveur.

T3 — Serveur A (repli non atomique) et serveur B (script Lua atomique) verrouillent maintenant selon deux protocoles différents. Sur A, lockItem lit `hget itemKey state` (ligne 221), puis `set lockKey NX PX` (ligne 231) en deux allers-retours distincts.

T4 — Entre la lecture d'état de A et son SET NX, B exécute removeItem qui passe `auction:item:<id>` à STATE_REMOVED. A ne le voit pas : il acquiert le verrou sur un item déjà retiré. Combiné à la revalidation d'achat qui ne teste pas storage_type, l'acheteur sur A paie et reçoit un item que B vient de rendre à son vendeur.

Seconde voie, transitoire mais globale : lignes 203 et 263, `scriptsLoaded = false` est posé hors du synchronized avant loadScripts(). Tout thread entrant dans lockItem pendant ce rechargement lit false et bascule sur le repli non atomique alors que Redis est parfaitement sain.

**Impact** — Le seul mécanisme réellement atomique du verrou distribué peut être désactivé de façon permanente et quasi silencieuse par un incident Redis de deux secondes au démarrage, dont la seule trace est un warning noyé dans les logs de boot. Toutes les fenêtres de course du repli deviennent alors permanentes pour ce nœud.

**Correctif**

```java
1) Appeler reloadScriptsIfNeeded() en tête de lockItem (avant le if ligne 190) et de unlockItem (avant le if ligne 254), avec un limiteur pour ne pas marteler Redis :

private volatile long nextScriptReloadAttemptAt = 0L;

private void reloadScriptsIfNeeded() {
    if (scriptsLoaded) return;
    if (System.currentTimeMillis() < nextScriptReloadAttemptAt) return;
    synchronized (scriptLoadLock) {
        if (scriptsLoaded || System.currentTimeMillis() < nextScriptReloadAttemptAt) return;
        nextScriptReloadAttemptAt = System.currentTimeMillis() + 5000;
        loadScripts();
    }
}

2) Déplacer `scriptsLoaded = false` (lignes 203 et 263) à l'INTÉRIEUR du synchronized de loadScripts, pour qu'aucun thread concurrent ne voie l'état intermédiaire.
3) Faire échouer onEnable si loadScripts() échoue au démarrage : un cluster sans verrou atomique est plus dangereux qu'un cluster indisponible.
```

---

<a id="c-059"></a>

### `C-059` — Vente cross-serveur : le vendeur connecté ailleurs reçoit « votre item a été vendu pour X » mais n'est jamais crédité, et le log d'achat est marqué lu au passage

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:810`
- **Catégorie** : Cycle de vie — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ZAuctionManager.java:810-818 (vérifié) :

        boolean sellerOnThisServer = seller.isOnline();
        boolean deferDeposit = !auctionEconomy.isAutoClaim()
                || (!sellerOnThisServer && auctionEconomy.mustBeOnline())
                || (!sellerOnThisServer && clusterBridge.isDistributed());

        if (deferDeposit) {
            transactionStatus = TransactionStatus.PENDING;

ZAuctionHouse Redis, ItemBoughtListener.java:68-78 (vérifié) : le message est envoyé, le log marqué lu, mais AUCUN crédit ni déclenchement de claim :

            var seller = Bukkit.getOfflinePlayer(sellerUuid);
            if (seller.isOnline()) {
                var sellerPlayer = seller.getPlayer();
                manager.message(sellerPlayer, Message.ITEM_BOUGHT_SELLER, "%items%", message.itemDisplay(), "%price%", message.price(), ...);
                manager.clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING);
                auctionPlugin.getStorageManager().markPurchaseLogAsRead(id, sellerUuid);
            }

Seuls déclencheurs de claimMoney (grep vérifié sur src/) : ClaimButton.java:120 (clic manuel), CommandAuctionClaim.java:22 (`/ah claim`), et ClaimService.handlePlayerJoin appelé uniquement depuis PlayerListener.java:24 (PlayerJoinEvent).
Aucune tâche périodique n'existe : `grep -rn "runTimer" src/main/java/` ne retourne QUE BossBarAnimation.java:21.
```

**Chronologie**

T0 — Le vendeur V est connecté sur le serveur B ; son item est listé.

T1 — L'acheteur A achète l'item sur le serveur A. Sur A, `seller.isOnline()` est false (V est sur B) et `clusterBridge.isDistributed()` est true : ZAuctionManager.java:812 -> deferDeposit = true -> ligne 818 statut PENDING, AUCUN dépôt.

T2 — A publie ItemBoughtMessage. Sur B, ItemBoughtListener.java:71 envoie à V le message ITEM_BOUGHT_SELLER contenant %price%, et ligne 77 marque le log PURCHASE comme lu.

T3 — V lit « votre item a été vendu 100000 », consulte /balance : rien n'a bougé.

T4 — V reste connecté sur B pendant des heures : rien ne déclenche de claim. Le seul chemin automatique est PlayerJoinEvent, qui n'arrivera qu'à sa prochaine reconnexion.

T5 — Le log PURCHASE ayant été marqué lu en T2, la notification de rattrapage de HistoryService.handlePlayerJoin ne se déclenchera pas non plus : le rattrapage repose entièrement sur l'auto-claim de ClaimService.

**Impact** — Sur un réseau, la quasi-totalité des ventes se font vendeur-connecté-ailleurs : l'argent reste bloqué en PENDING de façon opaque juste après un message annonçant le montant. L'argent n'est pas perdu, mais l'expérience est celle d'un vol, et l'effacement du log de rattrapage supprime le second filet de sécurité.

**Correctif**

1) Déclencher le claim là où l'on sait déjà que le vendeur est en ligne — dans ItemBoughtListener, après le clearPlayerCache ligne 76 :

            auctionPlugin.getAuctionManager().getClaimService().claimMoney(sellerPlayer)
                    .exceptionally(t -> { this.plugin.getLogger().warning("Auto-claim after cross-server sale failed: " + t.getMessage()); return null; });

Ce claim doit impérativement être posé APRÈS la correction du compare-and-set sur les transactions PENDING, sinon il ouvre une fenêtre de double crédit avec un `/ah claim` manuel simultané.
2) Le conditionner à `getAutoClaimConfiguration().enabled()`, et sinon utiliser une variante du message indiquant que le montant est en attente de réclamation.
3) Filet de sécurité indépendant du pub/sub (fire-and-forget, donc perdable) : `getScheduler().runTimerAsync(() -> Bukkit.getOnlinePlayers().forEach(claimService::handlePlayerJoin), 5, 5, TimeUnit.MINUTES)` — inoffensif une fois le CAS en place.
4) Ne pas marquer le log PURCHASE comme lu tant que le paiement n'a pas été effectivement crédité.

---

<a id="c-060"></a>

### `C-060` — adminRemoveItem ne relit jamais l'etat autoritaire en base sous verrou, faute de primitive verrouiller+lire dans le contrat

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:687`
- **Catégorie** : Duplication d’item — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ZAuctionManager.adminRemoveItem (verifie l.659, chaine l.679-703) : verrou puis suppression immediate, aucun selectItem, aucune garde sur l'etat reel de la ligne :

        clusterBridge.checkAvailability(item).thenCompose(available -> {
            if (!available) { ... return failedFuture(new IllegalStateException("Item indisponible")); }
            return clusterBridge.lockItem(item, admin.getUniqueId(), storageType);
        }).thenCompose(lockToken -> clusterBridge.removeItem(item, storageType).thenApply(v -> lockToken)).thenAccept(lockToken -> {
            removeItem(storageType, item);
            this.plugin.getStorageManager().updateItem(item, StorageType.DELETED);
            clearPlayersCache(...);
            giveItem(admin, item);

A comparer avec PurchaseService.java:118-127 (verifie), qui compense a la main, hors du contrat, par un aller-retour SQL supplementaire :

            .thenCompose(v -> this.plugin.getStorageManager().selectItem(item.getId())
                    .orTimeout(performanceConfig.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS))
            .thenCompose(dbItem -> {
                if (dbItem == null || dbItem.getBuyerUniqueId() != null) { ... "Item already sold" ... }

et avec ExpireService.java:280-289 (verifie), qui fait la meme compensation. Le contrat, lui, ne propose aucune primitive d'etat : AuctionClusterBridge.java:24-94 ne connait que checkAvailability / lockItem / unlockItem / notify* / removeItem.

L'UPDATE final est de surcroit inconditionnel pour la cible DELETED - ItemRepository.createUpdateSchema:71-91 (verifie) ne pose une garde de transition que pour EXPIRED :

            schema.where("id", item.getId());
            if (storageType == StorageType.EXPIRED) {
                schema.where("storage_type", StorageType.LISTED.name());
            }
            schema.string("storage_type", storageType.name());
```

**Chronologie**

T0 - Serveurs A et B. L'item 42 de V est LISTED. Un administrateur ouvre l'inventaire admin sur B (les stores memoire de B contiennent l'item, statut AVAILABLE).

T1 - Sur A, l'acheteur P achete l'item 42 : PurchaseService valide, l'argent de P est preleve, V est credite, la ligne passe a PURCHASED avec buyer_unique_id = P (ou a DELETED si purchased-item.give-item: true), l'etat Redis passe a SOLD. Le verrou est relache normalement.

T2 - Sur B, l'ItemBoughtMessage est perdu (bus en reconnexion, ou ItemStatusListener.java:44-52 abandonne le message parce que sa recherche lineaire dans les 3 stores ne trouve pas l'item). B affiche toujours l'item 42.

T3 - L'admin clique "supprimer" sur B. checkAvailability lit state=SOLD : RedisAuctionClusterBridge.java:167-175 ne refuse que DELETED et LOCKED -> true. lockItem : LOCK_SCRIPT ne bloque lui aussi que sur DELETED et LOCKED -> verrou accorde. Aucun selectItem n'est fait.

T4 - ZAuctionManager.java:693 execute updateItem(item, StorageType.DELETED) : UPDATE aveugle qui ecrase la ligne PURCHASED de P, puis l.696 giveItem(admin, item) donne l'item a l'admin. P a paye et recu l'item, l'admin en detient un second exemplaire, et la trace de l'achat de P est effacee de la table items. Si le meme flux etait passe par PurchaseService, la revalidation l.121 (dbItem.getBuyerUniqueId() != null) aurait abandonne proprement.

**Impact** — Duplication d'item et perte de la ligne d'achat, atteignables par une action admin banale sur un noeud desynchronise. Cause structurelle : le contrat n'offre aucune primitive "verrouiller ET lire l'etat autoritaire", donc chaque appelant doit y penser seul - PurchaseService et ExpireService l'ont fait, adminRemoveItem pas du tout.

**Correctif**

```java
1) Correctif immediat dans ZAuctionManager.adminRemoveItem : intercaler la relecture autoritaire entre le verrou et la suppression.

    }).thenCompose(lockToken -> this.plugin.getStorageManager().selectItem(item.getId()).thenCompose(dbItem -> {
        boolean stale = dbItem == null
                || (storageType == StorageType.LISTED && dbItem.getBuyerUniqueId() != null)
                || dbItem.getStorageType() != storageType;
        if (stale) {
            clusterBridge.unlockItem(item, lockToken, storageType);
            removeItem(storageType, item); // purge le fantome local
            message(this.plugin, admin, Message.ADMIN_ITEM_ALREADY_GONE);
            return CompletableFuture.completedFuture(null);
        }
        return clusterBridge.removeItem(item, storageType, StorageType.DELETED).thenApply(v -> lockToken);
    })).thenAccept(lockToken -> { if (lockToken == null) return; ... });

2) Fermer le trou du contrat pour que l'oubli devienne impossible, en fusionnant acquisition et lecture :

    record LockedItemState(boolean exists, StorageType storageType, UUID buyerUniqueId) {}
    record LockAcquisition(boolean acquired, LockToken token, long leaseExpiresAtMillis, LockedItemState state) {}

    /** Acquiert le verrou ET renvoie l'etat autoritaire lu APRES acquisition. */
    CompletableFuture<LockAcquisition> lockAndRead(Item item, UUID ownerId, StorageType expected, Duration lease);

Cela remplace les 3 aller-retours actuels (checkAvailability + lockItem + selectItem) par 2 et rend la revalidation obligatoire pour tout appelant, y compris les implementations tierces (setAuctionClusterBridge est publique, AuctionPlugin.java:120).

3) Completer les gardes SQL manquantes dans ItemRepository.createUpdateSchema pour que la base refuse elle aussi la transition :

    switch (storageType) {
        case EXPIRED   -> schema.where("storage_type", StorageType.LISTED.name());
        case PURCHASED -> { schema.where("storage_type", StorageType.LISTED.name()); schema.where("buyer_unique_id", null); }
        case DELETED   -> schema.where("storage_type", "!=", StorageType.DELETED.name());
        default        -> { }
    }
```

---

<a id="c-061"></a>

### `C-061` — unlockItem ne rend aucun resultat : le code retour du script Lua est jete et RemoveService declare un retrait reussi meme quand le verrou ne lui appartenait plus

- [ ] **Corrigé**
- **Fichier** : `API/api/cluster/AuctionClusterBridge.java:45`
- **Catégorie** : Contrat API — **Sévérité** : Haute — **Contre-expertise** : non vérifié

**Preuve dans le code**

```lua
Le contrat n'a aucun canal de retour - AuctionClusterBridge.java:37-45 (verifie) :

    /**
     * Releases a previously acquired lock, allowing other nodes to act on the item again.
     * @return future completing once the unlock has been propagated
     */
    CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType);

Le script Lua produit pourtant l'information - RedisAuctionClusterBridge.java:194-211 (verifie) :

            local currentToken = redis.call('HGET', itemKey, 'lock')
            if currentToken ~= tokenValue then
                return 0
            end

mais elle est jetee - RedisAuctionClusterBridge.java:257-258 (verifie) :

                        jedis.evalsha(unlockScriptSha, Arrays.asList(lockKeyStr, itemKeyStr), Collections.singletonList(lockToken.value()));
                        return;

Et cote appelant le succes est deduit du seul fait que le future s'est complete - RemoveService.java:259-265 (verifie) :

    private CompletableFuture<RemoveResult> unlockAndCompleteStep(RemovalContext context, AuctionClusterBridge clusterBridge, PerformanceConfiguration config) {
        return clusterBridge.unlockItem(context.item, context.token, context.storageType).orTimeout(config.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS).thenApply(v -> {
            context.result = RemoveResult.success("Item removed successfully", true);
            return context.result;
        });
    }

Meme aveuglement cote LocalAuctionClusterBridge.java:34-38 (verifie), qui ne regarde ni le jeton ni le proprietaire :

    public CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType) {
        itemLocks.remove(item.getId());
        return CompletableFuture.completedFuture(null);
    }
```

**Chronologie**

T0 - Serveur A prend le verrou de l'item 42 pour un retrait (RemoveService, StorageType.LISTED). Le bail Redis est de 30 s par defaut (redis-config.lock-ttl-seconds).

T1 - La section critique de A depasse le bail : appel economie lent, GC, aller-retour MySQL sature. La cle auction:lock:42 expire cote Redis.

T2 - Serveur B verrouille legitimement l'item 42 (LOCK_SCRIPT : plus de cle de verrou, l'etat LOCKED est considere perime l.172-175) et lance son propre traitement.

T3 - A revient et appelle unlockItem. UNLOCK_SCRIPT compare HGET(itemKey,'lock') au jeton de A : comme LockToken.of produit une valeur deterministe ("item:42"), la comparaison peut meme reussir a tort et faire sauter le verrou de B ; et si elle echoue, le script rend 0 - qui est jete l.257.

T4 - Dans les deux cas, RemoveService.unlockAndCompleteStep rend RemoveResult.success("Item removed successfully", true) : le joueur est informe d'un retrait reussi, aucun WARNING n'est journalise, et l'exploitant n'a strictement aucun signal que deux serveurs viennent de traiter le meme item en parallele - alors que c'est exactement le symptome qui precede une duplication.

**Impact** — Le seul evenement observable d'une perte de verrou (expiration de bail, collision de jetons deterministes, liberation par un autre noeud) est produit par Redis puis immediatement jete. Ni le service, ni les logs, ni l'exploitant ne peuvent detecter la course ; la duplication est diagnostiquee apres coup, sur plainte de joueur. Toute implementation tierce du contrat herite du meme trou puisque la signature ne permet pas de remonter l'echec.

**Correctif**

```java
1) Typer le retour dans l'API :

    /** @return {@code true} si ce noeud detenait encore le verrou et l'a effectivement libere. */
    CompletableFuture<Boolean> unlockItem(Item item, LockToken lockToken, StorageType storageType);

2) Cote Redis, exploiter le code retour deja produit (RedisAuctionClusterBridge.java:257) :

    Object result = jedis.evalsha(unlockScriptSha, Arrays.asList(lockKeyStr, itemKeyStr), Collections.singletonList(lockToken.value()));
    return (result instanceof Long) && ((Long) result) == 1L;

3) Cote LocalAuctionClusterBridge, valider la propriete au lieu d'un remove inconditionnel :

    public CompletableFuture<Boolean> unlockItem(Item item, LockToken token, StorageType st) {
        return CompletableFuture.completedFuture(itemLocks.remove(item.getId(), ownerOf(token)));
    }

4) Journaliser en SEVERE et compter la metrique dans RemoveService.unlockAndCompleteStep, PurchaseService (l.137) et ExpireService (whenComplete) quand le retour est false :

    return clusterBridge.unlockItem(...).thenApply(released -> {
        if (!released) logger.severe("Lock lost before unlock for item " + context.item.getId() + " (lease expired or token collision) - possible concurrent processing");
        context.result = RemoveResult.success("Item removed successfully", true);
        return context.result;
    });
```

---

## Constats de sévérité moyenne (48)

<a id="c-062"></a>

### `C-062` — /ah admin add <joueur> expired|purchased cree un item deja expire, immediatement detruit et jamais reclamable

- [ ] **Corrigé**
- **Fichier** : `command/commands/admin/CommandAuctionAdminAdd.java:112`
- **Catégorie** : Perte d’item — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
CommandAuctionAdminAdd.java:111-120 :
    private void addExpired(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        this.plugin.getStorageManager().createAuctionItem(target, price, System.currentTimeMillis(), List.of(cloned), economy)
                .thenAccept(item -> {
                    item.setStatus(ItemStatus.REMOVED);
                    item.setExpiredAt(new Date());
                    this.auctionManager.addItem(StorageType.EXPIRED, item);

Idem addPurchased l.124 : createAuctionItem(admin, price, System.currentTimeMillis(), ...).

ZItem.isExpired() : System.currentTimeMillis() >= this.expiredAt.getTime() && this.expiredAt.getTime() != 0 -> vrai des la milliseconde suivante.

ZAuctionManager.getItemIds:407-419 pousse alors l'item vers l'expiration au lieu de l'afficher :
            if (item.isExpired()) {
                expiredItems.add(item);
                continue;
            }
            ...
        if (!expiredItems.isEmpty()) {
            this.auctionExpireService.processExpiredItems(expiredItems, storageType);
        }
```

**Chronologie**

T0 - L'item en main de l'admin est supprime de son inventaire (removeItemInHand, l.77) avant l'insertion asynchrone.

T1 - addExpired insere la ligne et place l'item dans le store EXPIRED avec expiredAt = maintenant.

T2 - Le joueur cible ouvre son inventaire 'items expires'. getExpiredItems -> getItemIds(EXPIRED) : la boucle l.407-411 constate isExpired() et EXCLUT l'item de l'affichage, puis l.419 le pousse dans processExpiredItems(expiredItems, EXPIRED).

T3 - ExpireService.processExpiredItems, branche EXPIRED (l.227-236) : item.setStatus(DELETED) puis updateItems({DELETED: validItems}). La ligne DB passe en DELETED.

T4 - L'item n'existe plus. Le joueur ne l'a jamais vu ; s'il avait pu cliquer dessus, RemoveService.removeExpiredItem:127-133 l'aurait de toute facon refuse. Le meme parcours detruit l'item ajoute par addPurchased.

**Impact** — Toute restitution manuelle d'item par le staff (compensation, remboursement, restauration apres incident) via expired ou purchased detruit silencieusement l'item : il a ete retire de la main de l'admin et n'atteint jamais le joueur cible. Seul admin add listed fonctionne.

**Précision apportée par la contre-expertise**

Le constat est reel ; deux precisions de description a apporter.

(a) Le declencheur de la destruction n'est PAS specifiquement l'ouverture par le joueur cible. getItemIds balaie TOUTE la map du store (`for (Item item : items.values())`), pas seulement les items du joueur teste par le predicate. La destruction survient donc au premier acces quelconque au store EXPIRED (resp. PURCHASED) par n'importe qui : un autre joueur ouvrant son propre inventaire "items expires", un admin consultant AdminExpiredItemsButton, ou meme la simple resolution du placeholder `%zauctionhouse_expired_items%` (PlayerPlaceholders.java:21 et :23). Comme il n'existe aucune tache periodique d'expiration (l'expiration est purement paresseuse, declenchee a la lecture), l'item peut survivre en memoire tant que personne ne lit le store — mais il est deja invisible pour la cible des sa premiere lecture, et il disparait definitivement au premier passage de n'importe qui.

(b) "L'item n'existe plus" est a nuancer au niveau base : la ligne n'est pas supprimee physiquement, elle est passee en `storage_type = 'DELETED'` (ItemRepository.createUpdateSchema l.71-90). Elle reste donc recuperable par SQL manuel, mais elle est definitivement filtree cote plugin (`ItemRepository.select()` l.44 : `where storage_type != DELETED`), y compris apres redemarrage. La perte est donc irrecuperable par tout moyen in-game.

Le reste (scenario T0-T4, impact, correctif) est exact tel qu'ecrit.

**Correctif**

```java
Calculer une vraie date d'expiration a partir de la configuration, comme le font les chemins normaux (ZAuctionManager.removeListedItem:527-529 pour EXPIRED, purchaseAuctionItem:890-891 pour PURCHASED) :

    private void addExpired(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        long expiration = plugin.getConfiguration().getExpireExpiration().getExpiration(target);
        long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000L) : 0L;   // 0 = jamais
        this.plugin.getStorageManager().createAuctionItem(target, price, expiredAt, List.of(cloned), economy)
                .thenAccept(item -> {
                    item.setStatus(ItemStatus.REMOVED);
                    item.setExpiredAt(new Date(expiredAt));
                    ...
                });
    }

et symetriquement dans addPurchased avec getPurchaseExpiration().
```

---

<a id="c-063"></a>

### `C-063` — /ah admin add detruit l'item de la main de l'admin avant l'ecriture DB et ne le rend jamais en cas d'echec (aucun exceptionally sur les trois branches)

- [ ] **Corrigé**
- **Fichier** : `command/commands/admin/CommandAuctionAdminAdd.java:77`
- **Catégorie** : Perte d’item — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
CommandAuctionAdminAdd.java:74-82 - la main est videe AVANT tout appel base :
        ItemStack cloned = inHand.clone();

        removeItemInHand(admin, cloned.getAmount());

        switch (type.toLowerCase(Locale.ENGLISH)) {
            case "expired" -> this.addExpired(target, cloned, price, economy, admin);
            case "purchased" -> this.addPurchased(target, cloned, price, economy, admin);
            default -> this.addListed(target, cloned, price, economy, admin);
        }

removeItemInHand:87-95 : la condition getAmount() > how est toujours fausse puisque how = cloned.getAmount(), donc setItemInMainHand(AIR) s'applique systematiquement.

Les trois branches (l.102-108, 112-120, 124-132) ne chainent QUE des .thenAccept, sans aucun .exceptionally ni .whenComplete. A comparer avec SellService:111-130, qui remet les items dans l'inventaire du vendeur.
```

**Chronologie**

T0 - L'admin tape /ah admin add Bob listed 1000 avec un item unique en main. Ligne 77, removeItemInHand vide immediatement la main.

T1 - addListed:102 appelle createAuctionItem, un supplyAsync sur l'asyncExecutor qui fait ItemRepository.create puis AuctionItemRepository.create. Si l'un des deux echoue, Sarah leve une DatabaseException (RuntimeException) et le CompletableFuture est complete exceptionnellement.

T2 - Le thenAccept n'est pas execute et le future est abandonne sans .exceptionally et sans join : l'exception est AVALEE SILENCIEUSEMENT, sans le moindre log JUL.

T3 - L'item n'existe ni en base, ni dans l'inventaire de l'admin, ni dans celui de la cible. L'admin ne recoit meme pas de message d'erreur, ADMIN_ITEM_ADDED n'etant envoye que dans le thenAccept.

**Impact** — Perte definitive et silencieuse de l'item manipule par l'admin des que la base est indisponible ou renvoie une erreur, sans aucune trace exploitable pour le diagnostic. Le meme item est typiquement une compensation ou une restauration de valeur.

**Précision apportée par la contre-expertise**

Description exacte du defaut :

/ah admin add retire l'item de la main de l'admin AVANT toute ecriture en base et ne le restitue jamais si l'ecriture echoue.

Chemin precis :

- CommandAuctionAdminAdd.java:75-77 : `ItemStack cloned = inHand.clone(); removeItemInHand(admin, cloned.getAmount());` — la main est videe avant le switch l.79-83.

- removeItemInHand:88-96 : `how` valant toujours l'amount complet de la main, la condition l.90 est toujours fausse et la main est mise a AIR (l.93). Le stack entier part.

- Les trois branches addListed:102, addExpired:112, addPurchased:124 appellent ZStorageManager.createAuctionItem (ZStorageManager.java:140-145), un `supplyAsync` nu sur `Executors.newFixedThreadPool(4)` (ZAuctionPlugin.java:82), et ne chainent qu'un `.thenAccept`.

- En cas d'echec SQL, Sarah leve une DatabaseException NON CHECKED (Sarah/requests/InsertRequest.java:74 ; SarahException extends RuntimeException) que le `catch (SQLException)` de Repository.insertSync:141 / Repository.insert:127 n'intercepte pas. Le future se complete exceptionnellement, `thenAccept` ne s'execute pas, ADMIN_ITEM_ADDED n'est jamais envoye, et l'item n'existe ni en base, ni chez l'admin, ni chez la cible.

Corrections a apporter au constat tel qu'il est redige :

1. Le point T2 est faux sur « sans le moindre log JUL ». Sarah emet bien une ligne JUL INFO avant de throw : InsertRequest.java:73 `logger.info("Insert operation failed on table: <table> - <message SQL>")`, routee vers plugin.getLogger() par Repository.java:42 (JULogger.from). Ce qui est reellement perdu, c'est l'exception du CompletableFuture : pas de stacktrace, pas de niveau SEVERE, et aucun lien avec l'admin, la cible ou l'item concerne — donc un diagnostic post-mortem tres difficile, mais pas une absence totale de trace.

2. Deux sous-cas non mentionnes, a ajouter :

a) Echec PARTIEL : ItemRepository.create (Repository.insertSync) reussit puis AuctionItemRepository.create:30-36 echoue -> une ligne orpheline reste dans la table `items` sans aucune ligne `auction_items`, l'item est perdu ET la base est polluee d'un item sans contenu.

b) Base64ItemStack.encode (API/.../Base64ItemStack.java:42-45) retourne `null` sur IOException sans lever d'exception : l'insert reussit alors avec une colonne `itemstack` nulle, `thenAccept` s'execute, l'admin recoit ADMIN_ITEM_ADDED (faux succes) et l'item est neanmoins irrecuperable.

Correction proposee : valable. Deux precisions — le message `Message.ADMIN_ITEM_ADD_FAILED` n'existe pas (seul ADMIN_ITEM_ADDED est defini, API/.../messages/Message.java:68), il faut le creer et le repercuter dans les 4 dossiers de langue (en/fr/es/it). La seconde variante (deplacer `removeItemInHand` dans le `thenAccept`) est preferable : elle supprime la fenetre de perte au lieu de tenter une compensation, mais elle doit alors re-verifier que l'item est toujours en main (comme SellService.verifyItemsInSlots) sous peine d'ouvrir une duplication cote admin.

Severite : moyenne plutot que haute. Le chemin exige la permission ZAUCTIONHOUSE_ADMIN_ITEMS (l.28) ET une panne base au moment precis de la commande ; il ne provoque pas de duplication ni de perte monetaire, seulement la perte d'un stack detenu par un admin.

**Correctif**

```java
Chainer un exceptionally sur les trois branches :

    .exceptionally(throwable -> {
        plugin.getLogger().log(Level.SEVERE, "admin add failed", throwable);
        plugin.getScheduler().runAtEntity(admin, w -> admin.getInventory().addItem(cloned)
                .forEach((s, drop) -> admin.getWorld().dropItem(admin.getLocation(), drop)));
        auctionManager.message(admin, Message.ADMIN_ITEM_ADD_FAILED);
        return null;
    });

Ou, plus simplement, deplacer removeItemInHand(admin, cloned.getAmount()) a l'interieur du thenAccept, apres confirmation de l'ecriture en base.
```

---

<a id="c-064"></a>

### `C-064` — /ah admin add, /ah admin generate et la migration V3 n'annoncent jamais leurs items au cluster : items invisibles ailleurs et cles Redis sans TTL

- [ ] **Corrigé**
- **Fichier** : `command/commands/admin/CommandAuctionAdminAdd.java:102`
- **Catégorie** : Cluster — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
CommandAuctionAdminAdd.java:102-108 - aucun appel au clusterBridge :
        this.plugin.getStorageManager().createAuctionItem(target, price, expiredAt, List.of(cloned), economy)
                .thenAccept(item -> {
                    this.auctionManager.addItem(StorageType.LISTED, item);
                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
                    this.auctionManager.updateListedItems(item, true, target);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, ...);
                });

grep notifyItemListed sur src/ et API/ ne retourne qu'un seul appelant metier : SellService.java:391. Ni addListed/addExpired/addPurchased, ni CommandAuctionAdminGenerate.java:208-212 (itemRepository.create + auctionManager.addItem), ni V3MigrationService ne l'appellent.
```

**Chronologie**

T0 - Serveur A : un admin fait /ah admin add Bob 1000 listed. La ligne items est creee LISTED dans la base partagee, l'item est ajoute au store memoire de A.

T1 - Aucun HSET auction:item:<id>, aucun PUBLISH. Les serveurs B et C n'apprennent rien et ne connaitront cet item qu'a leur prochain demarrage complet. Bob, connecte sur B, ne le voit pas dans 'mes ventes' et il n'est pas compte dans sa limite (SellService:256).

T2 - La cle Redis auction:item:<id> n'existe pas non plus : RedisAuctionClusterBridge.notifyItemListed est le SEUL endroit qui fait hset(key, FIELD_STATE, STATE_AVAILABLE) puis expire(key, itemListedTtl). Depuis B, checkAvailability lit HGET state = nil et repond disponible ; LOCK_SCRIPT trouve state nil et accorde le verrou. Quand le premier acheteur verrouille l'item, LOCK_SCRIPT cree le hash par HSET, et HSET ne pose aucun TTL : la cle devient permanente.

T3 - Apres /ah admin generate 100000 ou la migration d'un gros HDV V3, ce sont jusqu'a 100 000 cles Redis qui n'expireront jamais.

T4 - C'est aussi le mecanisme qui pousse l'admin a relancer la migration depuis le serveur B ('elle n'a rien fait ici'), avec le risque de doublonner l'import complet.

**Impact** — Tout item injecte par une commande d'administration ou par la migration est invisible et non gouverne par le protocole cluster sur les autres serveurs du reseau jusqu'a leur redemarrage, et laisse une cle Redis sans TTL. C'est la cause operationnelle directe des relances de migration.

**Précision apportée par la contre-expertise**

Le constat est reel mais deux points sont mal decrits et un troisieme est sous-estime.

1) T3 EXAGERE - "jusqu'a 100 000 cles Redis qui n'expireront jamais" n'est pas soutenu par le code. Les cles auction:item:<id> ne sont PAS creees par /ah admin generate : elles n'apparaissent que paresseusement, quand quelqu'un pose un verrou (LOCK_SCRIPT:64 HSET). Un item genere que personne ne touche ne cree aucune cle Redis. De plus, deux chemins reposent un TTL apres coup : notifyItemBought (RedisAuctionClusterBridge.java:295-298, hset SOLD + expire(itemStateTtl)) et removeItem (355-358, hset REMOVED/DELETED + expire(itemStateTtl)) - ce dernier etant appele par ExpireService:293 a l'expiration. La population reellement fuitee se limite donc aux items verrouilles puis relaches sans achat ni suppression (achat annule, fonds insuffisants, confirmation abandonnee) : UNLOCK_SCRIPT:88 remet state='AVAILABLE' et supprime la cle de lock, mais laisse le hash auction:item:<id> sans TTL. Le defaut est reel et la correction proposee (faire poser un TTL par LOCK_SCRIPT a la creation du hash) reste juste, mais l'ordre de grandeur annonce ne l'est pas.

2) LA MIGRATION EST PIRE QUE DECRIT, pas seulement pour B et C. V3MigrationService n'appelle ni le bridge NI auctionManager.addItem : il n'ecrit qu'en base (createItem:256, createAuctionItems:282, insertAuctionItem:299). Les items migres sont donc invisibles y compris sur le serveur qui a lance la migration, jusqu'a son propre redemarrage. Cela renforce fortement T4 : l'admin voit "rien ne s'est passe" partout, pas seulement ailleurs. Et j'ai confirme qu'aucune garde d'idempotence n'existe (CommandAuctionAdminMigrate n'exige que le litteral "confirm", V3MigrationService ne verifie aucun marqueur de migration deja effectuee) : une relance re-insere l'integralite de l'import. C'est le seul vrai vecteur de duplication de ce constat.

3) PAS DE VECTEUR DE DUPE VIA LE PROTOCOLE CLUSTER. Le scenario T2 decrit correctement le comportement (checkAvailability:162-168 renvoie true quand HGET state est nil, LOCK_SCRIPT accorde le verrou) mais ce fail-open est intentionnel et documente dans le code, et il ne cree pas de duplication : B et C n'ayant pas l'item dans leur store memoire, ils ne peuvent ni l'afficher, ni le vendre, ni le remettre au joueur ; au redemarrage ils le chargent depuis la base partagee et le protocole de verrou normal s'applique. L'impact reel est donc l'incoherence fonctionnelle multi-serveurs (item invisible, non compte dans la limite SellService:256, non annonce), plus une cle Redis sans TTL, plus le piege operationnel de la relance de migration - d'ou severite moyenne et non haute.

La correction proposee reste globalement valable. Precision utile : pour addExpired/addPurchased le chainage doit se faire APRES le updateItem existant (CommandAuctionAdminAdd:117 et 129), sinon les autres noeuds rechargeraient l'item avec un storage_type encore LISTED. Pour V3MigrationService, il faut en priorite ajouter un rechargement local (storageManager.loadItems ou equivalent) et une garde d'idempotence, avant meme la question du cluster.

**Correctif**

Dans CommandAuctionAdminAdd.addListed, chainer l'annonce :
    .thenCompose(item -> plugin.getAuctionClusterBridge().notifyItemListed(item).thenApply(v -> item))
    .thenAccept(item -> { ... })

Pour addExpired/addPurchased, appeler notifyItemListed puis clusterBridge.removeItem(item, StorageType.LISTED, StorageType.EXPIRED / PURCHASED) afin que les autres noeuds chargent l'item dans le bon store. Faire de meme pour chaque item cree par CommandAuctionAdminGenerate et par V3MigrationService (ou, pour la migration, imposer un redemarrage coordonne documente). En complement, faire poser un TTL par LOCK_SCRIPT quand il cree le hash auction:item:<id>.

---

<a id="c-065"></a>

### `C-065` — /ah admin cache clear efface ITEM_SHOW et gele definitivement l'item en IS_*_CONFIRM sur tout le cluster

- [ ] **Corrigé**
- **Fichier** : `command/commands/admin/cache/CommandAuctionAdminCacheClear.java:55`
- **Catégorie** : Perte d’item — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
CommandAuctionAdminCacheClear.java:55-58 (et le meme appel l.85 pour un joueur unique) :
            if (keyName == null || keyName.equalsIgnoreCase("all")) {
                for (Player target : onlinePlayers) {
                    this.auctionManager.clearPlayerCache(target, PlayerCacheKey.values());
                }

PlayerCacheKey.values() inclut ITEM_SHOW, dont depend l'unique chemin de restauration (ConfirmHelper.java:36-38) :
        var cache = manager.getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) return;
```

**Chronologie**

T0 - Serveur A : le joueur P clique l'item 42. ListedItemsButton:229-234 diffuse notifyItemStatusChange(AVAILABLE -> IS_PURCHASE_CONFIRM), pose le statut, masque l'item et ouvre l'inventaire de confirmation. Le cache de P contient ITEM_SHOW = item 42.

T1 - Serveurs B et C : ItemStatusListener:54-59 applique le statut et masque l'item 42 de leurs HDV.

T2 - Serveur A : un admin, voyant un affichage incoherent (precisement le symptome que cette commande est censee corriger), tape /ah admin cache clear * all. ITEM_SHOW est efface du cache de P.

T3 - P ferme son inventaire. ConfirmHelper.onInventoryClose:37-38 lit ITEM_SHOW, obtient null et fait return. Le statut n'est JAMAIS remis a AVAILABLE, et aucun notifyItemStatusChange de retour n'est emis vers B et C.

T4 - ItemStatus n'est jamais persiste (aucune colonne de statut dans %prefix%items), n'a aucun TTL et aucune tache de nettoyage. L'item 42 reste IS_PURCHASE_CONFIRM en memoire sur A, B et C : absent de tous les HDV, et PurchaseService:68 le refuse a quiconque. Seul un redemarrage des trois serveurs le libere.

**Impact** — La commande de diagnostic censee reparer un cache incoherent gele definitivement, sur tout le reseau, chaque item dont un joueur avait une confirmation ouverte. L'item est perdu pour son vendeur jusqu'a un redemarrage global de tous les noeuds.

**Précision apportée par la contre-expertise**

Titre exact : "/ah admin cache clear efface ITEM_SHOW et gele l'item en IS_*_CONFIRM sur tout le cluster jusqu'a l'expiration de l'annonce".

Categorie exacte : ce n'est PAS une "perte-item" mais un gel temporaire de disponibilite (deni de service sur l'item) — aucun item ni argent n'est perdu ni duplique.

Description corrigee : `/ah admin cache clear <joueur|*> [all]` (CommandAuctionAdminCacheClear.java:57 et :84) purge PlayerCacheKey.values(), qui inclut ITEM_SHOW (PlayerCacheKey.java:39). Si un joueur a un inventaire de confirmation ouvert a ce moment, l'unique chemin de restauration (ConfirmHelper.java:37-38 pour onInventoryClose, :65-66 pour onBackClick) sort par `if (item == null) return;` : item.setStatus(AVAILABLE) (l.47) et notifyItemStatusChange (l.48) ne s'executent jamais. L'item reste en IS_PURCHASE_CONFIRM (ou IS_REMOVE_CONFIRM) en memoire sur tous les noeuds, puisque les autres serveurs ont deja applique le statut via ItemStatusListener.java:54-59 et ne recevront jamais le message de retour. Le statut n'etant ni persiste (aucune colonne dans les items, cf. ItemLoaderUtils.java:43-48) ni soumis a un TTL ni nettoye par une tache, l'item disparait de la liste principale sur tous les serveurs (SortedItemsCache.java:305) et son vendeur ne peut plus le retirer (RemoveService.java:96 rejette tout statut != AVAILABLE).

Duree du gel — c'est le point ou le constat original se trompe : le gel n'est PAS permanent jusqu'a redemarrage. Le balayage d'expiration paresseux de ZAuctionManager.java:407-419 (getItemIds) parcourt tout le store LISTED en ne testant que item.isExpired(), sans jamais consulter le statut, et ExpireService.processExpiredItems:118-127 n'ecarte que les items DELETED. En cluster, expireListedItemClustered aboutit egalement car RedisAuctionClusterBridge.java:158-177 (checkAvailability) n'examine que le verrou Redis, jamais ItemStatus, et aucun verrou n'est detenu pendant la phase de confirmation. Des que l'annonce atteint sa date d'expiration, le premier appel de getItemIds sur LISTED (par ex. ZAuctionManager:314, quand le vendeur ouvre son onglet de ventes) fait passer l'item en EXPIRED, ou le vendeur le recupere. Le gel est donc borne par la duree restante de l'annonce (heures/jours selon auction-expiration), pas par un redemarrage.

Correction de la preuve secondaire : PurchaseService.java:68 (`if (item.getStatus() != ItemStatus.IS_PURCHASE_CONFIRM)`) ne "refuse pas l'item a quiconque" — cette garde rejette au contraire les items qui ne sont PAS en IS_PURCHASE_CONFIRM, et un item gele la franchit. Ce qui empeche reellement l'achat, c'est le filtre d'affichage SortedItemsCache.java:305 (l'item n'est plus dans la liste, donc plus cliquable) et ConfirmHelper.java:88-91 (ITEM_SHOW null -> retour au menu principal). De meme, l'item n'est pas "absent de tous les HDV" : le vendeur continue de le voir dans son onglet selling avec le lore "beingPurchased" (ZAuctionManager:314 ne filtre que DELETED, SellingItemsButton.java:41-46), mais chaque clic echoue.

Le correctif propose reste valable et pertinent, en particulier le point 1 (exclure ITEM_SHOW et les cles d'operation en vol de la purge "all") et le point 3 (tache de remise a AVAILABLE des statuts IS_*_CONFIRM au-dela de N secondes), qui corrigerait au passage le meme gel provoque par une deconnexion brutale : PlayerListener.java:42-46 supprime tout le cache du joueur sur PlayerQuitEvent sans restaurer le statut.

**Correctif**

1) Exclure les cles d'operation en vol de la purge 'all' : definir un Set<PlayerCacheKey> PROTECTED_KEYS = Set.of(ITEM_SHOW, PURCHASE_ITEM, SELL_ITEMS, SELL_PRICE, SELL_ECONOMY) et filtrer PlayerCacheKey.values() avant les appels des lignes 57 et 85.
2) Rendre la restauration independante du cache : memoriser l'Item dans l'instance du bouton de confirmation, et restaurer le statut sur PlayerQuitEvent.
3) Ajouter la tache periodique de remise a AVAILABLE des statuts IS_*_CONFIRM au-dela de N secondes.

---

<a id="c-066"></a>

### `C-066` — Aucun index en dehors des cles primaires et etrangeres, et aucune purge des lignes DELETED : le chargement au boot devient impossible

- [ ] **Corrigé**
- **Fichier** : `storage/migrations/CreateItemMigration.java:11`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```sql
CreateItemMigration.java:11-22 (verifie, aucun index) :
        create(Tables.ITEMS, table -> {
            table.autoIncrement("id");
            table.string("item_type", 255);
            table.string("seller_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);
            table.string("buyer_unique_id", 36).nullable().foreignKey(Tables.PLAYERS, "unique_id", true);
            table.decimal("price", 65, 2);
            table.string("economy_name", 255);
            table.enumType("storage_type", StorageType.class);
            table.string("server_name", 255);
            table.timestamp("expired_at");
            table.timestamps();
        });

Verifie : `grep -rn "index(" src/main/java/fr/maxlego08/zauctionhouse/storage/` ne retourne AUCUNE occurrence, alors que Sarah expose Migration.index(String table, String column).

ItemRepository.java:43-45 (verifie) - la requete de boot filtre sur une colonne non indexee :
    public List<ItemDTO> select() {
        return select(ItemDTO.class, schema -> schema.where("storage_type", "!=", StorageType.DELETED.name()));
    }

AuctionLoader.loadItems (verifie) l'appelle au demarrage, puis ItemLoaderUtils.java:59 :
        var auctionItems = storageManager.with(AuctionItemRepository.class).select(getIDS(items, ItemType.AUCTION));
ItemRepository.java:97-99 : `select(List<String> ids)` -> `schema.whereIn("id", ids)`, soit UN placeholder par id.

Aucun DELETE FROM items n'existe dans le projet : DELETED est un tombstone logique definitif (createUpdateSchema ecrit storage_type='DELETED', jamais de suppression physique).
```

**Chronologie**

T0 : cluster de 3 serveurs partageant une base MySQL, en production depuis un an. La table %prefix%items contient 100 000 lignes dont 80 000 tombstones DELETED que rien ne purge.

T1 : le serveur A redemarre. AuctionLoader.loadItems execute SELECT * FROM items WHERE storage_type != 'DELETED'. storage_type n'est ni indexe ni couvert par une FK : balayage complet de 100 000 lignes, a chaque demarrage de chaque serveur.

T2 : ItemLoaderUtils:59 execute ensuite AuctionItemRepository.select(getIDS(items, AUCTION)), qui genere `item_id IN (?,?,...)` avec un placeholder par item vivant. A 20 000 items en vente, c'est une requete de 20 000 parametres ; la limite protocolaire d'un prepared statement MySQL est de 65535 parametres, et en mode client-side c'est max_allowed_packet qui saute.

T3 : si la requete echoue, loadItems remonte une exception : le serveur A demarre avec un store memoire vide ou partiel alors que les serveurs B et C ont les items en memoire. Les items absents de A ne sont plus jamais rafraichis chez lui, et un joueur de A ne voit pas ce qu'un joueur de B voit.

T4 : en fonctionnement, ClaimService.getPendingTransactions filtre sur transactions.status et LogRepository.selectUnreadSales sur log_type + readed_at IS NULL, aucune de ces colonnes n'etant indexee : chaque connexion de joueur declenche des balayages complets sur la base partagee.

**Impact** — Temps de demarrage qui croit lineairement puis quadratiquement avec l'historique, et echec pur et simple du chargement au-dela de quelques dizaines de milliers de ventes actives. Comme les tombstones ne sont jamais purges, la degradation est irreversible sans intervention SQL manuelle, et un chargement partiel desynchronise durablement un noeud du cluster.

**Précision apportée par la contre-expertise**

Le constat est reel mais son scenario contient deux erreurs factuelles a corriger, et sa severite est surestimee.

ERREUR 1 - T3 est faux sur le mecanisme, et la realite est PIRE. Le constat dit : "si la requete echoue, loadItems remonte une exception". Non : API/src/main/java/fr/maxlego08/zauctionhouse/api/storage/Repository.java:172-181 avale tout :

protected <T> List<T> select(Class<T> clazz, Consumer<Schema> consumer) {

Schema schema = SchemaBuilder.select(getTableName());

consumer.accept(schema);

try {

return schema.executeSelect(clazz, this.connection, this.logger);

} catch (Exception exception) {

exception.printStackTrace();

}

return new ArrayList<>();

}

`catch (Exception)` + `printStackTrace()` + liste vide. Consequences reelles :

- si ItemRepository.select() echoue, AuctionLoader.java:47-53 logge "Loaded 0 items successfully" et le serveur demarre avec un store vide, sans aucune exception ;

- si c'est le IN de ItemLoaderUtils.java:59 qui echoue, `auctionItems` est vide, et ItemLoaderUtils.java:40 `currentAuctionItems.stream().map(...)` produit une liste d'ItemStack VIDE pour chaque item. Tous les items se chargent en memoire avec zero contenu, ce qui est un etat plus dangereux qu'un store vide dans un audit anti-dup (item present et achetable, contenu absent).

De plus les commandes sont enregistrees en ZAuctionPlugin.java:156, AVANT loadItems en :160 : le plugin reste pleinement utilisable dans cet etat degrade. Le point 4) du correctif reste donc valide mais doit etre applique dans Repository.select (relancer l'exception ou remonter un flag d'echec), pas dans loadItems qui ne verra jamais l'exception.

ERREUR 2 - T4 est faux sur MySQL. "ClaimService.getPendingTransactions filtre sur transactions.status et LogRepository.selectUnreadSales sur log_type + readed_at IS NULL, aucune de ces colonnes n'etant indexee : chaque connexion de joueur declenche des balayages complets". En realite ces deux requetes ont un predicat de tete porte par une FK :

- ClaimService.java:117-121 -> TransactionRepository.java:38-40 `schema.where("player_unique_id", ...).where("status", ...)`, et CreateTransactionsMigration.java:13 `table.string("player_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true)` ;

- LogRepository.java:74-76 `schema.where("target_unique_id", ...).where("log_type", ...).whereNull("readed_at")`, et CreateLogsMigration.java:15 `table.string("target_unique_id", 36).nullable().foreignKey(...)`.

Sarah emet bien la contrainte (SchemaBuilder.java:385 `FOREIGN KEY (%s) REFERENCES %s(...)`), et InnoDB cree automatiquement un index sur la colonne enfant d'une FK. Sur le cluster MySQL decrit, ce sont des range scans sur les lignes d'un seul joueur, pas des full scans. T4 ne tient que sur SQLite (qui n'indexe pas les colonnes de FK) — c'est-a-dire le mode mono-serveur par defaut, hors du scenario cluster du constat. Corollaire : dans le correctif propose, `index(Tables.ITEMS, "seller_unique_id")` est redondant sur MySQL.

ERREUR 3 - le seuil de rupture est surestime. `getIDS(items, ItemType.AUCTION)` (ItemLoaderUtils.java:35-37) ne filtre que sur `item_type == AUCTION`, donc il inclut LISTED + EXPIRED + PURCHASED, pas seulement "les items en vente" : la taille du IN est plus grande que ce que dit le scenario. Mais la limite de 65535 parametres ne s'applique qu'avec `useServerPrepStmts=true` ; Connector/J est en client-side par defaut, ou la contrainte est max_allowed_packet, et 20 000 placeholders ne pesent que quelques dizaines de Ko. L'"echec pur et simple au-dela de quelques dizaines de milliers de ventes actives" n'est donc pas la realite la plus probable : le mur dur est plutot ~65k parametres en server-side.

CE QUI RESTE, ET QUI EST VRAI : zero index applicatif, full scan sur `storage_type != 'DELETED'` a chaque boot de chaque noeud sur une table que rien ne purge, IN non borne, et echec silencieux en cas de depassement. Le point 2) (chunking par 1000) et le point 3) (commande de purge avec retention) du correctif sont les deux mesures reellement necessaires ; le point 1) doit se limiter a `items.storage_type`, `items.expired_at`, `logs.log_type`, `logs.readed_at` (et, seulement si SQLite est supporte en production, aux colonnes de FK).

Severite ramenee de haute a moyenne : degradation progressive et reversible par une simple migration d'index, pas de perte de donnees ni de vecteur de duplication direct — le seul lien avec l'audit anti-dup est l'etat degrade silencieux de T3 corrige ci-dessus.

**Correctif**

```sql
1) Nouvelle migration d'index enregistree dans ZStorageManager apres les migrations existantes :
public class CreateIndexesMigration extends Migration {
    @Override public void up() {
        index(Tables.ITEMS, "storage_type");
        index(Tables.ITEMS, "expired_at");
        index(Tables.ITEMS, "seller_unique_id");
        index(Tables.TRANSACTIONS, "status");
        index(Tables.LOGS, "log_type");
        index(Tables.LOGS, "readed_at");
    }
}

2) Paginer le IN de ItemLoaderUtils:59 : decouper getIDS(...) en tranches de 1000 ids et concatener les resultats, pour ne jamais depasser la limite de parametres.

3) Ajouter une commande admin de purge (retention configurable) :
DELETE FROM auction_items WHERE item_id IN (SELECT id FROM items WHERE storage_type='DELETED' AND updated_at < ?);
DELETE FROM items WHERE storage_type='DELETED' AND updated_at < ?;

4) Faire echouer bruyamment loadItems (log severe + refus d'installer le bridge cluster) plutot que de laisser un noeud demarrer avec un store partiel.
```

---

<a id="c-067"></a>

### `C-067` — Base64ItemStack.encode peut rendre null (contrat javadoc) ou lever une NPE, et cette valeur part directement dans une colonne NOT NULL apres que la ligne items a deja ete commitee

- [ ] **Corrigé**
- **Fichier** : `storage/repository/repositories/AuctionItemRepository.java:34`
- **Catégorie** : Perte d’item — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```sql
AuctionItemRepository.java:30-37 - aucune verification de la valeur encodee :
    public AuctionItem create(UUID sellerUniqueId, String sellerName, int itemId, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        for (ItemStack itemStack : itemStacks) {
            insert(schema -> {
                schema.object("item_id", itemId);
                schema.string("itemstack", Base64ItemStack.encode(itemStack));
            });
        }

Base64ItemStack.java:22-27 - contrat explicite : peut rendre null :
     * @return the Base64-encoded string, or {@code null} if encoding fails

ItemStackUtils.java:23-37 - voie < 1.20.5 : NPE garantie si le bloc try echoue, le contrat javadoc est meme viole :
        ByteArrayOutputStream localByteArrayOutputStream = null;
        try {
            ...
        } catch (Exception localException) {
            localException.printStackTrace();
        }
        return Base64.encode(localByteArrayOutputStream.toByteArray());

Et la colonne est NOT NULL (CreateAuctionItemMigration : table.longText("itemstack"), Sarah ColumnDefinition nullable=false par defaut).
```

**Chronologie**

T0 - Serveur A (1.20.4, voie NMS) : un joueur vend un item dont la serialisation NMS echoue - classe NMS introuvable apres une mise a jour de Paper, methode obfusquee renommee, ou item d'un plugin tiers (ItemsAdder/Nexo/MMOItems, tous en softdepend) dont le NBT fait echouer la reflexion.

T1 - SellService a deja retire les items de l'inventaire du joueur.

T2 - ZStorageManager:142 : INSERT INTO items ... storage_type='LISTED' COMMITE, id 5120.

T3 - ZStorageManager:143 -> AuctionItemRepository:34 -> ItemStackUtils.serializeItemStack : le bloc try echoue, l'exception est simplement imprimee, puis ligne 37 Base64.encode(localByteArrayOutputStream.toByteArray()) dereference une variable restee NULL -> NullPointerException. (Sur la voie moderne, encode rend null et schema.string("itemstack", null) est refuse par la contrainte NOT NULL -> DatabaseException.)

T4 - Dans les deux cas c'est une RuntimeException : elle traverse Repository.insert (qui ne capture que SQLException) et complete le future en echec.

T5 - SellService.exceptionally rend au joueur la totalite de ses ItemStacks.

T6 - La ligne items #5120 reste LISTED avec ZERO enfant : au prochain demarrage de n'importe quel noeud, elle est chargee comme un lot vide achetable, et l'argent d'un acheteur part sans contrepartie tandis que le joueur a deja tout recupere.

**Impact** — Chemin de declenchement supplementaire - et purement deterministe pour un type d'item donne - de l'annonce fantome sans contenu : il suffit qu'un item ne soit pas serialisable pour que sa mise en vente laisse une ligne LISTED vide, achetable et non livrable.

**Précision apportée par la contre-expertise**

Le constat est exact et toutes ses citations sont a jour. Trois precisions a apporter :

(a) VARIANTE SILENCIEUSE MANQUANTE, plus grave que celle decrite. Sur la voie legacy, la NPE de ItemStackUtils.java:37 n'arrive que si l'echec survient AVANT la ligne 32 (`localByteArrayOutputStream = new ByteArrayOutputStream();`). Si l'exception est levee a la ligne 33 (l'appel `NBTCompressedStreamTools.a(NBTTagCompound, OutputStream)` — cas typique d'une methode obfusquee renommee, alors que les classes sont trouvees), la variable est NON nulle et le catch renvoie `Base64.encode(new byte[0])` : aucune exception, l'INSERT REUSSIT avec une chaine vide. Le future se complete en SUCCES, `postSell` s'execute, le joueur est facture/annonce et l'item est publie dans le cluster... alors qu'au rechargement `Base64ItemStack.decode("")` rendra null (ItemLoaderUtils.java:40 mappe sans filtrer les null). C'est le meme resultat metier (annonce non livrable) mais SANS aucune trace d'echec et SANS restitution des items au joueur — donc perte d'item reelle cote vendeur. Le correctif 2) propose (renvoyer null dans le catch) traite bien ce cas, mais le scenario du constat ne le mentionne pas.

(b) REACHABILITE A NUANCER. La voie NPE (legacy) n'est atteignable que sur 1.20-1.20.4 (`isAttributItemStack()` = version >= 1205, borne basse imposee par `api-version: '1.20'` de plugin.yml:4). Sur 1.20.5+ — le cas normal, le projet compilant contre paper-api 1.21.10 — c'est uniquement la voie `return null` + violation NOT NULL -> DatabaseException qui joue. Cette derniere reste pleinement valide, y compris pour toute RuntimeException non-IOException levee par `BukkitObjectOutputStream.writeObject` (Base64ItemStack.java:42 ne capture que `IOException`), qui remonte alors directement sans meme passer par la base.

(c) PERTE POSSIBLE DANS LE RATTRAPAGE. Le T5 n'est pas totalement sur : SellService.java:120 utilise `player.getInventory().addItem(itemStack)` en ignorant la Map de restes retournee. Si l'inventaire s'est rempli entre le retrait (ligne 105) et l'echec asynchrone, une partie des items est perdue silencieusement en plus de l'annonce fantome.

Severite ramenee de haute a moyenne : le defaut de code est certain et les trois correctifs proposes sont pertinents (le 3), validation dans validateItems avant removeItemsFromSlots, etant le bon point d'ancrage), mais le declencheur reste conditionnel a un environnement degrade (item non serialisable / mapping NMS casse) et non atteignable sur un serveur sain en 1.21 avec des items standards ; le prejudice final (achat d'un lot vide) exige de surcroit un redemarrage de noeud, `postSell` n'etant jamais atteint sur la voie exception.

**Correctif**

```java
1) Encoder AVANT toute ecriture et refuser globalement, en placant l'encodage dans ZStorageManager.createAuctionItem avant l'INSERT dans items :
    List<String> encoded = itemStacks.stream().map(Base64ItemStack::encode).toList();
    if (encoded.stream().anyMatch(Objects::isNull)) throw new IllegalStateException("unserializable itemstack");
2) Corriger ItemStackUtils.serializeItemStack : renvoyer null explicitement dans le catch au lieu de dereferencer une variable nulle.
3) Valider la serialisabilite des SellService.validateItems (avant removeItemsFromSlots), afin qu'un item non serialisable soit refuse au joueur au lieu de lui etre retire puis rendu.
```

---

<a id="c-068"></a>

### `C-068` — ConfirmHelper.onBackClick force le statut AVAILABLE sans aucune garde, meme en pleine section critique d'achat

- [ ] **Corrigé**
- **Fichier** : `buttons/confirm/ConfirmHelper.java:72`
- **Catégorie** : Race condition — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// ConfirmHelper.java:41-56 -- onInventoryClose VERIFIE que le statut n'a pas change
if (item.getStatus() == this.previous) {
    if (processIfExpired(player, item)) return;
    item.setStatus(this.next);
    this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, this.previous, this.next)...

// ConfirmHelper.java:70-77 -- onBackClick ne verifie RIEN
if (processIfExpired(player, item)) return;

item.setStatus(this.next);
this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, this.previous, this.next)
    .exceptionally(throwable -> { ... });

// ConfirmPurchaseButton.java:17 -- pour l'achat, next == AVAILABLE
super((AuctionPlugin) plugin, ItemStatus.IS_PURCHASE_CONFIRM, ItemStatus.AVAILABLE);
```

**Chronologie**

T0 -- Serveur A : le joueur clique sur confirmer l'achat de l'item 42. ConfirmPurchaseButton.onPostClick lance purchaseItem, qui part sur deux allers-retours Redis (checkAvailability puis lockItem) avant de poser IS_BEING_PURCHASED (PurchaseService.java:107).

T1 -- Le meme joueur clique aussitot sur le bouton retour du menu de confirmation. onBackClick n'a aucune garde : il execute item.setStatus(AVAILABLE) et diffuse notifyItemStatusChange(IS_PURCHASE_CONFIRM -> AVAILABLE) a tout le cluster.

T2 -- Serveur B : ItemStatusListener applique AVAILABLE (ItemStatusListener.java:54) et, branche AVAILABLE (lignes 60-62), fait updateListedItems(item, true, null) : l'item est reaffiche comme disponible dans les inventaires ouverts alors que A est en train de le vendre.

T3 -- Sur A, la chaine d'achat continue, debite l'acheteur et supprime l'item. Entre-temps le statut memoire est AVAILABLE : PurchaseService.exceptionally (ligne 176) ne restaurera plus rien en cas d'echec, et RemoveService.removeListedItem accepte desormais AVAILABLE (RemoveService.java:55) -- le vendeur peut lancer un retrait sur l'item en cours d'achat, arbitre seulement par le verrou Redis dont le bail de 30 s peut avoir expire.

T4 -- Meme sequence via onInventoryClose pour ConfirmRemoveListedButton, sauf que celle-la est protegee par la garde ligne 41 : la protection existe donc bien dans le fichier, elle a juste ete oubliee dans onBackClick.

**Impact** — Un joueur peut, en deux clics, faire rediffuser "AVAILABLE" a tout le cluster pour un item dont l'achat est en cours, remettant l'item dans les inventaires des autres serveurs et rouvrant les chemins de retrait local.

**Précision apportée par la contre-expertise**

Le defaut est reel mais le scenario decrit vise la MAUVAISE fenetre temporelle, et la correction proposee ne corrige pas la fenetre qu'elle nomme.

A) LE SCENARIO T0->T1 EST FAUX, ET LE CORRECTIF PROPOSE NE LE COUVRE PAS.

Le constat dit : "T1 -- le joueur clique aussitot sur retour" pendant que purchaseItem fait ses deux allers-retours Redis, donc AVANT PurchaseService.java:107. Or a cet instant precis `item.getStatus()` vaut encore IS_PURCHASE_CONFIRM, c'est-a-dire exactement `this.previous`. La garde proposee `if (item.getStatus() != this.previous) return;` PASSE, et AVAILABLE est diffuse quand meme. Le correctif propose est donc inoperant sur le scenario qu'il pretend corriger.

De plus, dans cette fenetre-la le degat est transitoire : la chaine atteint ensuite PurchaseService.java:107-108, repositionne IS_BEING_PURCHASED et rediffuse (`notifyItemStatusChange(item, previousStatusHolder.get(), IS_BEING_PURCHASED)`), ce qui ecrase l'AVAILABLE sur tout le cluster en un aller-retour Redis.

B) LA VRAIE FENETRE NOCIVE EST L'INVERSE.

Le probleme exploitable est le clic retour APRES PurchaseService.java:107, c'est-a-dire une fois le verrou Redis acquis et le statut passe a IS_BEING_PURCHASED, et jusqu'a la fin de l'achat (relecture DB l.118-125, `auctionEconomy.has`, `withdraw` ZAuctionManager.java:802, `deposit` l.823). La, onBackClick ecrase IS_BEING_PURCHASED par AVAILABLE et le diffuse, et PLUS RIEN ne le corrige. C'est bien cette fenetre, et elle seule, que la garde `item.getStatus() != this.previous` ferme. La correction proposee est donc la bonne, mais pour une raison differente de celle annoncee -- l'enonce du scenario doit etre reecrit.

C) DETAIL AGGRAVANT NON RELEVE PAR LE CONSTAT.

onBackClick passe `this.previous` comme oldStatus alors que le statut reel est IS_BEING_PURCHASED : le message ItemStatusMessage diffuse un oldStatus mensonger. C'est sans effet aujourd'hui puisque ItemStatusListener.java:54 n'exploite que newStatus, mais cela interdit toute garde CAS cote recepteur.

D) LA LECTURE DE onInventoryClose (point T4) EST APPROXIMATIVE.

Le constat presente la garde ligne 41 comme "la protection oubliee dans onBackClick". Son role premier est autre : sur un clic retour, zMenu declenche AUSSI onInventoryClose un tick plus tard -- VInventoryManager.java:196-197 `oldInventoryEngine.onInventorySwitch(event, player, newInventoryEngine)` -> VInventory.java:235-236 `onInventorySwitch -> onPreClose` -> l.229 `onClose` -> InventoryDefault.java:187 `this.buttons.forEach(button -> button.onInventoryClose(player, this));`. La garde l.41 est donc d'abord ce qui empeche la transition d'etre appliquee et diffusee DEUX FOIS apres onBackClick. Elle sert accessoirement de garde de concurrence.

Corollaire : dire que ConfirmRemoveListedButton "est protegee" est partiellement faux -- onInventoryClose presente exactement le meme trou dans la fenetre anterieure a RemoveService.executeRemoval / changeStatusAndNotifyStep (RemoveService.java:239 `context.item.setStatus(context.targetStatus)`), ou le statut vaut encore IS_REMOVE_CONFIRM.

E) IMPACT SUREVALUE -> SEVERITE RAMENEE DE HAUTE A MOYENNE.

- Le verrou Redis reste l'arbitre reel et n'est pas contourne : PurchaseService.java:82-104 et RemoveService.java:206-236 (checkAvailabilityStep puis acquireLockStep, `LockToken.noop()` -> echec) prennent tous deux le verrou. Un retrait vendeur ou un second achat pendant que A detient le verrou echoue en LOCK_FAILED / ITEM_NOT_AVAILABLE. Il n'y a donc PAS de duplication directe en deux clics.

- Defense supplementaire : PurchaseService.java:118-125 relit l'item en base SOUS VERROU et abandonne si `dbItem == null || dbItem.getBuyerUniqueId() != null`.

- Attenuation cote UI : zMenu impose un cooldown de clic -- VInventoryManager.java:172-177, defauts Configuration.java:228/236 (`enableCooldownClick = true`, `cooldownClickMilliseconds = 100`). 100 ms ralentit mais n'empeche pas, la chaine d'achat depassant regulierement ce delai (2 allers-retours Redis + SELECT MySQL + appels economie).

- La sous-affirmation "PurchaseService.exceptionally (l.176) ne restaurera plus rien" est exacte mais son effet est INVERSE de ce qui est suggere : la restauration remettrait `previousStatusHolder.get()` == IS_PURCHASE_CONFIRM (l.76), un statut transitoire d'UI qui rendrait l'item non achetable ; ne rien restaurer laisse AVAILABLE, etat de fait plus sain.

CLASSIFICATION EXACTE : desynchronisation d'etat inter-serveurs + affaiblissement d'une couche de defense (l'item est reaffiche comme AVAILABLE sur tout le cluster alors qu'il est en cours d'achat, et le rearmement de statut en cas d'echec est desactive). Ce n'est un vecteur de duplication qu'en conjonction avec une perte du verrou Redis (TTL par defaut 30 s, RedisAuctionClusterBridge.java:188 `long ttlMs = lockTtl != null ? lockTtl.toMillis() : 30000;`, non renouvele pendant l'achat).

CORRECTIF RECOMMANDE (elargi) : appliquer la garde `if (item.getStatus() != this.previous) return;` dans onBackClick comme propose, MAIS y ajouter la vraie protection manquante -- refuser toute retro-transition depuis un statut de traitement, dans onBackClick ET onInventoryClose, par ex. `if (item.getStatus() == ItemStatus.IS_BEING_PURCHASED || item.getStatus() == ItemStatus.IS_BEING_REMOVED || item.getStatus() == ItemStatus.PURCHASED || item.getStatus() == ItemStatus.REMOVED || item.getStatus() == ItemStatus.DELETED) return;`. Optionnellement, faire d'ItemStatusListener un CAS en verifiant `message.oldStatus()` contre le statut local avant `item.setStatus(...)` (ItemStatusListener.java:54).

**Correctif**

```java
Appliquer dans onBackClick la meme garde que onInventoryClose :

@Override
public void onBackClick(...) {
    super.onBackClick(...);
    var manager = this.plugin.getAuctionManager();
    Item item = manager.getCache(player).get(PlayerCacheKey.ITEM_SHOW);
    if (item == null) return;
    if (item.getStatus() != this.previous) return;   // <-- l'item a change d'etat entre-temps, ne rien diffuser
    if (processIfExpired(player, item)) return;
    item.setStatus(this.next);
    ...
}
```

---

<a id="c-069"></a>

### `C-069` — ConfirmHelper.onBackClick ne verifie pas le statut courant (contrairement a onInventoryClose) et rediffuse AVAILABLE par-dessus un achat deja verrouille

- [ ] **Corrigé**
- **Fichier** : `buttons/confirm/ConfirmHelper.java:72`
- **Catégorie** : Duplication d’item — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
onInventoryClose PROTEGE sa mutation (ConfirmHelper.java:41) :
        if (item.getStatus() == this.previous) {
            if (processIfExpired(player, item)) return;
            item.setStatus(this.next);
            ...
            manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
            manager.updateListedItems(item, true, player);
        }

onBackClick fait la MEME mutation SANS la garde (ConfirmHelper.java:70-80) :
        if (processIfExpired(player, item)) return;

        item.setStatus(this.next);
        this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, this.previous, this.next)
            .exceptionally(...);

        manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_LISTED);
        manager.updateListedItems(item, true, player);

next vaut ItemStatus.AVAILABLE pour les deux boutons (ConfirmPurchaseButton et ConfirmRemoveListedButton). La purge est en outre asymetrique : ligne 54 clearPlayersCache(ITEMS_LISTED, ITEMS_SEARCH) pour TOUS les joueurs, ligne 79 clearPlayerCache(player, ITEMS_LISTED) pour le seul acteur.
```

**Chronologie**

T0 - Serveur A : l'acheteur P est dans l'inventaire de confirmation d'achat de l'item #42 (IS_PURCHASE_CONFIRM, diffuse au cluster).

T1 - P clique Confirmer. PurchaseService.purchaseItem demarre une chaine asynchrone (checkAvailability -> lockItem -> notifyStatusChange -> selectItem), soit 2 allers-retours Redis + 1 SQL. Rien ne ferme l'inventaire de confirmation : le bouton Retour reste cliquable pendant toute la chaine.

T2 (+5 ms) - PurchaseService:107 pose IS_BEING_PURCHASED, le verrou Redis est pris.

T3 (+80 ms, double-clic humain) - P clique Retour. onBackClick force setStatus(AVAILABLE) par-dessus IS_BEING_PURCHASED et publie ItemStatusMessage(-> AVAILABLE).

T4 - Serveurs B et C : ItemStatusListener:60-62 applique AVAILABLE et reaffiche l'item comme disponible alors qu'il est verrouille et en cours de paiement. Sur A, le statut memoire etant revenu a AVAILABLE, les gardes de RemoveService (l.55 et l.96) laissent desormais passer le vendeur, qui n'est plus arrete que par le verrou Redis - et plus du tout une fois ce verrou relache, puisque notifyItemBought laisse l'etat a SOLD, non bloquant pour checkAvailability comme pour LOCK_SCRIPT.

Le meme enchainement s'applique cote retrait : Confirmer puis Retour ecrase IS_BEING_REMOVED par AVAILABLE en pleine suppression.

**Impact** — Un simple double-clic remet un item verrouille et en cours de transaction a l'etat AVAILABLE sur tout le cluster, rouvre les gardes de statut de RemoveService et reinjecte l'item dans les interfaces des autres joueurs. La purge asymetrique ajoute une divergence d'affichage : les joueurs en recherche active (ITEMS_SEARCH jamais purge par onBackClick) continuent de voir l'item avec un Consumer de clic date du rendu initial.

**Précision apportée par la contre-expertise**

Description exacte du defaut : ConfirmHelper.onBackClick (ConfirmHelper.java:70-80) applique `setStatus(AVAILABLE)` + `notifyItemStatusChange(previous -> AVAILABLE)` sans la garde `item.getStatus() == this.previous` presente dans onInventoryClose (l.41). Si le joueur clique "Confirmer" puis "Retour" pendant la chaine asynchrone (l'inventaire de confirmation n'est jamais ferme par onClick, l.84-104), un item deja passe en IS_BEING_PURCHASED (PurchaseService.java:107) ou IS_BEING_REMOVED est ecrase par AVAILABLE en memoire et rediffuse comme disponible a tout le cluster (ItemStatusListener.java:54-62). S'ajoute l'asymetrie de purge : l.54 purge ITEMS_LISTED+ITEMS_SEARCH pour tous les joueurs, l.79 seulement ITEMS_LISTED pour l'acteur.

Impact reel (pas de duplication) : desynchronisation de l'etat memoire a l'echelle du cluster. Consequences observables :

1. Affichage fantome : l'item reapparait comme disponible dans les interfaces des autres joueurs alors qu'une transaction est en cours ; les clics aboutissent a des achats qui echouent (verrou Redis ou re-validation SQL), avec messages d'erreur et rafraichissements inutiles.

2. Blocage d'item : si l'achat echoue apres le clic Retour, PurchaseService.java:148-150 `item.setStatus(previousStatus)` restaure IS_PURCHASE_CONFIRM et le rediffuse. L'inventaire de confirmation etant deja ferme, plus aucun onInventoryClose ne remettra l'item en AVAILABLE : il reste bloque en IS_PURCHASE_CONFIRM en memoire, ce qui interdit au vendeur de le retirer (garde RemoveService.java:55) jusqu'a ce qu'un autre joueur declenche un nouveau changement de statut ou qu'un redemarrage recharge le cache.

3. Divergence d'affichage supplementaire pour les joueurs en recherche active, ITEMS_SEARCH n'etant jamais purge par onBackClick.

La correction proposee reste pertinente (helper prive partage avec la garde `if (item.getStatus() != this.previous) return;` et purge identique), mais elle corrige une incoherence d'etat, pas une faille de duplication : le verrou distribue et la re-validation sous verrou restent les seules autorites et sont intactes. Fermer l'inventaire de confirmation des le clic Confirmer supprime la fenetre a la racine.

**Correctif**

```java
Extraire un helper prive appele par onInventoryClose ET onBackClick, avec la meme garde et la meme purge :

    private void restoreAvailable(Player player, Item item, AuctionManager manager) {
        if (item.getStatus() != this.previous) return;   // operation deja engagee ailleurs
        item.setStatus(this.next);
        this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, this.previous, this.next)
            .exceptionally(t -> { this.plugin.getLogger().warning("..." + t.getMessage()); return null; });
        manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH, PlayerCacheKey.ITEMS_SELLING);
        manager.updateListedItems(item, true, null);
        manager.getCache(player).remove(PlayerCacheKey.ITEM_SHOW);
    }

Et fermer/verrouiller l'inventaire de confirmation des le clic Confirmer (ConfirmHelper.onClick, avant onPostClick) pour que le bouton Retour ne soit plus atteignable pendant la chaine asynchrone.
```

---

<a id="c-070"></a>

### `C-070` — Expiration en cluster : boucle non bornee item par item (5 emprunts Redis + 4 requetes SQL chacun) declenchee depuis le rendu d'un inventaire

- [ ] **Corrigé**
- **Fichier** : `services/ExpireService.java:130`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ExpireService.java:129-135 (verifie) - aucune borne, aucun batch :

        // Multi-server: route LISTED -> EXPIRED through the cluster-aware per-item path.
        if (storageType == StorageType.LISTED && this.plugin.getAuctionClusterBridge().isDistributed()) {
            for (Item item : filtered) {
                expireListedItemClustered(item);
            }
            return;
        }

ExpireService.java:265-310 (verifie) - cout unitaire d'UNE expiration clusteree :
        clusterBridge.checkAvailability(item)                    // emprunt Redis 1
            ... clusterBridge.lockItem(item, item.getSellerUniqueId(), StorageType.LISTED)   // emprunt 2
                ... storageManager.selectItem(item.getId())      // 3 a 4 requetes SQL
                    ... performListedToExpired(item)             // 1 UPDATE SQL
                        .thenCompose(v -> clusterBridge.removeItem(item, StorageType.LISTED, StorageType.EXPIRED))  // emprunts 3-4
        .whenComplete(... clusterBridge.unlockItem(item, token, StorageType.LISTED) ...)     // emprunt 5

ZStorageManager.java:176-177 (verifie) - selectItem est le SEUL supplyAsync du fichier sans executor avec findUniqueId l.215, alors que createAuctionItem (l.144/153), updateItem (l.157) et updateItems (l.162) passent tous this.plugin.getExecutorService() :
    public CompletableFuture<Item> selectItem(int id) {
        return CompletableFuture.supplyAsync(() -> {

ZAuctionManager.java:404-420 (verifie) - le declencheur, sur le MAIN THREAD pendant le rendu :
        for (Item item : items.values()) {
            if (item.isExpired()) {
                expiredItems.add(item);
                continue;
            }
            if (predicate.test(item)) { filtered.add(item); }
        }
        if (!expiredItems.isEmpty()) {
            this.auctionExpireService.processExpiredItems(expiredItems, storageType);
        }

ZAuctionManager.java:313-315 (verifie) : getPlayerSellingItems -> getItemIds(StorageType.LISTED, ...), appele depuis SellingItemsButton.onRender:24-26 (verifie).
```

**Chronologie**

T0 (nuit) : serveur A et serveur B tournent, 800 annonces arrivent a expiration pendant que personne ne joue. Aucune tache planifiee ne balaye les expirations : un grep confirme que processExpiredItems n'est appele que depuis getItemIds, donc depuis un rendu d'inventaire.

T1 : un joueur tape /ah selling sur A. SellingItemsButton.onRender (main thread) -> getPlayerSellingItems -> getItemIds(LISTED, ...) parcourt les 50 000 items du store.

T2 : la boucle ZAuctionManager:407-416 collecte TOUS les items expires du serveur, pas seulement ceux du joueur : expiredItems contient les 800 items.

T3 : ExpireService:131 lance 800 expireListedItemClustered dans le meme tick. Chaque appel = 5 emprunts de connexion Jedis + 3 a 4 SELECT + 1 UPDATE. Total instantane : ~4000 emprunts Jedis et ~3200 requetes SQL, dont les selectItem sont du JDBC BLOQUANT poste sur ForkJoinPool.commonPool.

T4 : le pool Jedis est sature (une connexion est de plus monopolisee en permanence par le thread subscriber). Les .orTimeout(perf.checkAvailabilityTimeoutMs()) et .orTimeout(perf.lockItemTimeoutMs()) expirent en masse.

T5 : le whenComplete l.299 fait this.expiringItemIds.remove(item.getId()) meme en cas de timeout. Le prochain joueur qui ouvre un inventaire sur A relance donc exactement la meme tempete : le systeme ne converge jamais.

T6 : serveur B subit la meme chose des qu'un de ses joueurs ouvre un inventaire. Pendant la saturation, les lockItem des achats en cours sur A et B timeoutent eux aussi, ce qui elargit la fenetre pendant laquelle deux serveurs peuvent traiter le meme item sans arbitrage Redis.

**Impact** — Un seul clic d'inventaire peut lancer plusieurs milliers d'operations Redis et SQL simultanees, epuiser le pool Jedis et geler la synchronisation cluster du noeud. La saturation degrade le mecanisme de verrou distribue lui-meme (timeouts sur lockItem), ce qui rapproche le scenario de duplication. Le probleme se reproduit a chaque ouverture d'inventaire tant que le lot n'a pas ete traite.

**Précision apportée par la contre-expertise**

Titre exact : « Expiration en cluster : boucle non bornee item par item (5 emprunts Redis + 4 requetes SQL chacun) declenchee depuis un rendu d'inventaire, sans aucun balayage planifie — backlog non borne et timeouts en cascade ».

MECANIQUE REELLE (prouvee) :

1) ExpireService.java:130-135 route la transition LISTED->EXPIRED clusteree item par item, sans borne ni batch :

if (storageType == StorageType.LISTED && this.plugin.getAuctionClusterBridge().isDistributed()) {

for (Item item : filtered) { expireListedItemClustered(item); }

return;

}

`expiringItemIds` (ExpireService.java:31, :256) ne fait que dedoublonner par id ; N ids distincts = N dispatches, aucun plafond global.

2) Cout unitaire (ExpireService.java:265-310) : 5 emprunts Jedis. Precision a ajouter : `clusterBridge.removeItem(...)` (RedisAuctionClusterBridge.java:350-364) appelle `this.plugin.sendMessage(...)` a l'INTERIEUR de son propre `try (Jedis jedis = jedisPool.getResource())`, et sendMessage reprend une connexion (ZAuctionHouseRedis.java:276-279). Cette etape detient donc 2 connexions simultanement — le constat comptait 5 emprunts mais pas leur imbrication.

3) Declencheur : SellingItemsButton.onRender (buttons/list/SellingItemsButton.java:23-26) -> ZAuctionManager.java:314 getPlayerSellingItems -> getItemIds(LISTED, ...). La boucle ZAuctionManager.java:407-416 collecte TOUS les items expires du store, pas ceux du joueur. Nuance a apporter : l'appel est memoise (`getCache(player).getOrCompute(PlayerCacheKey.ITEMS_SELLING, ...)`), donc le scan a lieu sur defaut de cache, pas a chaque frame. Mais ExpireService.java:331 invalide precisement `ITEMS_SELLING` du vendeur a chaque expiration reussie, et :327 invalide `ITEMS_LISTED`/`ITEMS_SEARCH` globalement : le succes s'auto-reamorce autant que l'echec.

4) Amplificateur principal reel = l'ABSENCE totale de balayage. Aucun `runTimer` d'expiration (seuls BossBarAnimation.java:21 et Metrics.java:187 existent) et un unique appelant de processExpiredItems (ZAuctionManager.java:419). Le backlog d'items expires croit donc sans limite tant que personne n'ouvre l'onglet « selling », puis est purge d'un seul coup. C'est ce point, plus que le volume instantane, qui rend le lot arbitrairement grand.

5) Regime d'execution a corriger : il n'y a jamais des milliers d'operations SIMULTANEES. Les 4 appels du bridge et `selectItem` (ZStorageManager.java:176) sont des supplyAsync/runAsync SANS executor -> ForkJoinPool.commonPool (parallelisme ~ nCPU-1, sans compensation pour de l'IO bloquant), et les UPDATE passent par ZAuctionPlugin.java:82 `Executors.newFixedThreadPool(4)`. Le pool Jedis (`max-total: 64`, Redis config.yml:58) n'est pas prouve epuise. Le dommage reel est : (a) file d'attente sur commonPool et sur les 4 threads SQL ; (b) les `orTimeout(5000ms)` (PerformanceConfiguration.java:47) expirent sur des taches encore en file, donc echecs massifs sans qu'aucune erreur reelle ne se soit produite ; (c) `orTimeout` n'annule pas la tache sous-jacente : le travail Redis/SQL s'execute quand meme apres abandon du future ; (d) famine de commonPool partagee avec SortedItemsCache.java:368 (`parallelStream`).

6) CONSEQUENCE MANQUANTE PAR LE CONSTAT, plus grave que celle annoncee : `tokenHolder.set(token)` est a ExpireService.java:275, DANS le `thenCompose`. Si `lockItem` timeout APRES que le script Lua a pose le verrou (LOCK_SCRIPT fait `SET NX PX` + `HSET state=LOCKED`), le token n'est jamais memorise, `whenComplete` l.303-304 voit `token == null` et n'appelle PAS `unlockItem`. L'item reste LOCKED dans Redis jusqu'a expiration de `lockTtl`. Meme forme a PurchaseService.java:96. C'est une fuite de verrou / indisponibilite temporaire (auto-guerie par TTL grace au garde-fou `EXISTS lockKey` du LOCK_SCRIPT et de checkAvailability), pas une duplication.

IMPACT A REECRIRE : degradation de disponibilite et rafale de warnings « Cluster-aware expiration skipped/failed », backlog qui ne converge pas, verrous Redis fuites jusqu'a TTL. SUPPRIMER l'affirmation que cela rapproche la duplication : PurchaseService echoue en mode ferme sur timeout (PurchaseService.java:99-103 et :155-172), aucun debit ni transfert.

CORRECTIONS PROPOSEES : les points 1) a 5) du constat restent valides et pertinents. Ajouter un 6e point : memoriser le token AVANT le timeout (p. ex. `lockItem(...).thenApply(t -> { tokenHolder.set(t); return t; }).orTimeout(...)`) dans ExpireService.java:272-275 ET PurchaseService.java:93-96, pour que le `whenComplete`/`exceptionally` puisse relacher un verrou acquis mais abandonne. Ajouter un 7e : desimbriquer `sendMessage` de la connexion deja detenue dans RedisAuctionClusterBridge.removeItem/notifyItemBought/notifyItemListed (sortir l'appel du bloc try-with-resources) pour ne pas detenir 2 connexions par operation.

SEVERITE : « haute » n'etait justifiee qu'a travers le lien duplication, qui est refute. Severite reelle : moyenne (performance/disponibilite, aggravee par l'absence de balayage planifie qui rend la taille du lot non bornee).

**Correctif**

```java
1) Borner le lot cote appelant, ZAuctionManager.java:418-420 :
if (!expiredItems.isEmpty()) {
    int batch = Math.min(expiredItems.size(), this.plugin.getConfiguration().getPerformance().expireBatchSize()); // ex. 25
    this.auctionExpireService.processExpiredItems(expiredItems.subList(0, batch), storageType);
}

2) Plafonner les expirations clusterees en vol dans ExpireService (aujourd'hui seul expiringItemIds dedoublonne, sans limite globale) :
private final Semaphore clusterExpireSlots = new Semaphore(8);
private void expireListedItemClustered(Item item) {
    if (!this.expiringItemIds.add(item.getId())) return;
    if (!clusterExpireSlots.tryAcquire()) { this.expiringItemIds.remove(item.getId()); return; }
    ... // dans le whenComplete l.298 : clusterExpireSlots.release();
}

3) Passer l'executor dedie aux deux supplyAsync orphelins de ZStorageManager (l.177 selectItem, l.215 findUniqueId) : CompletableFuture.supplyAsync(() -> { ... }, this.plugin.getExecutorService()).

4) Traiter par lots : StorageManager.selectItems(List<Integer>) existe deja (ZStorageManager:238-251) et fait 3 requetes pour N items au lieu de 3N.

5) Ajouter une tache planifiee de balayage (plugin.getScheduler().runTimerAsync(..., 30, 30, TimeUnit.SECONDS)) traitant au plus 50 items par passage, et laisser le chemin de rendu se contenter de MASQUER les items expires (le filtre existe : ListedItemsButton `if (!item.isActivelyListed()) continue;`).
```

---

<a id="c-071"></a>

### `C-071` — ItemRemovedListener et ItemBoughtListener re-ajoutent l'item apres un second aller-retour DB, sans verifier qu'il n'a pas ete supprime entre-temps

- [ ] **Corrigé**
- **Fichier** : `zAuctionHouse Redis/redis/listener/listeners/ItemRemovedListener.java:50`
- **Catégorie** : Cohérence de cache — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// ItemRemovedListener.java:32-63 -- etape 1 immediate, etape 2 apres un SELECT, aucune garde entre les deux
// Step 1: Immediately remove from all storage types and clear caches.
auctionPlugin.getScheduler().runNextTick(wrappedTask -> {
    for (StorageType st : StorageType.values()) {
        if (st != StorageType.DELETED) { manager.removeItem(st, id); }
    }
    ...
});

// Step 2: If the destination is not DELETED, fetch the item from DB and re-add it
StorageType destination = message.destinationStorageType();
if (destination != null && destination != StorageType.DELETED) {
    auctionPlugin.getStorageManager().selectItem(id).whenComplete((item, throwable) -> {
        ...
        if (item == null) return;
        auctionPlugin.getScheduler().runNextTick(w -> {
            var manager = auctionPlugin.getAuctionManager();
            manager.addItem(destination, item);      // destination figee au moment de l'EMISSION du message

// ItemBoughtListener.java:96-108 -- meme structure
auctionPlugin.getStorageManager().selectItem(id).whenComplete((item, throwable) -> {
    ...
    if (item == null || item.getBuyerUniqueId() == null) { return; }
    auctionPlugin.getScheduler().runNextTick(w -> {
        var manager = auctionPlugin.getAuctionManager();
        manager.addItem(StorageType.PURCHASED, item);
```

**Chronologie**

La destination du re-ajout est figee au moment ou le message a ete EMIS, alors que le re-ajout, lui, s'applique apres un SELECT de latence non bornee (ZStorageManager.java:176, commonPool sature par tout l'IO du bridge).

T0 -- Serveur A : le vendeur retire son annonce 42 alors qu'il n'a pas de place. RemoveService.removeListedItem calcule destination = EXPIRED (RemoveService.java:63) et diffuse removeItem(item, LISTED, EXPIRED).

T1 -- Serveur B applique l'etape 1 : l'item 42 est retire de tous les stockages memoire. Puis il lance selectItem(42), qui traine.

T2 -- Toujours sur A, le vendeur reclame immediatement l'item depuis son onglet "expires" : RemoveService le passe a DELETED, le lui donne, et diffuse removeItem(item, EXPIRED, DELETED).

T3 -- Serveur B recoit ce second message et applique son etape 1 : suppression de tous les stockages -- l'item n'y est pas encore, l'operation ne fait rien. Sa branche destination == DELETED n'effectue aucun re-ajout.

T4 -- Le selectItem lance en T1 revient enfin sur B avec l'instantane lu AVANT le passage a DELETED, et execute addItem(StorageType.EXPIRED, item) : l'item 42, deja donne physiquement au vendeur sur A, reapparait dans les items expires de B.

T5 -- Le vendeur bascule sur B et reclame de nouveau l'item expire. RemoveService ne revalide pas la base sous verrou, et l'etat Redis vaut DELETED... mais le TTL de 24 h (item-state-ttl-seconds) l'aura efface au plus tard le lendemain, moment ou checkAvailability retourne true et ou le second exemplaire est livre.

**Impact** — Duplication d'item via un fantome EXPIRED (ou PURCHASED pour ItemBoughtListener) qui survit a la suppression, exactement dans la fenetre ou le joueur enchaine retrait puis reclamation. Le re-ajout n'observe jamais l'etat courant, seulement un instantane perime.

**Précision apportée par la contre-expertise**

Description exacte du defaut : dans ItemRemovedListener (etape 2, l.48-63) et ItemBoughtListener (etape 2, l.96-108), la destination du re-ajout memoire est figee a la reception du message et l'objet Item re-ajoute provient d'un instantane DB lu plus tot. Entre la lecture de la ligne par ItemRepository.select(int) et l'execution de `manager.addItem(...)` dans le runNextTick, il s'ecoule deux requetes SQL supplementaires (ZStorageManager.java:176-200) plus un tick serveur. Si, pendant cet intervalle, un autre serveur fait passer la ligne a DELETED (reclamation de l'item expire ou de l'item achete) et diffuse son propre ItemRemovedMessage, l'etape 1 de ce second message ne trouve rien a supprimer et l'etape 1 est ensuite ecrasee par le re-ajout de l'instantane perime : l'item reapparait en memoire en EXPIRED (ou PURCHASED) sur ce serveur alors qu'il a deja ete remis physiquement au joueur ailleurs.

Le fantome n'est purge par rien : loadItems() n'est appele qu'au demarrage (ZAuctionPlugin.java:160), et le nettoyage de fantomes d'ExpireService.java:281-288 ne couvre que StorageType.LISTED.

La duplication n'est pas immediate : elle est bloquee par checkAvailability (RedisAuctionClusterBridge.java:165-173) tant que la clef `auction:item:<id>` porte STATE_DELETED. Elle devient possible apres expiration de item-state-ttl-seconds (86400 par defaut, config.yml:51) : hget renvoie null, checkAvailability renvoie true, lockItem reussit (l'etat null passe le script Lua), et ZAuctionManager.removeExpiredItem l.599-603 livre l'item sans verifier les lignes affectees par l'UPDATE.

Fenetre reelle : quelques dizaines a ~150 ms, dans un enchainement retrait -> reclamation par le meme joueur. Exploitation deliberee difficile mais atteignable par macro ; declenchement accidentel plausible sur un cluster charge.

Correction juste : dans les deux listeners, revalider le storage_type reellement relu contre la destination annoncee juste avant addItem (`if (item.getStatus() != ItemStatus.REMOVED) return;` pour destination EXPIRED, `!= ItemStatus.PURCHASED` pour PURCHASED) — le garde `item.getStatus() == ItemStatus.DELETED` de la correction proposee est mort, ItemRepository.java:51-56 filtrant deja `storage_type != DELETED` et le statut etant derive du storage_type (ItemLoaderUtils.java:43-47). Correction plus solide : serialiser le traitement des messages par identifiant d'item (executeur a cle) pour que l'etape 1 d'un message posterieur ne puisse plus etre devancee par l'etape 2 d'un message anterieur.

**Correctif**

```java
Relire l'etat au moment du re-ajout et refuser tout instantane devenu incoherent :

if (destination != null && destination != StorageType.DELETED) {
    auctionPlugin.getStorageManager().selectItem(id).whenComplete((item, throwable) -> {
        if (throwable != null || item == null) return;
        auctionPlugin.getScheduler().runNextTick(w -> {
            // le stockage reel de la ligne doit encore correspondre a la destination annoncee
            if (item.getStatus() == ItemStatus.DELETED) return;
            if (destination == StorageType.EXPIRED  && item.getStatus() != ItemStatus.REMOVED)   return;
            if (destination == StorageType.PURCHASED && item.getStatus() != ItemStatus.PURCHASED) return;
            manager.addItem(destination, item);
            ...
        });
    });
}

Appliquer la meme garde dans ItemBoughtListener (etape 2) et, plus surement, serialiser le traitement des messages par identifiant d'item pour que l'ordre d'emission soit respecte.
```

---

<a id="c-072"></a>

### `C-072` — Les statuts transitoires recus par Redis n'expirent jamais : un item peut rester invisible et non reclamable sur tout le reseau indefiniment

- [ ] **Corrigé**
- **Fichier** : `REDIS/listener/listeners/ItemStatusListener.java:56`
- **Catégorie** : Perte d’item — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ItemStatusListener.java:54-63 - le receveur applique et masque, sans horodatage ni proprietaire :
            item.setStatus(message.newStatus());

            if (message.newStatus() == ItemStatus.IS_PURCHASE_CONFIRM || message.newStatus() == ItemStatus.IS_REMOVE_CONFIRM
                    || message.newStatus() == ItemStatus.IS_BEING_REMOVED || message.newStatus() == ItemStatus.IS_BEING_PURCHASED) {
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING);
                manager.updateListedItems(item, false, null);
            }

SortedItemsCache.rebuildCache:305 ne conserve que AVAILABLE :
                if (item.getStatus() == ItemStatus.AVAILABLE && !item.isExpired()) {

Les SEULS chemins de retour vers AVAILABLE sont ConfirmHelper:47 et :72 (serveur emetteur, dependants de son cache ITEM_SHOW), PurchaseService:149/:178 et RemoveService:309. Aucune tache periodique de reconciliation n'existe (grep runTimer/runTaskTimer : aucune hors BossBarAnimation et le heartbeat de l'addon), et ItemStatus n'est jamais persiste en base.
```

**Chronologie**

T0 - Serveur A : le joueur P clique l'item 42. ListedItemsButton:229-234 diffuse notifyItemStatusChange(AVAILABLE -> IS_PURCHASE_CONFIRM), pose le statut localement et ouvre l'inventaire de confirmation.

T1 - Serveurs B, C, D : ItemStatusListener:54 applique IS_PURCHASE_CONFIRM et appelle updateListedItems(item, false, null) : l'item disparait des GUI ouverts. Au prochain rebuild, SortedItemsCache l'exclut de toutes les listes triees.

T2 - Serveur A : P se deconnecte brutalement, ou A crashe / est arrete. PlayerListener.onQuit:43 fait removeCache(player) ; la lecture de ITEM_SHOW renvoie ensuite null et ConfirmHelper:38 fait return. AUCUN retour a AVAILABLE n'est pose ni diffuse.

T3 - Plus rien ne remet le statut. Le TTL Redis (30 s sur auction:lock, 24 h sur auction:item) ne corrige que l'etat Redis, jamais la memoire Java des pairs.

T4 - L'item 42 est invendable et invisible sur B, C, D indefiniment. Seul un redemarrage de chaque serveur repare (ItemLoaderUtils:43-48 rederive le statut depuis storage_type).

Variante equivalente : RemoveService:242 diffuse IS_BEING_REMOVED pour un item EXPIRED ou PURCHASED ; si le retrait echoue apres localRemovalCompleted, les pairs gardent IS_BEING_REMOVED et RemoveService:134 / :172 refusent definitivement la reclamation sur ces serveurs.

**Impact** — Item gele : ni achetable ni reclamable sur les serveurs receveurs, sans limite de temps ni auto-guerison. Perte de valeur pour le vendeur et incoherence permanente entre les noeuds, amplifiee par le fait qu'ItemStatus n'existe que dans la memoire de chaque JVM.

**Précision apportée par la contre-expertise**

Titre corrigé : « Les statuts de confirmation (*_CONFIRM) reçus par Redis ne sont jamais réarmés si l'émetteur ne diffuse pas le retour AVAILABLE : l'item devient invisible et inachetable sur tous les pairs jusqu'à l'expiration naturelle de l'annonce ».

Catégorie corrigée : cohérence-multi-serveurs / indisponibilité (pas perte-item).

Description exacte :

ItemStatusListener.java:54-63 applique le statut reçu sans horodatage ni propriétaire (ZItem:182-185 ne stocke que l'enum), et RedisAuctionClusterBridge:335-337 se contente de publier le message sans écrire ni expirer quoi que ce soit dans `auction:item:` — le statut transitoire n'a donc aucun TTL, aucune persistance (ItemDTO n'a pas de colonne status ; ItemLoaderUtils:43-48 le redérive de storage_type) et aucune tâche de réconciliation (seuls timers existants : BossBarAnimation:21, Metrics:187, heartbeat Redis:204).

Déclencheur réel et courant (pas un crash) : un joueur ouvre la confirmation d'achat sur le serveur A (ListedItemsButton:229-234 diffuse AVAILABLE -> IS_PURCHASE_CONFIRM), puis se déconnecte. zMenu diffère la fermeture d'un tick (VInventoryManager:192-202 `runAtEntityLater(..., 1)`) alors que PlayerQuitEvent -> PlayerListener:43 `removeCache(player)` s'exécute dans le même tick ; quand InventoryDefault.onClose:186-187 appelle enfin ConfirmHelper.onInventoryClose, `cache.get(PlayerCacheKey.ITEM_SHOW)` est null et ConfirmHelper:38 `return`. Le retour à AVAILABLE (ConfirmHelper:47-48, next = ItemStatus.AVAILABLE d'après ConfirmPurchaseButton:17) n'est ni posé ni diffusé. Idem si A s'arrête ou crashe. Les statuts IS_BEING_PURCHASED / IS_BEING_REMOVED, eux, sont protégés par un rollback en processus (PurchaseService:175-186, RemoveService:305-311) et n'exposent le problème qu'en cas de crash JVM.

Impact réel : sur B, C, D l'item reste hors de SortedItemsCache (SortedItemsCache:304-305 ne garde que AVAILABLE), donc invisible dans l'hôtel des ventes et dans la recherche, et non retirable par son vendeur (RemoveService:99-100 exige AVAILABLE) — alors qu'il reste visible dans son onglet « selling » (ZAuctionManager:314 ne filtre que DELETED), ce qui produit un item apparemment bloqué. Ce n'est PAS indéfini : dès que `expired_at` est dépassé, tout appel à getItemIds(LISTED, ...) (ZAuctionManager:314/320, déclenché par l'ouverture de l'onglet « selling » de n'importe quel joueur) balaie tous les items sans regarder le statut (ZAuctionManager:406-419) et route vers ExpireService.expireListedItemClustered:255 -> performListedToExpired:367 `item.setStatus(ItemStatus.REMOVED)` + passage en EXPIRED, rendant l'item réclamable. Le gel est donc borné par la durée restante de l'annonce (potentiellement plusieurs jours), sans perte d'item ni duplication.

Correction recommandée (version resserrée) : horodater le statut dans ZItem.setStatus et n'appliquer le TTL de réarmement qu'aux statuts de confirmation (IS_PURCHASE_CONFIRM / IS_REMOVE_CONFIRM), avec un TTL court aligné sur la durée de vie d'un GUI de confirmation (~60 s). Ne PAS réarmer IS_BEING_REMOVED/IS_BEING_PURCHASED côté receveur au bout d'un simple délai : RemoveService.restoreStatusOnError:305-311 documente explicitement que ne pas restaurer après `localRemovalCompleted` évite un item fantôme dupliqué ; un réarmement aveugle de ces deux états réintroduirait ce risque de duplication. Alternativement, une simple purge à l'émission suffirait pour le cas dominant : diffuser le retour AVAILABLE depuis PlayerListener.onQuit (avant removeCache) pour tout item du joueur en statut *_CONFIRM, et depuis ZAuctionPlugin.onDisable.

**Correctif**

```java
1) Horodater et attribuer le statut transitoire cote receveur : ajouter dans ZItem 'private volatile long statusChangedAt; private volatile UUID statusOwnerServer;' positionnes dans setStatus, et transporter le serverId (deja present dans RedisMessage.serverId()).
2) Ajouter une tache periodique dans ZAuctionPlugin :
    getScheduler().runTimerAsync(() -> {
        long ttl = configuration.getPerformance().transientStatusTtlMs();
        long now = System.currentTimeMillis();
        for (StorageType st : List.of(LISTED, EXPIRED, PURCHASED)) {
            for (Item item : auctionManager.getItems(st)) {
                if (!item.getStatus().isTransient()) continue;
                if (now - item.getStatusChangedAt() < ttl) continue;
                item.setStatus(switch (st) { case LISTED -> AVAILABLE; case EXPIRED -> REMOVED; case PURCHASED -> PURCHASED; default -> DELETED; });
                auctionManager.invalidateSortedItemsCache();
                if (st == StorageType.LISTED) auctionManager.updateListedItems(item, true, null);
            }
        }
    }, 10, 10, TimeUnit.SECONDS);
3) Ajouter ItemStatus.isTransient().
```

---

<a id="c-073"></a>

### `C-073` — Les trois commandes de purge des logs materialisent toutes les lignes (dont l'itemstack LONGTEXT) en memoire uniquement pour les compter

- [ ] **Corrigé**
- **Fichier** : `storage/repository/repositories/LogRepository.java:143`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```sql
LogRepository.java:143-160 (verifie, les trois methodes) :

    public long deleteByPlayer(UUID playerUniqueId) {
        long count = select(LogDTO.class, schema -> schema.where("player_unique_id", playerUniqueId.toString())).size();
        delete(schema -> schema.where("player_unique_id", playerUniqueId.toString()));
        return count;
    }

    public long deleteOlderThan(long olderThanMs) {
        Date cutoff = new Date(System.currentTimeMillis() - olderThanMs);
        long count = select(LogDTO.class, schema -> schema.where("created_at", "<", cutoff)).size();
        delete(schema -> schema.where("created_at", "<", cutoff));
        return count;
    }

    public long deleteMigrated() {
        long count = select(LogDTO.class, schema -> schema.where("item_id", 0)).size();
        delete(schema -> schema.where("item_id", 0));
        return count;
    }

Aucun index sur logs.created_at / logs.player_unique_id / logs.item_id : grep 'index(' sur storage/migrations/ ne retourne AUCUNE occurrence.
```

**Chronologie**

T0 : ZStorageManager.log journalise chaque vente, chaque achat et chaque retrait de TOUS les serveurs du cluster dans la meme table MySQL partagee. Apres quelques mois, %prefix%logs contient plusieurs millions de lignes, chacune portant une colonne itemstack en LONGTEXT (Base64 complet de l'ItemStack).

T1 : l'administrateur, voyant la base grossir, lance /ah admin logs purge 30 sur le serveur A.

T2 : deleteOlderThan fait un select(LogDTO.class, ...) qui charge en heap TOUTES les lignes correspondantes, itemstack compris, juste pour appeler .size() dessus. Plusieurs Go de heap.

T3 : le serveur A part en GC continu puis en OutOfMemoryError avant meme que le DELETE ne commence. La table n'est pas purgee ; l'admin recommence et re-tue le serveur.

T4 : le SELECT puis le DELETE sont deux balayages complets successifs d'une table de plusieurs millions de lignes sur la base PARTAGEE : le serveur B voit ses propres requetes (selectItem de revalidation d'achat, createAuctionItem) ralentir jusqu'a declencher les .orTimeout des sections critiques.

**Impact** — Les trois commandes de maintenance des logs peuvent faire tomber le serveur par OutOfMemoryError sur une base de production, exactement au moment ou l'administrateur cherche a la degonfler, et degradent tous les autres noeuds du cluster qui partagent la base.

**Précision apportée par la contre-expertise**

Le constat est reel mais une de ses sous-affirmations est fausse, et la severite est surevaluee.

1) SOUS-AFFIRMATION FAUSSE — "Aucun index sur logs.created_at / logs.player_unique_id / logs.item_id".

Le grep est correct (le plugin ne cree jamais d'index explicite), mais la conclusion ne suit pas sur MySQL/MariaDB. CreateLogsMigration.java declare :

table.integer("item_id").foreignKey(Tables.ITEMS, "id", true);

table.string("player_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);

table.string("target_unique_id", 36).nullable().foreignKey(Tables.PLAYERS, "unique_id", true);

`SchemaBuilder.foreignKey(String, String, boolean)` (SchemaBuilder.java:381-387) emet une vraie clause `FOREIGN KEY (%s) REFERENCES %s(`%s`)`, et `CreateRequest.execute` les concatene puis force le moteur : `if (databaseConfiguration.getDatabaseType() != DatabaseType.SQLITE) createTableSQL.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");`. InnoDB cree automatiquement un index sur toute colonne referencante d'une cle etrangere.

Donc, sur le MySQL partage du scenario cluster, `deleteByPlayer` (player_unique_id) et `deleteMigrated` (item_id) sont des recherches INDEXEES, pas des full scans. Seul `created_at` — utilise par le chemin phare `/ah admin logs purge <jours>` — est reellement non indexe. (Sur SQLite les FK ne creent aucun index, mais SQLite est mono-serveur et hors du scenario cluster.)

=> La correction #3 doit etre reduite a un seul index reellement manquant : `%prefix%logs(created_at)`. Les index sur player_unique_id / item_id / target_unique_id sont deja fournis par les contraintes FK sous InnoDB.

Cela ne touche PAS le defaut central : le gaspillage de heap est independant de l'indexation — un `deleteByPlayer` indexe materialise quand meme toutes les lignes de ce joueur avec leurs itemstack LONGTEXT.

2) SEVERITE — haute a ramener a moyenne. Le chemin est reserve a l'admin (`Permission.ZAUCTIONHOUSE_ADMIN` sur les trois commandes), a declenchement strictement manuel (aucune purge planifiee), execute sur un thread async, et exige une table deja tres volumineuse. Aucun joueur ne peut l'atteindre et ce n'est pas un vecteur de duplication. Le defaut reste a corriger car sa condition de declenchement est exactement la situation que la commande est censee resoudre.

3) Les corrections #1, #2 et #4 sont valides et a conserver telles quelles. La #1 est la seule vraiment necessaire : `return delete(schema -> schema.where("created_at", "<", cutoff));` supprime a la fois la materialisation memoire et le double balayage, en utilisant une valeur de retour deja calculee par DeleteRequest.

**Correctif**

```java
1) Utiliser la valeur de retour du DELETE, deja disponible (Sarah DeleteRequest remonte executeUpdate()) :
    public long deleteOlderThan(long olderThanMs) {
        Date cutoff = new Date(System.currentTimeMillis() - olderThanMs);
        return delete(schema -> schema.where("created_at", "<", cutoff));
    }
ou, si le compte doit precéder la suppression, la surcharge de comptage deja presente dans Repository : long count = select(schema -> schema.where("created_at", "<", cutoff));  // SchemaBuilder.selectCount
2) Appliquer la meme correction a deleteByPlayer et deleteMigrated.
3) Ajouter une migration creant des index sur %prefix%logs(created_at), (player_unique_id) et (item_id).
4) Supprimer par lots (DELETE ... LIMIT 5000 en boucle) pour ne pas verrouiller la table pour les autres serveurs.
```

---

<a id="c-074"></a>

### `C-074` — ListedItemsButton.updateInventory : clone d'IntList complete + scan lineaire, par joueur regardant l'HDV, a chaque message cluster

- [ ] **Corrigé**
- **Fichier** : `buttons/list/ListedItemsButton.java:255`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ListedItemsButton.java:249-268 (verifie), appele depuis ZAuctionManager.updateListedItems :
    public void updateInventory(Player player, InventoryEngine inventoryEngine, Item item, boolean isAdded, AuctionManager manager) {
        int page = inventoryEngine.getPage();
        // Get the IDs from cache (O(1) access)
        IntList itemIds = manager.getItemIdsListedForSale(player);
        ...
        int itemIndex = findIndexOf(itemIds, item.getId());

ListedItemsButton.java:305-312 (verifie) - scan lineaire :
    private int findIndexOf(IntList ids, int itemId) {
        for (int i = 0; i < ids.size(); i++) {
            if (ids.getInt(i) == itemId) { return i; }
        }
        return -1;
    }

Le commentaire "O(1) access" est faux des que le cache vient d'etre vide.
ZAuctionManager.java:295 (verifie) :
        IntList ids = cache.getOrCompute(PlayerCacheKey.ITEMS_LISTED, () -> sortedItemsCache.getSortedIds(category, sort));
SortedItemsCache.java:96-98 (verifie) :
        IntList cached = cache.get(sortItem);
        return cached != null ? cached.clone() : new IntArrayList();
API IntArrayList.java:93-99 (verifie) :
    public IntList clone() {
        IntArrayList copy = new IntArrayList(size);
        copy.size = this.size;
        System.arraycopy(this.data, 0, copy.data, 0, this.size);
        return copy;
    }

Or les listeners Redis vident ITEMS_LISTED juste AVANT d'appeler updateListedItems : ItemStatusListener.java:58 et :61, ItemListedListener.java:45.
```

**Chronologie**

Charge : 50 000 items LISTED, cluster de 3 serveurs, 50 joueurs ayant l'inventaire /ah ouvert sur le serveur B.

T0 serveur A : un joueur ouvre une confirmation d'achat -> un ItemStatusMessage est diffuse sur le bus.

T1 serveur B : ItemStatusListener.java:58 fait manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING) : le cache ITEMS_LISTED des 50 joueurs de B est vide. Puis l.59 manager.updateListedItems(item, false, null).

T2 serveur B : ZAuctionManager:953-975 boucle sur getOnlinePlayers() et planifie un runAtEntity par joueur ; pour ceux dont le holder est un InventoryEngine portant un ListedItemsButton, updateInventory est execute.

T3 serveur B, MAIN THREAD, x50 joueurs : getItemIdsListedForSale -> cache MISS (vide en T1) -> sortedItemsCache.getSortedIds -> clone() = allocation d'un int[50000] (200 Ko) + System.arraycopy ; puis findIndexOf = jusqu'a 50 000 comparaisons.

Bilan par message recu : 50 x (200 Ko + 50 000 iterations) = 10 Mo alloues et 2,5 millions d'iterations dans un seul tick. A 40 messages de statut par seconde sur le cluster, le serveur B ne tient plus son tick.

**Impact** — Effondrement du TPS proportionnel a nombre_d_items x nombre_de_spectateurs x frequence des evenements cluster. Le clearPlayersCache global annule precisement l'optimisation que le cache trie est cense apporter : plus le cluster est actif, plus l'affichage coute cher.

**Précision apportée par la contre-expertise**

Le defaut est reel et bien localise, mais trois points du constat doivent etre corriges.

A) IMPACT SUREVALUE — severite ramenee a "moyenne". Le chiffrage "le serveur B ne tient plus son tick" n'est pas soutenu par les ordres de grandeur. Pour 50 spectateurs x 50 000 items : 2,5 M de comparaisons d'int sur tableau contigu ~= 2-3 ms, et 50 x System.arraycopy de 200 Ko ~= 1-2 ms, soit ~5 ms de main thread par message cluster, soit ~20 % du budget de tick a 40 msg/s — degradation nette, pas effondrement. Le cout dominant reel est la PRESSION GC : ~10 Mo alloues par message, ~400 Mo/s a 40 msg/s, en tableaux int[50000] de 200 Ko. C'est ce point qu'il faut mettre en avant, pas le temps CPU. De plus un interrupteur documente existe deja : config.yml:774 `update-inventory-on-action: true` — a false, ZAuctionManager.java:940-947 court-circuite toute la boucle. Cela reste un defaut de performance a corriger, mais avec mitigation configurable disponible.

B) CORRECTION PROPOSEE n°1 PARTIELLEMENT FAUSSE — elle introduirait un bug d'affichage. Retirer PlayerCacheKey.ITEMS_LISTED du clearPlayersCache n'est sur QUE pour ItemStatusListener.java:58 (branche added=false), ou ZAuctionManager.java:972 `if (!added) removeFromCache(onlinePlayer, item);` assure effectivement le retrait cible via removeFromCache (:978-986). En revanche c'est INCORRECT pour ItemStatusListener.java:61 (retour a AVAILABLE, added=true) et pour ItemListedListener.java:45 : sur le chemin added=true, RIEN ne reinjecte l'id dans le cache ITEMS_LISTED d'un joueur — removeFromCache (:978) ne sait que retirer — et updateInventory abandonne alors en ListedItemsButton.java:268 `if (itemIndex == -1) return;` puisque findIndexOf ne trouve pas l'id. Consequence : l'item reste invisible chez tous les spectateurs jusqu'a fermeture/reouverture. Il faut donc soit conserver l'invalidation sur les branches added=true, soit ajouter un addToCache cible symetrique de removeFromCache avant de supprimer le clear.

C) POINTS B) ET C) DE LA CORRECTION SONT VALIDES. L'index inverse (fix n°2) et le getSortedIdsView sans clone (fix n°3) sont applicables : les IntList publiees dans sortedAllItems/sortedByCategoryItems sont bien remplacees en copy-on-write par rebuildCache (SortedItemsCache.java:284+, sous lock.writeLock()), donc une vue lecture seule est sure. Attention toutefois : removeFromCache (ZAuctionManager.java:983) appelle `items.rem(item.getId())` sur la liste rendue par getItemIdsListedForSale et stockee dans le cache joueur — si getItemIdsListedForSale renvoyait une vue non clonee, ce `rem` muterait la liste partagee du SortedItemsCache. Le clone de ZAuctionManager.java:295 n'est donc PAS purement lecteur et ne peut pas etre supprime tel quel ; seuls des chemins strictement lecteurs (getPaginationSize, resolveItemsForPage) peuvent basculer sur la vue.

D) BUG ADJACENT HORS PERIMETRE, a signaler separement : ZAuctionManager.java:956 `if (onlinePlayer == ignoredPlayer) return;` est place dans la boucle for d'un lambda thenRun — `return` sort du LAMBDA, pas de l'iteration ; il faudrait `continue`. Sans effet sur le scenario Redis decrit (ignoredPlayer y est null, cf. ItemStatusListener.java:59/62 et ItemListedListener.java:47), mais sur le chemin local (ex. ListedItemsButton.java:232 `manager.updateListedItems(item, false, player)`) tous les joueurs iteres apres le joueur acteur sont silencieusement prives de la mise a jour.

**Correctif**

```java
1) Ne pas invalider ITEMS_LISTED pour un evenement portant sur UN item : updateListedItems fait deja le retrait cible via removeFromCache (ZAuctionManager:978-986). Dans ItemStatusListener.java:58 et :61, supprimer PlayerCacheKey.ITEMS_LISTED du clearPlayersCache et laisser updateListedItems travailler item par item.

2) Rendre findIndexOf O(1) : publier dans SortedItemsCache un index inverse construit pendant le rebuild, a cote de chaque IntList triee :
private final AtomicReference<Map<SortItem, Map<Integer,Integer>>> indexOfAllItems = new AtomicReference<>(new ConcurrentHashMap<>());
// dans rebuildCache, apres extractIdsToArray :
Map<Integer,Integer> idx = new HashMap<>(ascDateIds.length * 2);
for (int i = 0; i < ascDateIds.length; i++) idx.put(ascDateIds[i], i);
Exposer int indexOf(Category c, SortItem s, int itemId) et remplacer l'appel a findIndexOf.

3) Supprimer le clone() sur les chemins purement lecteurs : les IntList publiees sont immuables apres publication (copy-on-write dans rebuildCache). Ajouter IntList getSortedIdsView(Category, SortItem) qui retourne directement `cached` (documentee lecture seule) et l'utiliser dans ListedItemsButton.onRender, getPaginationSize et updateInventory. Ne conserver clone() que pour les appelants qui derivent la liste.
```

---

<a id="c-075"></a>

### `C-075` — Migration V3 : un echec d'insert de stack laisse un item vendable au prix plein avec un lot ampute ou vide, et la ligne items parente n'est jamais supprimee

- [ ] **Corrigé**
- **Fichier** : `migration/v3/V3MigrationService.java:282`
- **Catégorie** : Perte d’argent — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
V3MigrationService.createAuctionItems (l.282-297) : boucle sans transaction ni compensation :
    private void createAuctionItems(int itemId, V3AuctionItem v3Item) {
        String itemstack = v3Item.getItemstack();
        if (v3Item.isInventoryType() && itemstack.contains(";")) {
            String[] itemstacks = itemstack.split(";");
            for (String stack : itemstacks) {
                if (!stack.trim().isEmpty()) {
                    insertAuctionItem(itemId, stack.trim());
                }
            }
        } else {
            insertAuctionItem(itemId, itemstack);
        }
    }

V3MigrationService.insertAuctionItem (l.299-313) : n'attrape que SQLException, alors que Sarah leve une DatabaseException (RuntimeException) :
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create auction item for item_id " + itemId + ": " + e.getMessage());
        }

V3MigrationService.migrateItems (l.223-250) : la ligne items parente est deja inseree et n'est jamais retiree :
                int itemId = createItem(v3Item);
                if (itemId == -1) { errors.incrementAndGet(); continue; }
                createAuctionItems(itemId, v3Item);
                migrated++;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate item " + v3Item.getId() + ": " + e.getMessage());
                errors.incrementAndGet();
            }

Aucun `DELETE FROM items` et aucun appel a `DatabaseConnection.beginTransaction()` dans tout zAuctionHouseV4 : il n'existe ni rollback ni nettoyage.
```

**Chronologie**

T0 - L'admin migre depuis V3 sur le serveur A. migrateItems:229 appelle createItem, qui insere avec succes la ligne %prefix%items (storage_type='LISTED', price = prix V3 COMPLET). La cle generee est retournee.

T1 - createAuctionItems boucle sur les 5 stacks d'un item V3 de type INVENTORY. Le 3e insertAuctionItem echoue (LONGTEXT trop volumineux, coupure Hikari, deadlock InnoDB...). InsertRequest.java:74 leve une DatabaseException, qui n'est PAS une SQLException et traverse donc le catch l.307 ; elle sort de la boucle for : les stacks 4 et 5 ne sont jamais inseres.

T2 - L'exception remonte a migrateItems:244 -> warning + errors++, et `migrated++` est saute. Mais la ligne items parente EXISTE toujours, en LISTED, au prix plein.

T3 - Les serveurs A, B et C partagent la base : au prochain chargement, ItemLoaderUtils.java:39 construit `itemStacks` avec 2 elements (ou 0 si l'unique insert d'un item mono-stack a echoue). ZAuctionItem.getItemStack l.69 : `size()==1` est faux, donc l.81 renvoie l'icone de lot configuree -- l'item s'affiche exactement comme un lot legitime au prix affiche.

T4 - Un joueur achete sur n'importe lequel des trois serveurs. ZAuctionManager.java:802 preleve le prix plein, puis l.930 `for (ItemStack itemStack : auctionItem.getItemStacks())` ne donne que 2 stacks sur 5 -- ou strictement rien.

T5 - L'item ampute passe en DELETED/PURCHASED ; si l'achat est configure sans give-item, il repart en circulation. Le vendeur (ou le systeme de claim) encaisse le prix plein.

**Impact** — L'acheteur paie le prix plein d'un lot pour recevoir une partie, voire rien du tout, sur n'importe quel serveur du reseau. La migration annonce un simple warning et l'admin n'a aucun moyen d'identifier les items amputes a posteriori.

**Précision apportée par la contre-expertise**

Le constat est reel et techniquement exact. Quatre precisions / rectifications :

(a) Numero de ligne : la construction des itemStacks est ItemLoaderUtils.java:40, pas :39 (la l.39 est la signature de createAuctionItem). Le reste des references (V3MigrationService:282/299/307/223-250, InsertRequest:74, ZAuctionItem:69/81, ZAuctionManager:802/930) est exact.

(b) Impact legerement surestime : "l'admin n'a aucun moyen d'identifier les items amputes" est trop fort. migrateItems:245 loggue `"Failed to migrate item " + v3Item.getId()`, donc l'ID V3 EST trace. Ce qui manque, c'est l'ID V4 de la ligne items orpheline (la cle generee par createItem n'est jamais loggee), donc la correlation V3->V4 doit se faire a la main en base. La detection est penible, pas impossible.

(c) La correction proposee n°3, prise SEULE, est contre-productive et aggraverait le probleme. Remplacer `catch (SQLException e)` par `catch (Exception e)` en l.307 ferait avaler l'echec : la boucle `for` l.288-292 continuerait, createAuctionItems retournerait normalement, `migrated++` (l.238) s'executerait et `errors` resterait a 0. On passerait d'un lot ampute signale par un warning a un lot ampute compte comme "migre avec succes" et invisible. Le point 3 n'est acceptable que couple au point 4 (verification du nombre de lignes auction_items inserees vs nombre de stacks attendus) ou, mieux, remplace par le point 1 (transaction Sarah `DatabaseConnection.beginTransaction()` autour de createItem + createAuctionItems).

(d) Le meme catch mort existe dans trois autres methodes du fichier : createItem (l.272), createLogEntry (l.363), createPendingTransaction (l.385). Consequence supplementaire non relevee : le `return -1;` de createItem (l.274) est INATTEIGNABLE, donc la garde `if (itemId == -1) { errors.incrementAndGet(); continue; }` (l.230-233) est du code mort. Un echec de createItem remonte en fait au catch(Exception) l.244 — sans consequence ici (aucune ligne items n'est creee, donc pas d'orphelin), mais la logique defensive prevue ne s'execute jamais.

(e) Severite ramenee de haute a moyenne : la perte d'argent est reelle et reseau-wide, mais le declencheur est une panne d'infrastructure SQL (deadlock InnoDB, coupure Hikari, LONGTEXT tronque) pendant une operation admin one-shot `/ah admin migrate`, non declenchable par un joueur et sans lien avec la course multi-serveurs qui est le risque central de cet audit. La correction reste necessaire (le catch mort et l'absence de compensation sont des defauts averes).

**Correctif**

````java
1) Encadrer la creation d'un item par une transaction SQL -- `DatabaseConnection.beginTransaction()` existe dans Sarah et n'est utilise nulle part dans zAuctionHouseV4 -- autour de createItem + createAuctionItems.
2) A defaut, compenser explicitement dans migrateItems :
```java
int itemId = createItem(v3Item);
if (itemId == -1) { errors.incrementAndGet(); continue; }
try {
    createAuctionItems(itemId, v3Item);
} catch (Exception e) {
    SchemaBuilder.delete(Tables.ITEMS, s -> s.where("id", itemId))
            .execute(plugin.getStorageManager().with(PlayerRepository.class).getConnection(), logger);
    throw e;   // comptabilise en erreur, sans laisser de ligne orpheline
}
```
3) Remplacer `catch (SQLException e)` par `catch (Exception e)` dans insertAuctionItem (l.307), sinon aucun echec d'insert n'est jamais rattrape.
4) Verifier apres la boucle que le nombre de lignes auction_items inserees egale le nombre de stacks attendus, et refuser l'item sinon.
````

---

<a id="c-076"></a>

### `C-076` — PurchaseService.exceptionally reinitialise et rediffuse le statut d'un item verrouille par une AUTRE chaine d'achat

- [ ] **Corrigé**
- **Fichier** : `services/PurchaseService.java:176`
- **Catégorie** : Race condition — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// PurchaseService.java:68-76 -- les deux chaines passent la garde et capturent le meme previousStatus
if (item.getStatus() != ItemStatus.IS_PURCHASE_CONFIRM) { ... }
final AtomicReference<ItemStatus> previousStatusHolder = new AtomicReference<>(item.getStatus());

// PurchaseService.java:100-107 -- la chaine perdante sort par failedFuture, la gagnante mute le statut
if (LockToken.noop().value().equals(token.value())) {
    inventoryManager.updateInventory(player);
    resultHolder.set(PurchaseResult.failure("Lock failed", PurchaseFailReason.LOCK_FAILED));
    return failedFuture(new IllegalStateException("Item deja en cours d'achat"));
}
item.setStatus(ItemStatus.IS_BEING_PURCHASED);

// PurchaseService.java:175-184 -- aucune trace de "c'est MOI qui ai change le statut"
// Restore item status only if it was changed (lock was acquired)
if (item.getStatus() == ItemStatus.IS_BEING_PURCHASED) {
    var previousStatus = previousStatusHolder.get();
    item.setStatus(previousStatus);
    clusterBridge.notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, previousStatus)...
}

// ItemStatusListener.java:52-54 -- les autres serveurs appliquent le statut sans aucune garde
if (item == null) return;
item.setStatus(message.newStatus());
```

**Chronologie**

La condition de la ligne 176 teste l'etat GLOBAL de l'item, pas le fait que cette chaine-la l'ait modifie. A comparer avec RemoveService, qui lui suit son propre changement via context.statusChanged (RemoveService.java:240, 308).

T0 -- Serveur A : le joueur double-clique sur ConfirmPurchaseButton. Deux chaines purchaseItem demarrent ; les deux passent la garde ligne 68 (statut encore IS_PURCHASE_CONFIRM) et memorisent previousStatusHolder = IS_PURCHASE_CONFIRM.

T1 -- La chaine 1 acquiert le verrou Redis, execute item.setStatus(IS_BEING_PURCHASED) (ligne 107) et diffuse ce statut au cluster.

T2 -- La chaine 2 recoit LockToken.noop (LOCK_SCRIPT retourne 0) -> failedFuture -> exceptionally ligne 156.

T3 -- Ligne 176, la chaine 2 lit item.getStatus() == IS_BEING_PURCHASED -- statut pose par la chaine 1 -- le juge sien, le remet a IS_PURCHASE_CONFIRM et DIFFUSE notifyItemStatusChange(IS_BEING_PURCHASED -> IS_PURCHASE_CONFIRM) a tout le cluster.

T4 -- Serveur B : ItemStatusListener applique aveuglement le statut (ligne 54). L'item n'est plus marque comme en cours d'achat alors que A est en train de debiter l'acheteur.

T5 -- Sur A comme sur B, tout chemin qui ne fait confiance qu'au statut redevient ouvert : ConfirmHelper.onInventoryClose (ConfirmHelper.java:41) voit le statut attendu et repousse l'item en AVAILABLE, et RemoveService.removeListedItem accepte AVAILABLE ou IS_REMOVE_CONFIRM (RemoveService.java:55). Le vendeur peut lancer un retrait pendant que l'achat se conclut.

**Impact** — Desynchronisation du statut sur tout le cluster pendant une section critique d'achat, ouvrant les chemins de retrait et de reaffichage sur un item en cours de vente. Reproductible par simple double-clic, sans outil ni triche.

**Précision apportée par la contre-expertise**

Le défaut est réel mais le scénario de déclenchement et surtout l'impact sont mal décrits.

A) Déclencheur — le « simple double-clic » est le cas le PLUS faible, pas le plus sûr. zMenu applique par défaut un cooldown de clic par joueur : `Configuration.enableCooldownClick = true` et `cooldownClickMilliseconds = 100` (workspace2.0/[Spigot] zMenu/API/.../configuration/Configuration.java:228,236), appliqué dans VInventoryManager.handleClick:172-177 (`if (Configuration.enableCooldownClick && this.cooldownClick.getOrDefault(...) > System.currentTimeMillis()) { message(CLICK_COOLDOWN); return; }`). Un double-clic humain rapide (<100 ms) est donc avalé ; il faut un clic >100 ms ou le cooldown désactivé. Le vrai déclencheur, immunisé au cooldown (qui est par joueur ET par serveur), est DEUX joueurs — ou le même joueur sur deux serveurs — qui confirment le même item en concurrence : les deux ont vu IS_PURCHASE_CONFIRM (diffusé par ListedItemsButton.java:229-231), le perdant reçoit IS_BEING_PURCHASED via ItemStatusListener:54 depuis le gagnant, échoue au lock, et son `exceptionally` annule cluster-wide le statut du gagnant. Aucun double-clic requis.

B) Impact — il n'y a PAS de duplication d'item ni d'argent, et les « chemins de retrait » ne sont pas réellement ouverts :

- Un achat concurrent reste bloqué par le verrou Redis (LOCK_SCRIPT `SET ... NX PX`, RedisAuctionClusterBridge.java:41-67), tenu de la ligne 107 jusqu'au unlock ligne 137, plus la re-validation en base SOUS VERROU (PurchaseService.java:118-125 : `if (dbItem == null || dbItem.getBuyerUniqueId() != null) ... failedFuture`).

- Un retrait lancé pendant la fenêtre échoue aussi : RemoveService.executeRemoval passe par checkAvailabilityStep (:206-210) puis acquireLockStep/changeStatusAndNotifyStep (:214-240) et sort en ITEM_NOT_AVAILABLE ou LOCK_FAILED tant que le gagnant tient le verrou. La garde de statut RemoveService.java:55 n'est donc pas le dernier rempart.

- Le dommage réel est une désynchronisation d'état en mémoire propagée à tout le cluster : l'item repasse en IS_PURCHASE_CONFIRM pendant le débit, puis ConfirmHelper.onInventoryClose:41-56 (ou onBackClick:70-80) le voit dans l'état « attendu », le passe en AVAILABLE, le rediffuse (`notifyItemStatusChange`) et le ré-affiche (`manager.updateListedItems(item, true, player)`) sur tous les serveurs — listing fantôme d'un item vendu ou en cours de vente, clics qui échouent, caches vidés en boucle. À noter que l'escalade est bornée : après un achat réussi le gagnant pose `auctionItem.setStatus(ItemStatus.PURCHASED)` (ZAuctionManager.purchaseAuctionItem, ~ligne 862) et ItemBoughtListener retire l'item de LISTED sur les autres serveurs, donc une restauration tardive est inerte — c'est bien le rollback intermédiaire vers IS_PURCHASE_CONFIRM puis AVAILABLE qui nuit.

C) Correctif — l'AtomicBoolean `statusChangedByUs` proposé est le bon patch (aligne PurchaseService sur RemovalContext.statusChanged) ; on peut aussi simplement garder sur le token déjà en main : `var token = tokenHolder.get(); if (token != null && !LockToken.noop().value().equals(token.value()) && item.getStatus() == IS_BEING_PURCHASED) {...}`. En revanche, la garde anti-double-clic suggérée dans ConfirmPurchaseButton est un confort local : elle ne couvre pas le cas inter-serveurs / deux joueurs, qui est le cas dominant. Il faut en plus verrouiller ConfirmHelper.onInventoryClose/onBackClick pour qu'ils ne rediffusent AVAILABLE que si ce joueur-là est bien celui qui a posé IS_PURCHASE_CONFIRM.

**Correctif**

```java
Suivre explicitement le changement de statut propre a la chaine, comme le fait deja RemoveService :

final AtomicBoolean statusChangedByUs = new AtomicBoolean(false);
...
item.setStatus(ItemStatus.IS_BEING_PURCHASED);
statusChangedByUs.set(true);
...
}).exceptionally(e -> {
    ...
    if (statusChangedByUs.get() && item.getStatus() == ItemStatus.IS_BEING_PURCHASED) {
        var previousStatus = previousStatusHolder.get();
        item.setStatus(previousStatus);
        clusterBridge.notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, previousStatus)...
    }
    ...
});

Et ajouter dans ConfirmPurchaseButton une garde anti-double-clic (compareAndSet du statut vers IS_BEING_PURCHASED, ou un Set<Integer> d'items en cours par joueur) pour que la seconde chaine n'atteigne meme pas le bridge.
```

---

<a id="c-077"></a>

### `C-077` — Tout l'IO bloquant (Jedis et JDBC) est poste sur ForkJoinPool.commonPool, dimensionne coeurs-1

- [ ] **Corrigé**
- **Fichier** : `REDIS/RedisAuctionClusterBridge.java:159`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// RedisAuctionClusterBridge.java:157-161 (idem l.182 lockItem, 245 unlockItem, 292 notifyItemBought, 318 notifyItemListed, 336 notifyItemStatusChange, 351 removeItem)
    public CompletableFuture<Boolean> checkAvailability(Item item) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String key = itemKey(item);
                String state = jedis.hget(key, FIELD_STATE);

// V4/storage/ZStorageManager.java:176-177 et :215 -- JDBC bloquant, meme pool
    public CompletableFuture<Item> selectItem(int id) {
        return CompletableFuture.supplyAsync(() -> {
...
        return CompletableFuture.supplyAsync(() -> this.with(PlayerRepository.class).selectByName(playerName));

// V4/utils/cache/SortedItemsCache.java:63-64 -- les auteurs ont explicitement evite commonPool ailleurs
    // Custom ForkJoinPool for parallel operations (avoids blocking common pool)
    private final ForkJoinPool forkJoinPool;

// REDIS/src/main/resources/config.yml
  timeout: 2000
  lock-ttl-seconds: 30
```

**Chronologie**

Charge declenchante : 40 joueurs consultent l'hotel des ventes sur le serveur A. Chaque clic sur un item emet un notifyItemStatusChange (bridge l.336 = 1 tache bloquante), chaque ouverture de confirmation en emet un autre a la fermeture (ConfirmHelper.java:48 ou :73), et chaque achat consomme 6 taches bloquantes : checkAvailability (l.159), lockItem (l.182), notifyItemStatusChange (l.336), selectItem JDBC (ZStorageManager.java:177), notifyItemBought (l.292), unlockItem (l.245). Toutes sont postees sur ForkJoinPool.commonPool, dont le parallelisme vaut availableProcessors() - 1 : 3 workers sur une machine 4 coeurs. T0 : Redis ou MySQL ralentit (GC, reseau, failover sentinel). T1 : les 3 workers restent bloques jusqu'a redis-config.timeout (2000 ms) ou jusqu'au timeout JDBC. Le blocage d'IO simple dans une ForkJoinTask ne declenche aucune compensation (seul ManagedBlocker le fait), donc le pool ne cree pas de worker de secours. T2 : toutes les taches suivantes s'empilent en file. T3 : les orTimeout de PurchaseService (config.yml l.992-1004 : check-availability-ms 5000, lock-item-ms 5000, notify-status-change-ms 3000, unlock-item-ms 3000) et de RemoveService se declenchent alors AU MILIEU de la section critique, ce qui produit les verrous fuites decrits par ailleurs. T4, variante petite machine : sur 1 ou 2 coeurs, ForkJoinPool.getCommonPoolParallelism() vaut 1 ; CompletableFuture bascule alors sur son executeur de repli qui cree un Thread par tache. Chaque clic dans l'hotel des ventes cree un thread systeme neuf, sans borne.

**Impact** — Effondrement de l'hotel des ventes sous charge, timeouts en cascade, verrous Redis abandonnes, et sur les petites machines creation de threads non bornee. Le pool est de plus partage avec le reste de la JVM (autres plugins, parallelStream -- SortedItemsCache.java:368 en utilise un), donc la degradation est bidirectionnelle.

**Correctif**

Ajouter un Executor dedie et le passer explicitement a tous les supplyAsync/runAsync : dans ZAuctionHouseRedis, creer un Executors.newFixedThreadPool dimensionne sur redis-config.pool.max-total, le passer au constructeur de RedisAuctionClusterBridge et l'utiliser aux lignes 159, 182, 245, 292, 318, 336, 351 ; le fermer dans onDisable. Cote plugin principal, passer this.plugin.getExecutorService() aux supplyAsync de ZStorageManager.selectItem (l.177) et findUniqueId (l.215) -- ce sont les deux seuls appels du fichier qui l'oublient, updateItem (l.157), updateItems (l.162) et createAuctionItem (l.141) le passent deja. Garder deux pools distincts (Redis et JDBC) pour qu'un blocage Redis n'affame pas les ecritures en base.

---

<a id="c-078"></a>

### `C-078` — Transition EXPIRED/PURCHASED -> DELETED : destruction de l'item sans verrou, sans revalidation et sans aucune diffusion cluster

- [ ] **Corrigé**
- **Fichier** : `services/ExpireService.java:102`
- **Catégorie** : Duplication d’item — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
ExpireService.java:102-106 (processExpiredItem) :
        } else {

            item.setStatus(ItemStatus.DELETED);
            storageManager.updateItem(item, StorageType.DELETED);
        }

ExpireService.java:227-236 (processExpiredItems) fait la meme chose en lot.

La garde de routage cluster ne couvre que LISTED (ExpireService.java:51 et :130) :
        if (storageType == StorageType.LISTED && this.plugin.getAuctionClusterBridge().isDistributed()) {
            expireListedItemClustered(item);
            return;
        }

Et ItemRepository.createUpdateSchema:79-81 ne pose la garde where storage_type que pour la cible EXPIRED :
            if (storageType == StorageType.EXPIRED) {
                schema.where("storage_type", StorageType.LISTED.name());
            }
```

**Chronologie**

T0 - L'item 42 est EXPIRED (onglet 'expires' du vendeur), fin de conservation a l'instant T. Il est en memoire sur A et sur B. Etat Redis : REMOVED, pose au moment de l'expiration.

T1 (T-3s, horloge de B legerement en avance) - Sur B, un balayage declenche getItemIds(EXPIRED) : isExpired() vrai -> processExpiredItems(..., EXPIRED) -> branche else : item.setStatus(DELETED) puis UPDATE items SET storage_type='DELETED' WHERE id=42. AUCUN appel au clusterBridge : ni checkAvailability, ni lockItem, ni removeItem. Aucun message pub/sub, l'etat Redis reste REMOVED.

T2 (T-2s) - Sur A, le vendeur clique l'item 42 dans son onglet 'expires'. RemoveService:127 isExpired() -> faux selon l'horloge de A ; :134 status == REMOVED sur A -> passe.

T3 - executeRemoval : checkAvailability lit REMOVED -> RedisAuctionClusterBridge:167 'if (!STATE_LOCKED.equals(state)) return true;' -> disponible. lockItem accorde.

T4 - ZAuctionManager:602-603 updateItem(DELETED) puis giveItem(player, item) : le vendeur recoit un item que le systeme avait deja detruit deux secondes plus tot.

**Impact** — Creation d'item : un item officiellement detruit est rendu au vendeur. En outre l'UPDATE storage_type='DELETED' WHERE id=? est totalement aveugle et peut ecraser un etat plus recent pose par un autre noeud (par exemple une ligne repassee en PURCHASED). Enfin, les autres serveurs ne sont jamais avertis de la destruction : leurs stores EXPIRED/PURCHASED conservent des objets zombies et reemettent chacun un UPDATE DELETED redondant.

**Précision apportée par la contre-expertise**

Le constat est REEL sur le plan mecanique, mais sa categorie, sa severite et trois de ses affirmations derivees sont a corriger.

A) CATEGORIE : ce n'est PAS une duplication d'item, c'est une perte de destruction / divergence d'etat cluster. Le chemin incrimine (ExpireService:102-106 et :227-236) ne donne aucun item a personne : il detruit. Le pire resultat atteignable est qu'un item cense etre detruit soit finalement rendu UNE fois a son proprietaire sur un autre noeud. Aucun chemin ne produit deux exemplaires : la remise effective passe toujours par RemoveService.executeRemoval, protege par lockItem (RedisAuctionClusterBridge:181-238), donc deux noeuds ne peuvent pas donner l'item simultanement. Requalifier en "coherence-cluster / transition non synchronisee".

B) CONDITION DU SCENARIO : le scenario T1->T4 exige un DECALAGE D'HORLOGE entre A et B. ZItem.java:168-169 : `return System.currentTimeMillis() >= this.expiredAt.getTime() && this.expiredAt.getTime() != 0;` - les deux noeuds comparent la MEME date absolue `expired_at` lue en base. Si l'horloge de A n'est pas en retard, RemoveService:127 (`isExpired()`) rejette le clic avec ITEM_EXPIRED et rien ne se passe. La fenetre exploitable vaut donc le skew NTP (plus la latence asynchrone de executeRemoval), pas une fenetre franche. Ce n'est pas exploitable a volonte par un joueur.

C) AFFIRMATION NON DEMONTREE : "peut ecraser un etat plus recent pose par un autre noeud (par exemple une ligne repassee en PURCHASED)". La branche else ne s'execute que pour un item present dans le store local EXPIRED ou PURCHASED, et aucun chemin ne fait repasser une ligne de EXPIRED vers LISTED/PURCHASED (une remise en vente cree une NOUVELLE ligne via ItemRepository.create:29-40). L'UPDATE aveugle est bien reel, mais aucun ecrasement concret n'est prouve. A presenter comme un durcissement defensif, pas comme un impact avere.

D) POINT 4 DE LA CORRECTION PROPOSEE EST FAUX. "l'objet reste dans le store jusqu'au balayage suivant, ce qui provoque un second updateItems(DELETED) inutile" : la premiere moitie est vraie (la branche else n'appelle pas removeItem), la conclusion non. Au balayage suivant, la garde ExpireService:42-45 (unitaire) et :121-126 (lot) intercepte `item.getStatus() == ItemStatus.DELETED` et appelle `this.auctionManager.removeItem(storageType, item)` en retournant AVANT tout UPDATE. Il n'y a donc pas de second UPDATE sur le meme serveur. Les UPDATE redondants existent bien, mais entre NOEUDS (chaque autre serveur reemet le sien lors de son propre balayage), ce que le constat dit correctement dans son paragraphe "impact".

E) IMPACT REEL A RETENIR, avec une precision : la divergence est auto-cicatrisante. Les items zombies ne persistent pas indefiniment sur les autres noeuds - des qu'un joueur ouvre l'onglet correspondant sur le noeud B, getItemIds (ZAuctionManager:395-420) les detecte expires et les detruit localement. La consequence durable est donc : (1) N UPDATE DELETED redondants pour un meme item, un par noeud ; (2) une fenetre de skew pendant laquelle un item deja detruit en base peut etre rendu a son proprietaire, sans que Redis (STATE_DELETED jamais pose) ni le bus pub/sub (aucun message emis) ne puissent l'en empecher ; (3) une asymetrie architecturale : toutes les autres transitions destructrices passent par verrou + diffusion, celle-ci non.

F) LA CORRECTION PROPOSEE (points 1, 2 et 3) RESTE VALIDE et est la bonne : router EXPIRED/PURCHASED -> DELETED par un `deleteExpiredItemClustered` calque sur expireListedItemClustered:253-311, et passer le storage SOURCE a createUpdateSchema pour poser `where storage_type = source` sur toutes les cibles. Ajouter deux points manquants : (i) la branche else doit aussi appeler `auctionManager.removeItem(storageType, item)` pour ne pas laisser l'objet en store avec le statut DELETED ; (ii) Repository.update:100-106 doit remonter le nombre de lignes affectees, sans quoi la garde `where storage_type` (existante ou ajoutee) reste un no-op silencieux : personne ne peut savoir que l'UPDATE a matche 0 ligne.

**Correctif**

```java
Router toutes les transitions vers DELETED par le meme chemin cluster que LISTED -> EXPIRED :

1) Etendre la garde de routage (l.51 et l.130) :
    if (this.plugin.getAuctionClusterBridge().isDistributed()) {
        if (storageType == StorageType.LISTED) { expireListedItemClustered(item); return; }
        deleteExpiredItemClustered(item, storageType); return;   // EXPIRED et PURCHASED
    }
2) deleteExpiredItemClustered reprend la structure de expireListedItemClustered : checkAvailability -> lockItem -> selectItem (abandon si dbItem == null ou statut inattendu) -> updateItem(DELETED) en verifiant le nombre de lignes -> removeItem(storageType, item) -> clusterBridge.removeItem(item, storageType, StorageType.DELETED) -> unlockItem.
3) Rendre l'UPDATE conditionnel pour toutes les cibles dans ItemRepository.createUpdateSchema en passant le storage source : schema.where("storage_type", source.name()).
4) La branche else n'appelle jamais auctionManager.removeItem(storageType, item) : l'objet reste dans le store jusqu'au balayage suivant, ce qui provoque un second updateItems(DELETED) inutile.
```

---

<a id="c-079"></a>

### `C-079` — Un timeout sur notifyItemBought fait echouer un achat pourtant integralement conclu et peut remettre l'etat Redis a AVAILABLE

- [ ] **Corrigé**
- **Fichier** : `services/PurchaseService.java:135`
- **Catégorie** : Cohérence de cache — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```lua
PurchaseService.java:133-142 - notifyItemBought est un maillon bloquant de la chaine de succes :
                    if (hasMoney) {
                        return auctionManager.purchaseItem(player, item)
                                .thenCompose(v -> clusterBridge.notifyItemBought(player, item)
                                        .orTimeout(performanceConfig.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS))
                                .thenCompose(v -> clusterBridge.unlockItem(item, token, StorageType.LISTED)
                                        .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS))
                                .thenApply(v -> {
                                    resultHolder.set(PurchaseResult.success("Purchase successful", true));

RedisAuctionClusterBridge.notifyItemBought:290-299 - HSET SOLD puis publish, sans atomicite ; l'echec du publish est avale par ZAuctionHouseRedis.sendMessage (log WARNING, future complete normalement).

RedisAuctionClusterBridge UNLOCK_SCRIPT:85-88 - remet AVAILABLE si l'etat est encore LOCKED :
    local currentState = redis.call('HGET', itemKey, 'state')
    if currentState == 'LOCKED' then
        redis.call('HSET', itemKey, 'state', 'AVAILABLE')
    end
```

**Chronologie**

T0 - Serveur A : P1 achete l'item 42. L'achat aboutit completement : argent debite, vendeur credite ou transaction PENDING, item retire du store LISTED, UPDATE en base reussi, item remis a P1.

T1 - La chaine appelle notifyItemBought. Le pool Jedis est epuise (RedisSubscriberRunnable monopolise en permanence une connexion via un subscribe bloquant, et max-total vaut 10 par defaut) ou Redis est momentanement lent. Le .orTimeout(notify-item-action-ms) declenche.

T2 - La chaine bascule dans exceptionally et appelle unlockItem : UNLOCK_SCRIPT s'execute pendant que le runAsync de notifyItemBought attend encore une connexion. HGET state rend encore LOCKED -> le script ecrit state='AVAILABLE'. Le ItemBoughtMessage n'a pas encore ete publie.

T3 - Le runAsync de notifyItemBought obtient enfin une connexion et ecrit state=SOLD puis publie - ou echoue definitivement, auquel cas rien n'est publie et l'echec est avale.

T4 - purchaseItem retourne PurchaseResult.failure(INTERNAL_ERROR) alors que l'achat est integralement conclu. Toute integration externe utilisant AuctionPurchaseService croira l'achat echoue et pourra le rejouer.

T5 - Serveur B : si le publish a echoue, l'item 42 reste dans le store LISTED en memoire et reste affiche a tous. Un joueur B clique : checkAvailability repond disponible (AVAILABLE comme SOLD), lockItem reussit, et seule la revalidation selectItem arrete l'achat.

**Impact** — Un achat reussi est rapporte comme INTERNAL_ERROR, l'etat Redis peut repasser a AVAILABLE pour un item vendu, et les autres serveurs conservent un fantome cliquable jusqu'a leur prochain redemarrage. Toute la coherence d'achat repose alors sur la seule requete SELECT de revalidation.

**Précision apportée par la contre-expertise**

Le mecanisme est confirme, mais deux elements de l'impact annonce sont a corriger a la baisse.

a) La regression d'etat vers AVAILABLE est inerte en pratique. Elle n'ouvre aucune fenetre supplementaire, parce que SOLD n'est deja pas un etat terminal : checkAvailability (RedisAuctionClusterBridge.java:163-170) ne retourne false que pour DELETED et pour un LOCKED dont la cle de verrou existe encore ("null, AVAILABLE, SOLD, REMOVED are all settled states"), et LOCK_SCRIPT (lignes 47-57) ne refuse que DELETED et le LOCKED vivant. Un item SOLD est donc deja verrouillable et deja annonce disponible. Ecrire AVAILABLE a la place de SOLD ne change strictement rien au comportement des autres serveurs. De plus l'etat AVAILABLE n'est durable que si notifyItemBought echoue definitivement ; dans le cas d'un simple retard, le HSET SOLD arrive apres l'unlock et l'etat converge vers SOLD. Le point 3 de la correction proposee (rendre SOLD terminal) reste la bonne mesure, mais c'est un durcissement independant, pas la reparation de ce defaut-ci.

b) Le fantome cliquable sur les autres serveurs (T5) n'est pas cause par ce defaut. Il decoule uniquement de l'echec du publish avale par ZAuctionHouseRedis.sendMessage:277-281 : ItemBoughtListener:44-50 est le seul mecanisme qui retire l'item du store LISTED distant, et il ne s'execute jamais si le message n'est pas publie. Ce fantome apparaitrait a l'identique si notifyItemBought etait hors du chemin critique. La correction proposee 1) ne le corrige donc pas ; il faut une reprise/retry du publish (ou l'ecriture atomique du point 2 avec relecture, la source de verite restant la base MySQL).

Le defaut reellement imputable a PurchaseService.java:135 se reduit donc a : (i) un achat integralement commis (argent retire, item livre, ligne base a jour) est rapporte PurchaseResult.failure(INTERNAL_ERROR) aux consommateurs de l'API AuctionPurchaseService ; (ii) l'unlock peut precede le marquage SOLD. L'appelant interne ConfirmPurchaseButton.java:22 ignore le resultat retourne, donc aucun joueur ne voit ce faux echec aujourd'hui : l'exposition est limitee aux integrations tierces et aux journaux ("Purchase operation timed out for item X" alors que la vente a eu lieu). Aucune duplication d'item ni d'argent n'est possible. Severite reelle : moyenne. Le point 1 de la correction proposee (sortir le notify du chemin critique, l'achat etant deja commis) reste exactement le bon correctif, a condition de conserver l'ordre notify-puis-unlock ou d'appliquer le point 2 : lancer les deux en parallele comme dans l'extrait propose rendrait la course unlock/SOLD plus frequente au lieu de la supprimer.

**Correctif**

```java
1) Sortir la notification du chemin critique : l'achat est deja commis en base, il ne doit plus jamais etre rapporte en echec.
    return auctionManager.purchaseItem(player, item).thenApply(v -> {
        clusterBridge.notifyItemBought(player, item).exceptionally(t -> { logger.severe("notifyItemBought failed for item " + item.getId() + ": " + t.getMessage()); return null; });
        clusterBridge.unlockItem(item, token, StorageType.LISTED).exceptionally(t -> null);
        resultHolder.set(PurchaseResult.success("Purchase successful", true));
        return resultHolder.get();
    });
2) Rendre notifyItemBought atomique et ordonne vis-a-vis de l'unlock : un script Lua unique qui pose state=SOLD, supprime le champ lock et la cle auction:lock:<id> et applique l'EXPIRE - l'unlock devient superflu apres une vente.
3) Rendre SOLD terminal dans LOCK_SCRIPT et checkAvailability : if currentState == 'DELETED' or currentState == 'SOLD' then return 0 end (REMOVED doit rester verrouillable, un item EXPIRED etant encore reclamable).
```

---

<a id="c-080"></a>

### `C-080` — Vente : les items quittent l'inventaire du joueur avant l'INSERT et rien ne force la sauvegarde du profil -- un crash entre les deux duplique le lot

- [ ] **Corrigé**
- **Fichier** : `services/SellService.java:105`
- **Catégorie** : Duplication d’item — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// SellService.java:104-110
// Remove items from their slots
removeItemsFromSlots(player, validSlotItems);

var storageManager = this.plugin.getStorageManager();
storageManager.createAuctionItem(player, price, expiredAt, itemsToSell, auctionEconomy).thenAccept(auctionItem -> {
    this.postSell(player, auctionItem, auctionEconomy, taxResult);

// SellService.java:192-217 -- removeItemsFromSlots ne touche QUE l'inventaire en memoire
private void removeItemsFromSlots(Player player, Map<Integer, ItemStack> slotItems) {
    PlayerInventory inventory = player.getInventory();
    ...
    inventory.setItem(slot, null);

// ZStorageManager.java:140-145 -- l'INSERT part sur un autre thread et commite immediatement
return CompletableFuture.supplyAsync(() -> {
    int itemId = with(ItemRepository.class).create(seller, ItemType.AUCTION, price, expiredAt, auctionEconomy);
    return with(AuctionItemRepository.class).create(seller, itemId, price, expiredAt, itemStacks, auctionEconomy);
}, this.plugin.getExecutorService());
```

**Chronologie**

L'inventaire du joueur n'est persiste par le serveur Minecraft qu'a la deconnexion ou lors d'une sauvegarde periodique, alors que l'INSERT est commite dans le MySQL PARTAGE en quelques millisecondes. La fenetre de divergence n'est donc pas de quelques millisecondes mais de plusieurs minutes.

T0 -- Serveur A : le joueur vend 64 diamants. removeItemsFromSlots vide le slot EN MEMOIRE uniquement.

T1 (T0+50ms) -- createAuctionItem commite items + auction_items. L'annonce est visible depuis tous les serveurs (et postSell la diffuse via notifyItemListed).

T2 (T0+3min) -- Le serveur A crashe / est kill -9 avant la prochaine sauvegarde du playerdata.

T3 -- Au redemarrage, le playerdata du joueur est restaure a son etat d'avant la vente : il a de nouveau ses 64 diamants. L'annonce, elle, est toujours en base et toujours LISTED.

T4 -- Le joueur achete ou fait acheter sa propre annonce, ou la retire via l'onglet "en vente" : il obtient un second lot de 64 diamants.

La manoeuvre est reproductible sur un serveur instable et se declenche aussi sur un simple /reload ou un kick brutal.

**Impact** — Duplication d'item a chaque crash survenant entre la mise en vente et la sauvegarde du profil. C'est le vecteur de dupe classique des hotels des ventes, et il est ici structurel puisque rien ne relie la durabilite de l'INSERT a celle de l'inventaire.

**Précision apportée par la contre-expertise**

Le constat est réel mais deux affirmations du scénario sont fausses et doivent être retirées, et la sévérité doit être ramenée à "moyenne".

CORRECTIONS AU SCÉNARIO :

1. FAUX : « se déclenche aussi sur un simple /reload ou un kick brutal ». Un `/reload` Bukkit garde les joueurs connectés et ne restaure aucun playerdata ancien ; un kick déclenche `PlayerQuitEvent` et Paper sauvegarde le playerdata à la déconnexion. Seul un crash DUR (kill -9, OOM-kill, coupure, freeze du watchdog) avant la prochaine sauvegarde provoque le rollback. La manœuvre n'est donc PAS déclenchable à volonté par le joueur sans primitive de crash.

2. IMPRÉCIS : la fenêtre n'est pas ouverte indéfiniment. Elle est bornée par `bukkit.yml ticks-per.autosave` (6000 ticks ≈ 5 min par défaut, `player-auto-save-rate` sur Paper). Fenêtre max ≈ 5 minutes, pas « plusieurs minutes » indéterminées.

SÉVÉRITÉ : "haute" → "moyenne". Le vecteur requiert un crash non contrôlé par l'attaquant, n'est pas déterministe, et l'asymétrie playerdata/base externe est partagée par tout plugin Bukkit qui déplace des items vers une DB externe. Reste exploitable en masse sur un serveur instable, d'où "moyenne" et non "basse".

DÉFAUT SUPPLÉMENTAIRE SUR LE MÊME CHEMIN (à intégrer au correctif) :

`SellService.java:111-130` — le `.exceptionally(...)` est enregistré sans variante `Async`, il s'exécute donc sur le thread qui a complété le future, c'est-à-dire un thread du `asyncExecutor` (pool fixe de 4, `ZAuctionPlugin.java:82`). Or ligne 117-123 :

if (player.isOnline() && itemsToSell != null) {

itemsToSell.forEach(itemStack -> {

if (itemStack != null) {

player.getInventory().addItem(itemStack);

}

});

}

L'inventaire du joueur est donc muté HORS du thread principal / de région (illégal sur Paper et sur Folia). Le correctif proposé doit envelopper ce bloc de remboursement dans `this.plugin.getScheduler().runAtEntity(player, task -> { ... })` avant d'y ajouter le second `saveData()`.

RÉSERVES SUR LE CORRECTIF PROPOSÉ :

- `player.saveData()` est une écriture disque SYNCHRONE sur le thread principal/de région, exécutée à chaque vente. Sur un serveur chargé c'est un coût TPS non négligeable ; à mesurer, éventuellement à rendre optionnel via config.

- Le fallback `addItem` de la branche d'échec peut échouer si l'inventaire est plein entre-temps (le retour de `addItem` n'est pas testé) : les items seraient alors perdus. À corriger en même temps (drop au sol ou dépôt dans les items expirés).

**Correctif**

```java
Forcer la persistance du profil immediatement apres avoir retire les items, et ne considerer la vente comme acquise qu'ensuite :

this.plugin.getScheduler().runAtEntity(player, task -> {
    ...
    removeItemsFromSlots(player, validSlotItems);
    player.saveData();                       // le playerdata sur disque n'a plus les items
    var storageManager = this.plugin.getStorageManager();
    storageManager.createAuctionItem(...)
        ...
});

Sur un reseau avec un plugin de synchronisation d'inventaire, remplacer saveData() par l'appel de sauvegarde de ce plugin. Compter aussi les cas d'echec : si l'INSERT echoue APRES saveData(), le remboursement doit lui aussi etre suivi d'un saveData().
```

---

<a id="c-081"></a>

### `C-081` — admin generate execute jusqu'a des dizaines de milliers de SELECT bloquants sur le thread principal

- [ ] **Corrigé**
- **Fichier** : `command/commands/admin/CommandAuctionAdminGenerate.java:118`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
CommandAuctionAdminGenerate.generateAuctionItems (verifie) :

        // Pre-generate all data on the main thread
        List<GenerationData> dataList = new ArrayList<>(amount);
        Map<String, UUID> nameToUuidCache = new HashMap<>();
        var playerRepository = plugin.getStorageManager().with(PlayerRepository.class);

        for (int i = 0; i < amount; i++) {
            ...
            UUID sellerUUID = nameToUuidCache.get(sellerName);
            if (sellerUUID == null) {
                sellerUUID = playerRepository.selectByName(sellerName);

Toutes les commandes du plugin sont synchrones : VCommand.java:27 `private boolean runAsync = false;` et un grep sur setRunAsync dans src/ et API/ ne retourne QUE sa definition (VCommand.java:318) et sa lecture (ZCommandManager.java:158 `if (command.isRunAsync())`). Aucune commande n'appelle setRunAsync.

Aucun index sur players(name) : grep 'index(' sur storage/migrations/ ne retourne AUCUNE occurrence.
```

**Chronologie**

T0 : un admin tape /ah admin generate 100000 (le plafond autorise par le code) sur le serveur A d'un cluster, pour tester la montee en charge.

T1 : la commande s'execute sur le thread principal (isRunAsync est faux pour toutes les commandes). La boucle de pre-generation tourne 100 000 fois, sur le main thread, avant meme le runAsync qui suit.

T2 : pour chaque pseudo aleatoire non encore vu, playerRepository.selectByName fait un SELECT * FROM %prefix%players WHERE name = ? strictement synchrone (Repository.select est bloquant), avec emprunt/restitution d'une connexion Hikari.

T3 : players n'ayant aucun index sur name, chaque requete est un balayage complet de table. Sur une base de 100 000 joueurs et des dizaines de milliers de pseudos distincts generes, cela represente des milliards de comparaisons de lignes avant le premier tick rendu.

T4 : le serveur A est gele ; le watchdog Spigot le tue. Les joueurs de A sont deconnectes en plein achat, alors que le serveur B continue de tourner sur la meme base : toute chaine d'achat interrompue apres withdraw et avant l'UPDATE laisse un etat incoherent que rien ne repare.

**Impact** — Gel complet du serveur puis kill par le watchdog, declenche par une commande d'administration dont la seule protection est une double frappe sous 30 secondes. Sur un cluster, ce kill brutal interrompt des sections critiques sous verrou Redis.

**Précision apportée par la contre-expertise**

Le defaut est reel et la correction proposee est valide. Deux precisions de description a apporter.

A) Le nombre exact de SELECT bloquants. Le constat laisse entendre un SELECT quasi par iteration. En realite l'espace de noms de generateRandomName (lignes 242-253) est fini : FIRST_NAMES = 50 (ligne 26), LAST_SUFFIXES = 50 (ligne 28), et les 4 formats donnent 2500 + 2500 + 2500 + (50 * 1000) = environ 57 500 noms distincts possibles. A amount = 100000, le nombre attendu de noms DISTINCTS, donc de selectByName reellement executes, est d'environ 27 000 (formats 0/1/2 saturent leurs 2500 valeurs, le format 3 couvre 50000*(1-e^-0.5) ~ 19 700). Le titre "des dizaines de milliers de SELECT" reste donc exact, mais la valeur est ~27 000, pas 100 000. A amount = 10000 (valeur proposee en tab-completion) on est deja a ~7 000 SELECT bloquants. Independamment des SELECT, la boucle elle-meme fait bien 100 000 iterations et alloue 100 000 GenerationData sur le main thread.

B) Le maillon T4 (dupe / etat incoherent apres kill du watchdog) n'est PAS prouve par ce fichier. C'est une consequence plausible mais extrapolee ; le defaut se suffit a lui-meme comme gel du thread principal. La categorie "performance" est la bonne, pas "duplication".

Severite ramenee a moyenne : l'atteinte est un gel complet + kill watchdog (impact eleve), mais la surface d'exploitation est nulle pour un joueur - il faut la permission ZAUCTIONHOUSE_ADMIN, une frappe volontaire, et une seconde frappe identique sous 30 s. C'est un outil de benchmark declenche deliberement par un administrateur, pas un chemin atteignable en jeu.

Corrections 1) 2) 3) du constat sont toutes applicables telles quelles : playerRepository.select() existe bien (PlayerRepository, "public List<PlayerDTO> select() { return selectAll(PlayerDTO.class); }") et PlayerDTO expose name() et unique_id(). Nuance sur le point 2 : charger toute la table players en memoire est lui aussi couteux sur une grosse base ; preferer, dans le bloc runAsync, un seul select(PlayerDTO.class, schema -> schema.whereIn("name", nomsDistincts)) apres avoir pre-genere les noms. Le point 3 (index sur %prefix%players(name)) reste necessaire car ZStorageManager.findUniqueId (ZStorageManager.java:214-216) appelle aussi selectByName.

**Correctif**

1) Deplacer TOUTE la pre-generation dans le bloc plugin.getScheduler().runAsync deja present plus bas dans la methode.
2) Remplacer les N selectByName par une seule lecture : Map<String, UUID> byName = playerRepository.select().stream().collect(Collectors.toMap(PlayerDTO::name, PlayerDTO::unique_id, (a, b) -> a));
3) Ajouter une migration creant un index sur %prefix%players(name) si la recherche par nom est conservee ailleurs (elle l'est : ZStorageManager.findUniqueId).

---

<a id="c-082"></a>

### `C-082` — postSell s'execute sur l'executor asynchrone et mute l'index IntArrayList non thread-safe pendant que le thread principal le modifie

- [ ] **Corrigé**
- **Fichier** : `services/SellService.java:370`
- **Catégorie** : Race condition — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// SellService.java:107-109 -- thenAccept sans executor : postSell tourne sur le thread de l'executor de la base
var storageManager = this.plugin.getStorageManager();
storageManager.createAuctionItem(player, price, expiredAt, itemsToSell, auctionEconomy).thenAccept(auctionItem -> {
    this.postSell(player, auctionItem, auctionEconomy, taxResult);

// ZStorageManager.java:141-144 -- le future est complete sur plugin.getExecutorService()
return CompletableFuture.supplyAsync(() -> { ... }, this.plugin.getExecutorService());

// SellService.java:373-382 -- mutations d'etat global + API Bukkit depuis ce thread
this.plugin.getCategoryManager().applyCategories(auctionItem);
this.manager.addItem(StorageType.LISTED, auctionItem);
this.manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
this.manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING);
this.manager.updateListedItems(auctionItem, true, player);

// ZAuctionManager.java:236-240 -> 435-441 -> 468-472 : la structure d'index n'est PAS concurrente
public void addItem(StorageType storageType, Item item) {
    var storage = this.storageItemsById.get(storageType);
    storage.put(item.getId(), item);
    this.indexItem(storageType, item);

private void addToIndex(Map<UUID, IntList> index, UUID owner, int itemId) {
    if (owner == null) return;
    index.computeIfAbsent(owner, uuid -> new IntArrayList()).add(itemId);   // IntArrayList : non thread-safe
}

private void removeFromIndex(Map<UUID, IntList> index, UUID owner, int itemId) {
    IntList ids = index.get(owner);
    if (ids == null) return;
    ids.rem(itemId);
```

**Chronologie**

Les Map sont bien des ConcurrentHashMap (ZAuctionManager.java:82), mais leurs VALEURS sont des IntArrayList fastutil, muables et non synchronisees. Tous les autres chemins (listeners Redis, expiration, achat) passent par runNextTick et s'executent donc sur le thread principal ; postSell est le seul a muter ces listes depuis un thread de pool.

T0 -- Serveur A, thread principal : la tache d'expiration traite les annonces du vendeur X et appelle removeItem(LISTED, item) -> removeFromIndex -> idsListedByOwner.get(X).rem(id). L'IntArrayList decale son tableau interne et decremente sa taille.

T1 -- Exactement au meme instant, le thread de l'executor termine createAuctionItem pour une nouvelle vente de X et execute postSell -> addItem(LISTED, ...) -> addToIndex -> idsListedByOwner.get(X).add(newId). L'IntArrayList lit une taille perimee, redimensionne son tableau ou ecrit a un index deja reutilise.

T2 -- Resultat non deterministe : identifiant perdu (l'annonce n'apparait jamais dans l'onglet "en vente" du joueur et devient irrecuperable), identifiant duplique dans l'index (l'annonce s'affiche deux fois), ou ArrayIndexOutOfBoundsException remontee dans un thread de pool sans handler -- le future echoue et SellService.exceptionally (ligne 111) REMBOURSE les items alors que l'INSERT est deja commite, ce qui duplique le lot.

T3 -- postSell appelle aussi updateListedItems, qui itere sur getOnlinePlayers() et manipule les inventaires depuis ce meme thread de pool, hors du modele de threads Bukkit/Folia.

**Impact** — Corruption silencieuse de l'index par vendeur (items invisibles et donc perdus, ou dupliques a l'affichage), et exception possible dans le pool qui declenche le remboursement post-commit -- donc, indirectement, une duplication d'item.

**Précision apportée par la contre-expertise**

Description exacte apres lecture du code :

A. L'IMPACT ANNONCE EST FAUX — les trois index par proprietaire sont EN ECRITURE SEULE. Un grep exhaustif sur `idsListedByOwner|idsExpiredByOwner|idsPurchasedByBuyer|getIndexFor|indexItem|deindexItem|addToIndex|removeFromIndex` dans zAuctionHouseV4 + l'addon Redis ne rend QUE les declarations (ZAuctionManager.java:64-66) et les chemins d'ecriture (239, 259, 435-484). Aucune lecture, aucun getter, aucun accesseur API. Les onglets du joueur n'utilisent pas ces index : getPlayerSellingItems (ZAuctionManager.java:313-316), getExpiredItems (302-305) et getPurchasedItems (324-327) passent par `getItemIds(storageType, predicate, comparator)` (395-433) qui iterent `this.storageItemsById` — une ConcurrentHashMap (ligne 82). Donc "identifiant perdu, annonce jamais visible dans l'onglet en vente et irrecuperable" et "annonce affichee deux fois" sont infondes : un index corrompu est inerte. Seule survit la branche AIOOBE de T2.

B. "postSell est le seul a muter ces listes depuis un thread de pool" EST FAUX. Au moins deux autres chemins mutent les memes structures hors thread principal :

- ExpireService.java:202-213 : `configuration.getExpireExpiration().getExpiration(this.plugin.getOfflinePermission(), item.getSeller()).whenComplete((expiration, throwable) -> { ... this.auctionManager.addItem(StorageType.EXPIRED, item); ...})` — sans runNextTick. Quand `enable-permission` est actif, ce future vient du hook LuckPerms offline (ExpirationConfiguration.java:48-64, `offlinePermission.hasPermissions(...).handle(...)`), donc asynchrone. Idem ExpireService.java:79-85 et 169/187 qui s'executent sur le thread appelant de processExpiredItems, lui-meme declenchable depuis getItemIds (ZAuctionManager.java:419) appele dans des caches calcules en async (prepareCacheAsync, ligne 148-158).

- CommandAuctionAdminAdd.java:103-104 / 113-116 / 125-128 : `createAuctionItem(...).thenAccept(item -> { this.auctionManager.addItem(StorageType.LISTED/EXPIRED/PURCHASED, item); ...})` — exactement le meme defaut que SellService.

En revanche la partie "les listeners Redis passent par runNextTick" est exacte (ItemListedListener.java:42-44, ItemBoughtListener.java:44-49 et 104-106, ItemRemovedListener.java:34-39/58-60/83-85, ItemStatusListener.java:39). Le probleme est donc PLUS LARGE que decrit, pas plus etroit — le correctif doit couvrir ExpireService et CommandAuctionAdminAdd, pas seulement SellService.

C. T3 EST INEXACT. updateListedItems (ZAuctionManager.java:938-976) ne manipule PAS les inventaires depuis le thread de pool : la boucle est deja dans `this.sortedItemsCache.ensureCacheValidAsync().thenRun(...)` (ligne 953) pour TOUS les appelants, et chaque acces inventaire est encapsule dans `this.plugin.getScheduler().runAtEntity(onlinePlayer, w -> {...})` (ligne 958). Ce qui tourne hors thread principal, c'est uniquement l'iteration de `getOnlinePlayers()` — comportement identique quel que soit l'appelant, donc pas un defaut specifique a postSell.

D. LE CORRECTIF PROPOSE NE COMPILE PAS. Ce n'est pas fastutil : la classe est `fr.maxlego08.zauctionhouse.api.utils.IntArrayList` (API/src/main/java/fr/maxlego08/zauctionhouse/api/utils/IntArrayList.java), dont le javadoc dit explicitement "a lightweight alternative to FastUtil's IntArrayList". Un grep `fastutil` sur tout le projet (java + gradle) ne retourne rien, et `IntLists` n'existe nulle part. `IntLists.synchronize(new IntArrayList())` est donc impossible ; il faudrait soit une implementation `SynchronizedIntList` maison, soit synchroniser addToIndex/removeFromIndex.

E. ENONCE CORRIGE DU RISQUE. Le seul canal de duplication reel est : postSell (SellService.java:370-408) s'execute sur le pool de 4 threads ; si UNE quelconque exception y est levee — dont une AIOOBE de IntArrayList.add lors d'une mutation concurrente de l'index du meme vendeur, mais aussi tout NPE dans applyCategories, getItemDisplay, le webhook Discord ou broadcastSell — le `exceptionally` (SellService.java:111-130) rend les items au joueur alors que la ligne est deja commitee en base et, si l'exception survient apres `addItem` (ligne 375), l'annonce reste aussi presente en memoire et en base. Le vrai defaut a corriger en priorite est ce REMBOURSEMENT POST-COMMIT non discriminant, pas la corruption de l'index (inerte). Correctif recommande : (1) ramener postSell sur le thread de l'entite via runAtEntity et l'entourer d'un try/catch qui journalise SANS rembourser, (2) restreindre le `exceptionally` aux echecs du future de creation (avant commit) uniquement, (3) appliquer le meme traitement a CommandAuctionAdminAdd.java:103-128 et ExpireService.java:202-213.

Severite ramenee de "haute" a "moyenne" : la fenetre d'interleaving exige deux mutations simultanees sur la liste du MEME joueur, et l'impact "items invisibles/dupliques a l'affichage" qui portait la severite haute n'existe pas puisque l'index n'est jamais lu.

**Correctif**

```java
Ramener postSell sur le thread de l'entite comme le fait le reste du service :

storageManager.createAuctionItem(player, price, expiredAt, itemsToSell, auctionEconomy)
    .thenAccept(auctionItem -> this.plugin.getScheduler().runAtEntity(player, t -> {
        try {
            this.postSell(player, auctionItem, auctionEconomy, taxResult);
        } catch (Exception e) {
            this.plugin.getLogger().severe("postSell failed for item " + auctionItem.getId() + ": " + e.getMessage());
        }
        resultFuture.complete(SellResult.success("Item listed successfully", auctionItem));
    }))

Et, par securite, rendre l'index reellement concurrent dans ZAuctionManager :

index.computeIfAbsent(owner, uuid -> IntLists.synchronize(new IntArrayList()))
```

---

<a id="c-083"></a>

### `C-083` — removeListedItem : la destination est calculee avant le verrou mais canReceiveItem est reevalue apres, ce qui diffuse un DELETED pour un item parti en EXPIRED

- [ ] **Corrigé**
- **Fichier** : `services/RemoveService.java:63`
- **Catégorie** : Perte d’item — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
RemoveService.java:62-65 - decision prise AVANT toute la chaine asynchrone :
        var listedConfig = this.plugin.getConfiguration().getActions().listed();
        StorageType destination = (listedConfig.giveItem() && item.canReceiveItem(player)) ? StorageType.DELETED : StorageType.EXPIRED;

        return executeRemoval(ItemStatus.IS_BEING_REMOVED, player, item, () -> manager.updateInventory(player), () -> manager.removeListedItem(player, item), StorageType.LISTED, destination);

ZAuctionManager.removeListedItem:520-532 reevalue la MEME condition bien plus tard :
        if (configuration.getActions().listed().giveItem() && item.canReceiveItem(player)) {
            updateFuture = storageManager.updateItem(item, StorageType.DELETED);
            giveItem(player, item);
        } else {
            ...
            addItem(StorageType.EXPIRED, item);
            updateFuture = storageManager.updateItem(item, StorageType.EXPIRED);
        }

Et RemoveService.executeLocalRemovalStep:250-253 diffuse la destination PRE-CALCULEE :
        return context.onLocalRemoval.get().thenCompose(v -> {
            context.localRemovalCompleted = true;
            return clusterBridge.removeItem(context.item, context.storageType, context.destinationStorageType)...
```

**Chronologie**

Config action.remove-listed-item.give-item: true, open-confirm-inventory: false. Le vendeur a EXACTEMENT un slot libre et deux annonces A et B.

T0 - Serveur S : clic sur A. RemoveService:63 evalue canReceiveItem -> true -> destinationA = DELETED. La chaine part sur Redis (checkAvailability puis lockItem, deux allers-retours).

T1 - Serveur S : clic sur B au tick suivant, l'item A n'est pas encore rendu. canReceiveItem -> toujours true -> destinationB = DELETED.

T2 - La chaine de A atteint ZAuctionManager:520 : canReceiveItem encore vrai -> giveItem remplit le dernier slot. Coherent.

T3 - La chaine de B atteint ZAuctionManager:520 : canReceiveItem vaut maintenant FALSE -> branche else -> addItem(EXPIRED) et updateItem(item, EXPIRED). La ligne DB devient EXPIRED, l'item vit dans le store EXPIRED de S.

T4 - executeLocalRemovalStep diffuse malgre tout clusterBridge.removeItem(item, LISTED, destinationB = DELETED). Cote Redis, state = DELETED puis publish ItemRemovedMessage(id, LISTED, DELETED).

T5 - Serveurs A et C : ItemRemovedListener retire l'item de tous les stores ; destination == DELETED donc l'etape 2 ne le re-ajoute pas. L'item n'existe plus nulle part ailleurs.

T6 - Serveur S : le vendeur ouvre son onglet 'expires', l'item y est. Il clique -> removeExpiredItem -> checkAvailability lit state='DELETED' -> RedisAuctionClusterBridge:161 return false -> ITEM_NOT_AVAILABLE. L'item est INRECLAMABLE.

**Impact** — L'item existe en base (EXPIRED) mais est refuse par le verrou distribue jusqu'a expiration de la cle auction:item:<id> (item-state-ttl-seconds, 24 h par defaut ; jamais si cette cle est configuree a 0). Entre-temps il a disparu de tous les autres serveurs. Le declencheur - deux clics rapides quand il ne reste qu'un slot - est atteignable en quelques secondes par un joueur ordinaire.

**Précision apportée par la contre-expertise**

Le constat est reel mais deux points doivent etre precises. (1) La configuration du scenario n'est PAS celle par defaut : src/main/resources/config.yml:788 livre `give-item: false` et config.yml:795 livre `open-confirm-inventory: true`. Avec les valeurs par defaut, destination vaut toujours EXPIRED des deux cotes et le bug ne peut pas se produire ; il faut qu'un administrateur active `action.remove-listed-item.give-item: true`. Avec open-confirm-inventory laisse a true, la course reste atteignable (deux cycles clic + confirmation) mais la fenetre est plus longue a exploiter. La fenetre reelle est celle de la latence Redis de la premiere chaine (checkAvailability puis lockItem puis notifyItemStatusChange) : deux clics dans le MEME tick (plusieurs paquets de clic traites dans la meme boucle de tick) sont le declencheur fiable, pas necessairement « deux clics en quelques secondes ». (2) L'impact « jamais si item-state-ttl-seconds est a 0 » est trop absolu : quand itemStateTtl est null, removeItem (RedisAuctionClusterBridge.java:357-359) n'appelle simplement pas expire, mais la cle auction:item:<id> peut conserver le TTL pose au listing par notifyItemListed (ligne 325-327, item-listed-ttl), car hset ne reinitialise pas le TTL. La persistance eternelle n'est donc garantie que si les deux TTL sont a 0. A l'inverse, l'impact est aggrave dans un cas non mentionne : si expire-expiration est > 0, l'item bloque dans le store EXPIRED porte un expiredAt (ZAuctionManager.java:527-528) ; s'il est atteint avant l'expiration de la cle Redis, RemoveService.java:127-131 refuse desormais la reclamation avec ITEM_EXPIRED et la perte devient definitive. Nit de citation : le `return false;` du bridge est a la ligne 164 (test STATE_DELETED en 163), pas 161. La correction proposee (remonter la destination effective depuis ZAuctionManager.removeListedItem via CompletableFuture<StorageType> et rendre RemovalContext.destinationStorageType mutable) est correcte et suffisante ; le point 3 (reserver le slot) est un plus facultatif.

**Correctif**

```java
Ne calculer la destination qu'au moment ou la decision est reellement prise, et la remonter a RemoveService.
1) ZAuctionManager.removeListedItem retourne la destination effective :
    public CompletableFuture<StorageType> removeListedItem(Player player, Item item) {
        boolean give = configuration.getActions().listed().giveItem() && item.canReceiveItem(player);
        StorageType effective = give ? StorageType.DELETED : StorageType.EXPIRED;
        ...
        return updateFuture.thenApply(v -> effective);
    }
2) RemovalContext.destinationStorageType devient non final et executeLocalRemovalStep utilise la valeur reelle :
    return context.onLocalRemoval.get().thenCompose(effectiveDestination -> {
        context.destinationStorageType = effectiveDestination;
        context.localRemovalCompleted = true;
        return clusterBridge.removeItem(context.item, context.storageType, context.destinationStorageType)...
    });
(La signature du Supplier passe de Supplier<CompletableFuture<Void>> a Supplier<CompletableFuture<StorageType>> ; les trois autres variantes retournent StorageType.DELETED.)
3) En complement, capturer int reservedSlot = player.getInventory().firstEmpty() au moment de la decision et le reutiliser plutot que de reevaluer firstEmpty().
```

---

<a id="c-084"></a>

### `C-084` — withdraw et deposit sont appeles depuis commonPool et l'asyncExecutor, sans aucune serialisation par compte

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:802`
- **Catégorie** : Duplication d’argent — **Sévérité** : Moyenne — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
// economy/ZAuctionEconomy.java:86-94 -- appels directs, synchrones, sur le thread appelant
    @Override
    public void deposit(UUID playerId, BigDecimal value, String reason) {
        this.currencyProvider.deposit(playerId, value, reason);
    }

    @Override
    public void withdraw(UUID playerId, BigDecimal value, String reason) {
        this.currencyProvider.withdraw(playerId, value, reason);
    }

// ZAuctionManager.java:800-802 (sur commonPool, cf. le constat sur le confinement de threads)
        // On retire l'argent de l'acheteur
        try {
            auctionEconomy.withdraw(player.getUniqueId(), buyerPays, args(auctionEconomy.getWithdrawReason(), "%seller%", sellerName, "%items%", items));

// ZAuctionManager.java:821-823
            transactionStatus = TransactionStatus.RETRIEVED;
            try {
                auctionEconomy.deposit(seller.getUniqueId(), sellerReceives, args(auctionEconomy.getDepositReason(), "%buyer%", player.getName(), "%items%", items));

// ClaimService.java:71 (asyncExecutor) et SellService.java:87, :98, :126, :346 (thread appelant)
                            economy.deposit(player.getUniqueId(), economyTotal, depositReason);
```

**Chronologie**

T0 serveur A : deux acheteurs distincts confirment au meme instant l'achat de deux items du meme vendeur. Les deux chaines PurchaseService tournent sur deux workers distincts de ForkJoinPool.commonPool (aucun thenComposeAsync avec executeur, aucun verrou par compte). T1 A : commonPool-worker-1 execute ZAuctionManager.java:823 deposit(vendeur, 1000) pendant que commonPool-worker-2 execute deposit(vendeur, 500). Les implementations Vault courantes (EssentialsX, CMI) realisent un getBalance() puis un setBalance() non atomiques sur le thread appelant : les deux lectures voient le meme solde initial, la seconde ecriture ecrase la premiere et le vendeur perd 1000. T2 A, variante symetrique : le meme acheteur enchaine deux achats (double session, ou un achat par serveur avec une economie MySQL partagee) ; les deux withdraw (l.802) partent en parallele, la seconde ecriture ecrase la premiere et l'acheteur ne paie qu'une fois pour deux items -- duplication d'argent. T3 : le meme provider est simultanement sollicite depuis l'asyncExecutor par ClaimService.java:71 (auto-claim au join) et depuis le thread appelant par SellService.java:346 (prelevement de la taxe de vente), soit trois familles de threads differentes sans aucun point de serialisation.

**Impact** — Soldes corrompus dans les deux sens selon l'entrelacement : perte pour le vendeur, ou paiement unique pour deux achats cote acheteur. Le plugin ne pose aucun verrou par UUID et n'impose nulle part le thread principal, alors que la quasi-totalite des providers Vault documente une utilisation mono-thread. Les transactions ecrites en base (ZAuctionManager.java:839-852) enregistrent un before/after relu apres coup par auctionEconomy.get(), donc elles refletent le solde corrompu au lieu de reveler l'ecrasement.

**Correctif**

1) Court terme : serialiser toutes les mutations d'economie par compte dans ZAuctionEconomy, par exemple private static final ConcurrentHashMap<UUID, Object> LOCKS = new ConcurrentHashMap<>(); puis synchronized (LOCKS.computeIfAbsent(playerId, k -> new Object())) { this.currencyProvider.withdraw(...); } dans deposit et withdraw. 2) Cible : executer toute mutation d'economie sur le thread principal via plugin.getScheduler().runNextTick(...) en retournant un CompletableFuture, et chainer la suite de purchaseAuctionItem dessus -- cela resout simultanement le probleme de thread-safety des providers Vault et celui du confinement de threads. 3) Documenter explicitement le contrat de thread attendu dans l'interface API AuctionEconomy, qui n'en dit rien aujourd'hui.

---

<a id="c-085"></a>

### `C-085` — /ah admin generate attribue les items generes a de vrais joueurs quand le pseudo aleatoire existe deja en base

- [ ] **Corrigé**
- **Fichier** : `command/commands/admin/CommandAuctionAdminGenerate.java:132`
- **Catégorie** : Perte d’item — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
// CommandAuctionAdminGenerate.java:126-137 -- si le nom existe en base, on REUTILISE l'UUID du vrai joueur
String sellerName = generateRandomName(random);

// Check cache first
UUID sellerUUID = nameToUuidCache.get(sellerName);
if (sellerUUID == null) {
    // Check database
    sellerUUID = playerRepository.selectByName(sellerName);
    if (sellerUUID == null) {
        sellerUUID = UUID.randomUUID();
    }
    nameToUuidCache.put(sellerName, sellerUUID);
}

// CommandAuctionAdminGenerate.java:242-253 -- espace de noms tres reduit
private String generateRandomName(ThreadLocalRandom random) {
    String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
    String suffix = LAST_SUFFIXES[random.nextInt(LAST_SUFFIXES.length)];

    int format = random.nextInt(4);
    return switch (format) {
        case 0 -> firstName + suffix;
        case 1 -> firstName + "_" + suffix;
        case 2 -> suffix + firstName;
        default -> firstName + random.nextInt(1000);
    };
}
```

**Chronologie**

L'intention du code est de ne pas creer de doublon de pseudo dans la table des joueurs ; l'effet est d'attribuer les annonces de test au VRAI proprietaire du pseudo.

T0 -- Un administrateur lance /ah admin generate 5000 pour tester l'affichage ou les performances.

T1 -- L'espace de noms est le produit FIRST_NAMES x LAST_SUFFIXES x 4 formats : sur 5000 tirages, les collisions avec les pseudos reellement presents dans la table players sont probables, et certaines sont massives via le format 3 (prenom + nombre a trois chiffres).

T2 -- Pour chaque collision, selectByName renvoie l'UUID du vrai joueur et l'annonce generee est creee avec seller_unique_id = ce joueur, storage_type = LISTED, prix aleatoire entre 10 et 100000.

T3 -- Ce joueur se connecte sur n'importe quel serveur du reseau, ouvre son onglet "en vente" et retire les annonces : RemoveService lui livre physiquement des items qui n'ont jamais existe (materiaux aleatoires, jusqu'a une stack chacun).

T4 -- Variante monetaire : si un autre joueur achete l'une de ces annonces, l'argent est verse au vrai joueur porteur du pseudo.

**Impact** — Creation d'items et d'argent a partir de rien au profit de joueurs reels, declenchee par une commande de test presentee comme inoffensive. L'administrateur n'a aucun moyen de savoir quelles annonces ont ete attribuees a de vrais comptes ni de les nettoyer.

**Correctif**

```java
Ne jamais reutiliser l'UUID d'un joueur existant pour des donnees de test : tirer un nouveau pseudo ou marquer explicitement les annonces generees.

UUID sellerUUID = nameToUuidCache.get(sellerName);
if (sellerUUID == null) {
    if (playerRepository.selectByName(sellerName) != null) {
        continue;                      // pseudo deja pris par un vrai joueur : on saute ce tirage
    }
    sellerUUID = UUID.randomUUID();
    nameToUuidCache.put(sellerName, sellerUUID);
}

Prefixer en plus les pseudos generes (par exemple "TEST_" + nom) pour rendre la collision impossible et permettre un /ah admin generate clear qui supprime toutes les annonces de test. Accessoirement, selectByName est ici un appel JDBC synchrone dans une boucle de taille `amount` sur le thread principal : il fige le serveur pendant la generation.
```

---

<a id="c-086"></a>

### `C-086` — Arrêt du thread abonné Redis non fiable : champ jedisPubSub non volatile, unsubscribe qui peut échouer avant l'attachement, et aucun interrupt

- [ ] **Corrigé**
- **Fichier** : `REDIS/listener/RedisSubscriberRunnable.java:32`
- **Catégorie** : Cycle de vie — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
RedisSubscriberRunnable.java:32 (vérifié) :

    private JedisPubSub jedisPubSub;      // ni volatile, ni protégé

Écrit dans le thread abonné, ligne 71 (vérifié) :

                this.jedisPubSub = new JedisPubSub() { ... };

Lu depuis le thread principal, lignes 46-54 (vérifié) :

    public void shutdown() {
        running.set(false);
        if (jedisPubSub != null) {
            try {
                jedisPubSub.unsubscribe();
            } catch (Exception e) {
                plugin.getLogger().warning("Error during unsubscribe: " + e.getMessage());
            }
        }
    }

Côté appelant, ZAuctionHouseRedis.onDisable lignes 118-126 (vérifié) : `this.subscriberThread.join(5000)` puis... rien. Aucun `subscriberThread.interrupt()`, et le thread n'est jamais forcé à sortir si le join expire.
```

**Chronologie**

T0 — Un `/reload` ou un arrêt survient très peu de temps après le démarrage, ou pendant une phase de reconnexion (la boucle run() ligne 88 dort jusqu'à 60 s dans son backoff exponentiel).

T1 — Le thread principal appelle shutdown(). Le champ `jedisPubSub` n'étant pas volatile, il peut lire `null` alors que le thread abonné vient de l'affecter : aucun unsubscribe n'est émis.

T2 — Variante : `jedisPubSub` est non nul mais le client n'est pas encore attaché (jedis.subscribe ligne 78 n'a pas encore été atteint). `unsubscribe()` lève, l'exception est avalée avec un simple warning, et là non plus aucun désabonnement n'a lieu.

T3 — Le thread reste bloqué dans `jedis.subscribe(...)` ou dans son `Thread.sleep(backoffMs)`. `join(5000)` expire silencieusement, et onDisable enchaîne sur `this.jedisPool.close()` (ligne 137).

T4 — Après un `/reload`, le thread survivant appartient à l'ancien ClassLoader du plugin : il maintient en vie tout le graphe de classes de l'addon et de ses listeners, qui référencent l'ancienne instance de ZAuctionPlugin et son AuctionManager. Chaque `/reload` en accumule un de plus.

**Impact** — Fuite de thread et de ClassLoader à chaque rechargement, plus une fenêtre où l'ancien abonné et le nouveau coexistent sur le même canal Redis. Le thread étant daemon, l'arrêt complet du serveur reste propre ; le problème se manifeste sur les serveurs qui utilisent /reload ou un gestionnaire de plugins à chaud.

**Correctif**

```java
1) Rendre le champ visible entre threads et purger la référence :

    private volatile JedisPubSub jedisPubSub;

2) Rendre l'arrêt effectif dans ZAuctionHouseRedis.onDisable, après le join :

        if (this.subscriberThread != null && this.subscriberThread.isAlive()) {
            this.subscriberThread.join(5000);
            if (this.subscriberThread.isAlive()) {
                getLogger().warning("Redis subscriber did not stop in time, interrupting it.");
                this.subscriberThread.interrupt();   // débloque le Thread.sleep du backoff
            }
        }

3) Dans la boucle run(), ne plus dormir en une seule fois : découper le backoff en tranches courtes qui retestent `running.get()`, afin qu'un arrêt ne doive jamais attendre 60 secondes.
```

---

<a id="c-087"></a>

### `C-087` — Aucune garde d'exclusion sur admin migrate : plusieurs migrations paralleles saturent l'asyncExecutor a 4 threads partage avec le gameplay

- [ ] **Corrigé**
- **Fichier** : `migration/v3/V3MigrationService.java:108`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
V3MigrationService.java:107-170 (verifie) - la tache occupe son thread du debut a la fin, avec des .join() bloquants, et s'execute sur l'executor partage :
    public CompletableFuture<V3MigrationResult> migrate(V3DataReader reader) {
        return CompletableFuture.supplyAsync(() -> {
            ...
                Boolean connected = reader.testConnection().join();
                ...
                int itemCount = reader.getItemCount().join();
                int transactionCount = reader.getTransactionCount().join();
                ...
                List<V3AuctionItem> v3Items = reader.readItems().join();
                ...
                List<V3Transaction> v3Transactions = reader.readTransactions().join();
        }, plugin.getExecutorService());          // l.170 (verifie)

ZAuctionPlugin.java:82 (verifie) : private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(4);

CommandAuctionAdminMigrate.perform (verifie, l.34-105) : AUCUNE garde "migration en cours" - ni AtomicBoolean, ni setCooldown. Le seul garde-fou est l'argument litteral `confirm` (l.66), qui ne protege pas contre une seconde execution.
```

**Chronologie**

T0 : un admin tape /ah admin migrate zauctionhousev3 confirm sur le serveur A. Rien ne s'affiche immediatement (la migration est asynchrone), il pense que la commande n'a pas pris.

T1 : il la retape 3 fois. Chaque frappe soumet une nouvelle tache a l'asyncExecutor de 4 threads : les 4 threads sont desormais occupes du debut a la fin par des migrations qui bloquent sur .join() puis enchainent des dizaines de milliers d'INSERT synchrones en autocommit.

T2 : ce meme pool de 4 threads sert ZStorageManager.createAuctionItem (toute vente de joueur), updateItem, updateItems, ClaimService et HistoryService. Plus aucune vente, aucun claim et aucun historique ne progresse sur A.

T3 : les .orTimeout(...) de PurchaseService et RemoveService expirent alors EN PLEINE section critique, verrou Redis detenu, ce qui laisse des items verrouilles et des chaines interrompues apres le withdraw.

T4 : variante cluster - la commande est lancee sur A et sur B en meme temps. Les deux migrations ecrivent les memes donnees dans la MEME base MySQL : chaque annonce V3 est importee deux fois.

**Impact** — Une commande d'administration peut, sans aucune protection, monopoliser l'integralite du pool asynchrone du plugin et faire expirer les timeouts des achats et retraits en cours. Lancee sur deux noeuds du cluster, elle duplique en base les items importes.

**Correctif**

```java
1) Garde d'exclusion dans CommandAuctionAdminMigrate :
    private static final AtomicBoolean MIGRATION_RUNNING = new AtomicBoolean();
    ...
    if (!MIGRATION_RUNNING.compareAndSet(false, true)) {
        message(plugin, sender, Message.MIGRATION_FAILED, "%error%", "A migration is already running");
        return CommandType.SUCCESS;
    }
    migrationFuture.whenComplete((r, t) -> MIGRATION_RUNNING.set(false));

2) Executer la migration sur son propre Executors.newSingleThreadExecutor() dedie, ferme a la fin, au lieu de plugin.getExecutorService() partage avec le gameplay (V3MigrationService.java:170).

3) Poser un verrou cluster le temps de la migration (clusterBridge / cle Redis SET NX dediee) et refuser la commande si un autre noeud le detient, pour empecher la double importation en base partagee.
```

---

<a id="c-088"></a>

### `C-088` — Aucune tâche planifiée d'expiration : l'expiration n'est déclenchée que par getItemIds, que la liste principale de l'hôtel des ventes n'emprunte jamais

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:407`
- **Catégorie** : Cycle de vie — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ZAuctionManager.getItemIds, lignes 406-420 (vérifié) — unique déclencheur du traitement de masse :

        for (Item item : items.values()) {
            if (item.isExpired()) {
                expiredItems.add(item);
                continue;
            }
            if (predicate.test(item)) {
                filtered.add(item);
            }
        }

        if (!expiredItems.isEmpty()) {
            this.auctionExpireService.processExpiredItems(expiredItems, storageType);
        }

`grep -rn "processExpiredItems" src/` ne retourne QUE cette ligne 419 et la déclaration (ExpireService.java:115) : aucun autre appelant.
`grep -rn "runTimer" src/main/java/` ne retourne QUE BossBarAnimation.java:21 : aucune tâche périodique d'expiration n'existe.
Or la liste principale ne passe PAS par getItemIds — ZAuctionManager.java:295 (vérifié) :

        IntList ids = cache.getOrCompute(PlayerCacheKey.ITEMS_LISTED, () -> sortedItemsCache.getSortedIds(category, sort));

et SortedItemsCache ne filtre les expirés qu'au moment d'une reconstruction, laquelle n'a lieu que si le cache a été invalidé (SortedItemsCache.java:131 `if (dirty.get() && rebuildInProgress.compareAndSet(false, true))`, filtre ligne 305 `if (item.getStatus() == ItemStatus.AVAILABLE && !item.isExpired())`).
```

**Chronologie**

T0 — Le vendeur V liste un item avec une expiration de 48 h, puis ne se reconnecte plus.

T1 — 48 h plus tard l'item est expiré au sens de `isExpired()`, mais rien ne le traite : aucune tâche planifiée, et les joueurs qui parcourent l'hôtel des ventes empruntent le chemin sortedItemsCache, pas getItemIds.

T2 — Tant que le SortedItemsCache n'est pas invalidé (ce qui n'arrive que lors d'un ajout ou d'un retrait d'annonce), l'item expiré reste AFFICHÉ dans la liste principale sur tous les serveurs.

T3 — Un acheteur clique dessus. PurchaseService.java:62 (`if (item.isExpired())`) refuse l'achat : le joueur voit un item cliquable qui refuse systématiquement de s'acheter, sans que rien ne le retire de la liste.

T4 — Le retour effectif de l'item à V n'aura lieu que le jour où un joueur quelconque ouvrira son propre inventaire de vente (getPlayerSellingItems -> getItemIds -> processExpiredItems balaie TOUS les items LISTED expirés), ou lorsque V lui-même cliquera dessus (ListedItemsButton.java:104). Sur un petit serveur, cela peut être des jours.

**Impact** — Le retour des annonces expirées à leur vendeur dépend entièrement du fait qu'un joueur ouvre une GUI particulière. En attendant, des items morts polluent la liste principale de tous les serveurs et refusent de s'acheter, et le vendeur inactif ne récupère jamais ses items. Il n'y a pas de perte définitive, mais le cycle de vie annoncé n'est pas respecté.

**Correctif**

```java
Ajouter une tâche périodique de balayage dans ZAuctionPlugin.onEnable, après le chargement des items :

        this.getScheduler().runTimerAsync(() -> {
            var expired = this.auctionManager.getItems(StorageType.LISTED).stream()
                    .filter(Item::isExpired)
                    .toList();
            if (!expired.isEmpty()) {
                this.auctionManager.getExpireService().processExpiredItems(expired, StorageType.LISTED);
            }
        }, 60, 60, TimeUnit.SECONDS);

En cluster, ce balayage doit impérativement s'exécuter sous le verrou distribué de chaque item (ou n'être actif que sur un nœud élu via un verrou Redis `auction:expire:leader` posé en SET NX PX), faute de quoi deux serveurs expireraient le même item simultanément et le rendraient deux fois. Invalider en complément le SortedItemsCache (`sortedItemsCache.invalidate()`) dès qu'un item devient expiré, pour qu'il quitte immédiatement la liste principale.
```

---

<a id="c-089"></a>

### `C-089` — Chaque message pub/sub declenche un selectItem() en base sur CHAQUE serveur : amplification x(N-1) et 3 a 4 requetes SQL non indexees par message

- [ ] **Corrigé**
- **Fichier** : `REDIS/listener/listeners/ItemListedListener.java:31`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ItemListedListener.java:31 (verifie) - un aller-retour DB par message recu :
        auctionPlugin.getStorageManager().selectItem(id).thenAccept(item -> {

Verifie par grep sur listener/listeners/ :
  ItemRemovedListener.java:50 et :66 -> selectItem(id).whenComplete(...)
  ItemBoughtListener.java:96 -> selectItem(id).whenComplete(...)

ZStorageManager.java:176-200 (verifie) - ce que coute UN selectItem :
    public CompletableFuture<Item> selectItem(int id) {
        return CompletableFuture.supplyAsync(() -> {
            var optional = with(ItemRepository.class).select(id);                 // requete 1
            if (optional.isEmpty()) return null;
            var dto = optional.get();
            var sellerName = with(PlayerRepository.class).select(dto.seller_unique_id());   // requete 2
            ...
                    var auctionItems = with(AuctionItemRepository.class).select(List.of(String.valueOf(dto.id())));  // requete 3
                    ...
                    if (dto.buyer_unique_id() != null) {
                        auctionItem.setBuyer(dto.buyer_unique_id(), with(PlayerRepository.class).select(dto.buyer_unique_id()));  // requete 4
                    }

ItemRepository.java:51-56 (verifie) : le SELECT filtre en plus sur storage_type, colonne non indexee.

Le message ne transporte pourtant que l'id : ItemListedMessage(String itemId).
```

**Chronologie**

Charge : cluster de 5 serveurs partageant une base MySQL, 10 mises en vente + 5 achats + 5 retraits par seconde sur l'ensemble du reseau.

T0 serveur A : un joueur vend -> notifyItemListed publie ItemListedMessage(id) sur le bus.

T1 : les 4 autres serveurs recoivent le message sur leur thread subscriber et appellent CHACUN selectItem(id) sur la base partagee.

T2 : chaque selectItem execute 3 requetes SQL (items, players, auction_items), 4 si un acheteur est renseigne. Soit 4 serveurs x 3 requetes = 12 requetes SQL pour propager UNE mise en vente, alors que l'emetteur connaissait deja toute la donnee.

T3 : idem pour ItemRemovedListener (jusqu'a 4 x 3 requetes par retrait ou expiration) et pour ItemBoughtListener quand purchased-item.give-item vaut false.

Bilan a 20 evenements/s : 20 x 4 x 3 = 240 requetes SQL/s ajoutees, en plus des ecritures metier et du selectItem de revalidation sous verrou de PurchaseService.

T4 : ces supplyAsync n'ont pas d'executor (ZStorageManager:177) : ils tournent sur ForkJoinPool.commonPool, deja occupe par les appels Jedis bloquants du bridge. JDBC bloquant + Jedis bloquant sur (coeurs - 1) workers -> les callbacks des listeners prennent des secondes, et ItemListedListener.java:33-36 finit par logguer "Unable to find the item" par pur retard de replication plutot que par absence reelle.

**Impact** — Charge SQL qui croit lineairement avec le nombre de serveurs alors que la donnee est entierement connue de l'emetteur. Convergence des caches retardee (items invisibles plusieurs secondes chez les autres noeuds), ce qui allonge les fenetres pendant lesquelles deux serveurs ont des vues divergentes du meme item.

**Correctif**

1) Enrichir les messages pour supprimer l'aller-retour DB. ItemListedMessage ne transporte que l'id ; y ajouter les champs que selectItem reconstruit (id, sellerUniqueId, sellerName, price, economyName, createdAt, expiredAt, itemstacks Base64, serverName) et construire l'Item localement depuis le message dans ItemListedListener, en ne retombant sur selectItem que si le message est d'une version anterieure (champ absent).

2) A defaut, dedupliquer et batcher : accumuler les ids recus pendant une fenetre de 100 ms et resoudre le lot via StorageManager.selectItems(List<Integer>) (ZStorageManager:238-251), qui fait 3 requetes pour N items au lieu de 3N.

3) Passer l'executor dedie au supplyAsync de ZStorageManager.selectItem (l.177) pour que ces resolutions ne concurrencent plus le commonPool.

---

<a id="c-090"></a>

### `C-090` — Chargement des ItemStacks en O(n x m) : getAuctionItems refait un stream().filter() sur la liste complete pour CHAQUE item

- [ ] **Corrigé**
- **Fichier** : `utils/ItemLoaderUtils.java:31`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```sql
ItemLoaderUtils.java:31-33 (verifie) - un stream().filter() sur TOUTE la liste :
    protected List<AuctionItemDTO> getAuctionItems(List<AuctionItemDTO> auctionItemDTOS, int itemId) {
        return auctionItemDTOS.stream().filter(e -> e.item_id() == itemId).toList();
    }

ItemLoaderUtils.java:66-85 (verifie) - appele dans la boucle sur TOUS les items :
        for (ItemDTO dto : items) {
            ...
            switch (dto.item_type()) {
                case AUCTION -> {
                    var currentAuctionItems = getAuctionItems(auctionItems, dto.id());

CreateAuctionItemMigration.java:10-15 (verifie) - aucun index declare hors la cle etrangere :
        create(Tables.AUCTION_ITEMS, table -> {
            table.autoIncrement("id");
            table.integer("item_id").foreignKey(Tables.ITEMS, "id", true);
            table.longText("itemstack");
            table.timestamps();
        });

Ce code est sur les DEUX chemins chauds : AuctionLoader.loadItems (boot) et ZStorageManager.selectItems(List<Integer>) (resolution de lots).
```

**Chronologie**

T0 : cluster de 3 serveurs, base MySQL partagee, 20 000 items vivants et 25 000 lignes dans auction_items (les shulkers/conteneurs generent plusieurs lignes par item).

T1 : le serveur A redemarre. AuctionLoader.loadItems charge les 20 000 ItemDTO puis les 25 000 AuctionItemDTO en une requete.

T2 : la boucle de createItems appelle getAuctionItems une fois par item, et chaque appel reparcourt les 25 000 AuctionItemDTO : 20 000 x 25 000 = 500 millions de comparaisons, en pur CPU, sur le thread de chargement, a chaque demarrage.

T3 : le meme cout est paye a chaud par ZStorageManager.selectItems(List<Integer>), qui appelle createItems : une resolution de lot d'items depuis un evenement cluster paye le meme O(n x m) sur son lot.

T4 : le demarrage de A s'etire pendant que B et C continuent d'ecrire dans la meme base ; plus le boot est long, plus la vue memoire de A est perimee au moment ou il commence a servir des joueurs.

**Impact** — Temps de chargement quadratique en fonction du nombre d'items et du nombre de lignes auction_items, sur un chemin execute a chaque demarrage de chaque serveur du cluster, et re-execute a chaud par selectItems.

**Correctif**

```java
Remplacer le regroupement O(n x m) par une seule passe, dans ItemLoaderUtils.createItems avant la boucle :

    Map<Integer, List<AuctionItemDTO>> byItem = auctionItems.stream()
            .collect(Collectors.groupingBy(AuctionItemDTO::item_id));

puis dans la boucle :

    var currentAuctionItems = byItem.getOrDefault(dto.id(), List.of());

et supprimer la methode getAuctionItems (ou la conserver en la faisant deleguer a la map). Ajouter en complement un index sur %prefix%auction_items(item_id) via une migration, la cle etrangere n'en garantissant pas un sur toutes les configurations.
```

---

<a id="c-091"></a>

### `C-091` — Chemin cluster d'expiration : la revalidation sous verrou ne verifie pas que la ligne est encore LISTED, et 0 ligne modifiee est interprete comme un succes

- [ ] **Corrigé**
- **Fichier** : `services/ExpireService.java:283`
- **Catégorie** : Cohérence de cache — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ExpireService.java:283 - la garde ne teste ni le storage_type ni le statut :
                                            if (dbItem == null || dbItem.getBuyerUniqueId() != null) {

Or ItemRepository.select(int) ne filtre que DELETED :
        return select(ItemDTO.class, schema -> {
            schema.where("id", id);
            schema.where("storage_type", "!=", StorageType.DELETED.name());
        }).stream().findFirst();

ExpireService.java:355-364 - le nombre de lignes n'est jamais consulte (updateItem renvoie CompletableFuture<Void>) :
                storageManager.updateItem(item, StorageType.EXPIRED).whenComplete((u, t) -> {
                    if (t != null) {
                        // DB update failed: revert the expiredAt change and leave the item in the
                        // LISTED store unchanged so a later sweep retries. Never create a local
                        // EXPIRED phantom against a still-LISTED database row.
                        item.setExpiredAt(previousExpiredAt);
                        done.completeExceptionally(t);
                        return;
                    }
```

**Chronologie**

T0 - L'item 42 est LISTED, expiredAt = T. Serveurs A, B, C en mode Redis.

T1 - Le vendeur ouvre /ah selling sur A. A expire l'item : verrou pris, selectItem confirme LISTED sans acheteur, UPDATE items SET storage_type='EXPIRED', expired_at=<T+7j vu par A> WHERE id=42 AND storage_type='LISTED' -> 1 ligne, puis broadcast ItemRemovedMessage(42, LISTED, EXPIRED), puis unlock.

T2 - Le message n'atteint pas C (RedisSubscriberRunnable en backoff, jusqu'a 60 s). C garde l'item 42 dans son store LISTED avec expiredAt = T.

T3 - Sur C, n'importe quel appel a getItemIds(LISTED) constate isExpired() -> expireListedItemClustered(42). checkAvailability : etat Redis REMOVED -> disponible. lockItem : pas de lockKey -> verrou accorde.

T4 - ExpireService:283 : la ligne est EXPIRED (donc non nulle) et buyer_unique_id est NULL -> LA GARDE NE DETECTE RIEN. C poursuit vers performListedToExpired.

T5 - C recalcule expiredAt avec SA propre horloge et SES propres permissions, puis lance l'UPDATE ... WHERE id=42 AND storage_type='LISTED'. La ligne est deja EXPIRED -> 0 ligne modifiee. Repository.update jette l'entier et ne leve rien : t == null -> la branche succes s'execute.

T6 - C applique le deplacement memoire (l.366-371) avec un expiredAt QUI N'A JAMAIS ETE PERSISTE, puis diffuse un second ItemRemovedMessage(42, LISTED, EXPIRED) qui declenche sur A et B un removeItem sur tous les stores suivi d'un selectItem + re-addItem, soit une disparition/reapparition parasite dans les inventaires ouverts.

T7 - La date d'expiration de l'onglet 'expires' differe entre C (memoire seulement) et A/B/base. Au redemarrage de C, l'ecart disparait brutalement.

**Impact** — Divergence memoire/base sur expired_at, faux compte a rebours pour le vendeur, et rafales de messages ItemRemovedMessage redondants qui font clignoter les inventaires ouverts sur tout le reseau et generent un selectItem par noeud et par rafale. Le commentaire du code affirme 'Never create a local EXPIRED phantom against a still-LISTED database row', mais la protection ne couvre que l'erreur SQL, pas le cas '0 ligne matchee' que la garde where storage_type='LISTED' est precisement censee produire.

**Correctif**

```java
1) Durcir la revalidation l.283 pour qu'elle exige LISTED, ce qui evite l'aller-retour inutile et la diffusion parasite :
    if (dbItem == null || dbItem.getBuyerUniqueId() != null || dbItem.getStatus() != ItemStatus.AVAILABLE) {
        this.plugin.getScheduler().runNextTick(w -> {
            this.auctionManager.removeItem(StorageType.LISTED, item.getId());
            this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
        });
        return CompletableFuture.<Void>completedFuture(null);
    }
2) Faire remonter le nombre de lignes (Repository.update renvoie l'int, ItemRepository.updateItem le propage, ZStorageManager.updateItem devient CompletableFuture<Integer>) et traiter rows < 1 comme un echec dans le whenComplete l.355 : purger le fantome local au lieu de le deplacer.
```

---

<a id="c-092"></a>

### `C-092` — ItemBoughtListener et ItemRemovedListener n'appellent jamais updateListedItems : les slots restent affiches et cliquables dans les HDV deja ouverts, et ITEMS_SEARCH n'est pas purge par le retrait

- [ ] **Corrigé**
- **Fichier** : `REDIS/listener/listeners/ItemRemovedListener.java:43`
- **Catégorie** : Cohérence de cache — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
Asymetrie prouvable entre les trois listeners. ItemListedListener.java:47 propage bien un AJOUT dans les inventaires ouverts :
                manager.updateListedItems(item, true, null);

Mais ItemBoughtListener.java:49-50 et ItemRemovedListener.java:37-43 se contentent de removeItem + clearPlayersCache :
            for (StorageType st : StorageType.values()) {
                if (st != StorageType.DELETED) {
                    manager.removeItem(st, id);
                }
            }

            manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED);

Or clearPlayersCache ne fait que vider une cle du cache joueur (ZAuctionManager:492-494), sans aucun effet sur un inventaire deja affiche dont les boutons sont figes dans inventoryEngine. Et cette liste omet PlayerCacheKey.ITEMS_SEARCH, alors que getItemIdsListedForSale:287-292 sert la liste ITEMS_SEARCH en priorite des qu'une recherche est active (ItemBoughtListener:50 l'inclut, lui).
```

**Chronologie**

T0 - Serveur B : 8 joueurs ont l'hotel des ventes ouvert, l'item 42 est visible et cliquable (bouton cree par ListedItemsButton:66-68 avec la reference Item capturee).

T1 - Serveur A : le vendeur retire l'item 42 (destination EXPIRED) -> ItemRemovedMessage diffuse.

T2 - Serveur B : l'etape 1 du listener retire 42 de tous les stores et vide quatre cles de cache. Aucun updateListedItems(item, false, null) : les 8 joueurs voient toujours l'item et peuvent cliquer dessus.

T3 - L'un d'eux clique. ListedItemsButton.createClick ne consulte pas le store, il utilise la reference capturee -> processPurchase -> IS_PURCHASE_CONFIRM diffuse a tout le cluster -> ouverture de la confirmation -> PurchaseService. La ligne etant EXPIRED avec buyer NULL, la revalidation de PurchaseService:121 (dbItem == null || buyer != null) NE DETECTE RIEN et laisse passer.

T4 - En parallele, un joueur en recherche active garde l'id supprime dans son ITEMS_SEARCH jamais purge et obtient une page incomplete.

**Impact** — Items fantomes cliquables sur les serveurs distants apres un achat ou un retrait ; declencheur direct des chemins d'achat sur reference perimee. Chaque clic fantome genere en plus deux messages pub/sub inutiles (IS_PURCHASE_CONFIRM puis retour AVAILABLE), et les joueurs en recherche active voient une liste incoherente.

**Correctif**

```java
Aligner les deux listeners sur ItemListedListener. Dans ItemBoughtListener etape 1 et ItemRemovedListener etape 1, avant de retirer l'item du store (pour que updateInventory puisse encore le localiser dans la liste d'ids) :

    var existing = manager.getItem(StorageType.LISTED, id); // accesseur O(1) a ajouter
    if (existing != null) {
        manager.updateListedItems(existing, false, null); // retire le bouton des GUI ouverts
    }
    manager.removeItem(StorageType.LISTED, id);

Et ajouter PlayerCacheKey.ITEMS_SEARCH a la liste de ItemRemovedListener l.43 (et l.61/l.86). En defense en profondeur, faire revalider le store dans ListedItemsButton.createClick avant toute action :

    if (manager.getItem(StorageType.LISTED, item.getId()) != item) {
        manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
        manager.updateInventory(player);
        return;
    }
```

---

<a id="c-093"></a>

### `C-093` — ItemListedListener re-injecte l'item en LISTED sans verifier son etat reel et ne purge jamais ITEMS_SELLING

- [ ] **Corrigé**
- **Fichier** : `REDIS/listener/listeners/ItemListedListener.java:44`
- **Catégorie** : Cohérence de cache — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ItemListedListener.java:31-48 :
        auctionPlugin.getStorageManager().selectItem(id).thenAccept(item -> {
            if (item == null) { ... return; }
            var manager = auctionPlugin.getAuctionManager();
            auctionPlugin.getCategoryManager().applyCategories(item);

            auctionPlugin.getScheduler().runNextTick(wrappedTask -> {

                manager.addItem(StorageType.LISTED, item);
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);

                manager.updateListedItems(item, true, null);
            });

Aucun test sur item.getStatus() avant addItem(LISTED, ...), et ItemRepository.select(int) ne filtre que storage_type != 'DELETED'. La purge omet ITEMS_SELLING, contrairement a ItemRemovedListener:43 qui l'inclut.
```

**Chronologie**

(a) Re-ajout aveugle en LISTED.

T0 - Serveur A : postSell publie ItemListedMessage("8421").

T1 - Serveur B : le thread abonne lance selectItem(8421), un aller-retour JDBC complet (supplyAsync sans executor, donc sur ForkJoinPool.commonPool).

T2 - Serveur A : l'annonce est achetee ; ZAuctionManager:893 updateItem(..., PURCHASED) -> la ligne devient storage_type='PURCHASED' avec buyer renseigne. A publie ItemBoughtMessage("8421").

T3 - Serveur B : ItemBoughtListener traite le message, removeItem(LISTED, 8421) (rien en memoire), puis addItem(PURCHASED, item). Etat correct.

T4 - Serveur B : le selectItem du T1 se termine enfin et renvoie l'item PURCHASED (le SELECT n'exclut que DELETED). Le listener fait alors addItem(StorageType.LISTED, item) : un item VENDU est reinjecte dans le store LISTED de B.

Consequence mesurable : getPlayerSellingItems ne filtre que status != DELETED, donc l'annonce vendue reapparait dans l'onglet 'mes ventes' du vendeur sur B et gonfle son compteur de limite. Le clic est refuse par RemoveService (status != AVAILABLE) : l'entree reste coincee jusqu'au redemarrage.

(b) ITEMS_SELLING jamais purge.

T0 - Serveur B : l'arrivee d'une annonce du joueur X pendant qu'il est connecte a B ajoute un item LISTED lui appartenant.

T1 - X avait deja consulte son inventaire 'mes ventes', donc ITEMS_SELLING est en cache et n'est pas purge par ce listener.

T2 - getPlayerSellingItems(X) renvoie la liste perimee : l'annonce est invisible dans son GUI et n'est pas comptee par la limite de SellService:256, jusqu'a ce qu'un autre evenement purge la cle.

**Impact** — (a) empoisonne durablement le store LISTED des noeuds pairs avec des annonces vendues, fausse le compteur de limite de vente et laisse des entrees non cliquables ; (b) desynchronise en permanence le GUI 'mes ventes' et le compteur de limite entre serveurs.

**Correctif**

```java
Dans ItemListedListener.onMessage :
1) N'ajouter que si l'item est reellement disponible :
    if (item.getStatus() != ItemStatus.AVAILABLE || item.isExpired()) {
        this.plugin.debug("Item " + id + " is no longer listed (" + item.getStatus() + "), ignoring");
        return;
    }
(le statut est deja derive du storage_type par ItemLoaderUtils:43-48, ce test equivaut donc a un controle storage_type='LISTED').
2) Aligner la purge sur ItemRemovedListener :
    manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH, PlayerCacheKey.ITEMS_SELLING);
3) Deplacer applyCategories(item) dans le runNextTick : il execute aujourd'hui les regles de hooks (ItemsAdder, MMOItems, Oraxen...) sur le thread subscriber Redis.
```

---

<a id="c-094"></a>

### `C-094` — ItemRepository.updateItems fait N UPDATE unitaires alors que Sarah fournit un batch transactionnel deja utilise ailleurs dans le projet

- [ ] **Corrigé**
- **Fichier** : `storage/repository/repositories/ItemRepository.java:58`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```sql
ItemRepository.java:58-69 (verifie) :
    public void updateItems(Map<StorageType, List<Item>> itemsByStorageType) {
        for (Map.Entry<StorageType, List<Item>> entry : itemsByStorageType.entrySet()) {
            StorageType storageType = entry.getKey();
            List<Item> items = entry.getValue();
            if (items.isEmpty()) continue;

            // Update each item individually using the consumer pattern
            for (Item item : items) {
                update(createUpdateSchema(item, storageType));
            }
        }
    }

ZStorageManager.java:160-163 (verifie) - l'appel tourne sur l'asyncExecutor a 4 threads :
    public CompletableFuture<Void> updateItems(Map<StorageType, List<Item>> itemsByStorageType) {
        return CompletableFuture.runAsync(() -> with(ItemRepository.class).updateItems(itemsByStorageType), this.plugin.getExecutorService());
    }
ZAuctionPlugin.java:82 (verifie) : private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(4);

Sarah expose pourtant UpdateBatchRequest (setAutoCommit(false) -> addBatch -> executeBatch -> commit), deja employe dans ce meme projet par TransactionRepository.updateStatus et LogRepository.markAsRead via Repository.update(List<Schema>).
```

**Chronologie**

Charge : un joueur ouvre son onglet /ah selling apres une absence. L'expiration est paresseuse et pilotee par le rendu (ZAuctionManager:404-420), donc 3000 items expirent d'un coup et ExpireService appelle storageManager.updateItems(batchUpdate).

T0 : ce seul appel produit 3000 UPDATE ... WHERE id=? sequentiels, chacun empruntant puis rendant une connexion HikariCP, sur l'un des 4 threads de l'asyncExecutor.

T1 : a 1 ms d'aller-retour reseau, c'est 3 s pendant lesquelles un quart de l'executor async du plugin est monopolise ; a 5 ms (MySQL distant sur un cluster), 15 s.

T2 : pendant ce temps, sur ce meme pool de 4 threads, tous les createAuctionItem des ventes en cours et tous les updateItem des achats attendent leur tour ; les .orTimeout des sections critiques de PurchaseService et RemoveService peuvent expirer alors qu'un verrou Redis est detenu.

**Impact** — Pic de latence sur toutes les operations base du plugin (achat, vente, claim) a chaque vague d'expiration, alors que la primitive batch existe et est deja employee ailleurs dans le meme projet. Sur un cluster, ces retards se traduisent en timeouts au milieu de sections critiques sous verrou.

**Correctif**

```sql
Router updateItems vers le batch, en regroupant par forme de requete (UpdateBatchRequest construit le SQL a partir du PREMIER schema uniquement : tous les schemas d'un lot doivent donc avoir exactement les memes colonnes et les memes conditions WHERE - ce qui n'est pas le cas ici, createUpdateSchema ajoutant conditionnellement `where("storage_type", LISTED)` pour EXPIRED et la colonne buyer_unique_id pour PURCHASED/DELETED) :

public void updateItems(Map<StorageType, List<Item>> itemsByStorageType) {
    for (var entry : itemsByStorageType.entrySet()) {
        StorageType to = entry.getKey();
        if (entry.getValue().isEmpty()) continue;
        // meme forme SQL = meme cible + meme presence de buyer_unique_id
        Map<Boolean, List<Item>> byShape = entry.getValue().stream()
                .collect(Collectors.partitioningBy(i -> i.getBuyerUniqueId() != null));
        for (var group : byShape.values()) {
            if (group.isEmpty()) continue;
            var schemas = group.stream().map(item -> buildSchema(item, to)).toList();
            update(schemas);   // -> UpdateBatchRequest, une seule transaction
        }
    }
}

A combiner avec le compare-and-set : Repository.update(List<Schema>) doit renvoyer la somme des lignes affectees pour detecter les items perdus dans une course entre serveurs.
```

---

<a id="c-095"></a>

### `C-095` — L'addon est compile contre un SHA git fige de l'API et aucun controle de compatibilite n'est fait au demarrage

- [ ] **Corrigé**
- **Fichier** : `REDIS/build.gradle.kts:48`
- **Catégorie** : Contrat API — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```diff
build.gradle.kts:47-49 (verifie) - dependance API epinglee sur un hash de commit, pas sur une version :

    compileOnly("org.spigotmc:spigot-api:1.20.6-R0.1-SNAPSHOT")
    compileOnly("fr.maxlego08.zauctionhouse:zauctionhousev4-api:deb8f16")
    compileOnly("fr.maxlego08.menu:zmenu-api:1.1.0.8")

src/main/resources/plugin.yml (verifie) - aucune contrainte de version exprimable ni exprimee :

    depend:
      - zAuctionHouse

ZAuctionHouseRedis.java:62 (verifie) - cast direct, aucune verification prealable, et l.99 installation du bridge sans aucun controle de contrat :

        this.auctionPlugin = (AuctionPlugin) pluginManager.getPlugin("zAuctionHouse");
    ...
        this.auctionPlugin.setAuctionClusterBridge(new RedisAuctionClusterBridge(this, this.jedisPool, this.lockTtl, itemStateTtl, itemListedTtl));

API/.../AuctionPlugin.java:120 (verifie) : void setAuctionClusterBridge(AuctionClusterBridge auctionClusterBridge); - aucun parametre de version.

Le seul "version checker" present (ZAuctionHouseRedis.java:71-73) interroge groupez.dev pour la version publiee de l'addon et ne verifie RIEN sur la compatibilite API :

            new VersionChecker(auctionPlugin, this.getLogger(), 210);

Et le contrat contient deja des methodes default qu'un addon ancien n'implemente pas - AuctionClusterBridge.java:92-94 et 104-106 (verifie) : removeItem(Item, StorageType, StorageType) et isDistributed().
```

**Chronologie**

T0 - Un exploitant met a jour zAuctionHouse.jar mais conserve son zAuctionHouseRedis.jar compile contre l'API du commit deb8f16. Aucun avertissement au demarrage : plugin.yml ne permet pas d'exprimer une contrainte de version, et le cast l.62 reussit puisque AuctionPlugin vient du classloader du plugin principal.

T1 - Cas A (methode default ajoutee au contrat) : l'addon ne l'implemente pas, le comportement par defaut s'applique silencieusement. Precedent verifiable dans le code actuel : un addon anterieur a l'introduction de removeItem(Item, StorageType, StorageType) perd toutes les destinations (etat Redis REMOVED partout au lieu de DELETED), et isDistributed() rend false, ce qui fait que le plugin cesse de differer les depots vendeur et paie localement un vendeur hors ligne qui n'a peut-etre jamais rejoint ce serveur.

T2 - Cas B (methode abstraite ajoutee) : RedisAuctionClusterBridge se charge normalement ; l'AbstractMethodError n'est levee qu'au premier appel reel, donc au premier achat d'un joueur, a l'interieur d'un CompletableFuture. Elle est absorbee par le exceptionally de PurchaseService et rapportee comme "Internal error" : l'hotel des ventes est casse sans aucun diagnostic exploitable.

T3 - Cas C (signature consommee par l'addon modifiee : item.getItemDisplay(), item.getFormattedPrice(), getConfiguration().getActions().purchased().giveItem(), tous appeles dans notifyItemBought, RedisAuctionClusterBridge.java:301-310) : NoSuchMethodError levee a l'interieur du CompletableFuture.runAsync. Le future de notifyItemBought echoue APRES que auctionManager.purchaseItem a deja commit l'achat (PurchaseService l.134-136). L'acheteur a paye et recu l'item, mais aucun ItemBoughtMessage n'est diffuse : les autres serveurs gardent le fantome - condition exacte d'une seconde livraison ailleurs - et PurchaseService rend un echec INTERNAL_ERROR pour un achat pourtant reussi.

**Impact** — Toute derive de version entre le jar principal et l'addon degrade la synchronisation soit silencieusement (methodes default), soit tardivement et de facon illisible (AbstractMethodError / NoSuchMethodError absorbees dans un CompletableFuture). Le cas T3 decouple definitivement l'etat commit en base de l'etat propage au cluster. Rien, aujourd'hui, ne signale l'incompatibilite au demarrage.

**Correctif**

```java
1) Introduire une version de contrat explicite dans l'API, independante de la version du plugin :

    public interface AuctionClusterBridge {
        /** Version du contrat cluster. Incrementer a CHAQUE ajout/modification de methode. */
        int CONTRACT_VERSION = 2;

2) Faire declarer a chaque implementation la version contre laquelle elle a ete compilee et la verifier a l'installation (AuctionPlugin) :

    void setAuctionClusterBridge(AuctionClusterBridge bridge, int compiledAgainstContractVersion);

    // ZAuctionPlugin
    public void setAuctionClusterBridge(AuctionClusterBridge bridge, int compiledVersion) {
        if (compiledVersion > AuctionClusterBridge.CONTRACT_VERSION) {
            throw new IllegalStateException("Cluster bridge compiled against contract v" + compiledVersion
                    + " but zAuctionHouse only implements v" + AuctionClusterBridge.CONTRACT_VERSION + ". Update zAuctionHouse.");
        }
        if (compiledVersion < AuctionClusterBridge.CONTRACT_VERSION) {
            getLogger().warning("Cluster bridge compiled against contract v" + compiledVersion + " (current: v"
                    + AuctionClusterBridge.CONTRACT_VERSION + "). Update the addon.");
        }
        this.auctionClusterBridge = bridge;
    }

Cote addon : setAuctionClusterBridge(bridge, AuctionClusterBridge.CONTRACT_VERSION) - la constante etant inlinee par javac, elle porte bien la version de compilation. En cas d'echec, disablePlugin(this) plutot que de laisser tourner un cluster a moitie synchronise.

3) Remplacer compileOnly("fr.maxlego08.zauctionhouse:zauctionhousev4-api:deb8f16") par une version semantique publiee alignee sur le plugin, pour que la derive soit visible dans le build.

4) Encadrer les blocs supplyAsync/runAsync du bridge afin qu'une LinkageError ne soit plus muette :

    catch (LinkageError e) { plugin.getLogger().severe("zAuctionHouse API incompatibility: " + e); throw e; }
```

---

<a id="c-096"></a>

### `C-096` — La limite d'annonces est evaluee sur le cache memoire local avant la chaine asynchrone : contournable par spam et entre serveurs

- [ ] **Corrigé**
- **Fichier** : `services/SellService.java:256`
- **Catégorie** : Cohérence de cache — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
// SellService.java:256-261 -- comptage sur l'etat memoire local, aucune verification en base
long listedItems = manager.getPlayerSellingItems(player).size();
long maxSellPermission = configuration.getPermission().getLimit(ItemType.AUCTION, player);
if (listedItems >= maxSellPermission) {
    message(plugin, player, Message.LISTED_ITEMS_LIMIT, "%max-items%", String.valueOf(maxSellPermission));
    return SellFailReason.LISTING_LIMIT_REACHED;
}

// SellService.java:56-59 -- validateItems est appele UNE SEULE FOIS, avant toute la chaine async
SellFailReason validationReason = this.validateItems(player, price, auctionEconomy, itemsToSell);
if (validationReason != SellFailReason.NONE) {
    return CompletableFuture.completedFuture(SellResult.failure("Validation failed", validationReason));
}

// SellService.java:76 puis 83 puis 108 -- trois sauts asynchrones avant l'INSERT, sans re-verification
applySellTaxAsync(player, price, itemsToSell, auctionEconomy).thenAccept(taxResult -> {
    this.plugin.getScheduler().runAtEntity(player, task -> {
        ...
        storageManager.createAuctionItem(...)
```

**Chronologie**

Le compte provient de l'index memoire du serveur local, alimente par ItemListedListener, lui-meme dependant d'un bus pub/sub sans garantie de livraison ni de delai.

T0 -- Le joueur a droit a 10 annonces et en a deja 10, reparties sur le reseau.

T1 -- Il se connecte au serveur B pendant que B reconstruit son etat (redemarrage recent) ou apres une coupure Redis : les annonces creees ailleurs pendant la coupure ne sont pas dans les LISTED memoire de B. getPlayerSellingItems(player).size() renvoie 4 -> la vente passe.

T2 -- Variante sans coupure : le joueur mitraille /ah sell depuis deux clients (ou deux serveurs) dans la meme seconde. validateItems s'execute pour les deux ventes AVANT que l'une des deux n'atteigne l'INSERT (trois sauts asynchrones les separent : calcul de taxe, retour sur le thread d'entite, puis createAuctionItem), donc les deux comptent 9 et les deux passent.

T3 -- Aucune re-verification n'a lieu apres le calcul de taxe ni avant l'INSERT : la limite est franchie en base, definitivement.

**Impact** — Contournement de la limite d'annonces par permission, systematique sur un reseau multi-serveurs et trivial par double clic. Pas de duplication, mais une regle commerciale (limites VIP payantes) rendue ineffective et un gonflement non borne de l'hotel des ventes.

**Correctif**

```java
Recompter juste avant l'INSERT, cote base et non cote memoire :

// dans ItemRepository
public long countListedBySeller(UUID sellerUniqueId) {
    return count(schema -> {
        schema.where("seller_unique_id", sellerUniqueId);
        schema.where("storage_type", StorageType.LISTED.name());
    });
}

// dans SellService, juste avant createAuctionItem, dans le meme thread executor
storageManager.countListedBySeller(player.getUniqueId()).thenCompose(count -> {
    if (count >= maxSellPermission) {
        return CompletableFuture.failedFuture(new IllegalStateException("LISTING_LIMIT_REACHED"));
    }
    return storageManager.createAuctionItem(...);
})

Idealement, faire porter la contrainte par la base (INSERT ... SELECT avec condition sur le compte, ou trigger) pour que deux serveurs concurrents ne puissent pas la franchir simultanement.
```

---

<a id="c-097"></a>

### `C-097` — Le GUI de vente n'affiche que slots.size() stacks alors que la sélection n'est pas bornée : le joueur vend des stacks qu'il ne voit pas et ne peut pas retirer

- [ ] **Corrigé**
- **Fichier** : `buttons/sell/SellShowItemButton.java:49`
- **Catégorie** : Fiabilité — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
SellShowItemButton.onRender, lignes 48-67 (vérifié) — le rendu est borné, et le setClick de retrait n'est créé QUE pour les entrées effectivement dessinées :

            List<Map.Entry<Integer, ItemStack>> entries = new ArrayList<>(sellItems.entrySet());
            var maxIndex = Math.min(slots.size(), entries.size());
            for (int i = 0; i != maxIndex; i++) {
                ...
                var button = inventoryEngine.addItem(this.slots.get(i), itemStack);
                if (button == null) continue;
                button.setClick(e -> {
                    if (removeFromSellList(player, inventorySlot)) { ... }
                });
            }

Mais onInventoryClick (lignes 111-117, vérifié) n'impose AUCUNE borne à la map :

        if (sellItems.containsKey(clickedSlot)) {
            sellItems.remove(clickedSlot);
            manager.message(player, Message.SELL_ITEM_REMOVED);
        } else {
            sellItems.put(clickedSlot, clickedItem.clone());
            manager.message(player, Message.SELL_ITEM_ADDED);
        }

Et SellConfirmButton transmet la map ENTIÈRE à sellAuctionItems, qui ne filtre que l'air (SellService.java:47).
```

**Chronologie**

T0 — Le pattern sell-inventory expose par exemple 21 slots pour SellShowItemButton.

T1 — X clique 30 stacks dans son inventaire. Chaque clic répond « SELL_ITEM_ADDED », donc X croit que les 30 sont pris en compte — et ils le sont, dans la map.

T2 — Le GUI n'en dessine que 21. Les 9 derniers sont dans la map, invisibles, et sans bouton de retrait puisque le setClick n'est créé que pour les entrées rendues : X ne peut plus les désélectionner, même en cliquant à nouveau dans son inventaire il ne sait pas lesquels sont concernés.

T3 — X confirme en croyant vendre les 21 stacks qu'il voit. removeItemsFromSlots (SellService.java:105) en retire 30 de son inventaire et les 30 partent dans l'annonce.

T4 — Effet aggravant au retrait : l'annonce étant indivisible, elle rend les 30 stacks d'un coup. La garde canReceiveItem (ZItem.java:173-175) ne teste qu'un seul emplacement libre via `firstEmpty() != -1`, donc le surplus tombe au sol via dropItem.

**Impact** — Un joueur vend par erreur des stacks qu'aucun écran de confirmation ne lui a montrés, sans possibilité de revenir en arrière — le grief classique du « le plugin m'a pris mes items ». Et la restitution d'une annonce surdimensionnée fait tomber au sol les stacks qui n'entrent pas dans l'inventaire.

**Correctif**

```java
Borner la sélection à ce que le GUI sait afficher ET retirer, dans SellShowItemButton.onInventoryClick, avant le put :

        if (!sellItems.containsKey(clickedSlot) && sellItems.size() >= this.slots.size()) {
            manager.message(player, Message.SELL_INVENTORY_FULL);   // nouveau message, à ajouter dans les 4 langues (racine + fr/, es/, it/)
            return;
        }

Corriger en complément la garde de restitution : ZItem.canReceiveItem doit vérifier qu'il y a autant d'emplacements libres que de stacks à rendre (ou utiliser `inventory.addItem(...)` et ne consommer l'annonce que si la map de retour est vide), plutôt que de tester un unique `firstEmpty() != -1`.
```

---

<a id="c-098"></a>

### `C-098` — Le placeholder %listed_items% copie integralement le store LISTED a chaque resolution, sans aucun cache

- [ ] **Corrigé**
- **Fichier** : `placeholder/placeholders/GlobalPlaceholders.java:18`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
GlobalPlaceholders.java:16-20 (verifie) - aucun cache, copie complete a chaque appel :
        // Count only items actually shown in the auction list (available for sale and not expired),
        // so the placeholder stays consistent with the GUI list and the category counts.
        placeholder.register("listed_items", player -> String.valueOf(manager.getItems(StorageType.LISTED).stream()
                .filter(item -> item.isActivelyListed())
                .count()), "Returns the number of listed items");

ZAuctionManager.java:220-223 (verifie) :
    @Override
    public List<Item> getItems(StorageType storageType) {
        return new ArrayList<>(this.storageItemsById.getOrDefault(storageType, Map.of()).values());
    }

Le compte est pourtant deja disponible en O(1) dans SortedItemsCache.getTotalCount(SortItem), dont le filtre de rebuild (SortedItemsCache.java:305 `item.getStatus() == ItemStatus.AVAILABLE && !item.isExpired()`) est exactement isActivelyListed().
```

**Chronologie**

Charge : 50 000 items LISTED, 100 joueurs connectes, un plugin de scoreboard qui rafraichit %zauctionhouse_listed_items% une fois par seconde et par joueur. PlaceholderAPI resout sur le thread appelant, generalement le main thread.

T0 : chaque resolution alloue une ArrayList de 50 000 references (200 Ko) puis un stream avec 50 000 appels a isActivelyListed().

T1 : a 100 resolutions par seconde, cela represente ~20 Mo/s d'allocation et 5 millions d'appels de predicat par seconde, sur le main thread, uniquement pour produire un entier.

T2 : sur un cluster, chaque serveur paie ce cout independamment, en plus des rebuilds de SortedItemsCache et des messages pub/sub qui se disputent le meme tick.

**Impact** — Sur un gros hotel des ventes, un simple scoreboard suffit a consommer plusieurs dizaines de millisecondes de main thread par seconde, pour un compteur deja calcule et stocke ailleurs.

**Correctif**

```java
GlobalPlaceholders.java:18 :
    placeholder.register("listed_items", player -> String.valueOf(manager.getListedItemCount()),
            "Returns the number of listed items");

en exposant dans ZAuctionManager (et sur l'interface AuctionManager) :
    @Override
    public int getListedItemCount() {
        // sortedAllItems ne contient QUE les items AVAILABLE && !isExpired (SortedItemsCache:305),
        // ce qui est exactement isActivelyListed().
        return this.sortedItemsCache.getTotalCount(this.plugin.getConfiguration().getSort().defaultSort());
    }

La semantique affichee reste strictement identique, et le cout passe de O(n) avec allocation a O(1) sans allocation.
```

---

<a id="c-099"></a>

### `C-099` — Le repli non atomique de lockItem/unlockItem peut ecraser l'etat terminal DELETED et remettre un item supprime en circulation

- [ ] **Corrigé**
- **Fichier** : `zAuctionHouse Redis/redis/RedisAuctionClusterBridge.java:220`
- **Catégorie** : Duplication d’item — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
// RedisAuctionClusterBridge.java:220-238 -- lecture puis ecritures separees, sans MULTI ni WATCH
// Fallback to non-atomic operation (with race condition risk)
String currentState = jedis.hget(itemKeyStr, FIELD_STATE);
if (STATE_DELETED.equals(currentState)) {
    return LockToken.noop();
}
// A stale LOCKED state (lock key already expired) must not block acquisition.
if (STATE_LOCKED.equals(currentState) && jedis.exists(lockKeyStr)) {
    return LockToken.noop();
}

String setResult = jedis.set(lockKeyStr, token.value(), new redis.clients.jedis.params.SetParams().nx().px(ttlMs));
if (!"OK".equals(setResult)) {
    return LockToken.noop();
}

jedis.hset(itemKeyStr, FIELD_STATE, STATE_LOCKED);   // ecrase tout etat pose entre-temps, DELETED compris
jedis.hset(itemKeyStr, FIELD_LOCK, token.value());

// RedisAuctionClusterBridge.java:276-285 -- meme structure au deverrouillage
String currentToken = jedis.hget(itemKeyStr, FIELD_LOCK);
if (Objects.equals(currentToken, lockToken.value())) {
    jedis.del(lockKeyStr);
    jedis.hdel(itemKeyStr, FIELD_LOCK);
    String currentState = jedis.hget(itemKeyStr, FIELD_STATE);
    if (STATE_LOCKED.equals(currentState)) {
        jedis.hset(itemKeyStr, FIELD_STATE, STATE_AVAILABLE);
    }
}

// RedisAuctionClusterBridge.java:122-125 -- le repli s'active des qu'un scriptLoad echoue
} catch (Exception e) {
    plugin.getLogger().warning("Failed to load Lua scripts: " + e.getMessage() + ". Falling back to non-atomic operations.");
    this.scriptsLoaded = false;
```

**Chronologie**

Le repli s'active des qu'un SCRIPT LOAD echoue -- redemarrage de Redis, SCRIPT FLUSH, coupure passagere -- et reste actif jusqu'a ce que reloadScriptsIfNeeded reussisse.

T0 -- Redis redemarre ; sur le serveur A, loadScripts echoue et scriptsLoaded reste a false. A travaille desormais en mode non atomique sans que rien ne le signale ailleurs.

T1 -- Serveur A veut verrouiller l'item 42. Il lit state : la valeur est AVAILABLE, il continue.

T2 -- Entre ce HGET (ligne 221) et le HSET (ligne 235), le serveur B conclut la suppression definitive de l'item 42 : removeItem(item, EXPIRED, DELETED) ecrit state = DELETED.

T3 -- A execute son SET NX (qui reussit, la cle de verrou etant libre) puis HSET state = LOCKED : l'etat TERMINAL DELETED est ecrase.

T4 -- A libere son verrou : le repli du deverrouillage voit state == LOCKED et le repasse a AVAILABLE (ligne 283). L'item 42, supprime definitivement, est de nouveau annonce comme disponible dans Redis.

T5 -- N'importe quel serveur peut desormais le verrouiller (LOCK_SCRIPT ne bloque que sur DELETED) et un chemin sans revalidation -- RemoveService, qui ne relit jamais la base -- le livre une seconde fois a partir d'un fantome memoire.

**Impact** — Perte de l'etat terminal qui constitue le dernier garde-fou cote Redis, dans une fenetre ou le mode degrade n'est visible que dans les logs d'un seul noeud. Probabilite faible (il faut un echec de chargement des scripts), consequence identique a une duplication.

**Correctif**

```java
Supprimer le repli non atomique : sans script, l'exclusion mutuelle n'est plus garantie, il vaut mieux echouer franchement que verrouiller a tort.

if (!scriptsLoaded) {
    reloadScriptsIfNeeded();
}
if (!scriptsLoaded) {
    plugin.getLogger().severe("Lua scripts unavailable, refusing to lock item " + item.getId());
    return LockToken.noop();   // aucun verrou pose : les services abandonnent proprement
}

Si un repli doit absolument subsister, le rendre conditionnel via WATCH/MULTI/EXEC sur itemKey, et refaire le HSET de l'etat uniquement si la valeur lue n'a pas change entre-temps.
```

---

<a id="c-100"></a>

### `C-100` — Les logs d'annonces multi-stacks sont écrits en concaténant les payloads Base64 avec ';' mais relus par un unique Base64ItemStack.decode qui ne redécoupe rien

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:1003`
- **Catégorie** : Fiabilité — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
Écriture — ZAuctionManager.logItemAction, ligne 1003 (vérifié) :

                encodedItemStack = itemStacks.stream().map(Base64ItemStack::encode).collect(Collectors.joining(";"));

Même schéma dans SellService.java:384 (vérifié).

Lecture — AdminLogsButton.createAdminLogItem, lignes 141-150 (vérifié) : un seul decode, aucun split :

        if (log.itemstack() != null && !log.itemstack().isEmpty()) {
            try {
                ItemStack decoded = Base64ItemStack.decode(log.itemstack());
                if (decoded != null) {
                    itemStacks.add(decoded);
                }
            } catch (Exception e) {
                this.plugin.getLogger().warning("Failed to decode itemstack for log " + log.id() + ": " + e.getMessage());
            }
        }

Le redécoupage existe pourtant ailleurs dans le projet — V3MigrationService.java:285-287 (vérifié) : `if (v3Item.isInventoryType() && itemstack.contains(";")) { String[] itemstacks = itemstack.split(";"); ... }`.
```

**Chronologie**

T0 — Un joueur met en vente une annonce de 5 stacks (un contenu de shulker, cas explicitement supporté par le plugin).

T1 — logItemAction écrit en base la chaîne « <base64_1>;<base64_2>;...;<base64_5> » dans la colonne itemstack du log.

T2 — Un administrateur enquête sur une suspicion de duplication et ouvre l'inventaire admin des logs.

T3 — AdminLogsButton passe la chaîne entière au décodeur Base64. Le caractère ';' n'appartient pas à l'alphabet Base64 : `Base64.getDecoder().decode` lève IllegalArgumentException.

T4 — Le catch avale l'exception avec un simple warning, `itemStacks` reste vide, et l'entrée de log s'affiche SANS aucun item. Toutes les annonces multi-stacks — précisément celles qui portent le plus de valeur et qui sont les plus intéressantes à dupliquer — sont donc invisibles dans la piste d'audit.

Sur un serveur antérieur à 1.20.5, la conséquence est pire : le decode part dans la récursion mutuelle décrite dans le constat correspondant et lève un StackOverflowError, qui n'est PAS rattrapé par le `catch (Exception)` ligne 147 et casse le rendu de la GUI admin.

**Impact** — La piste d'audit administrateur perd l'item pour toutes les annonces multi-stacks : dans une enquête anti-duplication, c'est exactement l'information qui manque. Le mode d'échec est silencieux côté GUI (seul un warning en console) et le format d'écriture est déjà connu ailleurs dans le code, ce qui montre que la lecture est simplement restée en arrière.

**Correctif**

```java
Redécouper à la lecture, exactement comme le fait déjà V3MigrationService :

        if (log.itemstack() != null && !log.itemstack().isEmpty()) {
            for (String encoded : log.itemstack().split(";")) {
                if (encoded.isBlank()) continue;
                try {
                    ItemStack decoded = Base64ItemStack.decode(encoded);
                    if (decoded != null) itemStacks.add(decoded);
                } catch (Throwable t) {   // Throwable : cf. la récursion StackOverflowError pré-1.20.5
                    this.plugin.getLogger().warning("Failed to decode itemstack for log " + log.id() + ": " + t.getMessage());
                }
            }
        }

À terme, factoriser encodage et décodage multi-stacks dans une paire de méthodes utilitaires (`encodeAll(List<ItemStack>)` / `decodeAll(String)`) pour que les deux côtés ne puissent plus diverger, et les utiliser aussi depuis SellService.java:384.
```

---

<a id="c-101"></a>

### `C-101` — SortedItemsCache : une invalidation survenant pendant un rebuild est ecrasee, l'item reste invisible sans que le cache soit marque sale

- [ ] **Corrigé**
- **Fichier** : `utils/cache/SortedItemsCache.java:389`
- **Catégorie** : Cohérence de cache — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
SortedItemsCache.java:296-316 - l'instantane logique est pris ICI :
            Collection<Item> allItems = itemsSupplier.get();
            ...
            for (Item item : allItems) {
                if (item.getStatus() == ItemStatus.AVAILABLE && !item.isExpired()) {

SortedItemsCache.java:342 - les tris prennent plusieurs dizaines de ms sur un gros HDV :
                Arrays.parallelSort(itemArray, SortItem.ASCENDING_DATE.getComparator());

SortedItemsCache.java:386-389 - dirty est nettoye inconditionnellement a la fin :
            sortedAllItems.set(newSortedAllItems);
            sortedByCategoryItems.set(newSortedByCategoryItems);

            dirty.set(false);

(idem l.330 sur le chemin itemCount == 0). Or invalidate() ne prend aucun verrou (l.173-175) :
    public void invalidate() {
        dirty.set(true);
    }

et la lecture suivante croit alors le cache valide (l.242-245) :
        if (!dirty.get()) {
            return CompletableFuture.completedFuture(null);
        }
```

**Chronologie**

T0 - Serveur B : un rebuild demarre. L'instantane des items disponibles est construit aux lignes 304-316, puis les tris commencent (50 000 items = plusieurs dizaines de ms).

T1 - Serveur A : un joueur met un item en vente -> notifyItemListed.

T2 - Serveur B : ItemListedListener:44 addItem(StorageType.LISTED, item) -> ZAuctionManager:243 sortedItemsCache.invalidate() -> dirty = true. Le nouvel item n'est PAS dans l'instantane du T0, la boucle de filtrage etant deja terminee.

T3 - Serveur B : ItemListedListener:47 updateListedItems -> ZAuctionManager:953 ensureCacheValidAsync(). Le rebuild du T0 n'etant pas fini, l'appel est coalesce sur ongoingRebuild et rend le future de CE rebuild-la, celui qui ignore l'item.

T4 - Le rebuild du T0 se termine, publie ses maps puis fait dirty.set(false) l.389, ECRASANT le dirty = true pose en T2.

T5 - Le thenRun de updateListedItems s'execute, ITEMS_LISTED a deja ete vide, chaque joueur recalcule depuis sortedItemsCache : l'item du T1 est absent. Et comme dirty vaut false, aucune lecture ulterieure ne declenchera de rebuild.

T6 - L'item reste invisible dans l'hotel des ventes de B jusqu'a ce qu'un AUTRE addItem/removeItem LISTED survienne. Le vendeur, lui, le voit dans son onglet 'en vente' (getPlayerSellingItems passe par getItemIds, pas par le cache trie) : incoherence visible entre serveurs.

**Impact** — Items mis en vente qui n'apparaissent jamais dans l'hotel des ventes des autres serveurs tant qu'aucun autre evenement ne survient. Plus le rebuild est long (donc plus l'hotel est gros), plus la fenetre est large ; sous forte charge de mises en vente concurrentes, la perte est quasi systematique.

**Correctif**

```java
Marquer la generation avant l'instantane et ne nettoyer dirty que si rien n'a bouge :

    private final AtomicLong generation = new AtomicLong();

    public void invalidate() {
        generation.incrementAndGet();
        dirty.set(true);
    }

    private void rebuildCache() {
        lock.writeLock().lock();
        try {
            if (!dirty.get()) { ... return; }
            long gen = generation.get();          // AVANT itemsSupplier.get()
            Collection<Item> allItems = itemsSupplier.get();
            ...
            sortedAllItems.set(newSortedAllItems);
            sortedByCategoryItems.set(newSortedByCategoryItems);
            if (generation.get() == gen) {
                dirty.set(false);
            }
            lastRebuildTime = System.currentTimeMillis();
        } finally { lock.writeLock().unlock(); }
    }

Appliquer la meme correction au chemin itemCount == 0 (l.326-334).
```

---

<a id="c-102"></a>

### `C-102` — Taxe de vente : has() puis withdraw() ne sont pas atomiques et le retour du retrait est ignore -- l'annonce est publiee sans que la taxe soit percue

- [ ] **Corrigé**
- **Fichier** : `services/SellService.java:336`
- **Catégorie** : Perte d’argent — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
SellService.java:335-346 -- le has() et le withdraw() sont deux operations distinctes, et le withdraw ne rend rien :
        return auctionEconomy.has(player.getUniqueId(), taxResult.taxAmount()).thenApply(hasMoney -> {
            if (!hasMoney) {
                this.plugin.getScheduler().runAtEntity(player, task -> {
                    message(this.plugin, player, Message.TAX_INSUFFICIENT_FUNDS, "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount()));
                });
                return null;
            }

            // Withdraw the tax
            auctionEconomy.withdraw(player.getUniqueId(), taxResult.taxAmount(), "Sell tax (zAuctionHouse)");

ZAuctionEconomy.java:81-94 -- has() n'est qu'une lecture, withdraw() renvoie void :
    public CompletableFuture<Boolean> has(UUID playerId, BigDecimal price) {
        return get(playerId).thenApply(balance -> balance.compareTo(price) >= 0);
    }
    public void withdraw(UUID playerId, BigDecimal value, String reason) {
        this.currencyProvider.withdraw(playerId, value, reason);
    }

CurrenciesAPI VaultProvider.java:34-38 : l'EconomyResponse de `withdrawPlayer` est jete.
```

**Chronologie**

Le chemin de la taxe de vente est un point de prelevement SEPARE de celui de l'achat : il n'est protege par aucun verrou et le code appelant ne peut pas savoir si le retrait a eu lieu.

T0 - Le joueur X a 100 pieces. Economie partagee entre les serveurs (Vault adosse a MySQL, RedisEconomy, CoinsEngine) -- le cas nominal d'un reseau qui installe l'addon Redis.

T1 - Serveur A : X lance `/ah sell` sur un item dont la taxe est de 80. SellService.java:336 `has(80)` -> true.

T2 - Serveur B : X (seconde session, ou un `/pay` declenche depuis B) depense 50. Solde reel : 50.

T3 - Serveur A : SellService.java:346 `withdraw(80)`. Le provider refuse (EconomyResponse FAILURE) ou passe le solde a -30 selon l'implementation ; dans les deux cas la valeur de retour est jetee.

T4 - Serveur A : la chaine continue sans interruption, l'annonce est creee et postSell s'execute. La vente a lieu, la taxe n'a pas ete percue.

Le meme trou existe en sens inverse sur les chemins de remboursement de taxe (SellService.java:87, :98, :126) : `deposit` renvoie void, donc un remboursement qui echoue est totalement invisible.

**Impact** — Fuite de revenus du serveur : l'annonce est publiee sur tout le reseau sans que la taxe correspondante soit prelevee, et les remboursements de taxe peuvent etre perdus sans aucune trace. L'ampleur depend du provider d'economie, mais aucun garde-fou cote plugin n'existe.

**Correctif**

````java
1) Faire remonter le resultat du provider -- meme correction de fond que pour le withdraw d'achat : dans l'API AuctionEconomy, `boolean withdraw(...)` / `boolean deposit(...)`, et dans ZAuctionEconomy valider par delta de solde :
```java
public boolean withdraw(UUID playerId, BigDecimal value, String reason) {
    BigDecimal before = this.currencyProvider.getBalance(playerId);
    if (before.compareTo(value) < 0) return false;
    this.currencyProvider.withdraw(playerId, value, reason);
    return this.currencyProvider.getBalance(playerId).compareTo(before) < 0;
}
```
2) SellService.applySellTaxAsync : supprimer le `has()` prealable (qui ne fait qu'elargir la fenetre) et se fier au retour du withdraw :
```java
if (!auctionEconomy.withdraw(player.getUniqueId(), taxResult.taxAmount(), "Sell tax (zAuctionHouse)")) {
    this.plugin.getScheduler().runAtEntity(player, t -> message(this.plugin, player, Message.TAX_INSUFFICIENT_FUNDS, "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount())));
    return null;   // la vente est annulee, l'annonce n'est pas creee
}
```
3) Logger en SEVERE tout deposit de remboursement qui echoue (SellService.java:87, :98, :126), ces montants etant sinon perdus sans trace.
````

---

<a id="c-103"></a>

### `C-103` — Un changement de statut n'invalide jamais SortedItemsCache alors que le rebuild filtre precisement sur AVAILABLE

- [ ] **Corrigé**
- **Fichier** : `utils/cache/SortedItemsCache.java:305`
- **Catégorie** : Cohérence de cache — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
SortedItemsCache.rebuildCache:304-306 - le statut EST le filtre du cache :
            for (Item item : allItems) {
                if (item.getStatus() == ItemStatus.AVAILABLE && !item.isExpired()) {
                    availableItems.add(item);

Mais sortedItemsCache.invalidate() n'est appele qu'a DEUX endroits (grep sur src/ et API/) : ZAuctionManager.addItem:243 et ZAuctionManager.removeItem:263. Aucun des 10 sites de setStatus (ListedItemsButton:132/:231, ConfirmHelper:47/:72, PurchaseService:107/:149/:178, RemoveService:239/:309, ItemStatusListener:54) ne l'invalide.
```

**Chronologie**

T0 - Serveur B : l'item 42 est LISTED/AVAILABLE, present dans toutes les listes triees de SortedItemsCache.

T1 - Serveur A : un joueur ouvre la confirmation d'achat de l'item 42 -> diffusion IS_PURCHASE_CONFIRM. Sur B, ItemStatusListener:54 pose le statut. La cache triee n'est PAS marquee dirty : l'id 42 y reste. Consequence immediate : ListedItemsButton.getPaginationSize:75 compte l'item, mais onRender:63 (if (!item.isActivelyListed()) continue;) le saute -> case vide en fin de page et pagination qui annonce plus d'items qu'il n'y en a.

T2 - Serveur B : un joueur met un item en vente. addItem(LISTED, ...) -> invalidate() -> rebuild. Cette fois l'item 42 (IS_PURCHASE_CONFIRM) est EXCLU de toutes les listes triees.

T3 - Serveur A : le joueur ferme l'inventaire de confirmation. ConfirmHelper:47 repasse AVAILABLE et diffuse.

T4 - Serveur B : ItemStatusListener branche AVAILABLE -> clearPlayersCache + updateListedItems(item, true, null). Mais la cache triee n'est toujours pas dirty et ne contient plus l'id 42 : getItemIdsListedForSale recalcule depuis sortedItemsCache.getSortedIds et rend une liste SANS 42, donc ListedItemsButton.updateInventory (findIndexOf == -1) sort sans rien inserer.

T5 - L'item 42 est disponible et achetable partout sauf sur B, ou il est totalement invisible tant qu'aucun addItem/removeItem sur LISTED ne force un nouveau rebuild.

**Impact** — Item disponible mais introuvable sur un ou plusieurs serveurs pendant une duree arbitraire (jusqu'au prochain ajout/retrait d'annonce), pagination incoherente et cases vides dans les GUI. Effet inverse egalement : un item non-AVAILABLE reste compte dans getPaginationSize.

**Correctif**

```java
Faire du changement de statut un evenement d'invalidation de premier ordre.
1) Exposer void invalidateSortedItemsCache() dans l'API AuctionManager.
2) Centraliser les mutations : remplacer les appels directs item.setStatus(x) par auctionManager.setItemStatus(item, x) :
    public void setItemStatus(Item item, ItemStatus status) {
        ItemStatus old = item.getStatus();
        if (old == status) return;
        item.setStatus(status);
        if ((old == ItemStatus.AVAILABLE) != (status == ItemStatus.AVAILABLE)
                && this.storageItemsById.get(StorageType.LISTED).containsKey(item.getId())) {
            this.sortedItemsCache.invalidate();
            this.plugin.getCategoryManager().invalidateCategoryCountCache();
        }
    }
3) Cote addon, ItemStatusListener:54 appelle manager.setItemStatus(item, message.newStatus()).
4) Corriger ListedItemsButton.getPaginationSize pour qu'il ne compte que les items reellement affichables, afin que le compte et le rendu soient coherents.
```

---

<a id="c-104"></a>

### `C-104` — ZCategoryManager.computeCategoryCount copie integralement le store LISTED par categorie, et son cache est vide a chaque item liste sur le cluster

- [ ] **Corrigé**
- **Fichier** : `category/ZCategoryManager.java:253`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ZCategoryManager.java:253-271 (verifie) :
    private long computeCategoryCount(String categoryId) {
        long startTime = performanceDebug.start();
        var manager = this.plugin.getAuctionManager();
        var items = manager.getItems(StorageType.LISTED);
        ...
        boolean all = categoryId.equals("all");
        int c = 0;
        for (var item : items) {
            if (!item.isActivelyListed()) continue;
            if (all || item.hasCategory(categoryId)) c++;
        }

ZAuctionManager.java:220-223 (verifie) : getItems(StorageType) ALLOUE `new ArrayList<>(...values())`.

ZAuctionManager.java:241-244 (verifie) - le cache est vide a chaque ajout d'item LISTED :
        if (storageType == StorageType.LISTED) {
            this.plugin.getCategoryManager().invalidateCategoryCountCache();
            this.sortedItemsCache.invalidate();
        }
ZCategoryManager.java:244-246 (verifie) : `public void invalidateCategoryCountCache() { this.categoryCountCache.clear(); }`

Alors que le compte est deja disponible en O(1) - SortedItemsCache.getTotalCount(SortItem) retourne cached.size(), et le filtre du rebuild (`item.getStatus() == ItemStatus.AVAILABLE && !item.isExpired()`, SortedItemsCache.java:305) est exactement isActivelyListed().
```

**Chronologie**

Charge : 50 000 items LISTED, 8 categories + "all" livrees par categories.yml, cluster de 4 serveurs, 10 mises en vente par seconde sur le reseau.

T0 serveur A : un joueur vend -> notifyItemListed.

T1 serveur B : ItemListedListener.java:44 manager.addItem(LISTED, item) -> ZAuctionManager:242 invalidateCategoryCountCache() -> categoryCountCache.clear(). Le cache de categories de B est integralement vide.

T2 serveur B : un joueur ouvre le menu categories -> la GUI resout 9 fois %category_count_<id>% -> 9 x computeCategoryCount -> 9 copies completes du store de 50 000 items (~1,8 Mo) + 450 000 appels a isActivelyListed()/hasCategory(), dans un seul tick, sur le main thread (PlaceholderAPI resout sur le thread appelant).

T3 : a 10 mises en vente par seconde sur le cluster, le cache de categories de B est vide 10 fois par seconde ; tout joueur consultant le menu categories repaye les 9 copies completes a chaque fois. Le cache n'amortit plus rien : plus le cluster est actif, plus le menu coute cher.

**Impact** — L'ouverture du menu categories devient un pic de latence garanti a chaque evenement cluster, pour des compteurs deja calcules et stockes en O(1) dans SortedItemsCache.

**Correctif**

```java
Servir les compteurs depuis SortedItemsCache. ZCategoryManager.computeCategoryCount :

    private long computeCategoryCount(String categoryId) {
        var manager = this.plugin.getAuctionManager();
        var sort = this.plugin.getConfiguration().getSort().defaultSort();
        if (categoryId.equals("all")) return manager.getListedItemCount();
        var category = getCategory(categoryId).orElse(null);
        if (category == null) return 0;
        return manager.getListedItemCount(category);   // -> sortedItemsCache.getTotalCount(category, sort)
    }

en exposant dans ZAuctionManager :
    public int getListedItemCount() { return this.sortedItemsCache.getTotalCount(this.plugin.getConfiguration().getSort().defaultSort()); }
    public int getListedItemCount(Category category) { return this.sortedItemsCache.getTotalCount(category, this.plugin.getConfiguration().getSort().defaultSort()); }

La semantique est identique : le filtre du rebuild (AVAILABLE && !isExpired) est exactement isActivelyListed(). invalidateCategoryCountCache devient alors inutile sur le chemin addItem, puisque SortedItemsCache porte deja sa propre invalidation.
```

---

<a id="c-105"></a>

### `C-105` — ZPlayerCache est un EnumMap nu écrit depuis des threads asynchrones, alors que le contrat de l'API promet la sûreté aux accès concurrents

- [ ] **Corrigé**
- **Fichier** : `utils/cache/ZPlayerCache.java:14`
- **Catégorie** : Fiabilité — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ZPlayerCache.java:14 (vérifié) :

    private final Map<PlayerCacheKey, Object> cache = new EnumMap<>(PlayerCacheKey.class);

ZPlayerCache.java:52-60 (vérifié) — test-puis-écriture non atomique :

    public <T> T getOrCompute(PlayerCacheKey key, Supplier<T> supplier) {
        if (has(key)) {
            return get(key);
        }
        T value = supplier.get();
        set(key, value);
        return value;
    }

Le contrat annonce pourtant l'inverse — API/.../api/cache/PlayerCache.java:6-8 (vérifié) : « Implementations are expected to be thread-safe when accessed from asynchronous tasks interacting with the auction house. »

Écrivains asynchrones vérifiés : SortButton.java:85-90 (`getScheduler().runAsync(...)` puis `cache.set(ITEM_SORT, ...)` et `cache.remove(ITEMS_LISTED)`), ListedItemsButton.java:193+ (`economy.has(...).whenComplete`), ExpireService.java:64, :146, :287, :327 (`clearPlayersCache`), ZAuctionManager.java:694 (`clearPlayersCache` dans un thenAccept), ZAuctionManager.java:866-874 (chemin d'achat).
ZAuctionManager.java:493 (vérifié) : `this.caches.forEach((player, cache) -> cache.remove(keys));` — l'invalidation globale itère et mute ces EnumMap depuis le thread appelant, quel qu'il soit.
Lecteur concurrent : le thread principal à chaque rendu de GUI, via getItemIdsListedForSale -> `cache.getOrCompute(ITEMS_LISTED, ...)` (ZAuctionManager.java:295).
```

**Chronologie**

T0 — Le joueur P a l'hôtel des ventes ouvert sur le serveur B ; son cache contient ITEMS_LISTED incluant l'id 42.

T1 — L'item 42 est vendu ailleurs. Sur B, le traitement passe par un chemin asynchrone qui appelle clearPlayersCache(ITEMS_LISTED) (ExpireService ou adminRemoveItem, hors thread principal). `EnumMap.remove` écrit `vals[ordinal] = null` et décrémente `size`, sans aucune barrière mémoire.

T2 — Le thread principal re-rend l'inventaire. Aucune relation happens-before ne l'oblige à voir cette écriture : `has(key)` peut lire l'ancienne valeur depuis son cache CPU et servir la liste périmée, indéfiniment tant qu'aucune autre synchronisation n'intervient.

T3 — P clique sur l'item 42, qui n'existe plus. Ce clic alimente exactement les chemins « clic sur référence périmée » déjà identifiés (remise sans revalidation DB).

En parallèle, deux écritures concurrentes sur des clés différentes corrompent le champ `size` (incrémentations perdues), ce qui fausse durablement size()/isEmpty(). Et `getOrCompute` étant un test-puis-écriture, deux threads peuvent lancer deux fois le calcul coûteux (sortedItemsCache.getSortedIds ou searchService.search).

**Impact** — Invalidations de cache silencieusement perdues : le joueur continue de voir et de cliquer un item déjà vendu ou retiré. Le bug est non déterministe (dépendant du matériel et du JIT), donc très difficile à reproduire en support, et il amplifie tous les autres défauts de revalidation.

**Correctif**

```java
Rendre le cache réellement concurrent, ce que le contrat promet déjà :

public class ZPlayerCache implements PlayerCache {

    private final Map<PlayerCacheKey, Object> cache = new ConcurrentHashMap<>();

    @Override
    public <T> void set(PlayerCacheKey key, T value) {
        if (value == null) { this.cache.remove(key); return; }   // ConcurrentHashMap interdit null
        if (!key.getRawType().isInstance(value)) {
            throw new IllegalArgumentException("Invalid type for key " + key + ": expected " + key.getType().getType());
        }
        this.cache.put(key, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOrCompute(PlayerCacheKey key, Supplier<T> supplier) {
        return (T) this.cache.computeIfAbsent(key, k -> supplier.get());   // atomique
    }
}

Corriger aussi la mutation en place de ZAuctionManager.removeFromCache (ligne 983, `items.rem(item.getId())`) qui modifie un IntArrayList (`int[] data; int size;` sans synchronisation) potentiellement en cours d'itération par le thread de rendu : remplacer la mutation par un remplacement de liste (`cache.set(ITEMS_LISTED, copieSansId)`).
```

---

<a id="c-106"></a>

### `C-106` — checkAvailability est un pre-controle TOCTOU strictement redondant avec LOCK_SCRIPT : 6 emprunts de connexion Redis par achat au lieu de 4

- [ ] **Corrigé**
- **Fichier** : `REDIS/RedisAuctionClusterBridge.java:157`
- **Catégorie** : Performance — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```lua
LOCK_SCRIPT, RedisAuctionClusterBridge.java:40-67 (verifie) - refuse dans exactement 2 cas, plus la course SET NX :
            local currentState = redis.call('HGET', itemKey, 'state')
            if currentState == 'DELETED' then
                return 0
            end
            if currentState == 'LOCKED' and redis.call('EXISTS', lockKey) == 1 then
                return 0
            end
            local acquired = redis.call('SET', lockKey, tokenValue, 'NX', 'PX', ttlMs)
            if not acquired then
                return 0
            end

checkAvailability, RedisAuctionClusterBridge.java:157-177 (verifie) - LES MEMES 2 cas, dans une connexion separee :
                String state = jedis.hget(key, FIELD_STATE);
                if (STATE_DELETED.equals(state)) {
                    return false;
                }
                if (!STATE_LOCKED.equals(state)) {
                    return true;
                }
                return !jedis.exists(lockKey(item));

Les 4 sites d'appel enchainent systematiquement les deux (tous verifies) :
  PurchaseService.java:82 checkAvailability puis :93 lockItem
  RemoveService.java:200 `checkAvailabilityStep(...).thenCompose(available -> acquireLockStep(...))`
  ExpireService.java:265 checkAvailability puis :272 lockItem
  ZAuctionManager.java:679 checkAvailability puis :687 lockItem
```

**Chronologie**

Charge : 30 achats et 30 retraits par seconde sur le reseau.

Decompte exact des emprunts de connexion Redis pour UN achat aujourd'hui :

1. checkAvailability -> 1 emprunt, 1 a 2 allers-retours (HGET, + EXISTS si LOCKED)

2. lockItem -> 1 emprunt, 1 aller-retour (EVALSHA)

3. notifyItemStatusChange -> 1 emprunt, 1 aller-retour (PUBLISH)

4. notifyItemBought -> 2 emprunts imbriques, 3 allers-retours (HSET, EXPIRE, PUBLISH)

5. unlockItem -> 1 emprunt, 1 aller-retour (EVALSHA)

Soit 6 emprunts et 7 a 8 allers-retours par achat ; meme decompte pour un retrait.

A 60 operations/s : ~360 emprunts/s. L'etape 1 est du gaspillage pur : le seul cas ou checkAvailability rend true et ou lockItem echoue est la course sur le SET NX, que le code sait deja traiter (token noop, PurchaseService.java:100).

Et sur le plan de la surete, le pre-controle n'apporte rien : entre le TRUE rendu par checkAvailability et le SET NX de lockItem, l'etat peut avoir change sur un autre serveur - c'est precisement pourquoi LOCK_SCRIPT refait la verification de maniere atomique. checkAvailability est strictement plus faible que lockItem, jamais plus fort, et entretient l'illusion d'une verification qui n'en est pas une.

**Impact** — Environ 33 % des emprunts de pool Jedis et ~40 % des allers-retours reseau sont evitables sur les 4 chemins les plus chauds (achat, retrait, expiration clusteree, retrait admin). Cela rapproche l'epuisement du pool sous charge et allonge la fenetre entre le verrou et la revalidation DB.

**Correctif**

```lua
1) LOCK_SCRIPT (RedisAuctionClusterBridge.java:40-67) : distinguer les deux causes de refus au lieu de rendre 0 dans les deux cas :
            local currentState = redis.call('HGET', itemKey, 'state')
            if currentState == 'DELETED' then return -1 end
            if currentState == 'LOCKED' and redis.call('EXISTS', lockKey) == 1 then return -1 end
            local acquired = redis.call('SET', lockKey, tokenValue, 'NX', 'PX', ttlMs)
            if not acquired then return 0 end
            redis.call('HSET', itemKey, 'state', 'LOCKED')
            redis.call('HSET', itemKey, 'lock', tokenValue)
            return 1

2) Ajouter au contrat API/.../api/cluster/AuctionClusterBridge.java une methode par defaut retro-compatible (LocalAuctionClusterBridge n'a rien a changer) :
    enum LockOutcome { ACQUIRED, UNAVAILABLE, CONTENDED }
    record LockAttempt(LockOutcome outcome, LockToken token) {}
    default CompletableFuture<LockAttempt> checkAndLock(Item item, UUID lockerId, StorageType storageType) {
        return checkAvailability(item).thenCompose(available -> {
            if (!available) return CompletableFuture.completedFuture(new LockAttempt(LockOutcome.UNAVAILABLE, LockToken.noop()));
            return lockItem(item, lockerId, storageType).thenApply(t -> LockToken.noop().value().equals(t.value())
                    ? new LockAttempt(LockOutcome.CONTENDED, t) : new LockAttempt(LockOutcome.ACQUIRED, t));
        });
    }
RedisAuctionClusterBridge surcharge checkAndLock avec un SEUL evalsha et mappe -1 -> UNAVAILABLE, 0 -> CONTENDED, 1 -> ACQUIRED.

3) PurchaseService.java:82-104, RemoveService.java:200 (fusionner checkAvailabilityStep et acquireLockStep), ExpireService.java:265-272 et ZAuctionManager.java:679-687 appellent checkAndLock ; les motifs ITEM_NOT_AVAILABLE et LOCK_FAILED sont preserves a l'identique.

4) Documenter checkAvailability comme methode d'AFFICHAGE non engageante, qui NE DOIT PAS preceder lockItem.
```

---

<a id="c-107"></a>

### `C-107` — getCache() ressuscite indéfiniment le cache d'un joueur déconnecté : la map est indexée par l'objet Player, et onInventoryClose est appelée après PlayerQuitEvent

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:488`
- **Catégorie** : Cycle de vie — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ZAuctionManager.java:62 et :488 (vérifié) — indexation par l'objet Player, et création à la demande sans aucun test de connexion :

    private final Map<Player, PlayerCache> caches = new ConcurrentHashMap<>();
    ...
    return this.caches.computeIfAbsent(player, p -> new ZPlayerCache());

Unique purge — PlayerListener.java:42-43 (vérifié) :

    public void onQuit(PlayerQuitEvent event) {
        this.plugin.getAuctionManager().removeCache(event.getPlayer());

Appelant post-déconnexion — ConfirmHelper.onInventoryClose, lignes 32-37 (vérifié) :

    public void onInventoryClose(@NonNull Player player, @NonNull InventoryEngine inventory) {
        super.onInventoryClose(player, inventory);
        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);            // computeIfAbsent -> ré-insère
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);

À comparer avec clearPlayerCache (ZAuctionManager.java:979-980, vérifié), qui lui prend soin de ne pas ressusciter l'entrée : `if (this.caches.containsKey(player)) { var cache = this.caches.get(player); ... }`.
```

**Chronologie**

T0 — Le joueur P a un inventaire de confirmation d'achat ouvert et se déconnecte brutalement.

T1 — PlayerQuitEvent est traité : removeCache(P) retire l'entrée de `caches`.

T2 — La fermeture de l'inventaire est propagée ensuite par zMenu, et ConfirmHelper.onInventoryClose appelle getCache(player) : `computeIfAbsent` ré-insère une entrée pour un CraftPlayer désormais hors ligne.

T3 — Plus aucun PlayerQuitEvent ne surviendra pour ce Player : l'entrée est définitive. La clé étant l'objet CraftPlayer lui-même, elle maintient une référence forte vers l'entité, son inventaire et, par ricochet, son monde.

T4 — À chaque déconnexion dans ces conditions, une entrée s'ajoute. `clearPlayersCache` (ligne 493) itère sur toutes ces entrées mortes à chaque vente, achat ou expiration : la fuite coûte aussi du temps CPU en croissance continue.

Le même schéma existe sur les complétions asynchrones tardives (PurchaseService, ExpireService) qui appellent getCache après la déconnexion du joueur.

**Impact** — Fuite mémoire progressive de CraftPlayer (et de tout le graphe d'objets accroché) sur les serveurs à fort renouvellement, plus un ralentissement continu de clearPlayersCache. Ce n'est pas un vecteur de duplication, mais c'est un défaut de cycle de vie qui finit en OutOfMemoryError sur un serveur au long cours.

**Correctif**

```java
1) Indexer par UUID plutôt que par Player, ce qui supprime la référence forte :

    private final Map<UUID, PlayerCache> caches = new ConcurrentHashMap<>();

    public PlayerCache getCache(Player player) {
        return this.caches.computeIfAbsent(player.getUniqueId(), p -> new ZPlayerCache());
    }

2) Ne pas créer d'entrée pour un joueur déconnecté — ajouter une variante de lecture seule et l'utiliser dans les chemins de fermeture/complétion tardive :

    public Optional<PlayerCache> peekCache(Player player) {
        return Optional.ofNullable(this.caches.get(player.getUniqueId()));
    }

et dans ConfirmHelper.onInventoryClose : `var cache = manager.peekCache(player).orElse(null); if (cache == null) return;`
3) Traiter PlayerQuitEvent en priorité MONITOR, après zMenu, pour que la purge soit bien la dernière opération.
```

---

<a id="c-108"></a>

### `C-108` — migratePlayers ecrase le pseudo des joueurs V4 existants par le placeholder "Unknown"

- [ ] **Corrigé**
- **Fichier** : `migration/v3/V3MigrationService.java:183`
- **Catégorie** : Perte de données — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
V3MigrationService.collectPlayers:179-195 - le placeholder est mis pour tout UUID sans nom connu :
            if (item.getBuyer() != null) {
                players.putIfAbsent(item.getBuyer(), "Unknown");
            }
        }

        for (V3Transaction transaction : transactions) {
            if (transaction.getSeller() != null) {
                players.putIfAbsent(transaction.getSeller(), "Unknown");
            }
            if (transaction.getBuyer() != null) {
                players.putIfAbsent(transaction.getBuyer(), "Unknown");
            }

V3MigrationService.migratePlayers:209 - et il est ecrit en UPSERT :
                playerRepo.upsertPlayer(entry.getKey(), entry.getValue());

PlayerRepository.upsertPlayer:26-31 :
    public void upsertPlayer(UUID uniqueId, String name) {
        this.upsert(schema -> {
            schema.uuid("unique_id", uniqueId).primary();
            schema.string("name", name);
        });
    }

Sarah UpsertRequest emet name = ? dans ON DUPLICATE KEY UPDATE (MySQL) / name = excluded.name (SQLite).
```

**Chronologie**

T0 - Le joueur X joue depuis des mois sur le reseau. PlayerListener.onConnect:21 upsertPlayer(player) a ecrit la ligne (X, 'SonVraiPseudo') dans %prefix%players, ligne partagee par tous les serveurs.

T1 - L'admin migre le V3. X y apparait uniquement comme acheteur d'un vieil item ou dans une transaction : collectPlayers ne dispose pas de son nom (V3 ne stocke que sellerName) et met 'Unknown'.

T2 - migratePlayers:209 appelle upsertPlayer(X, 'Unknown'). Le vrai pseudo est ECRASE.

T3 - Au prochain chargement sur n'importe quel serveur, ItemLoaderUtils:68 et :72 getPlayerName(players, uuid) renvoient 'Unknown' : tous les items vendus ou achetes par X affichent 'Unknown' comme vendeur/acheteur. ZAuctionManager:784 propage 'Unknown' dans les messages d'achat inter-serveurs, et PlayerRepository.selectByName devient ambigu (des dizaines de lignes portent le meme nom).

T4 - La correction n'intervient qu'a la prochaine connexion de X sur un serveur.

**Impact** — Corruption de la table de reference des pseudos lors d'une migration dans une base V4 deja peuplee : historiques, messages d'achat et affichages des items deviennent illisibles pour tous les joueurs concernes, et la recherche par nom devient ambigue.

**Correctif**

Ne jamais ecraser un nom connu. Dans migratePlayers, sauter les entrees dont la valeur est le placeholder :
    if ("Unknown".equals(entry.getValue())) continue;
L'UUID sera de toute facon insere par la contrainte de cle etrangere ou par la prochaine connexion. Alternative plus propre : ajouter a PlayerRepository une methode insertIfAbsent(uuid, name) utilisant un INSERT ... ON CONFLICT DO NOTHING, et l'employer ici a la place de upsertPlayer.

---

<a id="c-109"></a>

### `C-109` — selectItem() confond trois causes de null : une economie absente de economies.yml est interpretee comme une vente et purge l'item de tous les hotels des ventes du reseau

- [ ] **Corrigé**
- **Fichier** : `storage/ZStorageManager.java:186`
- **Catégorie** : Cohérence d’état — **Sévérité** : Moyenne — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ZStorageManager.selectItem:176-211 (verifie) rend null dans TROIS situations semantiquement opposees.

(a) ligne absente ou filtree parce que DELETED - ItemRepository.select(int):51-56 (verifie) :

        return select(ItemDTO.class, schema -> {
            schema.where("id", id);
            schema.where("storage_type", "!=", StorageType.DELETED.name());
        }).stream().findFirst();

(b) economie introuvable - ZStorageManager.java:186-190 :

            var optionalAuctionEconomy = this.plugin.getEconomyManager().getEconomy(dto.economy_name());
            if (optionalAuctionEconomy.isEmpty()) {
                this.plugin.getLogger().severe("Impossible to find the economy " + dto.economy_name() + " for auction item id " + dto.id() + ", skip it...");
                return null;
            }

(c) item_type BID ou RENT - ZStorageManager.java:192-209 : les deux branches sont vides et le `return null` final s'applique.

Les appelants ne distinguent rien. PurchaseService.java:120-124 (verifie) :

                            if (dbItem == null || dbItem.getBuyerUniqueId() != null) {
                                resultHolder.set(PurchaseResult.failure("Item already sold", PurchaseFailReason.ITEM_NOT_AVAILABLE));

ExpireService.java:282-289 (verifie) va plus loin et detruit l'etat memoire :

                                            if (dbItem == null || dbItem.getBuyerUniqueId() != null) {
                                                // Sold/deleted on another server: remove the local ghost, do NOT expire.
                                                this.plugin.getScheduler().runNextTick(w -> {
                                                    this.auctionManager.removeItem(StorageType.LISTED, item.getId());
                                                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
                                                });

A noter au passage : le supplyAsync de selectItem (l.177-210) et celui de findUniqueId (l.215) sont les seuls du fichier a ne pas recevoir this.plugin.getExecutorService() et tournent donc sur ForkJoinPool.commonPool.
```

**Chronologie**

T0 - Un administrateur renomme une economie dans economies.yml (ou desactive le plugin d'economie correspondant) et fait /ah admin reload sur le serveur A. 400 items en base referencent encore l'ancien nom.

T1 - Serveur A : le rendu de l'hotel des ventes declenche l'expiration paresseuse d'un de ces items. expireListedItemClustered prend le verrou, appelle selectItem -> null (cause b), avec un simple log SEVERE noye dans la console.

T2 - Serveur A interprete ce null comme "vendu ailleurs" : ExpireService.java:285-288 retire l'item du store LISTED en memoire et purge les caches joueurs. La ligne en base, elle, reste LISTED.

T3 - Serveur B fait exactement de meme de son cote. L'item a disparu de tous les hotels des ventes du reseau alors qu'il est toujours LISTED en base et que personne ne l'a achete.

T4 - Si un joueur avait deja l'inventaire ouvert et clique l'item, PurchaseService.java:121 lui annonce "Item already sold" : l'item est inachetable, invisible, et le vendeur ne peut plus le recuperer. La meme ambiguite ferait passer un item BID/RENT (cause c) pour un item vendu.

**Impact** — Un item parfaitement valide devient invisible et inachetable sur tout le reseau, et son vendeur ne peut plus le recuperer, sur une simple erreur de configuration d'economie - un cas d'exploitation courant. Personne ne relie la disparition a la cause : le seul indice est un log SEVERE par item. Aucune duplication, mais une perte fonctionnelle silencieuse et generalisee.

**Correctif**

```diff
Rendre le resultat explicite plutot que de surcharger null :

    public enum ItemLookup { FOUND, GONE, UNAVAILABLE }
    public record ItemLookupResult(ItemLookup status, Item item) {}

    @Override
    public CompletableFuture<ItemLookupResult> selectItemState(int id) {
        return CompletableFuture.supplyAsync(() -> {
            var optional = with(ItemRepository.class).selectIncludingDeleted(id); // sans le filtre DELETED
            if (optional.isEmpty()) return new ItemLookupResult(ItemLookup.GONE, null);
            var dto = optional.get();
            if (dto.storage_type() == StorageType.DELETED) return new ItemLookupResult(ItemLookup.GONE, null);
            var eco = this.plugin.getEconomyManager().getEconomy(dto.economy_name());
            if (eco.isEmpty() || dto.item_type() != ItemType.AUCTION)
                return new ItemLookupResult(ItemLookup.UNAVAILABLE, null); // probleme LOCAL, pas une vente
            ...
            return new ItemLookupResult(ItemLookup.FOUND, auctionItem);
        }, this.plugin.getExecutorService());
    }

Cote appelants :
- PurchaseService : GONE -> echec ITEM_NOT_AVAILABLE comme aujourd'hui ; UNAVAILABLE -> echec INTERNAL_ERROR, message distinct au joueur, item laisse intact en memoire.
- ExpireService.expireListedItemClustered : ne purger le fantome local (l.285-288) que sur GONE ; sur UNAVAILABLE, se contenter d'un WARNING et reessayer plus tard.

Passer aussi this.plugin.getExecutorService() aux supplyAsync des lignes 177 et 215, comme le font toutes les autres methodes du fichier, pour ne plus poster d'IO JDBC sur le commonPool.
```

---

## Constats de sévérité basse (10)

<a id="c-110"></a>

### `C-110` — Contrat lockItem non specifie et divergent entre les deux implementations : LocalAuctionClusterBridge signale l'echec par un future en erreur, le bridge Redis par un jeton noop

- [ ] **Corrigé**
- **Fichier** : `cluster/LocalAuctionClusterBridge.java:23`
- **Catégorie** : Contrat API — **Sévérité** : Basse — **Contre-expertise** : confirmé

**Preuve dans le code**

```java
V4/cluster/LocalAuctionClusterBridge.java:23-31
    public CompletableFuture<LockToken> lockItem(Item item, UUID buyerId, StorageType storageType) {
        var existingLock = itemLocks.putIfAbsent(item.getId(), buyerId);

        if (existingLock != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Item already locked by another player"));
        }

        return CompletableFuture.completedFuture(LockToken.of(item));
    }

REDIS/.../RedisAuctionClusterBridge.java:199 (meme situation, autre convention)
                        return LockToken.noop();

V4/API/.../api/cluster/AuctionClusterBridge.java (javadoc de lockItem : aucune mention de l'echec)
     * @return future containing a lock token to be used when unlocking
     */
    CompletableFuture<LockToken> lockItem(Item item, UUID buyerId, StorageType storageType);

Consequence cote appelant : V4/ZAuctionManager.java:1128
                    boolean stopped = result == null || result.getFailReason() == RemoveFailReason.INSUFFICIENT_SPACE || result.getFailReason() == RemoveFailReason.INTERNAL_ERROR;
```

**Chronologie**

T0 - Serveur A en mono-serveur (bridge Local, cas nominal d'un client sans addon, ou cas degrade du constat 1). Un joueur possede 20 annonces et clique sur RemoveAllSellingButton.

T1 - ZAuctionManager.processBulkItems chaine les 20 retraits. Le 5e item est deja verrouille sur ce serveur (achat concurrent d'un autre joueur, ou verrou orphelin du constat precedent) : lockItem renvoie un failedFuture au lieu d'un LockToken.noop().

T2 - RemoveService.java:232 ne voit jamais ce cas : le future echoue avant, l'exception remonte a handleRemovalException (ligne 270) qui, faute de context.result renseigne, renvoie `RemoveResult.failure("Internal error", RemoveFailReason.INTERNAL_ERROR)` (ligne 287).

T3 - ZAuctionManager.java:1128 traite INTERNAL_ERROR comme un arret : la chaine s'interrompt. Les 15 annonces restantes ne sont pas retirees, alors que la seule cause est un verrou contendu qui aurait du produire LOCK_FAILED (non bloquant).

T4 - Le joueur voit le message REMOVE_ALL_ITEMS avec un compteur partiel et recommence, relancant a chaque fois une nouvelle chaine complete sur un snapshot perime (finishBulkRemoval ligne 1166).

T5 - En cluster le meme code renvoie proprement LOCK_FAILED et continue : le comportement du plugin depend donc de l'implementation du bridge, sans que rien dans l'API ne le documente.

**Impact** — Comportement fonctionnel different selon le bridge installe, alors que l'interface est censee etre l'unique point de variation. Un echec de verrou benin est promu en erreur interne et interrompt les operations de masse. Tout addon tiers implementant AuctionClusterBridge (le point d'extension documente par le javadoc de la classe) n'a aucun moyen de savoir laquelle des deux conventions adopter.

**Correctif**

Specifier la convention dans le javadoc de AuctionClusterBridge.lockItem : "l'echec d'acquisition DOIT etre signale par LockToken.noop() ; le future ne doit echouer qu'en cas de panne du transport". Corriger LocalAuctionClusterBridge.lockItem pour renvoyer `CompletableFuture.completedFuture(LockToken.noop())` au lieu de failedFuture. Symetriquement, faire que LocalAuctionClusterBridge.unlockItem verifie l'appartenance (`itemLocks.remove(item.getId(), ownerUuid)`) au lieu d'un remove inconditionnel, et distinguer dans RemoveService/PurchaseService les echecs de verrou (non bloquants pour le bulk) des vraies erreurs internes.

---

<a id="c-111"></a>

### `C-111` — Le orTimeout sur lockItem fuit le verrou Redis : le jeton n'est capture que dans l'etape suivante

- [ ] **Corrigé**
- **Fichier** : `services/PurchaseService.java:92`
- **Catégorie** : Race condition — **Sévérité** : Basse — **Contre-expertise** : contesté

**Preuve dans le code**

```java
// PurchaseService.java:92-97
                    return clusterBridge.lockItem(item, player.getUniqueId(), StorageType.LISTED)
                            .orTimeout(performanceConfig.lockItemTimeoutMs(), TimeUnit.MILLISECONDS);

                }).thenCompose(token -> {
                    // Store token for exception cleanup
                    tokenHolder.set(token);

// PurchaseService.java:166-173
                    // Ensure lock is released on any exception
                    var token = tokenHolder.get();
                    if (token != null && !LockToken.noop().value().equals(token.value())) {
                        clusterBridge.unlockItem(item, token, StorageType.LISTED).exceptionally(unlockError -> {

// RemoveService.java:222 puis :230 puis :294 -- meme defaut
        return clusterBridge.lockItem(context.item, player.getUniqueId(), context.storageType).orTimeout(config.lockItemTimeoutMs(), TimeUnit.MILLISECONDS);
...
        context.token = token;
...
        if (context.token != null && !LockToken.noop().value().equals(context.token.value())) {
```

**Chronologie**

T0 serveur A : plusieurs achats et retraits simultanes ; tous les workers de commonPool sont bloques sur jedisPool.getResource()/evalsha (redis-config.timeout: 2000 sert de socketTimeoutMillis). T1 A : la tache runAsync de lockItem pour l'item 42 reste en file. T2 A : a 5000 ms (config.yml l.995 lock-item-ms: 5000) le orTimeout de PurchaseService.java:93 complete le futur exceptionnellement. La lambda thenCompose de la l.95 n'est donc JAMAIS invoquee et tokenHolder reste null. T3 A : la tache finit par s'executer -- orTimeout n'annule rien -- et LOCK_SCRIPT (RedisAuctionClusterBridge.java:41-67) execute SET auction:lock:42 "item:42" NX PX 30000 puis HSET auction:item:42 state LOCKED. Le verrou est pose dans Redis alors que plus personne cote Java ne le detient. T4 A : exceptionally (l.167) lit tokenHolder.get() == null, la condition l.168 est fausse, aucun unlockItem n'est emis. T5 serveurs A, B et C : pendant 30 s (lock-ttl-seconds), RedisAuctionClusterBridge.checkAvailability (l.170-177 : state == LOCKED et EXISTS lockKey == 1) renvoie false partout : l'item 42 est inachetable et irretirable sur tout le reseau. T6 A : exceptionally l.176 teste item.getStatus() == IS_BEING_PURCHASED, or le statut vaut encore IS_PURCHASE_CONFIRM (pose hors verrou par ListedItemsButton.java:231 et deja diffuse au cluster) : aucune restauration, aucun AVAILABLE rediffuse. Passe la TTL de 30 s l'item redevient verrouillable, mais il reste masque de tous les hotels des ventes du reseau de facon permanente, car plus rien ne remettra son statut a AVAILABLE.

**Impact** — Sous charge (exactement le moment ou les timeouts se declenchent), chaque timeout sur lockItem gele un item pendant 30 s pour l'ensemble du cluster et le masque definitivement de toutes les interfaces. RemoveService.java:222/230/294 a strictement le meme defaut. Aucun log ne mentionne le jeton perdu.

**Correctif**

Capturer le jeton avant que le timeout puisse court-circuiter l'etape : return clusterBridge.lockItem(item, player.getUniqueId(), StorageType.LISTED).whenComplete((token, err) -> { if (token != null) tokenHolder.set(token); }).orTimeout(performanceConfig.lockItemTimeoutMs(), TimeUnit.MILLISECONDS); -- whenComplete est branche sur le futur SOURCE, il s'execute donc meme si le futur derive a deja expire. Appliquer la meme correction a RemoveService.acquireLockStep (l.222) en affectant context.token dans un whenComplete au lieu de changeStatusAndNotifyStep (l.230). En complement, restaurer le statut dans exceptionally des que item.getStatus() est un etat IS_* (pas seulement IS_BEING_PURCHASED) afin de rediffuser AVAILABLE.

---

<a id="c-112"></a>

### `C-112` — adminRemoveItem ne relache pas le verrou en cas d'exception : avec LocalAuctionClusterBridge (sans TTL) l'item reste verrouille jusqu'au redemarrage

- [ ] **Corrigé**
- **Fichier** : `ZAuctionManager.java:705`
- **Catégorie** : Fiabilité — **Sévérité** : Basse — **Contre-expertise** : contesté

**Preuve dans le code**

```java
ZAuctionManager.java:705-709 (aucun unlockItem, contrairement aux deux autres services)
        }).exceptionally(e -> {
            this.plugin.getLogger().severe("Failed to remove item for admin: " + e.getMessage());
            inventoryManager.updateInventory(admin);
            return null;
        });

A comparer avec V4/services/PurchaseService.java:167-173
                    var token = tokenHolder.get();
                    if (token != null && !LockToken.noop().value().equals(token.value())) {
                        clusterBridge.unlockItem(item, token, StorageType.LISTED).exceptionally(unlockError -> {

et V4/services/RemoveService.java:293-299 (releaseLockOnError)
        if (context.token != null && !LockToken.noop().value().equals(context.token.value())) {
            clusterBridge.unlockItem(context.item, context.token, context.storageType).exceptionally(unlockError -> {

V4/cluster/LocalAuctionClusterBridge.java:16 et 24-25 (map en memoire, aucun TTL, aucune purge)
    private final ConcurrentHashMap<Integer, UUID> itemLocks = new ConcurrentHashMap<>();
        var existingLock = itemLocks.putIfAbsent(item.getId(), buyerId);
```

**Chronologie**

T0 - Serveur A tourne SANS l'addon Redis (ou l'addon a echoue, cf. constat 1) : le bridge est LocalAuctionClusterBridge.

T1 - Un admin clique sur un item dans AdminPurchasedItemsButton (ligne 47). checkAvailability renvoie true, lockItem fait putIfAbsent(42, uuidAdmin) : la map locale contient desormais 42.

T2 - Le bloc thenAccept (ZAuctionManager.java:690-703) leve une exception avant la ligne 703 : par exemple giveItem ligne 696 -> ZAuctionManager.java:931 player.getInventory().addItem(...) execute hors du thread principal ou de la region Folia du joueur, ou item.getSellerUniqueId() null ligne 698, ou l'inventaire zMenu ligne 701.

T3 - Le controle passe au .exceptionally ligne 705 : un SEVERE est logge, l'inventaire est rafraichi, et c'est tout. La ligne 703 (unlockItem) n'est jamais atteinte.

T4 - L'entree 42 reste dans itemLocks pour toute la duree de vie du serveur : LocalAuctionClusterBridge n'a ni TTL, ni tache de purge, ni expiration. Desormais checkAvailability(42) renvoie false pour tout le monde.

T5 - L'item 42 devient definitivement inachetable (PurchaseService.java:86 -> ITEM_NOT_AVAILABLE) et irretirable (RemoveService.java:216 -> ITEM_NOT_AVAILABLE) sur ce serveur, y compris par son proprietaire. Avec le bridge Redis le meme scenario ne bloque que lock-ttl-seconds (30 s), mais il laisse aussi state=LOCKED dans le hash, ce qui bloque checkAvailability tant que le lockKey n'a pas expire.

**Impact** — Item definitivement gele (invendable, irretirable, invisible dans les operations de masse) apres la moindre exception dans le bloc admin, en mono-serveur. Comme le retour de updateItem ligne 693 est egalement ignore et que giveItem ligne 696 s'execute quoi qu'il arrive, l'admin peut de surcroit repartir avec l'item alors que la mise a jour base a echoue.

**Correctif**

Extraire le jeton dans un AtomicReference<LockToken> comme le fait PurchaseService (tokenHolder), et ajouter dans le .exceptionally ligne 705 la meme liberation defensive : `var token = tokenHolder.get(); if (token != null && !LockToken.noop().value().equals(token.value())) clusterBridge.unlockItem(item, token, storageType).exceptionally(...)`. Ajouter en complement une purge de securite dans LocalAuctionClusterBridge (horodatage du verrou + expiration equivalente au lock-ttl du bridge Redis) pour qu'aucun oubli d'unlock ne puisse briquer un item a vie.

---

<a id="c-113"></a>

### `C-113` — L'addon Redis s'installe sur un plugin principal qui peut avoir échoué : seul un test de nullité est fait, jamais isEnabled()

- [ ] **Corrigé**
- **Fichier** : `REDIS/ZAuctionHouseRedis.java:62`
- **Catégorie** : Cycle de vie — **Sévérité** : Basse — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ZAuctionHouseRedis.onEnable, lignes 61-67 (vérifié) :

        var pluginManager = getServer().getPluginManager();
        this.auctionPlugin = (AuctionPlugin) pluginManager.getPlugin("zAuctionHouse");
        if (this.auctionPlugin == null) {
            getLogger().severe("zAuctionHouse is not loaded. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

`getPlugin(...)` renvoie l'instance dès que le plugin est CHARGÉ, qu'il soit activé ou non : `this.auctionPlugin.isEnabled()` n'est jamais testé.
Or le plugin principal se désactive lui-même en cas d'échec base — ZStorageManager.java:52-57 (vérifié) :

        if (!databaseConnection.isValid()) {
            this.plugin.getLogger().severe("Unable to connect to database !");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return false;
        }

et ZAuctionPlugin.java:137 (vérifié) : `if (!this.storageManager.onEnable()) return;`.
plugin.yml de l'addon (vérifié) : `depend: [zAuctionHouse]` — la dépendance garantit l'ordre de chargement, pas que la dépendance se soit activée avec succès.
```

**Chronologie**

T0 — Sur le serveur C, MySQL est momentanément injoignable au démarrage.

T1 — ZStorageManager.onEnable détecte la connexion invalide, logge « Unable to connect to database ! » et appelle disablePlugin(zAuctionHouse). ZAuctionPlugin.onEnable sort ligne 137 : aucune commande, aucun listener, aucun service enregistré.

T2 — Bukkit poursuit néanmoins l'activation des plugins suivants : zAuctionHouseRedis démarre. Son test ligne 63 passe (l'instance existe, elle est juste désactivée).

T3 — L'addon ouvre son pool Jedis, démarre son thread abonné puis, dans validateAndRegisterUUID (ligne 203), appelle `this.auctionPlugin.getScheduler().runTimerAsync(...)` : planifier une tâche pour un plugin désactivé déclenche une IllegalPluginAccessException non rattrapée, qui remonte au PluginManager sous la forme d'un pavé d'erreur peu lisible.

T4 — Selon le point exact de la rupture, un pool Jedis et un thread abonné peuvent rester ouverts alors que l'addon est en échec.

**Impact** — Diagnostic brouillé : la cause réelle (base injoignable) est noyée sous une exception secondaire de l'addon, et l'administrateur enquête sur le mauvais plugin. Ressources Redis potentiellement laissées ouvertes. Le nœud est de toute façon hors service, donc pas de risque de duplication ici.

**Correctif**

```java
Tester l'état d'activation et sortir proprement :

        this.auctionPlugin = (AuctionPlugin) pluginManager.getPlugin("zAuctionHouse");
        if (this.auctionPlugin == null || !((org.bukkit.plugin.Plugin) this.auctionPlugin).isEnabled()) {
            getLogger().severe("zAuctionHouse is not loaded or failed to enable (check the errors above, most likely a database failure).");
            getLogger().severe("zAuctionHouseRedis cannot run without it. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

Et s'assurer que tout chemin de sortie anticipée de onEnable ferme le pool Jedis et arrête le thread abonné déjà démarrés (appeler onDisable(), ou extraire une méthode `shutdownResources()` partagée).
```

---

<a id="c-114"></a>

### `C-114` — L'expiration ne repose que sur l'horloge locale de chaque serveur : aucune source de temps partagee

- [ ] **Corrigé**
- **Fichier** : `items/ZItem.java:168`
- **Catégorie** : Race condition — **Sévérité** : Basse — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
// ZItem.java:168-170
@Override
public boolean isExpired() {
    return System.currentTimeMillis() >= this.expiredAt.getTime() && this.expiredAt.getTime() != 0;
}

// SellService.java:146-147 -- l'echeance est calculee avec l'horloge du serveur qui met en vente
long expiration = configuration.getSellExpiration().getExpiration(player);
long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;

// PurchaseService.java:62-66 -- chaque serveur tranche seul
if (item.isExpired()) {
    auctionManager.getCache(player).remove(PlayerCacheKey.ITEMS_LISTED);
    auctionManager.openMainAuction(player);
    return CompletableFuture.completedFuture(PurchaseResult.failure("Item expired", PurchaseFailReason.ITEM_EXPIRED));
}

// ExpireService.java:51-53 -- et declenche seul la transition LISTED -> EXPIRED
if (storageType == StorageType.LISTED && this.plugin.getAuctionClusterBridge().isDistributed()) {
    expireListedItemClustered(item);
```

**Chronologie**

L'echeance est bien un epoch absolu partage en base, mais chaque noeud la compare a SON System.currentTimeMillis(), sans aucune synchronisation ni tolerance.

T0 -- Le serveur A a une horloge en avance de 90 s sur le serveur B (machines distinctes, NTP absent ou desynchronise -- courant sur des VPS de fournisseurs differents).

T1 -- L'item 42 arrive a echeance selon l'horloge de A. A lance expireListedItemClustered : il verrouille, revalide, passe la ligne a EXPIRED et diffuse removeItem(LISTED, EXPIRED). L'item entre dans les items expires du vendeur.

T2 -- Pendant ces 90 s, un joueur sur B (dont l'horloge dit que l'item est encore valide) confirme l'achat. La garde PurchaseService.java:62 ne se declenche pas cote B. La revalidation sous verrou (ligne 121) ne teste que dbItem == null et buyerUniqueId : une ligne EXPIRED sans acheteur la franchit sans probleme.

T3 -- L'acheteur paie et recoit l'item, alors que le vendeur peut deja le reclamer depuis son onglet "expires".

La fenetre est exactement egale a la derive entre les deux horloges ; elle est nulle avec un NTP correct, et de plusieurs minutes sans.

**Impact** — Fenetre d'incoherence proportionnelle a la derive d'horloge entre noeuds, pendant laquelle un item peut etre simultanement expire pour le vendeur et achetable pour un tiers. Attenuable par NTP, donc severite limitee, mais rien dans le code ne le garantit ni ne l'avertit.

**Correctif**

```java
Deriver le temps d'une source unique plutot que de l'horloge locale. La plus simple, sans dependance supplementaire : utiliser l'horloge de la base partagee comme reference et memoriser le decalage au demarrage puis periodiquement.

// au demarrage et toutes les 5 minutes
long dbNow = storageManager.selectDatabaseTime();     // SELECT UNIX_TIMESTAMP() * 1000
ZItem.CLOCK_OFFSET = dbNow - System.currentTimeMillis();

@Override
public boolean isExpired() {
    long now = System.currentTimeMillis() + CLOCK_OFFSET;
    return now >= this.expiredAt.getTime() && this.expiredAt.getTime() != 0;
}

Et journaliser un avertissement au demarrage si |CLOCK_OFFSET| depasse quelques secondes, pour que l'exploitant corrige son NTP.
```

---

<a id="c-115"></a>

### `C-115` — La section admin-generate de config.yml n'est lue par aucun code : les valeurs du générateur de données de test sont codées en dur

- [ ] **Corrigé**
- **Fichier** : `command/commands/admin/CommandAuctionAdminGenerate.java:140`
- **Catégorie** : Fiabilité — **Sévérité** : Basse — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
CommandAuctionAdminGenerate.java:139-142 (vérifié) — trois valeurs en dur :

            Material material = validMaterials.get(random.nextInt(validMaterials.size()));
            int itemAmount = material.getMaxStackSize() == 1 ? 1 : random.nextInt(material.getMaxStackSize()) + 1;
            BigDecimal price = BigDecimal.valueOf(random.nextInt(99990) + 10);
            long expiredAt = System.currentTimeMillis() + (24L * 60L * 60L * 1000L);

`grep -rn "admin-generate" src/` (vérifié) ne retourne QUE des fichiers de ressources — src/main/resources/config.yml:1115 et src/main/resources/es/config.yml:1111 — plus des clés de messages. AUCUN fichier .java ne lit cette section.

La section déclare pourtant (config.yml:1115-1174, vérifié) trois sous-sections documentées : expiration (mode FIXED/RANDOM, fixed 172800, min 3600, max 604800), price (mode, fixed 1000, min 10, max 1000000) et item-amount (mode, fixed, min, max avec -1 = taille de stack max).

À noter dans la même classe : confirmationMap et confirmationAmountMap (lignes 30-31) sont des HashMap<CommandSender, ...> purgées uniquement lors d'une confirmation réussie (lignes 77-78) ; une première frappe sans confirmation laisse indéfiniment une référence forte vers l'objet Player.
```

**Chronologie**

T0 — Un administrateur veut reproduire un bug d'expiration signalé par un joueur. Il règle `admin-generate.expiration.mode: FIXED` et `fixed: 172800` (2 jours) dans config.yml, puis recharge.

T1 — Il lance `/ah admin generate 500`.

T2 — Le code ignore totalement la configuration : les 500 items reçoivent une expiration figée à 24 h (ligne 142), un prix tiré entre 10 et 100 000 (au lieu des 10 à 1 000 000 annoncés) et une quantité toujours aléatoire — le mode FIXED est inatteignable.

T3 — L'administrateur observe un comportement d'expiration qui ne correspond pas à sa configuration et conclut à tort à un bug du moteur d'expiration. Il ouvre un ticket sur un défaut inexistant.

T4 — Chaque frappe de `/ah admin generate <n>` non suivie de confirmation laisse une entrée définitive dans confirmationMap, référençant le CommandSender (donc le Player) même après sa déconnexion.

**Impact** — Configuration mensongère : l'administrateur croit piloter le générateur de données de test alors qu'aucun de ses réglages n'est appliqué, ce qui fausse toutes les campagnes de test — y compris celles menées pour valider les correctifs anti-duplication. Fuite mineure de références Player en prime.

**Correctif**

Deux options, la première étant préférable puisque la documentation existe déjà :
1) Lire la section : ajouter un AdminGenerateConfiguration à MainConfiguration (mode/fixed/min/max pour expiration, prix et quantité) et l'utiliser aux lignes 140-142.
2) Sinon, supprimer la section `admin-generate` de config.yml ET des dossiers de traduction fr/, es/ et it/, conformément à la règle du projet sur la synchronisation des traductions (elle n'existe aujourd'hui que dans la racine et dans es/, ce qui est déjà une désynchronisation).
3) Indépendamment : remplacer confirmationMap/confirmationAmountMap par des Map<UUID, ...> horodatées, expirées à la lecture (par exemple 60 secondes), pour supprimer la rétention de Player.

---

<a id="c-116"></a>

### `C-116` — Le VersionChecker de l'addon Redis compare la version de zAuctionHouse à celle de la ressource Redis, et enregistre son listener sur le plugin principal

- [ ] **Corrigé**
- **Fichier** : `REDIS/utils/VersionChecker.java:42`
- **Catégorie** : Fiabilité — **Sévérité** : Basse — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
VersionChecker.java:31-56 (vérifié) — `plugin` est l'AuctionPlugin, c'est-à-dire le plugin PRINCIPAL, passé depuis ZAuctionHouseRedis.java:72 (`new VersionChecker(auctionPlugin, this.getLogger(), 210)`) :

    public VersionChecker(AuctionPlugin plugin, Logger logger, int pluginID) {
        ...
    }

    public void useLastVersion() {

        Bukkit.getPluginManager().registerEvents(this, this.plugin);          // ligne 42 : listener enregistré SOUS le plugin principal

        String pluginVersion = plugin.getDescription().getVersion();          // ligne 44 : version de zAuctionHouse, pas de l'addon
        ...
        this.getVersion(version -> {
            long ver = Long.parseLong(version.replace(".", ""));              // version de la ressource 210 = l'addon Redis
            long plVersion = Long.parseLong(pluginVersion.replace(".", ""));
            atomicBoolean.set(plVersion >= ver);

Et ligne 71, le message affiché utilise lui aussi la description du plugin principal :

                event.getPlayer().sendMessage("§8(§bzAuctionHouse Redis§8) §aLe serveur utilise §2" + this.plugin.getDescription().getFullName() + " §a!");

À noter également : `Long.parseLong(version.replace(".", ""))` casse la comparaison dès qu'un segment de version passe à deux chiffres (4.0.10 -> 4010 < 4.0.9 -> 409).
```

**Chronologie**

T0 — Le serveur tourne avec zAuctionHouse 4.0.1.3 et zAuctionHouseRedis 1.0.2.

T1 — Le VersionChecker interroge l'API pour la ressource 210 (l'addon) et reçoit « 1.0.3 » -> ver = 103.

T2 — Il compare à `plugin.getDescription().getVersion()`, soit « 4.0.1.3 » (zAuctionHouse) -> plVersion = 4013.

T3 — 4013 >= 103 : le checker conclut « No update available » et n'avertira JAMAIS d'une mise à jour de l'addon, quelle qu'elle soit. Symétriquement, un numéro de version du plugin principal plus court que celui de l'addon produirait des alertes permanentes et fausses.

T4 — Effet secondaire du registerEvents ligne 42 : le listener appartient au plugin PRINCIPAL. Il survit donc à onDisable de l'addon (HandlerList.unregisterAll est appliqué au plugin propriétaire), maintenant une référence vers des classes de l'ancien ClassLoader de l'addon après un /reload.

**Impact** — Le contrôle de version de l'addon Redis — la brique la plus sensible du dispositif anti-duplication — est inopérant : un correctif de sécurité publié pour l'addon ne sera jamais signalé aux administrateurs. Fuite de listener/ClassLoader en prime au /reload.

**Correctif**

```java
1) Comparer la bonne version et s'enregistrer sous le bon plugin. Passer l'instance de l'addon (`ZAuctionHouseRedis`) au VersionChecker et l'utiliser pour registerEvents et pour getDescription().getVersion(), en ne gardant l'AuctionPlugin que pour son scheduler :

    public VersionChecker(JavaPlugin owner, AuctionPlugin schedulerOwner, Logger logger, int pluginID) { ... }
    ...
    Bukkit.getPluginManager().registerEvents(this, owner);
    String pluginVersion = owner.getDescription().getVersion();

2) Remplacer la comparaison `Long.parseLong(v.replace(".", ""))` par une comparaison segment par segment :

    private static int compare(String a, String b) {
        String[] as = a.split("\\."), bs = b.split("\\.");
        for (int i = 0; i < Math.max(as.length, bs.length); i++) {
            int x = i < as.length ? Integer.parseInt(as[i]) : 0;
            int y = i < bs.length ? Integer.parseInt(bs[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

3) Ajouter un `unregister()` appelé depuis onDisable de l'addon, comme le fait déjà ZAuctionPlugin.java:188-190 pour son propre checker.
```

---

<a id="c-117"></a>

### `C-117` — Le champ auctionClusterBridge n'est pas volatile alors qu'il est remplace a chaud et lu depuis tous les pools asynchrones

- [ ] **Corrigé**
- **Fichier** : `ZAuctionPlugin.java:100`
- **Catégorie** : Race condition — **Sévérité** : Basse — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
// ZAuctionPlugin.java:100 -- champ mutable non volatile
private AuctionClusterBridge auctionClusterBridge = new LocalAuctionClusterBridge();

// ZAuctionPlugin.java:416-422
return this.auctionClusterBridge;
...
public void setAuctionClusterBridge(AuctionClusterBridge auctionClusterBridge) {
    this.auctionClusterBridge = auctionClusterBridge;
}

// ZAuctionHouseRedis.java:99 -- ecriture depuis le onEnable de l'addon
this.auctionPlugin.setAuctionClusterBridge(new RedisAuctionClusterBridge(this, this.jedisPool, this.lockTtl, itemStateTtl, itemListedTtl));

// lectures depuis des threads de pool, par exemple PurchaseService.java:41 puis 82
var clusterBridge = this.plugin.getAuctionClusterBridge();
...
return clusterBridge.checkAvailability(item)
```

**Chronologie**

Le champ est ecrit une fois par le thread principal (onEnable de l'addon Redis) et lu ensuite depuis le commonPool, l'executor de la base et les threads Folia. Sans volatile ni final, le modele memoire Java n'impose aucune relation happens-before entre cette ecriture et ces lectures : un thread peut continuer a observer le LocalAuctionClusterBridge initial.

T0 -- Le serveur demarre ; auctionClusterBridge vaut LocalAuctionClusterBridge (mono-serveur, aucune synchronisation).

T1 -- L'addon Redis s'active et remplace le bridge.

T2 -- Un thread de pool qui avait deja lu le champ (ou dont le cache processeur n'a pas ete invalide) continue d'utiliser le bridge local : isDistributed() renvoie false, donc ExpireService.java:51 prend le chemin mono-serveur au lieu du chemin verrouille et revalide, et lockItem ne pose aucun verrou visible par les autres noeuds.

T3 -- Ce noeud agit alors sans aucune coordination pendant que les autres se croient en cluster -- le scenario deja documente du repli silencieux, mais atteint ici par un simple defaut de visibilite memoire.

En pratique la fenetre est etroite (l'activation precede les actions joueurs et de nombreuses barrieres memoire s'intercalent), mais rien dans le code ne l'exclut, et un rechargement a chaud de l'addon la rouvre entierement.

**Impact** — Risque de visibilite memoire sur le composant qui porte toute la coordination inter-serveurs. Faible probabilite, consequence maximale (un noeud travaillant hors cluster).

**Correctif**

Marquer le champ volatile, ce qui suffit a etablir la relation happens-before pour une reference publiee une fois :

private volatile AuctionClusterBridge auctionClusterBridge = new LocalAuctionClusterBridge();

Et, tant qu'a faire, journaliser tout changement de bridge dans setAuctionClusterBridge (ancienne classe -> nouvelle classe) afin que le passage effectif en mode distribue soit verifiable dans les logs de chaque noeud.

---

<a id="c-118"></a>

### `C-118` — ZAuctionPlugin.onDisable sort avant d'avoir fermé la base quand onEnable a échoué : la connexion (pool HikariCP ou fichier SQLite) est fuitée

- [ ] **Corrigé**
- **Fichier** : `ZAuctionPlugin.java:185`
- **Catégorie** : Cycle de vie — **Sévérité** : Basse — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ZAuctionPlugin.java:183-185 (vérifié) — garde d'entrée de onDisable :

    public void onDisable() {

        if (!this.isEnabled) return;

ZAuctionPlugin.java:178 (vérifié) : `isEnabled = true;` est la DERNIÈRE instruction de onEnable.
ZAuctionPlugin.java:137 (vérifié) : `if (!this.storageManager.onEnable()) return;` — sortie anticipée bien avant la ligne 178.
Or la connexion a déjà été créée à ce stade — ZStorageManager.java:50 (vérifié) :

        this.databaseConnection = isSqlite ? new SqliteConnection(...) : new HikariDatabaseConnection(databaseConfiguration, sarahLogger);

et sa fermeture n'a lieu que dans ZStorageManager.onDisable (lignes 87-89, vérifié), appelé uniquement depuis ZAuctionPlugin.java:209, donc APRÈS la garde ligne 185 :

    public void onDisable() {
        this.databaseConnection.disconnect();
    }
```

**Chronologie**

T0 — MySQL est injoignable au démarrage (ou surchargé, ou les identifiants sont erronés).

T1 — ZStorageManager.onEnable construit le HikariDatabaseConnection — ce qui crée déjà le pool et ses threads de maintenance — puis constate `!databaseConnection.isValid()`, logge l'erreur et appelle disablePlugin(plugin).

T2 — ZAuctionPlugin.onEnable sort ligne 137. `isEnabled` vaut toujours false.

T3 — Bukkit appelle onDisable. La garde ligne 185 renvoie immédiatement : storageManager.onDisable() n'est JAMAIS exécuté.

T4 — Le pool HikariCP et ses threads restent en vie pour toute la durée du serveur. Après un /reload avec MySQL toujours en panne, un pool supplémentaire s'ajoute à chaque tentative.

Le même trou concerne toute exception survenant entre la ligne 137 et la ligne 178 : la connexion, le SortedItemsCache (ForkJoinPool dédié) et l'asyncExecutor restent ouverts.

**Impact** — Fuite de pool de connexions et de threads dès qu'un démarrage échoue, aggravée à chaque /reload. Sans conséquence sur l'intégrité des données, mais elle transforme un incident base transitoire en dégradation persistante du serveur.

**Correctif**

```java
Séparer « la fermeture des ressources » de « le plugin a démarré complètement » :

    @Override
    public void onDisable() {

        if (this.isEnabled) {
            if (this.versionChecker != null) this.versionChecker.unregister();
            this.auctionManager.shutdown();
            // ... arrêt de l'asyncExecutor ...
        }

        // À exécuter systématiquement, y compris après un onEnable interrompu
        if (this.storageManager != null && this.storageManager.getDatabaseConnection() != null) {
            this.storageManager.onDisable();
        }
        context.shutdown();
    }

Ou, plus simple, fermer la connexion directement dans le chemin d'échec de ZStorageManager.onEnable, juste avant `return false` :

        if (!databaseConnection.isValid()) {
            this.plugin.getLogger().severe("Unable to connect to database !");
            this.databaseConnection.disconnect();
            Bukkit.getPluginManager().disablePlugin(plugin);
            return false;
        }
```

---

<a id="c-119"></a>

### `C-119` — clearPendingTransactions (API publique AuctionClaimService) reproduit le meme defaut : depot avant marquage, UPDATE non conditionnel, et RETRIEVED meme sans depot

- [ ] **Corrigé**
- **Fichier** : `services/ClaimService.java:210`
- **Catégorie** : Duplication d’argent — **Sévérité** : Basse — **Contre-expertise** : non vérifié

**Preuve dans le code**

```java
ClaimService.java:175-212 (lu en entier) -- meme ordre depot-puis-marquage que claimMoney, avec en plus AUCUN `continue` apres l'echec du depot :
    public CompletableFuture<Void> clearPendingTransactions(UUID playerUniqueId, boolean giveMoney) {
        return getPendingTransactions(playerUniqueId).thenAccept(transactions -> {
            if (transactions.isEmpty()) return;
            if (giveMoney) {
                ...
                for (var entry : byEconomy.entrySet()) {
                    var optionalEconomy = economyManager.getEconomy(entry.getKey());
                    if (optionalEconomy.isEmpty()) {
                        this.plugin.getLogger().warning("Economy not found: " + entry.getKey());
                        continue;
                    }
                    ...
                    if (economyTotal.compareTo(BigDecimal.ZERO) > 0) {
                        try {
                            economy.deposit(playerUniqueId, economyTotal, depositReason);
                        } catch (Exception e) {
                            this.plugin.getLogger().severe("Failed to deposit " + economyTotal + " to " + playerUniqueId + " for economy " + entry.getKey() + ": " + e.getMessage());
                        }
                    }
                }
            }

            var transactionIds = transactions.stream().map(TransactionDTO::id).toList();
            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            repository.updateStatus(transactionIds, TransactionStatus.RETRIEVED);
        });
    }

TransactionRepository.updateStatus (l.46-57) : toujours aucun `where("status", PENDING)`.
API/.../api/services/AuctionClaimService.java:68 : la methode est exposee publiquement --
    CompletableFuture<Void> clearPendingTransactions(UUID playerUniqueId, boolean giveMoney);
Grep sur tout le depot : aucun appelant interne aujourd'hui, la methode n'existe que pour les addons.
```

**Chronologie**

Le defaut est latent dans le plugin principal (aucun appelant interne) mais immediatement exploitable des qu'un addon ou une commande admin l'utilise -- exactement le role d'une methode d'API publique.

T0 - Un addon (ou une future commande `/ah admin clearpending <joueur>`) appelle clearPendingTransactions(J, true) sur le serveur A. Le joueur J a 3 lignes PENDING pour 50 000.

T1 - Serveur A : getPendingTransactions lit les 3 lignes. Rien n'est reserve.

T2 - Serveur B : J se connecte, l'auto-claim declenche claimMoney -> MEME SELECT -> les MEMES 3 lignes, toujours PENDING.

T3 - Serveur A : l.200 `deposit(J, 50000)`. Serveur B : ClaimService.java:71 `deposit(J, 50000)`. J encaisse 100 000.

T4 - Les deux executent `UPDATE transactions SET status='RETRIEVED' WHERE id=?` sans clause de statut : les deux reussissent, aucun ne detecte le doublon.

Second defaut au meme endroit, symetrique : l.201-203, quand `economy.deposit` leve, il n'y a meme pas de `continue` -- l'exception est simplement journalisee et le flux tombe l.210 sur `updateStatus(transactionIds, RETRIEVED)`, qui marque TOUTES les lignes encaissees. Idem quand `giveMoney` vaut false ou quand l'economie est introuvable (l.187-190) : l'argent est efface sans jamais avoir ete verse.

**Impact** — Duplication d'argent inter-serveurs et destruction d'argent, dans une methode de l'API publique que les addons sont invites a utiliser. Aujourd'hui non atteignable depuis le plugin lui-meme, d'ou la severite basse -- mais le developpeur doit corriger ce second emplacement en meme temps que claimMoney, sinon la correction du claim laissera une porte ouverte.

**Correctif**

Appliquer ici exactement la meme reservation par compare-and-set que dans claimMoney :
1) `int won = repository.reservePending(playerUniqueId, token);` en TETE de methode ; si `won <= 0`, sortir sans rien faire.
2) `var mine = repository.selectByClaimToken(token);` et ne deposer que `mine`.
3) N'appeler `finishClaim(token, RETRIEVED)` que pour les economies dont le depot a effectivement abouti ; repasser les autres en PENDING (`status='PENDING', claim_token=NULL`).
4) Ajouter le `continue` manquant apres l'echec du deposit (l.202) pour ne jamais marquer une economie non versee.
5) Quand `giveMoney == false`, ne pas passer les lignes en RETRIEVED (ce qui pretend qu'elles ont ete encaissees) mais dans un statut dedie, par exemple `CANCELLED`, afin que l'historique reste auditable.

---

# Partie 3 — Index par fichier

Pour corriger fichier par fichier. Le nombre entre crochets est le nombre de constats critiques ou hauts.

### `ZAuctionManager.java` — 16 constats [11 critique/haut]

- [`C-005`](#c-005) **Critique** — L'argent et l'item sont livrés avant que l'écriture DB soit durable : un échec ou un crash duplique l'item et paie deux fois *(ligne 883)*
- [`C-012`](#c-012) **Critique** — adminRemoveItem diffuse une suppression non terminale (REMOVED, destination null) AVANT d'ecrire en base : les autres serveurs remettent l'item en vente *(ligne 689)*
- [`C-014`](#c-014) **Critique** — giveItem() ecrit dans l'inventaire du joueur et fait spawner des entites depuis ForkJoinPool.commonPool *(ligne 931)*
- [`C-019`](#c-019) **Haute** — Creation d'argent deterministe : une regle de taxe par item de type CAPITALISM sur une economie PURCHASE/BOTH fait toucher au vendeur price + taxe pendant que l'acheteur ne paie que price *(ligne 769)*
- [`C-032`](#c-032) **Haute** — Les economies liees au joueur (ITEM, ZMENUITEMS, LEVEL, EXPERIENCE) detruisent silencieusement le paiement du vendeur hors ligne, et la transaction est enregistree RETRIEVED *(ligne 812)*
- [`C-042`](#c-042) **Haute** — adminRemoveItem ne teste jamais le jeton noop : la suppression admin s'execute meme quand un autre serveur detient le verrou de l'item *(ligne 689)*
- [`C-045`](#c-045) **Haute** — updateListedItems : un return au lieu d'un continue interrompt le rafraichissement pour tous les joueurs suivants *(ligne 956)*
- [`C-053`](#c-053) **Haute** — Retrait en masse : un échec survenu APRÈS la remise de l'item renvoie INTERNAL_ERROR, ce qui abandonne silencieusement le reste du lot et sous-compte les items rendus *(ligne 1128)*
- [`C-056`](#c-056) **Haute** — Tempete de rebuild du SortedItemsCache : chaque item liste sur le cluster declenche un retri complet, et updateListedItems attend ce rebuild avant d'afficher *(ligne 952)*
- [`C-059`](#c-059) **Haute** — Vente cross-serveur : le vendeur connecté ailleurs reçoit « votre item a été vendu pour X » mais n'est jamais crédité, et le log d'achat est marqué lu au passage *(ligne 810)*
- [`C-060`](#c-060) **Haute** — adminRemoveItem ne relit jamais l'etat autoritaire en base sous verrou, faute de primitive verrouiller+lire dans le contrat *(ligne 687)*
- [`C-084`](#c-084) **Moyenne** — withdraw et deposit sont appeles depuis commonPool et l'asyncExecutor, sans aucune serialisation par compte *(ligne 802)*
- [`C-088`](#c-088) **Moyenne** — Aucune tâche planifiée d'expiration : l'expiration n'est déclenchée que par getItemIds, que la liste principale de l'hôtel des ventes n'emprunte jamais *(ligne 407)*
- [`C-100`](#c-100) **Moyenne** — Les logs d'annonces multi-stacks sont écrits en concaténant les payloads Base64 avec ';' mais relus par un unique Base64ItemStack.decode qui ne redécoupe rien *(ligne 1003)*
- [`C-107`](#c-107) **Moyenne** — getCache() ressuscite indéfiniment le cache d'un joueur déconnecté : la map est indexée par l'objet Player, et onInventoryClose est appelée après PlayerQuitEvent *(ligne 488)*
- [`C-112`](#c-112) **Basse** — adminRemoveItem ne relache pas le verrou en cas d'exception : avec LocalAuctionClusterBridge (sans TTL) l'item reste verrouille jusqu'au redemarrage *(ligne 705)*

### `REDIS/ZAuctionHouseRedis.java` — 6 constats [5 critique/haut]

- [`C-016`](#c-016) **Critique** — L'echec de loadServerUUID() n'interrompt pas onEnable : le noeud installe le bridge Redis avec INSTANCE_UUID null et devient sourd et muet sur le bus *(ligne 59)*
- [`C-018`](#c-018) **Haute** — Bridge zombie apres onDisable de l'addon : le plugin principal conserve un RedisAuctionClusterBridge dont le pool Jedis est ferme, sans aucun chemin de recuperation *(ligne 137)*
- [`C-024`](#c-024) **Haute** — L'unicite de l'INSTANCE_UUID est best-effort : exception avalee au demarrage, heartbeat en simple EXPIRE, et tache portee par le scheduler du plugin principal *(ligne 199)*
- [`C-031`](#c-031) **Haute** — Le thread abonne demarre et le bridge Redis est installe AVANT l'enregistrement des listeners : tous les messages de cette fenetre sont jetes sans aucun log *(ligne 83)*
- [`C-036`](#c-036) **Haute** — Repli silencieux en mono-serveur : si l'addon Redis n'atteint jamais la ligne 99, le noeud garde LocalAuctionClusterBridge sans le moindre avertissement *(ligne 77)*
- [`C-113`](#c-113) **Basse** — L'addon Redis s'installe sur un plugin principal qui peut avoir échoué : seul un test de nullité est fait, jamais isEnabled() *(ligne 62)*

### `services/PurchaseService.java` — 7 constats [4 critique/haut]

- [`C-006`](#c-006) **Critique** — La revalidation d'achat sous verrou ne teste pas storage_type : un item déjà passé en EXPIRED reste achetable *(ligne 121)*
- [`C-007`](#c-007) **Critique** — Le bail Redis de 30 s n'est jamais renouvelé et la section critique n'a aucun timeout : le verrou expire en plein commit *(ligne 134)*
- [`C-017`](#c-017) **Haute** — Achat CAPITALISM : la verification de solde ignore les regles de taxe par item, l'acheteur est preleve plus que le montant verifie *(ligne 49)*
- [`C-035`](#c-035) **Haute** — PurchaseService rediffuse IS_PURCHASE_CONFIRM au cluster apres avoir constate en base que l'item est deja vendu *(ligne 178)*
- [`C-076`](#c-076) **Moyenne** — PurchaseService.exceptionally reinitialise et rediffuse le statut d'un item verrouille par une AUTRE chaine d'achat *(ligne 176)*
- [`C-079`](#c-079) **Moyenne** — Un timeout sur notifyItemBought fait echouer un achat pourtant integralement conclu et peut remettre l'etat Redis a AVAILABLE *(ligne 135)*
- [`C-111`](#c-111) **Basse** — Le orTimeout sur lockItem fuit le verrou Redis : le jeton n'est capture que dans l'etape suivante *(ligne 92)*

### `storage/ZStorageManager.java` — 5 constats [4 critique/haut]

- [`C-010`](#c-010) **Critique** — SQLITE reste accepté avec un bridge cluster distribué : chaque serveur a sa propre base, les ids se recoupent et toute la protection anti-duplication opère sur des objets différents *(ligne 49)*
- [`C-020`](#c-020) **Haute** — Creation d'une vente : 1 + N INSERT hors transaction, la ligne items existe avant son contenu *(ligne 140)*
- [`C-027`](#c-027) **Haute** — La dette envers le vendeur (ligne transactions PENDING) est ecrite en fire-and-forget sur un scheduler non draine a l'arret : l'acheteur est preleve, le vendeur n'est jamais paye *(ligne 171)*
- [`C-043`](#c-043) **Haute** — createAuctionItem enchaine deux INSERT hors transaction : un echec partiel laisse une annonce vide et achetable en base partagee *(ligne 141)*
- [`C-109`](#c-109) **Moyenne** — selectItem() confond trois causes de null : une economie absente de economies.yml est interpretee comme une vente et purge l'item de tous les hotels des ventes du reseau *(ligne 186)*

### `services/SellService.java` — 7 constats [3 critique/haut]

- [`C-030`](#c-030) **Haute** — Le remboursement d'une vente echouee reecrit l'inventaire depuis l'asyncExecutor *(ligne 120)*
- [`C-037`](#c-037) **Haute** — SellService : le bloc .exceptionally rembourse les items alors que postSell a deja commite et diffuse la mise en vente *(ligne 111)*
- [`C-051`](#c-051) **Haute** — La limite de mise en vente compte des ANNONCES alors qu'une annonce peut contenir 36 ItemStacks : la limite de grade est contournable d'un facteur 36 sans aucun timing *(ligne 256)*
- [`C-080`](#c-080) **Moyenne** — Vente : les items quittent l'inventaire du joueur avant l'INSERT et rien ne force la sauvegarde du profil -- un crash entre les deux duplique le lot *(ligne 105)*
- [`C-082`](#c-082) **Moyenne** — postSell s'execute sur l'executor asynchrone et mute l'index IntArrayList non thread-safe pendant que le thread principal le modifie *(ligne 370)*
- [`C-096`](#c-096) **Moyenne** — La limite d'annonces est evaluee sur le cache memoire local avant la chaine asynchrone : contournable par spam et entre serveurs *(ligne 256)*
- [`C-102`](#c-102) **Moyenne** — Taxe de vente : has() puis withdraw() ne sont pas atomiques et le retour du retrait est ignore -- l'annonce est publiee sans que la taxe soit percue *(ligne 336)*

### `migration/v3/V3MigrationService.java` — 5 constats [2 critique/haut]

- [`C-033`](#c-033) **Haute** — Migration V3 : l'insert de log avec item_id = 0 viole la cle etrangere sous MySQL et leve une DatabaseException que catch (SQLException) ne voit pas -- tout l'argent PENDING V3 est perdu *(ligne 345)*
- [`C-034`](#c-034) **Haute** — Migration V3 sans aucune idempotence : rejouer la commande duplique tous les items ET tout l'argent PENDING *(ligne 107)*
- [`C-075`](#c-075) **Moyenne** — Migration V3 : un echec d'insert de stack laisse un item vendable au prix plein avec un lot ampute ou vide, et la ligne items parente n'est jamais supprimee *(ligne 282)*
- [`C-087`](#c-087) **Moyenne** — Aucune garde d'exclusion sur admin migrate : plusieurs migrations paralleles saturent l'asyncExecutor a 4 threads partage avec le gameplay *(ligne 108)*
- [`C-108`](#c-108) **Moyenne** — migratePlayers ecrase le pseudo des joueurs V4 existants par le placeholder "Unknown" *(ligne 183)*

### `REDIS/RedisAuctionClusterBridge.java` — 4 constats [2 critique/haut]

- [`C-002`](#c-002) **Critique** — Côté Redis, SOLD et REMOVED sont considérés disponibles et DELETED s'auto-détruit au bout de 24 h : le verrou ne protège que la simultanéité, jamais la séquence *(ligne 163)*
- [`C-058`](#c-058) **Haute** — Un seul échec de loadScripts() condamne le nœud au chemin de verrouillage non atomique à vie : reloadScriptsIfNeeded() n'a aucun appelant *(ligne 132)*
- [`C-077`](#c-077) **Moyenne** — Tout l'IO bloquant (Jedis et JDBC) est poste sur ForkJoinPool.commonPool, dimensionne coeurs-1 *(ligne 159)*
- [`C-106`](#c-106) **Moyenne** — checkAvailability est un pre-controle TOCTOU strictement redondant avec LOCK_SCRIPT : 6 emprunts de connexion Redis par achat au lieu de 4 *(ligne 157)*

### `REDIS/listener/listeners/ItemStatusListener.java` — 3 constats [2 critique/haut]

- [`C-004`](#c-004) **Critique** — ItemStatusListener applique le statut recu sans comparer oldStatus : un item deja PURCHASED redevient IS_PURCHASE_CONFIRM et n'est plus jamais reclamable *(ligne 54)*
- [`C-050`](#c-050) **Haute** — ItemStatusListener recopie jusqu'a trois stores memoire entiers et les scanne lineairement, sur le thread principal, a chaque message de statut *(ligne 45)*
- [`C-072`](#c-072) **Moyenne** — Les statuts transitoires recus par Redis n'expirent jamais : un item peut rester invisible et non reclamable sur tout le reseau indefiniment *(ligne 56)*

### `buttons/list/ListedItemsButton.java` — 3 constats [2 critique/haut]

- [`C-048`](#c-048) **Haute** — Confirmation d'achat : `thenRun` sur notifyItemStatusChange ouvre l'inventaire Bukkit depuis ForkJoinPool.commonPool, sans aucun `exceptionally` *(ligne 229)*
- [`C-049`](#c-049) **Haute** — Confirmation de retrait : deuxième occurrence du même `thenRun` non protégé, qui ouvre REMOVE_CONFIRM depuis commonPool (site distinct de processPurchase) *(ligne 130)*
- [`C-074`](#c-074) **Moyenne** — ListedItemsButton.updateInventory : clone d'IntList complete + scan lineaire, par joueur regardant l'HDV, a chaque message cluster *(ligne 255)*

### `services/ClaimService.java` — 3 constats [2 critique/haut]

- [`C-003`](#c-003) **Critique** — Double claim inter-serveurs : ClaimService.claimMoney depose l'argent AVANT un UPDATE non conditionnel dont le resultat est jete *(ligne 71)*
- [`C-013`](#c-013) **Critique** — claimMoney marque toutes les transactions RETRIEVED meme quand aucun depot n'a eu lieu *(ligne 78)*
- [`C-119`](#c-119) **Basse** — clearPendingTransactions (API publique AuctionClaimService) reproduit le meme defaut : depot avant marquage, UPDATE non conditionnel, et RETRIEVED meme sans depot *(ligne 210)*

### `services/RemoveService.java` — 3 constats [2 critique/haut]

- [`C-009`](#c-009) **Critique** — RemoveService ne revalide JAMAIS la base sous verrou, contrairement à PurchaseService : tout fantôme mémoire devient un item gratuit *(ligne 200)*
- [`C-039`](#c-039) **Haute** — Une exception dans la remise locale ressuscite l'item sur tout le cluster alors que sa ligne est deja DELETED *(ligne 250)*
- [`C-083`](#c-083) **Moyenne** — removeListedItem : la destination est calculee avant le verrou mais canReceiveItem est reevalue apres, ce qui diffuse un DELETED pour un item parti en EXPIRED *(ligne 63)*

### `economy/ZAuctionEconomy.java` — 2 constats [2 critique/haut]

- [`C-015`](#c-015) **Critique** — withdraw() ne rend aucun resultat et n'est jamais verifie : entre le has() et le debit, l'acheteur peut obtenir l'item sans payer et le vendeur est credite a partir de rien *(ligne 92)*
- [`C-041`](#c-041) **Haute** — ZAuctionEconomy.get() est un faux async : une exception du provider s'echappe de purchaseAuctionItem apres le debit acheteur et le credit vendeur, l'item reste LISTED *(ligne 77)*

### `items/ZAuctionItem.java` — 2 constats [2 critique/haut]

- [`C-011`](#c-011) **Critique** — Un item dont la liste d'ItemStacks est vide reste LISTED, s'affiche comme un lot normal et reste achetable : l'acheteur est debite du prix plein et ne recoit RIEN *(ligne 68)*
- [`C-038`](#c-038) **Haute** — Un ItemStack null rend l'item inachetable a vie et destructeur au retrait : la ligne passe en DELETED avant giveItem, dont la boucle s'interrompt au milieu *(ligne 155)*

### `ZAuctionPlugin.java` — 3 constats [1 critique/haut]

- [`C-022`](#c-022) **Haute** — L'achat en vol survit a onDisable : asyncExecutor est ferme mais la chaine tourne sur commonPool *(ligne 196)*
- [`C-117`](#c-117) **Basse** — Le champ auctionClusterBridge n'est pas volatile alors qu'il est remplace a chaud et lu depuis tous les pools asynchrones *(ligne 100)*
- [`C-118`](#c-118) **Basse** — ZAuctionPlugin.onDisable sort avant d'avoir fermé la base quand onEnable a échoué : la connexion (pool HikariCP ou fichier SQLite) est fuitée *(ligne 185)*

### `buttons/confirm/ConfirmHelper.java` — 3 constats [1 critique/haut]

- [`C-023`](#c-023) **Haute** — L'etat de confirmation n'a ni proprietaire ni TTL et sa seule liberation depend d'un onInventoryClose que zMenu differe d'un tick ou saute completement *(ligne 38)*
- [`C-068`](#c-068) **Moyenne** — ConfirmHelper.onBackClick force le statut AVAILABLE sans aucune garde, meme en pleine section critique d'achat *(ligne 72)*
- [`C-069`](#c-069) **Moyenne** — ConfirmHelper.onBackClick ne verifie pas le statut courant (contrairement a onInventoryClose) et rediffuse AVAILABLE par-dessus un achat deja verrouille *(ligne 72)*

### `REDIS/listener/RedisSubscriberRunnable.java` — 2 constats [1 critique/haut]

- [`C-047`](#c-047) **Haute** — Aucune resynchronisation apres une coupure du bus Redis : le contrat n'offre aucun point de reprise et le subscriber se reconnecte en aveugle *(ligne 66)*
- [`C-086`](#c-086) **Moyenne** — Arrêt du thread abonné Redis non fiable : champ jedisPubSub non volatile, unsubscribe qui peut échouer avant l'attachement, et aucun interrupt *(ligne 32)*

### `storage/repository/repositories/AuctionItemRepository.java` — 2 constats [1 critique/haut]

- [`C-001`](#c-001) **Critique** — Au-dela d'environ 65 000 items, le IN(...) de AuctionItemRepository.select echoue, l'echec est avale par Repository.select et TOUS les items du reseau sont charges sans aucun contenu *(ligne 41)*
- [`C-067`](#c-067) **Moyenne** — Base64ItemStack.encode peut rendre null (contrat javadoc) ou lever une NPE, et cette valeur part directement dans une colonne NOT NULL apres que la ligne items a deja ete commitee *(ligne 34)*

### `storage/repository/repositories/ItemRepository.java` — 2 constats [1 critique/haut]

- [`C-026`](#c-026) **Haute** — La base ne peut arbitrer aucune course : UPDATE items sans compare-and-set et nombre de lignes affectées systématiquement jeté *(ligne 71)*
- [`C-094`](#c-094) **Moyenne** — ItemRepository.updateItems fait N UPDATE unitaires alors que Sarah fournit un batch transactionnel deja utilise ailleurs dans le projet *(ligne 58)*

### `zAuctionHouse Redis/redis/RedisAuctionClusterBridge.java` — 2 constats [1 critique/haut]

- [`C-025`](#c-025) **Haute** — LOCK_SCRIPT reprend un verrou perime tant que l'etat vaut LOCKED, sans jeton de fencing : un detenteur lent conserve son droit d'ecriture *(ligne 51)*
- [`C-099`](#c-099) **Moyenne** — Le repli non atomique de lockItem/unlockItem peut ecraser l'etat terminal DELETED et remettre un item supprime en circulation *(ligne 220)*

### `API/api/cluster/AuctionClusterBridge.java` — 1 constat [1 critique/haut]

- [`C-061`](#c-061) **Haute** — unlockItem ne rend aucun resultat : le code retour du script Lua est jete et RemoveService declare un retrait reussi meme quand le verrou ne lui appartenait plus *(ligne 45)*

### `API/api/cluster/LockToken.java` — 1 constat [1 critique/haut]

- [`C-008`](#c-008) **Critique** — Le jeton de verrou est déterministe ("item:<id>") : la vérification de propriété est inopérante et un serveur libère le verrou d'un autre *(ligne 34)*

### `API/api/utils/Base64ItemStack.java` — 1 constat [1 critique/haut]

- [`C-029`](#c-029) **Haute** — Le format de serialisation des ItemStack depend de la version Minecraft du serveur LOCAL : dans un cluster 1.20.x + 1.21.x, la meme colonne contient deux encodages mutuellement illisibles *(ligne 30)*

### `API/api/utils/ItemStackUtils.java` — 1 constat [1 critique/haut]

- [`C-054`](#c-054) **Haute** — Récursion mutuelle infinie entre ItemStackUtils.safeDeserializeItemStack et Base64ItemStack.decode sur tout serveur antérieur à 1.20.5 : StackOverflowError qu'aucun catch du projet ne rattrape *(ligne 41)*

### `REDIS/connection/RedisConnectionFactory.java` — 1 constat [1 critique/haut]

- [`C-052`](#c-052) **Haute** — Pool Jedis : attente INFINIE à l'épuisement (maxWait jamais configuré), aggravée par un double emprunt imbriqué de connexion dans la branche NOSCRIPT *(ligne 66)*

### `REDIS/listener/listeners/ItemBoughtListener.java` — 1 constat [1 critique/haut]

- [`C-046`](#c-046) **Haute** — ItemBoughtListener/ItemRemovedListener sortent l'item du store sans jamais changer son statut : les boutons deja rendus gardent une reference AVAILABLE et passent toutes les gardes de RemoveService *(ligne 49)*

### `REDIS/utils/Utils.java` — 1 constat [1 critique/haut]

- [`C-057`](#c-057) **Haute** — Un seul champ non convertible detruit tout le message de synchronisation : constante d'enum inconnue ou parametre primitif manquant -> message integralement abandonne *(ligne 95)*

### `migration/v3/V3MigrationProvider.java` — 1 constat [1 critique/haut]

- [`C-040`](#c-040) **Haute** — V3MigrationProvider transforme tout échec de migration en succès : `thenApply` mappe systématiquement vers MigrationResult.success et jette isSuccess()/getErrorMessage() *(ligne 126)*

### `services/SearchService.java` — 1 constat [1 critique/haut]

- [`C-055`](#c-055) **Haute** — SearchService reconstruit une HashMap de tout le store LISTED a chaque recalcul de recherche, sur le thread principal *(ligne 48)*

### `storage/AuctionLoader.java` — 1 constat [1 critique/haut]

- [`C-044`](#c-044) **Haute** — loadItems() est purement additif : appele a chaud par /ah admin migrate, il remet tous les statuts a AVAILABLE et ne purge aucun fantome *(ligne 34)*

### `zAuctionHouse Redis/redis/ZAuctionHouseRedis.java` — 1 constat [1 critique/haut]

- [`C-028`](#c-028) **Haute** — Le bus pub/sub est en "au plus une fois" : l'echec de publication est avale et aucun rattrapage n'existe apres une coupure Redis *(ligne 274)*

### `zAuctionHouse Redis/redis/listener/listeners/ItemListedListener.java` — 1 constat [1 critique/haut]

- [`C-021`](#c-021) **Haute** — ItemListedListener re-ajoute l'item en LISTED apres un aller-retour DB non ordonne : un item vendu entre-temps ressuscite en memoire *(ligne 31)*

### `command/commands/admin/CommandAuctionAdminAdd.java` — 3 constats [0 critique/haut]

- [`C-062`](#c-062) **Moyenne** — /ah admin add <joueur> expired|purchased cree un item deja expire, immediatement detruit et jamais reclamable *(ligne 112)*
- [`C-063`](#c-063) **Moyenne** — /ah admin add detruit l'item de la main de l'admin avant l'ecriture DB et ne le rend jamais en cas d'echec (aucun exceptionally sur les trois branches) *(ligne 77)*
- [`C-064`](#c-064) **Moyenne** — /ah admin add, /ah admin generate et la migration V3 n'annoncent jamais leurs items au cluster : items invisibles ailleurs et cles Redis sans TTL *(ligne 102)*

### `command/commands/admin/CommandAuctionAdminGenerate.java` — 3 constats [0 critique/haut]

- [`C-081`](#c-081) **Moyenne** — admin generate execute jusqu'a des dizaines de milliers de SELECT bloquants sur le thread principal *(ligne 118)*
- [`C-085`](#c-085) **Moyenne** — /ah admin generate attribue les items generes a de vrais joueurs quand le pseudo aleatoire existe deja en base *(ligne 132)*
- [`C-115`](#c-115) **Basse** — La section admin-generate de config.yml n'est lue par aucun code : les valeurs du générateur de données de test sont codées en dur *(ligne 140)*

### `services/ExpireService.java` — 3 constats [0 critique/haut]

- [`C-070`](#c-070) **Moyenne** — Expiration en cluster : boucle non bornee item par item (5 emprunts Redis + 4 requetes SQL chacun) declenchee depuis le rendu d'un inventaire *(ligne 130)*
- [`C-078`](#c-078) **Moyenne** — Transition EXPIRED/PURCHASED -> DELETED : destruction de l'item sans verrou, sans revalidation et sans aucune diffusion cluster *(ligne 102)*
- [`C-091`](#c-091) **Moyenne** — Chemin cluster d'expiration : la revalidation sous verrou ne verifie pas que la ligne est encore LISTED, et 0 ligne modifiee est interprete comme un succes *(ligne 283)*

### `REDIS/listener/listeners/ItemListedListener.java` — 2 constats [0 critique/haut]

- [`C-089`](#c-089) **Moyenne** — Chaque message pub/sub declenche un selectItem() en base sur CHAQUE serveur : amplification x(N-1) et 3 a 4 requetes SQL non indexees par message *(ligne 31)*
- [`C-093`](#c-093) **Moyenne** — ItemListedListener re-injecte l'item en LISTED sans verifier son etat reel et ne purge jamais ITEMS_SELLING *(ligne 44)*

### `utils/cache/SortedItemsCache.java` — 2 constats [0 critique/haut]

- [`C-101`](#c-101) **Moyenne** — SortedItemsCache : une invalidation survenant pendant un rebuild est ecrasee, l'item reste invisible sans que le cache soit marque sale *(ligne 389)*
- [`C-103`](#c-103) **Moyenne** — Un changement de statut n'invalide jamais SortedItemsCache alors que le rebuild filtre precisement sur AVAILABLE *(ligne 305)*

### `REDIS/build.gradle.kts` — 1 constat [0 critique/haut]

- [`C-095`](#c-095) **Moyenne** — L'addon est compile contre un SHA git fige de l'API et aucun controle de compatibilite n'est fait au demarrage *(ligne 48)*

### `REDIS/listener/listeners/ItemRemovedListener.java` — 1 constat [0 critique/haut]

- [`C-092`](#c-092) **Moyenne** — ItemBoughtListener et ItemRemovedListener n'appellent jamais updateListedItems : les slots restent affiches et cliquables dans les HDV deja ouverts, et ITEMS_SEARCH n'est pas purge par le retrait *(ligne 43)*

### `REDIS/utils/VersionChecker.java` — 1 constat [0 critique/haut]

- [`C-116`](#c-116) **Basse** — Le VersionChecker de l'addon Redis compare la version de zAuctionHouse à celle de la ressource Redis, et enregistre son listener sur le plugin principal *(ligne 42)*

### `buttons/sell/SellShowItemButton.java` — 1 constat [0 critique/haut]

- [`C-097`](#c-097) **Moyenne** — Le GUI de vente n'affiche que slots.size() stacks alors que la sélection n'est pas bornée : le joueur vend des stacks qu'il ne voit pas et ne peut pas retirer *(ligne 49)*

### `category/ZCategoryManager.java` — 1 constat [0 critique/haut]

- [`C-104`](#c-104) **Moyenne** — ZCategoryManager.computeCategoryCount copie integralement le store LISTED par categorie, et son cache est vide a chaque item liste sur le cluster *(ligne 253)*

### `cluster/LocalAuctionClusterBridge.java` — 1 constat [0 critique/haut]

- [`C-110`](#c-110) **Basse** — Contrat lockItem non specifie et divergent entre les deux implementations : LocalAuctionClusterBridge signale l'echec par un future en erreur, le bridge Redis par un jeton noop *(ligne 23)*

### `command/commands/admin/cache/CommandAuctionAdminCacheClear.java` — 1 constat [0 critique/haut]

- [`C-065`](#c-065) **Moyenne** — /ah admin cache clear efface ITEM_SHOW et gele definitivement l'item en IS_*_CONFIRM sur tout le cluster *(ligne 55)*

### `items/ZItem.java` — 1 constat [0 critique/haut]

- [`C-114`](#c-114) **Basse** — L'expiration ne repose que sur l'horloge locale de chaque serveur : aucune source de temps partagee *(ligne 168)*

### `placeholder/placeholders/GlobalPlaceholders.java` — 1 constat [0 critique/haut]

- [`C-098`](#c-098) **Moyenne** — Le placeholder %listed_items% copie integralement le store LISTED a chaque resolution, sans aucun cache *(ligne 18)*

### `storage/migrations/CreateItemMigration.java` — 1 constat [0 critique/haut]

- [`C-066`](#c-066) **Moyenne** — Aucun index en dehors des cles primaires et etrangeres, et aucune purge des lignes DELETED : le chargement au boot devient impossible *(ligne 11)*

### `storage/repository/repositories/LogRepository.java` — 1 constat [0 critique/haut]

- [`C-073`](#c-073) **Moyenne** — Les trois commandes de purge des logs materialisent toutes les lignes (dont l'itemstack LONGTEXT) en memoire uniquement pour les compter *(ligne 143)*

### `utils/ItemLoaderUtils.java` — 1 constat [0 critique/haut]

- [`C-090`](#c-090) **Moyenne** — Chargement des ItemStacks en O(n x m) : getAuctionItems refait un stream().filter() sur la liste complete pour CHAQUE item *(ligne 31)*

### `utils/cache/ZPlayerCache.java` — 1 constat [0 critique/haut]

- [`C-105`](#c-105) **Moyenne** — ZPlayerCache est un EnumMap nu écrit depuis des threads asynchrones, alors que le contrat de l'API promet la sûreté aux accès concurrents *(ligne 14)*

### `zAuctionHouse Redis/redis/listener/listeners/ItemRemovedListener.java` — 1 constat [0 critique/haut]

- [`C-071`](#c-071) **Moyenne** — ItemRemovedListener et ItemBoughtListener re-ajoutent l'item apres un second aller-retour DB, sans verifier qu'il n'a pas ete supprime entre-temps *(ligne 50)*
