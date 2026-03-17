package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.component.ComponentMessage;
import fr.maxlego08.zauctionhouse.api.messages.DefaultFontInfo;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.messages.MessageType;
import fr.maxlego08.zauctionhouse.api.messages.messages.BossBarMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.ClassicMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.TitleMessage;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public abstract class MessageUtils extends PlaceholderUtils {

    protected final ComponentMessage componentMessage = ComponentMessageHelper.componentMessage;

    public static String getString(String message, Object[] newArgs) {
        if (newArgs.length % 2 != 0) {
            throw new IllegalArgumentException("Number of invalid arguments. Arguments must be in pairs.");
        }

        for (int i = 0; i < newArgs.length; i += 2) {
            if (newArgs[i] == null || newArgs[i + 1] == null) {
                continue;
            }
            message = message.replace(newArgs[i].toString(), newArgs[i + 1].toString());
        }
        return message;
    }

    protected void message(CommandSender sender, String message) {
        this.componentMessage.sendMessage(sender, message);
    }

    protected void message(AuctionPlugin plugin, CommandSender sender, Message message, Object... args) {

        if (sender == null) return;

        if (sender instanceof Player player) {
            message.getMessages().forEach(essentialsMessage -> {

                if (essentialsMessage instanceof ClassicMessage classicMessage) {

                    switch (essentialsMessage.messageType()) {
                        case TCHAT, WITHOUT_PREFIX -> sendTchatMessage(sender, classicMessage, args);
                        case ACTION -> classicMessage.messages().forEach(currentMessage -> {
                            this.componentMessage.sendActionBar(player, papi(getString(currentMessage, args), player));
                        });
                        case CENTER -> classicMessage.messages().forEach(currentMessage -> {
                            this.componentMessage.sendMessage(sender, getCenteredMessage(papi(getString(currentMessage, args), player)));
                        });
                    }

                } else if (essentialsMessage instanceof BossBarMessage bossBarMessage) {

                    this.componentMessage.sendBossBar(plugin, player, bossBarMessage);
                } else if (essentialsMessage instanceof TitleMessage titleMessage) {

                    this.componentMessage.sendTitle(player, titleMessage);
                }
            });
        } else {
            message.getMessages().forEach(essentialsMessage -> {
                if (essentialsMessage instanceof ClassicMessage classicMessage) {
                    sendTchatMessage(sender, classicMessage, args);
                }
            });
        }
    }

    private void sendTchatMessage(CommandSender sender, ClassicMessage classicMessage, Object... args) {
        boolean isWithoutPrefix = classicMessage.messageType() == MessageType.WITHOUT_PREFIX || classicMessage.messages().size() > 1;
        classicMessage.messages().forEach(message -> this.componentMessage.sendMessage(sender, (isWithoutPrefix ? "" : Message.PREFIX.getMessageAsString()) + papi(getString(message, args), sender)));
    }

    protected String getMessage(Message message, Object... args) {
        return getString(String.join("\n", message.getMessageAsStringList()), args);
    }

    protected String args(String message, Object... objects){
        return getString(message, objects);
    }

    protected String getCenteredMessage(String message) {
        if (message == null || message.equals("")) return "";

        int CENTER_PX = 154;

        message = ChatColor.translateAlternateColorCodes('&', message);

        int messagePxSize = 0;
        boolean previousCode = false;
        boolean isBold = false;

        for (char c : message.toCharArray()) {
            if (c == '§') {
                previousCode = true;
            } else if (previousCode) {
                previousCode = false;
                isBold = c == 'l' || c == 'L';
            } else {
                DefaultFontInfo dFI = DefaultFontInfo.getDefaultFontInfo(c);
                messagePxSize += isBold ? dFI.getBoldLength() : dFI.getLength();
                messagePxSize++;
            }
        }

        int halvedMessageSize = messagePxSize / 2;
        int toCompensate = CENTER_PX - halvedMessageSize;
        int spaceLength = DefaultFontInfo.SPACE.getLength() + 1;
        int compensated = 0;
        StringBuilder sb = new StringBuilder();
        while (compensated < toCompensate) {
            sb.append(" ");
            compensated += spaceLength;
        }
        return sb + message;
    }

}
