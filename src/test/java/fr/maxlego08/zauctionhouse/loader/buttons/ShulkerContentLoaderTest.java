package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.buttons.shulker.ShulkerContentButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ShulkerContentLoaderTest extends LoaderTestBase {

    private ShulkerContentLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new ShulkerContentLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_SHULKER_CONTENT")
    void name() {
        assertEquals("ZAUCTIONHOUSE_SHULKER_CONTENT", loader.getName());
    }

    @Test
    @DisplayName("le bouton est construit avec le plugin")
    void button_built() {
        ShulkerContentButton button = loadInline(loader, "content", "slot: 1", ShulkerContentButton.class);

        assertSame(plugin, field(button, "plugin"));
        assertEquals(0, (int) field(button, "emptySlot"));
    }

    @Test
    @Disabled("BUG: ShulkerContentLoader.java:22 lit `path + \".empty-slot\"` alors que zMenu fournit deja un chemin termine par un point (items.content.) : la cle items.content..empty-slot n'existe jamais et empty-slot vaut toujours 0")
    @DisplayName("shulker-content.yml livre : empty-slot 22 est lu")
    void shippedFile_readsEmptySlot() {
        ShulkerContentButton button = load(loader, TestResources.yaml("inventories/shulker-content.yml"), "content", ShulkerContentButton.class);

        assertEquals(22, (int) field(button, "emptySlot"));
    }

    @Test
    @Disabled("BUG: ShulkerContentLoader.java:22 double point dans le chemin (`path + \".empty-slot\"`) : la valeur configuree est ignoree")
    @DisplayName("empty-slot configure inline est lu")
    void inline_readsEmptySlot() {
        ShulkerContentButton button = loadInline(loader, "content", "empty-slot: 31", ShulkerContentButton.class);

        assertEquals(31, (int) field(button, "emptySlot"));
    }
}
