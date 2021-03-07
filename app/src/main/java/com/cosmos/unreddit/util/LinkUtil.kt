package com.cosmos.unreddit.util

import com.cosmos.unreddit.api.imgur.pojo.Image
import com.cosmos.unreddit.model.MediaType
import okhttp3.HttpUrl

object LinkUtil {

    private val GIF_REGEX = Regex("gif(v)?")
    private val REDDIT_VIDEO_REGEX = Regex("DASH_(\\d+)")

    private val SUBREDDIT_REGEX = Regex("/r/[A-Za-z0-9_-]{3,21}")
    private val USER_REGEX = Regex("/u/[A-Za-z0-9_-]{3,20}")

    fun getAlbumIdFromImgurLink(link: String): String {
        return HttpUrl.parse(link)?.pathSegments()?.getOrNull(1) ?: ""
    }

    fun getUrlFromImgurImage(image: Image): String {
        return "https://i.imgur.com/${image.hash}${image.ext}"
    }

    fun getImgurVideo(link: String): String {
        return link.replace(GIF_REGEX, "mp4")
    }

    fun getRedditSoundTrack(link: String): String {
        return link.replace(REDDIT_VIDEO_REGEX, "DASH_audio")
    }

    fun getGfycatVideo(link: String): String {
        return link.replace("size_restricted.gif", "mobile.mp4")
    }

    fun getStreamableShortcode(link: String): String {
        return HttpUrl.parse(link)?.pathSegments()?.getOrNull(0) ?: ""
    }

    fun getLinkType(link: String): MediaType {
        when {
            link.matches(SUBREDDIT_REGEX) -> return MediaType.REDDIT_SUBREDDIT
            link.matches(USER_REGEX) -> return MediaType.REDDIT_USER
        }

        val httpUrl = HttpUrl.parse(link) ?: return MediaType.NO_MEDIA
        val domain = httpUrl.host()

        return when {
            domain == "www.reddit.com" || domain == "old.reddit.com" ||
                    domain == "np.reddit.com" -> MediaType.REDDIT_LINK

            domain == "imgur.com" -> {
                when {
                    link.contains("imgur.com/a/") -> MediaType.IMGUR_ALBUM
                    link.contains("imgur.com/gallery/") -> MediaType.IMGUR_GALLERY
                    link.endsWith(".gifv") ||
                            link.endsWith(".gif") -> MediaType.IMGUR_GIF
                    link.endsWith(".mp4") -> MediaType.IMGUR_VIDEO
                    else -> MediaType.IMGUR_LINK
                }
            }

            domain == "i.imgur.com" -> {
                when {
                    link.endsWith(".gifv") ||
                            link.endsWith(".gif") -> MediaType.IMGUR_GIF
                    link.endsWith(".mp4") -> MediaType.IMGUR_VIDEO
                    else -> MediaType.IMGUR_IMAGE
                }
            }

            domain == "www.redgifs.com" -> MediaType.REDGIFS

            domain == "streamable.com" -> MediaType.STREAMABLE

            domain == "i.redd.it" -> MediaType.IMAGE

            link.endsWith(".jpg") || link.endsWith(".jpeg") ||
                    link.endsWith(".png") -> MediaType.IMAGE

            link.endsWith(".mp4") || link.endsWith(".webm") -> MediaType.VIDEO

            else -> MediaType.LINK
        }
    }
}