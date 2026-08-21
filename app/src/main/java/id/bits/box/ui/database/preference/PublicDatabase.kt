package id.bits.box.database.preference

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.matrix.roomigrant.GenerateRoomMigrations
import id.bits.box.Key
import id.bits.box.BitsBoxApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [KeyValuePair::class], version = 1)
@GenerateRoomMigrations
abstract class PublicDatabase : RoomDatabase() {
    companion object {
        val instance by lazy {
            BitsBoxApp.application.getDatabasePath(Key.DB_PROFILE).parentFile?.mkdirs()
            Room.databaseBuilder(BitsBoxApp.application, PublicDatabase::class.java, Key.DB_PUBLIC)
                .setJournalMode(JournalMode.TRUNCATE)
                .allowMainThreadQueries()
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration(true)
                .setQueryExecutor { BitsBoxApp.application.applicationScope.launch(Dispatchers.IO) { it.run() } }
                .build()
        }

        val kvPairDao get() = instance.keyValuePairDao()
    }

    abstract fun keyValuePairDao(): KeyValuePair.Dao

}
