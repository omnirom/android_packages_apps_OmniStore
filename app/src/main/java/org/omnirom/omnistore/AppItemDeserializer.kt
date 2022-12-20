package org.omnirom.omnistore

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class AppItemDeserializer(val baseUrl: String) : JsonDeserializer<AppItem> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): AppItem {
        json?.let {
            val jsonObject = json.asJsonObject
            val devices = mutableListOf<String>()
            if (jsonObject.has("devices")) {
                jsonObject.get("devices")?.asJsonArray?.forEach { device ->
                    device?.let {
                        devices.add(device.asString)
                    }
                }
            }

            val appItem = AppItem(
                jsonObject.get("title")?.asString,
                jsonObject.get("file")?.asString,
                jsonObject.get("category")?.asString,
                jsonObject.get("package")?.asString,
                jsonObject.get("icon")?.asString,
                jsonObject.get("versionCode")?.asString,
                jsonObject.get("versionName")?.asString,
                jsonObject.get("description")?.asString,
                jsonObject.get("note")?.asString,
                devices
            )
            appItem.mBaseUrl = baseUrl
            return appItem
        }
        return AppItem(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )
    }
}