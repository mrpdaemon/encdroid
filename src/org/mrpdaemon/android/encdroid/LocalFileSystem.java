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
import android.os.Environment;

// Class representing the internal SD card file system
public class LocalFileSystem extends FileSystem {

	public LocalFileSystem(Context context) {
		super(null, context);
	}

	@Override
	public String getName() {
		return mContext.getString(R.string.fs_name_local);
	}

	@Override
	public int getIconResId() {
		return R.drawable.ic_fs_local;
	}

	@Override
	public String getPathPrefix() {
		return Environment.getExternalStorageDirectory().getAbsolutePath();
	}

	@Override
	public EncFSFileProvider getFileProvider(String path) {
		return new EncFSLocalFileProvider(new File(
				Environment.getExternalStorageDirectory(), path));
	}
}