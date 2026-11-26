package arg.adegtiarev.heartratetraining

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import arg.adegtiarev.heartratetraining.settings.DataStoreZoneSettings
import arg.adegtiarev.heartratetraining.zones.ZoneConfiguration
import arg.adegtiarev.heartratetraining.zones.ZoneSettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZoneDataStoreTest {
    @get:Rule val folder = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
    private val file get() = File(folder.root, "zones.preferences_pb")
    private val a = ZoneConfiguration.create(listOf(100, 120, 140, 160), listOf(0, 1, 2, 0xFFFFFF, 1))!!
    private val b = ZoneConfiguration.create(listOf(100, 125, 140, 160), listOf(0, 0x183D6C, 2, 0xFFFFFF, 1))!!

    private suspend fun withStore(path: File = file, block: suspend (DataStore<Preferences>) -> Unit) {
        val owner = SupervisorJob()
        try {
            block(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + owner)) { path })
        } finally {
            owner.cancelAndJoin()
        }
    }

    private suspend fun DataStoreZoneSettings.loaded() = settings.first { it != ZoneSettings.Loading }

    @Test fun twoEditsAndTwoReopensPreserveEveryField() = runBlocking {
        withStore {
            val repo = DataStoreZoneSettings(it)
            assertEquals(ZoneSettings.Unconfigured, repo.loaded())
            repo.save(a)
        }
        withStore {
            val repo = DataStoreZoneSettings(it)
            assertEquals(ZoneSettings.Saved(a), repo.loaded())
            repo.save(b)
        }
        repeat(2) { withStore { assertEquals(ZoneSettings.Saved(b), DataStoreZoneSettings(it).loaded()) } }
    }

    @Test fun incompleteWrongTypeAndOutOfOrderValuesStayInvalidUntilExplicitSave() = runBlocking {
        withStore { store ->
            val repo = DataStoreZoneSettings(store)
            store.edit { it[intPreferencesKey("version")] = 1 }
            assertEquals(ZoneSettings.Invalid, repo.loaded())
            repo.save(a)
            store.edit { it[stringPreferencesKey("zone_2_start")] = "broken" }
            assertEquals(ZoneSettings.Invalid, repo.loaded())
            repo.save(a)
            store.edit { it[intPreferencesKey("zone_3_start")] = 99 }
            assertEquals(ZoneSettings.Invalid, repo.loaded())
            repo.save(b)
            assertEquals(ZoneSettings.Saved(b), repo.loaded())
        }
    }

    @Test fun unreadableFileIsReportedAndCanBeRetried() = runBlocking {
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
        withStore {
            val repo = DataStoreZoneSettings(it)
            assertEquals(ZoneSettings.ReadError, repo.loaded())
            // Simulate the unreadable file becoming available again in this isolated store.
            file.delete()
            repo.retry()
            assertEquals(ZoneSettings.Unconfigured, repo.loaded())
        }
    }

    @Test fun aFailedDiskWriteCanBeRetriedWithTheCompleteConfiguration() = runBlocking {
        val parent = folder.newFile("unavailable-directory")
        withStore(File(parent, "zones.preferences_pb")) {
            val repo = DataStoreZoneSettings(it)
            try {
                repo.save(b)
                fail("Writing below a regular file must fail")
            } catch (_: IOException) {
                check(parent.delete() && parent.mkdir())
            }
            repo.save(b)
            assertEquals(ZoneSettings.Saved(b), repo.loaded())
        }
    }
}
