package domain.model.sort

import kotlin.reflect.full.isSubclassOf

interface SortBy {
    fun getValue(): String {
        if (!this::class.isSubclassOf(Enum::class)) return ""
        val enum = this as Enum<*>
        return enum.name.lowercase()
    }
}