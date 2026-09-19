# Correctifs prêts à appliquer — zAuctionHouseV4 + addon Redis

> Recueil généré à partir de `AUDIT-CLUSTER.md` après vérification des 119 constats contre le code réel
> (`zAuctionHouseV4` @ `49571d9`, `zAuctionHouse Redis` @ `d951be2` — les deux révisions auditées).
> **120 étapes**, chacune de taille d'un commit, chacune laissant le projet compilable.
> L'ordre de livraison est dans `PLAN-CORRECTIONS.md` : ce fichier-ci est la matière, pas la séquence.

**Convention** : `LOT n` renvoie au lot de livraison du plan. `REDIS/` désigne le dépôt `zAuctionHouse Redis`.

---

## Index des étapes

| Chantier | # | Étape | Fichier | Constats | Lot | Risque |
|---|---|---|---|---|---|---|
| 1 | 1 | Repository : faire remonter le nombre de lignes modifiees | `Repository.java` | C-026 | 3 | LOW |
| 1 | 2 | StaleItemException : le vocabulaire de la course perdue | `StaleItemException.java` | C-026 | 3 | LOW |
| 1 | 3 | ClaimService : ne marquer RETRIEVED que ce qui a reellement ete verse | `ClaimService.java` | C-013 | 1 | LOW |
| 1 | 4 | Migration claim_token + primitives de reservation dans TransactionRepository | `CreateTransactionsMigration.java` | C-003 | 3 | MEDIUM |
| 1 | 5 | ClaimService : reserver d'abord, payer ensuite | `ClaimService.java` | C-003 | 3 | MEDIUM |
| 1 | 6 | clearPendingTransactions : meme primitive de reservation | `ClaimService.java` | C-119 | 3 | LOW |
| 1 | 7 | ItemRepository : compare-and-set universel et batch transactionnel | `ItemRepository.java` | C-026, C-094 | 3 | LOW |
| 1 | 8 | StorageManager : surcharges CAS non cassantes | `StorageManager.java` | C-026, C-094 | 3 | LOW |
| 1 | 9 | ExpireService : bascule sur le CAS, garde AVAILABLE et purge des perdants | `ExpireService.java` | C-091, C-078, C-094, C-026 | 3 | MEDIUM |
| 1 | 10 | PurchaseService : revalidation exhaustive sous verrou avant de facturer | `PurchaseService.java` | C-006 | 3 | LOW |
| 1 | 11 | Bascule des 8 derniers sites d'ecriture et convergence sur perte de course | `ZAuctionManager.java` | C-026, C-012, C-094 | 2, 3 | HIGH |
| 2 | 1 | Quarantaine des annonces illisibles au chargement + accesseurs null-safe (C-011 + C-038) | `ItemLoaderUtils.java` | C-011, C-038 | 4 | MEDIUM |
| 2 | 2 | giveItem chaînable et confiné au thread d'entité + inversion commit→remise sur les 4 chemi | `ZAuctionManager.java` | C-014, C-005, C-039, C-083 | 5 | HIGH |
| 2 | 3 | Achat : commit de la ligne avant toute mutation mémoire et avant la remise (C-005, site 88 | `ZAuctionManager.java` | C-005, C-014 | 5 | HIGH |
| 2 | 4 | Retrait admin : chaîner l'écriture avant la remise et cesser de jeter le future (C-005, si | `ZAuctionManager.java` | C-005 | 5 | MEDIUM |
| 2 | 5 | Retrait en masse : ne plus abandonner le lot sur une erreur interne et dire la vérité au j | `ZAuctionManager.java` | C-053 | 5 | LOW |
| 2 | 6 | Infrastructure de vente atomique : réservation, publication, annulation (C-020, C-043) | `ItemRepository.java` | C-020, C-043 | 6 | MEDIUM |
| 2 | 7 | Vente : réserver avant de retirer, publier après, rembourser sur le bon thread (C-020, C-0 | `SellService.java` | C-020, C-043, C-067, C-037, C-030, C-082 | 6 | HIGH |
| 2 | 8 | Forcer la sauvegarde du profil après le retrait des items du vendeur (C-080) | `config.yml` | C-080 | 6 | MEDIUM |
| 2 | 9 | Récupération au démarrage des réservations orphelines (complément C-020, optionnel) | `ItemRepository.java` | C-020, C-080 | 6 | LOW |
| 3 | 1 | TaxResult devient auto-descriptif (appliedType, buyerPays, sellerReceives) sans casser les | `TaxResult.java` | C-017, C-019 | 7 | MEDIUM |
| 3 | 2 | ZTaxConfiguration renseigne le type de taxe reellement applique | `ZTaxConfiguration.java` | C-019, C-017 | 7 | LOW |
| 3 | 3 | AuctionEconomy : ajouter withdrawChecked / depositChecked / supportsOfflineDeposit en meth | `AuctionEconomy.java` | C-015, C-102, C-032 | 7 | LOW |
| 3 | 4 | ZAuctionEconomy : pre-controle de solde sous verrou strippe par compte | `ZAuctionEconomy.java` | C-015, C-084, C-032 | 7 | MEDIUM |
| 3 | 5 | ZEconomyManager : forcer must-be-online pour les economies incapables de crediter hors lig | `ZEconomyManager.java` | C-032 | 7 | LOW |
| 3 | 6 | ZPurchaseCharge : calcul UNIQUE du montant d'achat, cable sur les deux chemins de VERIFICA | `ListedItemsButton.java` | C-017, C-019, C-015 | 7 | MEDIUM |
| 3 | 7 | ZAuctionManager : le PRELEVEMENT passe par le meme resolveur (fin de la frappe monetaire) | `ZAuctionManager.java` | C-019, C-017 | 7 | MEDIUM |
| 3 | 8 | ZAuctionManager : mouvements d'argent verifiables, deposit differe pour les economies hors | `ZAuctionManager.java` | C-015, C-032, C-041 | 7 | MEDIUM |
| 3 | 9 | SellService : taxe de vente atomique et remboursements verifiables | `SellService.java` | C-102 | 7 | MEDIUM |
| 3 | 10 | economies.yml x6 locales + changelog + documentation | `) et changelogs.md` | C-032, C-019 | 7 | LOW |
| 3 | 11 | [Addon Redis] Auto-claim du vendeur connecte sur un autre noeud, log marque lu APRES le cr | `ItemBoughtListener.java` | C-059 | 7 | HIGH |
| 4 | 1 | API — jeton de verrou unique par acquisition (socle de tout le chantier) | `LockToken.java` | C-008, C-025 | 1, 8 | LOW |
| 4 | 2 | API — extensions default du contrat AuctionClusterBridge (verdict de libération, bail, por | `AuctionClusterBridge.java` | C-061, C-110, C-007, C-002 | 2, 8 | LOW |
| 4 | 3 | adminRemoveItem — garde de jeton noop et libération du verrou en cas d'exception | `ZAuctionManager.java` | C-112, C-042 | 2 | MEDIUM |
| 4 | 4 | LocalAuctionClusterBridge — alignement sur la convention noop et libération conditionnelle | `LocalAuctionClusterBridge.java` | C-110 | 2 | MEDIUM |
| 4 | 5 | Verrou fuité par le orTimeout : émission unique et libération compensatoire | `PurchaseService.java` | C-111 | 2 | MEDIUM |
| 4 | 6 | Même compensation dans RemoveService (acquireLockStep) + token rendu volatile | `RemoveService.java` | C-111 | 2 | LOW |
| 4 | 7 | Consommer releaseLock et journaliser tout verrou perdu | `PurchaseService.java, RemoveService.java, ExpireService.java, ZAuctionManager.java` | C-061, C-025 | 2, 8 | LOW |
| 4 | 8 | Addon — bump du pin API et auto-guérison des scripts Lua | `RedisAuctionClusterBridge.java` | C-058 | 8 | LOW |
| 4 | 9 | Addon — suppression des replis non atomiques : échec fermé | `RedisAuctionClusterBridge.java` | C-099 | 8 | MEDIUM |
| 4 | 10 | Addon — états terminaux à portée, jeton par acquisition, TTL des marqueurs terminaux | `RedisAuctionClusterBridge.java` | C-002, C-025 | 8 | MEDIUM |
| 4 | 11 | Addon — bail renouvelable : lockLeaseDuration, renewLock, isHeldBy | `config.yml` | C-007 | 8 | LOW |
| 4 | 12 | Plugin — watchdog de bail et vérification de détention avant le débit | `AuctionService.java + PurchaseService.java` | C-007 | 8 | MEDIUM |
| 4 | 13 | (Release suivante) Fusion du pré-contrôle et de l'acquisition : checkAndLock | `AuctionClusterBridge.java + RedisAuctionClusterBridge.java + les 4 sites d'appel` | C-106 | 13 | MEDIUM |
| 5 | 1 | ZAuctionManager : accesseur O(1) getItem + marquage terminal de l'instance sortie du store | `ZAuctionManager.java` | C-046 | 1 | LOW |
| 5 | 2 | RemoveService : garde d'identité en tête de executeRemoval | `RemoveService.java` | C-046 | 1 | LOW |
| 5 | 3 | Relecture autoritaire sous verrou : selectItemRow (1 requête) + revalidateUnderLockStep +  | `RemoveService.java` | C-009 | 5 | MEDIUM |
| 5 | 4 | Message.ADMIN_ITEM_NOT_AVAILABLE + réplication dans les 6 messages.yml | `Message.java` | C-012, C-042, C-060 | 2 | LOW |
| 5 | 5 | Réécriture intégrale de adminRemoveItem : noop, relecture sous verrou, écriture avant diff | `ZAuctionManager.java` | C-012, C-042, C-060, C-046 | 1, 2 | MEDIUM |
| 5 | 6 | C-083 : la destination du retrait d'une annonce est décidée une seule fois, sur le thread  | `RemoveService.java` | C-083 | 5 | MEDIUM |
| 5 | 7 | changelogs.md + documentation Docusaurus EN/FR | `changelogs.md` | C-009, C-012, C-042, C-046, C-060, C-083 | 1, 2, 5 | LOW |
| 6 | 1 | C-040 — V3MigrationProvider propage le verdict réel au lieu de le remplacer par un succès | `V3MigrationProvider.java` | C-040 | 12 | LOW |
| 6 | 2 | C-118 — Le teardown de onDisable ne dépend plus de isEnabled : un démarrage avorté ne lais | `ZAuctionPlugin.java` | C-118 | 1 | LOW |
| 6 | 3 | C-054 — Casser la récursion mutuelle ItemStackUtils ↔ Base64ItemStack et la NPE de seriali | `ItemStackUtils.java` | C-054 | 1 | LOW |
| 6 | 4 | C-029 — Le format de sérialisation est porté par la DONNÉE, pas par la version du serveur  | `Base64ItemStack.java` | C-029 | 4 | MEDIUM |
| 6 | 5 | C-001 (+ quarantaine C-011) — Paginer le IN, faire échouer le chargement bruyamment, et ne | `ItemLoaderUtils.java` | C-001, C-011 | 4 | MEDIUM |
| 6 | 6 | C-109 — selectItemState : distinguer « l'item a disparu » de « je n'ai pas su le lire », s | `StorageManager.java` | C-109 | 4 | LOW |
| 6 | 7 | C-044 — loadItems() devient une vraie réinitialisation, et /ah admin migrate refuse le rec | `AuctionLoader.java` | C-044 | 4 | MEDIUM |
| 6 | 8 | C-108 — La migration V3 n'écrase plus jamais un pseudo V4, et un vrai pseudo l'emporte tou | `V3MigrationService.java` | C-108 | 12 | LOW |
| 6 | 9 | C-075 — Supprimer les catch morts de la migration V3 et compenser l'annonce orpheline au l | `V3MigrationService.java` | C-075 | 12 | MEDIUM |
| 6 | 10 | C-033 — Ligne sentinelle au lieu de item_id = 0 : l'argent PENDING de la V3 cesse d'être d | `V3MigrationService.java` | C-033 | 12 | MEDIUM |
| 6 | 11 | C-034 — Sentinelle d'idempotence en base : la migration n'est plus rejouable sans un `forc | `MigrationStateRepository.java` | C-034 | 12 | MEDIUM |
| 6 | 12 | C-113 + C-016 (addon) — Garde d'entrée sur un plugin principal désactivé, et UUID de serve | `ZAuctionHouseRedis.java` | C-113, C-016 | 8 | LOW |
| 6 | 13 | C-010 — SQLITE + bridge cluster distribué est une configuration impossible : refus explici | `ZAuctionPlugin.java` | C-010 | 8 | LOW |
| 6 | 14 | C-036 — ClusterUnavailableBridge : un nœud multi-serveurs refuse d'opérer plutôt que de re | `ClusterUnavailableBridge.java` | C-036 | 8 | MEDIUM |
| 6 | 15 | C-031 — Enregistrer les listeners et confirmer l'abonnement AVANT d'installer le bridge, e | `ZAuctionHouseRedis.java` | C-031 | 8 | LOW |
| 6 | 16 | C-057 — Un champ non convertible ne détruit plus tout le message de synchronisation | `Utils.java` | C-057 | 8 | LOW |
| 7 | 1 | Garde autoritaire de statut dans la re-validation d'achat sous verrou (C-114) | `PurchaseService.java` | C-114 | 3 | LOW |
| 7 | 2 | Libération explicite du statut de confirmation à la déconnexion et à la mort (C-023, volet | `PlayerListener.java` | C-023 | 9 | MEDIUM |
| 7 | 3 | Unification et durcissement des deux sorties de confirmation (C-068 + C-069) | `ConfirmHelper.java` | C-068, C-069 | 9 | MEDIUM |
| 7 | 4 | Protection des clés « opération en cours » dans /ah admin cache clear (C-065) | `CommandAuctionAdminCacheClear.java` | C-065 | 9 | LOW |
| 7 | 5 | Nouvelle section de configuration `maintenance` (additive côté API, inerte côté comporteme | `MainConfiguration.java + les 6 config.yml` | C-023, C-072, C-088 | 9 | LOW |
| 7 | 6 | Balayage TTL des statuts de confirmation (C-023 volet temporel + C-072) | `ZAuctionPlugin.java` | C-023, C-072 | 9 | MEDIUM |
| 7 | 7 | Tâche d'expiration planifiée (C-088) | `ZMaintenanceScheduler.java` | C-088 | 9 | MEDIUM |
| 7 | 8 | /ah admin add : application des catégories et annonce au cluster (C-064) | `CommandAuctionAdminAdd.java` | C-064 | 9 | MEDIUM |
| 7 | 9 | Addon : primitive d'ordonnancement des messages par item (C-021, C-071 volet chronologique | `ItemMessageGenerations.java (nouveau) + ZAuctionHouseRedis.java + les 4 listeners` | C-021, C-071 | 9 | MEDIUM |
| 7 | 10 | Addon : durcissement de ItemListedListener et garde de cohérence des ré-ajouts (C-093 + C- | `ItemListedListener.java + ItemRemovedListener.java` | C-093, C-071 | 9 | MEDIUM |
| 7 | 11 | Addon : compare-and-swap de statut et barrière de cycle de vie (C-004) | `ItemStatusListener.java` | C-004 | 9 | LOW |
| 7 | 12 | Addon : purge visuelle des HDV déjà ouverts au retrait et à l'achat (C-092) | `ItemLookup.java (nouveau) + ItemRemovedListener.java + ItemBoughtListener.java` | C-092 | 9 | MEDIUM |
| 7 | 13 | Addon : publication différenciée pré-commit / post-commit et file de resynchronisation (C- | `ZAuctionHouseRedis.java + RedisAuctionClusterBridge.java` | C-028 | 9 | MEDIUM |
| 7 | 14 | Addon : réconciliation du store LISTED après chaque coupure du bus (C-047) | `RedisSubscriberRunnable.java + ZAuctionHouseRedis.java + RedisAuctionClusterBridge.java` | C-047 | 9 | MEDIUM |
| 7 | 15 | Addon : arrêt propre — bridge fail-closed et libération des verrous détenus (C-018) | `ClusterUnavailableBridge.java (nouveau) + ZAuctionHouseRedis.java + RedisAuctionClusterBridge.java` | C-018 | 8 | MEDIUM |
| 8 | 1 | Publier le bridge cluster et la permission hors-ligne sous barrière mémoire | `ZAuctionPlugin.java` | C-117 | 1 | LOW |
| 8 | 2 | Executor de stockage non rejetant, nommé et dimensionné + drapeau d'arrêt | `ZAuctionPlugin.java` | C-022 | 10 | MEDIUM |
| 8 | 3 | Déclarer le drapeau d'arrêt au contrat API | `AuctionPlugin.java` | C-022 | 10 | LOW |
| 8 | 4 | Router tout le JDBC vers l'executor du plugin, seul pool drainé à l'arrêt | `ZStorageManager.java` | C-027, C-089, C-077 | 10, 7, 8 | MEDIUM |
| 8 | 5 | Rendre ZPlayerCache sûr en concurrence sans changer sa sémantique | `ZPlayerCache.java` | C-105 | 10 | MEDIUM |
| 8 | 6 | Indexer les caches joueur par UUID, cesser de les ressusciter, confiner la mutation d'ITEM | `ZAuctionManager.java` | C-107, C-105 | 10 | MEDIUM |
| 8 | 7 | Sérialiser et confiner les mutations d'économie par compte | `ZAuctionEconomy.java` | C-084 | 7 | MEDIUM |
| 8 | 8 | Propager le type d'économie à ZAuctionEconomy | `ZEconomyManager.java` | C-084 | 7 | LOW |
| 8 | 9 | Message d'indisponibilité pendant l'arrêt | `Message.java` | C-022 | 10 | LOW |
| 8 | 10 | Services : refuser les opérations pendant l'arrêt et confiner les appels Bukkit au thread  | `PurchaseService.java` | C-022, C-035, C-076, C-079 | 10, 8, 9 | MEDIUM |
| 8 | 11 | Confirmations d'achat et de retrait : un seul chemin, exécuté sur le thread du clic | `ListedItemsButton.java` | C-048, C-049 | 10 | MEDIUM |
| 8 | 12 | Addon : borner l'attente du pool Jedis et supprimer les emprunts de connexion imbriqués | `RedisConnectionFactory.java` | C-052 | 8 | MEDIUM |
| 8 | 13 | Addon : exécuteur dédié pour le bridge, fermé avant le pool Jedis | `RedisAuctionClusterBridge.java` | C-077 | 8 | MEDIUM |
| 8 | 14 | Addon : arrêt réellement fiable du thread abonné | `RedisSubscriberRunnable.java` | C-086 | 8 | LOW |
| 8 | 15 | Commandes admin : sortir le travail bloquant du thread principal | `CommandAuctionAdminGenerate.java` | C-081, C-087 | 12 | MEDIUM |
| QUICK | 1 | C-045 — `return` → `continue` dans la boucle de rafraîchissement des inventaires | `ZAuctionManager.java` | C-045 | 1 | LOW |
| QUICK | 2 | C-117 — publication sûre des références mutées à chaud (`volatile`) | `ZAuctionPlugin.java` | C-117 | 1 | LOW |
| QUICK | 3 | C-054 — casser la récursion mutuelle Base64ItemStack ↔ ItemStackUtils et la NPE de sériali | `ItemStackUtils.java` | C-054 | 1 | LOW |
| QUICK | 4 | C-100 — redécouper les charges multi-stacks à la LECTURE des logs admin | `AdminLogsButton.java` | C-100 | 1 | LOW |
| QUICK | 5 | C-006 / C-114 — exiger que la ligne soit ENCORE listée dans la revalidation sous verrou | `PurchaseService.java` | C-006, C-114 | 3 | LOW |
| QUICK | 6 | C-013 — ne marquer RETRIEVED que les transactions réellement créditées | `ClaimService.java` | C-013 | 1 | LOW |
| QUICK | 7 | C-062 — calculer une vraie date limite de récupération dans `/ah admin add expired|purchas | `CommandAuctionAdminAdd.java` | C-062 | 9 | LOW |
| QUICK | 8 | C-085 — ne jamais adopter l'UUID d'un joueur réel pour des annonces de test | `CommandAuctionAdminGenerate.java` | C-085 | 12 | LOW |
| QUICK | 9 | C-115 (b) — supprimer la rétention de `Player` dans les maps de confirmation | `CommandAuctionAdminGenerate.java` | C-115 | 12 | LOW |
| QUICK | 10 | C-097 — borner la sélection de vente à ce que le GUI sait afficher et retirer | `SellShowItemButton.java` | C-097 | 6 | LOW |
| QUICK | 11 | C-004 — compare-and-swap du statut reçu par le bus Redis (addon) | `ItemStatusListener.java` | C-004 | 9 | LOW |
| QUICK | 12 | C-031 — enregistrer les listeners AVANT le thread abonné, et fermer la course de données | `ZAuctionHouseRedis.java` | C-031 | 8 | LOW |
| QUICK | 13 | C-116 — rendre le contrôleur de version de l'addon opérant | `VersionChecker.java` | C-116 | 1 | LOW |
| QUICK | 14 | C-110 — aligner le contrat de `LocalAuctionClusterBridge` sur celui du bridge Redis (GATÉ  | `LocalAuctionClusterBridge.java` | C-110 | 2 | MEDIUM |
| QUICK | 15 | C-090 — chargement O(n×m) → O(n+m) par regroupement des contenus | `ItemLoaderUtils.java` | C-090 | 4 | LOW |
| QUICK | 16 | C-073 — utiliser le rowcount du DELETE au lieu de pré-compter les logs | `LogRepository.java` | C-073 | 4 | LOW |
| QUICK | 17 | C-066 — migrations d'index, une classe PAR index | `CreateItemsStorageTypeIndexMigration.java` | C-066 | 4 | MEDIUM |
| QUICK | 18 | C-074 — supprimer les copies défensives et le scan linéaire du rendu incrémental | `ListedItemsButton.java` | C-074 | 11 | LOW |
| QUICK | 19 | C-055 / C-098 / C-104 (API) — accesseurs O(1) sur `AuctionManager`, en méthodes `default` | `AuctionManager.java` | C-055, C-050, C-098, C-104 | 11, 9 | LOW |
| QUICK | 20 | C-055 — la recherche lit le store en O(1) au lieu d'en faire une copie complète | `SearchService.java` | C-055 | 11 | LOW |
| QUICK | 21 | C-101 — compteur de génération : une invalidation ne peut plus être perdue par un rebuild | `SortedItemsCache.java` | C-101 | 11 | LOW |
| QUICK | 22 | C-056 (parties gratuites) — une seule garde de reconstruction, et les tris sur le pool pri | `SortedItemsCache.java` | C-056 | 11 | LOW |
| QUICK | 23 | C-098 + C-104 — servir les compteurs depuis le cache trié et supprimer la mémoïsation | `ZCategoryManager.java` | C-098, C-104 | 11 | MEDIUM |

---

# Chantier 1 — Compare-and-set universel + propagation du rowcount (items + transactions)

Ce chantier donne a la base de donnees le pouvoir d'arbitrer les courses, et rend le verdict visible aux appelants. Aujourd'hui, `Repository.update()` jette le nombre de lignes modifiees rendu par Sarah (`UpdateRequest.java:61`), et `ItemRepository.createUpdateSchema` ne pose de clause `where storage_type` que pour la cible EXPIRED : toutes les transitions vers DELETED et PURCHASED sont des UPDATE aveugles qui ecrasent la vente d'un autre serveur, et un UPDATE qui ne matche aucune ligne se termine en succes silencieux. Le meme defaut existe sur l'argent : `ClaimService` depose PUIS marque RETRIEVED sans aucune garde, et marque meme les lignes qu'il n'a pas payees. Le lot est indivisible parce qu'un compare-and-set sans propagation du rowcount ne sert a rien (le perdant croit avoir gagne), et parce que propager le rowcount sans que les appelants convergent vers la base transformerait un ecrasement silencieux en fantome memoire — voire, sur le chemin de retrait, en rediffusion d'un `AVAILABLE` qui remettrait en vente sur tout le cluster un item deja vendu. La strategie de compatibilite est stricte : aucune signature publiee n'est modifiee, tout passe par des surcharges et des methodes `default`, et l'addon Redis (fige sur le SHA `deb8f16`) n'appelle aucune des methodes touchees — verifie par grep.

**Prérequis**

- Aucun. Ce chantier est le socle : il ne depend d'aucun autre et doit etre livre EN PREMIER.
- Contrainte inverse a respecter imperativement : C-059 (auto-claim declenche depuis ItemBoughtListener, chantier 7) NE DOIT PAS etre livre avant l'etape 5 de ce chantier, sinon il ouvre une fenetre de double credit.
- Contrainte inverse : C-060 (garde SQL vers PURCHASED, chantier 5) est deja incluse ici ; ne pas la re-livrer separement.
- Contrainte inverse : C-088 (balayage periodique d'expiration, chantier 7) exige les etapes 7 a 9 de ce chantier, sinon deux noeuds expirent et rendent le meme item.

**Constats couverts** : C-026, C-094, C-091, C-078 (volet SQL/rowcount uniquement — le routage cluster des transitions vers DELETED reste au chantier 7 avec C-070), C-006, C-013, C-003, C-119, C-012 (volet ecriture : chainage du future jete et CAS ; le test du jeton noop et la relecture sous verrou restent aux chantiers 4/5)

*~460 LOC · 1 fichiers nouveaux*

## 1.1 — Repository : faire remonter le nombre de lignes modifiees

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/storage/Repository.java`  
**Risque** : LOW  
**Constats** : C-026

Sarah calcule deja le rowcount (UpdateRequest.java:61 `return preparedStatement.getUpdateCount();`, UpdateBatchRequest.java:80 `return total;`) et le projet le jette dans les deux cas. Sans ce retour, aucun compare-and-set n'est observable. Ajout purement additif de deux methodes `protected` : binairement compatible pour toute sous-classe existante (aucune ne declare ces signatures — verifie). Je NE touche PAS aux `printStackTrace()` existants : la contre-expertise a raison, Sarah leve une DatabaseException (RuntimeException) et ces catch(SQLException) sont du code mort ; les modifier gonflerait le diff sans rien corriger.

```java
// --- imports a ajouter en tete du fichier ---
import fr.maxlego08.sarah.exceptions.DatabaseException;

// --- a inserer juste apres update(Consumer<Schema> consumer) (l.106) ---

    /**
     * Executes an update operation and returns the number of rows actually affected.
     * <p>
     * Contrairement a {@link #update(Consumer)}, le nombre de lignes modifiees n'est PAS jete.
     * C'est le seul signal qui permette a un appelant de savoir qu'il a PERDU une course :
     * 0 ligne signifie que la ligne ne portait plus l'etat source attendu au moment de
     * l'ecriture, autrement dit qu'un autre serveur (ou un autre chemin local) est passe avant.
     *
     * @param consumer the schema configuration
     * @return the number of affected rows; {@code 0} means the compare-and-set was lost
     */
    protected int updateReturning(Consumer<Schema> consumer) {
        try {
            return SchemaBuilder.update(getTableName(), consumer).execute(this.connection, this.logger);
        } catch (SQLException exception) {
            // Filet : Sarah leve deja une DatabaseException (RuntimeException) et n'atteint
            // jamais cette branche. On relaie sans jamais avaler l'echec, sinon un echec SQL
            // serait indiscernable d'une course perdue.
            throw new DatabaseException("update", getTableName(), exception);
        }
    }

    /**
     * Executes a batch update operation and returns the total number of rows affected.
     * <p>
     * ATTENTION : certains pilotes JDBC renvoient {@code Statement.SUCCESS_NO_INFO} (-2) par
     * instruction ; la somme peut donc etre negative ou plus petite que le nombre de schemas
     * meme sans course perdue. L'appelant doit traiter tout total different du nombre de
     * schemas comme un "a verifier", jamais comme un "a echouer".
     *
     * @param schemas the list of update schemas to execute
     * @return the sum of the per-statement affected row counts
     */
    protected int updateReturning(List<Schema> schemas) {
        if (schemas.isEmpty()) return 0;
        UpdateBatchRequest updateBatchRequest = new UpdateBatchRequest(schemas);
        return updateBatchRequest.execute(this.connection, this.connection.getDatabaseConfiguration(), this.logger);
    }
```

## 1.2 — StaleItemException : le vocabulaire de la course perdue

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/storage/StaleItemException.java`  
**Risque** : LOW  
**Constats** : C-026

Il faut un type distinct pour differencier "la base est tombee" (retenter / alerter) de "j'ai perdu la course" (abandonner sans rien rendre ni debiter). Sans ce type, chaque appelant retomberait sur un `INTERNAL_ERROR` generique et, pire, sur le chemin de retrait, sur une restauration de statut AVAILABLE qui remettrait l'item en vente sur tout le cluster. Classe nouvelle dans le module publie : purement additif. Le `unwrap` statique est indispensable car les futures emballent tout dans des CompletionException.

```java
package fr.maxlego08.zauctionhouse.api.storage;

import fr.maxlego08.zauctionhouse.api.item.StorageType;

/**
 * Levee quand un UPDATE compare-and-set n'a modifie AUCUNE ligne : la ligne ne portait plus
 * l'etat source attendu au moment de l'ecriture.
 * <p>
 * Autrement dit, un autre serveur (ou un autre chemin local) a deja fait transiter cet item.
 * L'appelant a PERDU la course : il ne doit ni remettre l'item au joueur, ni deplacer d'argent,
 * ni restaurer un statut du cycle LISTED. Il doit converger vers la verite de la base, c'est-a-dire
 * purger sa copie memoire.
 */
public class StaleItemException extends RuntimeException {

    private final int itemId;
    private final StorageType expectedFrom;
    private final StorageType destination;

    public StaleItemException(int itemId, StorageType expectedFrom, StorageType destination) {
        super("Item " + itemId + " is no longer " + expectedFrom + " (transition to " + destination + " matched 0 row)");
        this.itemId = itemId;
        this.expectedFrom = expectedFrom;
        this.destination = destination;
    }

    public int getItemId() {
        return this.itemId;
    }

    public StorageType getExpectedFrom() {
        return this.expectedFrom;
    }

    public StorageType getDestination() {
        return this.destination;
    }

    /**
     * Deballe une chaine de CompletionException / ExecutionException et rend l'exception de
     * course perdue si elle s'y trouve, {@code null} sinon.
     *
     * @param throwable the throwable to unwrap, may be {@code null}
     * @return the stale exception, or {@code null} if the cause chain contains none
     */
    public static StaleItemException unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof StaleItemException stale) return stale;
            Throwable cause = current.getCause();
            current = (cause == current) ? null : cause;
        }
        return null;
    }
}
```

## 1.3 — ClaimService : ne marquer RETRIEVED que ce qui a reellement ete verse

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/ClaimService.java`  
**Risque** : LOW  
**Constats** : C-013

Correctif autonome et immediat, sans schema ni API : aujourd'hui `repository.updateStatus(transactionIds, RETRIEVED)` (l.84) marque TOUTE la liste, y compris les economies introuvables (`continue` l.59), les depots en echec (`continue` l.74) et le cas joueur hors ligne (le `if (player.isOnline())` l.69 saute le depot mais pas le marquage). L'argent est detruit. On ne collecte plus que les identifiants effectivement credites. A livrer AVANT l'etape 5 : il arrete la destruction d'argent tout de suite, alors que la reservation atomique demande une migration. Ne PAS le presenter comme corrigeant le double claim — il ne corrige que la destruction.

```java
// --- imports a ajouter ---
import java.util.ArrayList;
import java.util.logging.Level;

// --- remplace integralement le corps de claimMoney (l.30-93) ---

    @Override
    public CompletableFuture<ClaimResult> claimMoney(Player player) {
        return getPendingTransactions(player.getUniqueId()).thenApply(transactions -> {
            if (transactions.isEmpty()) {
                message(this.plugin, player, Message.CLAIM_NO_PENDING);
                return ClaimResult.nothingToClaim("No pending transactions");
            }

            Map<String, List<TransactionDTO>> byEconomy = transactions.stream().collect(Collectors.groupingBy(TransactionDTO::economy_name));
            if (byEconomy.isEmpty()) {
                message(this.plugin, player, Message.CLAIM_NO_PENDING);
                return ClaimResult.nothingToClaim("No pending transactions");
            }

            var economyManager = this.plugin.getEconomyManager();
            var depositReason = this.plugin.getConfiguration().getAutoClaimConfiguration().depositReason();

            // On ne cloture QUE les lignes effectivement creditees. Tout ce qui n'a pas ete
            // verse reste PENDING et sera represente au prochain claim.
            final List<Integer> claimedIds = new ArrayList<>();
            BigDecimal totalClaimed = BigDecimal.ZERO;
            AuctionEconomy lastEconomy = null;

            for (var entry : byEconomy.entrySet()) {
                String economyName = entry.getKey();
                List<TransactionDTO> economyTransactions = entry.getValue();

                var optionalEconomy = economyManager.getEconomy(economyName);
                if (optionalEconomy.isEmpty()) {
                    // Economie disparue d'economies.yml : surtout ne rien marquer, l'argent reste du.
                    this.plugin.getLogger().warning("Economy not found: " + economyName + ", " + economyTransactions.size() + " transaction(s) left PENDING for " + player.getName());
                    continue;
                }
                var economy = optionalEconomy.get();

                BigDecimal economyTotal = economyTransactions.stream().map(TransactionDTO::value).filter(v -> v.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add);

                if (economyTotal.compareTo(BigDecimal.ZERO) <= 0) {
                    // Aucune valeur positive a verser : ce sont des lignes d'historique, on cloture.
                    economyTransactions.forEach(transaction -> claimedIds.add(transaction.id()));
                    continue;
                }

                if (!player.isOnline()) {
                    this.plugin.getLogger().warning("Player " + player.getName() + " went offline during claim, " + economyTransactions.size() + " transaction(s) left PENDING");
                    continue;
                }

                try {
                    economy.deposit(player.getUniqueId(), economyTotal, depositReason);
                } catch (Exception exception) {
                    this.plugin.getLogger().log(Level.SEVERE, "Failed to deposit " + economyTotal + " to " + player.getName() + " for economy " + economyName + ", transaction(s) left PENDING", exception);
                    continue;
                }

                economyTransactions.forEach(transaction -> claimedIds.add(transaction.id()));
                totalClaimed = totalClaimed.add(economyTotal);
                lastEconomy = economy;
                message(this.plugin, player, Message.CLAIM_ECONOMY_SUCCESS, "%amount%", economyManager.format(economy, economyTotal), "%economy%", economy.getDisplayName());
            }

            if (claimedIds.isEmpty()) {
                return ClaimResult.nothingToClaim("Nothing could be claimed");
            }

            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            repository.updateStatus(claimedIds, TransactionStatus.RETRIEVED);

            if (totalClaimed.compareTo(BigDecimal.ZERO) > 0) {
                message(this.plugin, player, Message.CLAIM_SUCCESS, "%amount%", totalClaimed.toString());
                return ClaimResult.success("Money claimed successfully", totalClaimed.doubleValue(), lastEconomy);
            }

            return ClaimResult.nothingToClaim("No positive amount to claim");
        });
    }
```

## 1.4 — Migration claim_token + primitives de reservation dans TransactionRepository

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/storage/migrations/CreateTransactionsMigration.java`  
**Risque** : MEDIUM  
**Constats** : C-003

CORRECTION IMPORTANTE A L'AUDIT ET A LA CONTRE-EXPERTISE : ajouter `table.string("claim_token", 36)` a la migration existante NE SUFFIT PAS. `MigrationManager.execute` (Sarah, l.100-102) sort par `if (!schema.getMigration().isAlter()) return;` des que la migration est deja enregistree, et `Migration.create(...)` laisse `alter = false` (seul `createOrAlter` le met a true, Migration.java:83-86). Sur toutes les installations existantes, la colonne ne serait jamais creee et la reservation echouerait a l'execution. Il faut basculer la migration sur `createOrAlter`. Une seconde colonne `claim_reserved_at` est necessaire : `updated_at` n'est auto-bumpe que sur MySQL/MariaDB (`CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`) et JAMAIS sur SQLite (SchemaBuilder.updatedAt():423-431), donc un filet de recuperation base dessus serait inoperant sur le storage-type par defaut. Les deux colonnes sont nullables : un downgrade de jar les ignore.

```java
// ===== FICHIER 1 : CreateTransactionsMigration.java (remplacement complet) =====
package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

public class CreateTransactionsMigration extends Migration {

    @Override
    public void up() {
        // createOrAlter et non create : positionne isAlter() a true, ce qui autorise
        // MigrationManager a AJOUTER les colonnes manquantes sur les bases deja migrees.
        // Avec create(), MigrationManager sort immediatement (l.100-102) et claim_token
        // n'existerait jamais sur une installation existante.
        createOrAlter(Tables.TRANSACTIONS, table -> {
            table.autoIncrement("id");
            table.integer("item_id").foreignKey(Tables.ITEMS, "id", true);
            table.string("player_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);
            table.string("economy_name", 255);
            table.decimal("before", 65, 2);
            table.decimal("after", 65, 2);
            table.decimal("value", 65, 2);
            table.string("status", 32);
            // Reservation atomique du claim : un claim ecrit son jeton sur les lignes qu'il
            // remporte AVANT de payer. Nullable = ligne libre.
            table.string("claim_token", 36).nullable();
            // Horodatage ECRIT PAR NOUS (updated_at n'est pas auto-bumpe sous SQLite),
            // seule base fiable du filet de recuperation des reservations orphelines.
            table.timestamp("claim_reserved_at").nullable();
            table.timestamps();
        });
    }
}

// ===== FICHIER 2 : TransactionRepository.java =====
// imports a ajouter :
//   import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO; (deja present)
//   import java.util.ArrayList;
//   import java.util.Date;

    /**
     * Reserve, en UNE seule ecriture atomique, toutes les lignes PENDING encore libres du joueur.
     * <p>
     * Le nombre de lignes rendu par la base est le seul arbitre : deux claims concurrents
     * (bouton + /ah claim + auto-claim, ou deux serveurs du cluster) ne peuvent pas remporter
     * les memes lignes, le second en obtient zero.
     *
     * @param playerUniqueId the player whose pending money is being claimed
     * @param claimToken     the token identifying this claim attempt
     * @return the number of rows this claim actually reserved
     */
    public int reservePending(UUID playerUniqueId, String claimToken) {
        return updateReturning(schema -> {
            schema.where("player_unique_id", playerUniqueId.toString());
            schema.where("status", TransactionStatus.PENDING.name());
            schema.whereNull("claim_token");
            schema.string("claim_token", claimToken);
            schema.object("claim_reserved_at", new Date());
        });
    }

    /**
     * Reads back the rows reserved by a given claim attempt.
     *
     * @param claimToken the token used by {@link #reservePending(UUID, String)}
     * @return the reserved transactions
     */
    public List<TransactionDTO> selectByClaimToken(String claimToken) {
        return select(TransactionDTO.class, schema -> schema.where("claim_token", claimToken));
    }

    /**
     * Cloture les lignes REELLEMENT payees. Le compare-and-set porte a la fois sur le jeton
     * (ce sont bien mes lignes) et sur le statut (elles n'ont pas ete cloturees entre-temps).
     *
     * @param transactionIds the transactions that were actually credited
     * @param claimToken     the token that reserved them
     * @return the number of rows actually closed
     */
    public int finishClaim(Collection<Integer> transactionIds, String claimToken) {
        if (transactionIds == null || transactionIds.isEmpty()) return 0;
        var schemas = transactionIds.stream().map(transactionId -> createUpdateSchema(schema -> {
            schema.where("id", transactionId);
            schema.where("claim_token", claimToken);
            schema.where("status", TransactionStatus.PENDING.name());
            schema.string("status", TransactionStatus.RETRIEVED.name());
        })).toList();
        return updateReturning(schemas);
    }

    /**
     * Rend reclamables les lignes reservees mais NON payees (economie introuvable, depot en
     * echec, joueur deconnecte). Sans cet appel, l'argent resterait bloque jusqu'au filet TTL.
     *
     * @param transactionIds the reserved transactions that were not paid
     * @param claimToken     the token that reserved them
     * @return the number of rows released
     */
    public int releaseClaim(Collection<Integer> transactionIds, String claimToken) {
        if (transactionIds == null || transactionIds.isEmpty()) return 0;
        var schemas = transactionIds.stream().map(transactionId -> createUpdateSchema(schema -> {
            schema.where("id", transactionId);
            schema.where("claim_token", claimToken);
            schema.object("claim_token", null);
            schema.object("claim_reserved_at", null);
        })).toList();
        return updateReturning(schemas);
    }

    /**
     * Filet de securite : libere les reservations orphelines d'UN SEUL joueur (crash entre la
     * reservation et le paiement). Volontairement borne au joueur concerne : un balayage global
     * libererait les lignes qu'un AUTRE serveur est en train de payer.
     *
     * @param playerUniqueId the player to recover
     * @param olderThanMs    reservation age beyond which it is considered abandoned
     * @return the number of reservations released
     */
    public int releaseStaleReservations(UUID playerUniqueId, long olderThanMs) {
        var threshold = new Date(System.currentTimeMillis() - olderThanMs);
        return updateReturning(schema -> {
            schema.where("player_unique_id", playerUniqueId.toString());
            schema.where("status", TransactionStatus.PENDING.name());
            schema.whereNotNull("claim_token");
            schema.where("claim_reserved_at", "<", threshold);
            schema.object("claim_token", null);
            schema.object("claim_reserved_at", null);
        });
    }

    // --- remplace updateStatus(Collection, TransactionStatus) : ajout du compare-and-set ---
    /**
     * @param transactionIds the transactions to close
     * @param status         the target status
     * @return the number of rows actually updated (a shortfall means a concurrent claim won)
     */
    public int updateStatus(Collection<Integer> transactionIds, TransactionStatus status) {
        if (transactionIds == null || transactionIds.isEmpty()) return 0;

        var schemas = transactionIds.stream()
                .map(transactionId -> createUpdateSchema(schema -> {
                    schema.where("id", transactionId);
                    // Compare-and-set : le perdant d'une course matche 0 ligne au lieu
                    // de re-marquer une ligne deja cloturee par quelqu'un d'autre.
                    schema.where("status", TransactionStatus.PENDING.name());
                    schema.string("status", status.name());
                }))
                .toList();
        return updateReturning(schemas);
    }
```

## 1.5 — ClaimService : reserver d'abord, payer ensuite

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/ClaimService.java`  
**Risque** : MEDIUM  
**Constats** : C-003

Le vecteur reel n'est pas inter-serveurs (la garde `player.isOnline()` l.69 l'invalide) mais MONO-SERVEUR : ClaimButton:120, CommandAuctionClaim:22 et l'auto-claim (l.147/151) appellent `claimMoney` sans partager le moindre etat, tous sur le pool fixe de 4 threads (ZAuctionPlugin:82). Deux declencheurs = deux SELECT identiques = deux depots. On inverse l'ordre : la reservation atomique par `claim_token` decide QUI possede les lignes, et seul le proprietaire paie. Le `claimingPlayers` est un confort par JVM, jamais l'arbitre. Le filet TTL est une constante (5 min) et non une cle de config, pour ne rien avoir a repliquer dans les six jeux de langue.

```java
// --- imports a ajouter ---
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// --- champs de classe ---

    /**
     * Delai au-dela duquel une reservation de claim est consideree comme orpheline (crash du
     * serveur entre la reservation et le paiement). Volontairement une constante : en faire une
     * cle de configuration imposerait de la repliquer dans les six jeux de langue.
     */
    private static final long STALE_CLAIM_RECOVERY_MS = TimeUnit.MINUTES.toMillis(5);

    // Garde de confort par JVM : empeche deux claims simultanes pour le meme joueur sur CE
    // serveur. Sans aucun effet en cluster : c'est la reservation en base qui arbitre.
    private final Set<UUID> claimingPlayers = ConcurrentHashMap.newKeySet();

// --- remplace claimMoney (issu de l'etape 3) ---

    @Override
    public CompletableFuture<ClaimResult> claimMoney(Player player) {

        var playerUniqueId = player.getUniqueId();
        if (!this.claimingPlayers.add(playerUniqueId)) {
            return CompletableFuture.completedFuture(ClaimResult.failure("A claim is already in progress"));
        }

        return CompletableFuture.supplyAsync(() -> doClaim(player, playerUniqueId), this.plugin.getExecutorService())
                .whenComplete((result, throwable) -> this.claimingPlayers.remove(playerUniqueId));
    }

    /**
     * Sequence : recuperer les reservations orphelines, RESERVER, relire ce qu'on a remporte,
     * payer, puis cloturer uniquement ce qui a ete paye et liberer le reste.
     */
    private ClaimResult doClaim(Player player, UUID playerUniqueId) {

        var repository = this.plugin.getStorageManager().with(TransactionRepository.class);

        int recovered = repository.releaseStaleReservations(playerUniqueId, STALE_CLAIM_RECOVERY_MS);
        if (recovered > 0) {
            this.plugin.getLogger().warning("Released " + recovered + " stale claim reservation(s) for " + playerUniqueId + " (server crashed between reservation and payment?)");
        }

        // RESERVER AVANT DE PAYER : le rowcount est le seul arbitre.
        String claimToken = UUID.randomUUID().toString();
        if (repository.reservePending(playerUniqueId, claimToken) == 0) {
            message(this.plugin, player, Message.CLAIM_NO_PENDING);
            return ClaimResult.nothingToClaim("No pending transactions");
        }

        var transactions = repository.selectByClaimToken(claimToken);
        if (transactions.isEmpty()) {
            message(this.plugin, player, Message.CLAIM_NO_PENDING);
            return ClaimResult.nothingToClaim("No pending transactions");
        }

        var economyManager = this.plugin.getEconomyManager();
        var depositReason = this.plugin.getConfiguration().getAutoClaimConfiguration().depositReason();
        Map<String, List<TransactionDTO>> byEconomy = transactions.stream().collect(Collectors.groupingBy(TransactionDTO::economy_name));

        final List<Integer> paidIds = new ArrayList<>();
        final List<Integer> unpaidIds = new ArrayList<>();
        BigDecimal totalClaimed = BigDecimal.ZERO;
        AuctionEconomy lastEconomy = null;

        for (var entry : byEconomy.entrySet()) {
            String economyName = entry.getKey();
            List<TransactionDTO> economyTransactions = entry.getValue();

            var optionalEconomy = economyManager.getEconomy(economyName);
            if (optionalEconomy.isEmpty()) {
                this.plugin.getLogger().warning("Economy not found: " + economyName + ", " + economyTransactions.size() + " transaction(s) released back to PENDING");
                economyTransactions.forEach(transaction -> unpaidIds.add(transaction.id()));
                continue;
            }
            var economy = optionalEconomy.get();

            BigDecimal economyTotal = economyTransactions.stream().map(TransactionDTO::value).filter(v -> v.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add);

            if (economyTotal.compareTo(BigDecimal.ZERO) <= 0) {
                economyTransactions.forEach(transaction -> paidIds.add(transaction.id()));
                continue;
            }

            if (!player.isOnline()) {
                this.plugin.getLogger().warning("Player " + player.getName() + " went offline during claim, " + economyTransactions.size() + " transaction(s) released back to PENDING");
                economyTransactions.forEach(transaction -> unpaidIds.add(transaction.id()));
                continue;
            }

            try {
                economy.deposit(playerUniqueId, economyTotal, depositReason);
            } catch (Exception exception) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to deposit " + economyTotal + " to " + player.getName() + " for economy " + economyName + ", transaction(s) released back to PENDING", exception);
                economyTransactions.forEach(transaction -> unpaidIds.add(transaction.id()));
                continue;
            }

            economyTransactions.forEach(transaction -> paidIds.add(transaction.id()));
            totalClaimed = totalClaimed.add(economyTotal);
            lastEconomy = economy;
            message(this.plugin, player, Message.CLAIM_ECONOMY_SUCCESS, "%amount%", economyManager.format(economy, economyTotal), "%economy%", economy.getDisplayName());
        }

        try {
            if (!paidIds.isEmpty()) repository.finishClaim(paidIds, claimToken);
            if (!unpaidIds.isEmpty()) repository.releaseClaim(unpaidIds, claimToken);
        } catch (RuntimeException exception) {
            // Trou residuel assume : le depot a eu lieu mais la cloture a echoue. Les lignes
            // restent reservees et releaseStaleReservations les rendra reclamables dans 5 min,
            // avec un risque de second paiement. On trace tout ce qu'il faut pour l'auditer.
            this.plugin.getLogger().log(Level.SEVERE, "CLAIM NOT CLOSED - player " + playerUniqueId + " token " + claimToken + " paid ids " + paidIds + " : money was deposited but the rows could not be closed, manual check required", exception);
            throw exception;
        }

        if (totalClaimed.compareTo(BigDecimal.ZERO) > 0) {
            message(this.plugin, player, Message.CLAIM_SUCCESS, "%amount%", totalClaimed.toString());
            return ClaimResult.success("Money claimed successfully", totalClaimed.doubleValue(), lastEconomy);
        }

        return ClaimResult.nothingToClaim("No positive amount to claim");
    }
```

## 1.6 — clearPendingTransactions : meme primitive de reservation

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/ClaimService.java`  
**Risque** : LOW  
**Constats** : C-119

Cette methode publique de l'API (AuctionClaimService:68) reproduit exactement les deux defauts corriges aux etapes 3 et 5 : depot avant marquage, UPDATE non conditionnel, et cloture inconditionnelle meme quand aucun depot n'a eu lieu. Elle n'a aucun appelant interne (grep sur les deux depots) — elle n'existe que pour les addons — d'ou la severite basse, mais la laisser en l'etat maintiendrait une porte de duplication grande ouverte a cote d'une porte fermee. Je REJETTE le point 5 du correctif d'audit (introduire un statut CANCELLED) : le javadoc documente explicitement que `giveMoney=false` signifie "discarded without any payment", RETRIEVED est donc le marqueur de cloture attendu, et ajouter une constante a l'enum publie TransactionStatus serait un changement d'API visible des addons pour un gain nul.

```java
    @Override
    public CompletableFuture<Void> clearPendingTransactions(UUID playerUniqueId, boolean giveMoney) {
        return CompletableFuture.runAsync(() -> {

            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);

            if (!giveMoney) {
                // Le javadoc de l'API l'indique : "the transactions are simply discarded without
                // any payment". Aucun paiement, donc aucune reservation necessaire, mais le
                // compare-and-set sur PENDING reste indispensable pour ne pas ecraser une
                // cloture concurrente.
                var pendingIds = repository.selectByPlayerAndStatus(playerUniqueId, TransactionStatus.PENDING).stream().map(TransactionDTO::id).toList();
                if (pendingIds.isEmpty()) return;
                int closed = repository.updateStatus(pendingIds, TransactionStatus.RETRIEVED);
                this.plugin.getLogger().warning("Discarded " + closed + "/" + pendingIds.size() + " pending transaction(s) for " + playerUniqueId + " without payment (clearPendingTransactions)");
                return;
            }

            repository.releaseStaleReservations(playerUniqueId, STALE_CLAIM_RECOVERY_MS);

            String claimToken = UUID.randomUUID().toString();
            if (repository.reservePending(playerUniqueId, claimToken) == 0) return;

            var transactions = repository.selectByClaimToken(claimToken);
            if (transactions.isEmpty()) return;

            var economyManager = this.plugin.getEconomyManager();
            var depositReason = this.plugin.getConfiguration().getAutoClaimConfiguration().depositReason();
            Map<String, List<TransactionDTO>> byEconomy = transactions.stream().collect(Collectors.groupingBy(TransactionDTO::economy_name));

            final List<Integer> paidIds = new ArrayList<>();
            final List<Integer> unpaidIds = new ArrayList<>();

            for (var entry : byEconomy.entrySet()) {
                var optionalEconomy = economyManager.getEconomy(entry.getKey());
                if (optionalEconomy.isEmpty()) {
                    this.plugin.getLogger().warning("Economy not found: " + entry.getKey() + ", transaction(s) released back to PENDING");
                    entry.getValue().forEach(transaction -> unpaidIds.add(transaction.id()));
                    continue;
                }

                BigDecimal economyTotal = entry.getValue().stream().map(TransactionDTO::value).filter(v -> v.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add);

                if (economyTotal.compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        optionalEconomy.get().deposit(playerUniqueId, economyTotal, depositReason);
                    } catch (Exception exception) {
                        this.plugin.getLogger().log(Level.SEVERE, "Failed to deposit " + economyTotal + " to " + playerUniqueId + " for economy " + entry.getKey() + ", transaction(s) released back to PENDING", exception);
                        entry.getValue().forEach(transaction -> unpaidIds.add(transaction.id()));
                        continue;
                    }
                }
                entry.getValue().forEach(transaction -> paidIds.add(transaction.id()));
            }

            if (!paidIds.isEmpty()) repository.finishClaim(paidIds, claimToken);
            if (!unpaidIds.isEmpty()) repository.releaseClaim(unpaidIds, claimToken);

        }, this.plugin.getExecutorService());
    }
```

## 1.7 — ItemRepository : compare-and-set universel et batch transactionnel

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/storage/repository/repositories/ItemRepository.java`  
**Risque** : LOW  
**Constats** : C-026, C-094

Coeur du chantier. La methode historique `updateItem(Item, StorageType)` est conservee STRICTEMENT a l'identique (meme schema, meme garde EXPIRED historique, meme absence de whereNull) et simplement marquee @Deprecated : aucun chemin non encore migre ne change de comportement, ce qui rend les etapes 9 et 11 revisables une par une. La nouvelle surcharge pose `where storage_type = from` sur TOUTES les cibles, plus `whereNull("buyer_unique_id")` sur PURCHASED (une vente ne peut viser qu'une ligne sans acheteur). Le batch (C-094) reutilise `UpdateBatchRequest`, deja employe par TransactionRepository et LogRepository, mais impose un regroupement par forme de requete : Sarah construit le SQL a partir du PREMIER schema uniquement (UpdateBatchRequest:29-46), donc melanger des schemas avec et sans colonne buyer_unique_id produirait une requete fausse. Enfin, la somme d'`executeBatch` n'est PAS utilisable comme detecteur de course (certains pilotes rendent SUCCESS_NO_INFO = -2) : tout ecart declenche une relecture ciblee qui, elle, est fiable.

```java
// --- imports a ajouter ---
import fr.maxlego08.sarah.database.Schema;   // deja present
import java.util.stream.Collectors;

    /** Taille de lot : borne la clause IN de la relecture et la duree du verrou SQLite. */
    private static final int BATCH_SIZE = 500;

    /**
     * @deprecated aucune garde de transition n'est posee (hors la garde historique vers EXPIRED) :
     * l'UPDATE ecrase la ligne quel que soit son etat courant, et le nombre de lignes modifiees
     * est jete. Conserve uniquement pour les chemins non encore audites.
     * Utiliser {@link #updateItem(Item, StorageType, StorageType)}.
     */
    @Deprecated
    public void updateItem(Item item, StorageType storageType) {
        this.update(createUpdateSchema(item, storageType));
    }

    /**
     * Compare-and-set : l'UPDATE n'aboutit QUE si la ligne porte encore l'etat source attendu.
     * C'est la seule barriere que la base oppose aux transitions concurrentes entre serveurs.
     *
     * @param item item to move
     * @param from storage state the row is expected to still carry
     * @param to   destination storage state
     * @return the number of affected rows; {@code 0} means the race was lost
     */
    public int updateItem(Item item, StorageType from, StorageType to) {
        return updateReturning(createCasUpdateSchema(item, from, to));
    }

    /**
     * Batch compare-and-set. Rend les identifiants des items qui ont PERDU la course, afin que
     * l'appelant purge sa memoire au lieu de garder un fantome reclamable.
     *
     * @param itemsByStorageType destination -> items to move
     * @param from               storage state all these rows are expected to still carry
     * @return the ids whose row did not move
     */
    public List<Integer> updateItems(Map<StorageType, List<Item>> itemsByStorageType, StorageType from) {

        List<Integer> staleIds = new ArrayList<>();

        for (Map.Entry<StorageType, List<Item>> entry : itemsByStorageType.entrySet()) {
            StorageType destination = entry.getKey();
            List<Item> items = entry.getValue();
            if (items.isEmpty()) continue;

            // UpdateBatchRequest construit le SQL a partir du PREMIER schema seulement : tous les
            // schemas d'un meme lot doivent donc avoir exactement les memes colonnes. La seule
            // variable, a destination fixee, est la presence de buyer_unique_id.
            Map<Boolean, List<Item>> byShape = items.stream().collect(Collectors.partitioningBy(item ->
                    (destination == StorageType.PURCHASED || destination == StorageType.DELETED) && item.getBuyerUniqueId() != null));

            for (List<Item> group : byShape.values()) {
                if (group.isEmpty()) continue;
                for (int offset = 0; offset < group.size(); offset += BATCH_SIZE) {
                    List<Item> chunk = group.subList(offset, Math.min(offset + BATCH_SIZE, group.size()));
                    var schemas = chunk.stream().map(item -> createUpdateSchema(createCasUpdateSchema(item, from, destination))).toList();
                    int affected = updateReturning(schemas);
                    if (affected != chunk.size()) {
                        // Soit une course perdue, soit un pilote qui rend SUCCESS_NO_INFO (-2).
                        // On tranche par une relecture ciblee : c'est le seul verdict fiable.
                        staleIds.addAll(findStale(chunk, destination));
                    }
                }
            }
        }

        return staleIds;
    }

    /**
     * Relit l'etat reel des lignes d'un lot et rend celles qui ne sont pas arrivees a destination.
     */
    private List<Integer> findStale(List<Item> chunk, StorageType destination) {
        var ids = chunk.stream().map(item -> String.valueOf(item.getId())).toList();
        Map<Integer, StorageType> actual = select(ids).stream().collect(Collectors.toMap(dto -> dto.id(), dto -> dto.storage_type(), (a, b) -> a));

        List<Integer> staleIds = new ArrayList<>();
        for (Item item : chunk) {
            if (actual.get(item.getId()) != destination) staleIds.add(item.getId());
        }
        return staleIds;
    }

    /**
     * Schema d'UPDATE avec compare-and-set. Contrairement au schema historique, la clause
     * {@code where storage_type = from} est posee sur TOUTES les destinations, et une transition
     * vers PURCHASED exige en plus que la ligne n'ait pas deja un acheteur.
     */
    private Consumer<Schema> createCasUpdateSchema(Item item, StorageType expectedFrom, StorageType storageType) {
        return schema -> {
            schema.where("id", item.getId());
            schema.where("storage_type", expectedFrom.name());
            if (storageType == StorageType.PURCHASED) {
                schema.whereNull("buyer_unique_id");
            }
            schema.string("storage_type", storageType.name());
            if (storageType != StorageType.DELETED) {
                schema.object("expired_at", item.getExpiredAt());
            }
            if (storageType == StorageType.PURCHASED || storageType == StorageType.DELETED) {
                if (item.getBuyerUniqueId() != null) {
                    schema.uuid("buyer_unique_id", item.getBuyerUniqueId());
                }
            }
        };
    }

// NOTE : createUpdateSchema(Item, StorageType) (l.71-92) et updateItems(Map) (l.58-69) restent
// INCHANGES a cette etape ; ils sont supprimes a l'etape 11 une fois le dernier appelant migre.
```

## 1.8 — StorageManager : surcharges CAS non cassantes

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/storage/StorageManager.java`  
**Risque** : LOW  
**Constats** : C-026, C-094

Je REJETTE explicitement le correctif de l'audit qui transforme `CompletableFuture<Void> updateItem(Item, StorageType)` en `CompletableFuture<Integer> updateItem(Item, StorageType, StorageType)` : le type de retour fait partie du descripteur JVM, et la methode appartient a une interface publiee dont un implementeur tiers casserait a la compilation. Deux methodes `default` additives suffisent : les implementations tierces continuent de compiler ET de fonctionner (elles heritent d'une delegation vers l'ancien chemin, sans la garantie — c'est documente), et ZStorageManager les surcharge pour poser reellement le compare-and-set. Le type de retour reste `CompletableFuture<Void>` pour la version unitaire : l'echec porte l'information, un `Integer` n'apporterait rien et alourdirait les 10 sites d'appel.

```java
// ===== FICHIER 1 : StorageManager.java, apres updateItems(Map) (l.121) =====

    /**
     * Updates the item in compare-and-set mode: the write only succeeds while the row still
     * carries {@code from}.
     * <p>
     * Le future echoue avec {@link StaleItemException} quand la course est perdue. L'appelant ne
     * doit alors ni remettre l'item au joueur, ni deplacer d'argent, ni restaurer un statut du
     * cycle LISTED : il doit purger sa copie memoire et converger vers la base.
     * <p>
     * L'implementation par defaut delegue a {@link #updateItem(Item, StorageType)} et n'offre donc
     * AUCUNE garantie de compare-and-set : elle n'existe que pour ne pas casser les implementations
     * tierces existantes de cette interface.
     *
     * @param item item to update
     * @param from storage bucket the row is expected to still carry
     * @param to   destination storage bucket
     * @return future completing when the update is persisted, failing with
     *         {@link StaleItemException} when no row matched
     */
    default CompletableFuture<Void> updateItem(Item item, StorageType from, StorageType to) {
        return updateItem(item, to);
    }

    /**
     * Batch compare-and-set variant of {@link #updateItems(Map)}.
     *
     * @param itemsByStorageType destination bucket to items to move
     * @param from               storage bucket all these rows are expected to still carry
     * @return future completing with the ids that lost the race (empty when everything moved);
     *         the default implementation always reports an empty list
     */
    default CompletableFuture<List<Integer>> updateItems(Map<StorageType, List<Item>> itemsByStorageType, StorageType from) {
        return updateItems(itemsByStorageType).thenApply(ignored -> List.of());
    }

// ===== FICHIER 2 : ZStorageManager.java, remplace/complete l.155-163 =====
// import fr.maxlego08.zauctionhouse.api.storage.StaleItemException;

    @Override
    @SuppressWarnings("deprecation")
    public CompletableFuture<Void> updateItem(Item item, StorageType storageType) {
        return CompletableFuture.runAsync(() -> with(ItemRepository.class).updateItem(item, storageType), this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<Void> updateItem(Item item, StorageType from, StorageType to) {
        return CompletableFuture.runAsync(() -> {
            int rows = with(ItemRepository.class).updateItem(item, from, to);
            // 0 ligne = la ligne ne portait plus l'etat source : course perdue, pas panne SQL.
            // Une vraie panne remonte deja en DatabaseException depuis Sarah.
            if (rows == 0) throw new StaleItemException(item.getId(), from, to);
        }, this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<List<Integer>> updateItems(Map<StorageType, List<Item>> itemsByStorageType, StorageType from) {
        return CompletableFuture.supplyAsync(() -> with(ItemRepository.class).updateItems(itemsByStorageType, from), this.plugin.getExecutorService());
    }
```

## 1.9 — ExpireService : bascule sur le CAS, garde AVAILABLE et purge des perdants

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/ExpireService.java`  
**Risque** : MEDIUM  
**Constats** : C-091, C-078, C-094, C-026

Trois corrections dans un fichier isole du chemin d'achat, donc revisable seul. (1) C-091 : la garde de relecture sous verrou l.283 ne teste que `dbItem == null || buyer != null` ; or `ItemRepository.select(int)` ne filtre que DELETED, donc une ligne deja passee a EXPIRED par un autre noeud franchit la garde avec un buyer null. Le mapping storage_type -> ItemStatus de ItemLoaderUtils:43-48 rend AVAILABLE pour LISTED : tester le statut equivaut exactement a tester `storage_type = 'LISTED'`. (2) C-078 volet SQL : toutes les transitions vers DELETED passent au CAS, et surtout les deux branches `else` mono-serveur cessent de laisser l'objet dans le store avec un statut DELETED — c'est ce fantome qui redevient reclamable. (3) C-094 : le batch rend desormais la liste des perdants, dont on purge la memoire. Je NE livre PAS ici le volet cluster de C-078 (verrou + relecture + diffusion pour les transitions vers DELETED) : il coute 1 verrou + 3 requetes + 2 emprunts Jedis par item et provoquerait la tempete decrite par C-070, il appartient au chantier 7 avec ses garde-fous.

```java
// --- imports a ajouter ---
import fr.maxlego08.zauctionhouse.api.storage.StaleItemException;

// ===== (1) l.79-85 : applyExpiration passe au CAS =====
            Consumer<Long> applyExpiration = expiration -> this.plugin.getScheduler().runNextTick(w -> {
                long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
                item.setExpiredAt(new Date(expiredAt));

                this.auctionManager.addItem(StorageType.EXPIRED, item);
                storageManager.updateItem(item, StorageType.LISTED, StorageType.EXPIRED).exceptionally(throwable -> {
                    handleStaleTransition(item, StorageType.EXPIRED, throwable);
                    return null;
                });
            });

// ===== (2) l.102-106 : branche EXPIRED/PURCHASED -> DELETED =====
        } else {

            item.setStatus(ItemStatus.DELETED);
            // C-078 : l'objet doit QUITTER le store. Le laisser dedans avec un statut DELETED
            // en fait un fantome que le joueur voit encore et peut tenter de reclamer.
            this.auctionManager.removeItem(storageType, item);
            storageManager.updateItem(item, storageType, StorageType.DELETED).exceptionally(throwable -> {
                handleStaleTransition(item, StorageType.DELETED, throwable);
                return null;
            });
        }

// ===== (3) l.190-194 : batch des vendeurs en ligne =====
                Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
                batchUpdate.put(StorageType.EXPIRED, onlineSellerItems);
                storageManager.updateItems(batchUpdate, StorageType.LISTED)
                        .thenAccept(staleIds -> dropStaleGhosts(staleIds, StorageType.EXPIRED))
                        .exceptionally(throwable -> {
                            this.plugin.getLogger().log(Level.SEVERE, "Failed to persist batch expiration of " + onlineSellerItems.size() + " item(s)", throwable);
                            return null;
                        });

// ===== (4) l.217-221 : batch des vendeurs hors ligne (meme traitement) =====
                                    this.plugin.getScheduler().runNextTick(w -> {
                                        Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
                                        batchUpdate.put(StorageType.EXPIRED, new ArrayList<>(processedItems));
                                        storageManager.updateItems(batchUpdate, StorageType.LISTED)
                                                .thenAccept(staleIds -> dropStaleGhosts(staleIds, StorageType.EXPIRED))
                                                .exceptionally(throwable -> {
                                                    this.plugin.getLogger().log(Level.SEVERE, "Failed to persist batch expiration (offline sellers)", throwable);
                                                    return null;
                                                });
                                    });

// ===== (5) l.227-237 : lot EXPIRED/PURCHASED -> DELETED =====
        } else {
            for (Item item : validItems) {
                item.setStatus(ItemStatus.DELETED);
                // C-078 : idem, l'objet quitte le store, il n'est plus reclamable.
                this.auctionManager.removeItem(storageType, item);
            }

            Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
            batchUpdate.put(StorageType.DELETED, validItems);
            storageManager.updateItems(batchUpdate, storageType)
                    .thenAccept(staleIds -> {
                        if (!staleIds.isEmpty()) {
                            this.plugin.getLogger().warning(staleIds.size() + " item(s) lost the deletion race: " + staleIds);
                        }
                    })
                    .exceptionally(throwable -> {
                        this.plugin.getLogger().log(Level.SEVERE, "Failed to persist batch deletion of " + validItems.size() + " item(s)", throwable);
                        return null;
                    });
        }

// ===== (6) C-091 : durcissement de la relecture sous verrou (l.282-290) =====
                                        .thenCompose(dbItem -> {
                                            // La ligne doit etre ENCORE listee. ItemRepository.select(int)
                                            // ne filtre que DELETED : une ligne deja passee a EXPIRED par un
                                            // autre noeud remonte ici avec buyer_unique_id null et franchirait
                                            // l'ancienne garde. ItemLoaderUtils traduit LISTED -> AVAILABLE,
                                            // donc tester le statut equivaut a tester storage_type = 'LISTED'.
                                            if (dbItem == null || dbItem.getBuyerUniqueId() != null || dbItem.getStatus() != ItemStatus.AVAILABLE) {
                                                this.plugin.getScheduler().runNextTick(w -> {
                                                    this.auctionManager.removeItem(StorageType.LISTED, item.getId());
                                                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
                                                });
                                                return CompletableFuture.<Void>completedFuture(null);
                                            }
                                            return performListedToExpired(item)
                                                    .thenCompose(v -> clusterBridge.removeItem(item, StorageType.LISTED, StorageType.EXPIRED)
                                                            .orTimeout(perf.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS));
                                        });

// ===== (7) l.355 : chemin cluster, meme CAS =====
                storageManager.updateItem(item, StorageType.LISTED, StorageType.EXPIRED).whenComplete((u, t) -> {
                    if (t != null) {
                        item.setExpiredAt(previousExpiredAt);
                        if (StaleItemException.unwrap(t) != null) {
                            // Course perdue : l'item n'est plus a nous, on retire le fantome local
                            // au lieu de laisser un LISTED memoire face a une base qui dit autre chose.
                            handleStaleTransition(item, StorageType.EXPIRED, t);
                        }
                        done.completeExceptionally(t);
                        return;
                    }
                    this.plugin.getScheduler().runNextTick(w2 -> {
                        item.setStatus(ItemStatus.REMOVED);
                        this.auctionManager.removeItem(StorageType.LISTED, item);
                        this.auctionManager.addItem(StorageType.EXPIRED, item);
                        done.complete(null);
                    });
                });

// ===== (8) deux helpers prives a ajouter en fin de classe =====

    /**
     * La transition a perdu la course : la ligne ne portait plus l'etat source attendu (un autre
     * serveur l'a vendue, expiree ou detruite entre-temps). On ne conserve JAMAIS le fantome en
     * memoire, il serait reclamable localement alors que la base dit autre chose.
     */
    private void handleStaleTransition(Item item, StorageType destination, Throwable throwable) {
        var stale = StaleItemException.unwrap(throwable);
        if (stale == null) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to persist transition of item " + item.getId() + " to " + destination, throwable);
            return;
        }
        this.plugin.getLogger().warning("Item " + item.getId() + " lost the " + destination + " race (" + stale.getMessage() + "), dropping the local ghost");
        dropStaleGhosts(List.of(item.getId()), destination);
    }

    /**
     * Purge de la memoire les items qui n'ont pas remporte leur transition, dans le conteneur
     * source comme dans le conteneur destination, puis invalide les caches d'affichage.
     */
    private void dropStaleGhosts(List<Integer> staleIds, StorageType destination) {
        if (staleIds.isEmpty()) return;
        this.plugin.getLogger().warning(staleIds.size() + " item(s) lost the " + destination + " race, dropping local ghosts: " + staleIds);
        this.plugin.getScheduler().runNextTick(w -> {
            for (Integer staleId : staleIds) {
                this.auctionManager.removeItem(StorageType.LISTED, staleId);
                this.auctionManager.removeItem(StorageType.EXPIRED, staleId);
                this.auctionManager.removeItem(StorageType.PURCHASED, staleId);
            }
            this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SEARCH);
        });
    }
```

## 1.10 — PurchaseService : revalidation exhaustive sous verrou avant de facturer

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/PurchaseService.java`  
**Risque** : LOW  
**Constats** : C-006

ARBITRAGE EXPLICITE, contre le correctif de l'audit : l'audit affirme que le CAS rend cette relecture redondante et propose de la SUPPRIMER pour gagner 3 requetes. Je refuse. La relecture ferme la fenetre AVANT que l'argent ne bouge (fail-closed, l'acheteur n'est pas debite) ; le CAS ne la ferme qu'APRES le withdraw (l.802) et le deposit (l.823). Tant que le chantier 2 n'a pas inverse l'ordre commit -> argent, supprimer la relecture echangerait 3 requetes contre un acheteur debite pour rien a chaque course perdue. On la garde donc, et on la durcit : refuser des que la ligne n'est plus reellement en vente, pas seulement quand elle a disparu ou porte deja un acheteur. On en profite pour reconcilier la memoire (le fantome local est retire du store) et pour resynchroniser expiredAt sur la verite base. Reserve assumee : ces deux mutations partent depuis ForkJoinPool.commonPool, comme le fait deja ZAuctionManager.purchaseAuctionItem:875 — le confinement de threads est le chantier 8, ne pas le bricoler ici.

```java
                            .thenCompose(dbItem -> {
                                // Verite base SOUS VERROU. ItemRepository.select(int) ne filtre que
                                // DELETED : une ligne passee a EXPIRED ou PURCHASED par un autre noeud
                                // remonte ici, et sans le test de statut elle franchissait la garde.
                                // ItemLoaderUtils:43-48 traduit LISTED -> AVAILABLE, donc tester le
                                // statut equivaut a tester storage_type = 'LISTED'.
                                if (dbItem == null || dbItem.getBuyerUniqueId() != null || dbItem.getStatus() != ItemStatus.AVAILABLE) {
                                    // Converger vers la base : notre exemplaire local est un fantome,
                                    // le laisser dans le store le rendrait cliquable a nouveau.
                                    auctionManager.removeItem(StorageType.LISTED, item.getId());
                                    auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
                                    inventoryManager.updateInventory(player);
                                    message(this.plugin, player, Message.ITEM_NO_LONGER_AVAILABLE);
                                    resultHolder.set(PurchaseResult.failure("Item already sold", PurchaseFailReason.ITEM_NOT_AVAILABLE));
                                    return failedFuture(new IllegalStateException("Item already sold on another server"));
                                }
                                // Resynchroniser l'expiration sur la verite base avant de facturer :
                                // un autre noeud a pu la prolonger ou la raccourcir.
                                item.setExpiredAt(dbItem.getExpiredAt());
                                return auctionEconomy.has(player.getUniqueId(), requiredBalance);
                            });
```

## 1.11 — Bascule des 8 derniers sites d'ecriture et convergence sur perte de course

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : HIGH  
**Constats** : C-026, C-012, C-094

Etape indivisible : migrer les sites de ZAuctionManager SANS traiter RemoveService serait une regression franche. Aujourd'hui, un echec d'`onLocalRemoval` fait passer par `restoreStatusOnError` (RemoveService:307-312), qui repose `AVAILABLE` et le rediffuse ; cote addon, `ItemStatusListener.java:54-62` applique le statut en aveugle puis `updateListedItems(item, true, null)` : on remettrait en vente sur tout le cluster l'item qu'on vient de constater vendu ailleurs. Le drapeau `staleDetected` porte le meme nom que celui que C-009 (chantier 5) introduira, pour que les deux patchs convergent au lieu de se contredire. Les huit `from` sont determines sans aucune devinette : chaque site connait le conteneur d'ou l'item sort. Pour adminRemoveItem je me limite volontairement au chainage du future aujourd'hui jete (le volet ecriture de C-012) ; le test du jeton noop (C-042), la relecture sous verrou (C-060) et la liberation du verrou dans l'exceptionally (C-112) appartiennent aux chantiers 4/5 et reecriront ce bloc.

```java
// ===== ZAuctionManager.java =====
// import fr.maxlego08.zauctionhouse.api.storage.StaleItemException;

// --- l.522 / l.532, removeListedItem ---
        if (configuration.getActions().listed().giveItem() && item.canReceiveItem(player)) {
            updateFuture = storageManager.updateItem(item, StorageType.LISTED, StorageType.DELETED);
            giveItem(player, item);
        } else {
            var expiration = configuration.getExpireExpiration().getExpiration(player);
            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            item.setExpiredAt(new Date(expiredAt));

            addItem(StorageType.EXPIRED, item);
            updateFuture = storageManager.updateItem(item, StorageType.LISTED, StorageType.EXPIRED);
        }

// --- l.568, removeSellingItem ---
        var updateFuture = storageManager.updateItem(item, StorageType.LISTED, StorageType.DELETED);

// --- l.602, removeExpiredItem ---
        var updateFuture = storageManager.updateItem(item, StorageType.EXPIRED, StorageType.DELETED);

// --- l.636, removePurchasedItem ---
        var updateFuture = storageManager.updateItem(item, StorageType.PURCHASED, StorageType.DELETED);

// --- l.693, adminRemoveItem : le future n'est plus jete ---
            // C-012 (volet ecriture) : ce future partait sur une ligne isolee, son echec etait
            // invisible. Le chainage complet (ecrire AVANT de diffuser) appartient au chantier 2.
            this.plugin.getStorageManager().updateItem(item, storageType, StorageType.DELETED).whenComplete((v, throwable) -> {
                if (throwable == null) return;
                var stale = StaleItemException.unwrap(throwable);
                if (stale != null) {
                    this.plugin.getLogger().severe("ADMIN REMOVE LOST THE RACE - item " + item.getId() + " admin " + admin.getName() + " : the row was already taken by another server, the item was handed over locally anyway, manual check required");
                    message(this.plugin, admin, Message.ITEM_NO_LONGER_AVAILABLE);
                } else {
                    this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to persist admin removal of item " + item.getId(), throwable);
                }
            });

// --- l.883 / l.893, purchaseAuctionItem ---
        if (purchasedConfiguration.giveItem()) {
            updateFuture = storageManager.updateItem(auctionItem, StorageType.LISTED, StorageType.DELETED);
            giveItem(player, auctionItem);
        } else {
            var expiration = configuration.getPurchaseExpiration().getExpiration(player);
            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            auctionItem.setExpiredAt(new Date(expiredAt));

            addItem(StorageType.PURCHASED, auctionItem);
            updateFuture = storageManager.updateItem(auctionItem, StorageType.LISTED, StorageType.PURCHASED);
        }

        // L'argent a deja bouge (l.802 / l.823) : si la ligne nous echappe ici, il faut la trace
        // complete pour un remboursement manuel. L'inversion commit -> argent est le chantier 2.
        updateFuture = updateFuture.whenComplete((v, throwable) -> {
            if (StaleItemException.unwrap(throwable) != null) {
                this.plugin.getLogger().severe("PURCHASE LOST THE RACE - item " + auctionItem.getId() + " buyer " + player.getUniqueId() + " (" + player.getName() + ") price " + buyerPays + " economy " + auctionEconomy.getName() + " : money was moved but the row was already taken by another server, manual refund required");
            }
        });

// ===== CommandAuctionAdminAdd.java, l.117 et l.129 =====
                    this.plugin.getStorageManager().updateItem(item, StorageType.LISTED, StorageType.EXPIRED);
                    // ...
                    this.plugin.getStorageManager().updateItem(item, StorageType.LISTED, StorageType.PURCHASED);

// ===== RemoveService.java : convergence au lieu de restauration =====
// import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
// import fr.maxlego08.zauctionhouse.api.storage.StaleItemException;

    private RemoveResult handleRemovalException(RemovalContext context, Throwable throwable, AuctionClusterBridge clusterBridge, Logger logger) {

        var stale = StaleItemException.unwrap(throwable);
        if (stale != null) {
            // Course perdue en base : la ligne ne portait plus l'etat source. Ne JAMAIS restaurer
            // AVAILABLE ici : ItemStatusListener (addon) applique le statut en aveugle puis
            // updateListedItems(item, true, null), ce qui remettrait en vente sur tout le cluster
            // un item deja vendu ailleurs. On converge vers la base.
            context.staleDetected = true;
            logger.warning("Removal lost the race for item " + context.item.getId() + " (" + stale.getMessage() + "), converging to database state");
            this.manager.removeItem(context.storageType, context.item.getId());
            this.manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SEARCH);
            if (context.player != null) message(this.plugin, context.player, Message.ITEM_NO_LONGER_AVAILABLE);
            context.result = RemoveResult.failure("Item already handled on another server", RemoveFailReason.ITEM_NOT_AVAILABLE);
        } else if (throwable.getCause() instanceof TimeoutException) {
            logger.warning("Removal operation timed out for item " + context.item.getId());
        } else if (throwable.getCause() instanceof IllegalStateException) {
            logger.warning("Removal unavailable for item " + context.item.getId() + ": " + throwable.getMessage());
        } else {
            logger.severe("Error during removal for item " + context.item.getId() + ": " + throwable.getMessage());
        }

        releaseLockOnError(context, clusterBridge, logger);
        restoreStatusOnError(context, clusterBridge);

        return context.result != null ? context.result : RemoveResult.failure("Internal error", RemoveFailReason.INTERNAL_ERROR);
    }

    private void restoreStatusOnError(RemovalContext context, AuctionClusterBridge clusterBridge) {
        // staleDetected : la base a tranche contre nous, restaurer l'ancien statut serait
        // rediffuser un etat que la base contredit.
        if (context.statusChanged && !context.localRemovalCompleted && !context.staleDetected) {
            context.item.setStatus(context.oldStatus);
            clusterBridge.notifyItemStatusChange(context.item, context.targetStatus, context.oldStatus);
        }
    }

// RemovalContext : ajouter les deux champs et le parametre player
//   final Player player;
//   boolean staleDetected;
// et propager `player` depuis executeRemoval(..., player, ...) vers le constructeur.

// ===== Message.java (API) : nouvelle constante, additive =====
    ITEM_NO_LONGER_AVAILABLE("<error>This item is no longer available, it has just been handled on another server."),

// ===== ItemRepository.java : nettoyage final =====
// Supprimer updateItem(Item, StorageType), updateItems(Map) et createUpdateSchema(Item, StorageType)
// une fois qu'aucun appelant ne les utilise plus (verifier par `grep -rn "updateItem(" src/`).
// Supprimer alors aussi les deux methodes correspondantes de ZStorageManager, mais CONSERVER
// les declarations de StorageManager (interface publiee).
```

### Ruptures d'API et stratégie de compatibilité

- AUCUNE rupture. Verifie par grep sur D:/Users/Maxlego08/workspace2.0/zAuctionHouse Redis : l'addon n'appelle ni updateItem, ni updateItems, ni updateStatus, ni ClaimService. Le pin `zauctionhousev4-api:deb8f16` n'a PAS besoin d'etre bumpe et l'addon deja deploye continue de tourner sans recompilation.
- Repository (classe abstraite publiee) : ajout de deux methodes `protected int updateReturning(...)`. Source- et binaire-compatible ; aucune sous-classe existante ne declare ces signatures (verifie sur les 6 repositories du projet).
- StorageManager (interface publiee) : ajout de deux methodes `default` — `updateItem(Item, StorageType from, StorageType to)` et `updateItems(Map, StorageType from)`. Les implementations tierces continuent de compiler et de fonctionner : le default delegue aux methodes historiques. Contrepartie explicitement documentee dans le javadoc : un implementeur tiers n'obtient PAS la garantie de compare-and-set tant qu'il n'a pas surcharge. Je REJETTE le correctif d'audit qui changeait `CompletableFuture<Void> updateItem(Item, StorageType)` en `CompletableFuture<Integer>` : le type de retour fait partie du descripteur JVM, c'est une rupture binaire dure.
- StorageManager.updateItem(Item, StorageType) et updateItems(Map) : signatures CONSERVEES a l'identique, marquees @Deprecated (javadoc) une fois le dernier appelant interne migre. Ne jamais les supprimer de l'interface.
- ItemRepository.updateItem(Item, StorageType) : marquee @Deprecated puis supprimee a l'etape 11 — classe interne au plugin (package storage.repository.repositories), hors module api/, aucun consommateur externe.
- Message (enum publie) : ajout de la constante ITEM_NO_LONGER_AVAILABLE. Additif ; aucun switch exhaustif sur Message n'existe dans les deux depots, donc aucune rupture binaire pour l'addon compile contre un SHA anterieur.
- StaleItemException : nouvelle classe publique dans api/storage. Additif. Un addon qui chaine un `.exceptionally` sur un future de StorageManager peut desormais distinguer une course perdue d'une panne SQL ; ceux qui ne le font pas voient simplement un echec la ou ils voyaient un faux succes.

### Migrations de schéma

- Table %prefix%transactions : ajout de deux colonnes NULLABLES — `claim_token` VARCHAR(36) et `claim_reserved_at` TIMESTAMP.
- MECANISME (point critique corrige par rapport a l'audit) : CreateTransactionsMigration doit passer de `create(...)` a `createOrAlter(...)`. Avec `create`, Migration.isAlter() vaut false et MigrationManager.execute sort par `if (!schema.getMigration().isAlter()) return;` (Sarah, MigrationManager:100-102) des que la migration est deja enregistree : la colonne ne serait JAMAIS ajoutee sur une installation existante et la reservation echouerait a l'execution avec une erreur SQL 'unknown column'. La contre-expertise de C-003 se trompe sur ce point.
- Aucune autre migration n'est modifiee. Aucun index n'est cree ici : l'index items(seller_unique_id, storage_type) et l'index transactions(player_unique_id, status) appartiennent au chantier PERF (C-066), et Sarah emet des CREATE INDEX SANS `IF NOT EXISTS` (CreateIndexRequest:26-36), ce qui peut empecher le demarrage — a ne pas glisser ici.
- Compatibilite descendante : les deux colonnes sont nullables, TransactionDTO (record) ne les declare pas et SchemaBuilder.transformResults mappe par composante de record et non par colonne du ResultSet ; un downgrade de jar les ignore purement et simplement.

### Changements de configuration

- RECTIFICATIF AU BRIEF : le plugin livre SIX jeux de configuration, pas quatre. `ls src/main/resources/` rend es/, fr/, id/, it/, th/ plus la racine anglaise. Le CLAUDE.md du projet est perime sur ce point.
- Nouvelle cle messages.yml `item-no-longer-available` (constante Message.ITEM_NO_LONGER_AVAILABLE), a placer dans le bloc des messages item-remove-* (autour de la l.74 du fichier racine). A REPLIQUER A L'IDENTIQUE dans les SIX fichiers : src/main/resources/messages.yml (EN, reference), fr/messages.yml, es/messages.yml, it/messages.yml, id/messages.yml, th/messages.yml.
- Textes proposes — EN: "<error>This item is no longer available, it has just been handled on another server." / FR: "<error>Cet objet n'est plus disponible, il vient d'etre traite sur un autre serveur." / ES: "<error>Este objeto ya no esta disponible, acaba de ser procesado en otro servidor." / IT: "<error>Questo oggetto non e piu disponibile, e appena stato gestito su un altro server." / ID et TH: a faire traduire, ne PAS laisser le texte anglais silencieusement.
- AUCUNE cle config.yml. Le delai de recuperation des reservations de claim est volontairement une constante Java (STALE_CLAIM_RECOVERY_MS = 5 min) precisement pour eviter une replication dans six fichiers ; s'il devait devenir configurable, ce serait `auto-claim.stale-claim-recovery-seconds` dans les six config.yml.
- Documentation Docusaurus (C:/Users/Admin/Desktop/groupez/documentation) : ajouter au changelog `# Unreleased` la mention du nouveau comportement — une operation peut desormais etre refusee avec le message ci-dessus quand un autre serveur est passe avant. EN (plugins/zauctionhouse/docs/) + FR (i18n/fr/docusaurus-plugin-content-docs-zauctionhouse/current/).

### Fichiers nouveaux

- API/src/main/java/fr/maxlego08/zauctionhouse/api/storage/StaleItemException.java

### Validation

- INVARIANT N°1 — aucune vente ecrasee. Apres une session de charge : `SELECT COUNT(*) FROM zauctionhousev4_items i JOIN zauctionhousev4_logs l ON l.item_id = i.id WHERE l.log_type = 'PURCHASE' AND i.storage_type NOT IN ('PURCHASED','DELETED');` doit rendre 0. Avant correctif ce compteur est non nul des qu'un retrait a croise une vente.
- COURSE D'ACHAT, deux serveurs, meme MySQL. Sur le noeud A, poser un point d'arret (ou un Thread.sleep temporaire) entre PurchaseService:127 et l'appel a purchaseItem. Pendant l'arret, executer a la main `UPDATE zauctionhousev4_items SET storage_type='PURCHASED', buyer_unique_id='<uuid-autre-joueur>' WHERE id=42;`. Relacher A. ATTENDU : log `PURCHASE LOST THE RACE - item 42 ...`, la ligne 42 INCHANGEE en base (`SELECT storage_type, buyer_unique_id FROM zauctionhousev4_items WHERE id=42` rend toujours PURCHASED + l'autre acheteur), et l'acheteur A voit le message item-no-longer-available. Avant correctif : la ligne est ecrasee et les deux joueurs ont l'item.
- COURSE DE RETRAIT. Idem mais sur RemoveService : `UPDATE ... SET storage_type='PURCHASED' WHERE id=43;` pendant qu'un retrait est en vol. ATTENDU : log `Removal lost the race for item 43`, AUCUN `notifyItemStatusChange(... -> AVAILABLE)` emis (le verifier dans les logs debug de l'addon sur le second noeud), l'item disparait des GUI des deux noeuds. Ce test est le garde-fou anti-regression du drapeau staleDetected : s'il echoue, l'item reapparait en vente sur le noeud B.
- MIGRATION SUR BASE EXISTANTE — le test le plus important du lot. Partir d'une base ou zauctionhousev4_migrations contient deja 'CreateTransactionsMigration'. Demarrer le nouveau jar. MySQL : `SHOW COLUMNS FROM zauctionhousev4_transactions LIKE 'claim%';` doit rendre 2 lignes. SQLite : `PRAGMA table_info(zauctionhousev4_transactions);` doit contenir claim_token et claim_reserved_at. Si ce test echoue, c'est que la migration est restee sur `create(...)` au lieu de `createOrAlter(...)` et TOUT le chantier claim est mort a l'execution.
- PORTABILITE DU setObject(null) — a executer sur les DEUX pilotes (mariadb-java-client 3.5.6 ET sqlite-jdbc). Provoquer un `releaseClaim` : configurer une economie inexistante dans une ligne PENDING (`UPDATE zauctionhousev4_transactions SET economy_name='ghost' WHERE id=X;`), lancer /ah claim. ATTENDU : `SELECT claim_token, claim_reserved_at FROM zauctionhousev4_transactions WHERE id=X;` rend NULL, NULL et status reste PENDING. Un echec ici signifie que le pilote refuse setObject(idx, null) et impose un setNull type — a corriger avant livraison.
- DOUBLE CLAIM. Joueur avec 3 lignes PENDING (2 economies). Declencher SIMULTANEMENT : clic sur ClaimButton + `/ah claim` en console-as-player + une reconnexion avec auto-claim actif. ATTENDU : le solde augmente exactement une fois du total ; `SELECT COUNT(*) FROM zauctionhousev4_transactions WHERE player_unique_id=? AND status='PENDING';` rend 0 ; deux des trois appels rendent ClaimResult.failure('A claim is already in progress') ou nothingToClaim.
- KILL -9 ENTRE RESERVATION ET PAIEMENT. Point d'arret dans doClaim juste apres reservePending, `taskkill /F` du serveur. Au redemarrage : `SELECT id, status, claim_token FROM zauctionhousev4_transactions WHERE player_unique_id=?;` montre status=PENDING et claim_token non nul (argent non perdu, non paye). Attendre 5 minutes, relancer /ah claim : ATTENDU log `Released 1 stale claim reservation(s)` puis paiement, exactement une fois.
- ECONOMIE SUPPRIMEE. Retirer une economie d'economies.yml alors que des lignes PENDING la referencent, /ah claim. ATTENDU : WARNING `Economy not found: X, N transaction(s) released back to PENDING` et les lignes TOUJOURS en PENDING. Avant correctif elles etaient marquees RETRIEVED : argent detruit.
- BATCH D'EXPIRATION. Generer 200 annonces (`/ah admin generate 200`), forcer leur expiration, et pendant le lot executer `UPDATE zauctionhousev4_items SET storage_type='PURCHASED', buyer_unique_id='<uuid>' WHERE id IN (id1,id2,id3);`. ATTENDU : log `3 item(s) lost the EXPIRED race, dropping local ghosts: [id1, id2, id3]`, ces 3 items absents de l'onglet expires du vendeur, et leurs lignes inchangees en base. Ce test valide aussi que le regroupement par forme de requete de updateItems ne produit pas un SQL faux (sans lui, l'UPDATE partirait avec les mauvaises colonnes).
- NON-REGRESSION MONO-SERVEUR, sans l'addon Redis, sur SQLite ET sur MySQL : cycle complet vendre -> acheter -> reclamer l'achat -> retirer une annonce -> laisser expirer -> reclamer l'expire -> /ah claim. Aucun message item-no-longer-available ne doit apparaitre, aucun WARNING 'lost the race' dans la console. Toute occurrence signale un `from` mal declare a l'etape 11 : c'est le mode de defaillance n°1 du chantier (un item fige, ni reclamable ni vendable).
- PILOTES ET SUCCESS_NO_INFO : activer `database.debug: true`, executer une expiration en lot et verifier dans les logs que le nombre de lignes rapporte par le batch est positif et egal a la taille du lot sur mariadb-java-client 3.5.6 comme sur sqlite-jdbc. Si un pilote rend -2, la relecture ciblee de findStale doit se declencher (visible par la requete SELECT ... WHERE id IN) sans jamais purger d'item legitime.

### Questions ouvertes pour le mainteneur

- CONFLIT TEXTUEL ASSUME AVEC LE CHANTIER 5 (C-009). Mon etape 11 introduit dans RemovalContext le drapeau `staleDetected` et l'utilise dans handleRemovalException / restoreStatusOnError. C-009 veut introduire un drapeau du MEME nom, alimente par une relecture PRE-emptive sous verrou. Les deux sont complementaires (le mien reagit apres l'echec de l'ecriture, le sien refuse avant), mais ils editent les memes methodes. Arbitrage a rendre : livrer ce chantier d'abord et faire reposer C-009 sur le drapeau existant. La contre-expertise de C-009 note d'ailleurs que sa relecture devient redondante une fois ce chantier livre et pourra etre retiree pour recuperer la requete.
- CONFLIT TEXTUEL ASSUME AVEC LES CHANTIERS 2 ET 4 SUR adminRemoveItem (ZAuctionManager:679-709). C-012, C-042, C-060 et C-112 veulent tous reecrire ce bloc. Je m'y limite volontairement au remplacement d'une seule instruction (le future jete devient chaine et garde). Recommandation : traiter adminRemoveItem comme UNE reecriture unique au chantier 4/5, en reprenant mon `updateItem(item, storageType, StorageType.DELETED)` tel quel.
- VOLET CLUSTER DE C-078 NON LIVRE ICI, decision a valider. Router les transitions vers DELETED par le patron verrou + relecture + diffusion coute 1 verrou, 3 requetes et 2 emprunts Jedis PAR ITEM ; sur une purge de 800 items en fin de conservation c'est exactement la tempete decrite par C-070. Je ne livre que le volet SQL (CAS + rowcount + purge du fantome memoire), qui ferme la duplication sans le cout. Question : accepte-t-on que la destruction d'items expires reste non diffusee au cluster jusqu'au chantier 7 (les autres noeuds gardent leur copie jusqu'a leur propre balayage) ?
- CHANGEMENT DE COMPORTEMENT VISIBLE PAR LES JOUEURS. Des operations qui reussissaient silencieusement en ecrasant la base echouent desormais avec un message. C'est l'objectif, mais sur un reseau ou des courses se produisent regulierement, les joueurs verront apparaitre un message qu'ils n'avaient jamais vu. A annoncer en tete de changelog. Question annexe : veut-on un message distinct entre 'un autre serveur est passe avant' et 'erreur interne', ou le message unique suffit-il ?
- TROU RESIDUEL DU CLAIM, a assumer explicitement. Si le depot reussit mais que finishClaim leve (base tombee entre les deux), les lignes restent reservees et releaseStaleReservations les rendra reclamables au bout de 5 minutes : le joueur peut alors etre paye DEUX FOIS. Impossible a fermer sans transaction englobante — Sarah n'en offre pas d'utilisable (chaque requete ouvre sa propre connexion). Le SEVERE 'CLAIM NOT CLOSED' rend l'incident auditable. Question : 5 minutes est-il le bon compromis entre 'le joueur recupere vite son argent apres un crash' et 'la fenetre de double paiement' ?
- ARBITRAGE QUE J'AI TRANCHE CONTRE L'AUDIT, a confirmer : l'audit propose de SUPPRIMER la relecture selectItem de PurchaseService:118-125 au motif que le CAS la rend redondante (gain annonce : 3 requetes par achat). Je l'ai conservee et durcie, parce qu'elle protege AVANT le debit alors que le CAS ne protege qu'APRES. Elle ne pourra etre supprimee qu'une fois le chantier 2 livre (ordre commit -> argent -> remise). Le mainteneur peut trancher autrement s'il accepte de debiter puis rembourser.
- MONO-SERVEUR AVEC BASE PARTAGEE SANS ADDON REDIS : cette configuration (plusieurs serveurs sur le meme MySQL, sans l'addon) est celle ou le CAS apporte le plus, puisque aucun verrou distribue n'existe. Faut-il la documenter comme desormais 'sure au niveau de la base mais toujours desynchronisee en memoire', ou continuer a la deconseiller ?
- SUPPRESSION DES METHODES DEPRECIEES : l'etape 11 prevoit de retirer ItemRepository.updateItem(Item, StorageType) une fois le dernier appelant migre, mais de CONSERVER les declarations dans l'interface publiee StorageManager. Confirmer qu'aucun plugin tiers connu n'appelle StorageManager.updateItem(item, storageType) — si un partenaire le fait, sa transition restera non gardee et pourra encore ecraser une vente.

### Retour arrière

["CODE (etapes 1-3 et 6-11) : integralement reversible par downgrade du jar. Aucun format de donnee n'est change, aucune ligne n'est ecrite differemment — seules des clauses WHERE supplementaires sont posees. Un serveur qui revient a l'ancien jar retrouve exactement le comportement anterieur (y compris ses duplications).", "SCHEMA (etapes 4-5) : les deux colonnes claim_token / claim_reserved_at sont NULLABLES et ne sont ni lues ni ecrites par l'ancien code (TransactionDTO ne les declare pas, l'INSERT de TransactionRepository.create ne les mentionne pas). Un downgrade de jar est donc SANS DANGER : ne surtout pas les supprimer. Si des lignes sont reservees au moment du downgrade (claim_token non nul, status PENDING), l'ancien code les ignore et les paie normalement : aucune perte d'argent, aucun blocage.", "NE JAMAIS executer `ALTER TABLE ... DROP COLUMN claim_token` tant qu'un seul noeud du reseau tourne avec le nouveau jar : la reservation echouerait avec une erreur SQL et tous les claims seraient refuses.", "PROCEDURE DE REPLI D'URGENCE SANS DOWNGRADE, si le CAS fige des items en production (symptome : messages item-no-longer-available sur des operations legitimes) : remplacer le corps de ZStorageManager.updateItem(Item, StorageType, StorageType) par une delegation vers l'ancien chemin non garde (`return updateItem(item, to);`) et redeployer. Un seul fichier, une seule ligne, la garde disparait sans toucher aux 10 sites d'appel ni au schema. C'est precisement pour rendre ce repli possible que la methode historique est conservee jusqu'a l'etape 11.", "DEPLOIEMENT PROGRESSIF : aucune contrainte d'ordre entre les noeuds. Un noeud ancien (UPDATE aveugle) et un noeud nouveau (UPDATE avec CAS) cohabitent sans incompatibilite — la garde est portee par chaque instruction SQL, pas par un protocole partage. Le seul effet est que la protection n'est effective que pour les ecritures emises par les noeuds a jour : mettre a jour tous les noeuds avant d'annoncer le correctif.", "SAUVEGARDE PREALABLE OBLIGATOIRE avant l'etape 4 (mysqldump de zauctionhousev4_transactions, ou copie du fichier .db en SQLite) : `createOrAlter` declenche un ALTER TABLE sur une table potentiellement volumineuse ; sous MySQL avec plusieurs millions de lignes, l'ALTER bloque l'etape de migration au demarrage."]


---

# Chantier 2 — Inverser l'ordre : commit → argent → remise, et rendre la vente atomique

Ce chantier retourne l'invariant central du plugin : aujourd'hui zAuctionHouse mute sa mémoire et remet physiquement l'item AVANT que l'écriture en base soit résolue, sur les 6 sites de `ZAuctionManager` (522-523, 568-569, 602-603, 636-637, 693-696, 883-884) et sur le chemin de vente (`SellService:105` retire les items de l'inventaire avant deux INSERT hors transaction). Après ce chantier, rien n'est muté ni remis tant que la ligne n'a pas bougé en base, et rien ne quitte l'inventaire du vendeur tant que son contenu n'est pas durablement écrit. La pièce maîtresse est un `giveItem` chaînable, confiné au thread de l'entité et tolérant aux ItemStack null, doublé d'une compensation : si la remise ne peut pas avoir lieu (joueur déconnecté, entité retirée sur Folia), la ligne est repositionnée dans un conteneur réclamable au lieu d'être perdue — l'inversion d'ordre ne troque donc pas une duplication contre une destruction. Le chantier est indivisible parce que les 13 constats qu'il couvre décrivent tous la même cause racine (`updateFuture = updateItem(...); giveItem(...);` et `removeItemsFromSlots(...); createAuctionItem(...);`) et parce que sept d'entre eux réécrivent littéralement les mêmes lignes. Deux constats de la fiche d'audit sont explicitement contredits et corrigés : C-039 (poser le drapeau avant le supplier devient faux et nuisible une fois l'ordre inversé) et C-043 (le DELETE de compensation n'échoue PAS sous InnoDB, la clé étrangère est ON DELETE CASCADE).

**Prérequis**

- AUCUN prérequis bloquant : les 9 étapes compilent et tournent sur le HEAD actuel (49571d9). Les points ci-dessous sont des compléments qui ferment des trous que ce chantier laisse ouverts, pas des blocages.
- Chantier 3 (C-015 / C-019 / C-032 / C-102 — couche monétaire) : après l'étape 3, l'échec de l'UPDATE d'achat ne duplique plus l'item, mais l'acheteur reste débité et le vendeur crédité (withdraw l.802 / deposit l.823 restent en amont du commit). C'est un progrès net (perte réversible à la main au lieu d'un dupe irréversible) mais le ticket support se déplace tant que `withdrawChecked` et la compensation monétaire ne sont pas livrés.
- Chantier 1 (C-026 — compare-and-set généralisé + propagation du rowcount) : mes `publishListing`, `restoreFromDeleted` et `recoverOrphanReservations` portent déjà leur propre garde `WHERE storage_type = ?` et lisent leur propre rowcount, sans toucher à `Repository`. Ils ne conflictent donc PAS avec C-026, mais `StorageManager.updateItem` reste `CompletableFuture<Void>` : un `updateItem` qui matche 0 ligne est encore un succès silencieux. C-026 est ce qui rend l'inversion d'ordre étanche en cluster.
- Chantier QUICK (C-012 + C-042 + C-060 + C-112 — réécriture complète de `adminRemoveItem`) : mon étape 4 ne fait que l'inversion d'ordre sur ce bloc et ne touche NI au test du jeton noop, NI à la relecture autoritaire, NI à la libération du verrou dans l'`exceptionally`. Zone de conflit textuel annoncée : ZAuctionManager.java:679-709.
- Chantier QUICK (C-045 — `return` → `continue` dans la boucle de `updateListedItems`, ZAuctionManager:956) : sans lui, les rafraîchissements de GUI déclenchés par mes étapes 2 et 3 ne touchent qu'une partie des spectateurs et toute validation manuelle multi-joueurs donne de faux négatifs.
- Chantier QUICK (C-054 — casser la récursion mutuelle `ItemStackUtils.safeDeserializeItemStack` ↔ `Base64ItemStack.decode`, et la NPE de `serializeItemStack:37`) : mon étape 1 attrape `Throwable` au décodage et mon étape 6 attrape `Throwable` à l'encodage, donc mes patchs sont corrects sans lui ; mais tant que C-054 n'est pas livré, un serveur 1.20.0-1.20.4 met en quarantaine des items parfaitement valides au lieu de les lire.
- Chantier 5 (C-009 — revalidation autoritaire sous verrou dans `RemoveService`) : mon étape 2 garantit que la mémoire ne diverge jamais de la base APRÈS le commit, elle ne garantit pas que la référence mémoire soumise au retrait était encore à jour AVANT.
- Chantier 8 (C-022 — politique de rejet de l'`asyncExecutor` à l'arrêt) : mes chaînes s'appuient toutes sur `plugin.getExecutorService()` ; pendant `onDisable`, une `RejectedExecutionException` fait échouer le future et déclenche mes compensations, ce qui est le bon comportement, mais C-022 les rend inutiles.

**Constats couverts** : C-014, C-005, C-039, C-083, C-053, C-038, C-011, C-020, C-043, C-067, C-037, C-030, C-082, C-080

*~870 LOC*

## 2.1 — Quarantaine des annonces illisibles au chargement + accesseurs null-safe (C-011 + C-038)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/utils/ItemLoaderUtils.java`  
**Risque** : MEDIUM  
**Constats** : C-011, C-038

C-011 et C-038 partagent un unique point de code : `ItemLoaderUtils.createAuctionItem` l.40, qui accepte une liste vide et propage les `null` rendus par `Base64ItemStack.decode`. Ils DOIVENT partir dans le même commit : livrer C-038 seul (rendre l'affichage null-safe) transformerait un déni de vente en VENTE PARTIELLE — un lot [ok, null, ok] deviendrait achetable et afficherait 2 items au lieu de 3. La quarantaine est volontairement non destructive : on journalise en SEVERE et on n'affiche pas, mais on ne touche PAS à la ligne en base (je rejette explicitement le correctif de la fiche C-011 qui propose de la passer à DELETED : dans un cluster de versions Minecraft mélangées, un nœud ancien effacerait définitivement des items valides pour les autres). La garde de nullité dans `ZStorageManager.selectItem` n'est pas optionnelle : sans elle, la revalidation sous verrou d'achat part en NPE dès le premier item mis en quarantaine.

```java
// === 1/4 — ItemLoaderUtils.java : remplacer createAuctionItem (l.39-50) ===

    /**
     * Construit une annonce a partir de sa ligne items et de ses contenus.
     *
     * @return l'annonce, ou {@code null} si elle doit etre mise en QUARANTAINE : une annonce sans
     * contenu, ou dont un ItemStack est illisible, ne doit JAMAIS etre publiee. Elle serait affichee
     * comme un lot normal et resterait achetable, l'acheteur payant le prix plein pour ne rien
     * recevoir. La ligne en base est laissee INTACTE : un decodage qui echoue peut etre transitoire
     * (cluster de versions Minecraft melangees), une purge automatique detruirait des items valides.
     */
    protected AuctionItem createAuctionItem(AuctionPlugin plugin, ItemDTO dto, String sellerName, List<AuctionItemDTO> currentAuctionItems, AuctionEconomy auctionEconomy) {

        if (currentAuctionItems == null || currentAuctionItems.isEmpty()) {
            plugin.getLogger().severe("[ZAH] Item #" + dto.id() + " has no content row in " + Tables.AUCTION_ITEMS
                    + ": quarantined, it will not be listed. The database row is left untouched.");
            return null;
        }

        List<ItemStack> itemStacks = new ArrayList<>(currentAuctionItems.size());
        for (AuctionItemDTO auctionItemDTO : currentAuctionItems) {

            ItemStack itemStack;
            try {
                itemStack = Base64ItemStack.decode(auctionItemDTO.itemstack());
            } catch (Throwable throwable) {
                // decode() ne rattrape que IOException/ClassNotFoundException : IllegalArgumentException
                // (Base64 invalide), ClassCastException et StackOverflowError (voie NMS legacy) remontent.
                itemStack = null;
                plugin.getLogger().severe("[ZAH] Unable to decode content #" + auctionItemDTO.id()
                        + " of item #" + dto.id() + ": " + throwable);
            }

            if (itemStack == null) {
                plugin.getLogger().severe("[ZAH] Item #" + dto.id() + " has an unreadable ItemStack (content #"
                        + auctionItemDTO.id() + "): quarantined, it will not be listed."
                        + " The database row is left untouched.");
                return null;
            }

            itemStacks.add(itemStack);
        }

        var auctionItem = new ZAuctionItem(plugin, dto.id(), dto.server_name(), dto.seller_unique_id(), sellerName, dto.price(), auctionEconomy, dto.created_at(), dto.expired_at(), itemStacks);
        auctionItem.setStatus(switch (dto.storage_type()) {
            case LISTED -> ItemStatus.AVAILABLE;
            case PURCHASED -> ItemStatus.PURCHASED;
            case EXPIRED -> ItemStatus.REMOVED;
            case DELETED -> ItemStatus.DELETED;
        });
        return auctionItem;
    }

// imports a ajouter dans ItemLoaderUtils.java :
//   import fr.maxlego08.zauctionhouse.api.storage.Tables;
//   import org.bukkit.inventory.ItemStack;
//   import java.util.ArrayList;


// === 2/4 — ItemLoaderUtils.java : dans createItems, remplacer le bras `case AUCTION` (l.84-95) ===

                case AUCTION -> {

                    var currentAuctionItems = getAuctionItems(auctionItems, dto.id());
                    var auctionItem = this.createAuctionItem(plugin, dto, sellerName, currentAuctionItems, optional.get());

                    if (auctionItem == null) {
                        // Item en quarantaine (contenu vide ou illisible) : deja journalise en SEVERE.
                        quarantined++;
                    } else {

                        if (buyerName != null) {
                            auctionItem.setBuyer(dto.buyer_unique_id(), buyerName);
                        }

                        categoryManager.applyCategories(auctionItem);

                        biConsumer.accept(dto.storage_type(), auctionItem);
                        amount++;
                    }
                }

// dans la meme methode : declarer `int quarantined = 0;` a cote de `int amount = 0;`,
// SUPPRIMER le `amount++;` qui se trouve en fin de boucle (l.104) — il comptait aussi les
// items BID/RENT non implementes et compterait desormais les items en quarantaine —
// et remplacer la ligne de fin par :

        performanceDebug.end("loadItems.processItems", processStartTime, "processed=" + amount + ", quarantined=" + quarantined);
        if (quarantined > 0) {
            plugin.getLogger().severe("[ZAH] " + quarantined + " listing(s) were quarantined and are NOT visible in the auction house."
                    + " Their database rows are intact; check the SEVERE lines above for the item ids.");
        }
        return new Result(amount, auctionItems.size(), 0, 0);


// === 3/4 — ZStorageManager.java : dans selectItem, bras `case AUCTION` (l.194-203) ===

                case AUCTION -> {

                    var auctionItems = with(AuctionItemRepository.class).select(List.of(String.valueOf(dto.id())));
                    var auctionItem = createAuctionItem(this.plugin, dto, sellerName, auctionItems, optionalAuctionEconomy.get());

                    // GARDE OBLIGATOIRE : createAuctionItem peut desormais rendre null (quarantaine).
                    // Sans elle, la revalidation sous verrou de PurchaseService:118 part en NPE.
                    if (auctionItem == null) return null;

                    if (dto.buyer_unique_id() != null) {
                        auctionItem.setBuyer(dto.buyer_unique_id(), with(PlayerRepository.class).select(dto.buyer_unique_id()));
                    }

                    return auctionItem;
                }


// === 4/4 — ZAuctionItem.java : les TROIS accesseurs qui parcourent itemStacks sans garde ===

// (a) buildItemStack, placeholder item_count (l.59) :
            if (needed.contains(ItemPlaceholder.ITEM_COUNT)) {
                placeholders.register("item_count", this.itemStacks.stream().filter(Objects::nonNull).map(ItemStack::getAmount).reduce(0, Integer::sum).toString());
            }

// (b) getItemDisplay : LES DEUX branches sont fautives (la fusion l.130 et le rendu l.140).
//     Remplacer le debut de la methode par :
        var currentItemStacks = this.itemStacks.stream().filter(Objects::nonNull).toList();
        if (configuration.mergeSimilar()) {
            List<ItemStack> merged = new ArrayList<>();
            for (ItemStack itemStack : currentItemStacks) {
                boolean canAdd = true;
                for (ItemStack currentItemStack : merged) {
                    if (currentItemStack.isSimilar(itemStack)) {
                        currentItemStack.setAmount(currentItemStack.getAmount() + itemStack.getAmount());
                        canAdd = false;
                        break;
                    }
                }
                if (canAdd) merged.add(itemStack.clone());
            }
            currentItemStacks = merged;
        }
//     (le reste de la methode est inchange)

// (c) getItemsAsString (l.156) :
    @Override
    public String getItemsAsString() {
        return this.itemStacks.stream().filter(Objects::nonNull).map(i -> "x" + i.getAmount() + " " + i.getType().name()).collect(Collectors.joining(", "));
    }

// import a ajouter dans ZAuctionItem.java : import java.util.Objects;


// === bonus, meme famille — ItemContentButton.java:44 ===
            return auctionItem.getItemStacks().stream().filter(Objects::nonNull).map(ItemStack::clone).toList();
// import a ajouter : import java.util.Objects;
```

## 2.2 — giveItem chaînable et confiné au thread d'entité + inversion commit→remise sur les 4 chemins de retrait (C-014, C-005, C-039, C-083)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : HIGH  
**Constats** : C-014, C-005, C-039, C-083

C'est le cœur du chantier et il est INDIVISIBLE : changer le contrat de `giveItem` sans inverser l'ordre laisserait `removeSellingItem` rendre son future avant que l'item soit dans l'inventaire (c'est exactement le risque de régression documenté par C-014). Je DIVERGE du correctif proposé par C-039 : il demande de positionner `localRemovalCompleted = true` AVANT d'invoquer le supplier, parce qu'aujourd'hui le supplier mute la mémoire dès sa première instruction. Une fois l'ordre inversé, le supplier ne mute plus RIEN avant que l'UPDATE soit confirmé — il n'y a donc plus aucune fenêtre à couvrir, et poser le drapeau en avance deviendrait faux (il interdirait le rollback de statut légitime quand c'est l'écriture DB qui échoue). Le rollback mémoire (`rollbackLocalRemoval`) proposé par C-005 devient lui aussi inutile : on ne mute qu'après le commit, donc il n'y a jamais rien à défaire. En contrepartie j'ajoute ce que ni C-005 ni C-014 ne prévoient : une compensation `restoreFromDeleted` qui repositionne la ligne dans un conteneur réclamable quand la remise physique n'a pas pu avoir lieu du tout — sans elle, l'inversion d'ordre échange une duplication contre une destruction pure et simple pour le joueur. C-083 est réglé en décidant la destination UNE seule fois, sur le thread du joueur, dans RemoveService, et en la transmettant par une surcharge de l'IMPLÉMENTATION (jamais de l'interface publiée `AuctionManager`, dont la signature `CompletableFuture<Void> removeListedItem` est gelée pour l'addon Redis).

```java
// ============================================================
// A — ItemRepository.java : la primitive de compensation
// ============================================================

    /**
     * Repositionne dans un conteneur RECLAMABLE une ligne deja passee a DELETED dont la remise
     * physique n'a finalement pas pu avoir lieu (joueur deconnecte, entite retiree sur Folia).
     * <p>
     * Volontairement SANS la garde `storage_type = LISTED` de {@code createUpdateSchema} : la source
     * est ici DELETED, pas LISTED. La garde de compare-and-set porte sur DELETED, ce qui rend
     * l'operation idempotente et sans effet si un autre noeud a deja bouge la ligne.
     *
     * @return le nombre de lignes reellement modifiees (1 = restauree, 0 = deja bougee ailleurs)
     */
    public int restoreFromDeleted(Item item, StorageType destination) {
        try {
            return SchemaBuilder.update(getTableName(), schema -> {
                schema.where("id", item.getId());
                schema.where("storage_type", StorageType.DELETED.name());
                schema.object("storage_type", destination.name());
                schema.object("expired_at", item.getExpiredAt());
            }).execute(this.connection, getLogger());
        } catch (SQLException exception) {
            // Sarah leve en realite une DatabaseException (RuntimeException) : ce catch est un
            // filet de compilation, il ne doit jamais avaler l'echec silencieusement.
            throw new IllegalStateException("Unable to restore item " + item.getId() + " from DELETED", exception);
        }
    }

// imports a ajouter dans ItemRepository.java :
//   import fr.maxlego08.sarah.SchemaBuilder;
//   import java.sql.SQLException;


// ============================================================
// B — ZAuctionManager.java : giveItem chainable + compensation
// ============================================================

// Constante de classe, a cote des autres champs :

    /**
     * Filet temporel de la remise physique. Sur Folia, si l'entite est retiree APRES la
     * planification, FoliaLib 0.5.1 ne complete jamais le future de la tache : sans ce filet
     * la chaine de retrait resterait en vol pour toujours. 15 s est tres au-dessus du delai
     * reel (1 tick) et ne se declenche donc jamais pour un joueur en ligne.
     */
    private static final long GIVE_ITEM_TIMEOUT_SECONDS = 15L;

    /**
     * Resultat d'une remise physique.
     *
     * @param executed  true si la tache a reellement tourne sur le thread de l'entite
     * @param delivered nombre d'ItemStack effectivement remis (ou deposes au sol)
     * @param total     nombre d'ItemStack que portait l'annonce
     */
    public record GiveResult(boolean executed, int delivered, int total) {

        public static GiveResult notExecuted(int total) {
            return new GiveResult(false, 0, total);
        }

        /** @return true si RIEN n'a ete remis : le lot doit etre rendu reclamable en base. */
        public boolean isNothingDelivered() {
            return this.delivered == 0;
        }
    }

// REMPLACER integralement `public void giveItem(Player, Item)` (l.926-935) par :

    /**
     * Remet le contenu d'une annonce dans l'inventaire d'un joueur.
     * <p>
     * La remise est TOUJOURS executee sur le thread de l'entite : c'est obligatoire sur Folia, et
     * c'est deja necessaire sur Paper puisque la chaine d'achat bascule sur ForkJoinPool.commonPool
     * des `ZStorageManager.selectItem` et la chaine de retrait sur l'executor de la base.
     * <p>
     * Le future rendu n'est JAMAIS complete exceptionnellement : un echec se lit dans le
     * {@link GiveResult}. La boucle ne s'interrompt plus au premier ItemStack null ou fautif,
     * ce qui evite qu'un lot partiellement corrompu soit detruit a moitie.
     */
    public CompletableFuture<GiveResult> giveItem(Player player, Item item) {

        if (!(item instanceof AuctionItem auctionItem)) {
            this.plugin.getLogger().severe("[ZAH] give item not implemented for item #" + item.getId());
            return CompletableFuture.completedFuture(GiveResult.notExecuted(0));
        }

        var itemStacks = auctionItem.getItemStacks();
        final int total = itemStacks == null ? 0 : itemStacks.size();
        if (total == 0) {
            this.plugin.getLogger().severe("[ZAH] Item #" + item.getId() + " has no content to give to " + player.getName());
            return CompletableFuture.completedFuture(GiveResult.notExecuted(0));
        }

        var future = new CompletableFuture<GiveResult>();
        var settled = new AtomicBoolean(false);

        var scheduled = this.plugin.getScheduler().runAtEntity(player, wrappedTask -> {

            // Le filet temporel ci-dessous a pu abandonner la remise et declencher la compensation :
            // dans ce cas il ne faut SURTOUT pas remettre les items, on dupliquerait le lot.
            if (!settled.compareAndSet(false, true)) {
                this.plugin.getLogger().severe("[ZAH] Give aborted for item #" + item.getId()
                        + ": the delivery was already compensated, the item is claimable again.");
                return;
            }

            if (!player.isOnline()) {
                future.complete(GiveResult.notExecuted(total));
                return;
            }

            int delivered = 0;
            try {
                for (ItemStack itemStack : itemStacks) {
                    if (itemStack == null) {
                        this.plugin.getLogger().severe("[ZAH] Null ItemStack in item #" + item.getId() + ", skipped.");
                        continue;
                    }
                    try {
                        player.getInventory().addItem(itemStack.clone())
                                .forEach((slot, dropItemStack) -> player.getWorld().dropItem(player.getLocation(), dropItemStack));
                        delivered++;
                    } catch (Throwable throwable) {
                        this.plugin.getLogger().severe("[ZAH] Unable to give a stack of item #" + item.getId() + ": " + throwable);
                    }
                }
            } finally {
                future.complete(new GiveResult(true, delivered, total));
            }
        });

        // Filet 1 : la tache n'a pas pu etre planifiee (ENTITY_RETIRED / SCHEDULER_RETIRED sur Folia).
        scheduled.whenComplete((result, throwable) -> {
            if (throwable == null && result == EntityTaskResult.SUCCESS) return;
            if (settled.compareAndSet(false, true)) future.complete(GiveResult.notExecuted(total));
        });

        // Filet 2 : l'entite est retiree APRES la planification, personne ne complete le future.
        this.plugin.getScheduler().runLaterAsync(wrappedTask -> {
            if (settled.compareAndSet(false, true)) {
                this.plugin.getLogger().severe("[ZAH] Give timed out for item #" + item.getId()
                        + " (player " + player.getName() + "), the item will be made claimable again.");
                future.complete(GiveResult.notExecuted(total));
            }
        }, GIVE_ITEM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        return future;
    }

    /**
     * Remet le lot au joueur APRES que l'ecriture en base a ete confirmee, et compense si la remise
     * n'a pas pu avoir lieu DU TOUT : la ligne est repositionnee dans {@code claimableStorage} au
     * lieu d'etre perdue. C'est ce qui empeche l'inversion d'ordre d'echanger une duplication
     * contre une destruction.
     * <p>
     * Ce future ne se termine JAMAIS en erreur : passe l'ecriture en base le retrait est commis et
     * ne doit plus jamais pouvoir etre rapporte en echec.
     *
     * @return true si le lot a effectivement ete remis au joueur
     */
    private CompletableFuture<Boolean> deliverOrRestore(Player player, Item item, StorageType claimableStorage) {

        return giveItem(player, item).thenCompose(giveResult -> {

            if (!giveResult.isNothingDelivered()) return CompletableFuture.completedFuture(Boolean.TRUE);

            this.plugin.getLogger().severe("[ZAH] Item #" + item.getId() + " could not be delivered to "
                    + player.getName() + ", restoring it as " + claimableStorage + " so it stays claimable.");

            return restoreClaimable(item, claimableStorage).thenApply(restored -> {
                if (!Boolean.TRUE.equals(restored)) {
                    this.plugin.getLogger().severe("[ZAH] CRITICAL: item #" + item.getId()
                            + " is neither delivered nor claimable, a manual restore is required.");
                }
                return Boolean.FALSE;
            });

        }).exceptionally(throwable -> {
            this.plugin.getLogger().severe("[ZAH] Unexpected error while delivering item #" + item.getId() + ": " + throwable);
            return Boolean.FALSE;
        });
    }

    private CompletableFuture<Boolean> restoreClaimable(Item item, StorageType claimableStorage) {

        var storageManager = this.plugin.getStorageManager();

        return CompletableFuture.supplyAsync(() -> {
            try {
                return storageManager.with(ItemRepository.class).restoreFromDeleted(item, claimableStorage) == 1;
            } catch (Throwable throwable) {
                this.plugin.getLogger().severe("[ZAH] Unable to restore item #" + item.getId() + ": " + throwable);
                return Boolean.FALSE;
            }
        }, this.plugin.getExecutorService()).thenCompose(restored -> {

            if (!Boolean.TRUE.equals(restored)) return CompletableFuture.completedFuture(Boolean.FALSE);

            // La mutation du store et des index par proprietaire (IntArrayList non thread-safe)
            // repasse par le thread principal.
            var applied = new CompletableFuture<Boolean>();
            this.plugin.getScheduler().runNextTick(wrappedTask -> {
                item.setStatus(claimableStorage == StorageType.PURCHASED ? ItemStatus.PURCHASED : ItemStatus.REMOVED);
                addItem(claimableStorage, item);
                applied.complete(Boolean.TRUE);
            });
            return applied;
        });
    }

// imports a ajouter dans ZAuctionManager.java :
//   import com.tcoded.folialib.enums.EntityTaskResult;
//   import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
//   import java.util.concurrent.TimeUnit;
//   import java.util.concurrent.atomic.AtomicBoolean;


// ============================================================
// C — ZAuctionManager.java : les 4 chemins de retrait reordonnes
// ============================================================

    /**
     * Decide UNE SEULE FOIS, sur le thread du joueur, ou doit aller une annonce retiree de la vente.
     * <p>
     * Cette decision etait faite DEUX fois (RemoveService:63 pour ce qui est diffuse au cluster,
     * ZAuctionManager:520 pour ce qui est ecrit en base) avec, entre les deux, plusieurs sauts
     * asynchrones : un inventaire qui se remplit entre-temps suffisait a les faire diverger.
     * `canReceiveItem` lit `player.getInventory().firstEmpty()`, cet appel DOIT rester sur le
     * thread du joueur.
     */
    public StorageType resolveListedDestination(Player player, Item item) {
        var listedConfig = this.plugin.getConfiguration().getActions().listed();
        return (listedConfig.giveItem() && item.canReceiveItem(player)) ? StorageType.DELETED : StorageType.EXPIRED;
    }

    @Override
    public CompletableFuture<Void> removeListedItem(Player player, Item item) {
        return removeListedItem(player, item, resolveListedDestination(player, item)).thenApply(delivered -> null);
    }

    /**
     * ORDRE : ecriture en base D'ABORD, mutation memoire et remise physique ENSUITE.
     * <p>
     * Tant que l'UPDATE n'est pas confirme, RIEN n'est mute localement : en cas d'echec la memoire
     * et la base restent d'accord (la ligne est toujours LISTED) et RemoveService restaure
     * proprement le statut. Il n'y a donc aucun rollback memoire a ecrire.
     *
     * @param destination decidee par l'appelant via {@link #resolveListedDestination}
     * @return true si le lot a effectivement ete remis au joueur
     */
    public CompletableFuture<Boolean> removeListedItem(Player player, Item item, StorageType destination) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        if (destination == StorageType.EXPIRED) {
            var expiration = configuration.getExpireExpiration().getExpiration(player);
            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            item.setExpiredAt(new Date(expiredAt));
        }

        return storageManager.updateItem(item, destination).thenCompose(v -> {

            item.setStatus(destination == StorageType.EXPIRED ? ItemStatus.REMOVED : ItemStatus.DELETED);
            removeItem(StorageType.LISTED, item);
            if (destination == StorageType.EXPIRED) addItem(StorageType.EXPIRED, item);

            this.updateListedItems(item, false, player);
            clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);

            message(this.plugin, player, Message.ITEM_REMOVE_LISTED, "%items%", item.getItemDisplay());

            if (configuration.getActions().listed().openInventory()) {
                openMainAuction(player, getCache(player).get(PlayerCacheKey.CURRENT_PAGE, 1));
            } else {
                this.plugin.getScheduler().runAtEntity(player, w -> {
                    if (player.isOnline()) player.closeInventory();
                });
            }

            callEvent(new AuctionRemoveListedItemEvent(item, player));
            logItemAction(LogType.REMOVE_LISTED, item, player, null, "removed_from_listed");

            if (destination != StorageType.DELETED) return CompletableFuture.completedFuture(Boolean.FALSE);
            return deliverOrRestore(player, item, StorageType.EXPIRED);
        });
    }

    @Override
    public CompletableFuture<Void> removeSellingItem(Player player, Item item) {
        return removeSellingItem(player, item, true).thenApply(delivered -> null);
    }

    public CompletableFuture<Boolean> removeSellingItem(Player player, Item item, boolean updatePlayer) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        return storageManager.updateItem(item, StorageType.DELETED).thenCompose(v -> {

            item.setStatus(ItemStatus.DELETED);
            removeItem(StorageType.LISTED, item);

            this.updateListedItems(item, false, player);
            clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);

            if (updatePlayer) {
                message(this.plugin, player, Message.ITEM_REMOVE_SELLING, "%items%", item.getItemDisplay());

                if (configuration.getActions().listed().openInventory()) {
                    this.updateInventory(player);
                } else {
                    this.plugin.getScheduler().runAtEntity(player, w -> {
                        if (player.isOnline()) player.closeInventory();
                    });
                }
            }

            callEvent(new AuctionRemoveListedItemEvent(item, player));
            logItemAction(LogType.REMOVE_SELLING, item, player, null, updatePlayer ? "removed_selling_item" : "removed_selling_item_bulk");

            return deliverOrRestore(player, item, StorageType.EXPIRED);
        });
    }

    @Override
    public CompletableFuture<Void> removeExpiredItem(Player player, Item item) {
        return removeExpiredItem(player, item, true).thenApply(delivered -> null);
    }

    public CompletableFuture<Boolean> removeExpiredItem(Player player, Item item, boolean updatePlayer) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        return storageManager.updateItem(item, StorageType.DELETED).thenCompose(v -> {

            item.setStatus(ItemStatus.DELETED);
            removeItem(StorageType.EXPIRED, item);
            clearPlayerCache(player, PlayerCacheKey.ITEMS_EXPIRED);

            if (updatePlayer) {
                message(this.plugin, player, Message.ITEM_REMOVE_EXPIRED, "%items%", item.getItemDisplay());

                if (configuration.getActions().expired().openInventory()) {
                    this.updateInventory(player);
                } else {
                    this.plugin.getScheduler().runAtEntity(player, w -> {
                        if (player.isOnline()) player.closeInventory();
                    });
                }
            }

            callEvent(new AuctionRemoveExpiredItemEvent(item, player));
            logItemAction(LogType.REMOVE_EXPIRED, item, player, null, updatePlayer ? "removed_expired_item" : "removed_expired_item_bulk");

            // Compensation vers le conteneur d'ORIGINE : l'item redevient reclamable la ou il etait.
            return deliverOrRestore(player, item, StorageType.EXPIRED);
        });
    }

    @Override
    public CompletableFuture<Void> removePurchasedItem(Player player, Item item) {
        return removePurchasedItem(player, item, true).thenApply(delivered -> null);
    }

    public CompletableFuture<Boolean> removePurchasedItem(Player player, Item item, boolean updatePlayer) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        return storageManager.updateItem(item, StorageType.DELETED).thenCompose(v -> {

            item.setStatus(ItemStatus.DELETED);
            removeItem(StorageType.PURCHASED, item);
            clearPlayerCache(player, PlayerCacheKey.ITEMS_PURCHASED);

            if (updatePlayer) {
                message(this.plugin, player, Message.ITEM_REMOVE_PURCHASED, "%items%", item.getItemDisplay());

                if (configuration.getActions().purchased().openInventory()) {
                    this.updateInventory(player);
                } else {
                    this.plugin.getScheduler().runAtEntity(player, w -> {
                        if (player.isOnline()) player.closeInventory();
                    });
                }
            }

            callEvent(new AuctionRemovePurchasedItemEvent(item, player));
            logItemAction(LogType.REMOVE_PURCHASED, item, player, item.getSellerUniqueId(), updatePlayer ? "removed_purchased_item" : "removed_purchased_item_bulk");

            return deliverOrRestore(player, item, StorageType.PURCHASED);
        });
    }


// ============================================================
// D — RemoveService.java : propager la destination et le fait que l'item a bien ete remis
// ============================================================

// (a) RemovalContext : changer le type du supplier et ajouter le drapeau de remise
        final Supplier<CompletableFuture<Boolean>> onLocalRemoval;
        ...
        boolean statusChanged;
        boolean localRemovalCompleted;
        boolean itemDelivered;
        RemoveResult result;
// (adapter la signature du constructeur de RemovalContext et celle de executeRemoval)

// (b) executeRemoval : signature
    private CompletableFuture<RemoveResult> executeRemoval(ItemStatus targetStatus, Player player, Item item, Runnable onUnavailable, Supplier<CompletableFuture<Boolean>> onLocalRemoval, StorageType storageType, StorageType destinationStorageType) {

// (c) removeListedItem (l.62-65) : la destination est calculee UNE fois et transmise
        // C-083 : une seule evaluation de `giveItem() && canReceiveItem(player)`, sur le thread du
        // joueur. Ce qui est diffuse au cluster et ce qui est ecrit en base ne peuvent plus diverger.
        StorageType destination = this.manager.resolveListedDestination(player, item);

        return executeRemoval(ItemStatus.IS_BEING_REMOVED, player, item, () -> manager.updateInventory(player), () -> this.manager.removeListedItem(player, item, destination), StorageType.LISTED, destination);

// (d) les trois autres appels a executeRemoval pointent desormais sur les surcharges Boolean :
//        () -> this.manager.removeSellingItem(player, item, updatePlayer)
//        () -> this.manager.removeExpiredItem(player, item, updatePlayer)
//        () -> this.manager.removePurchasedItem(player, item, updatePlayer)
//     (aucune autre modification : ces methodes rendent maintenant CompletableFuture<Boolean>)

// (e) executeLocalRemovalStep : le drapeau devient un invariant fiable
    private CompletableFuture<Void> executeLocalRemovalStep(RemovalContext context, AuctionClusterBridge clusterBridge, PerformanceConfiguration config) {

        return context.onLocalRemoval.get().thenCompose(delivered -> {
            // A ce point la ligne en base a DEJA bouge et le lot a ete remis (ou rendu reclamable) :
            // le drapeau signifie desormais reellement "point de non-retour franchi".
            context.localRemovalCompleted = true;
            context.itemDelivered = Boolean.TRUE.equals(delivered);
            return clusterBridge.removeItem(context.item, context.storageType, context.destinationStorageType).orTimeout(config.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS);
        });
    }

// (f) unlockAndCompleteStep : ne plus mentir sur itemGiven
            context.result = RemoveResult.success("Item removed successfully", context.itemDelivered);
```

## 2.3 — Achat : commit de la ligne avant toute mutation mémoire et avant la remise (C-005, site 883-884)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : HIGH  
**Constats** : C-005, C-014

Sixième et dernier site de la cause racine. Aujourd'hui `purchaseAuctionItem` mute `setBuyer`, `setStatus(PURCHASED)`, `removeItem(LISTED)`, les caches, puis lance l'UPDATE et remet l'item : si l'UPDATE échoue, l'acheteur a l'item et la ligne est restée LISTED — c'est une duplication franche, sans aucun cluster, en mono-serveur. Après ce patch, seuls `setBuyer` et `setExpiredAt` précèdent l'écriture (le schéma d'UPDATE lit `getBuyerUniqueId()` et `getExpiredAt()`, ils sont la charge utile), tout le reste est chaîné derrière le commit. Je NE touche PAS à `withdraw` (l.802), `deposit` (l.823) ni au bloc `createTransaction` (l.835-852) : la couche monétaire appartient au chantier 3 (C-015/C-019/C-032) et au chantier 1 (C-027), et déplacer ces lignes ici garantirait un conflit textuel avec eux. Conséquence à assumer et à écrire dans le changelog : après un échec DB, l'acheteur reste débité sans rien recevoir. C'est une perte réversible à la main, strictement préférable à un dupe irréversible, mais le ticket support ne disparaît qu'avec le chantier 3.

```java
// ZAuctionManager.java — REMPLACER tout le bloc a partir de
// `if (seller.isOnline()) { ... ITEM_BOUGHT_SELLER ... }` (l.854) jusqu'au `return updateFuture;`
// final de purchaseAuctionItem (l.916) par :

        // ------------------------------------------------------------------
        // A partir d'ici : la ligne en base bouge AVANT toute mutation memoire
        // et AVANT toute remise physique.
        //
        // `setBuyer` et `setExpiredAt` sont les seules mutations qui precedent l'ecriture,
        // parce qu'ils sont la CHARGE UTILE de l'UPDATE (createUpdateSchema lit
        // getBuyerUniqueId() et getExpiredAt()).
        //
        // NOTE : le debit de l'acheteur (l.802) et le credit du vendeur (l.823) restent en
        // amont. Apres un echec de l'UPDATE l'acheteur est debite sans rien recevoir : c'est
        // une perte reversible a la main, la ou l'ordre precedent produisait une DUPLICATION
        // irreversible. La compensation monetaire appartient au chantier 3.
        // ------------------------------------------------------------------

        auctionItem.setBuyer(player);

        var purchasedConfiguration = configuration.getActions().purchased();
        final StorageType destination;

        if (purchasedConfiguration.giveItem()) {
            destination = StorageType.DELETED;
        } else {
            var expiration = configuration.getPurchaseExpiration().getExpiration(player);
            long purchaseExpiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            auctionItem.setExpiredAt(new Date(purchaseExpiredAt));
            destination = StorageType.PURCHASED;
        }

        final BigDecimal displayedBuyerPays = buyerPays;
        final BigDecimal displayedSellerReceives = sellerReceives;

        return storageManager.updateItem(auctionItem, destination).thenCompose(v -> {

            // --- 1. Mutations memoire (la base a confirme) ---
            auctionItem.setStatus(destination == StorageType.PURCHASED ? ItemStatus.PURCHASED : ItemStatus.DELETED);
            removeItem(StorageType.LISTED, auctionItem);
            if (destination == StorageType.PURCHASED) addItem(StorageType.PURCHASED, auctionItem);

            this.updateListedItems(auctionItem, false, player);
            clearPlayerCache(player, PlayerCacheKey.ITEMS_PURCHASED);
            if (seller.isOnline()) {
                var sellerPlayer = seller.getPlayer();
                if (sellerPlayer != null) {
                    clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.HISTORY_DATA, PlayerCacheKey.PENDING_MONEY_DATA);
                }
            }

            // --- 2. Notifications (l'achat est acquis, on peut l'annoncer) ---
            if (seller.isOnline()) {
                var sellerPlayer = seller.getPlayer();
                if (sellerPlayer != null) {
                    message(this.plugin, sellerPlayer, Message.ITEM_BOUGHT_SELLER, "%items%", itemsDisplay, "%price%", economyManager.format(auctionEconomy, displayedSellerReceives), "%seller%", sellerName, "%buyer%", player.getName());
                }
            }
            message(player, Message.ITEM_BOUGHT_BUYER, "%items%", itemsDisplay, "%price%", economyManager.format(auctionEconomy, displayedBuyerPays), "%seller%", sellerName, "%buyer%", player.getName());

            cache.remove(PlayerCacheKey.ITEM_SHOW);
            if (purchasedConfiguration.openInventory()) {
                openMainAuction(player, cache.get(PlayerCacheKey.CURRENT_PAGE, 1));
            } else {
                this.plugin.getScheduler().runAtEntity(player, w -> {
                    if (player.isOnline()) player.closeInventory();
                });
            }

            logItemAction(LogType.PURCHASE, auctionItem, player, auctionItem.getSellerUniqueId(), "purchase_item", seller.isOnline() ? new Date() : null);

            if (this.plugin instanceof ZAuctionPlugin zAuctionPlugin) {
                DiscordWebhookService discordService = zAuctionPlugin.getDiscordWebhookService();
                if (discordService != null && discordService.isEnabled()) {
                    discordService.notifyItemPurchased(player, auctionItem);
                }
                zAuctionPlugin.getBroadcastService().broadcastPurchase(player, auctionItem);
            }

            // --- 3. Remise physique, chainee et compensee ---
            if (destination != StorageType.DELETED) return CompletableFuture.completedFuture(null);

            // Si la remise ne peut pas avoir lieu, la ligne redevient PURCHASED : l'acheteur
            // la reclamera dans son onglet "items achetes" au lieu de perdre son achat.
            return deliverOrRestore(player, auctionItem, StorageType.PURCHASED).thenApply(delivered -> null);
        });

// NOTE DE COMPILATION : supprimer la declaration `CompletableFuture<Void> updateFuture;` et les
// deux branches qui l'affectaient. Le `thenApply(delivered -> null)` final donne bien un
// CompletableFuture<Void>, type de retour inchange de purchaseAuctionItem.
```

## 2.4 — Retrait admin : chaîner l'écriture avant la remise et cesser de jeter le future (C-005, site 693-696)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : MEDIUM  
**Constats** : C-005

Dernier des 6 sites. Aujourd'hui le future de `updateItem` est jeté sur une ligne isolée (l.693) et `giveItem` est appelé juste après : l'admin reçoit l'item même si l'écriture échoue, et l'item reste listé pour tout le monde. Ce patch se limite STRICTEMENT à l'inversion d'ordre et à la compensation. Il ne touche ni au test du jeton noop (C-042), ni à la relecture autoritaire sous verrou (C-060), ni à la libération du verrou dans l'`exceptionally` (C-112), ni au passage de `removeItem` à la surcharge 3 arguments avec destination DELETED (C-012) — ces quatre constats appartiennent à d'autres chantiers et le verificateur recommande de traiter `adminRemoveItem` comme UNE réécriture unique. Zone de conflit textuel annoncée. Si le chantier QUICK passe avant, l'inversion ci-dessous doit être reportée dans leur réécriture : elle tient en deux lignes.

```java
// ZAuctionManager.java — dans adminRemoveItem, REMPLACER le `.thenAccept(lockToken -> { ... })`
// (l.689-704) par un `.thenCompose(...)` :

        }).thenCompose(lockToken -> clusterBridge.removeItem(item, storageType).thenApply(v -> lockToken)).thenCompose(lockToken -> {

            // ORDRE : l'ecriture en base D'ABORD. Tant que la ligne n'est pas passee a DELETED,
            // rien n'est mute en memoire et l'item n'est pas remis a l'administrateur. Le future
            // de l'UPDATE etait jusqu'ici jete sur une ligne isolee : un echec passait inapercu
            // et l'admin repartait avec un item toujours listed pour tout le reste du reseau.
            return this.plugin.getStorageManager().updateItem(item, StorageType.DELETED).thenCompose(v -> {

                removeItem(storageType, item);
                clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);

                var targetName = item.getSellerUniqueId().equals(targetUniqueId) ? item.getSellerName() : item.getBuyerName();
                message(this.plugin, admin, Message.ADMIN_ITEM_REMOVED, "%items%", item.getItemDisplay(), "%target%", targetName == null ? "unknown" : targetName);

                inventoryManager.updateInventory(admin);

                // Compensation vers le conteneur d'origine si la remise a l'admin echoue :
                // l'item reste reclamable par son proprietaire au lieu d'etre detruit.
                return deliverOrRestore(admin, item, storageType)
                        .thenCompose(delivered -> clusterBridge.unlockItem(item, lockToken, storageType));
            });

        }).exceptionally(e -> {
            this.plugin.getLogger().severe("Failed to remove item for admin: " + e.getMessage());
            inventoryManager.updateInventory(admin);
            return null;
        });
```

## 2.5 — Retrait en masse : ne plus abandonner le lot sur une erreur interne et dire la vérité au joueur (C-053)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : LOW  
**Constats** : C-053

C-053 se livre APRÈS les étapes 2 et 3, jamais avant : tant que `giveItem` précédait le commit, `localRemovalCompleted` ne signifiait pas « item remis » et la correction n°2 de l'audit aurait certifié au joueur un retrait dont la ligne DB était restée LISTED. Maintenant que le drapeau est un invariant, les deux volets sont sûrs. Volet 1 : seul `INSUFFICIENT_SPACE` doit interrompre la boucle — une erreur interne sur UNE annonce ne doit pas abandonner silencieusement les 99 suivantes. Volet 2 : `handleRemovalException` ne doit plus rapporter INTERNAL_ERROR quand le point de non-retour est franchi (échec de diffusion ou de déverrouillage APRÈS le commit) — sinon le joueur retente un retrait déjà effectué. Volet 3 : le joueur est informé du nombre d'annonces non traitées, ce que le code actuel passe entièrement sous silence.

```java
// ============================================================
// A — RemoveService.java : handleRemovalException (l.281-291)
// ============================================================

        releaseLockOnError(context, clusterBridge, logger);
        restoreStatusOnError(context, clusterBridge);

        // Passe le point de non-retour, l'operation est COMMISE : la ligne en base a bouge et le
        // lot a ete remis ou rendu reclamable. Un incident de transport survenu apres (diffusion
        // au cluster, deverrouillage) ne doit plus jamais etre rapporte en echec, sinon le joueur
        // retente un retrait deja effectue et le lot est compte a zero dans le retrait en masse.
        if (context.localRemovalCompleted) {
            logger.warning("Removal of item " + context.item.getId()
                    + " is committed but a post-commit step failed: " + throwable.getMessage());
            return RemoveResult.success("Item removed (post-commit transport failure)", context.itemDelivered);
        }

        return context.result != null ? context.result : RemoveResult.failure("Internal error", RemoveFailReason.INTERNAL_ERROR);


// ============================================================
// B — ZAuctionManager.java : processBulkItems / finishBulkRemoval
// ============================================================

    private CompletableFuture<BulkRemovalProgress> processBulkItems(Player player, List<Item> items, Function<Item, CompletableFuture<RemoveResult>> removal) {
        CompletableFuture<BulkRemovalProgress> future = CompletableFuture.completedFuture(new BulkRemovalProgress(0, 0, false));

        for (Item item : items) {
            future = future.thenCompose(progress -> {
                if (progress.stopped()) return CompletableFuture.completedFuture(progress);

                return runBulkRemovalOnPlayerThread(player, item, removal).handle((result, throwable) -> {

                    if (throwable != null) {
                        // Une annonce en echec ne doit PAS emporter tout le lot : on la compte comme
                        // traitee-non-rendue et on continue avec les suivantes.
                        this.plugin.getLogger().severe("Bulk removal failed for item " + item.getId() + ": " + throwable.getMessage());
                        return new BulkRemovalProgress(progress.given(), progress.processed() + 1, false);
                    }

                    boolean success = result != null && result.isSuccess() && result.isItemGiven();

                    // Seul un inventaire plein justifie d'arreter : continuer n'aurait aucun sens.
                    // INTERNAL_ERROR ne doit plus interrompre la chaine (C-053).
                    boolean stopped = result == null || result.getFailReason() == RemoveFailReason.INSUFFICIENT_SPACE;

                    return new BulkRemovalProgress(progress.given() + (success ? 1 : 0), progress.processed() + 1, stopped);
                });
            });
        }

        return future;
    }

    private void finishBulkRemoval(Player player, BulkRemovalProgress progress, int requested, boolean openInventory, PlayerCacheKey... cacheKeys) {
        this.plugin.getScheduler().runAtEntity(player, wrappedTask -> {
            clearPlayerCache(player, cacheKeys);

            int given = progress.given();
            if (given > 0) {
                message(this.plugin, player, Message.REMOVE_ALL_ITEMS, "%amount%", String.valueOf(given));
            }

            // Les annonces non rendues etaient jusqu'ici passees sous silence : le joueur croyait
            // avoir tout recupere alors que la chaine s'etait arretee au premier incident.
            int skipped = requested - given;
            if (skipped > 0) {
                message(this.plugin, player, Message.REMOVE_ALL_ITEMS_PARTIAL, "%amount%", String.valueOf(skipped));
            }

            if (!player.isOnline()) return;

            if (openInventory) {
                updateInventory(player);
            } else {
                player.closeInventory();
            }
        });
    }

    private record BulkRemovalProgress(int given, int processed, boolean stopped) { }

// --- les trois appelants ---

    @Override
    public void removeAllExpiredItems(Player player) {
        var items = new ArrayList<>(getExpiredItems(player));
        if (items.isEmpty()) return;

        processBulkItems(player, items, item -> this.auctionRemoveService.removeExpiredItem(player, item, false))
                .thenAccept(progress -> finishBulkRemoval(player, progress, items.size(), this.plugin.getConfiguration().getActions().expired().openInventory(), PlayerCacheKey.ITEMS_EXPIRED));
    }

    @Override
    public void removeAllSellingItems(Player player) {
        var items = new ArrayList<>(getPlayerSellingItems(player));
        if (items.isEmpty()) return;

        processBulkItems(player, items, item -> this.auctionRemoveService.removeSellingItem(player, item, false))
                .thenAccept(progress -> finishBulkRemoval(player, progress, items.size(), true, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED));
    }

    @Override
    public void removeAllPurchasedItems(Player player) {
        var items = new ArrayList<>(getPurchasedItems(player));
        if (items.isEmpty()) return;

        processBulkItems(player, items, item -> this.auctionRemoveService.removePurchasedItem(player, item, false))
                .thenAccept(progress -> finishBulkRemoval(player, progress, items.size(), this.plugin.getConfiguration().getActions().purchased().openInventory(), PlayerCacheKey.ITEMS_PURCHASED));
    }


// ============================================================
// C — API/.../api/messages/Message.java : constante additive
// ============================================================

    REMOVE_ALL_ITEMS_PARTIAL("<error>%amount% listing(s) could not be retrieved, please try again."),
```

## 2.6 — Infrastructure de vente atomique : réservation, publication, annulation (C-020, C-043)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/storage/repository/repositories/ItemRepository.java`  
**Risque** : MEDIUM  
**Constats** : C-020, C-043

Étape purement additive : elle compile et ne change AUCUN comportement (les nouvelles méthodes n'ont pas encore d'appelant), ce qui la rend relisable isolément. C-020 et C-043 sont le même défaut décrit deux fois sur la même méthode : un seul correctif. Je retiens la publication différée plutôt que la transaction Sarah, parce que `Transaction` est inutilisable ici (`InsertRequest` ouvre sa PROPRE connexion, chaque INSERT emprunterait une connexion différente du pool). Je CORRIGE une erreur de la fiche C-043 : elle affirme qu'un DELETE de compensation « échoue sous InnoDB si des lignes filles auction_items ont déjà été insérées ». C'est faux — `CreateAuctionItemMigration` déclare `foreignKey(Tables.ITEMS, "id", true)` et `SchemaBuilder` émet `ON DELETE CASCADE` : supprimer la ligne parente emporte ses contenus. `deleteReservation` est donc parfaitement valide et sert de nettoyage immédiat, la colonne `pending_publish` servant de filet pour le seul cas non couvert, le crash du process. `publishListing` et `restoreFromDeleted` lisent leur propre rowcount via `SchemaBuilder.update(...).execute(...)` (qui rend `getUpdateCount()`) sans toucher à `Repository` : aucun conflit avec le `updateReturning` du chantier 1.

```java
// ============================================================
// A — CreateItemMigration.java : colonne sentinelle de reservation
// ============================================================

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
            // 1 = annonce RESERVEE, pas encore publiee ; 0 = publiee ou cycle de vie normal ;
            // NULL = ligne anterieure a cette version, a ne JAMAIS considerer comme une reservation.
            // MigrationManager de Sarah ajoute automatiquement la colonne (nullable) sur les
            // installations existantes, aucune action administrateur n'est requise.
            table.integer("pending_publish").nullable();
            table.timestamps();
        });


// ============================================================
// B — ItemRepository.java : create parametre + publish + delete
// ============================================================

    public int create(UUID sellerUniqueId, ItemType itemType, BigDecimal price, long expiredAt, AuctionEconomy auctionEconomy) {
        return create(sellerUniqueId, itemType, price, expiredAt, auctionEconomy, StorageType.LISTED);
    }

    /**
     * Cree la ligne parente d'une annonce dans l'etat demande.
     * <p>
     * Avec {@link StorageType#DELETED} la ligne est RESERVEE : elle existe, elle porte deja son
     * vendeur, son prix et son economie, mais elle est invisible de tous les serveurs
     * ({@link #select()} et {@link #select(int)} filtrent DELETED). Elle ne devient une annonce
     * qu'apres {@link #publishListing(int)}, une fois son contenu insere.
     */
    public int create(UUID sellerUniqueId, ItemType itemType, BigDecimal price, long expiredAt, AuctionEconomy auctionEconomy, StorageType initialStorageType) {
        var expiredAtDate = new Date(expiredAt);
        var serverName = this.plugin.getConfiguration().getServerName();
        boolean reserved = initialStorageType == StorageType.DELETED;
        return insertSync(schema -> {
            schema.string("item_type", itemType.name());
            schema.uuid("seller_unique_id", sellerUniqueId);
            schema.string("economy_name", auctionEconomy.getName());
            schema.decimal("price", price);
            schema.object("expired_at", expiredAtDate);
            schema.object("storage_type", initialStorageType.name());
            schema.object("pending_publish", reserved ? 1 : 0);
            schema.string("server_name", serverName);
        });
    }

    /**
     * Publie une annonce reservee : DELETED -> LISTED, en compare-and-set.
     * <p>
     * L'annonce n'apparait dans aucun hotel des ventes, sur aucun serveur, tant que cet UPDATE
     * n'a pas eu lieu : une panne au milieu de la creation ne laisse plus une annonce VIDE et
     * ACHETABLE en base partagee, mais une simple reservation invisible.
     *
     * @return 1 si publiee, 0 si la reservation a disparu ou a deja ete publiee
     */
    public int publishListing(int itemId) {
        try {
            return SchemaBuilder.update(getTableName(), schema -> {
                schema.where("id", itemId);
                schema.where("storage_type", StorageType.DELETED.name());
                schema.where("pending_publish", 1);
                schema.object("storage_type", StorageType.LISTED.name());
                schema.object("pending_publish", 0);
            }).execute(this.connection, getLogger());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to publish listing " + itemId, exception);
        }
    }

    /**
     * Supprime une reservation qui n'a pas pu etre publiee.
     * <p>
     * La cle etrangere auction_items.item_id -> items.id est declaree ON DELETE CASCADE
     * (CreateAuctionItemMigration, troisieme argument de foreignKey a true) : les contenus deja
     * inseres partent avec la ligne parente. Contrairement a ce qu'affirme la fiche C-043,
     * ce DELETE n'echoue donc PAS sous InnoDB.
     */
    public int deleteReservation(int itemId) {
        return delete(schema -> {
            schema.where("id", itemId);
            schema.where("storage_type", StorageType.DELETED.name());
            schema.where("pending_publish", 1);
        });
    }


// ============================================================
// C — AuctionItemRepository.java : ecrire des contenus DEJA encodes
// ============================================================

    /**
     * @deprecated encode les ItemStack au moment de l'ecriture, donc APRES que la ligne parente est
     * commitee : un encodage qui rend null produit alors une annonce sans contenu. Utiliser la
     * surcharge qui recoit les charges utiles deja encodees et validees.
     */
    @Deprecated
    public AuctionItem create(UUID sellerUniqueId, String sellerName, int itemId, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        return create(sellerUniqueId, sellerName, itemId, price, expiredAt, itemStacks, itemStacks.stream().map(Base64ItemStack::encode).toList(), auctionEconomy);
    }

    public AuctionItem create(UUID sellerUniqueId, String sellerName, int itemId, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, List<String> encodedItemStacks, AuctionEconomy auctionEconomy) {

        // Aucun INSERT ne part si le lot n'est pas integralement serialisable : la colonne
        // itemstack est NOT NULL, et une chaine vide produirait une annonce achetable sans contenu.
        if (encodedItemStacks == null || encodedItemStacks.size() != itemStacks.size()) {
            throw new IllegalStateException("Encoded payload mismatch for item " + itemId);
        }
        for (String encoded : encodedItemStacks) {
            if (encoded == null || encoded.isEmpty()) {
                throw new IllegalStateException("Empty ItemStack payload for item " + itemId);
            }
        }

        for (String encoded : encodedItemStacks) {
            insert(schema -> {
                schema.object("item_id", itemId);
                schema.string("itemstack", encoded);
            });
        }
        return new ZAuctionItem(this.plugin, itemId, this.plugin.getConfiguration().getServerName(), sellerUniqueId, sellerName, price, auctionEconomy, new Date(), new Date(expiredAt), itemStacks);
    }


// ============================================================
// D — API/.../api/storage/StorageManager.java : 3 methodes DEFAULT (non cassantes)
// ============================================================

    /**
     * Reserve une annonce : la ligne parente est creee dans un etat NON PUBLIABLE et les contenus
     * sont inseres, mais l'annonce n'est visible d'aucun serveur tant que
     * {@link #publishAuctionItem(AuctionItem)} n'a pas ete appele.
     *
     * @param encodedItemStacks les contenus DEJA encodes ({@code Base64ItemStack}), dans le meme
     *                          ordre que {@code itemStacks} ; aucun element ne doit etre null
     * @implNote l'implementation par defaut delegue a {@link #createAuctionItem} : un StorageManager
     * tiers compile contre une version anterieure de l'API continue de fonctionner a l'identique,
     * sans le gain d'atomicite.
     */
    default CompletableFuture<AuctionItem> reserveAuctionItem(Player seller, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, List<String> encodedItemStacks, AuctionEconomy auctionEconomy) {
        return createAuctionItem(seller, price, expiredAt, itemStacks, auctionEconomy);
    }

    /**
     * Rend visible une annonce reservee.
     *
     * @return le nombre de lignes publiees : 1 = succes, 0 = la reservation n'existe plus
     */
    default CompletableFuture<Integer> publishAuctionItem(AuctionItem auctionItem) {
        return CompletableFuture.completedFuture(1);
    }

    /**
     * Annule une reservation qui n'a pas pu aboutir (les contenus partent en cascade).
     */
    default CompletableFuture<Void> cancelReservation(AuctionItem auctionItem) {
        return CompletableFuture.completedFuture(null);
    }


// ============================================================
// E — ZStorageManager.java : implementation reelle
// ============================================================

    @Override
    public CompletableFuture<AuctionItem> reserveAuctionItem(Player seller, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, List<String> encodedItemStacks, AuctionEconomy auctionEconomy) {
        return CompletableFuture.supplyAsync(() -> {
            int itemId = with(ItemRepository.class).create(seller.getUniqueId(), ItemType.AUCTION, price, expiredAt, auctionEconomy, StorageType.DELETED);
            // insertSync rend 0 quand le pilote ne remonte aucune cle generee : la vente doit
            // echouer proprement plutot que d'inserer des contenus orphelins sur l'item 0.
            if (itemId <= 0) throw new IllegalStateException("Unable to reserve a listing row (generated id = " + itemId + ")");
            return with(AuctionItemRepository.class).create(seller.getUniqueId(), seller.getName(), itemId, price, expiredAt, itemStacks, encodedItemStacks, auctionEconomy);
        }, this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<Integer> publishAuctionItem(AuctionItem auctionItem) {
        return CompletableFuture.supplyAsync(() -> with(ItemRepository.class).publishListing(auctionItem.getId()), this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<Void> cancelReservation(AuctionItem auctionItem) {
        return CompletableFuture.runAsync(() -> {
            int deleted = with(ItemRepository.class).deleteReservation(auctionItem.getId());
            if (deleted != 1) {
                this.plugin.getLogger().warning("[ZAH] Reservation " + auctionItem.getId() + " was already gone when cancelling.");
            }
        }, this.plugin.getExecutorService());
    }
```

## 2.7 — Vente : réserver avant de retirer, publier après, rembourser sur le bon thread (C-020, C-043, C-067, C-037, C-030, C-082)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/SellService.java`  
**Risque** : HIGH  
**Constats** : C-020, C-043, C-067, C-037, C-030, C-082

Ces six constats éditent tous le bloc `SellService.java:104-131` et ne PEUVENT PAS être commités séparément. Je vais plus loin que l'énoncé de l'audit sur un point décisif : l'audit garde `removeItemsFromSlots` AVANT la création de la ligne, et se contente de rendre la ligne invisible jusqu'à publication. Je réserve la ligne et j'insère les contenus AVANT de toucher à l'inventaire. Conséquence : si la réservation échoue, le joueur n'a strictement rien perdu et il n'y a même pas de remboursement à faire — la classe entière « items retirés puis INSERT en échec » disparaît, au lieu d'être rattrapée par un chemin de compensation. La seule fenêtre restante est publication-en-échec, traitée par `refundFailedSell` (sur le thread de l'entité, avec dépôt au sol du surplus, ce que le code actuel perd silencieusement — C-030) plus l'annulation de la réservation. C-037 est réglé structurellement : `postSell` migrant dans un `runNextTick` (C-082), le `whenComplete` chaîné sur la réservation ne voit plus jamais une exception de `postSell` et ne peut donc plus rembourser après commit. Le journal SEVERE porte impérativement `auctionItem.getId()`, seul moyen de réconcilier à la main. Correction annexe : `applySellTaxAsync` rendant null produit `INSUFFICIENT_FUNDS` alors que l'enum expose `INSUFFICIENT_FUNDS_FOR_TAX`, prévu exactement pour ce cas.

```java
// ============================================================
// A — REMPLACER integralement sellAuctionItems (l.42-138)
// ============================================================

    @Override
    public CompletableFuture<SellResult> sellAuctionItems(Player player, BigDecimal price, long expiredAt, Map<Integer, ItemStack> slotItems, AuctionEconomy auctionEconomy) {

        var validSlotItems = slotItems.entrySet().stream().filter(entry -> entry.getValue() != null && !entry.getValue().getType().isAir()).collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().clone()));

        if (validSlotItems.isEmpty()) {
            message(this.plugin, player, Message.SELL_ERROR_AIR);
            return CompletableFuture.completedFuture(SellResult.failure("No valid items to sell", SellFailReason.INVALID_ITEM));
        }

        List<ItemStack> itemsToSell = new ArrayList<>(validSlotItems.values());

        SellFailReason validationReason = this.validateItems(player, price, auctionEconomy, itemsToSell);
        if (validationReason != SellFailReason.NONE) {
            return CompletableFuture.completedFuture(SellResult.failure("Validation failed", validationReason));
        }

        // C-067 : on encode AVANT de toucher a l'inventaire ET avant tout INSERT. Un item non
        // serialisable est refuse au joueur, au lieu de lui etre retire puis rendu, et aucune ligne
        // ne part avec un contenu vide. Les objets encodes sont les CLONES qui seront stockes :
        // encoder ici, c'est encoder exactement ce qui sera ecrit, il n'y a pas de double encodage.
        List<String> encodedItems = encodeItems(itemsToSell);
        if (encodedItems == null) {
            message(this.plugin, player, Message.SELL_ERROR_INVALID_ITEM);
            return CompletableFuture.completedFuture(SellResult.failure("Item cannot be serialized", SellFailReason.INVALID_ITEM));
        }

        if (!verifyItemsInSlots(player, validSlotItems)) {
            message(this.plugin, player, Message.SELL_ERROR_CHANGE);
            return CompletableFuture.completedFuture(SellResult.failure("Items changed", SellFailReason.ITEMS_CHANGED));
        }

        CompletableFuture<SellResult> resultFuture = new CompletableFuture<>();
        resultFuture.completeOnTimeout(SellResult.failure("Operation timed out", SellFailReason.DATABASE_ERROR), 30, TimeUnit.SECONDS);

        applySellTaxAsync(player, price, itemsToSell, auctionEconomy).thenAccept(taxResult -> {

            if (taxResult == null) {
                // L'enum distingue les deux cas depuis toujours, le code renvoyait le mauvais.
                resultFuture.complete(SellResult.failure("Insufficient funds for tax", SellFailReason.INSUFFICIENT_FUNDS_FOR_TAX));
                return;
            }

            var storageManager = this.plugin.getStorageManager();

            // ----------------------------------------------------------------
            // ETAPE 1 : RESERVER. La ligne parente est creee en DELETED et les contenus sont
            // inseres. L'annonce n'est visible d'AUCUN serveur. Rien n'a encore quitte
            // l'inventaire du vendeur : si cette etape echoue, il n'a rien perdu.
            // ----------------------------------------------------------------
            storageManager.reserveAuctionItem(player, price, expiredAt, itemsToSell, encodedItems, auctionEconomy).whenComplete((auctionItem, reserveError) -> {

                if (reserveError != null || auctionItem == null) {
                    this.plugin.getLogger().severe("Unable to reserve the listing: " + reserveError);
                    refundSellTax(player, auctionEconomy, taxResult, "Refund sell tax (reservation failed)");
                    resultFuture.complete(SellResult.failure("Database error", SellFailReason.DATABASE_ERROR));
                    return;
                }

                // ------------------------------------------------------------
                // ETAPE 2 : sur le thread du joueur, verifier une derniere fois puis RETIRER.
                // ------------------------------------------------------------
                this.plugin.getScheduler().runAtEntity(player, task -> {

                    if (!player.isOnline() || !verifyItemsInSlots(player, validSlotItems)) {
                        if (player.isOnline()) message(this.plugin, player, Message.SELL_ERROR_CHANGE);
                        storageManager.cancelReservation(auctionItem);
                        refundSellTax(player, auctionEconomy, taxResult, "Refund sell tax (items changed)");
                        resultFuture.complete(SellResult.failure(player.isOnline() ? "Items changed" : "Player disconnected",
                                player.isOnline() ? SellFailReason.ITEMS_CHANGED : SellFailReason.PLAYER_DISCONNECTED));
                        return;
                    }

                    removeItemsFromSlots(player, validSlotItems);

                    // --------------------------------------------------------
                    // ETAPE 3 : PUBLIER. C'est ici, et seulement ici, que l'annonce devient
                    // visible et achetable sur tout le reseau.
                    // --------------------------------------------------------
                    storageManager.publishAuctionItem(auctionItem).whenComplete((rows, publishError) -> {

                        if (publishError != null || rows == null || rows != 1) {
                            this.plugin.getLogger().severe("Unable to publish listing " + auctionItem.getId()
                                    + " (rows=" + rows + "): " + publishError);
                            refundFailedSell(player, itemsToSell, auctionEconomy, taxResult);
                            storageManager.cancelReservation(auctionItem);
                            resultFuture.complete(SellResult.failure("Database error", SellFailReason.DATABASE_ERROR));
                            return;
                        }

                        // ----------------------------------------------------
                        // ETAPE 4 : APRES COMMIT. Plus aucun remboursement n'est possible ici :
                        // une exception de postSell ne doit JAMAIS rendre les items ni la taxe,
                        // l'annonce est publiee et achetable sur tout le reseau (C-037).
                        // postSell repasse par le thread principal : il mute les stores et les
                        // index IntArrayList non thread-safe (C-082).
                        // ----------------------------------------------------
                        this.plugin.getScheduler().runNextTick(tick -> {
                            try {
                                this.postSell(player, auctionItem, auctionEconomy, taxResult);
                            } catch (Throwable throwable) {
                                this.plugin.getLogger().severe("[ZAH] Listing " + auctionItem.getId()
                                        + " IS PUBLISHED but postSell failed, manual reconciliation may be needed: " + throwable);
                            }
                            resultFuture.complete(SellResult.success("Item listed successfully", auctionItem));
                        });
                    });
                });
            });

        }).exceptionally(throwable -> {
            this.plugin.getLogger().severe("Unable to check tax for sell: " + throwable.getMessage());
            resultFuture.complete(SellResult.failure("Tax calculation error", SellFailReason.TAX_ERROR));
            return null;
        });

        return resultFuture;
    }


// ============================================================
// B — trois helpers prives a ajouter dans SellService
// ============================================================

    /**
     * Encode tout le lot AVANT la moindre ecriture.
     *
     * @return les charges utiles, ou {@code null} si un seul item n'est pas serialisable.
     * On attrape {@code Throwable} : sur un serveur anterieur a 1.20.5 la voie NMS de
     * {@code ItemStackUtils} peut lever une NPE ou une StackOverflowError, pas seulement
     * une IOException.
     */
    private List<String> encodeItems(List<ItemStack> itemStacks) {
        List<String> encoded = new ArrayList<>(itemStacks.size());
        for (ItemStack itemStack : itemStacks) {
            String value;
            try {
                value = Base64ItemStack.encode(itemStack);
            } catch (Throwable throwable) {
                this.plugin.getLogger().severe("[ZAH] Unable to encode " + itemStack.getType() + " for sale: " + throwable);
                return null;
            }
            if (value == null || value.isEmpty()) {
                this.plugin.getLogger().severe("[ZAH] Base64ItemStack.encode returned an empty payload for " + itemStack.getType()
                        + ", the sale is refused instead of storing an empty listing.");
                return null;
            }
            encoded.add(value);
        }
        return encoded;
    }

    private void refundSellTax(Player player, AuctionEconomy auctionEconomy, TaxResult taxResult, String reason) {
        if (!taxResult.hasTax()) return;
        try {
            auctionEconomy.deposit(player.getUniqueId(), taxResult.taxAmount(), reason);
        } catch (Throwable throwable) {
            this.plugin.getLogger().severe("[ZAH] CRITICAL: sell tax refund of " + taxResult.taxAmount()
                    + " failed for " + player.getName() + ": " + throwable);
        }
    }

    /**
     * Rend les items au vendeur apres une vente qui n'a pas abouti.
     * <p>
     * La restitution DOIT repasser par le thread de l'entite : l'ancien bloc `.exceptionally`
     * n'avait pas de variante Async et ecrivait donc dans l'inventaire depuis un worker de
     * l'asyncExecutor. La Map de restes rendue par addItem etait par ailleurs ignoree, ce qui
     * perdait silencieusement tout ce qui ne rentrait pas.
     */
    private void refundFailedSell(Player player, List<ItemStack> itemsToSell, AuctionEconomy auctionEconomy, TaxResult taxResult) {

        this.plugin.getScheduler().runAtEntity(player, task -> {

            if (!player.isOnline()) {
                // Ne PAS tenter de recreer une annonce ici : deux ecritures non atomiques
                // feraient transiter le lot par LISTED au prix passe en argument, ce qui
                // creerait un vecteur d'achat a vil prix.
                this.plugin.getLogger().severe("[ZAH] Sale failed and " + player.getName()
                        + " is offline: " + itemsToSell.size() + " stack(s) could not be returned, manual restore required.");
                return;
            }

            for (ItemStack itemStack : itemsToSell) {
                if (itemStack == null) continue;
                player.getInventory().addItem(itemStack.clone())
                        .forEach((slot, leftover) -> player.getWorld().dropItem(player.getLocation(), leftover));
            }

            refundSellTax(player, auctionEconomy, taxResult, "Refund sell tax (sale failed)");
        });
    }


// ============================================================
// C — postSell : ne plus reencoder, reutiliser les charges utiles
// ============================================================
// Dans postSell (l.384), l'encodage du log peut echouer sur un stack fautif et faire remonter
// une exception APRES commit. On le rend defensif :

        String encodedItemStack;
        try {
            encodedItemStack = auctionItem.getItemStacks().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(Base64ItemStack::encode)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.joining(";"));
        } catch (Throwable throwable) {
            encodedItemStack = null;
            this.plugin.getLogger().severe("[ZAH] Unable to encode the log payload for listing " + auctionItem.getId() + ": " + throwable);
        }


// ============================================================
// D — API/.../api/messages/Message.java : constante additive
// ============================================================

    SELL_ERROR_INVALID_ITEM("<error>This item cannot be listed, its data could not be saved."),
```

## 2.8 — Forcer la sauvegarde du profil après le retrait des items du vendeur (C-080)

**Fichier** : `src/main/resources/config.yml`  
**Risque** : MEDIUM  
**Constats** : C-080

Ce correctif n'a de sens QU'APRÈS l'étape 7 et je diverge de la fiche C-080 sur son analyse. La fiche objecte, à raison, que `saveData()` seul n'élimine pas la fenêtre : il l'inverse (une duplication longue de ~5 min devient une perte courte de ~50 ms). Avec la séquence de l'étape 7, l'objection tombe : les contenus sont DÉJÀ durablement en base au moment où les items quittent l'inventaire. Un crash entre `saveData()` et `publishListing` laisse donc une réservation qui contient le lot — récupérable par l'étape 9 — et le joueur ne perd rien. `saveData()` est une écriture disque SYNCHRONE sur le thread de la région à chaque vente : d'où la clé de configuration, obligatoire pour les serveurs chargés ou équipés d'un plugin de synchronisation d'inventaire qui gère déjà la persistance. Je place aussi l'appel sur le chemin de remboursement : une restitution non sauvegardée n'est pas durable.

```java
// ============================================================
// A — config.yml : nouvelle cle, a repliquer dans les SIX jeux de langue
// (racine anglaise + fr/ + es/ + it/ + id/ + th/)
// A inserer dans le bloc `action:` (config.yml, apres `update-inventory-on-action`)
// ============================================================

  # When enabled, the seller's profile is written to disk immediately after their items leave
  # the inventory to be listed. This closes the window where a server crash between the listing
  # and the next autosave would give the player their items back while the listing already exists
  # (item duplication).
  # This is a SYNCHRONOUS disk write on each sale: disable it on heavily loaded servers, or if an
  # inventory synchronisation plugin already persists profiles for you.
  save-profile-on-sell: true


// ============================================================
// B — API/.../api/configuration/Configuration.java : methode DEFAULT (non cassante)
// ============================================================

    /**
     * @return {@code true} si le profil du vendeur doit etre sauvegarde sur disque immediatement
     * apres que ses items ont quitte son inventaire pour etre mis en vente.
     * @implNote methode {@code default} : les implementations tierces de Configuration compilent
     * toujours et heritent du comportement historique le plus sur.
     */
    default boolean isSaveProfileOnSell() {
        return true;
    }


// ============================================================
// C — MainConfiguration.java
// ============================================================

// champ, a cote de sellInventoryEnabled :
    private boolean saveProfileOnSell;

// chargement, a cote des autres getBoolean :
        this.saveProfileOnSell = config.getBoolean("action.save-profile-on-sell", true);

// accesseur :
    @Override
    public boolean isSaveProfileOnSell() {
        return this.saveProfileOnSell;
    }


// ============================================================
// D — SellService.java : deux points d'appel
// ============================================================

// (1) dans l'ETAPE 2, immediatement apres removeItemsFromSlots :
                    removeItemsFromSlots(player, validSlotItems);

                    // Les contenus sont DEJA durablement en base (etape 1) : forcer la sauvegarde
                    // du profil ici ne peut plus faire perdre le lot au joueur, elle ne fait que
                    // fermer la fenetre de duplication ouverte jusqu'a l'autosave suivant.
                    if (this.plugin.getConfiguration().isSaveProfileOnSell()) {
                        player.saveData();
                    }

// (2) dans refundFailedSell, apres la boucle de restitution et avant refundSellTax :
            if (this.plugin.getConfiguration().isSaveProfileOnSell()) {
                player.saveData();
            }
```

## 2.9 — Récupération au démarrage des réservations orphelines (complément C-020, optionnel)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/storage/repository/repositories/ItemRepository.java`  
**Risque** : LOW  
**Constats** : C-020, C-080

Étape séparée et droppable : sans elle, les étapes 6 à 8 sont déjà un progrès net, mais un `kill -9` entre le retrait des items et la publication laisse le lot dans une réservation invisible que rien ne rouvre — le joueur a perdu ses items alors qu'ils sont physiquement en base. La colonne `pending_publish` rend ces lignes identifiables sans la moindre ambiguïté : les lignes DELETED antérieures à cette version portent NULL et ne seront JAMAIS balayées, ce qui écarte le seul vrai danger de ce genre de tâche (ressusciter tout l'historique supprimé). La fenêtre de grâce de 10 minutes rend l'opération sûre en cluster : une réservation vit quelques millisecondes, un nœud qui démarre ne peut pas voler la vente en cours d'un autre. L'UPDATE est idempotent, deux nœuds qui démarrent ensemble ne se marchent pas dessus. Les annonces récupérées atterrissent dans EXPIRED avec `expired_at = 0` (jamais), donc réclamables indéfiniment par le vendeur.

```java
// ============================================================
// A — ItemRepository.java
// ============================================================

    /**
     * Repositionne en EXPIRED les reservations de vente qui n'ont jamais ete publiees.
     * <p>
     * Une reservation ne survit normalement que quelques millisecondes ; il n'en reste que si le
     * process est mort entre le retrait des items du vendeur et la publication. Le lot est
     * physiquement present dans {@code auction_items} : le rendre reclamable est la seule facon
     * de ne pas le perdre.
     * <p>
     * La garde {@code pending_publish = 1} est ce qui rend l'operation SURE : toutes les lignes
     * DELETED anterieures a cette version portent NULL et ne sont donc jamais touchees. Sans elle
     * on ressusciterait tout l'historique supprime du serveur.
     *
     * @param createdBefore ne recuperer que les reservations plus vieilles que cette date, pour ne
     *                      jamais voler la vente en cours d'un autre noeud du cluster
     * @return le nombre d'annonces recuperees
     */
    public int recoverOrphanReservations(long createdBefore) {
        try {
            return SchemaBuilder.update(getTableName(), schema -> {
                schema.where("pending_publish", 1);
                schema.where("storage_type", StorageType.DELETED.name());
                schema.where("created_at", "<", new Date(createdBefore));
                schema.object("storage_type", StorageType.EXPIRED.name());
                // 0 = n'expire jamais (ZItem.isExpired teste expiredAt.getTime() != 0) :
                // le vendeur peut reclamer son lot sans limite de temps.
                schema.object("expired_at", new Date(0));
                schema.object("pending_publish", 0);
            }).execute(this.connection, getLogger());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to recover orphan listing reservations", exception);
        }
    }


// ============================================================
// B — ZStorageManager.java : appel dans onEnable, APRES les migrations et AVANT loadItems()
// ============================================================

    /**
     * Fenetre de grace avant qu'une reservation soit consideree comme orpheline. Tres au-dessus
     * de la duree reelle d'une reservation (quelques ms) et de tout ecart d'horloge raisonnable
     * entre les noeuds d'un cluster.
     */
    private static final long ORPHAN_RESERVATION_GRACE_MS = 10L * 60L * 1000L;

    private void recoverOrphanReservations() {
        try {
            int recovered = with(ItemRepository.class).recoverOrphanReservations(System.currentTimeMillis() - ORPHAN_RESERVATION_GRACE_MS);
            if (recovered > 0) {
                this.plugin.getLogger().warning("[ZAH] " + recovered + " listing reservation(s) were never published"
                        + " (server crash during a sale). They have been made claimable by their seller"
                        + " in the 'expired items' section.");
            }
        } catch (Throwable throwable) {
            // Ne JAMAIS empecher le demarrage pour une tache de rattrapage.
            this.plugin.getLogger().severe("[ZAH] Unable to recover orphan listing reservations: " + throwable);
        }
    }

// dans onEnable, juste apres l'execution des migrations et avant loadItems() :
        recoverOrphanReservations();
```

### Ruptures d'API et stratégie de compatibilité

- AUCUNE rupture de signature dans le module api/. Les quatre méthodes publiées `AuctionManager.removeListedItem/removeSellingItem/removeExpiredItem/removePurchasedItem` conservent leur type de retour `CompletableFuture<Void>` : les surcharges qui rendent `CompletableFuture<Boolean>` (item effectivement remis) et celle qui reçoit la destination vivent UNIQUEMENT sur l'implémentation `ZAuctionManager`, hors du module publié. C'est la raison pour laquelle je rejette le correctif de la fiche C-083, qui proposait de faire remonter la destination en changeant `AuctionManager.removeListedItem` en `CompletableFuture<StorageType>` : le type de retour fait partie du descripteur JVM, l'addon Redis compilé contre le SHA figé `deb8f16` lèverait NoSuchMethodError.
- `ZAuctionManager.giveItem(Player, Item)` passe de `void` à `CompletableFuture<GiveResult>`. Vérifié par grep sur les deux dépôts : la méthode n'existe ni dans api/, ni dans l'addon Redis (les seules occurrences du mot y sont les records de configuration `ListedConfiguration.giveItem` / `PurchasedConfiguration.giveItem`, sans rapport). Six sites d'appel, tous dans ZAuctionManager. Non api-breaking.
- `StorageManager` gagne trois méthodes `default` : `reserveAuctionItem(Player, BigDecimal, long, List<ItemStack>, List<String>, AuctionEconomy)`, `publishAuctionItem(AuctionItem)` et `cancelReservation(AuctionItem)`. Source- et binaire-compatibles : un StorageManager tiers compilé contre une version antérieure hérite des implémentations par défaut (délégation à `createAuctionItem`, publication réputée réussie, annulation no-op) et continue de fonctionner à l'identique, simplement sans le gain d'atomicité. Précédent dans le même dépôt : `AuctionClusterBridge.isDistributed()` est déjà une `default`.
- `Configuration` gagne une méthode `default boolean isSaveProfileOnSell()` retournant `true`. Additive, source- et binaire-compatible.
- `Message` gagne deux constantes : `REMOVE_ALL_ITEMS_PARTIAL` et `SELL_ERROR_INVALID_ITEM`. L'ajout de constantes à un enum publié est additif ; vérifié qu'aucun `switch` exhaustif sur `Message` n'existe dans les deux dépôts, donc pas de rupture binaire pour l'addon compilé contre `deb8f16`.
- `AuctionItemRepository.create(UUID, String, int, BigDecimal, long, List<ItemStack>, AuctionEconomy)` est conservée et marquée `@Deprecated`, déléguant à la nouvelle surcharge à 8 arguments qui reçoit les charges utiles déjà encodées. La classe n'est pas dans api/ (elle est dans le module plugin), la précaution est donc purement interne mais évite de casser un éventuel hook maison.
- `ItemRepository.create(UUID, ItemType, BigDecimal, long, AuctionEconomy)` est conservée à l'identique et délègue à la nouvelle surcharge portant le `StorageType` initial. Aucun appelant existant ne change de comportement.
- MIGRATION DE SCHÉMA : une colonne `pending_publish` INT NULLABLE est ajoutée à `%prefix%items` via `CreateItemMigration`. Le `MigrationManager` de Sarah ajoute automatiquement les colonnes manquantes (`schemaAlter.addColumn(column).nullable()`) en comparant le schéma `create()` à la table vivante : aucune action administrateur. Une colonne nullable ajoutée est le type de migration le plus sûr pour un downgrade — un jar antérieur ignore purement et simplement la colonne. `ItemDTO` n'a PAS besoin d'être modifié : Sarah mappe par champ de record, pas par colonne du ResultSet.

### Migrations de schéma

- `%prefix%items` : ajout de la colonne `pending_publish` INT NULLABLE, portée par `CreateItemMigration.up()` (étape 6). Appliquée automatiquement au démarrage par le `MigrationManager` de Sarah, qui compare le schéma `create()` à la table vivante et émet un ALTER ... ADD COLUMN nullable pour les colonnes manquantes (PRAGMA table_info sous SQLite, information_schema.COLUMNS sinon). Aucune action administrateur, aucune interruption de service, aucun index créé — donc aucun risque de blocage au démarrage sur une grosse table (contrairement aux migrations d'index du chantier PERF).
- SÉMANTIQUE DE LA COLONNE — indispensable pour tout diagnostic manuel : `1` = annonce RÉSERVÉE, ligne créée mais pas encore publiée (invisible de tous les serveurs, son contenu est déjà dans `%prefix%auction_items`) ; `0` = ligne publiée au moins une fois, cycle de vie normal ; `NULL` = ligne antérieure au déploiement de cette version, qui ne doit JAMAIS être traitée comme une réservation. C'est cette distinction NULL/1 qui empêche le balayage de l'étape 9 de ressusciter tout l'historique supprimé du serveur.
- AUCUNE modification de colonne existante, AUCUN changement de type, AUCUNE suppression : la migration est purement additive et un jar antérieur ignore la colonne sans erreur.

### Changements de configuration

- RECTIFICATIF AU BRIEF ET AU CLAUDE.md : le plugin ne livre pas 4 mais SIX jeux de configuration. `ls src/main/resources/` rend `es/`, `fr/`, `id/`, `it/`, `th/` plus la racine anglaise. Toute clé ajoutée doit être répliquée SIX fois, pas quatre. Le CLAUDE.md du projet est périmé sur ce point et devrait être corrigé dans le même lot.
- config.yml — nouvelle clé `action.save-profile-on-sell: true` (étape 8), à insérer dans le bloc `action:` juste après `update-inventory-on-action`, avec son commentaire d'explication. À répliquer dans src/main/resources/config.yml (EN), fr/, es/, it/, id/, th/config.yml — commentaire traduit dans chaque langue.
- messages.yml — nouvelle clé `remove-all-items-partial` (étape 5), à placer à côté de `remove-all-items` (l.677 en anglais, l.605 dans fr/es/it/th, l.285 dans id/). Contenu de référence : `"<error>%amount% listing(s) could not be retrieved, please try again."`. À répliquer dans les SIX fichiers.
- messages.yml — nouvelle clé `sell-error-invalid-item` (étape 7), à placer à côté de `sell-error-air` / `sell-error-change` (l.121 en anglais). Contenu de référence : `"<error>This item cannot be listed, its data could not be saved."`. À répliquer dans les SIX fichiers.
- API/src/main/java/fr/maxlego08/zauctionhouse/api/messages/Message.java — les deux constantes correspondantes (`REMOVE_ALL_ITEMS_PARTIAL`, `SELL_ERROR_INVALID_ITEM`). Oublier de mettre à jour les six messages.yml ne provoque pas de crash mais laisse le texte anglais par défaut dans les cinq autres langues.
- DOCUMENTATION (obligatoire par la convention du dépôt) : `changelogs.md` sous une section `# Unreleased`, ET la documentation Docusaurus externe à `C:\Users\Admin\Desktop\groupez\documentation` en EN (`plugins/zauctionhouse/docs/`) et FR (`i18n/fr/docusaurus-plugin-content-docs-zauctionhouse/current/`). Trois changements de comportement doivent y figurer explicitement : (1) `save-profile-on-sell`, (2) un item dont le contenu est illisible disparaît désormais de l'hôtel des ventes au lieu de s'afficher en BARRIER achetable, (3) le retrait en masse ne s'arrête plus sur une erreur interne et signale les annonces non traitées.

### Validation

- INVARIANT SQL n°1 — aucune annonce vide ne doit être visible. À exécuter avant et après un stress-test de ventes : `SELECT i.id, i.storage_type FROM %prefix%items i LEFT JOIN %prefix%auction_items a ON a.item_id = i.id WHERE i.item_type = 'AUCTION' AND i.storage_type <> 'DELETED' GROUP BY i.id, i.storage_type HAVING COUNT(a.id) = 0;` — DOIT rendre 0 ligne. Sur HEAD, un `kill -9` bien placé pendant une vente en produit.
- INVARIANT SQL n°2 — aucune réservation ne doit survivre. `SELECT id, created_at FROM %prefix%items WHERE pending_publish = 1;` — DOIT rendre 0 ligne quand le serveur est au repos. Une ligne persistante signale soit un crash pendant une vente (l'étape 9 la récupère au prochain démarrage), soit un bug de publication.
- TEST kill -9 n°1, VENTE (étapes 6-8). Poser un breakpoint conditionnel ou un `Thread.sleep(20000)` temporaire entre `removeItemsFromSlots` et `storageManager.publishAuctionItem`. Vendre un diamant, `kill -9` pendant la pause, redémarrer. ATTENDU : au démarrage, la ligne WARNING « 1 listing reservation(s) were never published » ; le diamant N'EST PAS dans l'inventaire du joueur ; il EST réclamable dans `/ah` onglet items expirés ; `SELECT storage_type, expired_at FROM %prefix%items WHERE id = <id>` rend `EXPIRED` et `expired_at = 0`. Sur HEAD, le même scénario produit une annonce vide achetable (l'acheteur paie et ne reçoit rien).
- TEST kill -9 n°2, ACHAT (étape 3). `Thread.sleep(20000)` temporaire au début du `thenCompose` qui suit `storageManager.updateItem(auctionItem, destination)`. Acheter, `kill -9` pendant la pause, redémarrer. ATTENDU : la ligne est en `PURCHASED` ou `DELETED` avec `buyer_unique_id` renseigné, JAMAIS `LISTED` ; l'item n'est jamais à la fois dans l'inventaire de l'acheteur et en vente. Requête : `SELECT storage_type, buyer_unique_id FROM %prefix%items WHERE id = <id>;`.
- TEST base débranchée, RETRAIT (étape 2). Couper MySQL (ou `chmod 000` sur le fichier SQLite), puis cliquer sur « retirer » depuis l'onglet « en vente ». ATTENDU : aucun item dans l'inventaire, message d'erreur, l'annonce est TOUJOURS visible dans l'hôtel des ventes après reconnexion de la base, et le statut est revenu à AVAILABLE (restauré par `restoreStatusOnError`). Sur HEAD, le joueur reçoit l'item et l'annonce reste en vente.
- TEST compensation de remise (étape 2, cœur du correctif). Sur un serveur Folia de préférence : remplir l'inventaire du joueur à ras bord, lancer `/ah` onglet expirés puis « tout récupérer », et déconnecter le joueur brutalement (kill du client) au milieu du lot. ATTENDU : les annonces non remises repassent en EXPIRED (`SELECT storage_type FROM %prefix%items WHERE id IN (...)` rend `EXPIRED`, jamais `DELETED`), le joueur les retrouve à sa reconnexion, et la console porte les lignes SEVERE « could not be delivered ... restoring it as EXPIRED ». Aucune ligne ne doit rester en `DELETED` sans que l'item soit dans l'inventaire.
- TEST retrait en masse partiel (étape 5). Mettre 20 annonces en vente, remplir l'inventaire à 2 emplacements libres, cliquer « tout récupérer ». ATTENDU : le joueur reçoit 2 items, voit le message `remove-all-items` avec %amount%=2 ET le message `remove-all-items-partial` avec %amount%=18, et les 18 restantes sont TOUJOURS en vente. Sur HEAD, le message partiel n'existe pas et la boucle s'arrête sans rien dire.
- TEST deux serveurs A+B (Redis + MySQL partagée), non-régression cluster. Le joueur P vend sur A ; vérifier que l'annonce n'apparaît sur B qu'APRÈS la publication (instrumenter `publishAuctionItem` avec un sleep de 5 s et vérifier que B ne voit rien pendant ces 5 s — c'est le gain de l'étape 7). Puis P retire sur A pendant que Q ouvre l'HDV sur B : l'annonce doit disparaître de B, et `SELECT storage_type FROM %prefix%items WHERE id = <id>` doit rendre `DELETED` exactement une fois.
- TEST quarantaine (étape 1). Corrompre volontairement une ligne : `UPDATE %prefix%auction_items SET itemstack = 'not-base64' WHERE item_id = <id>;` puis redémarrer. ATTENDU : deux lignes SEVERE nommant l'item ; l'annonce N'APPARAÎT PAS dans l'hôtel des ventes ; le compteur de démarrage affiche `quarantined=1` ; les lignes en base sont INTACTES (`SELECT storage_type FROM %prefix%items WHERE id = <id>` rend toujours `LISTED`) ; aucun item BARRIER cliquable. Puis restaurer la valeur d'origine et redémarrer : l'annonce revient normalement — c'est ce qui prouve que la quarantaine n'est pas destructive.
- TEST item non sérialisable (étape 7). Sur un serveur Paper ≥ 1.20.6, forcer temporairement `Base64ItemStack.encode` à rendre `null` pour un Material donné, puis tenter de vendre cet item. ATTENDU : le message `sell-error-invalid-item`, l'item TOUJOURS dans l'inventaire du joueur, aucune ligne créée (`SELECT COUNT(*) FROM %prefix%items WHERE seller_unique_id = ? AND created_at > ?` rend 0), aucune taxe prélevée. Sur HEAD, l'item est retiré, une ligne est créée et le contenu est perdu.
- NON-RÉGRESSION MONO-SERVEUR (le cas de 99 % des installations). Avec `LocalAuctionClusterBridge` et SQLite par défaut, dérouler le parcours complet : vendre → acheter avec un autre compte → réclamer côté acheteur (`purchased-item.give-item: false` est le défaut) → retirer une annonce (`remove-listed-item.give-item: false` est le défaut, l'item part donc en EXPIRED) → la réclamer. Compter les items à chaque étape : aucune apparition, aucune disparition.
- BUILD : `JAVA_HOME="C:/Users/Admin/.jdks/ms-21.0.9" ./gradlew clean build` sur zAuctionHouseV4 puis, SANS republier l'API ni bumper son SHA, `./gradlew build` sur `zAuctionHouse Redis` — il doit compiler tel quel contre `zauctionhousev4-api:deb8f16`. C'est la preuve mécanique qu'aucune signature publiée n'a bougé.

### Questions ouvertes pour le mainteneur

- ÉTAPE 9 (récupération des réservations orphelines) : la garde-t-on ? Sans elle, un `kill -9` entre le retrait des items du vendeur et la publication fait perdre le lot au joueur — les contenus sont pourtant physiquement en base. Avec elle, on ajoute une migration de schéma et un balayage au démarrage. Mon avis : la garder, c'est ce qui justifie l'inversion d'ordre auprès des joueurs. Mais c'est une décision de risque produit, pas technique.
- ÉTAPE 8 (`save-profile-on-sell`) : `player.saveData()` est une écriture disque SYNCHRONE sur le thread de la région à chaque vente. Sur un serveur à fort volume de ventes, l'impact TPS est réel et non mesuré ici. Faut-il livrer avec `true` par défaut (protection maximale, coût pour tous) ou `false` (les serveurs exposés à la duplication l'activent explicitement) ? Le défaut `true` que je propose change le profil de performance de tous les serveurs à la mise à jour.
- DÉLAI DU FILET DE REMISE : `GIVE_ITEM_TIMEOUT_SECONDS = 15` est une constante, pas une clé de configuration, pour éviter une réplication dans six fichiers de langue. Si un serveur Folia très chargé voit ce filet se déclencher pour des joueurs pourtant en ligne, il faudra soit augmenter la constante, soit l'exposer — et alors la répliquer six fois.
- CHANGEMENT VISIBLE PAR LES JOUEURS n°1 (étape 1) : un item dont le contenu est illisible DISPARAÎT de l'hôtel des ventes au lieu de s'afficher en BARRIER cliquable. Les exploitants signaleront une « perte d'items » alors que les lignes sont intactes en base. Faut-il livrer en même temps une commande admin `/ah admin quarantine list` pour rendre ces items visibles côté administration ? Je ne l'ai pas incluse (hors périmètre), mais sans elle le vendeur n'a aucune visibilité sur son annonce disparue.
- CHANGEMENT VISIBLE PAR LES JOUEURS n°2 (étape 5) : le retrait en masse ne s'arrête plus sur une erreur interne. Un lot de 100 annonces dont 3 échouent produira désormais 97 retraits + un message signalant 3 échecs, là où le comportement actuel s'arrêtait à la première erreur sans rien dire. C'est la correction voulue, mais c'est un changement observable à annoncer.
- ORDONNANCEMENT AVEC LE CHANTIER 3 : mon étape 3 laisse volontairement `withdraw` et `deposit` en amont du commit. Accepte-t-on de livrer ce chantier seul — l'item n'est plus dupliqué mais l'argent peut encore être perdu après un échec DB — ou attend-on que le chantier 3 (`withdrawChecked` + compensation monétaire) soit prêt pour livrer les deux ensemble ? Livrer seul est défendable (une perte réversible à la main vaut mieux qu'un dupe irréversible) mais déplace le ticket support au lieu de le supprimer.
- ZONE DE CONFLIT `adminRemoveItem` (ZAuctionManager.java:679-709) : mon étape 4 ne fait que l'inversion d'ordre. Les chantiers QUICK/4/5 (C-012, C-042, C-060, C-112) veulent réécrire le même bloc de bout en bout. Qui passe en premier ? Si c'est eux, mon étape 4 se réduit à deux lignes à reporter dans leur réécriture ; si c'est moi, ils rebasent. À trancher avant d'ouvrir les deux branches.
- VÉRIFICATION DU NOMBRE DE CONTENUS AVANT PUBLICATION : j'ai écarté un `SELECT COUNT(*) FROM auction_items WHERE item_id = ?` avant `publishListing`, parce que `Repository.insert` ne peut pas avaler l'échec (Sarah lève une `DatabaseException`, une RuntimeException, et le `catch (SQLException)` est du code mort). Si le mainteneur veut une ceinture ET des bretelles, cette vérification coûte une requête par vente et rend la publication prouvable. Mon avis : inutile aujourd'hui, à reconsidérer si Sarah change son contrat d'exception.
- ACCUMULATION DE TOMBSTONES : chaque vente avortée après la réservation laisse, dans le cas non-crash, une ligne supprimée par `cancelReservation` — donc rien. Dans le cas crash, l'étape 9 la convertit en EXPIRED. Il ne reste aucune accumulation, contrairement à ce que craint la fiche C-020. En revanche il n'existe toujours AUCUNE commande de purge des lignes DELETED historiques (aucun `DELETE FROM items` dans tout le projet) : ce point reste ouvert et appartient au chantier PERF (C-066).

### Retour arrière

"Le downgrade de jar est SÛR pour les étapes 1 à 5 et 8 : elles ne touchent aucun format de données. Redéployer le jar précédent suffit, il n'y a rien à défaire.\n\nLes étapes 6, 7 et 9 introduisent la colonne `pending_publish` : elle est NULLABLE et purement additive, un jar antérieur l'ignore et continue de fonctionner. La migration n'a donc pas besoin d'être annulée et il ne faut SURTOUT PAS la supprimer (un DROP COLUMN sur une grosse table `items` est une opération longue et bloquante, pour un gain nul).\n\nUN SEUL point demande une action manuelle après un downgrade : les réservations en vol au moment de la bascule. Un jar antérieur ne connaît ni `publishListing` ni le balayage de l'étape 9 : les lignes `storage_type='DELETED' AND pending_publish=1` resteraient invisibles pour toujours, et leur propriétaire aurait perdu ses items. Les récupérer avec la requête suivante, AVANT ou APRÈS le downgrade (elle est idempotente et rejoue exactement ce que fait l'étape 9) :\n\n  UPDATE %prefix%items\n     SET storage_type = 'EXPIRED', expired_at = '1970-01-01 00:00:00', pending_publish = 0\n   WHERE pending_publish = 1\n     AND storage_type = 'DELETED';\n\n  -- controle avant execution :\n  SELECT id, seller_unique_id, price, created_at FROM %prefix%items WHERE pending_publish = 1 AND storage_type = 'DELETED';\n\nLes annonces récupérées apparaissent dans l'onglet « items expirés » de leur vendeur et n'expirent jamais (expired_at = 0). Sur un serveur au repos cette requête doit rendre 0 ligne : si elle en rend, c'est le décompte exact des ventes interrompues par le crash.\n\nSAUVEGARDE AVANT DÉPLOIEMENT (obligatoire) : `mysqldump` complet, ou copie du fichier `.db` pour SQLite. La bascule d'ordre modifie le moment où les lignes changent d'état ; en cas de doute sur un état intermédiaire, seule une restauration point-in-time permet de reconstituer la vérité.\n\nDÉPLOIEMENT EN CLUSTER : ces étapes sont compatibles avec une mise à jour tournante (nœud par nœud). Un nœud ancien ne voit pas les réservations (elles sont DELETED, filtrées par `ItemRepository.select()`), un nœud à jour lit sans problème les annonces créées par un nœud ancien. Une seule précaution : ne PAS activer le balayage de l'étape 9 tant que tous les nœuds ne sont pas à jour — un nœud ancien ne pose jamais `pending_publish = 1`, donc il n'y a rien à balayer, mais la fenêtre de grâce de 10 minutes suppose que tout le parc écrit la colonne."


---

# Chantier 3 — Chantier 3 — Rendre la couche monétaire vérifiable : retrait/dépôt qui peuvent dire NON, calcul de taxe unique, crédit vendeur cross-serveur

La couche monétaire est aujourd'hui aveugle à deux niveaux. D'abord `AuctionEconomy.withdraw`/`deposit` rendent `void` jusqu'au provider (CurrenciesAPI `VaultProvider` jette l'`EconomyResponse`, `LevelProvider.deposit` est un no-op silencieux hors ligne) : un débit refusé est invisible et l'achat se poursuit quand même. Ensuite le montant EXIGÉ et le montant PRÉLEVÉ ne sont pas calculés au même endroit — `PurchaseService:49` appelle `calculatePurchaseTax(player, price, null)` alors que `ZAuctionManager:744` passe l'ItemStack réel, puis réinterprète le résultat avec le type de taxe de l'ÉCONOMIE alors que `ZTaxConfiguration:133` l'a calculé avec le type de la RÈGLE ITEM. Ce chantier ferme les deux trous d'un même geste : `TaxResult` devient auto-descriptif (il porte le type réellement appliqué et expose `buyerPays()`/`sellerReceives()`), un résolveur unique `ZPurchaseCharge` sert les trois consommateurs (bouton, service, manager), et deux méthodes `default` additives `withdrawChecked`/`depositChecked` rendent enfin un booléen, implémentées dans `ZAuctionEconomy` avec un pré-contrôle de solde sous verrou strippé par compte. S'y ajoutent le prédicat `supportsOfflineDeposit()` qui empêche les économies LEVEL/EXPERIENCE/ITEM/ZMENUITEMS de détruire le paiement d'un vendeur hors ligne, la neutralisation des exceptions de comptabilité qui interrompaient la livraison après le débit, et le déclenchement de l'auto-claim du vendeur connecté sur un AUTRE nœud. Le lot est indivisible parce que chaque pièce dépend de la précédente pour être vraie : un `withdrawChecked` sans calcul unique vérifierait le mauvais montant, un calcul unique sans `withdrawChecked` ne saurait toujours pas si l'argent a bougé, et le garde-fou anti-frappe monétaire n'a de sens que si `TaxResult` sait quelle sémantique il porte.

**Prérequis**

- CHANTIER 1 (C-003 réservation par claim_token + C-013 ne marquer RETRIEVED que ce qui a été crédité) : PRÉREQUIS DUR de l'étape 11 (C-059). Sans le compare-and-set sur les transactions PENDING, déclencher un auto-claim depuis ItemBoughtListener ouvre une fenêtre de DOUBLE CRÉDIT (ce claim + un /ah claim manuel lisent la même liste PENDING). Les étapes 1 à 10 sont livrables sans lui.
- CHANTIER 1 (C-026 : where storage_type = source + propagation du rowcount) : non requis pour compiler, mais c'est lui qui rend la moitié BASE vérifiable. Tant qu'il n'est pas livré, ce chantier garantit que l'argent bouge comme annoncé, pas que la ligne DB a bougé.
- CHANTIER 2 (C-014 giveItem chaînable + C-005 inversion commit -> remise) : après l'étape 8, un débit refusé arrête l'achat avant tout mouvement d'item, mais un ÉCHEC D'ÉCRITURE DB après un débit réussi reste non compensé. L'ordonnancement sûr appartient au chantier 2.
- CHANTIER 8 (confinement de threads) : le verrou strippé de C-084 sérialise les mutations mais ne rend PAS légaux les appels Bukkit main-thread-only de LevelProvider/ExperienceProvider/ItemProvider exécutés depuis commonPool. Seul le passage par runNextTick/runAtEntity corrige ce point.
- CHANTIER 1 / C-027 : le catalogue attribue C-027 (createTransaction sur le scheduler FoliaLib jamais drainé, écriture de la ligne PENDING AVANT le withdraw) au chantier 3, mais il est absent de la liste de périmètre de ce chantier. L'étape 8 ne couvre que la moitié C-041 (les exceptions de comptabilité ne cassent plus la livraison) ; l'ordonnancement PENDING-avant-withdraw et le passage à getExecutorService restent à attribuer.
- DÉPÔT CurrenciesAPI (D:/Users/Maxlego08/workspace2.0/CurrenciesAPI) : tant que VaultProvider.withdraw ne retourne pas withdrawPlayer(...).transactionSuccess(), withdrawChecked reste une MITIGATION (pré-contrôle sous verrou) et non une PREUVE. À planifier séparément.

**Constats couverts** : C-015, C-041, C-017, C-019, C-032, C-102, C-084, C-059

*~590 LOC · 1 fichiers nouveaux*

## 3.1 — TaxResult devient auto-descriptif (appliedType, buyerPays, sellerReceives) sans casser les addons

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/tax/TaxResult.java`  
**Risque** : MEDIUM  
**Constats** : C-017, C-019

Racine de C-019 et de la variante B de C-017 : ZTaxConfiguration calcule finalPrice avec le type de la REGLE ITEM (l.133-135) et ZAuctionManager le reinterprete avec le type de l'ECONOMIE (l.736/756). Le seul moyen de rendre l'interpretation impossible a tromper est que le resultat porte lui-meme sa semantique. Le constructeur a 7 arguments est conserve explicitement : son descripteur JVM ne change pas, donc aucun addon compile contre un SHA anterieur ne casse (verifie : l'addon Redis ne reference pas TaxResult).

```java
package fr.maxlego08.zauctionhouse.api.tax;

import java.math.BigDecimal;

/**
 * Contains the result of a tax calculation.
 *
 * @param taxAmount           the calculated tax amount
 * @param taxPercentage       the effective tax percentage applied
 * @param originalPrice       the original price before tax
 * @param finalPrice          the final price (depends on context: what buyer pays or seller receives)
 * @param isBypassed          whether the tax was bypassed due to permission
 * @param isReduced           whether a tax reduction was applied
 * @param reductionPercentage the reduction percentage applied (0 if no reduction)
 * @param appliedType         le type de taxe REELLEMENT applique : celui de la regle par item
 *                            si une regle a matche, sinon celui de l'economie. {@code null}
 *                            pour les resultats construits par l'ancien constructeur a sept
 *                            arguments ; dans ce cas la semantique PURCHASE/BOTH est supposee
 *                            (l'acheteur paie {@code originalPrice}, le vendeur touche
 *                            {@code finalPrice}), la seule qui ne puisse pas creer de monnaie.
 */
public record TaxResult(
        BigDecimal taxAmount,
        double taxPercentage,
        BigDecimal originalPrice,
        BigDecimal finalPrice,
        boolean isBypassed,
        boolean isReduced,
        double reductionPercentage,
        TaxType appliedType
) {

    /**
     * Constructeur historique, conserve pour la compatibilite SOURCE et BINAIRE des addons
     * compiles contre une version anterieure de l'API. Son descripteur JVM est inchange.
     *
     * @deprecated utiliser le constructeur canonique a huit arguments, qui porte le type de
     * taxe reellement applique.
     */
    @Deprecated
    public TaxResult(BigDecimal taxAmount, double taxPercentage, BigDecimal originalPrice,
                     BigDecimal finalPrice, boolean isBypassed, boolean isReduced,
                     double reductionPercentage) {
        this(taxAmount, taxPercentage, originalPrice, finalPrice, isBypassed, isReduced, reductionPercentage, null);
    }

    /**
     * Creates a result for when tax is bypassed.
     *
     * @param price the original price
     * @return a TaxResult with no tax applied
     */
    public static TaxResult bypassed(BigDecimal price) {
        return new TaxResult(BigDecimal.ZERO, 0, price, price, true, false, 0, null);
    }

    /**
     * Creates a result for when tax is disabled.
     *
     * @param price the original price
     * @return a TaxResult with no tax applied
     */
    public static TaxResult disabled(BigDecimal price) {
        return new TaxResult(BigDecimal.ZERO, 0, price, price, false, false, 0, null);
    }

    /**
     * Checks if any tax was applied.
     *
     * @return true if tax amount is greater than zero
     */
    public boolean hasTax() {
        return taxAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Montant reellement debite a l'ACHETEUR.
     * <p>
     * Aucun consommateur ne doit plus rebrancher sur {@code TaxConfiguration.getTaxType()} :
     * c'est exactement la divergence qui faisait payer a l'acheteur un montant different de
     * celui qui avait ete verifie.
     *
     * @return le montant que l'acheteur doit payer
     */
    public BigDecimal buyerPays() {
        if (!hasTax()) return originalPrice;
        return appliedType == TaxType.CAPITALISM ? finalPrice : originalPrice;
    }

    /**
     * Montant reellement credite au VENDEUR.
     *
     * @return le montant que le vendeur doit toucher
     */
    public BigDecimal sellerReceives() {
        if (!hasTax()) return originalPrice;
        return appliedType == TaxType.CAPITALISM ? originalPrice : finalPrice;
    }

    /**
     * Vrai si la configuration produirait de la monnaie, c'est-a-dire si le vendeur toucherait
     * strictement plus que ce que l'acheteur paie. Doit etre teste AVANT tout mouvement d'argent.
     *
     * @return true si la configuration cree de la monnaie
     */
    public boolean mintsMoney() {
        return sellerReceives().compareTo(buyerPays()) > 0;
    }
}
```

## 3.2 — ZTaxConfiguration renseigne le type de taxe reellement applique

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/tax/ZTaxConfiguration.java`  
**Risque** : LOW  
**Constats** : C-019, C-017

Sans cette etape, appliedType reste null partout et buyerPays()/sellerReceives() retombent sur la semantique PURCHASE/BOTH : correct mais inutile. Ici on fait remonter le type retenu par la resolution de regle (calcTaxType), qui est la seule information manquante cote consommateur. Aucun changement de calcul : les montants produits sont bit-a-bit identiques, seule une composante d'information est ajoutee.

```java
// 1) calculateTax gagne le type applique en parametre (methode privee, aucun impact externe)
//    Remplacer la signature l.191 et le return l.229 :

    private TaxResult calculateTax(Player player, BigDecimal price, TaxAmountType calcAmountType,
                                   double calcAmount, TaxType appliedType) {
        // Check for bypass
        if (canBypass(player)) {
            return TaxResult.bypassed(price);
        }

        // ... corps inchange jusqu'au return final ...

        BigDecimal finalPrice = price.subtract(taxAmount);

        return new TaxResult(taxAmount, effectivePercentage, price, finalPrice, false, isReduced,
                reductionPercentage, appliedType);
    }

// 2) calculateSellTax : passer le type retenu aux deux appels (l.104 et l.118)

                if (ruleTaxType == TaxType.SELL || ruleTaxType == TaxType.BOTH) {
                    return calculateTax(player, price, matchingRule.getAmountType(), matchingRule.getAmount(), ruleTaxType);
                }

        return calculateTax(player, price, amountType, amount, taxType);

// 3) calculatePurchaseTaxInternal : le type applique voyage desormais dans le resultat

    private TaxResult calculatePurchaseTaxInternal(Player player, BigDecimal price,
                                                   TaxAmountType calcAmountType, double calcAmount, TaxType calcTaxType) {
        TaxResult baseResult = calculateTax(player, price, calcAmountType, calcAmount, calcTaxType);

        if (baseResult.isBypassed() || !baseResult.hasTax()) {
            return baseResult;
        }

        // Pour CAPITALISM, finalPrice est ce que paie l'acheteur (price + taxe).
        // Pour PURCHASE/BOTH, finalPrice est ce que touche le vendeur (price - taxe).
        // Dans les deux cas le type retenu est desormais porte par le resultat : le
        // consommateur n'a plus jamais a le rededuire de getTaxType().
        if (calcTaxType == TaxType.CAPITALISM) {
            BigDecimal buyerPays = price.add(baseResult.taxAmount());
            return new TaxResult(
                    baseResult.taxAmount(),
                    baseResult.taxPercentage(),
                    price,
                    buyerPays,
                    false,
                    baseResult.isReduced(),
                    baseResult.reductionPercentage(),
                    TaxType.CAPITALISM
            );
        }

        BigDecimal sellerReceives = price.subtract(baseResult.taxAmount());
        return new TaxResult(
                baseResult.taxAmount(),
                baseResult.taxPercentage(),
                price,
                sellerReceives,
                false,
                baseResult.isReduced(),
                baseResult.reductionPercentage(),
                calcTaxType
        );
    }
```

## 3.3 — AuctionEconomy : ajouter withdrawChecked / depositChecked / supportsOfflineDeposit en methodes default

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/economy/AuctionEconomy.java`  
**Risque** : LOW  
**Constats** : C-015, C-102, C-032

Le contrat doit pouvoir dire NON. Ajout strictement ADDITIF : les implementations tierces heritent d'un default qui delegue aux void existants et retourne true, donc rien ne casse ni a la compilation ni au link. Je REJETTE le correctif de l'audit (`boolean withdraw(...)` en gardant les `void` @Deprecated) : deux methodes de meme nom et meme liste de parametres ne peuvent pas differer par le seul type de retour, cela ne compile pas, et changer le type de retour casse le descripteur JVM des addons.

```java
// A inserer juste apres la methode `void withdraw(UUID playerId, BigDecimal value, String reason);`

    /**
     * Retire le montant du compte du joueur et indique si le retrait a REELLEMENT eu lieu.
     * <p>
     * L'implementation par defaut delegue a {@link #withdraw(UUID, BigDecimal, String)} et
     * repond {@code true} : elle preserve a l'identique le comportement des implementations
     * ecrites avant l'introduction de cette methode. Les implementations qui savent controler
     * leur provider (voir {@code ZAuctionEconomy}) doivent la surcharger.
     * <p>
     * Un {@code false} signifie qu'AUCUN argent n'a bouge : l'appelant doit abandonner
     * l'operation avant tout mouvement d'item.
     *
     * @param playerId the player to withdraw money from
     * @param value    the amount of money to withdraw
     * @param reason   the reason for the withdrawal
     * @return {@code true} si le retrait a eu lieu, {@code false} s'il a ete refuse
     */
    default boolean withdrawChecked(UUID playerId, BigDecimal value, String reason) {
        withdraw(playerId, value, reason);
        return true;
    }

    /**
     * Depose le montant sur le compte du joueur et indique si le depot a REELLEMENT eu lieu.
     * <p>
     * Meme contrat de compatibilite que {@link #withdrawChecked(UUID, BigDecimal, String)}.
     * Un {@code false} signifie que l'argent n'a pas ete credite et que l'appelant doit le
     * conserver sous forme de dette (transaction PENDING) ou le journaliser en SEVERE.
     *
     * @param playerId the player to deposit money into
     * @param value    the amount of money to deposit
     * @param reason   the reason for the deposit
     * @return {@code true} si le depot a eu lieu, {@code false} sinon
     */
    default boolean depositChecked(UUID playerId, BigDecimal value, String reason) {
        deposit(playerId, value, reason);
        return true;
    }

    /**
     * Indique si cette economie sait crediter un joueur HORS LIGNE.
     * <p>
     * Les economies adossees a l'entite joueur (niveaux, experience, items) sont des no-op
     * silencieux quand le joueur n'est pas connecte : l'argent du vendeur disparait. Le plugin
     * s'appuie sur ce predicat pour transformer le paiement en transaction PENDING plutot que
     * de le detruire.
     *
     * @return {@code true} si un depot hors ligne est possible (valeur par defaut)
     */
    default boolean supportsOfflineDeposit() {
        return true;
    }
```

## 3.4 — ZAuctionEconomy : pre-controle de solde sous verrou strippe par compte

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/economy/ZAuctionEconomy.java`  
**Risque** : MEDIUM  
**Constats** : C-015, C-084, C-032

C'est le coeur du chantier. Le pre-controle rend le refus possible ; le verrou strippe rend le couple lecture-mutation atomique par compte et par provider, ce qui est la seule chose qui donne un sens au pre-controle (C-084). Aucun appelant n'est modifie a cette etape : le comportement en production est strictement inchange, seul le nouveau contrat devient disponible. Je m'ecarte de l'audit sur deux points, voir risque de regression.

```java
// Nouveaux imports
import fr.traqueur.currencies.providers.ExperienceProvider;
import fr.traqueur.currencies.providers.ItemProvider;
import fr.traqueur.currencies.providers.LevelProvider;
import org.bukkit.Bukkit;
import java.util.logging.Level;

// Champs et bloc statique, en tete de classe

    /**
     * Nombre de verrous « stripes » servant a serialiser les mutations d'un meme compte.
     * Borne fixe et allouee une fois : contrairement a une ConcurrentHashMap<UUID, Object>
     * alimentee par computeIfAbsent, cela ne fuit pas une entree par joueur pour la duree de
     * vie de la JVM.
     */
    private static final int ACCOUNT_STRIPES = 64;
    private static final Object[] ACCOUNT_LOCKS = new Object[ACCOUNT_STRIPES];

    static {
        for (int index = 0; index < ACCOUNT_STRIPES; index++) {
            ACCOUNT_LOCKS[index] = new Object();
        }
    }

    /**
     * Verrou du couple (provider, joueur). Deux economies declarees sur le MEME provider
     * partagent le verrou d'un meme compte, ce qui est voulu : c'est le solde sous-jacent qui
     * doit etre serialise, pas le nom de l'economie. Deux providers differents ne se genent pas.
     */
    private Object accountLock(UUID playerId) {
        int hash = (System.identityHashCode(this.currencyProvider) * 31) + playerId.hashCode();
        hash ^= (hash >>> 16);
        return ACCOUNT_LOCKS[Math.floorMod(hash, ACCOUNT_STRIPES)];
    }

    /**
     * Vrai si le provider sait crediter un joueur hors ligne.
     * <p>
     * {@code ZMenuItemProvider} herite de {@code ItemProvider} : le test couvre donc a la fois
     * ITEM et ZMENUITEMS. LevelProvider.deposit et ItemProvider.deposit sont de simples no-op
     * quand {@code Bukkit.getPlayer(uuid)} rend null.
     *
     * @param currencyProvider le provider a tester
     * @return true si un depot hors ligne aboutit reellement
     */
    public static boolean supportsOfflineDeposit(CurrencyProvider currencyProvider) {
        return !(currencyProvider instanceof ItemProvider)
                && !(currencyProvider instanceof LevelProvider)
                && !(currencyProvider instanceof ExperienceProvider);
    }

// Nouvelles surcharges, a placer apres withdraw(...)

    @Override
    public boolean supportsOfflineDeposit() {
        return supportsOfflineDeposit(this.currencyProvider);
    }

    @Override
    public boolean withdrawChecked(UUID playerId, BigDecimal value, String reason) {
        if (value == null || value.signum() <= 0) return true;

        synchronized (accountLock(playerId)) {

            BigDecimal before;
            try {
                before = this.currencyProvider.getBalance(playerId);
            } catch (Exception exception) {
                this.plugin.getLogger().log(Level.SEVERE, "[" + this.name + "] Unable to read the balance of "
                        + playerId + " before withdrawing " + value, exception);
                return false;
            }

            // PRE-controle : c'est le SEUL controle sur lequel on s'autorise a refuser.
            // Il ne peut pas se tromper dans le sens dangereux : quand il refuse, rien n'a bouge.
            if (before == null || before.compareTo(value) < 0) return false;

            try {
                this.currencyProvider.withdraw(playerId, value, reason);
            } catch (Exception exception) {
                this.plugin.getLogger().log(Level.SEVERE, "[" + this.name + "] Withdraw of " + value
                        + " from " + playerId + " failed", exception);
                return false;
            }

            // POST-controle : PUREMENT INFORMATIF. On ne rend jamais false ici. Un depot
            // concurrent d'un autre plugin entre les deux lectures ferait sinon echouer un
            // retrait pourtant reussi, et l'appelant abandonnerait APRES avoir preleve
            // l'argent : on remplacerait une duplication par une destruction.
            try {
                BigDecimal after = this.currencyProvider.getBalance(playerId);
                if (after != null && before.subtract(after).compareTo(value) < 0) {
                    this.plugin.getLogger().warning("[" + this.name + "] Withdraw of " + value + " from "
                            + playerId + " reported success but the balance only moved by "
                            + before.subtract(after) + ". Concurrent economy write, or the provider"
                            + " silently ignored the withdrawal.");
                }
            } catch (Exception exception) {
                this.plugin.getLogger().warning("[" + this.name + "] Unable to verify the balance of "
                        + playerId + " after withdrawing " + value + ": " + exception.getMessage());
            }

            return true;
        }
    }

    @Override
    public boolean depositChecked(UUID playerId, BigDecimal value, String reason) {
        if (value == null || value.signum() <= 0) return true;

        // Refus AVANT tout appel : ces providers ne savent pas crediter un joueur hors ligne
        // et se contentent d'un no-op silencieux. Mieux vaut un false exploitable par
        // l'appelant (dette PENDING) qu'un depot fantome.
        if (!supportsOfflineDeposit() && Bukkit.getPlayer(playerId) == null) {
            this.plugin.getLogger().warning("[" + this.name + "] Refused to deposit " + value
                    + " to the offline player " + playerId + ": this economy type cannot credit an offline player.");
            return false;
        }

        synchronized (accountLock(playerId)) {
            try {
                this.currencyProvider.deposit(playerId, value, reason);
                return true;
            } catch (Exception exception) {
                this.plugin.getLogger().log(Level.SEVERE, "[" + this.name + "] Deposit of " + value
                        + " to " + playerId + " failed", exception);
                return false;
            }
        }
    }
```

## 3.5 — ZEconomyManager : forcer must-be-online pour les economies incapables de crediter hors ligne

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/economy/ZEconomyManager.java`  
**Risque** : LOW  
**Constats** : C-032

Deuxieme moitie de C-032 : le predicat ne sert a rien si l'exploitant peut configurer `must-be-online: false` sur une economie LEVEL. Le forcage se fait APRES la creation du provider, ce qui corrige au passage le defaut du correctif de l'audit (il placait le test l.301, alors que `currencies` n'est resolu qu'a la l.320 et le provider a la l.327 : cela ne compilait pas).

```java
// Inserer immediatement APRES le bloc `if (currencyProvider == null) { ... return; }` (l.348-351),
// donc avant la construction des maxPrices. `mustBeOnline` est une variable locale non finale
// declaree l.301, elle est donc reassignable ici.

        // C-032 : les providers adosses a l'entite joueur (LEVEL, EXPERIENCE, ITEM,
        // ZMENUITEMS) sont des no-op silencieux quand le joueur est hors ligne : le paiement
        // du vendeur disparait purement et simplement. Forcer must-be-online transforme ce
        // paiement en transaction PENDING, reclamable via /ah claim.
        if (!ZAuctionEconomy.supportsOfflineDeposit(currencyProvider) && !mustBeOnline) {
            mustBeOnline = true;
            this.plugin.getLogger().warning("Economy '" + name + "' uses type '" + type + "', which cannot"
                    + " credit an offline player. 'must-be-online' has been forced to true: sellers will now"
                    + " receive their money through /ah claim instead of losing it. Update economies.yml to"
                    + " silence this warning.");
        }
```

## 3.6 — ZPurchaseCharge : calcul UNIQUE du montant d'achat, cable sur les deux chemins de VERIFICATION

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/tax/ZPurchaseCharge.java (nouveau) + services/PurchaseService.java + buttons/list/ListedItemsButton.java`  
**Risque** : MEDIUM  
**Constats** : C-017, C-019, C-015

Les trois consommateurs (bouton d'affichage, service d'achat, manager) vivent tous dans le plugin : le calcul unique n'a donc PAS besoin de passer par l'API. On cable d'abord les deux chemins de VERIFICATION et seulement ensuite (etape 7) le chemin de PRELEVEMENT : dans cet ordre, l'etat intermediaire exige toujours au moins autant qu'il prelevera, jamais moins. L'ordre inverse ouvrirait une fenetre de sous-verification.

```java
// ======= NOUVEAU FICHIER src/main/java/fr/maxlego08/zauctionhouse/tax/ZPurchaseCharge.java =======
package fr.maxlego08.zauctionhouse.tax;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Montant unique d'un achat : ce que paie l'acheteur, ce que touche le vendeur, et le
 * {@link TaxResult} qui les a produits.
 * <p>
 * Ce type existe pour qu'il n'y ait plus qu'UN SEUL calcul de taxe a l'achat. Auparavant
 * {@code PurchaseService} calculait le solde exige avec un ItemStack {@code null} (donc en
 * ignorant les regles de taxe par item, cf. ZTaxConfiguration l.128) pendant que
 * {@code ZAuctionManager} prelevait avec l'ItemStack reel : le montant verifie pouvait etre
 * strictement inferieur au montant preleve.
 */
public record ZPurchaseCharge(BigDecimal buyerPays, BigDecimal sellerReceives, TaxResult taxResult) {

    /**
     * Calcule le montant d'un achat.
     * <p>
     * La regle de selection du stack representatif est la MEME que celle du chemin de vente
     * ({@code SellService.applySellTaxAsync}) : on retient la taxe la plus elevee parmi tous
     * les stacks de l'annonce. Ne regarder que le premier stack permettrait de contourner une
     * regle par item en la rangeant en deuxieme position.
     *
     * @param player         l'acheteur
     * @param item           l'annonce
     * @param auctionEconomy l'economie de l'annonce
     * @return le montant a verifier ET a prelever
     * @throws IllegalStateException si la configuration de taxe creerait de la monnaie
     */
    public static ZPurchaseCharge resolve(Player player, Item item, AuctionEconomy auctionEconomy) {

        BigDecimal price = item.getPrice();
        var taxConfiguration = auctionEconomy.getTaxConfiguration();

        TaxResult taxResult = TaxResult.disabled(price);

        // C-019 : ne PAS pre-filtrer sur taxConfiguration.getTaxType(). Une regle par item peut
        // porter un type d'achat alors que l'economie est declaree en SELL ; c'est
        // calculatePurchaseTax qui tranche, et lui seul.
        if (taxConfiguration.isEnabled()) {
            for (ItemStack itemStack : representativeStacks(item)) {
                TaxResult candidate = auctionEconomy.calculatePurchaseTax(player, price, itemStack);
                if (isBetterCandidate(candidate, taxResult)) {
                    taxResult = candidate;
                }
            }
        }

        BigDecimal buyerPays = taxResult.buyerPays();
        BigDecimal sellerReceives = taxResult.sellerReceives();

        // Garde-fou anti-frappe monetaire. Aucun mouvement d'argent ne doit avoir lieu si la
        // configuration ferait toucher au vendeur plus que ce que l'acheteur paie.
        if (taxResult.mintsMoney()) {
            throw new IllegalStateException("Tax configuration of economy '" + auctionEconomy.getName()
                    + "' would mint money on item " + item.getId() + ": buyer pays " + buyerPays
                    + " but seller would receive " + sellerReceives + ". Purchase aborted.");
        }

        return new ZPurchaseCharge(buyerPays, sellerReceives, taxResult);
    }

    /**
     * Retourne les stacks a soumettre aux regles par item. Une liste contenant un seul
     * {@code null} force une evaluation unique sur la configuration par defaut, ce qui est le
     * comportement attendu pour un item sans contenu.
     */
    private static List<ItemStack> representativeStacks(Item item) {
        if (item instanceof AuctionItem auctionItem) {
            var itemStacks = auctionItem.getItemStacks();
            if (itemStacks != null && !itemStacks.isEmpty()) return itemStacks;
        }
        return Collections.singletonList(null);
    }

    private static boolean isBetterCandidate(TaxResult candidate, TaxResult current) {
        if (candidate == null) return false;
        int comparison = candidate.taxAmount().compareTo(current.taxAmount());
        if (comparison > 0) return true;
        // A taxe egale (typiquement zero), preferer le resultat qui porte l'exoneration pour
        // que le message TAX_EXEMPT reste affiche a l'acheteur.
        return comparison == 0 && candidate.isBypassed() && !current.isBypassed();
    }
}

// ======= PurchaseService.java : remplacer les lignes 45-53 =======
// Supprimer l'import devenu inutile : fr.maxlego08.zauctionhouse.api.tax.TaxType
// Ajouter    : import fr.maxlego08.zauctionhouse.tax.ZPurchaseCharge;

        // Un seul et unique calcul du montant, avec les ItemStack REELS de l'annonce.
        // requiredBalance est desormais, par construction, exactement ce que ZAuctionManager
        // prelevera : le has() ne peut plus valider un montant inferieur au debit.
        final BigDecimal requiredBalance;
        try {
            requiredBalance = ZPurchaseCharge.resolve(player, item, auctionEconomy).buyerPays();
        } catch (IllegalStateException exception) {
            logger.severe(exception.getMessage());
            return CompletableFuture.completedFuture(
                    PurchaseResult.failure("Invalid tax configuration", PurchaseFailReason.INTERNAL_ERROR));
        }

// NOTE : `logger` est declare l.42, donc DEPLACER la ligne `var logger = this.plugin.getLogger();`
// et les autres `var` du bloc 39-43 AVANT ce bloc si ce n'est pas deja le cas (c'est le cas :
// le bloc 39-43 precede deja la l.45).

// ======= ListedItemsButton.java : remplacer les lignes 182-190 =======
// Supprimer l'import devenu inutile : fr.maxlego08.zauctionhouse.api.tax.TaxType
// Ajouter    : import fr.maxlego08.zauctionhouse.tax.ZPurchaseCharge;

        // Meme calcul que PurchaseService et ZAuctionManager : le prix affiche/verifie dans
        // la GUI ne peut plus differer du prix reellement debite.
        final BigDecimal requiredBalance;
        try {
            requiredBalance = ZPurchaseCharge.resolve(player, item, economy).buyerPays();
        } catch (IllegalStateException exception) {
            this.plugin.getLogger().severe(exception.getMessage());
            cache.set(PlayerCacheKey.PURCHASE_ITEM, false);
            return;
        }
```

## 3.7 — ZAuctionManager : le PRELEVEMENT passe par le meme resolveur (fin de la frappe monetaire)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : MEDIUM  
**Constats** : C-019, C-017

Supprime les lignes 734-780, ou le manager rebranchait sur `taxConfig.getTaxType()` pour interpreter un finalPrice calcule avec le type de la regle item. C'est cette reinterpretation qui, sur une economie PURCHASE portant une regle item CAPITALISM, faisait toucher au vendeur `price + taxe` pendant que l'acheteur ne payait que `price` : de la monnaie creee a chaque vente. Apres cette etape, buyerPays et sellerReceives viennent d'un unique appel et le garde-fou mintsMoney() est franchi avant tout mouvement.

```java
// Nouvel import : import fr.maxlego08.zauctionhouse.tax.ZPurchaseCharge;
// Remplacer integralement le bloc l.734-780 (de « // Calculate purchase tax » jusqu'a la
// fermeture du else qui pose buyerPays = price / sellerReceives = price) par :

        // Calcul UNIQUE du montant de l'achat. Strictement le meme appel que celui fait par
        // PurchaseService pour verifier le solde et par ListedItemsButton pour l'affichage :
        // le montant verifie et le montant preleve ne peuvent plus diverger, et le type de
        // taxe reellement applique est celui porte par le TaxResult, plus jamais celui de
        // l'economie rededuit a posteriori.
        var charge = ZPurchaseCharge.resolve(player, auctionItem, auctionEconomy);
        TaxResult taxResult = charge.taxResult();
        BigDecimal buyerPays = charge.buyerPays();
        BigDecimal sellerReceives = charge.sellerReceives();

        if (taxResult.isBypassed()) {

            message(this.plugin, player, Message.TAX_EXEMPT);

        } else if (taxResult.hasTax()) {

            if (taxResult.isReduced()) {
                message(this.plugin, player, Message.TAX_REDUCED, "%percentage%",
                        String.format("%.1f", 100 - taxResult.reductionPercentage()));
            }

            if (taxResult.appliedType() == TaxType.CAPITALISM) {
                // TVA : l'acheteur paie price + taxe, le vendeur touche le prix plein.
                message(player, Message.TAX_CAPITALISM_INFO,
                        "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount()),
                        "%percentage%", String.format("%.1f", taxResult.taxPercentage()));
            } else {
                // PURCHASE / BOTH : l'acheteur paie le prix plein, le vendeur touche price - taxe.
                message(player, Message.TAX_PURCHASE_APPLIED,
                        "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount()),
                        "%percentage%", String.format("%.1f", taxResult.taxPercentage()));
            }
        }

// Les variables locales `taxConfig`, `taxType`, `itemStacks` et `representativeItem` ne sont
// plus referencees nulle part dans purchaseAuctionItem : les supprimer. La variable `price`
// (l.723) peut rester, elle sert encore aux journaux/messages en aval.
```

## 3.8 — ZAuctionManager : mouvements d'argent verifiables, deposit differe pour les economies hors ligne, comptabilite non bloquante

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : MEDIUM  
**Constats** : C-015, C-032, C-041

Trois defauts sur le meme chemin. C-015 : le retrait pouvait echouer sans que rien ne l'apprenne, l'acheteur repartait avec l'item. C-032 : `deferDeposit` ne testait pas la capacite du provider a crediter hors ligne, donc LevelProvider.deposit no-opait et la transaction etait quand meme ecrite RETRIEVED. C-041 : une exception du provider levee pendant la construction des transactions s'echappait de purchaseAuctionItem APRES le debit et le credit, laissant l'item LISTED. La comptabilite devient non bloquante et les deux mouvements deviennent refusables.

```java
// Nouvel import : import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;

// ---- 1) Retrait acheteur : remplacer le try/catch des l.800-806 ----

        // On retire l'argent de l'acheteur. withdrawChecked pre-controle le solde SOUS VERROU
        // et rend false quand le provider refuse : l'achat s'arrete AVANT que l'item ne bouge,
        // AVANT que le vendeur ne soit credite et AVANT toute ecriture d'etat.
        boolean withdrawn = auctionEconomy.withdrawChecked(player.getUniqueId(), buyerPays,
                args(auctionEconomy.getWithdrawReason(), "%seller%", sellerName, "%items%", items));

        if (!withdrawn) {
            this.plugin.getLogger().severe("Withdraw of " + buyerPays + " from buyer " + player.getName()
                    + " was refused for item " + auctionItem.getId()
                    + ". Purchase aborted before any item or status movement.");
            message(this.plugin, player, Message.NOT_ENOUGH_MONEY);
            // Remonte dans le .exceptionally de PurchaseService, qui restaure le statut et
            // relache le verrou. Rien n'a bouge : c'est le comportement voulu.
            throw new IllegalStateException("Buyer payment refused for item " + auctionItem.getId());
        }

// ---- 2) deferDeposit : ajouter la quatrieme condition (l.810-813) ----

        boolean deferDeposit = !auctionEconomy.isAutoClaim()
                || (!sellerOnThisServer && auctionEconomy.mustBeOnline())
                || (!sellerOnThisServer && clusterBridge.isDistributed())
                // C-032 : ces economies (LEVEL, EXPERIENCE, ITEM, ZMENUITEMS) ne savent pas
                // crediter un joueur hors ligne et se contentent d'un no-op silencieux. Le
                // paiement doit devenir une transaction PENDING au lieu d'etre detruit.
                || (!sellerOnThisServer && !auctionEconomy.supportsOfflineDeposit());

// ---- 3) Credit vendeur : remplacer le bloc else des l.816-831 ----

        } else {

            transactionStatus = TransactionStatus.RETRIEVED;

            boolean deposited = auctionEconomy.depositChecked(seller.getUniqueId(), sellerReceives,
                    args(auctionEconomy.getDepositReason(), "%buyer%", player.getName(), "%items%", items));

            if (!deposited) {
                this.plugin.getLogger().severe("Failed to deposit " + sellerReceives + " to seller " + sellerName
                        + " for item " + auctionItem.getId() + ". Refunding the buyer and aborting the purchase.");

                if (!auctionEconomy.depositChecked(player.getUniqueId(), buyerPays,
                        args(auctionEconomy.getDepositReason(), "%buyer%", player.getName(), "%items%", items))) {
                    this.plugin.getLogger().severe("CRITICAL: buyer refund of " + buyerPays + " to "
                            + player.getName() + " ALSO failed for item " + auctionItem.getId()
                            + ". Manual reconciliation required.");
                }

                throw new IllegalStateException("Failed to deposit seller payment for item " + auctionItem.getId());
            }
        }

// ---- 4) Comptabilite : remplacer les deux blocs get().thenAccept().exceptionally() (l.833-852) ----

        // Comptabilite. Elle ne doit JAMAIS interrompre la livraison : a ce stade l'argent a
        // deja bouge. Toute exception du provider est journalisee, jamais propagee.
        // Les .exceptionally d'origine etaient de toute facon du code mort : ZAuctionEconomy.get
        // rend un CompletableFuture DEJA complete, aucune exception n'y transite.
        final BigDecimal finalBuyerPays = buyerPays;
        final BigDecimal finalSellerReceives = sellerReceives;
        final boolean deferred = deferDeposit;

        try {
            BigDecimal buyerBalance = readBalanceOrZero(auctionEconomy, player.getUniqueId());
            storageManager.createTransaction(auctionItem, player.getUniqueId(), economyName,
                    buyerBalance.add(finalBuyerPays), buyerBalance, finalBuyerPays.negate(), TransactionStatus.RETRIEVED);
        } catch (Exception exception) {
            this.plugin.getLogger().severe("Failed to create buyer transaction for item "
                    + auctionItem.getId() + ": " + exception.getMessage());
        }

        try {
            BigDecimal sellerBalance = readBalanceOrZero(auctionEconomy, seller.getUniqueId());
            var beforeBalance = deferred ? sellerBalance : sellerBalance.subtract(finalSellerReceives);
            storageManager.createTransaction(auctionItem, seller.getUniqueId(), economyName,
                    beforeBalance, sellerBalance, finalSellerReceives, transactionStatus);
        } catch (Exception exception) {
            this.plugin.getLogger().severe("Failed to create seller transaction for item "
                    + auctionItem.getId() + ": " + exception.getMessage());
        }

// ---- 5) Nouvelle methode privee, a placer pres de giveItem ----

    /**
     * Lecture defensive d'un solde, uniquement destinee aux colonnes before/after de
     * l'historique.
     * <p>
     * {@code AuctionEconomy.get} rend aujourd'hui un future DEJA complete (voir
     * {@code ZAuctionEconomy.get}) : le {@code join()} ne bloque donc jamais. Rendre get()
     * reellement asynchrone deplacerait la continuation de ListedItemsButton (son, setItem,
     * openInventory) hors du thread principal : c'est un chantier a part, pas celui-ci.
     * <p>
     * En cas de panne du provider on rend ZERO plutot que de faire echouer un achat deja
     * commis : les colonnes before/after sont alors fausses pour cette ligne, ce qui est
     * strictement preferable a la perte de la dette PENDING du vendeur. Le SEVERE qui precede
     * rend le cas reperable.
     */
    private BigDecimal readBalanceOrZero(AuctionEconomy auctionEconomy, UUID playerId) {
        try {
            var balance = auctionEconomy.get(playerId).join();
            return balance == null ? BigDecimal.ZERO : balance;
        } catch (Exception exception) {
            this.plugin.getLogger().severe("Unable to read the balance of " + playerId + " for economy "
                    + auctionEconomy.getName() + ": " + exception.getMessage());
            return BigDecimal.ZERO;
        }
    }
```

## 3.9 — SellService : taxe de vente atomique et remboursements verifiables

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/SellService.java`  
**Risque** : MEDIUM  
**Constats** : C-102

Le couple has()/withdraw() n'etait pas atomique et le retour du retrait etait jete : l'annonce partait publiee sans que la taxe soit percue. withdrawChecked fait le pre-controle SOUS VERROU et rend un booleen, ce qui supprime a la fois la fenetre et l'aveuglement. Les trois remboursements passent a depositChecked pour qu'un remboursement rate laisse une trace exploitable, et le motif d'echec devient INSUFFICIENT_FUNDS_FOR_TAX (la constante existe deja, elle n'etait jamais utilisee).

```java
// ---- 1) Remplacer le bloc has(...).thenApply(...) des l.335-348 ----
// Le has() prealable disparait : il ouvrait une fenetre entre la verification et le retrait,
// et son resultat etait de toute facon ignore par le withdraw void.
// IMPORTANT : on reste sur le thread APPELANT (thread du joueur : SellConfirmButton:76 ou
// CommandAuctionSell:100). Basculer ce retrait sur getExecutorService() deporterait les
// providers LEVEL/EXPERIENCE/ITEM, qui manipulent des API Bukkit main-thread-only, sur un pool :
// refus immediat sous Folia. Le confinement de threads de la couche economie releve du chantier 8.

        boolean withdrawn = auctionEconomy.withdrawChecked(player.getUniqueId(), taxResult.taxAmount(),
                "Sell tax (zAuctionHouse)");

        if (!withdrawn) {
            this.plugin.getScheduler().runAtEntity(player, task ->
                    message(this.plugin, player, Message.TAX_INSUFFICIENT_FUNDS,
                            "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount())));
            return CompletableFuture.completedFuture(null);
        }

        this.plugin.getScheduler().runAtEntity(player, task -> {
            if (taxResult.isReduced()) {
                message(this.plugin, player, Message.TAX_REDUCED,
                        "%percentage%", String.format("%.1f", 100 - taxResult.reductionPercentage()));
            }
            message(this.plugin, player, Message.TAX_SELL_APPLIED,
                    "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount()),
                    "%percentage%", String.format("%.1f", taxResult.taxPercentage()));
        });

        return CompletableFuture.completedFuture(taxResult);

// ---- 2) l.77 : motif d'echec correct ----

            if (taxResult == null) {
                resultFuture.complete(SellResult.failure("Insufficient funds for tax",
                        SellFailReason.INSUFFICIENT_FUNDS_FOR_TAX));
                return;
            }

// ---- 3) Nouvelle methode privee ----

    /**
     * Rembourse une taxe de vente deja prelevee.
     * <p>
     * Un remboursement qui echoue est un incident monetaire : il doit laisser une trace
     * exploitable pour une reconciliation manuelle. Avec une economie incapable de crediter un
     * joueur hors ligne, depositChecked refuse AVANT d'appeler le provider et journalise, la
     * ou l'ancien deposit() void perdait l'argent en silence.
     */
    private void refundSellTax(Player player, AuctionEconomy auctionEconomy, TaxResult taxResult, String reason) {
        if (taxResult == null || !taxResult.hasTax()) return;

        if (!auctionEconomy.depositChecked(player.getUniqueId(), taxResult.taxAmount(), reason)) {
            this.plugin.getLogger().severe("CRITICAL: unable to refund the sell tax of " + taxResult.taxAmount()
                    + " (" + auctionEconomy.getName() + ") to " + player.getUniqueId()
                    + " [" + reason + "]. Manual reconciliation required.");
        }
    }

// ---- 4) Remplacer les trois sites de remboursement (l.87, l.98, l.126) ----

                if (!player.isOnline()) {
                    refundSellTax(player, auctionEconomy, taxResult, "Refund sell tax (player disconnected)");
                    resultFuture.complete(SellResult.failure("Player disconnected", SellFailReason.PLAYER_DISCONNECTED));
                    return;
                }

                if (!verifyItemsInSlots(player, validSlotItems)) {
                    message(this.plugin, player, Message.SELL_ERROR_CHANGE);
                    refundSellTax(player, auctionEconomy, taxResult, "Refund sell tax (items changed)");
                    resultFuture.complete(SellResult.failure("Items changed", SellFailReason.ITEMS_CHANGED));
                    return;
                }

                    // ... dans le .exceptionally de createAuctionItem, en remplacement du deposit l.126 :
                    refundSellTax(player, auctionEconomy, taxResult, "Refund sell tax (sale failed)");
```

## 3.10 — economies.yml x6 locales + changelog + documentation

**Fichier** : `src/main/resources/economies.yml (+ es/ fr/ id/ it/ th/) et changelogs.md`  
**Risque** : LOW  
**Constats** : C-032, C-019

Les etapes 5 et 7 changent deux comportements VISIBLES des exploitants et des joueurs : une economie LEVEL/ITEM voit son must-be-online force, et une regle de taxe par item jusqu'ici silencieusement inerte sur une economie `type: SELL` se met a s'appliquer a l'achat. Livrer le code sans le commentaire produit exactement le ticket support « regression apres mise a jour ». Le plugin regenere ses fichiers dans la langue selectionnee au demarrage : les six jeux doivent etre identiques.

```sql
# economies.yml (racine EN), bloc must-be-online, remplacer le commentaire l.74-76 :

    # If true, money is only deposited when the seller is online.
    # If false, money is deposited even when the seller is offline.
    #
    # WARNING: this setting is FORCED to true at startup for the economy types LEVEL,
    # EXPERIENCE, ITEM and ZMENUITEMS. Those types are backed by the player entity and simply
    # do nothing when the player is offline, which used to destroy the seller's payment.
    # Sellers of those economies now receive their money through /ah claim.
    must-be-online: false

# economies.yml (racine EN), bloc tax:, a ajouter apres la description des quatre types :

      # NOTE on item-specific rules (tax.item-rules below):
      # An item rule is now evaluated on PURCHASE whatever the 'type' above is. A PURCHASE or
      # CAPITALISM rule declared on a 'type: SELL' economy used to be silently ignored at
      # purchase time; it is applied from now on. Review your item rules before updating.
      # For a listing containing several stacks, the rule that yields the HIGHEST tax wins,
      # which is the same rule already used on the selling side.

# Repliquer les DEUX blocs, traduits, dans :
#   src/main/resources/fr/economies.yml
#   src/main/resources/es/economies.yml
#   src/main/resources/it/economies.yml
#   src/main/resources/id/economies.yml
#   src/main/resources/th/economies.yml

# changelogs.md, sous `# Unreleased` :

- **Fixed** A purchase could withdraw more money than the amount that had been checked: the required balance was computed without the listing's real items, so item-specific tax rules were ignored on the check but applied on the debit
- **Fixed** A CAPITALISM item tax rule declared on a PURCHASE or BOTH economy credited the seller with `price + tax` while the buyer only paid `price`, creating money on every sale
- **Fixed** `withdraw` and `deposit` results were never checked: a refused withdrawal still handed the item to the buyer, and a refused deposit silently destroyed the seller's payment. Both now report success or failure, and a purchase is aborted before any item movement when the payment is refused
- **Fixed** Economies of type LEVEL, EXPERIENCE, ITEM and ZMENUITEMS silently destroyed the seller's payment when the seller was offline. Those payments become claimable pending transactions, and `must-be-online` is forced to true for them at startup
- **Fixed** The sell tax could be reported as paid without ever being collected: the balance check and the withdrawal are now a single verified operation, and a failed tax refund is logged
- **Fixed** An exception raised by the economy plugin while writing the transaction history aborted the purchase after the buyer had been charged and the seller credited, leaving the listing on sale
- **Changed** For a listing containing several stacks, the purchase tax now uses the highest matching item rule instead of only the first stack, which aligns it with the selling side
- **Changed** Item tax rules are now evaluated on purchase regardless of the economy `type`. A PURCHASE or CAPITALISM rule on a `type: SELL` economy used to be inert and now applies

# Documentation Docusaurus (C:/Users/Admin/Desktop/groupez/documentation), EN + FR :
#   plugins/zauctionhouse/docs/ (economies, taxes)
#   i18n/fr/docusaurus-plugin-content-docs-zauctionhouse/current/ (memes pages)
```

## 3.11 — [Addon Redis] Auto-claim du vendeur connecte sur un autre noeud, log marque lu APRES le credit

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/listener/listeners/ItemBoughtListener.java`  
**Risque** : HIGH  
**Constats** : C-059

Le vendeur connecte sur un AUTRE noeud recoit bien « votre item a ete vendu pour X » (l.71) et voit son log marque lu (l.77), mais rien ne le credite : `deferDeposit` vaut vrai des que le vendeur n'est pas sur le serveur acheteur en mode distribue, la ligne est PENDING, et les seuls appelants de claimMoney sont ClaimButton, CommandAuctionClaim et handlePlayerJoin (le vendeur est deja connecte, il ne rejoint pas). Aucune tache periodique n'existe : `grep runTimer src/main/java` ne rend que BossBarAnimation. Le marquage du log passe apres le credit pour qu'un echec soit rejouable.

```java
// A NE LIVRER QU'APRES LE CHANTIER 1 (C-003 : reservation des transactions PENDING par
// claim_token, et C-013 : ne marquer RETRIEVED que ce qui a ete credite). Sans le
// compare-and-set, ce declencheur ouvre une fenetre de DOUBLE CREDIT avec un /ah claim manuel
// ou un second ItemBoughtMessage rapproche.
//
// Aucun bump du pin API n'est necessaire : AuctionManager.getClaimService() et
// Configuration.getAutoClaimConfiguration() existent deja au SHA deb8f16 (verifie).

// Remplacer le bloc l.68-78 par :

            var seller = Bukkit.getOfflinePlayer(sellerUuid);
            if (!seller.isOnline()) {
                return;
            }

            var sellerPlayer = seller.getPlayer();
            manager.message(sellerPlayer, Message.ITEM_BOUGHT_SELLER,
                    "%items%", message.itemDisplay(),
                    "%price%", message.price(),
                    "%seller%", message.sellerName(),
                    "%buyer%", message.buyerName());
            manager.clearPlayerCache(sellerPlayer,
                    PlayerCacheKey.ITEMS_SELLING,
                    PlayerCacheKey.PENDING_MONEY_DATA,
                    PlayerCacheKey.HISTORY_DATA);

            // C-059 : le vendeur est connecte sur CE noeud alors que la vente a eu lieu
            // ailleurs. Le serveur acheteur a ecrit une transaction PENDING (deferDeposit vaut
            // vrai des que le vendeur n'est pas sur le serveur acheteur en mode distribue) et
            // PERSONNE ne la reclame : handlePlayerJoin n'est appele qu'a la connexion, et il
            // n'existe aucune tache periodique de claim. On declenche donc l'auto-claim ici.
            var autoClaim = auctionPlugin.getConfiguration().getAutoClaimConfiguration();
            if (!autoClaim.enabled()) {
                // Comportement historique conserve quand l'auto-claim est desactive : le
                // vendeur passera par /ah claim, le log peut etre marque lu tout de suite.
                auctionPlugin.getStorageManager().markPurchaseLogAsRead(id, sellerUuid);
                return;
            }

            // Petit delai : la ligne PENDING est ecrite de facon asynchrone par le serveur
            // acheteur (ZAuctionManager -> storageManager.createTransaction). Reclamer
            // immediatement ne trouverait rien et laisserait l'argent en attente jusqu'a la
            // prochaine connexion. On reutilise le delai deja configure pour l'auto-claim.
            long delayTicks = autoClaim.delayTicks() > 0 ? autoClaim.delayTicks() : 20L;

            auctionPlugin.getScheduler().runLater(claimTask -> {

                if (!sellerPlayer.isOnline()) return;

                manager.getClaimService().claimMoney(sellerPlayer).whenComplete((claimResult, claimError) -> {
                    if (claimError != null) {
                        this.plugin.getLogger().severe("Auto-claim failed for seller " + sellerUuid
                                + " after a cross-server sale of item " + id + ": " + claimError.getMessage());
                        // Le log reste NON lu : la prochaine connexion ou le prochain /ah claim
                        // rejouera l'operation. Ne jamais marquer lu un paiement non credite.
                        return;
                    }
                    auctionPlugin.getStorageManager().markPurchaseLogAsRead(id, sellerUuid);
                });

            }, delayTicks);
```

### Ruptures d'API et stratégie de compatibilité

- TaxResult (API/src/main/java/fr/maxlego08/zauctionhouse/api/tax/TaxResult.java) gagne une 8e composante `TaxType appliedType`, ce qui change son constructeur canonique. STRATÉGIE DE COMPATIBILITÉ : on déclare explicitement un constructeur @Deprecated à 7 arguments qui délègue au canonique avec appliedType=null. Son descripteur JVM (BigDecimal, double, BigDecimal, BigDecimal, boolean, boolean, double)V est identique à celui d'aujourd'hui, donc tout addon compilé contre un SHA antérieur qui fait `new TaxResult(...)` continue de se lier ET de compiler. Les 7 accesseurs existants sont inchangés. Ne cassent réellement que : les patrons de déconstruction de record (`case TaxResult(var a, ... var g)`) et la réflexion sur getRecordComponents(). Vérifié par grep : l'addon Redis ne référence NI TaxResult, NI AuctionEconomy, NI TaxConfiguration.
- AuctionEconomy (API/.../api/economy/AuctionEconomy.java) gagne trois méthodes `default` : `boolean withdrawChecked(UUID, BigDecimal, String)`, `boolean depositChecked(UUID, BigDecimal, String)`, `boolean supportsOfflineDeposit()`. Purement ADDITIF, source- et binaire-compatible : toute implémentation tierce hérite d'un default qui délègue aux `void` existants et retourne true. Les signatures `void withdraw` / `void deposit` sont CONSERVÉES et non dépréciées dans ce lot. REJET EXPLICITE du correctif de l'audit qui proposait `boolean withdraw(...)` + surcharges `void` @Deprecated : deux méthodes de même nom et même liste de paramètres ne peuvent pas différer par le seul type de retour, cela NE COMPILE PAS en Java, et changer le type de retour casse le descripteur JVM.
- TaxConfiguration : AUCUN changement. Je rejette l'ajout de `default TaxType resolvePurchaseTaxType(ItemStack)` proposé par la vérification de C-019 : il exige que le consommateur refasse une seconde résolution de règle avec exactement le même ItemStack, ce qui rouvre le mode de défaillance « deux résolutions peuvent diverger » que ce chantier existe précisément pour fermer. Le type appliqué doit voyager AVEC le résultat.
- AuctionManager.purchaseItem, StorageManager, AuctionClusterBridge, Repository, LockToken : AUCUN changement. Le pin `fr.maxlego08.zauctionhouse:zauctionhousev4-api:deb8f16` de l'addon Redis (REDIS/build.gradle.kts:49) N'A PAS À ÊTRE BUMPÉ. Vérifié par `git show deb8f16` : `AuctionManager.getClaimService()` (l.81) et `Configuration.getAutoClaimConfiguration()` (l.185) existent déjà à ce SHA, ce sont les deux seules API utilisées par l'étape 11.
- ZAuctionEconomy.supportsOfflineDeposit(CurrencyProvider) est ajoutée en `public static` : classe d'implémentation hors module api/, aucun impact publié. Elle existe pour que ZEconomyManager et l'instance partagent UN SEUL prédicat.

### Migrations de schéma

- AUCUNE migration de schéma dans ce chantier. Aucune colonne, aucune table, aucun index n'est ajouté ou modifié. C'est délibéré : cela rend le rollback par simple downgrade de jar entièrement sûr (voir rollback).
- CONSÉQUENCE DE DONNÉES SANS MIGRATION (étape 8, C-032) : les économies de type LEVEL / EXPERIENCE / ITEM / ZMENUITEMS produisent désormais des lignes %prefix%transactions en statut PENDING là où elles écrivaient RETRIEVED (et perdaient l'argent). Aucune donnée existante n'est réécrite ; seules les ventes postérieures au déploiement changent de statut. Ces lignes sont consommées par le /ah claim déjà en place.

### Changements de configuration

- RECTIFICATIF PRÉALABLE : le brief et le CLAUDE.md annoncent 4 jeux de langue. `ls src/main/resources/` en contient SIX : racine (anglais) + es/ + fr/ + id/ + it/ + th/. Toute réplication ci-dessous se fait donc 6 fois, pas 4.
- AUCUNE nouvelle clé YAML dans tout le chantier. Aucune clé de messages.yml non plus : les cas d'échec ajoutés réutilisent Message.NOT_ENOUGH_MONEY et Message.TAX_INSUFFICIENT_FUNDS, ou se contentent d'un log SEVERE console. C'est un choix : ajouter une clé aurait imposé une constante dans l'enum publié Message ET six fichiers à synchroniser pour un gain nul.
- economies.yml (étape 10) — bloc `must-be-online` (racine l.74-77) : le commentaire « Useful for economy plugins that don't support offline transactions » doit devenir un avertissement explicite indiquant que le réglage est désormais FORCÉ à true au démarrage pour les types LEVEL, EXPERIENCE, ITEM et ZMENUITEMS, et que les vendeurs de ces économies reçoivent leur argent via /ah claim. À répliquer à l'identique dans es/, fr/, id/, it/, th/.
- economies.yml (étape 10) — bloc `tax:` (racine l.109-145, description des types SELL/PURCHASE/BOTH/CAPITALISM) : ajouter que les règles `tax.item-rules` sont désormais évaluées à l'achat QUEL QUE SOIT le `type` de l'économie (une règle PURCHASE ou CAPITALISM sur une économie `type: SELL` était silencieusement inerte, elle s'applique maintenant), et que la règle retenue pour une annonce multi-stacks est celle qui produit la TAXE LA PLUS ÉLEVÉE — même règle que le chemin de vente. À répliquer dans les six fichiers.
- changelogs.md, section `# Unreleased` : trois entrées obligatoires marquées **Changed** (et non **Fixed**) car visibles des joueurs — (1) les règles de taxe par item s'appliquent désormais à l'achat sur les économies non-PURCHASE, (2) la taxe d'achat d'une annonce multi-stacks retient la plus élevée au lieu du premier stack, (3) les économies LEVEL/EXPERIENCE/ITEM/ZMENUITEMS passent en must-be-online forcé et produisent des paiements à réclamer.
- Documentation Docusaurus C:/Users/Admin/Desktop/groupez/documentation : EN `plugins/zauctionhouse/docs/` et FR `i18n/fr/docusaurus-plugin-content-docs-zauctionhouse/current/` — pages économies et taxes, mêmes trois points.

### Fichiers nouveaux

- D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/java/fr/maxlego08/zauctionhouse/tax/ZPurchaseCharge.java

### Validation

- INVARIANT SQL ANTI-FRAPPE MONETAIRE (le test central du chantier). Apres une campagne d'achats, `SELECT item_id, SUM(value) AS solde FROM zauctionhousev4_transactions GROUP BY item_id HAVING SUM(value) > 0;` doit rendre ZERO ligne. Un solde strictement positif signifie que le vendeur a touche plus que l'acheteur n'a paye. Avant le chantier, une economie `type: PURCHASE` + regle item `CAPITALISM 15%` produit une ligne par vente.
- TEST 1 (C-019, frappe monetaire). economies.yml : `tax.enabled: true`, `tax.type: PURCHASE`, `tax.item-rules` avec une regle CAPITALISM 15% sur DIAMOND. Lister un diamant a 1000. AVANT : acheteur debite 1000, vendeur credite 1150. APRES : acheteur debite 1150, vendeur credite 1000. Verifier par `/eco balance` des deux comptes et par la requete d'invariant ci-dessus.
- TEST 2 (C-017/C-015, verifie != preleve). economies.yml : `tax.type: CAPITALISM`, `amount: 5`, plus une regle item CAPITALISM 20% sur DIAMOND_SWORD. Lister une epee a 1000. Donner a l'acheteur EXACTEMENT 1050. AVANT : le has() passe (requiredBalance calcule avec ItemStack null = 1050) puis le withdraw prend 1200 -> solde negatif chez Vault, item livre. APRES : le bouton et le service exigent 1200, l'achat est refuse avec NOT_ENOUGH_MONEY, l'annonce reste visible, solde inchange a 1050.
- TEST 3 (C-017, stack non representatif). Meme configuration, mais la regle 20% porte sur un item place en DEUXIEME position d'une annonce multi-stacks (vendre 2 stacks via l'inventaire de vente). APRES : le montant exige inclut la regle (taxe la plus elevee retenue), ce que confirme le message TAX_CAPITALISM_INFO affiche a l'acheteur.
- TEST 4 (C-015, retrait refuse). Economie `type: LEVEL`. Mettre l'acheteur a exactement le niveau du prix. Ouvrir la GUI de confirmation, puis depuis la console `xp set <acheteur> 0 levels`, puis confirmer. AVANT : l'item est livre et setLevel part en negatif. APRES : SEVERE `Withdraw of ... was refused`, message NOT_ENOUGH_MONEY, item toujours LISTED (`SELECT storage_type FROM zauctionhousev4_items WHERE id = ?` = 'LISTED'), verrou Redis relache (`EXISTS auction:lock:<id>` = 0).
- TEST 5 (C-032, vendeur hors ligne). Economie `type: LEVEL`, `auto-claim: true`, `must-be-online: false` dans economies.yml. (a) Au demarrage, la console DOIT afficher le WARNING de forcage. (b) Vendeur deconnecte, acheteur achete. AVANT : `SELECT status FROM zauctionhousev4_transactions WHERE item_id=? AND player_unique_id='<vendeur>'` = 'RETRIEVED' et aucun niveau credite. APRES : statut 'PENDING'. (c) Le vendeur se reconnecte et fait `/ah claim` : ses niveaux augmentent et le statut passe a 'RETRIEVED'.
- TEST 6 (C-041, comptabilite non bloquante). Mettre MySQL en pause (`docker pause mysql`) juste apres avoir clique sur confirmer l'achat, puis reprendre au bout de 5 s. APRES : la console montre `Failed to create buyer transaction for item <id>` en SEVERE, MAIS l'acheteur a bien son item et la ligne passe en PURCHASED/DELETED des le retour de la base. AVANT, l'exception s'echappait avant `removeItem`/`updateItem` et laissait la ligne LISTED.
- TEST 7 (C-084, serialisation par compte). Buyer avec un solde de 1000. Preparer 20 annonces a 100 chacune. Depuis DEUX serveurs du cluster, lancer les 20 achats simultanement avec le meme compte (deux clients, ou un script de clics). Assertion : le solde final est >= 0 et `SELECT SUM(value) FROM zauctionhousev4_transactions WHERE player_unique_id='<acheteur>' AND value < 0` >= -1000. Sans le verrou strippe, plus de 10 achats reussissent sur un meme serveur.
- TEST 8 (C-102, taxe de vente). Economie VAULT, `tax.enabled: true`, `tax.type: SELL`, `amount-type: FIXED`, `amount: 100`. (a) Solde 50, mettre un item en vente : l'item reste dans l'inventaire, message TAX_INSUFFICIENT_FUNDS, aucune ligne dans `zauctionhousev4_items`, solde toujours 50. (b) Solde exactement 100 : la vente aboutit, solde a 0, une ligne LISTED existe et le log SALE porte `sell_tax=100`.
- TEST 9 (C-059, cross-serveur). Deux serveurs A et B, MySQL partage + addon Redis, `auto-claim.enable: true`, `auto-claim.delay-ticks: 20`. Vendeur connecte sur B, annonce a 500. Acheteur sur A achete. APRES (et seulement une fois le chantier 1 livre) : dans la seconde, le vendeur sur B recoit le message CLAIM_ECONOMY_SUCCESS et son solde augmente de 500 ; `SELECT status FROM zauctionhousev4_transactions WHERE item_id=?` = 'RETRIEVED'. Verifier aussi que `read_at` du log d'achat n'est renseigne QU'APRES le credit : couper MySQL pendant le claim -> le log doit rester non lu.
- TEST 10 (compatibilite binaire de TaxResult). Apres `JAVA_HOME="C:/Users/Admin/.jdks/ms-21.0.9" ./gradlew :API:build`, executer `javap -p -classpath target-api/zauctionhousev4-api.jar fr.maxlego08.zauctionhouse.api.tax.TaxResult | grep 'TaxResult('`. La sortie DOIT contenir a la fois un constructeur a sept parametres et un constructeur a huit parametres. Completer par un plugin de test compile contre l'ANCIEN jar d'API qui fait `new TaxResult(BigDecimal.ONE, 5.0, BigDecimal.TEN, BigDecimal.ONE, false, false, 0.0)` et l'executer sur le serveur avec le nouveau plugin : aucune NoSuchMethodError.
- TEST 11 (non-regression addon). Reconstruire l'addon Redis SANS toucher a son pin `zauctionhousev4-api:deb8f16` : `JAVA_HOME="C:/Users/Admin/.jdks/ms-21.0.9" ./gradlew build` dans `zAuctionHouse Redis` doit reussir apres l'etape 11. Demarrer un noeud avec l'ANCIEN jar d'addon et le NOUVEAU plugin : aucune AbstractMethodError, les achats cross-serveur fonctionnent (sans l'auto-claim de l'etape 11, qui vit dans l'addon).
- COMPILATION PAR ETAPE : apres CHACUNE des etapes 1 a 9, `JAVA_HOME="C:/Users/Admin/.jdks/ms-21.0.9" ./gradlew build` doit produire target/zAuctionHouse.jar sans erreur. Les etapes 1, 3, 4 et 5 ne changent aucun comportement observable : un serveur de recette doit s'y comporter a l'identique (utile pour bissecter une regression).

### Questions ouvertes pour le mainteneur

- ARBITRAGE PRINCIPAL — accepter la 8e composante de TaxResult ? Je tranche pour OUI (etape 1), contre la recommandation de la fiche C-019 qui proposait un `default TaxType resolvePurchaseTaxType(ItemStack)` sur TaxConfiguration. Motif : cette alternative oblige le consommateur a refaire une SECONDE resolution de regle avec exactement le meme ItemStack, ce qui rouvre le mode de defaillance « deux resolutions divergent » que ce chantier existe pour fermer. Le cout est un @Deprecated de plus dans l'API et la rupture des patrons de deconstruction de record (aucun usage connu). Si le mainteneur refuse toute evolution de TaxResult, replier sur resolvePurchaseTaxType et accepter que la garantie devienne conventionnelle au lieu d'etre structurelle.
- REGLE DU STACK REPRESENTATIF A L'ACHAT — je retiens « taxe la plus elevee parmi tous les stacks », par alignement avec SellService.applySellTaxAsync et pour qu'une regle par item ne puisse pas etre contournee en rangeant l'item en deuxieme position. C'est un CHANGEMENT VISIBLE : une annonce multi-stacks deja en vente peut devenir plus chere a l'achat. L'alternative conservatrice est de garder le premier stack (une ligne a changer dans ZPurchaseCharge.representativeStacks) ; l'invariant « verifie == preleve » tient dans les deux cas.
- ACTIVATION DES REGLES ITEM SUR LES ECONOMIES NON-PURCHASE (C-019, etape 7) — sur un serveur configure en `type: SELL` avec une regle item PURCHASE 10 % laissee la en croyant qu'elle ne servait pas, les acheteurs se mettent d'un coup a payer 10 % de plus. Faut-il livrer tel quel avec l'annonce de changelog, ou derriere une cle d'opt-in temporaire (`tax.item-rules.apply-on-purchase: true`) ? Une cle couterait six fichiers economies.yml a repliquer et une methode sur TaxConfiguration ; je ne l'ai pas mise dans le plan.
- FORCAGE DE must-be-online (C-032, etape 5) — les exploitants d'economies LEVEL/ITEM avec auto-claim voient apparaitre des paiements a reclamer la ou l'argent disparaissait. C'est la correction, mais certains serveurs ont peut-etre calibre leur economie sur cette fuite. Confirmer que le forcage silencieux (WARNING console) est acceptable, ou preferer un refus de chargement de l'economie mal configuree.
- DEPRECATION DE void withdraw / void deposit — le plan les CONSERVE non deprecies pour ne pas noyer les integrateurs sous des avertissements. Faut-il les marquer @Deprecated des maintenant, avec suppression et passage a une signature booleenne en version majeure ?
- SEQUENCEMENT DE L'ETAPE 11 (C-059) — elle ne doit PAS partir avant le chantier 1 (C-003). Qui garantit l'ordre entre les deux chantiers, et le chantier 1 sera-t-il livre dans la meme release ? Si non, l'etape 11 doit etre sortie du lot et livree separement ; le reste du chantier n'en depend pas.
- PROPRIETE DE C-027 — le catalogue de verification lui attribue `ch: 3`, mais il est absent de la liste de perimetre de ce chantier. L'etape 8 ne couvre que sa moitie C-041 (les exceptions de comptabilite ne cassent plus la livraison). Restent non traites : le passage des cinq methodes fire-and-forget de ZStorageManager du scheduler FoliaLib (jamais draine) vers getExecutorService, et surtout l'ecriture de la ligne PENDING AVANT le withdraw quand deferDeposit vaut true. A rattacher explicitement a un chantier.
- CONTENTION DU VERROU STRIPPE (C-084) — tenir un moniteur pendant un appel a un plugin d'economie inconnu (potentiellement JDBC synchrone, ex. RedisEconomy) est un risque de blocage. 64 stripes bornent la casse sans l'eliminer. Faut-il ajouter un garde-fou (log WARNING au-dela de N ms passees dans le moniteur) ou considerer le risque acceptable ?

### Retour arrière

"AUCUNE MIGRATION DE SCHEMA n'est introduite : le rollback est un simple downgrade de jar, sans intervention en base. Procedure : (1) remettre le jar precedent de zAuctionHouseV4 dans plugins/ et, si l'etape 11 a ete deployee, egalement le jar precedent de zAuctionHouseRedis (l'etape 11 est autonome cote addon, elle peut etre annulee seule) ; (2) supprimer economies.yml des dossiers de langue actifs OU laisser le plugin les regenerer au demarrage : l'ancien jar reecrit ses propres commentaires, les cles de valeurs sont inchangees (aucune cle n'a ete ajoutee ou renommee, donc aucune configuration existante ne devient invalide) ; (3) redemarrer. TROIS RESIDUS A CONNAITRE. (a) Les ventes realisees pendant que le nouveau jar tournait avec une economie LEVEL/EXPERIENCE/ITEM/ZMENUITEMS ont laisse des lignes zauctionhousev4_transactions en PENDING au lieu de RETRIEVED. Ces lignes restent parfaitement lisibles par l'ancien jar et se reclament avec /ah claim : ne PAS les purger. Les compter avant de decider : SELECT COUNT(*) FROM zauctionhousev4_transactions WHERE status = 'PENDING'. (b) Les achats qui ont ete REFUSES par withdrawChecked n'ont laisse aucune trace en base (rien n'a bouge) : rien a reconcilier. (c) Si l'etape 11 a tourne sans le chantier 1, chercher un eventuel double credit avant de rollback : SELECT player_unique_id, item_id, COUNT(*) FROM zauctionhousev4_transactions WHERE status='RETRIEVED' GROUP BY player_unique_id, item_id HAVING COUNT(*) > 1. ROLLBACK PARTIEL RECOMMANDE en cas d'incident cible : les etapes 1 a 4 ne changent aucun comportement (elles ne font qu'ajouter du contrat), les etapes 5, 7, 8 et 9 sont les seules porteuses de changement observable. Si l'incident porte sur le montant des taxes, revenir a l'etat post-etape-6 suffit et conserve tous les garde-fous de retrait/depot ; si l'incident porte sur les paiements hors ligne, revenir a l'etat post-etape-4."


---

# Chantier 4 — Identité, fencing et états terminaux du verrou distribué

Ce chantier transforme le verrou distribué, aujourd'hui purement décoratif, en une exclusion mutuelle réellement vérifiable. Il tient en une seule idée : un verrou n'a de valeur que si son détenteur est identifiable (C-008/C-025 : `LockToken.of` rend « item:<id> », identique sur tous les nœuds, donc la garde de propriété d'UNLOCK_SCRIPT est vraie pour tout le monde), si son échec d'acquisition se signale de la même façon des deux côtés (C-110), si sa libération rend un verdict (C-061), s'il n'est jamais posé par un chemin non atomique (C-058/C-099), si les états terminaux de l'item le refusent réellement (C-002), s'il n'est jamais abandonné en fuite (C-111/C-112) et si son bail couvre toute la section critique (C-007). Les onze premières étapes sont strictement additives côté API — aucune signature existante ne bouge, l'addon déjà déployé sur le SHA `deb8f16` continue de tourner et se trouve même corrigé sans recompilation dès l'étape 1, puisqu'il résout `LockToken` depuis le classloader du plugin principal. Deux divergences assumées vis-à-vis de l'audit et du catalogue sont documentées : les états SOLD/REMOVED deviennent terminaux pour le SEUL scope LISTED (les rendre terminaux tout court rendrait tout item acheté ou expiré définitivement irréclamable), et la compensation de verrou de C-111 est branchée sur `lockFuture.copy()` et non sur le futur source, car `CompletableFuture.orTimeout` retourne `this` et détruirait sinon la capture tardive du jeton. Le chantier ne prétend pas fermer la duplication à lui seul : sans le compare-and-set en base du chantier 1, le verrou reste une optimisation de contention, jamais une preuve.

**Prérequis**

- Chantier 2 / lot QUICK — C-012 + C-042 (réécriture de ZAuctionManager.adminRemoveItem:679-709). ARBITRAGE : mon étape 4 (LocalAuctionClusterBridge rendant LockToken.noop() au lieu d'un failedFuture) INTRODUIRAIT une duplication en mono-serveur si adminRemoveItem ne testait pas le jeton noop — un double-clic admin exécuterait deux fois giveItem. J'absorbe donc la garde noop MINIMALE (sans nouvelle clé de message, en réutilisant la branche `!available` existante) dans mon étape 3, qui doit précéder l'étape 4. Si le chantier 2 réécrit adminRemoveItem en premier, ma moitié « garde noop » du diff devient un no-op à droper et seule la partie C-112 (libération du verrou dans le .exceptionally) doit être fusionnée dans leur réécriture.
- Chantier 1 — C-026 (compare-and-set `where storage_type = source` + propagation du rowcount). Sans lui, le verrou distribué reste l'unique rempart : ce chantier réduit fortement les fenêtres mais ne prouve rien côté base. Le fence monotone (colonne `fence` BIGINT sur %prefix%items, clause `WHERE fence < ?`) est la moitié structurelle de C-025 et appartient au chantier 1 — je ne le livre PAS ici (il exigerait une migration de schéma et de faire transiter le fence par LockToken, type publié).
- Chantier 8 — C-077 / C-052 : executor dédié pour les 7 supplyAsync/runAsync du bridge Redis, `setMaxWait` sur le pool Jedis, suppression des emprunts imbriqués (plugin.sendMessage à l'intérieur d'un try-with-resources Jedis). Mon étape 11 (watchdog) emprunte une connexion Jedis par tick de renouvellement : sur un pool par défaut de code à 10 connexions dont une monopolisée par le thread abonné, plusieurs achats concurrents peuvent l'assécher. Livrer le chantier 8 avant ou avec l'étape 11.
- Chantier 5 — C-006 / C-009 : revalidation autoritaire en base sous verrou côté achat ET côté retrait. Mes états terminaux Redis (étape 9) sont la ceinture ; la relecture DB est les bretelles.
- Chantier 7 — C-070 point 3 : ExpireService.expireListedItemClustered souffre exactement de la même fuite de verrou que C-111 (tokenHolder renseigné APRÈS le orTimeout, ligne 273-275). Je ne la corrige pas ici pour ne pas empiéter, mais la correction est identique à celle de mon étape 5 (lockFuture.copy()).

**Constats couverts** : C-008, C-025, C-002, C-058, C-099, C-110, C-061, C-111, C-112, C-007, C-106

*~430 LOC*

## 4.1 — API — jeton de verrou unique par acquisition (socle de tout le chantier)

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/API/src/main/java/fr/maxlego08/zauctionhouse/api/cluster/LockToken.java`  
**Risque** : LOW  
**Constats** : C-008, C-025

C'est la seule étape qui corrige un binaire déjà déployé sans le recompiler : l'addon déclare zauctionhousev4-api en compileOnly et résout LockToken depuis le classloader du plugin principal, donc changer le CORPS de of(Item) rend immédiatement discriminante la garde de propriété d'UNLOCK_SCRIPT (`currentToken ~= tokenValue`), sur les nœuds Redis existants, sans toucher une seule ligne de Lua. Vérifié : les 8 sites qui manipulent un LockToken (PurchaseService:100/:168, RemoveService:232/:294, ExpireService:276/:304, ZAuctionManager:703, LocalAuctionClusterBridge:31) ne comparent jamais sa valeur à autre chose que LockToken.noop().value(), et le jeton n'est ni persisté en base ni sérialisé dans un message Redis. Aucune donnée ne dépend de son déterminisme. La sentinelle unavailable() est introduite ici plutôt qu'à l'étape 12 pour ne pas modifier deux fois un fichier d'API publiée.

```java
package fr.maxlego08.zauctionhouse.api.cluster;

import fr.maxlego08.zauctionhouse.api.item.Item;

import java.util.UUID;

/**
 * Represents a distributed lock token for cluster synchronization.
 * <p>
 * Un jeton est UNIQUE PAR ACQUISITION : il porte l'identite du detenteur et un nonce
 * aleatoire. C'est ce qui rend la verification de propriete du script Lua de
 * deverrouillage reellement discriminante entre serveurs — avant, tous les noeuds
 * produisaient litteralement la meme chaine "item:&lt;id&gt;" pour un item donne, et un
 * deverrouillage retardataire detruisait le verrou VIVANT d'un autre serveur.
 *
 * @param value the unique identifier for this lock
 */
public record LockToken(String value) {

    private static final String NOOP_VALUE = "NOOP";
    private static final String UNAVAILABLE_VALUE = "UNAVAILABLE";

    /**
     * Sentinelle « acquisition refusee par contention » : un autre acteur detient deja le
     * verrou. L'operation doit echouer proprement (LOCK_FAILED), pas remonter une erreur.
     *
     * @return a no-op lock token
     */
    public static LockToken noop() {
        return new LockToken(NOOP_VALUE);
    }

    /**
     * Sentinelle « item dans un etat terminal » : l'annonce est vendue, retiree ou detruite,
     * aucune acquisition n'est possible et ne le sera jamais. Distinguer ce cas de la simple
     * contention permet de remonter ITEM_NOT_AVAILABLE plutot que LOCK_FAILED.
     * <p>
     * Consommee a partir de {@link AuctionClusterBridge#checkAndLock(Item, UUID, fr.maxlego08.zauctionhouse.api.item.StorageType)}.
     *
     * @return an unavailable lock token
     */
    public static LockToken unavailable() {
        return new LockToken(UNAVAILABLE_VALUE);
    }

    /**
     * Creates a lock token for the specified auction item.
     * <p>
     * CONSERVEE POUR COMPATIBILITE BINAIRE : la signature et le type de retour sont
     * inchanges, seul le corps a change. Tout addon compile contre une version anterieure
     * de cette API beneficie donc du jeton unique sans recompilation. Preferer
     * {@link #issue(Item, UUID)}, qui inscrit en plus l'identite du detenteur.
     *
     * @param auctionItem the item to create a lock token for
     * @return a lock token identifying this acquisition
     */
    public static LockToken issue(Item auctionItem) {
        return issue(auctionItem, null);
    }

    /**
     * @deprecated remplace par {@link #issue(Item, UUID)}, qui porte l'identite du detenteur.
     */
    @Deprecated
    public static LockToken of(Item auctionItem) {
        return issue(auctionItem, null);
    }

    /**
     * Emet un jeton unique pour cette acquisition precise.
     *
     * @param auctionItem the item to lock
     * @param ownerId     identite du detenteur (UUID d'instance de serveur, ou du joueur en
     *                    mono-serveur) ; {@code null} accepte
     * @return a lock token unique to this acquisition
     */
    public static LockToken issue(Item auctionItem, UUID ownerId) {
        return new LockToken("item:" + auctionItem.getId()
                + ':' + (ownerId == null ? "-" : ownerId)
                + ':' + UUID.randomUUID());
    }

    /**
     * @return {@code true} si ce jeton signale un echec d'acquisition par contention
     */
    public boolean isNoop() {
        return NOOP_VALUE.equals(this.value);
    }

    /**
     * @return {@code true} si ce jeton signale un item dans un etat terminal
     */
    public boolean isUnavailable() {
        return UNAVAILABLE_VALUE.equals(this.value);
    }

    /**
     * @return {@code true} si un verrou a reellement ete acquis (ni noop, ni unavailable)
     */
    public boolean isAcquired() {
        return !isNoop() && !isUnavailable();
    }
}
```

## 4.2 — API — extensions default du contrat AuctionClusterBridge (verdict de libération, bail, portée)

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/API/src/main/java/fr/maxlego08/zauctionhouse/api/cluster/AuctionClusterBridge.java`  
**Risque** : LOW  
**Constats** : C-061, C-110, C-007, C-002

Regrouper TOUTES les additions d'API en une seule étape est délibéré : l'addon Redis est épinglé sur un SHA git figé (build.gradle.kts:49), donc chaque publication de l'API impose un bump de ce pin et un cycle de release croisé. Une seule publication suffit ainsi pour les étapes 7 à 12. Les cinq méthodes sont des default : un bridge tiers non recompilé conserve exactement le comportement d'aujourd'hui. Le point clé est lockLeaseDuration() == Duration.ZERO par défaut : aucun watchdog n'est armé tant que le bridge n'a pas explicitement déclaré son bail, ce qui rend l'étape 11 totalement inerte face à un addon ancien. Cette étape documente aussi, dans le javadoc de lockItem, la convention que C-110 va faire respecter — c'est le seul point de variation qu'un addon tiers pouvait interpréter de travers.

```java
// --- imports a ajouter ---
import java.time.Duration;

// --- javadoc de lockItem : remplacer le bloc existant (l.26-34) par celui-ci ---
    /**
     * Attempts to lock the item for the given buyer in the specified storage context, preventing
     * other servers from selling it simultaneously.
     * <p>
     * CONTRAT (seul point de variation entre implementations, a respecter imperativement) :
     * <ul>
     *   <li>acquisition reussie : future complete avec un jeton tel que
     *       {@link LockToken#isAcquired()} vaut {@code true} ;</li>
     *   <li>acquisition REFUSEE (contention, ou item dans un etat terminal) : future complete
     *       NORMALEMENT avec {@link LockToken#noop()} — jamais un future en erreur ;</li>
     *   <li>future en ERREUR : reserve aux pannes de transport (Redis injoignable, timeout).</li>
     * </ul>
     * Un appelant qui confond les deux derniers cas transforme une contention banale en
     * INTERNAL_ERROR et interrompt les chaines de retrait de masse.
     *
     * @param item        item to lock
     * @param buyerId     UUID of the buyer
     * @param storageType storage bucket the item resides in
     * @return future containing a lock token to be used when unlocking
     */

// --- methodes a ajouter en fin d'interface, avant isDistributed() ---

    /**
     * Variante de {@link #checkAvailability(Item)} portant la PORTEE de l'operation.
     * <p>
     * La disponibilite n'est pas absolue : un item vendu (etat SOLD) doit etre refuse a un
     * nouvel ACHAT (portee {@code LISTED}) mais rester verrouillable pour la RECLAMATION par
     * son acheteur (portee {@code PURCHASED}). De meme un item expire (etat REMOVED) doit
     * etre refuse a l'achat mais reclamable par son vendeur (portee {@code EXPIRED}).
     * <p>
     * L'implementation par defaut delegue a la variante sans portee, qui reste le predicat
     * permissif historique : seule la destruction definitive y bloque.
     *
     * @param item        item being evaluated
     * @param storageType portee de l'operation envisagee, {@code null} pour un simple affichage
     * @return future resolving to {@code true} if the item can still be locked for that scope
     */
    default CompletableFuture<Boolean> checkAvailability(Item item, StorageType storageType) {
        return checkAvailability(item);
    }

    /**
     * Libere le verrou ET indique s'il nous appartenait encore au moment de la liberation.
     * <p>
     * {@code false} est le seul signal exploitable de « verrou perdu ou vole » : il doit etre
     * journalise en SEVERE par l'appelant. Le code retour existait deja cote script Lua, il
     * etait simplement jete.
     * <p>
     * Methode {@code default} et NON un changement de type de retour de
     * {@link #unlockItem(Item, LockToken, StorageType)} : le type de retour fait partie du
     * descripteur JVM, le modifier leverait AbstractMethodError sur tout bridge compile contre
     * une version anterieure — c'est-a-dire un verrou jamais relache a chaque achat.
     * L'implementation par defaut delegue et rend {@code true} (optimisme assume, strictement
     * equivalent au comportement actuel).
     *
     * @param item        item to unlock
     * @param lockToken   token returned by {@link #lockItem(Item, UUID, StorageType)}
     * @param storageType storage bucket the item resides in
     * @return future resolving to {@code true} if the lock was still held by this token
     */
    default CompletableFuture<Boolean> releaseLock(Item item, LockToken lockToken, StorageType storageType) {
        return unlockItem(item, lockToken, storageType).thenApply(ignored -> Boolean.TRUE);
    }

    /**
     * Duree du bail pose par {@link #lockItem(Item, UUID, StorageType)}.
     * <p>
     * {@link Duration#ZERO} signifie « pas de bail » : le verrou n'expire pas tout seul (cas du
     * bridge mono-serveur) ou l'implementation ne sait pas le prolonger. Les appelants
     * n'arment alors AUCUN watchdog de renouvellement, ce qui rend cette mecanique totalement
     * inerte face a un bridge non mis a jour.
     *
     * @return la duree du bail, ou {@link Duration#ZERO} si la notion ne s'applique pas
     */
    default Duration lockLeaseDuration() {
        return Duration.ZERO;
    }

    /**
     * Prolonge le bail du verrou, si et seulement si ce jeton le detient toujours.
     * <p>
     * L'implementation par defaut rend {@code false} : « je ne sais pas prolonger ». Combinee a
     * {@link #lockLeaseDuration()} == ZERO, elle garantit qu'aucun appelant ne s'appuiera sur
     * une prolongation qui n'a pas eu lieu.
     *
     * @param item        item whose lock must be extended
     * @param lockToken   token returned by {@link #lockItem(Item, UUID, StorageType)}
     * @param storageType storage bucket the item resides in
     * @return future resolving to {@code true} if the lease was actually extended
     */
    default CompletableFuture<Boolean> renewLock(Item item, LockToken lockToken, StorageType storageType) {
        return CompletableFuture.completedFuture(Boolean.FALSE);
    }

    /**
     * Indique si ce jeton detient encore le verrou, a verifier juste avant toute ecriture
     * engageante (debit, remise d'item).
     * <p>
     * L'implementation par defaut rend {@code true} : elle ne peut pas faire pire que le
     * comportement actuel, qui ne verifie rien du tout.
     *
     * @param item        item to check
     * @param lockToken   token returned by {@link #lockItem(Item, UUID, StorageType)}
     * @param storageType storage bucket the item resides in
     * @return future resolving to {@code true} if the lock is still held by this token
     */
    default CompletableFuture<Boolean> isHeldBy(Item item, LockToken lockToken, StorageType storageType) {
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }
```

## 4.3 — adminRemoveItem — garde de jeton noop et libération du verrou en cas d'exception

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : MEDIUM  
**Constats** : C-112, C-042 (garde noop absorbée — prérequis dur de l'étape 4)

Cette étape DOIT précéder l'étape 4. Aujourd'hui LocalAuctionClusterBridge signale la contention par un failedFuture, ce qui interrompt fortuitement la chaîne admin ; dès que l'étape 4 le fera rendre LockToken.noop() (le contrat commun), un double-clic admin en mono-serveur exécuterait DEUX fois giveItem — une duplication que j'introduirais moi-même. La garde noop est donc un prérequis dur, pas un confort. Je la livre sans nouvelle clé de message : elle réutilise exactement le traitement de la branche `!available` existante (log console + updateInventory), ce qui évite d'ajouter Message.ADMIN_ITEM_NOT_AVAILABLE dans les SIX messages.yml. Le second volet est C-112 proprement dit : le jeton n'est aujourd'hui visible que dans le thenAccept, donc toute exception survenue entre le lock et la fin laisse l'item verrouillé — jusqu'au TTL côté Redis, et DÉFINITIVEMENT avec le bridge local qui n'a aucun TTL. Attention à la garde isAcquired() sur le chemin d'erreur : sans elle on appellerait unlockItem avec un noop, inoffensif côté Redis (sortie ligne 246) mais destructeur côté bridge local dont le remove était inconditionnel.

```java
// --- imports a ajouter dans ZAuctionManager.java ---
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import java.util.concurrent.atomic.AtomicReference;

// --- remplacer integralement le bloc l.679-709 ---

        // Jeton capture des l'acquisition : sans lui, toute exception survenant apres le
        // verrouillage laisse l'item verrouille — jusqu'au TTL cote Redis, et POUR TOUJOURS
        // avec LocalAuctionClusterBridge, qui n'a aucun TTL.
        var tokenHolder = new AtomicReference<LockToken>();

        clusterBridge.checkAvailability(item, storageType).thenCompose(available -> {

            if (!available) {
                this.plugin.getLogger().info("Item is not available");
                inventoryManager.updateInventory(admin);
                return this.<LockToken>failedFuture(new IllegalStateException("Item indisponible"));
            }

            return clusterBridge.lockItem(item, admin.getUniqueId(), storageType);

        }).thenCompose(lockToken -> {

            tokenHolder.set(lockToken);

            // Convention commune aux deux bridges : un echec d'acquisition se signale par un
            // jeton noop, jamais par un future en erreur. Sans cette garde, deux retraits admin
            // concurrents — ou un simple double-clic — executent TOUS LES DEUX la remise :
            // duplication franche. C'est le pendant de PurchaseService:100 et RemoveService:232.
            if (lockToken == null || !lockToken.isAcquired()) {
                this.plugin.getLogger().info("Item " + item.getId() + " is already locked, admin removal aborted");
                inventoryManager.updateInventory(admin);
                return this.<LockToken>failedFuture(new IllegalStateException("Item deja verrouille"));
            }

            return clusterBridge.removeItem(item, storageType).thenApply(v -> lockToken);

        }).thenAccept(lockToken -> {

            removeItem(storageType, item);

            this.plugin.getStorageManager().updateItem(item, StorageType.DELETED);
            clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);

            giveItem(admin, item);

            var targetName = item.getSellerUniqueId().equals(targetUniqueId) ? item.getSellerName() : item.getBuyerName();
            message(this.plugin, admin, Message.ADMIN_ITEM_REMOVED, "%items%", item.getItemDisplay(), "%target%", targetName == null ? "unknown" : targetName);

            inventoryManager.updateInventory(admin);

            clusterBridge.unlockItem(item, lockToken, storageType);

        }).exceptionally(e -> {
            this.plugin.getLogger().severe("Failed to remove item for admin: " + e.getMessage());

            // Liberation du verrou sur le chemin d'erreur. La garde isAcquired() est
            // INDISPENSABLE : deverrouiller avec un jeton noop est inoffensif cote Redis mais
            // casserait le verrou legitime d'un autre appelant cote bridge local.
            var token = tokenHolder.get();
            if (token != null && token.isAcquired()) {
                clusterBridge.unlockItem(item, token, storageType).exceptionally(unlockError -> {
                    this.plugin.getLogger().severe("Failed to unlock item " + item.getId() + " after admin removal error: " + unlockError.getMessage());
                    return null;
                });
            }

            inventoryManager.updateInventory(admin);
            return null;
        });
```

## 4.4 — LocalAuctionClusterBridge — alignement sur la convention noop et libération conditionnelle

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/java/fr/maxlego08/zauctionhouse/cluster/LocalAuctionClusterBridge.java`  
**Risque** : MEDIUM  
**Constats** : C-110

Deux défauts symétriques dans onze lignes. À l'acquisition, la contention est signalée par un failedFuture là où le bridge Redis rend un jeton noop : les appelants transforment donc une contention banale en INTERNAL_ERROR, ce qui interrompt notamment les chaînes de retrait de masse (ZAuctionManager:1092-1110). À la libération, `itemLocks.remove(item.getId())` est INCONDITIONNEL : un déverrouillage retardataire casse le verrou fraîchement acquis par un autre appelant — c'est le pendant mono-serveur exact de C-008. Stocker la VALEUR du jeton au lieu de l'UUID de l'acheteur rend le remove conditionnel ; le paramètre buyerId cesse d'être mémorisé, mais il est désormais inscrit DANS le jeton via LockToken.issue, donc l'information n'est pas perdue. La structure `release(...)` privée évite la récursion infinie qu'on obtiendrait en faisant déléguer unlockItem à releaseLock alors que le default de releaseLock délègue à unlockItem.

```java
package fr.maxlego08.zauctionhouse.cluster;

import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class LocalAuctionClusterBridge implements AuctionClusterBridge {

    // On memorise la VALEUR DU JETON, pas l'UUID de l'acheteur : c'est ce qui permet une
    // liberation CONDITIONNELLE. Avec l'ancienne map indexee par acheteur, un deverrouillage
    // retardataire faisait un remove inconditionnel et cassait le verrou fraichement acquis
    // par un autre appelant. L'identite du detenteur reste connue : elle est inscrite dans le
    // jeton par LockToken.issue(item, buyerId).
    private final ConcurrentHashMap<Integer, String> itemLocks = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Boolean> checkAvailability(Item item) {
        return CompletableFuture.completedFuture(!this.itemLocks.containsKey(item.getId()));
    }

    @Override
    public CompletableFuture<LockToken> lockItem(Item item, UUID buyerId, StorageType storageType) {
        var token = LockToken.issue(item, buyerId);

        // Convention commune aux deux bridges (cf. javadoc AuctionClusterBridge#lockItem) :
        // un echec d'acquisition se signale par LockToken.noop() et NON par un future en
        // erreur, qui est reserve aux pannes de transport. Avant ce correctif, une contention
        // mono-serveur remontait en INTERNAL_ERROR et interrompait la chaine de retrait de
        // masse au lieu de simplement sauter l'annonce concernee.
        var existingLock = this.itemLocks.putIfAbsent(item.getId(), token.value());
        if (existingLock != null) {
            return CompletableFuture.completedFuture(LockToken.noop());
        }

        return CompletableFuture.completedFuture(token);
    }

    @Override
    public CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType) {
        release(item, lockToken);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> releaseLock(Item item, LockToken lockToken, StorageType storageType) {
        return CompletableFuture.completedFuture(release(item, lockToken));
    }

    /**
     * Liberation conditionnelle : on ne retire l'entree que si elle porte EXACTEMENT notre
     * jeton. Implementation partagee par unlockItem et releaseLock, sans deleguer de l'une a
     * l'autre — le default de releaseLock appelle unlockItem, l'inverse boucquerait.
     */
    private boolean release(Item item, LockToken lockToken) {
        if (lockToken == null || !lockToken.isAcquired()) {
            return false;
        }
        return this.itemLocks.remove(item.getId(), lockToken.value());
    }

    @Override
    public CompletableFuture<Void> notifyItemBought(Player player, Item item) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> notifyItemListed(Item item) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> notifyItemStatusChange(Item item, ItemStatus oldStatus, ItemStatus newStatus) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeItem(Item item, StorageType storageType) {
        return CompletableFuture.completedFuture(null);
    }
}
```

## 4.5 — Verrou fuité par le orTimeout : émission unique et libération compensatoire

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/java/fr/maxlego08/zauctionhouse/services/PurchaseService.java`  
**Risque** : MEDIUM  
**Constats** : C-111

CORRECTION MAJEURE AU CORRECTIF DE L'AUDIT ET DU CATALOGUE. Les deux proposent de brancher un whenComplete « sur le futur SOURCE et non sur le futur dérivé ». C'est inapplicable tel quel : java.util.concurrent.CompletableFuture.orTimeout(long, TimeUnit) retourne `this`, pas un nouveau futur. `lockFuture.whenComplete(...)` suivi de `return lockFuture.orTimeout(...)` observerait donc la TimeoutException et JAMAIS le jeton tardif — le correctif serait un no-op silencieux. Il faut `lockFuture.copy().orTimeout(...)` : copy() (Java 9+) rend un futur distinct dont l'échec par timeout ne touche pas la source, laquelle continue et livre son jeton au whenComplete. La poignée de main croisée (chainAbandoned écrit avant lecture de tokenHolder d'un côté, tokenHolder écrit avant lecture de chainAbandoned de l'autre) garantit qu'exactement un des deux acteurs libère ; AtomicBoolean/AtomicReference fournissent les barrières mémoire nécessaires. Ce correctif n'est SÛR que parce que l'étape 1 est livrée : avec un jeton déterministe, un unlock compensatoire tardif détruirait le verrou légitime d'un autre nœud — ce serait une régression introduite par ce patch, pas un défaut préexistant.

```java
// --- imports a ajouter ---
import java.util.concurrent.atomic.AtomicBoolean;

// --- apres la l.76 (declaration des holders) ---

        // Poignee de main croisee avec le .exceptionally terminal : la chaine ecrit
        // chainAbandoned PUIS lit tokenHolder ; le whenComplete de lockItem ecrit tokenHolder
        // PUIS lit chainAbandoned. Exactement un des deux cotes declenche donc la liberation,
        // meme quand le jeton arrive APRES le orTimeout. unlockIssued garantit qu'un seul
        // unlock part, tous chemins confondus.
        final AtomicBoolean chainAbandoned = new AtomicBoolean(false);
        final AtomicBoolean unlockIssued = new AtomicBoolean(false);

// --- remplacer les l.92-93 ---

                    var lockFuture = clusterBridge.lockItem(item, player.getUniqueId(), StorageType.LISTED);

                    lockFuture.whenComplete((lateToken, lockError) -> {
                        if (lateToken == null || !lateToken.isAcquired()) return;
                        tokenHolder.set(lateToken);
                        if (chainAbandoned.get() && unlockIssued.compareAndSet(false, true)) {
                            // Verrou obtenu APRES l'abandon de la chaine : plus personne en aval
                            // ne le liberera, il resterait pose jusqu'au TTL (30 s par defaut) et
                            // pour toujours en mono-serveur.
                            clusterBridge.unlockItem(item, lateToken, StorageType.LISTED).exceptionally(unlockError -> {
                                logger.severe("Failed to release late-acquired lock for item " + item.getId() + ": " + unlockError.getMessage());
                                return null;
                            });
                        }
                    });

                    // copy() est INDISPENSABLE : orTimeout(...) retourne `this`, il completerait
                    // donc exceptionnellement le futur SOURCE et le whenComplete ci-dessus ne
                    // verrait jamais le jeton tardif.
                    return lockFuture.copy().orTimeout(performanceConfig.lockItemTimeoutMs(), TimeUnit.MILLISECONDS);

// --- remplacer la l.100 (garde noop) ---

                    if (!token.isAcquired()) {

// --- remplacer la l.137-138 (unlock nominal) ---

                                .thenCompose(v -> unlockIssued.compareAndSet(false, true)
                                        ? clusterBridge.unlockItem(item, token, StorageType.LISTED)
                                                .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                                        : CompletableFuture.<Void>completedFuture(null))

// --- remplacer la l.152-153 (unlock du chemin solde insuffisant) ---

                            .thenCompose(v -> unlockIssued.compareAndSet(false, true)
                                    ? clusterBridge.unlockItem(item, token, StorageType.LISTED)
                                            .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                                    : CompletableFuture.<Void>completedFuture(null))

// --- en TETE du .exceptionally (l.156), avant tout le reste ---

                }).exceptionally(e -> {
                    // Signale l'abandon AVANT de lire le jeton : c'est la moitie « chaine » de la
                    // poignee de main croisee.
                    chainAbandoned.set(true);

// --- remplacer les l.167-173 (liberation sur erreur) ---

                    var token = tokenHolder.get();
                    if (token != null && token.isAcquired() && unlockIssued.compareAndSet(false, true)) {
                        clusterBridge.unlockItem(item, token, StorageType.LISTED).exceptionally(unlockError -> {
                            logger.severe("Failed to unlock item after error: " + unlockError.getMessage());
                            return null;
                        });
                    }
```

## 4.6 — Même compensation dans RemoveService (acquireLockStep) + token rendu volatile

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/java/fr/maxlego08/zauctionhouse/services/RemoveService.java`  
**Risque** : LOW  
**Constats** : C-111

RemoveService a exactement la même fuite : le jeton n'est enregistré dans le contexte qu'à l'étape 3 (changeStatusAndNotifyStep, l.230), donc un timeout sur acquireLockStep laisse context.token à null et releaseLockOnError ne libère rien. Deux détails que ni l'audit ni le catalogue ne relèvent : (1) `context.token` devient écrit depuis le thread qui complète lockFuture et lu depuis le thread de la chaîne, il doit donc être volatile, sinon la libération compensatoire peut lire null sur une architecture à mémoire faible ; (2) le double-unlock RemoveService:261 puis :293-300 est justement la fenêtre 2 de C-008 — l'unlock retardataire qui vole le verrou d'autrui — et unlockIssued la ferme définitivement.

```java
// --- imports a ajouter ---
import java.util.concurrent.atomic.AtomicBoolean;

// --- RemovalContext : rendre token volatile et ajouter les deux drapeaux ---

        // Ecrit par le thread qui complete lockItem, lu par la chaine et par le chemin
        // d'erreur : volatile est obligatoire depuis que la capture est faite hors chaine.
        volatile LockToken token;
        boolean statusChanged;
        boolean localRemovalCompleted;
        RemoveResult result;

        // Poignee de main croisee (cf. PurchaseService) : garantit qu'exactement un acteur
        // libere le verrou, y compris quand il est acquis APRES le orTimeout.
        final AtomicBoolean chainAbandoned = new AtomicBoolean(false);
        final AtomicBoolean unlockIssued = new AtomicBoolean(false);

// --- remplacer le corps de acquireLockStep (l.216-223) ---

    private CompletableFuture<LockToken> acquireLockStep(RemovalContext context, boolean available, Player player, AuctionClusterBridge clusterBridge, PerformanceConfiguration config) {

        if (!available) {
            context.onUnavailable.run();
            context.result = RemoveResult.failure("Item not available", RemoveFailReason.ITEM_NOT_AVAILABLE);
            return failedFuture(new IllegalStateException("Item indisponible"));
        }

        var lockFuture = clusterBridge.lockItem(context.item, player.getUniqueId(), context.storageType);

        lockFuture.whenComplete((lateToken, lockError) -> {
            if (lateToken == null || !lateToken.isAcquired()) return;
            context.token = lateToken;
            if (context.chainAbandoned.get() && context.unlockIssued.compareAndSet(false, true)) {
                clusterBridge.unlockItem(context.item, lateToken, context.storageType).exceptionally(unlockError -> {
                    this.plugin.getLogger().severe("Failed to release late-acquired lock for item " + context.item.getId() + ": " + unlockError.getMessage());
                    return null;
                });
            }
        });

        // copy() : sans elle, orTimeout completerait le futur source et le whenComplete
        // ci-dessus ne verrait jamais le jeton tardif (orTimeout retourne `this`).
        return lockFuture.copy().orTimeout(config.lockItemTimeoutMs(), TimeUnit.MILLISECONDS);
    }

// --- remplacer la garde noop de changeStatusAndNotifyStep (l.232) ---

        if (token == null || !token.isAcquired()) {

// --- remplacer le corps de unlockAndCompleteStep (l.261) ---

        var unlock = context.unlockIssued.compareAndSet(false, true)
                ? clusterBridge.unlockItem(context.item, context.token, context.storageType)
                        .orTimeout(config.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                : CompletableFuture.<Void>completedFuture(null);

        return unlock.thenApply(v -> {
            context.result = RemoveResult.success("Item removed successfully", true);
            return context.result;
        });

// --- en TETE de handleRemovalException, avant le log ---

        // Moitie « chaine » de la poignee de main croisee : ecrire AVANT de lire le jeton.
        context.chainAbandoned.set(true);

// --- remplacer le corps de releaseLockOnError (l.293-300) ---

    private void releaseLockOnError(RemovalContext context, AuctionClusterBridge clusterBridge, Logger logger) {
        var token = context.token;
        if (token != null && token.isAcquired() && context.unlockIssued.compareAndSet(false, true)) {
            clusterBridge.unlockItem(context.item, token, context.storageType).exceptionally(unlockError -> {
                logger.severe("Failed to unlock item after error: " + unlockError.getMessage());
                return null;
            });
        }
    }
```

## 4.7 — Consommer releaseLock et journaliser tout verrou perdu

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/java/fr/maxlego08/zauctionhouse/services/PurchaseService.java, RemoveService.java, ExpireService.java, ZAuctionManager.java`  
**Risque** : LOW  
**Constats** : C-061, C-025

Le script Lua de déverrouillage rend déjà 0 quand le jeton ne correspond plus — c'est LE signal « mon verrou m'a été volé », le seul observable en production, et il part aujourd'hui à la poubelle (RedisAuctionClusterBridge:257 et :267 appellent evalsha sans affecter le résultat). Cette étape n'a d'effet visible qu'une fois l'étape 9 livrée (le bridge Redis doit d'abord surcharger releaseLock), mais elle est inoffensive avant et doit passer APRÈS l'étape 5/6 : sans la garantie d'émission unique d'unlockIssued, un second unlock légitime remonterait false et polluerait la console de faux SEVERE. À noter : avec un orTimeout, un dépassement remonte en TimeoutException et n'atteint jamais le thenAccept — l'alerte ne couvre donc que le cas « la commande a abouti mais le verrou ne nous appartenait plus », pas le cas « unlock parti dans le vide », qui reste couvert par la libération compensatoire de l'étape 5.

```java
// PurchaseService — chemin nominal (remplace le bloc introduit a l'etape 5)

                                .thenCompose(v -> unlockIssued.compareAndSet(false, true)
                                        ? clusterBridge.releaseLock(item, token, StorageType.LISTED)
                                                .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                                                .thenAccept(released -> warnIfLockLost(logger, item, released))
                                        : CompletableFuture.<Void>completedFuture(null))

// PurchaseService — chemin solde insuffisant : meme substitution.

// PurchaseService — helper prive a ajouter en fin de classe

    /**
     * Journalise en SEVERE la perte d'un verrou : le script Lua a repondu que le jeton presente
     * n'etait plus celui inscrit dans Redis. C'est le seul signal exploitable d'un vol de
     * verrou (bail expire pendant la section critique, ou reprise par un autre noeud).
     */
    private void warnIfLockLost(java.util.logging.Logger logger, Item item, Boolean released) {
        if (!Boolean.TRUE.equals(released)) {
            logger.severe("Cluster lock for item " + item.getId() + " was NOT held by this server at release time."
                    + " Another node may have taken it over: check lock-ttl-seconds against the duration of the critical section.");
        }
    }

// RemoveService — unlockAndCompleteStep

        var unlock = context.unlockIssued.compareAndSet(false, true)
                ? clusterBridge.releaseLock(context.item, context.token, context.storageType)
                        .orTimeout(config.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                        .thenAccept(released -> {
                            if (!Boolean.TRUE.equals(released)) {
                                this.plugin.getLogger().severe("Cluster lock for item " + context.item.getId()
                                        + " was NOT held by this server at release time; the removal may have raced another node.");
                            }
                        })
                : CompletableFuture.<Void>completedFuture(null);

// ExpireService — l.305 (whenComplete terminal)

                    var token = tokenHolder.get();
                    if (token != null && token.isAcquired()) {
                        clusterBridge.releaseLock(item, token, StorageType.LISTED).whenComplete((released, ex) -> {
                            if (ex != null) {
                                logger.severe("Failed to unlock item " + item.getId() + " after expiration: " + ex.getMessage());
                            } else if (!Boolean.TRUE.equals(released)) {
                                logger.severe("Cluster lock for item " + item.getId() + " was NOT held at release time after expiration.");
                            }
                        });
                    }

// ZAuctionManager — adminRemoveItem, chemin nominal (l.703) et chemin d'erreur (etape 3)

            clusterBridge.releaseLock(item, lockToken, storageType).whenComplete((released, ex) -> {
                if (ex != null) {
                    this.plugin.getLogger().severe("Failed to unlock item " + item.getId() + " after admin removal: " + ex.getMessage());
                } else if (!Boolean.TRUE.equals(released)) {
                    this.plugin.getLogger().severe("Cluster lock for item " + item.getId() + " was NOT held by this server at release time (admin removal).");
                }
            });
```

## 4.8 — Addon — bump du pin API et auto-guérison des scripts Lua

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouse Redis/build.gradle.kts + src/main/java/fr/maxlego08/zauctionhouse/redis/RedisAuctionClusterBridge.java`  
**Risque** : LOW  
**Constats** : C-058

reloadScriptsIfNeeded() existe depuis toujours et n'a AUCUN appelant : un seul échec de loadScripts() au démarrage (Redis en LOADING après un redémarrage avec un gros dataset, coupure réseau d'une seconde) condamne le nœud au chemin non atomique À VIE. Cette étape doit précéder l'étape 9, qui supprime ce chemin : sans auto-guérison, le fail-closed transformerait un incident Redis de deux secondes en hôtel des ventes définitivement mort. Je REFUSE le point 3 du correctif de l'audit (« faire échouer onEnable si loadScripts échoue ») : loadRedis() valide déjà la connexion par un PING, or PING est autorisé pendant que Redis est en LOADING alors que SCRIPT LOAD ne l'est pas — un simple redémarrage de Redis ferait alors systématiquement tomber l'addon au boot de CHAQUE serveur du réseau. Je supprime aussi les deux `scriptsLoaded = false;` posés hors moniteur (l.203 et l.263) : loadScripts() positionne déjà le drapeau dans les deux sens, les conserver ne fait qu'exposer un état intermédiaire aux threads concurrents. Placement critique : reloadScriptsIfNeeded() est appelée AVANT le try-with-resources, jamais dedans, pour ne pas créer un emprunt Jedis imbriqué de plus (cf. C-052/C-077).

```java
// --- build.gradle.kts : ligne 49 ---
    compileOnly("fr.maxlego08.zauctionhouse:zauctionhousev4-api:<SHA_PUBLIE_A_L_ETAPE_2>")

// --- RedisAuctionClusterBridge : imports ---
import java.util.concurrent.atomic.AtomicLong;

// --- champs a ajouter ---
    /** Intervalle minimal entre deux tentatives de rechargement, pour ne pas marteler un Redis KO. */
    private static final long SCRIPT_RELOAD_MIN_INTERVAL_MS = 5_000L;
    private final AtomicLong lastScriptLoadAttempt = new AtomicLong(0L);

// --- remplacer loadScripts() ---
    private void loadScripts() {
        synchronized (scriptLoadLock) {
            this.lastScriptLoadAttempt.set(System.currentTimeMillis());
            try (Jedis jedis = jedisPool.getResource()) {
                this.lockScriptSha = jedis.scriptLoad(LOCK_SCRIPT);
                this.unlockScriptSha = jedis.scriptLoad(UNLOCK_SCRIPT);
                this.renewScriptSha = jedis.scriptLoad(RENEW_SCRIPT);
                this.scriptsLoaded = true;
                plugin.getLogger().info("Redis Lua scripts loaded successfully.");
            } catch (Exception e) {
                // SEVERE et non WARNING : tant que les scripts manquent, ce noeud REFUSE de
                // verrouiller (cf. etape suivante), donc plus aucun achat ni retrait n'aboutit.
                plugin.getLogger().severe("Failed to load Lua scripts: " + e.getMessage()
                        + ". Locking is disabled on this node until they can be reloaded.");
                this.scriptsLoaded = false;
            }
        }
    }

// --- remplacer reloadScriptsIfNeeded() (jusqu'ici sans aucun appelant) ---
    /**
     * Recharge les scripts s'ils manquent, au plus une fois toutes les 5 secondes.
     * Appelee en tete du corps asynchrone de lockItem/unlockItem/renewLock — AVANT tout
     * emprunt de connexion, pour ne jamais creer d'emprunt Jedis imbrique.
     */
    private void reloadScriptsIfNeeded() {
        if (this.scriptsLoaded) return;

        long now = System.currentTimeMillis();
        long last = this.lastScriptLoadAttempt.get();
        if (now - last < SCRIPT_RELOAD_MIN_INTERVAL_MS) return;
        if (!this.lastScriptLoadAttempt.compareAndSet(last, now)) return;

        synchronized (scriptLoadLock) {
            if (this.scriptsLoaded) return; // double verification sous moniteur
            loadScripts();
        }
    }

// --- lockItem : premiere instruction du supplyAsync, AVANT le try-with-resources ---
            reloadScriptsIfNeeded();

// --- unlockItem : idem, premiere instruction du runAsync ---
            reloadScriptsIfNeeded();

// --- supprimer les deux `scriptsLoaded = false;` hors moniteur (l.203 et l.263) :
//     l'appel direct a loadScripts() suffit, il positionne le drapeau dans les deux sens.
```

## 4.9 — Addon — suppression des replis non atomiques : échec fermé

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouse Redis/src/main/java/fr/maxlego08/zauctionhouse/redis/RedisAuctionClusterBridge.java`  
**Risque** : MEDIUM  
**Constats** : C-099

Les replis (lockItem:220-238, unlockItem:276-285) ne sont pas une dégradation gracieuse, ce sont un vecteur de duplication : le HGET/SET/HSET en trois commandes séparées n'offre aucune exclusion mutuelle, et le HSET final écrase l'état terminal qu'un autre nœud vient de poser entre-temps, remettant en circulation un item détruit. Sans script, la bonne réponse est de refuser : lockItem rend LockToken.noop() après un SEVERE, ce que les trois services traitent déjà proprement par leur branche LOCK_FAILED existante ; unlockItem se contente de journaliser et laisse le verrou expirer par TTL, ce qui est auto-cicatrisant grâce à la branche LOCKED-périmé de LOCK_SCRIPT. C'est la seule correction du chantier qui peut RENDRE L'HÔTEL DES VENTES INUTILISABLE (offre Redis managée interdisant EVAL/SCRIPT) : elle est indissociable de l'étape 8. Cette étape doit précéder l'étape 10, sinon les modifications de prédicat que l'étape 10 apporterait au repli seraient jetées — perte sèche de travail.

```java
// --- lockItem : corps complet apres suppression du repli ---
    @Override
    public CompletableFuture<LockToken> lockItem(Item item, UUID lockerId, StorageType storageType) {
        return CompletableFuture.supplyAsync(() -> {
            reloadScriptsIfNeeded();

            if (!scriptsLoaded) {
                // ECHEC FERME. Sans script, l'exclusion mutuelle n'est pas garantie et le repli
                // non atomique ecraserait l'etat terminal pose entre-temps par un autre noeud :
                // mieux vaut refuser l'operation que remettre en circulation un item detruit.
                plugin.getLogger().severe("Refusing to lock item " + item.getId()
                        + ": Lua scripts are unavailable on this Redis instance. Auctions are non-transactional"
                        + " until EVALSHA works again (check that your Redis provider allows EVAL/SCRIPT).");
                return LockToken.noop();
            }

            LockToken token = LockToken.issue(item, ZAuctionHouseRedis.INSTANCE_UUID);
            long ttlMs = lockTtl != null ? lockTtl.toMillis() : 30_000L;

            try (Jedis jedis = jedisPool.getResource()) {
                Object result = evalLock(jedis, item, token, ttlMs, storageType);
                return result instanceof Long value && value == 1L ? token : LockToken.noop();
            }
        });
    }

    /**
     * Execute LOCK_SCRIPT avec UNE seule reprise sur NOSCRIPT (script evince du cache Redis).
     */
    private Object evalLock(Jedis jedis, Item item, LockToken token, long ttlMs, StorageType storageType) {
        var keys = Arrays.asList(lockKey(item), itemKey(item));
        var args = Arrays.asList(token.value(), String.valueOf(ttlMs), listedScopeArg(storageType));
        try {
            return jedis.evalsha(lockScriptSha, keys, args);
        } catch (Exception e) {
            if (!isNoScriptError(e)) throw e;
            plugin.getLogger().warning("Lua script not found in Redis, reloading...");
            loadScripts();
            if (!scriptsLoaded) {
                plugin.getLogger().severe("Lua scripts still unavailable, refusing to lock item " + item.getId());
                return 0L;
            }
            return jedis.evalsha(lockScriptSha, keys, args);
        }
    }

// --- unlockItem : le repli l.276-285 est SUPPRIME et remplace par ---
            if (!scriptsLoaded) {
                // On ne tente pas de deverrouillage non atomique : le verrou expirera par TTL
                // (lock-ttl-seconds) et la branche LOCKED-perime de LOCK_SCRIPT le recuperera.
                plugin.getLogger().severe("Cannot release lock for item " + item.getId()
                        + ": Lua scripts unavailable. The lock will expire on its own within "
                        + (lockTtl != null ? lockTtl.toSeconds() : 30) + "s.");
                return false;
            }
```

## 4.10 — Addon — états terminaux à portée, jeton par acquisition, TTL des marqueurs terminaux

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouse Redis/src/main/java/fr/maxlego08/zauctionhouse/redis/RedisAuctionClusterBridge.java`  
**Risque** : MEDIUM  
**Constats** : C-002, C-025

DIVERGENCE ASSUMÉE VIS-À-VIS DE L'AUDIT ET DU CATALOGUE, et c'est le point le plus important du chantier. L'audit veut rendre SOLD et REMOVED terminaux tout court ; le catalogue refuse REMOVED pour cause de mise à jour progressive. Les DEUX se trompent de dimension : rendre SOLD terminal tout court rend TOUT ITEM ACHETÉ DÉFINITIVEMENT IRRÉCLAMABLE par son acheteur, puisque RemoveService.removePurchasedItem verrouille l'item (storageType=PURCHASED) exactement comme un acheteur le ferait. La terminalité n'est pas une propriété de l'item, c'est une propriété du COUPLE (état, opération envisagée) : DELETED est terminal pour tout le monde ; SOLD et REMOVED sont terminaux pour le SEUL cycle de vente (portée LISTED) et restent verrouillables pour une réclamation. Ce découpage ferme le trou visé par l'audit ET supprime totalement le risque de mise à jour progressive relevé par le catalogue — les clés REMOVED écrites par l'ancienne version restent réclamables. Second correctif non identifié : verrouiller pour une réclamation écrase state=SOLD par LOCKED, et l'ancien UNLOCK_SCRIPT restaurerait AVAILABLE — soit un item vendu remis en vente. D'où le champ `prev`, mémorisé au verrouillage et restauré au déverrouillage.

```java
// --- LOCK_SCRIPT : nouvelle version (ARGV[3] = portee, memorisation de l'etat precedent) ---
    private static final String LOCK_SCRIPT = """
            local lockKey = KEYS[1]
            local itemKey = KEYS[2]
            local tokenValue = ARGV[1]
            local ttlMs = tonumber(ARGV[2])
            local listedScope = ARGV[3]

            local currentState = redis.call('HGET', itemKey, 'state')

            -- DELETED est terminal pour TOUS les usages : l'annonce n'existe plus nulle part.
            if currentState == 'DELETED' then
                return -1
            end

            -- SOLD et REMOVED sont terminaux pour le SEUL cycle de vente : l'annonce est sortie
            -- de la vente, mais l'acheteur (bucket PURCHASED) ou le vendeur (bucket EXPIRED)
            -- doit encore pouvoir la reclamer, ce qui passe par un verrou hors portee LISTED.
            if listedScope == '1' and (currentState == 'SOLD' or currentState == 'REMOVED') then
                return -1
            end

            -- Un etat LOCKED ne bloque que tant que la cle de verrou existe : si elle a expire,
            -- le detenteur precedent a disparu sans deverrouiller.
            if currentState == 'LOCKED' and redis.call('EXISTS', lockKey) == 1 then
                return 0
            end

            local acquired = redis.call('SET', lockKey, tokenValue, 'NX', 'PX', ttlMs)
            if not acquired then
                return 0
            end

            -- On memorise l'etat precedent pour pouvoir le RESTAURER au deverrouillage.
            -- Sans lui, verrouiller un item SOLD pour une reclamation puis le deverrouiller
            -- le repasserait a AVAILABLE : un item vendu redeviendrait achetable.
            local previous = currentState
            if previous == false or previous == 'LOCKED' then previous = 'AVAILABLE' end
            redis.call('HSET', itemKey, 'prev', previous)

            redis.call('HSET', itemKey, 'state', 'LOCKED')
            redis.call('HSET', itemKey, 'lock', tokenValue)
            return 1
            """;

// --- UNLOCK_SCRIPT : restauration de l'etat precedent ---
    private static final String UNLOCK_SCRIPT = """
            local lockKey = KEYS[1]
            local itemKey = KEYS[2]
            local tokenValue = ARGV[1]

            local currentToken = redis.call('HGET', itemKey, 'lock')
            if currentToken ~= tokenValue then
                return 0
            end

            redis.call('DEL', lockKey)
            redis.call('HDEL', itemKey, 'lock')

            local currentState = redis.call('HGET', itemKey, 'state')
            if currentState == 'LOCKED' then
                local previous = redis.call('HGET', itemKey, 'prev')
                if previous == false or previous == 'LOCKED' then previous = 'AVAILABLE' end
                redis.call('HSET', itemKey, 'state', previous)
            end
            redis.call('HDEL', itemKey, 'prev')
            return 1
            """;

// --- champs / constantes ---
    private static final String FIELD_PREV = "prev";
    /** TTL applique aux marqueurs TERMINAUX : doit survivre a tout fantome memoire. */
    private final Duration terminalStateTtl;

// --- constructeur : ajouter apres l'affectation de itemListedTtl ---
        // Un marqueur terminal ne doit JAMAIS expirer avant la duree de vie maximale d'une
        // annonce : sinon la cle disparait, l'etat redevient nul (donc « disponible ») et un
        // fantome memoire peut etre reverrouille puis vendu une seconde fois.
        this.terminalStateTtl = maxTtl(itemStateTtl, itemListedTtl);

    private static Duration maxTtl(Duration a, Duration b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

// --- helpers de portee et de terminalite ---
    private static String listedScopeArg(StorageType storageType) {
        return storageType == StorageType.LISTED ? "1" : "0";
    }

    /**
     * DELETED est terminal partout ; SOLD et REMOVED ne le sont que pour le cycle de vente.
     * Une portee nulle (affichage) applique la lecture permissive historique.
     */
    private static boolean isTerminalState(String state, StorageType scope) {
        if (STATE_DELETED.equals(state)) return true;
        if (scope != StorageType.LISTED) return false;
        return STATE_SOLD.equals(state) || STATE_REMOVED.equals(state);
    }

// --- checkAvailability : la variante a portee devient la vraie implementation ---
    @Override
    public CompletableFuture<Boolean> checkAvailability(Item item) {
        // Contrat historique conserve : predicat d'AFFICHAGE, non engageant.
        return checkAvailability(item, null);
    }

    @Override
    public CompletableFuture<Boolean> checkAvailability(Item item, StorageType storageType) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String state = jedis.hget(itemKey(item), FIELD_STATE);
                if (isTerminalState(state, storageType)) {
                    return false;
                }
                if (!STATE_LOCKED.equals(state)) {
                    return true;
                }
                return !jedis.exists(lockKey(item));
            }
        });
    }

// --- releaseLock : remonte enfin le code retour du script (jete aux l.257/267) ---
    @Override
    public CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType) {
        return releaseLock(item, lockToken, storageType).thenApply(released -> null);
    }

    @Override
    public CompletableFuture<Boolean> releaseLock(Item item, LockToken lockToken, StorageType storageType) {
        if (lockToken == null || !lockToken.isAcquired()) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        return CompletableFuture.supplyAsync(() -> {
            reloadScriptsIfNeeded();
            if (!scriptsLoaded) { /* cf. etape 9 */ return Boolean.FALSE; }

            try (Jedis jedis = jedisPool.getResource()) {
                var keys = Arrays.asList(lockKey(item), itemKey(item));
                var args = Collections.singletonList(lockToken.value());
                Object result;
                try {
                    result = jedis.evalsha(unlockScriptSha, keys, args);
                } catch (Exception e) {
                    if (!isNoScriptError(e)) throw e;
                    loadScripts();
                    if (!scriptsLoaded) return Boolean.FALSE;
                    result = jedis.evalsha(unlockScriptSha, keys, args);
                }
                boolean released = result instanceof Long value && value == 1L;
                if (!released) {
                    // Signal de VOL DE VERROU : le jeton inscrit dans Redis n'est plus le notre.
                    plugin.getLogger().severe("Lock for item " + item.getId()
                            + " was no longer held by this node at release time (token=" + lockToken.value() + ").");
                }
                return released;
            }
        });
    }

// --- removeItem : etats terminaux et TTL corrige ---
    @Override
    public CompletableFuture<Void> removeItem(Item item, StorageType sourceStorageType, StorageType destinationStorageType) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String itemKey = itemKey(item);

                // Une destination nulle designe la surcharge 2 arguments du retrait admin :
                // c'est une destruction definitive, pas un deplacement reclamable.
                boolean definitive = destinationStorageType == null || destinationStorageType == StorageType.DELETED;
                String state = definitive ? STATE_DELETED : STATE_REMOVED;

                jedis.hset(itemKey, FIELD_STATE, state);
                jedis.hdel(itemKey, FIELD_LOCK, FIELD_PREV);

                // Un marqueur terminal ne doit pas expirer avant les fantomes qu'il protege :
                // on n'applique PLUS le TTL de 24 h d'item-state-ttl-seconds.
                if (this.terminalStateTtl != null) {
                    jedis.expire(itemKey, this.terminalStateTtl.toSeconds());
                } else {
                    jedis.persist(itemKey);
                }

                this.plugin.sendMessage(new ItemRemovedMessage(String.valueOf(item.getId()), sourceStorageType, destinationStorageType));
            }
        });
    }

// --- notifyItemListed : purger aussi `prev` lors d'une remise en vente ---
                jedis.hset(key, FIELD_STATE, STATE_AVAILABLE);
                jedis.hdel(key, FIELD_LOCK, FIELD_PREV);
```

## 4.11 — Addon — bail renouvelable : lockLeaseDuration, renewLock, isHeldBy

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouse Redis/src/main/java/fr/maxlego08/zauctionhouse/redis/RedisAuctionClusterBridge.java + src/main/resources/config.yml`  
**Risque** : LOW  
**Constats** : C-007

Moitié addon du bail. isHeldBy est délibérément implémenté SANS Lua : avec un jeton unique (étape 1), un simple GET de la clé de verrou suivi d'une comparaison est exact et atomique — une seule commande, une surface NOSCRIPT en moins. renewLock, lui, exige l'atomicité check-puis-PEXPIRE, donc un script. La durée du bail EST lock-ttl-seconds : c'est ce qui supprime le couplage fragile « lock-lease-ms doit rester <= lock-ttl-seconds » que l'audit demandait de documenter dans quatre config.yml. La nouvelle clé lock-renew-enabled est l'interrupteur d'urgence : à false, lockLeaseDuration rend ZERO et le plugin n'arme aucun watchdog, donc aucune connexion Jedis supplémentaire n'est empruntée.

```java
// --- RENEW_SCRIPT (charge par loadScripts, cf. etape 8) ---
    private static final String RENEW_SCRIPT = """
            local lockKey = KEYS[1]
            local itemKey = KEYS[2]
            local tokenValue = ARGV[1]
            local ttlMs = tonumber(ARGV[2])

            if redis.call('GET', lockKey) ~= tokenValue then
                return 0
            end
            if redis.call('HGET', itemKey, 'lock') ~= tokenValue then
                return 0
            end

            redis.call('PEXPIRE', lockKey, ttlMs)
            return 1
            """;

    private volatile String renewScriptSha;
    private final boolean lockRenewEnabled;

// --- constructeur : nouveau parametre lockRenewEnabled, passe depuis ZAuctionHouseRedis:99 ---
//     getConfig().getBoolean("redis-config.lock-renew-enabled", true)

    @Override
    public Duration lockLeaseDuration() {
        // Duration.ZERO = « pas de bail » : le plugin principal n'armera aucun watchdog.
        return this.lockRenewEnabled && this.lockTtl != null ? this.lockTtl : Duration.ZERO;
    }

    @Override
    public CompletableFuture<Boolean> renewLock(Item item, LockToken lockToken, StorageType storageType) {
        if (!this.lockRenewEnabled || lockToken == null || !lockToken.isAcquired()) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        return CompletableFuture.supplyAsync(() -> {
            reloadScriptsIfNeeded();
            if (!scriptsLoaded) return Boolean.FALSE;

            long ttlMs = lockTtl != null ? lockTtl.toMillis() : 30_000L;
            try (Jedis jedis = jedisPool.getResource()) {
                var keys = Arrays.asList(lockKey(item), itemKey(item));
                var args = Arrays.asList(lockToken.value(), String.valueOf(ttlMs));
                Object result;
                try {
                    result = jedis.evalsha(renewScriptSha, keys, args);
                } catch (Exception e) {
                    if (!isNoScriptError(e)) throw e;
                    loadScripts();
                    if (!scriptsLoaded) return Boolean.FALSE;
                    result = jedis.evalsha(renewScriptSha, keys, args);
                }
                return result instanceof Long value && value == 1L;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> isHeldBy(Item item, LockToken lockToken, StorageType storageType) {
        if (lockToken == null || !lockToken.isAcquired()) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        // Pas de Lua ici : depuis que le jeton est unique par acquisition, un GET suivi d'une
        // comparaison est exact — et c'est une commande unique, donc atomique.
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                return lockToken.value().equals(jedis.get(lockKey(item)));
            }
        });
    }

// --- config.yml de l'addon : sous redis-config, apres lock-ttl-seconds ---
//   # Lock TTL in seconds for distributed locking.
//   # This is the LEASE duration: while a purchase or a removal is in progress, the lock is
//   # automatically renewed every lock-ttl-seconds/3 by the auction house. You no longer need
//   # to oversize this value to survive a slow economy plugin or a slow database.
//   lock-ttl-seconds: 30
//
//   # Automatically renew the lock lease while a critical section is running.
//   # Disable ONLY if lease renewal puts pressure on your Jedis pool: with it disabled, a
//   # transaction longer than lock-ttl-seconds is ABORTED before any money moves, instead of
//   # silently losing its lock to another server.
//   lock-renew-enabled: true
```

## 4.12 — Plugin — watchdog de bail et vérification de détention avant le débit

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/java/fr/maxlego08/zauctionhouse/services/AuctionService.java + PurchaseService.java`  
**Risque** : MEDIUM  
**Constats** : C-007

Le bail de 30 s n'est jamais prolongé et la section critique contient des appels SYNCHRONES BLOQUANTS qu'aucun orTimeout ne peut borner (getPlayerName JDBC à ZAuctionManager:784, withdraw à :802, deposit à :823, exécutés en ligne). Deux garde-fous complémentaires : le watchdog prolonge le bail toutes les lease/3, et isHeldBy vérifie la détention JUSTE AVANT le point d'engagement — à cet instant précis rien n'a encore été débité, l'abandon est donc gratuit. Je REJETTE les deux orTimeout proposés par l'audit : celui sur auctionEconomy.has est purement décoratif (ZAuctionEconomy.get rend un completedFuture, le blocage a déjà eu lieu quand le futur revient) et celui sur auctionManager.purchaseItem est ACTIVEMENT DANGEREUX — purchaseItem ne retourne que son updateFuture, à ce stade l'argent est débité et l'item remis, un timeout rapporterait donc un achat commis en échec et déclencherait en prime la restauration de statut. Trois garde-fous que l'audit oublie : Math.max(1000, lease/3) sous peine de période nulle si lease < 3 ms ; un plafond de renouvellements sous peine de verrou immortel si le thread de la section critique meurt ; et l'annulation du watchdog dans un whenComplete placé APRÈS le exceptionally, seul point traversé par tous les chemins.

```java
// ===== AuctionService.java — helper partage =====
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

    /** Plafond de renouvellements : au-dela, la section critique est consideree bloquee. */
    private static final int MAX_LEASE_RENEWALS = 30;

    /**
     * Arme un watchdog qui prolonge le bail du verrou distribue toutes les {@code bail / 3}
     * pendant toute la duree de la section critique.
     * <p>
     * Rend {@code null} — et n'ordonnance donc RIEN — quand le bridge n'expose aucun bail
     * ({@link AuctionClusterBridge#lockLeaseDuration()} == ZERO) : c'est le cas du bridge
     * mono-serveur et de tout addon compile contre une API anterieure. Le comportement est
     * alors strictement celui d'avant ce correctif.
     */
    protected WrappedTask startLeaseWatchdog(AuctionPlugin plugin, AuctionClusterBridge bridge, Item item, LockToken token, StorageType storageType) {
        if (token == null || !token.isAcquired()) return null;

        var lease = bridge.lockLeaseDuration();
        if (lease == null || lease.isZero() || lease.isNegative()) return null;

        var logger = plugin.getLogger();
        // Math.max : sans lui, un bail configure sous 3 ms produirait une periode de 0.
        long periodMs = Math.max(1000L, lease.toMillis() / 3);
        var renewals = new AtomicInteger();
        var taskHolder = new AtomicReference<WrappedTask>();

        var task = plugin.getScheduler().runTimerAsync(() -> {
            if (renewals.incrementAndGet() > MAX_LEASE_RENEWALS) {
                // Sans ce plafond, un thread de section critique mort laisserait le watchdog
                // prolonger le bail indefiniment : l'item serait verrouille pour toujours.
                logger.severe("Lease watchdog for item " + item.getId() + " exceeded " + MAX_LEASE_RENEWALS
                        + " renewals; the critical section looks stuck, stopping renewal.");
                stopLeaseWatchdog(taskHolder.get());
                return;
            }
            bridge.renewLock(item, token, storageType).whenComplete((renewed, error) -> {
                if (error != null) {
                    logger.warning("Failed to renew cluster lock for item " + item.getId() + ": " + error.getMessage());
                } else if (!Boolean.TRUE.equals(renewed)) {
                    logger.severe("Cluster lock lease LOST for item " + item.getId() + ": another node may hold it now.");
                }
            });
        }, periodMs, periodMs, TimeUnit.MILLISECONDS);

        taskHolder.set(task);
        return task;
    }

    protected void stopLeaseWatchdog(WrappedTask task) {
        if (task != null) task.cancel();
    }

// ===== PurchaseService.java =====
// --- holder a declarer avec les autres (apres la l.76) ---
        final AtomicReference<WrappedTask> watchdogHolder = new AtomicReference<>();

// --- remplacer la branche `if (hasMoney)` (l.133-143) ---
                    if (hasMoney) {

                        watchdogHolder.set(startLeaseWatchdog(this.plugin, clusterBridge, item, token, StorageType.LISTED));

                        return clusterBridge.isHeldBy(item, token, StorageType.LISTED)
                                .orTimeout(performanceConfig.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
                                .thenCompose(stillHeld -> {
                                    if (!Boolean.TRUE.equals(stillHeld)) {
                                        // Bail perdu AVANT tout debit : abandon gratuit, rien n'a bouge.
                                        resultHolder.set(PurchaseResult.failure("Lock lease lost", PurchaseFailReason.LOCK_FAILED));
                                        return this.<Void>failedFuture(new IllegalStateException("Lease expired before commit"));
                                    }
                                    // AUCUN orTimeout ici, JAMAIS : purchaseItem ne rend que son
                                    // updateFuture, l'argent est deja debite et l'item deja remis
                                    // quand il revient. Un timeout rapporterait un achat commis en
                                    // echec et declencherait la restauration de statut.
                                    return auctionManager.purchaseItem(player, item);
                                })
                                .thenCompose(v -> clusterBridge.notifyItemBought(player, item)
                                        .orTimeout(performanceConfig.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS))
                                .thenCompose(v -> unlockIssued.compareAndSet(false, true)
                                        ? clusterBridge.releaseLock(item, token, StorageType.LISTED)
                                                .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                                                .thenAccept(released -> warnIfLockLost(logger, item, released))
                                        : CompletableFuture.<Void>completedFuture(null))
                                .thenApply(v -> {
                                    resultHolder.set(PurchaseResult.success("Purchase successful", true));
                                    return resultHolder.get();
                                });
                    }

// --- APRES le .exceptionally terminal (l.189), en dernier maillon de la chaine ---
                }).whenComplete((result, throwable) -> stopLeaseWatchdog(watchdogHolder.getAndSet(null)));
```

## 4.13 — (Release suivante) Fusion du pré-contrôle et de l'acquisition : checkAndLock

**Fichier** : `D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/API/src/main/java/fr/maxlego08/zauctionhouse/api/cluster/AuctionClusterBridge.java + RedisAuctionClusterBridge.java + les 4 sites d'appel`  
**Risque** : MEDIUM  
**Constats** : C-106

À LIVRER EN DERNIER, dans une release séparée : figer une API autour du prédicat de disponibilité avant que les étapes 10 et 11 ne l'aient rendu juste serait une erreur. checkAvailability est un pré-contrôle TOCTOU strictement redondant avec LOCK_SCRIPT : deux emprunts Jedis et deux RTT là où un seul suffit, sur les quatre chemins chauds. Je m'écarte du catalogue sur deux points. D'abord, ce n'est PAS api-breaking : en réutilisant LockToken comme canal de retour (sentinelle unavailable() ajoutée à l'étape 1), aucun type public ni aucune signature existante ne bouge. Ensuite, le piège que la contre-expertise signale — l'implémentation par défaut cassée par le failedFuture de LocalAuctionClusterBridge — est déjà désamorcé par mon étape 4. La fusion RACCOURCIT la fenêtre TOCTOU sans la supprimer : la revalidation autoritaire en base sous verrou (chantier 5) reste indispensable, et retirer checkAvailability du chemin de retrait sans elle ne ferait que rendre le trou plus rapide à atteindre.

```java
// ===== API — AuctionClusterBridge =====
    /**
     * Verifie la disponibilite ET acquiert le verrou en UNE SEULE operation atomique.
     * <p>
     * Supprime la fenetre TOCTOU entre {@link #checkAvailability(Item, StorageType)} et
     * {@link #lockItem(Item, UUID, StorageType)}, ainsi qu'un aller-retour reseau et un emprunt
     * de connexion sur chacun des quatre chemins chauds.
     * <p>
     * Trois issues, distinguees par le jeton rendu :
     * <ul>
     *   <li>{@link LockToken#isAcquired()} : verrou obtenu ;</li>
     *   <li>{@link LockToken#isUnavailable()} : item dans un etat terminal pour cette portee,
     *       l'appelant doit remonter ITEM_NOT_AVAILABLE ;</li>
     *   <li>{@link LockToken#isNoop()} : contention, l'appelant doit remonter LOCK_FAILED.</li>
     * </ul>
     * L'implementation par defaut enchaine les deux appels historiques : elle est correcte pour
     * tout bridge respectant la convention documentee sur {@code lockItem}.
     */
    default CompletableFuture<LockToken> checkAndLock(Item item, UUID lockerId, StorageType storageType) {
        return checkAvailability(item, storageType).thenCompose(available -> available
                ? lockItem(item, lockerId, storageType)
                : CompletableFuture.completedFuture(LockToken.unavailable()));
    }

// ===== Addon — un seul evalsha, LOCK_SCRIPT rend deja -1 / 0 / 1 depuis l'etape 10 =====
    @Override
    public CompletableFuture<LockToken> checkAndLock(Item item, UUID lockerId, StorageType storageType) {
        return CompletableFuture.supplyAsync(() -> {
            reloadScriptsIfNeeded();
            if (!scriptsLoaded) {
                plugin.getLogger().severe("Refusing to lock item " + item.getId() + ": Lua scripts unavailable.");
                return LockToken.noop();
            }

            LockToken token = LockToken.issue(item, ZAuctionHouseRedis.INSTANCE_UUID);
            long ttlMs = lockTtl != null ? lockTtl.toMillis() : 30_000L;

            try (Jedis jedis = jedisPool.getResource()) {
                Object result = evalLock(jedis, item, token, ttlMs, storageType);
                if (!(result instanceof Long value)) return LockToken.noop();
                if (value == 1L) return token;
                // -1 = etat terminal (jamais acquerable) ; 0 = simple contention.
                return value == -1L ? LockToken.unavailable() : LockToken.noop();
            }
        });
    }

// ===== PurchaseService — remplace les l.82-104 (checkAvailability + lockItem + gardes) =====
        var lockFuture = clusterBridge.checkAndLock(item, player.getUniqueId(), StorageType.LISTED);
        lockFuture.whenComplete(/* ... compensation identique a l'etape 5 ... */);

        return lockFuture.copy()
                .orTimeout(performanceConfig.lockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                .thenCompose(token -> {
                    tokenHolder.set(token);

                    if (token.isUnavailable()) {
                        inventoryManager.updateInventory(player);
                        resultHolder.set(PurchaseResult.failure("Item not available", PurchaseFailReason.ITEM_NOT_AVAILABLE));
                        return failedFuture(new IllegalStateException("Item indisponible"));
                    }
                    if (token.isNoop()) {
                        inventoryManager.updateInventory(player);
                        resultHolder.set(PurchaseResult.failure("Lock failed", PurchaseFailReason.LOCK_FAILED));
                        return failedFuture(new IllegalStateException("Item deja en cours d'achat"));
                    }
                    /* ... suite inchangee : setStatus, notifyItemStatusChange, revalidation base ... */
                });

// Memes substitutions dans RemoveService (checkAvailabilityStep + acquireLockStep fusionnes),
// ExpireService.expireListedItemClustered et ZAuctionManager.adminRemoveItem.
// checkAvailability(Item) est CONSERVEE et documentee comme methode d'AFFICHAGE non engageante.
```

### Ruptures d'API et stratégie de compatibilité

- AUCUNE rupture. LockToken.of(Item) est CONSERVÉE avec la même signature et le même type de retour : seul son CORPS change (délègue à issue(item, null)). C'est délibéré et c'est le levier principal du chantier : l'addon Redis compilé contre le SHA figé deb8f16 déclare `compileOnly` et résout LockToken à l'exécution depuis le classloader du plugin principal (build.gradle.kts:87 `api(projects.api)` et shadowJar ne relocalise PAS fr.maxlego08.zauctionhouse.api.*), donc le binaire addon DÉJÀ DÉPLOYÉ est corrigé sans recompilation ni sortie coordonnée. Je rejette explicitement le point 3 de la remédiation de l'audit (« ne PAS conserver LockToken.of ») : le supprimer casserait binairement zauctionhousev4-api pour tout consommateur tiers pour un gain nul.
- LockToken : ajout des méthodes statiques `issue(Item, UUID)` et `unavailable()` et des méthodes d'instance `isNoop()`, `isUnavailable()`, `isAcquired()`. Additif pur sur un record publié : aucun composant ajouté, donc le constructeur canonique `LockToken(String)` et l'accesseur `value()` sont inchangés — pas de NoSuchMethodError possible.
- AuctionClusterBridge : ajout de CINQ méthodes `default` — `checkAvailability(Item, StorageType)`, `releaseLock(Item, LockToken, StorageType)`, `lockLeaseDuration()`, `renewLock(Item, LockToken, StorageType)`, `isHeldBy(Item, LockToken, StorageType)`. Toutes source- ET binaire-compatibles (précédent dans le même dépôt : `isDistributed()` est déjà un default, AuctionClusterBridge.java:104). Un bridge tiers non recompilé hérite de comportements strictement identiques à aujourd'hui : checkAvailability(item, st) délègue à checkAvailability(item), releaseLock délègue à unlockItem et rend true, lockLeaseDuration rend Duration.ZERO (donc AUCUN watchdog n'est armé), renewLock rend false, isHeldBy rend true.
- JE REJETTE le point 1 de la remédiation de l'audit pour C-061 (« unlockItem doit renvoyer CompletableFuture<Boolean> »). Le type de retour fait partie du descripteur JVM : l'addon compilé sur deb8f16 lèverait AbstractMethodError au PREMIER déverrouillage, c'est-à-dire un verrou jamais relâché sur CHAQUE achat — exactement le désastre que l'audit cherche à éviter. La méthode `default releaseLock` apporte le même verdict sans casser personne.
- Étape 12 (C-106) : ajout de `default CompletableFuture<LockToken> checkAndLock(Item, UUID, StorageType)`. Additif également — je REJETTE l'analyse du catalogue qui classe C-106 apiBreaking : en réutilisant LockToken comme canal de retour (sentinelle `unavailable()` en plus de `noop()`), aucun nouveau type public ni aucune signature existante n'est touché. Le coût réel est un cycle de release croisé (publier l'API, puis bumper le pin de l'addon), pas une rupture.
- PerformanceConfiguration (record publié) N'EST PAS TOUCHÉ. L'audit proposait d'y ajouter `performance.cluster-timeout.lock-lease-ms` ; ajouter un composant à un record change son constructeur canonique. J'ai préféré faire porter la durée du bail par le bridge lui-même (`lockLeaseDuration()`), ce qui supprime en prime le couplage fragile « lock-lease-ms doit rester <= lock-ttl-seconds » que l'audit demandait de documenter : la durée EST le TTL, par construction.

### Migrations de schéma

- AUCUNE. Ce chantier ne touche à aucune table. Le fence monotone persisté (colonne `fence` BIGINT sur %prefix%items + clause `WHERE id = ? AND fence < ?` sur les transitions terminales), qui est la moitié structurelle de C-025, est délibérément renvoyé au chantier 1 : il exige une migration Sarah ET la propagation du rowcount, et sans le rowcount la garde serait un no-op silencieux. Conséquence directe : ce chantier est intégralement réversible par simple downgrade de jar (voir rollback).

### Changements de configuration

- ADDON UNIQUEMENT (fichier non traduit, aucun coût multilingue) — D:/Users/Maxlego08/workspace2.0/zAuctionHouse Redis/src/main/resources/config.yml : nouvelle clé `redis-config.lock-renew-enabled: true` (étape 10). À false, `lockLeaseDuration()` rend Duration.ZERO et le plugin principal n'arme aucun watchdog : c'est l'interrupteur d'urgence si le renouvellement pèse sur le pool Jedis.
- ADDON — même fichier : commentaire de `lock-ttl-seconds` à réécrire. Il devient la durée du BAIL, renouvelée automatiquement toutes les ttl/3 pendant la section critique ; sa valeur n'a donc plus besoin d'être surdimensionnée « au cas où une transaction serait lente ».
- ADDON — même fichier : commentaires de `item-state-ttl-seconds` et `item-listed-ttl-seconds` à réécrire. Les états TERMINAUX (DELETED, REMOVED) utilisent désormais max(item-state-ttl, item-listed-ttl) et non plus les 24 h d'item-state : un marqueur terminal doit survivre à tout fantôme mémoire. Empreinte Redis : ~100 octets par item détruit conservés 30 j au lieu de 24 h, soit ~30 Mo pour 10 000 ventes/jour.
- PLUGIN PRINCIPAL — AUCUNE nouvelle clé. C'est un choix : l'audit proposait `performance.cluster-timeout.lock-lease-ms`, ce qui aurait imposé une réplication à l'identique dans SIX jeux de langue et non quatre — src/main/resources/config.yml (anglais, référence), fr/, es/, it/, id/ et th/. Le CLAUDE.md du projet annonce quatre langues : il est PÉRIMÉ, id/ et th/ existent bien sur disque (vérifié par `ls src/main/resources/`).
- PLUGIN PRINCIPAL — modification de commentaire OPTIONNELLE (documentation seulement) : ajouter sous `performance.cluster-timeout` (config.yml:990) une note indiquant que la durée du bail du verrou est désormais gérée par l'addon Redis (`lock-ttl-seconds`) et renouvelée automatiquement. Si elle est retenue, elle doit être répliquée à l'identique dans les SIX fichiers : src/main/resources/config.yml, fr/config.yml, es/config.yml, it/config.yml, id/config.yml, th/config.yml.
- DOCUMENTATION Docusaurus (C:/Users/Admin/Desktop/groupez/documentation) EN + FR : le contrat AuctionClusterBridge est exposé aux plugins tiers. Documenter les cinq nouvelles méthodes default, la convention « échec d'acquisition = LockToken.noop(), future en erreur = panne de transport », et le fait qu'un Redis dont EVAL/EVALSHA est indisponible rend désormais l'hôtel des ventes non transactionnel au lieu de dégrader silencieusement (étape 8).

### Validation

- IDENTITÉ DU JETON (C-008/C-025) — Réseau à 2 nœuds. Pendant un achat sur A : `redis-cli GET auction:lock:<id>` doit rendre une chaîne de la forme `item:<id>:<uuid-instance-A>:<uuid-aleatoire>`. Répéter sur B pour un autre item : les deux valeurs doivent différer sur les DEUX derniers segments. Test négatif à faire avant le correctif pour comparaison : la valeur vaut aujourd'hui exactement `item:<id>` sur les deux nœuds.
- VOL DE VERROU (C-008 + C-061) — `lock-ttl-seconds: 2`, `lock-renew-enabled: false`. Injecter une pause de 5 s dans ZAuctionManager.purchaseAuctionItem entre le withdraw (:802) et l'updateItem (:883) sur A (debugger ou Thread.sleep temporaire). Acheter l'item sur A, puis sur B pendant la pause. Attendu APRÈS correctif : quand A termine, sa libération remonte `false` et logue `Cluster lock for item <id> was NOT held by this server at release time`, et `redis-cli TTL auction:lock:<id>` montre que le verrou de B est TOUJOURS vivant. Attendu AVANT correctif : A détruit le verrou de B silencieusement. C'est le test discriminant du chantier.
- BAIL RENOUVELÉ (C-007) — `lock-ttl-seconds: 6`, `lock-renew-enabled: true`, même pause de 15 s injectée. Boucle d'observation : `while true; do redis-cli TTL auction:lock:<id>; sleep 1; done`. Attendu : le TTL remonte périodiquement à ~6 et ne descend jamais sous ~2 ; l'achat aboutit UNE fois. Puis rejouer avec `lock-renew-enabled: false` : l'achat doit ABORTER sur `Lease expired before commit` (PurchaseFailReason.LOCK_FAILED) et le solde de l'acheteur doit être STRICTEMENT INCHANGÉ (`/balance` avant/après) — c'est la preuve que l'abandon est antérieur au débit.
- ÉTAT TERMINAL SOLD, SCOPE LISTED (C-002) — `purchased-item.give-item: false`. Acheter l'item sur A. Sur B, avec un HDV resté ouvert (mettre `action.update-inventory-on-action: false` pour garantir le fantôme cliquable), cliquer sur l'item : l'achat doit être REFUSÉ. Vérifier `redis-cli HGET auction:item:<id> state` == `SOLD`.
- NON-RÉGRESSION DE LA RÉCLAMATION (le test qui invalide le correctif de l'audit) — Dans la continuité du test précédent, l'ACHETEUR ouvre son onglet « items achetés » sur A et réclame l'item : la réclamation DOIT réussir. Avec le correctif proposé par l'audit (SOLD terminal sans portée), elle échouerait et l'item serait définitivement perdu. Vérifier pendant la réclamation que `HGET auction:item:<id> prev` vaut `SOLD`, et après un abandon (fermeture de la GUI) que `state` est revenu à `SOLD` et non à `AVAILABLE`.
- MISE À JOUR PROGRESSIVE, CLÉS REMOVED HÉRITÉES — Simuler une clé écrite par l'ancien jar : `redis-cli HSET auction:item:<id> state REMOVED` puis `redis-cli EXPIRE auction:item:<id> 86400` sur un item réellement EXPIRED en base. Le vendeur doit pouvoir le réclamer normalement sur un nœud à jour. Ce test échouerait avec la version « REMOVED terminal tout court » que l'audit proposait.
- TTL DES MARQUEURS TERMINAUX (C-002) — Après un `/ah admin` de retrait définitif : `redis-cli TTL auction:item:<id>` doit rendre ~2592000 (ou -1 si les deux TTL sont désactivés), JAMAIS 86400.
- FAIL-CLOSED ET AUTO-GUÉRISON (C-099 + C-058) — Serveur en marche, `redis-cli SCRIPT FLUSH`. Acheter immédiatement : l'achat doit échouer avec un SEVERE `Refusing to lock item ... Lua scripts are unavailable`. Attendre 6 s et racheter : l'achat doit réussir SANS redémarrage, et `redis-cli SCRIPT EXISTS <lockScriptSha>` doit rendre 1. Vérifier dans la console qu'il n'y a eu au plus qu'une tentative de rechargement toutes les 5 s même sous 20 clics.
- FUITE DE VERROU SUR TIMEOUT (C-111) — `performance.cluster-timeout.lock-item-ms: 50` dans les 6 config.yml de test, et ralentir Redis depuis un client tiers (`redis-cli DEBUG SLEEP 1`). Cliquer sur un achat : la chaîne doit échouer par timeout, PUIS `redis-cli EXISTS auction:lock:<id>` doit rendre 0 dans la seconde qui suit et `HGET auction:item:<id> state` ne doit PAS valoir `LOCKED`. Test négatif préalable indispensable : sans le `copy()`, le verrou reste posé 30 s — c'est ce test qui prouve que la correction du `orTimeout` retournant `this` a bien été appliquée.
- VERROU ADMIN NON RELÂCHÉ (C-112) — Mono-serveur, sans Redis (LocalAuctionClusterBridge, donc aucun TTL). Provoquer une exception dans adminRemoveItem : supprimer à la main la ligne `%prefix%items` de l'item pendant que la GUI admin est ouverte, puis cliquer sur le retrait. Attendu : un SEVERE, puis une SECONDE tentative de retrait admin sur le même item doit atteindre la base (et échouer pour la même raison), et non pas être refusée par « Item deja verrouille ». Avant correctif, l'item reste verrouillé jusqu'au redémarrage.
- DOUBLE-CLIC ADMIN EN MONO-SERVEUR (garde noop de l'étape 3, prérequis de l'étape 4) — Sans Redis, double-cliquer très vite sur le retrait admin d'un item. Attendu : l'item est remis UNE SEULE FOIS dans l'inventaire de l'admin, et la console logue `Item <id> is already locked, admin removal aborted`. C'est le test qui prouve que l'étape 4 n'a pas introduit de duplication.
- INVARIANT SQL APRÈS CHARGE — Deux nœuds, 5 bots acheteurs chacun tirant au hasard dans les mêmes 200 annonces, 30 minutes. Puis : `SELECT item_id, COUNT(*) FROM zauctionhousev4_logs WHERE log_type = 'PURCHASE' GROUP BY item_id HAVING COUNT(*) > 1;` doit rendre ZÉRO ligne (substituer le préfixe réel de `database.table-prefix`, config.yml:144). Compter en parallèle les occurrences de `was NOT held by this server at release time` dans les logs : elles doivent être nulles ou très rares ; une fréquence élevée signale que lock-ttl-seconds est trop court pour la latence réelle de l'économie.
- COMPATIBILITÉ BINAIRE DE L'ADDON — Déployer le plugin principal issu des étapes 1 à 7 avec l'addon Redis NON recompilé (jar bâti sur le SHA deb8f16). Le serveur doit démarrer, les achats et retraits fonctionner à l'identique, `GET auction:lock:<id>` doit déjà montrer le jeton unique (preuve que l'étape 1 corrige le binaire déployé), et AUCUN watchdog ne doit être armé (aucune ligne de renouvellement dans les logs, `lockLeaseDuration()` valant ZERO par défaut).

### Questions ouvertes pour le mainteneur

- TERMINALITÉ À PORTÉE — Je fais de SOLD et REMOVED des états terminaux pour le SEUL scope LISTED, contre l'audit (terminaux tout court, ce qui rendrait tout item acheté irréclamable) et contre le catalogue (qui écarte REMOVED pour cause de mise à jour progressive). À valider : aucun plugin tiers ne verrouille un item acheté ou expiré en passant StorageType.LISTED. Le grep sur les deux dépôts est clean, mais l'interface AuctionClusterBridge est publiée.
- RETRAIT ADMIN DÉFINITIF — Avec l'étape 10, une destination nulle (surcharge 2 arguments, chemin du retrait admin) écrit `state=DELETED`, état terminal absolu. Un item EXPIRED ou PURCHASED retiré par un admin cesse donc d'être « rendu réclamable » ailleurs dans le cluster via ItemRemovedListener. C'est l'intention (l'admin a pris l'item), mais c'est un changement de comportement OBSERVABLE pour les réseaux qui s'appuyaient sur le ré-ajout. À annoncer dans le changelog ou à arbitrer.
- FAIL-CLOSED — L'étape 9 est la seule correction du chantier qui peut rendre l'hôtel des ventes inutilisable : sur un Redis managé ou derrière un proxy interdisant EVAL/SCRIPT, tous les achats et retraits échouent en LOCK_FAILED là où ils passaient jusqu'ici en mode dégradé. Accepter, ou prévoir une clé d'échappement explicite (`redis-config.allow-non-atomic-fallback: false` par défaut) que l'admin assumerait ?
- MESSAGE JOUEUR — Le joueur voit « item déjà en cours de traitement » (LOCK_FAILED) pour un problème d'infrastructure. Un message dédié impacterait messages.yml dans SIX jeux de langue (racine + fr/, es/, it/, id/, th/) et l'enum Message publiée. J'ai retenu le SEVERE console seul. Valider.
- FENCE MONOTONE — La moitié structurelle de C-025 (HINCRBY côté Redis, colonne `fence` BIGINT et clause `WHERE id = ? AND fence < ?` côté base) est renvoyée au chantier 1 : sans la propagation du rowcount, la garde SQL serait un no-op silencieux et le fence Redis un coût sans bénéfice. Le mainteneur veut-il les livrer dans la même release, ou assume-t-il que le verrou reste, jusqu'au chantier 1, une optimisation de contention et non une preuve ?
- RÉTENTION REDIS — Les marqueurs terminaux passent de 24 h à max(item-state-ttl, item-listed-ttl) = 30 jours par défaut : environ 100 octets par item détruit, soit ~30 Mo pour 10 000 ventes par jour. Acceptable, ou faut-il une clé dédiée `terminal-state-ttl-seconds` ?
- EMPLACEMENT DE LA CLÉ DE BAIL — L'audit voulait `performance.cluster-timeout.lock-lease-ms` dans le config.yml du plugin (donc SIX réplications i18n, plus le couplage à documenter « lease <= lock-ttl-seconds »). J'ai mis `lock-renew-enabled` dans le config.yml de l'addon et fait porter la durée par `lockLeaseDuration()`. Confirmer ce choix, qui interdit à un admin de régler la période de renouvellement autrement qu'en changeant lock-ttl-seconds.
- EXPIRESERVICE — expireListedItemClustered (l.272-275) a exactement la même fuite de verrou que C-111 : tokenHolder est renseigné APRÈS le orTimeout. Le catalogue la rattache à C-070 point 3 (chantier 7). La correction est identique à mon étape 6 (lockFuture.copy()) et tient en 6 lignes : la rapatrier ici, ou laisser le chantier 7 la faire ?
- ÉTAPE 13 (C-106) — Elle exige deux releases dans l'ordre : publier l'API du plugin sur repo.groupez.dev, puis bumper le pin `zauctionhousev4-api` de l'addon. Gain mesuré : environ un emprunt Jedis et un RTT sur quatre chemins chauds (~17 % des emprunts, pas 33 % comme l'annonce l'audit). La faire maintenant ou la reporter à une version majeure ?
- CLAUDE.md À CORRIGER — Le CLAUDE.md du projet annonce 4 jeux de langue ; il y en a SIX sur disque (racine, fr/, es/, it/, id/, th/). Trois grappes de vérification l'ont relevé indépendamment. À corriger avant le prochain chantier qui touchera une clé de configuration.

### Retour arrière

"Ce chantier n'introduit AUCUNE migration de schéma : le retour arrière est un simple downgrade de jars, ce qui est le principal argument pour l'avoir gardé hors de la colonne `fence` (renvoyée au chantier 1). Procédure par ordre de coût croissant. (1) DÉSARMER SANS REDÉPLOYER : passer `redis-config.lock-renew-enabled: false` dans le config.yml de l'addon et redémarrer un nœud à la fois — `lockLeaseDuration()` rend alors Duration.ZERO, aucun watchdog n'est armé, `isHeldBy` reste vrai par construction côté Redis mais le comportement redevient celui d'avant l'étape 12. C'est le premier geste si le pool Jedis souffre. (2) REVENIR À L'ANCIEN ADDON SEUL : redéployer le jar addon précédent sur tous les nœuds. Le plugin principal continue de fonctionner : ses appels à `releaseLock`, `checkAvailability(item, st)`, `renewLock` et `isHeldBy` retombent sur les implémentations `default` (délégation, permissif, false, true) et le comportement redevient exactement celui d'aujourd'hui. Les jetons restent uniques, puisque LockToken vit dans le plugin principal. (3) REVENIR AUX DEUX ANCIENS JARS : ordre imposé — descendre l'addon d'abord, le plugin principal ensuite (l'addon dépend de l'API du plugin, pas l'inverse). État Redis laissé derrière et sa tolérance, vérifiée : le champ `prev` est simplement ignoré par les anciens scripts ; les clés `state=DELETED` portant un TTL de 30 jours au lieu de 24 h sont interprétées correctement (DELETED bloque déjà tout, dans l'ancienne comme dans la nouvelle version) ; les verrous posés par un nœud nouveau et non relâchés expirent d'eux-mêmes en lock-ttl-seconds, l'ancien code recalculant `item:<id>` ne saura pas les déverrouiller mais la branche LOCKED-périmé de l'ancien LOCK_SCRIPT les récupère automatiquement. Nettoyage OPTIONNEL du champ résiduel : `redis-cli --scan --pattern 'auction:item:*' | xargs -n 100 -I{} redis-cli hdel {} prev`. Les anciens SHA de scripts Lua cohabitent sans conflit dans le cache de Redis, chaque nœud utilisant le sien. (4) SI LE FAIL-CLOSED DE L'ÉTAPE 9 BLOQUE UN REDIS MANAGÉ interdisant EVAL/SCRIPT, il n'y a PAS de contournement par configuration — c'est délibéré, le repli non atomique est un vecteur de duplication. Le retour arrière est le point (2), et la vraie sortie est de changer d'offre Redis. ROLLING UPGRADE : le seul point de vigilance est la fenêtre où des nœuds anciens et nouveaux coexistent — un ancien nœud qui déverrouille un verrou de réclamation remettra `state=AVAILABLE` au lieu de restaurer `SOLD`, ce qui est exactement son comportement actuel. Mettre à jour tous les nœuds dans la même fenêtre de maintenance et n'annoncer le correctif qu'ensuite."


---

# Chantier 5 — Revalidation autoritaire sous verrou sur le chemin de retrait (RemoveService + adminRemoveItem)

Le chemin de retrait est le seul chemin mutant du plugin qui rende physiquement un item au joueur sans jamais vérifier la vérité base : ses quatre gardes (RemoveService.java:55, 96, 134, 172) ne testent que `item.getStatus()`, un champ purement mémoire absent de la table `%prefix%items`, et `adminRemoveItem` (ZAuctionManager.java:679-709) ignore en plus le jeton `noop`, diffuse la suppression AVANT d'écrire en base et jette le future d'écriture. Ce chantier porte les trois remparts manquants, du moins cher au plus cher : une garde d'identité O(1) qui refuse toute référence `Item` qui n'est plus celle détenue par le store, un marquage terminal de l'instance partagée au moment où elle sort du store (ce qui neutralise les `setClick` déjà armés dans les GUI ouverts, y compris ceux déclenchés par l'addon Redis déjà déployé, sans le recompiler), et une relecture autoritaire d'une seule requête `ItemRepository.select(id)` intercalée sous verrou entre `changeStatusAndNotifyStep` et `executeLocalRemovalStep`, active uniquement en mode distribué. `adminRemoveItem` est intégralement réécrit sur le patron de `PurchaseService` : test du noop, relecture sous verrou, écriture en base d'abord, diffusion d'un état TERMINAL ensuite, livraison confinée au thread de l'entité, verrou relâché dans un `whenComplete`. Le lot est indivisible parce que les trois remparts partagent le même objet de contexte et les mêmes chemins de rollback : livrer la revalidation sans le drapeau `staleDetected` transformerait chaque détection de fantôme en remise en vente sur tout le cluster via `restoreStatusOnError` → `ItemStatusListener`, et livrer la garde d'identité sans le marquage terminal laisserait `ConfirmHelper` rediffuser AVAILABLE derrière.

**Prérequis**

- Aucun prérequis bloquant : les 7 étapes se compilent et se déploient telles quelles sur HEAD (49571d9 / d951be2), sans changement de signature publiée, sans migration et sans recompilation de l'addon Redis.
- Chantier 2 (C-014 giveItem chaînable + hop runAtEntity, C-039 localRemovalCompleted, C-005 inversion commit→remise) — RECOMMANDÉ AVANT l'étape 6. L'étape 6 introduit délibérément un `runAtEntity` local autour de `giveItem` pour être sûre isolément ; quand le chantier 2 rend `giveItem` chaînable, ce hop local doit être remplacé par `.thenCompose(v -> giveItem(...))`. Les étapes 1 à 5 n'ont aucune interaction avec le chantier 2.
- Chantier 1 (C-026 compare-and-set `where storage_type = <source>` sur TOUTES les cibles + propagation du rowcount par `updateReturning`) — COMPLÉMENT, pas prérequis. Ce chantier ferme la fenêtre de LECTURE ; le chantier 1 ferme celle d'ÉCRITURE. J'ai volontairement exclu de mon périmètre le point 3 du correctif d'audit de C-060 (gardes SQL dans `ItemRepository.createUpdateSchema`) : sans le rowcount il serait un no-op silencieux, et il réécrit la méthode dont le chantier 1 change la signature.
- Chantier 4 (C-008 jeton unique par acquisition) — COMPLÉMENT. Tant que `LockToken.of(item)` rend `"item:" + id`, identique sur tous les nœuds, le verrou distribué reste volable ; mes patchs ne s'appuient jamais sur la valeur du jeton autrement que par comparaison à `LockToken.noop()`, ils sont donc corrects avec l'un comme avec l'autre.
- Quick-win section 4 de l'audit `ZStorageManager.java:177/:215 -> supplyAsync(..., getExecutorService())` (chantier 8 / C-089) — non requis ici : ma relecture autoritaire n'emprunte PAS `selectItem`, elle passe par `selectItemRow` qui pose déjà explicitement `plugin.getExecutorService()`.

**Constats couverts** : C-009, C-012, C-042, C-046, C-060, C-083

*~195 LOC*

## 5.1 — ZAuctionManager : accesseur O(1) getItem + marquage terminal de l'instance sortie du store par identifiant

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : LOW  
**Constats** : C-046

Moitié « objet partagé » de C-046. Les boutons déjà rendus (SellingItemsButton:43, ExpiredItemsButton:39, PurchasedItemsButton:39, CombinedItemsButton:63-65, AdminSellingItemsButton:47 et le cache ITEM_SHOW de ConfirmHelper) capturent dans leur `setClick` la référence Item détenue par `storageItemsById`. Quand un autre serveur vend ou retire l'item, les listeners de l'addon appellent `removeItem(StorageType, int)` : l'instance sort du store mais conserve son statut AVAILABLE/REMOVED/PURCHASED et franchit donc toutes les gardes de RemoveService. Je REJETTE le correctif addon proposé par l'audit (`getItems(st).stream().filter(...)` dans les deux listeners) pour deux raisons : (a) il coûte trois copies complètes du store et trois scans linéaires par message reçu, sur le thread principal — inacceptable à 50 000 annonces ; (b) il exige de recompiler et redéployer l'addon. Le marquage placé ici est O(1) et, comme l'addon appelle cette méthode publiée via le classloader du plugin, il corrige l'addon DÉJÀ DÉPLOYÉ (SHA figé deb8f16) sans le toucher. Point crucial du découpage : le marquage doit vivre dans la surcharge `int` UNIQUEMENT, pas dans la surcharge `Item`. La surcharge `Item` est utilisée par `removeListedItem:513` (setStatus(REMOVED) puis removeItem puis addItem(EXPIRED)), par `performListedToExpired` et par `purchaseAuctionItem:875` (setStatus(PURCHASED) puis removeItem puis addItem(PURCHASED)) : y écraser le statut par DELETED rendrait l'item définitivement inréclamable. D'où la dé-délégation de la ligne 249. Les seuls appelants de la surcharge `int` sont ExpireService:286 (purge d'un fantôme vendu ailleurs), ItemBoughtListener:49 et ItemRemovedListener:39 de l'addon — trois cas où la sortie du store est bel et bien terminale.

```java
// ---- 1a. Remplacer le bloc removeItem (lignes 247-266) -------------------------

    @Override
    public void removeItem(StorageType storageType, Item item) {
        // ATTENTION : ne PAS déléguer à removeItem(StorageType, int). Cette surcharge est
        // utilisée par les transitions internes (removeListedItem:513 LISTED -> EXPIRED,
        // performListedToExpired, purchaseAuctionItem:875 LISTED -> PURCHASED) qui
        // RÉ-AJOUTENT ensuite la même instance dans un autre conteneur : lui imposer le
        // statut DELETED la rendrait définitivement inréclamable.
        var storage = this.storageItemsById.get(storageType);
        if (storage == null) return;

        Item removed = storage.remove(item.getId());
        if (removed == null) return;

        this.deindexItem(storageType, removed);
        invalidateListedCaches(storageType);
    }

    @Override
    public void removeItem(StorageType storageType, int itemId) {
        var storage = this.storageItemsById.get(storageType);
        if (storage == null) return;

        Item removed = storage.remove(itemId);
        if (removed == null) return;

        // C-046 : sortie TERMINALE par identifiant (purge d'un fantôme par ExpireService, ou
        // convergence demandée par ItemBoughtListener / ItemRemovedListener de l'addon Redis).
        // L'instance qu'on sort du store reste référencée par les setClick des boutons déjà
        // rendus et par le cache ITEM_SHOW : la marquer terminale la rend inoffensive, elle
        // ne franchit plus les gardes de statut de RemoveService (l.55, 96, 134, 172) et
        // ConfirmHelper.onInventoryClose:41 cesse de rediffuser AVAILABLE derrière.
        removed.setStatus(ItemStatus.DELETED);

        this.deindexItem(storageType, removed);
        invalidateListedCaches(storageType);
    }

    private void invalidateListedCaches(StorageType storageType) {
        if (storageType != StorageType.LISTED) return;
        this.plugin.getCategoryManager().invalidateCategoryCountCache();
        this.sortedItemsCache.invalidate();
    }

    /**
     * Accès O(1) à l'instance détenue par un conteneur, sans copie du store.
     * <p>
     * Volontairement ABSENTE de l'interface publiée {@code AuctionManager} : l'addon Redis est
     * compilé contre un SHA figé de l'API (deb8f16), y ajouter une méthode abstraite casserait
     * sa compilation et imposerait une release coordonnée. Le chantier PERF (C-050 / C-055)
     * remontera cet accesseur dans l'API ; il suffira alors d'ajouter {@code @Override} ici.
     *
     * @param storageType conteneur à interroger
     * @param itemId      identifiant de l'item
     * @return l'instance partagée détenue par le conteneur, ou {@code null}
     */
    public Item getItem(StorageType storageType, int itemId) {
        var storage = this.storageItemsById.get(storageType);
        return storage == null ? null : storage.get(itemId);
    }
```

## 5.2 — RemoveService : garde d'identité en tête de executeRemoval

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/RemoveService.java`  
**Risque** : LOW  
**Constats** : C-046

Moitié « garde » de C-046, et le seul rempart qui couvre AUSSI le mono-serveur (rechargement à chaud par `/ah admin migrate`, C-044) et les chemins pour lesquels aucune revalidation base n'est faite. Coût : une lecture de ConcurrentHashMap. Cette garde est indépendante du chemin d'appel : elle vaut pour les quatre entrées publiques, pour les trois boutons « tout récupérer » et pour ConfirmRemoveListedButton qui ressort l'item du cache ITEM_SHOW. Vérifié un par un : les huit appelants (ConfirmRemoveListedButton:23, CombinedItemsButton:63-65, ExpiredItemsButton:39, ListedItemsButton:137, PurchasedItemsButton:39, SellingItemsButton:43) obtiennent tous leur `Item` de `getExpiredItems` / `getPlayerSellingItems` / `getPurchasedItems` / `resolveItems`, qui rendent les références de `storageItemsById` — l'identité est donc vraie sur le chemin nominal et fausse exactement dans les cas fantômes. À noter : la garde et l'étape 1 sont complémentaires et non redondantes — l'étape 1 marque l'objet quand l'addon nous prévient, l'étape 2 refuse quand personne ne nous a prévenus mais que le store a changé.

```java
// ---- Remplacer executeRemoval (lignes 193-201) ---------------------------------

    private CompletableFuture<RemoveResult> executeRemoval(ItemStatus targetStatus, Player player, Item item, Runnable onUnavailable, Supplier<CompletableFuture<Void>> onLocalRemoval, StorageType storageType, StorageType destinationStorageType) {

        // C-046 : garde d'identité. Un bouton déjà rendu capture dans son setClick la référence
        // Item détenue par le store AU MOMENT DU RENDU, et rien ne redessine ce bouton quand un
        // autre serveur vend/retire l'item (updateListedItems ne cible que ListedItemsButton, et
        // clearPlayersCache ne fait que vider une clé). Si l'instance n'est plus celle du store,
        // c'est un fantôme : son statut mémoire ne prouve plus rien et lui rendre l'item
        // dupliquerait la marchandise. Coût : une lecture O(1).
        if (this.manager.getItem(storageType, item.getId()) != item) {
            this.plugin.getLogger().info("Stale item reference for item " + item.getId() + " in " + storageType + ", removal refused");
            onUnavailable.run();
            return CompletableFuture.completedFuture(RemoveResult.failure("Stale item reference", RemoveFailReason.ITEM_NOT_AVAILABLE));
        }

        var context = new RemovalContext(item, targetStatus, storageType, destinationStorageType, onUnavailable, onLocalRemoval);
        var performanceConfig = this.plugin.getConfiguration().getPerformance();
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var logger = this.plugin.getLogger();

        return checkAvailabilityStep(context, clusterBridge, performanceConfig)
                .thenCompose(available -> acquireLockStep(context, available, player, clusterBridge, performanceConfig))
                .thenCompose(token -> changeStatusAndNotifyStep(context, token, clusterBridge, performanceConfig))
                .thenCompose(v -> executeLocalRemovalStep(context, clusterBridge, performanceConfig))
                .thenCompose(v -> unlockAndCompleteStep(context, clusterBridge, performanceConfig))
                .exceptionally(throwable -> handleRemovalException(context, throwable, clusterBridge, logger));
    }
```

## 5.3 — Relecture autoritaire sous verrou : selectItemRow (1 requête) + revalidateUnderLockStep + drapeau staleDetected

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/RemoveService.java`  
**Risque** : MEDIUM  
**Constats** : C-009

Cœur du chantier. Trois écarts délibérés au correctif de l'audit. (1) Je n'utilise PAS `StorageManager.selectItem(int)` : il coûte 3 requêtes (ZStorageManager:176-211), il part sur `ForkJoinPool.commonPool` faute d'executor explicite, et il rend `null` quand l'économie de l'item a disparu de economies.yml (l.187-190) — sur un serveur ayant renommé une économie, TOUS les retraits de ces items deviendraient impossibles ET leurs entrées seraient purgées de la mémoire. `ItemRepository.select(id)` est une requête unique sur la clé primaire, rend un `Optional<ItemDTO>` qui porte `storage_type()`, `buyer_unique_id()` et `expired_at()` — tout ce dont la garde a besoin — et je la poste explicitement sur `plugin.getExecutorService()`. (2) J'ajoute `staleDetected` : appliqué tel quel, le correctif de l'audit transforme le constat en régression, car `handleRemovalException` → `restoreStatusOnError` republierait `IS_BEING_REMOVED → AVAILABLE`, et `ItemStatusListener.java:60-62` réagit à AVAILABLE par `updateListedItems(item, true, null)` — on remettrait EN VENTE sur tout le cluster l'item qu'on vient de constater vendu. Sur le chemin stale on restaure donc le statut LOCALEMENT (pour ne pas figer l'objet en IS_BEING_REMOVED) mais on ne diffuse RIEN. (3) Je distingue « ligne terminale » de « simple décalage de bucket » : purger le fantôme sur un simple décalage casserait le cas légitime où `ExpireService.processExpiredItems` a déjà déplacé l'item en mémoire (LISTED → EXPIRED) mais dont l'`updateItems` asynchrone n'a pas encore atterri — le joueur perdrait l'entrée de son onglet « expirés » jusqu'au redémarrage. On refuse (fail-closed, il réessaie une seconde plus tard) sans purger. J'ARBITRE CONTRE l'audit sur un dernier point : il recommande de désactiver l'étape dans les retraits de masse. Je la conserve, parce que les boutons « tout récupérer » sont précisément le vecteur le plus cliqué du constat ; le coût réel est +1 SELECT par clé primaire et par item, sérialisé sur l'executor de la base, soit de l'ordre de 0,5 s pour 100 items — un compromis très inférieur à une duplication. Le garde-fou de coût est ailleurs : l'étape est entièrement court-circuitée en mono-serveur (`isDistributed()` faux par défaut, AuctionClusterBridge:104), donc 99 % des installations ne paient rien.

```java
// ---- 3a. ZAuctionManager.java : ajouter après getItem(...) (étape 1) -----------
// Imports à ajouter en tête de ZAuctionManager.java :
//   import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
//   import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;

    /**
     * Relecture autoritaire d'une ligne {@code %prefix%items}, destinée aux revalidations
     * SOUS VERROU (retrait joueur et retrait admin).
     * <p>
     * Une seule requête sur la clé primaire, contrairement à {@code StorageManager.selectItem(int)}
     * qui en fait trois et rend {@code null} quand l'économie de l'item a disparu de
     * economies.yml. Postée explicitement sur l'executor de la base et non sur le
     * ForkJoinPool commun, déjà saturé par les appels Jedis bloquants du bridge.
     * <p>
     * {@code ItemRepository.select(int)} filtre déjà {@code storage_type = DELETED} : un
     * {@link Optional} vide signifie donc « ligne détruite ».
     *
     * @param itemId identifiant de l'item
     * @return la ligne base, ou un Optional vide si elle est détruite ou absente
     */
    public CompletableFuture<Optional<ItemDTO>> selectItemRow(int itemId) {
        var storageManager = this.plugin.getStorageManager();
        return CompletableFuture.supplyAsync(() -> storageManager.with(ItemRepository.class).select(itemId), this.plugin.getExecutorService());
    }


// ---- 3b. RemoveService.java : intercaler l'étape dans la chaîne ----------------
// Dans executeRemoval (issu de l'étape 2), insérer revalidateUnderLockStep :

        return checkAvailabilityStep(context, clusterBridge, performanceConfig)
                .thenCompose(available -> acquireLockStep(context, available, player, clusterBridge, performanceConfig))
                .thenCompose(token -> changeStatusAndNotifyStep(context, token, clusterBridge, performanceConfig))
                .thenCompose(v -> revalidateUnderLockStep(context, clusterBridge, performanceConfig))
                .thenCompose(v -> executeLocalRemovalStep(context, clusterBridge, performanceConfig))
                .thenCompose(v -> unlockAndCompleteStep(context, clusterBridge, performanceConfig))
                .exceptionally(throwable -> handleRemovalException(context, throwable, clusterBridge, logger));


// ---- 3c. RemoveService.java : nouvelle étape, à placer entre -------------------
//         changeStatusAndNotifyStep et executeLocalRemovalStep

    /**
     * Étape 3 bis : relecture autoritaire de la base SOUS VERROU.
     * <p>
     * C'est la protection que {@code PurchaseService.java:118-125} et
     * {@code ExpireService.java:280-289} appliquent déjà et que le chemin de retrait n'avait
     * pas : les quatre gardes de ce service ne portent que sur {@code item.getStatus()}, un
     * champ mémoire sans colonne dans {@code %prefix%items}, donc invisible des autres serveurs.
     * {@code checkAvailability} et {@code LOCK_SCRIPT} laissent passer les états Redis SOLD et
     * REMOVED : sans cette étape, toute référence périmée qui franchit la garde de statut
     * aboutit à {@code giveItem} + {@code updateItem(DELETED)} qui écrase la ligne PURCHASED
     * d'un autre serveur.
     */
    private CompletableFuture<Void> revalidateUnderLockStep(RemovalContext context, AuctionClusterBridge clusterBridge, PerformanceConfiguration config) {

        // Mono-serveur : la mémoire EST la vérité et la garde d'identité en tête de
        // executeRemoval suffit. On ne facture pas un aller-retour SQL par retrait — ni x N
        // sur les boutons « tout récupérer » — aux installations sans addon.
        if (!clusterBridge.isDistributed()) {
            return CompletableFuture.completedFuture(null);
        }

        return this.manager.selectItemRow(context.item.getId())
                .orTimeout(config.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
                .thenCompose(optional -> {

                    // « gone » = la ligne est TERMINALE : détruite (select(int) filtre DELETED)
                    // ou déjà vendue (acheteur posé). Le fantôme local doit alors disparaître.
                    boolean gone = optional.isEmpty()
                            || (context.storageType == StorageType.LISTED && optional.get().buyer_unique_id() != null);

                    // « stale » ajoute le simple décalage de conteneur : la ligne existe encore
                    // mais plus dans le bucket qu'on croit détenir.
                    boolean stale = gone || optional.get().storage_type() != context.storageType;

                    if (stale) {
                        // Interdit à restoreStatusOnError de rediffuser AVAILABLE : cela
                        // remettrait EN VENTE sur tout le cluster (ItemStatusListener:60-62 ->
                        // updateListedItems(item, true, null)) l'item qu'on constate vendu.
                        context.staleDetected = true;
                        context.result = RemoveResult.failure("Item already processed on another server", RemoveFailReason.ITEM_NOT_AVAILABLE);

                        if (gone) {
                            // On ne purge QUE sur un état terminal. Un décalage de bucket peut
                            // n'être qu'un UPDATE local encore en vol (ExpireService déplace en
                            // mémoire puis écrit en asynchrone) : purger là ferait disparaître
                            // une entrée légitime de l'onglet du joueur jusqu'au redémarrage.
                            this.plugin.getScheduler().runNextTick(w -> {
                                this.manager.removeItem(context.storageType, context.item.getId());
                                this.manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SEARCH);
                            });
                        }

                        context.onUnavailable.run();
                        return this.<Void>failedFuture(new IllegalStateException("Stale item " + context.item.getId()
                                + " (memory=" + context.storageType + ", db=" + (optional.isEmpty() ? "gone" : optional.get().storage_type()) + ")"));
                    }

                    // Resynchronisation de la date d'expiration sur la vérité base : l'objet
                    // mémoire peut porter un expiredAt divergent après un aller-retour cluster.
                    context.item.setExpiredAt(optional.get().expired_at());
                    return CompletableFuture.<Void>completedFuture(null);
                });
    }


// ---- 3d. RemoveService.java : remplacer restoreStatusOnError (lignes 302-312) ---

    /**
     * Restores the item status on error if it was changed.
     * If the local removal already completed (item given to player, DB updated),
     * we must NOT restore the status as it would create a ghost item on other servers.
     */
    private void restoreStatusOnError(RemovalContext context, AuctionClusterBridge clusterBridge) {
        if (!context.statusChanged || context.localRemovalCompleted) return;

        // Restauration LOCALE systématique : sans elle, un objet non purgé resterait figé en
        // IS_BEING_REMOVED et deviendrait invendable ET inréclamable.
        context.item.setStatus(context.oldStatus);

        // C-009 : mais quand la relecture autoritaire a montré que l'item ne nous appartient
        // plus, on ne DIFFUSE rien. Republier AVAILABLE le remettrait en vente sur tous les
        // nœuds via ItemStatusListener:60-62.
        if (context.staleDetected) return;

        clusterBridge.notifyItemStatusChange(context.item, context.targetStatus, context.oldStatus);
    }


// ---- 3e. RemoveService.java : nouveau champ dans RemovalContext (après l.329) ---

        LockToken token;
        boolean statusChanged;
        boolean localRemovalCompleted;
        boolean staleDetected;
        RemoveResult result;
```

## 5.4 — Message.ADMIN_ITEM_NOT_AVAILABLE + réplication dans les 6 messages.yml

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/messages/Message.java`  
**Risque** : LOW  
**Constats** : C-012, C-042, C-060

Prérequis de l'étape 5 : sans retour visuel, un admin qui clique sur un item verrouillé ou déjà traité ailleurs ne verrait plus rien du tout (aujourd'hui il voit ADMIN_ITEM_REMOVED à tort). Ajout purement ADDITIF à un enum publié : aucun `switch` exhaustif sur `Message` n'existe dans les deux dépôts, l'addon Redis reste binairement compatible. Attention : le CLAUDE.md du projet annonce 4 langues, le disque en porte SIX (racine anglaise + es/ fr/ id/ it/ th/). Une clé manquante ne fait pas planter le plugin (MessageLoader retombe sur la valeur par défaut de l'enum) mais journalise un SEVERE au démarrage pour CHAQUE message du fichier, ce qui est très bruyant : les 6 fichiers doivent être servis dans le même commit. Une clé inconnue est en revanche ignorée silencieusement (MessageLoader:79 `if (message == null) continue;`), ce qui rend le downgrade de jar sans effet de bord.

```java
// ---- 4a. API/.../api/messages/Message.java : après ADMIN_ITEM_REMOVED (l.67) ---

    ADMIN_ITEM_REMOVED("<success>You removed <white>%items%<success> from <white>%target%<success>."),
    ADMIN_ITEM_NOT_AVAILABLE("<error>The item <white>%items%<error> is no longer available: it was sold, claimed or removed on another server."),
    ADMIN_ITEM_ADDED("<success>You added <white>%items%<success> to <white>%target%<success> in <white>%type%<success>."),


// ---- 4b. src/main/resources/messages.yml : après admin-item-removed (l.187) ----

# Shown when an admin action is refused because the item is no longer available
# (locked by another server, sold, claimed or removed elsewhere).
# Available placeholders:
#   %items%  - Item description
admin-item-not-available: "<error>The item <white>%items%<error> is no longer available: it was sold, claimed or removed on another server."


// ---- 4c. src/main/resources/fr/messages.yml : après la ligne 187 --------------

# Affiché lorsqu'une action admin est refusée car l'item n'est plus disponible
# (verrouillé par un autre serveur, vendu, récupéré ou supprimé ailleurs).
# Placeholders disponibles :
#   %items%  - Description de l'item
admin-item-not-available: "<error>L'item <white>%items%<error> n'est plus disponible : il a été vendu, récupéré ou supprimé sur un autre serveur."


// ---- 4d. src/main/resources/es/messages.yml : après la ligne 187 --------------

admin-item-not-available: "<error>El objeto <white>%items%<error> ya no está disponible: fue vendido, recogido o eliminado en otro servidor."


// ---- 4e. src/main/resources/it/messages.yml : après la ligne 187 --------------

admin-item-not-available: "<error>L'oggetto <white>%items%<error> non è più disponibile: è stato venduto, ritirato o rimosso su un altro server."


// ---- 4f. src/main/resources/id/messages.yml : après la ligne 112 --------------

admin-item-not-available: "<error>Barang <white>%items%<error> udah gak tersedia: udah kejual, diambil, atau dihapus di server lain."


// ---- 4g. src/main/resources/th/messages.yml : après la ligne 187 --------------

admin-item-not-available: "<error>ไอเท็ม <white>%items%<error> ไม่พร้อมใช้งานแล้ว: ถูกขาย ถูกเรียกคืน หรือถูกลบไปแล้วบนเซิร์ฟเวอร์อื่น"
```

## 5.5 — Réécriture intégrale de adminRemoveItem : noop, relecture sous verrou, écriture avant diffusion, état terminal, verrou relâché sur tous les chemins

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : MEDIUM  
**Constats** : C-012, C-042, C-060, C-046

C-012, C-042 et C-060 sont trois lectures du même bloc de 30 lignes : les traiter en trois patchs successifs ferait que chacun invaliderait le suivant. Une seule réécriture. Ce qui change, dans l'ordre de la chaîne : (a) garde d'identité, comme à l'étape 2, car adminRemoveItem ne passe pas par executeRemoval ; (b) C-042, test de `LockToken.noop()` — le bridge Redis signale l'échec d'acquisition par un jeton noop et non par un future en erreur (RedisAuctionClusterBridge:196-200), et cette ligne était le SEUL des trois appelants de `lockItem` à ne pas le tester ; (c) C-060, relecture autoritaire sous verrou via `selectItemRow` (introduite à l'étape 3), gatée sur `isDistributed()` ; (d) C-012, l'UPDATE en base est désormais CHAÎNÉ et précède la diffusion, et la diffusion utilise la surcharge 3 arguments avec une destination TERMINALE (`StorageType.DELETED`) : l'ancienne surcharge 2 arguments passait `destination = null`, ce qui faisait écrire `state=REMOVED` côté Redis et poussait `ItemRemovedListener:64-88` à RECHARGER la ligne — encore LISTED puisque le future d'écriture était jeté — et à remettre l'item EN VENTE sur les autres nœuds ; (e) le verrou est relâché dans un `whenComplete`, donc aussi sur les nouveaux chemins d'échec que j'introduis — sans quoi j'aurais créé une fuite de verrou de 30 s à chaque détection de fantôme. Ce dernier point est mécaniquement C-112 (chantier 4) restreint à cette méthode : impossible d'écrire un adminRemoveItem correct sans lui, le chantier 4 doit donc retirer son hunk adminRemoveItem. J'ÉCARTE explicitement deux propositions de l'audit : la primitive de contrat `lockAndRead` (point 2 de C-060) — apiBreaking, crossRepo, elle n'a rien à faire dans un correctif ; et les gardes SQL de `ItemRepository.createUpdateSchema` (point 3 de C-060) — elles réécrivent la méthode dont le chantier 1 change la signature et, sans propagation du rowcount, un `where` supplémentaire n'est qu'un no-op silencieux qui donne une fausse confiance. Enfin le `giveItem` est confiné dans `runAtEntity` : il peut déverser le surplus au sol (`World#dropItem`, ZAuctionManager:931), illégal hors thread de région sous Folia, et la chaîne arrive ici depuis un thread d'executor.

```java
// Imports à ajouter en tête de ZAuctionManager.java :
//   import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
//   import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
//   import java.util.concurrent.TimeUnit;
//   import java.util.concurrent.atomic.AtomicBoolean;
//   import java.util.concurrent.atomic.AtomicReference;

// ---- Remplacer intégralement adminRemoveItem (lignes 658-710) -----------------

    @Override
    public void adminRemoveItem(Player admin, UUID targetUniqueId, Item item, StorageType storageType) {

        var clusterBridge = this.plugin.getAuctionClusterBridge();
        if (clusterBridge == null) {
            this.plugin.getLogger().severe("Cluster bridge is not initialized");
            return;
        }

        var inventoriesLoader = this.plugin.getInventoriesLoader();
        if (inventoriesLoader == null) {
            this.plugin.getLogger().severe("Inventories loader is not initialized");
            return;
        }

        var inventoryManager = inventoriesLoader.getInventoryManager();
        if (inventoryManager == null) {
            this.plugin.getLogger().severe("Inventory manager is not initialized");
            return;
        }

        // C-046 : garde d'identité. Le bouton admin capture la référence du store au rendu
        // (AdminSellingItemsButton:47) ; si un autre serveur l'a remplacée entre-temps, la
        // supprimer écraserait la ligne d'un état qui ne nous appartient plus.
        if (getItem(storageType, item.getId()) != item) {
            this.plugin.getLogger().info("Stale item reference for item " + item.getId() + " in " + storageType + ", admin removal refused");
            message(this.plugin, admin, Message.ADMIN_ITEM_NOT_AVAILABLE, "%items%", item.getItemDisplay());
            inventoryManager.updateInventory(admin);
            return;
        }

        var storageManager = this.plugin.getStorageManager();
        var previousStatus = item.getStatus();
        var tokenHolder = new AtomicReference<LockToken>();
        var statusChanged = new AtomicBoolean(false);

        clusterBridge.checkAvailability(item).thenCompose(available -> {

            if (!available) {
                this.plugin.getLogger().info("Item " + item.getId() + " is not available on the cluster");
                message(this.plugin, admin, Message.ADMIN_ITEM_NOT_AVAILABLE, "%items%", item.getItemDisplay());
                return this.<LockToken>failedFuture(new IllegalStateException("Item indisponible"));
            }

            return clusterBridge.lockItem(item, admin.getUniqueId(), storageType);

        }).thenCompose(lockToken -> {

            tokenHolder.set(lockToken);

            // C-042 : le bridge Redis signale l'échec d'acquisition par LockToken.noop() et non
            // par un future en erreur (RedisAuctionClusterBridge:196-200). Sans ce test, la
            // suppression admin s'exécutait pendant qu'un autre serveur tenait le verrou.
            // Inerte en mono-serveur : LocalAuctionClusterBridge:28 rend un failedFuture.
            if (LockToken.noop().value().equals(lockToken.value())) {
                this.plugin.getLogger().info("Item " + item.getId() + " is locked by another server, admin removal refused");
                message(this.plugin, admin, Message.ADMIN_ITEM_NOT_AVAILABLE, "%items%", item.getItemDisplay());
                return this.<Void>failedFuture(new IllegalStateException("Item verrouillé par un autre serveur"));
            }

            // C-060 : relecture autoritaire SOUS VERROU, comme PurchaseService:118-125 et
            // ExpireService:280-289. Sans elle, un clic admin sur un nœud désynchronisé écrase
            // en aveugle la ligne PURCHASED d'un acheteur et duplique l'item.
            return revalidateAdminRemoval(item, storageType, clusterBridge).thenCompose(fresh -> {

                if (!fresh) {
                    this.plugin.getLogger().warning("Admin removal refused for item " + item.getId() + ": the database row is no longer " + storageType);
                    removeItem(storageType, item.getId()); // purge du fantôme + marquage terminal (étape 1)
                    clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
                    message(this.plugin, admin, Message.ADMIN_ITEM_NOT_AVAILABLE, "%items%", item.getItemDisplay());
                    return this.<Void>failedFuture(new IllegalStateException("Item déjà traité sur un autre serveur"));
                }

                // C-012 : durabilité D'ABORD, diffusion d'un état TERMINAL ensuite.
                // L'ancienne surcharge 2 arguments passait destination = null, donc Redis
                // écrivait state=REMOVED et ItemRemovedListener:64-88 rechargeait la ligne
                // (encore LISTED, le future d'écriture étant jeté) pour la remettre EN VENTE.
                item.setStatus(ItemStatus.DELETED);
                statusChanged.set(true);

                return storageManager.updateItem(item, StorageType.DELETED)
                        .thenCompose(v -> clusterBridge.removeItem(item, storageType, StorageType.DELETED));
            });

        }).thenAccept(v -> {

            removeItem(storageType, item);
            clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);

            // La remise physique est confinée au thread de l'entité : giveItem déverse le
            // surplus au sol via World#dropItem (l.931), illégal hors thread de région sur
            // Folia, et cette continuation tourne sur un thread d'executor.
            // NOTE chantier 2 : quand C-014 aura rendu giveItem chaînable, remplacer ce hop
            // par un .thenCompose(v -> giveItem(admin, item)) chaîné en amont du message.
            this.plugin.getScheduler().runAtEntity(admin, w -> {
                giveItem(admin, item);

                var targetName = item.getSellerUniqueId().equals(targetUniqueId) ? item.getSellerName() : item.getBuyerName();
                message(this.plugin, admin, Message.ADMIN_ITEM_REMOVED, "%items%", item.getItemDisplay(), "%target%", targetName == null ? "unknown" : targetName);
            });

            inventoryManager.updateInventory(admin);

        }).whenComplete((v, throwable) -> {

            if (throwable != null) {
                this.plugin.getLogger().severe("Failed to remove item " + item.getId() + " for admin: " + throwable.getMessage());

                // Restaurer le statut si on l'avait déjà passé à DELETED : sinon l'item resterait
                // invisible localement (SortedItemsCache:305 ne garde que AVAILABLE) tout en
                // occupant le store, et deviendrait ni vendable ni récupérable.
                if (statusChanged.get()) item.setStatus(previousStatus);

                inventoryManager.updateInventory(admin);
            }

            // Le verrou n'était relâché que sur le chemin nominal : toute sortie par exception
            // (revalidation, écriture, diffusion) le laissait fuir jusqu'au TTL de 30 s.
            // La garde noop est indispensable : LocalAuctionClusterBridge.unlockItem fait un
            // itemLocks.remove(id) INCONDITIONNEL et casserait le verrou d'un autre appelant.
            var token = tokenHolder.get();
            if (token != null && !LockToken.noop().value().equals(token.value())) {
                clusterBridge.unlockItem(item, token, storageType).exceptionally(unlockError -> {
                    this.plugin.getLogger().severe("Failed to unlock item " + item.getId() + " after admin removal: " + unlockError.getMessage());
                    return null;
                });
            }
        });
    }

    /**
     * Relecture autoritaire de la ligne base pour le retrait admin, effectuée SOUS VERROU.
     * <p>
     * Court-circuitée en mono-serveur : la mémoire y est la vérité et la garde d'identité en
     * tête de {@link #adminRemoveItem} suffit ; on ne facture pas un aller-retour SQL par clic.
     *
     * @return {@code true} si la ligne est toujours celle qu'on croit détenir
     */
    private CompletableFuture<Boolean> revalidateAdminRemoval(Item item, StorageType storageType, AuctionClusterBridge clusterBridge) {

        if (!clusterBridge.isDistributed()) return CompletableFuture.completedFuture(true);

        var timeoutMs = this.plugin.getConfiguration().getPerformance().checkAvailabilityTimeoutMs();

        return selectItemRow(item.getId())
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .thenApply(optional -> optional.isPresent()
                        && optional.get().storage_type() == storageType
                        && (storageType != StorageType.LISTED || optional.get().buyer_unique_id() == null));
    }
```

## 5.6 — C-083 : la destination du retrait d'une annonce est décidée une seule fois, sur le thread du joueur

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/RemoveService.java`  
**Risque** : MEDIUM  
**Constats** : C-083

Aujourd'hui la condition `listedConfig.giveItem() && item.canReceiveItem(player)` est évaluée DEUX fois : à RemoveService:63, sur le thread du joueur, pour choisir la destination diffusée au cluster ; puis à ZAuctionManager:520, plusieurs allers-retours Redis plus tard et sur un thread d'executor, pour choisir ce qui est réellement écrit en base. Deux clics dans le même tick avec un seul slot libre suffisent à les faire diverger : la base passe l'item en EXPIRED pendant que Redis reçoit `state=DELETED`, l'item disparaît de tous les autres nœuds et devient INRÉCLAMABLE chez lui (checkAvailability refuse DELETED) jusqu'à expiration de la clé, 24 h par défaut. Je REJETTE le correctif de l'audit qui remonte la destination effective en changeant `AuctionManager.removeListedItem` en `CompletableFuture<StorageType>` : cette méthode est publiée (api/.../AuctionManager.java:272) et l'addon Redis est compilé contre un SHA figé — rupture binaire garantie. Je décide donc une seule fois, en amont, et je transmets le booléen à une SURCHARGE de l'implémentation ; la méthode publiée à 2 arguments conserve exactement son comportement actuel pour les tiers. Bénéfice collatéral : `canReceiveItem` (donc `player.getInventory().firstEmpty()`) cesse d'être lu depuis un thread d'executor. Changement de comportement assumé et à annoncer : avec `remove-listed-item.give-item: true`, si l'inventaire se remplit entre le clic et l'exécution, l'item est désormais REMIS QUAND MÊME et le surplus tombe au sol, au lieu de basculer silencieusement en EXPIRED. C'est précisément ce drop qui exigerait le thread de région, d'où le `runAtEntity` inclus dans la surcharge — il rend l'étape sûre même si le chantier 2 n'a pas encore été livré. Étape placée en dernier car c'est la seule qui entre en conflit textuel avec le chantier 2 (qui réécrit les lignes 507-549).

```java
// ---- 6a. RemoveService.java : remplacer les lignes 62-65 ----------------------

        var listedConfig = this.plugin.getConfiguration().getActions().listed();

        // C-083 : LA décision est prise ICI, une seule fois, sur le thread du joueur, et elle
        // est transmise telle quelle à l'implémentation. Avant, ZAuctionManager:520 la
        // réévaluait après plusieurs allers-retours Redis : la destination diffusée au cluster
        // et celle réellement écrite en base pouvaient diverger, ce qui rendait l'item
        // inréclamable pendant toute la durée de vie de la clé Redis.
        boolean giveItem = listedConfig.giveItem() && item.canReceiveItem(player);
        StorageType destination = giveItem ? StorageType.DELETED : StorageType.EXPIRED;

        return executeRemoval(ItemStatus.IS_BEING_REMOVED, player, item, () -> manager.updateInventory(player), () -> this.manager.removeListedItem(player, item, giveItem), StorageType.LISTED, destination);


// ---- 6b. ZAuctionManager.java : remplacer removeListedItem (lignes 506-550) ----

    @Override
    public CompletableFuture<Void> removeListedItem(Player player, Item item) {
        // Surcharge PUBLIÉE : comportement inchangé pour les plugins tiers.
        var listedConfig = this.plugin.getConfiguration().getActions().listed();
        return removeListedItem(player, item, listedConfig.giveItem() && item.canReceiveItem(player));
    }

    /**
     * Variante interne du retrait d'une annonce dont la destination est IMPOSÉE par l'appelant.
     * <p>
     * Volontairement absente de l'interface publiée {@code AuctionManager} : en changer la
     * signature casserait binairement l'addon Redis, compilé contre un SHA figé de l'API.
     * C'est {@code RemoveService.removeListedItem} qui décide, sur le thread du joueur, et qui
     * diffuse la même destination au cluster — les deux ne peuvent donc plus diverger.
     *
     * @param giveItem {@code true} pour rendre l'item au joueur (destination DELETED),
     *                 {@code false} pour le basculer dans les items expirés
     */
    public CompletableFuture<Void> removeListedItem(Player player, Item item, boolean giveItem) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        item.setStatus(ItemStatus.REMOVED);
        removeItem(StorageType.LISTED, item);

        this.updateListedItems(item, false, player);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED); // Suppression du cache du joueur

        CompletableFuture<Void> updateFuture;

        if (giveItem) {

            updateFuture = storageManager.updateItem(item, StorageType.DELETED);

            // La décision ayant été prise plus tôt, l'inventaire peut s'être rempli entre-temps
            // et giveItem déversera alors le surplus au sol (World#dropItem, l.931) : on confine
            // la remise au thread de l'entité, sans quoi Folia refuserait l'appel.
            // NOTE chantier 2 : quand C-014 aura rendu giveItem chaînable, remplacer ce hop par
            // updateFuture = storageManager.updateItem(...).thenCompose(v -> giveItem(player, item));
            this.plugin.getScheduler().runAtEntity(player, w -> giveItem(player, item));

        } else {

            var expiration = configuration.getExpireExpiration().getExpiration(player);
            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            item.setExpiredAt(new Date(expiredAt));

            addItem(StorageType.EXPIRED, item);
            updateFuture = storageManager.updateItem(item, StorageType.EXPIRED);
        }

        message(this.plugin, player, Message.ITEM_REMOVE_LISTED, "%items%", item.getItemDisplay());

        if (configuration.getActions().listed().openInventory()) {
            openMainAuction(player, getCache(player).get(PlayerCacheKey.CURRENT_PAGE, 1));
        } else {
            this.plugin.getScheduler().runAtEntity(player, w -> {
                if (player.isOnline()) player.closeInventory();
            });
        }

        callEvent(new AuctionRemoveListedItemEvent(item, player));

        logItemAction(LogType.REMOVE_LISTED, item, player, null, "removed_from_listed");

        return updateFuture;
    }
```

## 5.7 — changelogs.md + documentation Docusaurus EN/FR

**Fichier** : `changelogs.md`  
**Risque** : LOW  
**Constats** : C-009, C-012, C-042, C-046, C-060, C-083

Trois changements sont observables par les exploitants et DOIVENT être annoncés, sinon ils seront signalés comme des régressions : (1) le retrait devient fail-closed en cluster — si la base est injoignable, tous les retraits échouent au lieu de réussir à l'aveugle ; (2) la suppression admin d'un item EXPIRED/PURCHASED diffuse désormais un état TERMINAL, donc les réseaux qui comptaient sur le re-ajout par ItemRemovedListener ne le verront plus (c'est l'intention : l'admin a pris l'item) ; (3) avec `remove-listed-item.give-item: true`, un inventaire qui se remplit entre le clic et l'exécution ne fait plus basculer l'item en EXPIRED, il est rendu et le surplus tombe au sol. À signaler aussi : les items actuellement bloqués en production par C-083 (ligne EXPIRED + clé Redis à DELETED) ne sont PAS débloqués par le correctif, il faut un `DEL auction:item:<id>` manuel ou attendre le TTL. Le CLAUDE.md impose de répercuter toute modification de changelogs.md dans la documentation Docusaurus (C:\Users\Admin\Desktop\groupez\documentation), EN sous plugins/zauctionhouse/docs/ et FR sous i18n/fr/docusaurus-plugin-content-docs-zauctionhouse/current/.

```sql
# Unreleased

- **Fixed** Item duplication on the withdrawal path in clustered setups. Removing a listed, selling, expired or purchased item now re-reads the authoritative database row **while holding the cluster lock**, exactly like the purchase path already did, and aborts with `ITEM_NOT_AVAILABLE` when another server already sold, claimed or removed it. The extra query runs only when a distributed cluster bridge is installed; single-server installations are unaffected
- **Fixed** A withdrawal could be performed on a stale `Item` reference captured by an already-rendered inventory button (selling, expired, purchased, combined and admin views). Removal now refuses any reference that is no longer the one held by the in-memory store, and an item removed by identifier is marked as terminal so that buttons still armed in an open GUI can no longer act on it. This also fixes the ghost for servers running the Redis addon **without recompiling it**
- **Fixed** `adminRemoveItem` ignored a failed lock acquisition (`noop` token) and removed the item anyway while another server held the lock
- **Fixed** `adminRemoveItem` broadcast the removal **before** writing to the database, with a non-terminal destination: other servers reloaded the row, found it still listed and put the item back on sale. The database write is now chained and awaited first, and the broadcast carries a terminal `DELETED` destination
- **Fixed** `adminRemoveItem` never released the cluster lock when the operation failed; the item stayed locked until the Redis TTL expired
- **Fixed** Removing a listed item evaluated the "give the item back" condition twice — once to pick the destination broadcast to the cluster, once to pick what was written to the database. With `action.remove-listed-item.give-item: true` and a nearly full inventory the two could disagree, leaving the item stored as EXPIRED locally while every other server saw it as DELETED, making it permanently unclaimable
- **Changed** With `action.remove-listed-item.give-item: true`, an inventory that fills up between the click and the execution no longer silently sends the item to the expired tab: the item is handed back and the overflow drops on the ground
- **Changed** Admin removal of an EXPIRED or PURCHASED item now propagates a terminal state across the cluster; other nodes no longer re-add the row
- **Changed** In clustered mode, withdrawals now fail closed when the database is unreachable instead of succeeding blindly
- **Added** `admin-item-not-available` message (all six locales)

> Note de migration : les items déjà bloqués par l'ancien défaut (ligne `EXPIRED` en base + clé `auction:item:<id>` à `DELETED` dans Redis) ne sont pas débloqués par cette mise à jour. Exécuter `DEL auction:item:<id>` sur ces identifiants, ou attendre l'expiration de `redis-config.item-state-ttl-seconds`.
```

### Ruptures d'API et stratégie de compatibilité

- AUCUNE rupture de signature. Le module api/ ne reçoit qu'une constante d'enum ADDITIVE (`Message.ADMIN_ITEM_NOT_AVAILABLE`) : aucun `switch` exhaustif sur `Message` n'existe dans les deux dépôts (vérifié), l'addon Redis compilé contre le SHA figé `zauctionhousev4-api:deb8f16` reste binairement compatible et n'a pas besoin d'être recompilé ni de voir son pin bumpé.
- `ZAuctionManager.getItem(StorageType, int)` est ajouté sur l'IMPLÉMENTATION uniquement, pas sur l'interface publiée `AuctionManager`. C'est un choix délibéré : le chantier PERF (C-050 / C-055) veut cette méthode dans l'API, mais l'y mettre maintenant casserait la compilation de tout implémenteur tiers de `AuctionManager` et imposerait une republication de zauctionhousev4-api plus un bump du pin de l'addon. Quand C-050 sera livré, il suffira d'ajouter `@Override` sur la méthode existante.
- `ZAuctionManager.selectItemRow(int)` : nouvelle méthode publique sur l'implémentation, absente de `StorageManager` et de `AuctionManager`. Elle expose délibérément un `Optional<ItemDTO>` (type déjà publié dans api/storage/dto) plutôt qu'un `Item`, pour ne pas dépendre de `StorageManager.selectItem` qui rend `null` sur économie manquante. Aucun impact tiers.
- `ZAuctionManager.removeListedItem(Player, Item, boolean)` : SURCHARGE ajoutée sur l'implémentation. La méthode publiée `AuctionManager.removeListedItem(Player, Item)` conserve sa signature `CompletableFuture<Void>` ET son comportement exact (elle calcule elle-même le booléen comme aujourd'hui). C'est le refus explicite du correctif d'audit de C-083 qui la faisait passer à `CompletableFuture<StorageType>` — rupture binaire garantie pour l'addon et les plugins tiers.
- CHANGEMENT DE SÉMANTIQUE, non de signature, sur la méthode publiée `AuctionManager.removeItem(StorageType, int)` : l'instance retirée est désormais marquée `ItemStatus.DELETED`. Le contrat "retirer par identifiant = sortie définitive" est explicité dans le javadoc. La surcharge `removeItem(StorageType, Item)` est inchangée et cesse de déléguer à la surcharge `int` (c'est elle qu'utilisent les transitions internes qui ré-ajoutent la même instance ailleurs). Aucun appelant, dans les deux dépôts, ne dépend de l'ancien comportement — vérifié un par un (ExpireService:286, ItemBoughtListener:49, ItemRemovedListener:39).

### Migrations de schéma

- AUCUNE. Ce chantier ne crée ni table, ni colonne, ni index, et ne modifie aucun schéma Sarah. Il n'utilise que des lectures existantes (`ItemRepository.select(int)`, déjà appelée par `ZStorageManager.selectItem`) et l'écriture existante `updateItem(item, StorageType)`.
- COMPLÉMENT recommandé mais hors périmètre (chantier PERF / C-066) : aucun index n'existe sur `%prefix%items` en dehors de la clé primaire et des contraintes de clé étrangère. Ma relecture autoritaire attaque exclusivement la clé primaire (`where id = ?`), elle n'en a donc pas besoin — c'est précisément pourquoi j'ai choisi `ItemRepository.select(int)` plutôt qu'un chemin plus riche.

### Changements de configuration

- UNE seule clé nouvelle : `admin-item-not-available` dans messages.yml. Elle doit être répliquée dans les SIX jeux de langue livrés sur disque — src/main/resources/messages.yml (référence anglaise) puis fr/, es/, it/, id/ et th/. ATTENTION : le CLAUDE.md du projet et l'énoncé du chantier annoncent 4 langues ; `ls src/main/resources/` en montre 6 (id/ indonésien et th/ thaï existent bel et bien). Le CLAUDE.md est périmé sur ce point et devrait être corrigé.
- Une clé manquante dans une locale ne fait PAS planter le plugin : MessageLoader.load() retombe sur la valeur par défaut de l'enum. Mais il journalise alors un SEVERE (« Messages were not loaded correctly ») suivi d'une ligne par message manquant à CHAQUE démarrage : les six fichiers doivent partir dans le même commit.
- Aucune clé nouvelle dans config.yml, economies.yml, categories.yml, rules.yml, discord.yml, ni dans inventories/ ou patterns/. Aucune migration de schéma, aucune nouvelle table, aucune nouvelle colonne, aucun nouvel index.
- Deux clés EXISTANTES changent d'effet observable et doivent être re-documentées (commentaire du fichier + Docusaurus EN/FR) : `action.remove-listed-item.give-item: true` (l'item est désormais rendu même si l'inventaire s'est rempli entre le clic et l'exécution, le surplus tombant au sol) et `performance.cluster-timeout.check-availability-ms` (il borne désormais aussi la relecture autoritaire du chemin de retrait, en plus de celle du chemin d'achat).

### Validation

- INVARIANT SQL GLOBAL, à exécuter après chaque scénario : `SELECT id, storage_type, buyer_unique_id FROM zauctionhousev4_items WHERE id = <id>;` doit rester cohérent avec ce que les joueurs détiennent réellement. Aucune ligne ne doit passer de `PURCHASED` à `DELETED` sans que l'acheteur ait cliqué, et aucune ligne `PURCHASED` ne doit voir son `buyer_unique_id` effacé.
- SCÉNARIO 1 — duplication par GUI ouvert (C-046, C-009). Deux serveurs A et B + Redis + MySQL. Le vendeur V se connecte sur B et ouvre `/ah selling` ; laisser l'inventaire OUVERT. Sur A, l'acheteur P achète l'item #42 (`action.purchased-item.give-item: false`, valeur par défaut). Sur B, sans refermer, cliquer la case toujours affichée. ATTENDU : message d'indisponibilité, la case disparaît, `SELECT storage_type, buyer_unique_id FROM ..._items WHERE id=42` rend `PURCHASED` + l'UUID de P, et V n'a RIEN reçu. AVANT le correctif : V reçoit l'item et la ligne passe à `DELETED`. Vérifier aussi dans les logs la ligne `Stale item reference for item 42 in LISTED, removal refused`.
- SCÉNARIO 2 — bus Redis coupé (C-009, celui que la garde d'identité seule ne couvre pas). Sur B, `iptables -A OUTPUT -p tcp --dport 6379 -j DROP` (ou `redis-cli CLIENT KILL TYPE pubsub`) pour que B ne reçoive plus rien, mais garde son accès MySQL. Vendre l'item #43 sur A. Sur B, l'objet Item est TOUJOURS dans le store LISTED : la garde d'identité passe. Retirer l'item sur B via `/ah selling`. ATTENDU : `ITEM_NOT_AVAILABLE`, log `Stale item 43 (memory=LISTED, db=gone)` ou `db=PURCHASED`, et le fantôme purgé du store B au tick suivant. C'est LE test qui prouve que la relecture autoritaire fonctionne indépendamment du bus.
- SCÉNARIO 3 — verrou détenu ailleurs, chemin admin (C-042). Poser le verrou à la main : `SET auction:lock:44 "item:44" EX 60` et `HSET auction:item:44 state LOCKED`. Sur B, ouvrir l'inventaire admin des ventes du vendeur et cliquer l'item #44. ATTENDU : message `admin-item-not-available`, l'item reste en base et en mémoire, l'admin ne reçoit rien, et `EXISTS auction:lock:44` rend TOUJOURS 1 avec `TTL` > 0 — on n'a pas volé ni relâché le verrou d'autrui. AVANT : l'admin reçoit l'item et la ligne passe à DELETED.
- SCÉNARIO 4 — ordre écriture/diffusion sur le chemin admin (C-012). Lancer `redis-cli MONITOR` sur un troisième terminal et `SET GLOBAL general_log = 'ON'` côté MySQL. Sur A, supprimer un item LISTED via l'inventaire admin. ATTENDU dans les deux journaux, DANS CET ORDRE : l'`UPDATE ..._items SET storage_type='DELETED' WHERE id=<id>` PUIS le `HSET auction:item:<id> state DELETED` PUIS le `PUBLISH`. Sur B, vérifier que l'item n'est PAS remis en vente : `manager.getItems(LISTED)` ne le contient plus (via `/ah` ou le placeholder `%zauctionhouse_listed_items%`), et le log de B ne montre aucun `addItem(LISTED, ...)` déclenché par ItemRemovedListener.
- SCÉNARIO 5 — kill -9 entre l'écriture et la diffusion (C-012, durabilité). Sur A, poser un point d'arrêt (ou un `Thread.sleep` temporaire de 10 s) entre `updateItem(DELETED)` et `clusterBridge.removeItem(...)`, déclencher une suppression admin, puis `kill -9` le serveur A pendant la pause. ATTENDU : la ligne est `DELETED` en base ; au redémarrage, `ItemRepository.select()` la filtre, l'item n'apparaît sur AUCUN serveur, et le verrou Redis expire seul au bout de `lock-ttl-seconds`. AVANT : la ligne était encore LISTED et l'item revenait en vente.
- SCÉNARIO 6 — divergence de destination (C-083). Régler `action.remove-listed-item.give-item: true` et `action.remove-listed-item.open-confirm-inventory: false`. Le vendeur a EXACTEMENT un slot libre et deux annonces A et B. Cliquer les deux dans le MÊME tick (macro/autoclicker à 0 ms, ou deux paquets de clic envoyés dans le même paquet réseau). ATTENDU : pour chacun des deux items, la destination diffusée (`redis-cli MONITOR`, champ `destinationStorageType` de l'ItemRemovedMessage) est ÉGALE au `storage_type` de la ligne en base. Le second item est rendu (surplus au sol) et sa ligne est `DELETED`, ou bien il part en EXPIRED des deux côtés — jamais l'un et l'autre. Contrôle de non-blocage : `HGET auction:item:<id> state` ne doit JAMAIS valoir `DELETED` pour un item dont la ligne est `EXPIRED`.
- SCÉNARIO 7 — non-régression mono-serveur (coût). SQLite, aucun addon Redis. Activer `general_log`/`performance-debug`, remplir 100 items expirés, cliquer « tout récupérer ». ATTENDU : ZÉRO requête `SELECT ... FROM ..._items WHERE id = ?` supplémentaire par rapport à la version précédente (la relecture est court-circuitée par `!clusterBridge.isDistributed()`), les 100 items sont rendus, `REMOVE_ALL_ITEMS` annonce 100, et la durée totale est dans l'épaisseur du trait de la mesure de référence.
- SCÉNARIO 8 — coût en cluster (le compromis assumé). Même test qu'au 7 mais avec l'addon Redis actif : compter exactement 100 `SELECT ... WHERE id = ?` supplémentaires et mesurer la latence totale. Critère d'acceptation : < 1,5 s pour 100 items sur MySQL local. Si le seuil est dépassé, c'est le signe que `plugin.getExecutorService()` est saturé et il faut livrer le quick-win `ZStorageManager:177/:215` du chantier 8 avant de mettre en production.
- SCÉNARIO 9 — non-régression de l'expiration paresseuse (le faux positif que je cherche à éviter). En cluster, laisser expirer une annonce puis ouvrir immédiatement l'onglet « expirés » et cliquer AVANT que l'`updateItems` asynchrone n'ait atterri. ATTENDU : le retrait est refusé une fois (`memory=EXPIRED, db=LISTED`), l'entrée RESTE VISIBLE dans l'onglet, et un second clic une seconde plus tard réussit. C'est le test qui valide la distinction `gone` / `stale` : si l'entrée disparaît de l'onglet, la condition de purge est trop large.
- SCÉNARIO 10 — pas de remise en vente sur détection de fantôme (la régression que le correctif d'audit aurait introduite). Sur trois serveurs A, B, C. Vendre l'item #45 sur A. Sur B, tenter le retrait (il sera refusé). ATTENDU : sur C, l'item #45 n'apparaît JAMAIS dans `/ah` après le refus de B. Contrôle direct : aucun `ItemStatusMessage(#45, IS_BEING_REMOVED -> AVAILABLE)` ne doit apparaître dans `redis-cli MONITOR`. Sans le drapeau `staleDetected`, ce message part et C remet l'item en vente.

### Questions ouvertes pour le mainteneur

- ARBITRAGE 1, le plus important — j'ai REFUSÉ la recommandation « Perf » de l'audit qui demande de désactiver la relecture autoritaire dans les retraits de masse. Les boutons « tout récupérer » sont précisément le vecteur le plus cliqué du constat ; les désarmer y laisserait le trou grand ouvert pour un gain de ~0,5 s par lot de 100. Le garde-fou de coût que j'ai retenu à la place est le gate `isDistributed()`, qui épargne 100 % du coût aux installations mono-serveur. Si le mainteneur préfère malgré tout la variante de l'audit, la ligne à ajouter est explicite (le drapeau `updatePlayer == false` des trois surcharges distingue déjà le mode masse) — mais il faut alors documenter que la duplication reste possible via ces trois boutons.
- ARBITRAGE 2 — j'ai exclu de ce lot les gardes SQL de `ItemRepository.createUpdateSchema` (point 3 du correctif d'audit de C-060). Deux raisons : elles réécrivent la méthode dont le chantier 1 (C-026) change la signature, et sans propagation du rowcount une clause `where` supplémentaire ne fait que transformer un écrasement en no-op SILENCIEUX — on saurait qu'on n'a rien cassé, pas qu'on a gagné la course. Le mainteneur doit décider si le chantier 1 est planifié ; si oui, ce chantier-ci est sa ceinture et le chantier 1 ses bretelles. Si non, il faut au minimum ajouter `case DELETED -> schema.where("storage_type", "!=", StorageType.DELETED.name())`, qui est le seul cas où un 0-ligne silencieux est sémantiquement identique au succès.
- ARBITRAGE 3 — mon étape 5 relâche le verrou dans un `whenComplete`, ce qui est mécaniquement le contenu de C-112 restreint à `adminRemoveItem`. Il était impossible de faire autrement : les chemins d'échec que j'ajoute (noop, revalidation) auraient sinon créé une nouvelle fuite de verrou de 30 s. LE CHANTIER 4 DOIT DONC RETIRER SON HUNK `adminRemoveItem` de C-112, sous peine de double libération. C-112 reste entier sur les autres chemins.
- CHANGEMENT VISIBLE PAR LES JOUEURS à valider — avec `action.remove-listed-item.give-item: true`, un inventaire qui se remplit entre le clic et l'exécution ne renvoie plus silencieusement l'item dans l'onglet « expirés » : il est rendu et le surplus tombe au sol. Sur un serveur avec anti-lag agressif ou en zone protégée, cet item au sol peut être ramassé par un tiers. L'alternative (réserver le slot capturé au moment de la décision et refuser la remise si l'inventaire s'est rempli) est implémentable mais réintroduit une décision différée. Décision du mainteneur.
- CHANGEMENT VISIBLE PAR LES EXPLOITANTS à valider — la suppression admin d'un item EXPIRED ou PURCHASED diffuse désormais un état TERMINAL (`DELETED`). Les réseaux qui s'appuyaient sur le re-ajout par `ItemRemovedListener` pour rendre l'item réclamable ailleurs ne le verront plus. C'est l'intention (l'admin a physiquement pris l'item), mais c'est un changement de comportement observable en cluster.
- TROU RÉSIDUEL ASSUMÉ, à couvrir par le chantier 7 — sur le chemin `staleDetected` je n'émets AUCUN statut au cluster (émettre un statut terminal serait appliqué en aveugle par `ItemStatusListener:54`, qui n'a pas encore le compare-and-set de C-004 : diffuser DELETED sur un item vivant en EXPIRED chez un tiers casserait la réclamation du vendeur). Conséquence : un troisième nœud ayant reçu notre `IS_BEING_REMOVED` mais pas encore le message de suppression du gagnant peut garder ce statut transitoire jusqu'à l'arrivée de ce dernier. En pratique le message du gagnant est parti AVANT le nôtre, donc la fenêtre est bornée ; le filet général est le TTL des statuts de confirmation du chantier 7 (C-072) et le CAS de C-004. À confirmer par le mainteneur comme acceptable pour cette release.
- PÉRIMÈTRE NON COUVERT, à décider — les quatre gardes de statut de RemoveService (l.55, 96, 134, 172) restent en place et acceptent toujours `IS_REMOVE_CONFIRM` (l.55). Le trou décrit par la contre-expertise de C-009 (« ListedItemsButton:118-133 n'a aucune garde de statut avant de poser IS_REMOVE_CONFIRM », donc il écrase un IS_BEING_PURCHASED propagé par le cluster) appartient à C-048/C-049, chantier 8. Mes trois remparts arrêtent la conséquence (la duplication) mais pas la cause (l'écrasement de statut).
- CORRECTION DE DOCUMENTATION à acter — `src/main/resources/` contient SIX jeux de configuration (racine anglaise + es/, fr/, id/, it/, th/), pas quatre. Le CLAUDE.md du projet ET l'énoncé de ce chantier sont périmés sur ce point ; tous les chantiers suivants qui ajouteront une clé se tromperont tant que ce n'est pas corrigé.

### Retour arrière

"Retour arrière PUR PAR DOWNGRADE DE JAR : ce chantier n'introduit aucune migration de schéma, aucune colonne, aucun index et aucune écriture de format nouveau. Redéployer le jar précédent suffit et rétablit intégralement le comportement antérieur. Points à connaître : (1) la clé `admin-item-not-available` restera dans les messages.yml des serveurs mis à jour — c'est inoffensif, MessageLoader:79 ignore silencieusement toute clé inconnue (`if (message == null) continue;`), aucun SEVERE, aucun crash ; il n'est donc PAS nécessaire de la retirer des fichiers. (2) Les lignes `%prefix%items` que le chantier a fait passer à `DELETED` sur le chemin admin l'auraient été de toute façon par l'ancien code — aucune donnée n'est écrite dans un état que l'ancien jar ne saurait relire. (3) Les clés Redis `auction:item:<id>` portant `state=DELETED` posées par la nouvelle diffusion terminale du retrait admin ne sont pas comprises différemment par l'ancien code (checkAvailability et LOCK_SCRIPT refusent DELETED dans les deux versions) : l'item reste correctement mort, il ne ressuscite pas. (4) ROLLBACK PARTIEL possible et recommandé si un seul comportement pose problème en production : chaque étape est un commit indépendant et revertible isolément, dans l'ordre inverse. En particulier, l'étape 6 (C-083) peut être revertée seule si le drop au sol dérange, sans toucher aux remparts anti-duplication des étapes 1 à 5 ; et l'étape 3 peut être neutralisée SANS revert, en repassant temporairement le serveur en bridge local, puisque toute la relecture autoritaire est gatée sur `isDistributed()`. (5) Aucun redémarrage coordonné n'est nécessaire : un cluster peut tourner en version mixte pendant le déploiement tournant, les nouveaux nœuds étant simplement plus stricts que les anciens (fail-closed), jamais l'inverse."


---

# Chantier 6 — Chantier 6 — Cesser d'avaler les erreurs, et fail-closed au démarrage

Ce chantier ferme la famille de défauts la plus insidieuse des deux dépôts : les erreurs qui ne remontent nulle part. Une requête qui échoue rend une liste vide et l'hôtel des ventes se charge sans contenu tout en restant achetable (C-001) ; un ItemStack illisible devient un BARRIER vendu au prix fort (C-029, C-054) ; un nœud Redis démarre sans UUID, sans listeners ou sans bridge et se croit synchronisé (C-016, C-031, C-036, C-113) ; une migration V3 ratée s'affiche en vert (C-040) et détruit l'argent PENDING sans le dire (C-033, C-075) ; un démarrage avorté laisse le pool Hikari vivant (C-118). L'axe commun est unique : chaque chemin de démarrage et de chargement doit soit réussir, soit échouer BRUYAMMENT et refuser de servir, jamais servir un état partiel. Il est indivisible parce que rendre un maillon fail-closed sans les autres ne fait que déplacer le silence : paginer le IN sans mettre l'item en quarantaine expose des lots vides, casser la récursion de ItemStackUtils sans marqueur de format ne dit toujours pas POURQUOI un décodage a échoué, et refuser SQLITE+cluster côté plugin ne sert à rien si l'addon a déjà démarré son thread abonné. Les 16 étapes sont ordonnées pour que chaque commit compile et laisse le serveur démarrable.

**Prérequis**

- Aucun chantier prérequis DUR : les 16 étapes sont autoportantes et ne dépendent d'aucun correctif d'un autre chantier.
- Contrainte d'ordonnancement INVERSE à faire connaître aux autres chantiers : l'étape 2 (C-118) réécrit ZAuctionPlugin.onDisable — le chantier 8 (C-022, drapeau shuttingDown + RejectedExecutionHandler) doit se rebaser dessus, jamais l'inverse (confirmé par la vérification de la grappe ZAuctionPlugin).
- L'étape 5 réécrit ItemLoaderUtils.createAuctionItem : le chantier PERF (C-090, groupingBy sur getAuctionItems) touche la méthode voisine du même fichier. Livrer C-090 après, il se rebase en 3 lignes.
- L'étape 6 livre la primitive selectItemState mais NE migre aucun appelant : PurchaseService:118 (chantier QUICK / C-006) et ExpireService:283 (chantier 1 / C-091) réécrivent déjà ces blocs et doivent consommer selectItemState au passage.
- Suivi attendu du chantier 2 : l'étape 3 fait rendre null à ItemStackUtils.serializeItemStack en cas d'échec (au lieu d'une NPE ou d'une chaîne vide). C-067 doit ajouter la garde `if (encoded == null) throw` dans le chemin de vente, sinon on échange un crash contre un INSERT refusé en aval.
- Suivi attendu du chantier 7 : ClusterUnavailableBridge introduit à l'étape 14 est la brique que C-018 doit réutiliser dans ZAuctionHouseRedis.onDisable — ne pas en créer une seconde.

**Constats couverts** : C-001, C-010, C-016, C-029, C-031, C-033, C-034, C-036, C-040, C-044, C-054, C-057, C-075, C-108, C-109, C-113, C-118, C-011 (volet quarantaine seulement, indissociable de C-001)

*~790 LOC · 3 fichiers nouveaux*

## 6.1 — C-040 — V3MigrationProvider propage le verdict réel au lieu de le remplacer par un succès

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/migration/v3/V3MigrationProvider.java`  
**Risque** : LOW  
**Constats** : C-040

Première étape volontairement : 12 lignes, zéro dépendance, et elle rend OBSERVABLE tout le reste du chantier migration (étapes 8 à 11). Sans elle, aucun test d'acceptation des correctifs V3 ne peut distinguer un succès d'un échec.

```java
// Remplacer le bloc final de migrate() (l.126-132) par :

        return migrationFuture.thenApply(result -> {

            // Le verdict du service DOIT etre propage. Le thenApply d'origine mappait
            // inconditionnellement vers MigrationResult.success : une base V3 vide ou des
            // identifiants errones affichaient un succes vert a l'admin, et la branche
            // Message.MIGRATION_FAILED de CommandAuctionAdminMigrate:94 etait du code mort.
            // Les trois providers freres (CrazyAuctions, DonutAuction, ZelAuction) le font deja.
            if (!result.isSuccess()) {
                plugin.getLogger().severe("[Migration] zAuctionHouse V3 migration FAILED: " + result.getErrorMessage());
                return MigrationResult.failure(result.getErrorMessage());
            }

            // `errors` etait affiche a l'admin sans jamais empecher le message de succes :
            // on ne fait pas echouer la migration pour autant (les lignes valides sont importees),
            // mais l'exploitant doit savoir qu'il ne peut PAS encore supprimer ses donnees V3.
            if (result.getErrors() > 0) {
                plugin.getLogger().warning("[Migration] zAuctionHouse V3 migration completed with " + result.getErrors()
                        + " error(s). Read the SEVERE/WARNING lines above BEFORE deleting your V3 data.");
            }

            return MigrationResult.success(
                    result.getPlayersImported(),
                    result.getItemsImported(),
                    result.getTransactionsImported(),
                    result.getErrors(),
                    result.getDurationMs()
            );
        }).exceptionally(throwable -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Migration] zAuctionHouse V3 migration threw", throwable);
            return MigrationResult.failure(throwable.getMessage());
        });
```

## 6.2 — C-118 — Le teardown de onDisable ne dépend plus de isEnabled : un démarrage avorté ne laisse plus le pool Hikari vivant

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionPlugin.java`  
**Risque** : LOW  
**Constats** : C-118

Prérequis de l'étape 5 : C-001 rend le chargement fail-closed, donc onEnable va désormais s'interrompre pour de bon sur une panne DB. Sans ce teardown inconditionnel, chaque échec de démarrage laisserait un pool Hikari et un ForkJoinPool vivants jusqu'au kill du serveur. La chronologie T1 de l'audit (« isValid() renvoie false → pool fuite ») est fausse et n'est PAS ce qu'on corrige : HikariDataSource fait du fail-fast dans son constructeur et détruit son housekeeper lui-même. Le vrai trou est toute exception entre les lignes 137 et 178.

```java
// 1) Nouveau champ, a placer pres de `private boolean isEnabled = false;` (l.98) :

    private volatile boolean teardownDone = false;

// 2) Remplacer INTEGRALEMENT onDisable() (l.183-211) par :

    @Override
    public void onDisable() {

        // ZAuctionPlugin melangeait deux notions distinctes : « le plugin a fini de demarrer »
        // (isEnabled, positionne en toute derniere ligne de onEnable) et « les ressources JVM
        // sont ouvertes » (asyncExecutor l.82, sortedItemsCache l.135, connexion base l.137).
        // Les ressources naissent AVANT le point de sortie anticipee, la garde `if (!isEnabled)
        // return;` sautait donc TOUT le teardown des qu'une exception survenait entre les
        // lignes 137 et 178 (migration Sarah, YAML d'inventaire invalide, loadItems, hooks).
        // Seul le desenregistrement du VersionChecker reste conditionne au demarrage complet.
        if (this.teardownDone) return;
        this.teardownDone = true;

        if (this.isEnabled && this.versionChecker != null) {
            this.versionChecker.unregister();
        }

        // Arret du cache trie (ferme le ForkJoinPool prive).
        this.auctionManager.shutdown();

        // Drain de l'executeur asynchrone. CET ORDRE EST CRITIQUE : le drain doit rester
        // AVANT la fermeture de la connexion, c'est lui qui garantit que les ecritures deja
        // soumises aboutissent au lieu de mourir sur une connexion fermee (cf. C-022).
        this.asyncExecutor.shutdown();
        try {
            if (!this.asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                this.asyncExecutor.shutdownNow();
                if (!this.asyncExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    getLogger().warning("ExecutorService did not terminate properly");
                }
            }
        } catch (InterruptedException exception) {
            this.asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Fermeture de la connexion base, protegee par un test de nullite : storageManager
        // .onEnable() peut avoir echoue avant d'affecter databaseConnection, et onDisable est
        // alors invoque de facon REENTRANTE depuis l'interieur de onEnable (ZStorageManager:55
        // appelle disablePlugin alors que Bukkit a deja positionne isEnabled a true).
        try {
            if (this.storageManager.getDatabaseConnection() != null) {
                this.storageManager.onDisable();
            }
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Failed to close the database connection", exception);
        }

        // SimpleContext.shutdown() commence par `if (!ready) return;` : no-op tant que
        // context.ready() (l.166) n'a pas ete atteint. Aucun risque a le rendre inconditionnel.
        this.context.shutdown();
    }

// L'import java.util.logging.Level est deja present (l.72 du fichier).
```

## 6.3 — C-054 — Casser la récursion mutuelle ItemStackUtils ↔ Base64ItemStack et la NPE de serializeItemStack

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/utils/ItemStackUtils.java`  
**Risque** : LOW  
**Constats** : C-054

Prérequis dur de l'étape 4 (le marqueur de format doit pouvoir router vers un décodeur qui ne se rappelle pas lui-même) et de l'étape 5 (la quarantaine n'a de sens que si un décodage raté rend null au lieu de tuer la JVM). Je REJETTE la « correction rapide » de la section 4 de l'audit : elle patche serializeItemStack, ne touche pas la récursion, et référence un symbole `logger` inexistant — elle ne compile pas.

```java
// ===== FICHIER 1 : API/.../api/utils/Base64ItemStack.java =====
// Extraire le corps du decodeur/encodeur Bukkit dans deux methodes de PORTEE PAQUET,
// pour que le repli de ItemStackUtils puisse les appeler sans repasser par le dispatcher.
// Elargir au passage le catch : decoder.decode() leve IllegalArgumentException sur une
// charge non-Base64 et readObject() leve ClassCastException, ni l'une ni l'autre n'etaient
// rattrapees et elles s'echappaient de ItemLoaderUtils:40, au milieu d'un .map() de stream,
// avortant TOUT le lot de chargement.

    public static String encode(ItemStack itemStack) {
        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            return ItemStackUtils.serializeItemStack(itemStack);
        }
        return encodeBukkitStream(itemStack);
    }

    public static ItemStack decode(String data) {
        if (data == null) return null;
        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            return ItemStackUtils.safeDeserializeItemStack(data);
        }
        return decodeBukkitStream(data);
    }

    /**
     * Encodes an ItemStack through the Bukkit object stream, without any version dispatch.
     *
     * @param itemStack the ItemStack to encode
     * @return the Base64-encoded payload, or {@code null} if encoding fails
     */
    static String encodeBukkitStream(ItemStack itemStack) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
            ObjectOutputStream objectOutputStream = new BukkitObjectOutputStream(gzipOutputStream);
            objectOutputStream.writeObject(itemStack);
            objectOutputStream.close();
            return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
        } catch (Exception exception) {
            org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                    "[zAuctionHouse] Unable to encode an ItemStack", exception);
            return null;
        }
    }

    /**
     * Decodes a Bukkit object stream payload, without any version dispatch.
     * <p>
     * Package-private on purpose: {@code ItemStackUtils.safeDeserializeItemStack} calls it
     * DIRECTLY as its fallback, so that the fallback can never re-enter {@link #decode(String)}.
     *
     * @param data the Base64-encoded payload
     * @return the decoded ItemStack, or {@code null} if decoding fails
     */
    static ItemStack decodeBukkitStream(String data) {
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
            ObjectInputStream objectInputStream = new BukkitObjectInputStream(gzipInputStream);
            ItemStack item = (ItemStack) objectInputStream.readObject();
            objectInputStream.close();
            return item;
        } catch (Exception exception) {
            org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.WARNING,
                    "[zAuctionHouse] Unable to decode an ItemStack payload", exception);
            return null;
        }
    }

// ===== FICHIER 2 : API/.../api/utils/ItemStackUtils.java =====
// Remplacer serializeItemStack (l.19-38) et safeDeserializeItemStack (l.41-47) par :

    /**
     * Change {@link ItemStack} to {@link String}
     *
     * @return {@link String}, or {@code null} when the legacy NMS path is unusable
     */
    public static String serializeItemStack(ItemStack paramItemStack) {

        if (paramItemStack == null) return "null";

        try {
            Class<?> localClass = EnumReflectionItemStack.NBTTAGCOMPOUND.getClassz();
            Constructor<?> localConstructor = localClass.getConstructor();
            Object localObject1 = localConstructor.newInstance();
            Object localObject2 = EnumReflectionItemStack.CRAFTITEMSTACK.getClassz().getMethod("asNMSCopy", new Class[]{ItemStack.class}).invoke(null, paramItemStack);

            EnumReflectionItemStack.ITEMSTACK.getClassz().getMethod("b", new Class[]{localClass}).invoke(localObject2, localObject1);

            ByteArrayOutputStream localByteArrayOutputStream = new ByteArrayOutputStream();
            EnumReflectionItemStack.NBTCOMPRESSEDSTREAMTOOLS.getClassz().getMethod("a", new Class[]{localClass, OutputStream.class}).invoke(null, localObject1, localByteArrayOutputStream);
            return Base64.encode(localByteArrayOutputStream.toByteArray());
        } catch (Throwable throwable) {
            // Le code d'origine dereferencait `localByteArrayOutputStream` APRES son propre
            // catch : NPE garantie des que la reflexion echoue (getClassz() fait
            // nmsPackage.split(",")[3], ce qui explose sur Paper >= 1.20.6).
            // Et surtout : NE JAMAIS rendre Base64.encode(new byte[0]). Cette chaine vide
            // partait telle quelle dans auction_items.itemstack, l'INSERT REUSSISSAIT et le
            // vendeur perdait son item sans la moindre trace. On rend null : la vente echoue
            // bruyamment (garde a poser dans le chemin de vente, cf. C-067 / chantier 2).
            Bukkit.getLogger().log(Level.SEVERE,
                    "[zAuctionHouse] Unable to serialize an ItemStack through the legacy NBT path", throwable);
            return null;
        }
    }

    public static ItemStack safeDeserializeItemStack(String paramString) {
        try {
            return tryDeserializeItemStack(paramString);
        } catch (Throwable throwable) {
            // RECURSION MUTUELLE INFINIE corrigee (C-054) : le repli appelait
            // Base64ItemStack.decode(), qui sur un serveur < 1.20.5 rappelle cette meme
            // methode -> StackOverflowError, qu'aucun catch(Exception) du projet ne rattrape,
            // et qui avortait AuctionLoader.loadItems() (100 % synchrone, appele nu depuis
            // ZAuctionPlugin.onEnable():160). On appelle desormais le decodeur Bukkit DIRECT.
            return Base64ItemStack.decodeBukkitStream(paramString);
        }
    }

// Imports a ajouter dans ItemStackUtils.java :
//   import java.util.logging.Level;
// (org.bukkit.Bukkit est deja importe l.3)
```

## 6.4 — C-029 — Le format de sérialisation est porté par la DONNÉE, pas par la version du serveur lecteur (phase 1 : lecture)

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/utils/Base64ItemStack.java`  
**Risque** : MEDIUM  
**Constats** : C-029

Je m'écarte de l'audit sur un point que sa fiche ne voit pas : le marqueur ne RESTAURE PAS la lisibilité des lignes NBT historiques (EnumReflectionItemStack.getClassz() fait `split(",")[3]` et explose sur Paper ≥ 1.20.6, les noms obfusqués « b »/« a » ne valent plus rien sur un serveur mojang-mapped). Il permet uniquement de les IDENTIFIER pour les mettre proprement en quarantaine (étape 5) au lieu de les vendre en BARRIER. La lecture bidirectionnelle est le vrai livrable ; l'écriture est un drapeau de configuration parce qu'un déploiement d'un bloc casserait le catalogue pendant un redémarrage tournant.

```java
// ===== FICHIER 1 : API/.../api/utils/Base64ItemStack.java =====
// Ajouter en tete de classe :

    /** Marqueur de format : charge utile serialisee par le flux Bukkit (serveurs >= 1.20.5). */
    static final String MARKER_BUKKIT = "V2:";

    /** Marqueur de format : charge utile serialisee par la voie NMS historique (< 1.20.5). */
    static final String MARKER_NBT = "NBT:";

    /**
     * DEPLOIEMENT EN DEUX PHASES, OBLIGATOIRE.
     * <p>
     * Phase 1 (defaut, {@code false}) : ce jar SAIT lire les deux formats mais continue
     * d'ecrire SANS marqueur, pour qu'un noeud reste en version anterieure puisse encore lire
     * ce qu'il ecrit. Des qu'un noeud ecrit "V2:...", un noeud ancien passe cette chaine a
     * Base64.getDecoder().decode() qui leve IllegalArgumentException.
     * <p>
     * Phase 2 : basculer a {@code true} (config.yml `write-itemstack-format-marker`) UNE FOIS
     * que TOUS les noeuds du reseau sont passes en phase 1. Livrer les deux d'un bloc casse le
     * catalogue pendant un redemarrage tournant.
     */
    private static volatile boolean writeFormatMarker = false;

    /**
     * Enables or disables the writing of the format marker.
     *
     * @param value {@code true} to prefix every encoded payload with its format marker
     */
    public static void setWriteFormatMarker(boolean value) {
        writeFormatMarker = value;
    }

    /**
     * @return {@code true} when the format marker is written
     */
    public static boolean isWriteFormatMarker() {
        return writeFormatMarker;
    }

// Puis remplacer encode()/decode() (les versions posees a l'etape 3) par :

    public static String encode(ItemStack itemStack) {

        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            String payload = ItemStackUtils.serializeItemStack(itemStack);
            if (payload == null) return null;
            return writeFormatMarker ? MARKER_NBT + payload : payload;
        }

        String payload = encodeBukkitStream(itemStack);
        if (payload == null) return null;
        return writeFormatMarker ? MARKER_BUKKIT + payload : payload;
    }

    public static ItemStack decode(String data) {

        if (data == null) return null;

        // Le format est porte par la DONNEE. L'aiguillage d'origine se faisait sur la version
        // du serveur LECTEUR : dans un cluster 1.20.x + 1.21.x, et meme sur un serveur unique
        // mis a jour a travers le seuil 1.20.5, la meme colonne contient deux encodages
        // mutuellement illisibles et l'item devenait un BARRIER vendu au prix du vendeur (C-029).
        if (data.startsWith(MARKER_BUKKIT)) {
            return decodeBukkitStream(data.substring(MARKER_BUKKIT.length()));
        }
        if (data.startsWith(MARKER_NBT)) {
            return ItemStackUtils.safeDeserializeItemStack(data.substring(MARKER_NBT.length()));
        }

        // Lignes historiques, sans marqueur : on retombe sur l'heuristique de version.
        // Un echec rend null, ce que l'etape 5 traduit en QUARANTAINE (item non publie)
        // au lieu d'un BARRIER achetable.
        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            return ItemStackUtils.safeDeserializeItemStack(data);
        }
        return decodeBukkitStream(data);
    }

// ===== FICHIER 2 : src/main/java/fr/maxlego08/zauctionhouse/ZAuctionPlugin.java =====
// Dans onEnable(), juste apres `this.saveFile("config.yml", true);` :

        // Phase 2 du deploiement du marqueur de format (C-029) : a n'activer qu'une fois TOUS
        // les noeuds du reseau passes sur une version qui sait LIRE les marqueurs.
        Base64ItemStack.setWriteFormatMarker(getConfig().getBoolean("write-itemstack-format-marker", false));

// Et dans reload(), juste apres `this.reloadConfig();` :

        Base64ItemStack.setWriteFormatMarker(getConfig().getBoolean("write-itemstack-format-marker", false));

// Import a ajouter : import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
```

## 6.5 — C-001 (+ quarantaine C-011) — Paginer le IN, faire échouer le chargement bruyamment, et ne jamais publier une annonce sans contenu

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/utils/ItemLoaderUtils.java`  
**Risque** : MEDIUM  
**Constats** : C-001, C-011

Cœur du chantier. Je REFUSE le correctif de l'audit qui modifie `Repository.select` lui-même : il changerait la sémantique de 17 sites d'appel d'un coup (boutons d'inventaire, historique, claim), alors que `selectOrFail`/`selectInOrFail` ciblent uniquement les chemins de chargement en masse. La quarantaine est indissociable de la pagination : paginer sans quarantiner laisserait les lignes orphelines DÉJÀ présentes en production s'afficher comme des lots vides achetables. Le garde-fou sur `getPlayerName` n'est dans aucune fiche mais c'est un tueur de démarrage repéré par deux vérificateurs, et il est dans la méthode que je réécris.

```java
// ===== FICHIER 1 : API/.../api/storage/Repository.java (ajouts ADDITIFS) =====

    /**
     * Maximum number of identifiers sent in a single {@code IN (...)} clause.
     * <p>
     * The JDBC drivers cap the number of bound parameters (32 766 for sqlite-jdbc, 65 535 for
     * MySQL/MariaDB). Beyond that the statement is rejected, and the swallowing variants of
     * {@code select} would turn that rejection into an EMPTY list.
     */
    protected static final int IN_CLAUSE_CHUNK_SIZE = 500;

    /**
     * Executes a select query and maps results to the specified class, PROPAGATING any failure.
     * <p>
     * Unlike {@link #select(Class, Consumer)}, which swallows the exception and returns an empty
     * list, this variant rethrows. Use it on every path where "no rows" and "the query failed"
     * would be indistinguishable and where the empty result would publish a broken state.
     *
     * @param clazz    the class to map results to
     * @param consumer the schema configuration for WHERE clauses
     * @param <T>      the result type
     * @return list of mapped objects
     * @throws IllegalStateException if the query failed
     */
    protected <T> List<T> selectOrFail(Class<T> clazz, Consumer<Schema> consumer) {
        Schema schema = SchemaBuilder.select(getTableName());
        consumer.accept(schema);
        try {
            return schema.executeSelect(clazz, this.connection, this.logger);
        } catch (Exception exception) {
            // Le logger de Sarah n'expose que info(String) : on passe par celui du plugin.
            this.plugin.getLogger().severe("select failed on " + getTableName() + ": " + exception.getMessage());
            throw new IllegalStateException("select failed on " + getTableName(), exception);
        }
    }

    /**
     * Executes a select all query and maps results to the specified class, PROPAGATING failures.
     *
     * @param clazz the class to map results to
     * @param <T>   the result type
     * @return list of all mapped objects
     * @throws IllegalStateException if the query failed
     */
    protected <T> List<T> selectAllOrFail(Class<T> clazz) {
        try {
            return SchemaBuilder.select(getTableName()).executeSelect(clazz, this.connection, this.logger);
        } catch (Exception exception) {
            this.plugin.getLogger().severe("selectAll failed on " + getTableName() + ": " + exception.getMessage());
            throw new IllegalStateException("selectAll failed on " + getTableName(), exception);
        }
    }

    /**
     * Runs a paginated {@code IN (...)} select, so the bound-parameter cap can never be reached.
     *
     * @param clazz      the class to map results to
     * @param columnName the column the identifiers belong to
     * @param values     the identifiers, may be empty
     * @param <T>        the result type
     * @return the concatenated results of every chunk
     * @throws IllegalStateException if any chunk failed
     */
    protected <T> List<T> selectInOrFail(Class<T> clazz, String columnName, List<String> values) {
        if (values.isEmpty()) return List.of();

        List<T> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index += IN_CLAUSE_CHUNK_SIZE) {
            // List.copyOf : subList rend une VUE, la lambda ne doit pas capturer une vue.
            List<String> chunk = List.copyOf(values.subList(index, Math.min(index + IN_CLAUSE_CHUNK_SIZE, values.size())));
            result.addAll(selectOrFail(clazz, schema -> schema.whereIn(columnName, chunk)));
        }
        return result;
    }

// ===== FICHIER 2 : .../repositories/AuctionItemRepository.java =====

    /**
     * Loads the content rows of several listings.
     * <p>
     * Au-dela d'environ 32 766 identifiants (SQLite) ou 65 535 (MySQL), le pilote refusait la
     * requete et {@code Repository.select} convertissait l'echec en LISTE VIDE : tous les items
     * du reseau se chargeaient sans aucun contenu et restaient achetables au prix plein (C-001).
     * On pagine, et on propage l'echec : mieux vaut un demarrage avorte qu'un hotel des ventes
     * qui vend du neant.
     */
    public List<AuctionItemDTO> select(List<String> ids) {
        return selectInOrFail(AuctionItemDTO.class, "item_id", ids);
    }

// Import a ajouter : rien (List est deja importe).

// ===== FICHIER 3 : .../repositories/ItemRepository.java =====
// Meme IN non borne, meme liste vide silencieuse (alimente par ZStorageManager.selectItems,
// donc par l'historique admin) :

    public List<ItemDTO> select() {
        return selectOrFail(ItemDTO.class, schema -> schema.where("storage_type", "!=", StorageType.DELETED.name()));
    }

    public List<ItemDTO> select(List<String> ids) {
        return selectInOrFail(ItemDTO.class, "id", ids);
    }

// ===== FICHIER 4 : .../repositories/PlayerRepository.java =====

    public List<PlayerDTO> select() {
        return selectAllOrFail(PlayerDTO.class);
    }

    public List<PlayerDTO> select(List<String> uuids) {
        return selectInOrFail(PlayerDTO.class, "unique_id", uuids);
    }

// ===== FICHIER 5 : src/main/java/fr/maxlego08/zauctionhouse/utils/ItemLoaderUtils.java =====
// Remplacer createAuctionItem (l.39-50) et le corps de la boucle de createItems (l.66-104) :

    /**
     * @return l'item construit, ou {@code null} si l'annonce doit etre mise en QUARANTAINE.
     */
    protected AuctionItem createAuctionItem(AuctionPlugin plugin, ItemDTO dto, String sellerName, List<AuctionItemDTO> currentAuctionItems, AuctionEconomy auctionEconomy) {

        // QUARANTAINE. Une annonce AUCTION sans aucune ligne de contenu, ou dont un seul
        // ItemStack est illisible, ne doit JAMAIS etre publiee : elle s'affichait comme un lot
        // normal, l'acheteur etait debite du prix plein et ne recevait RIEN (C-001 / C-011).
        // La ligne est laissee INTACTE en base : un decode qui echoue peut etre transitoire
        // (cluster de versions Minecraft melangees) et la purger detruirait des items
        // parfaitement valides pour les autres noeuds.
        if (currentAuctionItems.isEmpty()) {
            plugin.getLogger().severe("[Quarantine] Auction item #" + dto.id() + " (seller " + sellerName
                    + ") has NO content row in " + Tables.AUCTION_ITEMS + ": it will NOT be published. "
                    + "The database row is left untouched.");
            return null;
        }

        var itemStacks = new ArrayList<ItemStack>(currentAuctionItems.size());
        for (AuctionItemDTO auctionItemDTO : currentAuctionItems) {
            ItemStack itemStack = Base64ItemStack.decode(auctionItemDTO.itemstack());
            if (itemStack == null) {
                plugin.getLogger().severe("[Quarantine] Auction item #" + dto.id() + " has an unreadable ItemStack ("
                        + Tables.AUCTION_ITEMS + " #" + auctionItemDTO.id() + "): it will NOT be published. "
                        + "The database row is left untouched.");
                return null;
            }
            itemStacks.add(itemStack);
        }

        var auctionItem = new ZAuctionItem(plugin, dto.id(), dto.server_name(), dto.seller_unique_id(), sellerName, dto.price(), auctionEconomy, dto.created_at(), dto.expired_at(), itemStacks);
        auctionItem.setStatus(switch (dto.storage_type()) {
            case LISTED -> ItemStatus.AVAILABLE;
            case PURCHASED -> ItemStatus.PURCHASED;
            case EXPIRED -> ItemStatus.REMOVED;
            case DELETED -> ItemStatus.DELETED;
        });
        return auctionItem;
    }

    protected Result createItems(AuctionPlugin plugin, Map<UUID, String> players, List<ItemDTO> items, PerformanceDebug performanceDebug, BiConsumer<StorageType, Item> biConsumer) {

        var categoryManager = plugin.getCategoryManager();
        var storageManager = plugin.getStorageManager();
        var economyManager = plugin.getEconomyManager();

        long auctionItemsStartTime = performanceDebug.start();
        var auctionItems = storageManager.with(AuctionItemRepository.class).select(getIDS(items, ItemType.AUCTION));
        performanceDebug.end("loadItems.loadAuctionItemsFromDB", auctionItemsStartTime, "count=" + auctionItems.size());

        int amount = 0;
        int quarantined = 0;

        long processStartTime = performanceDebug.start();
        for (ItemDTO dto : items) {

            // getPlayerName levait une IllegalStateException DANS la boucle, sans aucun
            // rattrapage : une seule ligne dont le vendeur manque de la table players faisait
            // echouer TOUT le chargement, donc tout onEnable. Les FK ne sont pas appliquees
            // sous SQLite (Sarah n'emet aucun PRAGMA foreign_keys=ON), l'etat est atteignable.
            var sellerName = players.get(dto.seller_unique_id());
            if (sellerName == null) {
                plugin.getLogger().severe("[Quarantine] Auction item #" + dto.id() + " references an unknown seller "
                        + dto.seller_unique_id() + " (no row in " + Tables.PLAYERS + "): it will NOT be published.");
                quarantined++;
                continue;
            }

            String buyerName = null;
            if (dto.buyer_unique_id() != null) {
                buyerName = players.get(dto.buyer_unique_id());
                if (buyerName == null) {
                    // Un nom d'acheteur manquant n'est PAS une raison de cacher l'item a son
                    // proprietaire : on degrade l'affichage, on ne met pas en quarantaine.
                    plugin.getLogger().warning("Auction item #" + dto.id() + " references an unknown buyer "
                            + dto.buyer_unique_id() + ", displaying it as Unknown.");
                    buyerName = "Unknown";
                }
            }

            var optional = economyManager.getEconomy(dto.economy_name());
            if (optional.isEmpty()) {
                plugin.getLogger().severe("Impossible to find the economy " + dto.economy_name() + " for auction item id " + dto.id() + ", skip it...");
                quarantined++;
                continue;
            }

            switch (dto.item_type()) {
                case AUCTION -> {

                    var currentAuctionItems = getAuctionItems(auctionItems, dto.id());
                    var auctionItem = this.createAuctionItem(plugin, dto, sellerName, currentAuctionItems, optional.get());

                    if (auctionItem == null) {
                        quarantined++;
                        continue;
                    }

                    if (buyerName != null) {
                        auctionItem.setBuyer(dto.buyer_unique_id(), buyerName);
                    }

                    categoryManager.applyCategories(auctionItem);

                    biConsumer.accept(dto.storage_type(), auctionItem);
                }
                case BID -> plugin.getLogger().severe("Bid items not implemented");
                case RENT -> plugin.getLogger().severe("Rent items not implemented");
            }

            amount++;
        }
        performanceDebug.end("loadItems.processItems", processStartTime, "processed=" + amount + ", quarantined=" + quarantined);

        if (quarantined > 0) {
            plugin.getLogger().severe("[Quarantine] " + quarantined + " auction item(s) were NOT published because their "
                    + "data is inconsistent. They are still present in the database: see the SEVERE lines above.");
        }

        return new Result(amount, auctionItems.size(), 0, 0, quarantined);
    }

    public record Result(int amount, int auctionItems, int bidItems, int rentItems, int quarantined) {

    }

// Imports a ajouter dans ItemLoaderUtils.java :
//   import fr.maxlego08.zauctionhouse.api.storage.Tables;
//   import org.bukkit.inventory.ItemStack;
//   import java.util.ArrayList;

// ===== FICHIER 6 : src/main/java/fr/maxlego08/zauctionhouse/storage/ZStorageManager.java =====
// Dans selectItem, brancher la garde de nullite : sans elle, un item mis en quarantaine
// ferait partir la revalidation sous verrou d'achat en NPE a la ligne setBuyer.

                case AUCTION -> {

                    var auctionItems = with(AuctionItemRepository.class).select(List.of(String.valueOf(dto.id())));
                    var auctionItem = createAuctionItem(this.plugin, dto, sellerName, auctionItems, optionalAuctionEconomy.get());

                    // createAuctionItem rend desormais null en cas de quarantaine (C-001).
                    // Cette garde n'est PAS optionnelle : setBuyer partirait en NPE.
                    if (auctionItem == null) return null;

                    if (dto.buyer_unique_id() != null) {
                        auctionItem.setBuyer(dto.buyer_unique_id(), with(PlayerRepository.class).select(dto.buyer_unique_id()));
                    }

                    return auctionItem;
                }

// ===== FICHIER 7 : src/main/java/fr/maxlego08/zauctionhouse/ZAuctionPlugin.java =====
// Le chargement devient fail-closed : on l'entoure explicitement plutot que de laisser
// l'exception traverser onEnable, pour poser un message d'exploitation lisible.
// Remplacer `this.storageManager.loadItems();` (l.160) par :

        try {
            this.storageManager.loadItems();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Failed to load the auction items from the database. The plugin REFUSES "
                    + "to start with a partially loaded auction house: buyers would be charged full price for "
                    + "listings whose content could not be read.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
```

## 6.6 — C-109 — selectItemState : distinguer « l'item a disparu » de « je n'ai pas su le lire », sans casser selectItem

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/storage/StorageManager.java`  
**Risque** : LOW  
**Constats** : C-109

Livre la PRIMITIVE, pas la migration des appelants : PurchaseService:118 et ExpireService:283 sont déjà réécrits par les chantiers QUICK/1/5 sur les mêmes lignes, les migrer ici garantirait un conflit textuel et un état intermédiaire incohérent. Le `default` sur l'interface publiée évite toute rupture pour les implémentations tierces et pour l'addon figé sur `deb8f16`. Les deux `supplyAsync` sans executor sont corrigés ici parce que je réécris la méthode ; si le chantier 8 (C-089) les a déjà livrés, ce hunk disparaît sans conséquence.

```java
// ===== FICHIER 1 : API/.../api/storage/StorageManager.java (ajouts ADDITIFS) =====

    /**
     * Outcome of a single-item lookup.
     */
    enum LookupState {
        /** The row exists and the item could be fully rebuilt. */
        FOUND,
        /** The row does not exist any more (deleted, or never existed): the item is definitively gone. */
        GONE,
        /** The row exists but could NOT be rebuilt: unknown economy, unreadable content, unsupported type. */
        UNAVAILABLE
    }

    /**
     * Result of {@link #selectItemState(int)}.
     *
     * @param state the lookup outcome
     * @param item  the resolved item, non-null only when {@code state} is {@link LookupState#FOUND}
     */
    record ItemLookupResult(LookupState state, Item item) {

        public static ItemLookupResult found(Item item) {
            return new ItemLookupResult(LookupState.FOUND, item);
        }

        public static ItemLookupResult gone() {
            return new ItemLookupResult(LookupState.GONE, null);
        }

        public static ItemLookupResult unavailable() {
            return new ItemLookupResult(LookupState.UNAVAILABLE, null);
        }

        public boolean isFound() {
            return this.state == LookupState.FOUND;
        }
    }

    /**
     * Looks up a single item and reports WHY it could not be resolved.
     * <p>
     * {@link #selectItem(int)} collapses three very different situations into a single
     * {@code null}: the row was deleted, the economy referenced by the row is missing from
     * economies.yml, or the content could not be decoded. Callers then treat all three as
     * "sold elsewhere" and purge the local copy — which silently makes a listing vanish when
     * the real problem is a configuration mistake.
     * <p>
     * The default implementation maps {@code null} to {@link LookupState#GONE}, so third-party
     * implementations of this interface keep compiling and behaving exactly as before.
     *
     * @param id identifier of the item
     * @return future resolving to the lookup outcome
     */
    default CompletableFuture<ItemLookupResult> selectItemState(int id) {
        return selectItem(id).thenApply(item -> item == null ? ItemLookupResult.gone() : ItemLookupResult.found(item));
    }

// Import a ajouter si absent : import fr.maxlego08.zauctionhouse.api.item.Item; (deja present)

// ===== FICHIER 2 : src/main/java/fr/maxlego08/zauctionhouse/storage/ZStorageManager.java =====
// Remplacer selectItem (l.175-211) et findUniqueId (l.213-216) par :

    @Override
    public CompletableFuture<Item> selectItem(int id) {
        // CONTRAT HISTORIQUE CONSERVE : null = "introuvable OU non reconstructible".
        // Les 4 sites d'appel de l'addon Redis (ItemListedListener:31, ItemBoughtListener:96,
        // ItemRemovedListener:50 et :66) sont compiles contre un SHA fige de l'API et en
        // dependent, dont un commentaire deliberé d'ItemBoughtListener:38-40. Tout NOUVEL
        // appelant doit utiliser selectItemState (C-109).
        return selectItemState(id).thenApply(result -> result.isFound() ? result.item() : null);
    }

    @Override
    public CompletableFuture<ItemLookupResult> selectItemState(int id) {
        // supplyAsync SANS executor postait ce JDBC bloquant sur ForkJoinPool.commonPool,
        // deja sature par les appels Jedis du bridge : c'est la cause des faux
        // "Unable to find the item" d'ItemListedListener:34.
        return CompletableFuture.supplyAsync(() -> {

            var optional = with(ItemRepository.class).select(id);
            if (optional.isEmpty()) return ItemLookupResult.gone();

            var dto = optional.get();
            var sellerName = with(PlayerRepository.class).select(dto.seller_unique_id());

            var optionalAuctionEconomy = this.plugin.getEconomyManager().getEconomy(dto.economy_name());
            if (optionalAuctionEconomy.isEmpty()) {
                this.plugin.getLogger().severe("Impossible to find the economy " + dto.economy_name()
                        + " for auction item id " + dto.id() + ". The item is NOT gone, it is UNREADABLE: "
                        + "check economies.yml before assuming the listing disappeared.");
                return ItemLookupResult.unavailable();
            }

            switch (dto.item_type()) {
                case AUCTION -> {

                    var auctionItems = with(AuctionItemRepository.class).select(List.of(String.valueOf(dto.id())));
                    var auctionItem = createAuctionItem(this.plugin, dto, sellerName, auctionItems, optionalAuctionEconomy.get());

                    if (auctionItem == null) return ItemLookupResult.unavailable();

                    if (dto.buyer_unique_id() != null) {
                        auctionItem.setBuyer(dto.buyer_unique_id(), with(PlayerRepository.class).select(dto.buyer_unique_id()));
                    }

                    return ItemLookupResult.found(auctionItem);
                }
                case BID, RENT -> {
                    this.plugin.getLogger().severe("Item type " + dto.item_type() + " is not implemented (item id " + dto.id() + ")");
                    return ItemLookupResult.unavailable();
                }
            }
            return ItemLookupResult.unavailable();
        }, this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<UUID> findUniqueId(String playerName) {
        return CompletableFuture.supplyAsync(() -> this.with(PlayerRepository.class).selectByName(playerName), this.plugin.getExecutorService());
    }

// Import a ajouter dans ZStorageManager : rien (StorageManager est deja importe, les types
// imbriques sont accessibles par heritage de l'interface).
```

## 6.7 — C-044 — loadItems() devient une vraie réinitialisation, et /ah admin migrate refuse le rechargement à chaud avec des joueurs connectés

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/storage/AuctionLoader.java`  
**Risque** : MEDIUM  
**Constats** : C-044

La purge seule rend le rechargement cohérent mais pas SÛR ; c'est pourquoi le garde-fou « aucun joueur connecté » part dans le même commit — sans lui, on remplacerait une corruption silencieuse par une purge brutale sous les pieds des joueurs. Je n'implémente PAS les deux autres recommandations de l'audit : « invalider sortedItemsCache » est déjà fait deux fois (AuctionLoader:57 et ZAuctionManager:243), et « corriger addToIndex » porte sur du code mort (les trois index par propriétaire ne sont jamais lus). Le message réutilise MIGRATION_FAILED : aucune clé nouvelle.

```java
// ===== FICHIER 1 : src/main/java/fr/maxlego08/zauctionhouse/storage/AuctionLoader.java =====

    public void loadItems() {
        long totalStartTime = performanceDebug.start();

        // REINITIALISATION AVANT RECHARGEMENT. loadItems() n'est pas appele qu'au demarrage :
        // CommandAuctionAdminMigrate:92 l'invoque A CHAUD apres une migration. Le chargement
        // etant purement ADDITIF (createItems -> auctionManager::addItem, sans jamais vider les
        // storages), un second appel remettait tous les statuts a AVAILABLE, ne purgeait aucun
        // fantome et laissait les index par proprietaire pousser indefiniment (C-044).
        // Au demarrage le comportement est strictement inchange : les storages y sont vides.
        this.resetStorages();

        // Load players
        long playersStartTime = performanceDebug.start();
        var players = this.storageManager.with(PlayerRepository.class).select().stream().collect(Collectors.toMap(PlayerDTO::unique_id, PlayerDTO::name));
        performanceDebug.end("loadItems.loadPlayers", playersStartTime, "count=" + players.size());
        this.plugin.getLogger().info("Loaded " + players.size() + " players successfully");

        var categoryManager = this.plugin.getCategoryManager();

        // Load items from database
        long itemsStartTime = performanceDebug.start();
        var items = this.storageManager.with(ItemRepository.class).select();
        performanceDebug.end("loadItems.loadItemsFromDB", itemsStartTime, "count=" + items.size());

        var result = this.createItems(this.plugin, players, items, performanceDebug, auctionManager::addItem);

        performanceDebug.end("loadItems.total", totalStartTime, "players=" + players.size() + ", items=" + result.amount() + ", auctionItems=" + result.auctionItems());
        this.logger.info("Loaded " + result.amount() + " items successfully (" + result.auctionItems() + " total)");

        // Rebuild the sorted items cache after bulk loading
        long cacheStartTime = performanceDebug.start();
        auctionManager.rebuildSortedItemsCache();
        performanceDebug.end("loadItems.rebuildSortedItemsCache", cacheStartTime, "scheduled async rebuild");
    }

    /**
     * Vide les storages memoire et les caches joueur lies aux items.
     * <p>
     * N'utilise QUE des methodes deja publiees par {@link AuctionManager}
     * ({@code getItems}, {@code removeItem}, {@code clearPlayersCache}) : aucun changement d'API.
     * {@code getItems(StorageType)} rend une copie ({@code new ArrayList<>(...)}), l'iteration
     * pendant la suppression est donc sure.
     */
    private void resetStorages() {
        int removed = 0;
        for (StorageType storageType : StorageType.values()) {
            for (Item item : this.auctionManager.getItems(storageType)) {
                this.auctionManager.removeItem(storageType, item.getId());
                removed++;
            }
        }

        if (removed == 0) return;

        // On NE purge PAS les cles SELL_* : un joueur en cours de mise en vente perdrait son
        // panier. ITEM_SHOW et CURRENT_PAGE sont purges volontairement : ils referencent des
        // objets Item qui viennent d'etre detruits.
        this.auctionManager.clearPlayersCache(
                PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED,
                PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH,
                PlayerCacheKey.ITEM_SHOW, PlayerCacheKey.CURRENT_PAGE);

        this.logger.warning("Cleared " + removed + " in-memory item(s) before reloading them from the database");
    }

// Imports a ajouter dans AuctionLoader.java :
//   import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
//   import fr.maxlego08.zauctionhouse.api.item.Item;
//   import fr.maxlego08.zauctionhouse.api.item.StorageType;

// ===== FICHIER 2 : .../command/commands/admin/CommandAuctionAdminMigrate.java =====
// Juste AVANT `// Start migration` (l.73) :

        // La purge/reconstruction des storages memoire ne rend le rechargement a chaud COHERENT
        // que si personne n'agit pendant : les futures d'achat et de retrait deja en vol
        // detiennent des references vers les objets purges et continueraient de muter des
        // orphelins. On refuse donc la migration tant qu'un joueur est connecte (C-044).
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        if (onlinePlayers > 0) {
            message(plugin, sender, Message.MIGRATION_FAILED, "%error%",
                    onlinePlayers + " player(s) are connected. Run the migration on an empty server.");
            return CommandType.SUCCESS;
        }
```

## 6.8 — C-108 — La migration V3 n'écrase plus jamais un pseudo V4, et un vrai pseudo l'emporte toujours sur « Unknown »

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/migration/v3/V3MigrationService.java`  
**Risque** : LOW  
**Constats** : C-108

Doit précéder toute ré-exécution de la migration (étapes 9 à 11). `playerRepo.select()` charge toute la table players une fois — c'est exactement ce que `AuctionLoader.loadItems()` fait déjà à chaque boot, donc aucun surcoût nouveau. Effet voulu : un pseudo V3 plus récent qu'un pseudo V4 ne sera plus appliqué, V4 est la source de vérité et se resynchronise à la prochaine connexion.

```java
// Constante de classe :

    private static final String UNKNOWN_PLAYER_NAME = "Unknown";

// Remplacer collectPlayers (l.176-198) et migratePlayers (l.203-218) par :

    /**
     * Collects all unique players from items and transactions.
     */
    private Map<UUID, String> collectPlayers(List<V3AuctionItem> items, List<V3Transaction> transactions) {
        Map<UUID, String> players = new HashMap<>();

        for (V3AuctionItem item : items) {
            trackPlayer(players, item.getSeller(), item.getSellerName());
            trackPlayer(players, item.getBuyer(), null);
        }

        for (V3Transaction transaction : transactions) {
            trackPlayer(players, transaction.getSeller(), null);
            trackPlayer(players, transaction.getBuyer(), null);
        }

        return players;
    }

    /**
     * Un vrai pseudo l'emporte TOUJOURS sur le placeholder, quel que soit l'ordre de parcours.
     * <p>
     * Avec {@code putIfAbsent}, un joueur rencontre d'abord comme ACHETEUR figeait "Unknown"
     * alors qu'on connaissait son pseudo en tant que vendeur un peu plus loin dans la liste.
     * Les hooks freres le font deja correctement (CrazyAuctionsMigrationService.trackPlayer).
     */
    private void trackPlayer(Map<UUID, String> players, UUID uniqueId, String name) {
        if (uniqueId == null) return;

        String resolved = (name != null && !name.isBlank()) ? name : UNKNOWN_PLAYER_NAME;
        String previous = players.get(uniqueId);

        if (previous == null || (UNKNOWN_PLAYER_NAME.equals(previous) && !UNKNOWN_PLAYER_NAME.equals(resolved))) {
            players.put(uniqueId, resolved);
        }
    }

    /**
     * Migrates players to V4 database.
     */
    private int migratePlayers(Map<UUID, String> players, AtomicInteger errors) {
        int migrated = 0;
        PlayerRepository playerRepo = plugin.getStorageManager().with(PlayerRepository.class);

        // Le pseudo V4 vient de PlayerListener.onConnect : il est TOUJOURS plus fiable que le
        // placeholder de la migration. upsertPlayer ecrasait la ligne existante et remplacait
        // le pseudo reel d'un joueur actif par "Unknown" (C-108).
        //
        // ATTENTION : je REJETTE le correctif propose par l'audit
        // (`if ("Unknown".equals(entry.getValue())) continue;`). Sauter l'INSERTION d'un UUID
        // inconnu casse deux invariants verifies : items.buyer_unique_id porte une FK vers
        // players (CreateItemMigration:15), donc sous MySQL l'item entier est rejete ; et
        // ItemLoaderUtils met desormais l'item en quarantaine si son vendeur manque de la table.
        // Les UUID inconnus DOIVENT donc etre inseres, meme sous "Unknown".
        Set<UUID> knownPlayers = playerRepo.select().stream().map(PlayerDTO::unique_id).collect(Collectors.toSet());

        for (Map.Entry<UUID, String> entry : players.entrySet()) {

            if (knownPlayers.contains(entry.getKey())) continue;

            try {
                playerRepo.upsertPlayer(entry.getKey(), entry.getValue());
                migrated++;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate player " + entry.getKey() + ": " + e.getMessage());
                errors.incrementAndGet();
            }
        }

        return migrated;
    }

// Imports a ajouter :
//   import fr.maxlego08.zauctionhouse.api.storage.dto.PlayerDTO;
//   import java.util.stream.Collectors;
// (java.util.* couvre deja Set/Map/HashMap/UUID/List)
```

## 6.9 — C-075 — Supprimer les catch morts de la migration V3 et compenser l'annonce orpheline au lieu de la laisser vendable

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/migration/v3/V3MigrationService.java`  
**Risque** : MEDIUM  
**Constats** : C-075

Je fais l'INVERSE du correctif n°3 de l'audit (« élargir le catch à Exception ») : élargir le catch ferait continuer la boucle, `migrated++` s'exécuterait et `errors` resterait à 0 — on passerait d'un lot amputé signalé à un lot amputé compté comme migré. Je supprime les catch pour laisser remonter, et je compense. Je n'utilise PAS `DatabaseConnection.beginTransaction()` : la méthode existe dans Sarah mais n'est utilisée nulle part dans le projet, l'introduire ici en ferait le premier usage non testé sur un chemin admin.

```java
// Helper de connexion (evite `with(PlayerRepository.class).getConnection()` dans 5 methodes) :

    private fr.maxlego08.sarah.DatabaseConnection connection() {
        return plugin.getStorageManager().getDatabaseConnection();
    }

// Remplacer migrateItems / createItem / createAuctionItems / insertAuctionItem (l.223-310) par :

    /**
     * Migrates auction items to V4 database.
     */
    private int migrateItems(List<V3AuctionItem> items, AtomicInteger errors) {
        int migrated = 0;

        for (V3AuctionItem v3Item : items) {

            int itemId = -1;
            try {
                // V3SqlDataReader.parseItem ne controle NI le vendeur NI l'itemstack : une seule
                // ligne V3 corrompue produisait soit une NPE dans SchemaBuilder.uuid (value.toString()),
                // soit un INSERT enfant avec un itemstack null, donc une annonce vide et VENDABLE.
                if (v3Item.getSeller() == null) {
                    plugin.getLogger().warning("Skipping V3 item " + v3Item.getId() + ": no seller");
                    errors.incrementAndGet();
                    continue;
                }
                if (v3Item.getItemstack() == null || v3Item.getItemstack().isBlank()) {
                    plugin.getLogger().warning("Skipping V3 item " + v3Item.getId() + ": empty itemstack payload");
                    errors.incrementAndGet();
                    continue;
                }

                itemId = createItem(v3Item);

                // InsertRequest rend 0 (et non -1) quand le pilote ne remonte aucune cle generee :
                // la garde `itemId == -1` etait morte deux fois, le catch qui rendait -1 l'etant
                // lui aussi (Sarah leve une DatabaseException runtime, jamais une SQLException).
                if (itemId <= 0) {
                    plugin.getLogger().warning("Failed to create the parent row for V3 item " + v3Item.getId());
                    errors.incrementAndGet();
                    continue;
                }

                createAuctionItems(itemId, v3Item);

                migrated++;

                if (migrated % 100 == 0) {
                    progress("Migrated " + migrated + "/" + items.size() + " items...");
                }
            } catch (Exception e) {
                // COMPENSATION. La ligne parente est deja commitee : sans ce DELETE, l'annonce
                // reste VENDABLE au prix plein avec un lot ampute ou vide (C-075). La FK
                // auction_items.item_id est ON DELETE CASCADE, les contenus deja inseres partent
                // avec la ligne parente.
                errors.incrementAndGet();
                plugin.getLogger().severe("Failed to migrate V3 item " + v3Item.getId() + " (V4 id " + itemId + "): " + e.getMessage());
                if (itemId > 0) {
                    deleteOrphanItem(itemId);
                }
            }
        }

        return migrated;
    }

    /**
     * Supprime la ligne %prefix%items orpheline laissee par un lot partiellement insere.
     */
    private void deleteOrphanItem(int itemId) {
        try {
            SchemaBuilder.delete(Tables.ITEMS).where("id", itemId).execute(connection(), logger);
            plugin.getLogger().warning("Rolled back the orphan V4 item row #" + itemId);
        } catch (Exception e) {
            // Trace exploitable : sans l'id V4 exact, l'exploitant n'a AUCUN moyen de retrouver
            // l'annonce a supprimer a la main.
            plugin.getLogger().severe("MANUAL ACTION REQUIRED: the orphan V4 item row #" + itemId
                    + " could not be deleted (" + e.getMessage() + "). It is currently listed for sale "
                    + "with a truncated or empty content.");
        }
    }

    /**
     * Creates an item in the V4 ITEMS table.
     * <p>
     * Le {@code catch (SQLException)} d'origine etait du CODE MORT : Sarah leve une
     * {@code DatabaseException extends SarahException extends RuntimeException}. L'echec
     * remontait donc quand meme, mais au parent, apres avoir fait croire au contraire.
     */
    private int createItem(V3AuctionItem v3Item) throws SQLException {
        Schema schema = SchemaBuilder.insert(Tables.ITEMS, s -> {
            s.string("item_type", ItemType.AUCTION.name());
            s.uuid("seller_unique_id", v3Item.getSeller());
            if (v3Item.getBuyer() != null) {
                s.uuid("buyer_unique_id", v3Item.getBuyer());
            }
            s.decimal("price", BigDecimal.valueOf(v3Item.getPrice()));
            s.string("economy_name", v3Item.getEconomy() != null ? v3Item.getEconomy() : "vault");
            s.string("storage_type", v3Item.getStorageType().toV4StorageType().name());
            s.string("server_name", v3Item.getServerName() != null ? v3Item.getServerName() : plugin.getConfiguration().getServerName());
            s.object("expired_at", new Date(v3Item.getExpireAt()));
        });

        return schema.execute(connection(), logger);
    }

    /**
     * Creates auction item entries in the V4 AUCTION_ITEMS table.
     * Handles both single items and multi-item (INVENTORY type) items.
     */
    private void createAuctionItems(int itemId, V3AuctionItem v3Item) throws SQLException {
        String itemstack = v3Item.getItemstack();
        int inserted = 0;

        if (v3Item.isInventoryType() && itemstack.contains(";")) {
            // Multi-item: split by semicolon
            for (String stack : itemstack.split(";")) {
                if (!stack.trim().isEmpty()) {
                    insertAuctionItem(itemId, stack.trim());
                    inserted++;
                }
            }
        } else {
            // Single item
            insertAuctionItem(itemId, itemstack);
            inserted++;
        }

        // Un lot V3 dont la charge utile vaut exactement ";" produit `";".split(";")` = tableau
        // VIDE : ZERO contenu insere, ZERO exception, item compte comme migre, annonce vendable
        // au prix plein pour un lot vide. C'est le declencheur le plus certain de C-075.
        if (inserted == 0) {
            throw new IllegalStateException("no readable ItemStack in the V3 payload for item_id " + itemId);
        }
    }

    private void insertAuctionItem(int itemId, String itemstack) throws SQLException {
        if (itemstack == null || itemstack.isBlank()) {
            throw new IllegalStateException("empty itemstack payload for item_id " + itemId);
        }

        SchemaBuilder.insert(Tables.AUCTION_ITEMS, s -> {
            s.object("item_id", itemId);
            s.string("itemstack", itemstack);
        }).execute(connection(), logger);
    }
```

## 6.10 — C-033 — Ligne sentinelle au lieu de item_id = 0 : l'argent PENDING de la V3 cesse d'être détruit sous MySQL

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/migration/v3/V3MigrationService.java`  
**Risque** : MEDIUM  
**Constats** : C-033

Le vecteur dépend du backend, et c'est ce qui l'a rendu invisible : sous MySQL les FK sont émises et l'argent est détruit ; sous SQLite (le défaut) Sarah n'émet aucun `PRAGMA foreign_keys=ON`, l'insert passe et rejouer la commande DOUBLE l'argent (d'où l'étape 11). La ligne sentinelle évite tout changement de schéma et fonctionne à l'identique sur les deux backends. J'inclus le jumeau ZelAuction parce que laisser un défaut identique à trois fichiers de distance serait une négligence.

```java
// ===== FICHIER 1 : .../migration/v3/V3MigrationService.java =====
// Remplacer migrateTransactions / createLogEntry / createPendingTransaction (l.315-388) par :

    /**
     * Migrates V3 transactions to V4 logs and transactions tables.
     */
    private int migrateTransactions(List<V3Transaction> transactions, AtomicInteger errors) {
        int migrated = 0;

        for (V3Transaction v3Trans : transactions) {
            try {
                if (v3Trans.getSeller() == null || v3Trans.getBuyer() == null) {
                    // SchemaBuilder.uuid fait value.toString() : un UUID null partait en NPE.
                    plugin.getLogger().warning("Skipping V3 transaction " + v3Trans.getId() + ": missing seller or buyer");
                    errors.incrementAndGet();
                    continue;
                }

                // item_id = 0 violait la cle etrangere logs/transactions -> items sous MySQL
                // (CreateLogsMigration:12 et CreateTransactionsMigration:12 declarent bien la FK,
                // et SchemaBuilder l'emet en InnoDB) : l'insert levait une DatabaseException que
                // le catch(SQLException) ne voyait pas, et TOUT l'argent PENDING V3 etait perdu.
                // Sous SQLite, ou Sarah n'emet aucun PRAGMA foreign_keys=ON, l'insert passait :
                // d'ou une bascule de comportement selon le backend, invisible a l'admin (C-033).
                //
                // On cree une vraie ligne %prefix%items en storage_type DELETED : jamais chargee
                // (ItemRepository.select et select(int) filtrent DELETED), mais elle satisfait la FK.
                // Je REJETTE le correctif de l'audit (rendre item_id nullable) : MigrationManager
                // de Sarah ne sait qu'AJOUTER des colonnes manquantes, il n'emet jamais
                // d'ALTER ... MODIFY, le correctif serait inoperant sur les installations existantes.
                int sentinelItemId = createSentinelItem(v3Trans);
                if (sentinelItemId <= 0) {
                    plugin.getLogger().severe("Failed to create the sentinel row for V3 transaction " + v3Trans.getId() + ", skipping it");
                    errors.incrementAndGet();
                    continue;
                }

                // L'ARGENT D'ABORD. L'ordre d'origine ecrivait l'historique en premier : un
                // echec de log faisait sauter le `continue` implicite et les gains en attente
                // du vendeur n'etaient jamais importes. Chacun a desormais son propre try/catch.
                if (v3Trans.isNeedMoney()) {
                    try {
                        createPendingTransaction(sentinelItemId, v3Trans);
                    } catch (Exception e) {
                        plugin.getLogger().severe("MONEY LOST: failed to import the pending money of V3 transaction "
                                + v3Trans.getId() + " (seller " + v3Trans.getSeller() + ", amount " + v3Trans.getPrice()
                                + "): " + e.getMessage());
                        errors.incrementAndGet();
                    }
                }

                try {
                    createLogEntry(sentinelItemId, v3Trans);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to import the history of V3 transaction " + v3Trans.getId() + ": " + e.getMessage());
                    errors.incrementAndGet();
                }

                migrated++;

                if (migrated % 100 == 0) {
                    progress("Migrated " + migrated + "/" + transactions.size() + " transactions...");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate transaction " + v3Trans.getId() + ": " + e.getMessage());
                errors.incrementAndGet();
            }
        }

        return migrated;
    }

    /**
     * Cree la ligne %prefix%items porteuse des FK de logs/transactions pour une transaction V3.
     * <p>
     * La V3 n'a aucun item correspondant : la ligne est ecrite directement en
     * {@link StorageType#DELETED}, donc invisible en jeu, mais reelle pour la base.
     * La FK etant ON DELETE CASCADE, purger ces lignes effacerait aussi les logs et
     * transactions associes -- c'est le comportement voulu.
     */
    private int createSentinelItem(V3Transaction v3Trans) throws SQLException {
        return SchemaBuilder.insert(Tables.ITEMS, s -> {
            s.string("item_type", ItemType.AUCTION.name());
            s.uuid("seller_unique_id", v3Trans.getSeller());
            s.uuid("buyer_unique_id", v3Trans.getBuyer());
            s.decimal("price", BigDecimal.valueOf(v3Trans.getPrice()));
            s.string("economy_name", v3Trans.getEconomy() != null ? v3Trans.getEconomy() : "vault");
            s.string("storage_type", StorageType.DELETED.name());
            s.string("server_name", plugin.getConfiguration().getServerName());
            s.object("expired_at", new Date(v3Trans.getTransactionDate()));
            s.object("created_at", new Date(v3Trans.getTransactionDate()));
        }).execute(connection(), logger);
    }

    /**
     * Creates a log entry for a V3 transaction.
     */
    private void createLogEntry(int itemId, V3Transaction v3Trans) throws SQLException {
        SchemaBuilder.insert(Tables.LOGS, s -> {
            s.string("log_type", LogType.PURCHASE.name());
            s.object("item_id", itemId);
            s.uuid("player_unique_id", v3Trans.getBuyer());
            s.uuid("target_unique_id", v3Trans.getSeller());
            s.string("itemstack", v3Trans.getItemstack());
            s.decimal("price", BigDecimal.valueOf(v3Trans.getPrice()));
            s.string("economy_name", v3Trans.getEconomy() != null ? v3Trans.getEconomy() : "vault");
            s.string("additional_data", "migrated_from_v3");
            if (v3Trans.isRead()) {
                s.object("readed_at", new Date(v3Trans.getTransactionDate()));
            }
            s.object("created_at", new Date(v3Trans.getTransactionDate()));
        }).execute(connection(), logger);
    }

    /**
     * Creates a pending transaction entry for unclaimed money.
     */
    private void createPendingTransaction(int itemId, V3Transaction v3Trans) throws SQLException {
        SchemaBuilder.insert(Tables.TRANSACTIONS, s -> {
            s.object("item_id", itemId);
            s.uuid("player_unique_id", v3Trans.getSeller());
            s.string("economy_name", v3Trans.getEconomy() != null ? v3Trans.getEconomy() : "vault");
            s.decimal("before", BigDecimal.ZERO);
            s.decimal("after", BigDecimal.ZERO);
            s.decimal("value", BigDecimal.valueOf(v3Trans.getPrice()));
            s.string("status", TransactionStatus.PENDING.name());
            s.object("created_at", new Date(v3Trans.getTransactionDate()));
        }).execute(connection(), logger);
    }

// Import a ajouter : import fr.maxlego08.zauctionhouse.api.item.StorageType;

// ===== FICHIER 2 : Hooks/ZelAuction/.../ZelAuctionMigrationService.java =====
// MEME DEFAUT, AUCUNE FICHE NE LE COUVRE : `s.object("item_id", 0)` avec le meme
// catch(SQLException) mort. Sous MySQL, tout l'historique ZelAuction est perdu en silence.
// Appliquer le meme patron : createSentinelItem local puis

    private void createLogEntry(DatabaseConnection v4Connection, UUID playerUuid, String itemstack, double price, LogType logType, long date) throws SQLException {
        int sentinelItemId = createSentinelItem(v4Connection, playerUuid, price, date);
        if (sentinelItemId <= 0) {
            throw new IllegalStateException("unable to create the sentinel item row for the ZelAuction log");
        }
        SchemaBuilder.insert(Tables.LOGS, s -> {
            s.string("log_type", logType.name());
            s.object("item_id", sentinelItemId);
            s.uuid("player_unique_id", playerUuid);
            s.string("itemstack", itemstack);
            s.decimal("price", BigDecimal.valueOf(price));
            s.string("economy_name", "vault");
            s.string("additional_data", "migrated_from_zelauction");
            s.object("created_at", new Date(date));
        }).execute(v4Connection, logger);
    }

    private int createSentinelItem(DatabaseConnection v4Connection, UUID sellerUuid, double price, long date) throws SQLException {
        return SchemaBuilder.insert(Tables.ITEMS, s -> {
            s.string("item_type", ItemType.AUCTION.name());
            s.uuid("seller_unique_id", sellerUuid);
            s.decimal("price", BigDecimal.valueOf(price));
            s.string("economy_name", "vault");
            s.string("storage_type", StorageType.DELETED.name());
            s.string("server_name", plugin.getConfiguration().getServerName());
            s.object("expired_at", new Date(date));
            s.object("created_at", new Date(date));
        }).execute(v4Connection, logger);
    }
// (et propager `throws SQLException` / un try-catch au site d'appel de createLogEntry)
```

## 6.11 — C-034 — Sentinelle d'idempotence en base : la migration n'est plus rejouable sans un `force` explicite

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/storage/repository/repositories/MigrationStateRepository.java`  
**Risque** : MEDIUM  
**Constats** : C-034

Ne JAMAIS livrer cette garde avant l'étape 10 : sur MySQL les serveurs déjà migrés n'ont aucune transaction PENDING ; si la sentinelle était posée alors que le bug item_id = 0 est encore présent, l'admin serait bloqué et l'argent V3 perdu définitivement. D'où la position en dernier de la série migration, et le drapeau `force` documenté. La sentinelle ne désamorce pas deux commandes lancées à la même seconde sur deux nœuds (le premier à committer gagne) : une vraie exclusion demanderait un verrou distribué que AuctionClusterBridge n'expose pas.

```java
// ===== FICHIER 1 : API/.../api/storage/Tables.java =====

    String MIGRATION_STATE = "%prefix%migration_state";

// ===== FICHIER 2 (NOUVEAU) : .../storage/migrations/CreateMigrationStateMigration.java =====
package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

/**
 * Trace des migrations de donnees deja executees.
 * <p>
 * La sentinelle vit en base PARTAGEE et non en memoire : c'est la seule facon de bloquer aussi
 * une seconde execution lancee depuis un AUTRE noeud du reseau.
 */
public class CreateMigrationStateMigration extends Migration {

    @Override
    public void up() {
        create(Tables.MIGRATION_STATE, table -> {
            table.string("provider_id", 64).primary();
            table.string("server_name", 255);
            table.bigInt("migrated_at");
            table.integer("players_imported");
            table.integer("items_imported");
            table.integer("transactions_imported");
        });
    }
}

// ===== FICHIER 3 (NOUVEAU) : .../repositories/MigrationStateRepository.java =====
package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

import java.util.Optional;

public class MigrationStateRepository extends Repository {

    public MigrationStateRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.MIGRATION_STATE);
    }

    public Optional<MigrationStateDTO> select(String providerId) {
        return select(MigrationStateDTO.class, schema -> schema.where("provider_id", providerId)).stream().findFirst();
    }

    /**
     * Marque un provider comme deja migre.
     * <p>
     * N'est appele QU'EN CAS DE SUCCES : une sentinelle ecrite apres un echec bloquerait
     * definitivement une reprise, et l'argent V3 resterait perdu pour toujours.
     */
    public void markMigrated(String providerId, String serverName, int players, int items, int transactions) {
        upsert(schema -> {
            schema.string("provider_id", providerId).primary();
            schema.string("server_name", serverName);
            schema.bigInt("migrated_at", System.currentTimeMillis());
            schema.object("players_imported", players);
            schema.object("items_imported", items);
            schema.object("transactions_imported", transactions);
        });
    }

    public record MigrationStateDTO(String provider_id, String server_name, long migrated_at,
                                    int players_imported, int items_imported, int transactions_imported) {
    }
}

// ===== FICHIER 4 : .../storage/ZStorageManager.java =====
// Enregistrer la migration EN DERNIER (l.71) pour ne pas perturber l'ordre historique
// deja applique sur les serveurs existants, et le repository (l.79) :

        MigrationManager.registerMigration(new CreateMigrationStateMigration());
        ...
        this.repositories.register(MigrationStateRepository.class);

// ===== FICHIER 5 : API/.../api/messages/Message.java =====
// Ajout ADDITIF a l'enum (aucun switch exhaustif sur Message n'existe : non cassant) :

    MIGRATION_ALREADY_DONE("<error>Migration from <white>%source%<error> has already been run on <white>%server%<error> (<white>%date%<error>, <white>%items%<error> items).",
            "<gray>Running it again would duplicate every item and every pending payment.",
            "<gray>If you really know what you are doing: <white>/ah admin migrate %source% confirm force"),

// ===== FICHIER 6 : .../command/commands/admin/CommandAuctionAdminMigrate.java =====
// Constante + argument :

    private static final String FORCE_ARG = "force";

// Dans le constructeur, apres addOptionalArg("confirm", ...) :
        this.addOptionalArg("force", (sender, args) -> List.of("force"));

// Dans perform(), lecture de l'argument :
        String forceArg = this.argAsString(2, "");

// Juste APRES le bloc de confirmation (l.66-71) et AVANT la garde "joueurs connectes" :

        // IDEMPOTENCE. La migration n'a AUCUNE cle d'unicite cote V4 : la rejouer duplique tous
        // les items ET tout l'argent PENDING (C-034). La sentinelle est en base partagee, elle
        // bloque donc aussi le scenario deux-noeuds, ce qu'un drapeau memoire ne peut pas faire.
        boolean force = forceArg.equalsIgnoreCase(FORCE_ARG);
        var migrationState = plugin.getStorageManager().with(MigrationStateRepository.class).select(provider.getId());
        if (migrationState.isPresent() && !force) {
            var state = migrationState.get();
            message(plugin, sender, Message.MIGRATION_ALREADY_DONE,
                    "%source%", provider.getDisplayName(),
                    "%server%", state.server_name(),
                    "%date%", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(state.migrated_at())),
                    "%items%", String.valueOf(state.items_imported()));
            return CommandType.SUCCESS;
        }

// Dans le thenAccept, AVANT le runNextTick (donc hors thread principal) :

        migrationFuture.thenAccept(result -> {

            // Ecriture de la sentinelle UNIQUEMENT en cas de succes.
            if (result.isSuccess()) {
                try {
                    plugin.getStorageManager().with(MigrationStateRepository.class).markMigrated(
                            provider.getId(), plugin.getConfiguration().getServerName(),
                            result.getPlayersImported(), result.getItemsImported(), result.getTransactionsImported());
                } catch (Exception exception) {
                    plugin.getLogger().severe("Migration succeeded but the idempotency sentinel could NOT be written: "
                            + exception.getMessage() + ". Running the command again would duplicate everything.");
                }
            }

            plugin.getScheduler().runNextTick(wrappedTask -> {
                ... // inchange
            });
        })

// Import a ajouter : import fr.maxlego08.zauctionhouse.storage.repository.repositories.MigrationStateRepository;
```

## 6.12 — C-113 + C-016 (addon) — Garde d'entrée sur un plugin principal désactivé, et UUID de serveur écrit de façon atomique

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/ZAuctionHouseRedis.java`  
**Risque** : LOW  
**Constats** : C-113, C-016

Première étape addon parce que tout le reste en dépend : la garde `isEnabled` doit être en TÊTE de onEnable (l'audit la plaçait après loadServerUUID, ce qui fausse le test dès qu'un disablePlugin réentrant est en jeu). L'écriture atomique supprime la cause de production de fichiers tronqués, et le traitement du fichier vide comme un fichier absent évite de transformer un incident bénin en refus de démarrage.

```java
// ===== FICHIER 1 : REDIS/.../ZAuctionHouseRedis.java =====
// Remplacer le DEBUT de onEnable (l.55-67) par :

    @Override
    public void onEnable() {

        // GARDE D'ENTREE, EN TOUTE PREMIERE POSITION. `depend: zAuctionHouse` ne garantit que
        // l'ORDRE de chargement, jamais le SUCCES : si zAuctionHouse s'est desactive lui-meme
        // (echec de sa connexion base, ZStorageManager:55), il est toujours "charge" mais tous
        // ses managers sont a moitie construits et l'addon plante en cascade (C-113).
        var pluginManager = getServer().getPluginManager();
        this.auctionPlugin = (AuctionPlugin) pluginManager.getPlugin("zAuctionHouse");
        if (this.auctionPlugin == null) {
            getLogger().severe("zAuctionHouse is not loaded. Disabling plugin.");
            pluginManager.disablePlugin(this);
            return;
        }

        // AuctionPlugin extends Plugin (API/AuctionPlugin.java:28) : aucun cast n'est necessaire,
        // contrairement a ce que propose l'audit.
        if (!this.auctionPlugin.isEnabled()) {
            getLogger().severe("zAuctionHouse is loaded but DISABLED. This is almost always a database");
            getLogger().severe("failure on ITS side, not a problem with this addon: read its own log above.");
            getLogger().severe("Fix zAuctionHouse first, then restart. Disabling plugin.");
            pluginManager.disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        this.enableDebug = getConfig().getBoolean("debug");

        if (!this.loadServerUUID()) {
            return;
        }

        this.gson = getGsonBuilder().create();
        ... // suite inchangee pour l'instant (reordonnee a l'etape 15)
    }

// Remplacer loadServerUUID (l.144-167) et saveServerUUID (l.169-176) par :

    /**
     * @return {@code false} si le noeud ne doit pas demarrer, auquel cas le plugin s'est deja
     * desactive lui-meme.
     */
    private boolean loadServerUUID() {
        File serverInfoFile = new File(getDataFolder(), "server.info");

        String uuidString = null;
        if (serverInfoFile.exists()) {
            try {
                uuidString = new String(Files.readAllBytes(serverInfoFile.toPath()), StandardCharsets.UTF_8).trim();
            } catch (IOException exception) {
                getLogger().severe("Failed to read server.info: " + exception.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return false;
            }
        }

        // Un fichier VIDE ou tronque est traite comme un fichier ABSENT (regeneration) et non
        // comme une erreur fatale : c'est exactement l'etat que produit l'ancienne ecriture non
        // atomique interrompue (createNewFile() puis Files.write dans un second temps).
        if (uuidString == null || uuidString.isEmpty()) {
            INSTANCE_UUID = UUID.randomUUID();
            if (!writeServerUUID(serverInfoFile)) {
                getServer().getPluginManager().disablePlugin(this);
                return false;
            }
            getLogger().info("Generated a new server UUID: " + INSTANCE_UUID);
            return true;
        }

        try {
            INSTANCE_UUID = UUID.fromString(uuidString);
        } catch (IllegalArgumentException exception) {
            getLogger().severe("server.info contains an invalid UUID (" + uuidString + ").");
            getLogger().severe("Delete the file to let the plugin regenerate one. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        getLogger().info("Server UUID: " + INSTANCE_UUID);
        return true;
    }

    private void saveServerUUID() {
        writeServerUUID(new File(getDataFolder(), "server.info"));
    }

    /**
     * Ecriture ATOMIQUE (fichier temporaire + move). L'ecriture directe pouvait laisser un
     * server.info tronque si le process mourait au mauvais moment : le noeud demarrait alors
     * avec INSTANCE_UUID null, il devenait sourd (il filtrait TOUS les messages) et muet
     * (il publiait serverId=null), sans le moindre avertissement (C-016).
     */
    private boolean writeServerUUID(File serverInfoFile) {
        try {
            File parent = serverInfoFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                getLogger().severe("Failed to create the plugin data folder.");
                return false;
            }

            Path temporary = new File(parent, "server.info.tmp").toPath();
            Files.write(temporary, INSTANCE_UUID.toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, serverInfoFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, serverInfoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            getLogger().severe("Failed to write server.info: " + exception.getMessage());
            return false;
        }
    }

// Imports a ajouter :
//   import java.nio.charset.StandardCharsets;
//   import java.nio.file.AtomicMoveNotSupportedException;
//   import java.nio.file.Path;
//   import java.nio.file.StandardCopyOption;

// ===== FICHIER 2 : REDIS/.../listener/RedisSubscriberRunnable.java =====
// Remplacer la ligne 121. CE HUNK DOIT PARTIR DANS LE MEME COMMIT : sans la garde de onEnable,
// retirer le test `INSTANCE_UUID == null` ferait qu'un noeud a UUID null cesserait de filtrer
// SES PROPRES messages (senderId.equals(null) est faux) et se les rejouerait en boucle.

            java.util.UUID senderId = redisMessage.serverId();
            if (senderId == null) {
                plugin.getLogger().warning("Redis message without a sender id, dropped: " + message);
                return;
            }
            // INSTANCE_UUID ne peut plus etre null ici : onEnable refuse de demarrer sans (C-016).
            if (senderId.equals(ZAuctionHouseRedis.INSTANCE_UUID)) return;
```

## 6.13 — C-010 — SQLITE + bridge cluster distribué est une configuration impossible : refus explicite des deux côtés

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionPlugin.java`  
**Risque** : LOW  
**Constats** : C-010

Cross-repo obligatoire : la garde côté plugin seule ne suffit pas, l'addon aurait déjà démarré son thread abonné et ses listeners muteraient la mémoire sans bridge installé. Le log systématique du bridge actif est fusionné ici plutôt que dans l'étape 14 parce que c'est la même méthode — deux commits sur `setAuctionClusterBridge` s'invalideraient. Je ne touche PAS au sous-constat Sarah (SqliteConnection sans `PRAGMA foreign_keys=ON` / `journal_mode=WAL` / `busy_timeout`) : il concerne la bibliothèque et TOUS ses consommateurs.

```java
// ===== FICHIER 1 : src/main/java/fr/maxlego08/zauctionhouse/ZAuctionPlugin.java =====
// Remplacer setAuctionClusterBridge (l.419-422) par :

    @Override
    public void setAuctionClusterBridge(AuctionClusterBridge auctionClusterBridge) {

        // SQLITE + bridge distribue = configuration IMPOSSIBLE. Chaque serveur a son PROPRE
        // fichier de base : les identifiants d'items se recoupent d'un serveur a l'autre, les
        // verrous du cluster portent sur des objets differents, et toute la protection
        // anti-duplication opere dans le vide. Les items SERONT dupliques (C-010).
        //
        // Je suis la contre-expertise contre le correctif d'origine : on NE fait PAS
        // disablePlugin(zAuctionHouse). Couper l'hotel des ventes entier priverait les joueurs
        // d'items deja en base, alors que retomber en mono-serveur est SUR ; et desactiver un
        // plugin depuis le onEnable d'un AUTRE plugin, pendant que Bukkit enumere ses plugins,
        // est fragile.
        if (auctionClusterBridge != null && auctionClusterBridge.isDistributed() && isSqliteStorage()) {
            getLogger().severe("=========================================================================");
            getLogger().severe("A DISTRIBUTED cluster bridge (" + auctionClusterBridge.getClass().getSimpleName() + ") tried to install");
            getLogger().severe("itself while storage-type is SQLITE. Each server has its OWN database file:");
            getLogger().severe("item ids collide across servers and the cluster locks protect nothing.");
            getLogger().severe("The bridge is REFUSED. This server stays in single-server mode.");
            getLogger().severe("Fix: set storage-type to MYSQL/MARIADB with a SHARED database,");
            getLogger().severe("or remove the cluster addon.");
            getLogger().severe("=========================================================================");
            return;
        }

        var previous = this.auctionClusterBridge;
        this.auctionClusterBridge = auctionClusterBridge;

        // Le mode REELLEMENT en vigueur doit etre lisible dans la console de chaque noeud :
        // c'est le seul moyen de detecter un repli silencieux en mono-serveur (C-036).
        getLogger().info("Cluster bridge: " + (previous == null ? "none" : previous.getClass().getSimpleName())
                + " -> " + (auctionClusterBridge == null ? "none" : auctionClusterBridge.getClass().getSimpleName())
                + " (distributed=" + (auctionClusterBridge != null && auctionClusterBridge.isDistributed()) + ")");
    }

    private boolean isSqliteStorage() {
        var connection = this.storageManager.getDatabaseConnection();
        return connection != null && connection.getDatabaseConfiguration().getDatabaseType() == DatabaseType.SQLITE;
    }

// Import a ajouter : import fr.maxlego08.sarah.database.DatabaseType;

// ===== FICHIER 2 : REDIS/.../ZAuctionHouseRedis.java =====
// Dans onEnable, APRES la garde d'entree de l'etape 12 et AVANT loadRedis() :

        // Refus symetrique cote addon : ne pas demarrer DU TOUT plutot que d'installer un
        // bridge dont les verrous ne protegent rien. On ne pose PAS de ClusterUnavailableBridge
        // ici : avec SQLITE il n'y a par construction AUCUN cluster, retomber en mono-serveur
        // (LocalAuctionClusterBridge) est le comportement SUR. C'est la seule sortie anticipee
        // de onEnable qui laisse volontairement le bridge local en place.
        if (isSqliteStorage()) {
            getLogger().severe("zAuctionHouse is configured with storage-type: SQLITE.");
            getLogger().severe("Multi-server synchronization requires a SHARED MySQL/MariaDB database:");
            getLogger().severe("with SQLite each server has its own file and item ids collide.");
            getLogger().severe("Disabling zAuctionHouseRedis. zAuctionHouse stays in single-server mode.");
            pluginManager.disablePlugin(this);
            return;
        }

    /**
     * ATTENTION : dans le jar d'API publie, Sarah est RELOCALISE en
     * {@code fr.maxlego08.zauctionhouse.libs.sarah} (API/build.gradle.kts:18). C'est ce nom-la
     * qu'il faut importer cote addon, exactement comme pour FoliaLib.
     */
    private boolean isSqliteStorage() {
        try {
            var connection = this.auctionPlugin.getStorageManager().getDatabaseConnection();
            return connection != null && connection.getDatabaseConfiguration().getDatabaseType() == DatabaseType.SQLITE;
        } catch (Throwable throwable) {
            // Version d'API plus ancienne ou relocation differente : on ne bloque pas le
            // demarrage sur ce test, le refus cote plugin reste le filet (C-095).
            getLogger().warning("Unable to read the zAuctionHouse storage type: " + throwable);
            return false;
        }
    }

// Import a ajouter cote ADDON :
//   import fr.maxlego08.zauctionhouse.libs.sarah.database.DatabaseType;
```

## 6.14 — C-036 — ClusterUnavailableBridge : un nœud multi-serveurs refuse d'opérer plutôt que de retomber silencieusement en mono-serveur

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/cluster/ClusterUnavailableBridge.java`  
**Risque** : MEDIUM  
**Constats** : C-036

Répond en une seule brique à C-036 et prépare C-018 (chantier 7), qui proposait de RESTAURER le bridge local au onDisable — ce qui réintroduirait exactement le défaut dénoncé ici. Changement de comportement fort et assumé : un serveur MONO-serveur qui installe l'addon puis perd Redis au boot ne pourra plus rien vendre ; c'est voulu, ces exploitants doivent désinstaller l'addon, pas le laisser en échec. La sortie SQLITE de l'étape 13 est la seule exception, et elle est justifiée : avec SQLite il n'y a par construction aucun cluster à protéger.

```java
// ===== FICHIER 1 (NOUVEAU) : REDIS/.../redis/cluster/ClusterUnavailableBridge.java =====
package fr.maxlego08.zauctionhouse.redis.cluster;

import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Bridge FAIL-CLOSED installe des que l'addon ne peut PAS garantir la synchronisation cluster.
 * <p>
 * Il ne restaure surtout pas {@code LocalAuctionClusterBridge} : sur un reseau multi-serveurs,
 * un bridge local est un piege silencieux -- ses verrous sont une {@code ConcurrentHashMap}
 * memoire invisible des autres noeuds, les achats concurrents passent tous, et les items se
 * dupliquent sans le moindre avertissement (C-036). Toutes les operations transactionnelles
 * echouent donc explicitement.
 * <p>
 * {@link #isDistributed()} rend {@code true} a dessein : le plugin principal doit continuer de
 * router par les chemins clusterises (paiements differes vers le systeme de claim, expiration
 * cluster-aware) plutot que de croire qu'il est seul au monde.
 */
public class ClusterUnavailableBridge implements AuctionClusterBridge {

    private final String reason;

    public ClusterUnavailableBridge(String reason) {
        this.reason = reason;
    }

    private <T> CompletableFuture<T> unavailable() {
        return CompletableFuture.failedFuture(new IllegalStateException(
                "zAuctionHouse cluster bridge unavailable: " + this.reason));
    }

    @Override
    public CompletableFuture<Boolean> checkAvailability(Item item) {
        return unavailable();
    }

    @Override
    public CompletableFuture<LockToken> lockItem(Item item, UUID buyerId, StorageType storageType) {
        return unavailable();
    }

    @Override
    public CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType) {
        // Relacher un verrou qu'on n'a jamais pris ne doit JAMAIS faire echouer une chaine :
        // c'est le seul chemin ou fail-closed serait contre-productif.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> notifyItemBought(Player player, Item item) {
        return unavailable();
    }

    @Override
    public CompletableFuture<Void> notifyItemListed(Item item) {
        return unavailable();
    }

    @Override
    public CompletableFuture<Void> notifyItemStatusChange(Item item, ItemStatus oldStatus, ItemStatus newStatus) {
        return unavailable();
    }

    @Override
    public CompletableFuture<Void> removeItem(Item item, StorageType storageType) {
        return unavailable();
    }

    @Override
    public boolean isDistributed() {
        return true;
    }

    public String getReason() {
        return this.reason;
    }
}

// ===== FICHIER 2 : REDIS/.../ZAuctionHouseRedis.java =====
// Helper + installation sur les sorties anticipees de onEnable POSTERIEURES a la resolution
// du plugin principal, SAUF la sortie SQLITE de l'etape 13 (voir son commentaire).

    /**
     * Installe le bridge fail-closed puis desactive l'addon.
     * <p>
     * Le chantier 7 (C-018) reutilisera ce meme helper dans onDisable : ne pas en creer un second.
     */
    private void failClosed(String reason) {
        getLogger().severe("=========================================================================");
        getLogger().severe("zAuctionHouseRedis cannot guarantee cluster synchronization: " + reason);
        getLogger().severe("This node will REFUSE every auction operation instead of silently");
        getLogger().severe("falling back to single-server locks, which WOULD duplicate items.");
        getLogger().severe("If this server is NOT part of a multi-server network, uninstall this addon.");
        getLogger().severe("=========================================================================");
        if (this.auctionPlugin != null) {
            this.auctionPlugin.setAuctionClusterBridge(new ClusterUnavailableBridge(reason));
        }
        getServer().getPluginManager().disablePlugin(this);
    }

// Puis, dans loadRedis(), remplacer les 4 `getServer().getPluginManager().disablePlugin(this);`
// par un appel a failClosed(...) portant la cause :
//   failClosed("missing 'redis-config' section in config.yml");
//   failClosed("invalid Redis configuration: " + exception.getMessage());
//   failClosed("Redis ping returned " + response + " instead of PONG");
//   failClosed("unable to connect to Redis: " + e.getMessage());
// (conserver les `return false;` qui suivent)
```

## 6.15 — C-031 — Enregistrer les listeners et confirmer l'abonnement AVANT d'installer le bridge, et supprimer les courses de données du démarrage

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/ZAuctionHouseRedis.java`  
**Risque** : LOW  
**Constats** : C-031

À placer après les autres étapes addon : il réordonne le bloc que celles-ci modifient, l'inverse imposerait des rebasages pénibles. Le point important à ne pas laisser croire au commanditaire est écrit dans le commit lui-même : ce correctif ferme la course de données et le trou « listeners non enregistrés », pas la fenêtre instantané-DB → SUBSCRIBE.

```java
// ===== FICHIER 1 : REDIS/.../listener/RedisSubscriberRunnable.java =====

    // Ecrit par le thread principal APRES Thread.start(), lu par le thread abonne : sans
    // ConcurrentHashMap et sans volatile, il n'existe AUCUNE relation happens-before (C-031).
    private final Map<Class<?>, RedisListener<?>> listeners = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.CountDownLatch subscribed = new java.util.concurrent.CountDownLatch(1);

    /**
     * Bloque jusqu'a la confirmation d'abonnement au canal, ou jusqu'a expiration du delai.
     *
     * @return {@code true} si l'abonnement est confirme
     */
    public boolean awaitSubscription(long timeout, java.util.concurrent.TimeUnit unit) {
        try {
            return this.subscribed.await(timeout, unit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

// Dans run(), completer le JedisPubSub anonyme :

                this.jedisPubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        handleMessage(channel, message, gson);
                    }

                    @Override
                    public void onSubscribe(String channel, int subscribedChannels) {
                        subscribed.countDown();
                    }
                };

// Dans handleMessage, ne plus jeter silencieusement un message sans listener :

            RedisListener<?> listener = getListener(messageClass);
            if (listener == null) {
                plugin.getLogger().warning("No listener registered for " + className + ", message DROPPED: " + message);
                return;
            }
            Object result = Utils.createInstanceFromMap(plugin.getLogger(), resolveConstructor(messageClass), map);
            plugin.debug("MessageClass: " + messageClass + " / Result: " + result);
            listener.message(result);

// ===== FICHIER 2 : REDIS/.../ZAuctionHouseRedis.java =====

    // Ecrit par le thread principal, lu par le thread abonne (debug()) : volatile obligatoire.
    private volatile boolean enableDebug = false;

// Reordonner la FIN de onEnable. L'ordre d'origine demarrait le thread abonne (l.83) et
// installait le bridge (l.99) AVANT d'enregistrer les listeners (l.101-104) : tout message
// recu dans cette fenetre etait jete sans le moindre log, et le noeud pouvait deja verrouiller
// et publier alors qu'il etait incapable de RECEVOIR (C-031).

        this.gson = getGsonBuilder().create();

        if (getConfig().getBoolean("enable-version-checker", true)) {
            new VersionChecker(auctionPlugin, this.getLogger(), 210);
        }
        new Metrics(this, 13288);
        context.ready();

        if (!this.loadRedis()) {
            return;
        }

        this.validateAndRegisterUUID();

        // Lecture des TTL avant toute construction de bridge.
        int lockTtlSeconds = getConfig().getInt("redis-config.lock-ttl-seconds", 30);
        this.lockTtl = Duration.ofSeconds(lockTtlSeconds);

        int itemStateTtlSeconds = getConfig().getInt("redis-config.item-state-ttl-seconds", 86400);
        Duration itemStateTtl = itemStateTtlSeconds > 0 ? Duration.ofSeconds(itemStateTtlSeconds) : null;

        int itemListedTtlSeconds = getConfig().getInt("redis-config.item-listed-ttl-seconds", 2592000);
        Duration itemListedTtl = itemListedTtlSeconds > 0 ? Duration.ofSeconds(itemListedTtlSeconds) : null;

        // 1. LES LISTENERS D'ABORD : le thread abonne doit trouver la table complete des son
        //    premier message. L'ecriture est faite AVANT Thread.start(), ce qui etablit la
        //    relation happens-before que l'ordre d'origine n'avait pas.
        this.redisSubscriberRunnable.registerListener(ItemListedMessage.class, new ItemListedListener(this));
        this.redisSubscriberRunnable.registerListener(ItemRemovedMessage.class, new ItemRemovedListener(this));
        this.redisSubscriberRunnable.registerListener(ItemBoughtMessage.class, new ItemBoughtListener(this));
        this.redisSubscriberRunnable.registerListener(ItemStatusMessage.class, new ItemStatusListener(this));

        // 2. LE THREAD ABONNE ENSUITE.
        this.subscriberThread = new Thread(this.redisSubscriberRunnable, "zAuctionHouse-Redis-Subscriber");
        this.subscriberThread.setDaemon(true);
        this.subscriberThread.start();

        // 3. ATTENTE DE LA CONFIRMATION D'ABONNEMENT. Un noeud ne doit jamais verrouiller ni
        //    publier avant d'etre capable de RECEVOIR. L'attente est bornee et ne bloque que le boot.
        if (!this.redisSubscriberRunnable.awaitSubscription(5, TimeUnit.SECONDS)) {
            getLogger().warning("Redis subscription not confirmed after 5s: this node may miss the messages");
            getLogger().warning("published during this window. It will keep retrying in the background.");
        }

        // 4. LE BRIDGE EN DERNIER.
        this.auctionPlugin.setAuctionClusterBridge(new RedisAuctionClusterBridge(this, this.jedisPool, this.lockTtl, itemStateTtl, itemListedTtl));

// NOTE HONNETE A PORTER DANS LE MESSAGE DE COMMIT : ce reordonnancement NE FERME PAS la fenetre
// principale. `depend: zAuctionHouse` garantit que ZAuctionPlugin.onEnable -- instantane DB
// inclus (storageManager.loadItems()) -- se termine AVANT le onEnable de l'addon. Le vrai trou
// va de cet instantane au SUBSCRIBE effectif, soit plusieurs secondes. Seul C-047
// (reconciliation apres reconnexion, chantier 7) le referme.
```

## 6.16 — C-057 — Un champ non convertible ne détruit plus tout le message de synchronisation

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/utils/Utils.java`  
**Risque** : LOW  
**Constats** : C-057

Le correctif de l'audit appliqué seul est PIRE que le mal : renvoyer null pour une constante inconnue casse ItemStatusListener:54. Les gardes des deux listeners porteurs d'enums sont donc obligatoires dans le même commit. Le déclencheur est aujourd'hui préventif (ItemStatus et StorageType n'ont pas bougé depuis `deb8f16`), mais la pratique existe : SellFailReason a bien vu apparaître une constante sur la même période. Le constructeur canonique est un durcissement, pas un correctif de bug actuel (javac émet bien MethodParameters pour les records).

```java
// ===== FICHIER 1 : REDIS/.../utils/Utils.java =====
// Branche enum de convertToRequiredType (l.95-100) :

        } else if (type.isEnum()) {
            try {
                return Enum.valueOf((Class<Enum>) type, (String) value);
            } catch (IllegalArgumentException | ClassCastException exception) {
                // NE JAMAIS rendre la valeur BRUTE. La String non convertie remontait telle
                // quelle a constructor.newInstance(), qui levait IllegalArgumentException :
                // le message ENTIER etait abandonne, itemId compris, et l'evenement de
                // synchronisation perdu pour ce noeud (C-057). Un CHAMP perdu vaut infiniment
                // mieux qu'un evenement perdu.
                logger.log(Level.SEVERE, String.format(
                        "Unknown constant '%s' for enum '%s'. The field is dropped: this node is probably "
                        + "running an older version than the server that sent the message.", value, type.getName()));
                return null;
            }
        } else if (type == BigDecimal.class) {
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException exception) {
                logger.log(Level.SEVERE, String.format("Failed to convert '%s' to BigDecimal", value), exception);
                return null;
            }
        } else if (type == UUID.class) {
            try {
                return UUID.fromString((String) value);
            } catch (IllegalArgumentException | ClassCastException exception) {
                logger.log(Level.SEVERE, String.format("Failed to convert '%s' to UUID", value), exception);
                return null;
            }
        }
        // ... idem pour Integer/Double/Long/Boolean/Float : `return null` au lieu de tomber
        //     sur le `return value` final. Pour Integer, remplacer le `throw e` par `return null`.

// Dans createInstanceFromMap, apres le bloc de conversion :

                if (value != null) {
                    try {
                        ...
                    } catch (Exception exception) {
                        logger.log(Level.SEVERE, String.format("Error converting value '%s' for parameter '%s' to type '%s'", value, paramName, paramType.getName()), exception);
                        value = null;
                    }
                }

                // Un parametre PRIMITIF ne peut pas recevoir null : newInstance leverait une
                // IllegalArgumentException et le message entier serait de nouveau perdu.
                // Aucun des 4 records n'en declare aujourd'hui : garde preventive.
                if (value == null && paramType.isPrimitive()) {
                    value = primitiveDefault(paramType);
                }

                arguments[i] = value;

    private static Object primitiveDefault(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        if (type == boolean.class) return false;
        return null;
    }

// ===== FICHIER 2 : REDIS/.../listener/RedisSubscriberRunnable.java =====
// Remplacer messageClass.getConstructors()[0] (l.135) par un resolveur explicite.

    /**
     * Resout le constructeur CANONIQUE d'un record plutot que {@code getConstructors()[0]},
     * dont l'ordre n'est garanti par aucune specification.
     */
    private static java.lang.reflect.Constructor<?> resolveConstructor(Class<?> messageClass) throws NoSuchMethodException {
        if (messageClass.isRecord()) {
            Class<?>[] types = java.util.Arrays.stream(messageClass.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getType)
                    .toArray(Class<?>[]::new);
            return messageClass.getDeclaredConstructor(types);
        }
        return messageClass.getConstructors()[0];
    }

// Ajouter `catch (NoSuchMethodException exception)` a handleMessage (ou l'absorber dans le
// catch(Exception) existant, qui le couvre deja).

// ===== FICHIER 3 : REDIS/.../listener/listeners/ItemStatusListener.java =====
// GARDE OBLIGATOIRE, DANS LE MEME COMMIT. Sans elle le retour a null casserait la l.54 :
// item.setStatus(null) rend l'item invisible PARTOUT (Item.isActivelyListed teste
// getStatus() == AVAILABLE) et chaque switch sur le statut leve une NPE.
// A inserer juste apres le parse de l'itemId, avant le runNextTick :

        if (message.newStatus() == null) {
            this.plugin.getLogger().warning("Item status message for #" + message.itemId() + " carries an unknown "
                    + "status and is IGNORED. This node is probably older than the server that sent it.");
            return;
        }

// ===== FICHIER 4 : REDIS/.../listener/listeners/ItemRemovedListener.java =====
// destinationStorageType == null route deja vers la branche de compatibilite qui relit l'etat
// en base : c'est le comportement SUR, mais il doit laisser une trace explicite.
// A inserer juste apres le parse de l'itemId :

        if (message.destinationStorageType() == null) {
            this.plugin.getLogger().warning("Item removal message for #" + message.itemId() + " has no readable "
                    + "destination storage type, falling back to the database state.");
        }
```

### Ruptures d'API et stratégie de compatibilité

- Repository.java (API publié) — ajout de `protected static final int IN_CLAUSE_CHUNK_SIZE`, `selectOrFail`, `selectAllOrFail`, `selectInOrFail`. Ajouts purement ADDITIFS sur une classe abstraite : source- et binaire-compatibles pour toute sous-classe existante. Aucune signature existante n'est modifiée.
- StorageManager.java (API publié) — ajout de l'enum imbriqué `LookupState`, du record imbriqué `ItemLookupResult` et de la méthode `default CompletableFuture<ItemLookupResult> selectItemState(int)`. Stratégie de compatibilité : la méthode est `default` et délègue à `selectItem(int)` en mappant null → GONE, donc toute implémentation tierce de StorageManager continue de compiler et de se comporter à l'identique. `selectItem(int)` conserve strictement son contrat (null pour les trois cas) parce que l'addon Redis, compilé contre le SHA figé `deb8f16`, en dépend sur 4 sites d'appel, dont un commentaire délibéré d'ItemBoughtListener:38-40.
- Tables.java (API publié) — ajout de la constante `MIGRATION_STATE`. Ajouter un champ à une interface est source- et binaire-compatible ; les constantes existantes déjà inlinées chez les consommateurs ne bougent pas.
- Message.java (API publié) — ajout de la constante d'enum `MIGRATION_ALREADY_DONE`. Purement additif : aucun switch exhaustif sur Message n'existe dans les deux dépôts (vérifié), aucune rupture binaire pour l'addon figé.
- Base64ItemStack.java (API publié) — ajout de `setWriteFormatMarker(boolean)` / `isWriteFormatMarker()` (additif) et passage de portée PAQUET pour `encodeBukkitStream`/`decodeBukkitStream` (nouvelles méthodes, pas de réduction de visibilité d'une méthode existante). `encode(ItemStack)` et `decode(String)` gardent leur signature. CHANGEMENT DE CONTRAT DOCUMENTÉ : `ItemStackUtils.serializeItemStack` peut désormais rendre `null` là où il rendait une chaîne Base64 vide — c'est l'intention (la chaîne vide faisait perdre l'item en silence), mais tout appelant tiers doit tester la nullité.
- AUCUN changement d'API n'oblige à bumper le pin `fr.maxlego08.zauctionhouse:zauctionhousev4-api:deb8f16` de l'addon Redis : les correctifs addon de ce chantier (C-016, C-031, C-036, C-057, C-113, moitié addon de C-010) n'utilisent que des membres déjà présents dans ce SHA (`AuctionClusterBridge` avec ses deux `default`, `StorageManager.getDatabaseConnection()`, `AuctionPlugin extends Plugin`).
- PIÈGE DE RELOCATION à ne pas manquer côté addon : le jar d'API publié relocalise Sarah en `fr.maxlego08.zauctionhouse.libs.sarah` (API/build.gradle.kts:18). L'import correct dans ZAuctionHouseRedis est `fr.maxlego08.zauctionhouse.libs.sarah.database.DatabaseType`, PAS `fr.maxlego08.sarah.database.DatabaseType` (qui est le nom utilisé côté plugin, où la relocation n'a lieu qu'au packaging).

### Migrations de schéma

- NOUVELLE TABLE `%prefix%migration_state` (étape 11), créée par `CreateMigrationStateMigration` enregistrée EN DERNIER dans ZStorageManager.onEnable pour ne pas perturber l'ordre historique déjà appliqué sur les serveurs existants. Colonnes : provider_id VARCHAR(64) PRIMARY KEY, server_name VARCHAR(255), migrated_at BIGINT, players_imported INT, items_imported INT, transactions_imported INT. Aucune clé étrangère, aucune action admin : le MigrationManager de Sarah l'applique automatiquement au démarrage.
- AUCUN ALTER sur les tables existantes. Vérifié : le MigrationManager de Sarah ne sait qu'AJOUTER des colonnes manquantes (PRAGMA table_info sous SQLite, information_schema.COLUMNS sinon) et n'émet jamais d'ALTER ... MODIFY — c'est précisément pourquoi je rejette le correctif de l'audit pour C-033 (rendre item_id nullable), qui serait inopérant sur toute installation existante.
- AUCUN nouvel index : la migration d'index de C-066 appartient au chantier PERF, et Sarah émet ses CREATE INDEX SANS `IF NOT EXISTS` (CreateIndexRequest:26-36) en relançant l'exception jusqu'à onEnable — un index déjà créé à la main TUE le démarrage. Ne pas glisser d'index dans ce chantier.
- EFFET DE DONNÉES de l'étape 10 (non réversible mais inoffensif) : la migration V3 écrit désormais une ligne `%prefix%items` en storage_type='DELETED' par transaction V3 importée. Ces lignes sont invisibles en jeu (ItemRepository.select et select(int) filtrent DELETED) et portent les FK de logs/transactions. La FK étant ON DELETE CASCADE, les purger effacerait aussi les logs et transactions associés — c'est le comportement voulu.

### Changements de configuration

- RECTIFICATIF PRÉALABLE — le plugin livre SIX jeux de configuration et non quatre : racine (anglais) + fr/ + es/ + it/ + id/ (indonésien) + th/ (thaï), tous présents sur disque dans src/main/resources/. Le CLAUDE.md du projet et l'énoncé du chantier sont périmés sur ce point. Toute clé ci-dessous doit être répliquée SIX fois.
- config.yml — NOUVELLE CLÉ `write-itemstack-format-marker: false`, à placer juste après le bloc `storage-type` (l.129). Commentaire à traduire dans les 6 langues : « Écrit un marqueur de format ("V2:" / "NBT:") en tête de chaque ItemStack sérialisé. DÉPLOIEMENT EN DEUX PHASES : laissez false tant que TOUS les serveurs du réseau ne sont pas passés sur cette version ou une plus récente. Un serveur resté en version antérieure ne sait pas lire un marqueur et affichera les items concernés comme corrompus. Une fois tout le parc à jour, passez à true : le format devient porté par la donnée et non par la version Minecraft du serveur qui lit. »
- config.yml — MODIFICATION DE COMMENTAIRE l.128 : remplacer « Note: For multi-server synchronization, use MySQL/MariaDB with the Redis addon. » par un AVERTISSEMENT explicite : « WARNING: SQLITE is INCOMPATIBLE with the Redis cluster addon. Each server would have its own database file, item ids would collide between servers and the cluster locks would protect nothing — items WOULD be duplicated. With the addon installed, SQLITE is now refused: the addon disables itself and this server stays in single-server mode. » — à répliquer dans les 6 config.yml.
- messages.yml — NOUVELLE CLÉ `migration-already-done` (3 lignes), correspondant à la constante Message.MIGRATION_ALREADY_DONE. Placeholders : %source%, %server%, %date%, %items%. À traduire et répliquer dans les 6 messages.yml.
- AUCUNE nouvelle clé dans le config.yml de l'addon Redis (fichier unique, non traduit) : les correctifs C-016, C-031, C-036, C-057, C-113 n'en introduisent aucune.
- DOCUMENTATION Docusaurus obligatoire (C:/Users/Admin/Desktop/groupez/documentation), EN + FR : (1) page storage — l'incompatibilité SQLITE + addon Redis devient un refus explicite ; (2) page cluster — un nœud qui ne peut pas joindre Redis REFUSE désormais toute opération au lieu de retomber silencieusement en mono-serveur ; (3) page migration — la commande n'est plus rejouable sans `force`, et la procédure de rejeu après correctif ; (4) nouvelle clé write-itemstack-format-marker avec sa procédure de déploiement en deux phases. Section `# Unreleased` de changelog.md à compléter.

### Fichiers nouveaux

- D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/java/fr/maxlego08/zauctionhouse/storage/migrations/CreateMigrationStateMigration.java
- D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/java/fr/maxlego08/zauctionhouse/storage/repository/repositories/MigrationStateRepository.java
- D:/Users/Maxlego08/workspace2.0/zAuctionHouse Redis/src/main/java/fr/maxlego08/zauctionhouse/redis/cluster/ClusterUnavailableBridge.java

### Validation

- C-001 / chargement paginé — Sur SQLite : `/ah admin generate 40000`, arrêter, redémarrer. AVANT : la ligne « Loaded N items » apparaît mais l'hôtel des ventes affiche des lots vides achetables. APRÈS : le chargement réussit et l'invariant SQL doit tenir — `SELECT COUNT(*) FROM zauctionhousev4_auction_items;` doit être supérieur ou égal au nombre d'items chargés annoncé dans la console, et `SELECT COUNT(*) FROM zauctionhousev4_items i WHERE i.item_type='AUCTION' AND i.storage_type<>'DELETED' AND NOT EXISTS (SELECT 1 FROM zauctionhousev4_auction_items a WHERE a.item_id=i.id);` doit valoir 0.
- C-001 / quarantaine contenu illisible — `UPDATE zauctionhousev4_auction_items SET itemstack='###not-base64###' WHERE item_id=<X>;` puis redémarrer. Attendu : exactement une ligne SEVERE `[Quarantine] Auction item #<X> has an unreadable ItemStack`, l'item ABSENT de `/ah`, et la ligne INTACTE en base : `SELECT storage_type FROM zauctionhousev4_items WHERE id=<X>;` doit toujours rendre LISTED.
- C-001 / quarantaine lot vide — `DELETE FROM zauctionhousev4_auction_items WHERE item_id=<X>;` puis redémarrer. Attendu : SEVERE `has NO content row`, item non affiché, aucun achat possible. Un second joueur qui avait l'item en cache GUI avant le redémarrage doit obtenir un refus, pas un débit.
- C-001 / fail-closed au boot — Démarrer avec MySQL, puis `docker stop mysql` et redémarrer le serveur Minecraft. Attendu : SEVERE « Failed to load the auction items from the database », plugin désactivé proprement, et `jcmd <pid> Thread.print | grep -i hikari` NE doit plus rien retourner 10 s après la désactivation (vérifie l'étape 2). Relancer MySQL et redémarrer : boot normal.
- C-118 — Provoquer un échec APRÈS storageManager.onEnable() : introduire volontairement un YAML d'inventaire invalide dans inventories/auction.yml. Attendu : onEnable s'interrompt, ET le pool est bien fermé (même vérification jcmd que ci-dessus). AVANT le correctif, la garde `if (!isEnabled) return;` sautait tout le teardown.
- C-054 — Sur un serveur 1.20.4 (donc isAttributItemStack() == false) : insérer une charge illisible (`UPDATE ... SET itemstack='@@@'`) puis démarrer. AVANT : StackOverflowError non rattrapé dans AuctionLoader.loadItems, boot mort. APRÈS : une ligne WARNING de decode + une ligne SEVERE de quarantaine, boot normal.
- C-029 — Test de non-régression du déploiement : avec `write-itemstack-format-marker: false` (défaut), vendre un item puis vérifier que la colonne ne commence NI par `V2:` NI par `NBT:` (`SELECT LEFT(itemstack,4) FROM zauctionhousev4_auction_items ORDER BY id DESC LIMIT 1;`). Puis passer la clé à true, vendre, vérifier le préfixe `V2:`, et confirmer qu'un serveur resté en phase 1 relit correctement l'item marqué (c'est le test qui autorise la phase 2).
- C-010 (deux serveurs) — Configurer `storage-type: SQLITE` et installer l'addon Redis. Attendu : bloc SEVERE côté addon, `/plugins` montre zAuctionHouseRedis en rouge, la console du plugin principal NE contient AUCUNE ligne `Cluster bridge: ... -> RedisAuctionClusterBridge`, et `/ah` reste pleinement fonctionnel en mono-serveur.
- C-113 — Casser volontairement la configuration MySQL du plugin principal (mauvais mot de passe) et démarrer avec l'addon. Attendu : l'addon logue « zAuctionHouse is loaded but DISABLED » et se désactive, au lieu de partir en cascade d'exceptions.
- C-016 — (a) `truncate -s 0 plugins/zAuctionHouseRedis/server.info` puis démarrer : un nouvel UUID est généré, le nœud démarre, `redis-cli KEYS "auction:server:*"` montre la nouvelle clé. (b) `printf 'not-a-uuid' > server.info` puis démarrer : l'addon REFUSE de démarrer avec un message actionnable. (c) Vérifier l'absence de `server.info.tmp` résiduel après un démarrage normal.
- C-036 (deux serveurs A et B) — Couper Redis, démarrer B. Attendu : bloc SEVERE « cannot guarantee cluster synchronization », et une tentative d'achat sur B doit ÉCHOUER (message d'erreur au joueur) sans que la base bouge : `SELECT storage_type, buyer_unique_id FROM zauctionhousev4_items WHERE id=<X>;` doit rester LISTED/NULL. AVANT le correctif, l'achat réussissait localement avec des verrous mémoire invisibles de A.
- C-031 — Sur B, vérifier l'ORDRE des lignes de console au démarrage : « Subscribed to Redis channel » DOIT précéder « Cluster bridge: ... -> RedisAuctionClusterBridge ». Puis, pendant le boot de B, mettre en vente 20 items sur A : après le boot, `/ah` sur B doit tous les afficher (ou, s'il en manque, la cause doit être la fenêtre instantané-DB → SUBSCRIBE documentée, pas des messages jetés sans log).
- C-057 — Injecter un message forgé : `redis-cli PUBLISH zauctionhouse '{"serverId":"<uuid-d-un-autre-noeud>","className":"fr.maxlego08.zauctionhouse.redis.listener.messages.ItemStatusMessage","t":{"itemId":"42","oldStatus":"AVAILABLE","newStatus":"FUTURE_STATUS"}}'`. Attendu : un SEVERE « Unknown constant 'FUTURE_STATUS' », un WARNING « carries an unknown status and is IGNORED », et l'item 42 TOUJOURS visible et achetable dans `/ah` (avant, tout le message était perdu ; avec le correctif de l'audit appliqué seul, l'item devenait invisible partout).
- C-040 — Pointer `migration.zauctionhouse-v3.host` sur un hôte inexistant puis `/ah admin migrate v3 confirm`. Attendu : message ROUGE MIGRATION_FAILED. AVANT : message vert de succès avec 0 item importé.
- C-033 (MySQL obligatoire, c'est le backend où le bug mord) — Migrer un jeu V3 contenant au moins 5 transactions `need_money=1`. Invariants : `SELECT COUNT(*) FROM zauctionhousev4_transactions WHERE status='PENDING';` doit valoir 5 (valait 0 avant) ; `SELECT COUNT(*) FROM zauctionhousev4_items WHERE storage_type='DELETED' AND server_name=<serveur>;` doit valoir 5 ; `/ah claim` sur le vendeur doit créditer le montant.
- C-075 — Dans la base V3, poser `UPDATE zauctionhouse_items SET itemstack=';' WHERE id=<Y>;` (type INVENTORY) puis migrer. Attendu : l'item compté dans `errors`, un SEVERE nommant l'id V4, et l'invariant d'orphelins à 0 (même requête que le test C-001). AVANT : l'annonce était importée vide et vendable au prix plein.
- C-108 — Insérer manuellement `INSERT INTO zauctionhousev4_players VALUES ('<uuid>','RealName',...)`, puis migrer un jeu V3 où ce même UUID n'apparaît QUE comme acheteur. Attendu : `SELECT name FROM zauctionhousev4_players WHERE unique_id='<uuid>';` rend toujours 'RealName' (rendait 'Unknown' avant).
- C-034 — Après une migration réussie, relancer `/ah admin migrate v3 confirm` : refus avec migration-already-done nommant le serveur et la date. Puis `/ah admin migrate v3 confirm force` : la migration repart. Vérifier `SELECT * FROM zauctionhousev4_migration_state;`.
- C-044 — Connecter un joueur puis lancer `/ah admin migrate v3 confirm` : refus explicite. Le déconnecter, relancer : la migration passe et la console affiche « Cleared N in-memory item(s) before reloading them from the database ». Vérifier ensuite qu'un item vendu AVANT la migration n'est pas ressuscité en AVAILABLE dans `/ah`.
- C-109 — Renommer une économie dans economies.yml en laissant des items qui la référencent, puis déclencher un selectItem sur l'un d'eux. Attendu : le log distingue explicitement « The item is NOT gone, it is UNREADABLE: check economies.yml » au lieu du message générique. (La consommation du verdict par PurchaseService/ExpireService est laissée aux chantiers 1/5, cf. openQuestions.)

### Questions ouvertes pour le mainteneur

- RUPTURE D'API ASSUMÉE OU NON : `selectItemState` est livrée en `default` pour ne rien casser, mais elle laisse `selectItem` avec son contrat ambigu et les 4 sites d'appel de l'addon Redis dessus. Faut-il planifier un second commit cross-repo migrant l'addon vers `selectItemState` (après republication de zauctionhousev4-api et bump du pin `deb8f16`), ou accepter durablement les deux sémantiques ? Attention : ItemBoughtListener:38-40 EXPLOITE délibérément le fait que selectItem rend null sur une ligne DELETED.
- PÉRIMÈTRE DE C-109 : je livre la primitive mais NE migre aucun appelant, parce que PurchaseService:118 (C-006, chantier QUICK) et ExpireService:283 (C-091, chantier 1) réécrivent déjà ces blocs. Confirmez-vous que ces deux chantiers prennent la migration à leur charge ? Sinon C-109 reste à moitié ouvert et il faut me réattribuer les deux call sites.
- PHASE 2 DU MARQUEUR DE FORMAT (C-029) : la clé `write-itemstack-format-marker` est à false par défaut et doit être basculée manuellement par l'exploitant après mise à jour complète du parc. Préférez-vous plutôt un basculement automatique piloté par une annonce de version des nœuds dans Redis (point 5 du correctif d'audit) ? Cela ajouterait une dépendance du plugin principal vers l'addon, ce que j'ai volontairement évité.
- COMPORTEMENT VISIBLE PAR LES JOUEURS (C-001/C-011) : la quarantaine fait DISPARAÎTRE de l'hôtel des ventes les annonces déjà corrompues en production, au lieu de les afficher vides ou en BARRIER. Les exploitants signaleront une « perte d'items ». Faut-il livrer dans le même lot une commande admin `/ah admin quarantine list` (non chiffrée dans ce plan, ~60 LOC) pour qu'ils puissent les inspecter et rembourser, ou se contenter des lignes SEVERE ?
- REFUS DE DÉMARRAGE (C-001, étape 5) : le plugin refuse désormais de démarrer si le chargement des items échoue. Les commandes sont enregistrées AVANT (ZAuctionPlugin l.156) : Bukkit laisse-t-il un `/ah` orphelin après désactivation sur votre version de Paper ? À vérifier avant la release ; si oui, déplacer l'enregistrement des commandes après loadItems (changement d'ordre non anodin, je ne l'ai pas fait de ma propre initiative).
- IMPOSER MYSQL (C-010) : un réseau déjà en production avec SQLITE + Redis (configuration cassée mais silencieuse) perdra sa « synchronisation » au premier redémarrage après mise à jour. C'est l'effet voulu, mais il doit apparaître EN TÊTE du changelog, sinon l'admin conclura à une régression de l'addon. Validez-vous ce message de communication ?
- FAIL-CLOSED CLUSTER (C-036) : un serveur mono-serveur qui a installé l'addon « au cas où » et perd Redis ne pourra plus rien vendre ni acheter. C'est un changement d'exploitation fort. Acceptez-vous, ou préférez-vous une clé `cluster.required: true` dans le config.yml du plugin principal (ce qui imposerait une réplication dans les SIX jeux de langue et un message joueur dédié) ?
- SIX LANGUES, PAS QUATRE : l'énoncé du chantier et le CLAUDE.md annoncent 4 jeux de configuration ; le dépôt en contient 6 (racine + fr/es/it/id/th). Toutes mes clés doivent donc être répliquées 6 fois. Faut-il corriger le CLAUDE.md du projet dans le même lot ?
- HORS FICHE, INCLUS PAR NÉCESSITÉ : (a) le jumeau ZelAuction du bug item_id = 0 (étape 10, fichier 2) ; (b) la quarantaine sur `getPlayerName` (étape 5), qui transformait une seule ligne orpheline en refus de chargement TOTAL. Confirmez-vous leur inclusion, ou préférez-vous deux fiches séparées ?
- NON TRAITÉ ET À RÉATTRIBUER : `CommandAuctionAdminMigrate:87-92` exécute `loadItems()` dans un `runNextTick`, donc sur le THREAD PRINCIPAL, alors qu'AuctionLoader enchaîne trois SELECT JDBC bloquants — sur 20 000 items c'est un gel serveur de plusieurs secondes. Je ne l'ai pas déplacé (le sortir du thread principal introduirait ses propres mutations concurrentes) ; avec le garde-fou « aucun joueur connecté » de l'étape 7 le gel est acceptable, mais cela mérite une fiche pour le chantier 8.

### Retour arrière

["RÈGLE GÉNÉRALE : 15 des 16 étapes sont réversibles par simple downgrade du jar. Les deux dépôts doivent être downgradés ENSEMBLE (C-010 et C-036 sont cross-repo).", "Étape 11 (table %prefix%migration_state) — NON réversible par downgrade, mais INOFFENSIVE : un jar antérieur ignore complètement la table et la ligne correspondante dans zauctionhousev4_migrations. Aucune action. Si l'on veut réellement effacer la trace : `DROP TABLE zauctionhousev4_migration_state; DELETE FROM zauctionhousev4_migrations WHERE migration = 'CreateMigrationStateMigration';`.", "Étape 10 (lignes sentinelles items DELETED) — NON réversible et à NE PAS annuler : la FK est ON DELETE CASCADE, supprimer ces lignes effacerait aussi les logs ET les transactions PENDING importées, donc l'argent des vendeurs. Elles sont invisibles en jeu, les laisser est sans conséquence.", "Étape 4 (marqueur de format) — SEUL POINT VRAIMENT DANGEREUX EN ROLLBACK. Si la clé a été passée à true et qu'on downgrade, les lignes déjà écrites avec le préfixe `V2:` deviennent illisibles par l'ancien jar (IllegalArgumentException dans Base64.getDecoder()). Procédure d'urgence, à exécuter AVANT le downgrade : `UPDATE zauctionhousev4_auction_items SET itemstack = SUBSTRING(itemstack, 4) WHERE itemstack LIKE 'V2:%';` puis `UPDATE zauctionhousev4_auction_items SET itemstack = SUBSTRING(itemstack, 5) WHERE itemstack LIKE 'NBT:%';` (SUBSTR sous SQLite). C'est la raison pour laquelle la clé est à false par défaut et documentée en deux phases.", "Étapes 5 et 6 (chargement fail-closed) — Si un serveur de production refuse désormais de démarrer, c'est que sa base est réellement incohérente : NE PAS downgrader pour retrouver un démarrage silencieux, car l'ancien comportement vendait du vide. Les lignes SEVERE `[Quarantine]` nomment exactement les ids fautifs ; la sortie de secours immédiate est de les neutraliser à la main (`UPDATE zauctionhousev4_items SET storage_type='DELETED' WHERE id IN (...)`) après avoir remboursé les vendeurs concernés.", "Étape 14 (ClusterUnavailableBridge) — Si un exploitant MONO-serveur se retrouve bloqué parce qu'il avait installé l'addon sans en avoir besoin, la remédiation est de DÉSINSTALLER l'addon (supprimer le jar), pas de downgrader : le plugin principal repart alors sur LocalAuctionClusterBridge, ce qui est correct pour lui.", "Étape 12 (server.info) — Aucun effet persistant à annuler. Si un UUID a été régénéré à cause d'un fichier vide, l'ancien est perdu ; sans conséquence, la clé Redis `auction:server:<ancien>` expire d'elle-même en 120 s.", "Clés de configuration — Toutes additives. Un jar antérieur ignore `write-itemstack-format-marker` et `migration-already-done` sans erreur."]


---

# Chantier 7 — Réconciliation du bus Redis et expiration des statuts transitoires

Ce chantier ferme la famille de défauts où un état transitoire (statut de confirmation, message de bus, annonce expirée) n'a ni propriétaire, ni horloge de sûreté, ni point de reprise après incident. Il apporte quatre briques indivisibles : (1) côté plugin, un cycle de vie complet pour les statuts IS_*_CONFIRM — libération explicite à la déconnexion et à la mort, garde de statut unifiée sur les deux sorties de ConfirmHelper, protection des clés « opération en cours » contre /ah admin cache clear, et un balayage TTL qui rearme tout statut de confirmation oublié ; (2) une tâche d'expiration planifiée, sans laquelle une annonce arrivée à terme n'expire que si un joueur ouvre par hasard un onglet qui emprunte getItemIds ; (3) côté addon, une primitive d'ordonnancement par identifiant d'item plus un compare-and-swap de statut, qui rendent les quatre listeners insensibles au réordonnancement et aux instantanés périmés ; (4) une publication différenciée pré-commit / post-commit et une réconciliation automatique du store LISTED après chaque coupure du bus. L'ensemble est indivisible parce que chaque garde ajoutée transforme une perte de message en désynchronisation permanente : le CAS de C-004 n'est vivable qu'avec le rearmement TTL, et le rearmement TTL n'est correct qu'avec la garde de statut de C-068/C-069. Un correctif isolé de cette grappe déplace le problème au lieu de le supprimer.

**Prérequis**

- C-045 (chantier 2/QUICK) — ZAuctionManager.java:956 `return` -> `continue`. Sans lui, `updateListedItems(item, added, player)` ne rafraîchit qu'une partie des spectateurs : l'étape 3 (ConfirmHelper) et l'étape 12 (C-092) restent partiellement inopérantes et TOUT test multi-joueurs de ce chantier donne un faux négatif. À livrer avant la validation.
- C-062 (chantier 6) — CommandAuctionAdminAdd calcule `expiredAt = System.currentTimeMillis()` pour addExpired/addPurchased, donc l'item est immédiatement récupérable-expiré et détruit au premier `getItemIds(EXPIRED)`. Mon étape 8 diffuse désormais cet item au cluster : sans C-062, on diffuse une destruction. Livrer C-062 avec ou avant l'étape 8.
- C-036 (chantier 6) — introduit `ClusterUnavailableBridge` côté addon pour les sorties anticipées de `onEnable`. Mon étape 15 a besoin de la MÊME classe pour `onDisable`. ARBITRAGE : je fournis la classe dans l'étape 15 ; si le chantier 6 est livré en premier, réutiliser sa version telle quelle et ne garder de mon étape que les modifications de `onDisable` et `releaseAllLocks()`. Ne pas créer deux classes.
- C-052 / C-077 (chantier 8) — dimensionnement du pool Jedis, `setMaxWait`, executor dédié. Mon étape 13 introduit `publish(Jedis, T)` et supprime le double emprunt imbriqué des trois `notify*`, qui fait partie du patch C-077 : le chantier 8 doit BÂTIR SUR cette signature et ne pas refaire la suppression. Zone de conflit textuel annoncée.
- C-089 (chantier 8) — `ZStorageManager.java:177` et `:215` doivent passer `getExecutorService()` à leurs `supplyAsync`. Mes étapes 10 et 11 ajoutent des gardes sur le résultat de `selectItem` ; tant que ce JDBC bloquant part sur `ForkJoinPool.commonPool` saturé par les appels Jedis, les faux `Unable to find the item` persistent et masqueront l'effet des gardes.
- C-050 (chantier PERF) — `Item getItem(StorageType, int)` sur l'API `AuctionManager`. Mes étapes 11, 12 et 14 utilisent un helper addon-local `ItemLookup.find` en O(n) (copie complète du store par appel). Une fois C-050 livré et le pin `zauctionhousev4-api` de l'addon bumpé, remplacer le corps de `ItemLookup.find` par un appel O(1) ; aucune autre ligne à changer.
- C-103 (chantier PERF) — `setItemStatus` invalidant `SortedItemsCache`. Mon étape 6 appelle explicitement `rebuildSortedItemsCache()` après un rearmement, précisément parce que `item.setStatus(...)` n'invalide rien aujourd'hui. Une fois C-103 livré, ce rebuild explicite devient redondant et doit être retiré.

**Constats couverts** : C-114, C-023, C-068, C-069, C-065, C-072, C-088, C-064, C-021, C-071, C-093, C-004, C-092, C-028, C-047, C-018

*~780 LOC · 5 fichiers nouveaux*

## 7.1 — Garde autoritaire de statut dans la re-validation d'achat sous verrou (C-114)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/PurchaseService.java`  
**Risque** : LOW  
**Constats** : C-114

La re-validation sous verrou (l.118-125) ne refuse l'achat que si la ligne a disparu (DELETED) ou porte déjà un acheteur. `ItemRepository.select(int)` ne filtre que DELETED : une ligne déjà basculée en EXPIRED par un autre nœud (ou par la nouvelle tâche d'expiration de l'étape 7) revient ici avec `buyer_unique_id` à null et franchit la garde. L'acheteur paie alors un item que le vendeur est en train de récupérer. `ItemLoaderUtils.createAuctionItem` (l.43-48) traduit fidèlement storage_type en ItemStatus (LISTED->AVAILABLE, EXPIRED->REMOVED, PURCHASED->PURCHASED) : tester le statut de l'objet relu équivaut exactement à tester `storage_type = 'LISTED'`. Ce correctif est la PREMIÈRE étape du chantier parce que l'étape 7 (balayage d'expiration) multiplie mécaniquement le nombre de transitions LISTED->EXPIRED concurrentes : livrer la tâche avant la garde ouvrirait la fenêtre au lieu de la fermer. Je rejette le correctif de la fiche d'origine (horloge partagée `selectDatabaseTime()`) : il ajoute une méthode à l'interface publiée `StorageManager`, exige du SQL brut, et `SELECT UNIX_TIMESTAMP()` n'existe pas en SQLite, qui est le storage-type par défaut.

```java
// Remplacer intégralement le bloc `.thenCompose(dbItem -> { ... })` (l.118-127)

                            .thenCompose(dbItem -> {
                                // Garde autoritaire SOUS VERROU : la ligne doit encore etre LISTED.
                                //
                                // ItemRepository.select(int) ne filtre que DELETED, et
                                // ItemLoaderUtils.createAuctionItem traduit storage_type -> ItemStatus
                                // (LISTED -> AVAILABLE, EXPIRED -> REMOVED, PURCHASED -> PURCHASED) :
                                // tester le statut de l'objet relu equivaut donc exactement a tester
                                // storage_type = 'LISTED' en base, sans SQL brut ni nouvelle methode
                                // sur l'interface publiee StorageManager.
                                //
                                // Sans le terme sur le statut, un item deja bascule en EXPIRED par un
                                // autre noeud (ou par la tache d'expiration planifiee) revient ici avec
                                // buyer_unique_id a null et franchit la garde : l'acheteur paie un item
                                // que le vendeur est en train de recuperer.
                                if (dbItem == null || dbItem.getBuyerUniqueId() != null || dbItem.getStatus() != ItemStatus.AVAILABLE) {
                                    inventoryManager.updateInventory(player);
                                    resultHolder.set(PurchaseResult.failure("Item already sold", PurchaseFailReason.ITEM_NOT_AVAILABLE));
                                    return failedFuture(new IllegalStateException("Item " + item.getId() + " is no longer listed on the authoritative database row"));
                                }
                                return auctionEconomy.has(player.getUniqueId(), requiredBalance);
                            });

// Aucun import a ajouter : ItemStatus est deja importe dans ce fichier.
```

## 7.2 — Libération explicite du statut de confirmation à la déconnexion et à la mort (C-023, volet immédiat)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/buttons/confirm/ConfirmHelper.java + src/main/java/fr/maxlego08/zauctionhouse/listeners/PlayerListener.java`  
**Risque** : MEDIUM  
**Constats** : C-023

Vérifié par grep exhaustif sur `setStatus(ItemStatus.AVAILABLE)` : `ConfirmHelper.onInventoryClose:47` et `onBackClick:72` sont les DEUX SEULS chemins de restauration d'un statut de confirmation dans tout le projet. Les deux dépendent de `PlayerCacheKey.ITEM_SHOW`, que `PlayerListener.onQuit:43` (`removeCache`) détruit, et du dispatch d'inventaire de zMenu, que `VInventoryManager:190` (`if (player.isDead()) return;`) coupe intégralement quand le joueur meurt fenêtre ouverte. Une déconnexion brutale ou une mort pendant une confirmation gèle donc l'item en IS_*_CONFIRM sur TOUT le cluster. Ce volet est le correctif immédiat (0 s de latence) ; l'étape 6 fournit le filet temporel. L'ordre dans `onQuit` devient significatif et doit être commenté, sinon tout code inséré plus tard avant la libération réintroduit le bug.

```java
// ===== 1/2 : ConfirmHelper.java — ajouter cette methode statique publique =====
// (imports a ajouter : rien, tout est deja importe)

    /**
     * Libere le statut de confirmation encore porte par l'item que le joueur avait ouvert,
     * sans dependre du dispatch d'inventaire de zMenu.
     * <p>
     * zMenu saute {@code onInventoryClose} quand le joueur est mort fenetre ouverte
     * (VInventoryManager coupe le dispatch sur {@code player.isDead()}) et le cache joueur
     * peut avoir ete purge avant la fermeture : ces deux chemins laissaient l'item fige en
     * IS_*_CONFIRM sur tout le cluster.
     * <p>
     * <b>Contrainte d'ordre</b> : cette methode lit {@link PlayerCacheKey#ITEM_SHOW}. Elle DOIT
     * etre appelee AVANT tout {@code removeCache} / {@code clearPlayerCache(ITEM_SHOW)}, sinon
     * la reference vers l'item a liberer est deja perdue.
     *
     * @param plugin instance du plugin
     * @param player joueur qui abandonne la confirmation
     */
    public static void releaseConfirmation(@NonNull AuctionPlugin plugin, @NonNull Player player) {
        var manager = plugin.getAuctionManager();
        var cache = manager.getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) return;

        var status = item.getStatus();
        // On ne libere QUE les deux statuts de confirmation. IS_BEING_PURCHASED /
        // IS_BEING_REMOVED signifient que la section critique est deja engagee (argent
        // debite, ligne DB en cours de mutation) : restaurer AVAILABLE la-dessus
        // remettrait en vente un item deja vendu.
        if (status != ItemStatus.IS_PURCHASE_CONFIRM && status != ItemStatus.IS_REMOVE_CONFIRM) return;

        cache.remove(PlayerCacheKey.ITEM_SHOW);

        // L'annonce a expire pendant que la confirmation etait ouverte : la router vers les
        // items expires plutot que de la rediffuser comme disponible.
        if (item.isExpired()) {
            manager.getExpireService().processExpiredItem(item, StorageType.LISTED);
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
            return;
        }

        item.setStatus(ItemStatus.AVAILABLE);
        plugin.getAuctionClusterBridge().notifyItemStatusChange(item, status, ItemStatus.AVAILABLE)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to release confirmation status for item " + item.getId() + ": " + throwable.getMessage());
                    return null;
                });

        manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
        manager.updateListedItems(item, true, player);
    }


// ===== 2/2 : PlayerListener.java — imports + remplacement de onQuit + nouveau handler =====

import fr.maxlego08.zauctionhouse.buttons.confirm.ConfirmHelper;
import org.bukkit.event.entity.PlayerDeathEvent;

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();

        // ORDRE SIGNIFICATIF : releaseConfirmation lit PlayerCacheKey.ITEM_SHOW, que
        // removeCache detruit. Ne JAMAIS inserer avant cette ligne du code qui touche au
        // cache du joueur, sous peine de figer a nouveau l'item en IS_*_CONFIRM sur tout
        // le cluster jusqu'au balayage de maintenance.
        ConfirmHelper.releaseConfirmation(this.plugin, player);

        this.plugin.getAuctionManager().removeCache(player);
        this.plugin.getAuctionManager().getOptionService().clearPlayerOptions(player.getUniqueId());
        this.plugin.getCommandManager().clearCooldowns(player.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // zMenu n'emet pas onInventoryClose quand le joueur meurt fenetre ouverte
        // (VInventoryManager: `if (player.isDead()) return;`), le statut de confirmation
        // resterait donc pose jusqu'au balayage de maintenance.
        ConfirmHelper.releaseConfirmation(this.plugin, event.getEntity());
    }
```

## 7.3 — Unification et durcissement des deux sorties de confirmation (C-068 + C-069)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/buttons/confirm/ConfirmHelper.java`  
**Risque** : MEDIUM  
**Constats** : C-068, C-069

`onInventoryClose:41` teste `item.getStatus() == this.previous` avant de rediffuser ; `onBackClick:72` ne le teste pas et force `setStatus(this.next)` en aveugle. Un clic Retour survenu APRÈS `PurchaseService:107` ou `RemoveService:239` rediffuse donc AVAILABLE par-dessus une section critique déjà engagée : `ConfirmHelper.onInventoryClose` voit ensuite l'item dans l'état « attendu », le repasse AVAILABLE, le rediffuse et le RÉ-AFFICHE sur tous les serveurs — listing fantôme d'un item vendu (C-076 documente cette chaîne). Les deux méthodes divergent aussi sur la purge : `onBackClick` ne vide que ITEMS_LISTED du seul acteur et jamais ITEMS_SEARCH, laissant un id vendu dans le cache de recherche des autres joueurs. Un helper unique supprime les deux divergences par construction. Je REJETTE le correctif de l'audit qui passe `null` en `ignoredPlayer` : combiné au bug `return`/`continue` de ZAuctionManager:956 (prérequis C-045) il change le comportement d'itération ; on conserve `player`. Ce durcissement transforme des réparations accidentelles en blocages visibles : il DOIT être livré après l'étape 2 et avant l'étape 6, qui en est le filet.

```java
// Remplacer integralement onInventoryClose (l.31-57) et onBackClick (l.59-81)

    @Override
    public void onInventoryClose(@NonNull Player player, @NonNull InventoryEngine inventory) {
        super.onInventoryClose(player, inventory);
        releaseConfirmationState(player);
    }

    @Override
    public void onBackClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, @NonNull List<Inventory> oldInventories, @NonNull Inventory toInventory, int slot) {
        super.onBackClick(player, event, inventory, oldInventories, toInventory, slot);
        releaseConfirmationState(player);
    }

    /**
     * Chemin de sortie UNIQUE d'un inventaire de confirmation : fermeture de la fenetre ou
     * clic Retour. Les deux gestes ont exactement la meme semantique (le joueur renonce),
     * ils doivent donc appliquer exactement les memes gardes et la meme purge.
     * <p>
     * C-068 : la garde de statut, jusqu'ici presente uniquement dans {@code onInventoryClose},
     * s'applique desormais aussi au clic Retour. Sans elle, un Retour clique apres que
     * PurchaseService ou RemoveService a pose IS_BEING_*, rediffuse AVAILABLE par-dessus une
     * section critique deja engagee et re-affiche sur tout le reseau un item en cours de vente.
     * <p>
     * C-069 : les deux chemins partagent maintenant la meme purge (ITEMS_LISTED + ITEMS_SEARCH
     * pour TOUS les joueurs). Le clic Retour ne vidait auparavant que ITEMS_LISTED du seul
     * acteur, laissant un identifiant perime dans le cache de recherche des autres joueurs.
     *
     * @param player joueur qui quitte la confirmation
     */
    private void releaseConfirmationState(@NonNull Player player) {
        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) return;

        // Le statut a bouge depuis l'ouverture de la confirmation : une autre chaine (achat
        // ou retrait, local ou distant) a pris la main sur cet item. Ne rien restaurer et
        // surtout ne rien rediffuser : c'est a cette chaine-la de conclure.
        if (item.getStatus() != this.previous) return;

        // Si l'item a expire pendant que l'inventaire de confirmation etait ouvert, on le
        // deplace vers les items expires au lieu de le rediffuser comme disponible.
        if (processIfExpired(player, item)) return;

        item.setStatus(this.next);
        this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, this.previous, this.next)
                .exceptionally(throwable -> {
                    this.plugin.getLogger().warning("Failed to notify item status change on confirmation exit: " + throwable.getMessage());
                    return null;
                });

        manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
        manager.updateListedItems(item, true, player);
    }
```

## 7.4 — Protection des clés « opération en cours » dans /ah admin cache clear (C-065)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/command/commands/admin/cache/CommandAuctionAdminCacheClear.java`  
**Risque** : LOW  
**Constats** : C-065

`clearPlayerCache(target, PlayerCacheKey.values())` (l.57 et l.84) efface ITEM_SHOW, seule référence dont dépend l'unique chemin de restauration du statut (ConfirmHelper). Un `/ah admin cache clear * all` lancé pendant qu'un joueur a une confirmation ouverte gèle l'item en IS_*_CONFIRM sur tout le cluster. La purge « all » efface aussi les clés SELL_*, faisant perdre son panier à un joueur en cours de mise en vente. On dérive donc un tableau CLEARABLE_KEYS, utilisé aux deux appels ET dans la tab-complétion. Pour la voie explicite `/ah admin cache clear Bob ITEM_SHOW` — geste délibéré que la fiche laissait ouvert — je vais plus loin que l'audit : au lieu de la refuser (ce qui exigerait une clé de message dans 6 fichiers de langue), on libère proprement la confirmation AVANT de vider la clé, en réutilisant le point d'entrée de l'étape 2. Le geste admin garde son effet et ne peut plus rien geler.

```java
// ===== imports a ajouter =====
import fr.maxlego08.zauctionhouse.buttons.confirm.ConfirmHelper;
import java.util.EnumSet;

// ===== champs statiques a ajouter en tete de classe =====

    /**
     * Cles qui portent une operation en cours et que la purge « all » ne doit jamais toucher.
     * <p>
     * {@link PlayerCacheKey#ITEM_SHOW} est la plus critique : c'est l'unique reference
     * utilisee par {@code ConfirmHelper} pour restaurer le statut d'un item en IS_*_CONFIRM.
     * L'effacer gele l'item sur tout le cluster jusqu'au balayage de maintenance.
     * Les cles SELL_* portent le panier de mise en vente : les effacer fait perdre au joueur
     * sa selection en cours. PURCHASE_ITEM est le garde anti-double-clic d'achat.
     */
    private static final EnumSet<PlayerCacheKey> PROTECTED_KEYS = EnumSet.of(
            PlayerCacheKey.ITEM_SHOW,
            PlayerCacheKey.PURCHASE_ITEM,
            PlayerCacheKey.SELL_ITEMS,
            PlayerCacheKey.SELL_PRICE,
            PlayerCacheKey.SELL_ECONOMY,
            PlayerCacheKey.SELL_EXPIRED_AT,
            PlayerCacheKey.SELL_AMOUNT
    );

    /** Cles reellement purgeables par le mot-cle « all ». */
    private static final PlayerCacheKey[] CLEARABLE_KEYS = Arrays.stream(PlayerCacheKey.values())
            .filter(key -> !PROTECTED_KEYS.contains(key))
            .toArray(PlayerCacheKey[]::new);

// ===== constructeur : tab-completion (remplacer le bloc addOptionalArg l.33-38) =====

        this.addOptionalArg("key", (sender, args) -> {
            List<String> keys = new ArrayList<>();
            keys.add("all");
            // Les cles protegees ne sont pas proposees : « all » ne les touche pas, et les
            // demander explicitement reste possible mais reste un geste delibere.
            Arrays.stream(CLEARABLE_KEYS).map(PlayerCacheKey::name).forEach(keys::add);
            return keys;
        });

// ===== perform() : remplacer les deux purges « all » =====

            // l.56-58 (wildcard) devient :
                for (Player target : onlinePlayers) {
                    this.auctionManager.clearPlayerCache(target, CLEARABLE_KEYS);
                }

            // l.84 (joueur unique) devient :
            this.auctionManager.clearPlayerCache(target, CLEARABLE_KEYS);

// ===== perform() : voie explicite (remplacer le bloc l.94-95) =====

            // Un admin peut toujours demander explicitement une cle protegee. Pour ITEM_SHOW
            // on libere d'abord proprement la confirmation en cours, sinon vider la cle
            // gelerait l'item en IS_*_CONFIRM sur tout le cluster (meme defaut que la purge
            // « all » que ce correctif vient de fermer).
            if (key == PlayerCacheKey.ITEM_SHOW) {
                ConfirmHelper.releaseConfirmation(this.plugin, target);
            }
            this.auctionManager.clearPlayerCache(target, key);
            message(this.plugin, this.sender, Message.ADMIN_CACHE_CLEARED, "%key%", key.name(), "%player%", target.getName());
```

## 7.5 — Nouvelle section de configuration `maintenance` (additive côté API, inerte côté comportement)

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/configuration/records/MaintenanceConfiguration.java (nouveau) + API/.../configuration/Configuration.java + src/main/java/fr/maxlego08/zauctionhouse/configuration/MainConfiguration.java + les 6 config.yml`  
**Risque** : LOW  
**Constats** : C-023, C-072, C-088

Les étapes 6 et 7 ont besoin de quatre réglages. Je REFUSE d'ajouter des composantes au record publié `PerformanceConfiguration` : ajouter une composante à un record modifie son constructeur canonique, ce qui est une rupture binaire pour l'addon Redis compilé contre un SHA figé et pour tout plugin tiers. Un NOUVEAU record + une méthode `default` sur l'interface `Configuration` (dont l'implémentation par défaut rend les valeurs codées en dur) est source- et binaire-compatible dans les deux sens. Cette étape ne change AUCUN comportement : rien ne lit encore la configuration, le projet compile et le plugin est fonctionnel. C'est volontaire — elle isole la réplication multilingue (6 fichiers) dans un commit trivialement relisable. Le plancher de 60 s sur `confirmation-timeout-seconds` est un garde-fou dur : en dessous, un joueur lent verrait sa confirmation invalidée en pleine lecture et un second acheteur pourrait ouvrir une confirmation concurrente.

```java
// ===== 1/4 : NOUVEAU FICHIER API/src/main/java/fr/maxlego08/zauctionhouse/api/configuration/records/MaintenanceConfiguration.java =====
package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration des taches de maintenance periodiques du plugin.
 *
 * @param expirationSweepIntervalSeconds   intervalle du balayage des annonces arrivees a terme, 0 pour desactiver
 * @param expirationSweepBatchSize         nombre maximum d'annonces traitees par passage
 * @param confirmationTimeoutSeconds       duree au-dela de laquelle un statut IS_*_CONFIRM est rearme, 0 pour desactiver
 * @param confirmationSweepIntervalSeconds intervalle du balayage des statuts de confirmation
 */
public record MaintenanceConfiguration(
        long expirationSweepIntervalSeconds,
        int expirationSweepBatchSize,
        long confirmationTimeoutSeconds,
        long confirmationSweepIntervalSeconds
) {

    public static final long DEFAULT_EXPIRATION_SWEEP_INTERVAL_SECONDS = 60L;
    public static final int DEFAULT_EXPIRATION_SWEEP_BATCH_SIZE = 50;
    public static final long DEFAULT_CONFIRMATION_TIMEOUT_SECONDS = 60L;
    public static final long DEFAULT_CONFIRMATION_SWEEP_INTERVAL_SECONDS = 10L;

    /**
     * Plancher dur du delai de rearmement. En dessous, un joueur lent verrait sa confirmation
     * invalidee en pleine lecture et un second acheteur pourrait ouvrir une confirmation
     * concurrente sur le meme item.
     */
    public static final long MIN_CONFIRMATION_TIMEOUT_SECONDS = 60L;

    /**
     * Valeurs par defaut, utilisees par l'implementation {@code default} de
     * {@code Configuration#getMaintenance()} pour rester binaire-compatible avec les
     * implementations tierces existantes.
     *
     * @return la configuration de maintenance par defaut
     */
    public static MaintenanceConfiguration defaults() {
        return new MaintenanceConfiguration(
                DEFAULT_EXPIRATION_SWEEP_INTERVAL_SECONDS,
                DEFAULT_EXPIRATION_SWEEP_BATCH_SIZE,
                DEFAULT_CONFIRMATION_TIMEOUT_SECONDS,
                DEFAULT_CONFIRMATION_SWEEP_INTERVAL_SECONDS
        );
    }

    public static MaintenanceConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        long expirationInterval = configuration.getLong("maintenance.expiration-sweep-interval-seconds", DEFAULT_EXPIRATION_SWEEP_INTERVAL_SECONDS);
        int batchSize = configuration.getInt("maintenance.expiration-sweep-batch-size", DEFAULT_EXPIRATION_SWEEP_BATCH_SIZE);
        long confirmationTimeout = configuration.getLong("maintenance.confirmation-timeout-seconds", DEFAULT_CONFIRMATION_TIMEOUT_SECONDS);
        long confirmationInterval = configuration.getLong("maintenance.confirmation-sweep-interval-seconds", DEFAULT_CONFIRMATION_SWEEP_INTERVAL_SECONDS);

        if (batchSize <= 0) {
            plugin.getLogger().warning("maintenance.expiration-sweep-batch-size must be > 0, falling back to " + DEFAULT_EXPIRATION_SWEEP_BATCH_SIZE + ".");
            batchSize = DEFAULT_EXPIRATION_SWEEP_BATCH_SIZE;
        }
        if (confirmationInterval <= 0) {
            confirmationInterval = DEFAULT_CONFIRMATION_SWEEP_INTERVAL_SECONDS;
        }
        if (confirmationTimeout > 0 && confirmationTimeout < MIN_CONFIRMATION_TIMEOUT_SECONDS) {
            plugin.getLogger().warning("maintenance.confirmation-timeout-seconds is below the " + MIN_CONFIRMATION_TIMEOUT_SECONDS
                    + "s floor and would cancel confirmations under a player's eyes; using " + MIN_CONFIRMATION_TIMEOUT_SECONDS + "s.");
            confirmationTimeout = MIN_CONFIRMATION_TIMEOUT_SECONDS;
        }

        return new MaintenanceConfiguration(expirationInterval, batchSize, confirmationTimeout, confirmationInterval);
    }

    /**
     * @return {@code true} si le balayage d'expiration est actif
     */
    public boolean isExpirationSweepEnabled() {
        return this.expirationSweepIntervalSeconds > 0;
    }

    /**
     * @return {@code true} si le rearmement des statuts de confirmation est actif
     */
    public boolean isConfirmationSweepEnabled() {
        return this.confirmationTimeoutSeconds > 0;
    }
}


// ===== 2/4 : API/.../api/configuration/Configuration.java =====
import fr.maxlego08.zauctionhouse.api.configuration.records.MaintenanceConfiguration;

    /**
     * Configuration des taches de maintenance periodiques (balayage d'expiration et
     * rearmement des statuts de confirmation).
     * <p>
     * Methode {@code default} volontaire : elle est purement additive et n'oblige aucune
     * implementation tierce existante a etre recompilee.
     *
     * @return la configuration de maintenance
     */
    default MaintenanceConfiguration getMaintenance() {
        return MaintenanceConfiguration.defaults();
    }


// ===== 3/4 : src/main/java/fr/maxlego08/zauctionhouse/configuration/MainConfiguration.java =====
import fr.maxlego08.zauctionhouse.api.configuration.records.MaintenanceConfiguration;

    private MaintenanceConfiguration maintenanceConfiguration;   // champ, pres de performanceConfiguration (l.67)

    // dans load(), juste apres this.performanceConfiguration = ... (l.102) :
        this.maintenanceConfiguration = MaintenanceConfiguration.of(plugin, config);

    @Override
    public MaintenanceConfiguration getMaintenance() {
        return this.maintenanceConfiguration == null ? MaintenanceConfiguration.defaults() : this.maintenanceConfiguration;
    }


// ===== 4/4 : bloc a ajouter dans src/main/resources/config.yml, JUSTE APRES la section
// « performance: » (le YamlUpdater fusionne automatiquement les cles manquantes sur les
// serveurs existants). A REPLIQUER A L'IDENTIQUE dans fr/, es/, it/, id/ et th/ =====

#=============================================================================
# MAINTENANCE TASKS
#=============================================================================

# Periodic background tasks that keep the auction house consistent.
# These tasks are cheap and bounded; disabling them is only recommended for debugging.

maintenance:
  # How often (in seconds) the plugin scans active listings for entries that reached their
  # expiration date. Without this task, a listing only expires when a player happens to open
  # a tab that walks the item list, so a listing can stay for sale long past its deadline.
  # In a multi-server setup each item is expired under the distributed lock, so several
  # servers running this task cannot expire the same item twice.
  # Set to 0 to disable.
  expiration-sweep-interval-seconds: 60

  # Maximum number of listings processed per sweep. A large backlog is cleared progressively
  # over several sweeps instead of in a single burst.
  expiration-sweep-batch-size: 50

  # How long (in seconds) an item may stay in a confirmation state (purchase or removal
  # confirmation GUI) before the plugin releases it back to "available".
  # This is the safety net for a player who disconnects, dies or loses their cache while a
  # confirmation window is open: without it the item stays frozen and unbuyable on every
  # server of the network.
  # Minimum 60 seconds. Set to 0 to disable.
  confirmation-timeout-seconds: 60

  # How often (in seconds) the plugin checks for stale confirmation states.
  confirmation-sweep-interval-seconds: 10
```

## 7.6 — Balayage TTL des statuts de confirmation (C-023 volet temporel + C-072)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/maintenance/ZMaintenanceScheduler.java (nouveau) + src/main/java/fr/maxlego08/zauctionhouse/ZAuctionPlugin.java`  
**Risque** : MEDIUM  
**Constats** : C-023, C-072

ARBITRAGE MAJEUR, divergence assumée de l'audit : la fiche C-072 place le rearmement DANS L'ADDON, avec une clé dans le config.yml de l'addon. Je l'implémente CÔTÉ PLUGIN, dans le même balayage que C-023, pour trois raisons vérifiées. (1) Le serveur ÉMETTEUR ne reçoit pas son propre message (`RedisSubscriberRunnable:121` filtre par serverId) : un rearmement addon-local ne peut par construction jamais débloquer l'item du nœud qui l'a bloqué — la fiche C-072 l'admet elle-même et réclame « le complément côté plugin ». (2) Le même gel se produit en MONO-SERVEUR (déconnexion brutale, `/ah admin cache clear`), où l'addon n'est pas installé : un correctif addon-only ne couvre pas le cas le plus fréquent. (3) Deux TTL concurrents sur le même objet sont un piège de maintenance. Le balayage plugin tourne sur CHAQUE nœud, donc il rearme aussi bien l'item local que la copie reçue par le bus : il subsume intégralement C-072. Choix d'implémentation important : l'horodatage est OBSERVÉ par le balayage lui-même (map `confirmationSince` remplie à la première observation) et non posé aux sites `setStatus`. Cela évite tout conflit textuel avec le chantier 8, qui réécrit précisément `ListedItemsButton:130` et `:229`, et rend le filet robuste quel que soit l'auteur du statut (bouton local, message cluster, commande admin). On ne rearme JAMAIS IS_BEING_* : `RemoveService.restoreStatusOnError` documente que restaurer après le point de non-retour duplique l'item.

```java
// ===== 1/2 : NOUVEAU FICHIER src/main/java/fr/maxlego08/zauctionhouse/maintenance/ZMaintenanceScheduler.java =====
package fr.maxlego08.zauctionhouse.maintenance;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Taches de maintenance periodiques : rearmement des statuts de confirmation oublies et
 * balayage des annonces arrivees a terme.
 * <p>
 * Les deux balayages s'executent hors thread principal et n'y reviennent que pour muter
 * l'etat memoire, via {@code runNextTick}.
 */
public class ZMaintenanceScheduler {

    /**
     * Statuts rearmes par le balayage. IS_BEING_PURCHASED et IS_BEING_REMOVED en sont
     * volontairement absents : passe ce point la section critique est engagee (argent
     * debite, ligne DB en cours de mutation) et restaurer le statut dupliquerait l'item.
     */
    private static final Set<ItemStatus> CONFIRMATION_STATUSES = EnumSet.of(ItemStatus.IS_PURCHASE_CONFIRM, ItemStatus.IS_REMOVE_CONFIRM);

    private static final List<StorageType> SCANNED_STORAGES = List.of(StorageType.LISTED, StorageType.EXPIRED, StorageType.PURCHASED);

    private final AuctionPlugin plugin;

    /**
     * Date de PREMIERE OBSERVATION d'un statut de confirmation, par identifiant d'item.
     * <p>
     * L'horodatage est observe par le balayage lui-meme et non pose aux sites d'appel de
     * {@code setStatus} : le filet fonctionne donc quel que soit l'auteur du statut (bouton
     * local, message recu du cluster, commande admin) et ne touche aucune ligne du chemin
     * chaud d'achat, evitant tout conflit avec la refonte des boutons.
     */
    private final Map<Integer, Long> confirmationSince = new ConcurrentHashMap<>();

    private WrappedTask confirmationTask;
    private WrappedTask expirationTask;

    public ZMaintenanceScheduler(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Demarre les balayages actifs dans la configuration. Idempotent : un appel supplementaire
     * arrete les taches en cours avant de les recreer (utilise par {@code reload()}).
     */
    public void start() {
        stop();

        var configuration = this.plugin.getConfiguration().getMaintenance();
        var scheduler = this.plugin.getScheduler();

        if (configuration.isConfirmationSweepEnabled()) {
            long interval = configuration.confirmationSweepIntervalSeconds();
            this.confirmationTask = scheduler.runTimerAsync(this::sweepConfirmations, interval, interval, TimeUnit.SECONDS);
            this.plugin.getLogger().info("Confirmation status sweep scheduled every " + interval + "s (timeout " + configuration.confirmationTimeoutSeconds() + "s).");
        }

        if (configuration.isExpirationSweepEnabled()) {
            long interval = configuration.expirationSweepIntervalSeconds();
            this.expirationTask = scheduler.runTimerAsync(this::sweepExpirations, interval, interval, TimeUnit.SECONDS);
            this.plugin.getLogger().info("Expiration sweep scheduled every " + interval + "s (batch " + configuration.expirationSweepBatchSize() + ").");
        }
    }

    /**
     * Arrete les balayages et oublie les horodatages observes.
     */
    public void stop() {
        if (this.confirmationTask != null) {
            this.confirmationTask.cancel();
            this.confirmationTask = null;
        }
        if (this.expirationTask != null) {
            this.expirationTask.cancel();
            this.expirationTask = null;
        }
        this.confirmationSince.clear();
    }

    /**
     * Rearme tout statut de confirmation observe depuis plus de
     * {@code maintenance.confirmation-timeout-seconds}.
     * <p>
     * Le rearmement est diffuse au cluster UNIQUEMENT pour les items detenus en LISTED :
     * pour un item detenu en EXPIRED ou PURCHASED, on se contente de reparer l'etat local
     * vers le statut coherent avec son conteneur (la barriere de cycle de vie du listener
     * refuserait de toute facon un statut du cycle LISTED sur un tel item).
     */
    private void sweepConfirmations() {
        var configuration = this.plugin.getConfiguration().getMaintenance();
        if (!configuration.isConfirmationSweepEnabled()) return;

        long timeoutMs = configuration.confirmationTimeoutSeconds() * 1000L;
        long now = System.currentTimeMillis();
        var manager = this.plugin.getAuctionManager();

        List<PendingRelease> toRelease = new ArrayList<>();
        Set<Integer> stillPending = new HashSet<>();

        for (StorageType storageType : SCANNED_STORAGES) {
            for (Item item : manager.getItems(storageType)) {
                if (!CONFIRMATION_STATUSES.contains(item.getStatus())) continue;

                stillPending.add(item.getId());
                Long since = this.confirmationSince.putIfAbsent(item.getId(), now);
                if (since != null && now - since >= timeoutMs) {
                    toRelease.add(new PendingRelease(storageType, item));
                }
            }
        }

        // Les items qui ne portent plus de statut de confirmation sortent de la map : elle
        // reste bornee par le nombre de confirmations reellement ouvertes.
        this.confirmationSince.keySet().retainAll(stillPending);
        if (toRelease.isEmpty()) return;

        this.plugin.getScheduler().runNextTick(w -> {
            boolean listedChanged = false;

            for (PendingRelease pending : toRelease) {
                var item = pending.item();
                var status = item.getStatus();
                // Le statut a pu bouger entre le balayage et le tick : ne rien forcer.
                if (!CONFIRMATION_STATUSES.contains(status)) continue;

                this.confirmationSince.remove(item.getId());
                var target = targetStatusFor(pending.storageType());
                item.setStatus(target);

                this.plugin.getLogger().warning("Item " + item.getId() + " stayed in " + status + " for more than "
                        + configuration.confirmationTimeoutSeconds() + "s, releasing it to " + target + ".");

                if (pending.storageType() == StorageType.LISTED) {
                    listedChanged = true;
                    this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, status, target)
                            .exceptionally(throwable -> {
                                this.plugin.getLogger().warning("Failed to broadcast the released confirmation status of item "
                                        + item.getId() + ": " + throwable.getMessage());
                                return null;
                            });
                }
            }

            if (!listedChanged) return;

            // item.setStatus(...) n'invalide pas le cache trie aujourd'hui (voir C-103) :
            // sans ce rebuild explicite, l'item rearme resterait absent de la liste affichee.
            manager.rebuildSortedItemsCache();
            manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH, PlayerCacheKey.ITEMS_SELLING);
        });
    }

    private static ItemStatus targetStatusFor(StorageType storageType) {
        return switch (storageType) {
            case EXPIRED -> ItemStatus.REMOVED;
            case PURCHASED -> ItemStatus.PURCHASED;
            default -> ItemStatus.AVAILABLE;
        };
    }

    private void sweepExpirations() {
        // Implemente a l'etape 7.
    }

    private record PendingRelease(StorageType storageType, Item item) {
    }
}


// ===== 2/2 : ZAuctionPlugin.java =====
import fr.maxlego08.zauctionhouse.maintenance.ZMaintenanceScheduler;

    private final ZMaintenanceScheduler maintenanceScheduler = new ZMaintenanceScheduler(this);

    // dans onEnable(), juste apres this.storageManager.loadItems(); (l.160) :
        this.maintenanceScheduler.start();

    // dans onDisable(), en tete du corps utile, avant auctionManager.shutdown() :
        this.maintenanceScheduler.stop();

    // dans reload(), a la fin (apres this.auctionManager.updateItemEconomies();) :
        // Les intervalles ont pu changer dans config.yml.
        this.maintenanceScheduler.start();

    public ZMaintenanceScheduler getMaintenanceScheduler() {
        return this.maintenanceScheduler;
    }
```

## 7.7 — Tâche d'expiration planifiée (C-088)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/maintenance/ZMaintenanceScheduler.java`  
**Risque** : MEDIUM  
**Constats** : C-088

Vérifié : `grep runTimer src/main/java` ne rend que `BossBarAnimation`. AUCUNE tâche périodique n'expire quoi que ce soit. L'expiration n'est déclenchée que par `ZAuctionManager.getItemIds` (l.407-419), que la liste principale de l'hôtel des ventes n'emprunte jamais (elle passe par `SortedItemsCache`). Une annonce arrivée à terme reste donc en vente jusqu'à ce qu'un joueur ouvre par hasard un onglet qui itère le store. ARBITRAGE, divergence de l'audit : la fiche exige une élection de leader (`SET NX` sur `auction:expire:leader`) pour éviter que N nœuds expirent le même item N fois. C'est INUTILE et j'écarte cette complexité : en mode distribué, `ExpireService.processExpiredItems` route chaque item vers `expireListedItemClustered`, qui acquiert le verrou distribué, relit la ligne autoritaire et abandonne sur jeton noop — l'exclusion par item est déjà là et elle est plus fine qu'une élection de leader (elle protège aussi contre l'expiration concurrente d'un achat en cours, ce qu'un leader ne fait pas). L'élection de leader ajouterait une primitive absente de `AuctionClusterBridge`, donc une rupture d'API, pour zéro sûreté supplémentaire. Le lot est borné et la fenêtre filtrée sur AVAILABLE pour ne jamais toucher un item engagé dans une section critique.

```java
// Remplacer le stub sweepExpirations() de l'etape 6 par cette implementation.

    /**
     * Balaye les annonces actives arrivees a terme et les route vers le service d'expiration.
     * <p>
     * Sans cette tache, l'expiration n'est declenchee que par {@code ZAuctionManager.getItemIds},
     * que la liste principale de l'hotel des ventes n'emprunte jamais (elle lit
     * {@code SortedItemsCache}) : une annonce peut rester en vente indefiniment apres sa date
     * limite, jusqu'a ce qu'un joueur ouvre par hasard un onglet qui itere le store.
     * <p>
     * <b>Sûrete en cluster</b> : en mode distribue,
     * {@code ExpireService.processExpiredItems} route chaque item vers le chemin
     * {@code expireListedItemClustered}, qui acquiert le verrou distribue, relit la ligne
     * autoritaire en base et abandonne si un autre noeud detient deja le verrou. L'exclusion
     * est donc portee par item, ce qui est strictement plus fin qu'un noeud elu : deux serveurs
     * executant ce balayage en meme temps ne peuvent pas expirer deux fois le meme item, et un
     * item en cours d'achat n'est pas expire sous les pieds de l'acheteur.
     * <p>
     * Le lot est borne : un arriere important est resorbe progressivement sur plusieurs
     * passages plutot qu'en une rafale de verrouillages distribues.
     */
    private void sweepExpirations() {
        var configuration = this.plugin.getConfiguration().getMaintenance();
        if (!configuration.isExpirationSweepEnabled()) return;

        int batchSize = configuration.expirationSweepBatchSize();
        var manager = this.plugin.getAuctionManager();

        List<Item> expired = new ArrayList<>(batchSize);
        for (Item item : manager.getItems(StorageType.LISTED)) {
            if (!item.isExpired()) continue;

            // On ne touche QUE les annonces au repos. Un item en IS_*_CONFIRM ou IS_BEING_*
            // appartient a une chaine en cours (confirmation ouverte, achat ou retrait
            // engage) : c'est a cette chaine de conclure, et le balayage de confirmation
            // se charge de la debloquer si elle ne revient jamais.
            if (item.getStatus() != ItemStatus.AVAILABLE) continue;

            expired.add(item);
            if (expired.size() >= batchSize) break;
        }

        if (expired.isEmpty()) return;

        this.plugin.getLogger().info("Expiration sweep: processing " + expired.size() + " expired listing(s).");
        this.plugin.getScheduler().runNextTick(w -> manager.getExpireService().processExpiredItems(expired, StorageType.LISTED));
    }
```

## 7.8 — /ah admin add : application des catégories et annonce au cluster (C-064)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/command/commands/admin/CommandAuctionAdminAdd.java`  
**Risque** : MEDIUM  
**Constats** : C-064

Les trois branches créent une ligne en base mais n'annoncent rien au bus : l'item est invisible sur tous les autres serveurs jusqu'au prochain redémarrage. Défaut connexe trouvé en lisant le code et non répertorié par l'audit : `addListed` n'appelle JAMAIS `applyCategories`, alors que les trois autres chemins de publication le font (`SellService.postSell`, `CommandAuctionAdminGenerate`, `ItemLoaderUtils`) — l'item apparaît dans « toutes catégories » mais disparaît dès qu'un joueur filtre, et il manque aux compteurs de catégorie. JE M'ÉCARTE DU CORRECTIF DE L'AUDIT sur addExpired/addPurchased : il demande « appeler notifyItemListed PUIS clusterBridge.removeItem ». C'est faux et dangereux — `RedisAuctionClusterBridge.notifyItemListed` pose `state=AVAILABLE` et publie `ItemListedMessage`, et `ItemListedListener` fait `addItem(LISTED, item)` sur tous les autres nœuds : on créerait une fenêtre d'un aller-retour Redis complet pendant laquelle un item déjà expiré ou déjà acheté est ACHETABLE dans le HDV principal du reste du réseau. `removeItem(item, LISTED, EXPIRED)` suffit : `ItemRemovedListener` purge l'id de tous les stores puis, destination non-DELETED, recharge l'item depuis la base dans le bon store. La diffusion est chaînée SUR le future de `updateItem` parce que le listener distant relit la ligne : elle doit être committée avant.

```java
// ===== addListed — remplacer integralement (l.98-109) =====

    private void addListed(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        long expiredAt = plugin.getConfiguration().getSellExpiration().getExpiration(target);
        expiredAt = expiredAt > 0 ? System.currentTimeMillis() + (expiredAt * 1000) : 0;

        this.plugin.getStorageManager().createAuctionItem(target, price, expiredAt, List.of(cloned), economy)
                .thenAccept(item -> {
                    // Sans applyCategories, l'item apparait dans « toutes categories » mais
                    // n'est rattache a AUCUNE categorie : invisible des qu'un joueur filtre, et
                    // absent des compteurs de categorie. Les trois autres chemins de publication
                    // l'appellent deja (SellService.postSell, CommandAuctionAdminGenerate,
                    // ItemLoaderUtils) : celui-ci etait le seul oubli.
                    this.plugin.getCategoryManager().applyCategories(item);

                    this.auctionManager.addItem(StorageType.LISTED, item);
                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
                    this.auctionManager.updateListedItems(item, true, target);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, "%items%", item.getItemDisplay(), "%target%", target.getName(), "%type%", "listed");

                    // Annonce au cluster, exactement comme SellService.postSell : sans elle
                    // l'annonce reste invisible sur tous les autres serveurs jusqu'au prochain
                    // redemarrage.
                    this.plugin.getAuctionClusterBridge().notifyItemListed(item)
                            .exceptionally(throwable -> {
                                this.plugin.getLogger().severe("Failed to broadcast admin-added listing " + item.getId() + ": " + throwable.getMessage());
                                return null;
                            });
                });
    }

// ===== addExpired — remplacer integralement (l.111-121) =====

    private void addExpired(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        this.plugin.getStorageManager().createAuctionItem(target, price, System.currentTimeMillis(), List.of(cloned), economy)
                .thenAccept(item -> {
                    item.setStatus(ItemStatus.REMOVED);
                    item.setExpiredAt(new Date());
                    this.auctionManager.addItem(StorageType.EXPIRED, item);
                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_SELLING);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, "%items%", item.getItemDisplay(), "%target%", target.getName(), "%type%", "expired");

                    // createAuctionItem insere TOUJOURS la ligne en LISTED : on la bascule en
                    // base PUIS on diffuse. La diffusion est chainee sur le future d'ecriture
                    // parce que ItemRemovedListener relit la ligne en base pour replacer l'item
                    // dans le bon store cote distant : elle doit etre committee avant.
                    //
                    // NE PAS diffuser notifyItemListed ici : cela poserait state=AVAILABLE et
                    // ferait addItem(LISTED, ...) sur tous les autres noeuds, affichant comme
                    // ACHETABLE un item deja expire, le temps d'un aller-retour Redis complet.
                    this.plugin.getStorageManager().updateItem(item, StorageType.EXPIRED)
                            .thenCompose(v -> this.plugin.getAuctionClusterBridge().removeItem(item, StorageType.LISTED, StorageType.EXPIRED))
                            .exceptionally(throwable -> {
                                this.plugin.getLogger().severe("Failed to persist or broadcast admin-added expired item " + item.getId() + ": " + throwable.getMessage());
                                return null;
                            });
                });
    }

// ===== addPurchased — remplacer integralement (l.123-133) =====

    private void addPurchased(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        this.plugin.getStorageManager().createAuctionItem(admin, price, System.currentTimeMillis(), List.of(cloned), economy)
                .thenAccept(item -> {
                    item.setBuyer(target);
                    item.setStatus(ItemStatus.PURCHASED);
                    this.auctionManager.addItem(StorageType.PURCHASED, item);
                    this.auctionManager.clearPlayerCache(target, PlayerCacheKey.ITEMS_PURCHASED);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, "%items%", item.getItemDisplay(), "%target%", target.getName(), "%type%", "purchased");

                    // Meme raisonnement que addExpired : commit d'abord, diffusion ensuite,
                    // et surtout PAS de notifyItemListed sur un item deja achete.
                    this.plugin.getStorageManager().updateItem(item, StorageType.PURCHASED)
                            .thenCompose(v -> this.plugin.getAuctionClusterBridge().removeItem(item, StorageType.LISTED, StorageType.PURCHASED))
                            .exceptionally(throwable -> {
                                this.plugin.getLogger().severe("Failed to persist or broadcast admin-added purchased item " + item.getId() + ": " + throwable.getMessage());
                                return null;
                            });
                });
    }
```

## 7.9 — Addon : primitive d'ordonnancement des messages par item (C-021, C-071 volet chronologique)

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/listener/ItemMessageGenerations.java (nouveau) + ZAuctionHouseRedis.java + les 4 listeners`  
**Risque** : MEDIUM  
**Constats** : C-021, C-071

Les quatre listeners appliquent leurs effets par des chemins de latence hétérogènes : l'étape 1 est un `runNextTick` immédiat, l'étape 2 un SELECT JDBC suivi d'un `runNextTick`. Aucun ordonnancement par identifiant n'existe. Un message « retiré » dont le SELECT a été résolu avant le commit du DELETE ré-ajoute l'item après qu'un message plus récent l'a purgé. Le correctif proposé par l'audit pour C-071 (comparer le statut relu à la destination annoncée) NE FERME PAS la chronologie que la fiche décrit elle-même — dans ce scénario l'instantané rend bien REMOVED, qui « correspond » à EXPIRED, et la garde le laisse passer. Seule une garde de génération ferme ce cas. Une seule primitive partagée subsume C-021 et le volet chronologique de C-071, et sert aussi de barrière au ré-ajout de C-093. Elle est 100 % addon-locale : aucune modification de l'API, aucun bump du pin `zauctionhousev4-api`.

```java
// ===== 1/3 : NOUVEAU FICHIER REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/listener/ItemMessageGenerations.java =====
package fr.maxlego08.zauctionhouse.redis.listener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ordonnancement des messages du bus par identifiant d'item.
 * <p>
 * Chaque listener appelle {@link #next(int)} en tete de {@code onMessage} et conserve la
 * generation obtenue. Toute etape differee (typiquement le ré-ajout en memoire qui suit un
 * aller-retour vers la base) verifie {@link #isCurrent(int, long)} juste avant de muter le
 * store : si un message plus recent est arrive entre-temps pour le meme item, l'etape est
 * abandonnee.
 * <p>
 * Sans cette primitive, un message « retire » dont le SELECT a ete resolu avant le commit du
 * DELETE ressuscite en memoire un item qu'un message plus recent venait de purger.
 * <p>
 * La table est bornee : une entree qui n'a recu aucun message depuis {@code ENTRY_TTL_MS} est
 * purgee, la purge etant declenchee au fil de l'eau par {@link #next(int)}.
 */
public final class ItemMessageGenerations {

    private static final long ENTRY_TTL_MS = 300_000L;
    private static final long PURGE_INTERVAL_MS = 60_000L;

    private final Map<Integer, Generation> generations = new ConcurrentHashMap<>();
    private final AtomicLong lastPurgeMs = new AtomicLong(System.currentTimeMillis());

    private static final class Generation {
        private final AtomicLong value = new AtomicLong();
        private volatile long lastTouchMs = System.currentTimeMillis();
    }

    /**
     * Enregistre l'arrivee d'un nouveau message pour cet item et rend la generation associee.
     *
     * @param itemId identifiant de l'item concerne
     * @return la generation de ce message
     */
    public long next(int itemId) {
        purgeIfNeeded();
        Generation generation = this.generations.computeIfAbsent(itemId, id -> new Generation());
        generation.lastTouchMs = System.currentTimeMillis();
        return generation.value.incrementAndGet();
    }

    /**
     * Indique si la generation fournie est toujours la plus recente pour cet item.
     *
     * @param itemId     identifiant de l'item
     * @param generation generation obtenue par {@link #next(int)}
     * @return {@code false} si un message plus recent est arrive pour cet item
     */
    public boolean isCurrent(int itemId, long generation) {
        Generation current = this.generations.get(itemId);
        // Entree deja purgee : aucun message n'est arrive pour cet item depuis ENTRY_TTL_MS,
        // il ne peut donc plus y avoir de message concurrent en vol. On laisse passer.
        return current == null || current.value.get() == generation;
    }

    /**
     * Oublie toutes les generations. Appele a l'arret de l'addon.
     */
    public void clear() {
        this.generations.clear();
    }

    private void purgeIfNeeded() {
        long now = System.currentTimeMillis();
        long last = this.lastPurgeMs.get();
        if (now - last < PURGE_INTERVAL_MS) return;
        if (!this.lastPurgeMs.compareAndSet(last, now)) return;
        this.generations.entrySet().removeIf(entry -> now - entry.getValue().lastTouchMs > ENTRY_TTL_MS);
    }
}


// ===== 2/3 : ZAuctionHouseRedis.java — champ + accesseur + nettoyage =====
import fr.maxlego08.zauctionhouse.redis.listener.ItemMessageGenerations;

    private final ItemMessageGenerations messageGenerations = new ItemMessageGenerations();

    public ItemMessageGenerations getMessageGenerations() {
        return this.messageGenerations;
    }

    // dans onDisable(), avant la fermeture du pool :
        this.messageGenerations.clear();


// ===== 3/3 : cablage dans les QUATRE listeners =====
// Dans chaque onMessage, immediatement apres la resolution de `int id` :

        // Enregistre l'arrivee de ce message : toute etape differee de ce listener verifiera
        // que sa generation est toujours la plus recente avant de muter le store memoire.
        long generation = this.plugin.getMessageGenerations().next(id);

// ItemRemovedListener — dans les DEUX blocs d'etape 2, en tete du runNextTick :
                auctionPlugin.getScheduler().runNextTick(w -> {
                    if (!this.plugin.getMessageGenerations().isCurrent(id, generation)) {
                        this.plugin.debug("Dropping stale re-add of item " + id + ": a newer bus message superseded it.");
                        return;
                    }
                    var manager = auctionPlugin.getAuctionManager();
                    manager.addItem(destination, item);
                    manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED);
                });

// ItemBoughtListener — etape 2, en tete du runNextTick (l.104) : meme garde.
// ItemListedListener — etape 2 : garde ajoutee a l'etape 10, dans le meme runNextTick.
// ItemStatusListener — pas d'etape differee, mais l'appel a next(id) est INDISPENSABLE :
//   c'est lui qui invalide un re-ajout encore en vol pour le meme item.
```

## 7.10 — Addon : durcissement de ItemListedListener et garde de cohérence des ré-ajouts (C-093 + C-071 volet cohérence)

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/listener/listeners/ItemListedListener.java + ItemRemovedListener.java`  
**Risque** : MEDIUM  
**Constats** : C-093, C-071

`ItemRepository.select(int)` ne filtre que DELETED et le statut est dérivé du storage_type : un item déjà PURCHASED sur un autre nœud revient de `selectItem` et est injecté tel quel dans le store LISTED par `ItemListedListener:44` — annonce fantôme achetable. Deuxième défaut : ITEMS_SELLING n'est pas purgé, donc le vendeur ne voit pas sa nouvelle annonce et son compteur de limite (`SellService.validateItems`) reste faux sur ce serveur. Troisième : `applyCategories` exécute aujourd'hui les règles des hooks (ItemsAdder, MMOItems, Oraxen, Nexo, Slimefun, CraftEngine, ExecutableItems) sur le THREAD ABONNÉ REDIS, hors thread principal. Je M'ÉCARTE de la fiche C-093 sur un point : je n'inclus PAS `item.isExpired()` dans le test de rejet. Sur un parc mal synchronisé NTP, ce terme rejetterait des annonces légitimes et les rendrait invisibles sur ce nœud jusqu'au redémarrage — aucun mécanisme ne répare cela. Une annonce déjà expirée injectée en LISTED est désormais ramassée en moins de 60 s par la tâche de l'étape 7 et proprement basculée en EXPIRED : c'est strictement meilleur qu'un rejet silencieux. Côté ItemRemovedListener, la garde de cohérence statut/destination complète la garde de génération de l'étape 9 pour le cas où la ligne a changé de conteneur non terminal pendant le vol du SELECT.

```java
// ===== 1/2 : ItemListedListener.java — remplacer le corps du thenAccept (l.31-53) =====
// imports a ajouter : fr.maxlego08.zauctionhouse.api.item.ItemStatus

        auctionPlugin.getStorageManager().selectItem(id).thenAccept(item -> {

            if (item == null) {
                this.plugin.getLogger().severe("Unable to find the item " + message.itemId());
                return;
            }

            // L'instantane relu doit etre REELLEMENT publiable. ItemRepository.select(int) ne
            // filtre que DELETED et le statut est derive du storage_type
            // (ItemLoaderUtils) : un item deja PURCHASED ou deja bascule en EXPIRED sur un
            // autre noeud revient donc ici et serait injecte tel quel dans le store LISTED,
            // ou il deviendrait une annonce fantome achetable.
            //
            // NOTE : on ne teste volontairement PAS item.isExpired() ici. Sur un parc dont les
            // horloges divergent, ce terme rejetterait des annonces legitimes et les rendrait
            // invisibles sur ce noeud jusqu'au redemarrage. Une annonce deja arrivee a terme
            // est desormais ramassee par la tache d'expiration planifiee du plugin principal
            // et proprement basculee en EXPIRED.
            if (item.getStatus() != ItemStatus.AVAILABLE || item.getBuyerUniqueId() != null) {
                this.plugin.debug("Ignoring listing broadcast for item " + id + ": authoritative state is " + item.getStatus());
                return;
            }

            var manager = auctionPlugin.getAuctionManager();

            // Toutes les mutations d'etat ET l'application des categories sur le thread
            // principal : applyCategories execute les regles des hooks (ItemsAdder, MMOItems,
            // Oraxen, Nexo, Slimefun, CraftEngine, ExecutableItems), qui ne sont pas
            // thread-safe et tournaient jusqu'ici sur le thread abonne Redis.
            auctionPlugin.getScheduler().runNextTick(wrappedTask -> {

                if (!this.plugin.getMessageGenerations().isCurrent(id, generation)) {
                    this.plugin.debug("Dropping stale listing insertion for item " + id + ": a newer bus message superseded it.");
                    return;
                }

                auctionPlugin.getCategoryManager().applyCategories(item);

                manager.addItem(StorageType.LISTED, item);
                // ITEMS_SELLING est indispensable : sans elle le vendeur ne voit pas sa
                // nouvelle annonce sur ce serveur et son compteur de limite reste faux.
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH, PlayerCacheKey.ITEMS_SELLING);

                manager.updateListedItems(item, true, null);
            });
        }).exceptionally(throwable -> {
            this.plugin.getLogger().severe("Failed to handle item listing: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });


// ===== 2/2 : ItemRemovedListener.java — garde de coherence + helper =====
// A inserer dans les DEUX blocs d'etape 2, juste apres `if (item == null) return;` :

                // Coherence : le conteneur reellement relu en base doit correspondre a la
                // destination annoncee par le message. Couvre le cas ou la ligne a change de
                // conteneur non terminal pendant le vol du SELECT. Ne remplace PAS la garde de
                // generation : celle-ci seule ferme la chronologie « SELECT resolu avant le
                // commit du DELETE », ou l'instantane rend REMOVED et « correspond » a EXPIRED.
                if (!matchesDestination(item.getStatus(), destination)) {
                    this.plugin.debug("Ignoring re-add of item " + id + " into " + destination
                            + ": authoritative status is " + item.getStatus() + ".");
                    return;
                }

// Helper prive a ajouter en fin de classe :

    /**
     * Verifie que le statut autoritaire relu en base correspond bien au conteneur dans lequel
     * le message demande de replacer l'item.
     *
     * @param status      statut relu en base
     * @param destination conteneur annonce par le message
     * @return {@code true} si les deux sont coherents
     */
    private static boolean matchesDestination(ItemStatus status, StorageType destination) {
        if (destination == null) return false;
        return switch (destination) {
            case EXPIRED -> status == ItemStatus.REMOVED;
            case PURCHASED -> status == ItemStatus.PURCHASED;
            case LISTED -> status == ItemStatus.AVAILABLE;
            default -> false;
        };
    }

// Dans la branche de compatibilite ascendante (destination == null, l.64-90), la destination
// effective est DERIVEE du statut relu : la garde y est structurellement satisfaite, ne pas
// l'y ajouter. Seule la garde de generation de l'etape 9 s'y applique.
```

## 7.11 — Addon : compare-and-swap de statut et barrière de cycle de vie (C-004)

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/listener/listeners/ItemStatusListener.java`  
**Risque** : LOW  
**Constats** : C-004

`ItemStatusListener:54` applique `message.newStatus()` en aveugle. Un message en retard ou réordonné écrase un statut plus récent : un item déjà PURCHASED redevient IS_PURCHASE_CONFIRM et n'est plus jamais réclamable. `ItemStatusMessage` transporte déjà `oldStatus` et les six appelants de `notifyItemStatusChange` le renseignent tous explicitement — le compare-and-swap ne coûte rien. La seconde barrière refuse tout statut du cycle LISTED pour un item détenu en EXPIRED ou PURCHASED : c'est elle qui empêche le scénario d'irréversibilité le plus grave, et c'est le préalable dur du correctif C-035 (chantier 7 bis) qui diffuse un statut terminal après abandon d'achat. La transition REMOVED/PURCHASED -> DELETED reste autorisée : DELETED n'appartient pas au cycle LISTED. La garde `oldStatus != null` conserve la compatibilité avec une charge utile d'ancienne version pendant une mise à jour progressive. CE CORRECTIF NE PEUT PAS ÊTRE LIVRÉ SEUL : le CAS transforme une perte de message pub/sub en désynchronisation PERMANENTE là où l'application aveugle finissait par se resynchroniser par hasard. C'est exactement ce que compense le balayage TTL de l'étape 6, qui doit être dans la même release.

```java
// Remplacer integralement le corps du runNextTick (l.39-64)
// imports a ajouter : java.util.EnumSet, java.util.Set

    /**
     * Statuts du cycle de vie d'une annonce active. Un item detenu en EXPIRED ou PURCHASED ne
     * peut plus en recevoir aucun : la transition REMOVED/PURCHASED -> DELETED reste en
     * revanche legitime, DELETED n'appartenant pas a ce cycle.
     */
    private static final Set<ItemStatus> LISTED_CYCLE_STATUSES = EnumSet.of(
            ItemStatus.AVAILABLE,
            ItemStatus.IS_PURCHASE_CONFIRM,
            ItemStatus.IS_REMOVE_CONFIRM,
            ItemStatus.IS_BEING_PURCHASED,
            ItemStatus.IS_BEING_REMOVED
    );

        auctionPlugin.getScheduler().runNextTick(wrappedTask -> {
            var manager = auctionPlugin.getAuctionManager();

            Item resolved = null;
            StorageType resolvedIn = null;
            for (StorageType storageType : SEARCHABLE_STORAGE_TYPES) {
                var optional = manager.getItems(storageType).stream().filter(e -> e.getId() == id).findFirst();
                if (optional.isPresent()) {
                    resolved = optional.get();
                    resolvedIn = storageType;
                    break;
                }
            }

            if (resolved == null) return;

            final Item item = resolved;
            final StorageType foundIn = resolvedIn;

            var newStatus = message.newStatus();
            if (newStatus == null) {
                // Constante d'enum inconnue de cette version (mise a jour progressive) :
                // ne JAMAIS poser null, item.isActivelyListed() rendrait l'item invisible
                // partout et chaque switch sur le statut leverait une NPE.
                this.plugin.getLogger().warning("Unknown item status received for item " + id + ", message ignored.");
                return;
            }

            // 1) Compare-and-swap : n'appliquer le nouveau statut que si l'etat local est bien
            // celui que l'emetteur avait observe. Sans cette garde, un message en retard ou
            // reordonne ecrase un statut plus recent : un item deja PURCHASED redevient
            // IS_PURCHASE_CONFIRM et n'est plus jamais reclamable par son acheteur.
            // oldStatus peut etre null sur une charge utile d'ancienne version pendant une
            // mise a jour progressive : on retombe alors sur l'ancien comportement.
            var oldStatus = message.oldStatus();
            if (oldStatus != null && item.getStatus() != oldStatus) {
                this.plugin.debug("Skipping status change for item " + id + ": local status is "
                        + item.getStatus() + " but the sender observed " + oldStatus + ".");
                return;
            }

            // 2) Barriere de cycle de vie : un item detenu en EXPIRED ou PURCHASED ne peut pas
            // recevoir un statut du cycle LISTED, quelle que soit la valeur de oldStatus.
            if (foundIn != StorageType.LISTED && LISTED_CYCLE_STATUSES.contains(newStatus)) {
                this.plugin.getLogger().warning("Refusing LISTED-cycle status " + newStatus + " for item "
                        + id + " held in " + foundIn + ".");
                return;
            }

            if (item.getStatus() == newStatus) return; // message sans effet

            item.setStatus(newStatus);

            if (foundIn != StorageType.LISTED) return; // rien a rafraichir dans le HDV principal

            if (newStatus == ItemStatus.IS_PURCHASE_CONFIRM || newStatus == ItemStatus.IS_REMOVE_CONFIRM
                    || newStatus == ItemStatus.IS_BEING_REMOVED || newStatus == ItemStatus.IS_BEING_PURCHASED) {
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING);
                manager.updateListedItems(item, false, null);
            } else if (newStatus == ItemStatus.AVAILABLE) {
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING);
                manager.updateListedItems(item, true, null);
            }
        });
```

## 7.12 — Addon : purge visuelle des HDV déjà ouverts au retrait et à l'achat (C-092)

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/utils/ItemLookup.java (nouveau) + ItemRemovedListener.java + ItemBoughtListener.java`  
**Risque** : MEDIUM  
**Constats** : C-092

`clearPlayersCache` ne fait que vider une clé (`ZAuctionManager:492-494`), il ne redessine RIEN : les slots d'un item vendu ou retiré ailleurs restent affichés et cliquables dans les HDV déjà ouverts, jusqu'au prochain événement. `ItemListedListener` appelle bien `updateListedItems`, les deux autres jamais. PIÈGE D'ORDONNANCEMENT que le correctif de l'audit ne mentionne pas : `updateListedItems` n'est pas synchrone (il enchaîne sur `ensureCacheValidAsync().thenRun`), et `ListedItemsButton.updateInventory` a besoin que l'id soit ENCORE présent dans `ITEMS_LISTED` du spectateur — sinon `findIndexOf` rend -1 et aucun slot n'est retiré. Donc : appeler `updateListedItems` AVANT le retrait du store, et surtout NE PAS vider ITEMS_LISTED quand l'objet existait localement (`updateListedItems` l'entretient de façon incrémentale via `removeFromCache`). C'est exactement ce que fait déjà le chemin d'achat local. Second défaut, absent de la fiche mais réel : `ItemRemovedListener` n'inclut ITEMS_SEARCH dans AUCUN de ses trois `clearPlayersCache`, alors que `ItemBoughtListener` l'inclut — un joueur en recherche active garde un id vendu dans sa liste, et c'est cet id qu'un clic résout.

```java
// ===== 1/3 : NOUVEAU FICHIER REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/utils/ItemLookup.java =====
package fr.maxlego08.zauctionhouse.redis.utils;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

/**
 * Recherche d'un item dans un conteneur memoire du plugin principal.
 * <p>
 * Implementation volontairement addon-locale et en O(n) : l'API publiee
 * {@code AuctionManager} n'expose pas encore d'accesseur par identifiant. Des que
 * {@code AuctionManager.getItem(StorageType, int)} sera publie et le pin
 * {@code zauctionhousev4-api} de l'addon bumpe, seul le CORPS de cette methode change.
 */
public final class ItemLookup {

    private ItemLookup() {
    }

    /**
     * @param manager     gestionnaire d'encheres du plugin principal
     * @param storageType conteneur a fouiller
     * @param itemId      identifiant recherche
     * @return l'item detenu localement, ou {@code null}
     */
    public static Item find(AuctionManager manager, StorageType storageType, int itemId) {
        for (Item item : manager.getItems(storageType)) {
            if (item.getId() == itemId) return item;
        }
        return null;
    }
}


// ===== 2/3 : ItemRemovedListener.java — remplacer l'etape 1 (l.34-44) =====
// imports a ajouter : fr.maxlego08.zauctionhouse.api.item.Item ; ...redis.utils.ItemLookup

        // Etape 1 : retirer l'annonce des conteneurs memoire et des HDV deja ouverts.
        auctionPlugin.getScheduler().runNextTick(wrappedTask -> {
            var manager = auctionPlugin.getAuctionManager();

            // Retirer le slot des inventaires deja ouverts AVANT de sortir l'objet du store :
            // clearPlayersCache ne fait que vider une cle, il ne redessine rien, et
            // ListedItemsButton.updateInventory a besoin que l'identifiant soit ENCORE present
            // dans le cache ITEMS_LISTED du spectateur pour retrouver son index et decaler
            // les slots. Inverser cet ordre rend le correctif inoperant.
            Item existing = ItemLookup.find(manager, StorageType.LISTED, id);
            if (existing != null) {
                manager.updateListedItems(existing, false, null);
            }

            for (StorageType st : StorageType.values()) {
                if (st != StorageType.DELETED) {
                    manager.removeItem(st, id);
                }
            }

            if (existing != null) {
                // ITEMS_LISTED est VOLONTAIREMENT absent : updateListedItems l'entretient de
                // facon incrementale (removeFromCache). La vider ici ferait echouer le retrait
                // visuel qu'on vient de declencher. C'est exactement ce que fait le chemin
                // d'achat local du plugin principal.
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED,
                        PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SEARCH);
            } else {
                // Rien n'etait affiche ici : purge complete, comme auparavant.
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING,
                        PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SEARCH);
            }
        });

// Dans les DEUX blocs d'etape 2 (l.61 et l.86), ajouter ITEMS_SEARCH a clearPlayersCache :
                    manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING,
                            PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SEARCH);


// ===== 3/3 : ItemBoughtListener.java — remplacer le debut de l'etape 1 (l.44-50) =====
// imports a ajouter : fr.maxlego08.zauctionhouse.api.item.Item ; ...redis.utils.ItemLookup

        auctionPlugin.getScheduler().runNextTick(wrappedTask -> {
            var manager = auctionPlugin.getAuctionManager();

            // Meme raisonnement que dans ItemRemovedListener : purger visuellement les HDV
            // ouverts AVANT de sortir l'objet du store, et ne pas vider ITEMS_LISTED quand
            // l'objet existait (updateListedItems s'en charge de facon incrementale).
            Item existing = ItemLookup.find(manager, StorageType.LISTED, id);
            if (existing != null) {
                manager.updateListedItems(existing, false, null);
            }

            manager.removeItem(StorageType.LISTED, id);

            if (existing != null) {
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
            } else {
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
            }

            // ... suite inchangee (notification du vendeur en ligne)
```

## 7.13 — Addon : publication différenciée pré-commit / post-commit et file de resynchronisation (C-028)

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/ZAuctionHouseRedis.java + RedisAuctionClusterBridge.java`  
**Risque** : MEDIUM  
**Constats** : C-028

`sendMessage` enveloppe tout le publish dans un `catch (Exception)` qui journalise en WARNING : le bus est en « au plus une fois » et l'échec de publication est totalement avalé, le future rendu se complète NORMALEMENT. RISQUE PRINCIPAL, non identifié par la fiche : faire échouer `sendMessage` sans distinction CASSERAIT le chemin d'achat. `PurchaseService` chaîne `purchaseItem -> notifyItemBought -> unlockItem` ; un échec de `notifyItemBought` part dans le `.exceptionally` et rend INTERNAL_ERROR alors que l'argent est débité et l'item livré — le joueur verrait un échec pour un achat réussi. D'où la séparation stricte : `notifyItemStatusChange` est PRÉ-commit (l'appelant peut encore annuler, un statut non propagé signifie qu'un autre nœud peut engager le même item) et son échec doit remonter ; `notifyItemBought`, `notifyItemListed` et `removeItem` sont POST-commit et leur échec est journalisé en SEVERE avec l'identifiant d'item puis poussé dans une file de resynchronisation, drainée par l'étape 14. L'overload `publish(Jedis, T)` supprime au passage le double emprunt imbriqué permanent des trois `notify*` (getResource externe + celui de sendMessage), qui avec `blockWhenExhausted=true` et `maxWait=-1` est le vrai déclencheur de l'épuisement du pool.

```java
// ===== 1/2 : ZAuctionHouseRedis.java =====
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

    private static final int MAX_PENDING_RESYNC = 1000;

    /**
     * Diffusions POST-commit dont la publication a echoue. Rejouees au retablissement du bus
     * et a l'arret de l'addon. Bornee : au-dela de {@value #MAX_PENDING_RESYNC} entrees, la
     * plus ancienne est abandonnee avec un avertissement.
     */
    private final Queue<Object> pendingResync = new ConcurrentLinkedQueue<>();

    /**
     * Publie un message sur une connexion DEJA empruntee au pool.
     * <p>
     * Supprime le double emprunt imbrique (une connexion tenue par l'appelant + une seconde
     * prise par {@code sendMessage}) qui, avec {@code blockWhenExhausted=true} et
     * {@code maxWait=-1}, peut assecher puis interbloquer le pool sur le chemin chaud.
     *
     * @param jedis   connexion deja empruntee
     * @param message message a publier
     * @param <T>     type du message
     * @throws RuntimeException si Redis refuse la publication
     */
    public <T> void publish(Jedis jedis, T message) {
        String jsonMessage = this.gson.toJson(new RedisMessage<>(INSTANCE_UUID, message, message.getClass().getName()));
        this.debug("Send: " + jsonMessage);
        jedis.publish(this.channelName, jsonMessage);
    }

    /**
     * Publication POST-commit : l'ecriture en base a deja abouti, l'echec de diffusion ne doit
     * JAMAIS faire echouer le future de l'appelant.
     * <p>
     * Rendre l'echec a PurchaseService produirait un {@code INTERNAL_ERROR} sur un achat
     * pourtant reussi (argent debite, item livre) : le joueur verrait un echec et pourrait
     * retenter. On journalise donc en SEVERE avec l'identifiant du message et on met la
     * diffusion en file de resynchronisation.
     *
     * @param jedis   connexion deja empruntee
     * @param message message a publier
     * @param <T>     type du message
     */
    public <T> void publishBestEffort(Jedis jedis, T message) {
        try {
            publish(jedis, message);
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to broadcast " + message.getClass().getSimpleName()
                    + " (queued for resync): " + message, exception);
            enqueueForResync(message);
        }
    }

    private void enqueueForResync(Object message) {
        while (this.pendingResync.size() >= MAX_PENDING_RESYNC) {
            this.pendingResync.poll();
            getLogger().warning("Resync queue is full, dropping the oldest pending broadcast.");
        }
        this.pendingResync.offer(message);
    }

    /**
     * Rejoue les diffusions en attente. Appele au retablissement du bus et a l'arret.
     */
    public void drainPendingResync() {
        if (this.pendingResync.isEmpty() || this.jedisPool == null) return;

        int drained = 0;
        try (Jedis jedis = this.jedisPool.getResource()) {
            Object message;
            while ((message = this.pendingResync.poll()) != null) {
                try {
                    publish(jedis, message);
                    drained++;
                } catch (Exception exception) {
                    // Toujours indisponible : on remet le message en tete de file et on arrete.
                    enqueueForResync(message);
                    getLogger().warning("Resync interrupted, " + this.pendingResync.size() + " broadcast(s) still pending.");
                    break;
                }
            }
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Unable to borrow a connection to replay pending broadcasts", exception);
        }

        if (drained > 0) {
            getLogger().info("Re-published " + drained + " pending broadcast(s).");
        }
    }

    /**
     * @deprecated conserve pour compatibilite. Preferer {@link #publish(Jedis, Object)} ou
     * {@link #publishBestEffort(Jedis, Object)} sur une connexion deja empruntee.
     */
    @Deprecated
    public <T> void sendMessage(T message) {
        try (Jedis jedis = jedisPool.getResource()) {
            publish(jedis, message);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to send Redis message", e);
        }
    }


// ===== 2/2 : RedisAuctionClusterBridge.java — les quatre sites de diffusion =====

    @Override
    public CompletableFuture<Void> notifyItemStatusChange(Item item, ItemStatus oldStatus, ItemStatus newStatus) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                // Diffusion PRE-commit : l'appelant peut encore annuler son action. Un statut
                // non propage signifie qu'un autre noeud peut engager le meme item, l'echec
                // doit donc remonter et faire echouer la chaine.
                this.plugin.publish(jedis, new ItemStatusMessage(String.valueOf(item.getId()), oldStatus, newStatus));
            }
        });
    }

    // notifyItemBought : remplacer this.plugin.sendMessage(new ItemBoughtMessage(...)) par
                // Diffusion POST-commit : l'achat est ecrit et l'item livre, ne jamais le
                // rapporter en echec. Publication sur la connexion deja tenue (plus de double
                // emprunt imbrique).
                this.plugin.publishBestEffort(jedis, new ItemBoughtMessage(
                        String.valueOf(item.getId()),
                        item.getSellerUniqueId().toString(),
                        item.getSellerName(),
                        item.getItemDisplay(),
                        item.getFormattedPrice(),
                        player.getName(),
                        givenToBuyer
                ));

    // notifyItemListed : remplacer this.plugin.sendMessage(...) par
                this.plugin.publishBestEffort(jedis, new ItemListedMessage(String.valueOf(item.getId())));

    // removeItem(Item, StorageType, StorageType) : remplacer this.plugin.sendMessage(...) par
                this.plugin.publishBestEffort(jedis, new ItemRemovedMessage(String.valueOf(item.getId()), sourceStorageType, destinationStorageType));
```

## 7.14 — Addon : réconciliation du store LISTED après chaque coupure du bus (C-047)

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/listener/RedisSubscriberRunnable.java + ZAuctionHouseRedis.java + RedisAuctionClusterBridge.java`  
**Risque** : MEDIUM  
**Constats** : C-047

`RedisSubscriberRunnable:81-96` perd DÉFINITIVEMENT tout message publié pendant sa boucle de reconnexion (backoff jusqu'à 60 s) et se re-souscrit en aveugle : aucune reconciliation n'existe. Les items vendus ailleurs pendant la coupure restent des fantômes achetables. Je retiens la variante 100 % addon-locale du vérificateur plutôt que celle de l'audit (`onTransportRecovered` ajouté au contrat + `reconcileItemsSince` relisant la base) : celle-ci est cassante et cross-repo, et exige un index `items(updated_at)` inexistant. `StorageManager.loadItems()` n'est PAS une alternative : `AuctionLoader.loadItems()` fait `createItems(..., auctionManager::addItem)` SANS vider les stores, un second appel dupliquerait tout en mémoire (vérifié à HEAD). La reconciliation interroge donc l'état autoritaire des clés Redis `auction:item:<id>` en pipeline par lots de 500 et purge tout item dont l'état est terminal. Limite intrinsèque à annoncer dans le changelog : les items dont la clé d'état a expiré (`item-state-ttl-seconds`, 24 h par défaut) rendent null et sont conservés — une coupure de plus de 24 h laisse des fantômes.

```java
// ===== 1/3 : RedisSubscriberRunnable.java =====

    /**
     * Positionne a {@code true} des qu'une souscription echoue. La souscription suivante
     * declenche alors une reconciliation : le pub/sub Redis est en « au plus une fois », tout
     * message publie pendant la coupure est definitivement perdu.
     */
    private final AtomicBoolean reconnected = new AtomicBoolean(false);

    // dans run(), juste apres le log "Subscribed to Redis channel" et AVANT jedis.subscribe :
                plugin.getLogger().info("Subscribed to Redis channel: " + plugin.getChannelName());

                if (this.reconnected.compareAndSet(true, false)) {
                    // Le bus avait ete coupe : rejouer nos diffusions en attente et reconcilier
                    // le store LISTED avec l'etat autoritaire des cles Redis. L'appel est
                    // non bloquant, il ne doit pas retarder le subscribe qui suit.
                    this.plugin.onTransportRecovered();
                }

                jedis.subscribe(jedisPubSub, this.plugin.getChannelName());

    // dans le catch (Exception exception), juste apres le test `if (!running.get()) break;` :
                this.reconnected.set(true);


// ===== 2/3 : ZAuctionHouseRedis.java =====
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.redis.utils.ItemLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

    /** Bridge Redis installe, conserve pour la reconciliation et la liberation des verrous. */
    private RedisAuctionClusterBridge clusterBridge;

    // dans onEnable, remplacer la ligne 99 par :
        this.clusterBridge = new RedisAuctionClusterBridge(this, this.jedisPool, this.lockTtl, itemStateTtl, itemListedTtl);
        this.auctionPlugin.setAuctionClusterBridge(this.clusterBridge);

    /**
     * Appele par le thread abonne apres une RE-souscription (jamais au premier demarrage).
     * Rejoue les diffusions en attente puis reconcilie le store LISTED local.
     */
    public void onTransportRecovered() {
        var plugin = this.auctionPlugin;
        if (plugin == null || !plugin.isEnabled()) return;

        getLogger().warning("Redis bus recovered after an outage: replaying pending broadcasts and reconciling listings.");
        plugin.getScheduler().runAsync(w -> {
            drainPendingResync();
            reconcileListedItems();
        });
    }

    /**
     * Purge du store LISTED local toute annonce dont l'etat Redis est terminal (SOLD, REMOVED
     * ou DELETED) : ces items ont ete regles sur un autre noeud pendant que le bus etait coupe
     * et leurs messages sont definitivement perdus.
     * <p>
     * Limite assumee : un item dont la cle d'etat a expire ({@code item-state-ttl-seconds},
     * 24 h par defaut) rend {@code null} et est conserve. Une coupure de plus de 24 h laisse
     * donc des fantomes, que seul un redemarrage corrige.
     */
    private void reconcileListedItems() {
        var bridge = this.clusterBridge;
        if (bridge == null) return;

        var manager = this.auctionPlugin.getAuctionManager();
        List<Integer> ids = new ArrayList<>();
        manager.getItems(StorageType.LISTED).forEach(item -> ids.add(item.getId()));
        if (ids.isEmpty()) return;

        Set<Integer> settled = bridge.findSettledItemIds(ids);
        if (settled.isEmpty()) {
            getLogger().info("Bus recovery: " + ids.size() + " listing(s) checked, nothing to reconcile.");
            return;
        }

        getLogger().warning("Bus recovery: purging " + settled.size() + " listing(s) settled on another node during the outage.");
        this.auctionPlugin.getScheduler().runNextTick(w -> {
            for (int id : settled) {
                var existing = ItemLookup.find(manager, StorageType.LISTED, id);
                if (existing != null) {
                    manager.updateListedItems(existing, false, null);
                }
                manager.removeItem(StorageType.LISTED, id);
            }
            manager.clearPlayersCache(PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH,
                    PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED);
            manager.rebuildSortedItemsCache();
        });
    }


// ===== 3/3 : RedisAuctionClusterBridge.java =====
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

    private static final int RECONCILE_CHUNK = 500;

    /**
     * Rend, parmi les identifiants fournis, ceux dont l'etat Redis est terminal (SOLD, REMOVED
     * ou DELETED) : ces annonces ont ete reglees sur un autre noeud et ne doivent plus figurer
     * dans le store LISTED local.
     * <p>
     * Interroge Redis en pipeline par lots de {@value #RECONCILE_CHUNK} : sur 20 000 annonces,
     * 40 allers-retours au lieu de 20 000.
     *
     * @param itemIds identifiants a verifier
     * @return les identifiants dont l'etat est terminal, jamais {@code null}
     */
    public Set<Integer> findSettledItemIds(List<Integer> itemIds) {
        Set<Integer> settled = new HashSet<>();

        try (Jedis jedis = jedisPool.getResource()) {
            for (int start = 0; start < itemIds.size(); start += RECONCILE_CHUNK) {
                List<Integer> chunk = itemIds.subList(start, Math.min(start + RECONCILE_CHUNK, itemIds.size()));

                Map<Integer, Response<String>> responses = new LinkedHashMap<>();
                Pipeline pipeline = jedis.pipelined();
                for (Integer id : chunk) {
                    responses.put(id, pipeline.hget(KEY_PREFIX_ITEM + id, FIELD_STATE));
                }
                pipeline.sync();

                for (Map.Entry<Integer, Response<String>> entry : responses.entrySet()) {
                    String state = entry.getValue().get();
                    if (STATE_SOLD.equals(state) || STATE_REMOVED.equals(state) || STATE_DELETED.equals(state)) {
                        settled.add(entry.getKey());
                    }
                }
            }
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to reconcile item states after bus recovery", exception);
        }

        return settled;
    }
```

## 7.15 — Addon : arrêt propre — bridge fail-closed et libération des verrous détenus (C-018)

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/ClusterUnavailableBridge.java (nouveau) + ZAuctionHouseRedis.java + RedisAuctionClusterBridge.java`  
**Risque** : MEDIUM  
**Constats** : C-018

`onDisable` ferme le pool Jedis sans jamais désinstaller le bridge : le plugin principal conserve un `RedisAuctionClusterBridge` dont le pool est mort, et chaque achat part en `INTERNAL_ERROR` opaque, sans aucun chemin de récupération. J'ÉCARTE le correctif de l'audit (« restaurer le bridge par défaut avant `jedisPool.close()` ») : restaurer `LocalAuctionClusterBridge` réintroduit exactement le défaut dénoncé par C-036 — ses verrous vivent dans une `ConcurrentHashMap` locale, invisible des autres nœuds, ce qui autoriserait ce serveur à vendre sans exclusion pendant que le reste du réseau tourne encore. Un bridge fail-closed refuse d'opérer, ce qui est le seul comportement sûr, et évite toute modification de l'API (`LocalAuctionClusterBridge` vit dans `src/`, pas dans `api/` : l'addon ne peut de toute façon pas l'instancier). Second volet : relâcher les verrous encore détenus par ce nœud plutôt que d'attendre `lock-ttl-seconds`, sinon les items concernés sont invendables sur tout le réseau pendant 30 s. Le suivi des verrous par le bridge est de surcroît compatible avec le jeton unique par acquisition du chantier 4 : on mémorise la valeur réelle du jeton au lieu de la recalculer.

```java
// ===== 1/3 : NOUVEAU FICHIER REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/ClusterUnavailableBridge.java =====
// COORDINATION : si le chantier 6 (C-036) a deja livre cette classe, la reutiliser telle
// quelle et ne garder de cette etape que les modifications de onDisable et releaseAllLocks().
package fr.maxlego08.zauctionhouse.redis;

import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Bridge fail-closed installe des que ce noeud ne peut plus parler au cluster.
 * <p>
 * On ne restaure JAMAIS {@code LocalAuctionClusterBridge} : ses verrous vivent dans une table
 * locale, invisible des autres noeuds, qui autoriserait ce serveur a vendre sans exclusion
 * pendant que le reste du reseau tourne encore. Refuser d'operer est le seul comportement sur.
 * <p>
 * {@code isDistributed()} reste {@code true} : les depots destines a un vendeur hors ligne
 * doivent continuer de passer par le systeme de claim, jamais par l'economie locale.
 */
public class ClusterUnavailableBridge implements AuctionClusterBridge {

    private static <T> CompletableFuture<T> unavailable() {
        return CompletableFuture.failedFuture(new IllegalStateException("zAuctionHouse cluster bridge is unavailable on this node"));
    }

    @Override
    public CompletableFuture<Boolean> checkAvailability(Item item) {
        return unavailable();
    }

    @Override
    public CompletableFuture<LockToken> lockItem(Item item, UUID lockerId, StorageType storageType) {
        return unavailable();
    }

    @Override
    public CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType) {
        return unavailable();
    }

    @Override
    public CompletableFuture<Void> notifyItemBought(Player player, Item item) {
        return unavailable();
    }

    @Override
    public CompletableFuture<Void> notifyItemListed(Item item) {
        return unavailable();
    }

    @Override
    public CompletableFuture<Void> notifyItemStatusChange(Item item, ItemStatus oldStatus, ItemStatus newStatus) {
        return unavailable();
    }

    @Override
    public CompletableFuture<Void> removeItem(Item item, StorageType storageType) {
        return unavailable();
    }

    @Override
    public boolean isDistributed() {
        return true;
    }
}


// ===== 2/3 : RedisAuctionClusterBridge.java — suivi et liberation des verrous =====

    /** Verrous distribues actuellement detenus par ce noeud : identifiant d'item -> valeur du jeton. */
    private final Map<Integer, String> heldLocks = new ConcurrentHashMap<>();

    // dans lockItem, sur CHAQUE chemin qui rend `token` (script atomique, retry apres NOSCRIPT,
    // et repli non atomique), remplacer `return token;` par :
                        this.heldLocks.put(item.getId(), token.value());
                        return token;

    // en tete du corps de unlockItem, apres la garde sur le jeton noop :
            this.heldLocks.remove(item.getId(), lockToken.value());

    /**
     * Relache tous les verrous encore detenus par ce noeud.
     * <p>
     * Appele a l'arret : sans cela, les items verrouilles restent invendables sur TOUT le
     * reseau jusqu'a l'expiration de {@code lock-ttl-seconds} (30 s par defaut).
     * <p>
     * La valeur reelle du jeton est memorisee a l'acquisition et non recalculee : ce choix
     * reste correct le jour ou le jeton deviendra unique par acquisition (chantier 4).
     */
    public void releaseAllLocks() {
        if (this.heldLocks.isEmpty()) return;

        Map<Integer, String> snapshot = new HashMap<>(this.heldLocks);
        this.heldLocks.clear();

        try (Jedis jedis = jedisPool.getResource()) {
            for (Map.Entry<Integer, String> entry : snapshot.entrySet()) {
                List<String> keys = Arrays.asList(KEY_PREFIX_LOCK + entry.getKey(), KEY_PREFIX_ITEM + entry.getKey());
                List<String> args = Collections.singletonList(entry.getValue());
                try {
                    if (this.scriptsLoaded) {
                        jedis.evalsha(this.unlockScriptSha, keys, args);
                    } else {
                        jedis.eval(UNLOCK_SCRIPT, keys, args);
                    }
                } catch (Exception exception) {
                    this.plugin.getLogger().warning("Failed to release cluster lock on item " + entry.getKey() + ": " + exception.getMessage());
                }
            }
            this.plugin.getLogger().info("Released " + snapshot.size() + " cluster lock(s) held by this node.");
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to release the locks held by this node", exception);
        }
    }


// ===== 3/3 : ZAuctionHouseRedis.java — remplacer integralement onDisable (l.109-142) =====

    @Override
    public void onDisable() {

        // 1) Couper l'acces au bridge EN PREMIER. Le plugin principal ne doit plus jamais
        // appeler un bridge dont le pool Jedis est sur le point de disparaitre. On installe un
        // bridge fail-closed et NON LocalAuctionClusterBridge : sur un reseau ou les autres
        // noeuds tournent encore, des verrous purement locaux reautoriseraient la vente sans
        // exclusion partagee.
        if (this.auctionPlugin != null) {
            this.auctionPlugin.setAuctionClusterBridge(new ClusterUnavailableBridge());
        }

        if (this.heartbeatTask != null) {
            this.heartbeatTask.cancel();
        }

        this.redisSubscriberRunnable.shutdown();
        if (this.subscriberThread != null && this.subscriberThread.isAlive()) {
            try {
                this.subscriberThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 2) Relacher les verrous encore detenus, plutot que d'attendre lock-ttl-seconds :
        // sinon les items concernes restent invendables sur tout le reseau pendant 30 s.
        if (this.clusterBridge != null) {
            this.clusterBridge.releaseAllLocks();
        }

        // 3) Rejouer les diffusions en attente tant que le pool est encore ouvert.
        drainPendingResync();

        this.messageGenerations.clear();

        if (this.jedisPool != null && INSTANCE_UUID != null) {
            try (Jedis jedis = this.jedisPool.getResource()) {
                jedis.del(REGISTRY_KEY_PREFIX + INSTANCE_UUID);
            } catch (Exception ignored) {
            }
        }

        if (this.jedisPool != null) {
            this.jedisPool.close();
        }

        context.shutdown();
    }
```

### Ruptures d'API et stratégie de compatibilité

- AUCUNE rupture. Toutes les modifications de api/ sont additives, par construction.
- NOUVEAU record public `fr.maxlego08.zauctionhouse.api.configuration.records.MaintenanceConfiguration` : ajout pur, aucun type existant n'est modifie. J'ai explicitement REFUSE d'ajouter des composantes au record publie `PerformanceConfiguration`, ce qui aurait modifie son constructeur canonique et casse binairement l'addon Redis (compile contre le SHA fige deb8f16) ainsi que tout plugin tiers.
- NOUVELLE methode `default MaintenanceConfiguration getMaintenance()` sur l'interface publiee `Configuration`. Une methode `default` est source- ET binaire-compatible : `MainConfiguration` la surcharge, toute implementation tierce existante continue de compiler et d'executer sans recompilation, en heritant des valeurs par defaut. Precedent identique dans le meme depot : `AuctionClusterBridge.isDistributed()`.
- AUCUNE methode ajoutee a `AuctionManager`, `StorageManager` ou `AuctionClusterBridge`. Les correctifs de l'addon qui auraient eu besoin de `AuctionManager.getItem(StorageType, int)` (C-050) utilisent a la place le helper addon-local `ItemLookup.find` en O(n) : le pin `fr.maxlego08.zauctionhouse:zauctionhousev4-api:deb8f16` de l'addon N'A PAS a etre bumpe pour ce chantier, les deux depots peuvent etre publies independamment.
- AUCUN point d'entree `onTransportRecovered(long)` ajoute au contrat `AuctionClusterBridge`, contrairement a ce que proposait l'audit pour C-047 : la reconciliation est entierement interne a l'addon.
- `ZAuctionHouseRedis.sendMessage(T)` est conserve avec sa signature exacte et marque `@Deprecated` : les deux nouvelles methodes `publish(Jedis, T)` et `publishBestEffort(Jedis, T)` sont des ajouts. Aucun appelant externe n'est casse (verifie : le seul consommateur est RedisAuctionClusterBridge).
- AJOUT de `ZAuctionPlugin.getMaintenanceScheduler()` : methode publique sur la classe d'implementation uniquement, absente de l'interface `AuctionPlugin`. Aucun impact sur l'API publiee.

### Changements de configuration

- NOUVELLE section `maintenance:` dans config.yml, portant 4 cles : `expiration-sweep-interval-seconds` (defaut 60, 0 = desactive), `expiration-sweep-batch-size` (defaut 50), `confirmation-timeout-seconds` (defaut 60, plancher dur 60, 0 = desactive), `confirmation-sweep-interval-seconds` (defaut 10).
- REPLICATION OBLIGATOIRE DANS SIX JEUX DE LANGUE, pas quatre : src/main/resources/config.yml (anglais, reference) + fr/config.yml + es/config.yml + it/config.yml + id/config.yml + th/config.yml. Le CLAUDE.md du projet est PERIME sur ce point (il n'annonce que en/fr/es/it) : `ls src/main/resources/` rend bien es, fr, id, it, th en plus de la racine. Le commentaire anglais doit etre traduit dans chaque fichier, les cles et les valeurs restant identiques.
- AUCUNE nouvelle cle dans messages.yml, economies.yml, categories.yml, rules.yml ni discord.yml. AUCUNE nouvelle constante dans l'enum publie `Message`. C'est un choix deliberé : le seul message joueur qui aurait ete souhaitable (« hotel des ventes temporairement indisponible » a la place de l'INTERNAL_ERROR opaque du bridge fail-closed) est reporte, pour ne pas melanger une replication de 6 fichiers de messages a ce chantier. Voir openQuestions.
- AUCUNE nouvelle cle dans le config.yml de l'addon Redis. La fiche C-072 proposait `redis-config.confirm-status-ttl-seconds` : le rearmement etant implemente cote plugin (voir etape 6), cette cle n'existe pas. Le commentaire de `redis-config.item-state-ttl-seconds` devrait en revanche etre complete pour signaler qu'il borne desormais aussi la fenetre de reconciliation apres coupure du bus (etape 14).
- AUCUNE migration de schema. Aucune colonne, aucun index, aucune table ajoutee : ce chantier ne touche pas la base de donnees.
- DOCUMENTATION Docusaurus a mettre a jour en EN + FR (C:/Users/Admin/Desktop/groupez/documentation) : nouvelle section `maintenance` dans la page de configuration, et note d'exploitation sur le comportement d'arret de l'addon Redis (bridge fail-closed, liberation des verrous). Ajouter une entree `# Unreleased` dans changelogs.md.

### Fichiers nouveaux

- D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/API/src/main/java/fr/maxlego08/zauctionhouse/api/configuration/records/MaintenanceConfiguration.java
- D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/java/fr/maxlego08/zauctionhouse/maintenance/ZMaintenanceScheduler.java
- D:/Users/Maxlego08/workspace2.0/zAuctionHouse Redis/src/main/java/fr/maxlego08/zauctionhouse/redis/listener/ItemMessageGenerations.java
- D:/Users/Maxlego08/workspace2.0/zAuctionHouse Redis/src/main/java/fr/maxlego08/zauctionhouse/redis/utils/ItemLookup.java
- D:/Users/Maxlego08/workspace2.0/zAuctionHouse Redis/src/main/java/fr/maxlego08/zauctionhouse/redis/ClusterUnavailableBridge.java

### Validation

- INVARIANT SQL n°1 — aucune annonce perimee ne survit au balayage. Sur un serveur de test, mettre 200 items en vente avec une expiration de 60 s, puis N'OUVRIR AUCUN inventaire pendant 5 minutes. Requete : `SELECT COUNT(*) FROM zauctionhousev4_items WHERE storage_type = 'LISTED' AND expired_at > 0 AND expired_at < UNIX_TIMESTAMP()*1000;` doit rendre 0. AVANT le chantier elle rend 200 (aucune tache n'expire quoi que ce soit). Refaire avec `maintenance.expiration-sweep-interval-seconds: 0` : elle doit rendre 200, ce qui prouve que l'interrupteur fonctionne.
- SCENARIO DEUX SERVEURS n°1 (C-088 + C-114) — double expiration. Deux serveurs A et B partageant MySQL + Redis, `expiration-sweep-interval-seconds: 5` sur les deux pour forcer la concurrence, 500 annonces expirant simultanement. Attendre 60 s. Verifier : (a) `SELECT COUNT(*) FROM zauctionhousev4_items WHERE storage_type='EXPIRED'` = 500 exactement, (b) chaque vendeur voit 500 items dans son onglet expire et PAS 1000, (c) aucun `Cluster-aware expiration skipped/failed` en boucle dans les logs. L'exclusion doit venir du verrou par item, pas d'une election de leader.
- SCENARIO DEUX SERVEURS n°2 (C-114) — achat d'un item en cours d'expiration. Sur A, poser un point d'arret (ou un `Thread.sleep(3000)` temporaire) entre l'acquisition du verrou et le SELECT de `PurchaseService`. Sur B, declencher le balayage d'expiration du meme item. Attendu : l'achat sur A echoue proprement en ITEM_NOT_AVAILABLE, l'acheteur n'est PAS debite (`SELECT * FROM zauctionhousev4_transactions WHERE item_id = ?` reste vide), et l'item est bien en EXPIRED. AVANT le chantier, l'achat aboutit et l'item est vendu ET rendu au vendeur.
- SCENARIO TTL n°1 (C-023 + C-072) — deconnexion en pleine confirmation. Sur A, ouvrir la confirmation d'achat d'un item, puis `/kill` du process client (ALT-F4, pas un quit propre). Attendu IMMEDIAT (< 1 s, via PlayerQuitEvent) : sur A ET sur B, `%zauctionhouse_listed_items%` reintegre l'item et il redevient cliquable. Repeter en tuant le serveur A entier (kill -9) : l'item doit etre rearme sur B en moins de `confirmation-timeout-seconds` + `confirmation-sweep-interval-seconds` (70 s par defaut), avec un WARNING `stayed in IS_PURCHASE_CONFIRM for more than 60s` dans la console de B.
- SCENARIO TTL n°2 (C-023) — mort en pleine confirmation. Ouvrir la confirmation de retrait, se faire tuer par un mob ou `/kill`. zMenu n'emet pas onInventoryClose. Attendu : l'item redevient AVAILABLE immediatement (handler PlayerDeathEvent) et non au bout de 60 s.
- SCENARIO ADMIN (C-065). Joueur Bob avec une confirmation d'achat ouverte. Executer `/ah admin cache clear * all`. Attendu : Bob garde sa confirmation fonctionnelle, l'item n'est pas gele, et la fermeture de la fenetre le libere normalement. Executer ensuite `/ah admin cache clear Bob ITEM_SHOW` : l'item doit etre libere ET redevenir AVAILABLE sur les deux serveurs. AVANT le chantier, les deux commandes gelent l'item.
- SCENARIO CAS (C-004) — reordonnancement de messages. Sur B, injecter manuellement dans le canal Redis un ItemStatusMessage `{oldStatus: AVAILABLE, newStatus: IS_PURCHASE_CONFIRM}` pour un item que B detient deja en PURCHASED. Attendu : ligne `Refusing LISTED-cycle status IS_PURCHASE_CONFIRM for item <id> held in PURCHASED` dans la console de B, et l'item reste reclamable par son acheteur. AVANT le chantier, l'item passe en IS_PURCHASE_CONFIRM et n'est plus jamais reclamable.
- SCENARIO GENERATIONS (C-021 + C-071). Sur B, ralentir artificiellement `ZStorageManager.selectItem` (Thread.sleep(2000)). Sur A, retirer un item puis le supprimer definitivement en moins de 2 s. Attendu sur B : ligne debug `Dropping stale re-add of item <id>` et l'item ABSENT de tous les conteneurs memoire. Verifier avec `/ah admin cache show` et `%zauctionhouse_listed_items%`. AVANT le chantier, l'item ressuscite en EXPIRED et le joueur peut le reclamer une seconde fois.
- SCENARIO PURGE VISUELLE (C-092). PREREQUIS : le quick win `return` -> `continue` (ZAuctionManager:956) doit etre applique, sinon faux negatif garanti. Trois joueurs sur B ont le HDV ouvert sur la page contenant l'item X, SANS toucher a leur souris. Sur A, acheter X. Attendu : les trois slots disparaissent et les items suivants se decalent, SANS que les joueurs aient a changer de page. AVANT le chantier, le slot reste affiche et cliquable. Repeter avec un joueur ayant une recherche active : son slot est rafraichi au rendu suivant, mais un clic ne doit JAMAIS resoudre l'item vendu.
- SCENARIO COUPURE DU BUS (C-047 + C-028). Trois serveurs. Sur A, `iptables -A OUTPUT -p tcp --dport 6379 -j DROP` (ou arreter Redis pour A uniquement). Pendant la coupure, vendre 20 items sur B, en acheter 20 autres sur C. Retablir la connexion. Attendu dans la console de A, en moins d'un cycle de backoff : `Redis bus recovered after an outage`, puis `Bus recovery: purging 20 listing(s) settled on another node during the outage`, et l'onglet HDV de A ne contient plus aucun des 20 items achetes. Verifier aussi qu'une publication echouee pendant la coupure est rejouee : ligne `Re-published N pending broadcast(s)`.
- SCENARIO ARRET DE L'ADDON (C-018). Sur A, ouvrir une confirmation d'achat (verrou Redis pose sur l'item), puis `/plugman unload ZAuctionHouseRedis` (ou arret propre du serveur A). Attendu : ligne `Released 1 cluster lock(s) held by this node` dans la console de A, et sur B `EXISTS auction:lock:<id>` rend 0 IMMEDIATEMENT (pas au bout de 30 s). Ensuite, tenter un achat sur A : le joueur doit recevoir un echec, et `GET auction:item:<id>` ne doit PAS avoir change d'etat — le bridge fail-closed refuse d'operer au lieu de verrouiller localement.
- SCENARIO ADMIN ADD (C-064). Sur A, `/ah admin add Bob listed 1000` avec un item ItemsAdder ou MMOItems en main. Attendu : (a) l'item apparait dans le HDV de B en moins de 2 s, (b) il apparait dans la BONNE categorie et non seulement dans « toutes categories » — filtrer par categorie sur A et sur B, (c) `/ah admin add Bob expired` ne fait apparaitre AUCUN item achetable dans le HDV principal de B, meme fugitivement (surveiller le canal Redis : aucun ItemListedMessage ne doit etre publie pour ce chemin).
- NON-REGRESSION MONO-SERVEUR. Desinstaller l'addon Redis. Rejouer les scenarios TTL n°1, TTL n°2 et ADMIN : ils doivent tous passer a l'identique, `LocalAuctionClusterBridge.notifyItemStatusChange` rendant un `completedFuture`. Verifier qu'aucune ligne de log ne mentionne Redis et que le balayage d'expiration tourne (`Expiration sweep: processing N expired listing(s)`).
- COMPILATION ETAPE PAR ETAPE. Apres CHAQUE etape : `JAVA_HOME="C:/Users/Admin/.jdks/ms-21.0.9" ./gradlew build` dans zAuctionHouseV4 (jar dans target/), puis `./gradlew build` dans zAuctionHouse Redis. Une etape qui ne compile pas est une erreur de decoupage a corriger avant de continuer. Les etapes 1 a 8 sont independantes des etapes 9 a 15 : les deux depots peuvent etre construits separement.

### Questions ouvertes pour le mainteneur

- ACTIVER LE BALAYAGE D'EXPIRATION PAR DEFAUT ? J'ai mis `expiration-sweep-interval-seconds: 60` (actif) parce que c'est le comportement CORRECT : sans lui, une annonce arrivee a terme reste en vente indefiniment, ce qui est le defaut meme que C-088 signale. Mais c'est un changement de comportement VISIBLE a la mise a jour : sur un serveur avec un gros arriere d'annonces expirees jamais ramassees, des centaines d'items vont basculer en EXPIRED dans les minutes suivant le redemarrage, et les vendeurs recevront un afflux d'items a recuperer. A annoncer en tete du changelog. Alternative si vous preferez une montee en charge douce : livrer avec 0 (desactive) et demander aux exploitants de l'activer, au prix de laisser le bug ouvert par defaut.
- PLANCHER DE 60 s SUR LE REARMEMENT DES CONFIRMATIONS. J'impose un minimum dur de 60 s, avec un WARNING au demarrage si l'exploitant descend en dessous. En dessous, un joueur qui lit tranquillement une confirmation verrait son item repasser en vente sous ses yeux et un second acheteur pourrait ouvrir une confirmation concurrente : le degat reste borne (PurchaseService revalide en base sous verrou et avorte), mais les confirmations refusees se multiplieraient. Acceptez-vous ce plancher, ou preferez-vous laisser la valeur totalement libre avec un simple avertissement ?
- C-072 DEPLACE COTE PLUGIN — a valider. La fiche C-072 place le rearmement dans l'addon avec une cle dans son config.yml. Je l'ai deplace cote plugin (etape 6) pour trois raisons : le serveur emetteur ne recoit pas son propre message et ne pourrait donc jamais se debloquer lui-meme, le meme gel existe en mono-serveur ou l'addon n'est pas installe, et deux TTL concurrents sur le meme objet sont un piege de maintenance. Consequence : la cle `redis-config.confirm-status-ttl-seconds` n'existe pas et le rearmement s'applique aussi aux serveurs sans Redis. Si vous tenez a un reglage propre a l'addon, il faudra reintroduire un second TTL et arbitrer lequel gagne.
- PAS D'ELECTION DE LEADER POUR L'EXPIRATION — a valider. L'audit exige un noeud elu par `SET NX` sur `auction:expire:leader`. Je m'en passe : `expireListedItemClustered` verrouille deja chaque item et relit la ligne autoritaire, ce qui est une exclusion PLUS FINE qu'une election (elle protege aussi contre l'expiration d'un item en cours d'achat, ce qu'un leader ne fait pas), et une election exigerait une primitive de verrou generique absente de `AuctionClusterBridge`, donc une rupture d'API. Le prix est que N noeuds executent chacun le balayage : N fois la lecture memoire (negligeable) et N tentatives de verrou dont N-1 sortent immediatement sur jeton noop. Sur un reseau a plus d'une dizaine de noeuds, ce cout Redis merite d'etre mesure avant d'exclure l'election.
- MESSAGE JOUEUR POUR LE BRIDGE INDISPONIBLE. Avec le bridge fail-closed de l'etape 15, un joueur qui tente un achat pendant l'arret de l'addon recoit un `INTERNAL_ERROR` opaque. Un message dedie (« hotel des ventes temporairement indisponible ») serait bien meilleur, mais impose une constante dans l'enum publie `Message` et une replication dans SIX messages.yml. Je l'ai volontairement exclu de ce chantier pour garder les commits relisables. Le faites-vous maintenant, ou dans un lot « messages » regroupant aussi ADMIN_ITEM_NOT_AVAILABLE (C-012) et SELL_INVENTORY_FULL (C-097) ?
- COUT TPS DE applyCategories SUR LE THREAD PRINCIPAL (etape 10). Deplacer `applyCategories` dans le `runNextTick` est correct du point de vue thread-safety — il execute les regles des hooks ItemsAdder, MMOItems, Oraxen, Nexo, Slimefun, CraftEngine, ExecutableItems — mais c'est un transfert de cout vers le tick. Sur un reseau a fort volume de mises en vente, a surveiller avec `performance-debug`. Si l'impact est mesurable, la parade est de batcher les insertions sur une fenetre de 100 ms, ce qui sort du perimetre de ce chantier.
- PURGE VISUELLE POUR LES JOUEURS EN RECHERCHE ACTIVE (etape 12). Mon patch retire correctement le slot pour les spectateurs sans recherche, mais un joueur avec une recherche active ne voit son slot rafraichi qu'au rendu suivant : rien ne retire incrementalement un identifiant de `ITEMS_SEARCH`, et le vider avant `updateListedItems` ferait echouer le retrait visuel. Je purge donc ITEMS_SEARCH pour la justesse des DONNEES (un clic ne resout jamais un item vendu) en acceptant le decalage visuel. Le vrai correctif demande un retrait incrementiel dans la liste de recherche, qui releve du chantier PERF (C-055/C-074).
- COORDINATION AVEC LE CHANTIER 6 SUR ClusterUnavailableBridge. La classe est necessaire a mon etape 15 (onDisable) et au correctif C-036 du chantier 6 (sorties anticipees de onEnable). Je la fournis ; il faut decider qui la livre en premier pour eviter deux classes concurrentes. Recommandation : que le chantier 6 la reprenne telle quelle et se contente d'ajouter ses points d'installation.
- COORDINATION AVEC LE CHANTIER 8 SUR sendMessage. Mon etape 13 introduit `publish(Jedis, T)` et supprime le double emprunt imbrique des trois `notify*`, qui fait partie du patch C-077 du chantier 8. Le chantier 8 doit batir sur cette signature (executor dedie dimensionne a max-total/2, `setMaxWait`) et ne PAS refaire la suppression. Conflit textuel garanti si les deux sont ecrits en parallele.

### Retour arrière

["AUCUNE migration de schema n'est introduite : un downgrade de jar est integralement reversible dans les deux depots, sans intervention en base et sans perte de donnees.", "DEGRADATION SANS REDEPLOIEMENT (a privilegier en incident). Toutes les nouveautes du plugin sont pilotees par configuration : poser `maintenance.expiration-sweep-interval-seconds: 0` et `maintenance.confirmation-timeout-seconds: 0` dans config.yml puis `/ah admin reload` desactive integralement les deux taches de fond, sans redemarrage. Restent actives les gardes non desactivables (etapes 1, 3, 4, 8, 11) : elles ne font que REFUSER des transitions et ne peuvent pas produire de duplication ; le pire cas est un refus fail-closed (item temporairement non achetable) qui se resorbe au balayage suivant ou au redemarrage.", "ROLLBACK DU JAR PLUGIN. Redeployer le jar precedent. La section `maintenance:` reste dans les config.yml : elle est simplement ignoree, aucune erreur de chargement (Bukkit tolere les cles inconnues). Ne PAS supprimer la section a la main, elle sera reutilisee au redeploiement.", "ROLLBACK DU JAR ADDON. Les deux jars sont independants : l'addon en version N-1 fonctionne avec le plugin en version N (aucune methode nouvelle de l'API n'est appelee par l'addon), et l'inverse aussi. On peut donc revenir en arriere sur UN SEUL des deux depots. C'est deliberé et c'est la raison pour laquelle j'ai refuse d'ajouter `getItem(StorageType, int)` a l'API.", "ETAT REDIS APRES ROLLBACK DE L'ADDON. Le chantier n'introduit AUCUN nouveau format de cle ni de message : les cles `auction:item:<id>` et `auction:lock:<id>` gardent exactement leur schema, `ItemStatusMessage` / `ItemListedMessage` / `ItemRemovedMessage` / `ItemBoughtMessage` gardent leurs composantes. Une mise a jour progressive (certains noeuds a jour, d'autres non) est donc sure dans les deux sens ; un noeud ancien ignorera simplement les gardes. Aucun FLUSHDB ni DEL manuel n'est necessaire.", "SEULE SEQUELLE POSSIBLE, a corriger a la main : si un rollback intervient alors que le bridge fail-closed vient d'etre installe (etape 15) et que le serveur ne redemarre PAS, le plugin principal conserve ce bridge jusqu'au prochain redemarrage complet. Symptome : tous les achats echouent en INTERNAL_ERROR avec `cluster bridge is unavailable on this node` dans les logs. Remede : redemarrer le serveur (un simple rechargement de l'addon ne suffit pas, `setAuctionClusterBridge` n'est appele qu'au onEnable de l'addon).", "PROCEDURE D'URGENCE SI DES ITEMS SONT GELES apres un rollback partiel (statut IS_*_CONFIRM figé sans balayage pour les liberer) : `DEL auction:item:<id>` cote Redis debloque l'etat distribue, et un redemarrage du serveur relit les items depuis la base avec le statut derive du storage_type, donc AVAILABLE pour toute ligne restee LISTED. Aucune donnee n'est perdue, le statut n'est pas persiste en base."]


---

# Chantier 8 — Chantier 8 — Confinement de threads et pools dédiés

Ce chantier ne corrige aucune logique métier : il remet chaque instruction sur le thread qui a le droit de l'exécuter, et chaque I/O bloquante sur un pool que le plugin possède et draine. Trois familles de défauts s'y rejoignent : (1) le JDBC et le Jedis partent aujourd'hui sur ForkJoinPool.commonPool, dimensionné cœurs-1, où un ralentissement Redis fige toutes les opérations cluster du nœud (C-077, C-052, C-089) ; (2) les continuations asynchrones appellent des API Bukkit main-thread-only — mise à jour d'inventaire, ouverture de GUI, player.setLevel, mutation d'IntArrayList partagée — depuis ces mêmes workers (C-035, C-048, C-049, C-076, C-079, C-084, C-105) ; (3) les pools et références du plugin ne survivent pas proprement à l'arrêt : asyncExecutor est fermé pendant qu'une chaîne d'achat va encore lui soumettre une écriture, les écritures différées partent sur un ordonnanceur jamais drainé, et le bridge cluster est publié sans barrière mémoire (C-022, C-027, C-117, C-086, C-087). L'ensemble est indivisible parce que les correctifs se conditionnent mutuellement : donner un pool dédié au bridge Redis AVANT d'avoir supprimé le double emprunt de connexion imbriqué produit un interblocage définitif, et migrer les écritures de log/transaction vers asyncExecutor AVANT de l'avoir redimensionné et rendu non-rejetant ne fait que déplacer la famine. Aucune migration de schéma, aucun changement de signature : la seule addition à l'API publiée est une méthode `default` et une constante d'enum, toutes deux compatibles binaire avec l'addon Redis épinglé sur le SHA deb8f16.

**Prérequis**

- AUCUN prérequis dur : les 11 étapes sont applicables sur HEAD (49571d9 / d951be2) dans l'ordre donné. Les entrées ci-dessous sont des correctifs d'AUTRES chantiers que ce chantier ne fait PAS et qu'il ne faut pas croire réglés.
- Chantier 2 (C-014 / C-005) — inversion « commit DB puis remise physique » et `giveItem` chaîné via `runAtEntity` aux 6 sites de ZAuctionManager (522-523, 568-569, 602-603, 636-637, 693-696, 883-884). Mon étape 2 rétablit l'EXÉCUTION de `giveItem` à l'arrêt (le handler de rejet supprime la RejectedExecutionException synchrone qui sautait la ligne suivante) mais ne change pas l'ordre « argent retiré -> mémoire mutée -> persistance » : un kill -9 entre withdraw et updateItem reproduit la perte.
- Chantier 1 (C-026 / C-003) — compare-and-set SQL + propagation du rowcount. Sans lui, la sérialisation d'économie de l'étape 5 ferme la course intra-JVM sur le solde mais pas la course en base.
- Chantier 3 (C-027 volet ordonnancement) — faire remonter `createTransaction` en `CompletableFuture<Integer>` et écrire la ligne PENDING AVANT le `withdraw`. Mon étape 3 ne fait que remettre cette écriture sur un pool drainé ; elle ne la rend pas atomique avec l'achat.
- Chantier 4 (C-008 jeton de verrou unique, C-058/C-099 self-heal et fail-closed des scripts Lua). Mes étapes 8 et 9 touchent les mêmes méthodes `lockItem`/`unlockItem` : appliquer C-099 (qui SUPPRIME les replis non atomiques) APRÈS l'étape 8 sous peine de jeter le `loadScriptsOn(jedis)` que j'y introduis.
- Chantier 5/7 (C-035, C-076, C-079 volets machine à états : `statusChangedByUs`, drapeau `itemGone`, « un achat commis ne redescend jamais en échec »). Je ne traite QUE le volet confinement de threads de ces trois constats.
- Chantier 7 (C-023, C-065, C-072 — TTL des statuts de confirmation). L'étape 7 durcit le CAS à l'entrée de la confirmation, ce qui rend un `IS_*_CONFIRM` orphelin bloquant PLUS TÔT et plus visible : livrer C-023 dans la même release.
- Chantier 6 (C-118) — sortir le teardown de ressources de la garde `if (!this.isEnabled) return;` de onDisable. Mon étape 2 positionne délibérément `shuttingDown = true` AVANT cette garde mais ne la supprime pas.
- Chantier PERF (C-066) — `CreateIndexesMigration` et la correction de Sarah (`CREATE INDEX IF NOT EXISTS`), qui porte l'index `players(name)` que la fiche C-081 réclamait.
- Chantier migration (C-034) — sentinelle d'idempotence en base. Mon étape 11 ajoute une garde de ré-entrance LOCALE (par JVM) : elle ne protège pas de deux migrations lancées simultanément sur deux nœuds.

**Constats couverts** : C-022, C-027, C-035, C-048, C-049, C-052, C-076, C-077, C-079, C-081, C-084, C-086, C-087, C-105, C-107, C-117, C-089 (volet exécuteur, partagé avec C-027), C-082 (volet thread de postSell — voir openQuestions, non traité ici)

*~470 LOC*

## 8.1 — Publier le bridge cluster et la permission hors-ligne sous barrière mémoire

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionPlugin.java`  
**Risque** : LOW  
**Constats** : C-117

`auctionClusterBridge` (l.100) est écrit par le onEnable de l'addon Redis et lu par les trois services depuis des threads asynchrones, sans aucune relation happens-before : rien ne garantit qu'un worker voie autre chose que le `LocalAuctionClusterBridge` initial. Le même défaut existe EN PIRE sur `offlinePermission` (l.101), que l'audit a manqué : son setter est PUBLIC dans l'API (`AuctionPlugin.java:137`), donc appelable par n'importe quel plugin tiers depuis n'importe quel thread, et il est lu par ExpireService sur des chemins asynchrones. Le log dans le setter rend enfin vérifiable, dans la console de chaque nœud, le mode réellement en vigueur — aujourd'hui rien ne le dit. Ne PAS passer le champ en `final` : le setter fait partie de l'API publiée et l'addon en dépend.

```java
// --- champs, l.100-101 : ajouter volatile ---
    // Ecrit une seule fois par le onEnable de l'addon Redis, lu par les trois services
    // depuis des workers : sans volatile, aucune relation happens-before ne garantit que
    // le worker voie autre chose que le LocalAuctionClusterBridge initial (C-117).
    private volatile AuctionClusterBridge auctionClusterBridge = new LocalAuctionClusterBridge();

    // Meme defaut, en pire : setOfflinePermission est publie dans l'API (AuctionPlugin:137),
    // donc appelable par un plugin tiers depuis n'importe quel thread, et lu par
    // ExpireService sur des chemins asynchrones.
    private volatile OfflinePermission offlinePermission = new EmptyOfflinePermission();


// --- setter, l.419-422 : remplacer integralement ---
    @Override
    public void setAuctionClusterBridge(AuctionClusterBridge auctionClusterBridge) {
        var previous = this.auctionClusterBridge;
        this.auctionClusterBridge = auctionClusterBridge;
        // Rend le mode reellement actif verifiable dans la console de chaque noeud : un repli
        // silencieux en mono-serveur sur un reseau clusterise est aujourd'hui indetectable.
        getLogger().info("Cluster bridge: " + previous.getClass().getSimpleName()
                + " -> " + auctionClusterBridge.getClass().getSimpleName()
                + " (distributed=" + auctionClusterBridge.isDistributed() + ")");
    }
```

## 8.2 — Executor de stockage non rejetant, nommé et dimensionné + drapeau d'arrêt

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionPlugin.java`  
**Risque** : MEDIUM  
**Constats** : C-022

`Executors.newFixedThreadPool(4)` applique `AbortPolicy` : après `asyncExecutor.shutdown()` en onDisable, `CompletableFuture.runAsync(..., executor)` lève une `RejectedExecutionException` SYNCHRONEMENT depuis ZStorageManager.updateItem. Cette exception synchrone ne se contente pas d'être perdue : elle saute la ligne SUIVANTE de ZAuctionManager (`giveItem(player, auctionItem)`, l.884), d'où le bilan « argent débité, aucun item remis, ligne toujours LISTED » qui remet l'item en vente au redémarrage. Le handler maison exécute la tâche dans le thread appelant : à ce moment la connexion base est encore ouverte (elle n'est fermée qu'APRÈS le drainage), donc l'écriture aboutit. Ne PAS utiliser `ThreadPoolExecutor.CallerRunsPolicy` : son implémentation teste `if (!e.isShutdown())` et DISCARDE silencieusement une fois l'executor arrêté — exactement notre cas. Le passage de 4 à `max(4, availableProcessors())` est un prérequis de l'étape 3, qui va router vers ce même pool tous les logs, transactions et upserts aujourd'hui postés ailleurs.

```java
// --- imports a ajouter ---
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

// --- champs : remplacer l.82 ---
    private static final int STORAGE_POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());
    private final ExecutorService asyncExecutor = createStorageExecutor();
    private volatile boolean shuttingDown = false;

// --- nouvelle methode privee statique ---
    /**
     * Executor dedie a l'IO bloquante (JDBC). Deux differences avec
     * {@code Executors.newFixedThreadPool(4)} :
     * <ul>
     *   <li>threads nommes et daemon, pour que les dumps de threads soient exploitables ;</li>
     *   <li>politique de rejet qui EXECUTE la tache dans le thread appelant au lieu de lever
     *       une RejectedExecutionException synchrone. Pendant onDisable la connexion base est
     *       encore ouverte (elle n'est fermee qu'apres le drainage), donc l'ecriture aboutit
     *       au lieu d'etre perdue en plein milieu d'un achat (C-022).</li>
     * </ul>
     * NE PAS remplacer par {@link ThreadPoolExecutor.CallerRunsPolicy} : elle teste
     * {@code if (!e.isShutdown())} et discarde SILENCIEUSEMENT une fois l'executor arrete,
     * c'est-a-dire precisement dans le cas que l'on cherche a couvrir.
     */
    private static ExecutorService createStorageExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "zAuctionHouse-Storage-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(STORAGE_POOL_SIZE, STORAGE_POOL_SIZE, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), factory,
                (runnable, executor) -> runnable.run());
    }

// --- accesseur : ajouter apres getExecutorService() ---
    @Override
    public boolean isShuttingDown() {
        return this.shuttingDown;
    }

// --- onDisable : remplacer les deux premieres instructions (l.184-185) ---
    @Override
    public void onDisable() {

        // TOUTE PREMIERE instruction, volontairement AVANT la garde isEnabled : les services
        // doivent cesser d'accepter de nouvelles operations avant que quoi que ce soit ne soit
        // ferme (C-022). Le deplacement du teardown hors de cette garde est C-118 / chantier 6
        // et n'est PAS fait ici.
        this.shuttingDown = true;

        if (!this.isEnabled) return;

        // ... reste de onDisable inchange ...
        // L'ordre « drainage de asyncExecutor PUIS storageManager.onDisable() » est ce qui
        // garantit que les ecritures en vol aboutissent : ne jamais l'inverser.
```

## 8.3 — Déclarer le drapeau d'arrêt au contrat API

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/AuctionPlugin.java`  
**Risque** : LOW  
**Constats** : C-022

Les trois services consomment `AuctionPlugin` (l'interface), pas `ZAuctionPlugin` : sans cette méthode ils ne peuvent pas tester le drapeau. Méthode `default` retournant `false` : source- et binaire-compatible, l'addon Redis compilé contre le SHA figé deb8f16 continue de tourner sans recompilation. Le précédent existe dans le même dépôt (`AuctionClusterBridge.isDistributed()`). J'écarte délibérément le compteur d'opérations en vol proposé par l'audit — voir openQuestions : bloquer le thread principal en attendant qu'il retombe à zéro interbloque avec les hops `runAtEntity` que les étapes 5 à 7 introduisent.

```java
// --- a inserer apres getExecutorService() (l.82) ---
    /**
     * Indique si le plugin a commence son arret.
     * <p>
     * Les services (achat, vente, retrait) doivent refuser toute NOUVELLE operation quand
     * cette methode rend {@code true} : leurs chaines tournent sur des pools que le plugin ne
     * controle pas (ForkJoinPool.commonPool, pool Jedis de l'addon) et survivent donc a
     * {@code onDisable}, ou elles debiteraient un acheteur pendant que la connexion base se
     * ferme (C-022).
     * <p>
     * Methode {@code default} : source- et binaire-compatible pour toute implementation tierce
     * existante, comme {@link fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge#isDistributed()}.
     *
     * @return true si l'arret du plugin a commence
     */
    default boolean isShuttingDown() {
        return false;
    }
```

## 8.4 — Router tout le JDBC vers l'executor du plugin, seul pool drainé à l'arrêt

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/storage/ZStorageManager.java`  
**Risque** : MEDIUM  
**Constats** : C-027, C-089, C-077

Deux défauts distincts, un seul fichier. (a) `async()` (l.107) poste sur `plugin.getScheduler().runAsync(...)`, un ordonnanceur que RIEN ne draine, alors que `storageManager.onDisable()` ferme la connexion juste après : la ligne `transactions` PENDING du vendeur — la dette d'un achat déjà encaissé — est perdue à chaque /stop sous charge. Les cinq méthodes concernées sont `upsertPlayer` x2 (l.130/135), `log` (l.166), `createTransaction` (l.170) et `markPurchaseLogAsRead` (l.251). (b) `selectItem` (l.176) et `findUniqueId` (l.214) sont les DEUX SEULS `supplyAsync` du fichier sans exécuteur : leur JDBC bloquant part sur `ForkJoinPool.commonPool`, déjà saturé par les appels Jedis du bridge — c'est la cause directe des faux « Unable to find the item » d'ItemListedListener:34, et c'est ce qui fait basculer la chaîne d'achat hors du thread principal MÊME EN MONO-SERVEUR. Les signatures publiques restent intactes : `markPurchaseLogAsRead` reste `void`, l'addon n'est pas cassé. `async()` rend maintenant un future uniquement pour journaliser l'échec ; le chaîner dans l'achat est C-027 volet ordonnancement (chantier 3).

```java
// --- import a ajouter ---
import java.util.logging.Level;

// --- remplacer async() (l.107-109) ---
    /**
     * Poste une ecriture differee sur l'executor du plugin.
     * <p>
     * Avant : {@code plugin.getScheduler().runAsync(...)}, un ordonnanceur que rien ne draine
     * a l'arret alors que {@link #onDisable()} ferme la connexion base juste apres. La ligne
     * {@code transactions} PENDING du vendeur — la dette d'un achat deja encaisse — etait donc
     * perdue a chaque /stop sous charge (C-027). L'executor du plugin est le SEUL pool draine
     * (5 s) avant la fermeture de la connexion.
     * <p>
     * Le future rendu ne sert aujourd'hui qu'a journaliser l'echec ; le chainer dans le future
     * d'achat releve du chantier 3.
     */
    protected CompletableFuture<Void> async(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, this.plugin.getExecutorService()).exceptionally(throwable -> {
            this.plugin.getLogger().log(Level.SEVERE, "Deferred storage write failed", throwable);
            return null;
        });
    }

// --- selectItem (l.175-176) : ajouter l'executor ---
    @Override
    public CompletableFuture<Item> selectItem(int id) {
        // Executor explicite : sans lui, ce JDBC bloquant (3 requetes) partait sur
        // ForkJoinPool.commonPool, deja sature par les appels Jedis du bridge, et faisait
        // basculer toute la chaine d'achat hors du thread principal, y compris en
        // mono-serveur (C-077 / C-089).
        return CompletableFuture.supplyAsync(() -> {

            // ... corps inchange ...

        }, this.plugin.getExecutorService());
    }

// --- findUniqueId (l.213-215) : remplacer ---
    @Override
    public CompletableFuture<UUID> findUniqueId(String playerName) {
        // Second et dernier supplyAsync orphelin du fichier (C-077).
        return CompletableFuture.supplyAsync(() -> this.with(PlayerRepository.class).selectByName(playerName), this.plugin.getExecutorService());
    }
```

## 8.5 — Rendre ZPlayerCache sûr en concurrence sans changer sa sémantique

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/utils/cache/ZPlayerCache.java`  
**Risque** : MEDIUM  
**Constats** : C-105

L'EnumMap nue est mutée depuis le thread principal (rendu d'inventaire), depuis l'executor de stockage (callbacks d'achat) et depuis commonPool (bridge Redis). Je REJETTE le correctif proposé par l'audit (`ConcurrentHashMap` + `computeIfAbsent`) pour deux raisons vérifiées. (1) `computeIfAbsent` y est ré-entrance-hostile : `getOrCompute(ITEMS_EXPIRED, ...)` (ZAuctionManager:303) descend dans `getItemIds` (:395), puis `processExpiredItems` (:419), puis `clearPlayersCache` (:493) — soit un `remove()` sur LA MÊME map pendant que son `computeIfAbsent` est en cours ; si les deux clés tombent dans le même bin, `replaceNode` se synchronise sur le ReservationNode (hash RESERVED = -3), n'entre dans aucune branche de validation et respin sans fin : gel du thread principal, non déterministe. (2) `ConcurrentHashMap` interdit les valeurs nulles, or `LogTypeFilterButton:69` et `TransactionStatusFilterButton:69` écrivent DÉLIBÉRÉMENT `null` pour l'état « aucun filtre » (`getNextFilter` rend null à l'index 0). Un moniteur réentrant sur EnumMap évite les deux. Le supplier reste évalué HORS du moniteur, sinon on le tiendrait pendant un aller-retour base.

```java
// --- imports ---
import java.util.Collections;

// --- champ : remplacer l.14 ---
    /**
     * EnumMap enveloppee dans un moniteur reentrant plutot qu'un ConcurrentHashMap :
     * <ul>
     *   <li>ConcurrentHashMap INTERDIT les valeurs nulles, or LogTypeFilterButton:69 et
     *       TransactionStatusFilterButton:69 ecrivent deliberement {@code null} pour l'etat
     *       « aucun filtre » ;</li>
     *   <li>{@code computeIfAbsent} y est re-entrance-hostile : getOrCompute(ITEMS_EXPIRED, …)
     *       redescend dans clearPlayersCache(), donc dans un remove() sur CETTE map, ce qui
     *       peut faire respiner le thread principal a l'infini sur le ReservationNode.</li>
     * </ul>
     * Toutes les operations utilisees ici (get / put / remove / containsKey) sont atomiques
     * sous le moniteur ; aucune iteration n'est faite sur cette map.
     */
    private final Map<PlayerCacheKey, Object> cache = Collections.synchronizedMap(new EnumMap<>(PlayerCacheKey.class));

// --- getOrCompute : remplacer l.52-60 ---
    @Override
    public <T> T getOrCompute(PlayerCacheKey key, Supplier<T> supplier) {
        if (has(key)) {
            return get(key);
        }

        // Le supplier est evalue HORS du moniteur : getItemIds() redescend dans
        // processExpiredItems() puis clearPlayersCache(), qui reprend ce meme moniteur, et il
        // peut declencher des ecritures base. Le tenir ici serrerait le verrou pendant un
        // aller-retour SQL, et le double calcul concurrent — deja possible aujourd'hui — reste
        // inoffensif (les deux threads calculent la meme valeur).
        T value = supplier.get();
        set(key, value);
        return value;
    }
```

## 8.6 — Indexer les caches joueur par UUID, cesser de les ressusciter, confiner la mutation d'ITEMS_LISTED

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : MEDIUM  
**Constats** : C-107, C-105

Trois corrections liées à la même map. (a) `Map<Player, PlayerCache>` retient une référence forte vers le CraftPlayer, son inventaire et son monde pour toute déconnexion dont `removeCache` n'est pas atteint. (b) `clearPlayerCache` (l.497-499) fait `getCache(player).remove(keys)` — donc un `computeIfAbsent` — et `finishBulkRemoval` (l.1167-1168) l'appelle DANS le `runAtEntity` AVANT le test `if (!player.isOnline()) return;` de la l.1174 : c'est un chemin de résurrection du cache d'un joueur déconnecté, atteint à chaque « tout récupérer » suivi d'une déconnexion. Ce défaut n'est relevé nulle part dans l'audit (la fiche C-107 cite à tort les lignes 979-980, qui sont `removeFromCache`, comme exemple de méthode prudente). (c) `removeFromCache` mute un `IntArrayList` NON thread-safe partagé avec le rendu d'inventaire, et la branche `update-inventory-on-action: false` (l.940-947) le fait pour TOUS les joueurs en ligne depuis un thread arbitraire. Le passage à UUID fait survivre le cache à une reconnexion dans le même tick : c'est correct puisque onQuit purge, mais à tester explicitement sur un changement de serveur BungeeCord rapide.

```java
// --- champ : remplacer l.62 ---
    // Indexation par UUID et non par Player : la cle Player retenait une reference forte vers
    // le CraftPlayer, son inventaire et son monde pour toute deconnexion dont removeCache
    // n'etait pas atteint (C-107). Les signatures publiees de l'API (getCache/clearPlayerCache/
    // removeCache prennent un Player) sont INCHANGEES : le changement est purement interne.
    private final Map<UUID, PlayerCache> caches = new ConcurrentHashMap<>();

// --- remplacer le bloc l.486-504 ---
    @Override
    public PlayerCache getCache(Player player) {
        return this.caches.computeIfAbsent(player.getUniqueId(), uuid -> new ZPlayerCache());
    }

    /**
     * Accesseur NON creant, a utiliser sur tous les chemins de completion tardive (fin de
     * retrait en masse, callbacks d'achat/expiration, fermeture d'inventaire) ou
     * {@link #getCache(Player)} ressusciterait le cache d'un joueur deja deconnecte.
     * Volontairement prive : le remonter dans l'interface publiee AuctionManager rendrait ce
     * correctif de fuite api-breaking pour un gain nul.
     */
    private PlayerCache peekCache(Player player) {
        return this.caches.get(player.getUniqueId());
    }

    @Override
    public void clearPlayersCache(PlayerCacheKey... keys) {
        this.caches.forEach((uniqueId, cache) -> cache.remove(keys));
    }

    @Override
    public void clearPlayerCache(Player player, PlayerCacheKey... keys) {
        // No-op quand aucune entree n'existe : finishBulkRemoval nous appelle AVANT son test
        // isOnline(), ce qui ressuscitait le cache d'un joueur deconnecte a chaque
        // « tout recuperer ». Verifie : aucun appelant actuel ne comptait sur l'effet de bord
        // « creer le cache ».
        var cache = peekCache(player);
        if (cache != null) cache.remove(keys);
    }

    @Override
    public void removeCache(Player player) {
        this.caches.remove(player.getUniqueId());
    }

// --- remplacer removeFromCache (l.978-986) ---
    private void removeFromCache(Player player, Item item) {
        // ITEMS_LISTED est un IntArrayList NON thread-safe partage avec le rendu de
        // l'inventaire du joueur. La branche update-inventory-on-action:false (l.940-947) le
        // mute pour TOUS les joueurs en ligne depuis un thread arbitraire, pendant que le
        // thread proprietaire l'itere. On confine donc la mutation au thread du joueur (C-105).
        var scheduler = this.plugin.getScheduler();
        if (scheduler.isOwnedByCurrentRegion(player)) {
            removeFromCacheNow(player, item);
            return;
        }
        scheduler.runAtEntity(player, wrappedTask -> removeFromCacheNow(player, item));
    }

    private void removeFromCacheNow(Player player, Item item) {
        var cache = peekCache(player);
        if (cache == null) return;
        IntList items = cache.get(PlayerCacheKey.ITEMS_LISTED);
        if (items != null && !items.isEmpty()) {
            items.rem(item.getId());
        }
    }
```

## 8.7 — Sérialiser et confiner les mutations d'économie par compte

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/economy/ZAuctionEconomy.java`  
**Risque** : MEDIUM  
**Constats** : C-084

`deposit`/`withdraw` sont appelés depuis commonPool et depuis l'executor de stockage, sans aucune sérialisation : deux crédits concurrents sur le même compte via un provider en read-modify-write (get puis set) se perdent l'un l'autre. Deux branches, mutuellement exclusives et volontairement différentes. (1) Pour ITEM / ZMENUITEMS / LEVEL / EXPERIENCE, le provider manipule des API Bukkit main-thread-only (`player.setLevel`, inventaire) : un `synchronized` ne les rend PAS légaux — c'est le point que la fiche d'audit sous-estime. Seul le hop vers le thread propriétaire corrige, et il sérialise du même coup (une seule région par joueur), donc pas de verrou. (2) Pour tous les autres, verrous stripés bornés — je déconseille la `ConcurrentHashMap<UUID,Object>` alimentée par `computeIfAbsent` proposée par l'audit, qui fuit une entrée par joueur pour la durée de vie de la JVM. Le contrat reste SYNCHRONE : l'appelant (ZAuctionManager:802/823) doit pouvoir considérer l'argent comme déplacé au retour, sans quoi on remplacerait une course par un mensonge. D'où l'attente bornée à 5 s — et jamais depuis le thread propriétaire lui-même, ce qui exclut l'auto-interblocage. Le timeout couvre aussi le piège FoliaLib vérifié : `runAtEntity` peut ne JAMAIS compléter son future si l'entité est retirée après la planification.

```java
// --- imports ---
import com.tcoded.folialib.enums.EntityTaskResult;
import fr.traqueur.currencies.Currencies;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

// --- champs statiques et d'instance ---
    /**
     * Providers qui font du read-modify-write nu sur des API Bukkit main-thread-only
     * (player.setLevel, player.giveExp, inventaire). Les appeler depuis un worker est illegal
     * sous Paper et refuse sous Folia : ces types passent par le thread proprietaire du joueur,
     * ce qui les serialise du meme coup — inutile d'y ajouter un verrou.
     */
    private static final EnumSet<Currencies> OWNER_THREAD_CURRENCIES = EnumSet.of(Currencies.ITEM, Currencies.ZMENUITEMS, Currencies.LEVEL, Currencies.EXPERIENCE);

    /**
     * Verrous stripes, bornes et sans allocation : serialisent les mutations de solde par
     * compte pour tous les AUTRES providers. Une ConcurrentHashMap<UUID, Object> alimentee par
     * computeIfAbsent fuirait une entree par joueur pour la duree de vie de la JVM.
     */
    private static final int ACCOUNT_STRIPES = 64;
    private static final Object[] ACCOUNT_LOCKS = createAccountLocks();

    private final boolean ownerThreadRequired;

    private static Object[] createAccountLocks() {
        Object[] locks = new Object[ACCOUNT_STRIPES];
        for (int i = 0; i < ACCOUNT_STRIPES; i++) locks[i] = new Object();
        return locks;
    }

    private static Object lockFor(UUID playerId) {
        return ACCOUNT_LOCKS[Math.floorMod(playerId.hashCode(), ACCOUNT_STRIPES)];
    }

// --- constructeur : ajouter le parametre `Currencies currencies` juste apres currencyProvider,
//     et dans le corps : ---
        this.ownerThreadRequired = currencies != null && OWNER_THREAD_CURRENCIES.contains(currencies);

// --- remplacer deposit / withdraw (l.85-93) ---
    @Override
    public void deposit(UUID playerId, BigDecimal value, String reason) {
        mutateBalance(playerId, () -> this.currencyProvider.deposit(playerId, value, reason), "deposit");
    }

    @Override
    public void withdraw(UUID playerId, BigDecimal value, String reason) {
        mutateBalance(playerId, () -> this.currencyProvider.withdraw(playerId, value, reason), "withdraw");
    }

    /**
     * Execute une mutation de solde en respectant deux invariants (C-084) :
     * <ol>
     *   <li>les providers main-thread-only sont executes sur le thread proprietaire du joueur
     *       (inline si on y est deja, sinon hop + attente bornee) ;</li>
     *   <li>tous les autres sont serialises par compte via un verrou stripe.</li>
     * </ol>
     * Le contrat reste SYNCHRONE : ZAuctionManager doit pouvoir considerer l'argent comme
     * deplace au retour. On ne bloque JAMAIS le thread proprietaire (branche inline), donc pas
     * d'auto-interblocage ; l'attente est bornee a 5 s et journalisee en SEVERE.
     * <p>
     * LIMITE ASSUMEE : ce verrou ne protege que les appels passant par ZAuctionEconomy. Un
     * plugin tiers touchant le meme compte via Vault n'est pas serialise — la fenetre est
     * reduite, pas fermee. Sa fermeture reelle exige le retour d'EconomyResponse dans
     * CurrenciesAPI (chantier 3).
     */
    private void mutateBalance(UUID playerId, Runnable mutation, String operation) {

        if (!this.ownerThreadRequired) {
            synchronized (lockFor(playerId)) {
                mutation.run();
            }
            return;
        }

        var scheduler = this.plugin.getScheduler();
        Player player = Bukkit.getPlayer(playerId);

        // Joueur hors ligne : ce provider ne peut de toute facon rien faire d'utile. C'est le
        // sujet de C-032 (chantier 3) ; on ne change pas son comportement ici.
        if (player == null || scheduler.isOwnedByCurrentRegion(player)) {
            mutation.run();
            return;
        }

        try {
            // Timeout indispensable : FoliaImplementation.runAtEntity peut ne JAMAIS completer
            // son future si l'entite est retiree apres la planification.
            EntityTaskResult result = scheduler.runAtEntity(player, wrappedTask -> mutation.run()).get(5, TimeUnit.SECONDS);
            if (result != EntityTaskResult.SUCCESS) {
                this.plugin.getLogger().severe("Economy " + operation + " for " + playerId + " was not executed (" + result + ") on economy " + this.name);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.plugin.getLogger().severe("Interrupted while performing a " + operation + " for " + playerId + " on economy " + this.name);
        } catch (ExecutionException | TimeoutException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to " + operation + " on the owning thread for " + playerId + " on economy " + this.name, exception);
        }
    }
```

## 8.8 — Propager le type d'économie à ZAuctionEconomy

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/economy/ZEconomyManager.java`  
**Risque** : LOW  
**Constats** : C-084

L'étape 7 a besoin de savoir si le provider est main-thread-only. Tester `instanceof ItemProvider/LevelProvider/…` créerait une dépendance de compilation dure sur des classes internes de CurrenciesAPI ; tester le nom de classe en chaîne est fragile. La valeur `Currencies` est déjà résolue ici même (`findCurrencies(type)`, l.320) : il suffit de la transmettre. Seul site d'instanciation de ZAuctionEconomy dans les deux dépôts (vérifié par grep) — aucun impact tiers, la classe n'est pas dans api/.

```java
// --- l.365 : ajouter `currencies` en 3e argument ---
        // `currencies` est transmis pour que ZAuctionEconomy sache si le provider fait du
        // read-modify-write sur des API Bukkit main-thread-only (ITEM / ZMENUITEMS / LEVEL /
        // EXPERIENCE) et doive donc muter le solde sur le thread proprietaire du joueur (C-084).
        var auctionEconomy = new ZAuctionEconomy(this.plugin, currencyProvider, currencies, name, displayName, format, symbol, permission, depositReason, withdrawReason, priceFormat, minPrices, maxPrices, autoClaim, mustBeOnline, taxConfiguration);
```

## 8.9 — Message d'indisponibilité pendant l'arrêt

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/messages/Message.java`  
**Risque** : LOW  
**Constats** : C-022

Sans message, la garde d'arrêt de l'étape 10 refuse l'achat en silence (ConfirmPurchaseButton ignore totalement le PurchaseResult retourné) : le joueur clique et rien ne se passe. Ajout additif à un enum publié — vérifié par grep, aucun `switch` exhaustif sur `Message` n'existe dans les deux dépôts. Le texte anglais est porté par le constructeur : un messages.yml oublié dégrade en anglais, il ne plante pas.

```java
// --- l.196 : remplacer le point-virgule final par une virgule et ajouter la constante ---
    ADMIN_LOGS_INVALID_DAYS("<error>Please specify a valid number of days (greater than 0)."),
    SERVER_SHUTTING_DOWN("<error>The server is shutting down, please try again later.");

// --- messages.yml, A REPLIQUER DANS LES SIX JEUX DE LANGUE ---
// racine (EN) : server-shutting-down: "<error>The server is shutting down, please try again later."
// fr/         : server-shutting-down: "<error>Le serveur est en cours d'arrêt, réessayez plus tard."
// es/         : server-shutting-down: "<error>El servidor se está apagando, inténtalo más tarde."
// it/         : server-shutting-down: "<error>Il server si sta spegnendo, riprova più tardi."
// id/         : server-shutting-down: "<error>Server sedang dimatikan, coba lagi nanti."
// th/         : server-shutting-down: "<error>เซิร์ฟเวอร์กำลังปิด กรุณาลองใหม่ภายหลัง"
```

## 8.10 — Services : refuser les opérations pendant l'arrêt et confiner les appels Bukkit au thread du joueur

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/PurchaseService.java`  
**Risque** : MEDIUM  
**Constats** : C-022, C-035, C-076, C-079

Deux corrections. (a) La garde d'arrêt : sans elle le drapeau de l'étape 2 est inerte, et une chaîne d'achat lancée après le début de onDisable débite l'acheteur pendant que la connexion base se ferme. (b) Le confinement : les trois `inventoryManager.updateInventory(player)` (l.86, 100, 122) et le `message(...)` de la l.144 s'exécutent dans des `thenCompose` dont le thread d'exécution est celui qui complète le future amont — commonPool via le bridge Redis, ou l'executor de stockage via `selectItem` (l.118). Ce sont des appels zMenu/Bukkit hors thread principal : violation silencieuse sous Paper, refus immédiat sous Folia. C'est le volet chantier 8 de C-035, C-076 et C-079 ; leurs volets machine à états (drapeau `statusChangedByUs`, distinction des deux causes d'abandon, « un achat commis ne redescend jamais en échec ») appartiennent aux chantiers 4/7 et ne sont PAS traités ici — le patch ci-dessous est volontairement neutre vis-à-vis d'eux, il ne fait que déplacer le thread d'exécution. La même garde d'arrêt s'ajoute en tête de `RemoveService.executeRemoval` (l.193, choke point unique des quatre entrées publiques) et de `SellService.sellAuctionItems` (l.44).

```java
// --- PurchaseService : en tete de purchaseItem, AVANT le callEvent (l.33) ---
        // Les chaines d'achat tournent sur des pools que le plugin ne controle pas (commonPool,
        // pool Jedis) : elles survivent a onDisable et debiteraient l'acheteur pendant que la
        // connexion base se ferme (C-022). ConfirmPurchaseButton ignorant le PurchaseResult,
        // le refus DOIT etre visible par un message.
        if (this.plugin.isShuttingDown()) {
            message(this.plugin, player, Message.SERVER_SHUTTING_DOWN);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Server is shutting down", PurchaseFailReason.INTERNAL_ERROR));
        }

// --- PurchaseService : nouvelle methode privee ---
    /**
     * Toute interaction Bukkit/zMenu declenchee depuis la chaine asynchrone doit repasser par
     * le thread proprietaire du joueur : ces continuations s'executent sur le thread qui
     * complete le future amont — ForkJoinPool.commonPool via le bridge Redis, l'executor de
     * stockage via selectItem — jamais sur le thread principal (C-035 / C-076 / C-079).
     */
    private void onPlayerThread(Player player, Runnable runnable) {
        var scheduler = this.plugin.getScheduler();
        if (scheduler.isOwnedByCurrentRegion(player)) {
            runnable.run();
            return;
        }
        scheduler.runAtEntity(player, wrappedTask -> runnable.run());
    }

// --- PurchaseService : les 3 sites d'updateInventory (l.86, 100, 122) ---
-                        inventoryManager.updateInventory(player);
+                        onPlayerThread(player, () -> inventoryManager.updateInventory(player));

// --- PurchaseService : le message d'echec de solde (l.144) ---
-                    message(this.plugin, player, Message.NOT_ENOUGH_MONEY);
+                    onPlayerThread(player, () -> message(this.plugin, player, Message.NOT_ENOUGH_MONEY));


// --- RemoveService.executeRemoval : en tete (l.193) ---
    private CompletableFuture<RemoveResult> executeRemoval(ItemStatus targetStatus, Player player, Item item, Runnable onUnavailable, Supplier<CompletableFuture<Void>> onLocalRemoval, StorageType storageType, StorageType destinationStorageType) {

        // Choke point unique des quatre entrees publiques de retrait (C-022).
        if (this.plugin.isShuttingDown()) {
            message(this.plugin, player, Message.SERVER_SHUTTING_DOWN);
            return CompletableFuture.completedFuture(RemoveResult.failure("Server is shutting down", RemoveFailReason.INTERNAL_ERROR));
        }

        var context = new RemovalContext(item, targetStatus, storageType, destinationStorageType, onUnavailable, onLocalRemoval);
        // ... reste inchange ...


// --- SellService.sellAuctionItems : en tete (l.44) ---
        // Une vente engagee pendant l'arret retire les items de l'inventaire puis voit son
        // INSERT rejete : le joueur perd son lot (C-022).
        if (this.plugin.isShuttingDown()) {
            message(this.plugin, player, Message.SERVER_SHUTTING_DOWN);
            return CompletableFuture.completedFuture(SellResult.failure("Server is shutting down", SellFailReason.INTERNAL_ERROR));
        }
```

## 8.11 — Confirmations d'achat et de retrait : un seul chemin, exécuté sur le thread du clic

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/buttons/list/ListedItemsButton.java`  
**Risque** : MEDIUM  
**Constats** : C-048, C-049

Le même défaut est écrit deux fois (retrait l.128-133, achat l.228-235) : le statut, les caches et l'ouverture de la GUI vivent dans un `thenRun` accroché à `notifyItemStatusChange`, donc exécuté sur commonPool dès que le bridge Redis est installé — et jamais exécuté du tout si le transport échoue, laissant le joueur sans aucun retour après son clic. Je corrige les DEUX SITES par une méthode unique, comme le suggère la fin de la fiche C-049. L'ordre est volontairement inversé : engagement local d'abord, diffusion ensuite. Deux rectifications à l'audit, vérifiées dans le code : (1) l'ouverture d'inventaire n'est PAS faite hors thread principal aujourd'hui — `ZInventoriesLoader.openInventory:270` replanifie déjà via `runAtEntity` ; (2) `ZAuctionHouseRedis.sendMessage:274-282` AVALE l'échec de publication, donc le future se complète normalement et le `thenRun` n'est pas « sauté en silence » par ce chemin-là. Le vrai gain est ailleurs : la garde compare-and-set. `createClick` ne vérifie JAMAIS `item.getStatus()` — seul `isExpired()` est testé (l.104) — alors que `onRender` filtre sur `isActivelyListed()` : le Consumer de clic d'un item devenu IS_BEING_PURCHASED sur un autre serveur reste armé dans la GUI déjà rendue, et avec `update-inventory-on-action: false` (config.yml:774) il n'y a JAMAIS de rafraîchissement. Enfin le garde anti-double-clic PURCHASE_ITEM n'est plus relâché avant l'engagement.

```java
// --- imports a ajouter ---
import java.util.logging.Level;

// --- nouvelle methode privee ---
    /**
     * Chemin commun aux deux confirmations (retrait par le vendeur, achat par un tiers).
     * <p>
     * Ordre volontairement inverse par rapport au code d'origine : garde compare-and-set, pose
     * du statut LOCAL, ecriture des caches et ouverture de la GUI de maniere SYNCHRONE sur le
     * thread du clic, PUIS diffusion au cluster. Avant, tout cela vivait dans un {@code thenRun}
     * execute sur ForkJoinPool.commonPool (C-048 / C-049).
     * <p>
     * Contrepartie assumee : si la diffusion echoue, ce serveur masque l'item alors que les
     * autres le laissent visible — divergence inverse de celle d'aujourd'hui, mais bornee, car
     * ConfirmHelper.onInventoryClose restaure AVAILABLE a la fermeture. On ne vend jamais deux
     * fois localement, ce qui est le bon compromis.
     */
    private void openConfirmation(Player player, Item item, Inventories inventories, ItemStatus confirmStatus, boolean refreshSpectators) {

        var scheduler = this.plugin.getScheduler();
        if (!scheduler.isOwnedByCurrentRegion(player)) {
            // Une implementation tierce d'AuctionEconomy peut rendre un has() reellement
            // asynchrone : on se re-confine avant de toucher aux caches et a la GUI.
            scheduler.runAtEntity(player, wrappedTask -> openConfirmation(player, item, inventories, confirmStatus, refreshSpectators));
            return;
        }

        var manager = this.plugin.getAuctionManager();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();
        var cache = manager.getCache(player);

        // Garde compare-and-set : createClick ne testait que isExpired(), jamais le statut. Le
        // Consumer de clic d'un item engage sur un AUTRE serveur reste arme dans la GUI deja
        // rendue, et avec update-inventory-on-action:false il n'y a jamais de rafraichissement.
        if (item.getStatus() != ItemStatus.AVAILABLE) {
            cache.set(PlayerCacheKey.PURCHASE_ITEM, false);
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
            inventoryManager.updateInventory(player);
            return;
        }

        cache.set(PlayerCacheKey.ITEM_SHOW, item);
        cache.set(PlayerCacheKey.CURRENT_PAGE, inventoryManager.getPage(player));

        item.setStatus(confirmStatus);

        // Le garde anti-double-clic n'est relache qu'APRES l'engagement, jamais avant.
        cache.set(PlayerCacheKey.PURCHASE_ITEM, false);

        if (refreshSpectators) {
            manager.updateListedItems(item, false, player);
        }

        this.plugin.getInventoriesLoader().openInventory(player, inventories);

        // Diffusion APRES l'engagement local : un echec de transport ne doit plus empecher
        // l'ouverture de la GUI. Rendre cet echec bloquant est C-028 (chantier 7).
        this.plugin.getAuctionClusterBridge()
                .notifyItemStatusChange(item, ItemStatus.AVAILABLE, confirmStatus)
                .exceptionally(throwable -> {
                    this.plugin.getLogger().log(Level.WARNING, "Failed to broadcast " + confirmStatus + " for item " + item.getId(), throwable);
                    return null;
                });
    }

// --- createClick, branche retrait : remplacer l.126-133 ---
                    var isMultipleAuctionItem = item instanceof AuctionItem auctionItem && auctionItem.getItemStacks().size() > 1;
                    openConfirmation(player, item, isMultipleAuctionItem ? Inventories.REMOVE_INVENTORY_CONFIRM : Inventories.REMOVE_CONFIRM, ItemStatus.IS_REMOVE_CONFIRM, false);

// --- processPurchase, branche succes : remplacer l.224-235 ---
            openConfirmation(player, item, inventories, ItemStatus.IS_PURCHASE_CONFIRM, true);

// NOTE : les deux `cache.set(PlayerCacheKey.PURCHASE_ITEM, false);` des branches d'echec
// anticipe (economie absente l.179, throwable l.198, solde insuffisant l.220) sont CONSERVES
// tels quels ; seul celui de la branche de succes (l.226) disparait, absorbe par
// openConfirmation.
```

## 8.12 — Addon : borner l'attente du pool Jedis et supprimer les emprunts de connexion imbriqués

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/connection/RedisConnectionFactory.java`  
**Risque** : MEDIUM  
**Constats** : C-052

`buildPoolConfig` (l.66-71) ne pose ni `setMaxWait` ni `setBlockWhenExhausted` : on garde les défauts commons-pool2, soit une attente ÉTERNELLE dans `getResource()`. Mais borner l'attente ne suffit pas — il faut supprimer la cause. L'audit ne voit que les 2 emprunts imbriqués de la branche NOSCRIPT (`loadScripts()` rappelé depuis un `try(Jedis)`, l.204 et l.264). Il en manque TROIS, permanents et sur le chemin chaud : `notifyItemBought` (l.293), `notifyItemListed` (l.319) et `removeItem` (l.352) ouvrent une connexion puis appellent `plugin.sendMessage()` qui en reprend une SECONDE. Ces trois-là sont le vrai déclencheur de l'épuisement — un simple SCRIPT FLUSH n'est pas nécessaire. Cette étape DOIT précéder l'étape 13 : donner un pool dédié dimensionné sur `max-total` alors que chaque tâche tient deux connexions imbriquées produit un interblocage définitif et silencieux du bridge, bien pire que la lenteur corrigée. J'ÉCARTE le `setTestOnBorrow(true)` proposé par l'audit : il ajoute un PING à chaque emprunt sur un chemin appelé plusieurs fois par achat, alors que `JedisPoolConfig` active déjà `testWhileIdle` avec éviction toutes les 30 s.

```java
// --- RedisConnectionFactory : import + buildPoolConfig ---
import java.time.Duration;

    private static JedisPoolConfig buildPoolConfig(RedisConfig config) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getMaxTotal());
        poolConfig.setMaxIdle(config.getMaxIdle());
        poolConfig.setMinIdle(config.getMinIdle());
        // Sans cette borne, commons-pool2 applique blockWhenExhausted=true et maxWait=-1 :
        // une attente ETERNELLE dans getResource(), qu'aucun timeout ne rompt (C-052). Une
        // JedisExhaustedPoolException remontee au CompletableFuture vaut infiniment mieux
        // qu'un thread fige pour toujours. On NE pose PAS setTestOnBorrow : JedisPoolConfig
        // active deja testWhileIdle avec une eviction toutes les 30 s, et un PING par emprunt
        // sur un chemin appele plusieurs fois par achat serait un cout net.
        poolConfig.setMaxWait(Duration.ofMillis(config.getTimeout()));
        return poolConfig;
    }


// --- ZAuctionHouseRedis : surcharge de sendMessage sur une connexion deja tenue ---
    /**
     * Publie sur une connexion DEJA empruntee par l'appelant. Sans cette surcharge,
     * notifyItemBought / notifyItemListed / removeItem tiennent DEUX connexions imbriquees a
     * chaque vente, annonce et retrait — ce sont les emprunts imbriques permanents que
     * l'audit n'avait pas identifies (C-052).
     * <p>
     * Semantique d'echec INCHANGEE (log WARNING, future complete normalement) : la rendre
     * bloquante est C-028, chantier 7.
     */
    public <T> void sendMessage(Jedis jedis, T message) {
        String jsonMessage = this.gson.toJson(new RedisMessage<>(INSTANCE_UUID, message, message.getClass().getName()));
        this.debug("Send: " + jsonMessage);
        try {
            jedis.publish(this.channelName, jsonMessage);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to send Redis message", e);
        }
    }

    public <T> void sendMessage(T message) {
        try (Jedis jedis = jedisPool.getResource()) {
            sendMessage(jedis, message);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to send Redis message", e);
        }
    }


// --- RedisAuctionClusterBridge : rechargement des scripts sur la connexion tenue ---
    /**
     * Recharge les scripts SUR LA CONNEXION DEJA EMPRUNTEE. L'ancien {@code loadScripts()}
     * appele depuis l'interieur d'un try(Jedis) demandait une SECONDE connexion au pool.
     */
    private void loadScriptsOn(Jedis jedis) {
        synchronized (scriptLoadLock) {
            try {
                this.lockScriptSha = jedis.scriptLoad(LOCK_SCRIPT);
                this.unlockScriptSha = jedis.scriptLoad(UNLOCK_SCRIPT);
                this.scriptsLoaded = true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to reload Lua scripts: " + e.getMessage());
                this.scriptsLoaded = false;
            }
        }
    }

// --- lockItem l.203-205 et unlockItem l.262-264 : remplacer les deux occurrences de ---
-                            scriptsLoaded = false;
-                            loadScripts();
+                            loadScriptsOn(jedis);

// --- notifyItemBought (l.301), notifyItemListed (l.328), removeItem (l.360) :
//     remplacer les trois appels imbriques ---
-                this.plugin.sendMessage(new ItemBoughtMessage(...));
+                this.plugin.sendMessage(jedis, new ItemBoughtMessage(...));
-                this.plugin.sendMessage(new ItemListedMessage(String.valueOf(item.getId())));
+                this.plugin.sendMessage(jedis, new ItemListedMessage(String.valueOf(item.getId())));
-                this.plugin.sendMessage(new ItemRemovedMessage(String.valueOf(item.getId()), sourceStorageType, destinationStorageType));
+                this.plugin.sendMessage(jedis, new ItemRemovedMessage(String.valueOf(item.getId()), sourceStorageType, destinationStorageType));
```

## 8.13 — Addon : exécuteur dédié pour le bridge, fermé avant le pool Jedis

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/RedisAuctionClusterBridge.java`  
**Risque** : MEDIUM  
**Constats** : C-077

Les 7 `supplyAsync`/`runAsync` du bridge (l.159, 182, 245, 292, 318, 336, 351) postent de l'IO Jedis bloquante sur `ForkJoinPool.commonPool`, dont le parallélisme vaut `availableProcessors() - 1` : 3 workers sur une machine 4 cœurs. Un blocage d'I/O simple dans une ForkJoinTask ne déclenche AUCUNE compensation (seul ManagedBlocker le fait), donc le pool ne crée pas de worker de secours ; les `orTimeout` de PurchaseService se déclenchent alors AU MILIEU de la section critique, ce qui produit les verrous fuités. Les auteurs ont d'ailleurs déjà évité commonPool ailleurs (`SortedItemsCache` a son propre ForkJoinPool). Je m'écarte du correctif de l'audit sur le DIMENSIONNEMENT : `newFixedThreadPool(pool.max-total)` créerait 64 threads pour 64 connexions ; même après l'étape 12 la marge est nulle (thread abonné + heartbeat empruntent aussi). `max-total/2` laisse 50 % de marge. L'arrêt AVANT `jedisPool.close()` est impératif, sinon les tâches en vol empruntent sur un pool fermé.

```java
// --- RedisAuctionClusterBridge : champ + constructeur ---
    private final Executor executor;

    public RedisAuctionClusterBridge(ZAuctionHouseRedis plugin, Pool<Jedis> jedisPool, Executor executor, Duration lockTtl, Duration itemStateTtl, Duration itemListedTtl) {
        this.plugin = plugin;
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool");
        this.executor = Objects.requireNonNull(executor, "executor");
        // ... reste inchange ...
    }

// --- les 7 sites : ajouter l'executor en second argument ---
// l.159  checkAvailability      : CompletableFuture.supplyAsync(() -> { ... }, this.executor);
// l.182  lockItem               : CompletableFuture.supplyAsync(() -> { ... }, this.executor);
// l.245  unlockItem             : CompletableFuture.runAsync(() -> { ... }, this.executor);
// l.292  notifyItemBought       : CompletableFuture.runAsync(() -> { ... }, this.executor);
// l.318  notifyItemListed       : CompletableFuture.runAsync(() -> { ... }, this.executor);
// l.336  notifyItemStatusChange : CompletableFuture.runAsync(() -> ..., this.executor);
// l.351  removeItem             : CompletableFuture.runAsync(() -> { ... }, this.executor);


// --- ZAuctionHouseRedis : champs ---
    private ExecutorService redisExecutor;

// --- ZAuctionHouseRedis.onEnable, juste avant setAuctionClusterBridge (l.99) ---
        // Executor dedie : sans lui, l'IO Jedis bloquante partait sur ForkJoinPool.commonPool
        // (parallelisme coeurs-1, aucune compensation sur blocage d'I/O simple), ce qui figeait
        // toutes les operations cluster du noeud des que Redis ralentissait (C-077).
        //
        // DIMENSIONNEMENT : la MOITIE de pool.max-total, et non sa totalite comme le propose
        // l'audit. Meme apres la suppression des emprunts imbriques (etape 12), le thread
        // abonne monopolise une connexion a vie et le heartbeat en emprunte une par battement :
        // 50 % de marge est le minimum. Dimensionner a max-total avec blockWhenExhausted=true
        // reintroduirait un interblocage.
        int maxTotal = getConfig().getInt("redis-config.pool.max-total", 10);
        int poolSize = Math.max(2, maxTotal / 2);
        AtomicInteger bridgeThreadCounter = new AtomicInteger();
        this.redisExecutor = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "zAuctionHouse-Redis-Bridge-" + bridgeThreadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        getLogger().info("Redis bridge executor started with " + poolSize + " threads (pool.max-total=" + maxTotal + ").");

        this.auctionPlugin.setAuctionClusterBridge(new RedisAuctionClusterBridge(this, this.jedisPool, this.redisExecutor, this.lockTtl, itemStateTtl, itemListedTtl));

// --- ZAuctionHouseRedis.onDisable : AVANT jedisPool.close() (l.137) ---
        // ORDRE IMPERATIF : l'executor doit etre arrete AVANT la fermeture du pool, sinon les
        // taches en vol empruntent sur un pool ferme.
        if (this.redisExecutor != null) {
            this.redisExecutor.shutdown();
            try {
                if (!this.redisExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    this.redisExecutor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                this.redisExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Close Jedis pool
        if (this.jedisPool != null) {
            this.jedisPool.close();
        }
```

## 8.14 — Addon : arrêt réellement fiable du thread abonné

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/listener/RedisSubscriberRunnable.java`  
**Risque** : LOW  
**Constats** : C-086

`jedisPubSub` (l.32) n'est pas `volatile` : le thread qui appelle `shutdown()` peut lire `null` alors que l'abonnement est actif, ou l'inverse. Surtout, le correctif de l'audit (`subscriberThread.interrupt()`) est INSUFFISANT et il faut le dire : `Thread.interrupt()` NE DÉBLOQUE PAS un thread bloqué dans `jedis.subscribe(...)`, parce que Jedis force un timeout socket infini pendant l'abonnement et qu'une lecture sur `java.net.Socket` n'est pas interruptible. Seule la fermeture de la socket libère le thread — d'où la référence volatile sur le Jedis emprunté. L'interrupt reste utile pour le cas backoff, mais le découpage du sleep en tranches courtes le rend accessoire : on ne veut plus attendre 60 s au /stop. `connection.disconnect()` sur un Jedis emprunté le rend inutilisable : il sera détruit à la restitution puisque le pool teste la validité — acceptable à l'arrêt, à ne JAMAIS appeler ailleurs qu'au onDisable.

```java
// --- champs : remplacer l.32 ---
    private volatile JedisPubSub jedisPubSub;
    private volatile Jedis subscriberConnection;

// --- shutdown() : remplacer l.46-54 ---
    public void shutdown() {
        running.set(false);

        var pubSub = this.jedisPubSub;
        if (pubSub != null) {
            try {
                pubSub.unsubscribe();
            } catch (Exception e) {
                plugin.getLogger().warning("Error during unsubscribe: " + e.getMessage());
            }
        }

        // Thread.interrupt() NE DEBLOQUE PAS jedis.subscribe(...) : Jedis force un timeout
        // socket infini pendant l'abonnement et une lecture sur java.net.Socket n'est pas
        // interruptible. Seule la fermeture de la socket libere le thread. C'est aussi ce qui
        // couvre la fenetre ou jedisPubSub vient d'etre remis a null (C-086).
        var connection = this.subscriberConnection;
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Error while closing the subscriber connection: " + e.getMessage());
            }
        }
    }

// --- run() : memoriser la connexion et decouper le backoff ---
            try (Jedis jedis = this.plugin.getJedisPool().getResource()) {
                this.subscriberConnection = jedis;
                backoffMs = 1000; // Reset backoff on successful connection

                // ... construction du JedisPubSub et subscribe inchanges ...

            } catch (Exception exception) {
                if (!running.get()) {
                    break;
                }

                plugin.getLogger().warning("Redis subscription failed: " + exception.getMessage() + ". Reconnecting in " + backoffMs + "ms...");

                // Sleep decoupe : un backoff de 60 s bloquait l'arret du serveur d'autant.
                long slept = 0L;
                try {
                    while (slept < backoffMs && running.get()) {
                        long chunk = Math.min(250L, backoffMs - slept);
                        Thread.sleep(chunk);
                        slept += chunk;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (!running.get()) break;

                backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
            } finally {
                this.subscriberConnection = null;
                this.jedisPubSub = null;
            }

// --- ZAuctionHouseRedis.onDisable : filet apres le join (l.121-127) ---
        if (this.subscriberThread != null && this.subscriberThread.isAlive()) {
            try {
                this.subscriberThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Filet pour le seul cas que l'interrupt sait reellement debloquer (Thread.sleep
            // du backoff) ; le blocage dans subscribe() est traite par le disconnect ci-dessus.
            if (this.subscriberThread.isAlive()) {
                this.subscriberThread.interrupt();
            }
        }
```

## 8.15 — Commandes admin : sortir le travail bloquant du thread principal

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/command/commands/admin/CommandAuctionAdminGenerate.java`  
**Risque** : MEDIUM  
**Constats** : C-081, C-087

(a) C-081 : la pré-génération (l.117-145) tourne SUR LE THREAD PRINCIPAL et fait un `playerRepository.selectByName(...)` par pseudo distinct — l'espace de noms de `generateRandomName` (50 x 50 x 4 formats) plafonne à ~57 500, donc ~27 000 SELECT bloquants pour `amount=100000`, et la table `players` n'a aucun index sur `name`. Le correctif déplace tout le bloc dans le `runAsync` déjà présent l.159 et remplace les N SELECT par UNE lecture de la table. Je REFUSE la variante `whereIn` : Sarah construit ses IN par placeholders et SQLite plafonne à 999 ou 32 766 variables, ce qui casserait à ~27 000 noms. J'écarte aussi l'index de la fiche (voir dbMigrations). (b) C-087 : `provider.migrate` est posté sur `asyncExecutor`, un pool de 4 threads PARTAGÉ avec tout le gameplay ; plusieurs migrations parallèles le saturent et gèlent achats et ventes. Garde de ré-entrance statique (partagée par les quatre providers — ils écrivent dans les mêmes tables) + exécuteur mono-thread daemon propre à la migration. Le `whenComplete` doit impérativement être attaché AVANT le `thenAccept` existant, et l'appel entouré d'un try/catch, sinon un échec synchrone de `provider.migrate` laisserait le drapeau coincé à `true` jusqu'au prochain redémarrage.

```java
// --- CommandAuctionAdminGenerate.generateAuctionItems : remplacer l.113-145 ---
    private void generateAuctionItems(int amount, List<Material> validMaterials) {
        long startTime = System.currentTimeMillis();
        CommandSender commandSender = this.sender;

        var storageManager = plugin.getStorageManager();
        var playerRepository = storageManager.with(PlayerRepository.class);
        AuctionEconomy defaultEconomy = plugin.getEconomyManager().getDefaultEconomy(ItemType.AUCTION);
        if (defaultEconomy == null) {
            plugin.getLogger().severe("No default economy configured for AUCTION items, cannot generate items.");
            return;
        }

        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger lastReported = new AtomicInteger(0);
        int totalAmount = amount;

        plugin.getScheduler().runAsync(task -> {
            try {
                // La pre-generation etait faite SUR LE THREAD PRINCIPAL et faisait un
                // selectByName par pseudo distinct : jusqu'a ~27 000 SELECT bloquants sur une
                // colonne `name` non indexee (C-081). Une seule lecture de la table remplace
                // le tout. Volontairement PAS de whereIn : Sarah construit ses IN par
                // placeholders et SQLite plafonne a 999 / 32 766 variables.
                Map<String, UUID> knownPlayers = new HashMap<>();
                for (var dto : playerRepository.select()) {
                    knownPlayers.putIfAbsent(dto.name(), dto.unique_id());
                }

                List<GenerationData> dataList = new ArrayList<>(totalAmount);
                Map<String, UUID> nameToUuidCache = new HashMap<>();

                for (int i = 0; i < totalAmount; i++) {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    String sellerName = generateRandomName(random);

                    UUID sellerUUID = nameToUuidCache.get(sellerName);
                    if (sellerUUID == null) {
                        // Semantique STRICTEMENT identique a l'ancien code (reutilisation de
                        // l'UUID existant). Ne PAS reutiliser l'UUID d'un vrai joueur est
                        // C-085, hors de ce chantier.
                        sellerUUID = knownPlayers.getOrDefault(sellerName, UUID.randomUUID());
                        nameToUuidCache.put(sellerName, sellerUUID);
                    }

                    Material material = validMaterials.get(random.nextInt(validMaterials.size()));
                    int itemAmount = material.getMaxStackSize() == 1 ? 1 : random.nextInt(material.getMaxStackSize()) + 1;
                    BigDecimal price = BigDecimal.valueOf(random.nextInt(99990) + 10);
                    long expiredAt = System.currentTimeMillis() + (24L * 60L * 60L * 1000L);

                    dataList.add(new GenerationData(sellerUUID, sellerName, material, itemAmount, price, expiredAt));
                }

                var itemRepository = storageManager.with(ItemRepository.class);
                var auctionItemRepository = storageManager.with(AuctionItemRepository.class);

                // ... suite inchangee a partir de « First, count unique players » ...


// --- CommandAuctionAdminMigrate : champ + garde ---
    /**
     * Garde de re-entrance, volontairement STATIQUE : les quatre providers de migration (V3,
     * CrazyAuctions, DonutAuction, ZelAuction) ecrivent tous dans les memes tables. Elle est
     * LOCALE a la JVM : elle ne protege pas du lancement simultane sur deux noeuds — seule la
     * sentinelle en base de C-034 le fait. Ne pas la presenter comme telle dans le changelog.
     */
    private static final AtomicBoolean MIGRATION_RUNNING = new AtomicBoolean(false);

// --- remplacer le bloc l.84-102 ---
        if (!MIGRATION_RUNNING.compareAndSet(false, true)) {
            message(plugin, sender, Message.MIGRATION_FAILED, "%error%", "A migration is already running.");
            return CommandType.SUCCESS;
        }

        // Executor mono-thread dedie : la migration monopolisait l'asyncExecutor du plugin,
        // partage avec tout le gameplay (achats, ventes, retraits) — C-087. Daemon pour ne
        // jamais retenir l'arret du serveur.
        ExecutorService migrationExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "zAuctionHouse-Migration");
            thread.setDaemon(true);
            return thread;
        });

        CompletableFuture<MigrationResult> migrationFuture;
        try {
            migrationFuture = CompletableFuture
                    .supplyAsync(() -> provider.migrate(plugin, migrationSection, callback), migrationExecutor)
                    .thenCompose(future -> future);
        } catch (RuntimeException exception) {
            // Un echec SYNCHRONE de provider.migrate laisserait sinon le drapeau coince a true
            // jusqu'au prochain redemarrage.
            MIGRATION_RUNNING.set(false);
            migrationExecutor.shutdown();
            throw exception;
        }

        // whenComplete attache AVANT le thenAccept existant : il doit s'executer quel que soit
        // le sort de la chaine avale.
        migrationFuture = migrationFuture.whenComplete((result, throwable) -> {
            MIGRATION_RUNNING.set(false);
            migrationExecutor.shutdown();
        });

        migrationFuture.thenAccept(result -> {
            // ... corps inchange ...
        }).exceptionally(throwable -> {
            // ... corps inchange ...
        });
```

### Ruptures d'API et stratégie de compatibilité

- `AuctionPlugin.isShuttingDown()` — NOUVELLE méthode `default` retournant `false`. Source- ET binaire-compatible : toute implémentation tierce d'AuctionPlugin continue de compiler et de tourner sans recompilation. Précédent identique dans le même dépôt : `AuctionClusterBridge.isDistributed()` est déjà un `default` (api/.../cluster/AuctionClusterBridge.java:104). L'addon Redis, compilé contre le SHA figé deb8f16, n'appelle pas cette méthode et n'a PAS besoin d'un bump de pin.
- `Message.SERVER_SHUTTING_DOWN` — ajout d'une constante à un enum publié. Purement additif : vérifié par grep, aucun `switch` exhaustif sur `Message` n'existe dans les deux dépôts, donc aucune rupture binaire. Le texte anglais par défaut est porté par le constructeur de l'enum : un messages.yml non mis à jour dégrade en anglais, il ne plante pas.
- AUCUN changement de signature. `StorageManager.updateItem`, `createTransaction`, `markPurchaseLogAsRead`, `selectItem`, `findUniqueId`, `AuctionManager.getCache/clearPlayerCache/clearPlayersCache/removeCache`, `AuctionEconomy.deposit/withdraw` conservent EXACTEMENT leur signature actuelle. En particulier `markPurchaseLogAsRead` reste `void` (seul appelant externe : REDIS/ItemBoughtListener.java:77) et `getCache(Player)` garde le paramètre `Player` alors que l'indexation interne passe à l'UUID.
- `ZStorageManager.async(Runnable)` passe de `void` à `CompletableFuture<Void>` — classe d'IMPLÉMENTATION (`src/`, pas `api/`), méthode `protected`, aucun implémenteur externe connu de StorageManager. Non publié, donc non cassant.
- Le constructeur de `ZAuctionEconomy` gagne un paramètre `Currencies` — classe d'implémentation hors `api/`, instanciée en un seul point (`ZEconomyManager.java:365`). L'interface publiée `AuctionEconomy` est inchangée.

### Migrations de schéma

- AUCUNE — décision délibérée. La fiche C-081 réclame un index sur %prefix%players(name) ; je le REFUSE dans ce chantier pour deux raisons. (1) Sarah `CreateIndexRequest.java:26-36` émet `CREATE INDEX` SANS `IF NOT EXISTS` et `MigrationManager.execute` relance l'exception jusqu'à onEnable : un serveur où l'index existe déjà (création manuelle, restauration de dump) ne démarrerait plus. (2) L'étape 11 supprime de toute façon les N `selectByName` au profit d'une lecture unique de la table : l'index ne sert plus le chemin corrigé. L'index appartient à la migration `CreateIndexesMigration` de C-066 (chantier PERF), qui doit d'abord corriger Sarah. Conséquence heureuse : ce chantier est intégralement réversible par simple downgrade de jar.

### Changements de configuration

- messages.yml : NOUVELLE clé `server-shutting-down` (valeur EN : `"<error>The server is shutting down, please try again later."`). ATTENTION — le brief et le CLAUDE.md annoncent 4 jeux de langue ; `ls src/main/resources` en montre SIX à HEAD : racine (anglais) + fr/ + es/ + it/ + id/ (indonésien) + th/ (thaï). La clé doit donc être répliquée dans SIX fichiers, pas quatre. Un fichier oublié n'entraîne aucun crash (le défaut anglais est porté par la constante d'enum) mais une incohérence visible.
- AUCUNE nouvelle clé dans config.yml (plugin). Le dimensionnement de `asyncExecutor` est une constante dérivée de `availableProcessors()` et non une clé : l'exposer imposerait soit une composante nouvelle au record publié `PerformanceConfiguration` (rupture binaire pour l'addon), soit une clé à répliquer six fois pour un réglage que personne ne saura calibrer. Voir openQuestions.
- Addon Redis, `src/main/resources/config.yml` : AUCUNE clé nouvelle, mais deux commentaires à mettre à jour. (a) `redis-config.timeout` borne désormais AUSSI l'attente d'une connexion du pool (`setMaxWait`) : un pool sous-dimensionné produira des `JedisExhaustedPoolException` là où l'on attendait silencieusement pour toujours. (b) `redis-config.pool.max-total` dimensionne désormais aussi l'exécuteur du bridge (à la MOITIÉ de sa valeur). L'addon n'a qu'un seul config.yml non traduit : aucun coût multilingue.
- DÉRIVE DE DÉFAUT À SIGNALER dans le changelog : `redis-config.pool.max-total` vaut 64 dans le config.yml livré mais 10 dans le défaut de code (`RedisConfig.java:65`). Un serveur dont le config.yml n'a pas été régénéré après mise à jour tourne à 10 connexions, dont une monopolisée à vie par le thread abonné. Après l'étape 9 cela donne un exécuteur de 5 threads : correct, mais à documenter.

### Validation

- THREADS — assertion runtime temporaire (à retirer avant merge) : ajouter `if (!plugin.getScheduler().isOwnedByCurrentRegion(player)) throw new IllegalStateException("off-thread");` en tête de `ZInventoriesLoader.openInventory`, `ListedItemsButton.updateInventory` et `ZAuctionEconomy.mutateBalance`. Lancer un achat complet et un retrait complet AVEC l'addon Redis actif : aucune exception ne doit apparaître. Avant les étapes 10/11, cette assertion se déclenche sur les trois `updateInventory` de PurchaseService et sur les deux `thenRun` de ListedItemsButton — c'est la preuve que le correctif est nécessaire, à capturer avant/après.
- POOLS — `jcmd <pid> Thread.print | grep -c "ForkJoinPool.commonPool"` pendant 200 achats en 60 s sur un cluster de 2 nœuds. Avant : des workers commonPool bloqués dans `SocketInputStream.read` (Jedis) et dans le driver JDBC. Après : plus aucun worker commonPool dans le plugin ; les threads bloquants portent les noms `zAuctionHouse-Storage-*` et `zAuctionHouse-Redis-Bridge-*`, et leur nombre est plafonné à `max(4, cores)` et `max-total/2`.
- INTERBLOCAGE DU POOL JEDIS (régression de l'étape 13 si l'étape 12 est oubliée) : régler `redis-config.pool.max-total: 4`, lancer 20 achats simultanés. Avec l'étape 12 seule ou les deux : les achats aboutissent ou échouent proprement en `JedisExhaustedPoolException` sous 2 s. Sans l'étape 12 : `jcmd Thread.print` montre les threads du bridge tous bloqués dans `GenericObjectPool.borrowObject` et le nœud ne se rétablit JAMAIS. Ce test est le garde-fou de l'ordre 12 → 13.
- ARRÊT (C-022) — script : lancer un achat sur un item dont l'économie est un provider lent (ajouter un `Thread.sleep(3000)` dans un provider de test), et exécuter `/stop` 500 ms après le clic de confirmation. Invariant SQL à vérifier au redémarrage : `SELECT storage_type, buyer_unique_id FROM zauctionhousev4_items WHERE id = <id>;` doit rendre `DELETED`/`PURCHASED` avec un acheteur, jamais `LISTED` avec un solde acheteur débité. Avant le correctif : ligne restée `LISTED`, argent parti, aucun item remis.
- DETTE PENDING (C-027) — acheter un item d'un vendeur HORS LIGNE (économie `must-be-online: true` ou `auto-claim: false`), puis `/stop` dans la seconde qui suit. `SELECT COUNT(*) FROM zauctionhousev4_transactions WHERE status='PENDING' AND player_unique_id='<vendeur>';` doit rendre 1. Avant : 0 dans la majorité des essais (l'écriture partait sur le scheduler FoliaLib, jamais drainé).
- CACHE JOUEUR (C-105/C-107) — 1) fuite : `jcmd <pid> GC.class_histogram | grep CraftPlayer` après 50 connexions/déconnexions et un `/ah` par joueur ; le compte doit retomber au nombre de joueurs en ligne. 2) Valeurs nulles : cliquer le filtre `LogTypeFilterButton` jusqu'à l'état « ALL » — aucune `NullPointerException` ne doit être levée (c'est ce que le correctif ConcurrentHashMap de l'audit aurait cassé). 3) Ré-entrance : ouvrir l'onglet « expirés » d'un joueur ayant >100 annonces expirées ; le serveur ne doit pas geler (le `computeIfAbsent` de l'audit y respinait à l'infini).
- GUI DÉJÀ RENDUE (C-048/C-049) — deux serveurs A et B, `update-inventory-on-action: false`. Joueur 1 sur A ouvre la confirmation d'achat de l'item X. Joueur 2 sur B, dont la GUI a été rendue AVANT, clique X. Attendu après correctif : refus immédiat, GUI rafraîchie, aucun second inventaire de confirmation ouvert. Avant : `REMOVE_CONFIRM`/`PURCHASE_CONFIRM` s'ouvre et seul le bail Redis de 30 s arbitre.
- GUI SANS REDIS (C-048/C-049) — arrêter Redis, cliquer un item. Attendu : l'inventaire de confirmation s'ouvre malgré tout et un WARNING « Failed to broadcast » apparaît en console. Avant : selon le chemin, aucun retour visuel.
- SUBSCRIBER (C-086) — provoquer un backoff (arrêter Redis), attendre que la console affiche « Reconnecting in 32000ms », puis `/stop`. Le serveur doit s'arrêter en moins de 6 s ; le log « Redis subscriber stopped. » doit apparaître. Avant : jusqu'à 60 s de blocage, ou un thread abonné qui ne se termine jamais quand il est bloqué dans `subscribe()`.
- COMMANDES ADMIN — `/ah admin generate 20000 confirm` : mesurer les MSPT pendant l'exécution (`/tps` ou Spark). Aucun tick au-dessus de 100 ms ne doit être imputable à la commande hors du `getValidMaterials()` initial. Avant : gel de plusieurs dizaines de secondes. Puis lancer deux `/ah admin migrate v3 confirm` à 1 s d'intervalle : le second doit être refusé par le message MIGRATION_FAILED.
- NON-RÉGRESSION MONO-SERVEUR — sur une installation SQLite sans addon Redis : vendre, acheter, retirer, réclamer, `/ah admin add`, `/ah admin generate 100 confirm`. Comparer les compteurs `SELECT storage_type, COUNT(*) FROM zauctionhousev4_items GROUP BY storage_type;` avant/après avec ceux obtenus sur le jar de référence.

### Questions ouvertes pour le mainteneur

- COMPTEUR D'OPÉRATIONS EN VOL — l'audit demande « arrêt de l'executor seulement après compteur d'opérations en vol à zéro ». Je ne l'ai PAS implémenté et c'est un arbitrage à valider : attendre ce compteur bloque le thread principal, or les étapes 6, 7, 10 et 11 introduisent des hops `runAtEntity` que seule l'exécution du thread principal peut satisfaire — l'attente ne se terminerait jamais et brûlerait systématiquement son budget. Le drapeau `isShuttingDown()` (refus des nouvelles opérations) plus le handler de rejet non discardant couvrent le cas réel. Si vous voulez tout de même le compteur, il faudra un ordonnanceur d'arrêt qui continue de faire tourner les tâches de tick, ce qui n'existe pas dans Bukkit.
- TAILLE DU POOL DE STOCKAGE — je passe de 4 threads fixes à `max(4, availableProcessors())` en constante, sans clé de configuration : l'exposer imposerait soit une composante nouvelle au record publié `PerformanceConfiguration` (rupture binaire pour l'addon), soit une clé à répliquer dans six jeux de langue pour un réglage que peu d'exploitants sauront calibrer. À arbitrer si vous préférez la clé malgré le coût.
- VERROU D'ÉCONOMIE ET PLUGINS TIERS — le verrou stripé de l'étape 7 ne protège que les mutations passant par ZAuctionEconomy. Un plugin qui touche le même compte directement via Vault n'est pas sérialisé : la fenêtre est réduite, pas fermée. La fermer réellement exige de faire remonter l'`EconomyResponse` dans un TROISIÈME dépôt, `D:/Users/Maxlego08/workspace2.0/CurrenciesAPI` (`VaultProvider.withdraw` → `return withdrawPlayer(...).transactionSuccess()`). Acceptez-vous d'ouvrir ce dépôt, ou reste-t-on sur la mitigation ?
- BLOCAGE 5 s SUR LE THREAD PROPRIÉTAIRE — pour les économies LEVEL/EXPERIENCE/ITEM/ZMENUITEMS, un worker attend jusqu'à 5 s que le thread du joueur exécute la mutation. Si le serveur lague fortement, une vente ou un achat peut donc échouer en SEVERE là où il « passait » avant (illégalement). C'est un changement de comportement visible sur ces quatre types d'économie uniquement — à annoncer dans le changelog, ou à rendre configurable.
- RECONNEXION DANS LE MÊME TICK — l'indexation par UUID (étape 6) fait SURVIVRE le cache joueur à une reconnexion immédiate : un joueur retrouve son ITEM_SHOW / CURRENT_CATEGORY / SEARCH_QUERY d'avant déconnexion. C'est correct puisque `onQuit` purge, sauf si l'ordre onQuit/reconnexion s'inverse (BungeeCord, changement de serveur rapide). À tester explicitement avant mise en production sur un réseau proxifié.
- MESSAGE D'ARRÊT — j'ajoute une constante d'enum et une clé dans six messages.yml. Si vous refusez toute nouvelle clé dans ce chantier, l'alternative est de refuser en silence avec un simple log console, au prix d'un clic sans effet pour le joueur pendant le /stop.
- CONSTATS ADJACENTS NON TRAITÉS, à assigner : (a) C-082 — `postSell` mute l'index `IntArrayList` non thread-safe depuis l'executor ; le catalogue le classe en chantier 8 mais il n'était pas dans mon périmètre, et son correctif (`runNextTick`) déplace `applyCategories` et `Base64ItemStack.encode` sur le thread principal, ce qui est un vrai coût TPS à mesurer. (b) `CommandAuctionAdminGenerate:212` fait `auctionManager.addItem(LISTED, …)` depuis le thread async, ce qui mute `idsListedByOwner` (ConcurrentHashMap dont les VALEURS sont des IntArrayList non thread-safe) pendant que le thread principal itère la même liste. (c) `CommandAuctionAdminGenerate` retient des références `CommandSender` (donc `Player`) dans deux HashMap jamais purgées — même famille que C-107, fiche C-115. (d) `PlayerPlaceholders:21-23` déclenche `processExpiredItems` — donc des écritures DB et des `clearPlayersCache` — depuis une simple résolution de placeholder PlaceholderAPI.

### Retour arrière

"Retour en arrière PAR SIMPLE DOWNGRADE DE JAR, sans aucune action base — c'est la raison pour laquelle j'ai délibérément exclu la migration d'index de C-081 de ce chantier : aucune des 15 étapes ne touche au schéma, ne modifie une valeur persistée, ni ne change le format d'un message Redis. Les deux dépôts sont indépendants au rollback : on peut redescendre le plugin en gardant l'addon corrigé, ou l'inverse (le paramètre `Executor` du constructeur de RedisAuctionClusterBridge est interne à l'addon ; la méthode `default isShuttingDown()` reste absente et donc inerte pour un addon ancien). Trois précisions. (1) La clé `server-shutting-down` reste dans les messages.yml après downgrade : clé inconnue et ignorée par l'ancien jar, aucun effet. (2) Le `setMaxWait` de l'addon disparaît au downgrade et l'attente redevient infinie : si le rollback est motivé par des `JedisExhaustedPoolException`, la bonne réponse est d'AUGMENTER `redis-config.pool.max-total` avant d'envisager le downgrade — l'exception est le symptôme d'un pool sous-dimensionné, pas du correctif. (3) Rollback partiel dangereux : ne JAMAIS redescendre l'étape 12 en gardant l'étape 13, la combinaison « exécuteur dédié + emprunts imbriqués » interbloque définitivement le bridge. Si l'étape 13 pose problème, revenir aux deux étapes ensemble. Mitigation immédiate sans downgrade, si un comportement de GUI régresse après l'étape 11 : `action.update-inventory-on-action: false` dans config.yml (présent dans les six locales) coupe le chemin de rafraîchissement des spectateurs."


---

# Chantier QUICK — Corrections rapides (&lt; 10 lignes) et gains de performance gratuits — zAuctionHouseV4 + addon Redis

Ce chantier regroupe les correctifs qui tiennent en quelques lignes, qui ne changent aucune signature publiée de façon cassante, et qui n'introduisent aucune nouvelle dépendance entre modules — plus les gains de performance qui sont de purs retraits de travail inutile. Il est indivisible parce que ces vingt-trois pas partagent une propriété unique : chacun se relit en moins de cinq minutes et se retire par un simple downgrade de jar (à l'exception des quatre migrations d'index, dont le rollback SQL est fourni). Il est aussi le socle de validation des autres chantiers : tant que `ZAuctionManager:956` fait `return` au lieu de `continue`, tout test multi-joueurs des chantiers 2, 5 et 7 donne de faux négatifs, et tant que la récursion mutuelle `Base64ItemStack.decode` ↔ `ItemStackUtils.safeDeserializeItemStack` n'est pas cassée, un `StackOverflowError` au démarrage masque toute autre régression de chargement. Le volet performance retire trois travaux O(n) refaits à chaque rendu d'inventaire (recherche, compteur global, compteurs de catégorie), le O(n×m) du chargement, la matérialisation en heap de millions de lignes de logs, et une seconde reconstruction concurrente du cache trié causée par deux gardes indépendantes. Deux pas seulement portent une dépendance externe explicite : le pas 14 (contrat `LocalAuctionClusterBridge`) est bloqué sur C-042 du chantier adminRemoveItem, et le pas 11 (CAS de statut dans l'addon) doit sortir dans la même release que C-072 du chantier 7.

**Prérequis**

- C-042 (chantier adminRemoveItem / ch.2-5) DOIT être livré AVANT le pas 14. Passer `LocalAuctionClusterBridge.lockItem` de `failedFuture` à `LockToken.noop()` active la branche noop de `PurchaseService:100` et `RemoveService:232` (déjà écrite, sans risque) mais rend `ZAuctionManager:689` (adminRemoveItem) PERMISSIF en mono-serveur : le `.thenCompose(lockToken -> clusterBridge.removeItem(...))` ne teste pas le jeton noop et poursuivrait la suppression sans verrou. Aujourd'hui le failedFuture le bloque. Le pas 14 est donc explicitement gaté.
- C-072 (TTL de réarmement des statuts *_CONFIRM, chantier 7) doit sortir dans la MÊME release que le pas 11. Le CAS de C-004 transforme une perte de message pub/sub en désynchronisation permanente là où l'application aveugle finissait par se resynchroniser par hasard. Sans C-072, on échange un bug bruyant contre un item figé.
- C-035 (chantier 7, PurchaseService.exceptionally) est nécessaire pour la MOITIÉ manquante du pas 5. Le pas 5 ferme la fenêtre de LECTURE (refuser l'achat d'une ligne qui n'est plus LISTED) mais laisse le `.exceptionally` de PurchaseService:176 restaurer AVAILABLE et le rediffuser au cluster — comportement déjà présent aujourd'hui sur la branche `dbItem == null`, que ce chantier rend simplement plus fréquent. La réconciliation mémoire (removeItem + purge de caches) et le drapeau `itemGoneHolder` appartiennent à C-035.
- C-103 (invalidation du cache trié sur changement de statut, chantier 7) est le complément de correction des pas 21 à 23. Sans lui, un item bloqué en IS_*_CONFIRM reste compté par les compteurs servis depuis SortedItemsCache — mais il reste aussi AFFICHÉ dans la liste servie par le même cache : compteur et GUI convergent, ce qui est l'intention explicite du commentaire de GlobalPlaceholders:16-17. Le résiduel est donc une cohérence, pas une divergence.
- C-089 / C-081 (chantier 8) : passer `getExecutorService()` aux deux `supplyAsync` orphelins de `ZStorageManager` (`selectItem` l.177, `findUniqueId` l.215). Aucun pas de ce chantier n'en dépend pour compiler, mais le pas 5 ajoute une condition sur une chaîne qui tourne aujourd'hui sur ForkJoinPool.commonPool.
- C-081 (chantier 8) NE DOIT PAS recréer l'index `players(name)` : il est livré par le pas 17 de ce chantier. Si les deux chantiers créent la même migration, la seconde échoue sur `CREATE INDEX` sans `IF NOT EXISTS` et empêche le démarrage.

**Constats couverts** : C-045, C-100, C-013, C-006, C-114, C-054, C-031, C-117, C-110, C-050, C-055, C-056, C-066, C-073, C-074, C-090, C-098, C-101, C-104, C-097, C-062, C-085, C-115, C-116, C-004

*~520 LOC · 4 fichiers nouveaux*

## QUICK.1 — C-045 — `return` → `continue` dans la boucle de rafraîchissement des inventaires

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionManager.java`  
**Risque** : LOW  
**Constats** : C-045

Le `return` sort du Runnable passé à `thenRun`, donc de TOUTE la boucle `for` : dès qu'un joueur ignoré est rencontré, tous les joueurs suivants dans l'itération de `getOnlinePlayers()` ne sont ni rafraîchis ni purgés de leur cache ITEMS_LISTED. C'est le pas n°1 du chantier parce qu'il conditionne toute validation manuelle multi-joueurs des chantiers 2, 5, 7 et 8 : sans lui, un test à deux joueurs donne des faux négatifs. Effet de bord à annoncer dans le changelog : on passe de « une fraction des joueurs rafraîchis » à « tous », donc N hops runAtEntity par vente ou retrait. Le garde-fou existe déjà : `action.update-inventory-on-action: false` (config.yml:774, présent dans les six locales).

```java
// ZAuctionManager.java, methode updateListedItems, dans le thenRun (ligne 956)

        this.sortedItemsCache.ensureCacheValidAsync().thenRun(() -> {
            for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {

                // `return` sortait du lambda thenRun, donc de TOUTE la boucle : tous les
                // joueurs situes apres le joueur ignore gardaient un GUI perime.
                if (onlinePlayer == ignoredPlayer) continue;

                this.plugin.getScheduler().runAtEntity(onlinePlayer, w -> {
                    // ... inchange ...
                });
            }
        });
```

## QUICK.2 — C-117 — publication sûre des références mutées à chaud (`volatile`)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionPlugin.java`  
**Risque** : LOW  
**Constats** : C-117

`auctionClusterBridge` est écrit par le `onEnable` de l'addon Redis et lu par les trois services depuis des threads asynchrones, sans aucune barrière mémoire. `offlinePermission` (l.101) souffre du même défaut EN PIRE, et l'audit l'a manqué : son setter est PUBLIÉ dans l'API (`AuctionPlugin.java:137`), donc appelable par n'importe quel plugin tiers depuis n'importe quel thread, et il est lu par `ExpireService` sur des chemins asynchrones (l.92, :202, :341). Le log ajouté dans le setter rend enfin VÉRIFIABLE, dans la console de chaque nœud, le passage effectif en mode distribué — aujourd'hui aucune trace ne le dit. Ne PAS passer le champ en `final` : le setter fait partie de l'API publiée et l'addon en dépend.

```java
// ZAuctionPlugin.java, champs l.100-101

    private volatile AuctionClusterBridge auctionClusterBridge = new LocalAuctionClusterBridge();
    private volatile OfflinePermission offlinePermission = new EmptyOfflinePermission();

// ZAuctionPlugin.java, setter l.419-422

    @Override
    public void setAuctionClusterBridge(AuctionClusterBridge auctionClusterBridge) {
        var previous = this.auctionClusterBridge;
        this.auctionClusterBridge = auctionClusterBridge;
        // Rend le mode reellement en vigueur lisible dans la console de chaque noeud.
        // isDistributed() est une constante cote Redis (RedisAuctionClusterBridge:340)
        // et ne touche pas au pool Jedis : aucun risque a l'appeler ici.
        getLogger().info("Cluster bridge: " + previous.getClass().getSimpleName()
                + " -> " + auctionClusterBridge.getClass().getSimpleName()
                + " (distributed=" + auctionClusterBridge.isDistributed() + ")");
    }
```

## QUICK.3 — C-054 — casser la récursion mutuelle Base64ItemStack ↔ ItemStackUtils et la NPE de sérialisation

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/utils/ItemStackUtils.java`  
**Risque** : LOW  
**Constats** : C-054

Sur un serveur < 1.20.5 (`isAttributItemStack()` faux, bande 1.20.0-1.20.4 plus tout nœud legacy d'un cluster hétérogène), `Base64ItemStack.decode` délègue à `safeDeserializeItemStack`, dont le `catch` rappelle `Base64ItemStack.decode` : récursion infinie, StackOverflowError non rattrapable par `catch(Exception)`. Le chemin réellement atteint est le démarrage : `AuctionLoader.loadItems()` est 100 % synchrone et appelé nu depuis `ZAuctionPlugin.onEnable():160`. Je REJETTE le diff de la section 4 de l'audit : il patche `serializeItemStack`, ne touche pas la récursion, et référence un symbole `logger` inexistant dans cette classe statique — il ne compile pas. J'ajoute deux défauts que l'audit a manqués : (1) `Base64.getDecoder().decode` lève `IllegalArgumentException` et `readObject` lève `ClassCastException`, aucune des deux n'étant rattrapée aujourd'hui ; (2) `localByteArrayOutputStream` est déréférencé l.37 APRÈS un catch qui avale l'échec — NPE garantie dès que la réflexion échoue (tout Paper ≥ 1.20.6 via `getClassz()`/`split(",")[3]`).

```java
// ===== API/.../api/utils/ItemStackUtils.java =====

    public static String serializeItemStack(ItemStack paramItemStack) {

        if (paramItemStack == null) return "null";

        ByteArrayOutputStream localByteArrayOutputStream;
        try {
            Class<?> localClass = EnumReflectionItemStack.NBTTAGCOMPOUND.getClassz();
            Constructor<?> localConstructor = localClass.getConstructor();
            Object localObject1 = localConstructor.newInstance();
            Object localObject2 = EnumReflectionItemStack.CRAFTITEMSTACK.getClassz().getMethod("asNMSCopy", new Class[]{ItemStack.class}).invoke(null, paramItemStack);

            EnumReflectionItemStack.ITEMSTACK.getClassz().getMethod("b", new Class[]{localClass}).invoke(localObject2, localObject1);

            localByteArrayOutputStream = new ByteArrayOutputStream();
            EnumReflectionItemStack.NBTCOMPRESSEDSTREAMTOOLS.getClassz().getMethod("a", new Class[]{localClass, OutputStream.class}).invoke(null, localObject1, localByteArrayOutputStream);
        } catch (Exception localException) {
            // Le flux restait null apres ce catch et etait dereference juste apres : NPE
            // garantie des que la reflexion echoue (Paper >= 1.20.6 : getClassz() fait
            // nmsPackage.split(",")[3] sur un paquet qui n'a plus que 3 segments).
            Bukkit.getLogger().severe("[zAuctionHouse] serializeItemStack failed: " + localException.getMessage());
            return null;
        }
        return Base64.encode(localByteArrayOutputStream.toByteArray());
    }

    public static ItemStack safeDeserializeItemStack(String paramString) {
        try {
            return tryDeserializeItemStack(paramString);
        } catch (Throwable throwable) {
            // NE JAMAIS rappeler Base64ItemStack.decode ici : sur un serveur < 1.20.5,
            // decode() redescend dans cette methode -> recursion mutuelle infinie ->
            // StackOverflowError, non rattrapable par un catch(Exception) et fatal au
            // chargement (AuctionLoader.loadItems est synchrone depuis onEnable:160).
            return Base64ItemStack.decodeBukkitStream(paramString);
        }
    }

// ===== API/.../api/utils/Base64ItemStack.java =====

    public static ItemStack decode(String data) {

        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            return ItemStackUtils.safeDeserializeItemStack(data);
        }

        return decodeBukkitStream(data);
    }

    /**
     * Decode une charge utile ecrite par le flux Bukkit, SANS repasser par le
     * dispatcher versionne {@link #decode(String)} : c'est ce qui casse la
     * recursion mutuelle avec {@link ItemStackUtils#safeDeserializeItemStack}.
     * <p>
     * Portee paquet : ItemStackUtils vit dans le meme paquet, aucune signature
     * publique de l'API publiee ne bouge.
     *
     * @param data la chaine Base64
     * @return l'ItemStack decode, ou {@code null} si la charge est illisible
     */
    static ItemStack decodeBukkitStream(String data) {
        if (data == null || data.isEmpty()) return null;

        Base64.Decoder decoder = Base64.getDecoder();
        try {
            byte[] bytes = decoder.decode(data);
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
            ObjectInputStream objectInputStream = new BukkitObjectInputStream(gzipInputStream);
            ItemStack item = (ItemStack) objectInputStream.readObject();
            objectInputStream.close();
            return item;
        } catch (Exception exception) {
            // Elargi de (IOException | ClassNotFoundException) a Exception :
            // Base64.decode leve IllegalArgumentException sur une charge non-Base64
            // et readObject leve ClassCastException, aucune des deux n'etait rattrapee
            // et toutes deux s'echappaient du .map() de ItemLoaderUtils:40, avortant
            // TOUT le lot de chargement.
            return null;
        }
    }
```

## QUICK.4 — C-100 — redécouper les charges multi-stacks à la LECTURE des logs admin

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/buttons/admin/AdminLogsButton.java`  
**Risque** : LOW  
**Constats** : C-100

L'écriture est cohérente et joint les charges par ';' (ZAuctionManager.logItemAction l.1003, SellService:384, et déjà V3MigrationService:285) ; c'est la LECTURE qui est restée en arrière avec un unique `Base64ItemStack.decode` sur la chaîne complète. Un log d'annonce multi-stacks s'affiche donc vide dans l'audit admin. `"abc".split(";")` rend `["abc"]` : comportement strictement identique sur les logs mono-stack, aucune régression. J'attrape `Throwable` et non `Exception` parce que le décodeur pré-1.20.5 passe par `ItemStackUtils` et peut lever `StackOverflowError` — après le pas 3 c'est de la ceinture-bretelles, mais un serveur qui déploie ce pas sans le pas 3 en a besoin.

```java
// AdminLogsButton.java, methode createAdminLogItem (l.139-154)

    private AdminLogItem createAdminLogItem(LogDTO log) {
        List<ItemStack> itemStacks = new ArrayList<>();

        if (log.itemstack() != null && !log.itemstack().isEmpty()) {
            // Les annonces multi-stacks sont ECRITES en joignant les charges utiles par ';'
            // (ZAuctionManager.logItemAction:1003, SellService:384, V3MigrationService:285).
            // La lecture doit donc redecouper, sinon un log multi-stacks s'affiche vide.
            for (String part : log.itemstack().split(";")) {
                if (part.isBlank()) continue;
                try {
                    ItemStack decoded = Base64ItemStack.decode(part);
                    if (decoded != null) itemStacks.add(decoded);
                } catch (Throwable throwable) {
                    // Throwable et non Exception : le decodeur < 1.20.5 passe par
                    // ItemStackUtils et peut lever StackOverflowError (cf. C-054).
                    // Un stack corrompu ne doit plus emporter les N-1 valides du lot.
                    this.plugin.getLogger().warning("Failed to decode itemstack for log " + log.id() + ": " + throwable.getMessage());
                }
            }
        }

        return new AdminLogItem(log, itemStacks);
    }
```

## QUICK.5 — C-006 / C-114 — exiger que la ligne soit ENCORE listée dans la revalidation sous verrou

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/PurchaseService.java`  
**Risque** : LOW  
**Constats** : C-006, C-114

`ItemRepository.select(int)` ne filtre que DELETED : une ligne déjà passée en EXPIRED — qui est la destination PAR DÉFAUT d'un retrait, `remove-listed-item.give-item: false` à config.yml:788 — remonte ici avec `buyer_unique_id` à null et franchit la garde. L'acheteur paie un item que le vendeur vient de récupérer. Le mapping `storage_type -> ItemStatus` de `ItemLoaderUtils:43-48` étant fidèle (LISTED→AVAILABLE), tester le statut de l'objet relu équivaut exactement à tester `storage_type = 'LISTED'` en base — aucune API nouvelle, aucune horloge partagée (je rejette pour cela le correctif de C-114, qui exigeait un `selectDatabaseTime()` sur l'interface publiée StorageManager et un `SELECT UNIX_TIMESTAMP()` inexistant en SQLite, le backend PAR DÉFAUT). Je m'en tiens VOLONTAIREMENT à la condition : la réconciliation mémoire et le drapeau `itemGoneHolder` appartiennent à C-035 (chantier 7) et ne peuvent pas être livrés ici sans rouvrir le `.exceptionally` l.176.

```java
// PurchaseService.java, l.118-127 (revalidation autoritaire sous verrou)

                            .thenCompose(dbItem -> {
                                // La ligne doit ENCORE etre listee. ItemRepository.select(int)
                                // ne filtre que DELETED : une ligne deja passee en EXPIRED
                                // (destination PAR DEFAUT d'un retrait, config.yml:788) remonte
                                // ici avec buyer_unique_id a null et franchissait la garde.
                                // Le mapping storage_type -> ItemStatus (ItemLoaderUtils:43-48)
                                // etant fidele, tester le statut equivaut a tester storage_type.
                                if (dbItem == null || dbItem.getBuyerUniqueId() != null
                                        || dbItem.getStatus() != ItemStatus.AVAILABLE) {
                                    inventoryManager.updateInventory(player);
                                    resultHolder.set(PurchaseResult.failure("Item already sold", PurchaseFailReason.ITEM_NOT_AVAILABLE));
                                    return failedFuture(new IllegalStateException("Item no longer listed on another server (db="
                                            + (dbItem == null ? "GONE" : dbItem.getStatus()) + ")"));
                                }
                                return auctionEconomy.has(player.getUniqueId(), requiredBalance);
                            });
```

## QUICK.6 — C-013 — ne marquer RETRIEVED que les transactions réellement créditées

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/ClaimService.java`  
**Risque** : LOW  
**Constats** : C-013

Aujourd'hui `repository.updateStatus(transactionIds, RETRIEVED)` marque INCONDITIONNELLEMENT toute la liste — y compris les économies introuvables (`continue` l.59), les dépôts en échec (`continue` l.73) et le cas `!player.isOnline()`, où le bloc `if (player.isOnline())` est sauté mais `totalClaimed` est quand même incrémenté l.78. C'est de la DESTRUCTION d'argent, pure et déterministe, en mono-serveur. Je corrige aussi `totalClaimed`/`lastEconomy`, qui annoncent au joueur un montant et une économie jamais payés. ATTENTION à ne pas présenter ce pas comme corrigeant le double-claim : `updateStatus` reste sans compare-and-set et rien ne réserve les lignes avant le dépôt — c'est C-003 (chantier 1), qui absorbera et remplacera ce patch. Effet de bord voulu : une économie définitivement absente d'economies.yml maintient ses lignes PENDING indéfiniment (avant, elles étaient silencieusement effacées) ; le WARNING l.58 devient l'unique trace.

```java
// ===== ClaimService.claimMoney, l.49-84 =====

            var economyManager = this.plugin.getEconomyManager();
            var depositReason = this.plugin.getConfiguration().getAutoClaimConfiguration().depositReason();

            BigDecimal totalClaimed = BigDecimal.ZERO;
            AuctionEconomy lastEconomy = null;
            // Ne marquer RETRIEVED que ce qui a REELLEMENT ete verse.
            List<Integer> claimedIds = new ArrayList<>();

            for (var entry : byEconomy.entrySet()) {
                String economyName = entry.getKey();
                List<TransactionDTO> economyTransactions = entry.getValue();

                var optionalEconomy = economyManager.getEconomy(economyName);
                if (optionalEconomy.isEmpty()) {
                    // Les lignes restent PENDING : elles seront representees au prochain claim,
                    // au lieu d'etre effacees en silence.
                    this.plugin.getLogger().warning("Economy not found: " + economyName + ", " + economyTransactions.size() + " transaction(s) left PENDING");
                    continue;
                }

                var economy = optionalEconomy.get();

                BigDecimal economyTotal = economyTransactions.stream().map(TransactionDTO::value).filter(v -> v.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add);

                if (economyTotal.compareTo(BigDecimal.ZERO) <= 0) continue;

                // Joueur deconnecte : ne RIEN marquer, sinon l'argent est detruit.
                if (!player.isOnline()) {
                    this.plugin.getLogger().warning("Player " + player.getUniqueId() + " went offline during claim, " + economyTotal + " " + economyName + " left PENDING");
                    continue;
                }

                try {
                    economy.deposit(player.getUniqueId(), economyTotal, depositReason);
                } catch (Exception e) {
                    this.plugin.getLogger().severe("Failed to deposit " + economyTotal + " to " + player.getName() + " for economy " + economyName + ": " + e.getMessage());
                    continue;
                }

                message(this.plugin, player, Message.CLAIM_ECONOMY_SUCCESS, "%amount%", economyManager.format(economy, economyTotal), "%economy%", economy.getDisplayName());

                lastEconomy = economy;
                totalClaimed = totalClaimed.add(economyTotal);
                economyTransactions.forEach(transaction -> claimedIds.add(transaction.id()));
            }

            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            if (!claimedIds.isEmpty()) {
                repository.updateStatus(claimedIds, TransactionStatus.RETRIEVED);
            }

            if (totalClaimed.compareTo(BigDecimal.ZERO) > 0) {
                message(this.plugin, player, Message.CLAIM_SUCCESS, "%amount%", totalClaimed.toString());
                return ClaimResult.success("Money claimed successfully", totalClaimed.doubleValue(), lastEconomy);
            }

            return ClaimResult.nothingToClaim("No positive amount to claim");

// ===== ClaimService.clearPendingTransactions, l.176-211 =====

    @Override
    public CompletableFuture<Void> clearPendingTransactions(UUID playerUniqueId, boolean giveMoney) {
        return getPendingTransactions(playerUniqueId).thenAccept(transactions -> {
            if (transactions.isEmpty()) return;

            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);

            // giveMoney == false : cloture directe, le javadoc de AuctionClaimService:58
            // documente explicitement "transactions are simply discarded without any payment".
            if (!giveMoney) {
                repository.updateStatus(transactions.stream().map(TransactionDTO::id).toList(), TransactionStatus.RETRIEVED);
                return;
            }

            var economyManager = this.plugin.getEconomyManager();
            var depositReason = this.plugin.getConfiguration().getAutoClaimConfiguration().depositReason();

            Map<String, List<TransactionDTO>> byEconomy = transactions.stream().collect(Collectors.groupingBy(TransactionDTO::economy_name));
            List<Integer> claimedIds = new ArrayList<>();

            for (var entry : byEconomy.entrySet()) {
                var optionalEconomy = economyManager.getEconomy(entry.getKey());
                if (optionalEconomy.isEmpty()) {
                    this.plugin.getLogger().warning("Economy not found: " + entry.getKey() + ", transactions left PENDING");
                    continue;
                }

                var economy = optionalEconomy.get();
                BigDecimal economyTotal = entry.getValue().stream()
                        .map(TransactionDTO::value)
                        .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (economyTotal.compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        economy.deposit(playerUniqueId, economyTotal, depositReason);
                    } catch (Exception e) {
                        this.plugin.getLogger().severe("Failed to deposit " + economyTotal + " to " + playerUniqueId + " for economy " + entry.getKey() + ": " + e.getMessage());
                        continue;
                    }
                }
                entry.getValue().forEach(transaction -> claimedIds.add(transaction.id()));
            }

            if (!claimedIds.isEmpty()) {
                repository.updateStatus(claimedIds, TransactionStatus.RETRIEVED);
            }
        });
    }

// Imports a ajouter : java.util.ArrayList (java.util.List est deja importe).
```

## QUICK.7 — C-062 — calculer une vraie date limite de récupération dans `/ah admin add expired|purchased`

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/command/commands/admin/CommandAuctionAdminAdd.java`  
**Risque** : LOW  
**Constats** : C-062

`addExpired` et `addPurchased` passent `System.currentTimeMillis()` comme `expiredAt` : l'item est créé DÉJÀ périmé et détruit par ExpireService à la première ouverture d'inventaire. La commande admin ne fait donc que consommer l'item de la main de l'admin. Les chemins normaux calculent correctement (`ZAuctionManager:527-529` pour EXPIRED, `:888-890` pour PURCHASED) ; on aligne dessus, avec la convention `0 = jamais` déjà en vigueur dans `addListed:99-100`. Ne PAS remplacer par `Long.MAX_VALUE` : `ZItem.isExpired:169` teste `expiredAt.getTime() != 0`, la valeur 0 est le marqueur neutre attendu. `getExpiration(target)` est la variante synchrone par permission, et `target` est garanti en ligne par la garde l.49-53 — pas besoin de la variante offline/CompletableFuture. La garde SQL de `ItemRepository.createUpdateSchema:79-81` (`where storage_type = LISTED` pour une transition vers EXPIRED) reste satisfaite puisque `ItemRepository.create:38` insère toujours en LISTED.

```java
// CommandAuctionAdminAdd.java

    private void addExpired(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        // Le 3e argument est la DATE LIMITE DE RECUPERATION, pas l'instant courant :
        // passer System.currentTimeMillis() creait un item DEJA perime, detruit par
        // ExpireService a la premiere ouverture d'inventaire. Meme convention que
        // ZAuctionManager:527-529 (0 = jamais).
        long expiration = this.plugin.getConfiguration().getExpireExpiration().getExpiration(target);
        long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;

        this.plugin.getStorageManager().createAuctionItem(target, price, expiredAt, List.of(cloned), economy)
                .thenAccept(item -> {
                    item.setStatus(ItemStatus.REMOVED);
                    item.setExpiredAt(new Date(expiredAt));
                    this.auctionManager.addItem(StorageType.EXPIRED, item);
                    this.plugin.getStorageManager().updateItem(item, StorageType.EXPIRED);
                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_SELLING);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, "%items%", item.getItemDisplay(), "%target%", target.getName(), "%type%", "expired");
                });
    }

    private void addPurchased(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        // Meme convention que ZAuctionManager:888-890.
        long expiration = this.plugin.getConfiguration().getPurchaseExpiration().getExpiration(target);
        long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;

        this.plugin.getStorageManager().createAuctionItem(admin, price, expiredAt, List.of(cloned), economy)
                .thenAccept(item -> {
                    item.setBuyer(target);
                    item.setStatus(ItemStatus.PURCHASED);
                    item.setExpiredAt(new Date(expiredAt));
                    this.auctionManager.addItem(StorageType.PURCHASED, item);
                    this.plugin.getStorageManager().updateItem(item, StorageType.PURCHASED);
                    this.auctionManager.clearPlayerCache(target, PlayerCacheKey.ITEMS_PURCHASED);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, "%items%", item.getItemDisplay(), "%target%", target.getName(), "%type%", "purchased");
                });
    }
```

## QUICK.8 — C-085 — ne jamais adopter l'UUID d'un joueur réel pour des annonces de test

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/command/commands/admin/CommandAuctionAdminGenerate.java`  
**Risque** : LOW  
**Constats** : C-085

`generateRandomName` produit ~57 500 pseudos possibles (50 × 50 × 4 formats) ; sur un serveur peuplé, une collision avec un joueur réel est certaine, et le code ADOPTE alors son UUID (`sellerUUID = playerRepository.selectByName(sellerName)`). Les annonces de test apparaissent dans l'onglet « mes ventes » de ce joueur et lui sont réellement récupérables. Je REJETTE le préfixe « TEST_ » suggéré par l'audit : `CreatePlayerMigration:12` déclare `table.string("name", 16)` et `generateRandomName` produit déjà jusqu'à 15 caractères (« Phoenix_Crafter ») — « TEST_ » + nom déborde la colonne, troncature silencieuse en MySQL non strict, erreur en mode strict. Ma variante ne dépend PAS de C-081 (chantier 8) : elle réutilise le `selectByName` déjà présent, sans ajouter de requête au cas nominal. Effet de bord assumé : le nombre d'items créés peut être inférieur à `amount` si l'espace de noms sature ; le message de fin remonte déjà `created.get()`, l'affichage reste honnête.

```java
// CommandAuctionAdminGenerate.generateAuctionItems, boucle de pre-generation (l.123-145)

        for (int i = 0; i < amount; i++) {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            String sellerName = null;
            UUID sellerUUID = null;

            // Ne JAMAIS adopter l'UUID d'un joueur REEL : les annonces de test
            // apparaitraient dans son onglet "mes ventes" et lui seraient recuperables.
            // Un pseudo deja tire par CETTE generation reste reutilisable, pour qu'un
            // meme faux vendeur puisse porter plusieurs annonces.
            for (int attempt = 0; attempt < 8 && sellerUUID == null; attempt++) {
                String candidate = generateRandomName(random);

                UUID cached = nameToUuidCache.get(candidate);
                if (cached != null) {
                    sellerName = candidate;
                    sellerUUID = cached;
                    break;
                }

                if (playerRepository.selectByName(candidate) != null) continue;

                sellerName = candidate;
                sellerUUID = UUID.randomUUID();
                nameToUuidCache.put(candidate, sellerUUID);
            }

            // Espace de pseudos sature : on saute cette annonce plutot que de
            // polluer le compte d'un joueur reel.
            if (sellerUUID == null) continue;

            Material material = validMaterials.get(random.nextInt(validMaterials.size()));
            int itemAmount = material.getMaxStackSize() == 1 ? 1 : random.nextInt(material.getMaxStackSize()) + 1;
            BigDecimal price = BigDecimal.valueOf(random.nextInt(99990) + 10);
            long expiredAt = System.currentTimeMillis() + (24L * 60L * 60L * 1000L);

            dataList.add(new GenerationData(sellerUUID, sellerName, material, itemAmount, price, expiredAt));
        }
```

## QUICK.9 — C-115 (b) — supprimer la rétention de `Player` dans les maps de confirmation

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/command/commands/admin/CommandAuctionAdminGenerate.java`  
**Risque** : LOW  
**Constats** : C-115

`Map<CommandSender, Long>` et `Map<CommandSender, Integer>` retiennent une référence dure vers le `CraftPlayer` — donc son inventaire et son monde — pour toute la durée de vie du plugin, dès qu'un admin tape la commande sans confirmer. Fuite faible mais réelle et gratuite à fermer. Le passage à `UUID` impose une constante dédiée pour la console (`new UUID(0L, 0L)`) : sans elle, plusieurs senders console partageraient l'entrée — sans conséquence ici puisqu'il n'y a qu'une console. Je fusionne les deux maps en une seule entrée horodatée, purgée à la lecture (aucune tâche périodique nécessaire). Je NE livre PAS ici la partie (a) de C-115 (lecture réelle de la section `admin-generate`) : c'est un ajout de record dans le module publié `api/` et une décision de mainteneur — voir openQuestions.

```java
// CommandAuctionAdminGenerate.java, champs l.30-31

    private static final UUID CONSOLE_ID = new UUID(0L, 0L);
    private static final long CONFIRMATION_WINDOW_MS = 30_000L;

    // Ne PAS indexer par CommandSender : conserver un Player retient le CraftPlayer,
    // son inventaire et son monde pour toute la duree de vie du plugin.
    private final Map<UUID, PendingGeneration> pendingGenerations = new ConcurrentHashMap<>();

    private record PendingGeneration(int amount, long requestedAt) {
    }

    private UUID senderId(CommandSender commandSender) {
        return commandSender instanceof Player player ? player.getUniqueId() : CONSOLE_ID;
    }

// CommandAuctionAdminGenerate.perform, l.69-89

        long currentTime = System.currentTimeMillis();
        UUID senderId = senderId(this.sender);

        // Purge des confirmations perimees a la lecture : pas de tache, pas de fuite.
        this.pendingGenerations.values().removeIf(pending -> currentTime - pending.requestedAt() >= CONFIRMATION_WINDOW_MS);

        PendingGeneration pending = this.pendingGenerations.get(senderId);
        if (pending != null && pending.amount() == amount) {

            this.pendingGenerations.remove(senderId);

            message(this.plugin, sender, Message.ADMIN_GENERATE_CONFIRMED, "%amount%", String.valueOf(amount));
            generateAuctionItems(amount, validMaterials);
            return CommandType.SUCCESS;
        }

        this.pendingGenerations.put(senderId, new PendingGeneration(amount, currentTime));
        message(this.plugin, sender, Message.ADMIN_GENERATE_WARNING, "%amount%", String.valueOf(amount));

        return CommandType.SUCCESS;

// Imports a ajouter : org.bukkit.entity.Player, java.util.concurrent.ConcurrentHashMap
// (java.util.* couvre deja UUID et Map).
```

## QUICK.10 — C-097 — borner la sélection de vente à ce que le GUI sait afficher et retirer

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/buttons/sell/SellShowItemButton.java`  
**Risque** : LOW  
**Constats** : C-097

`onInventoryClick` fait un `put` sans plafond, alors que `onRender` tronque à `Math.min(slots.size(), entries.size())` : au-delà de la capacité d'affichage, les stacks sélectionnés deviennent invisibles ET non désélectionnables (le bouton de retrait n'est rendu que pour les entrées affichées). Sur les configurations livrées (36 slots pour 36 emplacements cliquables) la garde n'est jamais atteinte — aucune régression. Sur une configuration réduite, des clics sont désormais refusés : c'est l'objectif. Le garde `!this.slots.isEmpty()` évite le cas dégénéré où un bouton mal configuré bloquerait TOUTE sélection. Ce pas est aussi le complément GUI de C-051 (chantier 6, plafond `max-stacks-per-listing`) : sans lui le joueur n'apprendrait le plafond qu'au moment de confirmer.

```java
// ===== SellShowItemButton.onInventoryClick, l.110-119 =====

        // Toggle: if slot already in map, remove it; otherwise add it
        if (sellItems.containsKey(clickedSlot)) {
            sellItems.remove(clickedSlot);
            manager.message(player, Message.SELL_ITEM_REMOVED);
        } else {
            // Borner la selection a ce que le GUI sait AFFICHER et RETIRER : onRender
            // tronque a slots.size(), les stacks en trop deviennent invisibles ET
            // non deselectionnables sur une configuration d'inventaire reduite.
            if (!this.slots.isEmpty() && sellItems.size() >= this.slots.size()) {
                manager.message(player, Message.SELL_INVENTORY_FULL, "%max%", String.valueOf(this.slots.size()));
                return;
            }
            sellItems.put(clickedSlot, clickedItem.clone());
            manager.message(player, Message.SELL_ITEM_ADDED);
        }

// ===== API/.../api/messages/Message.java, a cote de SELL_ITEM_ADDED (l.59) =====

    SELL_INVENTORY_FULL("<error>You cannot select more than <white>%max%<error> stacks."),

// ===== messages.yml (racine anglaise) + fr/ + es/ + it/ + id/ + th/, a cote de sell-item-added =====

sell-inventory-full: "<error>You cannot select more than <white>%max%<error> stacks."
```

## QUICK.11 — C-004 — compare-and-swap du statut reçu par le bus Redis (addon)

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/listener/listeners/ItemStatusListener.java`  
**Risque** : LOW  
**Constats** : C-004

`item.setStatus(message.newStatus())` est appliqué en aveugle : un message pub/sub arrivé en retard écrase un statut plus récent. `ItemStatusMessage` transporte pourtant `oldStatus`, et les 6 appelants de `notifyItemStatusChange` le renseignent tous explicitement (vérifié). J'ajoute la seconde barrière de C-004, que l'audit décrit sans la chiffrer : un item détenu dans EXPIRED ou PURCHASED ne doit JAMAIS recevoir un statut du cycle LISTED, sinon il redevient achetable alors qu'il appartient déjà à quelqu'un. La transition légitime REMOVED/PURCHASED → DELETED (RemoveService:142/:180) reste autorisée, DELETED n'appartenant pas au cycle LISTED. La garde `oldStatus != null` conserve la compatibilité avec un payload d'ancienne version pendant un rolling upgrade. ATTENTION : ce pas transforme une perte de message en désynchronisation PERMANENTE là où l'application aveugle se resynchronisait par hasard — C-072 (TTL de réarmement, chantier 7) doit sortir dans la même release.

```java
// ItemStatusListener.java, l.39-64

        auctionPlugin.getScheduler().runNextTick(wrappedTask -> {
            var manager = auctionPlugin.getAuctionManager();

            Item item = null;
            StorageType foundIn = null;
            for (StorageType storageType : SEARCHABLE_STORAGE_TYPES) {
                var optional = manager.getItems(storageType).stream().filter(e -> e.getId() == id).findFirst();
                if (optional.isPresent()) {
                    item = optional.get();
                    foundIn = storageType;
                    break;
                }
            }

            if (item == null) return;

            // Barriere 1 - compare-and-swap : n'appliquer le nouveau statut que si le
            // statut local est bien celui que l'emetteur a observe. Sans elle, un message
            // arrive en retard ecrase un statut plus recent. oldStatus peut etre null sur
            // un payload d'une version anterieure : on conserve alors l'ancien
            // comportement pour ne pas casser un rolling upgrade.
            if (message.oldStatus() != null && item.getStatus() != message.oldStatus()) {
                this.plugin.debug("Skip stale status for " + id + " (local=" + item.getStatus() + ", expected=" + message.oldStatus() + ")");
                return;
            }

            // Barriere 2 : un item detenu dans EXPIRED ou PURCHASED n'appartient plus au
            // cycle de vente. Lui appliquer AVAILABLE / IS_*_CONFIRM / IS_BEING_* le
            // rendrait achetable alors qu'il est deja a quelqu'un. DELETED reste autorise
            // (transition legitime RemoveService:142/:180).
            if (foundIn != StorageType.LISTED && isListedCycle(message.newStatus())) {
                this.plugin.getLogger().warning("Refused LISTED-cycle status " + message.newStatus()
                        + " for item " + id + " held in " + foundIn);
                return;
            }

            item.setStatus(message.newStatus());

            if (message.newStatus() == ItemStatus.IS_PURCHASE_CONFIRM || message.newStatus() == ItemStatus.IS_REMOVE_CONFIRM
                    || message.newStatus() == ItemStatus.IS_BEING_REMOVED || message.newStatus() == ItemStatus.IS_BEING_PURCHASED) {
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING);
                manager.updateListedItems(item, false, null);
            } else if (message.newStatus() == ItemStatus.AVAILABLE) {
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING);
                manager.updateListedItems(item, true, null);
            }
        });
    }

    private static boolean isListedCycle(ItemStatus status) {
        return status == ItemStatus.AVAILABLE
                || status == ItemStatus.IS_PURCHASE_CONFIRM
                || status == ItemStatus.IS_REMOVE_CONFIRM
                || status == ItemStatus.IS_BEING_PURCHASED
                || status == ItemStatus.IS_BEING_REMOVED;
    }
```

## QUICK.12 — C-031 — enregistrer les listeners AVANT le thread abonné, et fermer la course de données

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/ZAuctionHouseRedis.java`  
**Risque** : LOW  
**Constats** : C-031

`subscriberThread.start()` est à la l.85, les quatre `registerListener` aux l.101-104 et `enableDebug` à la l.106 : les deux champs sont écrits par le thread principal APRÈS le démarrage du thread lecteur, sans aucune relation happens-before. `listeners` est de surcroît une `HashMap` mutée pendant qu'un autre thread la lit. Et le bridge est installé (l.99) avant que les listeners existent : ce nœud peut verrouiller et publier alors qu'il est incapable de recevoir. Je déplace aussi l'installation du bridge APRÈS le démarrage de l'abonné. POINT À NE PAS LAISSER CROIRE AU COMMANDITAIRE : ce réordonnancement NE FERME PAS la fenêtre principale. `depend: zAuctionHouse` garantit que `ZAuctionPlugin.onEnable` — instantané DB inclus — se termine avant le onEnable de l'addon ; le vrai trou va de cet instantané au SUBSCRIBE effectif (plusieurs secondes). Seul C-047 (chantier 7) le referme. Je n'ajoute donc PAS d'attente de confirmation d'abonnement, qui coûterait 5 s de démarrage sans rien fermer.

```java
// ===== ZAuctionHouseRedis.java, champ l.43 =====

    // Lu par le thread abonne (handleMessage -> plugin.debug), ecrit par le thread principal.
    private volatile boolean enableDebug = false;

// ===== ZAuctionHouseRedis.onEnable, l.81-106 : REORDONNANCEMENT =====

        this.validateAndRegisterUUID();

        // Load configurable lock TTL (default 30 seconds)
        int lockTtlSeconds = getConfig().getInt("redis-config.lock-ttl-seconds", 30);
        this.lockTtl = Duration.ofSeconds(lockTtlSeconds);

        // Load configurable item state TTL (default 24 hours, 0 = disabled)
        int itemStateTtlSeconds = getConfig().getInt("redis-config.item-state-ttl-seconds", 86400);
        Duration itemStateTtl = itemStateTtlSeconds > 0 ? Duration.ofSeconds(itemStateTtlSeconds) : null;

        // Load configurable item listed TTL (default 30 days, 0 = disabled)
        int itemListedTtlSeconds = getConfig().getInt("redis-config.item-listed-ttl-seconds", 2592000);
        Duration itemListedTtl = itemListedTtlSeconds > 0 ? Duration.ofSeconds(itemListedTtlSeconds) : null;

        // ORDRE CRITIQUE. Le thread abonne lit `listeners` et `enableDebug` : les deux
        // DOIVENT etre renseignes AVANT son demarrage (relation happens-before). Et le
        // bridge ne doit etre installe qu'une fois ce noeud capable de RECEVOIR : sinon
        // il verrouille et publie alors qu'il ignore tout ce qui lui arrive en retour.
        this.enableDebug = getConfig().getBoolean("debug");

        this.redisSubscriberRunnable.registerListener(ItemListedMessage.class, new ItemListedListener(this));
        this.redisSubscriberRunnable.registerListener(ItemRemovedMessage.class, new ItemRemovedListener(this));
        this.redisSubscriberRunnable.registerListener(ItemBoughtMessage.class, new ItemBoughtListener(this));
        this.redisSubscriberRunnable.registerListener(ItemStatusMessage.class, new ItemStatusListener(this));

        this.subscriberThread = new Thread(this.redisSubscriberRunnable, "zAuctionHouse-Redis-Subscriber");
        this.subscriberThread.setDaemon(true);
        this.subscriberThread.start();

        this.auctionPlugin.setAuctionClusterBridge(new RedisAuctionClusterBridge(this, this.jedisPool, this.lockTtl, itemStateTtl, itemListedTtl));
    }

// ===== RedisSubscriberRunnable.java, champ l.30 =====

    // Ecrite par le thread principal, lue par le thread abonne : HashMap = course de donnees.
    private final Map<Class<?>, RedisListener<?>> listeners = new ConcurrentHashMap<>();

// ===== RedisSubscriberRunnable.handleMessage, l.141-144 =====

            if (listener != null) {
                listener.message(result);
            } else {
                // Ne plus jeter silencieusement un message dont personne n'ecoute la classe.
                plugin.getLogger().warning("No listener registered for " + className + ", message dropped");
            }

// Import a ajouter dans RedisSubscriberRunnable : java.util.concurrent.ConcurrentHashMap
// (java.util.HashMap devient inutilise).
```

## QUICK.13 — C-116 — rendre le contrôleur de version de l'addon opérant

**Fichier** : `REDIS/src/main/java/fr/maxlego08/zauctionhouse/redis/utils/VersionChecker.java`  
**Risque** : LOW  
**Constats** : C-116

Le VersionChecker de l'addon lit `plugin.getDescription().getVersion()` sur le PLUGIN PRINCIPAL (`AuctionPlugin`) et le compare à la dernière version de la ressource 210 (l'addon) : `4013 >= 103` est toujours vrai, le contrôle est inopérant à 100 %. Un correctif de sécurité publié pour la brique anti-duplication ne serait JAMAIS signalé — c'est ce qui justifie de le corriger dans ce chantier plutôt que de le laisser en « NONE ». Je corrige aussi `Long.parseLong(v.replace(".", ""))`, qui casse dès qu'un segment passe à deux chiffres (1.0.10 → 1010 < 1.1.0 → 110). Et `registerEvents(this, this.plugin)` enregistre le listener sur le plugin PRINCIPAL : il survit à la désactivation de l'addon, d'où le `unregister()` appelé depuis onDisable, comme le fait déjà le VersionChecker du plugin principal (ZAuctionPlugin:188-190). Effet de bord attendu : les serveurs sur un addon obsolète vont brusquement recevoir l'alerte — pic de tickets support possible au premier déploiement, à annoncer.

```java
// ===== REDIS/.../redis/utils/VersionChecker.java =====

public class VersionChecker implements Listener {

    private final String URL_API = "https://groupez.dev/api/v1/resource/version/%s";
    private final String URL_RESOURCE = "https://groupez.dev/resources/%s";
    /** Le plugin ADDON : porte la VRAIE version a comparer et possede le listener. */
    private final Plugin addon;
    /** Le plugin principal : uniquement pour son ordonnanceur compatible Folia. */
    private final AuctionPlugin plugin;
    private final Logger logger;
    private final int pluginID;
    private volatile boolean useLastVersion = false;

    public VersionChecker(Plugin addon, AuctionPlugin plugin, Logger logger, int pluginID) {
        this.addon = addon;
        this.plugin = plugin;
        this.logger = logger;
        this.pluginID = pluginID;
        this.useLastVersion();
    }

    public void useLastVersion() {

        // Enregistrer sur l'ADDON : sur le plugin principal, le listener survivait a
        // la desactivation de l'addon.
        Bukkit.getPluginManager().registerEvents(this, this.addon);

        String pluginVersion = this.addon.getDescription().getVersion();
        this.getVersion(version -> {
            this.useLastVersion = compare(pluginVersion, version) >= 0;
            if (this.useLastVersion) this.logger.info("No update available.");
            else {
                this.logger.info("New update available. Your version: " + pluginVersion + ", latest version: " + version);
                this.logger.info("Download plugin here: " + String.format(URL_RESOURCE, this.pluginID));
            }
        });
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    /**
     * Comparaison segment par segment. Long.parseLong(v.replace(".", "")) cassait des
     * qu'un segment passait a deux chiffres : 1.0.10 -> 1010 etait juge superieur a
     * 1.1.0 -> 110.
     */
    private static int compare(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int a = i < leftParts.length ? parseSegment(leftParts[i]) : 0;
            int b = i < rightParts.length ? parseSegment(rightParts[i]) : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }

    /** Absorbe un suffixe non numerique du type "-SNAPSHOT". */
    private static int parseSegment(String segment) {
        StringBuilder builder = new StringBuilder();
        for (char c : segment.toCharArray()) {
            if (Character.isDigit(c)) builder.append(c);
        }
        return builder.isEmpty() ? 0 : Integer.parseInt(builder.toString());
    }

    // onConnect() et getVersion() : inchanges. `this.plugin.getScheduler()` reste utilise
    // pour rester compatible Folia (plugin.yml de l'addon declare folia-supported: true).
}

// Imports a ajouter : org.bukkit.event.HandlerList, org.bukkit.plugin.Plugin

// ===== ZAuctionHouseRedis.java =====

    private VersionChecker versionChecker;

    // onEnable, l.71-73
        if (getConfig().getBoolean("enable-version-checker", true)) {
            this.versionChecker = new VersionChecker(this, this.auctionPlugin, this.getLogger(), 210);
        }

    // onDisable, en tete
        if (this.versionChecker != null) {
            this.versionChecker.unregister();
        }
```

## QUICK.14 — C-110 — aligner le contrat de `LocalAuctionClusterBridge` sur celui du bridge Redis (GATÉ sur C-042)

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/cluster/LocalAuctionClusterBridge.java`  
**Risque** : MEDIUM  
**Constats** : C-110

Le bridge local rend un `failedFuture` sur contention là où le bridge Redis rend `LockToken.noop()` : en mono-serveur, un échec bénin d'acquisition devient INTERNAL_ERROR et INTERROMPT la chaîne séquentielle des retraits de masse (ZAuctionManager:1092-1110). Je rends aussi `unlockItem` conditionnel, en stockant la VALEUR du jeton au lieu de l'UUID de l'acheteur — aujourd'hui `itemLocks.remove(id)` est INCONDITIONNEL et un déverrouillage retardataire casse le verrou légitime d'un autre appelant. GATE OBLIGATOIRE : ne pas livrer avant C-042. Sans le test du jeton noop dans `ZAuctionManager:689`, le retrait admin cesserait d'être fail-closed en mono-serveur (aujourd'hui le failedFuture le bloque, demain le noop le laisserait passer sans verrou). Note d'honnêteté : sans C-008 (jeton unique par acquisition, chantier 4), le `remove(id, value)` conditionnel compare deux valeurs déterministes identiques et ne protège de rien — il ne régresse rien non plus, mais il ne faut pas le présenter comme une protection. Changement observable à annoncer au changelog : ce qui produisait « Internal error » produit désormais LOCK_FAILED, et la chaîne de retrait de masse ne s'interrompt plus.

```java
// LocalAuctionClusterBridge.java

public class LocalAuctionClusterBridge implements AuctionClusterBridge {

    // La VALEUR du jeton, et non l'UUID de l'acheteur : c'est elle qui permet un
    // deverrouillage conditionnel. Toute exploitation future de la map pour savoir
    // QUI detient le verrou doit passer par LockToken.issue (chantier 4).
    private final ConcurrentHashMap<Integer, String> itemLocks = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Boolean> checkAvailability(Item item) {
        return CompletableFuture.completedFuture(!itemLocks.containsKey(item.getId()));
    }

    @Override
    public CompletableFuture<LockToken> lockItem(Item item, UUID buyerId, StorageType storageType) {
        var lockToken = LockToken.of(item);

        // Convention alignee sur RedisAuctionClusterBridge, seul point de variation
        // documente pour les bridges tiers : un echec d'ACQUISITION rend
        // LockToken.noop() (les services sortent proprement en LOCK_FAILED, et la
        // chaine de retrait de masse ne s'interrompt plus) ; un future en erreur est
        // RESERVE aux pannes de transport.
        if (itemLocks.putIfAbsent(item.getId(), lockToken.value()) != null) {
            return CompletableFuture.completedFuture(LockToken.noop());
        }

        return CompletableFuture.completedFuture(lockToken);
    }

    @Override
    public CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType) {
        // Liberation CONDITIONNELLE : `itemLocks.remove(id)` inconditionnel cassait le
        // verrou legitime d'un autre appelant quand un unlock retardataire arrivait.
        // Sans C-008 (jeton unique par acquisition) les deux valeurs sont identiques
        // et ce test ne protege encore de rien : il ne regresse rien non plus.
        if (lockToken != null) {
            itemLocks.remove(item.getId(), lockToken.value());
        }
        return CompletableFuture.completedFuture(null);
    }

    // notifyItemBought / notifyItemListed / notifyItemStatusChange / removeItem : inchanges.
}

// L'import java.util.UUID reste requis (parametre buyerId de lockItem).
```

## QUICK.15 — C-090 — chargement O(n×m) → O(n+m) par regroupement des contenus

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/utils/ItemLoaderUtils.java`  
**Risque** : LOW  
**Constats** : C-090

`getAuctionItems` refait un `stream().filter()` sur TOUTE la liste des contenus pour chaque item : 20 000 items × 25 000 contenus = 500 M de comparaisons à chaque démarrage, et le même coût dans `ZStorageManager.selectItems` (résolution de lots à chaud). `Collectors.groupingBy` préserve l'ordre de rencontre à l'intérieur de chaque groupe : l'ordre des ItemStack d'un lot est donc identique à celui du `filter` actuel — ce qui compte, puisque cet ordre détermine l'affichage de `getItemDisplay` et l'ordre de remise dans `giveItem`. Je NE livre PAS l'index SQL proposé par l'audit sur `auction_items(item_id)` : `Sarah/CreateIndexRequest` émet un `CREATE INDEX` SANS `IF NOT EXISTS` et le vrai goulot du démarrage est le `whereIn` non borné de `AuctionItemRepository:41` (chantier 6, pagination par lots de 500). La suppression de `getAuctionItems` est sans risque : elle est `protected`, appelée uniquement l.84 du même fichier, et ItemLoaderUtils n'appartient pas au module publié `api/`.

```java
// ItemLoaderUtils.java

    // SUPPRIMER la methode getAuctionItems (l.31-33) : seul appelant = l.84 ci-dessous.

    protected Result createItems(AuctionPlugin plugin, Map<UUID, String> players, List<ItemDTO> items, PerformanceDebug performanceDebug, BiConsumer<StorageType, Item> biConsumer) {

        var categoryManager = plugin.getCategoryManager();
        var storageManager = plugin.getStorageManager();
        var economyManager = plugin.getEconomyManager();

        long auctionItemsStartTime = performanceDebug.start();
        var auctionItems = storageManager.with(AuctionItemRepository.class).select(getIDS(items, ItemType.AUCTION));
        performanceDebug.end("loadItems.loadAuctionItemsFromDB", auctionItemsStartTime, "count=" + auctionItems.size());

        // O(n+m) au lieu de O(n x m) : un SEUL regroupement, au lieu d'un filtre complet
        // de la liste pour CHAQUE item (20 000 x 25 000 comparaisons a chaque boot).
        // groupingBy preserve l'ordre de rencontre a l'interieur d'un groupe : l'ordre
        // des ItemStack d'un lot est identique a celui de l'ancien filter, ce qui compte
        // pour getItemDisplay et pour l'ordre de remise dans giveItem.
        Map<Integer, List<AuctionItemDTO>> auctionItemsByItemId = auctionItems.stream()
                .collect(Collectors.groupingBy(AuctionItemDTO::item_id));

        int amount = 0;

        long processStartTime = performanceDebug.start();
        for (ItemDTO dto : items) {

            // ... inchange jusqu'au switch ...

            switch (dto.item_type()) {
                case AUCTION -> {

                    var currentAuctionItems = auctionItemsByItemId.getOrDefault(dto.id(), List.of());
                    var auctionItem = this.createAuctionItem(plugin, dto, sellerName, currentAuctionItems, optional.get());

                    // ... inchange ...
                }
                // ... inchange ...
            }
        }
        // ... inchange ...
    }

// Import a ajouter : java.util.stream.Collectors
```

## QUICK.16 — C-073 — utiliser le rowcount du DELETE au lieu de pré-compter les logs

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/storage/repository/repositories/LogRepository.java`  
**Risque** : LOW  
**Constats** : C-073

Les trois méthodes de purge font `select(...).size()` avant le `delete(...)` : elles matérialisent en heap l'intégralité des lignes à supprimer, chacune portant une colonne `itemstack` LONGTEXT, et balaient la table DEUX fois. Sur une table de plusieurs millions de logs c'est un OOM. Sarah calcule déjà le rowcount (`DeleteRequest:35 return preparedStatement.executeUpdate()`) et `Repository.delete` (l.206-215) le propage. Le compte devient même plus juste, puisqu'il reflète la suppression réelle et non un pré-comptage. Le `Math.max(0, ...)` neutralise le `-1` d'erreur, qui est du CODE MORT (Sarah lève une DatabaseException runtime, jamais une SQLException). Je livre en revanche le try/catch dans les trois commandes, sinon un échec DB fait désormais remonter une RuntimeException dans le bloc async et l'admin ne voit plus rien du tout. Le point 4 du correctif d'audit (`DELETE ... LIMIT 5000`) n'est PAS implémentable : `Sarah/DeleteRequest` ne supporte aucun LIMIT.

```java
// ===== LogRepository.java, l.143-160 =====

    public long deleteByPlayer(UUID playerUniqueId) {
        // Sarah remonte deja le rowcount (DeleteRequest:35). Le pre-comptage par select()
        // materialisait en heap toutes les lignes, chacune portant un itemstack LONGTEXT,
        // et balayait la table deux fois.
        return Math.max(0, delete(schema -> schema.where("player_unique_id", playerUniqueId.toString())));
    }

    public long deleteOlderThan(long olderThanMs) {
        Date cutoff = new Date(System.currentTimeMillis() - olderThanMs);
        return Math.max(0, delete(schema -> schema.where("created_at", "<", cutoff)));
    }

    public long deleteMigrated() {
        return Math.max(0, delete(schema -> schema.where("item_id", 0)));
    }

// ===== Meme patron dans les TROIS commandes de purge =====
// CommandAuctionAdminLogsPurge.java (l.35-38), CommandAuctionAdminLogsPlayer.java (l.35-38),
// CommandAuctionAdminLogsClearMigrated.java (l.24-27)
// Exemple sur CommandAuctionAdminLogsPurge :

        plugin.getScheduler().runAsync(wrappedTask -> {
            long deleted = 0L;
            try {
                deleted = plugin.getStorageManager().with(LogRepository.class).deleteOlderThan(olderThanMs);
            } catch (RuntimeException exception) {
                // Sarah leve une DatabaseException (RuntimeException) : sans ce catch,
                // elle s'echapperait du bloc async et l'admin n'aurait AUCUN retour.
                plugin.getLogger().log(Level.SEVERE, "Failed to purge logs older than " + days + " days", exception);
            }
            message(plugin, this.sender, Message.ADMIN_LOGS_PURGE_SUCCESS, "%amount%", String.valueOf(deleted), "%days%", String.valueOf(days));
        });

// Import a ajouter dans les trois commandes : java.util.logging.Level
```

## QUICK.17 — C-066 — migrations d'index, une classe PAR index

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/storage/migrations/CreateItemsStorageTypeIndexMigration.java`  
**Risque** : MEDIUM  
**Constats** : C-066

Aucune migration ne crée le moindre index : `selectByPlayerAndStatus` est un scan complet à chaque connexion, `deleteOlderThan` un scan complet, `selectByName` un scan complet. PIÈGE RÉEL, non identifié par l'audit et vérifié dans le code de Sarah : `CreateIndexRequest:26-36` émet `CREATE INDEX idx_<table>_<colonne>` SANS `IF NOT EXISTS` et lève une DatabaseException que `MigrationManager.execute` relance — si un DBA a déjà créé un index de même nom, LE PLUGIN NE DÉMARRE PLUS. Pire, `insertMigration` est appelé PAR SCHEMA : une migration multi-index qui échoue au 3e index sur 4 verra les 4 sautés au boot suivant (le nom de classe est déjà enregistré) et les index manquants ne seront JAMAIS créés. D'où UNE CLASSE PAR INDEX. Je RETIRE deux index du correctif d'audit : `items.seller_unique_id` (déjà couvert par l'index de clé étrangère sous InnoDB) et surtout `items.expired_at`, qui n'apparaît dans AUCUNE clause WHERE du projet — vérifié par grep, l'expiration est paresseuse et pilotée par la mémoire depuis `ZAuctionManager.getItemIds:417`. `Migration.index(table, column)` ne prend qu'UNE colonne : les index composites recommandés par l'audit ne sont pas exprimables avec Sarah 1.23. À prévenir dans le changelog : sur une table `items` de plusieurs centaines de milliers de lignes, `CREATE INDEX` bloque le démarrage plusieurs minutes sous MySQL.

```java
// ===== 4 NOUVELLES CLASSES, une par index =====
// src/main/java/fr/maxlego08/zauctionhouse/storage/migrations/

package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

/**
 * Index sur items(storage_type) : ItemRepository.select() filtre systematiquement
 * storage_type != DELETED.
 * <p>
 * UNE CLASSE PAR INDEX, deliberement : Sarah emet CREATE INDEX sans IF NOT EXISTS et
 * MigrationManager.insertMigration est appele PAR SCHEMA. Regrouper plusieurs index dans
 * une seule Migration ferait qu'un echec au 3e index sur 4 laisserait les suivants
 * definitivement non crees (le nom de classe est deja enregistre au boot suivant).
 */
public class CreateItemsStorageTypeIndexMigration extends Migration {

    @Override
    public void up() {
        index(Tables.ITEMS, "storage_type");
    }
}

// --- CreateTransactionsPlayerIndexMigration.java ---
/** Index sur transactions(player_unique_id) : selectByPlayerAndStatus est appele a chaque connexion. */
public class CreateTransactionsPlayerIndexMigration extends Migration {

    @Override
    public void up() {
        index(Tables.TRANSACTIONS, "player_unique_id");
    }
}

// --- CreateLogsCreatedAtIndexMigration.java ---
/** Index sur logs(created_at) : LogRepository.deleteOlderThan. */
public class CreateLogsCreatedAtIndexMigration extends Migration {

    @Override
    public void up() {
        index(Tables.LOGS, "created_at");
    }
}

// --- CreatePlayersNameIndexMigration.java ---
/**
 * Index sur players(name) : ZStorageManager.findUniqueId (3 commandes admin) et
 * CommandAuctionAdminGenerate.
 * ATTENTION chantier 8 (C-081) : NE PAS recreer cet index, il est livre ici.
 */
public class CreatePlayersNameIndexMigration extends Migration {

    @Override
    public void up() {
        index(Tables.PLAYERS, "name");
    }
}

// ===== ZStorageManager.onEnable, l.71 : enregistrer EN DERNIER =====
// L'ordre historique des migrations deja appliquees ne doit pas bouger.

        MigrationManager.registerMigration(new CreateOptionsMigration());

        // Index (ajoutes en dernier pour ne pas perturber l'ordre historique).
        MigrationManager.registerMigration(new CreateItemsStorageTypeIndexMigration());
        MigrationManager.registerMigration(new CreateTransactionsPlayerIndexMigration());
        MigrationManager.registerMigration(new CreateLogsCreatedAtIndexMigration());
        MigrationManager.registerMigration(new CreatePlayersNameIndexMigration());
```

## QUICK.18 — C-074 — supprimer les copies défensives et le scan linéaire du rendu incrémental

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/buttons/list/ListedItemsButton.java`  
**Risque** : LOW  
**Constats** : C-074

`updateInventory` est appelée une fois par joueur connecté et par événement (vente, achat, retrait, changement de statut cluster). Elle alloue une `ArrayList` copie de `getSlots()` à chaque appel et fait un scan linéaire privé pour retrouver l'index de l'item — le tout APRÈS avoir appelé `getItemIdsListedForSale`, qui peut déclencher un recalcul complet du cache joueur. Je hisse les sorties précoces bon marché AVANT ce calcul, je supprime les copies (vérifié : `processAdd`/`processRemove` ne font que LIRE `slots`), et je remonte `indexOf` sur l'interface `IntList` en méthode `default` — purement additive, aucune implémentation tierce cassée. Je NE touche PAS au `clone()` de `SortedItemsCache.getSortedIds` ni au retrait d'ITEMS_LISTED des `clearPlayersCache` : le clone n'est PAS purement lecteur (`ZAuctionManager.removeFromCache:983` fait `items.rem(...)` sur la liste rangée dans le cache joueur, une vue non clonée muterait la liste partagée du SortedItemsCache), et retirer ITEMS_LISTED sur une branche `added=true` rendrait l'item DÉFINITIVEMENT invisible chez tous les spectateurs — c'est la partie structurelle, hors de ce chantier.

```java
// ===== API/.../api/utils/IntList.java : ajout ADDITIF =====

    /**
     * Returns the index of the first occurrence of the given value, or {@code -1}.
     * <p>
     * Methode {@code default} : purement additive, aucune implementation tierce de
     * {@code IntList} n'est cassee, ni a la compilation ni au binaire.
     *
     * @param value the value to look for
     * @return the index of the first occurrence, or -1 when absent
     */
    default int indexOf(int value) {
        for (int i = 0; i < size(); i++) {
            if (getInt(i) == value) return i;
        }
        return -1;
    }

// ===== API/.../api/utils/IntArrayList.java : surcharge sur le tableau primitif =====

    @Override
    public int indexOf(int value) {
        for (int i = 0; i < size; i++) {
            if (data[i] == value) return i;
        }
        return -1;
    }

// ===== ListedItemsButton.updateInventory, l.249-296 =====

    public void updateInventory(Player player, InventoryEngine inventoryEngine, Item item, boolean isAdded, AuctionManager manager) {

        // Sorties precoces bon marche AVANT getItemIdsListedForSale, qui peut declencher
        // un recalcul complet du cache joueur. getSlots() n'est plus copie : processAdd et
        // processRemove ne font que le LIRE (verifie).
        var slots = getSlots();
        if (slots.size() <= 1) return;

        int page = inventoryEngine.getPage();

        IntList itemIds = manager.getItemIdsListedForSale(player);
        if (itemIds.size() <= 1) {
            this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
            return;
        }

        // indexOf porte par IntList : supprime le scan lineaire prive findIndexOf.
        int itemIndex = itemIds.indexOf(item.getId());
        if (itemIndex == -1) return;

        int itemsPerPage = slots.size();
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = startIndex + itemsPerPage;

        if (itemIndex < startIndex || itemIndex >= endIndex) return;

        if (!isAdded) {
            Item itemToAddAtEnd = null;
            if (endIndex < itemIds.size()) {
                int endItemId = itemIds.getInt(endIndex);
                List<Item> resolved = manager.resolveItems(StorageType.LISTED, createSingleItemList(endItemId));
                if (!resolved.isEmpty() && resolved.getFirst().isActivelyListed()) {
                    itemToAddAtEnd = resolved.getFirst();
                }
            }
            processRemove(itemToAddAtEnd, itemIndex, startIndex, slots, inventoryEngine, player);
        } else {
            processAdd(item, itemIndex, startIndex, slots, inventoryEngine, player);
        }
    }

    // SUPPRIMER la methode privee findIndexOf (l.305-312) : plus aucun appelant.
    // Idem l.57 : `var slots = new ArrayList<>(getSlots());` -> `var slots = getSlots();`
```

## QUICK.19 — C-055 / C-098 / C-104 (API) — accesseurs O(1) sur `AuctionManager`, en méthodes `default`

**Fichier** : `API/src/main/java/fr/maxlego08/zauctionhouse/api/AuctionManager.java`  
**Risque** : LOW  
**Constats** : C-055, C-050, C-098, C-104

Trois chemins chauds recopient tout le store LISTED pour répondre à une question O(1) : la recherche (`SearchService:48`), le compteur global (`GlobalPlaceholders:18`) et les compteurs de catégorie (`ZCategoryManager:253`). Ce commit ajoute les trois accesseurs en une seule passe sur l'interface publiée, parce que C-055 et C-098 éditent les mêmes zones d'`AuctionManager.java` et de `ZAuctionManager.java` : les livrer séparément garantit un conflit de patch. STRATÉGIE DE COMPATIBILITÉ : méthodes `default` avec une implémentation générique fonctionnelle, et non abstraites. C'est source- ET binaire-compatible (précédent dans le même dépôt : `AuctionClusterBridge.isDistributed()`), donc l'addon Redis compilé contre le SHA figé `deb8f16` continue de tourner sans recompilation, et aucune implémentation tierce d'`AuctionManager` ne casse. Ce commit ne change aucun appelant : il compile et se déploie seul.

```java
// ===== API/.../api/AuctionManager.java =====
// Import a ajouter : fr.maxlego08.zauctionhouse.api.category.Category

    /**
     * Resolves a single item by its identifier inside the given bucket.
     * <p>
     * Declaree {@code default} pour rester source- ET binaire-compatible : les
     * implementations tierces heritent d'un balayage lineaire, l'implementation du
     * plugin la surcharge en O(1) sur le conteneur interne.
     *
     * @param storageType logical container to read from
     * @param itemId      identifier to resolve
     * @return the live item instance, or {@code null} when the bucket does not hold it
     */
    default Item getItem(StorageType storageType, int itemId) {
        for (Item item : getItems(storageType)) {
            if (item.getId() == itemId) return item;
        }
        return null;
    }

    /**
     * Number of items actually displayed in the auction list: available for sale and not
     * expired. Same filter as {@link Item#isActivelyListed()} and as the sorted cache used
     * by the GUI, so the count always matches what the player sees.
     *
     * @return number of actively listed items
     */
    default int getListedItemCount() {
        return (int) getItems(StorageType.LISTED).stream().filter(Item::isActivelyListed).count();
    }

    /**
     * Same as {@link #getListedItemCount()}, restricted to one category.
     *
     * @param category category to restrict to; {@code null} means every category
     * @return number of actively listed items in that category
     */
    default int getListedItemCount(Category category) {
        if (category == null) return getListedItemCount();
        return (int) getItems(StorageType.LISTED).stream()
                .filter(Item::isActivelyListed)
                .filter(item -> item.hasCategory(category))
                .count();
    }

// ===== src/.../ZAuctionManager.java : surcharges O(1) =====

    @Override
    public Item getItem(StorageType storageType, int itemId) {
        // storageItemsById est deja un EnumMap<StorageType, ConcurrentHashMap<Integer,Item>> :
        // acces direct, sans copie du store ni scan lineaire.
        var storage = this.storageItemsById.get(storageType);
        return storage == null ? null : storage.get(itemId);
    }

    @Override
    public int getListedItemCount() {
        // Servi en O(1) par le cache trie : son filtre de reconstruction
        // (SortedItemsCache:305) est litteralement isActivelyListed().
        // Toutes les cles de tri portent le meme nombre d'elements.
        return this.sortedItemsCache.getTotalCount(this.plugin.getConfiguration().getSort().defaultSort());
    }

    @Override
    public int getListedItemCount(Category category) {
        if (category == null) return getListedItemCount();
        return this.sortedItemsCache.getTotalCount(category, this.plugin.getConfiguration().getSort().defaultSort());
    }

// Import a ajouter dans ZAuctionManager si absent : fr.maxlego08.zauctionhouse.api.category.Category
```

## QUICK.20 — C-055 — la recherche lit le store en O(1) au lieu d'en faire une copie complète

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/services/SearchService.java`  
**Risque** : LOW  
**Constats** : C-055

`search` copie l'intégralité du store LISTED (`getItems` alloue une ArrayList complète) puis reconstruit une `HashMap<Integer, Item>` de n entrées — à chaque frappe de recherche, pour chaque joueur. `storageItemsById.get(LISTED)` EST déjà une `Map<Integer, Item>` : sémantique strictement identique, deux allocations de moins par recherche. Je REJETTE l'étape 2 proposée par l'audit (retirer `PlayerCacheKey.ITEMS_SEARCH` des `clearPlayersCache` d'`ItemListedListener:45` et `ItemBoughtListener:50`) : sur le chemin de MISE EN VENTE c'est une régression de justesse — une nouvelle annonce correspondant à la requête n'apparaîtrait jamais dans une recherche active, et l'insérer au bon rang exigerait de recalculer son rang dans l'ordre de tri. Un `removeFromSearchCache` n'est acceptable que sur les chemins de RETRAIT, où retirer un id d'une liste déjà ordonnée est toujours sûr : c'est C-092, chantier 7.

```java
// SearchService.search, l.45-71

        IntList allIds = sortedItemsCache.getSortedIds(category, sort);
        IntList results = new IntArrayList();

        // Acces O(1) direct dans le conteneur LISTED. L'ancien code copiait TOUT le store
        // (getItems alloue une ArrayList complete) puis reconstruisait une HashMap de n
        // entrees, a chaque frappe de recherche et pour chaque joueur.
        var manager = this.plugin.getAuctionManager();
        String lowerValue = parsedQuery.value().toLowerCase();

        for (int id : allIds) {
            Item item = manager.getItem(StorageType.LISTED, id);
            if (item == null) continue;

            if (parsedQuery.isDefault()) {
                if (matchDefault(item, lowerValue)) {
                    results.add(id);
                }
            } else {
                if (matchWithFilter(item, parsedQuery.field(), parsedQuery.type(), parsedQuery.value())) {
                    results.add(id);
                }
            }
        }

        return results;
    }

// L'import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem reste utilise par matchDefault.
```

## QUICK.21 — C-101 — compteur de génération : une invalidation ne peut plus être perdue par un rebuild

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/utils/cache/SortedItemsCache.java`  
**Risque** : LOW  
**Constats** : C-101

`rebuildCache` lit son instantané puis, à la fin, fait `dirty.set(false)` inconditionnellement : une invalidation survenue PENDANT la reconstruction est effacée, et l'item mis en vente à ce moment-là n'apparaît jamais dans la liste triée jusqu'à la prochaine invalidation fortuite. C'est le prérequis DUR des pas 22 et 23 : servir les compteurs depuis un cache qui perd des mutations remplacerait un compteur lent mais exact par un compteur rapide et faux. Je livre aussi le second passage borné dans `ensureCacheValidAsync`, pour que le future rendu à `updateListedItems` ne se termine jamais sur un instantané périmé. Volontairement borné à UN essai : une boucle `while(dirty)` ne se terminerait jamais sous 10 mises en vente par seconde. Effet de bord assumé et connu : sous forte charge de mises en vente, la fréquence de reconstruction augmente — c'est exactement le symptôme que le pas 22 atténue, d'où l'enchaînement immédiat.

```java
// SortedItemsCache.java

// --- champ, a cote de `dirty` (l.48) ---

    // Compteur de generation, incremente a CHAQUE invalidation. Une reconstruction ne
    // peut remettre `dirty` a false que si aucune invalidation n'est survenue pendant
    // qu'elle lisait son instantane, sinon la mutation serait perdue en silence.
    private final AtomicLong generation = new AtomicLong();

// --- invalidate(), l.173-175 ---

    public void invalidate() {
        generation.incrementAndGet();
        dirty.set(true);
    }

// --- rebuildCache(), juste avant la lecture de l'instantane (l.296) ---

            // Double-check after acquiring lock
            if (!dirty.get()) {
                performanceDebug.end("SortedItemsCache.rebuild", startTime, "skipped (already rebuilt)");
                return;
            }

            long startedAtGeneration = generation.get();

            // Get all items
            Collection<Item> allItems = itemsSupplier.get();

// --- rebuildCache(), branche itemCount == 0 (l.330) ---

            if (itemCount == 0) {
                sortedAllItems.set(newSortedAllItems);
                sortedByCategoryItems.set(newSortedByCategoryItems);
                if (generation.get() == startedAtGeneration) dirty.set(false);
                lastRebuildTime = System.currentTimeMillis();
                performanceDebug.end("SortedItemsCache.rebuild", startTime, "items=0");
                return;
            }

// --- rebuildCache(), publication finale (l.389) ---

            sortedAllItems.set(newSortedAllItems);
            sortedByCategoryItems.set(newSortedByCategoryItems);

            // Ne marquer propre QUE si aucune invalidation n'est survenue pendant la
            // reconstruction : sinon on effacerait la mutation concurrente.
            if (generation.get() == startedAtGeneration) dirty.set(false);
            lastRebuildTime = System.currentTimeMillis();

// --- ensureCacheValidAsync(), corps du runAsync (l.260-268) ---

            plugin.getScheduler().runAsync(w -> {
                try {
                    rebuildCache();
                    // Une invalidation survenue PENDANT la reconstruction laisse le cache
                    // sale : un second passage garantit que le future rendu a
                    // updateListedItems ne se termine pas sur un instantane perime.
                    // Borne a UN essai : une boucle while(dirty) ne se terminerait jamais
                    // sous 10 mises en vente/seconde.
                    if (dirty.get()) rebuildCache();
                    newFuture.complete(null);
                } catch (Exception e) {
                    newFuture.completeExceptionally(e);
                } finally {
                    ongoingRebuild.compareAndSet(newFuture, null);
                }
            });

// Import a ajouter : java.util.concurrent.atomic.AtomicLong
```

## QUICK.22 — C-056 (parties gratuites) — une seule garde de reconstruction, et les tris sur le pool privé

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/utils/cache/SortedItemsCache.java`  
**Risque** : LOW  
**Constats** : C-056

Deux gardes INDÉPENDANTES protègent la reconstruction : `rebuildInProgress` (l.51/131/137) sur le chemin `getSortedIds`/`getTotalCount`, et `ongoingRebuild` dans `ensureCacheValidAsync` sur le chemin `updateListedItems`. Elles ne se connaissent pas : deux reconstructions concurrentes peuvent démarrer, chacune prenant le writeLock et re-triant intégralement 50 000 items. Les unifier est un gain de perf purement soustractif. Second point : `Arrays.parallelSort` utilise le ForkJoinPool COMMUN, déjà saturé par les appels Jedis bloquants du bridge ; le cache possède pourtant déjà son `forkJoinPool` privé, utilisé uniquement pour les catégories. JE REJETTE EXPLICITEMENT les deux autres points du correctif d'audit. Point 2 (« ne pas attendre le rebuild pour rafraîchir la GUI ») supprime le `ensureCacheValidAsync().thenRun(...)` que le commentaire l.951-952 dit avoir été ajouté délibérément contre un bug de cache périmé, et déplace la boucle que le pas 1 corrige : ne pas y toucher tant que ce bug d'origine n'est pas retracé. Point 1 (coalescence avec `min-rebuild-interval-ms`) introduit une clé de configuration dans six fichiers et une latence d'affichage bornée : ce n'est pas un gain gratuit, c'est un compromis — voir openQuestions.

```java
// SortedItemsCache.java

// --- SUPPRIMER le champ `rebuildInProgress` (l.50-51) ---

// --- triggerRebuildIfNeeded(), l.130-141 ---

    /**
     * Triggers an async cache rebuild if the cache is dirty. Non-blocking.
     * <p>
     * Delegue a {@link #ensureCacheValidAsync()} : il n'existe plus qu'UN SEUL point
     * d'entree de reconstruction. Deux gardes independantes (rebuildInProgress ici,
     * ongoingRebuild la-bas) laissaient demarrer DEUX reconstructions concurrentes,
     * chacune prenant le writeLock et re-triant integralement le store.
     */
    private void triggerRebuildIfNeeded() {
        if (dirty.get()) ensureCacheValidAsync();
    }

// --- nouvelle methode privee, a cote de buildCategorySortedLists ---

    /**
     * Trie sur le ForkJoinPool PRIVE du cache. {@link Arrays#parallelSort} utilise
     * sinon le ForkJoinPool COMMUN de la JVM, deja sature par les appels Jedis
     * bloquants du bridge (7 sites supplyAsync/runAsync sans executor).
     */
    private void sortInPool(Item[] itemArray, Comparator<Item> comparator, int itemCount) {
        if (itemCount < parallelSortThreshold) {
            Arrays.sort(itemArray, comparator);
            return;
        }
        try {
            forkJoinPool.submit(() -> Arrays.parallelSort(itemArray, comparator)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Arrays.sort(itemArray, comparator);
        } catch (Exception exception) {
            Arrays.sort(itemArray, comparator);
        }
    }

// --- rebuildCache(), les deux tris globaux (l.341-346 et 353-358) ---

            sortInPool(itemArray, SortItem.ASCENDING_DATE.getComparator(), itemCount);
            int[] ascDateIds = extractIdsToArray(itemArray);
            int[] descDateIds = reverseArray(ascDateIds);

            newSortedAllItems.put(SortItem.ASCENDING_DATE, wrapArray(ascDateIds));
            newSortedAllItems.put(SortItem.DECREASING_DATE, wrapArray(descDateIds));

            sortInPool(itemArray, SortItem.ASCENDING_PRICE.getComparator(), itemCount);
            int[] ascPriceIds = extractIdsToArray(itemArray);
            int[] descPriceIds = reverseArray(ascPriceIds);

            newSortedAllItems.put(SortItem.ASCENDING_PRICE, wrapArray(ascPriceIds));
            newSortedAllItems.put(SortItem.DECREASING_PRICE, wrapArray(descPriceIds));

// --- buildCategorySortedLists : remplacer les blocs
//     `if (size >= parallelSortThreshold) Arrays.parallelSort(...) else Arrays.sort(...)`
//     par `sortInPool(itemArray, <comparator>, size);` (meme raison).

// java.util.Comparator est deja couvert par l'import java.util.*
```

## QUICK.23 — C-098 + C-104 — servir les compteurs depuis le cache trié et supprimer la mémoïsation

**Fichier** : `src/main/java/fr/maxlego08/zauctionhouse/category/ZCategoryManager.java`  
**Risque** : MEDIUM  
**Constats** : C-098, C-104

DÉCOUVERTE QUI CHANGE LA PRIORITÉ : ces compteurs ne sont pas « un scoreboard tiers qui pourrait coûter cher ». `%zauctionhouse_listed_items%` est livré dans `inventories/auction.yml:278` et `%zauctionhouse_category_count_*%` apparaît 9 fois dans `inventories/categories.yml` — ils sont sur le chemin de rendu PROPRE du plugin, déclenché à chaque `updateInventory`, donc à chaque événement cluster pour chaque joueur ayant l'HDV ouvert. `getItems(LISTED)` alloue une ArrayList complète à chaque évaluation. JE M'ÉCARTE DU CORRECTIF DE L'AUDIT SUR C-104 : il dit « invalidateCategoryCountCache devient inutile » tout en conservant `categoryCountCache.computeIfAbsent`. Appliqué littéralement, chaque compteur serait calculé une fois puis FIGÉ jusqu'au reload. Je supprime la mémoïsation, pas l'invalidation, et je conserve `invalidateCategoryCountCache()` en no-op documenté pour ne casser ni l'interface publiée `CategoryManager` ni ses deux appelants. BONUS non identifié par l'audit : `loadCategoriesFromFile` indexe par `key.toLowerCase(Locale.ROOT)` mais `loadCategory` construit le ZCategory avec la clé BRUTE — `item.hasCategory(categoryIdMinuscule)` compare aujourd'hui à `getId()` et rend systématiquement 0 pour une catégorie à clé majuscule. Après correctif le compte devient CORRECT : un serveur qui affichait 0 verra soudain un nombre, à signaler dans le changelog.

```java
// ===== src/.../placeholder/placeholders/GlobalPlaceholders.java =====

public class GlobalPlaceholders implements PlaceholderRegister {

    @Override
    public void register(Placeholder placeholder, AuctionPlugin plugin) {

        var manager = plugin.getAuctionManager();
        var categoryManager = plugin.getCategoryManager();

        // Servi en O(1) par le cache trie, dont le filtre de reconstruction
        // (SortedItemsCache:305) est litteralement isActivelyListed() : le compteur
        // reste donc consistant avec la liste affichee, ce qui est l'intention du
        // commentaire d'origine. L'ancien code recopiait TOUT le store LISTED a chaque
        // rendu d'inventaire (le placeholder est livre dans inventories/auction.yml:278).
        placeholder.register("listed_items", player -> String.valueOf(manager.getListedItemCount()),
                "Returns the number of listed items");

        placeholder.register("category_count_", (player, args) -> {
            if (args == null || args.isEmpty()) {
                return "0";
            }
            return String.valueOf(categoryManager.getItemCountForCategory(args));
        }, "Returns the number of items in a category", "<category_id>");
    }
}

// L'import fr.maxlego08.zauctionhouse.api.item.StorageType devient inutilise : le retirer.

// ===== src/.../category/ZCategoryManager.java =====

// --- SUPPRIMER le champ categoryCountCache (l.33) et la methode computeCategoryCount (l.253-271) ---

    @Override
    public long getItemCountForCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) return 0L;

        var manager = this.plugin.getAuctionManager();

        // Servi en O(1) par le cache trie, meme filtre isActivelyListed() que le rendu.
        // La memoisation est SUPPRIMEE, pas l'invalidation : la conserver figerait
        // desormais chaque compteur a sa premiere valeur jusqu'au reload.
        if (categoryId.equalsIgnoreCase("all")) return manager.getListedItemCount();

        // getCategory() normalise la casse ; le ZCategory rendu porte la cle BRUTE dans
        // getId(), qui est exactement celle utilisee par SortedItemsCache.buildCacheKey.
        // Corrige au passage les categories a cle majuscule, qui rendaient 0.
        return getCategory(categoryId).map(category -> (long) manager.getListedItemCount(category)).orElse(0L);
    }

    @Override
    public void invalidateCategoryCountCache() {
        // Conservee pour ne casser ni l'interface publiee CategoryManager ni ses deux
        // appelants (ZAuctionManager:242/:262, CommandAuctionAdminGenerate:231).
        // Les compteurs sont desormais derives du cache trie, qui porte sa propre
        // invalidation : il n'y a plus rien a purger ici.
    }

// `performanceDebug` reste utilise par applyCategories (l.215/:234) : le laisser en place.
// L'import java.util.concurrent.ConcurrentHashMap peut devenir inutilise selon les autres champs.
```

### Ruptures d'API et stratégie de compatibilité

- AuctionManager.getItem(StorageType, int) — AJOUT en methode `default` avec implementation generique fonctionnelle (balayage lineaire sur getItems). Source- ET binaire-compatible : l'addon Redis compile contre le SHA fige deb8f16 continue de tourner sans recompilation, et toute implementation tierce d'AuctionManager continue de compiler. ZAuctionManager la surcharge en O(1). Precedent identique dans le meme depot : AuctionClusterBridge.isDistributed() (api/.../cluster/AuctionClusterBridge.java:104).
- AuctionManager.getListedItemCount() et getListedItemCount(Category) — AJOUT en methodes `default`, meme strategie. L'implementation par defaut reproduit exactement le comportement actuel de GlobalPlaceholders (stream + isActivelyListed), donc un implementeur tiers obtient la bonne valeur sans rien ecrire. Ajoute l'import fr.maxlego08.zauctionhouse.api.category.Category a l'interface : additif.
- IntList.indexOf(int) — AJOUT en methode `default` implementee via size()/getInt(). Purement additive, aucune implementation tierce d'IntList n'est cassee. IntArrayList la surcharge sur le tableau primitif.
- Message.SELL_INVENTORY_FULL — AJOUT d'une constante a un enum publie. Purement additif : aucun switch exhaustif sur Message n'existe dans les deux depots (verifie), donc aucune rupture binaire. Les six messages.yml doivent porter la cle, sinon les cinq locales non anglaises afficheront le texte anglais par defaut (pas de crash, incoherence visible).
- Base64ItemStack.decodeBukkitStream(String) — AJOUT de portee PAQUET (ni public ni protected). ItemStackUtils vit dans le meme paquet fr.maxlego08.zauctionhouse.api.utils : aucune signature publique de l'API publiee ne bouge, aucune republication du jar api n'est requise pour les consommateurs.
- AUCUNE rupture. ItemStackUtils.serializeItemStack change en revanche son CONTRAT observable : il rend desormais `null` au lieu de lever une NPE quand la reflexion NMS echoue. Verifier qu'aucun appelant ne suppose un retour non nul — chemin < 1.20.5 uniquement, atteignable puisque plugin.yml:4 declare api-version: '1.20'. Consequence a assumer : Base64ItemStack.encode peut alors rendre null et faire echouer l'INSERT sur la colonne NOT NULL auction_items.itemstack, ce qui est strictement preferable a une NPE en pleine vente. La garde de vente correspondante appartient a C-067 (chantier 2).
- AUCUN changement dans le depot addon qui exigerait un bump du pin `fr.maxlego08.zauctionhouse:zauctionhousev4-api:deb8f16` (REDIS/build.gradle.kts:49). Les pas 11, 12 et 13 n'utilisent que des API deja presentes dans ce SHA. La migration d'ItemStatusListener vers le nouvel accesseur getItem(StorageType, int) — qui supprimerait 3 copies completes du store par message de statut — exige la republication de l'api puis le bump du pin : elle est deliberement HORS de ce chantier.

### Migrations de schéma

- CreateItemsStorageTypeIndexMigration — CREATE INDEX idx_<prefix>items_storage_type ON <prefix>items (storage_type). Additive, non destructive.
- CreateTransactionsPlayerIndexMigration — CREATE INDEX idx_<prefix>transactions_player_unique_id ON <prefix>transactions (player_unique_id). Supprime le scan complet de selectByPlayerAndStatus, execute a CHAQUE connexion de joueur.
- CreateLogsCreatedAtIndexMigration — CREATE INDEX idx_<prefix>logs_created_at ON <prefix>logs (created_at). Sert LogRepository.deleteOlderThan.
- CreatePlayersNameIndexMigration — CREATE INDEX idx_<prefix>players_name ON <prefix>players (name). Sert ZStorageManager.findUniqueId (3 commandes admin) et CommandAuctionAdminGenerate. LE CHANTIER 8 (C-081) NE DOIT PAS LE RECREER.
- CONTRAINTE SARAH VERIFIEE : Migration.index(table, column) ne prend qu'UNE colonne. Les index composites recommandes par l'audit — items(seller_unique_id, storage_type) et transactions(player_unique_id, status) — ne sont PAS exprimables avec Sarah 1.23 et sont hors de ce chantier.
- INDEX RETIRES DU CORRECTIF D'AUDIT, deliberement : items(seller_unique_id), deja couvert par l'index de cle etrangere sous InnoDB ; et items(expired_at), qui n'apparait dans AUCUNE clause WHERE du projet (verifie par grep — l'expiration est paresseuse et pilotee par la memoire depuis ZAuctionManager.getItemIds:417, jamais par SQL).
- AVERTISSEMENT A PORTER AU CHANGELOG : sur une table items de plusieurs centaines de milliers de lignes, un CREATE INDEX MySQL bloque l'etape de migration plusieurs minutes AU DEMARRAGE, thread principal compris.

### Changements de configuration

- RECTIFICATIF AU CAHIER DES CHARGES : le plugin ne livre pas 4 mais SIX jeux de configuration sur disque — src/main/resources/ (anglais, la reference) plus fr/, es/, it/, id/ (indonesien) et th/ (thai). Le CLAUDE.md du projet et l'enonce du chantier sont perimes sur ce point. Toute cle nouvelle doit etre repliquee SIX fois.
- messages.yml — NOUVELLE CLE `sell-inventory-full` (pas 10), a ajouter a cote de `sell-item-added` (l.146 de la reference anglaise) dans les SIX fichiers : src/main/resources/messages.yml, fr/messages.yml, es/messages.yml, it/messages.yml, id/messages.yml, th/messages.yml. Valeur anglaise de reference : `sell-inventory-full: "<error>You cannot select more than <white>%max%<error> stacks."`. Placeholder : %max%.
- AUCUNE autre cle. Les pas 1 a 9 et 11 a 23 n'introduisent ni cle config.yml, ni cle economies.yml, ni cle discord.yml. Le pas 22 rejette DELIBEREMENT la cle `performance.min-rebuild-interval-ms` proposee par C-056 (voir openQuestions).
- COMMENTAIRES DE CONFIGURATION a mettre a jour sans changer de valeur : config.yml, section `action.update-inventory-on-action` (l.773-774) — preciser que ce reglage devient reellement effectif apres le pas 1 (le bug `return`/`continue` en masquait le cout), et qu'il reste la mitigation immediate pour un serveur qui souffrirait du rafraichissement de tous les joueurs. A repercuter dans les SIX config.yml.
- DOCUMENTATION Docusaurus (C:\Users\Admin\Desktop\groupez\documentation) — mise a jour obligatoire en EN (plugins/zauctionhouse/docs/) ET FR (i18n/fr/docusaurus-plugin-content-docs-zauctionhouse/current/) pour : la nouvelle cle de message, le changement de comportement des compteurs de categorie a cle majuscule (pas 23), et l'alerte de version desormais operante sur l'addon Redis (pas 13). Section `# Unreleased` de changelog.md.

### Fichiers nouveaux

- src/main/java/fr/maxlego08/zauctionhouse/storage/migrations/CreateItemsStorageTypeIndexMigration.java
- src/main/java/fr/maxlego08/zauctionhouse/storage/migrations/CreateTransactionsPlayerIndexMigration.java
- src/main/java/fr/maxlego08/zauctionhouse/storage/migrations/CreateLogsCreatedAtIndexMigration.java
- src/main/java/fr/maxlego08/zauctionhouse/storage/migrations/CreatePlayersNameIndexMigration.java

### Validation

- PAS 1 (C-045) — Serveur de test, 4 joueurs A/B/C/D connectes, tous avec /ah ouvert page 1, `action.update-inventory-on-action: true`. A met un item en vente. AVANT le patch : seuls les joueurs iteres avant A dans getOnlinePlayers() voient l'item apparaitre (l'ordre d'iteration n'est pas garanti : repeter 5 fois). APRES : les 3 spectateurs le voient a chaque essai. Test complementaire : A retire son annonce, verifier que les 3 spectateurs voient le slot disparaitre sans reouvrir l'inventaire.
- PAS 3 (C-054) — Sur un serveur 1.20.4 (isAttributItemStack() == false), inserer a la main une ligne auction_items dont itemstack vaut la chaine `!!!not-base64!!!`, puis redemarrer. AVANT : StackOverflowError dans AuctionLoader.loadItems, onEnable avorte. APRES : le serveur demarre, un WARNING nomme l'item et l'HDV se charge sans lui. Test 2 : sur un Paper 1.21+, `/ah sell 100` sur un item normal, verifier qu'aucune ligne SEVERE `serializeItemStack failed` n'apparait (le chemin NMS n'est pas emprunte).
- PAS 4 (C-100) — `/ah sell 100` avec 3 stacks selectionnes, puis achat par un autre joueur, puis `/ah admin logs`. Requete d'invariant : `SELECT id, LENGTH(itemstack) - LENGTH(REPLACE(itemstack, ';', '')) AS separators FROM <prefix>logs WHERE log_type IN ('SELL','PURCHASE') AND itemstack LIKE '%;%';` — chaque ligne remontee doit afficher separators+1 items dans le GUI d'audit.
- PAS 5 (C-006) — Deux serveurs partageant la meme base MySQL, addon Redis actif. Joueur A sur S1 ouvre la confirmation d'achat de l'item 42. Pendant que le GUI est ouvert, le vendeur, sur S2, fait `/ah` puis retire l'annonce (`remove-listed-item.give-item: false`, defaut) : la ligne passe en EXPIRED. A confirme. ATTENDU : message ITEM_NOT_AVAILABLE, aucun retrait de solde. Verifier `SELECT storage_type, buyer_unique_id FROM <prefix>items WHERE id = 42;` -> EXPIRED / NULL, et le solde de A inchange. AVANT le patch, A est debite et recoit l'item.
- PAS 6 (C-013) — Preparer 3 lignes PENDING pour un joueur, dont une sur une economie SUPPRIMEE d'economies.yml. `/ah claim`. ATTENDU : les 2 economies valides sont creditees et passent RETRIEVED, la troisieme reste PENDING avec un WARNING console. Requete d'invariant : `SELECT status, COUNT(*) FROM <prefix>transactions WHERE player_unique_id = '<uuid>' GROUP BY status;` -> exactement 1 PENDING restant. Test 2 (destruction d'argent) : declencher claimMoney puis deconnecter le joueur dans la meme seconde (kick programme) -> aucune ligne ne doit passer RETRIEVED, elles doivent etre representees a la reconnexion.
- PAS 7 (C-062) — `/ah admin add <joueur> expired 100` puis `/ah admin add <joueur> purchased 100`. Requete d'invariant AVANT toute ouverture d'inventaire : `SELECT id, storage_type, expired_at FROM <prefix>items ORDER BY id DESC LIMIT 2;` -> expired_at doit valoir soit 0, soit now + expire-expiration/purchase-expiration, JAMAIS l'instant de la commande. Puis le joueur ouvre son onglet « expires » : les deux items doivent etre presents et recuperables. AVANT le patch, ils ont disparu.
- PAS 8 (C-085) — Sur une base contenant un joueur reel nomme exactement comme un pseudo generable (creer `INSERT INTO <prefix>players VALUES ('<uuid-reel>', 'AlexPlayer')`), lancer `/ah admin generate 5000 confirm`. Requete d'invariant : `SELECT COUNT(*) FROM <prefix>items WHERE seller_unique_id = '<uuid-reel>';` -> doit valoir 0. Puis le joueur reel ouvre `/ah` onglet « mes ventes » : vide.
- PAS 11 (C-004) — Deux serveurs. Sur S1, poser artificiellement l'item 42 dans le store EXPIRED (le vendeur l'a recupere). Publier depuis S2 un ItemStatusMessage {itemId:42, oldStatus:AVAILABLE, newStatus:AVAILABLE}. ATTENDU sur S1 : WARNING `Refused LISTED-cycle status AVAILABLE for item 42 held in EXPIRED`, item toujours dans EXPIRED, toujours reclamable. Test 2 (CAS) : publier {oldStatus:AVAILABLE, newStatus:IS_BEING_PURCHASED} sur un item local deja en IS_BEING_REMOVED -> ligne debug `Skip stale status`, statut local inchange.
- PAS 12 (C-031) — Demarrer un reseau de 2 noeuds. Sur S2, `grep -n 'Subscribed to Redis channel' logs/latest.log` et `grep -n 'Cluster bridge:' logs/latest.log` (log du pas 2) : la ligne `Subscribed` doit apparaitre AVANT la ligne `Cluster bridge:`. Test 2 : mettre un item en vente sur S1 dans la seconde qui suit le demarrage de S2 -> aucun `No listener registered` dans les logs de S2.
- PAS 13 (C-116) — Poser temporairement dans plugin.yml de l'addon `version: 0.0.1` et redemarrer. ATTENDU : `New update available. Your version: 0.0.1, latest version: X.Y.Z` dans la console. AVANT le patch, la console dit toujours `No update available` (elle comparait la version 4.0.1.3 du plugin principal). Test 2 (comparaison segmentee) : verifier en unitaire que compare("1.0.10", "1.1.0") < 0.
- PAS 14 (C-110) — MONO-SERVEUR, sans addon. Joueur avec 30 annonces expirees, cliquer « tout recuperer ». AVANT : la chaine s'interrompt au premier conflit de verrou avec un INTERNAL_ERROR. APRES : les 30 items sont recuperes. Test de non-regression GATE : `/ah admin` -> supprimer un item, double-clic rapide -> le second clic doit produire ADMIN_ITEM_NOT_AVAILABLE (fourni par C-042) et NON une seconde suppression. Si C-042 n'est pas encore livre, ce test ECHOUE : ne pas merger le pas 14.
- PAS 15 (C-090) — Base de test a 20 000 items / 25 000 contenus. Activer `performance-debug`, redemarrer, relever la ligne `loadItems` : le temps doit chuter d'un ordre de grandeur. Invariant de non-regression sur l'ordre : pour un item multi-stacks connu, comparer la sortie de `/ah admin logs` AVANT et APRES — l'ordre des ItemStack affiches doit etre identique.
- PAS 16 (C-073) — Table logs a 2 M de lignes. `/ah admin logs purge 30`. AVANT : OOM ou plusieurs minutes et un pic heap visible en JFR. APRES : une seule requete, le compte annonce doit egaler `SELECT COUNT(*)` mesure avant la purge sur le meme predicat. Test d'erreur : couper la base pendant la commande -> `0 logs purged` cote joueur ET une ligne SEVERE avec stacktrace en console (avant : silence total).
- PAS 17 (C-066) — Demarrer sur une base VIERGE : verifier `SELECT migration_name FROM zauctionhousev4_migrations;` -> les 4 classes d'index presentes. Puis, sur une base EXISTANTE deja migree, redemarrer : les 4 index sont crees, les migrations historiques ne sont pas rejouees. Test du piege : creer manuellement `CREATE INDEX idx_<prefix>items_storage_type ...`, supprimer la ligne correspondante de zauctionhousev4_migrations, redemarrer -> le plugin ne demarre PAS (comportement connu de Sarah, documente dans rollback).
- PAS 19-20-23 (C-055/C-098/C-104) — Invariant de justesse : sur un serveur au repos (aucun achat ni retrait en cours), `%zauctionhouse_listed_items%` doit egaler `SELECT COUNT(*) FROM <prefix>items WHERE storage_type = 'LISTED' AND (expired_at = 0 OR expired_at > UNIX_TIMESTAMP()*1000);` et egaler le nombre total de slots parcourus dans /ah. Meme test pour `%zauctionhouse_category_count_weapons%` contre le compte de la categorie ouverte. Test du bug de casse : renommer une categorie de categories.yml en `Weapons` (majuscule) -> le compteur doit passer de 0 a la valeur reelle. Test de perf : 50 000 items, 20 joueurs avec /ah ouvert, `performance-debug` actif -> les lignes `computeCategoryCount` disparaissent totalement des logs.
- PAS 21 (C-101) — Test de course : script qui met 200 items en vente en 2 secondes pendant qu'un joueur rafraichit /ah. Invariant : a la fin, `%zauctionhouse_listed_items%` doit egaler le COUNT SQL ci-dessus dans les 2 secondes. AVANT le patch, l'ecart persiste jusqu'a la prochaine invalidation fortuite. Repeter 10 fois.
- PAS 22 (C-056) — 50 000 items, activer `performance-debug`, declencher 20 ventes en rafale. Compter les lignes `SortedItemsCache.rebuild` NON marquees `skipped` : leur nombre doit diminuer par rapport a la mesure de reference (deux gardes -> une seule). Verifier avec un profileur que `ForkJoinPool.commonPool` n'apparait plus dans la pile des tris du cache.
- TEST DE NON-REGRESSION TRANSVERSE (a passer apres CHAQUE pas) — Compilation des deux depots : `JAVA_HOME="C:/Users/Admin/.jdks/ms-21.0.9" ./gradlew build` dans zAuctionHouseV4 (jar dans target/), puis `./gradlew build` dans « zAuctionHouse Redis » SANS toucher au pin deb8f16 : si l'addon ne compile plus, c'est qu'un pas a introduit une rupture d'API interdite par ce chantier.

### Questions ouvertes pour le mainteneur

- COALESCENCE DU CACHE TRIE (C-056, point 1) — Je l'ai DELIBEREMENT exclue de ce chantier parce qu'elle n'est pas un gain gratuit : elle introduit une cle `performance.min-rebuild-interval-ms` a repliquer dans les six config.yml et une latence d'affichage bornee (250 ms par defaut) entre la mise en vente et l'apparition de l'item dans les listes triees. Sur un petit serveur ou le rebuild coute 2 ms, on remplace un comportement instantane par un delai. QUESTION : acceptez-vous ce compromis, et sur quelle valeur par defaut ? Le pas 21 (C-101) rend d'ailleurs les rebuilds PLUS frequents sous charge : si vous constatez une tempete en production apres ce lot, c'est le signal pour trancher.
- POINT 2 DE C-056 (« ne pas attendre le rebuild pour rafraichir la GUI ») — Je l'ai REJETE : il supprime le `ensureCacheValidAsync().thenRun(...)` que le commentaire de ZAuctionManager:951-952 dit avoir ete ajoute DELIBEREMENT pour corriger un bug de cache perime. QUESTION : ce bug d'origine est-il retracable (commit, ticket) ? Sans cette information, personne ne peut evaluer le risque a le supprimer.
- SECTION `admin-generate` MORTE (C-115, partie a) — La section existe, complete et traduite, dans les SIX config.yml (l.1115-1140+ : expiration FIXED/RANDOM, prix, bulk-items, economies) et n'est LUE NULLE PART : le generateur code en dur 24 h d'expiration et un prix aleatoire. Deux issues, et c'est votre arbitrage : (a) l'implementer via un record AdminGenerateConfiguration et une methode `default` sur l'interface publiee Configuration (~75 LOC, non cassant grace au default, mais les sous-sections bulk-items et economies resteront mortes car le generateur ne sait produire que des annonces mono-item) ; (b) SUPPRIMER la section des six fichiers. Ne pas trancher, c'est conserver une configuration mensongere.
- MIGRATION DE L'ADDON VERS `getItem(StorageType, int)` — Le pas 19 ajoute l'accesseur O(1) mais je ne l'utilise PAS dans l'addon, pour ne pas exiger un bump du pin `zauctionhousev4-api:deb8f16`. Or `ItemStatusListener:45` fait aujourd'hui jusqu'a TROIS copies completes du store plus trois scans lineaires PAR MESSAGE DE STATUT RECU, sur le thread principal. QUESTION : acceptez-vous une release coordonnee (republier zauctionhousev4-api sur repo.groupez.dev, puis bumper le pin de l'addon) pour recuperer ce gain ? Le meme bump servirait a C-050, C-046, C-092 (chantier 7).
- ALERTE DE VERSION DE L'ADDON (pas 13) — Le controle est inoperant depuis toujours : tous les serveurs sur un addon obsolete vont brusquement se mettre a recevoir l'alerte au premier deploiement. QUESTION : voulez-vous publier une version de l'addon marquee comme la derniere AVANT ce deploiement, pour eviter un pic de tickets support ?
- COMPTEURS DE CATEGORIE A CLE MAJUSCULE (pas 23) — Un serveur dont categories.yml declare une categorie `Weapons` (majuscule) affiche aujourd'hui 0 et affichera desormais le vrai nombre. C'est une correction, mais c'est un changement visible par les joueurs. QUESTION : le mentionner en tete de changelog, ou considerer que la configuration livree (toutes clefs minuscules) rend le cas marginal ?
- INDEX COMPOSITES — Sarah 1.23 ne sait creer que des index MONO-COLONNE (Migration.index(table, column)). Les deux index reellement optimaux — items(seller_unique_id, storage_type) pour le recomptage de C-096 et transactions(player_unique_id, status) pour ClaimService — ne sont pas exprimables. QUESTION : acceptez-vous de patcher Sarah (ajouter des index composites ET `IF NOT EXISTS`, qui reglerait du meme coup le risque de non-demarrage du pas 17), sachant que Sarah est partagee par ~15 autres plugins du workspace ?
- MOITIE MANQUANTE DE C-006 (pas 5) — Le pas ferme la fenetre de LECTURE mais laisse le `.exceptionally` de PurchaseService:176 restaurer AVAILABLE et le REDIFFUSER au cluster pour un item qu'on vient de constater vendu ailleurs — comportement deja present aujourd'hui sur la branche `dbItem == null`, que ce pas rend simplement plus frequent. La reconciliation appartient a C-035 (chantier 7). QUESTION : acceptez-vous de livrer le pas 5 avant C-035, sachant qu'il transforme une duplication d'item en une desynchronisation d'affichage reversible ? Mon avis : oui, sans hesiter.

### Retour arrière

"REVERSIBILITE PAR DOWNGRADE DE JAR pour 22 pas sur 23. Les pas 1 a 16 et 18 a 23 ne touchent ni au schema ni au format des donnees persistees : reinstaller le jar precedent suffit, sans aucune action base. Aucun pas n'ecrit un format de donnees nouveau (le prefixe 'V2:' de C-029 appartient au chantier 6 et n'est PAS ici) : il n'y a donc pas de deploiement en deux phases a orchestrer.\n\nPAS 17 (index) — SEUL PAS NON REVERSIBLE PAR DOWNGRADE. Un downgrade de jar laisse les index en place, ce qui est INOFFENSIF : ils accelerent les memes requetes et l'ancien code ne les connait pas. Ne rien faire est la bonne reponse dans 99 % des cas. Si un index doit reellement etre retire (contention d'ecriture sur un serveur a tres fort volume d'insertion), la procedure est, PLUGIN ARRETE :\n  DROP INDEX idx_<prefix>items_storage_type ON <prefix>items;            -- MySQL/MariaDB\n  DROP INDEX idx_<prefix>items_storage_type;                              -- SQLite\n  DELETE FROM zauctionhousev4_migrations WHERE migration_name = 'CreateItemsStorageTypeIndexMigration';\n(idem pour les trois autres classes). Les deux instructions vont ENSEMBLE : supprimer l'index sans supprimer la ligne de migration le laisse definitivement absent ; supprimer la ligne sans supprimer l'index fait ECHOUER LE DEMARRAGE au boot suivant, puisque Sarah emet CREATE INDEX sans IF NOT EXISTS et que MigrationManager relance l'exception.\n\nPAS 14 (contrat LocalAuctionClusterBridge) — a retirer EN MEME TEMPS que C-042 si l'on revient en arriere : garder C-042 sans le pas 14 est inoffensif, mais garder le pas 14 sans C-042 rend le retrait admin permissif en mono-serveur. En cas de doute, revert du pas 14 seul (il est confine a un fichier de 40 lignes).\n\nPAS 11 (CAS de statut, addon) — si un item se fige durablement en IS_*_CONFIRM sur un noeud apres deploiement, le symptome signe l'absence de C-072. Remede immediat sans redeploiement : `/ah admin cache clear <joueur> ITEM_SHOW` NE convient PAS (il aggrave, cf. C-065) ; redemarrer le noeud concerne recharge les statuts depuis storage_type et les remet a AVAILABLE. Remede definitif : livrer C-072.\n\nPAS 23 (compteurs) — le seul effet visible d'un revert est le retour des compteurs O(n) et le retour a 0 pour les categories a cle majuscule. Aucune donnee n'est en jeu.\n\nSERVEUR DE PRODUCTION QUI CASSE AU DEMARRAGE apres ce lot : la seule cause plausible est le pas 17. Le message sera `Create index operation failed on table: ...` suivi d'une DatabaseException dans onEnable. Appliquer la procedure DROP INDEX + DELETE ci-dessus, ou repartir sur le jar precedent, qui n'enregistre pas ces migrations."


---

