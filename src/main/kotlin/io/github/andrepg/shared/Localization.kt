package io.github.andrepg.shared

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.Messages"

/**
 * Central access point to the plugin's localized messages.
 *
 * Wraps IntelliJ's [DynamicBundle] to resolve keys from the `messages.Messages` resource bundle,
 * providing the same facilities used by the IDE for internationalized UI text.
 */
internal object Localization {
    private val instance = DynamicBundle(Localization::class.java, BUNDLE)

    /**
     * Resolves a localized message for the given bundle key.
     *
     * @param key the message bundle key, e.g. `runs.configuration.displayName`
     * @param params optional parameters substituted into the message pattern
     * @return the localized, formatted message
     */
    @JvmStatic
    fun message(
        key:
            @PropertyKey(resourceBundle = BUNDLE)
            String,
        vararg params: Any?,
    ): @Nls String {
        return instance.getMessage(key, *params)
    }
}
