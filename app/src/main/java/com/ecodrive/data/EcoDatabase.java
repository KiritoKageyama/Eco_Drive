package com.ecodrive.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = { TripEntity.class }, version = 2, exportSchema = false)
public abstract class EcoDatabase extends RoomDatabase {

    public abstract TripDao tripDao();

    private static volatile EcoDatabase INSTANCE;

    // Migration from version 1 to version 2: Add isSynthetic column
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add new isSynthetic column with default value false
            // This preserves all existing data and marks them as real (not synthetic)
            database.execSQL(
                    "ALTER TABLE trips ADD COLUMN isSynthetic INTEGER NOT NULL DEFAULT 0"
            );
        }
    };

    public static EcoDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (EcoDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            EcoDatabase.class, "eco_drive_database")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
