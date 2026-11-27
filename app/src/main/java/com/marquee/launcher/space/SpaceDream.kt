package com.marquee.launcher.space

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Marquee's screensaver. Any button wakes the TV; the button press isn't passed on to whatever is underneath.
 * Compose needs a lifecycle, which a DreamService doesn't have, so this provides one.
 */
class SpaceDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    /** Android tears a dream down in either order (window first or dreaming first), so never step back out of DESTROYED. */
    private fun moveTo(state: Lifecycle.State) {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = state
    }

    override fun onCreate() {
        super.onCreate()
        saved.performRestore(null)
        moveTo(Lifecycle.State.CREATED)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SpaceDream)
            setViewTreeSavedStateRegistryOwner(this@SpaceDream)
            setContent { SpaceScreen() }
        }
        setContentView(view)
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        moveTo(Lifecycle.State.RESUMED)
    }

    override fun onDreamingStopped() {
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) moveTo(Lifecycle.State.CREATED)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        moveTo(Lifecycle.State.DESTROYED)
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        moveTo(Lifecycle.State.DESTROYED)
        super.onDestroy()
    }
}
