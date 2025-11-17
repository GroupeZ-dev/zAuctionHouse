package fr.maxlego08.zauctionhouse.message;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.ConfigurationFile;
import fr.maxlego08.zauctionhouse.api.messages.AuctionMessage;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.messages.MessageType;
import fr.maxlego08.zauctionhouse.api.messages.messages.BossBarMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.ClassicMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.TitleMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MessageLoader implements ConfigurationFile {

    private final Locale locale = Locale.getDefault();
    private final AuctionPlugin plugin;
    private final List<Message> loadedMessages = new ArrayList<>();

    public MessageLoader(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void load() {

        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.copyFile();

        this.loadMessages(YamlConfiguration.loadConfiguration(file));

        if (this.loadedMessages.size() != Message.values().length) {
            this.plugin.getLogger().log(Level.SEVERE, "Messages were not loaded correctly.");
            for (Message value : Message.values()) {
                if (!this.loadedMessages.contains(value)) {
                    value.setPlugin(plugin);
                    this.plugin.getLogger().log(Level.SEVERE, value + " was not loaded.");

                    List<AuctionMessage> newMessages = new ArrayList<>();
                    for (AuctionMessage message : value.getMessages()) {
                        if (message instanceof ClassicMessage classicMessage) {
                            newMessages.add(new ClassicMessage(classicMessage.messageType(), classicMessage.messages().stream().map(this::replaceMessagesColors).toList()));
                        } else if (message instanceof BossBarMessage bossBarMessage) {
                            newMessages.add(new BossBarMessage(this.replaceMessagesColors(bossBarMessage.text()), bossBarMessage.color(), bossBarMessage.overlay(), bossBarMessage.flags(), bossBarMessage.duration(), bossBarMessage.isStatic()));
                        } else if (message instanceof TitleMessage titleMessage) {
                            newMessages.add(new TitleMessage(this.replaceMessagesColors(titleMessage.title()), this.replaceMessagesColors(titleMessage.subtitle()), titleMessage.start(), titleMessage.time(), titleMessage.end()));
                        }
                    }
                    value.setMessages(newMessages);
                }
            }
        }

    }

    private void copyFile() {

        String messageFileName = "messages";
        String localMessageName = "messages_" + locale.getLanguage();
        if (this.plugin.resourceExist("messages/" + localMessageName + ".yml")) {
            messageFileName = localMessageName;
        }

        this.plugin.saveOrUpdateConfiguration("messages/" + messageFileName + ".yml", "messages.yml", false);
    }

    private void loadMessages(YamlConfiguration configuration) {

        this.loadedMessages.clear();

        for (String key : configuration.getKeys(false)) {

            String messageKey = key.replace("-", "_").toUpperCase();
            try {

                Message message = Message.fromString(messageKey);
                if (message == null) {
                    plugin.getLogger().severe("Impossible to find the message " + key + ", it does not exist, you must delete it.");
                    continue;
                }

                message.setPlugin(this.plugin);

                List<AuctionMessage> AuctionMessages = new ArrayList<>();
                List<Map<?, ?>> mapList = configuration.getMapList(key);

                if (!mapList.isEmpty()) {

                    for (int index = 0; index != mapList.size(); index++) {

                        String path = key + " and index " + (index + 1);
                        Map<?, ?> map = mapList.get(index);
                        MessageType messageType = map.containsKey("type") ? MessageType.fromString((String) map.get("type")) : MessageType.TCHAT;
                        if (messageType == null) {
                            messageType = MessageType.TCHAT;
                            plugin.getLogger().severe("Message type was not found for " + path + ", use TCHAT by default.");
                        }

                        if (messageType == MessageType.BOSSBAR) {

                            String text = replaceMessagesColors(getValue(map, "text", path, String.class, "Default text", true));
                            String color = getValue(map, "color", path, String.class, "WHITE", false);
                            String overlay = getValue(map, "overlay", path, String.class, "PROGRESS", false);
                            List<String> flags = getValue(map, "flags", path, List.class, new ArrayList<>(), false);
                            long duration = getValue(map, "duration", path, Long.class, 60L, false);
                            boolean isStatic = getValue(map, "static", path, Boolean.class, false, false);

                            BossBarMessage bossBarMessage = new BossBarMessage(text, color, overlay, flags, duration, isStatic);

                            if (bossBarMessage.isValid(this.plugin)) {
                                AuctionMessages.add(bossBarMessage);
                            }

                        } else if (messageType == MessageType.TITLE) {

                            String title = replaceMessagesColors(getValue(map, "title", path, String.class, "Default title", true));
                            String subtitle = replaceMessagesColors(getValue(map, "subtitle", path, String.class, "Default subtitle", true));
                            long start = getValue(map, "start", path, Long.class, 100L, false);
                            long time = getValue(map, "time", path, Long.class, 2800L, false);
                            long end = getValue(map, "end", path, Long.class, 100L, false);

                            AuctionMessage AuctionMessage = new TitleMessage(title, subtitle, start, time, end);
                            AuctionMessages.add(AuctionMessage);
                        } else {

                            List<String> messages = getMessage(map);

                            messages.removeIf(Objects::isNull);
                            if (messages.isEmpty()) {

                                plugin.getLogger().severe("Message is empty for " + key + " and index " + index + ", use default configuration.");
                            } else {

                                AuctionMessage AuctionMessage = new ClassicMessage(messageType, messages.stream().map(this::replaceMessagesColors).collect(Collectors.toList()));
                                AuctionMessages.add(AuctionMessage);
                            }
                        }
                    }
                } else if (configuration.contains(key + ".type")) {

                    MessageType messageType = MessageType.fromString(configuration.getString(key + ".type", "TCHAT"));
                    if (messageType == null) {
                        messageType = MessageType.TCHAT;
                        plugin.getLogger().severe("Message type was not found for " + key + ", use TCHAT by default.");
                    }

                    if (messageType == MessageType.TITLE) {

                        String title = replaceMessagesColors(configuration.getString(key + ".title", "Default title"));
                        String subtitle = replaceMessagesColors(configuration.getString(key + ".subtitle", "Default subtitle"));
                        long start = configuration.getLong(key + ".start", 100);
                        long time = configuration.getLong(key + ".time", 2800);
                        long end = configuration.getLong(key + ".end", 100);

                        AuctionMessage AuctionMessage = new TitleMessage(title, subtitle, start, time, end);
                        AuctionMessages.add(AuctionMessage);

                    } else if (messageType == MessageType.BOSSBAR) {

                        String text = replaceMessagesColors(configuration.getString(key + ".text", "Default Text"));
                        String color = configuration.getString("color", "WHITE");
                        String overlay = configuration.getString("overlay", "PROGRESS");
                        List<String> flags = configuration.getStringList("flags");
                        long duration = configuration.getLong("duration", 60);
                        boolean isStatic = configuration.getBoolean("static", false);

                        BossBarMessage bossBarMessage = new BossBarMessage(text, color, overlay, flags, duration, isStatic);

                        if (bossBarMessage.isValid(this.plugin)) {
                            AuctionMessages.add(bossBarMessage);
                        }

                    } else {

                        List<String> messages = configuration.getStringList(key + ".messages");
                        if (messages.isEmpty()) {
                            messages.add(replaceMessagesColors(configuration.getString(key + ".message")));
                        } else {
                            messages = messages.stream().map(this::replaceMessagesColors).collect(Collectors.toList());
                        }

                        messages.removeIf(Objects::isNull);
                        if (messages.isEmpty()) {

                            plugin.getLogger().severe("Message is empty for " + key + ", use default configuration.");
                        } else {

                            AuctionMessage AuctionMessage = new ClassicMessage(messageType, messages);
                            AuctionMessages.add(AuctionMessage);
                        }
                    }

                } else {

                    List<String> messages = configuration.getStringList(key);
                    if (messages.isEmpty()) {
                        messages.add(replaceMessagesColors(configuration.getString(key)));
                    } else {
                        messages = messages.stream().map(this::replaceMessagesColors).collect(Collectors.toList());
                    }

                    messages.removeIf(Objects::isNull);
                    if (messages.isEmpty()) {

                        plugin.getLogger().severe("Message is empty for " + key + ", use default configuration.");
                    } else {

                        AuctionMessage AuctionMessage = new ClassicMessage(MessageType.TCHAT, messages);
                        AuctionMessages.add(AuctionMessage);
                    }
                }

                message.setMessages(AuctionMessages);
                this.loadedMessages.add(message);

            } catch (Exception exception) {
                exception.printStackTrace();
                this.plugin.getLogger().log(Level.SEVERE, messageKey + " key was not found !");
            }
        }

    }

    private String replaceMessagesColors(String message) {
        return this.plugin.getConfiguration().getMessageColors().stream().reduce(message, (msg, color) -> msg.replace(color.key(), color.color()), (msg1, msg2) -> msg1);
    }

    private List<String> getMessage(Map<?, ?> map) {
        List<String> messages = new ArrayList<>();

        for (String key : new String[]{"messages", "message"}) {
            Object value = map.get(key);
            if (value instanceof List<?>) {
                for (Object item : (List<?>) value) {
                    if (item != null) {
                        messages.add(item.toString());
                    }
                }
            } else if (value != null) {
                messages.add(value.toString());
            }
        }

        return messages;
    }

    private <T> T getValue(Map<?, ?> map, String key, String path, Class<T> type, T defaultValue, boolean isRequired) {

        if (map.containsKey(key)) {
            Object value = map.get(key);

            if (value == null) {
                return defaultValue;
            }

            if (Number.class.isAssignableFrom(type) && value instanceof Number number) {
                Object converted;

                if (type == Integer.class) {
                    converted = number.intValue();
                } else if (type == Long.class) {
                    converted = number.longValue();
                } else if (type == Double.class) {
                    converted = number.doubleValue();
                } else if (type == Float.class) {
                    converted = number.floatValue();
                } else {
                    this.plugin.getLogger().severe("Unsupported numeric type for the key " + key + " for the message " + path);
                    return defaultValue;
                }

                return type.cast(converted);
            }

            if (type == String.class) {
                if (value instanceof String) {
                    return type.cast(value);
                }
            } else if (type == Boolean.class && value instanceof Boolean) {
                return type.cast(value);
            } else if (type.isInstance(value)) {
                return type.cast(value);
            }

            this.plugin.getLogger().severe("Type mismatch for the key " + key + " for the message " + path + " (expected " + type.getSimpleName() + ", got " + value.getClass().getSimpleName() + ")");
        } else if (isRequired) {
            this.plugin.getLogger().severe("Unable to find the key " + key + " for the message " + path);
        }

        return defaultValue;
    }

}
