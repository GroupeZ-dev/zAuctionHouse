package fr.maxlego08.zauctionhouse.utils.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ComponentMessageHelperTest {

    @Test
    @DisplayName("avec MiniMessage sur le classpath (Paper), le helper selectionne l'implementation Paper")
    void componentMessage_isPaperWhenMiniMessagePresent() throws ClassNotFoundException {
        Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
        assertNotNull(ComponentMessageHelper.componentMessage);
        assertInstanceOf(PaperComponent.class, ComponentMessageHelper.componentMessage);
    }

    @Test
    @DisplayName("l'instance est un singleton statique")
    void componentMessage_isSingleton() {
        assertSame(ComponentMessageHelper.componentMessage, ComponentMessageHelper.componentMessage);
    }
}
