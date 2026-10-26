@file:OptIn(ExperimentalForeignApi::class)

package app.podgon.mobile.core.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Токены в Keychain (kSecClassGenericPassword). Доступны после первой разблокировки устройства,
 * не переносятся в резервные копии и на другие устройства (`...ThisDeviceOnly`).
 */
class IosTokenStorage : TokenStorage {
    override suspend fun read(): StoredTokens? {
        val data = query(returnData = true) { it } ?: return null
        val plain = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: return null
        val parts = plain.split(SEPARATOR, limit = 2)
        return if (parts.size == 2) StoredTokens(parts[0], parts[1]) else null
    }

    override suspend fun write(tokens: StoredTokens) {
        clear()
        val value = "${tokens.accessToken}$SEPARATOR${tokens.refreshToken}"
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        withAttributes(extra = { dict ->
            CFDictionaryAddValue(dict, kSecValueData, CFBridgingRetain(data))
            CFDictionaryAddValue(dict, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }) { SecItemAdd(it, null) }
    }

    override suspend fun clear() {
        withAttributes { SecItemDelete(it) }
    }

    private fun <T> query(
        returnData: Boolean,
        transform: (NSData) -> T,
    ): T? =
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status =
                withAttributes(extra = { dict ->
                    CFDictionaryAddValue(dict, kSecMatchLimit, kSecMatchLimitOne)
                    if (returnData) CFDictionaryAddValue(dict, kSecReturnData, kCFBooleanTrue)
                }) { SecItemCopyMatching(it, result.ptr) }
            if (status != errSecSuccess) return@memScoped null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            transform(data)
        }

    private fun withAttributes(
        extra: (platform.CoreFoundation.CFMutableDictionaryRef?) -> Unit = {},
        action: (CFDictionaryRef?) -> Int,
    ): Int {
        val dict =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, CFBridgingRetain(SERVICE))
        CFDictionaryAddValue(dict, kSecAttrAccount, CFBridgingRetain(ACCOUNT))
        extra(dict)
        return action(dict)
    }

    private companion object {
        const val SERVICE = "app.podgon"
        const val ACCOUNT = "tokens"
        const val SEPARATOR = "\n"
    }
}
