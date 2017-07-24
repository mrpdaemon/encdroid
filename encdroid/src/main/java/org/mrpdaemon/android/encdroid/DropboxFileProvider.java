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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.mrpdaemon.sec.encfs.EncFSFileInfo;
import org.mrpdaemon.sec.encfs.EncFSFileProvider;

import android.util.Log;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;

class DropboxFileProvider implements EncFSFileProvider {

	// Logger tag
	private final static String TAG = "DropboxFileProvider";

	// API object
	private DbxClientV2 mDbxClient;

	// Root path for this file provider
	private String rootPath;

	DropboxFileProvider(DbxClientV2 dbxClient,
			String rootPath) {
		this.mDbxClient = dbxClient;
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
			if (relPath.equals("")) {
				return rootPath;
			} else {
				return rootPath + "/" + relPath;
			}
		}
	}

	private void handleDbxException(DbxException e) throws IOException {
		Logger.logException(TAG, e);
		if (e.getMessage() != null) {
			throw new IOException(e.getMessage());
		} else {
			throw new IOException(e.toString());
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
			mDbxClient.files().copy(absPath(srcPath), absPath(dstPath));
		} catch (DbxException e) {
			handleDbxException(e);
		}

		return true;
	}

	@Override
	public EncFSFileInfo createFile(String path) throws IOException {
		FileMetadata meta;

		try {
			meta = mDbxClient.files().uploadBuilder(absPath(path)).uploadAndFinish(new FileInputStream("/dev/zero"), 0);
		} catch (DbxException e) {
			handleDbxException(e);
			return null;
		}

		if (meta != null) {
			return metaToFileInfo(meta);
		}

		return null;
	}

	@Override
	public boolean delete(String path) throws IOException {
		try {
			mDbxClient.files().delete((absPath(path)));
		} catch (DbxException e) {
			handleDbxException(e);
		}

		return true;
	}

	@Override
	public boolean exists(String path) throws IOException {

		try {
			String absPath = absPath(path);

			if (absPath.equals("/")) {
				return true;
			}

			Metadata meta = mDbxClient.files().getMetadata(absPath(path));

			return (meta != null);
		} catch (GetMetadataErrorException gme) {
			// Thrown in case of path not being found
			if (gme.errorValue.isPath()) {
				return false;
			} else {
				handleDbxException(gme);
				return false;
			}
		} catch (DbxException e) {
			handleDbxException(e);
			return false;
		}
	}

	private EncFSFileInfo metaToFileInfo(Metadata meta) {
		String relativePath;

		boolean isDir = meta instanceof FolderMetadata;
		FileMetadata fileMeta = isDir ? null : (FileMetadata) meta;

		String parentPath = new File(meta.getPathDisplay()).getParent();

		if (meta.getPathDisplay().equals(rootPath)) {
			// we're dealing with the root dir
			relativePath = "/";
		} else if (rootPath.equals("/")) {
			relativePath = parentPath;
		} else if (parentPath.equals(rootPath)) {
			// File is child of the root path
			relativePath = "/";
		} else {
			relativePath = parentPath.substring(rootPath.length());
		}

		long modified = 0;
		if (!isDir) {
			modified = (fileMeta.getClientModified() != null) ?
					fileMeta.getClientModified().getTime() : 0;
		}

		long size = 0;
		if (!isDir) {
			size = fileMeta.getSize();
		}

		return new EncFSFileInfo(meta.getName(), relativePath, isDir,
				modified, size, true, true, true);
	}

	@Override
	public EncFSFileInfo getFileInfo(String path) throws IOException {
		try {
			String absPath = absPath(path);

			if (absPath.equals("/")) {
				return null;
			}

			Metadata meta = mDbxClient.files().getMetadata(absPath);

			if (meta != null) {
				return metaToFileInfo(meta);
			}

			return null;
		} catch (DbxException e) {
			handleDbxException(e);
			return null;
		}
	}

	@Override
	public boolean isDirectory(String path) throws IOException {
		try {
			String absPath = absPath(path);

			if (absPath.equals("/")) {
				return true;
			}
			Metadata meta = mDbxClient.files().getMetadata(absPath);
			return (meta instanceof FolderMetadata);
		} catch (DbxException e) {
			handleDbxException(e);
			return false;
		}
	}

	@Override
	public List<EncFSFileInfo> listFiles(String path) throws IOException {
		try {
			List<EncFSFileInfo> list = new ArrayList<>();

			String absPath = absPath(path);

			if (!absPath.equals("/")) {
				Metadata meta = mDbxClient.files().getMetadata(absPath(path));

				IOException ioe = new IOException(path + " is not a directory");
				if (!(meta instanceof FolderMetadata)) {
					Log.e(TAG, ioe.toString() + "\n" + Log.getStackTraceString(ioe));
					throw ioe;
				}
			} else {
				// Dropbox v2 API wants root folder expressed as ""
				absPath = "";
			}

			ListFolderResult res = null;
			do {
				if (res == null) {
					res = mDbxClient.files().listFolder(absPath);
				} else {
					res = mDbxClient.files().listFolderContinue(res.getCursor());
				}

				List<Metadata> entries = res.getEntries();

				for (Metadata entry : entries) {
					try {
						list.add(metaToFileInfo(entry));
					} catch (IllegalArgumentException iae) {
					/*
					 * Can happen if the file name is illegal, for example
					 * starting with '/'. In this case just skip adding the file
					 * to the list.
					 */
					}
				}
			} while (res.getHasMore());

			return list;
		} catch (DbxException e) {
			handleDbxException(e);
			return null;
		}
	}

	@Override
	public boolean mkdir(String path) throws IOException {
		try {
			mDbxClient.files().createFolder(absPath(path));
		} catch (DbxException e) {
			handleDbxException(e);
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
			mDbxClient.files().move(absPath(srcPath), absPath(dstPath));
		} catch (DbxException e) {
			handleDbxException(e);
		}

		return true;
	}

	@Override
	public InputStream openInputStream(String path) throws IOException {
		try {
			return mDbxClient.files().download(absPath(path)).getInputStream();
		} catch (DbxException e) {
			handleDbxException(e);
			return null;
		}
	}

	@Override
	public OutputStream openOutputStream(String path, long length)
			throws IOException {
		return new DropboxOutputStream(mDbxClient, absPath(path));
	}

	@Override
	public String getFilesystemRootPath() {
		return "/";
	}

}
