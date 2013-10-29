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

import java.io.File;

import org.mrpdaemon.sec.encfs.EncFSFileProvider;
import org.mrpdaemon.sec.encfs.EncFSLocalFileProvider;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

// Class representing the internal SD card file system
public class ExtSDFileSystem extends FileSystem {

	SharedPreferences mPrefs;

	public ExtSDFileSystem(Context context) {
		super(null, context);

		mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
	}

	@Override
	public boolean isEnabled() {
		return mPrefs.getBoolean("ext_sd_enabled", false);
	}

	@Override
	public String getName() {
		return mContext.getString(R.string.fs_name_ext_sd);
	}

	@Override
	public int getIconResId() {
		return R.drawable.ic_fs_ext_sd;
	}

	@Override
	public String getPathPrefix() {
		return "[" + mContext.getString(R.string.ext_sd_vol_prefix_str) + "]:";
	}

	@Override
	public EncFSFileProvider getFileProvider(String path) {
		return new EncFSLocalFileProvider(new File(mPrefs.getString(
				"ext_sd_location", "/mnt/external1"), path));
	}
}