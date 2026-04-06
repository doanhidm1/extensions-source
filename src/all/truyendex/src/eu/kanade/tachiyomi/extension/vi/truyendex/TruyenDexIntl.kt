package eu.kanade.tachiyomi.extension.all.truyendex

class TruyenDexIntl(val lang: String) {
    val contentRating = if (lang == "vi") "Nội dung" else "Content Rating"
    val contentRatingSafe = if (lang == "vi") "An toàn" else "Safe"
    val contentRatingSuggestive = if (lang == "vi") "16+" else "Suggestive"
    val contentRatingErotica = if (lang == "vi") "18+" else "Erotica"
    val contentRatingPornographic = if (lang == "vi") "18+++" else "Pornographic"

    val demographic = if (lang == "vi") "Đối tượng" else "Demographic"
    val status = if (lang == "vi") "Tình trạng" else "Status"
    val statusOngoing = if (lang == "vi") "Đang tiến hành" else "Ongoing"
    val statusCompleted = if (lang == "vi") "Đã kết thúc" else "Completed"
    val statusHiatus = if (lang == "vi") "Tạm ngưng" else "Hiatus"
    val statusCancelled = if (lang == "vi") "Bị huỷ" else "Cancelled"

    val sort = if (lang == "vi") "Sắp xếp" else "Sort"
    val latest = if (lang == "vi") "Mới cập nhật" else "Latest Uploaded Chapter"
    val new = if (lang == "vi") "Truyện mới" else "Newly Added"
    val followed = if (lang == "vi") "Theo dõi nhiều nhất" else "Most Follows"
    val alphabetical = if (lang == "vi") "Bảng chữ cái" else "Alphabetical"
    val rating = if (lang == "vi") "Đánh giá cao nhất" else "Rating"
    val relevance = if (lang == "vi") "Liên quan nhất" else "Relevance"

    val originalLanguage = if (lang == "vi") "Quốc gia" else "Original Language"
    val year = if (lang == "vi") "Năm phát hành (nhập số)" else "Year of Release (digits)"
    val author = if (lang == "vi") "Từ những tác giả (danh sách UUID)" else "Authors (comma-separated UUIDs)"
    val artist = if (lang == "vi") "Từ những hoạ sĩ (danh sách UUID)" else "Artists (comma-separated UUIDs)"

    val translatedLanguage = if (lang == "vi") "Ngôn ngữ bản dịch" else "Translated Language"
    val vietnamese = if (lang == "vi") "Tiếng Việt" else "Vietnamese"
    val english = if (lang == "vi") "Tiếng Anh" else "English"

    val tagsHeader = if (lang == "vi") "Thể loại (▲ bao gồm, ▼ loại trừ)" else "Tags (▲ include, ▼ exclude)"
    val tagModeInclude = if (lang == "vi") "Chế độ tag bao gồm" else "Included Tags Mode"
    val tagModeExclude = if (lang == "vi") "Chế độ tag loại trừ" else "Excluded Tags Mode"
    val andAll = if (lang == "vi") "AND (tất cả)" else "AND (All)"
    val orAny = if (lang == "vi") "OR (bất kỳ)" else "OR (Any)"

    val tagContent = if (lang == "vi") "Cảnh báo nội dung" else "Content Warning"
    val tagFormat = if (lang == "vi") "Định dạng" else "Format"
    val tagGenre = if (lang == "vi") "Thể loại" else "Genre"
    val tagTheme = if (lang == "vi") "Chủ đề" else "Theme"
}
