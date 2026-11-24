package io.nekohasekai.sagernet.database.preference;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class KeyValuePair_Dao_PublicDatabase_Impl implements KeyValuePair.Dao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<KeyValuePair> __insertionAdapterOfKeyValuePair;

  private final EntityInsertionAdapter<KeyValuePair> __insertionAdapterOfKeyValuePair_1;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final SharedSQLiteStatement __preparedStmtOfReset;

  public KeyValuePair_Dao_PublicDatabase_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfKeyValuePair = new EntityInsertionAdapter<KeyValuePair>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `KeyValuePair` (`key`,`valueType`,`value`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final KeyValuePair entity) {
        statement.bindString(1, entity.getKey());
        statement.bindLong(2, entity.getValueType());
        statement.bindBlob(3, entity.getValue());
      }
    };
    this.__insertionAdapterOfKeyValuePair_1 = new EntityInsertionAdapter<KeyValuePair>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `KeyValuePair` (`key`,`valueType`,`value`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final KeyValuePair entity) {
        statement.bindString(1, entity.getKey());
        statement.bindLong(2, entity.getValueType());
        statement.bindBlob(3, entity.getValue());
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM `KeyValuePair` WHERE `key` = ?";
        return _query;
      }
    };
    this.__preparedStmtOfReset = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM `KeyValuePair`";
        return _query;
      }
    };
  }

  @Override
  public long put(final KeyValuePair value) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfKeyValuePair.insertAndReturnId(value);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insert(final List<KeyValuePair> list) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfKeyValuePair_1.insert(list);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public int delete(final String key) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
    int _argIndex = 1;
    _stmt.bindString(_argIndex, key);
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
      __preparedStmtOfDelete.release(_stmt);
    }
  }

  @Override
  public int reset() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfReset.acquire();
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
      __preparedStmtOfReset.release(_stmt);
    }
  }

  @Override
  public List<KeyValuePair> all() {
    final String _sql = "SELECT * FROM `KeyValuePair`";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfKey = CursorUtil.getColumnIndexOrThrow(_cursor, "key");
      final int _cursorIndexOfValueType = CursorUtil.getColumnIndexOrThrow(_cursor, "valueType");
      final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
      final List<KeyValuePair> _result = new ArrayList<KeyValuePair>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final KeyValuePair _item;
        _item = new KeyValuePair();
        final String _tmpKey;
        _tmpKey = _cursor.getString(_cursorIndexOfKey);
        _item.setKey(_tmpKey);
        final int _tmpValueType;
        _tmpValueType = _cursor.getInt(_cursorIndexOfValueType);
        _item.setValueType(_tmpValueType);
        final byte[] _tmpValue;
        _tmpValue = _cursor.getBlob(_cursorIndexOfValue);
        _item.setValue(_tmpValue);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public KeyValuePair get(final String key) {
    final String _sql = "SELECT * FROM `KeyValuePair` WHERE `key` = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, key);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfKey = CursorUtil.getColumnIndexOrThrow(_cursor, "key");
      final int _cursorIndexOfValueType = CursorUtil.getColumnIndexOrThrow(_cursor, "valueType");
      final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
      final KeyValuePair _result;
      if (_cursor.moveToFirst()) {
        _result = new KeyValuePair();
        final String _tmpKey;
        _tmpKey = _cursor.getString(_cursorIndexOfKey);
        _result.setKey(_tmpKey);
        final int _tmpValueType;
        _tmpValueType = _cursor.getInt(_cursorIndexOfValueType);
        _result.setValueType(_tmpValueType);
        final byte[] _tmpValue;
        _tmpValue = _cursor.getBlob(_cursorIndexOfValue);
        _result.setValue(_tmpValue);
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
