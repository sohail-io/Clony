package io.nekohasekai.sagernet.database;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.lang.Override;
import java.lang.SuppressWarnings;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
final class SagerDatabase_AutoMigration_5_6_Impl extends Migration {
  public SagerDatabase_AutoMigration_5_6_Impl() {
    super(5, 6);
  }

  @Override
  public void migrate(@NonNull final SupportSQLiteDatabase db) {
    db.execSQL("ALTER TABLE `rules` ADD COLUMN `config` TEXT NOT NULL DEFAULT ''");
  }
}
