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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import org.mrpdaemon.sec.encfs.EncFSFile;
import org.mrpdaemon.sec.encfs.EncFSFileInputStream;
import org.mrpdaemon.sec.encfs.EncFSFileOutputStream;
import org.mrpdaemon.sec.encfs.EncFSVolume;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnShowListener;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.text.Editable;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class VolumeBrowserActivity extends ListActivity {

	// Parameter key for specifying volume index
	public final static String VOL_ID_KEY = "vol_id";

	// Name of the SD card directory for copying files into
	public final static String ENCDROID_SD_DIR_NAME = "Encdroid";

	// Request ID's for calling into different activities
	public final static int VIEW_FILE_REQUEST = 0;
	public final static int PICK_FILE_REQUEST = 1;
	public final static int EXPORT_FILE_REQUEST = 2;

	// Saved instance state keys
	private final static String SAVED_CUR_DIR_PATH_KEY = "cur_dir_path";
	private final static String SAVED_PASTE_MODE_KEY = "paste_mode";
	private final static String SAVED_PASTE_FILE_PATH_KEY = "paste_file_path";
	private final static String SAVED_OPEN_FILE_PATH_KEY = "open_file_path";
	private final static String SAVED_OPEN_FILE_NAME_KEY = "open_file_name";
	private final static String SAVED_IMPORT_FILE_NAME_KEY = "import_file_name";
	private final static String SAVED_ASYNC_TASK_ID_KEY = "async_task_id";
	private final static String SAVED_PROGRESS_BAR_MAX_KEY = "prog_bar_max";
	private final static String SAVED_PROGRESS_BAR_PROG_KEY = "prog_bar_prog";
	private final static String SAVED_PROGRESS_BAR_STR_ARG_KEY = "prog_bar_strarg";

	// Dialog ID's
	private final static int DIALOG_ERROR = 0;
	private final static int DIALOG_FILE_RENAME = 1;
	private final static int DIALOG_FILE_DELETE = 2;
	private final static int DIALOG_CREATE_FOLDER = 3;

	// Async task ID's
	private final static int ASYNC_TASK_SYNC = 0;
	private final static int ASYNC_TASK_IMPORT = 1;
	private final static int ASYNC_TASK_DECRYPT = 2;
	private final static int ASYNC_TASK_EXPORT = 3;
	private final static int ASYNC_TASK_RENAME = 4;
	private final static int ASYNC_TASK_DELETE = 5;
	private final static int ASYNC_TASK_CREATE_DIR = 6;
	private final static int ASYNC_TASK_PASTE = 7;

	// Logger tag
	private final static String TAG = "VolumeBrowserActivity";

	// Adapter for the list
	private FileChooserAdapter mAdapter = null;

	// List that is currently being displayed
	private List<FileChooserItem> mCurFileList;

	// Volume
	private Volume mVolume;

	// EncFS volume
	private EncFSVolume mEncfsVolume;

	// Directory stack
	private Stack<EncFSFile> mDirStack;

	// Current directory
	private EncFSFile mCurEncFSDir;

	// Application object
	private EDApplication mApp;

	// Text for the error dialog
	private String mErrDialogText = "";

	// Progress dialog for async progress
	private ProgressDialog mProgDialog = null;

	// Async task object
	private EDAsyncTask<Void, Void, Boolean> mAsyncTask = null;

	// Async task ID
	private int mAsyncTaskId = -1;

	// Fill task object
	private AsyncTask<Void, Void, Void> mFillTask = null;

	// File observer
	private EDFileObserver mFileObserver;

	// Original file's modified timestamp
	private Date mOrigModifiedDate;

	// EncFSFile that is currently opened
	private EncFSFile mOpenFile;

	// Path to the opened file (used during restore of mOpenFile)
	private String mOpenFilePath = null;

	// Name of the opened file (used for display purposes)
	private String mOpenFileName = null;

	// Name of the file being imported
	private String mImportFileName;

	// File that is currently selected
	private FileChooserItem mSelectedFile;

	// EncFSFile that is being pasted
	private EncFSFile mPasteFile = null;

	// Paste operations
	private static final int PASTE_OP_NONE = 0;
	private static final int PASTE_OP_CUT = 1;
	private static final int PASTE_OP_COPY = 2;

	// Paste mode
	private int mPasteMode = PASTE_OP_NONE;

	// Broadcast receiver to monitor external storage state
	BroadcastReceiver mExternalStorageReceiver;

	// Whether external storage is available
	boolean mExternalStorageAvailable = false;

	// Whether external storage is writable
	boolean mExternalStorageWriteable = false;

	// Text view for list header
	private TextView mListHeader = null;

	// Class to hold context for restoring an activity after being recreated
	private class ActivityRestoreContext {
		public Volume savedVolume;
		public EDFileObserver savedObserver;
		public FileChooserItem savedSelectedFile;
		public EDAsyncTask<Void, Void, Boolean> savedTask;
		public Date savedOrigModifiedDate;
	}

	// Saved instance state for current EncFS directory
	private String mSavedCurDirPath = null;

	// Saved instance state for paste file path
	private String mSavedPasteFilePath = null;

	// Saved instance state for progress bar string argument
	private String mSavedProgBarStrArg = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		mApp = (EDApplication) getApplication();

		mCurFileList = new ArrayList<FileChooserItem>();

		// Start monitoring external storage state
		mExternalStorageReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				updateExternalStorageState();
			}
		};
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_REMOVED);
		registerReceiver(mExternalStorageReceiver, filter);
		updateExternalStorageState();

		if (mExternalStorageAvailable == false) {
			Log.e(TAG, "No SD card is available");
			mErrDialogText = getString(R.string.error_no_sd_card);
			showDialog(DIALOG_ERROR);
			finish();
		}

		// Restore UI elements
		super.onCreate(savedInstanceState);

		setContentView(R.layout.file_chooser);

		registerForContextMenu(this.getListView());

		mListHeader = new TextView(this);
		mListHeader.setTypeface(null, Typeface.BOLD);
		mListHeader.setTextSize(16);
		this.getListView().addHeaderView(mListHeader);

		if (savedInstanceState == null) {
			// Activity being created for the first time
			Bundle params = getIntent().getExtras();
			int position = params.getInt(VOL_ID_KEY);
			mVolume = mApp.getVolumeList().get(position);
			mEncfsVolume = mVolume.getVolume();
			mCurEncFSDir = mEncfsVolume.getRootDir();
			mDirStack = new Stack<EncFSFile>();

			launchFillTask();
		} else {
			// Activity being recreated
			ActivityRestoreContext restoreContext = (ActivityRestoreContext) getLastNonConfigurationInstance();
			if (restoreContext == null) {
				/*
				 * If getLastNonConfigurationInstance() returned null the
				 * activity was killed due to low memory and is being recreated.
				 * Unfortunately we've lost all the volume state at that point
				 * so we don't have any choice but to exit back to the volume
				 * list and start over.
				 */
				exitToVolumeList();
				return;
			}
			mVolume = restoreContext.savedVolume;
			mEncfsVolume = mVolume.getVolume();
			mFileObserver = restoreContext.savedObserver;
			mSelectedFile = restoreContext.savedSelectedFile;
			mOrigModifiedDate = restoreContext.savedOrigModifiedDate;
			mAsyncTask = restoreContext.savedTask;

			mSavedCurDirPath = savedInstanceState
					.getString(SAVED_CUR_DIR_PATH_KEY);

			// Restore open file state
			mOpenFile = null;
			String openFilePath = savedInstanceState
					.getString(SAVED_OPEN_FILE_PATH_KEY);
			String openFileName = savedInstanceState
					.getString(SAVED_OPEN_FILE_NAME_KEY);
			if (!openFilePath.equals("")) {
				mOpenFilePath = openFilePath;
				mOpenFileName = openFileName;
			} else {
				mOpenFilePath = null;
				mOpenFileName = null;
			}

			// Restore paste mode
			mPasteMode = savedInstanceState.getInt(SAVED_PASTE_MODE_KEY);
			mSavedPasteFilePath = savedInstanceState
					.getString(SAVED_PASTE_FILE_PATH_KEY);

			// Restore import file state
			mImportFileName = savedInstanceState
					.getString(SAVED_IMPORT_FILE_NAME_KEY);

			// Restore async task ID
			mAsyncTaskId = savedInstanceState.getInt(SAVED_ASYNC_TASK_ID_KEY);

			if (mAsyncTask != null) {
				// Create new progress dialog and replace the old one
				createProgressBarForTask(mAsyncTaskId,
						savedInstanceState
								.getString(SAVED_PROGRESS_BAR_STR_ARG_KEY));
				mProgDialog.setMax(savedInstanceState
						.getInt(SAVED_PROGRESS_BAR_MAX_KEY));
				mProgDialog.setProgress(savedInstanceState
						.getInt(SAVED_PROGRESS_BAR_PROG_KEY));
				mAsyncTask.setProgressDialog(mProgDialog);

				// Fix the activity for the task
				mAsyncTask.setActivity(this);
			}

			// Execute async task to restore instance state
			if (mProgDialog == null || !mProgDialog.isShowing()) {
				mProgDialog = new ProgressDialog(VolumeBrowserActivity.this);
				mProgDialog.setTitle(getString(R.string.loading_contents));
				mProgDialog.setCancelable(false);
				mProgDialog.show();
				new ActivityRestoreTask(mProgDialog, savedInstanceState)
						.execute();
			} else {
				new ActivityRestoreTask(null, savedInstanceState).execute();
			}
		}
	}

	// Retain the Volume object through activity being killed
	@Override
	public Object onRetainNonConfigurationInstance() {
		ActivityRestoreContext restoreContext = new ActivityRestoreContext();
		restoreContext.savedVolume = mVolume;
		restoreContext.savedObserver = mFileObserver;
		restoreContext.savedSelectedFile = mSelectedFile;
		restoreContext.savedOrigModifiedDate = mOrigModifiedDate;
		if (mAsyncTask != null
				&& mAsyncTask.getStatus() == AsyncTask.Status.RUNNING) {
			// Clear progress bar so we don't leak it
			mProgDialog.dismiss();
			mAsyncTask.setProgressDialog(null);
			// Clear the activity so we don't leak it
			mAsyncTask.setActivity(null);
			restoreContext.savedTask = mAsyncTask;
		} else {
			restoreContext.savedTask = null;
		}
		return restoreContext;
	}

	// Retain state information through activity being killed
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		if (mCurEncFSDir == null) {
			// Being recreated again before ActivityRestoreTask ran
			outState.putString(SAVED_CUR_DIR_PATH_KEY, mSavedCurDirPath);
		} else {
			outState.putString(SAVED_CUR_DIR_PATH_KEY, mCurEncFSDir.getPath());
		}
		outState.putInt(SAVED_PASTE_MODE_KEY, mPasteMode);
		if (mPasteMode != PASTE_OP_NONE) {
			if (mPasteFile == null) {
				outState.putString(SAVED_PASTE_FILE_PATH_KEY,
						mSavedPasteFilePath);
			} else {
				outState.putString(SAVED_PASTE_FILE_PATH_KEY,
						mPasteFile.getPath());
			}
		}
		if (mOpenFile != null) {
			outState.putString(SAVED_OPEN_FILE_PATH_KEY, mOpenFile.getPath());
			outState.putString(SAVED_OPEN_FILE_NAME_KEY, mOpenFile.getName());
		} else {
			if (mOpenFilePath != null) {
				outState.putString(SAVED_OPEN_FILE_PATH_KEY, mOpenFilePath);
			} else {
				outState.putString(SAVED_OPEN_FILE_PATH_KEY, "");
			}
			if (mOpenFileName != null) {
				outState.putString(SAVED_OPEN_FILE_NAME_KEY, mOpenFileName);
			} else {
				outState.putString(SAVED_OPEN_FILE_NAME_KEY, "");
			}
		}
		outState.putString(SAVED_IMPORT_FILE_NAME_KEY, mImportFileName);
		outState.putInt(SAVED_ASYNC_TASK_ID_KEY, mAsyncTaskId);

		if (mProgDialog != null) {
			outState.putInt(SAVED_PROGRESS_BAR_MAX_KEY, mProgDialog.getMax());
			outState.putInt(SAVED_PROGRESS_BAR_PROG_KEY,
					mProgDialog.getProgress());
			outState.putString(SAVED_PROGRESS_BAR_STR_ARG_KEY,
					mSavedProgBarStrArg);
		}

		super.onSaveInstanceState(outState);
	}

	// Clean stuff up
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mExternalStorageAvailable) {
			unregisterReceiver(mExternalStorageReceiver);
		}
		if (mFillTask != null
				&& mFillTask.getStatus() == AsyncTask.Status.RUNNING) {
			mFillTask.cancel(true);
		}
	}

	// Create the options menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.volume_browser_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	// Modify options menu items
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem pasteItem = menu.findItem(R.id.volume_browser_menu_paste);
		if (mPasteMode != PASTE_OP_NONE) {
			pasteItem.setVisible(true);
		} else {
			pasteItem.setVisible(false);
		}
		return super.onPrepareOptionsMenu(menu);
	}

	// Handler for options menu selections
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.volume_browser_menu_import:
			Intent startFileChooser = new Intent(this,
					FileChooserActivity.class);

			Bundle fileChooserParams = new Bundle();
			fileChooserParams.putInt(FileChooserActivity.MODE_KEY,
					FileChooserActivity.FILE_PICKER_MODE);
			startFileChooser.putExtras(fileChooserParams);

			startActivityForResult(startFileChooser, PICK_FILE_REQUEST);
			return true;
		case R.id.volume_browser_menu_mkdir:
			showDialog(DIALOG_CREATE_FOLDER);
			return true;
		case R.id.volume_browser_menu_paste:
			// Launch async task to paste file
			createProgressBarForTask(ASYNC_TASK_PASTE, null);
			mAsyncTask = new PasteFileTask(mProgDialog);
			mAsyncTaskId = ASYNC_TASK_PASTE;
			mAsyncTask.setActivity(this);
			mAsyncTask.execute();

			return true;
		case R.id.volume_browser_menu_refresh:
			launchFillTask();
			return true;
		case android.R.id.home:
			if (mCurEncFSDir == mEncfsVolume.getRootDir()) {
				// Go back to volume list
				Intent intent = new Intent(this, VolumeListActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
			} else {
				mCurEncFSDir = mDirStack.pop();
				launchFillTask();
			}
			return true;
		default:
			return false;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onKeyDown(int, android.view.KeyEvent)
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (mCurEncFSDir == mEncfsVolume.getRootDir()) {
				// Go back to volume list
				Intent intent = new Intent(this, VolumeListActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
			} else {
				mCurEncFSDir = mDirStack.pop();
				launchFillTask();
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPrepareDialog(int, android.app.Dialog)
	 */
	@Override
	protected void onPrepareDialog(int id, final Dialog dialog) {
		EditText input;
		AlertDialog ad = (AlertDialog) dialog;

		switch (id) {
		case DIALOG_FILE_DELETE:
			ad.setTitle(String.format(getString(R.string.del_dialog_title_str),
					mSelectedFile.getName()));
			break;
		case DIALOG_FILE_RENAME:
		case DIALOG_CREATE_FOLDER:
			input = (EditText) dialog.findViewById(R.id.dialog_edit_text);
			if (input != null) {
				if (id == DIALOG_FILE_RENAME) {
					input.setText(mSelectedFile.getName());
				} else if (id == DIALOG_CREATE_FOLDER) {
					input.setText("");
				}
			} else {
				Log.e(TAG,
						"dialog.findViewById returned null for dialog_edit_text");
			}

			/*
			 * We want these dialogs to immediately proceed when the user taps
			 * "Done" in the keyboard, so we create an EditorActionListener to
			 * catch the DONE action and trigger the positive button's onClick()
			 * event.
			 */
			input.setImeOptions(EditorInfo.IME_ACTION_DONE);
			input.setOnEditorActionListener(new OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId,
						KeyEvent event) {
					if (actionId == EditorInfo.IME_ACTION_DONE) {
						Button button = ((AlertDialog) dialog)
								.getButton(Dialog.BUTTON_POSITIVE);
						button.performClick();
						return true;
					}
					return false;
				}
			});

			break;
		case DIALOG_ERROR:
			ad.setMessage(mErrDialogText);
			break;
		default:
			break;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
		AlertDialog alertDialog = null;

		LayoutInflater inflater = LayoutInflater.from(this);

		final EditText input;
		final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		switch (id) {
		case DIALOG_FILE_RENAME: // Rename file dialog

			input = (EditText) inflater.inflate(R.layout.dialog_edit, null);

			alertBuilder.setTitle(getString(R.string.frename_dialog_title_str));
			alertBuilder.setView(input);
			alertBuilder.setPositiveButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						// Rename the file
						public void onClick(DialogInterface dialog,
								int whichButton) {
							Editable value = input.getText();
							launchAsyncTask(ASYNC_TASK_RENAME, value.toString());
						}
					});
			// Cancel button
			alertBuilder.setNegativeButton(getString(R.string.btn_cancel_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							dialog.cancel();
						}
					});
			alertDialog = alertBuilder.create();

			// Show keyboard
			alertDialog.setOnShowListener(new OnShowListener() {

				@Override
				public void onShow(DialogInterface dialog) {
					imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
				}
			});
			break;
		case DIALOG_CREATE_FOLDER: // Create folder dialog

			input = (EditText) inflater.inflate(R.layout.dialog_edit, null);

			alertBuilder.setTitle(getString(R.string.mkdir_dialog_input_str));
			alertBuilder.setView(input);
			alertBuilder.setPositiveButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						// Create the folder
						public void onClick(DialogInterface dialog,
								int whichButton) {
							Editable value = input.getText();
							launchAsyncTask(ASYNC_TASK_CREATE_DIR,
									value.toString());
						}
					});
			// Cancel button
			alertBuilder.setNegativeButton(getString(R.string.btn_cancel_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							dialog.cancel();
						}
					});
			alertDialog = alertBuilder.create();

			// Show keyboard
			alertDialog.setOnShowListener(new OnShowListener() {

				@Override
				public void onShow(DialogInterface dialog) {
					imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
				}
			});
			break;
		case DIALOG_FILE_DELETE:
			alertBuilder.setTitle(String.format(
					getString(R.string.del_dialog_title_str),
					mSelectedFile.getName()));
			alertBuilder.setCancelable(false);
			alertBuilder.setPositiveButton(getString(R.string.btn_yes_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							// Delete the file
							launchAsyncTask(ASYNC_TASK_DELETE, null);
						}
					});
			alertBuilder.setNegativeButton(getString(R.string.btn_no_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					});
			alertDialog = alertBuilder.create();
			break;
		case DIALOG_ERROR:
			alertBuilder.setMessage(mErrDialogText);
			alertBuilder.setCancelable(false);
			alertBuilder.setNeutralButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							dialog.dismiss();
						}
					});
			alertDialog = alertBuilder.create();
			break;

		default:
			Log.e(TAG, "Unknown dialog ID requested " + id);
			return null;
		}

		return alertDialog;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.ListActivity#onListItemClick(android.widget.ListView,
	 * android.view.View, int, long)
	 */
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		// We use position - 1 since we have an extra header
		if (position == 0) {
			return;
		}
		FileChooserItem selected = mAdapter.getItem(position - 1);

		if (selected.isDirectory()) {
			if (selected.getName().equals("..")) {
				// Chdir up
				mCurEncFSDir = mDirStack.pop();
			} else {
				mDirStack.push(mCurEncFSDir);
				mCurEncFSDir = selected.getFile();
			}

			launchFillTask();
		} else {
			// Launch file in external application

			if (mExternalStorageWriteable == false) {
				mErrDialogText = getString(R.string.error_sd_readonly);
				showDialog(DIALOG_ERROR);
				return;
			}

			// Create sdcard dir if it doesn't exist
			File encDroidDir = new File(
					Environment.getExternalStorageDirectory(),
					ENCDROID_SD_DIR_NAME);
			if (!encDroidDir.exists()) {
				encDroidDir.mkdir();
			}

			mOpenFile = selected.getFile();
			mOpenFileName = mOpenFile.getName();
			File dstFile = new File(encDroidDir, mOpenFileName);

			// Launch async task to decrypt the file
			launchAsyncTask(ASYNC_TASK_DECRYPT, dstFile, mOpenFile);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.volume_browser_menu_rename:
			mSelectedFile = mAdapter.getItem((int) info.id);
			showDialog(DIALOG_FILE_RENAME);
			return true;
		case R.id.volume_browser_menu_delete:
			mSelectedFile = mAdapter.getItem((int) info.id);
			showDialog(DIALOG_FILE_DELETE);
			return true;
		case R.id.volume_browser_menu_cut:
			mSelectedFile = mAdapter.getItem((int) info.id);
			mPasteFile = mSelectedFile.getFile();
			mPasteMode = PASTE_OP_CUT;

			// Show toast
			Toast.makeText(
					getApplicationContext(),
					String.format(getString(R.string.toast_cut_file),
							mPasteFile.getName()), Toast.LENGTH_SHORT).show();

			return true;
		case R.id.volume_browser_menu_copy:
			mSelectedFile = mAdapter.getItem((int) info.id);
			mPasteFile = mSelectedFile.getFile();
			mPasteMode = PASTE_OP_COPY;

			// Show toast
			Toast.makeText(
					getApplicationContext(),
					String.format(getString(R.string.toast_copy_file),
							mPasteFile.getName()), Toast.LENGTH_SHORT).show();

			return true;
		case R.id.volume_browser_menu_export:
			mSelectedFile = mAdapter.getItem((int) info.id);
			mOpenFile = mSelectedFile.getFile();
			mOpenFileName = mOpenFile.getName();

			Intent startFileExport = new Intent(this, FileChooserActivity.class);

			Bundle exportFileParams = new Bundle();
			exportFileParams.putInt(FileChooserActivity.MODE_KEY,
					FileChooserActivity.EXPORT_FILE_MODE);
			exportFileParams.putString(FileChooserActivity.EXPORT_FILE_KEY,
					mSelectedFile.getName());
			startFileExport.putExtras(exportFileParams);

			startActivityForResult(startFileExport, EXPORT_FILE_REQUEST);
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu,
	 * android.view.View, android.view.ContextMenu.ContextMenuInfo)
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.volume_browser_context, menu);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onActivityResult(int, int,
	 * android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		case VIEW_FILE_REQUEST:
			// Don't need to watch any more
			mFileObserver.stopWatching();

			File dstFile = new File(mFileObserver.getPath());

			// If the file was modified we need to sync it back
			Date newDate = new Date(dstFile.lastModified());
			if (mFileObserver.wasModified()
					|| (newDate.compareTo(mOrigModifiedDate) > 0)) {
				// Sync file contents
				try {
					launchAsyncTask(ASYNC_TASK_SYNC, dstFile, mOpenFile);
				} catch (Exception e) {
					mErrDialogText = e.getMessage();
					showDialog(DIALOG_ERROR);
				}
			} else {
				// File not modified, delete from SD
				dstFile.delete();
			}

			// Clean up reference to the file observer
			mFileObserver = null;

			break;
		case PICK_FILE_REQUEST:
			if (resultCode == Activity.RESULT_OK) {
				String result = data.getExtras().getString(
						FileChooserActivity.RESULT_KEY);
				String importPath = new File(
						Environment.getExternalStorageDirectory(), result)
						.getAbsolutePath();
				Log.d(TAG, "Importing file: " + importPath);

				File importFile = new File(importPath);
				mImportFileName = importFile.getName();

				// Launch async task to complete importing
				launchAsyncTask(ASYNC_TASK_IMPORT, importFile, null);
			} else {
				Log.d(TAG, "File chooser returned unexpected return code: "
						+ resultCode);
			}
			break;
		case EXPORT_FILE_REQUEST:
			if (resultCode == Activity.RESULT_OK) {
				String result = data.getExtras().getString(
						FileChooserActivity.RESULT_KEY);
				String exportPath = new File(
						Environment.getExternalStorageDirectory(), result)
						.getAbsolutePath();
				Log.d(TAG, "Exporting file to: " + exportPath);

				if (mExternalStorageWriteable == false) {
					mErrDialogText = getString(R.string.error_sd_readonly);
					showDialog(DIALOG_ERROR);
					return;
				}

				File exportFile = new File(exportPath, mOpenFileName);

				if (exportFile.exists()) {
					// Error dialog
					mErrDialogText = String.format(
							getString(R.string.error_file_exists),
							exportFile.getName());
					showDialog(DIALOG_ERROR);
				} else {
					// Launch async task to export the file
					launchAsyncTask(ASYNC_TASK_EXPORT, exportFile, mOpenFile);
				}
			} else {
				Log.e(TAG, "File chooser returned unexpected return code: "
						+ resultCode);
			}
			break;
		default:
			Log.e(TAG, "Unknown request: " + requestCode);
			break;
		}
	}

	// Bail out to the volume list
	private void exitToVolumeList() {
		Intent intent = new Intent(this, VolumeListActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
		finish();
	}

	/*
	 * Given an EncFSFile for a directory create a stack of all directories
	 * starting from root and leading to it.
	 */
	private Stack<EncFSFile> getFileStackForEncFSDir(EncFSFile dir) {
		Stack<EncFSFile> tmpStack, resultStack;
		EncFSFile curDir;

		/*
		 * XXX: We should just compare against mVolume.getRootDir() - pending
		 * encfs-java issue XXX
		 */
		if (dir.getPath().equals(EncFSVolume.ROOT_PATH)) {
			return new Stack<EncFSFile>();
		}

		// Get the parent of the input directory
		try {
			curDir = mEncfsVolume.getFile(dir.getParentPath());
		} catch (Exception e) {
			Logger.logException(TAG, e);
			return new Stack<EncFSFile>();
		}

		// Work backwards until we hit the root directory
		tmpStack = new Stack<EncFSFile>();
		/*
		 * XXX: We should just compare against mVolume.getRootDir() - pending
		 * encfs-java issue XXX
		 */
		while (!curDir.getPath().equals(EncFSVolume.ROOT_PATH)) {
			tmpStack.push(curDir);
			try {
				curDir = mEncfsVolume.getFile(curDir.getParentPath());
			} catch (Exception e) {
				Logger.logException(TAG, e);
				return new Stack<EncFSFile>();
			}
		}

		// Add root directory to the stack
		tmpStack.push(mEncfsVolume.getRootDir());

		// Reverse tmpStack into resultStack
		resultStack = new Stack<EncFSFile>();
		if (!tmpStack.isEmpty()) {
			curDir = tmpStack.pop();
		} else {
			curDir = null;
		}
		while (curDir != null) {
			resultStack.push(curDir);
			if (!tmpStack.isEmpty()) {
				curDir = tmpStack.pop();
			} else {
				curDir = null;
			}
		}
		return resultStack;
	}

	// Update the external storage state
	private void updateExternalStorageState() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			mExternalStorageAvailable = true;
			mExternalStorageWriteable = false;
		} else {
			mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
	}

	private void fill() {

		EncFSFile[] childEncFSFiles = null;

		try {
			childEncFSFiles = mCurEncFSDir.listFiles();
		} catch (IOException e) {
			Logger.logException(TAG, e);
			mErrDialogText = "Unable to list files: " + e.getMessage();

			// Show error dialog from the UI thread
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					showDialog(DIALOG_ERROR);
				}
			});
			return;
		}

		final boolean emptyDir = childEncFSFiles.length == 0 ? true : false;

		// Set title from UI thread
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				setTitle(mVolume.getName());

				if ((emptyDir == true)
						&& (mCurEncFSDir == mEncfsVolume.getRootDir())) {
					// Empty volume message
					mListHeader.setText(getString(R.string.no_files));
				} else {
					mListHeader.setText(mCurEncFSDir.getPath());
				}
			}
		});

		List<FileChooserItem> directories = new ArrayList<FileChooserItem>();
		List<FileChooserItem> files = new ArrayList<FileChooserItem>();

		for (EncFSFile file : childEncFSFiles) {
			if (file.isDirectory()) {
				directories.add(new FileChooserItem(file.getName(), true, file,
						0));
			} else {
				if (!file.getName().equals(EncFSVolume.CONFIG_FILE_NAME)) {
					files.add(new FileChooserItem(file.getName(), false, file,
							file.getLength()));
				}
			}
		}

		// Sort directories and files separately
		Collections.sort(directories);
		Collections.sort(files);

		// Merge directories + files into current file list
		mCurFileList.clear();
		mCurFileList.addAll(directories);
		mCurFileList.addAll(files);

		// Add an item for the parent directory (..)
		if (mCurEncFSDir != mEncfsVolume.getRootDir()) {
			mCurFileList.add(0, new FileChooserItem("..", true, "", 0));
		}

		if (mAdapter == null) {
			mAdapter = new FileChooserAdapter(this, R.layout.file_chooser_item,
					mCurFileList);

			// Set list adapter from UI thread
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					setListAdapter(mAdapter);
				}
			});
		} else {
			// Notify data set change from UI thread
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mAdapter.notifyDataSetChanged();
				}
			});
		}
	}

	// Show a progress spinner and launch the fill task
	private void launchFillTask() {
		mFillTask = new FillTask();
		mFillTask.execute();
	}

	private void launchAsyncTask(int taskId, File fileArg, EncFSFile encFSArg) {

		// Show a progress bar
		createProgressBarForTask(taskId, null);

		// Launch async task
		switch (taskId) {
		case ASYNC_TASK_DECRYPT:
			mAsyncTask = new ViewFileTask(mProgDialog, encFSArg, fileArg);
			break;
		case ASYNC_TASK_IMPORT:
			mAsyncTask = new ImportFileTask(mProgDialog, fileArg);
			break;
		case ASYNC_TASK_SYNC:
			mAsyncTask = new SyncFileTask(mProgDialog, fileArg, encFSArg);
			break;
		case ASYNC_TASK_EXPORT:
			mAsyncTask = new ExportFileTask(mProgDialog, encFSArg, fileArg);
			break;
		default:
			Log.e(TAG, "Unknown task ID: " + taskId);
			break;
		}
		mAsyncTaskId = taskId;
		mAsyncTask.setActivity(this);
		mAsyncTask.execute();
	}

	private void launchAsyncTask(int taskId, String strArg) {

		// Show a progress bar
		createProgressBarForTask(taskId, strArg);

		// Launch async task
		switch (taskId) {
		case ASYNC_TASK_RENAME:
			mAsyncTask = new MetadataOpTask(mProgDialog,
					MetadataOpTask.RENAME_FILE, strArg);
			break;
		case ASYNC_TASK_DELETE:
			mAsyncTask = new MetadataOpTask(mProgDialog,
					MetadataOpTask.DELETE_FILE, strArg);
			break;
		case ASYNC_TASK_CREATE_DIR:
			mAsyncTask = new MetadataOpTask(mProgDialog,
					MetadataOpTask.CREATE_DIR, strArg);
			break;
		default:
			Log.e(TAG, "Unknown task ID: " + taskId);
			break;
		}
		mAsyncTaskId = taskId;
		mAsyncTask.setActivity(this);
		mAsyncTask.execute();
	}

	// Create and show a progress dialog for the requested task ID
	private void createProgressBarForTask(int taskId, String strArg) {
		mProgDialog = new ProgressDialog(VolumeBrowserActivity.this);
		switch (taskId) {
		case ASYNC_TASK_SYNC:
			mProgDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgDialog.setTitle(String
					.format(getString(R.string.encrypt_dialog_title_str),
							mOpenFileName));
			break;
		case ASYNC_TASK_IMPORT:
			mProgDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgDialog.setTitle(String.format(
					getString(R.string.import_dialog_title_str),
					mImportFileName));
			break;
		case ASYNC_TASK_DECRYPT:
			mProgDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgDialog.setTitle(String
					.format(getString(R.string.decrypt_dialog_title_str),
							mOpenFileName));
			break;
		case ASYNC_TASK_EXPORT:
			mProgDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgDialog
					.setTitle(String.format(
							getString(R.string.export_dialog_title_str),
							mOpenFileName));
			break;
		case ASYNC_TASK_RENAME:
			mProgDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgDialog.setTitle(String.format(
					getString(R.string.rename_dialog_title_str),
					mSelectedFile.getName(), strArg));
			break;
		case ASYNC_TASK_DELETE:
			mProgDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgDialog.setTitle(String.format(
					getString(R.string.delete_dialog_title_str),
					mSelectedFile.getName()));
			break;
		case ASYNC_TASK_CREATE_DIR:
			mProgDialog.setTitle(String.format(
					getString(R.string.mkdir_dialog_title_str), strArg));
			break;
		case ASYNC_TASK_PASTE:
			mProgDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			if (mPasteMode == PASTE_OP_COPY) {
				mProgDialog.setTitle(getString(R.string.copy_dialog_title_str));
			} else {
				mProgDialog.setTitle(getString(R.string.cut_dialog_title_str));
			}
			break;
		default:
			Log.e(TAG, "Unknown task ID: " + taskId);
			break;
		}
		mProgDialog.setCancelable(false);
		mProgDialog.show();
		mSavedProgBarStrArg = strArg;
	}

	private boolean copyStreams(InputStream is, OutputStream os,
			EDAsyncTask<Void, Void, Boolean> task) {
		try {
			byte[] buf = new byte[8192];
			int bytesRead = 0;
			try {
				try {
					bytesRead = is.read(buf);
					while (bytesRead >= 0) {
						os.write(buf, 0, bytesRead);
						bytesRead = is.read(buf);
						if (task != null) {
							task.incrementProgressBy(8192);
						}
					}
				} finally {
					is.close();
				}
			} finally {
				os.close();
			}
		} catch (IOException e) {
			Logger.logException(TAG, e);
			mErrDialogText = e.getMessage();
			return false;
		}

		return true;
	}

	private boolean exportFile(EncFSFile srcFile, File dstFile,
			EDAsyncTask<Void, Void, Boolean> task) {
		EncFSFileInputStream efis = null;
		try {
			efis = new EncFSFileInputStream(srcFile);
		} catch (Exception e) {
			Logger.logException(TAG, e);
			mErrDialogText = e.getMessage();
			return false;
		}

		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(dstFile);
		} catch (FileNotFoundException e) {
			Logger.logException(TAG, e);
			mErrDialogText = e.getMessage();
			try {
				efis.close();
			} catch (IOException ioe) {
				mErrDialogText += ioe.getMessage();
			}
			return false;
		}

		return copyStreams(efis, fos, task);
	}

	// Export all files/dirs under the EncFS dir to the given dir
	private boolean recursiveExport(EncFSFile srcDir, File dstDir,
			EDAsyncTask<Void, Void, Boolean> task) {
		try {
			for (EncFSFile file : srcDir.listFiles()) {

				File dstFile = new File(dstDir, file.getName());

				if (file.isDirectory()) { // Directory
					if (dstFile.mkdir()) {
						task.incrementProgressBy(1);
						// Export all files/folders under this dir
						if (recursiveExport(file, dstFile, task) == false) {
							return false;
						}
					} else {
						mErrDialogText = String.format(
								getString(R.string.error_mkdir_fail),
								dstFile.getAbsolutePath());
						return false;
					}
				} else { // Export an individual file
					if (exportFile(file, dstFile, null) == false) {
						return false;
					}
					task.incrementProgressBy(1);
				}
			}
		} catch (Exception e) {
			Logger.logException(TAG, e);
			mErrDialogText = e.getMessage();
			return false;
		}
		return true;
	}

	private boolean importFile(File srcFile, EncFSFile dstFile,
			EDAsyncTask<Void, Void, Boolean> task) {
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(srcFile);
		} catch (FileNotFoundException e) {
			Logger.logException(TAG, e);
			mErrDialogText = e.getMessage();
			return false;
		}

		EncFSFileOutputStream efos = null;
		try {
			efos = new EncFSFileOutputStream(dstFile, srcFile.length());
		} catch (Exception e) {
			Logger.logException(TAG, e);
			mErrDialogText = e.getMessage();
			try {
				fis.close();
			} catch (IOException ioe) {
				mErrDialogText += ioe.getMessage();
			}
			return false;
		}

		return copyStreams(fis, efos, task);
	}

	// Count files and directories under the given file
	private int countFiles(File file) {
		if (file.isDirectory()) {
			int dirCount = 1;
			for (File subFile : file.listFiles()) {
				dirCount += countFiles(subFile);
			}
			return dirCount;
		} else {
			return 1;
		}
	}

	// Import all files/dirs under the given file to the given EncFS dir
	private boolean recursiveImport(File srcDir, EncFSFile dstDir,
			EDAsyncTask<Void, Void, Boolean> task) {
		for (File file : srcDir.listFiles()) {

			String dstPath = EncFSVolume.combinePath(dstDir, file.getName());

			try {
				if (file.isDirectory()) { // Directory
					if (mEncfsVolume.makeDir(dstPath)) {
						task.incrementProgressBy(1);
						// Import all files/folders under this dir
						if (recursiveImport(file,
								mEncfsVolume.getFile(dstPath), task) == false) {
							return false;
						}
					} else {
						mErrDialogText = String.format(
								getString(R.string.error_mkdir_fail), dstPath);
						return false;
					}
				} else { // Import an individual file
					if (importFile(file, mEncfsVolume.createFile(dstPath), null) == false) {
						return false;
					}
					task.incrementProgressBy(1);
				}
			} catch (Exception e) {
				Logger.logException(TAG, e);
				mErrDialogText = e.getMessage();
				return false;
			}
		}
		return true;
	}

	/*
	 * Task to fill the volume browser list. This is needed because fill() can
	 * end up doing network I/O with certain file providers and starting with
	 * API version 13 doing so results in a NetworkOnMainThreadException.
	 */
	private class FillTask extends AsyncTask<Void, Void, Void> {

		private ProgressBar mProgBar;
		private ListView mListView;
		private LinearLayout mLayout;

		public FillTask() {
			super();
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			try {
				// Replace the ListView with a ProgressBar
				mProgBar = new ProgressBar(VolumeBrowserActivity.this, null,
						android.R.attr.progressBarStyleLarge);

				// Set the layout to fill the screen
				mListView = VolumeBrowserActivity.this.getListView();
				mLayout = (LinearLayout) mListView.getParent();
				mLayout.setGravity(Gravity.CENTER);
				mLayout.setLayoutParams(new FrameLayout.LayoutParams(
						LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

				// Set the ProgressBar in the center of the layout
				LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
						LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				layoutParams.gravity = Gravity.CENTER;
				mProgBar.setLayoutParams(layoutParams);

				// Replace the ListView with the ProgressBar
				mLayout.removeView(mListView);
				mLayout.addView(mProgBar);
				mProgBar.setVisibility(View.VISIBLE);
			} catch (NullPointerException ne) {
				/*
				 * Its possible for mListView.getParent() to return null in some
				 * cases where user spams the Refresh action item, so we just
				 * don't set up a progress bar in that case.
				 */
				mProgBar = null;
			}
		}

		@Override
		protected Void doInBackground(Void... arg0) {
			fill();
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if (mProgBar != null) {
				// Restore the layout parameters
				mLayout.setLayoutParams(new FrameLayout.LayoutParams(
						LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
				mLayout.setGravity(Gravity.TOP);

				// Remove the progress bar and replace it with the list view
				mLayout.removeView(mProgBar);
				mLayout.addView(mListView);
			}
		}
	}

	private class ExportFileTask extends EDAsyncTask<Void, Void, Boolean> {
		// Source file
		private EncFSFile srcFile;

		// Destination file
		private File dstFile;

		public ExportFileTask(ProgressDialog dialog, EncFSFile srcFile,
				File dstFile) {
			super();
			setProgressDialog(dialog);
			this.srcFile = srcFile;
			this.dstFile = dstFile;
		}

		@Override
		protected Boolean doInBackground(Void... args) {
			/*
			 * If the activity gets destroyed/recreated then we need to obtain
			 * srcFile again.
			 */
			if ((srcFile == null) && (mOpenFilePath != null)) {
				try {
					srcFile = mEncfsVolume.getFile(mOpenFilePath);
				} catch (Exception e) {
					Logger.logException(TAG, e);
					exitToVolumeList();
				}
			}

			if (srcFile.isDirectory()) {
				myDialog.setMax(EncFSVolume.countFiles(srcFile));

				// Create destination dir
				if (dstFile.mkdir()) {
					myDialog.incrementProgressBy(1);
				} else {
					mErrDialogText = String.format(
							getString(R.string.error_mkdir_fail),
							dstFile.getAbsolutePath());
					return false;
				}

				return recursiveExport(srcFile, dstFile, this);
			} else {
				// Use size of the file
				myDialog.setMax((int) srcFile.getLength());
				return exportFile(srcFile, dstFile, this);
			}

		}

		// Run after the task is complete
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			if (myDialog.isShowing()) {
				myDialog.dismiss();
			}

			if (!isCancelled()) {
				if (result == true) {
					// Show toast
					Toast.makeText(getApplicationContext(),
							getString(R.string.toast_files_exported),
							Toast.LENGTH_SHORT).show();
				} else {
					showDialog(DIALOG_ERROR);
				}
			}
		}
	}

	private class ViewFileTask extends EDAsyncTask<Void, Void, Boolean> {

		// Source file
		private EncFSFile srcFile;

		// Destination file
		private File dstFile;

		public ViewFileTask(ProgressDialog dialog, EncFSFile srcFile,
				File dstFile) {
			super();
			setProgressDialog(dialog);
			this.srcFile = srcFile;
			this.dstFile = dstFile;

			// Set dialog max in KB
			myDialog.setMax((int) srcFile.getLength());
		}

		@Override
		protected Boolean doInBackground(Void... args) {
			return exportFile(srcFile, dstFile, this);
		}

		// Run after the task is complete
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			if (myDialog.isShowing()) {
				myDialog.dismiss();
			}

			if (!isCancelled()) {
				if (result == true) {
					// Set up a file observer
					mFileObserver = new EDFileObserver(
							dstFile.getAbsolutePath());
					mFileObserver.startWatching();

					mOrigModifiedDate = new Date(dstFile.lastModified());

					String mimeType = FileUtils.getMimeTypeFromFileName(dstFile
							.getName());

					// Launch viewer app
					Intent openIntent = new Intent(Intent.ACTION_VIEW);

					if (mimeType == null) {
						openIntent.setDataAndType(Uri.fromFile(dstFile),
								"application/unknown");
					} else {
						openIntent.setDataAndType(Uri.fromFile(dstFile),
								mimeType);
					}

					try {
						startActivityForResult(openIntent, VIEW_FILE_REQUEST);
					} catch (ActivityNotFoundException e) {
						mErrDialogText = String.format(
								getString(R.string.error_no_viewer_app),
								srcFile.getPath());
						Log.e(TAG, mErrDialogText);
						showDialog(DIALOG_ERROR);
					}
				} else {
					showDialog(DIALOG_ERROR);
				}
			}
		}
	}

	private class SyncFileTask extends EDAsyncTask<Void, Void, Boolean> {

		// Source file
		private File srcFile;

		// Destination file
		private EncFSFile dstFile;

		public SyncFileTask(ProgressDialog dialog, File srcFile,
				EncFSFile dstFile) {
			super();
			setProgressDialog(dialog);
			this.srcFile = srcFile;
			this.dstFile = dstFile;

			// Set dialog max in KB
			myDialog.setMax((int) srcFile.length());
		}

		@Override
		protected Boolean doInBackground(Void... args) {
			/*
			 * If the activity gets destroyed/recreated then we need to obtain
			 * dstFile again.
			 */
			if ((dstFile == null) && (mOpenFilePath != null)) {
				try {
					dstFile = mEncfsVolume.getFile(mOpenFilePath);
				} catch (Exception e) {
					Logger.logException(TAG, e);
					exitToVolumeList();
				}
			}

			return importFile(srcFile, dstFile, this);
		}

		// Run after the task is complete
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			if (myDialog.isShowing()) {
				myDialog.dismiss();
			}

			if (!isCancelled()) {
				if (result == true) {
					// Delete the file
					srcFile.delete();

					// Show toast
					Toast.makeText(
							getApplicationContext(),
							String.format(
									getString(R.string.toast_encrypt_file),
									mOpenFileName), Toast.LENGTH_SHORT).show();

					// Refresh view to get byte size changes
					launchFillTask();
				} else {
					showDialog(DIALOG_ERROR);
				}
			}
		}
	}

	private class ImportFileTask extends EDAsyncTask<Void, Void, Boolean> {

		// Source file
		private File srcFile;

		// Destination file
		private EncFSFile dstFile;

		public ImportFileTask(ProgressDialog dialog, File srcFile) {
			super();
			setProgressDialog(dialog);
			this.srcFile = srcFile;
		}

		@Override
		protected Boolean doInBackground(Void... args) {

			// Create destination encFS file or directory
			try {
				String dstPath = EncFSVolume.combinePath(mCurEncFSDir,
						srcFile.getName());

				if (srcFile.isDirectory()) {
					if (mEncfsVolume.makeDir(dstPath)) {
						dstFile = mEncfsVolume.getFile(dstPath);
					} else {
						mErrDialogText = String.format(
								getString(R.string.error_mkdir_fail), dstPath);
						return false;
					}
				} else {
					dstFile = mEncfsVolume.createFile(dstPath);
				}
			} catch (Exception e) {
				Logger.logException(TAG, e);
				mErrDialogText = e.getMessage();
				return false;
			}

			if (srcFile.isDirectory()) {
				myDialog.setMax(countFiles(srcFile));
				return recursiveImport(srcFile, dstFile, this);
			} else {
				// Use size of the file
				myDialog.setMax((int) srcFile.length());
				return importFile(srcFile, dstFile, this);
			}

		}

		// Run after the task is complete
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			if (myDialog.isShowing()) {
				myDialog.dismiss();
			}

			if (!isCancelled()) {
				if (result == true) {
					// Show toast
					Toast.makeText(getApplicationContext(),
							getString(R.string.toast_files_imported),
							Toast.LENGTH_SHORT).show();
					launchFillTask();
				} else {
					showDialog(DIALOG_ERROR);
					launchFillTask();
				}
			}
		}
	}

	private class PasteFileTask extends EDAsyncTask<Void, Void, Boolean> {

		public PasteFileTask(ProgressDialog dialog) {
			super();
			setProgressDialog(dialog);
		}

		@Override
		protected Boolean doInBackground(Void... args) {

			try {
				boolean result;
				if (mPasteMode == PASTE_OP_CUT) {
					result = mEncfsVolume.movePath(mPasteFile.getPath(),
							EncFSVolume.combinePath(mCurEncFSDir, mPasteFile),
							new ProgressListener(this));
				} else {
					// If destination path exists, use a duplicate name
					String combinedPath = EncFSVolume.combinePath(mCurEncFSDir,
							mPasteFile);
					if (mEncfsVolume.pathExists(combinedPath)) {
						// Bump up a counter until path doesn't exist
						int counter = 0;
						do {
							counter++;
							combinedPath = EncFSVolume.combinePath(
									mCurEncFSDir, "(Copy " + counter + ") "
											+ mPasteFile.getName());
						} while (mEncfsVolume.pathExists(combinedPath));

						result = mEncfsVolume.copyPath(mPasteFile.getPath(),
								combinedPath, new ProgressListener(this));
					} else {
						result = mEncfsVolume.copyPath(mPasteFile.getPath(),
								mCurEncFSDir.getPath(), new ProgressListener(
										this));
					}
				}

				if (result == false) {
					if (mPasteMode == PASTE_OP_CUT) {
						mErrDialogText = String.format(
								getString(R.string.error_move_fail),
								mPasteFile.getName(), mCurEncFSDir.getPath());
					} else {
						mErrDialogText = String.format(
								getString(R.string.error_copy_fail),
								mPasteFile.getName(), mCurEncFSDir.getPath());
					}

					return false;
				}
			} catch (Exception e) {
				if (e.getMessage() == null) {
					mErrDialogText = getString(R.string.paste_fail);
				} else {
					Logger.logException(TAG, e);
					mErrDialogText = e.getMessage();
				}
				return false;
			}

			return true;
		}

		// Run after the task is complete
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			if (myDialog.isShowing()) {
				myDialog.dismiss();
			}

			VolumeBrowserActivity myActivity = (VolumeBrowserActivity) getActivity();
			myActivity.mPasteFile = null;
			myActivity.mPasteMode = PASTE_OP_NONE;

			if (!isCancelled()) {
				if (result == true) {
					launchFillTask();
				} else {
					showDialog(DIALOG_ERROR);
					launchFillTask();
				}
			}
		}
	}

	private class MetadataOpTask extends EDAsyncTask<Void, Void, Boolean> {

		// Valid modes for the task
		public static final int DELETE_FILE = 0;
		public static final int RENAME_FILE = 1;
		public static final int CREATE_DIR = 2;

		// mode for the current task
		private int mode;

		// String argument for the task
		private String strArg;

		public MetadataOpTask(ProgressDialog dialog, int mode, String strArg) {
			super();
			setProgressDialog(dialog);
			this.mode = mode;
			this.strArg = strArg;
		}

		@Override
		protected Boolean doInBackground(Void... args) {
			switch (mode) {
			case DELETE_FILE:
				try {
					// boolean result = mSelectedFile.getFile().delete();
					boolean result = mEncfsVolume.deletePath(mSelectedFile
							.getFile().getPath(), true, new ProgressListener(
							this));

					if (result == false) {
						mErrDialogText = String.format(
								getString(R.string.error_delete_fail),
								mSelectedFile.getName());
						return false;
					}
				} catch (Exception e) {
					Logger.logException(TAG, e);
					mErrDialogText = e.getMessage();
					return false;
				}
				return true;
			case RENAME_FILE:
				try {
					String dstPath = EncFSVolume.combinePath(mCurEncFSDir,
							strArg);

					// Check if the destination path exists
					if (mEncfsVolume.pathExists(dstPath)) {
						mErrDialogText = String.format(
								getString(R.string.error_path_exists), dstPath);
						return false;
					}

					boolean result = mEncfsVolume.movePath(
							EncFSVolume.combinePath(mCurEncFSDir,
									mSelectedFile.getName()), dstPath,
							new ProgressListener(this));

					if (result == false) {
						mErrDialogText = String.format(
								getString(R.string.error_rename_fail),
								mSelectedFile.getName(), strArg);
						return false;
					}
				} catch (Exception e) {
					Logger.logException(TAG, e);
					mErrDialogText = e.getMessage();
					return false;
				}
				return true;
			case CREATE_DIR:
				try {
					boolean result = mEncfsVolume.makeDir(EncFSVolume
							.combinePath(mCurEncFSDir, strArg));

					if (result == false) {
						mErrDialogText = String.format(
								getString(R.string.error_mkdir_fail), strArg);
						return false;
					}
				} catch (Exception e) {
					Logger.logException(TAG, e);
					mErrDialogText = e.getMessage();
					return false;
				}
				return true;
			default:
				return false;
			}
		}

		// Run after the task is complete
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			if (myDialog.isShowing()) {
				myDialog.dismiss();
			}

			if (!isCancelled()) {
				if (result == true) {
					launchFillTask();
				} else {
					showDialog(DIALOG_ERROR);
					launchFillTask();
				}
			}
		}
	}

	private class ActivityRestoreTask extends EDAsyncTask<Void, Void, Boolean> {

		// Saved instance state to restore from
		private Bundle savedInstanceState;

		public ActivityRestoreTask(ProgressDialog dialog,
				Bundle savedInstanceState) {
			super();
			this.savedInstanceState = savedInstanceState;
			setProgressDialog(dialog);
		}

		@Override
		protected Boolean doInBackground(Void... arg0) {
			// Activity restored after being killed
			try {
				// XXX: volume.getFile("/") should return volume.getRootDir()
				if (mSavedCurDirPath.equals(EncFSVolume.ROOT_PATH)) {
					mCurEncFSDir = mEncfsVolume.getRootDir();
				} else {
					mCurEncFSDir = mEncfsVolume.getFile(savedInstanceState
							.getString(SAVED_CUR_DIR_PATH_KEY));
				}
			} catch (Exception e) {
				Logger.logException(TAG, e);
				exitToVolumeList();
			}
			mDirStack = getFileStackForEncFSDir(mCurEncFSDir);

			// Restore paste state
			if (mPasteMode != PASTE_OP_NONE) {
				try {
					mPasteFile = mEncfsVolume.getFile(savedInstanceState
							.getString(SAVED_PASTE_FILE_PATH_KEY));
				} catch (Exception e) {
					Logger.logException(TAG, e);
					exitToVolumeList();
				}
			}

			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			if (myDialog != null && myDialog.isShowing()) {
				myDialog.dismiss();
			}

			launchFillTask();
		}
	}

	private class EDFileObserver extends FileObserver {

		private boolean modified;

		private String myPath;

		public EDFileObserver(String path) {
			super(path);
			myPath = path;
			modified = false;
		}

		public boolean wasModified() {
			return modified;
		}

		public String getPath() {
			return myPath;
		}

		@Override
		public void onEvent(int event, String path) {
			switch (event) {
			case CLOSE_WRITE:
				modified = true;
				break;
			default:
				break;
			}
		}
	}
}