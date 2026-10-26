package app.podgon.common

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Публичные статические страницы без расширения в адресе (ссылка для магазина приложений). */
@Configuration(proxyBeanMethods = false)
class WebPagesConfig : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController("/account-deletion").setViewName("forward:/account-deletion.html")
    }
}
