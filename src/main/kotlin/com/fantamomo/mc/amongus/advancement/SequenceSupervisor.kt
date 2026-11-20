package com.fantamomo.mc.amongus.advancement

/**
 * Runs the attempts of one runner for one player during one game.
 *
 * - There is always exactly one attempt waiting for its first action (unless disabled or [Listener.canStart] is false).
 * - When an attempt completes its first action, a new waiting attempt is started.
 * - When an attempt ends, a new waiting attempt is started if there is none.
 * - When an attempt succeeds, all other attempts are stopped and [Listener.onCompleted] is called.
 * - An attempt that ends without ever suspending would restart forever, so the runner is disabled instead.
 *
 * @author Fantamomo
 * @since 2.5-SNAPSHOT
 */
@ExperimentalAdvancementApi
internal class SequenceSupervisor(
    val runner: AdvancementSequenceRunner,
    private val host: SequenceHost,
    private val listener: Listener,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) : SequenceCallbacks {

    interface Listener {
        /** Checked before every new attempt (e.g. "is the advancement still not completed"). */
        fun canStart(): Boolean = true

        /** An attempt that got past its first action (or succeeded) ended. */
        fun onAttemptFinished(sequence: AdvancementSequence, result: Result<Unit>) {}

        /** An attempt ended with an exception that is not a [SequenceCancellationException], i.e. a bug. */
        fun onError(sequence: AdvancementSequence, exception: Throwable) {}

        /** An attempt succeeded, the advancement should be awarded. */
        fun onCompleted(sequence: AdvancementSequence) {}

        /** Something is wrong with the runner, [reason] should be logged. */
        fun onWarning(reason: String) {}
    }

    private val running = mutableListOf<AdvancementSequence>()

    /** The attempts that are currently running. */
    val attempts: List<AdvancementSequence> get() = running

    var isStopped = false
        private set

    var isDisabled = false
        private set

    private var warnedAboutLimit = false

    fun start() = ensureWaitingAttempt()

    /** Stops all attempts, no new attempts are started afterward. */
    fun stop() {
        if (isStopped) return
        isStopped = true
        for (sequence in running.toList()) sequence.stop()
        running.clear()
    }

    private fun ensureWaitingAttempt() {
        if (isStopped || isDisabled) return
        if (running.any { !it.isFirstActionCompleted }) return
        if (!listener.canStart()) return
        if (running.size >= maxAttempts) {
            if (!warnedAboutLimit) {
                warnedAboutLimit = true
                listener.onWarning("More than $maxAttempts attempts are running at the same time, no new attempts are started until some finished")
            }
            return
        }
        val sequence = AdvancementSequence(runner, host, this)
        running += sequence
        sequence.start()
    }

    override fun onFirstAction(sequence: AdvancementSequence) {
        // if the first action was done without waiting, every new attempt would do the same immediately
        if (sequence.hasSuspended) ensureWaitingAttempt()
    }

    override fun onFinished(sequence: AdvancementSequence, result: Result<Unit>) {
        running.remove(sequence)
        val exception = result.exceptionOrNull()
        if (exception != null && exception !is SequenceCancellationException) listener.onError(sequence, exception)
        if (sequence.isFirstActionCompleted || result.isSuccess) listener.onAttemptFinished(sequence, result)
        if (isStopped) return

        if (result.isSuccess) {
            listener.onCompleted(sequence)
            stop()
            return
        }
        if (!sequence.hasSuspended) {
            isDisabled = true
            listener.onWarning("The runner ended without waiting for anything (${exception}), it is disabled to prevent an endless loop")
            return
        }
        ensureWaitingAttempt()
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 32
    }
}
