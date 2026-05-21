package com.memorycurator.app.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MediaDao_Impl implements MediaDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MediaEntity> __insertionAdapterOfMediaEntity;

  public MediaDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMediaEntity = new EntityInsertionAdapter<MediaEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `media` (`id`,`uri`,`bucketId`,`folderName`,`dateTaken`,`mimeType`,`width`,`height`,`size`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MediaEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getUri());
        if (entity.getBucketId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getBucketId());
        }
        if (entity.getFolderName() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getFolderName());
        }
        statement.bindLong(5, entity.getDateTaken());
        if (entity.getMimeType() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getMimeType());
        }
        statement.bindLong(7, entity.getWidth());
        statement.bindLong(8, entity.getHeight());
        statement.bindLong(9, entity.getSize());
      }
    };
  }

  @Override
  public Object insertAll(final List<MediaEntity> media,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMediaEntity.insert(media);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<MediaEntity>> getAllMedia() {
    final String _sql = "\n"
            + "        SELECT * FROM media\n"
            + "        ORDER BY dateTaken DESC\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"media"}, new Callable<List<MediaEntity>>() {
      @Override
      @NonNull
      public List<MediaEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfBucketId = CursorUtil.getColumnIndexOrThrow(_cursor, "bucketId");
          final int _cursorIndexOfFolderName = CursorUtil.getColumnIndexOrThrow(_cursor, "folderName");
          final int _cursorIndexOfDateTaken = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTaken");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfSize = CursorUtil.getColumnIndexOrThrow(_cursor, "size");
          final List<MediaEntity> _result = new ArrayList<MediaEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MediaEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpBucketId;
            if (_cursor.isNull(_cursorIndexOfBucketId)) {
              _tmpBucketId = null;
            } else {
              _tmpBucketId = _cursor.getString(_cursorIndexOfBucketId);
            }
            final String _tmpFolderName;
            if (_cursor.isNull(_cursorIndexOfFolderName)) {
              _tmpFolderName = null;
            } else {
              _tmpFolderName = _cursor.getString(_cursorIndexOfFolderName);
            }
            final long _tmpDateTaken;
            _tmpDateTaken = _cursor.getLong(_cursorIndexOfDateTaken);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpSize;
            _tmpSize = _cursor.getLong(_cursorIndexOfSize);
            _item = new MediaEntity(_tmpId,_tmpUri,_tmpBucketId,_tmpFolderName,_tmpDateTaken,_tmpMimeType,_tmpWidth,_tmpHeight,_tmpSize);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<AlbumProjection>> getAlbums() {
    final String _sql = "\n"
            + "        SELECT \n"
            + "            folderName,\n"
            + "            uri AS thumbnailUri,\n"
            + "            COUNT(*) AS photoCount\n"
            + "        FROM media\n"
            + "        GROUP BY folderName\n"
            + "        ORDER BY photoCount DESC\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"media"}, new Callable<List<AlbumProjection>>() {
      @Override
      @NonNull
      public List<AlbumProjection> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfFolderName = 0;
          final int _cursorIndexOfThumbnailUri = 1;
          final int _cursorIndexOfPhotoCount = 2;
          final List<AlbumProjection> _result = new ArrayList<AlbumProjection>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AlbumProjection _item;
            final String _tmpFolderName;
            _tmpFolderName = _cursor.getString(_cursorIndexOfFolderName);
            final String _tmpThumbnailUri;
            _tmpThumbnailUri = _cursor.getString(_cursorIndexOfThumbnailUri);
            final int _tmpPhotoCount;
            _tmpPhotoCount = _cursor.getInt(_cursorIndexOfPhotoCount);
            _item = new AlbumProjection(_tmpFolderName,_tmpThumbnailUri,_tmpPhotoCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
