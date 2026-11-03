package app.podgon.mobile.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Фрагмент значка: путь SVG, контурный (по умолчанию) или с заливкой. */
private class Part(
    val d: String,
    val filled: Boolean = false,
)

private fun circle(
    cx: Double,
    cy: Double,
    r: Double,
    filled: Boolean = false,
) = Part("M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z", filled)

private fun rect(
    x: Double,
    y: Double,
    w: Double,
    h: Double,
    r: Double,
) = Part(
    "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,${r}v${h - 2 * r}a$r,$r 0 0 1 ${-r},${r}h${-(w - 2 * r)}" +
        "a$r,$r 0 0 1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0 1 $r,${-r}z",
)

/** Значок в сетке 24×24; цвет задаёт `Icon(tint = …)` из темы. */
private fun icon(
    name: String,
    stroke: Float,
    vararg parts: Part,
): ImageVector =
    ImageVector
        .Builder(name, 24.dp, 24.dp, 24f, 24f)
        .apply {
            parts.forEach { part ->
                val nodes = PathParser().parsePathString(part.d).toNodes()
                if (part.filled) {
                    addPath(nodes, fill = SolidColor(Color.Black))
                } else {
                    addPath(
                        nodes,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = stroke,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }.build()

/**
 * Набор значков приложения (`docs/design.md`, «Иконки»): монохромные, контурные, из путей макетов.
 * Временный набор — окончательные иконки и логотип выбираются позже.
 */
object PodgonIcons {
    val Feed by lazy { icon("feed", 1.9f, rect(4.0, 4.0, 16.0, 7.0, 3.0), Part("M4 15.5h16M4 19.5h10")) }
    val Heart by lazy {
        icon(
            "heart",
            1.8f,
            Part("M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z"),
        )
    }
    val HeartFilled by lazy {
        icon(
            "heartFilled",
            1.8f,
            Part(
                "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z",
                filled = true,
            ),
        )
    }
    val Key by lazy {
        icon("key", 1.8f, circle(8.0, 15.0, 4.5), Part("M11.2 11.8 20 3M16.5 6.5l2.5 2.5M14 9l2 2"))
    }
    val Settings by lazy {
        icon(
            "settings",
            1.8f,
            Part(
                "M12.2 2.5h-.4a1.8 1.8 0 0 0-1.8 1.8v.2a1.8 1.8 0 0 1-.9 1.5l-.4.2a1.8 1.8 0 0 1-1.8 0l-.1-" +
                    ".1a1.8 1.8 0 0 0-2.5.7l-.2.3a1.8 1.8 0 0 0 .7 2.5l.1.1a1.8 1.8 0 0 1 .9 1.5v.5a1.8 1.8 0 0" +
                    " 1-.9 1.6l-.1.1a1.8 1.8 0 0 0-.7 2.5l.2.3a1.8 1.8 0 0 0 2.5.7l.1-.1a1.8 1.8 0 0 1 1.8 0l.4" +
                    ".2a1.8 1.8 0 0 1 .9 1.5v.2a1.8 1.8 0 0 0 1.8 1.8h.4a1.8 1.8 0 0 0 1.8-1.8v-.2a1.8 1.8 0 0 " +
                    "1 .9-1.5l.4-.2a1.8 1.8 0 0 1 1.8 0l.1.1a1.8 1.8 0 0 0 2.5-.7l.2-.3a1.8 1.8 0 0 0-.7-2.5l-." +
                    "1-.1a1.8 1.8 0 0 1-.9-1.6v-.4a1.8 1.8 0 0 1 .9-1.6l.1-.1a1.8 1.8 0 0 0 .7-2.5l-.2-.3a1.8 1" +
                    ".8 0 0 0-2.5-.7l-.1.1a1.8 1.8 0 0 1-1.8 0l-.4-.2a1.8 1.8 0 0 1-.9-1.5v-.2a1.8 1.8 0 0 0-1." +
                    "8-1.8z",
            ),
            circle(12.0, 12.0, 3.0),
        )
    }
    val Plus by lazy { icon("plus", 2.4f, Part("M12 5v14M5 12h14")) }
    val Bell by lazy { icon("bell", 1.8f, Part("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z"), Part("M10 21h4")) }
    val Search by lazy { icon("search", 2f, circle(10.5, 10.5, 6.5), Part("M15.5 15.5 20 20")) }
    val Filters by lazy {
        icon(
            "filters",
            1.6f,
            circle(12.0, 12.0, 9.5),
            circle(9.0, 9.0, 1.5, filled = true),
            circle(15.0, 9.0, 1.5, filled = true),
            circle(9.0, 15.0, 1.5, filled = true),
            circle(15.0, 15.0, 1.5, filled = true),
        )
    }
    val Close by lazy { icon("close", 2.4f, Part("M7 7l10 10M17 7 7 17")) }
    val ChevronDown by lazy { icon("chevronDown", 2f, Part("m6 9 6 6 6-6")) }
    val Back by lazy { icon("back", 2f, Part("M15 5l-7 7 7 7")) }
    val Star by lazy {
        icon("star", 1f, Part("M12 3.2l2.6 5.6 6.1.7-4.5 4.2 1.2 6-5.4-3-5.4 3 1.2-6-4.5-4.2 6.1-.7z", filled = true))
    }
    val Bookmark by lazy { icon("bookmark", 2f, Part("M6.5 4h11v16.5L12 16.8l-5.5 3.7z")) }
    val BookmarkFilled by lazy { icon("bookmarkFilled", 2f, Part("M6.5 4h11v16.5L12 16.8l-5.5 3.7z", filled = true)) }
    val Pin by lazy { icon("pin", 1.9f, Part("M9 3.5h6l-1 6 3.5 3.5h-11L10 9.5z"), Part("M12 13v7.5")) }
    val Fold by lazy { icon("fold", 1.9f, Part("m7 4 5 5 5-5M7 20l5-5 5 5")) }
    val Edit by lazy { icon("edit", 1.9f, Part("M4 20h4L19 9l-4-4L4 16z")) }
    val Order by lazy {
        icon(
            "order",
            1.9f,
            Part("M9 6h11M9 12h11M9 18h11"),
            Part("M4 5l1.5 1.5L4 8M4 11l1.5 1.5L4 14M4 17l1.5 1.5L4 20"),
        )
    }
    val Group by lazy {
        icon(
            "group",
            1.9f,
            circle(9.0, 9.0, 3.2),
            Part("M3.5 19a5.5 5.5 0 0 1 11 0"),
            circle(16.5, 8.0, 2.6),
            Part("M15.5 13.6A4.6 4.6 0 0 1 21 18"),
        )
    }
    val Invite by lazy {
        icon("invite", 1.9f, circle(9.0, 9.0, 3.2), Part("M3.5 19a5.5 5.5 0 0 1 9-4.2"), Part("M18 13v7M14.5 16.5h7"))
    }
    val Offline by lazy {
        icon(
            "offline",
            1.8f,
            Part("M7 18h10a4 4 0 0 0 .6-7.95A6 6 0 0 0 6.2 9.1 4.5 4.5 0 0 0 7 18z"),
            Part("M4 4l16 16"),
        )
    }
    val Lock by lazy { icon("lock", 1.9f, rect(5.0, 11.0, 14.0, 9.0, 3.0), Part("M8 11V8a4 4 0 0 1 8 0v3")) }
    val LockOpen by lazy { icon("lockOpen", 1.9f, rect(5.0, 11.0, 14.0, 9.0, 3.0), Part("M8 11V8a4 4 0 0 1 7.6-1.8")) }
    val Sun by lazy {
        icon(
            "sun",
            1.8f,
            circle(12.0, 12.0, 4.0),
            Part(
                "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4",
            ),
        )
    }
    val Moon by lazy { icon("moon", 1.8f, Part("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")) }
    val Monitor by lazy { icon("monitor", 1.8f, rect(3.0, 4.0, 18.0, 12.0, 3.0), Part("M8 20h8M12 16v4")) }
    val Refresh by lazy { icon("refresh", 2.4f, Part("M20 12a8 8 0 1 1-2.3-5.7"), Part("M20 4v4.5h-4.5")) }
    val Empty by lazy { icon("empty", 1.4f, rect(4.0, 4.0, 16.0, 16.0, 5.0), Part("M9 12h6M12 9v6")) }
    val History by lazy { icon("history", 1.9f, circle(12.0, 12.0, 8.5), Part("M12 7.5V12l3 2")) }
    val ChevronRight by lazy { icon("chevronRight", 2f, Part("m9 6 6 6-6 6")) }
}
