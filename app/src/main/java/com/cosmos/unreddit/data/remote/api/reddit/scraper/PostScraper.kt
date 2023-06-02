package com.cosmos.unreddit.data.remote.api.reddit.scraper

import com.cosmos.unreddit.data.remote.api.reddit.model.Awarding
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryData
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryDataItem
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryImage
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryItem
import com.cosmos.unreddit.data.remote.api.reddit.model.Listing
import com.cosmos.unreddit.data.remote.api.reddit.model.ListingData
import com.cosmos.unreddit.data.remote.api.reddit.model.Media
import com.cosmos.unreddit.data.remote.api.reddit.model.MediaMetadata
import com.cosmos.unreddit.data.remote.api.reddit.model.PostChild
import com.cosmos.unreddit.data.remote.api.reddit.model.PostData
import com.cosmos.unreddit.data.remote.api.reddit.model.RedditVideoPreview
import com.cosmos.unreddit.data.remote.api.reddit.model.RichText
import com.cosmos.unreddit.util.extension.toSeconds
import kotlinx.coroutines.CoroutineDispatcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PostScraper(
    body: String,
    ioDispatcher: CoroutineDispatcher
) : RedditScraper<Listing>(body, ioDispatcher) {

    override suspend fun scrap(document: Document): Listing {
        val posts = document.select("div[id~=thing_t3_\\w*]")
            .filter { element -> !element.attr("data-promoted").toBoolean() }

        val children = posts.map { it.toPost() }
        val after = getNextKey()

        return Listing(
            KIND,
            ListingData(
                null,
                null,
                children,
                after,
                null
            )
        )
    }

    private fun Element.toPost(): PostChild {
        // subreddit
        val subreddit = attr("data-subreddit")


        val titleParagraph = selectFirst("p.title")
        // title
        val title = titleParagraph?.selectFirst("a")?.text().orEmpty()

        // link_flair_richtext
        val flairRichText = titleParagraph
            ?.selectFirst("span.flairrichtext")
            ?.toFlair()
            ?: emptyList()

        // subreddit_name_prefixed
        val prefixedSubreddit = attr("data-subreddit-prefixed")

        // name
        val name = attr("data-fullname")

        val awardings = selectFirst("span.awardings-bar")
        val awards = awardings
            ?.select("a.awarding-link")
            ?.map { award -> award.toAwarding() }
            ?: emptyList()

        val moreAwards = awardings
            ?.selectFirst("a.awarding-show-more-link")
            ?.text()
            ?.run { MORE_AWARDS_REGEX.find(this)?.value?.toIntOrNull() }
            ?: 0

        // total_awards_received
        val totalAwards = awards.sumOf { it.count }.plus(moreAwards) ?: 0

        // is_original_content
        val isOC = attr("data-oc").toBoolean()

        // score
        val score = attr("data-score").toIntOrNull() ?: 0

        // domain
        val domain = attr("data-domain")

        // is_self
        val isSelf = hasClass("self")

        // over_18
        val isOver18 = attr("data-nsfw").toBoolean()

        // spoiler
        val isSpoiler = attr("data-spoiler").toBoolean()

        // locked
        val isLocked = hasClass("locked")

        // author
        val authorClass = document?.selectFirst("a.author")
        val author = authorClass?.text() ?: "[deleted]"

        // distinguished
        val distinguished = when {
            authorClass == null -> "regular"
            authorClass.hasClass("moderator") -> "moderator"
            authorClass.hasClass("admin") -> "admin"
            else -> "regular"
        }

        // num_comments
        val commentsNumber = attr("data-comments-count").toIntOrNull() ?: 0

        // permalink
        val permalink = attr("data-permalink")

        // stickied
        val isStickied = hasClass("stickied")

        // url
        val url = attr("data-url")

        // created_utc
        val created = attr("data-timestamp").toLongOrNull()?.toSeconds() ?: 0L

        // is_gallery
        val isRedditGallery = attr("data-is-gallery").toBoolean()

        val thumbnailClass = selectFirst("a.thumbnail")
        // is_video
        val isVideo = thumbnailClass?.selectFirst("div.duration-overlay")?.let { true } ?: false
        val thumbnail = thumbnailClass
            ?.selectFirst("img")
            ?.attr("src")
            ?.run { "https:$this" }

        val expando = selectFirst("div.expando")
            ?.attr("data-cachedhtml")
            ?.run { Jsoup.parse(this) }

        val media = when {
            isVideo -> {
                Media(
                    null,
                    null,
                    RedditVideoPreview(
                        "$url/DASH_720.mp4",
                        0,
                        0,
                        0,
                        false
                    )
                )
            }

            expando != null -> expando.toMedia()

            else -> null
        }

        val galleryData = expando?.toGalleryData()
        val mediaMetadata = expando?.toMediaMetadata()

        val postData = PostData(
            subreddit,
            flairRichText,
            authorFlairRichText = null,
            title,
            prefixedSubreddit,
            name,
            null,
            totalAwards,
            isOC,
            null,
            null,
            galleryData,
            score,
            null,
            isSelf,
            null,
            domain,
            selfTextHtml = null,
            null,
            false,
            isOver18,
            null,
            awards,
            isSpoiler,
            isLocked,
            distinguished,
            author,
            commentsNumber,
            permalink,
            isStickied,
            url,
            created,
            media,
            mediaMetadata,
            isRedditGallery,
            isVideo
        ).apply {
            this.thumbnail = thumbnail
        }

        return PostChild(postData)
    }

    private fun Element.toFlair(): List<RichText> {
        return children().map { flair ->
            when {
                flair.hasClass("flairemoji") -> {
                    val style = flair.attr("style")
                    val url = URL_REGEX.find(style)?.groups?.get(1)?.value
                    RichText(null, url)
                }

                else -> {
                    RichText(flair.text(), null)
                }
            }
        }
    }

    private fun Element.toAwarding(): Awarding {
        val count = attr("data-count").toIntOrNull() ?: 0
        val url = selectFirst("img")?.attr("src").orEmpty()

        return Awarding(url, emptyList(), count)
    }

    private fun Document.toMedia(): Media? {
        val source = selectFirst("source") ?: return null

        return when (source.attr("type")) {
            "video/mp4" -> {
                val src = source.attr("src")

                Media(
                    null,
                    null,
                    RedditVideoPreview(
                        src,
                        0,
                        0,
                        0,
                        true
                    )
                )
            }

            else -> null
        }
    }

    private fun Document.toGalleryData(): GalleryData {
        val items = select("div.gallery-tile")
            .map {
                val id = it.attr("data-media-id")
                GalleryDataItem(null, id)
            }

        return GalleryData(items)
    }

    private fun Document.toMediaMetadata(): MediaMetadata? {
        val items = select("div.gallery-preview")
            .map {
                val id = it.attr("id").substringAfterLast("-")
                val src = it.selectFirst("div.media-preview-content")
                    ?.selectFirst("a")
                    ?.attr("href")

                val image = GalleryImage(0, 0, src, null)

                GalleryItem(null, image, null, id)
            }

        return if (items.isNotEmpty()) MediaMetadata(items) else null
    }

    companion object {
        private const val KIND = "t3"

        private val URL_REGEX = Regex("url\\((.*?)\\)")
        private val MORE_AWARDS_REGEX = Regex("\\d+")
    }
}
