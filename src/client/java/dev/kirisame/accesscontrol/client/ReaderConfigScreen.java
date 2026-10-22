package dev.kirisame.accesscontrol.client;

import dev.kirisame.accesscontrol.ReaderNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ReaderConfigScreen extends Screen {
    private final ReaderNetworking.OpenConfig config;
    private final ButtonWidget[] levels = new ButtonWidget[4];
    private int selected;

    public ReaderConfigScreen(ReaderNetworking.OpenConfig config) {
        super(Text.translatable("screen.accesscontrol.title"));
        this.config = config;
        selected = config.level();
    }
    @Override protected void init() {
        int left = width / 2 - 120;
        int top = height / 2 - 75;
        for (int i = 0; i < 4; i++) {
            int level = i + 1;
            levels[i] = addDrawableChild(ButtonWidget.builder(Text.literal("L" + level), button -> {
                selected = level;
                updateButtons();
            }).dimensions(left + 15 + i * 54, top + 70, 48, 24).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.accesscontrol.save"), button -> {
            ClientPlayNetworking.send(new ReaderNetworking.SetLevel(config.pos(), config.dimension(), selected));
            close();
        }).dimensions(left + 15, top + 110, 102, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(left + 123, top + 110, 102, 20).build());
        updateButtons();
    }
    private void updateButtons() {
        for (int i = 0; i < levels.length; i++) levels[i].active = selected != i + 1;
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1.21.10 renders the screen background before render(); requesting blur
        // again here would fail with "Can only blur once per frame".
        int left = width / 2 - 120;
        int top = height / 2 - 75;
        context.fill(left - 2, top - 2, left + 242, top + 152, 0xFF35C4CF);
        context.fill(left, top, left + 240, top + 150, 0xFF111C27);
        context.fill(left, top, left + 240, top + 4, 0xFF35C4CF);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 16, 0xFFE5F7FF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.accesscontrol.minimum"), width / 2, top + 39, 0xFFA9C0CE);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.accesscontrol.rule", selected), width / 2, top + 54, 0xFF67E2CD);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
}
