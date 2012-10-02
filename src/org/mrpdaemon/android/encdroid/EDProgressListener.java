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
import android.app.ProgressDialog;

public class EDProgressListener extends EncFSProgressListener {

	// Progress dialog to update
	private ProgressDialog myDialog;
	
	// Activity for running UI tasks
	private Activity myActivity;

	public EDProgressListener(ProgressDialog dialog, Activity activity) {
		myDialog = dialog;
		myActivity = activity;
	}

	@Override
	public void handleEvent(int eventType) {
		switch (eventType) {
		case EDProgressListener.FILES_COUNTED_EVENT:
			myActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					myDialog.setMax(getNumFiles());
				}
			});
			break;
		case EDProgressListener.NEW_FILE_EVENT:
			myActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					myDialog.setMessage(getCurrentFile());
				}
			});
			break;
		case EDProgressListener.FILE_PROCESS_EVENT:
			myActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					myDialog.incrementProgressBy(1);
				}
			});
			break;
		case EDProgressListener.OP_COMPLETE_EVENT:
			break;
		default:
			break;
		}
	}
}
