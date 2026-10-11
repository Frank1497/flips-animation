package dev.franklyn.foldscenes.scene

object Scenes {
    val all: List<Scene> = listOf(SceneA(), SceneB(), SceneC(), SceneD(), SceneF())

    /** Scene with [id], or scene A when [id] is unknown. */
    fun byId(id: String): Scene = all.firstOrNull { it.id == id } ?: all.first()
}
