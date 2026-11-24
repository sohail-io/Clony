package io.nekohasekai.sagernet.database;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SagerDatabase_Impl extends SagerDatabase {
  private volatile ProxyGroup.Dao _proxyGroup;

  private volatile ProxyEntity.Dao _proxyEntity;

  private volatile RuleEntity.Dao _ruleEntity;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(6) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `proxy_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userOrder` INTEGER NOT NULL, `ungrouped` INTEGER NOT NULL, `name` TEXT, `type` INTEGER NOT NULL, `subscription` BLOB, `order` INTEGER NOT NULL, `isSelector` INTEGER NOT NULL, `frontProxy` INTEGER NOT NULL, `landingProxy` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `proxy_entities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupId` INTEGER NOT NULL, `type` INTEGER NOT NULL, `userOrder` INTEGER NOT NULL, `tx` INTEGER NOT NULL, `rx` INTEGER NOT NULL, `status` INTEGER NOT NULL, `ping` INTEGER NOT NULL, `uuid` TEXT NOT NULL, `error` TEXT, `socksBean` BLOB, `httpBean` BLOB, `ssBean` BLOB, `vmessBean` BLOB, `trojanBean` BLOB, `trojanGoBean` BLOB, `mieruBean` BLOB, `naiveBean` BLOB, `hysteriaBean` BLOB, `tuicBean` BLOB, `sshBean` BLOB, `wgBean` BLOB, `shadowTLSBean` BLOB, `anyTLSBean` BLOB, `chainBean` BLOB, `nekoBean` BLOB, `configBean` BLOB)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `groupId` ON `proxy_entities` (`groupId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `config` TEXT NOT NULL DEFAULT '', `userOrder` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `domains` TEXT NOT NULL, `ip` TEXT NOT NULL, `port` TEXT NOT NULL, `sourcePort` TEXT NOT NULL, `network` TEXT NOT NULL, `source` TEXT NOT NULL, `protocol` TEXT NOT NULL, `outbound` INTEGER NOT NULL, `packages` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3d3db9106a89d6f20ef3fde6e81dbaa9')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `proxy_groups`");
        db.execSQL("DROP TABLE IF EXISTS `proxy_entities`");
        db.execSQL("DROP TABLE IF EXISTS `rules`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsProxyGroups = new HashMap<String, TableInfo.Column>(10);
        _columnsProxyGroups.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyGroups.put("userOrder", new TableInfo.Column("userOrder", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyGroups.put("ungrouped", new TableInfo.Column("ungrouped", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyGroups.put("name", new TableInfo.Column("name", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyGroups.put("type", new TableInfo.Column("type", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyGroups.put("subscription", new TableInfo.Column("subscription", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyGroups.put("order", new TableInfo.Column("order", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyGroups.put("isSelector", new TableInfo.Column("isSelector", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyGroups.put("frontProxy", new TableInfo.Column("frontProxy", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyGroups.put("landingProxy", new TableInfo.Column("landingProxy", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysProxyGroups = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesProxyGroups = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoProxyGroups = new TableInfo("proxy_groups", _columnsProxyGroups, _foreignKeysProxyGroups, _indicesProxyGroups);
        final TableInfo _existingProxyGroups = TableInfo.read(db, "proxy_groups");
        if (!_infoProxyGroups.equals(_existingProxyGroups)) {
          return new RoomOpenHelper.ValidationResult(false, "proxy_groups(io.nekohasekai.sagernet.database.ProxyGroup).\n"
                  + " Expected:\n" + _infoProxyGroups + "\n"
                  + " Found:\n" + _existingProxyGroups);
        }
        final HashMap<String, TableInfo.Column> _columnsProxyEntities = new HashMap<String, TableInfo.Column>(27);
        _columnsProxyEntities.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("groupId", new TableInfo.Column("groupId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("type", new TableInfo.Column("type", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("userOrder", new TableInfo.Column("userOrder", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("tx", new TableInfo.Column("tx", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("rx", new TableInfo.Column("rx", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("status", new TableInfo.Column("status", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("ping", new TableInfo.Column("ping", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("uuid", new TableInfo.Column("uuid", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("error", new TableInfo.Column("error", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("socksBean", new TableInfo.Column("socksBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("httpBean", new TableInfo.Column("httpBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("ssBean", new TableInfo.Column("ssBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("vmessBean", new TableInfo.Column("vmessBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("trojanBean", new TableInfo.Column("trojanBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("trojanGoBean", new TableInfo.Column("trojanGoBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("mieruBean", new TableInfo.Column("mieruBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("naiveBean", new TableInfo.Column("naiveBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("hysteriaBean", new TableInfo.Column("hysteriaBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("tuicBean", new TableInfo.Column("tuicBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("sshBean", new TableInfo.Column("sshBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("wgBean", new TableInfo.Column("wgBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("shadowTLSBean", new TableInfo.Column("shadowTLSBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("anyTLSBean", new TableInfo.Column("anyTLSBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("chainBean", new TableInfo.Column("chainBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("nekoBean", new TableInfo.Column("nekoBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxyEntities.put("configBean", new TableInfo.Column("configBean", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysProxyEntities = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesProxyEntities = new HashSet<TableInfo.Index>(1);
        _indicesProxyEntities.add(new TableInfo.Index("groupId", false, Arrays.asList("groupId"), Arrays.asList("ASC")));
        final TableInfo _infoProxyEntities = new TableInfo("proxy_entities", _columnsProxyEntities, _foreignKeysProxyEntities, _indicesProxyEntities);
        final TableInfo _existingProxyEntities = TableInfo.read(db, "proxy_entities");
        if (!_infoProxyEntities.equals(_existingProxyEntities)) {
          return new RoomOpenHelper.ValidationResult(false, "proxy_entities(io.nekohasekai.sagernet.database.ProxyEntity).\n"
                  + " Expected:\n" + _infoProxyEntities + "\n"
                  + " Found:\n" + _existingProxyEntities);
        }
        final HashMap<String, TableInfo.Column> _columnsRules = new HashMap<String, TableInfo.Column>(14);
        _columnsRules.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("config", new TableInfo.Column("config", "TEXT", true, 0, "''", TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("userOrder", new TableInfo.Column("userOrder", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("domains", new TableInfo.Column("domains", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("ip", new TableInfo.Column("ip", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("port", new TableInfo.Column("port", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("sourcePort", new TableInfo.Column("sourcePort", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("network", new TableInfo.Column("network", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("protocol", new TableInfo.Column("protocol", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("outbound", new TableInfo.Column("outbound", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRules.put("packages", new TableInfo.Column("packages", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRules = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRules = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRules = new TableInfo("rules", _columnsRules, _foreignKeysRules, _indicesRules);
        final TableInfo _existingRules = TableInfo.read(db, "rules");
        if (!_infoRules.equals(_existingRules)) {
          return new RoomOpenHelper.ValidationResult(false, "rules(io.nekohasekai.sagernet.database.RuleEntity).\n"
                  + " Expected:\n" + _infoRules + "\n"
                  + " Found:\n" + _existingRules);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "3d3db9106a89d6f20ef3fde6e81dbaa9", "0167b54061df52bf94cefcc2b95c240e");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "proxy_groups","proxy_entities","rules");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `proxy_groups`");
      _db.execSQL("DELETE FROM `proxy_entities`");
      _db.execSQL("DELETE FROM `rules`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(ProxyGroup.Dao.class, ProxyGroup_Dao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ProxyEntity.Dao.class, ProxyEntity_Dao_Impl.getRequiredConverters());
    _typeConvertersMap.put(RuleEntity.Dao.class, RuleEntity_Dao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    _autoMigrations.add(new SagerDatabase_AutoMigration_3_4_Impl());
    _autoMigrations.add(new SagerDatabase_AutoMigration_4_5_Impl());
    _autoMigrations.add(new SagerDatabase_AutoMigration_5_6_Impl());
    return _autoMigrations;
  }

  @Override
  public ProxyGroup.Dao groupDao() {
    if (_proxyGroup != null) {
      return _proxyGroup;
    } else {
      synchronized(this) {
        if(_proxyGroup == null) {
          _proxyGroup = new ProxyGroup_Dao_Impl(this);
        }
        return _proxyGroup;
      }
    }
  }

  @Override
  public ProxyEntity.Dao proxyDao() {
    if (_proxyEntity != null) {
      return _proxyEntity;
    } else {
      synchronized(this) {
        if(_proxyEntity == null) {
          _proxyEntity = new ProxyEntity_Dao_Impl(this);
        }
        return _proxyEntity;
      }
    }
  }

  @Override
  public RuleEntity.Dao rulesDao() {
    if (_ruleEntity != null) {
      return _ruleEntity;
    } else {
      synchronized(this) {
        if(_ruleEntity == null) {
          _ruleEntity = new RuleEntity_Dao_Impl(this);
        }
        return _ruleEntity;
      }
    }
  }
}
