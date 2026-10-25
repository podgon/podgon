package app.podgon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Точка входа backend Podgon (модульный монолит). */
@SpringBootApplication
class PodgonApplication

fun main(args: Array<String>) {
    runApplication<PodgonApplication>(*args)
}
