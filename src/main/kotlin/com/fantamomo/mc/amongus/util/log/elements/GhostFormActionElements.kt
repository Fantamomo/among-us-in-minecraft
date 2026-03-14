package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.util.log.IdActionElement
import java.util.*

object GhostFormActionElements {
    class Enter(val player: UUID) : IdActionElement("enter_ghost_form")

    class Exit(val player: UUID) : IdActionElement("exit_ghost_form")
}