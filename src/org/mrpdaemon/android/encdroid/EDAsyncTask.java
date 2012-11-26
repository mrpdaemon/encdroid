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

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;

public abstract class EDAsyncTask<Params, Progress, Result> extends
		AsyncTask<Params, Progress, Result> {
	// The progress dialog for this task
	protected ProgressDialog myDialog;

	// The activity for this task
	private Activity myActivity = null;

	public ProgressDialog getProgressDialog() {
		return myDialog;
	}

	public void setProgressDialog(ProgressDialog dialog) {
		this.myDialog = dialog;
	}

	public void setActivity(Activity activity) {
		this.myActivity = activity;
	}

	public Activity getActivity() {
		return myActivity;
	}

	public void incrementProgressBy(int diff) {
		if (myDialog != null) {
			myDialog.incrementProgressBy(diff);
		}
	}

	public void setMaxProgress(int max) {
		if (myDialog != null) {
			myDialog.setMax(max);
		}
	}

	public void setProgressMessage(String message) {
		if (myDialog != null) {
			myDialog.setMessage(message);
		}
	}
}