package app.podgon.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Общие технические средства (ошибки, время, пагинация) без бизнес-логики.
 * Бизнес-модули (identity, groups, reviews — рекомендации и категории, mail)
 * лежат рядом с этим пакетом.
 *
 * Время получаем через внедряемый [Clock] (UTC), чтобы TTL кодов и токенов были тестируемыми.
 */
@Configuration(proxyBeanMethods = false)
class TimeConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
