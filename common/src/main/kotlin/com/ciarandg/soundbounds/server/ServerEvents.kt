package com.ciarandg.soundbounds.server

import com.ciarandg.soundbounds.SoundBounds
import com.ciarandg.soundbounds.common.network.RegionUpdateMessageS2C
import com.ciarandg.soundbounds.common.regions.WorldRegionState
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.networking.NetworkManager
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity

object ServerEvents {
    fun register() {
        registerMVC()
        registerMetaHashCheck()
    }

    private fun registerMVC() {
        PlayerEvent.PLAYER_JOIN.register { player -> sendWorldRegions(player) }
        PlayerEvent.CHANGE_DIMENSION.register { player, _, _ -> sendWorldRegions(player) }
    }

    private fun registerMetaHashCheck() {
        PlayerEvent.PLAYER_JOIN.register { player ->
            NetworkManager.sendToPlayer(player, SoundBounds.META_HASH_CHECK_S2C, PacketByteBuf(Unpooled.buffer()))
        }
    }

    private fun sendWorldRegions(player: ServerPlayerEntity) {
        val regions = WorldRegionState.get(player.getWorld()).getAllRegions().map { it.toPair() } ?: listOf()
        NetworkManager.sendToPlayer(
            player,
            SoundBounds.UPDATE_REGIONS_CHANNEL_S2C,
            RegionUpdateMessageS2C.buildBufferS2C(true, regions)
        )
    }
}
