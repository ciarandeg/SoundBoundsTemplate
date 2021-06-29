package com.ciarandegroot.soundbounds.forge.common.item

import com.ciarandegroot.soundbounds.SoundBounds
import com.ciarandegroot.soundbounds.forge.SoundBoundsForge
import com.ciarandegroot.soundbounds.forge.common.network.PosMarkerUpdateMessage
import com.ciarandegroot.soundbounds.server.ui.cli.PosMarker
import io.netty.buffer.Unpooled
import me.shedaniel.architectury.networking.NetworkManager
import me.shedaniel.architectury.platform.Platform
import me.shedaniel.architectury.utils.Env.CLIENT
import me.shedaniel.architectury.utils.Env.SERVER
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.item.NetherStarItem
import net.minecraft.network.PacketByteBuf
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.ActionResult
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class Baton(settings: Settings?) : NetherStarItem(settings) {
    companion object {
        private const val cooldown = 1000
    }

    private enum class Corner(var pos: BlockPos = BlockPos(0, 0, 0), var timestamp: Long = 0) {
        FIRST,
        SECOND
    }

    override fun canMine(state: BlockState?, world: World?, pos: BlockPos?, miner: PlayerEntity?): Boolean {
        return false
    }

    override fun onEntitySwing(stack: ItemStack?, entity: LivingEntity?): Boolean {
        setCorner(Corner.FIRST)
        return super.onEntitySwing(stack, entity)
    }

    override fun useOnBlock(context: ItemUsageContext?): ActionResult {
        setCorner(Corner.SECOND)
        return super.useOnBlock(context)
    }

    private fun setCorner(corner: Corner) {
        val trace: HitResult? = when (Platform.getEnvironment()) {
            CLIENT -> MinecraftClient.getInstance().crosshairTarget
            SERVER -> null
            null -> null
        }
        if (trace !is BlockHitResult) return

        // useOnBlock (the right-click handler) gets called from several threads at once
        synchronized(this) {
            val currentTime = System.currentTimeMillis()
            val isCoolingDown = currentTime - corner.timestamp < cooldown
            val isNewBlock = !trace.blockPos.equals(corner.pos)
            if (!isCoolingDown || isNewBlock) {
                SoundBounds.LOGGER.info("Set corner $corner to ${trace.blockPos.toImmutable()}")
                corner.pos = trace.blockPos
                corner.timestamp = currentTime

                NetworkManager.sendToServer(
                    SoundBoundsForge.SOUNDBOUNDS_POS_MARKER_UPDATE_CHANNEL,
                    PosMarkerUpdateMessage.buildBuffer(when (corner) {
                        Corner.FIRST -> PosMarker.FIRST
                        Corner.SECOND -> PosMarker.SECOND
                    }, corner.pos))
            }
        }
    }

    override fun appendTooltip(
        itemStack: ItemStack?,
        world: World?,
        tooltip: MutableList<Text>?,
        tooltipContext: TooltipContext?
    ) {
        tooltip?.replaceAll { TranslatableText("Bounds Baton") }
    }
}