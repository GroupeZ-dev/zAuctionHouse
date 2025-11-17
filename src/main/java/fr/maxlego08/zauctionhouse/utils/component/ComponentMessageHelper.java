package fr.maxlego08.zauctionhouse.utils.component;

import fr.maxlego08.zauctionhouse.api.component.ComponentMessage;

public class ComponentMessageHelper {

    public static ComponentMessage componentMessage;

    static {
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            componentMessage = new PaperComponent();
        } catch (Exception ignored) {
            componentMessage = new SpigotComponent();
        }
    }

}