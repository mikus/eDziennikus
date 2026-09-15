/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import com.google.gson.JsonPrimitive
import eu.mikus.edziennik.ext.toEnum
import eu.mikus.edziennik.ext.toInt

/**
 * How a value round-trips through the edit dialog, keyed off the same [LabTarget] the resolver chose -
 * so the arm is picked once and the two `when`s are compiler-checked against each other.
 *
 * [format] is **not** the tree's display text: it is unquoted (`LabProfileFragment.kt:90-99`), while
 * the row rendered `JsonElement.toString()` (`JsonElementViewHolder.kt:51`).
 */
object LabValueCodec {

    fun format(target: LabTarget): String = when (target) {
        is LabTarget.JsonLeaf -> when {
            target.old.isString -> target.old.asString
            target.old.isNumber -> target.old.asNumber.toString()
            target.old.isBoolean -> target.old.asBoolean.toString()
            else -> target.old.asString
        }
        is LabTarget.ConfigEntry -> target.owner.values[target.key] ?: ""
        is LabTarget.Field -> when (val old = target.old) {
            null -> ""
            is Enum<*> -> old.toInt().toString()
            else -> old.toString()
        }
        is LabTarget.Unsupported -> ""
    }

    fun parse(target: LabTarget, input: String): Result<Any?> = when (target) {
        is LabTarget.JsonLeaf -> parseJson(target.old, input)
        // No parsing, by design: BaseConfig.set takes a String and DelegateConfig types it on read.
        is LabTarget.ConfigEntry -> Result.success(input)
        is LabTarget.Field -> parseField(target.old, input)
        is LabTarget.Unsupported -> fail(target.reason)
    }

    private fun parseJson(old: JsonPrimitive, input: String): Result<Any?> = when {
        old.isString -> Result.success(input)
        // The original re-parsed every JSON number with toLong() while format rendered
        // asNumber.toString(), so a fractional number crashed with nothing typed. Long first keeps
        // whole numbers writing back as integers, exactly as before.
        old.isNumber -> (input.toLongOrNull() ?: input.toDoubleOrNull())
            ?.let { Result.success(it) } ?: fail("\"$input\" is not a number")
        old.isBoolean -> Result.success(input.toBoolean())
        else -> Result.success(input)
    }

    private fun parseField(old: Any?, input: String): Result<Any?> = when (old) {
        is Int -> input.toIntOrNull()?.let { Result.success(it) } ?: fail("\"$input\" is not an Int")
        is Long -> input.toLongOrNull()?.let { Result.success(it) } ?: fail("\"$input\" is not a Long")
        is Float -> input.toFloatOrNull()?.let { Result.success(it) } ?: fail("\"$input\" is not a Float")
        is Double -> input.toDoubleOrNull()?.let { Result.success(it) } ?: fail("\"$input\" is not a Double")
        is Boolean -> Result.success(input.toBoolean())
        is Char -> input.firstOrNull()?.let { Result.success(it) } ?: fail("a Char field needs one character")
        is String -> Result.success(input)
        is Enum<*> -> parseEnum(old, input)
        // Design D3: the original's `else -> input` arm handed a String to Field.set on a field of
        // unknown type, which threw IllegalArgumentException from outside the try. Dropped.
        null -> fail("cannot infer the type of a null field")
        else -> fail("${old::class.java.simpleName} fields are not editable here")
    }

    private fun parseEnum(old: Enum<*>, input: String): Result<Any?> {
        val id = input.toIntOrNull() ?: return fail("\"$input\" is not an enum id")
        val value = runCatching { enumOfId(old::class.java, id) }.getOrNull()
            ?: return fail("no ${old::class.java.simpleName} has id $id")
        return Result.success(value)
    }

    /** Satisfies `toEnum`'s `E : Enum<E>` bound and nothing else. See [enumOfId]. */
    private enum class ErasedEnum { PLACEHOLDER }

    /**
     * `ext/EnumExtensions.kt:49` declares `fun <E : Enum<E>> Int.toEnum(type: Class<*>): Enum<E>` and
     * ends in an unchecked `as E`, so the type argument is erased and never checked at runtime - the
     * instance returned is decided entirely by [type]. [ErasedEnum] is a private placeholder passed
     * only to satisfy the bound, which keeps this pure unit from importing the nav table for a type
     * argument that is thrown away. Verified: `enumOfId(LoginType::class.java, 2)` returns
     * `LoginType.LIBRUS`.
     *
     * Throws `NoSuchElementException` for an unknown id - `ext/EnumExtensions.kt:10-16` are seven
     * `first { }` one-liners, one per enum - and `IllegalArgumentException` for a class the table does
     * not list (`:58`); [parseEnum] catches both.
     */
    private fun enumOfId(type: Class<*>, id: Int): Enum<*> = id.toEnum<ErasedEnum>(type)

    private fun fail(message: String): Result<Any?> = Result.failure(LabEditError(message))
}
