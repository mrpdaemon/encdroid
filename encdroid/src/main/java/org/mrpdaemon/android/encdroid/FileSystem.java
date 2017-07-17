/*
 * encdroid - EncFS client application for Android
 * Copyright (C) 2013  Mark R. Pariente
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

import org.mrpdaemon.sec.encfs.EncFSFileProvider;

import android.content.Context;

// Base class for all file system types
public abstract class FileSystem {

	// Activity context
	protected Context mContext;

	// Account associated with this file system
	private Account mAccount;

	// Logger tag
	private static final String TAG = "FileSystem";

	// Create a new FileSystem object with an Account
	public FileSystem(Account account, Context context) {
		this.mContext = context;
		this.mAccount = account;
	}

	// Whether this file system is enabled
	public boolean isEnabled() {
		return true;
	}

	// Return the name of this file system
	public abstract String getName();

	// Return an icon resource for this file system
	public abstract int getIconResId();

	// Prefix to put in front of paths
	public abstract String getPathPrefix();

	// Return the account used with this file system
	public Account getAccount() {
		return mAccount;
	}

	// Return a file provider for this file system at a given path
	public EncFSFileProvider getFileProvider(String path) {
		if (mAccount != null) {
			if (mAccount.isLinked() && mAccount.isAuthenticated()) {
				return mAccount.getFileProvider(path);
			} else {
				if (mAccount.linkOrAuthIfNeeded(mContext, TAG)) {
					return mAccount.getFileProvider(path);
				}
			}
		}

		return null;
	}
}