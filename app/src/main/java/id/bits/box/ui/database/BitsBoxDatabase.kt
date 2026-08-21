package id.bits.box.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.matrix.roomigrant.GenerateRoomMigrations
import id.bits.box.Key
import id.bits.box.BitsBoxApp
import id.bits.box.fmt.KryoConverters
import id.bits.box.fmt.gson.GsonConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [ProxyGroup::class, ProxyEntity::class, RuleEntity::class],
    version = 6,
    autoMigrations = [] // Disabled - need schemas manually copied
)
@TypeConverters(value = [KryoConverters::class, GsonConverters::class])
@GenerateRoomMigrations
abstract class BitsBoxDatabase : RoomDatabase() {

    companion object {
        val instance by lazy {
            BitsBoxApp.application.getDatabasePath(Key.DB_PROFILE).parentFile?.mkdirs()
            Room.databaseBuilder(BitsBoxApp.application, BitsBoxDatabase::class.java, Key.DB_PROFILE)
//                .addMigrations(*BitsBoxDatabase_Migrations.build())
                .setJournalMode(JournalMode.TRUNCATE)
                .allowMainThreadQueries()
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration(true)
                .setQueryExecutor { BitsBoxApp.application.applicationScope.launch(Dispatchers.IO) { it.run() } }
                .build()
        }

        val groupDao get() = instance.groupDao()
        val proxyDao get() = instance.proxyDao()
        val rulesDao get() = instance.rulesDao()

    }

    abstract fun groupDao(): ProxyGroup.Dao
    abstract fun proxyDao(): ProxyEntity.Dao
    abstract fun rulesDao(): RuleEntity.Dao

}
