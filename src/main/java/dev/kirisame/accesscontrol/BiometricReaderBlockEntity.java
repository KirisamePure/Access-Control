package dev.kirisame.accesscontrol;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;

/** UUIDs authorize identities; saved names are display labels only. */
public final class BiometricReaderBlockEntity extends ReaderBlockEntity {
    private static final Codec<Map<String, String>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING);
    private final Map<UUID, String> players = new LinkedHashMap<>();
    private final Set<String> teams = new LinkedHashSet<>();
    public BiometricReaderBlockEntity(BlockPos pos, BlockState state) { super(AccessControl.BIOMETRIC_ENTITY, pos, state); }
    public boolean allows(UUID player) { return players.containsKey(player); }
    public boolean allows(PlayerEntity player) {
        var team = player.getScoreboardTeam();
        return allows(player.getUuid()) || team != null && teams.contains(team.getName());
    }
    public Set<String> teams() { return Collections.unmodifiableSet(teams); }
    public void setTeams(Set<String> names) {
        if (names.size() > BiometricWhitelist.MAX_TEAMS || names.stream().anyMatch(name -> !BiometricWhitelist.validTeamName(name)))
            throw new IllegalArgumentException("Invalid team whitelist");
        teams.clear();
        teams.addAll(names);
        markDirty();
    }
    public Map<UUID, String> players() { return Collections.unmodifiableMap(players); }
    public void setPlayers(Map<UUID, String> entries) {
        if (entries.size() > BiometricWhitelist.MAX_PLAYERS) throw new IllegalArgumentException("Too many whitelist entries");
        players.clear();
        players.putAll(entries);
        markDirty();
    }
    @Override protected void readData(ReadView view) {
        super.readData(view);
        players.clear();
        teams.clear();
        view.read("allowed_teams", Codec.STRING.listOf()).orElse(List.of()).stream()
                .filter(BiometricWhitelist::validTeamName).limit(BiometricWhitelist.MAX_TEAMS).forEach(teams::add);
        view.read("allowed_players", CODEC).orElse(Map.of()).forEach((uuid, name) -> {
            if (players.size() < BiometricWhitelist.MAX_PLAYERS) {
                try { players.put(UUID.fromString(uuid), name); } catch (IllegalArgumentException ignored) { }
            }
        });
    }
    @Override protected void writeData(WriteView view) {
        super.writeData(view);
        Map<String, String> entries = new LinkedHashMap<>();
        players.forEach((uuid, name) -> entries.put(uuid.toString(), name));
        view.put("allowed_players", CODEC, entries);
        view.put("allowed_teams", Codec.STRING.listOf(), List.copyOf(teams));
    }
}
