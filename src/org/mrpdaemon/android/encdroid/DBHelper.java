/*
 * encdroid - EncFS client application for Android
 * Copyright (C) 2012  Mark R. Pariente
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mrpdaemon.android.encdroid;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Base64;
import android.util.Log;

public class DBHelper extends SQLiteOpenHelper {

	// Logger tag
	private final String TAG = "DBHelper";

	// Database name
	public static final String DB_NAME = "volume.db";

	// Database version
	public static final int DB_VERSION = 5;

	// Volume table name
	public static final String DB_TABLE = "volumes";

	// Column names
	public static final String DB_COL_ID = BaseColumns._ID;
	public static final String DB_COL_NAME = "name";
	public static final String DB_COL_PATH = "path";
	public static final String DB_COL_CONFIGPATH = "configPath";
	public static final String DB_COL_TYPE = "type";
	public static final String DB_COL_KEY = "key";
	public static final String DB_COL_PIN = "pin";
	public static final String DB_COL_PINATTEMPTS = "pinAttempts"; // counts unsuccessful PIN attempts

	private static final String[] NO_ARGS = {};

	// Application object
	private EDApplication mApp;

	public DBHelper(EDApplication application) {
		super(application, DB_NAME, null, DB_VERSION);

		this.mApp = application;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String sqlCmd = "CREATE TABLE " + DB_TABLE + " (" + DB_COL_ID
				+ " int primary key, " + DB_COL_NAME + " text, " + DB_COL_PATH
				+ " text, " + DB_COL_KEY + " text, " + DB_COL_PIN + " text, "+ DB_COL_PINATTEMPTS + " int, "+ DB_COL_TYPE + " int, "
				+ DB_COL_CONFIGPATH + " text)";
		Log.d(TAG, "onCreate() executing SQL: " + sqlCmd);
		db.execSQL(sqlCmd);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Adding missing columns on upgrade
		if (oldVersion == 3 || oldVersion == 4) {
			Log.d(TAG, "onUpgrade() Upgrading DB");
			if(oldVersion == 3) {
				db.execSQL("ALTER TABLE " + DB_TABLE + " ADD COLUMN "
						+ DB_COL_CONFIGPATH + " TEXT");
			}
			db.execSQL("ALTER TABLE " + DB_TABLE + " ADD COLUMN "
					+ DB_COL_PIN + " TEXT");
			db.execSQL("ALTER TABLE " + DB_TABLE + " ADD COLUMN "
					+ DB_COL_PINATTEMPTS + " INT");
		} else {
			db.execSQL("DROP TABLE IF EXISTS " + DB_TABLE);
			Log.d(TAG, "onUpgrade() recreating DB");
			onCreate(db);
		}
	}

	public void insertVolume(Volume volume) {
		SQLiteDatabase db = getWritableDatabase();
		ContentValues values = new ContentValues();

		values.clear();
		values.put(DB_COL_NAME, volume.getName());
		values.put(DB_COL_PATH, volume.getPath());
		values.put(DB_COL_CONFIGPATH, volume.getCustomConfigPath());

		int fsIndex = mApp.getFSIndex(volume.getFileSystem());
		if (fsIndex < 0) {
			throw new InvalidParameterException("Invalid filesystem index: "
					+ fsIndex);
		}
		values.put(DB_COL_TYPE, fsIndex);

		Log.d(TAG, "insertVolume() name: '" + volume.getName() + "' path: '"
				+ volume.getPath() + "'");

		// TODO: Make sure path is unique
		db.insertOrThrow(DB_TABLE, null, values);
	}

	public void deleteVolume(Volume volume) {
		SQLiteDatabase db = getWritableDatabase();

		Log.d(TAG, "deleteVolume() " + volume.getName());

		db.delete(DB_TABLE, DB_COL_NAME + "=? AND " + DB_COL_PATH + "=?",
				new String[] { volume.getName(), volume.getPath() });
	}

	public void renameVolume(Volume volume, String newName) {
		SQLiteDatabase db = getWritableDatabase();

		Log.d(TAG, "renameVolume() " + volume.getName() + " to " + newName);

		ContentValues values = new ContentValues();
		values.put(DB_COL_NAME, newName);
		db.update(DB_TABLE, values, DB_COL_NAME + "=? AND " + DB_COL_PATH
				+ "=?", new String[] { volume.getName(), volume.getPath() });
	}

	public void cacheKey(Volume volume, byte[] key) {
		SQLiteDatabase db = getWritableDatabase();

		Log.d(TAG, "cacheKey() for volume" + volume.getName());

		ContentValues values = new ContentValues();
		values.put(DB_COL_KEY, Base64.encodeToString(key, Base64.DEFAULT));
		db.update(DB_TABLE, values, DB_COL_NAME + "=? AND " + DB_COL_PATH
				+ "=?", new String[] { volume.getName(), volume.getPath() });
	}
	
	public void setPIN(Volume volume, String pin) {
		SQLiteDatabase db = getWritableDatabase();

		Log.d(TAG, "setPIN() for volume" + volume.getName());

		ContentValues values = new ContentValues();
		values.put(DB_COL_PIN, pin);
		db.update(DB_TABLE, values, DB_COL_NAME + "=? AND " + DB_COL_PATH
				+ "=?", new String[] { volume.getName(), volume.getPath() });
	}
	
	public void setPINAttempts(Volume volume, int newVal) {
		SQLiteDatabase db = getWritableDatabase();

		Log.d(TAG, "setPINAttempts() " + volume.getName() + " to " + newVal);

		ContentValues values = new ContentValues();
		values.put(DB_COL_PINATTEMPTS, newVal);
		db.update(DB_TABLE, values, DB_COL_NAME + "=? AND " + DB_COL_PATH
				+ "=?", new String[] { volume.getName(), volume.getPath() });
	}
	
	public void clearKey(Volume volume) {
		SQLiteDatabase db = getWritableDatabase();

		Log.d(TAG, "clearKey() for volume" + volume.getName());

		ContentValues values = new ContentValues();
		values.putNull(DB_COL_KEY);
		db.update(DB_TABLE, values, DB_COL_NAME + "=? AND " + DB_COL_PATH
				+ "=?", new String[] { volume.getName(), volume.getPath() });
	}
	
	public void clearPIN(Volume volume) {
		SQLiteDatabase db = getWritableDatabase();

		Log.d(TAG, "clearPIN() for volume" + volume.getName());

		ContentValues values = new ContentValues();
		values.putNull(DB_COL_PIN);
		values.putNull(DB_COL_PINATTEMPTS);
		db.update(DB_TABLE, values, DB_COL_NAME + "=? AND " + DB_COL_PATH
				+ "=?", new String[] { volume.getName(), volume.getPath() });
	}

	public void clearAllKeys() {
		SQLiteDatabase db = getWritableDatabase();

		Log.d(TAG, "clearAllKeys()");

		db.execSQL("UPDATE " + DB_TABLE + " SET " + DB_COL_KEY + " = NULL");
	}
	
	public void clearAllPINs() {
		SQLiteDatabase db = getWritableDatabase();

		Log.d(TAG, "clearAllPINs()");

		db.execSQL("UPDATE " + DB_TABLE + " SET " + DB_COL_PIN + " = NULL");
		db.execSQL("UPDATE " + DB_TABLE + " SET " + DB_COL_PINATTEMPTS + " = 0");
	}

	public byte[] getCachedKey(Volume volume) {
		SQLiteDatabase db = getReadableDatabase();

		Cursor cursor = db.query(DB_TABLE, NO_ARGS, DB_COL_NAME + "=? AND "
				+ DB_COL_PATH + "=?",
				new String[] { volume.getName(), volume.getPath() }, null,
				null, null);

		if (cursor.moveToFirst()) {
			String keyStr = cursor.getString(cursor.getColumnIndex(DB_COL_KEY));
			if (keyStr != null) {
				return Base64.decode(keyStr, Base64.DEFAULT);
			}
		}
		return null;
	}
	
	public String getPIN(Volume volume) {
		SQLiteDatabase db = getReadableDatabase();

		Cursor cursor = db.query(DB_TABLE, NO_ARGS, DB_COL_NAME + "=? AND "
				+ DB_COL_PATH + "=?",
				new String[] { volume.getName(), volume.getPath() }, null,
				null, null);

		if (cursor.moveToFirst()) {
			String pin = cursor.getString(cursor.getColumnIndex(DB_COL_PIN));
			return pin;
		}
		return null;
	}
	
	public int getPINAttempts(Volume volume) {
		SQLiteDatabase db = getReadableDatabase();

		Cursor cursor = db.query(DB_TABLE, NO_ARGS, DB_COL_NAME + "=? AND "
				+ DB_COL_PATH + "=?",
				new String[] { volume.getName(), volume.getPath() }, null,
				null, null);

		if (cursor.moveToFirst()) {
			int pinAttempts = cursor.getInt((cursor.getColumnIndex(DB_COL_PINATTEMPTS)));
			return pinAttempts;
		}
		return 0;
	}

	public List<Volume> getVolumes() {
		ArrayList<Volume> volumes = new ArrayList<Volume>();
		SQLiteDatabase db = getReadableDatabase();

		// SELECT *, loop over each, create Volume
		Cursor cursor = db.rawQuery("SELECT * FROM " + DB_TABLE, NO_ARGS);

		int nameColId = cursor.getColumnIndex(DB_COL_NAME);
		int pathColId = cursor.getColumnIndex(DB_COL_PATH);
		int typeColId = cursor.getColumnIndex(DB_COL_TYPE);
		int pathConfigColId = cursor.getColumnIndex(DB_COL_CONFIGPATH);

		if (cursor.moveToFirst()) {
			do {
				String volName = cursor.getString(nameColId);
				String volPath = cursor.getString(pathColId);
				String volConfigPath = cursor.getString(pathConfigColId);
				int volFsIdx = cursor.getInt(typeColId);

				Log.d(TAG, "getVolume() name: '" + volName + "' path: '"
						+ volPath + "'");

				if (volFsIdx >= 0) {
					Volume volume;
					if (volConfigPath == null) {
						volume = new Volume(volName, volPath, mApp
								.getFileSystemList().get(volFsIdx));
					} else {
						volume = new Volume(volName, volPath, volConfigPath,
								mApp.getFileSystemList().get(volFsIdx));
					}

					volumes.add(volume);
				} else {
					/*
					 * Volume affected by a bug which ended up inserting volumes
					 * with type == -1 into the DB. Let's drop this volume from
					 * the DB.
					 */
					int keyColId = cursor.getColumnIndex(DB_COL_ID);
					int rowKey = cursor.getInt(keyColId);

					Log.i(TAG, "Invalid volume type: " + volFsIdx
							+ " deleting volume '" + volName
							+ "' from database");

					db.delete(DB_TABLE, DB_COL_ID + "=" + rowKey, null);
				}
			} while (cursor.moveToNext());
		}

		return volumes;
	}
}