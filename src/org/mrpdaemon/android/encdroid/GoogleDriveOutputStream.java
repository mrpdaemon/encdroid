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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import android.util.Log;

public class GoogleDriveOutputStream extends OutputStream {

	private static final String TAG = "GoogleDriveOutputStream";

	// Pipe's input end to hand off to the drive service
	private PipedInputStream pipeDrive;

	// Pipe's output end that this class writes to
	private OutputStream pipeToWrite;

	// Whether the upload failed
	private volatile boolean failed;

	// Failure message
	private volatile String failMessage;

	// Thread for stopping
	private Thread thread;

	public GoogleDriveOutputStream(final GoogleDriveFileProvider fileProvider,
			final String dstPath, final long length) throws IOException {
		this.failed = false;

		Log.d(TAG, "Creating output stream for path " + dstPath);

		// Create pipes
		pipeDrive = new PipedInputStream();
		pipeToWrite = new PipedOutputStream(pipeDrive);

		// Delete file touched by encfs-java
		if (fileProvider.exists(dstPath)) {
			fileProvider.delete(dstPath);
		}

		final File newFile = fileProvider.prepareFileForCreation(dstPath);

		thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					InputStreamContent streamContent = new InputStreamContent(
							"application/octet-stream", pipeDrive);
					streamContent.setLength(length);

					Drive.Files.Insert insert = fileProvider.getDriveService()
							.files().insert(newFile, streamContent);

					// Use resumable upload to not time out for larger files
					MediaHttpUploader uploader = insert.getMediaHttpUploader();
					uploader.setDirectUploadEnabled(false);
					uploader.setChunkSize(MediaHttpUploader.DEFAULT_CHUNK_SIZE);

					insert.execute();

				} catch (IOException e) {
					if (e.getMessage() != null) {
						GoogleDriveOutputStream.this.fail(e.getMessage());
					} else {
						GoogleDriveOutputStream.this.fail(e.toString());
					}
				}
			}
		});

		thread.start();
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

		pipeToWrite.flush();

		try {
			thread.join();
		} catch (InterruptedException e) {
			throw new IOException(e.getMessage());
		}

		pipeToWrite.close();
		pipeDrive.close();
	}

	@Override
	public void flush() throws IOException {
		Log.v(TAG, "flush() called");

		if (getFailed()) {
			throw new IOException(getFailMessage());
		}
		pipeToWrite.flush();
	}

	@Override
	public void write(byte[] buffer, int offset, int count) throws IOException {
		Log.v(TAG, "write() " + buffer.length + " bytes offset: " + offset
				+ " count: " + count);

		if (getFailed()) {
			throw new IOException(getFailMessage());
		}

		pipeToWrite.write(buffer, offset, count);
	}

	@Override
	public void write(byte[] buffer) throws IOException {
		Log.v(TAG, "write() " + buffer.length + " bytes");

		if (getFailed()) {
			throw new IOException(getFailMessage());
		}

		pipeToWrite.write(buffer);
	}

	@Override
	public void write(int oneByte) throws IOException {
		Log.v(TAG, "write() " + oneByte);

		if (getFailed()) {
			throw new IOException(getFailMessage());
		}

		pipeToWrite.write(oneByte);
	}
}