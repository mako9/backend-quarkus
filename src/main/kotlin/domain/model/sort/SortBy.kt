package domain.model.sort

import org.apache.commons.text.CaseUtils
import kotlin.reflect.full.isSubclassOf

interface SortBy {
    val value: String
        get() {
            if (!this::class.isSubclassOf(Enum::class)) return ""
            val enum = this as Enum<*>
            return CaseUtils.toCamelCase(enum.name, false, '_')
        }
}