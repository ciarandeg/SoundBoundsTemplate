package com.ciarandg.soundbounds.server.ui.cli

import com.ciarandg.soundbounds.common.regions.RegionData
import com.ciarandg.soundbounds.common.ui.cli.CommandNode
import com.ciarandg.soundbounds.common.ui.cli.command.nodes.RootNode
import com.ciarandg.soundbounds.common.util.Paginator
import com.ciarandg.soundbounds.common.util.PlaylistType
import com.ciarandg.soundbounds.plus
import com.ciarandg.soundbounds.server.metadata.ServerMetaState
import com.ciarandg.soundbounds.server.ui.PlayerView
import com.ciarandg.soundbounds.server.ui.cli.Colors.ModBadge.formatModBadge
import com.ciarandg.soundbounds.server.ui.cli.Colors.artistText
import com.ciarandg.soundbounds.server.ui.cli.Colors.blockPosText
import com.ciarandg.soundbounds.server.ui.cli.Colors.bodyText
import com.ciarandg.soundbounds.server.ui.cli.Colors.listPosText
import com.ciarandg.soundbounds.server.ui.cli.Colors.playlistTypeText
import com.ciarandg.soundbounds.server.ui.cli.Colors.posMarkerText
import com.ciarandg.soundbounds.server.ui.cli.Colors.priorityText
import com.ciarandg.soundbounds.server.ui.cli.Colors.quantityText
import com.ciarandg.soundbounds.server.ui.cli.Colors.regionNameText
import com.ciarandg.soundbounds.server.ui.cli.Colors.songTitleText
import com.ciarandg.soundbounds.server.ui.cli.Colors.volumeText
import com.ciarandg.soundbounds.server.ui.cli.help.HelpGenerator
import com.ciarandg.soundbounds.server.ui.cli.help.HelpTreeNode
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.LiteralText
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

class CLIServerPlayerView(override val owner: PlayerEntity) : PlayerView {
    private val helpTree = HelpTreeNode(RootNode)

    init {
        entityViews[owner] = this
    }

    companion object {
        private val entityViews: HashMap<Entity, CLIServerPlayerView> = HashMap()
        fun getEntityView(e: Entity) = entityViews[e]
        private val modBadge: Text = formatModBadge("SoundBounds")
    }

    fun showHelp(paginator: Paginator, root: CommandNode = RootNode) {
        send(
            paginator.paginate("SoundBounds Help", HelpGenerator.readOut(findHelpNode(root) ?: helpTree)),
        )
    }

    private fun findHelpNode(commandNode: CommandNode, helpTree: HelpTreeNode = this.helpTree): HelpTreeNode? {
        if (commandNode === helpTree.commandNode) return helpTree
        for (c in helpTree.children) {
            val h = findHelpNode(commandNode, c)
            if (h != null) return h
        }
        return null
    }

    override fun showNowPlaying(nowPlaying: String) {
        val meta = ServerMetaState.get().meta
        val songMeta = meta.songs[nowPlaying]
        if (songMeta != null) sendWithBadge(
            bodyText("Now playing: ") +
                artistText(songMeta.artist, songMeta.featuring) + bodyText(" - ") + songTitleText(songMeta.title)
        ) else sendError("Currently playing song does not have server-synced metadata")
    }

    override fun notifyMetaMismatch() = sendError(
        "Client metadata does not match server metadata. " +
            "Update and enable your resource pack, " +
            "or sync your client's metadata to the server."
    )

    override fun showNoSongPlaying() =
        sendWithBadge(bodyText("No song currently playing"))

    override fun notifyPosMarkerSet(marker: PosMarker, pos: BlockPos) = sendWithBadge(
        bodyText("Set marker ") + posMarkerText(marker) + bodyText(" to ") + blockPosText(pos)
    )

    override fun showRegionList(regions: List<Map.Entry<String, RegionData>>, paginator: Paginator) = send(
        paginator.paginate(
            "Region List",
            regions.mapIndexed { index, entry ->
                val n = index + 1
                val name = entry.key
                val playlistType = entry.value.playlistType
                val priority = entry.value.priority
                listPosText(n) + bodyText(". ") + regionNameText(name) + bodyText(" - ") +
                    playlistTypeText(playlistType) + bodyText(", priority ") + priorityText(priority)
            }
        )
    )

    override fun showRegionProximities(
        regions: List<Pair<Map.Entry<String, RegionData>, Double>>,
        paginator: Paginator
    ) = send(
        paginator.paginate(
            "Nearby Regions",
            regions.mapIndexed { index, entry ->
                val n = index + 1
                val name = entry.first.key
                val priority = entry.first.value.priority
                val distance = entry.second.toInt()
                listPosText(n) + bodyText(". ") + regionNameText(name) + bodyText(" - ") +
                    bodyText("priority ") + priorityText(priority) +
                    bodyText(", $distance ${if (distance == 1) "block" else "blocks"} away")
            }
        )
    )

    override fun notifyMetadataSynced() {
        val meta = ServerMetaState.get().meta
        sendWithBadge(
            bodyText("Successfully synced metadata: ") +
                quantityText(meta.composers.size) + bodyText(" composers, ") +
                quantityText(meta.groups.size) + bodyText(" groups, ") +
                quantityText(meta.songs.size) + bodyText(" songs")
        )
    }

    override fun notifyMetadataSyncFailed() {
        sendError("Failed to sync metadata")
    }

