package dev.kirisame.accesscontrol;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;

/** UUIDs authorize identities; saved names are display labels only. */
public final class BiometricReaderBlockEntity extends ReaderBlockEntity {
    private static final Codec<Map<String, String>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING);
    private final Map<UUID, String> players = new LinkedHashMap<>();
    public BiometricReaderBlockEntity(BlockPos pos, BlockState state) { super(AccessControl.BIOMETRIC_ENTITY, pos, state); }
    public boolean allows(UUID player) { return players.containsKey(player); }
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
    }
}
