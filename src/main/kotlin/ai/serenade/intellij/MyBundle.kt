package ai.serenade.intellij

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.MyBundle"

internal object MyBundle {
    private val instance = DynamicBundle(MyBundle::class.java, BUNDLE)

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): @Nls String =
        instance.getMessage(key, *params)
}
