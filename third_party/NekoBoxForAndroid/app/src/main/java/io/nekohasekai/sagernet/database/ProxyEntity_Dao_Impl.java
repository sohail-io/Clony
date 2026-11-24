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
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.http.HttpBean;
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean;
import io.nekohasekai.sagernet.fmt.internal.ChainBean;
import io.nekohasekai.sagernet.fmt.mieru.MieruBean;
import io.nekohasekai.sagernet.fmt.naive.NaiveBean;
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean;
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean;
import io.nekohasekai.sagernet.fmt.ssh.SSHBean;
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean;
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean;
import io.nekohasekai.sagernet.fmt.tuic.TuicBean;
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean;
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean;
import java.lang.Class;
import java.lang.Long;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;
import moe.matsuri.nb4a.proxy.anytls.AnyTLSBean;
import moe.matsuri.nb4a.proxy.config.ConfigBean;
import moe.matsuri.nb4a.proxy.neko.NekoBean;
import moe.matsuri.nb4a.proxy.shadowtls.ShadowTLSBean;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ProxyEntity_Dao_Impl implements ProxyEntity.Dao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ProxyEntity> __insertionAdapterOfProxyEntity;

  private final EntityDeletionOrUpdateAdapter<ProxyEntity> __deletionAdapterOfProxyEntity;

  private final EntityDeletionOrUpdateAdapter<ProxyEntity> __updateAdapterOfProxyEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByGroup;

  private final SharedSQLiteStatement __preparedStmtOfReset;

  public ProxyEntity_Dao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfProxyEntity = new EntityInsertionAdapter<ProxyEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `proxy_entities` (`id`,`groupId`,`type`,`userOrder`,`tx`,`rx`,`status`,`ping`,`uuid`,`error`,`socksBean`,`httpBean`,`ssBean`,`vmessBean`,`trojanBean`,`trojanGoBean`,`mieruBean`,`naiveBean`,`hysteriaBean`,`tuicBean`,`sshBean`,`wgBean`,`shadowTLSBean`,`anyTLSBean`,`chainBean`,`nekoBean`,`configBean`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProxyEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getGroupId());
        statement.bindLong(3, entity.getType());
        statement.bindLong(4, entity.getUserOrder());
        statement.bindLong(5, entity.getTx());
        statement.bindLong(6, entity.getRx());
        statement.bindLong(7, entity.getStatus());
        statement.bindLong(8, entity.getPing());
        statement.bindString(9, entity.getUuid());
        if (entity.getError() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getError());
        }
        final byte[] _tmp = KryoConverters.serialize(entity.getSocksBean());
        statement.bindBlob(11, _tmp);
        final byte[] _tmp_1 = KryoConverters.serialize(entity.getHttpBean());
        statement.bindBlob(12, _tmp_1);
        final byte[] _tmp_2 = KryoConverters.serialize(entity.getSsBean());
        statement.bindBlob(13, _tmp_2);
        final byte[] _tmp_3 = KryoConverters.serialize(entity.getVmessBean());
        statement.bindBlob(14, _tmp_3);
        final byte[] _tmp_4 = KryoConverters.serialize(entity.getTrojanBean());
        statement.bindBlob(15, _tmp_4);
        final byte[] _tmp_5 = KryoConverters.serialize(entity.getTrojanGoBean());
        statement.bindBlob(16, _tmp_5);
        final byte[] _tmp_6 = KryoConverters.serialize(entity.getMieruBean());
        statement.bindBlob(17, _tmp_6);
        final byte[] _tmp_7 = KryoConverters.serialize(entity.getNaiveBean());
        statement.bindBlob(18, _tmp_7);
        final byte[] _tmp_8 = KryoConverters.serialize(entity.getHysteriaBean());
        statement.bindBlob(19, _tmp_8);
        final byte[] _tmp_9 = KryoConverters.serialize(entity.getTuicBean());
        statement.bindBlob(20, _tmp_9);
        final byte[] _tmp_10 = KryoConverters.serialize(entity.getSshBean());
        statement.bindBlob(21, _tmp_10);
        final byte[] _tmp_11 = KryoConverters.serialize(entity.getWgBean());
        statement.bindBlob(22, _tmp_11);
        final byte[] _tmp_12 = KryoConverters.serialize(entity.getShadowTLSBean());
        statement.bindBlob(23, _tmp_12);
        final byte[] _tmp_13 = KryoConverters.serialize(entity.getAnyTLSBean());
        statement.bindBlob(24, _tmp_13);
        final byte[] _tmp_14 = KryoConverters.serialize(entity.getChainBean());
        statement.bindBlob(25, _tmp_14);
        final byte[] _tmp_15 = KryoConverters.serialize(entity.getNekoBean());
        statement.bindBlob(26, _tmp_15);
        final byte[] _tmp_16 = KryoConverters.serialize(entity.getConfigBean());
        statement.bindBlob(27, _tmp_16);
      }
    };
    this.__deletionAdapterOfProxyEntity = new EntityDeletionOrUpdateAdapter<ProxyEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `proxy_entities` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProxyEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfProxyEntity = new EntityDeletionOrUpdateAdapter<ProxyEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `proxy_entities` SET `id` = ?,`groupId` = ?,`type` = ?,`userOrder` = ?,`tx` = ?,`rx` = ?,`status` = ?,`ping` = ?,`uuid` = ?,`error` = ?,`socksBean` = ?,`httpBean` = ?,`ssBean` = ?,`vmessBean` = ?,`trojanBean` = ?,`trojanGoBean` = ?,`mieruBean` = ?,`naiveBean` = ?,`hysteriaBean` = ?,`tuicBean` = ?,`sshBean` = ?,`wgBean` = ?,`shadowTLSBean` = ?,`anyTLSBean` = ?,`chainBean` = ?,`nekoBean` = ?,`configBean` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProxyEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getGroupId());
        statement.bindLong(3, entity.getType());
        statement.bindLong(4, entity.getUserOrder());
        statement.bindLong(5, entity.getTx());
        statement.bindLong(6, entity.getRx());
        statement.bindLong(7, entity.getStatus());
        statement.bindLong(8, entity.getPing());
        statement.bindString(9, entity.getUuid());
        if (entity.getError() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getError());
        }
        final byte[] _tmp = KryoConverters.serialize(entity.getSocksBean());
        statement.bindBlob(11, _tmp);
        final byte[] _tmp_1 = KryoConverters.serialize(entity.getHttpBean());
        statement.bindBlob(12, _tmp_1);
        final byte[] _tmp_2 = KryoConverters.serialize(entity.getSsBean());
        statement.bindBlob(13, _tmp_2);
        final byte[] _tmp_3 = KryoConverters.serialize(entity.getVmessBean());
        statement.bindBlob(14, _tmp_3);
        final byte[] _tmp_4 = KryoConverters.serialize(entity.getTrojanBean());
        statement.bindBlob(15, _tmp_4);
        final byte[] _tmp_5 = KryoConverters.serialize(entity.getTrojanGoBean());
        statement.bindBlob(16, _tmp_5);
        final byte[] _tmp_6 = KryoConverters.serialize(entity.getMieruBean());
        statement.bindBlob(17, _tmp_6);
        final byte[] _tmp_7 = KryoConverters.serialize(entity.getNaiveBean());
        statement.bindBlob(18, _tmp_7);
        final byte[] _tmp_8 = KryoConverters.serialize(entity.getHysteriaBean());
        statement.bindBlob(19, _tmp_8);
        final byte[] _tmp_9 = KryoConverters.serialize(entity.getTuicBean());
        statement.bindBlob(20, _tmp_9);
        final byte[] _tmp_10 = KryoConverters.serialize(entity.getSshBean());
        statement.bindBlob(21, _tmp_10);
        final byte[] _tmp_11 = KryoConverters.serialize(entity.getWgBean());
        statement.bindBlob(22, _tmp_11);
        final byte[] _tmp_12 = KryoConverters.serialize(entity.getShadowTLSBean());
        statement.bindBlob(23, _tmp_12);
        final byte[] _tmp_13 = KryoConverters.serialize(entity.getAnyTLSBean());
        statement.bindBlob(24, _tmp_13);
        final byte[] _tmp_14 = KryoConverters.serialize(entity.getChainBean());
        statement.bindBlob(25, _tmp_14);
        final byte[] _tmp_15 = KryoConverters.serialize(entity.getNekoBean());
        statement.bindBlob(26, _tmp_15);
        final byte[] _tmp_16 = KryoConverters.serialize(entity.getConfigBean());
        statement.bindBlob(27, _tmp_16);
        statement.bindLong(28, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM proxy_entities WHERE id IN (?)";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteByGroup = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM proxy_entities WHERE groupId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfReset = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM proxy_entities";
        return _query;
      }
    };
  }

  @Override
  public long addProxy(final ProxyEntity proxy) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfProxyEntity.insertAndReturnId(proxy);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insert(final List<ProxyEntity> proxies) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfProxyEntity.insert(proxies);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public int deleteProxy(final ProxyEntity proxy) {
    __db.assertNotSuspendingTransaction();
    int _total = 0;
    __db.beginTransaction();
    try {
      _total += __deletionAdapterOfProxyEntity.handle(proxy);
      __db.setTransactionSuccessful();
      return _total;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public int deleteProxy(final List<ProxyEntity> proxies) {
    __db.assertNotSuspendingTransaction();
    int _total = 0;
    __db.beginTransaction();
    try {
      _total += __deletionAdapterOfProxyEntity.handleMultiple(proxies);
      __db.setTransactionSuccessful();
      return _total;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public int updateProxy(final ProxyEntity proxy) {
    __db.assertNotSuspendingTransaction();
    int _total = 0;
    __db.beginTransaction();
    try {
      _total += __updateAdapterOfProxyEntity.handle(proxy);
      __db.setTransactionSuccessful();
      return _total;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public int updateProxy(final List<ProxyEntity> proxies) {
    __db.assertNotSuspendingTransaction();
    int _total = 0;
    __db.beginTransaction();
    try {
      _total += __updateAdapterOfProxyEntity.handleMultiple(proxies);
      __db.setTransactionSuccessful();
      return _total;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public int deleteById(final long proxyId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, proxyId);
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
  public void deleteByGroup(final long groupId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByGroup.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, groupId);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteByGroup.release(_stmt);
    }
  }

  @Override
  public int deleteAll(final long groupId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByGroup.acquire();
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
      __preparedStmtOfDeleteByGroup.release(_stmt);
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
  public List<ProxyEntity> getAll() {
    final String _sql = "select * from proxy_entities";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfGroupId = CursorUtil.getColumnIndexOrThrow(_cursor, "groupId");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfUserOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "userOrder");
      final int _cursorIndexOfTx = CursorUtil.getColumnIndexOrThrow(_cursor, "tx");
      final int _cursorIndexOfRx = CursorUtil.getColumnIndexOrThrow(_cursor, "rx");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfPing = CursorUtil.getColumnIndexOrThrow(_cursor, "ping");
      final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
      final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
      final int _cursorIndexOfSocksBean = CursorUtil.getColumnIndexOrThrow(_cursor, "socksBean");
      final int _cursorIndexOfHttpBean = CursorUtil.getColumnIndexOrThrow(_cursor, "httpBean");
      final int _cursorIndexOfSsBean = CursorUtil.getColumnIndexOrThrow(_cursor, "ssBean");
      final int _cursorIndexOfVmessBean = CursorUtil.getColumnIndexOrThrow(_cursor, "vmessBean");
      final int _cursorIndexOfTrojanBean = CursorUtil.getColumnIndexOrThrow(_cursor, "trojanBean");
      final int _cursorIndexOfTrojanGoBean = CursorUtil.getColumnIndexOrThrow(_cursor, "trojanGoBean");
      final int _cursorIndexOfMieruBean = CursorUtil.getColumnIndexOrThrow(_cursor, "mieruBean");
      final int _cursorIndexOfNaiveBean = CursorUtil.getColumnIndexOrThrow(_cursor, "naiveBean");
      final int _cursorIndexOfHysteriaBean = CursorUtil.getColumnIndexOrThrow(_cursor, "hysteriaBean");
      final int _cursorIndexOfTuicBean = CursorUtil.getColumnIndexOrThrow(_cursor, "tuicBean");
      final int _cursorIndexOfSshBean = CursorUtil.getColumnIndexOrThrow(_cursor, "sshBean");
      final int _cursorIndexOfWgBean = CursorUtil.getColumnIndexOrThrow(_cursor, "wgBean");
      final int _cursorIndexOfShadowTLSBean = CursorUtil.getColumnIndexOrThrow(_cursor, "shadowTLSBean");
      final int _cursorIndexOfAnyTLSBean = CursorUtil.getColumnIndexOrThrow(_cursor, "anyTLSBean");
      final int _cursorIndexOfChainBean = CursorUtil.getColumnIndexOrThrow(_cursor, "chainBean");
      final int _cursorIndexOfNekoBean = CursorUtil.getColumnIndexOrThrow(_cursor, "nekoBean");
      final int _cursorIndexOfConfigBean = CursorUtil.getColumnIndexOrThrow(_cursor, "configBean");
      final List<ProxyEntity> _result = new ArrayList<ProxyEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ProxyEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final long _tmpGroupId;
        _tmpGroupId = _cursor.getLong(_cursorIndexOfGroupId);
        final int _tmpType;
        _tmpType = _cursor.getInt(_cursorIndexOfType);
        final long _tmpUserOrder;
        _tmpUserOrder = _cursor.getLong(_cursorIndexOfUserOrder);
        final long _tmpTx;
        _tmpTx = _cursor.getLong(_cursorIndexOfTx);
        final long _tmpRx;
        _tmpRx = _cursor.getLong(_cursorIndexOfRx);
        final int _tmpStatus;
        _tmpStatus = _cursor.getInt(_cursorIndexOfStatus);
        final int _tmpPing;
        _tmpPing = _cursor.getInt(_cursorIndexOfPing);
        final String _tmpUuid;
        _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
        final String _tmpError;
        if (_cursor.isNull(_cursorIndexOfError)) {
          _tmpError = null;
        } else {
          _tmpError = _cursor.getString(_cursorIndexOfError);
        }
        final SOCKSBean _tmpSocksBean;
        final byte[] _tmp;
        _tmp = _cursor.getBlob(_cursorIndexOfSocksBean);
        _tmpSocksBean = KryoConverters.socksDeserialize(_tmp);
        final HttpBean _tmpHttpBean;
        final byte[] _tmp_1;
        _tmp_1 = _cursor.getBlob(_cursorIndexOfHttpBean);
        _tmpHttpBean = KryoConverters.httpDeserialize(_tmp_1);
        final ShadowsocksBean _tmpSsBean;
        final byte[] _tmp_2;
        _tmp_2 = _cursor.getBlob(_cursorIndexOfSsBean);
        _tmpSsBean = KryoConverters.shadowsocksDeserialize(_tmp_2);
        final VMessBean _tmpVmessBean;
        final byte[] _tmp_3;
        _tmp_3 = _cursor.getBlob(_cursorIndexOfVmessBean);
        _tmpVmessBean = KryoConverters.vmessDeserialize(_tmp_3);
        final TrojanBean _tmpTrojanBean;
        final byte[] _tmp_4;
        _tmp_4 = _cursor.getBlob(_cursorIndexOfTrojanBean);
        _tmpTrojanBean = KryoConverters.trojanDeserialize(_tmp_4);
        final TrojanGoBean _tmpTrojanGoBean;
        final byte[] _tmp_5;
        _tmp_5 = _cursor.getBlob(_cursorIndexOfTrojanGoBean);
        _tmpTrojanGoBean = KryoConverters.trojanGoDeserialize(_tmp_5);
        final MieruBean _tmpMieruBean;
        final byte[] _tmp_6;
        _tmp_6 = _cursor.getBlob(_cursorIndexOfMieruBean);
        _tmpMieruBean = KryoConverters.mieruDeserialize(_tmp_6);
        final NaiveBean _tmpNaiveBean;
        final byte[] _tmp_7;
        _tmp_7 = _cursor.getBlob(_cursorIndexOfNaiveBean);
        _tmpNaiveBean = KryoConverters.naiveDeserialize(_tmp_7);
        final HysteriaBean _tmpHysteriaBean;
        final byte[] _tmp_8;
        _tmp_8 = _cursor.getBlob(_cursorIndexOfHysteriaBean);
        _tmpHysteriaBean = KryoConverters.hysteriaDeserialize(_tmp_8);
        final TuicBean _tmpTuicBean;
        final byte[] _tmp_9;
        _tmp_9 = _cursor.getBlob(_cursorIndexOfTuicBean);
        _tmpTuicBean = KryoConverters.tuicDeserialize(_tmp_9);
        final SSHBean _tmpSshBean;
        final byte[] _tmp_10;
        _tmp_10 = _cursor.getBlob(_cursorIndexOfSshBean);
        _tmpSshBean = KryoConverters.sshDeserialize(_tmp_10);
        final WireGuardBean _tmpWgBean;
        final byte[] _tmp_11;
        _tmp_11 = _cursor.getBlob(_cursorIndexOfWgBean);
        _tmpWgBean = KryoConverters.wireguardDeserialize(_tmp_11);
        final ShadowTLSBean _tmpShadowTLSBean;
        final byte[] _tmp_12;
        _tmp_12 = _cursor.getBlob(_cursorIndexOfShadowTLSBean);
        _tmpShadowTLSBean = KryoConverters.shadowTLSDeserialize(_tmp_12);
        final AnyTLSBean _tmpAnyTLSBean;
        final byte[] _tmp_13;
        _tmp_13 = _cursor.getBlob(_cursorIndexOfAnyTLSBean);
        _tmpAnyTLSBean = KryoConverters.anyTLSDeserialize(_tmp_13);
        final ChainBean _tmpChainBean;
        final byte[] _tmp_14;
        _tmp_14 = _cursor.getBlob(_cursorIndexOfChainBean);
        _tmpChainBean = KryoConverters.chainDeserialize(_tmp_14);
        final NekoBean _tmpNekoBean;
        final byte[] _tmp_15;
        _tmp_15 = _cursor.getBlob(_cursorIndexOfNekoBean);
        _tmpNekoBean = KryoConverters.nekoDeserialize(_tmp_15);
        final ConfigBean _tmpConfigBean;
        final byte[] _tmp_16;
        _tmp_16 = _cursor.getBlob(_cursorIndexOfConfigBean);
        _tmpConfigBean = KryoConverters.configDeserialize(_tmp_16);
        _item = new ProxyEntity(_tmpId,_tmpGroupId,_tmpType,_tmpUserOrder,_tmpTx,_tmpRx,_tmpStatus,_tmpPing,_tmpUuid,_tmpError,_tmpSocksBean,_tmpHttpBean,_tmpSsBean,_tmpVmessBean,_tmpTrojanBean,_tmpTrojanGoBean,_tmpMieruBean,_tmpNaiveBean,_tmpHysteriaBean,_tmpTuicBean,_tmpSshBean,_tmpWgBean,_tmpShadowTLSBean,_tmpAnyTLSBean,_tmpChainBean,_tmpNekoBean,_tmpConfigBean);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<Long> getIdsByGroup(final long groupId) {
    final String _sql = "SELECT id FROM proxy_entities WHERE groupId = ? ORDER BY userOrder";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, groupId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final List<Long> _result = new ArrayList<Long>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final Long _item;
        _item = _cursor.getLong(0);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<ProxyEntity> getByGroup(final long groupId) {
    final String _sql = "SELECT * FROM proxy_entities WHERE groupId = ? ORDER BY userOrder";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, groupId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfGroupId = CursorUtil.getColumnIndexOrThrow(_cursor, "groupId");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfUserOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "userOrder");
      final int _cursorIndexOfTx = CursorUtil.getColumnIndexOrThrow(_cursor, "tx");
      final int _cursorIndexOfRx = CursorUtil.getColumnIndexOrThrow(_cursor, "rx");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfPing = CursorUtil.getColumnIndexOrThrow(_cursor, "ping");
      final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
      final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
      final int _cursorIndexOfSocksBean = CursorUtil.getColumnIndexOrThrow(_cursor, "socksBean");
      final int _cursorIndexOfHttpBean = CursorUtil.getColumnIndexOrThrow(_cursor, "httpBean");
      final int _cursorIndexOfSsBean = CursorUtil.getColumnIndexOrThrow(_cursor, "ssBean");
      final int _cursorIndexOfVmessBean = CursorUtil.getColumnIndexOrThrow(_cursor, "vmessBean");
      final int _cursorIndexOfTrojanBean = CursorUtil.getColumnIndexOrThrow(_cursor, "trojanBean");
      final int _cursorIndexOfTrojanGoBean = CursorUtil.getColumnIndexOrThrow(_cursor, "trojanGoBean");
      final int _cursorIndexOfMieruBean = CursorUtil.getColumnIndexOrThrow(_cursor, "mieruBean");
      final int _cursorIndexOfNaiveBean = CursorUtil.getColumnIndexOrThrow(_cursor, "naiveBean");
      final int _cursorIndexOfHysteriaBean = CursorUtil.getColumnIndexOrThrow(_cursor, "hysteriaBean");
      final int _cursorIndexOfTuicBean = CursorUtil.getColumnIndexOrThrow(_cursor, "tuicBean");
      final int _cursorIndexOfSshBean = CursorUtil.getColumnIndexOrThrow(_cursor, "sshBean");
      final int _cursorIndexOfWgBean = CursorUtil.getColumnIndexOrThrow(_cursor, "wgBean");
      final int _cursorIndexOfShadowTLSBean = CursorUtil.getColumnIndexOrThrow(_cursor, "shadowTLSBean");
      final int _cursorIndexOfAnyTLSBean = CursorUtil.getColumnIndexOrThrow(_cursor, "anyTLSBean");
      final int _cursorIndexOfChainBean = CursorUtil.getColumnIndexOrThrow(_cursor, "chainBean");
      final int _cursorIndexOfNekoBean = CursorUtil.getColumnIndexOrThrow(_cursor, "nekoBean");
      final int _cursorIndexOfConfigBean = CursorUtil.getColumnIndexOrThrow(_cursor, "configBean");
      final List<ProxyEntity> _result = new ArrayList<ProxyEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ProxyEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final long _tmpGroupId;
        _tmpGroupId = _cursor.getLong(_cursorIndexOfGroupId);
        final int _tmpType;
        _tmpType = _cursor.getInt(_cursorIndexOfType);
        final long _tmpUserOrder;
        _tmpUserOrder = _cursor.getLong(_cursorIndexOfUserOrder);
        final long _tmpTx;
        _tmpTx = _cursor.getLong(_cursorIndexOfTx);
        final long _tmpRx;
        _tmpRx = _cursor.getLong(_cursorIndexOfRx);
        final int _tmpStatus;
        _tmpStatus = _cursor.getInt(_cursorIndexOfStatus);
        final int _tmpPing;
        _tmpPing = _cursor.getInt(_cursorIndexOfPing);
        final String _tmpUuid;
        _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
        final String _tmpError;
        if (_cursor.isNull(_cursorIndexOfError)) {
          _tmpError = null;
        } else {
          _tmpError = _cursor.getString(_cursorIndexOfError);
        }
        final SOCKSBean _tmpSocksBean;
        final byte[] _tmp;
        _tmp = _cursor.getBlob(_cursorIndexOfSocksBean);
        _tmpSocksBean = KryoConverters.socksDeserialize(_tmp);
        final HttpBean _tmpHttpBean;
        final byte[] _tmp_1;
        _tmp_1 = _cursor.getBlob(_cursorIndexOfHttpBean);
        _tmpHttpBean = KryoConverters.httpDeserialize(_tmp_1);
        final ShadowsocksBean _tmpSsBean;
        final byte[] _tmp_2;
        _tmp_2 = _cursor.getBlob(_cursorIndexOfSsBean);
        _tmpSsBean = KryoConverters.shadowsocksDeserialize(_tmp_2);
        final VMessBean _tmpVmessBean;
        final byte[] _tmp_3;
        _tmp_3 = _cursor.getBlob(_cursorIndexOfVmessBean);
        _tmpVmessBean = KryoConverters.vmessDeserialize(_tmp_3);
        final TrojanBean _tmpTrojanBean;
        final byte[] _tmp_4;
        _tmp_4 = _cursor.getBlob(_cursorIndexOfTrojanBean);
        _tmpTrojanBean = KryoConverters.trojanDeserialize(_tmp_4);
        final TrojanGoBean _tmpTrojanGoBean;
        final byte[] _tmp_5;
        _tmp_5 = _cursor.getBlob(_cursorIndexOfTrojanGoBean);
        _tmpTrojanGoBean = KryoConverters.trojanGoDeserialize(_tmp_5);
        final MieruBean _tmpMieruBean;
        final byte[] _tmp_6;
        _tmp_6 = _cursor.getBlob(_cursorIndexOfMieruBean);
        _tmpMieruBean = KryoConverters.mieruDeserialize(_tmp_6);
        final NaiveBean _tmpNaiveBean;
        final byte[] _tmp_7;
        _tmp_7 = _cursor.getBlob(_cursorIndexOfNaiveBean);
        _tmpNaiveBean = KryoConverters.naiveDeserialize(_tmp_7);
        final HysteriaBean _tmpHysteriaBean;
        final byte[] _tmp_8;
        _tmp_8 = _cursor.getBlob(_cursorIndexOfHysteriaBean);
        _tmpHysteriaBean = KryoConverters.hysteriaDeserialize(_tmp_8);
        final TuicBean _tmpTuicBean;
        final byte[] _tmp_9;
        _tmp_9 = _cursor.getBlob(_cursorIndexOfTuicBean);
        _tmpTuicBean = KryoConverters.tuicDeserialize(_tmp_9);
        final SSHBean _tmpSshBean;
        final byte[] _tmp_10;
        _tmp_10 = _cursor.getBlob(_cursorIndexOfSshBean);
        _tmpSshBean = KryoConverters.sshDeserialize(_tmp_10);
        final WireGuardBean _tmpWgBean;
        final byte[] _tmp_11;
        _tmp_11 = _cursor.getBlob(_cursorIndexOfWgBean);
        _tmpWgBean = KryoConverters.wireguardDeserialize(_tmp_11);
        final ShadowTLSBean _tmpShadowTLSBean;
        final byte[] _tmp_12;
        _tmp_12 = _cursor.getBlob(_cursorIndexOfShadowTLSBean);
        _tmpShadowTLSBean = KryoConverters.shadowTLSDeserialize(_tmp_12);
        final AnyTLSBean _tmpAnyTLSBean;
        final byte[] _tmp_13;
        _tmp_13 = _cursor.getBlob(_cursorIndexOfAnyTLSBean);
        _tmpAnyTLSBean = KryoConverters.anyTLSDeserialize(_tmp_13);
        final ChainBean _tmpChainBean;
        final byte[] _tmp_14;
        _tmp_14 = _cursor.getBlob(_cursorIndexOfChainBean);
        _tmpChainBean = KryoConverters.chainDeserialize(_tmp_14);
        final NekoBean _tmpNekoBean;
        final byte[] _tmp_15;
        _tmp_15 = _cursor.getBlob(_cursorIndexOfNekoBean);
        _tmpNekoBean = KryoConverters.nekoDeserialize(_tmp_15);
        final ConfigBean _tmpConfigBean;
        final byte[] _tmp_16;
        _tmp_16 = _cursor.getBlob(_cursorIndexOfConfigBean);
        _tmpConfigBean = KryoConverters.configDeserialize(_tmp_16);
        _item = new ProxyEntity(_tmpId,_tmpGroupId,_tmpType,_tmpUserOrder,_tmpTx,_tmpRx,_tmpStatus,_tmpPing,_tmpUuid,_tmpError,_tmpSocksBean,_tmpHttpBean,_tmpSsBean,_tmpVmessBean,_tmpTrojanBean,_tmpTrojanGoBean,_tmpMieruBean,_tmpNaiveBean,_tmpHysteriaBean,_tmpTuicBean,_tmpSshBean,_tmpWgBean,_tmpShadowTLSBean,_tmpAnyTLSBean,_tmpChainBean,_tmpNekoBean,_tmpConfigBean);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<ProxyEntity> getEntities(final List<Long> proxyIds) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT * FROM proxy_entities WHERE id in (");
    final int _inputSize = proxyIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (long _item : proxyIds) {
      _statement.bindLong(_argIndex, _item);
      _argIndex++;
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfGroupId = CursorUtil.getColumnIndexOrThrow(_cursor, "groupId");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfUserOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "userOrder");
      final int _cursorIndexOfTx = CursorUtil.getColumnIndexOrThrow(_cursor, "tx");
      final int _cursorIndexOfRx = CursorUtil.getColumnIndexOrThrow(_cursor, "rx");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfPing = CursorUtil.getColumnIndexOrThrow(_cursor, "ping");
      final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
      final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
      final int _cursorIndexOfSocksBean = CursorUtil.getColumnIndexOrThrow(_cursor, "socksBean");
      final int _cursorIndexOfHttpBean = CursorUtil.getColumnIndexOrThrow(_cursor, "httpBean");
      final int _cursorIndexOfSsBean = CursorUtil.getColumnIndexOrThrow(_cursor, "ssBean");
      final int _cursorIndexOfVmessBean = CursorUtil.getColumnIndexOrThrow(_cursor, "vmessBean");
      final int _cursorIndexOfTrojanBean = CursorUtil.getColumnIndexOrThrow(_cursor, "trojanBean");
      final int _cursorIndexOfTrojanGoBean = CursorUtil.getColumnIndexOrThrow(_cursor, "trojanGoBean");
      final int _cursorIndexOfMieruBean = CursorUtil.getColumnIndexOrThrow(_cursor, "mieruBean");
      final int _cursorIndexOfNaiveBean = CursorUtil.getColumnIndexOrThrow(_cursor, "naiveBean");
      final int _cursorIndexOfHysteriaBean = CursorUtil.getColumnIndexOrThrow(_cursor, "hysteriaBean");
      final int _cursorIndexOfTuicBean = CursorUtil.getColumnIndexOrThrow(_cursor, "tuicBean");
      final int _cursorIndexOfSshBean = CursorUtil.getColumnIndexOrThrow(_cursor, "sshBean");
      final int _cursorIndexOfWgBean = CursorUtil.getColumnIndexOrThrow(_cursor, "wgBean");
      final int _cursorIndexOfShadowTLSBean = CursorUtil.getColumnIndexOrThrow(_cursor, "shadowTLSBean");
      final int _cursorIndexOfAnyTLSBean = CursorUtil.getColumnIndexOrThrow(_cursor, "anyTLSBean");
      final int _cursorIndexOfChainBean = CursorUtil.getColumnIndexOrThrow(_cursor, "chainBean");
      final int _cursorIndexOfNekoBean = CursorUtil.getColumnIndexOrThrow(_cursor, "nekoBean");
      final int _cursorIndexOfConfigBean = CursorUtil.getColumnIndexOrThrow(_cursor, "configBean");
      final List<ProxyEntity> _result = new ArrayList<ProxyEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ProxyEntity _item_1;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final long _tmpGroupId;
        _tmpGroupId = _cursor.getLong(_cursorIndexOfGroupId);
        final int _tmpType;
        _tmpType = _cursor.getInt(_cursorIndexOfType);
        final long _tmpUserOrder;
        _tmpUserOrder = _cursor.getLong(_cursorIndexOfUserOrder);
        final long _tmpTx;
        _tmpTx = _cursor.getLong(_cursorIndexOfTx);
        final long _tmpRx;
        _tmpRx = _cursor.getLong(_cursorIndexOfRx);
        final int _tmpStatus;
        _tmpStatus = _cursor.getInt(_cursorIndexOfStatus);
        final int _tmpPing;
        _tmpPing = _cursor.getInt(_cursorIndexOfPing);
        final String _tmpUuid;
        _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
        final String _tmpError;
        if (_cursor.isNull(_cursorIndexOfError)) {
          _tmpError = null;
        } else {
          _tmpError = _cursor.getString(_cursorIndexOfError);
        }
        final SOCKSBean _tmpSocksBean;
        final byte[] _tmp;
        _tmp = _cursor.getBlob(_cursorIndexOfSocksBean);
        _tmpSocksBean = KryoConverters.socksDeserialize(_tmp);
        final HttpBean _tmpHttpBean;
        final byte[] _tmp_1;
        _tmp_1 = _cursor.getBlob(_cursorIndexOfHttpBean);
        _tmpHttpBean = KryoConverters.httpDeserialize(_tmp_1);
        final ShadowsocksBean _tmpSsBean;
        final byte[] _tmp_2;
        _tmp_2 = _cursor.getBlob(_cursorIndexOfSsBean);
        _tmpSsBean = KryoConverters.shadowsocksDeserialize(_tmp_2);
        final VMessBean _tmpVmessBean;
        final byte[] _tmp_3;
        _tmp_3 = _cursor.getBlob(_cursorIndexOfVmessBean);
        _tmpVmessBean = KryoConverters.vmessDeserialize(_tmp_3);
        final TrojanBean _tmpTrojanBean;
        final byte[] _tmp_4;
        _tmp_4 = _cursor.getBlob(_cursorIndexOfTrojanBean);
        _tmpTrojanBean = KryoConverters.trojanDeserialize(_tmp_4);
        final TrojanGoBean _tmpTrojanGoBean;
        final byte[] _tmp_5;
        _tmp_5 = _cursor.getBlob(_cursorIndexOfTrojanGoBean);
        _tmpTrojanGoBean = KryoConverters.trojanGoDeserialize(_tmp_5);
        final MieruBean _tmpMieruBean;
        final byte[] _tmp_6;
        _tmp_6 = _cursor.getBlob(_cursorIndexOfMieruBean);
        _tmpMieruBean = KryoConverters.mieruDeserialize(_tmp_6);
        final NaiveBean _tmpNaiveBean;
        final byte[] _tmp_7;
        _tmp_7 = _cursor.getBlob(_cursorIndexOfNaiveBean);
        _tmpNaiveBean = KryoConverters.naiveDeserialize(_tmp_7);
        final HysteriaBean _tmpHysteriaBean;
        final byte[] _tmp_8;
        _tmp_8 = _cursor.getBlob(_cursorIndexOfHysteriaBean);
        _tmpHysteriaBean = KryoConverters.hysteriaDeserialize(_tmp_8);
        final TuicBean _tmpTuicBean;
        final byte[] _tmp_9;
        _tmp_9 = _cursor.getBlob(_cursorIndexOfTuicBean);
        _tmpTuicBean = KryoConverters.tuicDeserialize(_tmp_9);
        final SSHBean _tmpSshBean;
        final byte[] _tmp_10;
        _tmp_10 = _cursor.getBlob(_cursorIndexOfSshBean);
        _tmpSshBean = KryoConverters.sshDeserialize(_tmp_10);
        final WireGuardBean _tmpWgBean;
        final byte[] _tmp_11;
        _tmp_11 = _cursor.getBlob(_cursorIndexOfWgBean);
        _tmpWgBean = KryoConverters.wireguardDeserialize(_tmp_11);
        final ShadowTLSBean _tmpShadowTLSBean;
        final byte[] _tmp_12;
        _tmp_12 = _cursor.getBlob(_cursorIndexOfShadowTLSBean);
        _tmpShadowTLSBean = KryoConverters.shadowTLSDeserialize(_tmp_12);
        final AnyTLSBean _tmpAnyTLSBean;
        final byte[] _tmp_13;
        _tmp_13 = _cursor.getBlob(_cursorIndexOfAnyTLSBean);
        _tmpAnyTLSBean = KryoConverters.anyTLSDeserialize(_tmp_13);
        final ChainBean _tmpChainBean;
        final byte[] _tmp_14;
        _tmp_14 = _cursor.getBlob(_cursorIndexOfChainBean);
        _tmpChainBean = KryoConverters.chainDeserialize(_tmp_14);
        final NekoBean _tmpNekoBean;
        final byte[] _tmp_15;
        _tmp_15 = _cursor.getBlob(_cursorIndexOfNekoBean);
        _tmpNekoBean = KryoConverters.nekoDeserialize(_tmp_15);
        final ConfigBean _tmpConfigBean;
        final byte[] _tmp_16;
        _tmp_16 = _cursor.getBlob(_cursorIndexOfConfigBean);
        _tmpConfigBean = KryoConverters.configDeserialize(_tmp_16);
        _item_1 = new ProxyEntity(_tmpId,_tmpGroupId,_tmpType,_tmpUserOrder,_tmpTx,_tmpRx,_tmpStatus,_tmpPing,_tmpUuid,_tmpError,_tmpSocksBean,_tmpHttpBean,_tmpSsBean,_tmpVmessBean,_tmpTrojanBean,_tmpTrojanGoBean,_tmpMieruBean,_tmpNaiveBean,_tmpHysteriaBean,_tmpTuicBean,_tmpSshBean,_tmpWgBean,_tmpShadowTLSBean,_tmpAnyTLSBean,_tmpChainBean,_tmpNekoBean,_tmpConfigBean);
        _result.add(_item_1);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public long countByGroup(final long groupId) {
    final String _sql = "SELECT COUNT(*) FROM proxy_entities WHERE groupId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, groupId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final long _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getLong(0);
      } else {
        _result = 0L;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public Long nextOrder(final long groupId) {
    final String _sql = "SELECT  MAX(userOrder) + 1 FROM proxy_entities WHERE groupId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, groupId);
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
  public ProxyEntity getById(final long proxyId) {
    final String _sql = "SELECT * FROM proxy_entities WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, proxyId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfGroupId = CursorUtil.getColumnIndexOrThrow(_cursor, "groupId");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfUserOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "userOrder");
      final int _cursorIndexOfTx = CursorUtil.getColumnIndexOrThrow(_cursor, "tx");
      final int _cursorIndexOfRx = CursorUtil.getColumnIndexOrThrow(_cursor, "rx");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfPing = CursorUtil.getColumnIndexOrThrow(_cursor, "ping");
      final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
      final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
      final int _cursorIndexOfSocksBean = CursorUtil.getColumnIndexOrThrow(_cursor, "socksBean");
      final int _cursorIndexOfHttpBean = CursorUtil.getColumnIndexOrThrow(_cursor, "httpBean");
      final int _cursorIndexOfSsBean = CursorUtil.getColumnIndexOrThrow(_cursor, "ssBean");
      final int _cursorIndexOfVmessBean = CursorUtil.getColumnIndexOrThrow(_cursor, "vmessBean");
      final int _cursorIndexOfTrojanBean = CursorUtil.getColumnIndexOrThrow(_cursor, "trojanBean");
      final int _cursorIndexOfTrojanGoBean = CursorUtil.getColumnIndexOrThrow(_cursor, "trojanGoBean");
      final int _cursorIndexOfMieruBean = CursorUtil.getColumnIndexOrThrow(_cursor, "mieruBean");
      final int _cursorIndexOfNaiveBean = CursorUtil.getColumnIndexOrThrow(_cursor, "naiveBean");
      final int _cursorIndexOfHysteriaBean = CursorUtil.getColumnIndexOrThrow(_cursor, "hysteriaBean");
      final int _cursorIndexOfTuicBean = CursorUtil.getColumnIndexOrThrow(_cursor, "tuicBean");
      final int _cursorIndexOfSshBean = CursorUtil.getColumnIndexOrThrow(_cursor, "sshBean");
      final int _cursorIndexOfWgBean = CursorUtil.getColumnIndexOrThrow(_cursor, "wgBean");
      final int _cursorIndexOfShadowTLSBean = CursorUtil.getColumnIndexOrThrow(_cursor, "shadowTLSBean");
      final int _cursorIndexOfAnyTLSBean = CursorUtil.getColumnIndexOrThrow(_cursor, "anyTLSBean");
      final int _cursorIndexOfChainBean = CursorUtil.getColumnIndexOrThrow(_cursor, "chainBean");
      final int _cursorIndexOfNekoBean = CursorUtil.getColumnIndexOrThrow(_cursor, "nekoBean");
      final int _cursorIndexOfConfigBean = CursorUtil.getColumnIndexOrThrow(_cursor, "configBean");
      final ProxyEntity _result;
      if (_cursor.moveToFirst()) {
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final long _tmpGroupId;
        _tmpGroupId = _cursor.getLong(_cursorIndexOfGroupId);
        final int _tmpType;
        _tmpType = _cursor.getInt(_cursorIndexOfType);
        final long _tmpUserOrder;
        _tmpUserOrder = _cursor.getLong(_cursorIndexOfUserOrder);
        final long _tmpTx;
        _tmpTx = _cursor.getLong(_cursorIndexOfTx);
        final long _tmpRx;
        _tmpRx = _cursor.getLong(_cursorIndexOfRx);
        final int _tmpStatus;
        _tmpStatus = _cursor.getInt(_cursorIndexOfStatus);
        final int _tmpPing;
        _tmpPing = _cursor.getInt(_cursorIndexOfPing);
        final String _tmpUuid;
        _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
        final String _tmpError;
        if (_cursor.isNull(_cursorIndexOfError)) {
          _tmpError = null;
        } else {
          _tmpError = _cursor.getString(_cursorIndexOfError);
        }
        final SOCKSBean _tmpSocksBean;
        final byte[] _tmp;
        _tmp = _cursor.getBlob(_cursorIndexOfSocksBean);
        _tmpSocksBean = KryoConverters.socksDeserialize(_tmp);
        final HttpBean _tmpHttpBean;
        final byte[] _tmp_1;
        _tmp_1 = _cursor.getBlob(_cursorIndexOfHttpBean);
        _tmpHttpBean = KryoConverters.httpDeserialize(_tmp_1);
        final ShadowsocksBean _tmpSsBean;
        final byte[] _tmp_2;
        _tmp_2 = _cursor.getBlob(_cursorIndexOfSsBean);
        _tmpSsBean = KryoConverters.shadowsocksDeserialize(_tmp_2);
        final VMessBean _tmpVmessBean;
        final byte[] _tmp_3;
        _tmp_3 = _cursor.getBlob(_cursorIndexOfVmessBean);
        _tmpVmessBean = KryoConverters.vmessDeserialize(_tmp_3);
        final TrojanBean _tmpTrojanBean;
        final byte[] _tmp_4;
        _tmp_4 = _cursor.getBlob(_cursorIndexOfTrojanBean);
        _tmpTrojanBean = KryoConverters.trojanDeserialize(_tmp_4);
        final TrojanGoBean _tmpTrojanGoBean;
        final byte[] _tmp_5;
        _tmp_5 = _cursor.getBlob(_cursorIndexOfTrojanGoBean);
        _tmpTrojanGoBean = KryoConverters.trojanGoDeserialize(_tmp_5);
        final MieruBean _tmpMieruBean;
        final byte[] _tmp_6;
        _tmp_6 = _cursor.getBlob(_cursorIndexOfMieruBean);
        _tmpMieruBean = KryoConverters.mieruDeserialize(_tmp_6);
        final NaiveBean _tmpNaiveBean;
        final byte[] _tmp_7;
        _tmp_7 = _cursor.getBlob(_cursorIndexOfNaiveBean);
        _tmpNaiveBean = KryoConverters.naiveDeserialize(_tmp_7);
        final HysteriaBean _tmpHysteriaBean;
        final byte[] _tmp_8;
        _tmp_8 = _cursor.getBlob(_cursorIndexOfHysteriaBean);
        _tmpHysteriaBean = KryoConverters.hysteriaDeserialize(_tmp_8);
        final TuicBean _tmpTuicBean;
        final byte[] _tmp_9;
        _tmp_9 = _cursor.getBlob(_cursorIndexOfTuicBean);
        _tmpTuicBean = KryoConverters.tuicDeserialize(_tmp_9);
        final SSHBean _tmpSshBean;
        final byte[] _tmp_10;
        _tmp_10 = _cursor.getBlob(_cursorIndexOfSshBean);
        _tmpSshBean = KryoConverters.sshDeserialize(_tmp_10);
        final WireGuardBean _tmpWgBean;
        final byte[] _tmp_11;
        _tmp_11 = _cursor.getBlob(_cursorIndexOfWgBean);
        _tmpWgBean = KryoConverters.wireguardDeserialize(_tmp_11);
        final ShadowTLSBean _tmpShadowTLSBean;
        final byte[] _tmp_12;
        _tmp_12 = _cursor.getBlob(_cursorIndexOfShadowTLSBean);
        _tmpShadowTLSBean = KryoConverters.shadowTLSDeserialize(_tmp_12);
        final AnyTLSBean _tmpAnyTLSBean;
        final byte[] _tmp_13;
        _tmp_13 = _cursor.getBlob(_cursorIndexOfAnyTLSBean);
        _tmpAnyTLSBean = KryoConverters.anyTLSDeserialize(_tmp_13);
        final ChainBean _tmpChainBean;
        final byte[] _tmp_14;
        _tmp_14 = _cursor.getBlob(_cursorIndexOfChainBean);
        _tmpChainBean = KryoConverters.chainDeserialize(_tmp_14);
        final NekoBean _tmpNekoBean;
        final byte[] _tmp_15;
        _tmp_15 = _cursor.getBlob(_cursorIndexOfNekoBean);
        _tmpNekoBean = KryoConverters.nekoDeserialize(_tmp_15);
        final ConfigBean _tmpConfigBean;
        final byte[] _tmp_16;
        _tmp_16 = _cursor.getBlob(_cursorIndexOfConfigBean);
        _tmpConfigBean = KryoConverters.configDeserialize(_tmp_16);
        _result = new ProxyEntity(_tmpId,_tmpGroupId,_tmpType,_tmpUserOrder,_tmpTx,_tmpRx,_tmpStatus,_tmpPing,_tmpUuid,_tmpError,_tmpSocksBean,_tmpHttpBean,_tmpSsBean,_tmpVmessBean,_tmpTrojanBean,_tmpTrojanGoBean,_tmpMieruBean,_tmpNaiveBean,_tmpHysteriaBean,_tmpTuicBean,_tmpSshBean,_tmpWgBean,_tmpShadowTLSBean,_tmpAnyTLSBean,_tmpChainBean,_tmpNekoBean,_tmpConfigBean);
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
  public void deleteByGroup(final long[] groupId) {
    __db.assertNotSuspendingTransaction();
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("DELETE FROM proxy_entities WHERE groupId in (");
    final int _inputSize = groupId.length;
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
    int _argIndex = 1;
    for (long _item : groupId) {
      _stmt.bindLong(_argIndex, _item);
      _argIndex++;
    }
    __db.beginTransaction();
    try {
      _stmt.executeUpdateDelete();
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
