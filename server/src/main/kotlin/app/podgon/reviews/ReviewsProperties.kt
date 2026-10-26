package app.podgon.reviews

import org.springframework.boot.context.properties.ConfigurationProperties

/** Ограничения модуля рекомендаций и категорий; значения по умолчанию подходят для старта. */
@ConfigurationProperties("podgon.reviews")
data class ReviewsProperties(
    val maxReviewsPerUser: Int = 5000,
    /** В скольких группах можно опубликовать одну рекомендацию. */
    val maxGroupsPerReview: Int = 50,
    val defaultPageSize: Int = 20,
    val maxPageSize: Int = 50,
    val categories: Categories = Categories(),
) {
    data class Categories(
        /** Максимальная глубина иерархии (корневая категория — уровень 1). */
        val maxDepth: Int = 5,
        val maxPerGroup: Int = 500,
    )
}
