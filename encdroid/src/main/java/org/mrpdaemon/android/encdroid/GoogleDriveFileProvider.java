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
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import org.mrpdaemon.sec.encfs.EncFSFileInfo;
import org.mrpdaemon.sec.encfs.EncFSFileProvider;

import android.util.Log;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

public class GoogleDriveFileProvider implements EncFSFileProvider {

	// Logger tag
	private final static String TAG = "GoogleDriveFileProvider";

	// Directory MIME type
	public final static String DIRECTORY_MIME_TYPE = "application/vnd.google-apps.folder";

	// Root path for this file provider
	private String rootPath;

	// Drive service object
	private Drive driveService;

	// Standard search filter
	private static String searchFilter = " and trashed=false";

	// File ID caching (absPath -> fileId)
	private HashMap<String, String> fileIdCache;

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

	// Return the last element of a given path
	private String lastPathElement(String path) {
		StringTokenizer st = new StringTokenizer(path, "/");

		String token = "/";
		while (st.hasMoreTokens()) {
			token = st.nextToken();
		}

		return token;
	}

	// Generate parent path of a given path
	private String parentPath(String path) {
		String result = "/";

		StringTokenizer st = new StringTokenizer(path, "/");
		if (st.countTokens() <= 1) {
			// Children of root
			return result;
		}

		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (st.hasMoreTokens()) {
				result += token + "/";
			}
		}