    override fun notifyRegionCreated(name: String, priority: Int) {
        sendWithBadge(
            bodyText("Created region ") + regionNameText(name) +
                bodyText(", priority ") + priorityText(priority)
        )
    }
    override fun notifyRegionDestroyed(name: String) = sendWithBadge(
        bodyText("Region ") + regionNameText(name) + bodyText(" destroyed")
    )

    override fun notifyRegionRenamed(from: String, to: String) = sendWithBadge(
        bodyText("Region ") + regionNameText(from) + bodyText(" renamed to ") + regionNameText(to)
    )

    override fun notifyRegionOverlaps(region1: String, region2: String, overlaps: Boolean) {} // TODO
    override fun showRegionInfo(regionName: String, data: RegionData) {
        sendWithBadge(
            bodyText("Region ") + regionNameText(regionName) +
                bodyText(": type ") + playlistTypeText(data.playlistType) +
                bodyText(", song count ") + quantityText(data.playlist.size) +
                bodyText(", bounds count ") + quantityText(data.volumes.size)
        )
    }
    override fun notifyRegionPrioritySet(name: String, oldPriority: Int, newPriority: Int) = sendWithBadge(
        bodyText("Region ") + regionNameText(name) + bodyText(" priority changed from ") +
            priorityText(oldPriority) + bodyText(" to ") + priorityText(newPriority)
    )

    override fun notifyRegionPlaylistTypeSet(name: String, from: PlaylistType, to: PlaylistType) = sendWithBadge(
        if (from == to) bodyText("Region ") + regionNameText(name) +
            bodyText(" is already of type ") + playlistTypeText(to) + bodyText("!")
        else bodyText("Region ") + regionNameText(name) + bodyText(" changed from type ") +
            playlistTypeText(from) + bodyText(" to type ") + playlistTypeText(to)
    )

    override fun notifyRegionVolumeAdded(regionName: String, volume: Pair<BlockPos, BlockPos>) = sendWithBadge(
        bodyText("Added new volume to ") + regionNameText(regionName) + bodyText(": ") + volumeText(volume)
    )

    override fun notifyRegionVolumeRemoved(regionName: String, position: Int, volume: Pair<BlockPos, BlockPos>) =
        sendWithBadge(
            bodyText("Removed volume from ") + regionNameText(regionName) + bodyText(" at position ") +
                listPosText(position) + bodyText(": ") + volumeText(volume)
        )

    override fun showRegionVolumeList(regionName: String, volumes: List<Pair<BlockPos, BlockPos>>, paginator: Paginator) =
        send(
            paginator.paginate(
                "Volumes in $regionName",
                volumes.mapIndexed { i, vol ->
                    val pos = i + 1
                    listPosText(pos) + bodyText(". \nCORNER 1: ") + blockPosText(vol.first) +
                        bodyText("\nCORNER 2: ") + blockPosText(vol.second) +
                        bodyText(if (pos < volumes.size) "\n" else "")
                }
            ),
        )

    override fun notifyRegionPlaylistSongAdded(regionName: String, song: String, pos: Int) =
        sendWithBadge(
            bodyText("Added song ") + songTitleText(song) + bodyText(" to ") +
                regionNameText(regionName) + bodyText(" at position ") + listPosText(pos)
        )

    override fun notifyRegionPlaylistSongRemoved(regionName: String, song: String, pos: Int) =
        sendWithBadge(
            bodyText("Removed song ") + songTitleText(song) + bodyText(" from ") +
                regionNameText(regionName) + bodyText(" at position ") + listPosText(pos)
        )

    override fun notifyRegionPlaylistSongReplaced(regionName: String, oldSong: String, newSong: String, pos: Int) =
        sendWithBadge(
            bodyText("Replaced song ") + songTitleText(oldSong) + bodyText(" with ") +
                songTitleText(newSong) + bodyText(" in region ") + regionNameText(regionName) +
                bodyText(" at position ") + listPosText(pos)
        )

    override fun showRegionContiguous(regionName: String) {} // TODO

    override fun notifyFailed(reason: PlayerView.FailureReason) = sendError(
        when (reason) {
            PlayerView.FailureReason.POS_MARKERS_MISSING -> "Position markers not set"
            PlayerView.FailureReason.NO_SUCH_REGION -> "Requested region does not exist"
            PlayerView.FailureReason.REGION_NAME_CONFLICT -> "Requested region name is taken"
            PlayerView.FailureReason.VOLUME_INDEX_OOB -> "Requested volume index is out of bounds"
            PlayerView.FailureReason.REGION_MUST_HAVE_VOLUME -> "Requested region only has one volume"
            PlayerView.FailureReason.NO_METADATA_PRESENT -> "No metadata has been provided to server (try /sb sync-meta)"
            PlayerView.FailureReason.NO_SUCH_SONG -> "Requested song ID does not exist"
            PlayerView.FailureReason.SONG_POS_OOB -> "Requested song position is out of bounds"
        }
    )

    private fun sendWithBadge(msg: MutableText) =
        send(modBadge.shallowCopy() + LiteralText(" ") + msg)
    private fun sendError(msg: String) = sendError(LiteralText(msg))
    private fun sendError(msg: MutableText) =
        send(modBadge.shallowCopy() + LiteralText(" ") + msg.formatted(Colors.ERROR))
    private fun send(msg: Text) = owner.sendMessage(msg, false)
}
