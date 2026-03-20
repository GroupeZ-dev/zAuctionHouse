package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionClaimService;
import fr.maxlego08.zauctionhouse.api.services.result.ClaimResult;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.TransactionRepository;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ClaimService extends AuctionService implements AuctionClaimService {

    private final AuctionPlugin plugin;

    public ClaimService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<ClaimResult> claimMoney(Player player) {
        return getPendingTransactions(player.getUniqueId()).thenApply(transactions -> {
            if (transactions.isEmpty()) {
                message(this.plugin, player, Message.CLAIM_NO_PENDING);
                return ClaimResult.nothingToClaim("No pending transactions");
            }

            // Group transactions by economy
            Map<String, List<TransactionDTO>> byEconomy = transactions.stream().collect(Collectors.groupingBy(TransactionDTO::economy_name));

            if (byEconomy.isEmpty()) {
                message(this.plugin, player, Message.CLAIM_NO_PENDING);
                return ClaimResult.nothingToClaim("No pending transactions");
            }

            var economyManager = this.plugin.getEconomyManager();
            var transactionIds = transactions.stream().map(TransactionDTO::id).toList();
            var depositReason = this.plugin.getConfiguration().getAutoClaimConfiguration().depositReason();

            BigDecimal totalClaimed = BigDecimal.ZERO;
            AuctionEconomy lastEconomy = null;

            for (var entry : byEconomy.entrySet()) {
                String economyName = entry.getKey();
                List<TransactionDTO> economyTransactions = entry.getValue();

                var optionalEconomy = economyManager.getEconomy(economyName);
                if (optionalEconomy.isEmpty()) {
                    this.plugin.getLogger().warning("Economy not found: " + economyName);
                    continue;
                }

                var economy = optionalEconomy.get();
                lastEconomy = economy;

                // Calculate total for this economy (only positive values = money to receive)
                BigDecimal economyTotal = economyTransactions.stream().map(TransactionDTO::value).filter(v -> v.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add);

                if (economyTotal.compareTo(BigDecimal.ZERO) > 0) {
                    // Check if player is still online before depositing
                    if (player.isOnline()) {
                        plugin.getScheduler().runAsync(w -> economy.deposit(player.getUniqueId(), economyTotal, depositReason));
                        message(this.plugin, player, Message.CLAIM_ECONOMY_SUCCESS, "%amount%", economyManager.format(economy, economyTotal), "%economy%", economy.getDisplayName());
                    }
                    totalClaimed = totalClaimed.add(economyTotal);
                }
            }

            // Mark all transactions as retrieved synchronously to prevent double claims
            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            repository.updateStatus(transactionIds, TransactionStatus.RETRIEVED);

            if (totalClaimed.compareTo(BigDecimal.ZERO) > 0) {
                message(this.plugin, player, Message.CLAIM_SUCCESS, "%amount%", totalClaimed.toString());
                return ClaimResult.success("Money claimed successfully", totalClaimed.doubleValue(), lastEconomy);
            }

            return ClaimResult.nothingToClaim("No positive amount to claim");
        });
    }

    @Override
    public CompletableFuture<Map<String, BigDecimal>> getPendingMoneyByEconomy(UUID playerUniqueId) {
        return getPendingTransactions(playerUniqueId).thenApply(transactions -> {
            Map<String, BigDecimal> result = new HashMap<>();

            for (TransactionDTO transaction : transactions) {
                // Only count positive values (money to receive)
                if (transaction.value().compareTo(BigDecimal.ZERO) > 0) {
                    result.merge(transaction.economy_name(), transaction.value(), BigDecimal::add);
                }
            }

            return result;
        });
    }

    @Override
    public CompletableFuture<BigDecimal> getTotalPendingMoney(UUID playerUniqueId) {
        return getPendingMoneyByEconomy(playerUniqueId).thenApply(byEconomy -> byEconomy.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Override
    public CompletableFuture<List<TransactionDTO>> getPendingTransactions(UUID playerUniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            return repository.selectByPlayerAndStatus(playerUniqueId, TransactionStatus.PENDING);
        }, this.plugin.getExecutorService());
    }

    @Override
    public void handlePlayerJoin(Player player) {
        var config = this.plugin.getConfiguration().getAutoClaimConfiguration();

        getPendingMoneyByEconomy(player.getUniqueId()).thenAccept(pendingByEconomy -> {
            // Check if player is still online at the start of async callback
            if (!player.isOnline()) {
                return;
            }

            if (pendingByEconomy.isEmpty()) {
                return;
            }

            BigDecimal total = pendingByEconomy.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            if (config.enabled()) {
                // Auto-claim is enabled
                long delay = config.delayTicks();
                if (delay <= 0) {
                    claimMoney(player);
                } else {
                    this.plugin.getScheduler().runLater(task -> {
                        if (player.isOnline()) {
                            claimMoney(player);
                        }
                    }, delay);
                }
            } else if (config.notifyPending()) {
                // Just notify the player about pending money
                long delay = config.notifyDelayTicks();
                Runnable notifyTask = () -> {
                    if (player.isOnline()) {
                        String formattedAmount = formatPendingMoney(pendingByEconomy);
                        message(this.plugin, player, Message.CLAIM_PENDING_NOTIFY, "%amount%", formattedAmount, "%count%", String.valueOf(pendingByEconomy.size()));
                    }
                };

                if (delay <= 0) {
                    notifyTask.run();
                } else {
                    this.plugin.getScheduler().runLater(task -> notifyTask.run(), delay);
                }
            }
        });
    }

    private String formatPendingMoney(Map<String, BigDecimal> pendingByEconomy) {
        var economyManager = this.plugin.getEconomyManager();
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (var entry : pendingByEconomy.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;

            var optionalEconomy = economyManager.getEconomy(entry.getKey());
            if (optionalEconomy.isPresent()) {
                sb.append(economyManager.format(optionalEconomy.get(), entry.getValue()));
            } else {
                sb.append(entry.getValue()).append(" ").append(entry.getKey());
            }
        }

        return sb.toString();
    }
}
