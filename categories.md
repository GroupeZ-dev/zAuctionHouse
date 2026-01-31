# Système de Catégories pour zAuctionHouseV4

## Document de Conception et Spécification

**Version**: 1.0
**Date**: Janvier 2026
**Auteur**: Analyse comparative et conception technique

---

## Table des Matières

1. [Contexte et Objectifs](#1-contexte-et-objectifs)
2. [Méthodologie de Recherche](#2-méthodologie-de-recherche)
3. [Tableau Comparatif des Plugins](#3-tableau-comparatif-des-plugins)
4. [Analyse Détaillée par Plugin](#4-analyse-détaillée-par-plugin)
5. [Synthèse : Patterns Gagnants et Pièges à Éviter](#5-synthèse--patterns-gagnants-et-pièges-à-éviter)
6. [Spécification pour zAuctionHouseV4](#6-spécification-pour-zauctionhousev4)
7. [Proposition de Configuration](#7-proposition-de-configuration)
8. [Plan de Migration V3 → V4](#8-plan-de-migration-v3--v4)
9. [Checklist d'Implémentation](#9-checklist-dimplémentation)
10. [Annexes](#10-annexes)

---

## 1. Contexte et Objectifs

### 1.1 Contexte

zAuctionHouseV4 est une refonte majeure du plugin d'hôtel des ventes Minecraft le plus utilisé. Le système de catégories est une fonctionnalité centrale permettant aux joueurs de naviguer efficacement parmi potentiellement des milliers d'items mis en vente.

Le système actuel de zAuctionHouseV3 présente plusieurs limitations :
- Correspondance uniquement basée sur Material, nom, lore et customModelData
- Pas de support natif pour les items custom (ItemsAdder, Oraxen, MMOItems)
- Cache à TTL fixe de 30 secondes (non configurable)
- Pas de permissions par catégorie
- Bug identifié dans `getMiscellaneous()` (logique inversée)

### 1.2 Objectifs

**Pour les joueurs :**
- Navigation rapide et intuitive
- Filtrage multi-critères efficace
- Recherche textuelle performante
- Catégories "intelligentes" (nouveautés, fin proche, etc.)

**Pour les administrateurs :**
- Configuration simple et lisible
- Extensibilité sans casser l'existant
- Compatibilité items custom (ItemsAdder, Oraxen, MMOItems, NeigeItems)
- Validation avec messages d'erreur clairs
- Support multi-langue natif

---

## 2. Méthodologie de Recherche

### 2.1 Plateformes Analysées

| Plateforme | Méthode | Résultats |
|------------|---------|-----------|
| **Modrinth** | Recherche web + pages produit | 7 plugins identifiés |
| **SpigotMC** | Recherche web + pages ressources | 6 plugins identifiés |
| **BuiltByBit** | Recherche web + pages produit | 5 plugins identifiés |
| **GitHub** | Recherche web + analyse code source | 4 repos analysés |
| **zAuctionHouseV3** | Analyse code source complète | 396 lignes analysées |

### 2.2 Critères d'Analyse

Pour chaque plugin, les éléments suivants ont été collectés :
- Fonctionnalités liées aux catégories
- Structure de configuration (format, fichiers)
- Règles de matching (materials, noms, NBT, etc.)
- UX joueur (navigation, filtres, recherche)
- UX admin (configuration, permissions, placeholders)
- Limitations et problèmes identifiés

### 2.3 Sources Consultées

- Documentation officielle des plugins
- Pages de description (Spigot/BBB/Modrinth)
- Dépôts GitHub (code source)
- Fichiers de configuration par défaut
- Issues et discussions communautaires
- Code source de zAuctionHouseV3 (`D:\Users\Maxlego08\workspace2.0\[Spigot] zAuctionHouseV3`)

---

## 3. Tableau Comparatif des Plugins

### 3.1 Matrice des Fonctionnalités

| Plugin | Catégories | Sous-cat. | Règles Auto | Items Custom | Permissions | Config Format |
|--------|------------|-----------|-------------|--------------|-------------|---------------|
| **zAuctionHouseV3** | ✅ | ❌ | ✅ | Partiel | ❌ | YAML unique |
| **Fadah** | ✅ | ❌ | ✅ | ✅ (%model%) | ❌ | YAML + menus/ |
| **AuctionHouse (kiranhart)** | ✅ | ❌ | ✅ | ❌ | ❌ | categories/*.yml |
| **AdvancedAuctionHouse** | ✅ | ❌ | ✅ | ✅ | ❌ | config.yml |
| **AuctionMaster** | ✅ | ❌ | ✅ | ✅ | ❌ | Multi-fichiers |
| **AxAuctions** | ✅ (opt) | ❌ | ✅ | ✅ | ❌ | YAML + wiki |

### 3.2 Matrice UX et Configuration

| Plugin | Recherche | Tri | GUI Custom | Multi-langue | Cache | Prix |
|--------|-----------|-----|------------|--------------|-------|------|
| **zAuctionHouseV3** | ✅ | ✅ | ✅ (zMenu) | ✅ | TTL 30s | Premium |
| **Fadah** | ✅ | ✅ | ✅ | ✅ | Inconnu | Gratuit |
| **AuctionHouse (kiranhart)** | ✅ | ✅ | Limité | ✅ | Inconnu | Gratuit |
| **AdvancedAuctionHouse** | ✅ | ✅ | ✅ | ✅ | Inconnu | Premium |
| **AuctionMaster** | ✅ | ✅ | ✅ | ✅ | Inconnu | Gratuit |
| **AxAuctions** | ✅ | ✅ | ✅ | ✅ | Inconnu | Premium |

---

## 4. Analyse Détaillée par Plugin

### 4.1 zAuctionHouseV3 (Analyse Approfondie)

**Source**: `D:\Users\Maxlego08\workspace2.0\[Spigot] zAuctionHouseV3`

#### Architecture

```
fr.maxlego08.zauctionhouse/
├── api/category/
│   ├── Category.java           # Interface
│   ├── CategoryItem.java       # Interface matching
│   └── CategoryManager.java    # Interface manager
├── categories/
│   ├── ZCategory.java          # Implémentation
│   ├── ZCategoryItem.java      # Règles de matching
│   └── ZCategoryManager.java   # Manager (396 lignes)
└── buttons/
    ├── ZCategoryButton.java
    ├── ZCategoriesButton.java
    └── ZCategoriesHomeButton.java
```

#### Règles de Matching (ZCategoryItem.java:60-116)

```java
// Ordre des vérifications (toutes doivent passer - logique AND)
1. Material != null → itemStack.getType() == material
2. Data != 0 → itemStack.getData().getData() == data
3. Name != null → displayName.contains(name) // substring
4. LoreKey != null → anyLoreLine.contains(loreKey)
5. CustomModelData != 0 → itemMeta.getCustomModelData() == modelData
```

#### Configuration (categories.yml)

```yaml
categories:
  blocks:
    name: "Blocks"
    materials:
      - material: GRASS_BLOCK
      - material: STONE

  weapons:
    name: "Weapons"
    materials:
      - material: DIAMOND_SWORD
      - material: IRON_SWORD
        name: "Special"  # Optionnel: filtre par nom

  custom_items:
    name: "Custom"
    materials:
      - material: PAPER
        modelId: 2500   # CustomModelData

  misc:
    name: "Misc"
    # Pas de materials = catégorie fourre-tout
```

#### Points Forts
- Intégration native avec zMenu
- Structure simple et compréhensible
- Support CustomModelData pour items custom basiques
- Catégorie "misc" automatique comme fallback

#### Limitations Identifiées
1. **Bug getMiscellaneous()** : Logique inversée (retourne items avec catégories au lieu de sans)
2. **Cache TTL fixe** : 30 secondes hardcodé, non configurable
3. **Pas de sous-catégories**
4. **Pas de permissions par catégorie**
5. **Matching substring uniquement** : Pas de regex, pas d'equals exact
6. **Pas de support natif** : ItemsAdder, Oraxen, MMOItems

---

### 4.2 Fadah (Finally a Decent Auction House)

**Sources**:
- [GitHub](https://github.com/Finally-A-Decent/Fadah)
- [Documentation](https://docs.preva1l.info/fadah/)
- [Modrinth](https://modrinth.com/plugin/fadah)

#### Points Clés

- **Catégories infinies et configurables**
- **Support %model%** : Utilise la clé namespaced du model data (`namespace:key`)
- **Items exclus automatiquement** si non présents dans categories.yml
- **Structure multi-fichiers** : `database/`, `menus/`, etc.

#### Fonctionnalités Notables
- Filtrage custom par items
- GUI layouts personnalisables
- Redis support pour multi-serveur
- Transaction logging

#### Feedback Communautaire
> "Can you please make a separate menu for the categories? Because then I could freely customise the category menu and have more space."
> — Utilisateur SpigotMC

---

### 4.3 AuctionHouse (kiranhart)

**Source**: [GitHub](https://github.com/kiranhart/Auction-House)

#### Structure Configuration

```
src/main/resources/
├── config.yml
├── plugin.yml
└── categories/
    └── food.yml
```

#### Format Catégorie (food.yml)

```yaml
attribute-conditions:
  NAME:
    EQUALS:
      - "Item Name"
    CONTAINS:
      - "partial"
  LORE:
    CONTAINS:
      - "rarity"
  MATERIAL:
    EQUALS:
      - COOKED_BEEF
      - GOLDEN_APPLE
```

#### Points Forts
- Système d'attributs conditions flexible
- Support EQUALS et CONTAINS séparés
- Fichiers séparés par catégorie (scalabilité)
- Open source (MIT-like)

#### Limitations
- Documentation limitée
- Support items custom non documenté

---

### 4.4 AdvancedAuctionHouse

**Source**: [AdvancedPlugins](https://advancedplugins.net/item/AdvancedAuctionHouse.4)

#### Points Clés

- **Smart Filter System** : Configurable dans config.yml
- **Filtres avancés** : NAME contains/equals, LORE contains/equals
- **Exemple spawners** : Peut filtrer "MOB_SPAWNER · Pig" (spawner spécifique)
- **Mode sans catégories** : Peut désactiver pour AH simple

#### Configuration

```yaml
# Génère materials.txt avec liste des matériaux compatibles
# pour la version Minecraft utilisée
```

#### Points Forts
- Fichier materials.txt auto-généré (UX admin)
- Filtres conditionnels avancés
- Désactivation catégories possible

---

### 4.5 AuctionMaster

**Source**: [GitHub](https://github.com/qKing12/AuctionMaster) (outdated)

#### Structure Multi-Fichiers

```
├── config.yml          # Config principale
├── armor.yml           # Catégorie Armor
├── weapons.yml         # Catégorie Weapons
├── tools.yml           # Catégorie Tools
├── consumables.yml     # Catégorie Consumables
├── blocks.yml          # Catégorie Blocks
├── others.yml          # Catégorie Others
└── menus.yml           # Configuration menus
```

#### Format Catégorie (weapons.yml)

```yaml
weapons-menu-item: '283'          # ID item pour icône
weapons-menu-name: '§6Weapons'    # Nom affiché
weapons-menu-lore:
  - '§8Category'
  - ''
  - '§7Examples:'
  - '§8■ §7Swords'
  - '§8■ §7Bows'

# Items custom par nom ou ID
custom-item-names:
  - '§e§lExample §6§lItem'

custom-item-ids:
  - '2266'
```

#### Points Forts
- Fichiers séparés par catégorie
- Deux méthodes d'ajout custom (nom OU id)
- Couleurs dans config supportées

#### Limitations
- Projet marqué OUTDATED
- Structure rigide (1 fichier = 1 catégorie)

---

### 4.6 AxAuctions

**Sources**:
- [Documentation](https://docs.artillex-studios.com/axauctions.html)
- [BuiltByBit](https://builtbybit.com/resources/axauctions-all-in-one-auction-plugin.40242/)

#### Points Clés

- **Catégories optionnelles** (désactivables)
- **Filtrage par** : nom, material, custom model data
- **Blacklist reworked** : Support regex basique
- **Multi-currency** : Joueurs choisissent la devise

#### Configuration
- Exemple commenté dans `main-gui.yml` pour ajouter category selector
- Config bien organisée et facile à traduire

---

## 5. Synthèse : Patterns Gagnants et Pièges à Éviter

### 5.1 Patterns Gagnants

#### A. Structure de Configuration

| Pattern | Plugins l'utilisant | Recommandation |
|---------|---------------------|----------------|
| **Fichiers séparés par catégorie** | AuctionMaster, kiranhart | ✅ Recommandé pour scalabilité |
| **Dossier categories/** | kiranhart | ✅ Organisation claire |
| **Config unique** | zAuctionHouseV3 | ⚠️ OK si peu de catégories |

**Recommandation V4** : Dossier `categories/` avec fichiers YAML auto-chargés + fichier `categories.yml` pour config globale.

#### B. Règles de Matching

| Pattern | Description | Recommandation |
|---------|-------------|----------------|
| **EQUALS vs CONTAINS** | Distinction matching exact/partiel | ✅ Essentiel |
| **Multi-attributs AND** | Tous les critères doivent matcher | ✅ Par défaut |
| **Multi-attributs OR** | Au moins un critère | ✅ Option à ajouter |
| **Support regex** | Patterns complexes | ⚠️ Optionnel (avancé) |
| **Namespace:key** | Items custom modernes | ✅ Essentiel pour ItemsAdder/Oraxen |

#### C. UX Joueur

| Pattern | Description | Recommandation |
|---------|-------------|----------------|
| **Catégorie "Tous"** | Vue globale sans filtre | ✅ Essentiel |
| **Catégorie "Misc"** | Fallback items non classés | ✅ Essentiel |
| **Compteur items** | `%category_count%` | ✅ Essentiel |
| **Recherche textuelle** | Filtre par nom/lore | ✅ Essentiel |
| **Tri multiple** | Prix, date, quantité | ✅ Essentiel |

#### D. UX Admin

| Pattern | Description | Recommandation |
|---------|-------------|----------------|
| **materials.txt auto-généré** | Liste matériaux version | ✅ Excellent QoL |
| **Validation config** | Messages erreur clairs | ✅ Essentiel |
| **Hot-reload** | `/ah reload` sans restart | ✅ Essentiel |
| **Commentaires YAML** | Documentation inline | ✅ Essentiel |

### 5.2 Pièges à Éviter

| Piège | Plugin concerné | Impact | Solution |
|-------|-----------------|--------|----------|
| **Cache TTL hardcodé** | zAuctionHouseV3 | Performance imprévisible | Rendre configurable |
| **Logique inversée** | zAuctionHouseV3 getMiscellaneous() | Bug fonctionnel | Tests unitaires |
| **Pas de validation** | Plusieurs | Configs invalides silencieuses | Validation au load |
| **1 fichier = 1 catégorie** | AuctionMaster | Rigidité | Permettre multiples par fichier |
| **Pas de fallback** | Certains | Items perdus | Catégorie misc obligatoire |

### 5.3 Fonctionnalités Différenciantes à Implémenter

1. **Catégories Dynamiques**
   - `@recent` : Items listés dans les dernières 24h
   - `@ending-soon` : Items expirant dans <1h
   - `@my-listings` : Mes ventes en cours
   - `@favorites` : Items favoris (si système favorites)

2. **Support Items Custom Natif**
   - ItemsAdder : `itemsadder:<namespace>:<id>`
   - Oraxen : `oraxen:<id>`
   - MMOItems : `mmoitems:<type>:<id>`
   - NeigeItems : `neigeitems:<id>`

3. **Héritage de Catégories**
   - `extends: parent_category` pour réduire duplication

4. **Conditions Avancées**
   - Enchantements spécifiques
   - Niveau d'enchantement min/max
   - NBT tags custom

---

## 6. Spécification pour zAuctionHouseV4

### 6.1 Exigences Fonctionnelles

#### Must-Have (P0)

| ID | Fonctionnalité | Description |
|----|----------------|-------------|
| F01 | Catégories manuelles | Admin définit catégories via YAML |
| F02 | Règles auto-classification | Items assignés par règles |
| F03 | Catégorie fallback | "Misc" pour items non classés |
| F04 | Catégorie globale | "Tous" affiche tout |
| F05 | Matching Material | Par type Minecraft |
| F06 | Matching Nom | EQUALS et CONTAINS |
| F07 | Matching Lore | EQUALS et CONTAINS |
| F08 | Matching CustomModelData | Par valeur numérique |
| F09 | Recherche textuelle | Dans main GUI |
| F10 | Tri configurable | Prix, date, quantité |
| F11 | Compteur par catégorie | Placeholder %count% |
| F12 | GUI personnalisable | Via zMenu YAML |

#### Should-Have (P1)

| ID | Fonctionnalité | Description |
|----|----------------|-------------|
| F13 | Support ItemsAdder | `itemsadder:namespace:id` |
| F14 | Support Oraxen | `oraxen:id` |
| F15 | Permissions catégorie | `zauctionhouse.category.<id>` |
| F16 | Catégories dynamiques | @recent, @ending-soon, etc. |
| F17 | Héritage extends | Réduire duplication config |
| F18 | Opérateur OR | Alternative au AND par défaut |
| F19 | Hot-reload | Sans restart serveur |
| F20 | Validation config | Messages erreur clairs |

#### Nice-to-Have (P2)

| ID | Fonctionnalité | Description |
|----|----------------|-------------|
| F21 | Sous-catégories | Profondeur configurable |
| F22 | Support regex | Patterns nom/lore avancés |
| F23 | Support MMOItems | `mmoitems:type:id` |
| F24 | Support NeigeItems | `neigeitems:id` |
| F25 | Matching enchantements | Par enchant + niveau |
| F26 | Catégories événements | Temporaires, schedulées |
| F27 | materials.txt auto | Liste matériaux version |

### 6.2 Exigences Non-Fonctionnelles

| ID | Exigence | Critère |
|----|----------|---------|
| NF01 | Performance | <50ms pour 10000 items, 20 catégories |
| NF02 | Cache configurable | TTL paramétrable |
| NF03 | Invalidation intelligente | Sur ajout/suppression item |
| NF04 | Backward compatible | Migration V3 automatique |
| NF05 | Config lisible | Commentaires, exemples |
| NF06 | Extensible | Ajout catégories sans casser |
| NF07 | Testable | API claire pour tests |

---

## 7. Proposition de Configuration

### 7.1 Structure de Fichiers Recommandée

```
plugins/zAuctionHouse/
├── config.yml                 # Config principale
├── categories.yml             # Config globale catégories
├── categories/                # Dossier catégories (auto-chargé)
│   ├── blocks.yml
│   ├── weapons.yml
│   ├── tools.yml
│   ├── consumables.yml
│   └── custom-items.yml       # Items custom serveur
├── inventories/
│   ├── categories.yml         # Menu liste catégories
│   └── category.yml           # Menu items d'une catégorie
└── messages.yml
```

### 7.2 Modèle de Données Principal (categories.yml)

```yaml
# ╔═══════════════════════════════════════════════════════════════╗
# ║           zAuctionHouseV4 - Configuration Catégories          ║
# ╠═══════════════════════════════════════════════════════════════╣
# ║  Documentation: https://zauctionhouse.groupez.dev/categories  ║
# ╚═══════════════════════════════════════════════════════════════╝

# Version de configuration (pour migrations futures)
config-version: 1

# ┌─────────────────────────────────────────────────────────────────┐
# │                    PARAMÈTRES GLOBAUX                           │
# └─────────────────────────────────────────────────────────────────┘

settings:
  # Activer le système de catégories (false = AH simple sans catégories)
  enabled: true

  # Durée du cache en millisecondes (0 = pas de cache)
  cache-ttl: 30000

  # Invalider le cache à chaque modification (ajout/suppression item)
  # Recommandé: true pour cohérence temps réel
  invalidate-on-change: true

  # Catégorie par défaut si item ne match aucune règle
  # Laisser vide pour rejeter les items non classifiables
  default-category: "misc"

  # Permettre aux items d'appartenir à plusieurs catégories
  allow-multiple-categories: false

  # Ordre de priorité pour le matching (première catégorie qui match)
  # Si allow-multiple-categories: true, cet ordre détermine l'affichage principal
  priority-order:
    - "custom-items"    # Items custom en priorité
    - "weapons"
    - "armor"
    - "tools"
    - "blocks"
    - "consumables"
    - "misc"            # Toujours en dernier

# ┌─────────────────────────────────────────────────────────────────┐
# │                   CATÉGORIES DYNAMIQUES                         │
# └─────────────────────────────────────────────────────────────────┘

dynamic-categories:
  # Catégorie "Tous" - affiche tous les items
  all:
    enabled: true
    display-name: "&eAll Items"
    icon: CHEST
    slot: 4

  # Items récemment listés (dernières 24h)
  recent:
    enabled: true
    display-name: "&aNew Listings"
    icon: CLOCK
    slot: 5
    max-age-hours: 24

  # Items expirant bientôt
  ending-soon:
    enabled: true
    display-name: "&cEnding Soon"
    icon: HOPPER
    slot: 6
    hours-remaining: 1

# ┌─────────────────────────────────────────────────────────────────┐
# │              INTÉGRATIONS ITEMS CUSTOM                          │
# └─────────────────────────────────────────────────────────────────┘

custom-items-support:
  # ItemsAdder integration
  itemsadder:
    enabled: true
    # Prefix pour les rules: itemsadder:namespace:id

  # Oraxen integration
  oraxen:
    enabled: true
    # Prefix pour les rules: oraxen:item_id

  # MMOItems integration
  mmoitems:
    enabled: false
    # Prefix pour les rules: mmoitems:TYPE:ID

  # NeigeItems integration
  neigeitems:
    enabled: false
    # Prefix pour les rules: neigeitems:item_id
```

### 7.3 Format Catégorie Individuelle

#### Exemple Minimal (categories/blocks.yml)

```yaml
# ╔═════════════════════════════════════════════╗
# ║        Catégorie: Blocks                    ║
# ╚═════════════════════════════════════════════╝

blocks:
  # Nom affiché dans les menus
  display-name: "&7Blocks"

  # Icône de la catégorie
  icon:
    material: GRASS_BLOCK
    # custom-model-data: 0  # Optionnel

  # Position dans le menu catégories (slot GUI)
  slot: 10

  # Ordre de tri (plus petit = plus prioritaire)
  priority: 100

  # Règles de matching (un item match si AU MOINS UNE règle est satisfaite)
  rules:
    # Règle simple: tous les blocs Minecraft
    - type: material-tag
      tag: BLOCKS

    # OU règles spécifiques
    - type: material
      materials:
        - GRASS_BLOCK
        - DIRT
        - STONE
        - COBBLESTONE
```

#### Exemple Avancé (categories/weapons.yml)

```yaml
# ╔═════════════════════════════════════════════╗
# ║        Catégorie: Weapons                   ║
# ╚═════════════════════════════════════════════╝

weapons:
  display-name: "&cWeapons"
  description:
    - "&7All combat weapons"
    - "&7Swords, bows, crossbows..."

  icon:
    material: DIAMOND_SWORD
    glow: true  # Effet enchant

  slot: 11
  priority: 10

  # Permission requise pour voir cette catégorie (optionnel)
  # permission: "zauctionhouse.category.weapons"

  rules:
    # Règle 1: Toutes les épées
    - type: material
      materials:
        - WOODEN_SWORD
        - STONE_SWORD
        - IRON_SWORD
        - GOLDEN_SWORD
        - DIAMOND_SWORD
        - NETHERITE_SWORD

    # Règle 2: Arcs et arbalètes
    - type: material
      materials:
        - BOW
        - CROSSBOW

    # Règle 3: Tridents
    - type: material
      materials:
        - TRIDENT

    # Règle 4: Items avec "Sword" dans le nom (pour items custom)
    - type: name
      mode: CONTAINS  # EQUALS ou CONTAINS
      values:
        - "Sword"
        - "Blade"
      ignore-case: true
      strip-colors: true

# ─────────────────────────────────────────────────
# Sous-catégorie: Épées Légendaires
# ─────────────────────────────────────────────────
legendary-weapons:
  display-name: "&6&lLegendary Weapons"
  description:
    - "&7Rare and powerful weapons"
    - "&7Found in dungeons..."

  icon:
    material: NETHERITE_SWORD
    glow: true
    custom-model-data: 1001

  slot: 12
  priority: 5

  # Hérite des règles de 'weapons' + ajoute des conditions
  extends: weapons

  # Conditions additionnelles (AND avec règles héritées)
  additional-conditions:
    # Doit avoir "Legendary" dans le lore
    - type: lore
      mode: CONTAINS
      values:
        - "Legendary"
        - "Légendaire"
      ignore-case: true
```

#### Exemple Items Custom (categories/custom-items.yml)

```yaml
# ╔═════════════════════════════════════════════╗
# ║     Catégorie: Items Custom Serveur         ║
# ╚═════════════════════════════════════════════╝

custom-items:
  display-name: "&d&lCustom Items"
  description:
    - "&7Special server items"
    - "&7Created with ItemsAdder/Oraxen"

  icon:
    material: NETHER_STAR
    glow: true

  slot: 13
  priority: 1  # Haute priorité pour matcher avant les catégories vanilla

  rules:
    # ─────────────────────────────────────────
    # ItemsAdder items
    # ─────────────────────────────────────────
    - type: itemsadder
      items:
        - "myserver:legendary_sword"
        - "myserver:magic_wand"
        - "myserver:*"  # Wildcard: tous les items du namespace

    # ─────────────────────────────────────────
    # Oraxen items
    # ─────────────────────────────────────────
    - type: oraxen
      items:
        - "obsidian_sword"
        - "emerald_armor_*"  # Wildcard

    # ─────────────────────────────────────────
    # Par CustomModelData (fallback générique)
    # ─────────────────────────────────────────
    - type: custom-model-data
      ranges:
        - min: 1000
          max: 9999

    # ─────────────────────────────────────────
    # Par namespace du model (Minecraft 1.21+)
    # ─────────────────────────────────────────
    - type: item-model
      models:
        - "myserver:custom_sword"
        - "myserver:*"

# ─────────────────────────────────────────────────
# Catégorie spécifique: Armes ItemsAdder
# ─────────────────────────────────────────────────
itemsadder-weapons:
  display-name: "&5IA Weapons"

  icon:
    # Utiliser un item ItemsAdder comme icône
    itemsadder: "myserver:legendary_sword"

  slot: 14
  priority: 2

  rules:
    - type: itemsadder
      items:
        - "myserver:*_sword"
        - "myserver:*_blade"

    # ET doit avoir ce tag NBT (condition AND)
    - type: nbt
      mode: AND  # Cette règle s'ajoute aux précédentes
      conditions:
        - path: "custom.weapon_type"
          value: "melee"
```

### 7.4 Exemple Configuration Complète (10+ catégories)

```yaml
# ╔═══════════════════════════════════════════════════════════════╗
# ║       EXEMPLE COMPLET - Configuration Serveur Survie          ║
# ╚═══════════════════════════════════════════════════════════════╝

# ┌─────────────────────────────────────────────────────────────────┐
# │                      BLOCS DE CONSTRUCTION                      │
# └─────────────────────────────────────────────────────────────────┘
blocks:
  display-name: "&7Blocks"
  icon:
    material: GRASS_BLOCK
  slot: 10
  priority: 100
  rules:
    - type: material-tag
      tag: BLOCKS

# ┌─────────────────────────────────────────────────────────────────┐
# │                           ARMES                                 │
# └─────────────────────────────────────────────────────────────────┘
weapons:
  display-name: "&cWeapons"
  icon:
    material: DIAMOND_SWORD
  slot: 11
  priority: 10
  rules:
    - type: material
      materials:
        - WOODEN_SWORD
        - STONE_SWORD
        - IRON_SWORD
        - GOLDEN_SWORD
        - DIAMOND_SWORD
        - NETHERITE_SWORD
        - BOW
        - CROSSBOW
        - TRIDENT

# ┌─────────────────────────────────────────────────────────────────┐
# │                          ARMURES                                │
# └─────────────────────────────────────────────────────────────────┘
armor:
  display-name: "&bArmor"
  icon:
    material: DIAMOND_CHESTPLATE
  slot: 12
  priority: 20
  rules:
    - type: material-suffix
      suffixes:
        - "_HELMET"
        - "_CHESTPLATE"
        - "_LEGGINGS"
        - "_BOOTS"
    - type: material
      materials:
        - ELYTRA
        - SHIELD
        - TURTLE_HELMET

# ┌─────────────────────────────────────────────────────────────────┐
# │                           OUTILS                                │
# └─────────────────────────────────────────────────────────────────┘
tools:
  display-name: "&eTools"
  icon:
    material: DIAMOND_PICKAXE
  slot: 13
  priority: 30
  rules:
    - type: material-suffix
      suffixes:
        - "_PICKAXE"
        - "_AXE"
        - "_SHOVEL"
        - "_HOE"
    - type: material
      materials:
        - SHEARS
        - FLINT_AND_STEEL
        - FISHING_ROD
        - BRUSH

# ┌─────────────────────────────────────────────────────────────────┐
# │                        CONSOMMABLES                             │
# └─────────────────────────────────────────────────────────────────┘
consumables:
  display-name: "&aConsumables"
  icon:
    material: GOLDEN_APPLE
  slot: 14
  priority: 40
  rules:
    - type: material-tag
      tag: EDIBLE
    - type: material
      materials:
        - POTION
        - SPLASH_POTION
        - LINGERING_POTION
        - EXPERIENCE_BOTTLE
        - ENDER_PEARL

# ┌─────────────────────────────────────────────────────────────────┐
# │                         RESSOURCES                              │
# └─────────────────────────────────────────────────────────────────┘
resources:
  display-name: "&6Resources"
  icon:
    material: DIAMOND
  slot: 15
  priority: 50
  rules:
    - type: material
      materials:
        - COAL
        - IRON_INGOT
        - GOLD_INGOT
        - DIAMOND
        - EMERALD
        - NETHERITE_INGOT
        - LAPIS_LAZULI
        - REDSTONE
        - COPPER_INGOT
        - AMETHYST_SHARD

# ┌─────────────────────────────────────────────────────────────────┐
# │                          REDSTONE                               │
# └─────────────────────────────────────────────────────────────────┘
redstone:
  display-name: "&4Redstone"
  icon:
    material: REDSTONE
  slot: 16
  priority: 60
  rules:
    - type: material
      materials:
        - REDSTONE
        - REDSTONE_BLOCK
        - REDSTONE_TORCH
        - REPEATER
        - COMPARATOR
        - PISTON
        - STICKY_PISTON
        - OBSERVER
        - HOPPER
        - DROPPER
        - DISPENSER
        - LEVER
        - TRIPWIRE_HOOK
        - DAYLIGHT_DETECTOR

# ┌─────────────────────────────────────────────────────────────────┐
# │                         DÉCORATION                              │
# └─────────────────────────────────────────────────────────────────┘
decoration:
  display-name: "&dDecoration"
  icon:
    material: PAINTING
  slot: 19
  priority: 70
  rules:
    - type: material
      materials:
        - PAINTING
        - ITEM_FRAME
        - GLOW_ITEM_FRAME
        - ARMOR_STAND
        - FLOWER_POT
        - CANDLE
    - type: material-contains
      patterns:
        - "BANNER"
        - "CARPET"
        - "BED"
        - "CANDLE"

# ┌─────────────────────────────────────────────────────────────────┐
# │                       SPAWN EGGS                                │
# └─────────────────────────────────────────────────────────────────┘
spawn-eggs:
  display-name: "&5Spawn Eggs"
  icon:
    material: PIG_SPAWN_EGG
  slot: 20
  priority: 80
  rules:
    - type: material-suffix
      suffixes:
        - "_SPAWN_EGG"
    - type: material
      materials:
        - SPAWNER

# ┌─────────────────────────────────────────────────────────────────┐
# │                      ENCHANTED BOOKS                            │
# └─────────────────────────────────────────────────────────────────┘
enchanted-books:
  display-name: "&bEnchanted Books"
  icon:
    material: ENCHANTED_BOOK
    glow: true
  slot: 21
  priority: 15
  rules:
    - type: material
      materials:
        - ENCHANTED_BOOK

# ┌─────────────────────────────────────────────────────────────────┐
# │                    ITEMS CUSTOM SERVEUR                         │
# └─────────────────────────────────────────────────────────────────┘
custom-items:
  display-name: "&d&lCustom Items"
  icon:
    material: NETHER_STAR
    glow: true
  slot: 22
  priority: 1
  rules:
    - type: custom-model-data
      ranges:
        - min: 1000
          max: 99999
    - type: itemsadder
      items:
        - "*:*"  # Tous les items ItemsAdder

# ┌─────────────────────────────────────────────────────────────────┐
# │                      MISCELLANEOUS                              │
# └─────────────────────────────────────────────────────────────────┘
misc:
  display-name: "&8Miscellaneous"
  icon:
    material: CHEST
  slot: 25
  priority: 999  # Toujours en dernier
  # Pas de règles = catégorie fallback automatique
```

### 7.5 Types de Règles Supportées

| Type | Description | Exemple |
|------|-------------|---------|
| `material` | Liste exacte de materials | `[DIAMOND_SWORD, IRON_SWORD]` |
| `material-tag` | Tag Minecraft (BLOCKS, EDIBLE, etc.) | `BLOCKS` |
| `material-suffix` | Matériaux finissant par | `["_SWORD", "_AXE"]` |
| `material-prefix` | Matériaux commençant par | `["DIAMOND_", "IRON_"]` |
| `material-contains` | Matériaux contenant | `["BANNER", "WOOL"]` |
| `name` | Nom d'affichage (EQUALS/CONTAINS) | `"Legendary"` |
| `lore` | Ligne de lore (EQUALS/CONTAINS) | `"Rare"` |
| `custom-model-data` | Plage de CustomModelData | `min: 1000, max: 9999` |
| `item-model` | Namespace:key du model (1.21+) | `"myserver:sword"` |
| `itemsadder` | ID ItemsAdder | `"namespace:item_id"` |
| `oraxen` | ID Oraxen | `"item_id"` |
| `mmoitems` | Type:ID MMOItems | `"SWORD:LEGENDARY_BLADE"` |
| `nbt` | Condition NBT path/value | `path: "custom.rarity"` |
| `enchantment` | Enchantement présent | `SHARPNESS` |
| `enchantment-level` | Niveau enchant min/max | `min: 3, max: 5` |

---

## 8. Plan de Migration V3 → V4

### 8.1 Analyse de Compatibilité

| Élément V3 | Équivalent V4 | Action |
|------------|---------------|--------|
| `categories.yml` structure | `categories/*.yml` | Migration auto |
| `material` rule | `type: material` | Compatible |
| `name` rule (substring) | `type: name, mode: CONTAINS` | Migration auto |
| `loreKey` rule | `type: lore, mode: CONTAINS` | Migration auto |
| `modelId` rule | `type: custom-model-data` | Migration auto |
| `data` (durability) | Supprimé (obsolète) | Ignoré avec warning |
| `removeColor` option | `strip-colors: true` | Migration auto |

### 8.2 Commande de Migration

```
/zauctionhouse migrate-categories
```

**Comportement:**
1. Lit `categories.yml` V3
2. Crée `categories/` dossier si inexistant
3. Génère fichiers V4 par catégorie
4. Backup `categories.yml` → `categories.yml.v3.backup`
5. Affiche rapport de migration

### 8.3 Exemple Migration

**V3 (categories.yml):**
```yaml
categories:
  weapons:
    name: "Weapons"
    materials:
      - material: DIAMOND_SWORD
      - material: IRON_SWORD
        name: "Special"
```

**V4 (categories/weapons.yml):**
```yaml
# Migré depuis zAuctionHouseV3
# Date: 2026-01-30

weapons:
  display-name: "Weapons"  # Anciennement 'name'
  icon:
    material: DIAMOND_SWORD  # Premier material de la liste
  slot: 10  # Auto-assigné
  priority: 10

  rules:
    - type: material
      materials:
        - DIAMOND_SWORD

    - type: material
      materials:
        - IRON_SWORD
      # Condition AND avec nom
    - type: name
      mode: CONTAINS
      values:
        - "Special"
      strip-colors: false
```

### 8.4 Risques et Solutions

| Risque | Probabilité | Impact | Solution |
|--------|-------------|--------|----------|
| Config V3 corrompue | Faible | Élevé | Validation avant migration |
| Règles complexes non migrables | Moyen | Moyen | Warning + migration manuelle |
| Perte de data (durability) | Faible | Faible | Log warning, fonctionnalité obsolète |
| Slots GUI différents | Élevé | Faible | Auto-assignation avec override possible |

---

## 9. Checklist d'Implémentation

### 9.1 Phase 1 : Core (Sprint 1)

- [x] **Modèle de données**
  - [x] `Category` interface (API module)
  - [x] `CategoryRule` interface (API module)
  - [x] `ZCategory` implementation
  - [x] `CategoryManager` interface et impl

- [x] **Règles de base**
  - [x] `MaterialRule`
  - [x] `MaterialTagRule`
  - [x] `MaterialSuffixRule`
  - [x] `NameRule` (EQUALS/CONTAINS)
  - [x] `LoreRule` (EQUALS/CONTAINS)
  - [x] `CustomModelDataRule`

- [x] **Chargement configuration**
  - [x] Parser YAML categories.yml
  - [x] Auto-load categories/*.yml
  - [x] Validation avec messages erreur
  - [x] Hot-reload support

### 9.2 Phase 2 : Intégrations (Sprint 2)

- [ ] **Items Custom**
  - [x] `ItemsAdderRule`
  - [x] `OraxenRule`
  - [x] `Nexo`
  - [ ] Détection auto plugins présents

- [ ] **GUI Integration**
  - [ ] Button ZAUCTIONHOUSE_CATEGORY
  - [ ] Placeholder %category_count%
  - [ ] Menu categories.yml template

- [ ] **Cache système**
  - [ ] TTL configurable
  - [ ] Invalidation on-change
  - [ ] Métriques performance

### 9.3 Phase 3 : Avancé (Sprint 3)

- [ ] **Catégories dynamiques**
  - [ ] @all (tous items)
  - [ ] @recent (24h)
  - [ ] @ending-soon (1h)

- [ ] **Fonctionnalités avancées**
  - [ ] Héritage `extends`
  - [ ] Permissions par catégorie
  - [ ] Sous-catégories (optionnel)

- [ ] **Migration**
  - [ ] Commande migrate-categories
  - [ ] Backup automatique
  - [ ] Rapport de migration

### 9.4 Tests

- [ ] **Tests unitaires**
  - [ ] Chaque type de règle
  - [ ] Combinaisons AND/OR
  - [ ] Edge cases (null, empty, etc.)

- [ ] **Tests intégration**
  - [ ] Chargement config valide
  - [ ] Rejet config invalide
  - [ ] Migration V3 → V4
  - [ ] Performance 10000 items

- [ ] **Tests manuels**
  - [ ] GUI navigation
  - [ ] Hot-reload
  - [ ] Multi-catégories

---

## 10. Annexes

### 10.1 Liens Sources

| Plugin | Plateforme | URL |
|--------|------------|-----|
| zAuctionHouseV3 | Local | `D:\Users\Maxlego08\workspace2.0\[Spigot] zAuctionHouseV3` |
| Fadah | GitHub | https://github.com/Finally-A-Decent/Fadah |
| Fadah | Modrinth | https://modrinth.com/plugin/fadah |
| AuctionHouse (kiranhart) | GitHub | https://github.com/kiranhart/Auction-House |
| AuctionMaster | GitHub | https://github.com/qKing12/AuctionMaster |
| AdvancedAuctionHouse | AdvancedPlugins | https://advancedplugins.net/item/AdvancedAuctionHouse.4 |
| AxAuctions | BuiltByBit | https://builtbybit.com/resources/axauctions-all-in-one-auction-plugin.40242/ |
| AxAuctions | Docs | https://docs.artillex-studios.com/axauctions.html |
| NexusAuctionHouse | Modrinth | https://modrinth.com/plugin/nexusauctionhouse |
| AzAuctionHouse | Modrinth | https://modrinth.com/plugin/azauctionhouse |

### 10.2 Tags Minecraft Utiles

```
# Tags de blocs (Material.isBlock())
BLOCKS

# Tags d'items comestibles
EDIBLE

# Autres tags utiles pour règles
LOGS, PLANKS, WOOL, CARPETS, TERRACOTTA, CONCRETE, GLASS
ORES, STONE_TYPES, DIRT_LIKE
SWORDS, AXES, PICKAXES, SHOVELS, HOES
HELMETS, CHESTPLATES, LEGGINGS, BOOTS
```

### 10.3 Codes Couleur Minecraft

```
&0 Noir       &8 Gris foncé
&1 Bleu foncé &9 Bleu
&2 Vert foncé &a Vert clair
&3 Cyan foncé &b Cyan
&4 Rouge foncé&c Rouge clair
&5 Violet     &d Rose
&6 Or         &e Jaune
&7 Gris       &f Blanc

&l Gras       &o Italique
&n Souligné   &m Barré
&k Obfuscated &r Reset
```

### 10.4 Exemple GUI categories.yml (zMenu)

```yaml
# inventories/categories.yml
name: '&8Categories'
size: 54

patterns:
  - "zauctionhouse_decoration"

items:
  # Bouton retour
  back:
    type: BACK
    slot: 49

  # Catégorie "Tous"
  all-items:
    type: ZAUCTIONHOUSE_DYNAMIC_CATEGORY
    category: "@all"
    slot: 4
    item:
      material: CHEST
      name: '&eAll Items'
      lore:
        - ''
        - '&7View all auction listings'
        - '&7Total: &f%zauctionhouse_category_count_all%'
        - ''
        - '&eClick to browse!'

  # Catégorie Blocks
  blocks:
    type: ZAUCTIONHOUSE_CATEGORY
    category: blocks
    slot: 10
    item:
      material: GRASS_BLOCK
      name: '&7Blocks'
      lore:
        - ''
        - '&7Building materials'
        - '&7Items: &f%zauctionhouse_category_count_blocks%'
        - ''
        - '&eClick to browse!'

  # Catégorie Weapons
  weapons:
    type: ZAUCTIONHOUSE_CATEGORY
    category: weapons
    slot: 11
    item:
      material: DIAMOND_SWORD
      name: '&cWeapons'
      lore:
        - ''
        - '&7Combat equipment'
        - '&7Items: &f%zauctionhouse_category_count_weapons%'
        - ''
        - '&eClick to browse!'

  # ... autres catégories
```

---

**Fin du document**

*Ce document sera mis à jour au fur et à mesure de l'implémentation.*
