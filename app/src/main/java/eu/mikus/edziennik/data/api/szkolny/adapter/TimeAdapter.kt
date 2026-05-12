/*
 * Copyright (c) Kacper Ziubryniewicz 2019-12-8
 */

package eu.mikus.edziennik.data.api.szkolny.adapter

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import eu.mikus.edziennik.utils.models.Time

class TimeAdapter : TypeAdapter<Time>() {
    override fun write(writer: JsonWriter?, time: Time?) {
        if (time == null) {
            writer?.nullValue()
        } else {
            writer?.value(time.value)
        }
    }

    override fun read(reader: JsonReader?): Time? {
        if (reader?.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return reader?.nextInt()?.let { Time.fromValue(it) }
    }
}
