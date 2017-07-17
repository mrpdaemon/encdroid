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

	// TaskFragment we're associated with
	protected TaskFragment mTaskFragment;

	// Associated progress dialog
	private ProgressDialog mProgressDialog = null;

	// Progress object to be updated by the task
	protected TaskProgress mTaskProgress;

	// Progress object that reflects what's currently displayed
	private TaskProgress mDisplayedTaskProgress;

	// Progress dialog parameters set by subclass constructor

	// Title of the progress dialog
	protected String mProgressDialogTitle = null;

	// Resource ID for the progress dialog message string
	protected int mProgressDialogMsgResId = -1;

	// Whether we're working on multiple jobs
	protected boolean mProgressDialogMultiJob = false;

	// Whether we're working on multiple files
	protected boolean mProgressDialogMultiFile = false;

	// Just a spinner dialog
	protected boolean mProgressDialogSpinnerOnly = false;

	public EDAsyncTask(TaskFragment fragment) {
		this.mTaskFragment = fragment;
		this.mTaskProgress = new TaskProgress();
		this.mDisplayedTaskProgress = new TaskProgress();
	}

	public TaskFragment getFragment() {
		return mTaskFragment;
	}

	public TaskProgress getProgress() {
		return mTaskProgress;
	}

	public void setProgressDialog(ProgressDialog progressDialog) {
		this.mProgressDialog = progressDialog;

		if (progressDialog == null) {
			// Reset displayed progress
			mDisplayedTaskProgress = new TaskProgress();
		}
	}

	// Notify that progress is updated
	public void updateProgress() {
		publishProgress((Progress[]) null);
	}

	// Method for subclasses to create ProgressDialog
	protected ProgressDialog createProgressDialog(Activity activity) {
		ProgressDialog dialog = new ProgressDialog(activity);

		if (mProgressDialogMsgResId != -1) {
			dialog.setMessage(activity.getString(mProgressDialogMsgResId));
		}

		if (!mProgressDialogSpinnerOnly) {
			dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		}

		dialog.setTitle(mProgressDialogTitle);
		dialog.setCancelable(false);

		return dialog;
	}

	/*
	 * Called after updateProgress() -> publishProgress() from the UI thread.
	 * 
	 * Implements generic way to update the various types of progress dialogs
	 */
	@Override
	protected void onProgressUpdate(Progress... values) {
		super.onProgressUpdate(values);

		if (mProgressDialog != null) {

			// Message string
			if ((mDisplayedTaskProgress.getCurrentJob() != mTaskProgress
					.getCurrentJob())
					|| (mDisplayedTaskProgress.getNumJobs() != mTaskProgress
							.getNumJobs())
					|| (mDisplayedTaskProgress.getCurrentFileName() != mTaskProgress
							.getCurrentFileName())) {
				String messageString = "";

				if (mProgressDialogMultiJob) {
					messageString += "[" + mTaskProgress.getCurrentJob() + "/"
							+ mTaskProgress.getNumJobs() + "]\n";
					mDisplayedTaskProgress.setCurrentJob(mTaskProgress
							.getCurrentJob());
					mDisplayedTaskProgress.setNumJobs(mTaskProgress
							.getNumJobs());
				}

				if (!mProgressDialogSpinnerOnly) {
					if (mProgressDialogMsgResId != -1) {
						messageString += String.format(mTaskFragment
								.getString(mProgressDialogMsgResId),
								mTaskProgress.getCurrentFileName());
					}
					mDisplayedTaskProgress.setCurrentFileName(mTaskProgress
							.getCurrentFileName());
				}

				mProgressDialog.setMessage(messageString);
			}

			// Max progress
			if ((mDisplayedTaskProgress.getTotalFiles() != mTaskProgress
					.getTotalFiles())
					|| (mDisplayedTaskProgress.getTotalBytes() != mTaskProgress
							.getTotalBytes())) {
				if (mProgressDialogMultiFile) {
					mProgressDialog.setMax(mTaskProgress.getTotalFiles());
					mDisplayedTaskProgress.setTotalFiles(mTaskProgress
							.getTotalFiles());
				} else {
					mProgressDialog.setMax(mTaskProgress.getTotalBytes());
					mDisplayedTaskProgress.setTotalBytes(mTaskProgress
							.getTotalBytes());
				}
			}

			// Progress
			if ((mDisplayedTaskProgress.getCurrentFileIdx() != mTaskProgress
					.getCurrentFileIdx())
					|| (mDisplayedTaskProgress.getCurrentBytes() != mTaskProgress
							.getCurrentBytes())) {
				if (mProgressDialogMultiFile) {
					mProgressDialog.setProgress(mTaskProgress
							.getCurrentFileIdx());
					mDisplayedTaskProgress.setCurrentFileIdx(mTaskProgress
							.getCurrentFileIdx());
				} else {
					mProgressDialog
							.setProgress(mTaskProgress.getCurrentBytes());
					mDisplayedTaskProgress.setCurrentBytes(mTaskProgress
							.getCurrentBytes());
				}
			}
		}
	}

}