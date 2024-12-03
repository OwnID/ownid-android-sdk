package com.ownid.sdk.internal.component.locale

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import okhttp3.internal.closeQuietly
import okio.buffer

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class CachedString(val timeStamp: Long, val data: String) {
    internal companion object {
        internal fun get(key: String, cache: DiskLruCache): CachedString? {
            val snapshot: DiskLruCache.Snapshot = try {
                cache[key] ?: return null
            } catch (_: java.lang.Exception) {
                return null
            }

            return try {
                snapshot.getSource(0).use { source ->
                    source.buffer().run { CachedString(readDecimalLong(), readUtf8()) }
                }
            } catch (_: java.lang.Exception) {
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
        } catch (_: java.lang.Exception) {
            try {
                editor?.abort()
            } catch (_: java.lang.Exception) {
            }
        }
        return false
    }
}