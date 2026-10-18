package com.llk.hexextra.helper

import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.utils.TreeList


fun CastingImage.withStack(stack: TreeList<Iota>): CastingImage {
    return this.copy(stack = stack)
}