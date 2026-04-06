package eu.kanade.tachiyomi.extension.all.truyendex

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TruyenDex(override val lang: String) : HttpSource(), ConfigurableSource {

    override val name = "TruyenDex"
    override val supportsLatest = true
    override val baseUrl = "https://truyendex.xyz"

    private val apiUrl = "https://api-proxy.truyendex.cc/mangadex"
    private val coverProxyUrl = "https://services.f-ck.me/v1/image"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val intl by lazy { TruyenDexIntl(lang) }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", MANGA_LIMIT.toString())
            .addQueryParameter("offset", ((page - 1) * MANGA_LIMIT).toString())
            .addQueryParameter("availableTranslatedLanguage[]", lang)
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("includes[]", "artist")
            .addQueryParameter("order[followedCount]", "desc")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val manga = result.data.map { it.toSManga() }
        return MangasPage(manga, result.hasNextPage)
    }

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", MANGA_LIMIT.toString())
            .addQueryParameter("offset", ((page - 1) * MANGA_LIMIT).toString())
            .addQueryParameter("availableTranslatedLanguage[]", lang)
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("includes[]", "artist")
            .addQueryParameter("order[latestUploadedChapter]", "desc")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ==============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", MANGA_LIMIT.toString())
            .addQueryParameter("offset", ((page - 1) * MANGA_LIMIT).toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("includes[]", "artist")

        if (query.isNotBlank()) {
            url.addQueryParameter("title", query)
        }

        val activeFilters = filters.ifEmpty { getFilterList() }

        // Handle UrlQueryFilter-based filters (sort, content rating, demographic, status, languages, tags, year, author, artist)
        TruyenDexFilters.addFiltersToUrl(url, activeFilters)

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Manga Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl${manga.url}".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("includes[]", "artist")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaResponse>()
        return result.data.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String {
        val uuid = manga.url.substringAfter("/manga/")
        return "$baseUrl/nettrom/truyen-tranh/$uuid"
    }

    // ============================== Chapter List ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfter("/manga/")
        return chapterListPageRequest(mangaId, 0)
    }

    private fun chapterListPageRequest(mangaId: String, offset: Int): Request {
        val url = "$apiUrl/manga/$mangaId/feed".toHttpUrl().newBuilder()
            .addQueryParameter("limit", CHAPTER_LIMIT.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("translatedLanguage[]", lang)
            .addQueryParameter("order[chapter]", "desc")
            .addQueryParameter("includes[]", "scanlation_group")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .addQueryParameter("contentRating[]", "erotica")
            .addQueryParameter("contentRating[]", "pornographic")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val firstPage = response.parseAs<ChapterListResponse>()
        val chapters = firstPage.data.toMutableList()

        // Handle pagination — MangaDex limits to 500 per request
        val mangaId = response.request.url.toString()
            .substringBefore("/feed")
            .substringAfterLast("/")

        var offset = firstPage.limit
        var hasMore = firstPage.hasNextPage

        while (hasMore) {
            val nextRequest = chapterListPageRequest(mangaId, offset)
            val nextResponse = client.newCall(nextRequest).execute()
            val nextPage = nextResponse.parseAs<ChapterListResponse>()
            chapters.addAll(nextPage.data)
            offset += nextPage.limit
            hasMore = nextPage.hasNextPage
        }

        return chapters.map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val uuid = chapter.url.substringAfter("/chapter/")
        return "$baseUrl/nettrom/chuong/$uuid"
    }

    // ============================== Pages ==============================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfter("/chapter/")
        return GET("$apiUrl/at-home/server/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<AtHomeResponse>()
        val useDataSaver = preferences.getString(PREF_DATA_SAVER_KEY, "false") == "true"
        return result.chapter.toPageList(result.baseUrl, useDataSaver)
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Referer", "https://mangadex.org/")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = TruyenDexFilters.getFilterList(intl)

    // ============================== Preferences ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_DATA_SAVER_KEY
            title = if (lang == "vi") "Chất lượng ảnh" else "Image Quality"
            summary = "%s"
            entries = if (lang == "vi") arrayOf("Chất lượng cao", "Tiết kiệm data") else arrayOf("High Quality", "Data Saver")
            entryValues = arrayOf("false", "true")
            setDefaultValue("false")
        }.also(screen::addPreference)
    }

    // ============================== Helpers ==============================

    private fun MangaData.toSManga(): SManga {
        val coverFileName = relationships
            .firstOrNull { it.type == "cover_art" }
            ?.attributes?.fileName

        val coverUrl = coverFileName?.let {
            val originalUrl = "https://mangadex.org/covers/$id/$it"
            "$coverProxyUrl/${java.util.Base64.getEncoder().encodeToString(originalUrl.toByteArray())}"
        }

        return (attributes ?: MangaAttributes()).let { attrs ->
            SManga.create().apply {
                url = "/manga/$id"
                title = attrs.getTitle(lang)
                thumbnail_url = coverUrl
                status = when (attrs.status) {
                    "ongoing" -> SManga.ONGOING
                    "completed" -> SManga.COMPLETED
                    "hiatus" -> SManga.ON_HIATUS
                    "cancelled" -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
                description = buildString {
                    val desc = attrs.getDescription(lang)
                    if (desc.isNotBlank()) append(desc)
                    val altNames = attrs.altTitles
                        .flatMap { it.values }
                        .filter { it != title }
                        .distinct()
                    if (altNames.isNotEmpty()) {
                        if (isNotBlank()) append("\n\n")
                        append(if (lang == "vi") "Tên khác: " else "Alternative names: ")
                        append(altNames.joinToString(", "))
                    }
                }
                genre = attrs.tags.mapNotNull { tag ->
                    tag.attributes?.name?.let { if (lang == "vi") it["vi"] ?: it["en"] else it["en"] }
                }.joinToString()
                author = relationships
                    .filter { it.type == "author" }
                    .mapNotNull { it.attributes?.name }
                    .joinToString()
                artist = relationships
                    .filter { it.type == "artist" }
                    .mapNotNull { it.attributes?.name }
                    .joinToString()
            }
        }
    }

    companion object {
        private const val MANGA_LIMIT = 24
        private const val CHAPTER_LIMIT = 500
        private const val PREF_DATA_SAVER_KEY = "pref_data_saver"
    }
}
