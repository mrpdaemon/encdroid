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

import java.util.List;

import android.app.Application;
import android.util.Log;

public class EDApplication extends Application {

	// Logger tag
	private final static String TAG = "EDApplication";

	// Volume list
	private List<EDVolume> volumeList;

	// DB helper
	private EDDBHelper dbHelper;

	// Dropbox context
	private EDDropbox mDropbox;

	// Whether action bar is available
	private static boolean mActionBarAvailable;

	static {
		try {
			EDActionBar.checkAvailable();
			mActionBarAvailable = true;
			Log.d(TAG, "Action bar class is available");
		} catch (Throwable t) {
			mActionBarAvailable = false;
			Log.d(TAG, "Action bar class is unavailable");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Application#onCreate()
	 */
	@Override
	public void onCreate() {
		super.onCreate();

		this.dbHelper = new EDDBHelper(this);
		this.volumeList = dbHelper.getVolumes();
		this.mDropbox = new EDDropbox(this);

		Log.d(TAG, "EDApplication initialized");
	}

	/**
	 * @return whether action bar class is available
	 */
	public boolean isActionBarAvailable() {
		return mActionBarAvailable;
	}

	/**
	 * @return the volumeList
	 */
	public List<EDVolume> getVolumeList() {
		return volumeList;
	}

	/**
	 * @return the dbHelper
	 */
	public EDDBHelper getDbHelper() {
		return dbHelper;
	}

	/**
	 * @return the dropbox context
	 */
	public EDDropbox getDropbox() {
		return mDropbox;
	}
}
