/*
 * BRIDGE (replaces ArchiveTune's utils/DataStore.kt).
 *
 * Same API the copied sources call — context.dataStore, PreferenceStore,
 * rememberPreference / rememberEnumPreference — over this module's own
 * PreferenceDataStore, plus a hook table the hosting app can install
 * adapters into for the settings it already owns (the player design style
 * itself, the slider and button styles, ...). A bridged key reads and
 * writes through the host's store; everything else persists locally with
 * ArchiveTune's defaults.
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.extensions.toEnum
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "archivetune_player_settings",
)

/**
 * The host's window into the preference layer: register an adapter for a
 * key and that key reads, writes and observes through the host instead of
 * the local store. Strings are the wire format for enum-valued keys so the
 * adapter can map the two apps' enum names.
 */
object BridgedPreferences {
    @PublishedApi
    internal class Adapter<T>(
        val read: () -> T,
        val write: (T) -> Unit,
        val changes: Flow<T>,
    )

    @PublishedApi
    internal val adapters = ConcurrentHashMap<Preferences.Key<*>, Adapter<*>>()

    @PublishedApi
    internal val enumAdapters = ConcurrentHashMap<Preferences.Key<String>, Adapter<*>>()

    fun <T> bridge(
        key: Preferences.Key<T>,
        read: () -> T,
        write: (T) -> Unit,
        changes: Flow<T>,
    ) {
        adapters[key] = Adapter(read, write, changes)
    }

    /** The enum-valued variant: the key is a String in the store, the value the host hands back is the enum itself. */
    fun <T : Enum<T>> bridgeEnum(
        key: Preferences.Key<String>,
        read: () -> T,
        write: (T) -> Unit,
        changes: Flow<T>,
    ) {
        enumAdapters[key] = Adapter(read, write, changes)
    }

    @PublishedApi
    internal fun <T> adapterFor(key: Preferences.Key<T>): Adapter<T>? {
        @Suppress("UNCHECKED_CAST")
        return adapters[key] as? Adapter<T>
    }

    /**
     * The host's current value for [key], in the store's wire format (an
     * enum's name for an enum-valued key), or null when the host does not
     * own the key.
     */
    @PublishedApi
    internal fun <T> bridgedValue(key: Preferences.Key<T>): T? {
        adapterFor(key)?.let { adapter ->
            val value = runCatching { adapter.read() }.getOrNull()
            // "Nothing chosen" on the host (a blank custom-font uri, say)
            // reads as an absent key, as it would from a fresh store.
            return if (value is String && value.isBlank()) null else value
        }
        @Suppress("UNCHECKED_CAST")
        val enumAdapter = enumAdapters[key as Preferences.Key<String>] ?: return null
        @Suppress("UNCHECKED_CAST")
        return runCatching { (enumAdapter.read() as Enum<*>).name as T }.getOrNull()
    }

    @PublishedApi
    internal fun <T : Enum<T>> enumAdapterFor(key: Preferences.Key<String>): Adapter<T>? {
        @Suppress("UNCHECKED_CAST")
        return enumAdapters[key] as? Adapter<T>
    }
}

object PreferenceStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var _prefs: Preferences? = null
    private val initialSnapshot = kotlinx.coroutines.CompletableDeferred<Preferences>()

    @Volatile
    private var started = false

    val isStarted: Boolean
        get() = started

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            scope.launch {
                try {
                    context.applicationContext.dataStore.data.collect { preferences ->
                        _prefs = preferences
                        initialSnapshot.complete(preferences)
                    }
                } catch (error: Throwable) {
                    initialSnapshot.completeExceptionally(error)
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    reportException(error)
                }
            }
        }
    }

    val snapshot: Preferences?
        get() = _prefs

    suspend fun awaitSnapshot(): Preferences = snapshot ?: initialSnapshot.await()

    fun <T> get(key: Preferences.Key<T>): T? = snapshot?.get(key)

    fun launchEdit(
        dataStore: DataStore<Preferences>,
        block: MutablePreferences.() -> Unit,
    ) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs.block()
            }
        }
    }
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? {
    // A key the host owns answers from the host, as rememberPreference does.
    BridgedPreferences.bridgedValue(key)?.let { return it }
    val snapshot = PreferenceStore.snapshot
    if (snapshot != null) return snapshot[key]

    val loaded =
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(1500) {
                if (PreferenceStore.isStarted) {
                    PreferenceStore.awaitSnapshot()
                } else {
                    data.first()
                }
            }
        }
    return loaded?.get(key)
}

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = get(key) ?: defaultValue

