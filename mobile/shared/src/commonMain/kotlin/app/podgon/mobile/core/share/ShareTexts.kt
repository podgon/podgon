package app.podgon.mobile.core.share

import app.podgon.contract.ReviewDetails

/** Текст рекомендации для «Поделиться»: название и оценка, категория, описание, теги и подпись. */
fun reviewShareText(
    review: ReviewDetails,
    category: String?,
    footer: String,
): String =
    buildList {
        add("${review.title} — ${review.rating}/10")
        category?.let(::add)
        review.description.takeIf { it.isNotBlank() }?.let(::add)
        review.tags.takeIf { it.isNotEmpty() }?.let { tags -> add(tags.joinToString(" ") { "#$it" }) }
        add("— $footer")
    }.joinToString("\n")
