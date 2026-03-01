# zAuctionHouse

Find here the new version of zAuctionHouse.

Version 3 of zAuctionHouse was released on February 11, 2021, which means it's almost been 5 years !

A full overhaul of the plugin is therefore necessary.
In a few months, you’ll be able to discover a plugin that’s even more complete, more efficient,
more beautiful, fresher, more radiant, basically everything you could possibly want!
It’s going to be amazing… or there will be bugs everywhere. Your call. 😄

# ToDo

## Nouvelles fonctionnalités

- [ ] Retravailler le système de mise à jour de fichier YAML pour éviter d'ajouter n'importe comment les nouvelles clés,
  et garder une cohérence dans le fichier
- [ ] Ajouter un système d'option pour activer ou désactiver les messages
- [ ] Ajouter la possibilité de vendre des items avec la commande /ah sell, et de pouvoir sélectionner un item dans son
  inventaire, de pouvoir sélectionner le prix. Doit pouvoir fonctionner avec zTextGenerator (donc mise à jour de
  l'inventaire lors d'un clic, sinon, mise à jour uniquement de l'item)
- [ ] Système de vente d'item admin comme sur fairysky
- [ ] Système d'item favoris configurable
- [x] Système pour afficher le nombre de vente effectué lors de la déconnexion et combien d'argent gagné (inventaire
  histoire de vente hors ligne ?)
- [x] Une commande pour ouvrir une certaine page ``/ah page <page>``
- [ ] Avoir la raison de pourquoi l'item n'est plus en vente (parce que acheté, supprimé, ou expiré)
- [ ] Pouvoir définir des custom modals id ou namespace pour les images d'items custom
- [ ] Pour modifier l'état d'une transactions qui doit être claim
- [ ] Pouvoir vendre un item à la place d'un joueur
- [ ] Pouvoir choisir un nom à son "bulk sale"
- [ ] Ajouter un système qui va vérifier le contenu des inventaires, et vérifier que les boutons sont bien utilisé, par exemple ne pas avoir de bouton de remove confirm purchase dans l'inventaire de confirmation de retirer l'item de la vente
- [x] Renommer "owned" en "selling", un nom plus parlant pour indiquer que ce sont les items du joueur qui sont en vente
- [ ] Ajouter plus de commande admin pour gérer les items des joueurs, en ajouter, en supprimer (supprimer tout d'un coup)

## Fonctionnalités V3 manquantes dans V4

### Commandes manquantes
- [ ] `/ah search <string>` - Recherche d'items par nom/matériau
- [ ] `/ah blacklist` / `/ah blacklist add/remove <player>` - Gestion de blacklist de joueurs
- [ ] `/ah config` - Configuration in-game du plugin
- [ ] `/ah version` - Afficher la version du plugin
- [ ] `/ah convert` - Conversion depuis V2 ou PlayerAuctions
- [ ] `/ah purge <days>` - Purge des anciennes transactions
- [ ] `/ah sellinventory <price>` - Vendre tout le contenu de l'inventaire (partiellement implémenté)

### Système de taxe
- [ ] Tax globale sur toutes les ventes/achats
- [ ] Tax par item configurable dans `taxs.yml`
- [ ] Types de tax: SELL, PURCHASE, BOTH
- [ ] Permission de bypass de la tax
- [ ] Pourcentage de tax configurable

### Système de priorité
- [ ] Priorité basée sur les permissions (VIP, etc.)
- [ ] Tri des items par priorité
- [ ] Limite max d'items par niveau de priorité
- [ ] Affichage du texte de priorité dans le lore

### Système de prix par item
- [ ] Configuration des prix min/max par item dans `prices.yml`
- [ ] Prix basé sur le nom contenant un texte
- [ ] Prix basé sur le custom model ID
- [ ] Prix basé sur le lore de l'item
- [ ] Prix basé sur le matériau

### Système de recherche avancé
- [ ] Recherche d'items via chat
- [ ] Panel de recherche GUI via ProtocolLib (anvil input)
- [ ] Recherche de matériaux traduits (support multi-langue)
- [ ] Filtres de recherche: matériau, nom, lore, vendeur

### Options de tri supplémentaires (V3 en a 14, V4 en a 4)
- [ ] Nom alphabétique (A-Z)
- [ ] Nom alphabétique inversé (Z-A)
- [ ] Nom du vendeur alphabétique
- [ ] Nom du vendeur inversé
- [ ] Type de matériau alphabétique
- [ ] Type de matériau inversé
- [ ] Nom d'économie alphabétique
- [ ] Nom d'économie inversé
- [ ] Taille de stack ascendant
- [ ] Taille de stack descendant

### Système de cooldown
- [ ] Cooldown de vente (empêcher spam)
- [ ] Cooldown de transaction
- [ ] Cooldown de commande
- [ ] Cooldown de changement de tri

### Système de blacklist de joueurs
- [ ] Empêcher des joueurs spécifiques d'utiliser l'hôtel des ventes
- [ ] Commandes d'ajout/suppression de joueurs blacklistés
- [ ] Permission pour bypass la blacklist

### Annonces et notifications
- [ ] Annonces globales de vente (avec permission pour voir)
- [ ] Annonces globales d'achat (avec permission pour voir)
- [ ] Messages de cooldown personnalisés

### Système BID (Enchères)
- [ ] Système d'enchères fonctionnel (framework présent mais pas implémenté)
- [ ] Commande `/ah bid <price> [amount]`
- [ ] Interface d'enchères
- [ ] Notifications d'enchères

### Système RENT (Location)
- [ ] Système de location d'items (framework présent mais pas implémenté)
- [ ] Commande `/ah rent <price> [duration]`
- [ ] Interface de location

### Prévention de dupe
- [ ] Détection de dupe via NMS
- [ ] Détection de dupe via PDC (Persistent Data Container)
- [ ] Listener anti-duplication
- [ ] Webhook Discord pour tentatives de dupe

### Intégrations manquantes
- [ ] Citizens NPC support (ouvrir l'AH via NPC)
- [ ] ProtocolLib (pour recherche GUI avancée)
- [ ] ZEssentials mailbox integration

### Autres fonctionnalités
- [ ] Système de scoreboard (FastBoard)
- [ ] Affichage du contenu des shulker boxes (clic gauche/droit toggle)
- [ ] Confirmation de vente toggle par joueur
- [ ] Mode créatif: empêcher/autoriser la vente

# zAuctionHouse Discord

- [ ] Envoyer des notifications en message privé lorsqu'un item est vendu
- [ ] Envoyer des notifications lorsqu'un item est mis en vente 
- [ ] Pouvoir ajouter des filtres sur les items mis en vente pour recevoir une notification en message privée
- [ ] Pouvoir acheter directement depuis discord un item (avec un bouton "acheter"), cela est uniquement possible avec l'inventaire des items achetés
- [ ] 