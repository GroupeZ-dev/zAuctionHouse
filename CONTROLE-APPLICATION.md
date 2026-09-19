# Contrôle d'application — zAuctionHouseV4 + addon Redis

> Vérification adversariale, constat par constat, de ce qui est **réellement présent dans le code**.
> Menée en lecture seule, avec pour consigne de trouver le chemin par lequel le défaut passe encore.
> Chaque verdict s'appuie sur une citation littérale du code, jamais sur une déclaration d'agent.
> Les constats touchés par le tour de correction des régressions ont été re-contrôlés une seconde fois.

| Verdict | Constats |
|---|---|
| APPLIQUE | **85** |
| PARTIEL | **29** |
| ABSENT | **5** |
| **Total** | **119** |

**APPLIQUE** = le défaut d'origine ne peut plus se produire. **PARTIEL** = un volet manque et le défaut reste atteignable par un chemin. **ABSENT** = rien n'a changé.

---

## Constats restés absents

### `C-051` — `SellService.java` *(ligne 417)*

Aucune correction. La ligne 668 compte toujours des ANNONCES (getPlayerSellingItems rend une List<Item>, une annonce = un Item), la boucle de validation qui suit (lignes 681-698) ne valide chaque ItemStack que sur air/blacklist/whitelist sans jamais en compter le nombre, et aucune cle max-stacks-per-listing n'existe (grep 'max-stacks|maxStacks' sans resultat dans src/ ni dans config.yml). Le facteur de contournement est desormais borne par le correctif de C-097, qui plafonne la SELECTION au nombre de slots du bouton (SellShowItemButton:126 `if (!this.slots.isEmpty() && sellItems.size() >= this.slots.size())`) : sur les configurations livrees ce plafond vaut 36, donc le contournement reste bien d'un facteur 36, exactement comme decrit dans le constat. Correction attendue inchangee : sommer les getItemStacks().size() des annonces existantes et y ajouter itemStacks.size() avant comparaison, ou introduire une cle de configuration bornant les stacks par annonce, repliquee dans fr/, es/, it/.

### `C-083` — `ZAuctionManager.java` *(ligne 71)*

Le chemin demande par la consigne (« base EXPIRED / cluster DELETED ») EXISTE ENCORE, hors RemoveService. Chronologie : /ah admin remove sur un lot ; la ligne passe LISTED->DELETED, le bridge ecrit STATE_DELETED (persiste, ou TTL max(item-state-ttl, item-listed-ttl) = 30 jours par defaut, cf. RedisAuctionClusterBridge.java:205 `this.terminalStateTtl = maxTtl(itemStateTtl, itemListedTtl);`) et publie destination=DELETED ; les autres serveurs purgent l'item de tous leurs stores et n'executent PAS l'etape 2 de re-ajout (ItemRemovedListener : `if (destination != null && destination != StorageType.DELETED)`). Puis deliverOrRestore(admin, ...) constate qu'aucun stack n'est parti (admin deconnecte, entite retiree, filet de 15 s) et restoreFromDeleted repasse la ligne en EXPIRED avec expired_at=0, addItem(EXPIRED, item) la remet dans l'onglet du proprietaire sur ce seul serveur. Etat final : base EXPIRED, memoire locale EXPIRED, Redis DELETED, invisible ailleurs. Le proprietaire clique -> RemoveService.checkAvailabilityStep -> checkAvailability(item) -> isTerminalState(DELETED, null) = true -> ITEM_NOT_AVAILABLE, jusqu'a expiration de la cle. C'est exactement le gel decrit par C-083, sur un autre site. Correction du meme patron que RemoveService : chainer la diffusion APRES deliverOrRestore et diffuser DELETED ou `claimable` selon le booleen rendu. Note : ce site n'est pas une regression du tour de correction — il resulte de l'application conjointe de C-012 (diffuser une destination terminale) et de la compensation restoreClaimable ; il n'a simplement jamais recu le second volet de C-083.

### `C-095` — `build.gradle.kts` *(ligne 49)*

Aucun des quatre points du correctif n'est applique. (1) Aucune constante CONTRACT_VERSION nulle part : grep 'CONTRACT_VERSION' sur zAuctionHouseV4/API, zAuctionHouseV4/src et l'addon rend zero resultat. (2) setAuctionClusterBridge garde sa signature a un seul argument, donc aucune version compilee n'est declaree ni verifiee a l'installation. (3) La dependance reste epinglee sur le hash de commit deb8f16, pas sur une version semantique : la derive entre le jar principal et l'addon reste invisible dans le build. (4) Aucun `catch (LinkageError)` dans l'addon : les blocs supplyAsync/runAsync du bridge absorbent toujours silencieusement une AbstractMethodError ou une NoSuchMethodError dans un CompletableFuture. Les gardes d'entree ajoutees a onEnable (auctionPlugin == null, !auctionPlugin.isEnabled(), C-113) traitent un tout autre probleme — le plugin principal absent ou desactive — et ne disent rien de la compatibilite du contrat. Le scenario T3 du constat (achat commis en base mais ItemBoughtMessage jamais diffuse, donc fantome achetable ailleurs) reste ouvert tel quel.

### `C-096` — `SellService.java` *(ligne 66)*

Aucune correction. Aucune methode countListedBySeller n'existe (grep 'countListedBySeller|countBySeller' sans resultat dans src/ ni API/), la limite n'est jamais reevaluee apres validateItems, et la chaine qui suit comporte toujours au moins trois sauts asynchrones avant l'ecriture engageante : taxFuture.thenAccept (l.126), storageManager.reserveAuctionItem(...).whenComplete (l.142), puis scheduler.runAtEntity (l.157) avant commitReservation et publishAuctionItem. Le refactor en reservation/jalon/publication introduit depuis l'audit n'ajoute aucun controle de limite a aucune de ces trois etapes : les deux ventes concurrentes du scenario T2 comptent toujours la meme valeur et passent toutes les deux, et le scenario T1 (cache memoire local incomplet apres coupure Redis ou redemarrage) est inchange puisque le compte vient de PlayerCacheKey.ITEMS_SELLING, alimente par le store memoire du seul serveur local. Correction attendue inchangee : recompter en base juste avant l'INSERT engageant, idealement sous contrainte portee par la base.

### `C-103` — `ZAuctionManager.java` *(ligne 186)*

