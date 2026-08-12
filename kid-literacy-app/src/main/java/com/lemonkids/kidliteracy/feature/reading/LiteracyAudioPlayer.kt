package com.lemonkids.kidliteracy.feature.reading

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

/**
 * 认字教学音频播放器。
 *
 * 预生成 MP3 优先通过 Media3 播放；音频 URL 缺失、下载失败或解码失败时，调用方会
 * 收到一次回调并使用系统 TTS 兜底。缓存键带上音频版本，更新音色/文本后不会误播旧文件。
 */
class LiteracyAudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val cache = SimpleCache(
        File(appContext.cacheDir, "literacy-audio"),
        LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
        StandaloneDatabaseProvider(appContext)
    )
    private var fallback: (() -> Unit)? = null
    private var activeCacheKey: String? = null

    private val player = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
                    // 缓存临时不可用时仍尝试从网络播放；网络播放失败才降级到系统 TTS。
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            )
        )
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    // Media3 仅对媒体/游戏用途自动管理焦点；内容类型仍保持语音。
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.d(TAG, "播放状态=${playbackStateName(playbackState)}，key=${activeCacheKey.orEmpty()}")
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val currentFallback = fallback ?: return
                    Log.w(TAG, "预生成 MP3 播放失败，回退系统 TTS", error)
                    fallback = null
                    stop()
                    clearMediaItems()
                    currentFallback()
                }

            })
        }

    /** 停止上一条音频后开始新请求，确保不会和系统 TTS 或上一条 MP3 重叠。 */
    fun play(audioUrl: String?, audioVersion: String?, onFallback: () -> Unit) {
        if (audioUrl.isNullOrBlank()) {
            stop()
            Log.d(TAG, "音频 URL 为空，使用系统 TTS")
            onFallback()
            return
        }

        // 不调用 stop()：连续长按时 stop 会先放弃音频焦点，紧接着的新 play
        // 可能在部分 Pad 上尚未重新获得焦点。直接替换 MediaItem 可原子地取消旧请求并持续持有焦点。
        val cacheKey = "${audioVersion.orEmpty()}|$audioUrl"
        Log.d(TAG, "播放预生成 MP3，key=$cacheKey")
        fallback = onFallback
        activeCacheKey = cacheKey
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(audioUrl)
                .setCustomCacheKey(cacheKey)
                .build()
        )
        player.prepare()
        player.play()
    }

    fun stop() {
        // 主动停止（关闭弹窗/开始录音/新点读）不应触发 TTS 兜底。
        fallback = null
        activeCacheKey = null
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        fallback = null
        activeCacheKey = null
        player.release()
        cache.release()
    }

    private companion object {
        const val MAX_CACHE_BYTES = 20L * 1024L * 1024L
        const val TAG = "LiteracyAudio"

        fun playbackStateName(state: Int): String = when (state) {
            Player.STATE_IDLE -> "idle"
            Player.STATE_BUFFERING -> "buffering"
            Player.STATE_READY -> "ready"
            Player.STATE_ENDED -> "ended"
            else -> "unknown($state)"
        }
    }
}
