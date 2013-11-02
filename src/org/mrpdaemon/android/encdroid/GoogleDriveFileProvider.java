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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.mrpdaemon.sec.encfs.EncFSFileInfo;
import org.mrpdaemon.sec.encfs.EncFSFileProvider;

public class GoogleDriveFileProvider implements EncFSFileProvider {

	// Root path for this file provider
	private String rootPath;

	public GoogleDriveFileProvider(String rootPath) {
		this.rootPath = rootPath;
	}

	@Override
	public boolean copy(String srcPath, String dstPath) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public EncFSFileInfo createFile(String path) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean delete(String path) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean exists(String path) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public EncFSFileInfo getFileInfo(String path) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getFilesystemRootPath() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isDirectory(String path) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<EncFSFileInfo> listFiles(String path) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean mkdir(String path) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean mkdirs(String path) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean move(String srcPath, String dstPath) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public InputStream openInputStream(String path) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OutputStream openOutputStream(String path, long length)
			throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