		return result;
	}

	// Convert from a File to EncFSFileInfo
	private EncFSFileInfo fileToEncFSFileInfo(String parentRelPath, File file) {
		return new EncFSFileInfo(file.getTitle(), parentRelPath,
				fileIsDirectory(file), file.getModifiedDate().getValue(),
				(file.getFileSize() != null) ? file.getFileSize().longValue()
						: 0, true, file.getEditable(), false);
	}

	// Lookup the given path in the file ID cache
	private String fileIdCacheLookup(String path) {
		String cachedId = fileIdCache.get(path);
		if (cachedId == null) {
			Log.v(TAG, "Cache lookup for path: '" + path + "' failed");
		} else {
			Log.v(TAG, "Cache lookup for path: '" + path + " returning: '"
					+ cachedId + "'");
		}
		return cachedId;
	}

	// Insert the given file ID to the cache
	private void fileIdCacheInsert(String path, String fileId) {
		Log.v(TAG, "Caching file ID: '" + fileId + "' for path '" + path + "'");
		fileIdCache.put(path, fileId);
	}

	// Delete the cache entry for the given path
	private void fileIdCacheDelete(String path) {
		Log.v(TAG, "Deleting file ID cache entry for '" + path + "'");
		fileIdCache.remove(path);
	}

	// Convert from a given path to file ID
	private String pathToFileId(String path) throws IOException {

		Log.v(TAG, "pathToFileId '" + path + "'");

		// Root alias
		if (path.equals("/")) {
			return "root";
		}

		// Drop trailing / from non-root path
		if (path.charAt(path.length() - 1) == '/') {
			path = path.substring(0, path.length() - 1);
			Log.v(TAG, "Clipped path to: '" + path + "'");
		}

		// Do cache lookup first
		String cachedId = fileIdCacheLookup(path);
		if (cachedId != null) {
			return cachedId;
		}

		// Cache lookup failed, need to do API requests for each path element
		StringTokenizer st = new StringTokenizer(path, "/");
		String curFileId = "root";
		String curPath = "";

		while (st.hasMoreTokens()) {
			String pathElement = st.nextToken();

			// See if current path is in cache yet
			curPath += "/" + pathElement;
			cachedId = fileIdCacheLookup(curPath);
			if (cachedId != null) {
				curFileId = cachedId;
				continue;
			}

			// Not in cache, do API requests to find pathElement
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
							// Have more path elements, cache and continue
							fileIdCacheInsert(curPath, curFileId);
							break;
						} else {
							// Found the search target, cache and return
							fileIdCacheInsert(path, curFileId);
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
					// Cache and return
					fileIdCacheInsert(path, curFileId);
					return curFileId;
				}
			}
		}

		return null;
	}

	public GoogleDriveFileProvider(Drive driveService, String rootPath) {
		this.driveService = driveService;
		this.rootPath = rootPath;
		this.fileIdCache = new HashMap<String, String>();
	}

	@Override
	public boolean copy(String srcPath, String dstPath) throws IOException {

		Log.v(TAG, "Copy '" + srcPath + "' to '" + dstPath + "'");

		/*
		 * If destination path exists, delete it first. This is a workaround for
		 * encfs-java behavior without chainedNameIV, the file is
		 * touched/created before calling into this function.
		 */
		if (exists(dstPath)) {
			delete(dstPath);
		}

		// Get fileId for srcPath
		String fileId = pathToFileId(absPath(srcPath));
		if (fileId == null) {
			return false;
		}
		Log.v(TAG, "File ID for '" + absPath(srcPath) + "' is '" + fileId + "'");

		// Get fileId of dstPath's parent
		String dstParentFileId = pathToFileId(parentPath(absPath(dstPath)));
		if (dstParentFileId == null) {
			return false;
		}
		Log.v(TAG, "File ID for '" + parentPath(absPath(dstPath)) + "' is '"
				+ dstParentFileId + "'");

		File copiedFile = new File();
		copiedFile.setTitle(lastPathElement(dstPath));

		// Add dstParentFileId to parents
		ParentReference parents = new ParentReference();
		parents.setId(dstParentFileId);
		ArrayList<ParentReference> parentList = new ArrayList<ParentReference>();
		parentList.add(parents);
		copiedFile.setParents(parentList);

		// API request for the copy
		driveService.files().copy(fileId, copiedFile).execute();

		// Invalidate old file ID cache entry
		fileIdCacheDelete(absPath(srcPath));

		return true;
	}

	public File prepareFileForCreation(String path) throws IOException {
		// Get parent's file Id
		String parentAbsPath = parentPath(absPath(path));
		String parentFileId = pathToFileId(parentAbsPath);
		if (parentFileId == null) {
			Log.e(TAG, "Parent path '" + parentAbsPath + "' not found!");
			return null;
		}

		// Create a new file
		File newFile = new File();
		newFile.setTitle(lastPathElement(path));

		// Mark parent reference
		ParentReference parentRef = new ParentReference();
		parentRef.setId(parentFileId);
		ArrayList<ParentReference> refList = new ArrayList<ParentReference>();
		refList.add(parentRef);
		newFile.setParents(refList);

		return newFile;
	}

	@Override
	public EncFSFileInfo createFile(String path) throws IOException {

		Log.v(TAG, "createFile '" + path + "'");

		// Make sure the given path doesn't exist
		if (exists(path)) {
			throw new IOException("Can't create file: already exists");
		}

		File newFile = prepareFileForCreation(path);

		// API request
		File createdFile = driveService.files().insert(newFile).execute();

		return fileToEncFSFileInfo(parentPath(path), createdFile);
	}

	@Override
	public boolean delete(String path) throws IOException {
		String fileId = pathToFileId(absPath(path));
		if (fileId == null) {
			throw new IOException("Can't delete file: not found");
		}

		driveService.files().delete(fileId).execute();

		// Get rid of cache entry
		fileIdCacheDelete(absPath(path));

		return true;
	}

	@Override
	public boolean exists(String relPath) throws IOException {
		return (pathToFileId(absPath(relPath)) != null);
	}

	@Override
	public EncFSFileInfo getFileInfo(String relPath) throws IOException {
		String fileId = pathToFileId(absPath(relPath));
		if (fileId == null) {
			return null;
		}

		File file = driveService.files().get(fileId).execute();
		if (file == null) {
			return null;
		}

		return fileToEncFSFileInfo(parentPath(relPath), file);
	}

	@Override
	public String getFilesystemRootPath() {
		return "/";
	}

	// Returns whether the given File is a directory
	private boolean fileIsDirectory(File file) {
		return file.getMimeType().equals(DIRECTORY_MIME_TYPE);
	}

	@Override
	public boolean isDirectory(String relPath) throws IOException {
		String fileId = pathToFileId(absPath(relPath));
		if (fileId == null) {
			return false;
		}

		File file = driveService.files().get(fileId).execute();
		if (file == null) {
			return false;
		}

		return fileIsDirectory(file);
	}

	@Override
	public List<EncFSFileInfo> listFiles(String relPath) throws IOException {
		List<EncFSFileInfo> result = new ArrayList<EncFSFileInfo>();
		List<File> apiResult = new ArrayList<File>();

		// Get file ID for path
		String fileId;
		try {
			fileId = pathToFileId(absPath(relPath));
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
					try {
						result.add(fileToEncFSFileInfo(relPath, file));
					} catch (IllegalArgumentException iae) {
						/*
						 * Can happen if the file name is illegal, for example
						 * starting with '/'. In this case just skip adding the
						 * file to the list.
						 */
					}
				}
			}
		}

		return result;
	}

	@Override
	public boolean mkdir(String path) throws IOException {

		Log.v(TAG, "mkdir '" + path + "'");

		// Make sure the given path doesn't exist
		if (exists(path)) {
			throw new IOException("Can't create directory: already exists");
		}

		// Get parent's file Id
		String parentAbsPath = parentPath(absPath(path));
		String parentFileId = pathToFileId(parentAbsPath);
		if (parentFileId == null) {
			Log.e(TAG, "Parent path '" + parentAbsPath + "' not found!");
			return false;
		}

		// Create a new file
		File newFile = new File();
		newFile.setTitle(lastPathElement(path));

		// Mark parent reference
		ParentReference parentRef = new ParentReference();
		parentRef.setId(parentFileId);
		ArrayList<ParentReference> refList = new ArrayList<ParentReference>();
		refList.add(parentRef);
		newFile.setParents(refList);

		// Set mime type
		newFile.setMimeType(DIRECTORY_MIME_TYPE);

		// API request
		driveService.files().insert(newFile).execute();

		return true;
	}

	@Override
	public boolean mkdirs(String path) throws IOException {
		Log.e(TAG, "NOT IMPLEMENTED: mkdirs");
		return false;
	}

	@Override
	public boolean move(String srcPath, String dstPath) throws IOException {

		Log.v(TAG, "Move '" + srcPath + "' to '" + dstPath + "'");

		// Make sure the destination path doesn't exist
		if (exists(dstPath)) {
			throw new IOException("Can't move: destination already exists");
		}

		// Get fileId for srcPath
		String fileId = pathToFileId(absPath(srcPath));
		if (fileId == null) {
			return false;
		}
		Log.v(TAG, "File ID for '" + absPath(srcPath) + "' is '" + fileId + "'");

		// Get fileId of srcPath's parent
		String srcParentFileId = pathToFileId(parentPath(absPath(srcPath)));
		if (srcParentFileId == null) {
			return false;
		}
		Log.v(TAG, "File ID for '" + parentPath(absPath(srcPath)) + "' is '"
				+ srcParentFileId + "'");

		// Get fileId of dstPath's parent
		String dstParentFileId = pathToFileId(parentPath(absPath(dstPath)));
		if (dstParentFileId == null) {
			return false;
		}
		Log.v(TAG, "File ID for '" + parentPath(absPath(dstPath)) + "' is '"
				+ dstParentFileId + "'");

		/*
		 * If we're doing a simple rename then the src/dst parents are the same
		 * so we don't need to do any metadata operations.
		 */
		if (!dstParentFileId.equals(srcParentFileId)) {
			// API request to add dstParentFileId to parents
			ParentReference newParent = new ParentReference();
			newParent.setId(dstParentFileId);
			try {
				ParentReference result = driveService.parents()
						.insert(fileId, newParent).execute();
				Log.v(TAG, "Insert returned " + result.toPrettyString());
			} catch (IOException e) {
				Log.e(TAG, "An error occurred: " + e.getMessage());
				return false;
			}

			// API request to remove srcParentFileId from parents
			try {
				driveService.parents().delete(fileId, srcParentFileId)
						.execute();
			} catch (IOException e) {
				Log.e(TAG, "An error occurred: " + e.getMessage());
				return false;
			}
		}

		// Rename file if needed
		String newName = lastPathElement(dstPath);
		if (!lastPathElement(srcPath).equals(newName)) {
			Log.v(TAG, "Renaming '" + lastPathElement(srcPath) + "' to '"
					+ newName + "'");
			try {
				File file = new File();
				file.setTitle(newName);

				Files.Patch patchRequest = driveService.files().patch(fileId,
						file);
				patchRequest.setFields("title");
				patchRequest.execute();
			} catch (IOException e) {
				Log.e(TAG, "An error occurred: " + e.getMessage());
				return false;
			}
		}

		// Invalidate old file ID cache entry
		fileIdCacheDelete(absPath(srcPath));

		return true;
	}

	@Override
	public InputStream openInputStream(String relPath) throws IOException {

		String fileId = pathToFileId(absPath(relPath));
		if (fileId == null) {
			return null;
		}

		File file = driveService.files().get(fileId).execute();

		if (file.getDownloadUrl() != null && file.getDownloadUrl().length() > 0) {

			HttpResponse resp = driveService.getRequestFactory()
					.buildGetRequest(new GenericUrl(file.getDownloadUrl()))
					.execute();
			return resp.getContent();
		}

		return null;
	}

	public Drive getDriveService() {
		return driveService;
	}

	@Override
	public OutputStream openOutputStream(String path, long length)
			throws IOException {
		return new GoogleDriveOutputStream(this, path, length);
	}

}
