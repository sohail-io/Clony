package io.nekohasekai.sagernet.database;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Long;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class RuleEntity_Dao_Impl implements RuleEntity.Dao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<RuleEntity> __insertionAdapterOfRuleEntity;

  private final EntityDeletionOrUpdateAdapter<RuleEntity> __deletionAdapterOfRuleEntity;

  private final EntityDeletionOrUpdateAdapter<RuleEntity> __updateAdapterOfRuleEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfReset;

  public RuleEntity_Dao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfRuleEntity = new EntityInsertionAdapter<RuleEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `rules` (`id`,`name`,`config`,`userOrder`,`enabled`,`domains`,`ip`,`port`,`sourcePort`,`network`,`source`,`protocol`,`outbound`,`packages`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RuleEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getConfig());
        statement.bindLong(4, entity.getUserOrder());
        final int _tmp = entity.getEnabled() ? 1 : 0;
        statement.bindLong(5, _tmp);
        statement.bindString(6, entity.getDomains());
        statement.bindString(7, entity.getIp());
        statement.bindString(8, entity.getPort());
        statement.bindString(9, entity.getSourcePort());
        statement.bindString(10, entity.getNetwork());
        statement.bindString(11, entity.getSource());
        statement.bindString(12, entity.getProtocol());
        statement.bindLong(13, entity.getOutbound());
        final String _tmp_1 = StringCollectionConverter.fromSet(entity.getPackages());
        statement.bindString(14, _tmp_1);
      }
    };
    this.__deletionAdapterOfRuleEntity = new EntityDeletionOrUpdateAdapter<RuleEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `rules` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RuleEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfRuleEntity = new EntityDeletionOrUpdateAdapter<RuleEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `rules` SET `id` = ?,`name` = ?,`config` = ?,`userOrder` = ?,`enabled` = ?,`domains` = ?,`ip` = ?,`port` = ?,`sourcePort` = ?,`network` = ?,`source` = ?,`protocol` = ?,`outbound` = ?,`packages` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RuleEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getConfig());
        statement.bindLong(4, entity.getUserOrder());
        final int _tmp = entity.getEnabled() ? 1 : 0;
        statement.bindLong(5, _tmp);
        statement.bindString(6, entity.getDomains());
        statement.bindString(7, entity.getIp());
        statement.bindString(8, entity.getPort());
        statement.bindString(9, entity.getSourcePort());
        statement.bindString(10, entity.getNetwork());
        statement.bindString(11, entity.getSource());
        statement.bindString(12, entity.getProtocol());
        statement.bindLong(13, entity.getOutbound());
        final String _tmp_1 = StringCollectionConverter.fromSet(entity.getPackages());
        statement.bindString(14, _tmp_1);
        statement.bindLong(15, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM rules WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfReset = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM rules";
        return _query;
      }
    };
  }

  @Override
  public long createRule(final RuleEntity rule) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfRuleEntity.insertAndReturnId(rule);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insert(final List<RuleEntity> rules) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfRuleEntity.insert(rules);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteRule(final RuleEntity rule) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfRuleEntity.handle(rule);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteRules(final List<RuleEntity> rules) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfRuleEntity.handleMultiple(rules);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void updateRule(final RuleEntity rule) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfRuleEntity.handle(rule);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void updateRules(final List<RuleEntity> rules) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfRuleEntity.handleMultiple(rules);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public int deleteById(final long ruleId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, ruleId);
    try {
      __db.beginTransaction();
      try {
        final int _result = _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
        return _result;
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteById.release(_stmt);
    }
  }

  @Override
  public void reset() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfReset.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfReset.release(_stmt);
    }
  }

  @Override
  public List<RuleEntity> checkVpnNeeded() {
    final String _sql = "SELECT * from rules WHERE (packages != '') AND enabled = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfConfig = CursorUtil.getColumnIndexOrThrow(_cursor, "config");
      final int _cursorIndexOfUserOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "userOrder");
      final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
      final int _cursorIndexOfDomains = CursorUtil.getColumnIndexOrThrow(_cursor, "domains");
      final int _cursorIndexOfIp = CursorUtil.getColumnIndexOrThrow(_cursor, "ip");
      final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
      final int _cursorIndexOfSourcePort = CursorUtil.getColumnIndexOrThrow(_cursor, "sourcePort");
      final int _cursorIndexOfNetwork = CursorUtil.getColumnIndexOrThrow(_cursor, "network");
      final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
      final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
      final int _cursorIndexOfOutbound = CursorUtil.getColumnIndexOrThrow(_cursor, "outbound");
      final int _cursorIndexOfPackages = CursorUtil.getColumnIndexOrThrow(_cursor, "packages");
      final List<RuleEntity> _result = new ArrayList<RuleEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final RuleEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpName;
        _tmpName = _cursor.getString(_cursorIndexOfName);
        final String _tmpConfig;
        _tmpConfig = _cursor.getString(_cursorIndexOfConfig);
        final long _tmpUserOrder;
        _tmpUserOrder = _cursor.getLong(_cursorIndexOfUserOrder);
        final boolean _tmpEnabled;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfEnabled);
        _tmpEnabled = _tmp != 0;
        final String _tmpDomains;
        _tmpDomains = _cursor.getString(_cursorIndexOfDomains);
        final String _tmpIp;
        _tmpIp = _cursor.getString(_cursorIndexOfIp);
        final String _tmpPort;
        _tmpPort = _cursor.getString(_cursorIndexOfPort);
        final String _tmpSourcePort;
        _tmpSourcePort = _cursor.getString(_cursorIndexOfSourcePort);
        final String _tmpNetwork;
        _tmpNetwork = _cursor.getString(_cursorIndexOfNetwork);
        final String _tmpSource;
        _tmpSource = _cursor.getString(_cursorIndexOfSource);
        final String _tmpProtocol;
        _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
        final long _tmpOutbound;
        _tmpOutbound = _cursor.getLong(_cursorIndexOfOutbound);
        final Set<String> _tmpPackages;
        final String _tmp_1;
        _tmp_1 = _cursor.getString(_cursorIndexOfPackages);
        _tmpPackages = StringCollectionConverter.toSet(_tmp_1);
        _item = new RuleEntity(_tmpId,_tmpName,_tmpConfig,_tmpUserOrder,_tmpEnabled,_tmpDomains,_tmpIp,_tmpPort,_tmpSourcePort,_tmpNetwork,_tmpSource,_tmpProtocol,_tmpOutbound,_tmpPackages);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<RuleEntity> allRules() {
    final String _sql = "SELECT * FROM rules ORDER BY userOrder";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfConfig = CursorUtil.getColumnIndexOrThrow(_cursor, "config");
      final int _cursorIndexOfUserOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "userOrder");
      final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
      final int _cursorIndexOfDomains = CursorUtil.getColumnIndexOrThrow(_cursor, "domains");
      final int _cursorIndexOfIp = CursorUtil.getColumnIndexOrThrow(_cursor, "ip");
      final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
      final int _cursorIndexOfSourcePort = CursorUtil.getColumnIndexOrThrow(_cursor, "sourcePort");
      final int _cursorIndexOfNetwork = CursorUtil.getColumnIndexOrThrow(_cursor, "network");
      final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
      final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
      final int _cursorIndexOfOutbound = CursorUtil.getColumnIndexOrThrow(_cursor, "outbound");
      final int _cursorIndexOfPackages = CursorUtil.getColumnIndexOrThrow(_cursor, "packages");
      final List<RuleEntity> _result = new ArrayList<RuleEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final RuleEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpName;
        _tmpName = _cursor.getString(_cursorIndexOfName);
        final String _tmpConfig;
        _tmpConfig = _cursor.getString(_cursorIndexOfConfig);
        final long _tmpUserOrder;
        _tmpUserOrder = _cursor.getLong(_cursorIndexOfUserOrder);
        final boolean _tmpEnabled;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfEnabled);
        _tmpEnabled = _tmp != 0;
        final String _tmpDomains;
        _tmpDomains = _cursor.getString(_cursorIndexOfDomains);
        final String _tmpIp;
        _tmpIp = _cursor.getString(_cursorIndexOfIp);
        final String _tmpPort;
        _tmpPort = _cursor.getString(_cursorIndexOfPort);
        final String _tmpSourcePort;
        _tmpSourcePort = _cursor.getString(_cursorIndexOfSourcePort);
        final String _tmpNetwork;
        _tmpNetwork = _cursor.getString(_cursorIndexOfNetwork);
        final String _tmpSource;
        _tmpSource = _cursor.getString(_cursorIndexOfSource);
        final String _tmpProtocol;
        _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
        final long _tmpOutbound;
        _tmpOutbound = _cursor.getLong(_cursorIndexOfOutbound);
        final Set<String> _tmpPackages;
        final String _tmp_1;
        _tmp_1 = _cursor.getString(_cursorIndexOfPackages);
        _tmpPackages = StringCollectionConverter.toSet(_tmp_1);
        _item = new RuleEntity(_tmpId,_tmpName,_tmpConfig,_tmpUserOrder,_tmpEnabled,_tmpDomains,_tmpIp,_tmpPort,_tmpSourcePort,_tmpNetwork,_tmpSource,_tmpProtocol,_tmpOutbound,_tmpPackages);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<RuleEntity> enabledRules(final boolean enabled) {
    final String _sql = "SELECT * FROM rules WHERE enabled = ? ORDER BY userOrder";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final int _tmp = enabled ? 1 : 0;
    _statement.bindLong(_argIndex, _tmp);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfConfig = CursorUtil.getColumnIndexOrThrow(_cursor, "config");
      final int _cursorIndexOfUserOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "userOrder");
      final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
      final int _cursorIndexOfDomains = CursorUtil.getColumnIndexOrThrow(_cursor, "domains");
      final int _cursorIndexOfIp = CursorUtil.getColumnIndexOrThrow(_cursor, "ip");
      final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
      final int _cursorIndexOfSourcePort = CursorUtil.getColumnIndexOrThrow(_cursor, "sourcePort");
      final int _cursorIndexOfNetwork = CursorUtil.getColumnIndexOrThrow(_cursor, "network");
      final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
      final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
      final int _cursorIndexOfOutbound = CursorUtil.getColumnIndexOrThrow(_cursor, "outbound");
      final int _cursorIndexOfPackages = CursorUtil.getColumnIndexOrThrow(_cursor, "packages");
      final List<RuleEntity> _result = new ArrayList<RuleEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final RuleEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpName;
        _tmpName = _cursor.getString(_cursorIndexOfName);
        final String _tmpConfig;
        _tmpConfig = _cursor.getString(_cursorIndexOfConfig);
        final long _tmpUserOrder;
        _tmpUserOrder = _cursor.getLong(_cursorIndexOfUserOrder);
        final boolean _tmpEnabled;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfEnabled);
        _tmpEnabled = _tmp_1 != 0;
        final String _tmpDomains;
        _tmpDomains = _cursor.getString(_cursorIndexOfDomains);
        final String _tmpIp;
        _tmpIp = _cursor.getString(_cursorIndexOfIp);
        final String _tmpPort;
        _tmpPort = _cursor.getString(_cursorIndexOfPort);
        final String _tmpSourcePort;
        _tmpSourcePort = _cursor.getString(_cursorIndexOfSourcePort);
        final String _tmpNetwork;
        _tmpNetwork = _cursor.getString(_cursorIndexOfNetwork);
        final String _tmpSource;
        _tmpSource = _cursor.getString(_cursorIndexOfSource);
        final String _tmpProtocol;
        _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
        final long _tmpOutbound;
        _tmpOutbound = _cursor.getLong(_cursorIndexOfOutbound);
        final Set<String> _tmpPackages;
        final String _tmp_2;
        _tmp_2 = _cursor.getString(_cursorIndexOfPackages);
        _tmpPackages = StringCollectionConverter.toSet(_tmp_2);
        _item = new RuleEntity(_tmpId,_tmpName,_tmpConfig,_tmpUserOrder,_tmpEnabled,_tmpDomains,_tmpIp,_tmpPort,_tmpSourcePort,_tmpNetwork,_tmpSource,_tmpProtocol,_tmpOutbound,_tmpPackages);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public Long nextOrder() {
    final String _sql = "SELECT MAX(userOrder) + 1 FROM rules";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final Long _result;
      if (_cursor.moveToFirst()) {
        final Long _tmp;
        if (_cursor.isNull(0)) {
          _tmp = null;
        } else {
          _tmp = _cursor.getLong(0);
        }
        _result = _tmp;
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public RuleEntity getById(final long ruleId) {
    final String _sql = "SELECT * FROM rules WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, ruleId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfConfig = CursorUtil.getColumnIndexOrThrow(_cursor, "config");
      final int _cursorIndexOfUserOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "userOrder");
      final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
      final int _cursorIndexOfDomains = CursorUtil.getColumnIndexOrThrow(_cursor, "domains");
      final int _cursorIndexOfIp = CursorUtil.getColumnIndexOrThrow(_cursor, "ip");
      final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
      final int _cursorIndexOfSourcePort = CursorUtil.getColumnIndexOrThrow(_cursor, "sourcePort");
      final int _cursorIndexOfNetwork = CursorUtil.getColumnIndexOrThrow(_cursor, "network");
      final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
      final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
      final int _cursorIndexOfOutbound = CursorUtil.getColumnIndexOrThrow(_cursor, "outbound");
      final int _cursorIndexOfPackages = CursorUtil.getColumnIndexOrThrow(_cursor, "packages");
      final RuleEntity _result;
      if (_cursor.moveToFirst()) {
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpName;
        _tmpName = _cursor.getString(_cursorIndexOfName);
        final String _tmpConfig;
        _tmpConfig = _cursor.getString(_cursorIndexOfConfig);
        final long _tmpUserOrder;
        _tmpUserOrder = _cursor.getLong(_cursorIndexOfUserOrder);
        final boolean _tmpEnabled;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfEnabled);
        _tmpEnabled = _tmp != 0;
        final String _tmpDomains;
        _tmpDomains = _cursor.getString(_cursorIndexOfDomains);
        final String _tmpIp;
        _tmpIp = _cursor.getString(_cursorIndexOfIp);
        final String _tmpPort;
        _tmpPort = _cursor.getString(_cursorIndexOfPort);
        final String _tmpSourcePort;
        _tmpSourcePort = _cursor.getString(_cursorIndexOfSourcePort);
        final String _tmpNetwork;
        _tmpNetwork = _cursor.getString(_cursorIndexOfNetwork);
        final String _tmpSource;
        _tmpSource = _cursor.getString(_cursorIndexOfSource);
        final String _tmpProtocol;
        _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
        final long _tmpOutbound;
        _tmpOutbound = _cursor.getLong(_cursorIndexOfOutbound);
        final Set<String> _tmpPackages;
        final String _tmp_1;
        _tmp_1 = _cursor.getString(_cursorIndexOfPackages);
        _tmpPackages = StringCollectionConverter.toSet(_tmp_1);
        _result = new RuleEntity(_tmpId,_tmpName,_tmpConfig,_tmpUserOrder,_tmpEnabled,_tmpDomains,_tmpIp,_tmpPort,_tmpSourcePort,_tmpNetwork,_tmpSource,_tmpProtocol,_tmpOutbound,_tmpPackages);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
