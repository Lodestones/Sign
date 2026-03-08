package gg.lode.sign.entities;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientEntity {
    private final int entityId;
    private final UUID uuid;

    private final EntityType type;
    private Location location;

    public ClientEntity(org.bukkit.entity.EntityType type, Location location) {
        this.entityId = SpigotReflectionUtil.generateEntityId();
        this.uuid = UUID.randomUUID();
        this.type = SpigotConversionUtil.fromBukkitEntityType(type);
        this.location = location;
    }

    public int getEntityId() {
        return entityId;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public List<EntityData<?>> getEntityData() {
        return new ArrayList<>();
    }

    public PacketWrapper<?> createSpawnPacket() {
        return new WrapperPlayServerSpawnEntity(
                this.entityId,
                this.uuid,
                this.type,
                SpigotConversionUtil.fromBukkitLocation(this.location),
                this.location.getYaw(),
                0,
                null
        );
    }

    public PacketWrapper<?> createMetadataPacket() {
        return new WrapperPlayServerEntityMetadata(
                this.entityId,
                this.getEntityData()
        );
    }

    public PacketWrapper<?> createDestroyPacket() {
        return new WrapperPlayServerDestroyEntities(this.entityId);
    }

    public PacketWrapper<?> createMountPacket(Entity entity) {
        return new WrapperPlayServerSetPassengers(
                entity.getEntityId(),
                new int[]{this.entityId}
        );
    }

    public static PacketWrapper<?> createMountPacket(Entity entity, List<? extends ClientEntity> passengers) {
        int[] ids = new int[passengers.size()];
        for (int i = 0; i < passengers.size(); i++) {
            ids[i] = passengers.get(i).getEntityId();
        }
        return new WrapperPlayServerSetPassengers(entity.getEntityId(), ids);
    }

    public void spawn(Player viewer) {
        sendPacket(createSpawnPacket(), viewer);
    }

    public void update(Player viewer) {
        sendPacket(createMetadataPacket(), viewer);
    }

    public void despawn(Player viewer) {
        sendPacket(createDestroyPacket(), viewer);
    }

    public void mount(Entity entity, Player viewer) {
        sendPacket(createMountPacket(entity), viewer);
    }

    public static void sendBundle(Player viewer, List<PacketWrapper<?>> packets) {
        var manager = PacketEvents.getAPI().getPlayerManager();
        for (PacketWrapper<?> packet : packets) {
            manager.sendPacketSilently(viewer, packet);
        }
    }

    private void sendPacket(PacketWrapper<?> packet, Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }
}
