package com.ownid.sdk.internal.locale

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import okhttp3.internal.closeQuietly
import okio.buffer
import org.json.JSONException
import java.io.IOException

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class CachedString(val timeStamp: Long, val data: String) {
    internal companion object {
        internal fun get(key: String, cache: DiskLruCache): CachedString? {
            val snapshot: DiskLruCache.Snapshot = try {
                cache[key] ?: return null
            } catch (_: IOException) {
                return null
            }

            return try {
                snapshot.getSource(0).use { source ->
                    source.buffer().run { CachedString(readDecimalLong(), readUtf8()) }
                }
            } catch (_: IOException) {
                snapshot.closeQuietly()
                null
            } catch (_: JSONException) {
                snapshot.closeQuietly()
                null
            }
        }
    }

    internal fun put(key: String, cache: DiskLruCache): Boolean {
        var editor: DiskLruCache.Editor? = null
        try {
            editor = cache.edit(key) ?: return false
            editor.newSink(0).buffer().use { sink ->
                sink.writeDecimalLong(timeStamp)
                    .writeByte('\n'.code)
                    .writeUtf8(data)
            }
            editor.commit()
            return true
        } catch (_: IOException) {
            try {
                editor?.abort()
            } catch (_: IOException) {
            }
        }
        return false
    }
}