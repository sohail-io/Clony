package io.nekohasekai.sagernet.database;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ProxyGroup_Dao_Impl implements ProxyGroup.Dao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ProxyGroup> __insertionAdapterOfProxyGroup;

  private final EntityDeletionOrUpdateAdapter<ProxyGroup> __deletionAdapterOfProxyGroup;

  private final EntityDeletionOrUpdateAdapter<ProxyGroup> __updateAdapterOfProxyGroup;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfReset;

  public ProxyGroup_Dao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfProxyGroup = new EntityInsertionAdapter<ProxyGroup>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `proxy_groups` (`id`,`userOrder`,`ungrouped`,`name`,`type`,`subscription`,`order`,`isSelector`,`frontProxy`,`landingProxy`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProxyGroup entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getUserOrder());
        final int _tmp = entity.getUngrouped() ? 1 : 0;
        statement.bindLong(3, _tmp);
        if (entity.getName() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getName());
        }
        statement.bindLong(5, entity.getType());
        final byte[] _tmp_1 = KryoConverters.serialize(entity.getSubscription());
        statement.bindBlob(6, _tmp_1);
        statement.bindLong(7, entity.getOrder());
        final int _tmp_2 = entity.isSelector() ? 1 : 0;
        statement.bindLong(8, _tmp_2);
        statement.bindLong(9, entity.getFrontProxy());
        statement.bindLong(10, entity.getLandingProxy());
      }
    };
    this.__deletionAdapterOfProxyGroup = new EntityDeletionOrUpdateAdapter<ProxyGroup>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `proxy_groups` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProxyGroup entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfProxyGroup = new EntityDeletionOrUpdateAdapter<ProxyGroup>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `proxy_groups` SET `id` = ?,`userOrder` = ?,`ungrouped` = ?,`name` = ?,`type` = ?,`subscription` = ?,`order` = ?,`isSelector` = ?,`frontProxy` = ?,`landingProxy` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProxyGroup entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getUserOrder());
        final int _tmp = entity.getUngrouped() ? 1 : 0;
        statement.bindLong(3, _tmp);
        if (entity.getName() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getName());
        }
        statement.bindLong(5, entity.getType());
        final byte[] _tmp_1 = KryoConverters.serialize(entity.getSubscription());
        statement.bindBlob(6, _tmp_1);
        statement.bindLong(7, entity.getOrder());
        final int _tmp_2 = entity.isSelector() ? 1 : 0;
        statement.bindLong(8, _tmp_2);
        statement.bindLong(9, entity.getFrontProxy());
        statement.bindLong(10, entity.getLandingProxy());
        statement.bindLong(11, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM proxy_groups WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfReset = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM proxy_groups";
        return _query;
      }
    };
  }

  @Override
  public long createGroup(final ProxyGroup group) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfProxyGroup.insertAndReturnId(group);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insert(final List<ProxyGroup> groupList) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfProxyGroup.insert(groupList);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteGroup(final ProxyGroup group) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfProxyGroup.handle(group);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteGroup(final List<ProxyGroup> groupList) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfProxyGroup.handleMultiple(groupList);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void updateGroup(final ProxyGroup group) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfProxyGroup.handle(group);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public int deleteById(final long groupId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, groupId);
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
  public List<ProxyGroup> allGroups() {
    final String _sql = "SELECT * FROM proxy_groups ORDER BY userOrder";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfUserOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "userOrder");
      final int _cursorIndexOfUngrouped = CursorUtil.getColumnIndexOrThrow(_cursor, "ungrouped");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfSubscription = CursorUtil.getColumnIndexOrThrow(_cursor, "subscription");
      final int _cursorIndexOfOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "order");
      final int _cursorIndexOfIsSelector = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelector");
      final int _cursorIndexOfFrontProxy = CursorUtil.getColumnIndexOrThrow(_cursor, "frontProxy");
      final int _cursorIndexOfLandingProxy = CursorUtil.getColumnIndexOrThrow(_cursor, "landingProxy");
      final List<ProxyGroup> _result = new ArrayList<ProxyGroup>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ProxyGroup _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final long _tmpUserOrder;
        _tmpUserOrder = _cursor.getLong(_cursorIndexOfUserOrder);
        final boolean _tmpUngrouped;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfUngrouped);
        _tmpUngrouped = _tmp != 0;
        final String _tmpName;
        if (_cursor.isNull(_cursorIndexOfName)) {
          _tmpName = null;
        } else {
          _tmpName = _cursor.getString(_cursorIndexOfName);
        }
        final int _tmpType;
        _tmpType = _cursor.getInt(_cursorIndexOfType);
        final SubscriptionBean _tmpSubscription;
        final byte[] _tmp_1;
        _tmp_1 = _cursor.getBlob(_cursorIndexOfSubscription);
        _tmpSubscription = KryoConverters.subscriptionDeserialize(_tmp_1);
        final int _tmpOrder;
        _tmpOrder = _cursor.getInt(_cursorIndexOfOrder);
        final boolean _tmpIsSelector;
        final int _tmp_2;
        _tmp_2 = _cursor.getInt(_cursorIndexOfIsSelector);
        _tmpIsSelector = _tmp_2 != 0;
        final long _tmpFrontProxy;
        _tmpFrontProxy = _cursor.getLong(_cursorIndexOfFrontProxy);
        final long _tmpLandingProxy;
        _tmpLandingProxy = _cursor.getLong(_cursorIndexOfLandingProxy);
        _item = new ProxyGroup(_tmpId,_tmpUserOrder,_tmpUngrouped,_tmpName,_tmpType,_tmpSubscription,_tmpOrder,_tmpIsSelector,_tmpFrontProxy,_tmpLandingProxy);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public Object subscriptions(final Continuation<? super List<ProxyGroup>> $completion) {
    final String _sql = "SELECT * FROM proxy_groups WHERE type = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ProxyGroup>>() {
      @Override
      @NonNull
      public List<ProxyGroup> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "userOrder");
          final int _cursorIndexOfUngrouped = CursorUtil.getColumnIndexOrThrow(_cursor, "ungrouped");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfSubscription = CursorUtil.getColumnIndexOrThrow(_cursor, "subscription");
          final int _cursorIndexOfOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "order");
          final int _cursorIndexOfIsSelector = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelector");
          final int _cursorIndexOfFrontProxy = CursorUtil.getColumnIndexOrThrow(_cursor, "frontProxy");
          final int _cursorIndexOfLandingProxy = CursorUtil.getColumnIndexOrThrow(_cursor, "landingProxy");
          final List<ProxyGroup> _result = new ArrayList<ProxyGroup>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ProxyGroup _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpUserOrder;
            _tmpUserOrder = _cursor.getLong(_cursorIndexOfUserOrder);
            final boolean _tmpUngrouped;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfUngrouped);
            _tmpUngrouped = _tmp != 0;
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final int _tmpType;
            _tmpType = _cursor.getInt(_cursorIndexOfType);
            final SubscriptionBean _tmpSubscription;
            final byte[] _tmp_1;
            _tmp_1 = _cursor.getBlob(_cursorIndexOfSubscription);
            _tmpSubscription = KryoConverters.subscriptionDeserialize(_tmp_1);
            final int _tmpOrder;
            _tmpOrder = _cursor.getInt(_cursorIndexOfOrder);
            final boolean _tmpIsSelector;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsSelector);
            _tmpIsSelector = _tmp_2 != 0;
            final long _tmpFrontProxy;
            _tmpFrontProxy = _cursor.getLong(_cursorIndexOfFrontProxy);
            final long _tmpLandingProxy;
            _tmpLandingProxy = _cursor.getLong(_cursorIndexOfLandingProxy);
            _item = new ProxyGroup(_tmpId,_tmpUserOrder,_tmpUngrouped,_tmpName,_tmpType,_tmpSubscription,_tmpOrder,_tmpIsSelector,_tmpFrontProxy,_tmpLandingProxy);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Long nextOrder() {
    final String _sql = "SELECT MAX(userOrder) + 1 FROM proxy_groups";
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
  public ProxyGroup getById(final long groupId) {
    final String _sql = "SELECT * FROM proxy_groups WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, groupId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfUserOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "userOrder");
      final int _cursorIndexOfUngrouped = CursorUtil.getColumnIndexOrThrow(_cursor, "ungrouped");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfSubscription = CursorUtil.getColumnIndexOrThrow(_cursor, "subscription");
      final int _cursorIndexOfOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "order");
      final int _cursorIndexOfIsSelector = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelector");
      final int _cursorIndexOfFrontProxy = CursorUtil.getColumnIndexOrThrow(_cursor, "frontProxy");
      final int _cursorIndexOfLandingProxy = CursorUtil.getColumnIndexOrThrow(_cursor, "landingProxy");
      final ProxyGroup _result;
      if (_cursor.moveToFirst()) {
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final long _tmpUserOrder;
        _tmpUserOrder = _cursor.getLong(_cursorIndexOfUserOrder);
        final boolean _tmpUngrouped;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfUngrouped);
        _tmpUngrouped = _tmp != 0;
        final String _tmpName;
        if (_cursor.isNull(_cursorIndexOfName)) {
          _tmpName = null;
        } else {
          _tmpName = _cursor.getString(_cursorIndexOfName);
        }
        final int _tmpType;
        _tmpType = _cursor.getInt(_cursorIndexOfType);
        final SubscriptionBean _tmpSubscription;
        final byte[] _tmp_1;
        _tmp_1 = _cursor.getBlob(_cursorIndexOfSubscription);
        _tmpSubscription = KryoConverters.subscriptionDeserialize(_tmp_1);
        final int _tmpOrder;
        _tmpOrder = _cursor.getInt(_cursorIndexOfOrder);
        final boolean _tmpIsSelector;
        final int _tmp_2;
        _tmp_2 = _cursor.getInt(_cursorIndexOfIsSelector);
        _tmpIsSelector = _tmp_2 != 0;
        final long _tmpFrontProxy;
        _tmpFrontProxy = _cursor.getLong(_cursorIndexOfFrontProxy);
        final long _tmpLandingProxy;
        _tmpLandingProxy = _cursor.getLong(_cursorIndexOfLandingProxy);
        _result = new ProxyGroup(_tmpId,_tmpUserOrder,_tmpUngrouped,_tmpName,_tmpType,_tmpSubscription,_tmpOrder,_tmpIsSelector,_tmpFrontProxy,_tmpLandingProxy);
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