suspend fun <T> DataStore<Preferences>.getAsync(key: Preferences.Key<T>): T? = data.first()[key]

suspend fun <T> DataStore<Preferences>.getAsync(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = data.first()[key] ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current

    val adapter = BridgedPreferences.adapterFor(key)
    if (adapter != null) {
        val state =
            adapter.changes
                .distinctUntilChanged()
                .collectAsStateWithLifecycle(initialValue = adapter.read())
        return remember {
            object : MutableState<T> {
                override var value: T
                    get() = state.value
                    set(value) {
                        adapter.write(value)
                    }

                override fun component1() = value

                override fun component2(): (T) -> Unit = { value = it }
            }
        }
    }

    val initial = remember { PreferenceStore.get(key) ?: defaultValue }

    val state =
        remember {
            context.dataStore.data
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = initial)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    PreferenceStore.launchEdit(context.dataStore) {
                        this[key] = value
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current

    val adapter = BridgedPreferences.enumAdapterFor<T>(key)
    if (adapter != null) {
        val state =
            adapter.changes
                .distinctUntilChanged()
                .collectAsStateWithLifecycle(initialValue = adapter.read())
        return remember {
            object : MutableState<T> {
                override var value: T
                    get() = state.value
                    set(value) {
                        adapter.write(value)
                    }

                override fun component1() = value

                override fun component2(): (T) -> Unit = { value = it }
            }
        }
    }

    val initial = remember { PreferenceStore.get(key).toEnum(defaultValue = defaultValue) }

    val state =
        remember {
            context.dataStore.data
                .map { it[key].toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = initial)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    PreferenceStore.launchEdit(context.dataStore) {
                        this[key] = value.name
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

/**
 * Writes every bridged key's host value into this module's DataStore, so
 * vendored code that reads `dataStore.data` flows directly (rather than
 * through rememberPreference) sees the host's value. All current values go
 * in one edit at install; after that each key writes only when it changes
 * (each flow's first emission is skipped when it equals what the batch
 * wrote). One way only — host to store — so it can never
 * loop back into the host. A blank String clears the key rather than
 * storing "", matching what [BridgedPreferences.bridgedValue] answers.
 */
fun mirrorBridgedPreferences(context: Context) {
    val store = context.applicationContext.dataStore
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun MutablePreferences.put(key: Preferences.Key<*>, value: Any?, isEnum: Boolean) {
        @Suppress("UNCHECKED_CAST")
        val typed = key as Preferences.Key<Any>
        val wire: Any? = if (isEnum) (value as? Enum<*>)?.name else value
        if (wire == null || (wire is String && wire.isBlank())) remove(typed) else this[typed] = wire
    }

    val entries =
        BridgedPreferences.adapters.map { (key, adapter) -> Triple<Preferences.Key<*>, BridgedPreferences.Adapter<*>, Boolean>(key, adapter, false) } +
            BridgedPreferences.enumAdapters.map { (key, adapter) -> Triple<Preferences.Key<*>, BridgedPreferences.Adapter<*>, Boolean>(key, adapter, true) }

    scope.launch {
        val written = HashMap<Preferences.Key<*>, Any?>()
        runCatching {
            store.edit { prefs ->
                entries.forEach { (key, adapter, isEnum) ->
                    runCatching {
                        val value = adapter.read()
                        written[key] = value
                        prefs.put(key, value, isEnum)
                    }
                }
            }
        }
        entries.forEach { (key, adapter, isEnum) ->
            launch {
                var first = true
                runCatching {
                    adapter.changes.collect { value ->
                        // A flow's first value is the current one, which the
                        // batch above already wrote — unless it changed since.
                        val skip = first && written.containsKey(key) && written[key] == value
                        first = false
                        if (!skip) store.edit { it.put(key, value, isEnum) }
                    }
                }
            }
        }
    }
}
