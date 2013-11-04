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
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.mrpdaemon.sec.encfs.EncFSFileInfo;
import org.mrpdaemon.sec.encfs.EncFSFileProvider;

import android.util.Log;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

public class GoogleDriveFileProvider implements EncFSFileProvider {

	// Logger tag
	private final static String TAG = "GoogleDriveFileProvider";

	// Root path for this file provider
	private String rootPath;

	// Drive service object
	private Drive driveService;

	// Standard search filter
	private static String searchFilter = " and trashed=false and hidden=false";

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

	// Convert from a File to EncFSFileInfo
	private EncFSFileInfo fileToEncFSFileInfo(String parentPath, File file) {
		// XXX: fix date (not 0)
		return new EncFSFileInfo(file.getTitle(), parentPath,
				fileIsDirectory(file), 0, (file.getFileSize() != null) ? file
						.getFileSize().longValue() : 0, true,
				file.getEditable(), false);
	}

	// Convert from a given path to file ID
	private String pathToFileId(String path) throws IOException {

		// Root alias
		if (path.equals("/")) {
			return "root";
		}

		StringTokenizer st = new StringTokenizer(path, "/");
		String curFileId = "root";

		while (st.hasMoreTokens()) {
			String pathElement = st.nextToken();
			File curFile = driveService.files().get(curFileId).execute();

			if (fileIsDirectory(curFile)) {
				// Get file list of current folder
				FileList childList = driveService.files().list()
						.setQ("'" + curFileId + "' in parents" + searchFilter)
						.execute();

				// Search pathElement in the list
				boolean found = false;
				for (File tmpFile : childList.getItems()) {
					if (tmpFile.getTitle().equals(pathElement)) {
						// Found pathElement
						found = true;
						curFileId = tmpFile.getId();
						if (st.hasMoreTokens()) {
							// Have more path elements
							break;
						} else {
							// Found the search target
							return curFileId;
						}
					}
				}

				if (found == false) {
					// A path element was not found
					return null;
				}
			} else {
				// Better be the last element!
				if (!st.hasMoreTokens()) {
					return curFileId;
				}
			}
		}

		return null;
	}

	public GoogleDriveFileProvider(Drive driveService, String rootPath) {
		this.driveService = driveService;
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
		return "/";
	}

	@Override
	public boolean isDirectory(String path) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	// Returns whether the given File is a directory
	private boolean fileIsDirectory(File file) {
		return file.getMimeType().equals("application/vnd.google-apps.folder");
	}

	@Override
	public List<EncFSFileInfo> listFiles(String path) throws IOException {
		List<EncFSFileInfo> result = new ArrayList<EncFSFileInfo>();
		List<File> apiResult = new ArrayList<File>();

		// Get file ID for path
		String fileId;
		try {
			fileId = pathToFileId(absPath(path));
		} catch (IOException e) {
			Log.e(TAG, "An error occurred: " + e.getMessage());
			return result;
		}

		Files.List request = driveService.files().list()
				.setQ("'" + fileId + "' in parents" + searchFilter);

		do {
			try {
				FileList files = request.execute();

				apiResult.addAll(files.getItems());
				request.setPageToken(files.getNextPageToken());
			} catch (IOException e) {
				Log.e(TAG, "An error occurred: " + e.getMessage());
				request.setPageToken(null);
			}
		} while (request.getPageToken() != null
				&& request.getPageToken().length() > 0);

		// Convert API results into EncFSFileInfo's
		for (File file : apiResult) {
			if (file != null) {
				// Filter out Google document formats
				String mimeType = file.getMimeType();
				if (fileIsDirectory(file)
						|| !mimeType.startsWith("application/vnd.google-apps")) {
					result.add(fileToEncFSFileInfo(path, file));
				}
			}
		}

		return result;
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
