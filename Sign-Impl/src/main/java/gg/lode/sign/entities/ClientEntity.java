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
        return createMountPacket(entity, List.of(this));
    }

    /**
     * Mounts this entity on a bare entity id.
     *
     * <p>For a vehicle the server does not have — a packet-built player in a replay, say. Nothing is
     * merged in, unlike the {@link Entity} form above: an entity the server has never heard of has no
     * server-side passengers to preserve, and asking for them is not possible anyway.
     */
    public PacketWrapper<?> createMountPacket(int vehicleEntityId) {
        return new WrapperPlayServerSetPassengers(vehicleEntityId, new int[]{this.entityId});
    }

    /** Sends that mount to one viewer. */
    public void mount(int vehicleEntityId, Player viewer) {
        sendPacket(createMountPacket(vehicleEntityId), viewer);
    }

    /**
     * Mounts several client entities on a bare entity id, in one packet.
     *
     * <p>It has to be one packet. SET_PASSENGERS replaces the vehicle's whole passenger list, so sending
     * one per passenger leaves only the last of them riding and the rest sitting wherever they spawned —
     * which for a multi-line nametag means every line but the bottom one disappears.
     */
    public static PacketWrapper<?> createMountPacket(int vehicleEntityId,
                                                    List<? extends ClientEntity> passengers) {
        int[] ids = new int[passengers.size()];
        int index = 0;
        for (ClientEntity passenger : passengers) ids[index++] = passenger.getEntityId();
        return new WrapperPlayServerSetPassengers(vehicleEntityId, ids);
    }

    /**
     * Builds a SET_PASSENGERS packet mounting the given client-side entities on
     * {@code entity}. The packet is absolute — it replaces the client's whole
     * passenger list for that vehicle — so the vehicle's real server-side
     * passengers (e.g. a player sitting on another player via GSit) are merged
     * in first. Omitting them silently unmounts the real rider client-side: they
     * stop following the vehicle and snap to its true position on dismount.
     * Real passengers keep the lower indices so their seat offsets are unchanged.
     */
    public static PacketWrapper<?> createMountPacket(Entity entity, List<? extends ClientEntity> passengers) {
        List<Entity> realPassengers = entity.getPassengers();
        int[] ids = new int[realPassengers.size() + passengers.size()];
        int index = 0;
        for (Entity realPassenger : realPassengers) {
            ids[index++] = realPassenger.getEntityId();
        }
        for (ClientEntity passenger : passengers) {
            ids[index++] = passenger.getEntityId();
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
