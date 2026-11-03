package app.podgon.media.internal

import app.podgon.media.MediaProperties
import app.podgon.media.MediaStorage
import app.podgon.media.StoredFile
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isRegularFile

/** Файлы в каталоге на диске; запись через временный файл и переименование, чтобы не читать недописанное. */
@Component
@EnableConfigurationProperties(MediaProperties::class)
class LocalMediaStorage(
    props: MediaProperties,
) : MediaStorage {
    private val root: Path = Path.of(props.dir).toAbsolutePath().normalize()

    override fun put(
        key: String,
        bytes: ByteArray,
    ) {
        val target = resolve(key)
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, ".upload", ".tmp")
        Files.write(temp, bytes)
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    override fun get(key: String): ByteArray? = resolve(key).takeIf { it.isRegularFile() }?.let(Files::readAllBytes)

    override fun delete(key: String) {
        Files.deleteIfExists(resolve(key))
    }

    override fun list(prefix: String): List<StoredFile> {
        val dir = resolve(prefix)
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { files ->
            files
                .filter { it.isRegularFile() && !it.fileName.toString().startsWith(".") }
                .map { StoredFile(prefix + it.fileName.toString(), Files.getLastModifiedTime(it).toInstant()) }
                .toList()
        }
    }

    /** Ключ не может выйти за пределы каталога хранилища. */
    private fun resolve(key: String): Path {
        val path = root.resolve(key).normalize()
        require(path.startsWith(root)) { "ключ вне хранилища: $key" }
        return path
    }
}
