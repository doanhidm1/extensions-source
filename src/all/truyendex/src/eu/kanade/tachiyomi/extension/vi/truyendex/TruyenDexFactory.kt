package eu.kanade.tachiyomi.extension.all.truyendex

import eu.kanade.tachiyomi.source.SourceFactory

class TruyenDexFactory : SourceFactory {
    override fun createSources() = listOf(
        TruyenDex("vi"),
        TruyenDex("en"),
    )
}
