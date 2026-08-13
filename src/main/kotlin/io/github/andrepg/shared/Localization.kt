package io.github.andrepg.shared

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

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
    fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any?): @Nls String {
        return instance.getMessage(key, *params)
    }

    /**
     * Returns a lazily-evaluated supplier of the localized message for the given key.
     *
     * Useful for messages that should only be resolved when actually displayed, such as
     * configurable display names.
     *
     * @param key the message bundle key
     * @param params optional parameters substituted into the message pattern
     * @return a [Supplier] that resolves the localized message on demand
     */
    @JvmStatic
    fun lazyMessage(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): Supplier<@Nls String> {
        return instance.getLazyMessage(key, *params)
    }
}
