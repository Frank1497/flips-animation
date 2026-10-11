package dev.franklyn.foldscenes.data

/**
 * The scene id to use for a [saved] value: [saved] when it is one of [validIds], otherwise the first
 * valid id, or "" when there are none. Pure (the caller passes `Scenes.all.map { it.id }`) so it can be
 * tested on the JVM, where the scenes' Paints are unmocked.
 */
fun resolveSceneId(saved: String?, validIds: List<String>): String =
    if (saved != null && saved in validIds) saved else validIds.firstOrNull().orEmpty()
