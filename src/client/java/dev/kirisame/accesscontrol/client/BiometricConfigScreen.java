package dev.kirisame.accesscontrol.client;

import dev.kirisame.accesscontrol.*;
import java.util.*;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

public final class BiometricConfigScreen extends Screen {
    private static final int PAGE_SIZE = 5;
    private final BiometricNetworking.OpenConfig config;
    private final Map<UUID, String> candidates = new LinkedHashMap<>();
    private final Map<UUID, String> online = new LinkedHashMap<>();
    private final Set<UUID> selected = new LinkedHashSet<>();
    private final ButtonWidget[] rows = new ButtonWidget[PAGE_SIZE];
    private final UUID[] rowIds = new UUID[PAGE_SIZE];
    private TextFieldWidget input;
    private ButtonWidget previous;
    private ButtonWidget next;
    private int page;
    private Text error = Text.empty();

    public BiometricConfigScreen(BiometricNetworking.OpenConfig config) {
        super(Text.translatable("screen.accesscontrol.biometric_title"));
        this.config = config;
        candidates.putAll(BiometricNetworking.decodeProfiles(config.whitelist()));
        selected.addAll(candidates.keySet());
        online.putAll(BiometricNetworking.decodeProfiles(config.online()));
        candidates.putAll(online);
    }
    @Override protected void init() {
        int left = width / 2 - 155, top = height / 2 - 117;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = i;
            rows[i] = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
                UUID id = rowIds[index];
                if (selected.contains(id)) selected.remove(id);
                else if (selected.size() < BiometricWhitelist.MAX_PLAYERS) selected.add(id);
                else error = Text.translatable("screen.accesscontrol.whitelist_full");
                refresh();
            }).dimensions(left + 12, top + 48 + i * 22, 286, 20).build());
        }
        previous = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; refresh(); })
                .dimensions(left + 12, top + 160, 30, 18).build());
        next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; refresh(); })
                .dimensions(left + 268, top + 160, 30, 18).build());
        input = addDrawableChild(new TextFieldWidget(textRenderer, left + 12, top + 183, 220, 20,
                Text.translatable("screen.accesscontrol.player_input")));
        input.setMaxLength(36);
        input.setPlaceholder(Text.translatable("screen.accesscontrol.player_input"));
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.accesscontrol.add"), button -> addInput())
                .dimensions(left + 238, top + 183, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.accesscontrol.save"), button -> {
            ClientPlayNetworking.send(new BiometricNetworking.SetWhitelist(config.pos(), config.dimension(),
                    selected.stream().map(UUID::toString).collect(Collectors.joining("\n"))));
            close();
        }).dimensions(left + 12, top + 208, 139, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(left + 159, top + 208, 139, 20).build());
        refresh();
    }
    private void addInput() {
        String value = input.getText().trim();
        UUID id = online.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(value))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (id == null) {
            try {
                var parsed = BiometricWhitelist.parse(value);
                if (parsed.size() != 1) throw new IllegalArgumentException();
                id = parsed.iterator().next();
            } catch (IllegalArgumentException exception) {
                error = Text.translatable("screen.accesscontrol.invalid_player"); return;
            }
        }
        if (selected.size() >= BiometricWhitelist.MAX_PLAYERS && !selected.contains(id)) {
            error = Text.translatable("screen.accesscontrol.whitelist_full"); return;
        }
        candidates.putIfAbsent(id, id.toString());
        selected.add(id);
        input.setText("");
        error = Text.empty();
        page = new ArrayList<>(candidates.keySet()).indexOf(id) / PAGE_SIZE;
        refresh();
    }
    private void refresh() {
        var entries = new ArrayList<>(candidates.entrySet());
        page = Math.max(0, Math.min(page, Math.max(0, (entries.size() - 1) / PAGE_SIZE)));
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            rows[i].visible = index < entries.size();
            if (rows[i].visible) {
                var entry = entries.get(index);
                rowIds[i] = entry.getKey();
                rows[i].setMessage(Text.literal((selected.contains(entry.getKey()) ? "[+] " : "[ ] ") + entry.getValue()));
            }
        }
        previous.active = page > 0;
        next.active = (page + 1) * PAGE_SIZE < entries.size();
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = width / 2 - 155, top = height / 2 - 117;
        context.fill(left - 2, top - 2, left + 312, top + 234, 0xFF59E0CA);
        context.fill(left, top, left + 310, top + 232, 0xFF111C27);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 9, 0xFFE5F7FF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.accesscontrol.whitelist_hint"), width / 2, top + 24, 0xFFA9C0CE);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.accesscontrol.whitelist_count", selected.size()), width / 2, top + 36, 0xFF67E2CD);
        context.drawCenteredTextWithShadow(textRenderer, error.getString().isEmpty() ? Text.literal((page + 1) + " / "
                + Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE)) : error, width / 2, top + 165, 0xFFFFCE79);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
}
