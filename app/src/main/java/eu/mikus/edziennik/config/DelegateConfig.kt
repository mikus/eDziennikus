/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-21.
 */

package eu.mikus.edziennik.config

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import kotlin.reflect.KProperty

private val gson = Gson()

inline fun <reified T> BaseConfig.config(name: String? = null, noinline default: () -> T) = ConfigDelegate(
    config = this,
    type = T::class.java,
    nullable = null is T,
    typeToken = object : TypeToken<T>() {},
    defaultFunc = default,
    defaultValue = null,
    fieldName = name,
)

inline fun <reified T> BaseConfig.config(default: T) = ConfigDelegate(
    config = this,
    type = T::class.java,
    nullable = null is T,
    typeToken = object : TypeToken<T>() {},
    defaultFunc = null,
    defaultValue = default,
    fieldName = null,
)

inline fun <reified T> BaseConfig.config(name: String? = null, default: T) = ConfigDelegate(
    config = this,
    type = T::class.java,
    nullable = null is T,
    typeToken = object : TypeToken<T>() {},
    defaultFunc = null,
    defaultValue = default,
    fieldName = name,
)

@Suppress("UNCHECKED_CAST")
class ConfigDelegate<T>(
    private val config: BaseConfig,
    private val type: Class<T>,
    private val nullable: Boolean,
    private val typeToken: TypeToken<T>,
    private val defaultFunc: (() -> T)?,
    private val defaultValue: T?,
    private val fieldName: String?,
) {
    private var value: T? = null
    private var isInitialized = false

    private fun getDefault(): T = when {
        defaultFunc != null -> defaultFunc.invoke()
        else -> defaultValue as T
    }

    private fun getGenericType(index: Int = 0): Class<*> {
        val parameterizedType = typeToken.type as ParameterizedType
        val typeArgument = parameterizedType.actualTypeArguments[index] as WildcardType
        return typeArgument.upperBounds[0] as Class<*>
    }

    operator fun setValue(_thisRef: Any, property: KProperty<*>, newValue: T) {
        value = newValue
        isInitialized = true
        config.set(fieldName ?: property.name, serialize(newValue)?.toString())
    }

    /**
     * Never yields null for a non-nullable [T], and never throws because of what is stored: a stored
     * string that will not parse reads as the property's declared default.
     *
     * Before this, a stored string that would not parse behaved three ways by type: a reference
     * type returned a bare null (the getter is `getValue -> checkcast`, which passes on null), a
     * primitive-backed type threw an NPE at the unboxing, and the enum arm threw inside
     * [deserialize] itself. The catch is deliberately broad for that last reason: it throws
     * (`NoSuchElementException` via `EnumExtensions.kt:16` `first { }` for a retired id, NPE for a
     * non-numeric element) rather than returning null, so a null check alone would not reach it.
     *
     * The fallback is read-through: the property's own row is never rewritten, so the next ordinary
     * write through [setValue] will persist the default over the malformed row. That is accepted,
     * and the `parseFailure.<key>` row, written here on the read and so before any such write can
     * happen, is what keeps the original value recoverable.
     *
     * [isInitialized] is set before [BaseConfig.recordParseFailure] runs, mirroring [setValue]'s
     * cache-before-write ordering (`:74-78`). `recordParseFailure` ends in `BaseConfig.set`, whose
     * `tryEmit` can resume a waiting [BaseConfig.changes] collector; with [isInitialized] already
     * set, a collector that re-read this property on that stack would get the cached fallback
     * rather than re-parsing. Keep the order.
     *
     * **No current consumer can produce that re-entrancy, so the ordering is defensive rather than
     * load-bearing** - an earlier revision of this KDoc claimed the opposite and named a dispatcher
     * that nothing sets. The five ViewModel `configFlow` consumers sit upstream of
     * `.flowOn(dispatcher)` with `dispatcher = Dispatchers.Default` (`HomeViewModel.kt:146`,
     * `:181`; same shape in Attendance, Grades, Agenda and Timetable), and the four Fragment
     * consumers collect inside a composition through `collectAsStateWithLifecycle`. Neither
     * dispatcher runs a collector inline on the emitting stack, and `Dispatchers.Main.immediate`
     * appears nowhere in the main sources.
     *
     * What `Dispatchers.Default` does mean is that this miss path can write [BaseConfig.values]
     * from a worker thread while `App.kt:228` and `:236` read that same plain `HashMap` on the
     * main thread. That is not a new hazard - [BaseConfig.set] already has arbitrary-thread
     * writers - and it is not one this ordering addresses.
     *
     * No test distinguishes the two orderings, and none can until a consumer re-enters: a
     * re-entrant call short-circuits on `recordParseFailure`'s dedupe either way. The
     * default-counting harness in `ConfigDelegateFallbackTest` is what would tell them apart.
     */
    operator fun getValue(_thisRef: Any, property: KProperty<*>): T {
        if (isInitialized)
            return value as T
        val key = fieldName ?: property.name

        if (key !in config.values) {
            value = getDefault()
            isInitialized = true
            return value as T
        }
        val str = config.values[key]

        var unparseable: String? = null
        value = if (str == null && nullable)
            null as T
        else if (str == null)
            getDefault()
        else
            try {
                deserialize<T>(str) ?: run {
                    unparseable = str
                    getDefault()
                }
            }
            catch (_: Exception) {
                unparseable = str
                getDefault()
            }

        isInitialized = true
        unparseable?.let { config.recordParseFailure(key, it) }
        return value as T
    }

    private fun <I> serialize(value: I?, serializeObjects: Boolean = true): Any? {
        if (value == null)
            return null

        return when (value) {
            is String -> value
            is Date -> value.stringY_m_d
            is Time -> value.stringValue
            is JsonObject -> value
            is JsonArray -> value
            // primitives
            is Number -> value
            is Boolean -> value
            // enums, maps & collections
            is Enum<*> -> value.toInt()
            is Collection<*> -> value.map {
                if (it is Number || it is Boolean) it else serialize(it, serializeObjects = false)
            }.toJsonElement()
            is Map<*, *> -> gson.toJson(value.mapValues { (_, it) ->
                if (it is Number || it is Boolean) it else serialize(it, serializeObjects = false)
            })
            // objects or else
            else -> if (serializeObjects) gson.toJson(value) else value
        }
    }

    private fun <I> deserialize(value: String?, type: Class<*> = this.type): I? {
        if (value == null)
            return null

        @Suppress("TYPE_MISMATCH_WARNING")
        return when (type) {
            String::class.java -> value
            Date::class.java -> Date.fromY_m_d(value)
            Time::class.java -> Time.fromHms(value)
            JsonObject::class.java -> value.toJsonObject()
            JsonArray::class.java -> value.toJsonArray()
            // primitives
            java.lang.Integer::class.java -> value.toIntOrNull()
            java.lang.Boolean::class.java -> value.toBooleanStrictOrNull()
            java.lang.Long::class.java -> value.toLongOrNull()
            java.lang.Float::class.java -> value.toFloatOrNull()
            // enums, maps & collections
            else -> when {
                Enum::class.java.isAssignableFrom(type) -> value.toIntOrNull()?.toEnum(type) as Enum<*>
                Collection::class.java.isAssignableFrom(type) -> {
                    val array = value.toJsonArray()
                    val genericType = getGenericType()
                    val list = array?.map {
                        val str = if (it.isJsonPrimitive) it.asString else it.toString()
                        deserialize<Any>(str, genericType)
                    }
                    // an element that returns null (a throwing one never gets here) makes the whole
                    // property unparseable, so no null reaches a non-null element type
                    when {
                        list == null || list.any { it == null } -> null
                        List::class.java.isAssignableFrom(type) -> list
                        Set::class.java.isAssignableFrom(type) -> list?.toSet()
                        else -> list?.toTypedArray()
                    }
                }
                Map::class.java.isAssignableFrom(type) -> {
                    val obj = value.toJsonObject()
                    val genericType = getGenericType(index = 1)
                    val map = obj?.entrySet()?.associate { (key, it) ->
                        val str = if (it.isJsonPrimitive) it.asString else it.toString()
                        key to deserialize<Any>(str, genericType)
                    }
                    // same rule as the collection arm: one null value fails the whole map
                    if (map == null || map.values.any { it == null }) null else map
                }
                // objects or else
                else -> gson.fromJson(value, type)
            }
        } as? I
    }
}
