package eu.kanade.tachiyomi.extension.vi.ztruyen

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ZTruyen : HttpSource(), ConfigurableSource {

    override val name: String = "ZTruyen"

    override val lang: String = "vi"

    override val supportsLatest: Boolean = true

    override val baseUrl: String get() = preferences.getString(PREF_BASE_URL_KEY, PREF_BASE_URL_DEFAULT)!!

    // OTruyen API backend (shared with other sites using the same API)
    private val apiUrl = "https://otruyenapi.com/v1/api"

    private val cdnUrl = "https://sv1.otruyencdn.com"

    private val imgUrl = "https://img.otruyenapi.com/uploads/comics"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Latest Updates ==============================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$apiUrl/danh-sach/truyen-moi?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val currentPageData = response.parseAs<DataDto<ListingData>>().data
        val pagination = currentPageData.params.pagination
        val requestedPage = pagination.currentPage
        val batchSize = pagination.totalItemsPerPage
        val totalPages = (pagination.totalItems + batchSize - 1) / batchSize

        val hideMangaWithoutChapters = preferences.getBoolean(PREF_HIDE_MANGA_WITHOUT_CHAPTERS, true)
        if (!hideMangaWithoutChapters) {
            val manga = currentPageData.items.map { it.toSManga(imgUrl) }
            return MangasPage(manga, requestedPage < totalPages)
        }

        val baseRequestUrl = response.request.url.newBuilder()
            .setQueryParameter("page", "1")
            .build()
        val routeKey = baseRequestUrl.toString()

        val cache = listingCache.getOrPut(routeKey) {
            ListingCacheState(totalPages = totalPages, batchSize = batchSize)
        }.apply {
            this.totalPages = totalPages
            this.batchSize = batchSize
        }

        cache.apiPages[requestedPage] = currentPageData

        val startIndex = (requestedPage - 1) * cache.batchSize
        val endExclusive = requestedPage * cache.batchSize

        while (cache.validItems.size < endExclusive && cache.lastScannedApiPage < cache.totalPages) {
            val nextApiPage = cache.lastScannedApiPage + 1
            val listing = cache.apiPages[nextApiPage] ?: fetchListingPage(baseRequestUrl, nextApiPage)?.also {
                cache.apiPages[nextApiPage] = it
            } ?: break

            cache.validItems += listing.items.filter { it.hasChapters() }
            cache.lastScannedApiPage = nextApiPage
        }

        val pageItems = cache.validItems.drop(startIndex).take(cache.batchSize)
        val hasNextPage = cache.validItems.size > endExclusive || cache.lastScannedApiPage < cache.totalPages

        return MangasPage(pageItems.map { it.toSManga(imgUrl) }, hasNextPage)
    }

    private data class ListingCacheState(
        var totalPages: Int,
        var batchSize: Int,
        val validItems: MutableList<EntriesData> = mutableListOf(),
        val apiPages: MutableMap<Int, ListingData> = mutableMapOf(),
        var lastScannedApiPage: Int = 0,
    )

    private val listingCache = mutableMapOf<String, ListingCacheState>()

    private fun fetchListingPage(baseRequestUrl: okhttp3.HttpUrl, page: Int): ListingData? {
        return try {
            val url = baseRequestUrl.newBuilder()
                .setQueryParameter("page", page.toString())
                .build()
            client.newCall(GET(url, headers)).execute().use {
                if (!it.isSuccessful) return null
                it.parseAs<DataDto<ListingData>>().data
            }
        } catch (_: Exception) {
            null
        }
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request =
        GET("$apiUrl/danh-sach/hoan-thanh?page=$page", headers)

    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    // ============================== Manga Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET("$apiUrl/truyen-tranh/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val res = response.parseAs<DataDto<EntryData>>()
        return res.data.item.toSManga(imgUrl)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/truyen-tranh/${manga.url}"

    // ============================== Chapter List ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val res = response.parseAs<DataDto<EntryData>>()
        val mangaUrl = res.data.item.slug
        val date = res.data.item.updatedAt
        return res.data.item.chapters
            .flatMap { server -> server.serverData.map { it.toSChapter(date, mangaUrl) } }
            .sortedByDescending { it.chapter_number }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaUrl = chapter.url.substringAfter(":")
        return "$baseUrl/truyen-tranh/$mangaUrl"
    }

    // ============================== Page List ==============================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringBefore(":")
        return GET("$cdnUrl/v1/api/chapter/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val res = response.parseAs<DataDto<PageDto>>()
        return res.data.toPage()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Search ==============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val selectedGenre = filters.filterIsInstance<GenreList>()
            .firstOrNull()
            ?.let { it.values.getOrNull(it.state)?.slug.orEmpty() }
            .orEmpty()

        val selectedStatus = filters.filterIsInstance<StatusList>()
            .firstOrNull()
            ?.let { it.values.getOrNull(it.state)?.slug.orEmpty() }
            .orEmpty()

        val (segments, params) = when {
            query.isNotBlank() -> {
                listOf("tim-kiem") to mapOf("keyword" to query)
            }

            selectedGenre.isNotBlank() -> {
                listOf("the-loai", selectedGenre) to emptyMap()
            }

            selectedStatus.isNotBlank() -> {
                listOf("danh-sach", selectedStatus) to emptyMap()
            }

            else -> {
                listOf("danh-sach", "truyen-moi") to emptyMap()
            }
        }

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            segments.forEach { addPathSegment(it) }
            addQueryParameter("page", "$page")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // ============================== Filters ==============================

    private fun genresRequest(): Request = GET("$apiUrl/the-loai", headers)

    private fun parseGenres(response: Response): List<Pair<String, String>> =
        response.parseAs<DataDto<GenresData>>().data.items.map { Pair(it.slug, it.name) }

    private var genreList: List<Pair<String, String>> = emptyList()

    private var fetchGenresAttempts: Int = 0

    private fun fetchGenres() {
        if (genreList.isNotEmpty() || fetchGenresAttempts >= 3) return
        launchIO {
            try {
                client.newCall(genresRequest()).await()
                    .use { parseGenres(it) }
                    .takeIf { it.isNotEmpty() }
                    ?.also { genreList = it }
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun launchIO(block: suspend () -> Unit) = scope.launch { block() }

    private class GenreList(name: String, pairs: List<Pair<String, String>>) : GenresFilter(name, pairs)

    private class StatusList :
        Filter.Select<Genre>(
            "Trạng thái",
            arrayOf(
                Genre("Tất cả", ""),
                Genre("Mới nhất", "truyen-moi"),
                Genre("Đang phát hành", "dang-phat-hanh"),
                Genre("Hoàn thành", "hoan-thanh"),
                Genre("Sắp ra mắt", "sap-ra-mat"),
            ),
        )

    private open class GenresFilter(title: String, pairs: List<Pair<String, String>>) :
        Filter.Select<Genre>(
            title,
            listOf(Genre("Tất cả", "")) + pairs.map { Genre(it.second, it.first) },
        )

    private class Genre(val name: String, val slug: String) {
        override fun toString() = name
    }

    override fun getFilterList(): FilterList {
        fetchGenres()
        return if (genreList.isEmpty()) {
            FilterList(
                Filter.Header("Nhấn 'Làm mới' để hiển thị thể loại"),
                Filter.Header("Hiển thị thể loại sẽ ẩn danh sách trạng thái vì không dùng chung được"),
                Filter.Header("Không dùng chung được với tìm kiếm bằng tên"),
                StatusList(),
            )
        } else {
            FilterList(
                Filter.Header("Không dùng chung được với tìm kiếm bằng tên"),
                GenreList("Thể loại", genreList),
            )
        }
    }

    // ============================== Preferences ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL_KEY
            title = "Đổi tên miền"
            summary = "Thay đổi URL gốc khi website đổi tên miền.\nMặc định: $PREF_BASE_URL_DEFAULT\nHiện tại: ${preferences.getString(PREF_BASE_URL_KEY, PREF_BASE_URL_DEFAULT)}"
            setDefaultValue(PREF_BASE_URL_DEFAULT)
            dialogTitle = "Đổi tên miền"
            dialogMessage = "Nhập URL mới (ví dụ: https://ztruyen.io.vn)"

            setOnPreferenceChangeListener { _, newValue ->
                val newUrl = newValue as String
                preferences.edit().putString(PREF_BASE_URL_KEY, newUrl).apply()
                summary = "Thay đổi URL gốc khi website đổi tên miền.\nMặc định: $PREF_BASE_URL_DEFAULT\nHiện tại: $newUrl"
                true
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_MANGA_WITHOUT_CHAPTERS
            title = "Ẩn truyện chưa có chapter"
            summary = "Ẩn các truyện không có chapter"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_BASE_URL_KEY = "pref_base_url"
        private const val PREF_BASE_URL_DEFAULT = "https://ztruyen.io.vn"
        private const val PREF_HIDE_MANGA_WITHOUT_CHAPTERS = "pref_hide_manga_without_chapters"
    }
}
