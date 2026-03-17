package fr.maxlego08.zauctionhouse.utils.component;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.component.ComponentMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.BossBarMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.TitleMessage;
import fr.maxlego08.zauctionhouse.utils.BossBarAnimation;
import fr.maxlego08.zauctionhouse.utils.MessageUtils;
import fr.maxlego08.zauctionhouse.utils.cache.SimpleCache;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaperComponent implements ComponentMessage {

    private final MiniMessage MINI_MESSAGE = MiniMessage.builder().tags(TagResolver.builder().resolver(StandardTags.defaults()).build()).build();
    private final Map<String, String> COLORS_MAPPINGS = new HashMap<>();
    private final SimpleCache<String, Component> cache = new SimpleCache<>();

    public PaperComponent() {
        this.COLORS_MAPPINGS.put("0", "black");
        this.COLORS_MAPPINGS.put("1", "dark_blue");
        this.COLORS_MAPPINGS.put("2", "dark_green");
        this.COLORS_MAPPINGS.put("3", "dark_aqua");
        this.COLORS_MAPPINGS.put("4", "dark_red");
        this.COLORS_MAPPINGS.put("5", "dark_purple");
        this.COLORS_MAPPINGS.put("6", "gold");
        this.COLORS_MAPPINGS.put("7", "gray");
        this.COLORS_MAPPINGS.put("8", "dark_gray");
        this.COLORS_MAPPINGS.put("9", "blue");
        this.COLORS_MAPPINGS.put("a", "green");
        this.COLORS_MAPPINGS.put("b", "aqua");
        this.COLORS_MAPPINGS.put("c", "red");
        this.COLORS_MAPPINGS.put("d", "light_purple");
        this.COLORS_MAPPINGS.put("e", "yellow");
        this.COLORS_MAPPINGS.put("f", "white");
        this.COLORS_MAPPINGS.put("k", "obfuscated");
        this.COLORS_MAPPINGS.put("l", "bold");
        this.COLORS_MAPPINGS.put("m", "strikethrough");
        this.COLORS_MAPPINGS.put("n", "underlined");
        this.COLORS_MAPPINGS.put("o", "italic");
        this.COLORS_MAPPINGS.put("r", "reset");
    }

    private TextDecoration.State getState(String text) {
        return text.contains("&o") || text.contains("<i>") || text.contains("<em>") || text.contains("<italic>") ? TextDecoration.State.TRUE : TextDecoration.State.FALSE;
    }

    private String colorMiniMessage(String message) {
        StringBuilder stringBuilder = new StringBuilder();

        Pattern pattern = Pattern.compile("(?<!<)(?<!:)(?<!</)#([a-fA-F0-9]{6})");
        Matcher matcher = pattern.matcher(message);

        while (matcher.find()) {
            matcher.appendReplacement(stringBuilder, "<$0>");
        }
        matcher.appendTail(stringBuilder);

        String newMessage = stringBuilder.toString();

        for (Map.Entry<String, String> entry : this.COLORS_MAPPINGS.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            newMessage = newMessage.replace("&" + key, "<" + value + ">");
            newMessage = newMessage.replace("§" + key, "<" + value + ">");
            newMessage = newMessage.replace("&" + key.toUpperCase(), "<" + value + ">");
            newMessage = newMessage.replace("§" + key.toUpperCase(), "<" + value + ">");
        }

        return newMessage;
    }

    public Component getComponent(String message) {
        return this.cache.get(message, () -> this.MINI_MESSAGE.deserialize(colorMiniMessage(message)));
    }

    @Override
    public void sendMessage(CommandSender sender, String message) {
        Component component = this.cache.get(message, () -> this.MINI_MESSAGE.deserialize(colorMiniMessage(message)));
        sender.sendMessage(component);
    }

    @Override
    public void sendActionBar(Player player, String message) {
        Component component = this.cache.get(message, () -> this.MINI_MESSAGE.deserialize(colorMiniMessage(message)));
        player.sendActionBar(component);
    }

    @Override
    public void sendTitle(Player player, TitleMessage titleMessage, Object... args) {
        Component title = getComponent(MessageUtils.getString(titleMessage.title(), args));
        Component subtitle = getComponent(MessageUtils.getString(titleMessage.subtitle(), args));

        player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(titleMessage.start()), Duration.ofMillis(titleMessage.time()), Duration.ofMillis(titleMessage.end()))));
    }

    @Override
    public void sendBossBar(AuctionPlugin plugin, Player player, BossBarMessage bossBarMessage) {
        BossBar bossBar = BossBar.bossBar(getComponent(bossBarMessage.text()), 1f, bossBarMessage.getColor(), bossBarMessage.getOverlay(), bossBarMessage.getFlags());
        player.showBossBar(bossBar);

        new BossBarAnimation(plugin, player, bossBar, bossBarMessage.duration());
    }

    @Override
    public String getItemStackName(ItemStack itemStack) {

        if (!itemStack.hasItemMeta()) return "";

        var meta = itemStack.getItemMeta();
        if (!meta.hasDisplayName()) return "";

        return PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(meta.displayName()));
    }

    @Override
    public List<String> getItemStackLore(ItemStack itemStack) {

        if (!itemStack.hasItemMeta()) return List.of();

        var meta = itemStack.getItemMeta();
        if (!meta.hasLore()) return List.of();

        return Objects.requireNonNull(meta.lore()).stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }

    @Override
    public boolean hasDisplayName(ItemStack itemStack) {
        return itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName();
    }

    @Override
    public String getItemStackDisplayName(ItemStack itemStack) {
        return MiniMessage.miniMessage().serialize(Objects.requireNonNull(itemStack.getItemMeta().displayName()));
    }

    @Override
    public String stripColor(String message) {
        return PlainTextComponentSerializer.plainText().serialize(this.MINI_MESSAGE.deserialize(colorMiniMessage(message)));
    }
}
