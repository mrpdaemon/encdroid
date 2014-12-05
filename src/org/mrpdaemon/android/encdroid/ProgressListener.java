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

import org.mrpdaemon.sec.encfs.EncFSProgressListener;

import android.app.Activity;

public class ProgressListener extends EncFSProgressListener {

	// Task that owns this progress listener
	private EDAsyncTask<?, ?, ?> myTask;

	public ProgressListener(EDAsyncTask<?, ?, ?> task) {
		myTask = task;
	}

	@Override
	public void handleEvent(int eventType) {

		switch (eventType) {
		case ProgressListener.FILES_COUNTED_EVENT:
			myTask.getProgress().setTotalFiles(getNumFiles());
			myTask.updateProgress();
			break;
		case ProgressListener.NEW_FILE_EVENT:
			myTask.getProgress().setCurrentFileName(getCurrentFile());
			myTask.updateProgress();
			break;
		case ProgressListener.FILE_PROCESS_EVENT:
			myTask.getProgress().incCurrentFileIdx();
			myTask.updateProgress();
			break;
		case ProgressListener.OP_COMPLETE_EVENT:
			break;
		default:
			break;
		}
	}
}
