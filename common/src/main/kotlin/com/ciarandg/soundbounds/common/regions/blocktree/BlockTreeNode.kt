package com.ciarandg.soundbounds.common.regions.blocktree

import net.minecraft.util.math.BlockPos
import java.lang.IllegalStateException
import kotlin.math.max
import kotlin.math.min

internal class BlockTreeNode private constructor (
    private val minPos: BlockPos,
    private val maxPos: BlockPos,
    private var color: Color
) {
    private var greyData = GreyData() // should only be accessed when node is grey
    private val capacity = run {
        val width = maxPos.x - minPos.x + 1
        val height = maxPos.y - minPos.y + 1
        val depth = maxPos.z - minPos.z + 1
        width.toLong() * height.toLong() * depth.toLong()
    }
    private val isAtomic = minPos == maxPos

    constructor(block: BlockPos) : this(block, block, Color.BLACK)
    constructor(node: BlockTreeNode, outsider: BlockPos) : this(
        BlockPos(min(node.minPos.x, outsider.x), min(node.minPos.y, outsider.y), min(node.minPos.z, outsider.z)),
        BlockPos(max(node.maxPos.x, outsider.x), max(node.maxPos.y, outsider.y), max(node.maxPos.z, outsider.z)),
        Color.GREY
    ) {
        val itr = node.iterator()
        while (itr.hasNext()) {
            add(itr.next())
        }
        add(outsider)
    }

    fun blockCount(): Int = when (color) {
        Color.WHITE -> 0
        Color.BLACK -> capacity.toInt()
        Color.GREY -> greyData.children.value.sumOf { it.blockCount() }
    }

    fun contains(element: BlockPos): Boolean = when (color) {
        Color.WHITE -> false
        Color.BLACK -> canContain(element)
        Color.GREY -> greyData.children.value.any { it.contains(element) }
    }

    fun canContain(block: BlockPos) =
        minPos.x <= block.x && minPos.y <= block.y && minPos.z <= block.z &&
            maxPos.x >= block.x && maxPos.y >= block.y && maxPos.z >= block.z

    fun add(element: BlockPos): Boolean = when (color) {
            Color.WHITE -> if (isAtomic) { becomeBlack(); true } else { becomeGreyWhiteChildren(); add(element) }
            Color.BLACK -> false
            Color.GREY -> {
                val result = greyData.findCorrespondingNode(element).add(element)
                if (greyData.children.value.all { it.color == Color.BLACK }) becomeBlack()
                result
            }
        }

    fun remove(element: BlockPos): Boolean = when (color) {
        Color.WHITE -> false
        Color.BLACK -> if (isAtomic) { becomeWhite(); true } else { becomeGreyBlackChildren(); remove(element) }
        Color.GREY -> {
            val result = greyData.findCorrespondingNode(element).remove(element)
            if (greyData.children.value.all { it.color == Color.WHITE }) becomeWhite()
            result
        }
    }

    fun iterator(): MutableIterator<BlockPos> = when (color) {
        Color.WHITE -> whiteIterator
        Color.BLACK -> object : MutableIterator<BlockPos> {
            var current: BlockPos? = null
            val totalBlocks = blockCount()
            var index = 0

            val width = maxPos.x - minPos.x + 1
            val height = maxPos.y - minPos.y + 1

            override fun hasNext() = index < totalBlocks

            override fun next(): BlockPos {
                if (!hasNext()) throw IllegalStateException("Can't get next when hasNext is false")
                val next = indexToPos(index)
                index++
                current = next
                return current ?: throw ConcurrentModificationException()
            }

            override fun remove() {
                current?.let { remove(it) }
                    ?: throw IllegalStateException("Attempted to remove a value that doesn't exist")
                current = null
            }

            private fun indexToPos(i: Int): BlockPos {
                // Stolen from here because I'm lazy: https://stackoverflow.com/a/34363187
                val z = i / (width * height)
                val j = i - (z * width * height)
                val y = j / width
                val x = j % width
                return BlockPos(minPos.x + x, minPos.y + y, minPos.z + z)
            }
        }
        Color.GREY -> object : MutableIterator<BlockPos> {
            val children = greyData.children.value.map { it.iterator() }
            var current: BlockPos? = null

            override fun hasNext() = children.any { it.hasNext() }

            override fun next(): BlockPos {
                val result = children.first { it.hasNext() }.next()
                current = result
                return result
            }

            override fun remove() {
                current?.let { remove(it) }
                    ?: throw IllegalStateException("Attempted to remove a value that doesn't exist")
                current = null
            }
        }
    }

    private fun becomeWhite() {
        color = Color.WHITE
        greyData = GreyData() // reset so GC to pick up old children
    }
    private fun becomeBlack() {
        color = Color.BLACK
        greyData = GreyData() // reset so GC can pick up old children
    }
    private fun becomeGreyWhiteChildren() {
        color = Color.GREY
        greyData = GreyData(Color.WHITE)
    }
    private fun becomeGreyBlackChildren() {
        color = Color.GREY
        greyData = GreyData(Color.BLACK)
    }

    enum class Color { WHITE, BLACK, GREY }

    inner class GreyData(childColor: Color = Color.WHITE) {
        private val middle = BlockPos(
            (maxPos.x + minPos.x) / 2,
            (maxPos.y + minPos.y) / 2,
            (maxPos.z + minPos.z) / 2
        )
        val children = lazy { if (capacity > 8) partitionIntoNonAtomic(childColor) else partitionIntoAtomic(childColor) }

        fun findCorrespondingNode(block: BlockPos): BlockTreeNode = children.value.first { it.canContain(block) }

        // since we're dealing with discrete blocks, the area must be split with a bias toward one particular corner
        // otherwise, there would be overlaps or gaps between our children's areas
        private fun partitionIntoNonAtomic(childColor: Color): List<BlockTreeNode> {
            val westDownNorth = genNode(BlockPos(minPos.x, minPos.y, minPos.z), middle, childColor)
            val eastDownNorth = genNode(BlockPos(maxPos.x, minPos.y, minPos.z), middle.east(), childColor)
            val westUpNorth = genNode(BlockPos(minPos.x, maxPos.y, minPos.z), middle.up(), childColor)
            val eastUpNorth = genNode(BlockPos(maxPos.x, maxPos.y, minPos.z), middle.east().up(), childColor)
            val westDownSouth = genNode(BlockPos(minPos.x, minPos.y, maxPos.z), middle.south(), childColor)
            val eastDownSouth = genNode(BlockPos(maxPos.x, minPos.y, maxPos.z), middle.east().south(), childColor)
            val westUpSouth = genNode(BlockPos(minPos.x, maxPos.y, maxPos.z), middle.up().south(), childColor)
            val eastUpSouth = genNode(BlockPos(maxPos.x, maxPos.y, maxPos.z), middle.east().up().south(), childColor)
            return listOf(westDownNorth, eastDownNorth, westUpNorth, eastUpNorth, westDownSouth, eastDownSouth, westUpSouth, eastUpSouth)
        }

        private fun partitionIntoAtomic(childColor: Color): List<BlockTreeNode> {
            val everyBlock = ArrayList<BlockPos>()
            for (x in minPos.x..maxPos.x)
                for (y in minPos.y..maxPos.y)
                    for (z in minPos.z..maxPos.z) {
                        val block = BlockPos(x, y, z)
                        everyBlock.add(block)
                    }
            return everyBlock.map { genNode(it, it, childColor) }
        }

        private fun genNode(corner1: BlockPos, corner2: BlockPos, childColor: Color): BlockTreeNode =
            with(justifiedCornerPair(corner1, corner2)) { BlockTreeNode(min, max, childColor) }
    }

    companion object {
        private val whiteIterator = object : MutableIterator<BlockPos> {
            override fun hasNext() = false
            override fun next() = throw IllegalStateException("White node iterator never has a next value")
            override fun remove() = throw IllegalStateException("White node iterator has no values to remove")
        }

        private fun justifiedCornerPair(corner1: BlockPos, corner2: BlockPos) = object {
            val min = BlockPos(min(corner1.x, corner2.x), min(corner1.y, corner2.y), min(corner1.z, corner2.z))
            val max = BlockPos(max(corner1.x, corner2.x), max(corner1.y, corner2.y), max(corner1.z, corner2.z))
        }
    }
}
