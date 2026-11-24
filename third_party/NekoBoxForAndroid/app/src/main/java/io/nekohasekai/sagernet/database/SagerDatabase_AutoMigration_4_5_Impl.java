package io.nekohasekai.sagernet.database;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.lang.Override;
import java.lang.SuppressWarnings;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
final class SagerDatabase_AutoMigration_4_5_Impl extends Migration {
  public SagerDatabase_AutoMigration_4_5_Impl() {
    super(4, 5);
  }

  @Override
  public void migrate(@NonNull final SupportSQLiteDatabase db) {
    db.execSQL("ALTER TABLE `proxy_entities` ADD COLUMN `anyTLSBean` BLOB DEFAULT NULL");
  }
}
