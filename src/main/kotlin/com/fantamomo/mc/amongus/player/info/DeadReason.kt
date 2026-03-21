package com.fantamomo.mc.amongus.player.info

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import net.kyori.adventure.text.Component

sealed interface DeadReason {

    val name: Component

    /** If the player leaves the server */
    data object Disconnected : DeadReason {
        override val name = Component.translatable("dead.reason.disconnected")
    }

    /** If the player is voted out */
    data object Ejected : DeadReason {
        override val name = Component.translatable("dead.reason.ejected")
    }

    /** If the player is killed by an admin via the `/aua game kill` command */
    data object Command : DeadReason {
        override val name = Component.translatable("dead.reason.command")
    }

    /** An unknown reason */
    data object Unknown : DeadReason {
        override val name = Component.translatable("dead.reason.unknown")
    }

    /** If the Sheriff kills a crewmate */
    data object Suicide : DeadReason {
        override val name = Component.translatable("dead.reason.suicide")
    }

    /** If the player is killed by a player */
    data class Murdered(val murderer: AmongUsPlayer) : DeadReason {
        override val name = textComponent {
            translatable("dead.reason.murdered") {
                args {
                    component(
                        "murderer",
                        Component.text(
                            murderer.name,
                            murderer.role.definition.team.textColor
                        )
                    )
                }
            }
        }
    }
}