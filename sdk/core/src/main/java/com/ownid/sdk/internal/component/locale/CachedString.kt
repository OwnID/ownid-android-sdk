package com.ownid.sdk.internal.component.locale

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import java.io.IOException

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class CachedString(val timeStamp: Long, val data: String) {
    internal companion object {
        internal fun get(key: String, cache: DiskLruCache): CachedString? {
            return try {
                cache[key].getInputStream(0).bufferedReader().use { reader ->
                    val timeStamp = reader.readLine()?.toLongOrNull() ?: throw IOException("Invalid timestamp in cache file for key: $key")
                    val data = reader.readText()
                    return CachedString(timeStamp, data)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    internal fun put(key: String, cache: DiskLruCache) {
        var editor: DiskLruCache.Editor? = null
        try {
            editor = cache.edit(key) ?: return
            editor.newOutputStream(0).bufferedWriter().use { writer ->
                writer.write(timeStamp.toString())
                writer.newLine()
                writer.write(data)
            }
            editor.commit()
        } catch (_: IOException) {
            runCatching { editor?.abort() }
            return
        }
    }
}