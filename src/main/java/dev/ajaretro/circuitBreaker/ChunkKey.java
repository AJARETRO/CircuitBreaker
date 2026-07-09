package dev.ajaretro.circuitBreaker;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import java.util.Objects;
import java.util.UUID;

public final class ChunkKey {
    private final UUID worldUid;
    private final int x;
    private final int z;

    public ChunkKey(UUID worldUid, int x, int z) {
        this.worldUid = worldUid;
        this.x = x;
        this.z = z;
    }

    public UUID getWorldUid() {
        return worldUid;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public Chunk toChunk() {
        World world = Bukkit.getWorld(worldUid);
        if (world == null) return null;
        if (!world.isChunkLoaded(x, z)) return null;
        return world.getChunkAt(x, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkKey chunkKey = (ChunkKey) o;
        return x == chunkKey.x && z == chunkKey.z && Objects.equals(worldUid, chunkKey.worldUid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldUid, x, z);
    }

    @Override
    public String toString() {
        return worldUid.toString() + ":" + x + ":" + z;
    }
}
