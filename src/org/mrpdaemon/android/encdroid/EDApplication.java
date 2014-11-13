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

import java.util.ArrayList;
import java.util.List;

import android.app.Application;
import android.util.Log;

public class EDApplication extends Application {

	// Logger tag
	private final static String TAG = "EDApplication";

	// Volume list
	private List<Volume> volumeList;

	// DB helper
	private DBHelper dbHelper;

	// Account list
	private ArrayList<Account> mAccountList;

	// Filesystem list
	private ArrayList<FileSystem> mFileSystemList;

	// PBKDF2 provider
	private NativePBKDF2Provider mNativePBKDF2Provider;

	// Whether native PBKDF2 provider is available
	private static boolean mNativePBKDF2ProviderAvailable;

	static {
		try {
			NativePBKDF2Provider.checkAvailable();
			mNativePBKDF2ProviderAvailable = true;
			Log.d(TAG, "Native PBKDF2 provider is available");
		} catch (Throwable t) {
			mNativePBKDF2ProviderAvailable = false;
			Log.d(TAG, "Native PBKDF2 provider is NOT available");
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

		DropboxAccount dropboxAccount = new DropboxAccount(this);
		GoogleDriveAccount driveAccount = new GoogleDriveAccount(this);

		// Create and populate list of accounts
		this.mAccountList = new ArrayList<Account>();
		mAccountList.add(dropboxAccount);
		mAccountList.add(driveAccount);

		/*
		 * Create and populate list of file systems
		 * 
		 * Note that the precise ordering of these file systems must be
		 * maintained in order to not break compatibility against databases
		 * created with earlier versions.
		 */
		this.mFileSystemList = new ArrayList<FileSystem>();
		mFileSystemList.add(new LocalFileSystem(this));
		mFileSystemList.add(new DropboxFileSystem(dropboxAccount, this));
		mFileSystemList.add(new ExtSDFileSystem(this));
		mFileSystemList.add(new GoogleDriveFileSystem(driveAccount, this));

		this.dbHelper = new DBHelper(this);
		this.volumeList = dbHelper.getVolumes();

		if (mNativePBKDF2ProviderAvailable) {
			mNativePBKDF2Provider = new NativePBKDF2Provider();
		} else {
			mNativePBKDF2Provider = null;
		}

		Log.d(TAG, "EDApplication initialized");
	}

	/**
	 * @return the volumeList
	 */
	public List<Volume> getVolumeList() {
		return volumeList;
	}

	/**
	 * @return the dbHelper
	 */
	public DBHelper getDbHelper() {
		return dbHelper;
	}

	/**
	 * @return list of accounts
	 */
	public ArrayList<Account> getAccountList() {
		return mAccountList;
	}

	/**
	 * @return list of file systems
	 */
	public ArrayList<FileSystem> getFileSystemList() {
		return mFileSystemList;
	}

	/**
	 * @return index of the given FS
	 */
	public int getFSIndex(FileSystem fileSystem) {
		int index = 0;

		if (fileSystem == null) {
			return -1;
		}

		for (FileSystem fs : mFileSystemList) {
			if (fs.getName().equals(fileSystem.getName())) {
				return index;
			}
			index++;
		}

		return -1;
	}

	/**
	 * @return index of the given FS by name
	 */
	public int getFSIndexByName(String fsName) {
		int index = 0;

		for (FileSystem fs : mFileSystemList) {
			if (fs.getName().equals(fsName)) {
				return index;
			}
			index++;
		}

		return -1;
	}

	/**
	 * @return whether native PBKDF2 provider is available
	 */
	public boolean isNativePBKDF2ProviderAvailable() {
		return mNativePBKDF2ProviderAvailable;
	}

	/**
	 * @return the native PBKDF2 provider
	 */
	public NativePBKDF2Provider getNativePBKDF2Provider() {
		return mNativePBKDF2Provider;
	}
}
