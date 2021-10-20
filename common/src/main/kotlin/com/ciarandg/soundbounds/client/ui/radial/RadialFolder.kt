package com.ciarandg.soundbounds.client.ui.radial

import net.minecraft.util.Identifier

open class RadialFolder(
    val getSubGroup: () -> MenuButtonGroup,
    startAngle: Double,
    endAngle: Double,
    hoverTexture: Identifier
) : RadialButton({}, startAngle, endAngle, hoverTexture)