Aucun des quatre points du correctif n'est applique. (1) Aucune methode invalidateSortedItemsCache dans l'API : grep 'sortedItemsCache.invalidate|invalidateSortedItemsCache|setItemStatus' sur src/, API/ et l'addon ne rend qu'UNE seule ligne, ZAuctionManager:354, atteinte uniquement par addItem/removeItem sur StorageType.LISTED. (2) Aucune centralisation des mutations : les 20+ appels directs item.setStatus(...) subsistent (ConfirmHelper:82 et :179, ListedItemsButton:186, PurchaseService:176/:301/:360/:373, RemoveService:313/:528, ExpireService, ZMaintenanceScheduler:148, ItemStatusListener:145). (3) Cote addon, ItemStatusListener pose toujours le statut directement et se contente de clearPlayersCache + updateListedItems ; or updateListedItems passe par sortedItemsCache.ensureCacheValidAsync(), qui ne reconstruit que si le cache est DIRTY — il ne l'est pas, donc le rebuild n'a pas lieu et getItemIdsListedForSale (ZAuctionManager:404, `cache.getOrCompute(PlayerCacheKey.ITEMS_LISTED, () -> sortedItemsCache.getSortedIds(category, sort))`) rend une liste perimee : c'est exactement le T4/T5 du constat. (4) ListedItemsButton.getPaginationSize:75 rend toujours `getItemIdsListedForSale(player).size()`, donc le compte de pagination inclut encore des items que onRender saute. Seule attenuation trouvee, strictement locale : ZMaintenanceScheduler:168 appelle manager.rebuildSortedItemsCache() apres avoir libere les confirmations expirees, avec le commentaire `item.setStatus(...) n'invalide pas le cache trie` — c'est un contournement d'un seul chemin, pas la correction du constat.

---

## Constats partiellement appliqués

### `C-005` — `ZAuctionManager.java` *(ligne 1214)*

Le volet ITEM est ferme : les 6 sites (removeListedItem:698, removeSellingItem:741, removeExpiredItem:784, removePurchasedItem:826, adminRemoveItem:930, purchaseAuctionItem:1214) passent tous par updateItem(item, from, to) qui leve StaleItemException si rowcount==0 (ZStorageManager.java:299-303), et la remise physique est CHAINEE derriere via deliverOrRestore. La surcharge depreciee updateItem(Item,StorageType) n'a plus aucun appelant. Le volet ARGENT n'est PAS applique et le code le documente lui-meme : le correctif 1 de la fiche demandait « commit d'abord, withdraw/deposit ensuite ». Ici withdrawChecked (l.1106), depositChecked (l.1138) et les DEUX INSERT transactions (l.1160-1180, dont la ligne PENDING du vendeur) precedent le compare-and-set de la l.1214. Sur perte de course, le whenComplete l.1258-1273 ne fait que journaliser (« manual refund required ») : pas de remboursement de l'acheteur, pas d'annulation de la ligne PENDING. Consequence concrete : la ligne reste LISTED (le vendeur garde l'item et peut le revendre) ALORS QUE sa transaction PENDING reste reclamable via ClaimService — il est paye pour un item qu'il detient encore. Le point 4 du correctif (transaction Sarah englobant l'UPDATE items et les 2 INSERT transactions) n'est pas non plus applique.

### `C-015` — `ZAuctionEconomy.java` *(ligne 239)*

Le volet principal EST applique : l'API porte `withdrawChecked`/`depositChecked` (api/.../api/economy/AuctionEconomy.java:121 et :138), ZAuctionEconomy les implemente avec pre-controle du solde DANS la section confinee (l.202 `if (before == null || before.compareTo(value) < 0) return false;`), et tous les appelants testent le verdict (ZAuctionManager.java:1109 abandonne avant tout mouvement d'item, :1141 rembourse l'acheteur, SellService.java:506). La fenetre has()->withdraw decrite dans la chronologie est bien fermee, puisque le solde est relu juste avant le retrait sous le meme verrou de compte.

CE QUI MANQUE, dans ce meme fichier ZAuctionEconomy.java l.239-256 : le POST-controle detient la preuve que l'argent n'a PAS bouge et la jette — `return true;` est inconditionnel. Chemin restant, exactement l'issue titre du constat (« l'acheteur obtient l'item gratuitement et le vendeur est credite a partir de rien ») : un provider qui accepte l'appel mais refuse silencieusement pour une raison AUTRE que le solde — Vault `EconomyResponse(FAILURE)` d'un backend SQL/bank indisponible, compte gele, politique de minimum, PlayerPoints `take()` qui rend false — passe le pre-controle (solde suffisant), ne retire rien, journalise un WARNING, et rend true. ZAuctionManager.java:1106 lit `withdrawn == true`, credite le vendeur l.1138 et livre l'item l.1214. Le correctif prescrivait `return before.subtract(getBalance()).compareTo(value) >= 0;` ou la remontee de l'EconomyResponse par CurrenciesAPI ; ni l'un ni l'autre n'est en place.

Deuxieme volet manquant, moindre : ACCOUNT_LOCKS est un `static Object[]` de la JVM (l.28), donc le pre-controle n'est serialise que dans un seul serveur — la course inter-serveurs sur une economie partagee reste ouverte, ce que le code admet lui-meme (« LIMITE ASSUMEE : ce verrou ne protege que les appels passant par ZAuctionEconomy... la fenetre est reduite, pas fermee »). Troisieme : le defaut par defaut de l'API, `default boolean withdrawChecked(...) { withdraw(...); return true; }` (AuctionEconomy.java:121-124), rend le verdict aveugle pour toute implementation tierce de AuctionEconomy — compat assumee, mais c'est le contrat public.

### `C-022` — `ZAuctionPlugin.java` *(ligne 292)*

Le defaut signale au tour precedent est bien ferme : plus aucun chemin n'execute la tache JDBC quand l'appelant est un thread de tick (isPrimaryThread vaut true pour tout thread de region sous Folia, et la reponse conservatrice en cas d'echec est true, donc « ne rien executer »). Le drain de onDisable est bien ordonne AVANT la fermeture de la connexion (asyncExecutor.shutdown() + awaitTermination puis storageManager.onDisable()), et le drapeau volatile shuttingDown est teste en entree de PurchaseService:42, SellService:71, RemoveService:225 et ClaimService:100. Deux remarques de precision : la file etant un LinkedBlockingQueue NON borne, le handler n'est atteignable qu'APRES shutdown(), donc la branche runnable.run() et la garde de thread de tick sont en pratique du code defensif inatteignable ; et le point 3 du correctif d'origine — rendre la soumission robuste en retournant un CompletableFuture.failedFuture — n'a PAS ete applique, ce qui est precisement ce qui laisse passer la nouvelle regression ci-contre.

### `C-024` — `ZAuctionHouseRedis.java` *(ligne 473)*

Les deux volets demandes tiennent. Chemin 1 (exception avalee) : ferme — 3 tentatives espacees de 1 s, puis failClosed qui installe ClusterUnavailableBridge et disablePlugin ; le heartbeat n'est arme que DANS registerInstance reussi, il ne peut plus exister de noeud qui bat sans cle. Chemin 2 (cle disparue) : ferme — le battement lit la cle, la RECREE si elle a ete evincee ou perdue au redemarrage de Redis, et compare la valeur a un jeton de session tire au hasard a chaque onEnable, donc distinct de l'INSTANCE_UUID comme la contre-expertise l'exigeait ; un conflit reel declenche onRegistryConflict -> annulation du battement + failClosed sur le thread principal. Deux points restent ouverts. (1) Le TROISIEME volet, nomme dans le titre meme du constat, n'est pas applique : le battement est enregistre sur auctionPlugin.getScheduler(), donc sur le plugin PRINCIPAL. Un disable de zAuctionHouse (PlugMan, echec a chaud) annule la tache alors que l'addon reste charge : la cle expire au bout de REGISTRY_TTL_SECONDS et un noeud clone peut reprendre l'UUID sans detection. Impact reduit par rapport au constat d'origine, puisque l'addon est de toute facon inoperant si le plugin principal disparait, mais l'item n'est pas traite. (2) heartbeat() n'est pas atomique (GET puis SET NX / SET XX au lieu du script Lua propose) ; la course est self-correcting — le battement suivant lit la valeur et tranche — et le code le documente, donc ce n'est pas un defaut, seulement un ecart au correctif propose.

### `C-025` — `RedisAuctionClusterBridge.java` *(ligne 91)*

Le jeton de fencing demande par la fiche n'existe nulle part : aucun HINCRBY 'fence' dans LOCK_SCRIPT, aucun champ fence dans le hash, aucune colonne fence en base (`grep -n fence` sur ItemRepository.java ne rend rien). La reprise du verrou perime est litteralement inchangee : deux noeuds peuvent toujours se croire simultanement detenteurs. Ce qui a ete fait a la place, et qui ferme la DUPLICATION : (a) jeton unique par acquisition, LockToken.java:82 `new LockToken("item:" + id + ':' + ownerId + ':' + UUID.randomUUID())`, ce qui neutralise le vol d'unlock inter-serveurs du point 1 de la contre-expertise ; (b) bail renouvele, RENEW_SCRIPT + AuctionService.startLeaseWatchdog ; (c) compare-and-set en base avec rowcount TESTE, ZStorageManager.java:302 `if (rows == 0) throw new StaleItemException(...)` sur `where storage_type = from` (ItemRepository.createCasUpdateSchema l.335-338) ; (d) repli non atomique supprime, donc le point 3 de la contre-expertise tombe. Ce qui RESTE ouvert : la fenetre entre isHeldBy (PurchaseService.java:225) et l'UPDATE CAS n'est couverte par rien. Un detenteur lent qui perd son bail apres isHeldBy execute quand meme withdrawChecked + depositChecked + createTransaction (ZAuctionManager.java:1218-1180) et n'echoue qu'a l'UPDATE : l'acheteur est debite, le vendeur credite, aucun item livre, et la seule reparation est le SEVERE 'PURCHASE LOST THE RACE [...] manual refund required' (ZAuctionManager.java:1268-1273). Un fence n'aurait pas mieux fait sur ce point precis, mais le volet Redis du constat reste, lui, entierement non traite.

### `C-027` — `ZStorageManager.java` *(ligne 328)*

Points 5 et 6 du correctif appliques, et ils ferment la moitie du constat : `async` (l.172-177) poste desormais sur `this.plugin.getExecutorService()` — l'asyncExecutor, seul pool que ZAuctionPlugin.onDisable draine 5 s AVANT storageManager.onDisable() — au lieu du scheduler FoliaLib, et l'echec est journalise en SEVERE au lieu d'une stacktrace sur System.err ; les cinq methodes concernees (upsertPlayer x2, log, createTransaction, markPurchaseLogAsRead) passent toutes par la. Le point 6 est fait aussi : ZAuctionManager:1163-1181 encadre les deux ecritures d'un try/catch SYNCHRONE avec readBalanceOrZero (l.1293-1300), donc un provider d'economie qui leve ne fait plus sauter la suite de l'achat.

CE QUI MANQUE, et qui laisse le defaut atteignable : points 1, 3 et 4. createTransaction reste `void`, le future rendu par async(...) est JETE au site d'appel, et PurchaseService declare donc l'achat reussi sans que la ligne PENDING existe. Chronologie T3 branche "la tache echoue" (pool Hikari epuise, wait_timeout, deadlock InnoDB) : l'acheteur est deja preleve (ZAuctionManager:1106 withdrawChecked), l'item passe PURCHASED/DELETED, et la seule trace de la dette du vendeur se resume a une ligne SEVERE dans la console — /ah claim rend CLAIM_NO_PENDING. Le point 4 (ecrire la ligne PENDING AVANT le withdraw quand deferDeposit == true) n'est pas implemente non plus.

### `C-029` — `Base64ItemStack.java` *(ligne 112)*

Il manque le volet qui ferme reellement le defaut : (1) aucune migration ne reencode les lignes existantes — les 11 classes de storage/migrations/ ne touchent pas la colonne itemstack, et grep MARKER_BUKKIT|MARKER_NBT ne trouve que Base64ItemStack.java ; (2) aucun controle au demarrage de l'homogeneite du reseau autour du seuil 1.20.5 (point 5 du correctif) — isAttributItemStack() n'est reference qu'aux lignes 77 et 112 de Base64ItemStack ; (3) le sens le plus frequent reste casse : sur un serveur >= 1.20.5, decode() finit par decodeBukkitStream(data) et ne retente JAMAIS la voie NBT, donc un serveur unique mis a jour a travers 1.20.5 (declencheur principal designe par la contre-expertise) ne peut lire aucune de ses lignes historiques. Le marqueur, seul remede livre, reste inerte tant que write-itemstack-format-marker vaut false, et son passage a true est explicitement conditionne a une phase 2 qui n'a pas eu lieu. Consequence residuelle : l'annonce n'est plus publiee du tout au lieu d'etre publiee en BARRIER — indisponibilite du catalogue, la ou le constat parlait aussi de perte.

### `C-031` — `RedisSubscriberRunnable.java` *(ligne 46)*

Les quatre volets de detail SONT faits : reordonnancement listeners -> thread -> bridge (ZAuctionHouseRedis.java:206-239), `listeners` en ConcurrentHashMap (l.38), `enableDebug` volatile (ZAuctionHouseRedis.java:93) et warning explicite quand aucun listener n'est enregistre (l.216-219). Ce qui MANQUE est precisement ce que la contre-expertise designait comme le vrai trou : la reconciliation au PREMIER etablissement de la souscription. `reconnected` est initialise a false, donc le `compareAndSet(true, false)` de la l.118 echoue a la premiere iteration et `onTransportRecovered()` n'est jamais appele au demarrage. La fenetre "instantane DB de ZAuctionPlugin.loadItems() -> SUBSCRIBE effectif" (saveDefaultConfig, isSqliteStorage, loadServerUUID sur disque, VersionChecker, Metrics, PING de loadRedis, SET de validateAndRegisterUUID, puis demarrage du thread), soit plusieurs secondes, reste entierement non couverte : tout item vendu ailleurs pendant ce temps reste un fantome LISTED dans la memoire du noeud qui redemarre. Correctif attendu : declencher la reconciliation aussi a la premiere souscription (RedisSubscriberRunnable.java:118), ou initialiser `reconnected` a true.

### `C-035` — `PurchaseService.java` *(ligne 291)*

Le defaut signale au tour precedent est bien ferme sur le chemin vise : quand selectItemState conclut que la ligne n'est plus LISTED, plus aucune restauration ni rediffusion de IS_PURCHASE_CONFIRM n'a lieu, ITEM_SHOW est purge (:238) avant removeItem(LISTED) (:239), et les trois gardes d'entree de ConfirmHelper.releaseConfirmationState (ConfirmHelper.java:63 item null, :68 statut != previous, :73 isStillHeldForSale) referment chacune independamment le chemin de re-annonce. Le scenario reste OUVERT par l'autre porte. Chronologie : la revalidation :207 passe (ligne encore LISTED) ; auctionManager.purchaseItem debite l'acheteur (ZAuctionManager.java:1105 withdrawChecked) puis tente l'UPDATE compare-and-set ; un autre noeud a pris la ligne entre-temps, rows == 0, StaleItemException remonte AVANT le `auctionItem.setStatus(...)` de la ligne 1216, donc l'item reste IS_BEING_PURCHASED et reste dans le store LISTED. Dans exceptionally : statusChangedByUs vaut true, terminalStatusHolder vaut null (il n'est arme que dans la branche !stillListed), item.getStatus() == IS_BEING_PURCHASED -> troisieme branche :368-379, qui repose IS_PURCHASE_CONFIRM et le DIFFUSE au cluster. A la fermeture de la confirmation, ConfirmHelper.releaseConfirmationState franchit ses trois gardes (ITEM_SHOW jamais purge sur ce chemin, statut == previous, getItem(LISTED, id) == item) et execute :82-90 `item.setStatus(this.next)` + notifyItemStatusChange + updateListedItems(item, true, player) : le serveur re-annonce disponible, sur tout le reseau, une annonce dont sa propre ecriture conditionnelle vient d'etablir qu'elle etait deja prise, acheteur debite. Correction : traiter StaleItemException comme un verdict terminal (armer terminalStatusHolder + purger ITEM_SHOW + removeItem(LISTED)) exactement comme le font deja RemoveService.java:454 et ExpireService.java:496.

### `C-036` — `ZAuctionHouseRedis.java` *(ligne 384)*

Le chemin NOMME par le constat est bien ferme : `loadRedis()` passe desormais par `failClosed(...)` (l.491, 499, 510, 514), qui installe `ClusterUnavailableBridge` sur le plugin principal (l.365-367), et `ZAuctionPlugin.setAuctionClusterBridge` journalise toute transition (ZAuctionPlugin.java:579-581). Mais TROIS sorties anticipees de onEnable rejouent exactement le defaut : les trois echecs de `loadServerUUID()` (l.385-387 lecture IO, l.396-398 ecriture, l.407-410 UUID invalide) font `disablePlugin(this); return false;` SANS jamais appeler `failClosed`. Le `return` de la l.164 sort alors de onEnable avant toute installation de bridge, et onDisable ne fait rien non plus puisque son garde l.257 ne se declenche que si un bridge Redis avait ete pose. Sur un reseau a MySQL partagee, le noeud reste sur le `LocalAuctionClusterBridge` initial (ZAuctionPlugin.java:121) dont `isDistributed()` vaut false et dont les verrous sont une ConcurrentHashMap memoire invisible des autres noeuds : c'est mot pour mot la chronologie T1-T5 du constat (double acheteur, double giveItem, deposit immediat au vendeur hors ligne au lieu du claim). Manque aussi le volet 2 (`cluster.required` + controle differe cote plugin principal) : aucune occurrence de `cluster.required` ni de `clusterDegraded` dans le plugin principal, donc un addon absent ou jamais active reste totalement silencieux. Correctif : router les trois echecs de loadServerUUID vers `failClosed(...)`.

### `C-039` — `RemoveService.java` *(ligne 371)*

Le point 1 du correctif (« positionner localRemovalCompleted = true AVANT d invoquer le supplier, ou capturer l exception synchrone ») n a PAS ete applique : le drapeau est toujours pose uniquement dans le thenCompose du future RENDU par le supplier. Les points 2 et 3 le sont : ZAuctionManager.removeListedItem/SellingItem/ExpiredItem/PurchasedItem (l.698, 741, 784, 826) commencent desormais par `storageManager.updateItem(item, <source>, <dest>).thenCompose(v -> { ... })`, et giveItem (l.1366-1399) est confine au thread d entite, chaque stack en try/catch, `finally { future.complete(...) }` — il ne se termine JAMAIS exceptionnellement, et deliverOrRestore (l.1451) porte un `.exceptionally(...)`. Le declencheur nomme par l audit (inventaire plein, 2e stack drop depuis commonPool) est donc ferme.
Ce qui reste : entre le COMMIT en base et la pose du drapeau s execute tout le corps de la lambda de ZAuctionManager (l.700-721 pour removeListedItem : setStatus, removeItem, addItem, updateListedItems -> boucle sur getOnlinePlayers + mutation d IntArrayList non thread-safe, clearPlayerCache, message + item.getItemDisplay() qui parcourt et MUTE les ItemStacks, openMainAuction, callEvent, logItemAction), le tout sur le thread de l executor base. Toute levee dans cette zone laisse localRemovalCompleted=false, staleDetected=false, statusChanged=true -> restoreStatusOnError:497 diffuse `notifyItemStatusChange(item, targetStatus, oldStatus)` = AVAILABLE, et releaseLockOnError declenche UNLOCK_SCRIPT qui restaure `prev` (=AVAILABLE) cote Redis. ItemStatusListener:120-122 fait alors `updateListedItems(item, true, null)` : l annonce ressuscite dans le HDV de tous les autres noeuds face a une ligne DELETED. Le defaut d origine (« ressuscite l item sur tout le cluster alors que sa ligne est deja DELETED ») reste donc atteignable ; seule sa CONSEQUENCE de duplication est neutralisee en aval, par revalidateUnderLockStep et par le compare-and-set (createCasUpdateSchema:337 `schema.where("storage_type", expectedFrom.name())`), qui refusent de rendre l item une seconde fois. Le fantome est cosmetique mais permanent (invendable et inretirable) jusqu au redemarrage du noeud.

### `C-050` — `ItemStatusListener.java` *(ligne 66)*

Le cout d'origine est INTACT. Points 1 et 2 du correctif faits cote plugin : AuctionManager.java:106 expose `default Item getItem(StorageType, int)` et ZAuctionManager.java:270 le surcharge en O(1) (`storage == null ? null : storage.get(itemId)`). Mais le point 3 (usage dans ItemStatusListener) N'EST PAS fait : la l.67 copie toujours integralement le store (ZAuctionManager:245 `return new ArrayList<>(this.storageItemsById.getOrDefault(storageType, Map.of()).values())`) puis le parcourt lineairement, jusqu'a TROIS fois quand l'item est absent. Point 4 non fait : la resolution reste dans `runNextTick` (l.60), donc sur le thread principal. Point 5 fait mais INOPERANT sur ce defaut : le court-circuit `if (item.getStatus() == newStatus) return;` est en l.110, APRES les trois copies. Point 6 non fait : ItemLookup.java:27 (utilise par ItemBoughtListener:61 et ItemRemovedListener:58) est lui aussi O(n) par choix documente (`for (Item item : manager.getItems(storageType))`), l'addon restant epingle sur `compileOnly("fr.maxlego08.zauctionhouse:zauctionhousev4-api:deb8f16")` (build.gradle.kts:49), SHA anterieur a la publication de getItem. A 100 000 annonces et 20 messages/s, les pics de tick et la pression GC decrits sont inchanges — et deux nouveaux appels O(n) (ItemLookup dans les deux autres listeners) ont ete AJOUTES sur le meme thread.

### `C-055` — `SearchService.java` *(ligne 55)*

Volet 1 (suppression de la copie ArrayList + de la HashMap de n entrees) : APPLIQUE, et c'est le gros de l'allocation.

Volet 2 (ne pas vider ITEMS_SEARCH pour un evenement portant sur un seul item) : ABSENT. Aucun `removeFromSearchCache` n'existe dans le depot, et tous les sites cites vident toujours la cle en bloc - ItemListedListener.java:78 `manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH, PlayerCacheKey.ITEMS_SELLING);`, ItemBoughtListener.java:69 et :71, SellService.java:543, PurchaseService.java:195, ConfirmHelper.java:81 et :174. La FREQUENCE de recalcul est donc exactement celle de la chronologie de l'audit : 15 evenements cluster/s x 10 joueurs x 2 appels par rendu (onRender + getPaginationSize).

Volet 3 (memoisation de la forme minuscule des champs cherchables) : ABSENT. matchDefault:75-111 refait par item et par recherche : getTranslationKey().toLowerCase(), itemStack.getType().name().toLowerCase(), getDisplayName(item) qui SERIALISE un Component via PlainTextComponentSerializer, puis .toLowerCase(), et getLore(item) qui construit une NOUVELLE List<String> en serialisant chaque ligne de lore (`lore.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList()`), suivie d'un toLowerCase par ligne.

CONSEQUENCE : le defaut "la recherche devient le point chaud du main thread des que le cluster est actif" reste atteignable par le meme chemin. Le profil d'allocation a change de nature (plus de HashMap de 50 000 entrees, mais une serialisation de composant Adventure + une List<String> de lore allouees PAR ITEM PARCOURU) et il n'est pas evident que le total ait baisse sur un HDV dont les annonces ont du lore.

### `C-056` — `SortedItemsCache.java` *(ligne 1522)*

La regression signalee (DEUX rebuildCache d'affilee par cycle, « Borne a UN essai ») est fermee : runRebuildTask n'appelle rebuildCache() qu'une fois, sans reprise. La coalescence temporelle (volet 1) est en place et le nombre de tris est reellement borne : requestRebuild() est l'unique planificateur, garde par un seul AtomicReference<CompletableFuture> (rebuildInProgress a disparu — grep ne rend plus aucune occurrence, ni du booleen dirty), et il calcule `long delay = remainingCooldownMillis();` puis `runLaterAsync(..., delay, TimeUnit.MILLISECONDS)`, lastRebuildTime etant pose a la FIN du rebuild (l.505). Sous invalidation permanente : au plus un tri complet par 250 ms + duree, et chaque lecture qui echoue le cooldown coute 3 lectures atomiques + un currentTimeMillis(), donc rien de proportionnel au trafic. Le volet 3 (verrou unique) et le volet 4 (ForkJoinPool prive, `sortInPool` l.570-591 avec la garde `ForkJoinTask.getPool() == this.forkJoinPool` pour ne pas imbriquer de submit) sont la aussi. RESTE OUVERT — volet 2 du correctif d'origine : ZAuctionManager.java:1522 fait toujours `this.sortedItemsCache.ensureCacheValidAsync().thenRun(() -> {` au lieu d'un invalidate() suivi de la boucle, donc l'affichage attend encore la reconstruction. RESTE OUVERT — le rebuild periodique garanti (`runTimerAsync`) prescrit par le meme volet 1 n'existe pas : une invalidation qui tombe dans la fenetre n'est reprise que par la PROCHAINE lecture, et si aucune lecture ne survient apres la fenetre le cache reste sale indefiniment (les compteurs %listed_items% / %category_count_*% servent alors une valeur perimee, getSortedIds etant non bloquant par contrat).

### `C-064` — `CommandAuctionAdminGenerate.java` *(ligne 251)*

Le volet `/ah admin add` est ferme, les trois branches annoncent maintenant : addListed appelle `notifyItemListed(item)` (CommandAuctionAdminAdd.java:131) et addExpired/addPurchased chainent `updateItem(...).thenCompose(ignored -> getAuctionClusterBridge().removeItem(item, LISTED, EXPIRED|PURCHASED))` (l.172-173, 206-207) — j'ai verifie cote addon que le noeud distant relit bien la ligne et replace l'item dans le bon conteneur (ItemRemovedListener.java:86-116, `manager.addItem(destination, item)` sous garde `matchesDestination`), et que buyer_unique_id est bien persiste avant la diffusion (ItemRepository.createCasUpdateSchema:345-348, appele apres `item.setBuyer(target)` l.197).\n\nTrois volets nommes par la fiche restent ouverts :\n1) `/ah admin generate` : AUCUN appel au bridge (grep `getAuctionClusterBridge` sur src/ ne retourne rien dans ce fichier). Les items generes restent invisibles des autres noeuds jusqu'a leur redemarrage, et ne sont pas gouvernes par le protocole.\n2) Migration V3 : la relecture LOCALE a bien ete ajoutee (CommandAuctionAdminMigrate.java:188 `plugin.getStorageManager().loadItems()`) et l'idempotence est fermee par une sentinelle en base (l.97-109), mais aucune diffusion cluster n'existe. Les autres noeuds ont toujours besoin d'un redemarrage, et rien dans le code ne l'impose ni ne le documente.\n3) Cle Redis sans TTL : LOCK_SCRIPT cree toujours le hash `auction:item:<id>` par HSET sans jamais poser d'expiration (zAuctionHouse Redis/RedisAuctionClusterBridge.java:105-109 : `redis.call('HSET', itemKey, 'prev', previous); redis.call('HSET', itemKey, 'state', 'LOCKED'); redis.call('HSET', itemKey, 'lock', tokenValue)` — le seul PEXPIRE du script, l.158, porte sur lockKey dans le script de renouvellement). Un item verrouille puis relache sans achat ni suppression laisse donc encore un hash permanent.

### `C-066` — `ZStorageManager.java` *(ligne 91)*

Points 1 (partiellement), 2 et 4 appliques : quatre index applicatifs existent la ou il n'y en avait aucun, la clause IN est paginee (Repository.selectInOrFail, lots de 500) et l'echec est desormais bruyant et fail-closed (ItemRepository.select() -> selectOrFail, ZAuctionPlugin.onEnable:228 disablePlugin). 

Point 3 ABSENT — c'etait pourtant, avec le chunking, l'une des deux mesures que la contre-expertise jugeait "reellement necessaires" : aucune commande ni tache de purge des tombstones. `grep` sur tout src/main/java ne trouve aucun DELETE sur %prefix%items hors V3MigrationService:333 (compensation d'une migration) et ItemRepository.deleteReservation (limite a pending_publish = 1). Les lignes DELETED continuent donc de s'accumuler indefiniment, et `select()` balaye toujours toute la table a chaque boot de chaque noeud — l'index items(storage_type) aide peu sur un predicat `!= 'DELETED'` a tres faible cardinalite quand la majorite des lignes SONT des tombstones. Index items(expired_at) egalement absent.

### `C-070` — `ExpireService.java` *(ligne 165)*

Applique : point 3 (executor dedie — ZStorageManager.selectItemState:348 et findUniqueId:395 passent tous deux `this.plugin.getExecutorService()`, plus aucun supplyAsync orphelin dans le fichier) et point 5 (balayage planifie — ZMaintenanceScheduler.sweepExpirations:206-232, borne par `expiration-sweep-batch-size: 50` toutes les `expiration-sweep-interval-seconds: 60`, config.yml:519-530).
NON applique :
(1) Point 1 — aucune borne cote appelant : ZAuctionManager.getItemIds:516-529 collecte TOUS les items expires du store (pas ceux du joueur) et transmet la liste entiere. Declencheur intact : getPlayerSellingItems (l.423, `getItemIds(StorageType.LISTED, ...)`) sur ouverture de /ah selling.
(2) Point 2 — aucun plafond global d expirations clusterees en vol : `expiringItemIds` (l.33, l.313) ne fait que dedoublonner par id ; la boucle l.166-168 dispatche N expireListedItemClustered dans le meme tick, chacune valant 5 emprunts Jedis (dont 2 imbriques dans removeItem) + 3 requetes SQL. Apres un arret du serveur, ou avec expiration-sweep-interval-seconds a 0, le premier /ah selling relance la rafale telle quelle. Aucun `Semaphore` dans le projet (grep).
(3) Point 6 de la contre-expertise (fuite de verrou) — NON applique dans ExpireService, alors qu il l est ailleurs. ExpireService:329-332 : `clusterBridge.lockItem(...).orTimeout(perf.lockItemTimeoutMs(), MILLISECONDS).thenCompose(token -> { tokenHolder.set(token); ...` : le jeton est memorise APRES le orTimeout. Si LOCK_SCRIPT a pose SET NX PX + HSET state=LOCKED mais que le future a deja expire, tokenHolder reste null, le whenComplete l.384-385 (`var token = tokenHolder.get(); if (token != null && token.isAcquired())`) ne libere rien, et l item reste LOCKED jusqu au TTL du bail. PurchaseService:118-137 et RemoveService.acquireLockStep:263-278 ont bien recu le correctif (`lockFuture.whenComplete(...)` + `lockFuture.copy().orTimeout(...)` avec le commentaire « copy() est INDISPENSABLE »). ExpireService est le seul des trois a ne pas l avoir : incoherence entre correctifs.

### `C-074` — `ListedItemsButton.java` *(ligne 318)*

Aucun des trois correctifs nommes n'est en place, et le chemin d'origine est intact de bout en bout. (1) SortedItemsCache.java:94-101 fait toujours `IntList cached = cache.get(sortItem); return cached != null ? cached.clone() : new IntArrayList();` — pas de `getSortedIdsView`, le clone d'un int[50000] (200 Ko) reste paye a chaque MISS. (2) L'index inverse O(1) n'existe nulle part : `indexOf` a simplement ete remonte de la methode privee `findIndexOf` vers IntList/IntArrayList, avec le meme corps `for` lineaire. (3) Cote addon, `zAuctionHouse Redis/.../ItemStatusListener.java:118` et `:121` font TOUJOURS `manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING);` juste avant `updateListedItems(...)`, et `ItemListedListener.java:78` fait de meme avec ITEMS_LISTED. La chronologie T1->T3 de la fiche est donc rejouable telle quelle : purge globale -> MISS dans `getOrCompute(ITEMS_LISTED, ...)` (ZAuctionManager.java:404) -> clone complet -> scan lineaire, par spectateur et par message cluster. Seuls deux points ANNEXES sont corriges : le bug adjacent D) (`if (onlinePlayer == ignoredPlayer) continue;`, ZAuctionManager.java:1527) et la suppression de la copie defensive de `slots` dans onRender/updateInventory. La pression GC, cout dominant selon la contre-expertise, est inchangee.

### `C-078` — `ExpireService.java` *(ligne 53)*

Le point 1 du correctif (« Etendre la garde de routage : if (isDistributed()) { ... deleteExpiredItemClustered(item, storageType); return; } ») n a PAS ete applique. Les DEUX gardes de routage (l.53 chemin unitaire, l.165 chemin en lot) testent toujours `storageType == StorageType.LISTED &&`. Aucune methode `deleteExpiredItemClustered` n existe (grep), et le fichier ne touche le clusterBridge qu aux l.316/322/329/374/389, toutes dans expireListedItemClustered. La transition EXPIRED/PURCHASED -> DELETED reste donc SANS verrou distribue et SANS aucune diffusion : Redis ne recoit jamais d etat terminal pour ces items, le bus pub/sub aucun message, et les autres noeuds conservent des zombies dans leurs stores EXPIRED/PURCHASED jusqu a leur propre balayage.
Ce qui EST applique : (a) l UPDATE n est plus aveugle — `updateItem(item, storageType, StorageType.DELETED)` est un compare-and-set (ItemRepository.createCasUpdateSchema:334-340 `schema.where("storage_type", expectedFrom.name())` sur TOUTES les destinations) dont le rowcount est teste (ZStorageManager:299-303 `if (rows == 0) throw new StaleItemException(...)`), donc plus aucun ecrasement d un etat plus recent ; (b) le point (i) de la contre-expertise — l objet quitte bien le store (`this.auctionManager.removeItem(storageType, item)` l.115 et l.278) ; (c) le point (ii) — Repository.update remonte son rowcount via updateReturning.
La consequence la plus grave du constat (un item deja detruit rendu au proprietaire sur un autre noeud, fenetre de skew NTP) est fermee, mais AILLEURS, pas ici : c est RemoveService.revalidateUnderLockStep:329 (`boolean gone = optional.isEmpty()`, et ItemRepository.select(int):220 filtre `storage_type != DELETED`) qui refuse desormais la remise. La grappe conserve donc la divergence d etat cluster que le constat decrit dans son titre.

### `C-079` — `PurchaseService.java` *(ligne 239)*

Le point 1 du correctif — sortir la notification du chemin critique — N'A PAS ete applique : notifyItemBought reste un maillon bloquant, toujours borne par un orTimeout, et le TimeoutException se propage jusqu'au .exceptionally l.266. Comme resultHolder n'a alors ete positionne par personne sur ce chemin, la methode rend l.303 « PurchaseResult.failure("Internal error", PurchaseFailReason.INTERNAL_ERROR) » pour un achat integralement commis (argent debite, ligne base a jour via le CAS, item livre) — c'est le defaut (i) de la contre-expertise, intact. Le journal affiche toujours « Purchase operation timed out for item X » sur une vente conclue. Le second volet, lui, est neutralise, mais dans l'addon et non ici : UNLOCK_SCRIPT restaure desormais le champ 'prev' au lieu d'ecrire AVENIR/AVAILABLE en aveugle, et LOCK_SCRIPT rend SOLD terminal en portee LISTED (RedisAuctionClusterBridge.java:85-90 et :48-58) ; l'unlock du .exceptionally ne peut donc plus effacer durablement le marqueur SOLD.

### `C-080` — `SellService.java` *(ligne 143)*

La correction est reelle sur le chemin nominal — saveProfileIfNeeded(player) est appele sur le thread de region juste apres un jalon COMMITTED (l.224) et de nouveau apres returnItemsNow dans les deux chemins de restitution (l.196 et l.406) — et la reserve de la contre-expertise sur le cout TPS est prise en compte via action.save-profile-on-sell (defaut true dans les 6 config.yml). Mais la branche CommitOutcome.UNKNOWN, entierement nouvelle, retire les items de l'inventaire (removeItemsFromSlots a deja tourne l.169), ne les rend pas, et sort par `return` SANS jamais appeler saveProfileIfNeeded. Or c'est precisement la seule branche ou la ligne peut valoir 2 sans qu'on le sache. Chemin de DUPLICATION concret : l'UPDATE 1 -> 2 est commite cote serveur, la reponse se perd (coupure MySQL, timeout du pool, failover) -> UNKNOWN ; l'inventaire en memoire est ampute mais rien n'est persiste ; le process meurt dur dans la fenetre d'autosave (~5 min, et une panne base qui provoque ce UNKNOWN est justement le genre d'incident qui precede un crash) ; le playerdata revient a son etat d'avant la vente, le vendeur RECUPERE son lot ; au demarrage, recoverCommittedReservations voit pending_publish=2 et bascule la ligne en EXPIRED avec expired_at=0, donc reclamable. Le vendeur GARDE ses items ET la ligne finit reclamable : c'est exactement la duplication que le lot devait exclure. Un simple saveProfileIfNeeded(player) avant le `return` de cette branche la ferme, au meme titre que l.224. Accessoirement cette branche ne rembourse pas non plus la taxe et son SEVERE n'en dit rien, alors que toutes les autres sorties d'echec appellent refundSellTax.

### `C-082` — `SellService.java` *(ligne 176)*

Le volet SellService est bien applique (postSell quitte le pool pour le tick, et son exception est absorbee sans rembourser), mais les deux autres volets exiges par la contre-expertise manquent, donc la mutation concurrente de l'index reste atteignable — seulement depuis l'autre bout. (1) ZAuctionManager.java:580 : `index.computeIfAbsent(owner, uuid -> new IntArrayList()).add(itemId);` — la valeur est toujours un IntArrayList maison non synchronise, aucun SynchronizedIntList, aucun `synchronized` sur addToIndex/removeFromIndex (l.577-589). (2) Des ecrivains hors thread de tick subsistent : CommandAuctionAdminAdd.java:123, :159, :200 (`thenAccept` sur le future de creation, donc thread du pool) et ExpireService.java:254 (`addItem(EXPIRED, item)` a l'interieur du `whenComplete` du future LuckPerms offline). Le postSell du tick et ces ecrivains peuvent donc encore muter simultanement l'IntList du MEME vendeur. L'impact reste contenu (l'index n'est jamais lu, et une AIOOBE dans postSell est desormais attrapee l.179 sans remboursement).

### `C-087` — `V3MigrationService.java` *(ligne 189)*

Le volet 2 du correctif est INERTE. `migrationExecutor` (CommandAuctionAdminMigrate:141) n'execute que l'APPEL `provider.migrate(...)`, qui se contente de parser la config, d'instancier V3MigrationService et de rendre immediatement un future ; le corps de la migration est resoumis a `plugin.getExecutorService()` par V3MigrationService.java:189, c'est-a-dire l'asyncExecutor de 4 threads partage avec ZStorageManager.createAuctionItem, ClaimService et HistoryService. Le thread "zAuctionHouse-Migration" se termine en quelques microsecondes et le `.thenCompose` ne fait qu'attendre. Il faut soit passer l'executeur dedie a V3MigrationService.migrate (et aux quatre providers), soit y remplacer plugin.getExecutorService(). Le volet 3 (verrou cluster / cle Redis SET NX pendant la migration) est totalement absent : rien dans migration/ ni dans CommandAuctionAdminMigrate n'appelle getAuctionClusterBridge(), donc la variante T4 (commande lancee sur A et B en meme temps -> double importation en base partagee) reste ouverte, la sentinelle de C-034 n'etant ecrite qu'a la FIN de la migration. Ce qui EST ferme : le volet 1, la garde de re-entrance MIGRATION_RUNNING (l.133, liberee dans le whenComplete l.162 et dans le catch synchrone l.155) empeche N migrations paralleles sur un meme noeud, donc la saturation des 4 threads decrite en T1 ; et la garde "0 joueur connecte" (l.116-121) neutralise les consequences gameplay T2/T3 sur le noeud migrant.

### `C-089` — `ItemListedMessage.java` *(ligne 3)*

Point 3 du correctif FAIT : ZStorageManager.java:389 clot desormais selectItemState par `}, this.plugin.getExecutorService());` avec un commentaire nommant C-077/C-089 — le JDBC bloquant ne concurrence plus les appels Jedis sur ForkJoinPool.commonPool. Point 1 fait A MOITIE et seulement pour l'achat : ItemBoughtMessage porte maintenant (itemId, sellerUuid, sellerName, itemDisplay, price, buyerName, givenToBuyer) et ItemBoughtListener:159 `if (Boolean.TRUE.equals(message.givenToBuyer())) return;` supprime le SELECT quand give-item=true. Mais ItemListedMessage n'a pas bouge (un seul champ) et ItemRemovedMessage non plus (itemId + deux StorageType) : ItemListedListener:37, ItemRemovedListener:87 et :120, et ItemBoughtListener:162 quand give-item=false, declenchent toujours un selectItem par message et par serveur, soit 3 requetes SQL (items, players, auction_items ; 4 si buyer renseigne) x (N-1) noeuds pour propager un seul evenement. Point 2 (deduplication/batch sur fenetre de 100 ms via selectItems(List)) : non fait, aucun accumulateur dans le paquet listener. L'amplification lineaire avec le nombre de serveurs, qui est le coeur du constat, subsiste pour les mises en vente et les retraits.

### `C-097` — `SellShowItemButton.java` *(ligne 124)*

Le volet principal est bien ferme : la borne est posee au SEUL point d'ecriture de SELL_ITEMS (verifie par grep : seuls SellShowItemButton:132 et :146 ecrivent la cle), le message existe dans l'enum (API/.../messages/Message.java:61) ET dans les six messages.yml (racine, fr, es, it, id, th, ligne 155) — T1/T2/T3 de la chronologie ne sont plus atteignables. En revanche le SECOND volet du correctif, explicitement exige par la fiche (« Corriger en complement la garde de restitution »), n'est PAS applique : src/main/java/fr/maxlego08/zauctionhouse/items/ZItem.java:172-174 est toujours `public boolean canReceiveItem(Player player) { return player.getInventory().firstEmpty() != -1; }`, et ZAuctionItem ne le surcharge pas. T4 reste donc rejouable : un joueur qui liste 36 stacks (sell-inventory.yml expose 36 slots pour ZAUCTIONHOUSE_SELL_SHOW_ITEM) puis remplit son inventaire voit la garde passer sur un unique slot libre — les 35 stacks excedentaires tombent au sol via dropItem lors du retrait ou de l'expiration. Les six appelants concernes sont PurchaseService.java:71 et RemoveService.java:71, :93, :131, :169.

### `C-106` — `RedisAuctionClusterBridge.java` *(ligne 410)*

Le volet 3 de la fiche (migrer les 4 sites d'appel) n'est PAS applique : checkAndLock n'a AUCUN appelant. `grep -rn checkAndLock --include=*.java` sur les deux depots ne rend que la declaration API (AuctionClusterBridge.java:155), la surcharge Redis (l.410) et deux renvois javadoc. Les 4 chemins chauds enchainent toujours les deux appels, donc les 6 emprunts de pool Jedis par achat et le pre-controle TOCTOU subsistent a l'identique : PurchaseService.java:108 puis :118 ; RemoveService.java:231 (checkAvailabilityStep l.245) puis :263 ; ExpireService.java:322 puis :329 ; ZAuctionManager.java:887 puis :895. Trois de ces quatre appellent en plus la surcharge SANS portee (`checkAvailability(item)`), donc permissive pour SOLD/REMOVED, ce qui rend le pre-controle non seulement inutile mais toujours trompeur. Volets appliques : LOCK_SCRIPT rend bien -1/0/1 (l.78-98), LockToken.unavailable() existe, et checkAvailability est documentee comme non engageante (AuctionClusterBridge.java:22-25).

### `C-107` — `ZAuctionManager.java` *(ligne 596)*

Le point 1 du correctif est applique (cle UUID au lieu de l'objet Player) : la fuite de CraftPlayer / inventaire / monde est bien fermee, et c'est l'essentiel de l'impact. Le point 2 ne l'est PAS : getCache ne teste toujours pas isOnline et fait toujours computeIfAbsent, et peekCache a ete declare PRIVE (l.607) — donc inutilisable depuis les chemins de fermeture. ConfirmHelper.releaseConfirmationState, appele depuis onInventoryClose (l.34) que zMenu emet APRES PlayerQuitEvent, fait toujours manager.getCache(player) et reinsere une entree pour un joueur deja purge par removeCache. Chronologie T2-T4 de la fiche encore vraie : l'entree n'est plus jamais retiree pour un joueur qui ne revient pas, et clearPlayersCache (l.612-614) itere ces entrees mortes a chaque vente. La fuite est desormais un ZPlayerCache vide par UUID au lieu d'un graphe CraftPlayer — nettement moins grave, mais non nulle et non bornee dans le temps.

### `C-114` — `ZItem.java` *(ligne 168)*

Rien n'a ete applique sur le constat lui-meme : aucun CLOCK_OFFSET, aucun selectDatabaseTime(), aucun avertissement de derive (« grep -rn "CLOCK_OFFSET|clockOffset|selectDatabaseTime" » ne rend RIEN dans src/). Le volet qui rendait le constat couteux est ferme par AILLEURS : PurchaseService.java:176-180 exige desormais « dbItem.getStatus() == ItemStatus.AVAILABLE » dans la relecture sous verrou, donc le T3 de la fiche (l'acheteur paie un item deja bascule EXPIRED par un noeud a l'horloge en avance) est inatteignable, et ExpireService.java:92-96 ecrit en compare-and-set updateItem(item, LISTED, EXPIRED). Ce qui RESTE atteignable, avec l'ecart d'horloge pour amplitude : (1) le noeud le plus en avance decide seul de l'expiration pour tout le reseau — ZMaintenanceScheduler.java:217 « if (!item.isExpired()) continue; » et ZAuctionManager.java:517 declenchent processExpiredItems sur leur seule horloge ; (2) plus grave que la fiche ne le dit, ZAuctionManager.getItemIds applique la meme regle au conteneur EXPIRED, dont la branche « else » de ExpireService (l.111-118) ecrit DELETED sans rien rendre : la fenetre de reclamation du vendeur est amputee de la derive ; (3) chaque nouvelle echeance posee a l'expiration est re-horodatee sur l'horloge du noeud qui traite, donc la derive se cumule d'un cycle a l'autre. L'addon Redis acte lui-meme le probleme sans le resoudre (ItemListedListener : « on ne teste volontairement PAS item.isExpired() ici. Sur un parc dont les horloges divergent, ce terme rejetterait des annonces legitimes »).

### `C-115` — `CommandAuctionAdminGenerate.java` *(ligne 193)*

Seul le point 3 du correctif a ete applique. Ce qui manque, dans ce meme fichier :\n- Point 1 (lire la section) : NON fait. Les trois valeurs sont toujours en dur aux l.194-196. Le prix reste tire dans [10 ; 100 000] la ou la configuration annonce [10 ; 1 000 000] (config.yml:1207-1211), l'expiration reste figee a 24 h la ou la configuration annonce RANDOM [3600 ; 604800] ou FIXED 172800 (config.yml:1178-1191), et le mode FIXED reste inatteignable pour les trois sous-sections.\n- Point 2 (supprimer la section) : NON fait non plus, et la desynchronisation s'est AGGRAVEE. `grep -rn "admin-generate" src/` retourne desormais SIX config.yml (racine, es/, fr/, id/, it/, th/) et toujours ZERO fichier .java. Aucun code ne lit `admin-generate` : la configuration reste integralement mensongere, dans six langues au lieu de deux.\n- Point 3 (retention de Player) : FAIT et verifie. `private final Map<UUID, PendingGeneration> pendingGenerations = new ConcurrentHashMap<>();` (l.40), cle par `senderId(...)` qui rend `player.getUniqueId()` ou CONSOLE_ID (l.105-107), et purge a la lecture `this.pendingGenerations.values().removeIf(entry -> currentTime - entry.requestedAt() >= CONFIRMATION_WINDOW_MS)` (l.82). Plus aucune reference forte vers un objet Player n'est conservee.

---

## Défauts restant introduits par les correctifs

Le contrôle signalait tout nouveau défaut, y compris sur un constat par ailleurs bien fermé. Ceux qui suivent sont ceux qui subsistent après le tour de correction des régressions.

### `C-002` (APPLIQUE) — `RedisAuctionClusterBridge.java`

Residu non bloquant, pas une regression franche : (1) notifyItemBought ecrit SOLD meme quand la destination base est DELETED (purchased.giveItem()==true) — l'annonce est detruite en base mais Redis annonce SOLD, non terminal hors portee LISTED ; seule la revalidation base (select(int) filtre DELETED) empeche une reclamation fantome. (2) Le correctif 4 de la fiche (fusionner SOLD+unlock+publish en un script Lua) n'est pas applique : notifyItemBought reste HSET+EXPIRE+PUBLISH, et sur son orTimeout PurchaseService.java:284 part en exceptionally -> unlockItem, dont UNLOCK_SCRIPT restaure prev='AVAILABLE' sur un achat DEJA commis en base. Non duplicant (revalidation sous verrou), mais l'etat Redis ment. (3) Le correctif 5 (ecrire FIELD_BUYER) n'est pas fait : `grep -n FIELD_BUYER` ne rend que la declaration l.44.

### `C-003` (APPLIQUE) — `ClaimService.java`

Le nouveau filet rouvre au REDEMARRAGE la fenetre de double paiement que son propre commentaire declare fermee. Quand un depot a reussi puis que closeClaim epuise ses 3 tentatives, la branche l.229-234 laisse les lignes en PENDING avec leur jeton et affirme : « AUCUNE recuperation automatique ne les rendra (leur jeton porte le prefixe du run courant), pour ne pas payer une seconde fois ». Or `ensureTokenPrefixes` (l.374-383) fait `this.runTokenPrefix = serverPrefix + randomHexadecimal(6) + "-";` — regenere a CHAQUE demarrage — alors que serverTokenPrefix est volontairement stable, et liveClaimTokens est un Set en memoire perdu a l'arret. Au boot suivant, ces lignes DEJA PAYEES matchent selectReservedTokens (meme prefixe serveur), echouent aux deux gardes (`token.startsWith(this.runTokenPrefix)` faux car le run a change, liveClaimTokens vide), et sont liberees par releaseReservation avec le message « server crashed between reservation and payment? ». Le claim suivant les reserve et les paie une seconde fois. Cause structurelle : rien n'est persiste entre le depot et la cloture, le mecanisme ne peut donc pas distinguer « reservee, jamais payee » (a liberer) de « reservee, payee, cloture en echec » (a ne jamais liberer). Fenetre nettement plus etroite que les 5 minutes qu'elle remplace (il faut un depot reussi + un echec d'ecriture persistant + un redemarrage), mais la garantie annoncee dans le code est fausse.

### `C-004` (APPLIQUE) — `ItemStatusListener.java`

Verrouillage de divergence (mineur, pas de duplication). Le CAS est absolu : une seule transition manquee sur un pair rend TOUTES les suivantes inapplicables, puisque chaque message porte l'oldStatus de l'emetteur. Si B rate ou rejette AVAILABLE->IS_PURCHASE_CONFIRM (message perdu, ou conflit avec un IS_REMOVE_CONFIRM concurrent), le IS_BEING_PURCHASED qui suit est rejete lui aussi (local AVAILABLE != IS_PURCHASE_CONFIRM) et B continue d'afficher l'annonce comme achetable jusqu'a sa vente/expiration reelle. Le balayage de ZMaintenanceScheduler ne repare pas ce cas : il ne rearme que les items PORTANT deja un statut IS_*_CONFIRM (CONFIRMATION_STATUSES l.33), jamais un AVAILABLE reste en retard. Pas de duplication : le verrou distribue + revalidation DB de PurchaseService rattrapent le clic.

### `C-005` (PARTIEL) — `ZAuctionManager.java`

Aucune regression, mais une contrepartie assumee et ecrite en commentaire l.1205-1211 : apres echec de l'UPDATE l'acheteur est debite sans rien recevoir. C'est une perte reversible a la main, pas une duplication d'item.

### `C-006` (APPLIQUE) — `PurchaseService.java`

Aucune sur ce volet. Point mort a signaler : item.setExpiredAt(dbItem.getExpiredAt()) l.210 resynchronise l'expiration sur la base puis appelle directement auctionEconomy.has(...) — le if (item.isExpired()) que prescrivait le correctif n'existe pas, la valeur resynchronisee n'est plus jamais relue dans la chaine. Une ligne encore LISTED mais expiree en base (expiration paresseuse non encore passee) reste donc achetable. Pas de duplication : le CAS LISTED->PURCHASED/DELETED tranche contre le CAS LISTED->EXPIRED de ExpireService.

### `C-007` (APPLIQUE) — `PurchaseService.java`

Aucune regression. Residus assumes : (1) auctionEconomy.has(...) l.211 n'a toujours aucun orTimeout et s'execute AVANT l'armement du watchdog l.223 — un blocage du provider superieur au TTL y laisse le bail expirer, mais isHeldBy l.225 avorte alors AVANT tout debit ; (2) getPlayerName JDBC synchrone et withdraw/deposit restent dans la section critique (point 4 du correctif non applique) ; (3) AuctionService.MAX_LEASE_RENEWALS = 30 borne la tenue a ~5 min (30 x lease/3), apres quoi le bail peut expirer en pleine section — le filet est alors le CAS base updateItem(item, LISTED, destination) qui leve StaleItemException a 0 ligne.

### `C-008` (APPLIQUE) — `LockToken.java`

Aucune regression fonctionnelle trouvee. Deux points du correctif d'origine NON appliques, verifies inoffensifs : (a) point 3 — LockToken.of(Item) est conserve (deprecated, LockToken.java:71) mais delegue a issue(), donc la valeur reste imprevisible et aucun appelant ne subsiste (grep LockToken.of( = 1 seule occurrence, sa propre definition) ; (b) point 4 — UNLOCK_SCRIPT ne valide toujours que le champ de hash (RedisAuctionClusterBridge.java:118-121 : local currentToken = redis.call('HGET', itemKey, 'lock') ; if currentToken ~= tokenValue then return 0 end), pas la valeur de la cle. J'ai deroule les deux fenetres du constat : fenetre 2 (double-unlock) est fermee car deux acquisitions ne produisent plus jamais la meme chaine ; fenetre 1 (bail expire puis re-acquisition par B) est fermee aussi car LOCK_SCRIPT reecrit HSET itemKey 'lock' tokenB, donc l'unlock retardataire de A lit tokenB != tokenA et rend 0 sans DEL. Le controle sur la cle n'ajoute rien. Risque residuel non introduit par ce correctif : LocalAuctionClusterBridge n'a aucun bail (lockLeaseDuration() == ZERO) et la liberation est desormais CONDITIONNELLE — une chaine qui perdrait son jeton laisserait l'item verrouille jusqu'au redemarrage ; aucun chemin de ce type n'existe aujourd'hui (le bridge local rend des completedFuture, donc aucun orTimeout ne peut couper avant l'affectation du jeton).

### `C-009` (APPLIQUE) — `RemoveService.java`

Aucune sur ce constat. La revalidation est bien sautee en mono-serveur (`if (!clusterBridge.isDistributed()) return completedFuture(null);` l.319-321), ce qui est correct puisque la garde d identite l.220 (`this.manager.getItem(storageType, item.getId()) != item`) y suffit. Verifie adversarialement : les 4 entrees publiques passent toutes par executeRemoval et AUCUN appelant ne court-circuite (grep sur `.removeListedItem(`/`.removeSellingItem(`/`.removeExpiredItem(`/`.removePurchasedItem(` : seuls les boutons + les 3 boutons de retrait en masse ZAuctionManager:1678/1687/1696 passent par auctionRemoveService). Le volet complementaire exige par la contre-expertise (garde de statut dans ListedItemsButton avant de poser IS_REMOVE_CONFIRM) est present : ListedItemsButton.openConfirmation:176 `if (item.getStatus() != ItemStatus.AVAILABLE) { ... return; }`. Le scenario T3-T7 est donc coupe en deux endroits.

### `C-011` (APPLIQUE) — `ItemLoaderUtils.java`

Aucune. Verifie sur les DEUX voies de construction depuis la base et sur la voie memoire : (1) AuctionLoader -> createItems l.160-165 traite le null comme quarantaine et n'appelle jamais biConsumer.accept, l'annonce n'entre donc dans aucun storage ; (2) ZStorageManager.selectItemState l.369-375 : « if (auctionItem == null) return ItemLookupResult.unavailable(); », si bien que la revalidation SOUS VERROU de PurchaseService (l.176-180 : « boolean stillListed = lookup.isFound() && dbItem != null && ... ») REFUSE l'achat avant tout withdraw, y compris sur le noeud vendeur dont la copie memoire porte encore les stacks ; (3) l'addon Redis ItemListedListener teste « if (item == null) ... return; » et ne peut plus injecter le fantome ; (4) la voie memoire AuctionItemRepository.create l.72-79 leve IllegalStateException sur toute charge utile nulle/vide, et SellService:59 refuse un lot vide. Le correctif n° 3 de la fiche est lui aussi en place : ZAuctionManager.giveItem l.1356-1361 rend « GiveResult.notExecuted(0) » sur un lot vide et deliverOrRestore repositionne la ligne en conteneur reclamable. Restent non appliques deux points explicitement etiquetes complementaires : la colonne stack_count (correctif n° 4) et la mise en DELETED/quarantaine de la ligne (choix documente : la ligne est laissee INTACTE). Consequence assumee : une ligne sans enfant est re-quarantinee et re-journalisee en SEVERE a chaque demarrage de chaque noeud, indefiniment.

### `C-013` (APPLIQUE) — `ClaimService.java`

Aucune. Les trois volets prescrits sont en place : joueur hors ligne -> unpaidIds + releaseClaim (l.130-134), exception ou refus du provider -> unpaidIds (l.143-153), `totalClaimed` deplace APRES le depot reussi (l.156), et `finishClaim(paidIds, claimToken)` ne cloture que les lignes creditees (l.162). Une seule zone grise, non atteignable aujourd'hui : l.124-128, un groupe d'economie dont la somme des valeurs STRICTEMENT POSITIVES est nulle part en paidIds (donc RETRIEVED) sans aucun depot. Le filtre `filter(v -> v.compareTo(BigDecimal.ZERO) > 0)` l.122 ignore les valeurs negatives, donc un groupe melangeant -50 et +100 crediterait 100 et cloturerait les deux lignes. Inatteignable en pratique : les seules lignes PENDING creees le sont avec `finalSellerReceives` positif (ZAuctionManager.java:1176) et par V3MigrationService.java:558.

### `C-015` (PARTIEL) — `ZAuctionEconomy.java`

Aucun nouveau defaut. Le point 5 du correctif (Set<UUID> `purchasing` dans PurchaseService) n'est pas applique — verifie : aucun `newKeySet` ni champ de ce type dans PurchaseService.java — mais il est devenu redondant pour la variante mono-serveur : accountLock() est indexe sur (identityHashCode(currencyProvider), playerId) (l.106-110), donc deux achats simultanes du meme acheteur sur la meme economie sont bien serialises par le verrou stripe, et le second voit le solde deja debite.

### `C-016` (APPLIQUE) — `ZAuctionHouseRedis.java`

Aucune regression sur le defaut d'origine. Les trois volets sont la : signature booleenne + garde a l'appel (l.163), ecriture ATOMIQUE tmp + Files.move ATOMIC_MOVE (l.438-444), fichier VIDE traite comme absent (l.394-402), et cote reception le test est bien inverse (RedisSubscriberRunnable.java:188-192 : `UUID senderId = redisMessage.serverId(); if (senderId == null) { ... return; }` puis `if (senderId.equals(INSTANCE_UUID)) return;`). Point mineur non ferme : `saveServerUUID()` (l.417-419) IGNORE le booleen de `writeServerUUID`, donc une regeneration d'UUID par `validateAndRegisterUUID` (l.464-465) qui echoue a ecrire laisse le noeud tourner avec un UUID memoire correct mais un server.info porteur de l'ANCIEN UUID duplique : la collision revient au redemarrage suivant. INSTANCE_UUID n'est jamais null pour autant, le defaut C-016 reste ferme.

### `C-017` (APPLIQUE) — `PurchaseService.java`

Aucune. Les trois sites convergent bien sur le meme calcul : PurchaseService:63, ZAuctionManager:1049-1059 (charge = ZPurchaseCharge.resolve(...); buyerPays = charge.buyerPays()) et ListedItemsButton:244. Le point B de la contre-expertise (TaxResult reinterprete sous le type de l'ECONOMIE) est ferme par TaxResult.appliedType + buyerPays()/sellerReceives(), le point D par withdrawChecked/depositChecked (ZAuctionManager:1106-1113 avorte et leve si le debit est refuse), et mintsMoney() ajoute un garde-fou anti-frappe. ZPurchaseCharge.resolve balaie TOUS les stacks (pas seulement getFirst()), ce qui ferme le contournement par rangement en 2e position.

### `C-018` (APPLIQUE) — `ZAuctionHouseRedis.java`

Deux effets nouveaux a connaitre. (1) Le bridge n'est JAMAIS restaure automatiquement : un `plugman unload` sans reload laisse le plugin principal sur ClusterUnavailableBridge, dont checkAvailability/lockItem/notify* rendent tous `failedFuture`. Le noeud passe donc d'"achats casses en silence" a "achats casses explicitement" — c'est le choix sur, mais il n'existe toujours ni compteur d'echecs, ni commande de reload, ni sonde de sante (volet 3 du correctif d'origine non implemente). (2) Le T5 du constat reste ouvert : au re-enable, `RedisSubscriberRunnable` est reconstruit avec `reconnected = new AtomicBoolean(false)` (l.46), donc la PREMIERE souscription ne declenche pas `onTransportRecovered()` et la memoire n'est pas reconciliee apres la coupure. Le dupe qui en decoulait est neanmoins bloque en aval par LOCK_SCRIPT (`if listedScope == '1' and (currentState == 'SOLD' or currentState == 'REMOVED') then return -1`) et par checkAvailability(item, LISTED).

### `C-022` (PARTIEL) — `ZAuctionPlugin.java`

L'abandon est MUET pour l'appelant : le handler ne lance rien et n'execute rien, donc CompletableFuture.runAsync(runnable, executor) rend un futur qui n'est JAMAIS complete (ni normalement, ni exceptionnellement). ZStorageManager.updateItem:315-316 (`return CompletableFuture.runAsync(() -> with(ItemRepository.class).updateItem(item, storageType), this.plugin.getExecutorService());`) est chaine par ZAuctionManager:1214 (`return storageManager.updateItem(auctionItem, StorageType.LISTED, destination).thenCompose(v -> {`) APRES le debit de l'acheteur (ZAuctionManager:1106 `boolean withdrawn = auctionEconomy.withdrawChecked(...)`), et PurchaseService:278-281 chaine ce futur en refusant explicitement tout garde-fou temporel (« AUCUN orTimeout ici, JAMAIS : purchaseItem ne rend que son updateFuture »). Consequence pour un achat deja en vol au moment du onDisable — le scenario meme de C-022, que le drapeau shuttingDown ne couvre pas puisqu'il n'est teste qu'a l'ENTREE : l'acheteur est debite et le vendeur credite, le thenCompose ne s'execute jamais donc l'item n'est ni remis ni bascule en PURCHASED, removeItem(LISTED) n'a pas lieu, le verrou n'est jamais relache (releaseLock est en aval du meme thenCompose), le watchdog de bail renouvelle indefiniment, et aucun exceptionally ne se declenche : le seul temoin est la ligne SEVERE du handler. La version d'origine levait au moins une RejectedExecutionException synchrone routee vers PurchaseService.exceptionally. Correction attendue : dans le chemin d'abandon, completer le futur en echec (point 3 du correctif d'origine) plutot que de le laisser pendant.

### `C-023` (APPLIQUE) — `PlayerListener.java`

Liberation croisee sur ITEM_SHOW perime. `releaseConfirmationState` (ConfirmHelper.java:59-83), contrairement au `releaseConfirmation` statique (l.157 `cache.remove(PlayerCacheKey.ITEM_SHOW)`), ne purge PAS ITEM_SHOW ; apres un clic Retour ou une fermeture normale, la cle de P1 pointe encore sur l'item #42. Si P2 rouvre ensuite une confirmation sur #42 (meme instance Item du store LISTED), puis que P1 quitte ou meurt, `releaseConfirmation(plugin, P1)` lit ITEM_SHOW=#42, voit IS_PURCHASE_CONFIRM (pose par P2), passe la garde l.155 et diffuse AVAILABLE : c'est la confirmation de P2 qui est cassee et l'item est re-affiche disponible sur tout le cluster pendant que P2 est dans la GUI. Le verrou Redis empeche la duplication, mais la desynchronisation que C-068/C-069 ferment est rouverte par ce chemin. Le volet TTL est en revanche bien present et actif par defaut (ZMaintenanceScheduler.sweepConfirmations, config.yml:538 `confirmation-timeout-seconds: 60`, :541 intervalle 10 s), IS_BEING_* explicitement exclus, ce qui couvre la variante T6 (crash du noeud A).

### `C-028` (APPLIQUE) — `ZAuctionHouseRedis.java`

REGRESSION REELLE, nee de la combinaison C-028 + C-052. `pendingResync` n'est draine qu'a deux endroits : `onTransportRecovered()` (l.651) et `onDisable` (l.307). Or `onTransportRecovered` n'est declenche QUE par un echec/retour de `jedis.subscribe` (RedisSubscriberRunnable.java:118 `reconnected.compareAndSet(true, false)`). Depuis que maxWait est borne (RedisConnectionFactory.java:86-87), un `publish` peut desormais echouer sur JedisExhaustedPoolException ou sur un timeout de commande SANS que le thread abonne ne perde sa souscription : le message part alors en file et y reste indefiniment (au plus MAX_PENDING_RESYNC=1000, puis les plus anciens sont JETES avec un simple warning l.603) pendant que les autres noeuds gardent le fantome. Il manque un drainage periodique de la file, independant du cycle de souscription.

### `C-029` (PARTIEL) — `Base64ItemStack.java`

Pas de regression, et deux vrais gains a porter au credit du correctif : (a) ItemLoaderUtils.createAuctionItem:80-84 met l'annonce en QUARANTAINE des qu'un ItemStack rend null ("it will NOT be published. The database row is left untouched."), ce qui supprime le BARRIER achetable, le debit du prix plein et la NPE en boucle de ZAuctionManager:731 (point 3 du correctif, applique) ; (b) le sens legacy est desormais repare par effet de bord du correctif C-054 : sur un serveur < 1.20.5, decode() -> safeDeserializeItemStack -> echec NMS -> decodeBukkitStream reussit a lire une charge moderne. Seul le sens moderne -> NBT reste aveugle.

### `C-030` (APPLIQUE) — `SellService.java`

Deux defauts introduits par la correction elle-meme, tous deux sur la voie 'perte acceptee mais tracee'. (a) Les logs mentent sur le sort reel du lot. Quand uncommitReservation a reussi (ligne repassee a 1) mais que returnItemsNow ou saveProfileIfNeeded leve ensuite, handedBack reste false et le SEVERE de la l.423 annonce "The reservation is kept CLAIMABLE by its seller in the 'expired items' section" — c'est FAUX : la ligne vaut 1, donc purgeUncommittedReservations la SUPPRIMERA au demarrage suivant. Symetriquement, le SEVERE de ZStorageManager.recoverOrphanReservations affirme pour toute ligne purgee "the seller still holds those stacks in his inventory, handing them back would have DUPLICATED them", ce qui est faux pour toute ligne arrivee a 1 par la branche UNKNOWN ou par ce chemin-la. Le design revendique explicitement 'la perte tracable plutot que la duplication silencieuse' ; ces deux messages orientent l'admin vers la conclusion inverse et rendent la reconciliation manuelle impossible. (b) Le runAtEntity principal (l.156, `this.plugin.getScheduler().runAtEntity(player, task -> {`) ne consomme PAS son future, alors que recoverFailedPublish attache justement un whenComplete pour couvrir le cas 'la tache ne s'execute jamais (Folia, entite retiree, ordonnanceur arrete)'. Si cette tache-la ne tourne pas, la taxe a deja ete prelevee par applySellTaxAsync : la reservation reste a 1 et sera bien supprimee (pas de duplication), mais la taxe n'est ni remboursee ni journalisee, et l'appelant ne voit qu'un completeOnTimeout a 30 s. La garde du meme risque a ete appliquee au chemin de compensation et oubliee sur le chemin nominal.

### `C-034` (APPLIQUE) — `CommandAuctionAdminMigrate.java`

La sentinelle est ecrite en fin de chaine (CommandAuctionAdminMigrate:174, markMigrated dans le thenAccept) et non en tete de migrate() comme le demandait le correctif. La fenetre non couverte n'est donc pas "la meme seconde" comme l'affirme le commentaire l.93-95, mais TOUTE la duree de la migration : deux noeuds qui demarrent a quelques minutes d'intervalle sur la meme base MySQL passent tous deux la garde et importent l'integralite des items et de l'argent PENDING en double. C'est le scenario T4 de C-087, dont le verrou cluster n'a pas ete pose. Le rejeu SEQUENTIEL (la chronologie propre de C-034, T3) est bien ferme.

### `C-035` (PARTIEL) — `PurchaseService.java`

Introduit par la correction elle-meme : PurchaseService diffuse desormais un statut TERMINAL (PURCHASED / REMOVED / DELETED via resolveTerminalStatus, :407-416) a des noeuds dont ItemStatusListener ne sait pas le faire converger. ItemStatusListener.java:147-156 ne declenche de rafraichissement que pour les statuts IS_* et AVAILABLE ; pour PURCHASED/REMOVED/DELETED il execute `item.setStatus(newStatus)` et sort, sans removeItem, sans clearPlayersCache et sans updateListedItems. Or ZAuctionManager.invalidateListedCaches (:351-355) n'est appele que depuis addItem/removeItem : un setStatus en place n'invalide pas SortedItemsCache. L'annonce reste donc dans le store LISTED et dans le cache trie du noeud receveur, encore affichee et cliquable, jusqu'a ce qu'un ItemBoughtMessage ou ItemRemovedMessage arrive — ce qui n'existe pas dans le sous-cas LookupState.GONE (le noeud gagnant a commit puis disparu). Avant ce tour, aucun statut terminal n'atteignait ce listener ; la portee reste limitee (fantome affiche, achat toujours bloque par le verrou et la revalidation sous verrou), mais le chemin est neuf.

### `C-038` (APPLIQUE) — `ZAuctionItem.java`

Pas de regression, mais un residu a connaitre. Les trois volets sont la : (a) source tarie — ItemLoaderUtils.java:80-85 met en quarantaine des qu'un Base64ItemStack.decode rend null (et decode rend bien null, Base64ItemStack.java:94-116/153-177), donc plus aucun ZAuctionItem publie ne porte de null ; (b) NPE d'achat fermees — getItemsAsString l.163 ET getItemDisplay l.122 filtrent, comme ItemContentButton.java:47 (« .filter(Objects::nonNull).map(ItemStack::clone) ») ; (c) retrait destructeur — ZAuctionManager.giveItem l.1382-1397 ignore et journalise les null, clone chaque stack, capture les Throwable PAR STACK sans casser la boucle, et rend un GiveResult ; les SIX sites listes par la contre-expertise passent tous par deliverOrRestore (l.721, 764, 806, 847, 944, 1263). RESIDU : deliverOrRestore ne compense que sur « isNothingDelivered() » (delivered == 0, l.1338-1341). Un lot de 3 dont le stack #2 fait echouer addItem est donc commis DELETED avec 2 stacks remis sur 3, le troisieme perdu contre un simple log SEVERE. C'est le prix assume de l'ordre ecriture-puis-remise (il ferme la duplication), mais la perte partielle du constat d'origine reste possible sur ce chemin etroit.

### `C-040` (APPLIQUE) — `V3MigrationProvider.java`

Aucune. Les deux chemins sans exception identifies par la contre-expertise (V3MigrationService:136 "Failed to connect to V3 data source" et :146 "No data found to migrate") produisent desormais MigrationResult.failure, et CommandAuctionAdminMigrate:193 affiche MIGRATION_FAILED avec le vrai message. La branche `else` de l'appelant, jusque-la morte, est reellement atteignable. Reserve mineure : le volet 4 du correctif de C-033 ("faire echouer la commande des que errors > 0") n'est PAS applique — un import avec 100 % d'erreurs partielles affiche toujours MIGRATION_SUCCESS avec %errors% renseigne ; l'avertissement console l.143 est le seul garde-fou contre la suppression de la source V3.

### `C-041` (APPLIQUE) — `ZAuctionManager.java`

Le defaut est ferme par une double barriere, et notamment sur la ligne que la contre-expertise avait designee comme le vrai declencheur (le solde du VENDEUR, aujourd'hui l.1174) : readBalanceOrZero attrape l'exception du provider et rend ZERO, et chacun des deux blocs comptables est de surcroit enveloppe dans son propre try/catch (l.1164-1171 et l.1173-1181). Plus aucune exception d'economie ne peut interrompre la sequence avant `storageManager.updateItem(auctionItem, StorageType.LISTED, destination)` l.1214 : l'item transitionne, il ne reste plus LISTED.

Deux points du correctif restent non appliques sans rouvrir le constat : (1) `get()` est toujours un faux async — ZAuctionEconomy.java:172 `return CompletableFuture.completedFuture(this.currencyProvider.getBalance(playerId));` — mais le seul appelant en aval du mouvement d'argent est readBalanceOrZero, qui l'attrape ; l'autre appelant, `has()` via PurchaseService.java:211, est dans un lambda de thenCompose (donc un throw synchrone y est capte par le future) et se situe de toute facon AVANT tout debit. (2) le point 3 (crediter le vendeur seulement apres le succes de l'UPDATE) n'est pas applique et est assume en commentaire l.1191-1194 : si `updateItem` echoue, l'acheteur est debite sans contrepartie et l'item reste LISTED — meme etat final que C-041 mais par un declencheur base, pas par le provider d'economie.

### `C-043` (APPLIQUE) — `ItemLoaderUtils.java`

Le chemin admin n'a PAS ete migre : CommandAuctionAdminAdd:103/147/187 appelle encore ZStorageManager.createAuctionItem (l.209-223), soit la sequence 1+N INSERT hors transaction avec la ligne items creee directement en LISTED. Un echec du contenu y laisse toujours une ligne items orpheline LISTED, definitive (aucun DELETE FROM items n'existe pour elle). Elle n'est plus achetable grace a la quarantaine, mais elle pollue la base a jamais et re-declenche un log SEVERE a chaque demarrage de chaque noeud.

### `C-046` (APPLIQUE) — `RemoveService.java`

Aucune trouvee. La garde est une lecture O(1) sur le store indexe (ZAuctionManager:270) et `onUnavailable.run()` redessine la GUI du joueur, donc un refus legitime ne laisse pas de slot fantome.

### `C-047` (APPLIQUE) — `ZAuctionHouseRedis.java`

Le point de reprise existe (RedisSubscriberRunnable.java:118 -> onTransportRecovered -> reconcileListedItems -> bridge.findSettledItemIds en pipeline par lots de 500), et la chronologie T1-T4 du constat est fermee deux fois : purge du fantome LISTED, et refus en aval (LOCK_SCRIPT `if listedScope == '1' and (currentState == 'SOLD' or currentState == 'REMOVED') then return -1`). Trois limites subsistent, dont deux sont documentees dans le code et une ne l'est pas : (a) la reconciliation ne PURGE que ; elle ne re-ajoute jamais les annonces creees ailleurs pendant la coupure, ni ne replace en EXPIRED/PURCHASED les items qui y ont migre — le noeud reste aveugle a ces items jusqu'au redemarrage ; (b) un item dont la cle d'etat a expire (item-state-ttl-seconds, 24 h) rend null et est CONSERVE en LISTED ; (c) non documentee : la reconciliation ne couvre pas le premier demarrage (cf. C-031), alors que c'est la fenetre la plus large.

### `C-048` (APPLIQUE) — `ListedItemsButton.java`

openConfirmation utilise `manager.getCache(player)` (l.171) et n'a AUCUN `player.isOnline()`. C'est pourtant un chemin de completion tardive : il est atteint depuis le `whenComplete` de `economy.has(...)` (l.251) puis re-planifie via `runAtEntity` (l.162-166). Si le joueur se deconnecte entre son clic et la completion, `onQuit` a deja execute `ConfirmHelper.releaseConfirmation` puis `removeCache`, et la tache differee (a) RESSUSCITE le cache via `computeIfAbsent` — exactement ce que le commentaire de `peekCache` (ZAuctionManager.java:600-606) interdit sur ces chemins — donc fuite d'une entree dans `this.caches`, et (b) repose `IS_PURCHASE_CONFIRM` sur l'item apres la liberation : l'annonce est soft-lockee sur tout le cluster jusqu'au balayage TTL (60 s + 10 s). Le correctif de l'audit prevoyait explicitement `if (!player.isOnline()) return;`, absent du code livre.

### `C-049` (APPLIQUE) — `ListedItemsButton.java`

Divergence inverse assumee et non signalee au joueur : le statut local est pose AVANT la diffusion, donc si `notifyItemStatusChange` echoue (l.199-204), seul un WARNING console est emis — l'item est masque sur ce noeud et reste visible sur B/C, sans aucun message au joueur (le correctif de l'audit prevoyait `Message.INVENTORY_ERROR`). Le filet de rattrapage est le TTL de ZMaintenanceScheduler, pas la boucle de clic.

### `C-050` (PARTIEL) — `ItemStatusListener.java`

Le durcissement des autres constats a ajoute du cout O(n) sur le thread principal : ItemBoughtListener:61 et ItemRemovedListener:58 appellent desormais ItemLookup.find(...), soit une copie complete de LISTED de plus par message d'achat et par message de retrait, en plus de celles du listener de statut.

### `C-052` (APPLIQUE) — `RedisConnectionFactory.java`

Les trois volets sont verifies dans le code, pas seulement annonces. (1) maxWait borne. (2) Le double emprunt imbrique a disparu de TOUS les chemins : `loadScriptsOn(Jedis)` (l.300-313) n'emprunte rien et remplace `loadScripts()` aux trois branches NOSCRIPT (evalLock l.473, releaseLock l.513, renewLock l.556) ; `reloadScriptsIfNeeded()` est appele AVANT tout `getResource()` (l.429 vs 442, l.494 vs 503, l.543 vs 547) ; et `sendMessage(T)` — l'ancien second emprunt sur le chemin chaud — est desormais du code mort, remplace partout par `publish(Jedis, T)` / `publishBestEffort(Jedis, T)`. (3) Executor dedie passe a tous les supplyAsync/runAsync du bridge, dimensionne a `Math.max(2, maxTotal / 2)` (ZAuctionHouseRedis.java:228-235), donc jamais plus d'emprunteurs que de connexions meme en comptant le thread abonne et le heartbeat. Consequence a assumer : un pool sature ne fige plus mais leve, ce qui alimente la regression signalee sur C-028.

### `C-054` (APPLIQUE) — `ItemStackUtils.java`

Le cycle est bien coupe dans les deux sens : decode() n'atteint safeDeserializeItemStack que via la branche MARKER_NBT (Base64ItemStack.java:106) ou la branche legacy (:113), et safeDeserializeItemStack ne rappelle plus decode(). Le catch a aussi ete elargi de Exception a Throwable, ce qui capture l'ArrayIndexOutOfBoundsException de getClassz() (nmsPackage.split(",")[3], toujours present ligne 121). Un seul point du correctif non tenu, sans effet sur ce constat : le garde-fou demande autour de loadItems() est un catch (RuntimeException) et non un catch (Throwable) — ZAuctionPlugin.java:227-229 "try { this.storageManager.loadItems(); } catch (RuntimeException exception) {". Une Error y echapperait encore ; mais la recursion qui en produisait une ne peut plus survenir, et ItemLoaderUtils.createAuctionItem:69-77 entoure deja chaque decodage d'un catch (Throwable) par ligne.

### `C-055` (PARTIEL) — `SearchService.java`

Aucune regression fonctionnelle. Un point de vigilance : getItem(StorageType, int) est declaree `default` sur l'interface publiee avec une implementation de repli en balayage LINEAIRE (AuctionManager.java:106-111, `for (Item item : getItems(storageType))` sur une ArrayList recopiee). Si SearchService etait un jour appele avec une implementation tierce d'AuctionManager, la boucle passerait de O(n) a O(n^2) avec une copie complete du store PAR ITEM - c'est-a-dire strictement pire que le defaut d'origine. ZAuctionManager surcharge bien la methode, donc l'exposition est aujourd'hui nulle, mais le repli est un piege.

### `C-056` (PARTIEL) — `SortedItemsCache.java`

La fenetre de 250 ms est desormais posee sur le chemin joueur. ZAuctionManager.java:148 `boolean globalCacheReady = !sortedItemsCache.isDirty();` : sous charge cluster isDirty() est vrai en permanence (comportement voulu du compteur de generation), donc CHAQUE ouverture de l'hotel des ventes passe par prepareCacheAsync -> ensureCacheValidAsync -> requestRebuild, qui — contrairement a triggerRebuildIfNeeded — ne renonce pas mais PLANIFIE : `this.plugin.getScheduler().runLaterAsync(() -> runRebuildTask(future), delay, TimeUnit.MILLISECONDS)` avec delay pouvant valoir 250 ms. L'inventaire ne s'ouvre donc qu'apres 250 ms d'attente + la duree du tri, et le meme delai s'ajoute au rafraichissement de updateListedItems (l.1522). Le cout CPU est bien divise, mais le symptome « affichage systematiquement en retard sur l'etat reel du cluster » decrit en T6 de la fiche est aggrave en temps mur, pas reduit.

### `C-057` (APPLIQUE) — `Utils.java`

Les quatre volets sont la et, surtout, les CONSOMMATEURS ont ete alignes — c'est ce qui rend le correctif reel et non cosmetique. Un enum inconnu rend null au lieu de la String brute (l.137), toutes les autres conversions font de meme (BigDecimal l.144, UUID l.151, Integer l.160, Double, Long, Boolean, Float), le garde primitif couvre le cas `int` que `Number.class.isAssignableFrom(int.class)` ratait (l.78-80 + primitiveDefault l.99-109), et le constructeur canonique est resolu par `getRecordComponents()` au lieu de `getConstructors()[0]` (RedisSubscriberRunnable.java:237-244). Cote listeners, le champ null est reellement traite : ItemStatusListener.java:81-87 refuse d'appliquer un statut null au lieu de le poser, et ItemRemovedListener.java:40-43 + 118-137 retombe sur l'etat autoritaire en base avec un warning. L'itemId n'est donc plus jamais perdu avec le reste du message.

### `C-058` (APPLIQUE) — `RedisAuctionClusterBridge.java`

Doctrine d'echec ferme assumee mais a signaler : sur un Redis qui interdit EVAL/SCRIPT (offres managees), plus AUCUN achat ni retrait n'aboutit sur tout le reseau, la seule trace etant un SEVERE par tentative. Le point 3 de la fiche (echouer onEnable) n'a pas ete retenu, donc le noeud demarre et sert des refus silencieux cote joueur. Detail mineur : loadScriptsOn() ecrit lui aussi lastScriptLoadAttempt (l.301), un NOSCRIPT ordinaire repousse donc de 5 s la fenetre de rechargement periodique.

### `C-061` (APPLIQUE) — `RemoveService.java`

Pas de regression. Le type de retour d'unlockItem est volontairement reste CompletableFuture<Void> (compat binaire) et le canal a ete ajoute en methode default releaseLock (AuctionClusterBridge.java:171-173). Les quatre sites nommes par la fiche consomment bien le verdict : RemoveService:390, PurchaseService:242 et :260 via warnIfLockLost, ExpireService:389, plus ZAuctionManager:988 (retrait admin). Deux reserves a connaitre, aucune ne rouvre le scenario decrit : (1) unlockItem est encore appele en direct sur les chemins COMPENSATOIRES (RemoveService:269 et :473, PurchaseService:127 et :284) et y jette le verdict — mais ces chemins rapportent deja un echec au joueur, ce n'est pas le "retrait declare reussi" du constat ; (2) le default releaseLock de l'interface rend Boolean.TRUE en aveugle ("thenApply(ignored -> Boolean.TRUE)"), donc un bridge tiers compile contre l'ancienne API reste silencieux — c'est le prix assume de la compat binaire, documente dans le javadoc.

### `C-062` (APPLIQUE) — `CommandAuctionAdminAdd.java`

Aucune regression fonctionnelle. Une seule remarque de coherence : addPurchased continue de creer la ligne avec l'ADMIN comme vendeur (`createAuctionItem(admin, ...)`, l.187) alors que addExpired utilise la cible. C'est le comportement d'origine, non signale par la fiche, mais il fait apparaitre l'annonce dans l'historique de vente de l'admin.

### `C-063` (APPLIQUE) — `CommandAuctionAdminAdd.java`

Le sous-cas (a) de la contre-expertise — echec PARTIEL, ItemRepository.create commite puis AuctionItemRepository.create echoue — devient une fenetre de DUPLICATION theorique qui n'existait pas avant : l'admin recupere maintenant son item (restoreToAdmin) ALORS QUE la ligne parente `items` est deja committee en LISTED. J'ai verifie que la duplication n'aboutit pas : ItemLoaderUtils.createAuctionItem:59-64 met la ligne en QUARANTAINE au rechargement (`has NO content row [...] it will NOT be published`), l'annonce orpheline n'est donc jamais publiee ni achetable. Il reste une ligne morte en base, sans consequence de jeu. Second point, plus reel : dans addExpired/addPurchased, si le `updateItem(item, LISTED, EXPIRED|PURCHASED)` (l.172, 206) echoue, l'exceptionally se contente de journaliser — la memoire locale dit EXPIRED/PURCHASED, la base dit LISTED. Au redemarrage l'item revient en vente publique au nom de la cible. Ce n'est pas une dupe (RemoveService passe par un compare-and-set EXPIRED->DELETED qui echouerait, RemoveService.java:151), mais l'item change de nature sans que personne ne soit averti.

### `C-064` (PARTIEL) — `CommandAuctionAdminGenerate.java`

Aucune regression introduite. A noter que le point 3 est desormais MOINS atteignable par `/ah admin add` qu'avant, puisque notifyItemListed (bridge l.622 `jedis.expire(key, this.itemListedTtl.toSeconds())`) et removeItem (l.674 `jedis.expire(itemKey, this.terminalStateTtl.toSeconds())`) posent tous deux un TTL : la fuite se concentre maintenant sur les items generes et sur le cycle verrou-puis-relache.

### `C-065` (APPLIQUE) — `CommandAuctionAdminCacheClear.java`

Un effet secondaire assume : la commande de diagnostic ne peut plus, par « all », purger un ITEM_SHOW reellement corrompu — il faut passer par la cle explicite. C'est le comportement voulu, mais l'auto-completion ne propose plus ITEM_SHOW (l.62-64), donc l'admin ne decouvrira ce recours que s'il connait le nom de la cle. Second detail sans consequence : sur la voie joker, `releaseConfirmation` est invoquee pour CHAQUE joueur en ligne et chaque liberation reelle declenche une publication Redis ; sur un gros serveur un seul `/ah admin cache clear * ITEM_SHOW` peut donc emettre plusieurs centaines de messages sur le bus.

### `C-068` (APPLIQUE) — `ConfirmHelper.java`

Aucune sur le defaut lui-meme. La garde `!= this.previous` couvre bien la VRAIE fenetre identifiee par la contre-expertise (Retour clique apres PurchaseService:153 / RemoveService, quand le statut vaut IS_BEING_PURCHASED / IS_BEING_REMOVED) : la retro-transition vers AVAILABLE est impossible. Le point C) de la contre-expertise (oldStatus mensonger dans le message diffuse) est ferme par construction, puisque la diffusion l.75 n'est atteinte que si le statut vaut exactement `this.previous`.

### `C-069` (APPLIQUE) — `ConfirmHelper.java`

Deux points. (1) La fenetre ANTERIEURE au verrou reste ouverte et devient DURABLE a cause d'un autre correctif : l'inventaire de confirmation n'est toujours pas ferme au clic Confirmer (onClick l.86-106 ne ferme rien, la recommandation « fermer/verrouiller des le clic Confirmer » n'est pas appliquee), donc un Retour clique entre Confirmer et PurchaseService.java:153 passe la garde (statut encore IS_PURCHASE_CONFIRM) et diffuse AVAILABLE. Avant, PurchaseService:154 `notifyItemStatusChange(item, previousStatusHolder.get(), IS_BEING_PURCHASED)` reparait la desynchronisation en un aller-retour ; desormais le CAS ajoute cote addon (ItemStatusListener.java:95-100 : `if (oldStatus != null && item.getStatus() != oldStatus) { ... return; }`) REJETTE ce message, puisque B et C sont deja passes a AVAILABLE alors que le message annonce oldStatus=IS_PURCHASE_CONFIRM. L'item reste donc affiche AVAILABLE sur B et C pendant TOUTE la transaction, jusqu'a ce que ItemBoughtListener le retire. C'est la consequence n°1 de la contre-expertise, rendue persistante par l'interaction des deux correctifs. (2) `releaseConfirmationState` ne purge pas ITEM_SHOW, contrairement au `restoreAvailable` propose — voir la regression signalee sur C-023, dont c'est la cause directe.

### `C-072` (APPLIQUE) — `ZMaintenanceScheduler.java`

Effet de bord borne : le rearmement d'un pair est DIFFUSE (l.155 `notifyItemStatusChange(item, status, target)`) pour les items en LISTED. Un joueur qui laisse sa GUI de confirmation ouverte plus de 60 s voit donc son annonce redevenir achetable par un tiers sous ses yeux, sur son propre serveur, via le CAS du listener de statut (local IS_PURCHASE_CONFIRM == oldStatus recu -> applique). Pas de double vente : PurchaseService pose le verrou distribue et relit la ligne autoritaire ; et ConfirmHelper ne rediffusera rien a la fermeture, sa garde `item.getStatus() == this.previous` ne correspondant plus.

### `C-075` (APPLIQUE) — `V3MigrationService.java`

Pas de nouveau defaut, mais deux residus a connaitre. (1) Le correctif retenu est le volet 2 (compensation applicative) et non le volet 1 (DatabaseConnection.beginTransaction()) : `deleteOrphanItem` s'execute avec la MEME connexion qui vient d'echouer. Sur le declencheur le plus probable (coupure Hikari, deadlock InnoDB), le DELETE echoue aussi et l'annonce ampute reste VENDABLE au prix plein — le code le reconnait et loggue "MANUAL ACTION REQUIRED" avec l'id V4, ce qui rend le residu tracable mais pas ferme. (2) Sarah n'emet aucun PRAGMA foreign_keys (verifie : zero occurrence dans Sarah/src), donc sous SQLite l'ON DELETE CASCADE invoque en commentaire l.312-314 ne se declenche pas : les lignes auction_items deja inserees survivent en orphelines. Inoffensif (le parent disparait, ItemLoaderUtils ne les joint jamais) mais la justification du commentaire est fausse pour le backend par defaut. A noter que la quarantaine d'ItemLoaderUtils.createAuctionItem ne couvre que le lot VIDE et le stack indecodable, pas le lot ampute 2-sur-5 : la compensation reste la seule barriere sur ce cas.

### `C-077` (APPLIQUE) — `RedisAuctionClusterBridge.java`

Le constructeur historique @Deprecated (l.235) fabrique un executor via createFallbackExecutor() que personne ne fermera : un addon tiers compile contre l'ancienne signature fuit un pool de threads a chaque rechargement. Le javadoc le dit, mais rien ne l'empeche. Par ailleurs le dimensionnement de repli (max-total/2) ne s'applique PAS au chemin nominal : ZAuctionHouseRedis.java:231 cree son propre pool, il faut verifier separement qu'il laisse des connexions au thread abonne et au heartbeat.

### `C-079` (PARTIEL) — `PurchaseService.java`

Aucune regression, mais un effet de bord du garde-fou unlockIssued : sur ce chemin de timeout, le CAS l.241 n'a pas encore ete pris, si bien que c'est le .exceptionally l.283 qui emet l'unlock, et il utilise unlockItem(...) et non releaseLock(...) — le verdict booleen « verrou vole » y est jete au lieu d'etre journalise par warnIfLockLost. Sans consequence en Redis (unlockItem delegue a releaseLock, qui journalise deja en SEVERE cote addon).

### `C-080` (PARTIEL) — `SellService.java`

La branche UNKNOWN de commitReservation est du code neuf introduit par cette correction, et c'est la seule sortie de sellAuctionItems qui laisse le lot hors de l'inventaire SANS forcer la persistance du profil, alors que la ligne peut deja valoir pending_publish=2. Elle rouvre donc, dans le seul cas ou l'etat est incertain, exactement la fenetre 'playerdata restaure / ligne reclamable' que saveProfileIfNeeded a ete ajoute pour fermer trois lignes plus bas.

### `C-081` (APPLIQUE) — `CommandAuctionAdminGenerate.java`

Deux effets de bord mineurs, aucun ne remet le thread principal en jeu. (a) `playerRepository.select()` charge desormais TOUTE la table players en memoire d'un coup — c'est exactement la nuance que la contre-expertise signalait sur le point 2 du correctif ; c'est asynchrone, mais sur un reseau a plusieurs centaines de milliers de joueurs c'est un pic memoire notable. (b) `getValidMaterials()` (l.72) reste sur le thread principal et s'execute a CHAQUE frappe de la commande, y compris la premiere non confirmee : ~1500 iterations avec un `categoryManager.getCategoryFor(new ItemStack(material))` chacune. C'est sans commune mesure avec les 27 000 SELECT d'origine, mais ce travail n'avait aucune raison de rester en avant de la garde de confirmation.

### `C-084` (APPLIQUE) — `ZAuctionEconomy.java`

Fenetre de blocage nouvelle, bornee : pour les economies ITEM/ZMENUITEMS/LEVEL/EXPERIENCE (ownerThreadRequired), executeConfined fait scheduler.runAtEntity(player, ...).get(MUTATION_TIMEOUT_SECONDS). Sur Folia, un appel emis depuis le thread de region A pour un joueur de la region B immobilise le thread A jusqu'a 5 s. Le chemin nominal reste sain (branche inline quand isOwnedByCurrentRegion, ou Vault via le stripe synchronise), mais un pic de contention sur ces economies peut geler une region entiere pendant l'attente.

### `C-085` (APPLIQUE) — `CommandAuctionAdminGenerate.java`

Deux effets a connaitre, tous deux benins. (a) La comparaison est SENSIBLE A LA CASSE (`HashMap.containsKey` sur `dto.name()`) : un joueur reel « alexpro » ne bloquera pas le candidat « AlexPro ». Ce n'est pas un trou du constat — le sens de l'erreur est protecteur : on cree un faux compte de plus au lieu d'adopter le vrai. (b) Les faux vendeurs sont ecrits en base (`playerRepository.upsertPlayer`, l.221) et se retrouvent donc dans `knownPlayers` a la generation suivante. Sur un espace de ~57 500 noms, une seconde grosse generation verra beaucoup de collisions, epuisera ses 8 tentatives et produira SILENCIEUSEMENT moins d'items que demande : ADMIN_GENERATE_ITEMS_START annonce `totalAmount` (l.242) alors que seuls `dataList.size()` items sont crees. Le message final utilise bien `created.get()`, l'ecart est donc visible a la fin, mais jamais explique. La suggestion de la fiche de prefixer les pseudos generes (« TEST_ ») n'a pas ete retenue : les annonces de test restent indiscernables des vraies, et aucun `generate clear` n'existe.

### `C-086` (APPLIQUE) — `RedisSubscriberRunnable.java`

Le correctif va plus loin que celui propose et corrige une erreur de l'audit d'origine : `Thread.interrupt()` ne debloque PAS `jedis.subscribe(...)` (lecture socket non interruptible), seule la fermeture de la connexion le fait — c'est ce que fait `subscriberConnection.disconnect()`, publie en volatile (l.54). Les trois volets attendus sont aussi la : champ `jedisPubSub` volatile (l.48), interrupt en filet apres le join(5000) (ZAuctionHouseRedis.java:279-281) et backoff decoupe en tranches de 250 ms retestant `running` (l.144-155). Fenetre residuelle purement theorique : entre le retour de `getResource()` et l'affectation de `subscriberConnection` (l.105), quelques microsecondes pendant lesquelles shutdown() ne trouve ni pubsub ni connexion — le thread etant daemon et la boucle retestant `running`, l'impact est nul.

### `C-089` (PARTIEL) — `ItemListedMessage.java`

Aggravation legere du volume de messages a traiter : ItemRemovedListener publie desormais deux clearPlayersCache distincts selon la branche et appelle ItemLookup.find (O(n)) avant chaque retrait, ce qui ajoute du cout par message sans reduire le nombre de requetes SQL.

### `C-090` (APPLIQUE) — `ItemLoaderUtils.java`

Aucune. La methode getAuctionItems a bien disparu (« grep -rn "getAuctionItems(" » ne rend AUCUNE occurrence dans les deux depots), et les DEUX chemins chauds cites par la fiche beneficient du regroupement puisqu'ils passent par le meme createItems : AuctionLoader.java:61 (boot) et ZStorageManager.java:421 (selectItems, resolution de lots cluster). L'ordre des ItemStack d'un lot est preserve (groupingBy conserve l'ordre de rencontre, et la source est la meme liste ordonnee), ce qui compte pour getItemDisplay et pour l'ordre de remise dans giveItem. Le COMPLEMENT SQL n'a pas ete livre : il n'existe aucune migration d'index sur auction_items(item_id) (le repertoire storage/migrations/ contient CreateItemsStorageTypeIndexMigration, CreateLogsCreatedAtIndexMigration, CreatePlayersNameIndexMigration, CreateTransactionsPlayerIndexMigration — pas d'equivalent pour AUCTION_ITEMS), alors que CreateAuctionItemMigration ne declare que la cle etrangere, qui ne garantit un index que sous InnoDB et pas sous SQLite. Le cout quadratique CPU, qui est l'objet du constat, est bien supprime ; il reste le cout SQL du WHERE item_id IN (...) sans index.

### `C-091` (APPLIQUE) — `ExpireService.java`

Les deux volets sont fermes et je n ai pas trouve de contournement. Volet 1 : la garde exige `dbItem.getStatus() == ItemStatus.AVAILABLE`, et ItemLoaderUtils:91-94 mappe LISTED->AVAILABLE / EXPIRED->REMOVED / PURCHASED->PURCHASED, donc tester le statut equivaut bien a tester storage_type='LISTED' — le T4 du scenario (ligne deja EXPIRED, buyer null, garde aveugle) ne passe plus. Volet 2 : « 0 ligne modifiee » n est plus un succes — ZStorageManager:299-303 `int rows = with(ItemRepository.class).updateItem(item, from, to); if (rows == 0) throw new StaleItemException(item.getId(), from, to);`, l exception est consommee ici et le deplacement memoire (l.458-463) n a lieu que dans la branche `t == null`, donc plus de fantome EXPIRED memoire ni de second ItemRemovedMessage parasite. Nuance non bloquante : la distinction C-109 introduit un nouvel etat — `lookup.state() == LookupState.UNAVAILABLE` (l.359-363) ajourne l expiration sans purger. Une economie retiree d economies.yml gele donc l expiration de ces annonces indefiniment, en boucle a chaque balayage (60 s), avec un warning par item et par passage. C est deliberé et documente, mais c est un cout recurrent, pas un cas terminal.

### `C-092` (APPLIQUE) — `ItemRemovedListener.java`

ItemLookup.find (utils/ItemLookup.java:27) est un balayage O(n) avec copie complete du store LISTED, execute sur le thread principal a chaque message d'achat et de retrait. C'est le prix du correctif et il aggrave directement C-050, qui reste ouvert.

### `C-093` (APPLIQUE) — `ItemListedListener.java`

Aucune propre a ce constat. La garde l.55 est en revanche le premier des deux filtres dont la garde de generation l.68 constitue la regression decrite sous C-021.

### `C-098` (APPLIQUE) — `GlobalPlaceholders.java`

Deux effets mineurs, aucun bloquant. (1) Derive de fraicheur : le compteur ne reflete plus l'etat instantane du store mais le dernier instantane PUBLIE du cache trie. Comme C-101 rend desormais `dirty` collant sous charge cluster, cette fenetre de retard s'allonge precisement quand le trafic est fort. L'audit assume ce compromis ("consistant avec la GUI"), mais l'invariant de recette de CORRECTIFS-PRETS.md:12412 - %listed_items% doit egaler le COUNT SQL - n'est verifiable que serveur au repos. (2) getTotalCount appelle triggerRebuildIfNeeded() : le placeholder, resolu sur le main thread a chaque updateInventory (il est livre dans inventories/auction.yml), participe maintenant a l'amorcage des reconstructions. L'appel reste non bloquant (atomiques + planification async).

### `C-099` (APPLIQUE) — `RedisAuctionClusterBridge.java`

Le deverrouillage degrade a ete supprime lui aussi (l.495-500) : quand les scripts manquent, releaseLock rend FALSE et LAISSE le verrou expirer par TTL. L'item est donc invendable sur tout le reseau pendant lock-ttl-seconds (30 s par defaut) apres chaque operation menee pendant l'indisponibilite — comportement voulu et documente, mais c'est un blocage temporaire nouveau. A noter aussi : releaseAllLocks (l.747) retombe sur `jedis.eval(UNLOCK_SCRIPT, ...)` quand scriptsLoaded est faux — c'est bien atomique, la doctrine tient, mais cela prouve qu'EVAL reste disponible dans ce cas, donc le refus d'acquisition aurait pu utiliser la meme voie plutot que d'echouer.

### `C-102` (APPLIQUE) — `SellService.java`

La taxe est desormais prelevee AVANT la reservation, et le `.exceptionally` terminal (l.189-193) ne rembourse rien : `this.plugin.getLogger().severe("Unable to check tax for sell: " + throwable.getMessage()); resultFuture.complete(SellResult.failure("Tax calculation error", SellFailReason.TAX_ERROR));`. Toute exception levee SYNCHRONEMENT dans le corps du `thenAccept` apres le retrait et avant la reservation est donc une perte seche pour le joueur. Cas concret et non theorique : `ZStorageManager.reserveAuctionItem` fait `CompletableFuture.supplyAsync(..., this.plugin.getExecutorService())`, qui propage une RejectedExecutionException a l'appelant des que l'executor est draine (ZAuctionPlugin.java:292 `asyncExecutor.shutdown()` — le garde `isShuttingDown` l.51 n'est lu qu'a l'entree, la fenetre reste ouverte). La taxe est retiree, l'annonce n'existe pas, rien n'est rembourse et le message ne mentionne que "Tax calculation error".

### `C-104` (APPLIQUE) — `ZCategoryManager.java`

Aucune regression, mais un CHANGEMENT DE VALEUR AFFICHEE a signaler. Le passage par getCategory() corrige au passage le bug de casse : avant, `item.hasCategory(categoryIdMinuscule)` etait compare a getId() (brut), donc toute categorie declaree avec une majuscule dans categories.yml affichait systematiquement 0. Apres correctif elle affiche le vrai compte. Un serveur en production verra un compteur passer de 0 a une valeur non nulle sans qu'aucune donnee n'ait bouge : cela merite une entree de changelog (CORRECTIFS-PRETS.md:12296 le note aussi). Reste par ailleurs l'appel mort ZAuctionManager.java:353 `this.plugin.getCategoryManager().invalidateCategoryCountCache();` dans invalidateListedCaches, desormais sans effet - inoffensif, mais trompeur a la lecture.

### `C-105` (APPLIQUE) — `ZPlayerCache.java`

Aucune. Le garde de type de set() tolerait deja null avant le lot (`if (value != null && !key.getRawType().isInstance(value))` est dans HEAD, pas dans le diff), donc l'argument "ConcurrentHashMap interdit null" du commentaire est juste et le comportement des filtres admin est inchange. Aucun risque d'interblocage : le supplier de getOrCompute est evalue hors moniteur, et clearPlayersCache ne detient jamais deux moniteurs ZPlayerCache a la fois.

### `C-106` (PARTIEL) — `RedisAuctionClusterBridge.java`

L'achat consomme aujourd'hui un emprunt de PLUS qu'a l'audit : isHeldBy (PurchaseService.java:225 -> bridge l.572) s'ajoute a checkAvailability, lockItem, notifyItemStatusChange, notifyItemBought et releaseLock, plus un evalsha de renouvellement toutes les lockTtl/3 pendant la section critique. La pression sur le pool que C-106 devait reduire a augmente.

### `C-108` (APPLIQUE) — `V3MigrationService.java`

Aucune regression fonctionnelle. Le correctif va plus loin que celui de l'audit et le fait bien : au lieu de sauter les entrees "Unknown" (ce qui aurait casse la FK items.buyer_unique_id -> players et mis les annonces en quarantaine), il saute les joueurs DEJA presents en V4, donc aucun upsertPlayer ne peut plus ecraser un pseudo issu de PlayerListener.onConnect. trackPlayer (l.222-231) ajoute la garantie complementaire : un vrai pseudo l'emporte sur le placeholder quel que soit l'ordre de parcours, la ou putIfAbsent figeait "Unknown" quand le joueur etait rencontre d'abord comme acheteur. Deux reserves d'exploitation, pas des defauts : (1) playerRepo.select() est un selectAllOrFail — l'annuaire ENTIER est materialise en heap, sur un gros reseau c'est un pic memoire pendant la migration, et un echec SQL fait echouer toute la migration (propage jusqu'au catch de migrate() l.183) au lieu d'etre compte en erreur ; (2) un joueur deja connu en V4 mais dont le pseudo V3 serait plus recent ne sera jamais rafraichi — comportement voulu et correct ici.

### `C-110` (APPLIQUE) — `LocalAuctionClusterBridge.java`

Aucune. Le volet symetrique de la fiche (unlockItem doit verifier l'appartenance) est present : LocalAuctionClusterBridge.java:61-66 release() ne retire que si la valeur du jeton correspond exactement. ClusterUnavailableBridge (addon Redis, lockItem:49) rend toujours un failedFuture, mais c'est precisement le cas "panne de transport" que le contrat reserve a l'erreur — conforme. Nuance de comportement subsistante, sans consequence : en mono-serveur une contention est detectee des checkAvailability (LocalAuctionClusterBridge.java:26 renvoie !containsKey), donc l'appelant remonte ITEM_NOT_AVAILABLE la ou le cluster remonterait LOCK_FAILED ; les deux sont non bloquants pour le retrait de masse depuis ZAuctionManager:1720, et onUnavailable ne fait qu'un updateInventory (RemoveService:112).

### `C-111` (APPLIQUE) — `PurchaseService.java`

Aucune. Le copy() est bien indispensable et present (orTimeout rend `this`, il aurait complete le futur SOURCE et le whenComplete n'aurait jamais vu le jeton). La poignee de main croisee est correcte : whenComplete ecrit tokenHolder (l.122) PUIS lit chainAbandoned (l.123), tandis que le .exceptionally ecrit chainAbandoned (l.270) PUIS lit tokenHolder (l.282) — l'ordre ecriture-avant-lecture des deux cotes rend impossible que les deux manquent le jeton, et unlockIssued garantit qu'un seul unlock part. Residu deja signale par le constat lui-meme (T6) et non corrige : sur ce timeout, item.getStatus() vaut encore IS_PURCHASE_CONFIRM, donc le bloc l.291 ne restaure rien et ne rediffuse pas AVAILABLE — le retour a AVAILABLE depend alors entierement de ConfirmHelper.releaseConfirmationState / releaseConfirmation.

### `C-113` (APPLIQUE) — `ZAuctionHouseRedis.java`

Garde placee en TOUTE PREMIERE position de onEnable, avant `saveDefaultConfig`, avant `loadRedis` et donc avant toute ouverture de pool : la chronologie T3 du constat (`auctionPlugin.getScheduler().runTimerAsync` sur un plugin desactive -> IllegalPluginAccessException) n'est plus atteignable, et le T4 (pool + thread abonne laisses ouverts) non plus puisque rien n'est encore ouvert a ce point. Le second volet du correctif est couvert par construction : la seule sortie anticipee posterieure a l'ouverture du pool est `failClosed` depuis `loadRedis`, qui appelle `disablePlugin(this)` — Bukkit execute alors onDisable, dont les gardes `if (this.jedisPool != null)` et `if (this.subscriberThread != null ...)` liberent ce qui a ete ouvert.

### `C-115` (PARTIEL) — `CommandAuctionAdminGenerate.java`

La purge a la lecture (l.82) est le SEUL mecanisme d'expiration : elle ne s'execute que lorsque quelqu'un retape `/ah admin generate`. Si personne ne rejoue la commande, une entree perimee reste en memoire indefiniment — c'est desormais un simple record de 16 octets et non plus un CraftPlayer, donc l'impact est nul, mais la fenetre de confirmation de 30 s n'est pas fermee par une tache : elle est fermee au tour suivant, ce qui est correct puisque la comparaison `pending.amount() == amount` est evaluee APRES la purge.

### `C-116` (APPLIQUE) — `VersionChecker.java`

Les trois volets sont fermes et l'appelant est bien migre : ZAuctionHouseRedis.java:173 utilise la surcharge a 4 arguments `new VersionChecker(this, this.auctionPlugin, this.getLogger(), 210)`, l'addon est passe en premier. Le listener est enregistre sous l'addon (l.66), la version comparee est celle de l'addon (l.71), la comparaison est segment par segment avec absorption des suffixes non numeriques (l.101-125), et `unregister()` (l.87-89) est bien appele en premiere instruction de onDisable (ZAuctionHouseRedis.java:248-250). Le message d'annonce utilise lui aussi `this.addon.getDescription().getFullName()` (l.139). Le constructeur historique a 3 arguments est conserve mais resout l'addon par `JavaPlugin.getProvidingPlugin(VersionChecker.class)` : meme un appelant non migre ne peut plus enregistrer sur le plugin principal.

### `C-118` (APPLIQUE) — `ZAuctionPlugin.java`

Aucune regression trouvee sur ce constat. Observation mineure sans consequence : databaseConnection n'est jamais remis a null apres disconnect(), donc isSqliteStorage() (l.590) interroge un objet deconnecte quand l'addon Redis reecrit le bridge depuis SON onDisable (ZAuctionHouseRedis.java:258) apres que V4 a ferme sa base. getDatabaseConfiguration() n'est qu'une lecture de champ : pas d'exception, pas de comportement fausse.

### `C-119` (APPLIQUE) — `ClaimService.java`

Le volet monetaire est ferme (reservePending l.280 en tete, selectByClaimToken l.282, `continue` present sur exception l.309 et sur refus l.315, finishClaim/releaseClaim l.321-322 : les deux chemins de duplication ET de destruction du constat sont coupes). Reste non applique le point 5 du correctif, qui n'est PAS un chemin de duplication mais un trou d'auditabilite : `TransactionStatus` ne contient que PENDING et RETRIEVED (api/.../api/transaction/TransactionStatus.java), donc la branche `giveMoney == false` (l.265-275) marque RETRIEVED — dont le javadoc dit « the funds delivered to the player » — des lignes dont l'argent n'a jamais ete verse. AdminTransactionsButton.java:120 les affichera comme encaissees, indiscernables d'un vrai paiement. Second point mineur : clearPendingTransactions n'a ni la garde `plugin.isShuttingDown()` ni le jeu `claimingPlayers` de claimMoney ; sans consequence monetaire, la reservation en base restant l'arbitre.

---

## Verdict par constat

| Constat | Verdict | Re-contrôlé | Fichier |
|---|---|---|---|
| `C-001` | APPLIQUE |  | `Repository.java` |
| `C-002` | APPLIQUE |  | `RedisAuctionClusterBridge.java` |
| `C-003` | APPLIQUE | oui | `ClaimService.java` |
| `C-004` | APPLIQUE |  | `ItemStatusListener.java` |
| `C-005` | PARTIEL |  | `ZAuctionManager.java` |
| `C-006` | APPLIQUE |  | `PurchaseService.java` |
| `C-007` | APPLIQUE |  | `PurchaseService.java` |
| `C-008` | APPLIQUE |  | `LockToken.java` |
| `C-009` | APPLIQUE |  | `RemoveService.java` |
| `C-010` | APPLIQUE |  | `ZAuctionPlugin.java` |
| `C-011` | APPLIQUE |  | `ItemLoaderUtils.java` |
| `C-012` | APPLIQUE |  | `ZAuctionManager.java` |
| `C-013` | APPLIQUE |  | `ClaimService.java` |
| `C-014` | APPLIQUE |  | `ZAuctionManager.java` |
| `C-015` | PARTIEL |  | `ZAuctionEconomy.java` |
| `C-016` | APPLIQUE |  | `ZAuctionHouseRedis.java` |
| `C-017` | APPLIQUE |  | `PurchaseService.java` |
| `C-018` | APPLIQUE |  | `ZAuctionHouseRedis.java` |
| `C-019` | APPLIQUE |  | `ZAuctionManager.java` |
| `C-020` | APPLIQUE | oui | `ItemRepository.java` |
| `C-021` | APPLIQUE | oui | `ItemListedListener.java` |
| `C-022` | PARTIEL | oui | `ZAuctionPlugin.java` |
| `C-023` | APPLIQUE |  | `PlayerListener.java` |
| `C-024` | PARTIEL | oui | `ZAuctionHouseRedis.java` |
| `C-025` | PARTIEL |  | `RedisAuctionClusterBridge.java` |
| `C-026` | APPLIQUE |  | `ItemRepository.java` |
| `C-027` | PARTIEL |  | `ZStorageManager.java` |
| `C-028` | APPLIQUE |  | `ZAuctionHouseRedis.java` |
| `C-029` | PARTIEL |  | `Base64ItemStack.java` |
| `C-030` | APPLIQUE | oui | `SellService.java` |
| `C-031` | PARTIEL |  | `RedisSubscriberRunnable.java` |
| `C-032` | APPLIQUE |  | `ZAuctionManager.java` |
| `C-033` | APPLIQUE | oui | `LogRepository.java` |
| `C-034` | APPLIQUE |  | `CommandAuctionAdminMigrate.java` |
| `C-035` | PARTIEL | oui | `PurchaseService.java` |
| `C-036` | PARTIEL |  | `ZAuctionHouseRedis.java` |
| `C-037` | APPLIQUE | oui | `SellService.java` |
| `C-038` | APPLIQUE |  | `ZAuctionItem.java` |
| `C-039` | PARTIEL |  | `RemoveService.java` |
| `C-040` | APPLIQUE |  | `V3MigrationProvider.java` |
| `C-041` | APPLIQUE |  | `ZAuctionManager.java` |
| `C-042` | APPLIQUE |  | `ZAuctionManager.java` |
| `C-043` | APPLIQUE |  | `ItemLoaderUtils.java` |
| `C-044` | APPLIQUE | oui | `AuctionLoader.java` |
| `C-045` | APPLIQUE |  | `ZAuctionManager.java` |
| `C-046` | APPLIQUE |  | `RemoveService.java` |
| `C-047` | APPLIQUE |  | `ZAuctionHouseRedis.java` |
| `C-048` | APPLIQUE |  | `ListedItemsButton.java` |
| `C-049` | APPLIQUE |  | `ListedItemsButton.java` |
| `C-050` | PARTIEL |  | `ItemStatusListener.java` |
| `C-051` | ABSENT | oui | `SellService.java` |
| `C-052` | APPLIQUE |  | `RedisConnectionFactory.java` |
| `C-053` | APPLIQUE |  | `ZAuctionManager.java` |
| `C-054` | APPLIQUE |  | `ItemStackUtils.java` |
| `C-055` | PARTIEL |  | `SearchService.java` |
| `C-056` | PARTIEL | oui | `SortedItemsCache.java` |
| `C-057` | APPLIQUE |  | `Utils.java` |
| `C-058` | APPLIQUE |  | `RedisAuctionClusterBridge.java` |
| `C-059` | APPLIQUE |  | `ItemBoughtListener.java` |
| `C-060` | APPLIQUE |  | `ZAuctionManager.java` |
| `C-061` | APPLIQUE |  | `RemoveService.java` |
| `C-062` | APPLIQUE |  | `CommandAuctionAdminAdd.java` |
| `C-063` | APPLIQUE |  | `CommandAuctionAdminAdd.java` |
| `C-064` | PARTIEL |  | `CommandAuctionAdminGenerate.java` |
| `C-065` | APPLIQUE |  | `CommandAuctionAdminCacheClear.java` |
| `C-066` | PARTIEL |  | `ZStorageManager.java` |
| `C-067` | APPLIQUE |  | `ItemStackUtils.java` |
| `C-068` | APPLIQUE |  | `ConfirmHelper.java` |
| `C-069` | APPLIQUE |  | `ConfirmHelper.java` |
| `C-070` | PARTIEL |  | `ExpireService.java` |
| `C-071` | APPLIQUE | oui | `ItemRemovedListener.java` |
| `C-072` | APPLIQUE |  | `ZMaintenanceScheduler.java` |
| `C-073` | APPLIQUE |  | `LogRepository.java` |
| `C-074` | PARTIEL |  | `ListedItemsButton.java` |
| `C-075` | APPLIQUE |  | `V3MigrationService.java` |
| `C-076` | APPLIQUE | oui | `PurchaseService.java` |
| `C-077` | APPLIQUE |  | `RedisAuctionClusterBridge.java` |
| `C-078` | PARTIEL |  | `ExpireService.java` |
| `C-079` | PARTIEL |  | `PurchaseService.java` |
| `C-080` | PARTIEL | oui | `SellService.java` |
| `C-081` | APPLIQUE |  | `CommandAuctionAdminGenerate.java` |
| `C-082` | PARTIEL |  | `SellService.java` |
| `C-083` | ABSENT | oui | `ZAuctionManager.java` |
| `C-084` | APPLIQUE |  | `ZAuctionEconomy.java` |
| `C-085` | APPLIQUE |  | `CommandAuctionAdminGenerate.java` |
| `C-086` | APPLIQUE |  | `RedisSubscriberRunnable.java` |
| `C-087` | PARTIEL |  | `V3MigrationService.java` |
| `C-088` | APPLIQUE |  | `ZMaintenanceScheduler.java` |
| `C-089` | PARTIEL |  | `ItemListedMessage.java` |
| `C-090` | APPLIQUE |  | `ItemLoaderUtils.java` |
| `C-091` | APPLIQUE |  | `ExpireService.java` |
| `C-092` | APPLIQUE |  | `ItemRemovedListener.java` |
| `C-093` | APPLIQUE |  | `ItemListedListener.java` |
| `C-094` | APPLIQUE |  | `ItemRepository.java` |
| `C-095` | ABSENT | oui | `build.gradle.kts` |
| `C-096` | ABSENT | oui | `SellService.java` |
| `C-097` | PARTIEL |  | `SellShowItemButton.java` |
| `C-098` | APPLIQUE |  | `GlobalPlaceholders.java` |
| `C-099` | APPLIQUE |  | `RedisAuctionClusterBridge.java` |
| `C-100` | APPLIQUE |  | `AdminLogsButton.java` |
| `C-101` | APPLIQUE | oui | `SortedItemsCache.java` |
| `C-102` | APPLIQUE |  | `SellService.java` |
| `C-103` | ABSENT | oui | `ZAuctionManager.java` |
| `C-104` | APPLIQUE |  | `ZCategoryManager.java` |
| `C-105` | APPLIQUE |  | `ZPlayerCache.java` |
| `C-106` | PARTIEL |  | `RedisAuctionClusterBridge.java` |
| `C-107` | PARTIEL |  | `ZAuctionManager.java` |
| `C-108` | APPLIQUE |  | `V3MigrationService.java` |
| `C-109` | APPLIQUE |  | `ZStorageManager.java` |
| `C-110` | APPLIQUE |  | `LocalAuctionClusterBridge.java` |
| `C-111` | APPLIQUE |  | `PurchaseService.java` |
| `C-112` | APPLIQUE |  | `ZAuctionManager.java` |
| `C-113` | APPLIQUE |  | `ZAuctionHouseRedis.java` |
| `C-114` | PARTIEL |  | `ZItem.java` |
| `C-115` | PARTIEL |  | `CommandAuctionAdminGenerate.java` |
| `C-116` | APPLIQUE |  | `VersionChecker.java` |
| `C-117` | APPLIQUE |  | `ZAuctionPlugin.java` |
| `C-118` | APPLIQUE |  | `ZAuctionPlugin.java` |
| `C-119` | APPLIQUE |  | `ClaimService.java` |
