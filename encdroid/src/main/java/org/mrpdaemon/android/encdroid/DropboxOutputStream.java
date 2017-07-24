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

import java.io.IOException;
import java.io.OutputStream;

import android.support.annotation.NonNull;
import android.util.Log;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxUploader;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;

class DropboxOutputStream extends OutputStream {

	private static final String TAG = "DropboxOutputStream";

	// Uploader object
	private DbxUploader mUploader;

	// Upload output stream
	private OutputStream mUploadOutputStream;

	// Whether the upload failed
	private volatile boolean failed;

	// Failure message
	private volatile String failMessage;

	DropboxOutputStream(final DbxClientV2 dbxClientV2,
						final String dstPath) throws IOException {
		this.failed = false;

		Log.d(TAG, "Creating output stream for path " + dstPath);

		try {
			mUploader = dbxClientV2.files().uploadBuilder(dstPath).withMode(WriteMode.OVERWRITE).start();
			mUploadOutputStream = mUploader.getOutputStream();
		} catch (DbxException e) {
			Logger.logException(TAG, e);
			// Propagate the error
			if (e.getMessage() != null) {
				DropboxOutputStream.this.fail(e.getMessage());
			} else {
				DropboxOutputStream.this.fail(e.toString());
			}
		}
	}

	private void fail(String message) {
		failed = true;
		failMessage = message;
	}

	private boolean getFailed() {
		return failed;
	}

	private String getFailMessage() {
		return failMessage;
	}

	@Override
	public void close() throws IOException {
		Log.v(TAG, "close() called");

		if (getFailed()) {
			throw new IOException(getFailMessage());
		}

		mUploadOutputStream.flush();
		mUploadOutputStream.close();

		try {
			mUploader.finish();
		} catch (DbxException e) {
		Logger.logException(TAG, e);
		// Propagate the error
		if (e.getMessage() != null) {
			DropboxOutputStream.this.fail(e.getMessage());
		} else {
			DropboxOutputStream.this.fail(e.toString());
		}
	}
	}

	@Override
	public void flush() throws IOException {
		Log.v(TAG, "flush() called");

		if (getFailed()) {
			throw new IOException(getFailMessage());
		}
		mUploadOutputStream.flush();
	}

	@Override
	public void write(@NonNull byte[] buffer, int offset, int count) throws IOException {
		Log.v(TAG, "write() " + buffer.length + " bytes offset: " + offset
				+ " count: " + count);

		if (getFailed()) {
			throw new IOException(getFailMessage());
		}

		mUploadOutputStream.write(buffer, offset, count);
	}

	@Override
	public void write(@NonNull byte[] buffer) throws IOException {
		Log.v(TAG, "write() " + buffer.length + " bytes");

		if (getFailed()) {
			throw new IOException(getFailMessage());
		}

		mUploadOutputStream.write(buffer);
	}

	@Override
	public void write(int oneByte) throws IOException {
		Log.v(TAG, "write() " + oneByte);

		if (getFailed()) {
			throw new IOException(getFailMessage());
		}

		mUploadOutputStream.write(oneByte);
	}
}