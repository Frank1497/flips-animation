package dev.franklyn.foldscenes.fold

/**
 * The foreground app per display, from TYPE_WINDOW_STATE_CHANGED. Only windows whose class is an Activity
 * of the event's package count: the keyguard, the keyboard, SystemUI panels and in-app dialogs also send
 * this event (with a View class name), and must not replace the app the user is actually in.
 */
class ForegroundTracker(
    private val ownPackage: String,
    private val isActivity: (packageName: String, className: String) -> Boolean,
) {
    private val byDisplay = mutableMapOf<Int, String>()
    private val activityCache = mutableMapOf<Pair<String, String>, Boolean>()

    val foreground: Map<Int, String> get() = byDisplay

    /** Returns true when the display's foreground app changed. */
    fun onWindowStateChanged(displayId: Int, packageName: String?, className: String?): Boolean {
        if (packageName.isNullOrBlank() || className.isNullOrBlank() || packageName == ownPackage) return false
        val key = packageName to className
        if (!activityCache.getOrPut(key) { isActivity(packageName, className) }) return false
        return byDisplay.put(displayId, packageName) != packageName
    }

    fun clear() {
        byDisplay.clear()
    }
}
