package cn.zbx1425.worldcomment.forge;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public class ClientPlatformImpl {

    public static void registerKeyBinding(KeyMapping keyMapping) {
        KeyMappingRegistry.register(keyMapping);
    }

    public static void registerNetworkReceiver(ResourceLocation resourceLocation, Consumer<FriendlyByteBuf> consumer) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, resourceLocation, (packet, context) -> consumer.accept(packet));
    }

    public static void registerPlayerJoinEvent(Consumer<LocalPlayer> consumer) {
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(consumer::accept);
    }

    public static void registerTickEvent(Consumer<Minecraft> consumer) {
        ClientTickEvent.CLIENT_PRE.register(consumer::accept);
    }

    public static void sendPacketToServer(ResourceLocation id, FriendlyByteBuf packet) {
        NetworkManager.sendToServer(id, packet);
    }
}
