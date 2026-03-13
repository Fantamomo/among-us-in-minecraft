package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

object MeetingActionElement {
    class Called(val caller: UUID, val reason: MeetingManager.MeetingReason, val body: UUID?) : IdActionElement("meeting_called") {
        override fun toJson() = buildJsonObject {
            put("caller", caller.toString())
            put("reason", reason.name)
            if (body != null) put("body", body.toString())
        }
    }

    class VoteFor(val voter: UUID, val target: UUID, val mayorVote: Boolean) : IdActionElement("meeting_vote_for") {
        override fun toJson() = buildJsonObject {
            put("voter", voter.toString())
            put("target", target.toString())
            put("mayorVote", mayorVote)
        }
    }

    class VoteSkip(val voter: UUID, val mayorVote: Boolean) : IdActionElement("meeting_vote_skip") {
        override fun toJson() = buildJsonObject {
            put("voter", voter.toString())
            put("mayorVote", mayorVote)
        }
    }

    class MeetingResult(val ejected: UUID?) : IdActionElement("meeting_result") {
        override fun toJson() = buildJsonObject {
            put("ejected", ejected?.toString())
        }
    }
}