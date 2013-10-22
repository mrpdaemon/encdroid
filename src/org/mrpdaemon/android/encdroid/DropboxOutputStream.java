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

import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;

public class DropboxOutputStream extends OutputStream {

	private static final String TAG = "DropboxOutputStream";

	// Pipe's input end to hand off to DropboxAPI.putFile()
	private PipedInputStream pipeDropbox;

	// Pipe's output end that this class writes to
	private PipedOutputStream pipeToWrite;

	// Whether the upload failed
	private volatile boolean failed;

	// Failure message
	private volatile String failMessage;

	// Thread for stopping
	private Thread thread;

	// Length
	private final long fileLength;

	public DropboxOutputStream(final DropboxAPI<AndroidAuthSession> api,
			final String dstPath, long length) throws IOException {
		this.failed = false;
		this.fileLength = length;

		Log.d(TAG, "Creating output stream for path " + dstPath);

		// Create pipes
		pipeDropbox = new PipedInputStream();
		pipeToWrite = new PipedOutputStream(pipeDropbox);

		thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					api.putFileOverwrite(dstPath, pipeDropbox, fileLength, null);
				} catch (DropboxException e) {
					Logger.logException(TAG, e);
					// Propagate the error
					if (e.getMessage() != null) {
						DropboxOutputStream.this.fail(e.getMessage());
					} else {
						DropboxOutputStream.this.fail(e.toString());
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
		pipeDropbox.close();
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