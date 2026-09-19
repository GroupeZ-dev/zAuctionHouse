package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * @author Maxlego08
 */
public class VersionChecker extends ZUtils implements Listener {

    private final String URL_API = "https://groupez.dev/api/v1/resource/version/%s";
    private final String URL_RESOURCE = "https://groupez.dev/resources/%s";
    private final AuctionPlugin plugin;
    private final int pluginID;
    private volatile boolean useLastVersion = false;
    private volatile String lastVersion;

    public VersionChecker(AuctionPlugin plugin, int pluginID) {
        super();
        this.plugin = plugin;
        this.pluginID = pluginID;
        this.lastVersion = plugin.getPluginMeta().getVersion();
    }

    /**
     * Unregisters the event listener to prevent memory leaks on plugin reload.
     */
    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    /**
     * Allows to check if the plugin version is up-to-date.
     */
    public void useLastVersion() {

        Bukkit.getPluginManager().registerEvents(this, this.plugin); // Register
        // event

        String pluginVersion = plugin.getDescription().getVersion();
        this.getVersion(version -> {

            this.useLastVersion = compare(pluginVersion, version) >= 0;
            this.lastVersion = version;

            if (this.useLastVersion) {

                this.plugin.getLogger().info("No update available.");
            } else {

                this.plugin.getLogger().info("New update available. Your version: " + pluginVersion + ", latest version: " + version);
                this.plugin.getLogger().info("Download plugin here: " + String.format(URL_RESOURCE, this.pluginID));
            }
        });

    }

    /**
     * Comparaison segment par segment. {@code Long.parseLong(v.replace(".", ""))} cassait des
     * qu'un segment passait a deux chiffres (4.0.1.10 -> 40110 etait juge superieur a
     * 4.0.2.0 -> 4020) et levait une NumberFormatException, dans un bloc asynchrone, sur toute
     * version portant un suffixe non numerique.
     *
     * @param left  la version locale
     * @param right la version distante
     * @return un entier negatif, nul ou positif selon que left est anterieure, egale ou
     * posterieure a right
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

    /**
     * Absorbe un suffixe non numerique du type "-SNAPSHOT".
     *
     * @param segment le segment de version a lire
     * @return la valeur numerique du segment, 0 s'il n'en porte aucune
     */
    private static int parseSegment(String segment) {
        StringBuilder builder = new StringBuilder();
        for (char c : segment.toCharArray()) {
            if (Character.isDigit(c)) builder.append(c);
        }
        return builder.isEmpty() ? 0 : Integer.parseInt(builder.toString());
    }

    @EventHandler
    public void onConnect(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (!useLastVersion && event.getPlayer().hasPermission("zplugin.notifs")) {
            this.plugin.getScheduler().runAtLocationLater(player.getLocation(), () -> {
                var pluginVersion = this.plugin.getPluginMeta().getVersion();
                message(this.plugin, player, Message.VERSION_AVAILABLE, "%version%", pluginVersion, "%latest%", this.lastVersion);
            }, 20);
        }
    }

    /**
     * Get version by plugin id
     *
     * @param consumer - Do something after
     */
    public void getVersion(Consumer<String> consumer) {
        this.plugin.getScheduler().runAsync(w -> {
            final String apiURL = String.format(URL_API, this.pluginID);
            try {
                URL url = URI.create(apiURL).toURL();
                URLConnection hc = url.openConnection();
                hc.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
                try (Scanner scanner = new Scanner(hc.getInputStream())) {
                    if (scanner.hasNext()) consumer.accept(scanner.next());
                }
            } catch (IOException exception) {
                this.plugin.getLogger().info("Cannot look for updates: " + exception.getMessage());
            }
        });
    }

}
