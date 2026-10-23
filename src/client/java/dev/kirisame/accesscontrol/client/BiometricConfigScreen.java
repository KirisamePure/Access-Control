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
    private static final int PAGE_SIZE = 4;
    private final BiometricNetworking.OpenConfig config;
    private final Map<UUID, String> candidates = new LinkedHashMap<>();
    private final Map<UUID, String> online = new LinkedHashMap<>();
    private final Set<UUID> selected = new LinkedHashSet<>();
    private final Set<String> selectedTeams = new LinkedHashSet<>();
    private final Set<String> teamCandidates = new LinkedHashSet<>();
    private final String[] rowTeams = new String[PAGE_SIZE];
    private boolean teamTab;
    private final ButtonWidget[] rows = new ButtonWidget[PAGE_SIZE];
    private final UUID[] rowIds = new UUID[PAGE_SIZE];
    private TextFieldWidget input;
    private ButtonWidget previous;
    private ButtonWidget next;
    private ButtonWidget playersTab;
    private ButtonWidget teamsTab;
    private int page;
    private Text error = Text.empty();

    public BiometricConfigScreen(BiometricNetworking.OpenConfig config) {
        super(Text.translatable("screen.accesscontrol.biometric_title"));
        this.config = config;
        candidates.putAll(BiometricNetworking.decodeProfiles(config.whitelist()));
        selected.addAll(candidates.keySet());
        online.putAll(BiometricNetworking.decodeProfiles(config.online()));
        candidates.putAll(online);
        selectedTeams.addAll(BiometricWhitelist.parseTeams(config.teams()));
        teamCandidates.addAll(selectedTeams);
        config.availableTeams().lines().filter(name -> !name.isBlank()).forEach(teamCandidates::add);
    }
    @Override protected void init() {
        int left = width / 2 - 155, top = height / 2 - 117;
        playersTab = addDrawableChild(ButtonWidget.builder(Text.translatable("screen.accesscontrol.players_tab"), b -> switchTab(false))
                .dimensions(left + 12, top + 25, 139, 20).build());
        teamsTab = addDrawableChild(ButtonWidget.builder(Text.translatable("screen.accesscontrol.teams_tab"), b -> switchTab(true))
                .dimensions(left + 159, top + 25, 139, 20).build());
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = i;
            rows[i] = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
                error = Text.empty();
                if (teamTab) {
                    String name = rowTeams[index];
                    if (selectedTeams.contains(name)) selectedTeams.remove(name);
                    else if (selectedTeams.size() < BiometricWhitelist.MAX_TEAMS) selectedTeams.add(name);
                    else error = Text.translatable("screen.accesscontrol.teams_full");
                } else {
                    UUID id = rowIds[index];
                    if (selected.contains(id)) selected.remove(id);
                    else if (selected.size() < BiometricWhitelist.MAX_PLAYERS) selected.add(id);
                    else error = Text.translatable("screen.accesscontrol.whitelist_full");
                }
                refresh();
            }).dimensions(left + 12, top + 60 + i * 22, 286, 20).build());
        }
        previous = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; refresh(); })
                .dimensions(left + 12, top + 152, 30, 18).build());
        next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; refresh(); })
                .dimensions(left + 268, top + 152, 30, 18).build());
        input = addDrawableChild(new TextFieldWidget(textRenderer, left + 12, top + 177, 220, 20,
                Text.translatable("screen.accesscontrol.player_input")));
        input.setMaxLength(36);
        input.setPlaceholder(Text.translatable("screen.accesscontrol.player_input"));
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.accesscontrol.add"), button -> addInput())
                .dimensions(left + 238, top + 177, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.accesscontrol.save"), button -> {
            ClientPlayNetworking.send(new BiometricNetworking.SetWhitelist(config.pos(), config.dimension(),
                    selected.stream().map(UUID::toString).collect(Collectors.joining("\n")), String.join("\n", selectedTeams)));
            close();
        }).dimensions(left + 12, top + 202, 139, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(left + 159, top + 202, 139, 20).build());
        refresh();
    }
    private void addInput() {
        String value = input.getText().trim();
        if (teamTab) {
            if (!teamCandidates.contains(value)) { error = Text.translatable("screen.accesscontrol.invalid_team"); return; }
            if (selectedTeams.size() >= BiometricWhitelist.MAX_TEAMS && !selectedTeams.contains(value)) {
                error = Text.translatable("screen.accesscontrol.teams_full"); return;
            }
            selectedTeams.add(value);
            input.setText("");
            error = Text.empty();
            page = new ArrayList<>(teamCandidates).indexOf(value) / PAGE_SIZE;
            refresh();
            return;
        }
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
    private void switchTab(boolean teams) {
        teamTab = teams;
        page = 0;
        error = Text.empty();
        input.setText("");
        refresh();
    }
    private void refresh() {
        var entries = new ArrayList<>(candidates.entrySet());
        var names = new ArrayList<>(teamCandidates);
        int count = teamTab ? names.size() : entries.size();
        page = Math.max(0, Math.min(page, Math.max(0, (count - 1) / PAGE_SIZE)));
        playersTab.active = teamTab;
        teamsTab.active = !teamTab;
        input.setMaxLength(teamTab ? 64 : 36);
        input.setPlaceholder(Text.translatable(teamTab ? "screen.accesscontrol.team_input" : "screen.accesscontrol.player_input"));
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            rows[i].visible = index < count;
            if (rows[i].visible) {
                if (teamTab) {
                    String name = names.get(index);
                    rowTeams[i] = name;
                    rows[i].setMessage(Text.literal((selectedTeams.contains(name) ? "[+] " : "[ ] ") + name));
                } else {
                    var entry = entries.get(index);
                    rowIds[i] = entry.getKey();
                    rows[i].setMessage(Text.literal((selected.contains(entry.getKey()) ? "[+] " : "[ ] ") + entry.getValue()));
                }
            }
        }
        previous.active = page > 0;
        next.active = (page + 1) * PAGE_SIZE < count;
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = width / 2 - 155, top = height / 2 - 117;
        context.fill(left - 2, top - 2, left + 312, top + 234, 0xFF59E0CA);
        context.fill(left, top, left + 310, top + 232, 0xFF111C27);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 9, 0xFFE5F7FF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable(teamTab ? "screen.accesscontrol.teams_count" : "screen.accesscontrol.whitelist_count",
                teamTab ? selectedTeams.size() : selected.size()), width / 2, top + 48, 0xFF67E2CD);
        context.drawCenteredTextWithShadow(textRenderer, error.getString().isEmpty() ? Text.literal((page + 1) + " / "
                + Math.max(1, ((teamTab ? teamCandidates.size() : candidates.size()) + PAGE_SIZE - 1) / PAGE_SIZE)) : error, width / 2, top + 158, 0xFFFFCE79);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
}
