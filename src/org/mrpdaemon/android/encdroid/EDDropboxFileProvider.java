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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.mrpdaemon.sec.encfs.EncFSFileInfo;
import org.mrpdaemon.sec.encfs.EncFSFileProvider;

import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.RESTUtility;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxServerException;

public class EDDropboxFileProvider implements EncFSFileProvider {

	// Logger tag
	private final static String TAG = "EDDropboxFileProvider";

	// API object
	private DropboxAPI<AndroidAuthSession> api;

	// Root path for this file provider
	private String rootPath;

	public EDDropboxFileProvider(DropboxAPI<AndroidAuthSession> api,
			String rootPath) {
		this.api = api;
		this.rootPath = rootPath;
	}

	// Generate absolute path for a given relative path
	private String absPath(String relPath) {
		// Take off leading '/' from relPath
		if (relPath.charAt(0) == '/') {
			relPath = relPath.substring(1);
		}

		if (rootPath.charAt(rootPath.length() - 1) == '/') {
			return rootPath + relPath;
		} else {
			return rootPath + "/" + relPath;
		}
	}

	@Override
	public boolean copy(String srcPath, String dstPath) throws IOException {

		/*
		 * If destination path exists, delete it first. This is a workaround for
		 * encfs-java behavior without chainedNameIV, the file is
		 * touched/created before calling into this function causing the Dropbox
		 * API to return 403 Forbidden thinking that the file already exists.
		 */
		if (exists(dstPath)) {
			delete(dstPath);
		}

		try {
			api.copy(absPath(srcPath), absPath(dstPath));
		} catch (DropboxException e) {
			Log.e(TAG, e.toString() + "\n" + Log.getStackTraceString(e));
			throw new IOException(e.getMessage());
		}

		return true;
	}

	@Override
	public EncFSFileInfo createFile(String path) throws IOException {
		Entry entry;

		try {
			entry = api.putFileOverwrite(absPath(path), new FileInputStream(
					"/dev/zero"), 0, null);
		} catch (DropboxException e) {
			Log.e(TAG, e.toString() + "\n" + Log.getStackTraceString(e));
			throw new IOException(e.getMessage());
		}

		if (entry != null) {
			return entryToFileInfo(entry);
		}

		return null;
	}

	@Override
	public boolean delete(String path) throws IOException {
		try {
			api.delete(absPath(path));
		} catch (DropboxException e) {
			Log.e(TAG, e.toString() + "\n" + Log.getStackTraceString(e));
			throw new IOException(e.getMessage());
		}

		return true;
	}

	@Override
	public boolean exists(String path) throws IOException {

		try {
			Entry entry = api.metadata(absPath(path), 1, null, false, null);

			if (entry == null) {
				return false;
			}

			return !entry.isDeleted;
		} catch (DropboxServerException e) {
			// 404 NOT FOUND is a legitimate case
			if (e.error == DropboxServerException._404_NOT_FOUND) {
				return false;
			} else {
				Log.e(TAG, e.toString() + "\n" + Log.getStackTraceString(e));
				throw new IOException(e.getMessage());
			}
		} catch (DropboxException e) {
			Log.e(TAG, e.toString() + "\n" + Log.getStackTraceString(e));
			throw new IOException(e.getMessage());
		}
	}

	private EncFSFileInfo entryToFileInfo(Entry entry) {
		String relativePath;
		if (rootPath.equals("/")) {
			relativePath = entry.parentPath();
		} else if (entry.path.equals(rootPath)) {
			// we're dealing with the root dir
			relativePath = "/";
		} else if (entry.parentPath().equals(rootPath)) {
			// File is child of the root path
			relativePath = "/";
		} else {
			relativePath = entry.parentPath().substring(rootPath.length());
		}

		return new EncFSFileInfo(entry.fileName(), relativePath, entry.isDir,
				RESTUtility.parseDate(entry.modified).getTime(), entry.bytes,
				true, true, true);
	}

	@Override
	public EncFSFileInfo getFileInfo(String path) throws IOException {
		try {
			Entry entry = api.metadata(absPath(path), 1, null, false, null);

			if (entry != null) {
				return entryToFileInfo(entry);
			}

			return null;
		} catch (DropboxException e) {
			Log.e(TAG, e.toString() + "\n" + Log.getStackTraceString(e));
			throw new IOException(e.getMessage());
		}
	}

	@Override
	public String getRootPath() {
		return "/";
	}

	@Override
	public String getSeparator() {
		return "/";
	}

	@Override
	public boolean isDirectory(String path) throws IOException {
		try {
			Entry entry = api.metadata(absPath(path), 1, null, false, null);
			return entry.isDir;
		} catch (DropboxException e) {
			Log.e(TAG, e.toString() + "\n" + Log.getStackTraceString(e));
			throw new IOException(e.getMessage());
		}
	}

	@Override
	public List<EncFSFileInfo> listFiles(String path) throws IOException {
		try {
			List<EncFSFileInfo> list = new ArrayList<EncFSFileInfo>();

			Entry dirEnt = api.metadata(absPath(path), 0, null, true, null);

			if (!dirEnt.isDir) {
				IOException ioe = new IOException(path + " is not a directory");
				Log.e(TAG, ioe.toString() + "\n" + Log.getStackTraceString(ioe));
				throw ioe;
			}

			// Add entries to list
			for (Entry childEnt : dirEnt.contents) {
				list.add(entryToFileInfo(childEnt));
			}

			return list;
		} catch (DropboxException e) {
			Log.e(TAG, e.toString() + "\n" + Log.getStackTraceString(e));
			throw new IOException(e.getMessage());
		}
	}

	@Override
	public boolean mkdir(String path) throws IOException {
		try {
			api.createFolder(absPath(path));
		} catch (DropboxException e) {
			Log.e(TAG, e.toString() + "\n" + Log.getStackTraceString(e));
			throw new IOException(e.getMessage());
		}

		return true;
	}

	@Override
	public boolean mkdirs(String path) throws IOException {
		// XXX: Not implemented
		IOException ioe = new IOException("NOT IMPLEMENTED");
		Log.e(TAG, ioe.toString() + "\n" + Log.getStackTraceString(ioe));
		throw ioe;
	}

	@Override
	public boolean move(String srcPath, String dstPath) throws IOException {
		try {
			api.move(absPath(srcPath), absPath(dstPath));
		} catch (DropboxException e) {
			Log.e(TAG, e.toString() + "\n" + Log.getStackTraceString(e));
			throw new IOException(e.getMessage());
		}

		return true;
	}

	@Override
	public InputStream openInputStream(String path) throws IOException {
		try {
			return api.getFileStream(absPath(path), null);
		} catch (DropboxException e) {
			Log.e(TAG, e.toString() + "\n" + Log.getStackTraceString(e));
			throw new IOException(e.getMessage());
		}
	}

	@Override
	public OutputStream openOutputStream(String path, long length)
			throws IOException {
		return new EDDropboxOutputStream(api, absPath(path), length);
	}

}
