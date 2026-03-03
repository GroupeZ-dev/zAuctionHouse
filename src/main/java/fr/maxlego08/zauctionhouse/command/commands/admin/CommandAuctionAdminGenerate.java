package fr.maxlego08.zauctionhouse.command.commands.admin;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.AuctionItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.PlayerRepository;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandAuctionAdminGenerate extends VCommand {

    private static final String[] FIRST_NAMES = {"Alex", "Steve", "Luna", "Max", "Aria", "Leo", "Nova", "Felix", "Maya", "Oscar", "Zara", "Kai", "Iris", "Theo", "Cleo", "Jax", "Sage", "Finn", "Ruby", "Milo", "Nyx", "Axel", "Jade", "Raven", "Storm", "Phoenix", "Blaze", "Shadow", "Crystal", "Frost", "Ember", "Vex", "Onyx", "Dawn", "Dusk", "Echo", "Flare", "Glitch", "Hex", "Ivy", "Jet", "Knox", "Lyric", "Maze", "Neon", "Orion", "Pulse", "Quest", "Rex", "Spark"};

    private static final String[] LAST_SUFFIXES = {"Player", "Gamer", "Master", "Pro", "King", "Queen", "Lord", "Boss", "Chief", "Hero", "Legend", "Star", "Wolf", "Dragon", "Phoenix", "Ninja", "Knight", "Wizard", "Mage", "Hunter", "Crafter", "Builder", "Miner", "Archer", "Warrior", "Slayer", "Ranger", "Scout", "Seeker", "Rider", "123", "456", "789", "007", "42", "99", "77", "666", "888", "1337", "HD", "TV", "YT", "TTV", "MC", "PVP", "PVE", "OP", "GG", "XD"};

    private final Map<CommandSender, Long> confirmationMap = new HashMap<>();
    private final Map<CommandSender, Integer> confirmationAmountMap = new HashMap<>();

    public CommandAuctionAdminGenerate(AuctionPlugin plugin) {
        super(plugin);
        this.addSubCommand("generate");
        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.ADMIN_GENERATE_WARNING);
        this.addRequireArg("amount", (sender, args) -> List.of("100", "500", "1000", "5000", "10000", "50000"));
        this.setConsoleCanUse(true);
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        String amountString = argAsString(0);
        if (amountString == null) {
            return CommandType.SYNTAX_ERROR;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountString);
        } catch (NumberFormatException e) {
            message(this.plugin, sender, Message.ADMIN_GENERATE_INVALID_AMOUNT);
            return CommandType.DEFAULT;
        }

        if (amount < 1 || amount > 100000) {
            message(this.plugin, sender, Message.ADMIN_GENERATE_INVALID_AMOUNT);
            return CommandType.DEFAULT;
        }

        // Check for valid materials in non-misc categories
        List<Material> validMaterials = getValidMaterials();
        if (validMaterials.isEmpty()) {
            message(this.plugin, sender, Message.ADMIN_GENERATE_NO_MATERIALS);
            return CommandType.DEFAULT;
        }

        long currentTime = System.currentTimeMillis();
        Long lastConfirmation = confirmationMap.get(sender);
        Integer lastAmount = confirmationAmountMap.get(sender);

        // Check if this is a confirmation (within 30 seconds and same amount)
        if (lastConfirmation != null && lastAmount != null && (currentTime - lastConfirmation) < 30000 && lastAmount.equals(amount)) {

            // Clear confirmation data
            confirmationMap.remove(sender);
            confirmationAmountMap.remove(sender);

            // Execute generation
            message(this.plugin, sender, Message.ADMIN_GENERATE_CONFIRMED, "%amount%", String.valueOf(amount));
            generateAuctionItems(amount, validMaterials);
            return CommandType.SUCCESS;
        }

        // First execution - show warning and store confirmation data
        confirmationMap.put(sender, currentTime);
        confirmationAmountMap.put(sender, amount);
        message(this.plugin, sender, Message.ADMIN_GENERATE_WARNING, "%amount%", String.valueOf(amount));

        return CommandType.SUCCESS;
    }

    private List<Material> getValidMaterials() {
        List<Material> validMaterials = new ArrayList<>();
        var categoryManager = plugin.getCategoryManager();

        for (Material material : Material.values()) {
            if (material.isAir() || !material.isItem()) {
                continue;
            }

            ItemStack testItem = new ItemStack(material);
            var category = categoryManager.getCategoryFor(testItem);

            if (!category.isMiscellaneous()) {
                validMaterials.add(material);
            }
        }

        return validMaterials;
    }

    private void generateAuctionItems(int amount, List<Material> validMaterials) {
        long startTime = System.currentTimeMillis();
        CommandSender commandSender = this.sender;

        // Pre-generate all data on the main thread
        List<GenerationData> dataList = new ArrayList<>(amount);
        Map<String, UUID> nameToUuidCache = new HashMap<>();
        var playerRepository = plugin.getStorageManager().with(PlayerRepository.class);

        for (int i = 0; i < amount; i++) {
            ThreadLocalRandom random = ThreadLocalRandom.current();

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

            Material material = validMaterials.get(random.nextInt(validMaterials.size()));
            int itemAmount = random.nextInt(64) + 1;
            BigDecimal price = BigDecimal.valueOf(random.nextInt(99990) + 10);
            long expiredAt = System.currentTimeMillis() + (random.nextInt(7) + 1) * 24L * 60L * 60L * 1000L;

            dataList.add(new GenerationData(sellerUUID, sellerName, material, itemAmount, price, expiredAt));
        }

        // Now process asynchronously
        var storageManager = plugin.getStorageManager();
        var economyManager = plugin.getEconomyManager();
        AuctionEconomy defaultEconomy = economyManager.getDefaultEconomy(ItemType.AUCTION);
        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger lastReported = new AtomicInteger(0);
        int totalAmount = amount;

        plugin.getScheduler().runAsync(task -> {
            try {
                var itemRepository = storageManager.with(ItemRepository.class);
                var auctionItemRepository = storageManager.with(AuctionItemRepository.class);

                // First, count unique players
                Set<String> uniquePlayerNames = new HashSet<>();
                for (GenerationData data : dataList) {
                    uniquePlayerNames.add(data.sellerName);
                }
                int totalPlayers = uniquePlayerNames.size();

                plugin.getLogger().info("[Generate] Starting registration of " + totalPlayers + " unique players...");
                plugin.getScheduler().runNextTick(t -> message(plugin, commandSender, Message.ADMIN_GENERATE_PLAYERS_START, "%total%", String.valueOf(totalPlayers)));

                // Register all unique players (directly, no async wrapper)
                Map<String, UUID> registeredPlayers = new HashMap<>();
                AtomicInteger playersCreated = new AtomicInteger(0);
                AtomicInteger lastPlayersReported = new AtomicInteger(0);

                for (GenerationData data : dataList) {
                    if (!registeredPlayers.containsKey(data.sellerName)) {
                        playerRepository.upsertPlayer(data.sellerUUID, data.sellerName);
                        registeredPlayers.put(data.sellerName, data.sellerUUID);

                        int currentPlayers = playersCreated.incrementAndGet();

                        // Log and report progress every 100 players
                        if (currentPlayers - lastPlayersReported.get() >= 100) {
                            lastPlayersReported.set(currentPlayers);
                            plugin.getLogger().info("[Generate] Players registered: " + currentPlayers + "/" + totalPlayers);
                            final int current = currentPlayers;
                            plugin.getScheduler().runNextTick(t -> message(plugin, commandSender, Message.ADMIN_GENERATE_PLAYERS_PROGRESS, "%current%", String.valueOf(current), "%total%", String.valueOf(totalPlayers)));
                        }
                    }
                }

                int finalPlayersCount = playersCreated.get();
                plugin.getLogger().info("[Generate] Completed registration of " + finalPlayersCount + " unique players.");
                plugin.getScheduler().runNextTick(t -> message(plugin, commandSender, Message.ADMIN_GENERATE_PLAYERS_COMPLETE, "%amount%", String.valueOf(finalPlayersCount)));

                // Then create all items (directly, no async wrapper)
                plugin.getLogger().info("[Generate] Starting creation of " + totalAmount + " auction items...");
                plugin.getScheduler().runNextTick(t -> message(plugin, commandSender, Message.ADMIN_GENERATE_ITEMS_START, "%total%", String.valueOf(totalAmount)));
                for (GenerationData data : dataList) {
                    try {
                        ItemStack itemStack = new ItemStack(data.material, data.itemAmount);

                        // Create item directly without CompletableFuture to avoid SQLite locks
                        int itemId = itemRepository.create(data.sellerUUID, ItemType.AUCTION, data.price, data.expiredAt, defaultEconomy);
                        var auctionItem = auctionItemRepository.create(data.sellerUUID, data.sellerName, itemId, data.price, data.expiredAt, List.of(itemStack), defaultEconomy);

                        this.plugin.getCategoryManager().applyCategories(auctionItem);
                        auctionManager.addItem(StorageType.LISTED, auctionItem);

                        int current = created.incrementAndGet();

                        // Report progress every 500 items
                        if (current - lastReported.get() >= 500) {
                            lastReported.set(current);
                            plugin.getScheduler().runNextTick(t -> message(plugin, commandSender, Message.ADMIN_GENERATE_PROGRESS, "%current%", String.valueOf(current), "%total%", String.valueOf(totalAmount)));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to generate auction item: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;

                // Clear caches
                auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED);
                plugin.getCategoryManager().invalidateCategoryCountCache();

                plugin.getScheduler().runNextTick(t -> message(plugin, commandSender, Message.ADMIN_GENERATE_COMPLETE, "%amount%", String.valueOf(created.get()), "%time%", String.valueOf(duration)));

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to generate auction items: " + e.getMessage());
                e.printStackTrace();
                plugin.getScheduler().runNextTick(t -> message(plugin, commandSender, Message.ADMIN_GENERATE_COMPLETE, "%amount%", String.valueOf(created.get()), "%time%", "ERROR"));
            }
        });
    }

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

    private record GenerationData(UUID sellerUUID, String sellerName, Material material, int itemAmount,
                                  BigDecimal price, long expiredAt) {
    }
}
