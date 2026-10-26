package app.podgon.catalog

import org.springframework.boot.context.properties.ConfigurationProperties

/** Ограничения модуля категорий; значения по умолчанию подходят для старта. */
@ConfigurationProperties("podgon.catalog")
data class CatalogProperties(
    /** Максимальная глубина иерархии (корневая категория — уровень 1). */
    val maxDepth: Int = 5,
    val maxCategoriesPerGroup: Int = 500,
)
