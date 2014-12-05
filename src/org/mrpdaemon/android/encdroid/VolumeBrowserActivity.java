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
import java.util.Locale;
import java.util.Stack;

import org.mrpdaemon.sec.encfs.EncFSFile;
import org.mrpdaemon.sec.encfs.EncFSFileInputStream;
import org.mrpdaemon.sec.encfs.EncFSFileOutputStream;
import org.mrpdaemon.sec.encfs.EncFSVolume;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
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
import android.util.SparseBooleanArray;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class VolumeBrowserActivity extends ListActivity implements
		TaskResultListener {

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
	private final static String SAVED_OPEN_FILE_PATH_KEY = "open_file_path";
	private final static String SAVED_OPEN_FILE_NAME_KEY = "open_file_name";
	private final static String SAVED_IMPORT_FILE_NAME_KEY = "import_file_name";

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

	// Task fragment tag
	private final static String TASK_FRAGMENT_TAG = "TaskFragment";

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

	// List of files that are selected
	private ArrayList<EncFSFile> mSelectedFileList;

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
		public ArrayList<EncFSFile> savedSelectedFileList;
		public Date savedOrigModifiedDate;
	}

	// Saved instance state for current EncFS directory
	private String mSavedCurDirPath = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		mApp = (EDApplication) getApplication();

		// Restore UI elements
		super.onCreate(savedInstanceState);

		setContentView(R.layout.file_chooser);

		mListHeader = new TextView(this);
		mListHeader.setTypeface(null, Typeface.BOLD);
		mListHeader.setTextSize(16);
		this.getListView().addHeaderView(mListHeader);

		mCurFileList = new ArrayList<FileChooserItem>();
		mAdapter = new FileChooserAdapter(this, R.layout.file_chooser_item,
				mCurFileList);
		setListAdapter(mAdapter);

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

		mPasteMode = PASTE_OP_NONE;

		mSelectedFileList = new ArrayList<EncFSFile>();

		this.getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

		this.getListView().setMultiChoiceModeListener(
				new MultiChoiceModeListener() {

					// Map of clicked items
					private SparseBooleanArray clickIndex = new SparseBooleanArray();
					private int numSelected = 0;

					// Save list of EncFSFile for selected items in
					// mSelectedFileList
					private void generateSelectedFileList() {
						mSelectedFileList.clear();

						for (int i = 0; i < numSelected; i++) {
							mSelectedFileList.add(mAdapter.getItem(
									clickIndex.keyAt(i) - 1).getFile());
						}
					}

					@Override
					public boolean onCreateActionMode(ActionMode mode, Menu menu) {
						mode.getMenuInflater().inflate(
								R.menu.volume_browser_context, menu);
						return true;
					}

					@Override
					public boolean onPrepareActionMode(ActionMode mode,
							Menu menu) {
						if (numSelected == 1) {
							// show the rename menu item
							menu.findItem(R.id.volume_browser_menu_rename)
									.setVisible(true);
						} else {
							// hide the rename menu item
							menu.findItem(R.id.volume_browser_menu_rename)
									.setVisible(false);
						}

						// Reset paste mode
						if (mPasteMode != PASTE_OP_NONE) {
							mPasteMode = PASTE_OP_NONE;
							invalidateOptionsMenu();
						}
						return true;
					}

					@Override
					public boolean onActionItemClicked(ActionMode mode,
							MenuItem item) {

						generateSelectedFileList();

						switch (item.getItemId()) {
						case R.id.volume_browser_menu_rename:
							Log.d(TAG, "Rename clicked");
							mode.finish();
							showDialog(DIALOG_FILE_RENAME);

							return true;
						case R.id.volume_browser_menu_delete:
							mode.finish();
							showDialog(DIALOG_FILE_DELETE);
							return true;
						case R.id.volume_browser_menu_cut:
							mPasteMode = PASTE_OP_CUT;

							mode.finish();

							invalidateOptionsMenu();

							// Show toast
							Toast.makeText(
									getApplicationContext(),
									String.format(
											getString(R.string.toast_cut_file),
											getSelectedFileString(
													VolumeBrowserActivity.this,
													mSelectedFileList)),
									Toast.LENGTH_SHORT).show();

							return true;
						case R.id.volume_browser_menu_copy:
							mPasteMode = PASTE_OP_COPY;

							mode.finish();

							invalidateOptionsMenu();

							// Show toast
							Toast.makeText(
									getApplicationContext(),
									String.format(
											getString(R.string.toast_copy_file),
											getSelectedFileString(
													VolumeBrowserActivity.this,
													mSelectedFileList)),
									Toast.LENGTH_SHORT).show();

							return true;
						case R.id.volume_browser_menu_export:
							Intent startFileExport = new Intent(
									VolumeBrowserActivity.this,
									FileChooserActivity.class);

							Bundle exportFileParams = new Bundle();
							exportFileParams.putInt(
									FileChooserActivity.MODE_KEY,
									FileChooserActivity.EXPORT_FILE_MODE);
							exportFileParams.putString(
									FileChooserActivity.EXPORT_FILE_KEY,
									getSelectedFileString(
											VolumeBrowserActivity.this,
											mSelectedFileList));
							startFileExport.putExtras(exportFileParams);

							mode.finish();

							startActivityForResult(startFileExport,
									EXPORT_FILE_REQUEST);
							return true;
						default:
							return false;
						}
					}

					@Override
					public void onDestroyActionMode(ActionMode mode) {
						Log.d(TAG, "Action mode destroyed");
						numSelected = 0;
						clickIndex.clear();
					}

					@Override
					public void onItemCheckedStateChanged(ActionMode mode,
							int position, long id, boolean checked) {
						Log.d(TAG, "click at position: " + position);

						if (position != 0) {
							if (clickIndex.get(position)) {
								numSelected--;
							} else {
								numSelected++;
							}

							clickIndex.put(position, !clickIndex.get(position));

							if (numSelected > 0) {
								mode.setTitle(String
										.format(getString(R.string.multi_select_num_selected),
												numSelected));
							}

							mode.invalidate();
						} else {
							if (numSelected == 0) {
								/*
								 * We don't want the header item to trigger the
								 * multi selection mode when long pressed.
								 */
								mode.finish();
							}
						}
					}
				});

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
			mSelectedFileList = restoreContext.savedSelectedFileList;
			mOrigModifiedDate = restoreContext.savedOrigModifiedDate;

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

			// Restore import file state
			mImportFileName = savedInstanceState
					.getString(SAVED_IMPORT_FILE_NAME_KEY);

			new ActivityRestoreTask(savedInstanceState).execute();
		}

		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setIcon(mVolume.getFileSystem().getIconResId());

		// Set title text
		setTitle(mVolume.getName());
	}

	// Retain the Volume object through activity being killed
	@Override
	public Object onRetainNonConfigurationInstance() {
		ActivityRestoreContext restoreContext = new ActivityRestoreContext();
		restoreContext.savedVolume = mVolume;
		restoreContext.savedObserver = mFileObserver;
		restoreContext.savedSelectedFileList = mSelectedFileList;
		restoreContext.savedOrigModifiedDate = mOrigModifiedDate;

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
			TaskFragment pasteTask = new PasteTaskFragment(
					VolumeBrowserActivity.this, mPasteMode, mSelectedFileList,
					mEncfsVolume, mCurEncFSDir);
			addTaskFragment(pasteTask);
			pasteTask.startTask();

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

	// Return a string that best represents the selected file list
	private static String getSelectedFileString(Object caller,
			ArrayList<EncFSFile> selectedFileList) {
		int size = selectedFileList.size();

		if (size == 0) {
			return "";
		} else if (size == 1) {
			return selectedFileList.get(0).getName();
		} else {
			if (caller instanceof Activity) {
				return String.format(((Activity) caller)
						.getString(R.string.multi_select_plural_files), size);
			} else if (caller instanceof Fragment) {
				return String.format(((Fragment) caller)
						.getString(R.string.multi_select_plural_files), size);
			} else {
				throw new ClassCastException(
						"called from neither an Activity nor Fragment");
			}
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
					getSelectedFileString(this, mSelectedFileList)));
			break;
		case DIALOG_FILE_RENAME:
		case DIALOG_CREATE_FOLDER:
			input = (EditText) dialog.findViewById(R.id.dialog_edit_text);
			if (input != null) {
				if (id == DIALOG_FILE_RENAME) {
					input.setText(getSelectedFileString(this, mSelectedFileList));
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

			input = (EditText) inflater.inflate(R.layout.dialog_edit,
					(ViewGroup) findViewById(R.layout.file_chooser));

			alertBuilder.setTitle(getString(R.string.frename_dialog_title_str));
			alertBuilder.setView(input);
			alertBuilder.setPositiveButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						// Rename the file
						public void onClick(DialogInterface dialog,
								int whichButton) {
							Editable value = input.getText();
							// Launch task to rename the file
							TaskFragment renameTask = new MetadataOpTaskFragment(
									VolumeBrowserActivity.this,
									MetadataOpTaskFragment.RENAME_FILE, value
											.toString(), mSelectedFileList,
									mEncfsVolume, mCurEncFSDir);
							addTaskFragment(renameTask);
							renameTask.startTask();
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

			input = (EditText) inflater.inflate(R.layout.dialog_edit,
					(ViewGroup) findViewById(R.layout.file_chooser));

			alertBuilder.setTitle(getString(R.string.mkdir_dialog_input_str));
			alertBuilder.setView(input);
			alertBuilder.setPositiveButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						// Create the folder
						public void onClick(DialogInterface dialog,
								int whichButton) {
							Editable value = input.getText();
							// Launch task to create directory
							TaskFragment mkdirTask = new MetadataOpTaskFragment(
									VolumeBrowserActivity.this,
									MetadataOpTaskFragment.CREATE_DIR, value
											.toString(), null, mEncfsVolume,
									mCurEncFSDir);
							addTaskFragment(mkdirTask);
							mkdirTask.startTask();
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
					getSelectedFileString(this, mSelectedFileList)));
			alertBuilder.setCancelable(false);
			alertBuilder.setPositiveButton(getString(R.string.btn_yes_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							// Launch task to delete the file
							TaskFragment deleteTask = new MetadataOpTaskFragment(
									VolumeBrowserActivity.this,
									MetadataOpTaskFragment.DELETE_FILE, null,
									mSelectedFileList, mEncfsVolume,
									mCurEncFSDir);
							addTaskFragment(deleteTask);
							deleteTask.startTask();
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
			TaskFragment decryptTask = new DecryptTaskFragment(this, mOpenFile,
					dstFile);
			addTaskFragment(decryptTask);
			decryptTask.startTask();
		}
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
			/*
			 * Seems like it's possible to come here with mFileObserver == null,
			 * possibly after spending a long time in a viewer app and the
			 * Encdroid instance getting killed to reclaim memory. We'll just
			 * ignore this activity result in that case.
			 */
			if (mFileObserver != null) {
				// Don't need to watch any more
				mFileObserver.stopWatching();

				File dstFile = new File(mFileObserver.getPath());

				// If the file was modified we need to sync it back
				Date newDate = new Date(dstFile.lastModified());
				if (mFileObserver.wasModified()
						|| (newDate.compareTo(mOrigModifiedDate) > 0)) {
					// Sync file contents
					try {
						TaskFragment syncTask = new SyncTaskFragment(this,
								dstFile, mOpenFile);
						addTaskFragment(syncTask);
						syncTask.startTask();
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
			}

			break;
		case PICK_FILE_REQUEST:
			if (resultCode == Activity.RESULT_OK) {
				Log.d(TAG, "Received list of import files");

				// Launch async task to complete importing
				TaskFragment importTask = new ImportTaskFragment(
						VolumeBrowserActivity.this, data.getExtras()
								.getStringArrayList(
										FileChooserActivity.RESULT_KEY),
						mEncfsVolume, mCurEncFSDir);
				addTaskFragment(importTask);
				importTask.startTask();
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

				// Launch async task to export the files
				TaskFragment exportTask = new ExportTaskFragment(this,
						mSelectedFileList, new File(exportPath));
				addTaskFragment(exportTask);
				exportTask.startTask();
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

		// Notify data set change from UI thread
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mAdapter.notifyDataSetChanged();
			}
		});

	}

	// Show a progress spinner and launch the fill task
	private void launchFillTask() {
		mFillTask = new FillTask();
		mFillTask.execute();
	}

	// Returns the best name for a multi select list
	private static String getBestNameForMultiSelectList(Fragment fragment,
			ArrayList<String> list) {
		if (list.size() == 1) {
			return list.get(0);
		}

		return String.format(
				fragment.getString(R.string.multi_select_plural_files),
				list.size());
	}

	// Add a task fragment to the Activity state
	private void addTaskFragment(TaskFragment fragment) {
		FragmentManager fragmentManager = getFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager
				.beginTransaction();
		fragmentTransaction.add(fragment, TASK_FRAGMENT_TAG);
		fragmentTransaction.commit();
	}

	// Remove the task fragment from the Activity state
	private void removeTaskFragment() {
		FragmentManager fragmentManager = getFragmentManager();
		TaskFragment taskFragment = (TaskFragment) fragmentManager
				.findFragmentByTag(TASK_FRAGMENT_TAG);
		if (taskFragment != null) {
			fragmentManager.beginTransaction().remove(taskFragment).commit();
		}
	}

	// Remove the task fragment from the Activity state

	private boolean copyStreams(InputStream is, OutputStream os,
			EDAsyncTask<?, ?, ?> task) {
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
							task.getProgress().incCurrentBytes(8192);
							task.updateProgress();
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
			task.getFragment().returnError(e.getMessage());
			return false;
		}

		return true;
	}

	private boolean exportFile(EncFSFile srcFile, File dstFile,
			EDAsyncTask<?, ?, ?> task) {
		EncFSFileInputStream efis = null;

		task.getProgress().setTotalBytes((int) srcFile.getLength());
		task.updateProgress();

		try {
			efis = new EncFSFileInputStream(srcFile);
		} catch (Exception e) {
			Logger.logException(TAG, e);
			task.getFragment().returnError(e.getMessage());
			return false;
		}

		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(dstFile);
		} catch (FileNotFoundException e) {
			Logger.logException(TAG, e);
			task.getFragment().returnError(e.getMessage());
			try {
				efis.close();
			} catch (IOException ioe) {
				task.getFragment().returnError(ioe.getMessage());
			}
			return false;
		}

		return copyStreams(efis, fos, task);
	}

	// Export all files/dirs under the EncFS dir to the given dir
	private boolean recursiveExport(EncFSFile srcDir, File dstDir,
			EDAsyncTask<?, ?, ?> task) {
		try {
			int currentFileIdx = 0;
			int totalFiles = EncFSVolume.countFiles(srcDir);

			for (EncFSFile file : srcDir.listFiles()) {
				task.getProgress().setTotalFiles(totalFiles);
				task.getProgress().setCurrentFileIdx(currentFileIdx++);

				File dstFile = new File(dstDir, file.getName());

				task.getProgress().setCurrentFileName(file.getPath());
				task.updateProgress();

				if (file.isDirectory()) { // Directory
					if (dstFile.mkdir()) {
						// Export all files/folders under this dir
						if (recursiveExport(file, dstFile, task) == false) {
							return false;
						}
					} else {
						task.getFragment().returnError(
								String.format(
										getString(R.string.error_mkdir_fail),
										dstFile.getAbsolutePath()));
						return false;
					}
				} else { // Export an individual file
					if (exportFile(file, dstFile, task) == false) {
						return false;
					}
				}
			}
		} catch (Exception e) {
			Logger.logException(TAG, e);
			task.getFragment().returnError(e.getMessage());
			return false;
		}
		return true;
	}

	private boolean importFile(File srcFile, EncFSFile dstFile,
			EDAsyncTask<?, ?, ?> task) {
		task.getProgress().setTotalBytes((int) srcFile.length());
		task.updateProgress();

		FileInputStream fis = null;
		try {
			fis = new FileInputStream(srcFile);
		} catch (FileNotFoundException e) {
			Logger.logException(TAG, e);
			task.getFragment().returnError(e.getMessage());
			return false;
		}

		EncFSFileOutputStream efos = null;
		try {
			efos = new EncFSFileOutputStream(dstFile, srcFile.length());
		} catch (Exception e) {
			Logger.logException(TAG, e);
			task.getFragment().returnError(e.getMessage());
			try {
				fis.close();
			} catch (IOException ioe) {
				task.getFragment().returnError(ioe.getMessage());
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
			EDAsyncTask<?, ?, ?> task) {

		int currentFileIdx = 0;
		int totalFiles = countFiles(srcDir);

		for (File file : srcDir.listFiles()) {
			task.getProgress().setTotalFiles(totalFiles);
			task.getProgress().setCurrentFileIdx(currentFileIdx++);

			String dstPath = EncFSVolume.combinePath(dstDir, file.getName());

			task.getProgress().setCurrentFileName(file.getPath());
			task.updateProgress();

			try {
				if (file.isDirectory()) { // Directory
					if (mEncfsVolume.makeDir(dstPath)) {
						// Import all files/folders under this dir
						if (recursiveImport(file,
								mEncfsVolume.getFile(dstPath), task) == false) {
							return false;
						}
					} else {
						task.getFragment().returnError(
								String.format(
										getString(R.string.error_mkdir_fail),
										dstPath));
						return false;
					}
				} else { // Import an individual file
					if (importFile(file, mEncfsVolume.createFile(dstPath), task) == false) {
						return false;
					}
				}
			} catch (Exception e) {
				Logger.logException(TAG, e);
				task.getFragment().returnError(e.getMessage());
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
						LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

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
						LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
				mLayout.setGravity(Gravity.TOP);

				// Remove the progress bar and replace it with the list view
				mLayout.removeView(mProgBar);
				mLayout.addView(mListView);
			}
		}
	}

	private class ExportTaskFragment extends TaskFragment {

		// List of selected files for multi file export
		private ArrayList<EncFSFile> mSelectedFileList;

		// Destination path to export to
		private File mDstPath;

		public ExportTaskFragment(Activity activity,
				ArrayList<EncFSFile> selectedFileList, File dstPath) {
			super(activity);
			this.mSelectedFileList = selectedFileList;
			this.mDstPath = dstPath;
		}

		@Override
		protected int getTaskId() {
			return ASYNC_TASK_EXPORT;
		}

		@Override
		protected EDAsyncTask<Void, Void, Boolean> createTask() {
			return new ExportFileTask(this);
		}

		private class ExportFileTask extends EDAsyncTask<Void, Void, Boolean> {

			public ExportFileTask(TaskFragment fragment) {
				super(fragment);
			}

			@Override
			protected ProgressDialog createProgressDialog(Activity activity) {
				mProgressDialogTitle = String
						.format(activity
								.getString(R.string.export_dialog_title_str),
								getSelectedFileString(mTaskFragment,
										mSelectedFileList));

				if (mSelectedFileList.size() > 1) {
					mProgressDialogMultiJob = true;
				}

				mProgressDialogMsgResId = R.string.export_dialog_msg_str;

				return super.createProgressDialog(activity);
			}

			@Override
			protected Boolean doInBackground(Void... args) {

				mTaskProgress.setNumJobs(mSelectedFileList.size());

				for (EncFSFile srcFile : mSelectedFileList) {
					mTaskProgress.incCurrentJob();
					mTaskProgress.setCurrentBytes(0);
					mTaskProgress.setCurrentFileIdx(1);
					mTaskProgress.setCurrentFileName(srcFile.getName());
					updateProgress();

					File dstFile = new File(mDstPath, srcFile.getName());

					if (dstFile.exists()) {
						// Error dialog
						mTaskFragment.returnError(String.format(mTaskFragment
								.getStringSafe(R.string.error_file_exists),
								dstFile.getName()));
						return false;
					}

					if (srcFile.isDirectory()) {
						mProgressDialogMultiFile = true;
						// Create destination dir
						if (!dstFile.mkdir()) {
							mTaskFragment
									.returnError(String.format(
											mTaskFragment
													.getStringSafe(R.string.error_mkdir_fail),
											dstFile.getAbsolutePath()));
							return false;
						}

						if (recursiveExport(srcFile, dstFile, this) != true) {
							return false;
						}
					} else {
						mProgressDialogMultiFile = false;
						if (exportFile(srcFile, dstFile, this) != true) {
							return false;
						}
					}
				}

				return true;
			}

			// Run after the task is complete
			@Override
			protected void onPostExecute(Boolean result) {
				super.onPostExecute(result);

				if (!isCancelled()) {
					if (result) {
						mTaskFragment.returnResult(result);
					}
				}
			}
		}
	}

	private class DecryptTaskResult {
		public EncFSFile srcFile;
		public File dstFile;

		public DecryptTaskResult(EncFSFile srcFile, File dstFile) {
			this.srcFile = srcFile;
			this.dstFile = dstFile;
		}
	}

	private class DecryptTaskFragment extends TaskFragment {

		// Source file
		private EncFSFile mSrcFile;

		// Destination file
		private File mDstFile;

		public DecryptTaskFragment(Activity activity, EncFSFile srcFile,
				File dstFile) {
			super(activity);
			this.mSrcFile = srcFile;
			this.mDstFile = dstFile;
		}

		@Override
		protected int getTaskId() {
			return ASYNC_TASK_DECRYPT;
		}

		@Override
		protected EDAsyncTask<Void, Void, DecryptTaskResult> createTask() {
			return new DecryptFileTask(this);
		}

		private class DecryptFileTask extends
				EDAsyncTask<Void, Void, DecryptTaskResult> {

			public DecryptFileTask(TaskFragment fragment) {
				super(fragment);
			}

			@Override
			protected ProgressDialog createProgressDialog(Activity activity) {
				mProgressDialogTitle = String.format(
						activity.getString(R.string.decrypt_dialog_title_str),
						mDstFile.getName());
				return super.createProgressDialog(activity);
			}

			@Override
			protected DecryptTaskResult doInBackground(Void... args) {
				boolean result = exportFile(mSrcFile, mDstFile, this);
				if (result) {
					return new DecryptTaskResult(mSrcFile, mDstFile);
				} else {
					return null;
				}
			}

			// Run after the task is complete
			@Override
			protected void onPostExecute(DecryptTaskResult result) {
				super.onPostExecute(result);

				if (!isCancelled()) {
					if (result != null) {
						mTaskFragment.returnResult(result);
					}
				}
			}
		}
	}

	private class SyncTaskFragment extends TaskFragment {

		// Source file
		private File mSrcFile;

		// Destination file
		private EncFSFile mDstFile;

		public SyncTaskFragment(Activity activity, File srcFile,
				EncFSFile dstFile) {
			super(activity);
			this.mSrcFile = srcFile;
			this.mDstFile = dstFile;
		}

		@Override
		protected int getTaskId() {
			return ASYNC_TASK_SYNC;
		}

		@Override
		protected EDAsyncTask<Void, Void, Boolean> createTask() {
			return new SyncFileTask(this);
		}

		private class SyncFileTask extends EDAsyncTask<Void, Void, Boolean> {

			public SyncFileTask(TaskFragment fragment) {
				super(fragment);
			}

			@Override
			protected ProgressDialog createProgressDialog(Activity activity) {
				mProgressDialogTitle = String.format(
						activity.getString(R.string.encrypt_dialog_title_str),
						mDstFile.getName());
				return super.createProgressDialog(activity);
			}

			@Override
			protected Boolean doInBackground(Void... args) {
				return importFile(mSrcFile, mDstFile, this);
			}

			// Run after the task is complete
			@Override
			protected void onPostExecute(Boolean result) {
				super.onPostExecute(result);

				if (!isCancelled()) {
					if (result) {
						// Delete the file
						mSrcFile.delete();

						mTaskFragment.returnResult(result);
					}
				}
			}
		}
	}

	private class ImportTaskFragment extends TaskFragment {

		// Source file list
		private ArrayList<String> mSrcFileList;

		// EncFS volume being operated on
		private EncFSVolume mEncfsVolume;

		// Current directory
		private EncFSFile mCurEncFSDir;

		public ImportTaskFragment(Activity activity,
				ArrayList<String> srcFileList, EncFSVolume encfsVolume,
				EncFSFile curEncFSDir) {
			super(activity);
			this.mSrcFileList = srcFileList;
			this.mEncfsVolume = encfsVolume;
			this.mCurEncFSDir = curEncFSDir;
		}

		@Override
		protected int getTaskId() {
			return ASYNC_TASK_IMPORT;
		}

		@Override
		protected EDAsyncTask<Void, Void, Boolean> createTask() {
			return new ImportFileTask(this);
		}

		private class ImportFileTask extends EDAsyncTask<Void, Void, Boolean> {

			public ImportFileTask(TaskFragment fragment) {
				super(fragment);
			}

			@Override
			protected ProgressDialog createProgressDialog(Activity activity) {
				mProgressDialogTitle = String.format(
						activity.getString(R.string.import_dialog_title_str),
						getBestNameForMultiSelectList(mTaskFragment,
								mSrcFileList));
				mProgressDialogMsgResId = R.string.import_dialog_msg_str;

				if (mSelectedFileList.size() > 1) {
					mProgressDialogMultiJob = true;
				}

				return super.createProgressDialog(activity);
			}

			@Override
			protected Boolean doInBackground(Void... args) {

				EncFSFile dstFile;

				mTaskProgress.setNumJobs(mSrcFileList.size());

				for (String srcFilePath : mSrcFileList) {
					mTaskProgress.incCurrentJob();
					mTaskProgress.setCurrentBytes(0);
					mTaskProgress.setCurrentFileIdx(1);
					mTaskProgress.setCurrentFileName(srcFilePath);
					updateProgress();

					String importPath = new File(
							Environment.getExternalStorageDirectory(),
							srcFilePath).getAbsolutePath();
					Log.d(TAG, "Importing file: " + importPath);

					File srcFile = new File(importPath);

					// Create destination encFS file or directory
					try {
						String dstPath = EncFSVolume.combinePath(mCurEncFSDir,
								srcFile.getName());

						if (srcFile.isDirectory()) {
							if (mEncfsVolume.makeDir(dstPath)) {
								dstFile = mEncfsVolume.getFile(dstPath);
							} else {
								mTaskFragment
										.returnError(String.format(
												mTaskFragment
														.getStringSafe(R.string.error_mkdir_fail),
												dstPath));
								return false;
							}
						} else {
							dstFile = mEncfsVolume.createFile(dstPath);
						}
					} catch (Exception e) {
						Logger.logException(TAG, e);
						mTaskFragment.returnError(e.getMessage());
						return false;
					}

					if (srcFile.isDirectory()) {
						mProgressDialogMultiFile = true;
						if (recursiveImport(srcFile, dstFile, this) != true) {
							return false;
						}
					} else {
						mProgressDialogMultiFile = false;
						if (importFile(srcFile, dstFile, this) != true) {
							return false;
						}
					}
				}

				return true;

			}

			// Run after the task is complete
			@Override
			protected void onPostExecute(Boolean result) {
				super.onPostExecute(result);

				if (!isCancelled()) {
					if (result) {
						mTaskFragment.returnResult(result);
					}
				}
			}
		}
	}

	private class PasteTaskFragment extends TaskFragment {

		// Paste mode
		private int mMode;

		// List of selected files for multi file paste
		private ArrayList<EncFSFile> mSelectedFileList;

		// EncFS volume being operated on
		private EncFSVolume mEncfsVolume;

		// Current directory
		private EncFSFile mCurEncFSDir;

		public PasteTaskFragment(Activity activity, int mode,
				ArrayList<EncFSFile> selectedFileList, EncFSVolume encfsVolume,
				EncFSFile curEncFSDir) {
			super(activity);
			this.mMode = mode;
			this.mSelectedFileList = selectedFileList;
			this.mEncfsVolume = encfsVolume;
			this.mCurEncFSDir = curEncFSDir;
		}

		@Override
		protected int getTaskId() {
			return ASYNC_TASK_PASTE;
		}

		@Override
		protected EDAsyncTask<Void, Void, Boolean> createTask() {
			return new PasteFileTask(this);
		}

		private class PasteFileTask extends EDAsyncTask<Void, Void, Boolean> {

			public PasteFileTask(TaskFragment fragment) {
				super(fragment);
			}

			@Override
			protected ProgressDialog createProgressDialog(Activity activity) {
				switch (mMode) {
				case PASTE_OP_COPY:
					mProgressDialogTitle = activity
							.getString(R.string.copy_dialog_title_str);
					mProgressDialogMsgResId = R.string.copy_dialog_msg_str;
					break;
				case PASTE_OP_CUT:
					mProgressDialogTitle = activity
							.getString(R.string.cut_dialog_title_str);
					mProgressDialogMsgResId = R.string.cut_dialog_msg_str;
					break;
				}

				if (mSelectedFileList.size() > 1) {
					mProgressDialogMultiJob = true;
				}

				mProgressDialogMultiFile = true;

				return super.createProgressDialog(activity);
			}

			@Override
			protected Boolean doInBackground(Void... args) {

				try {
					boolean result;

					mTaskProgress.setNumJobs(mSelectedFileList.size());

					for (EncFSFile curFile : mSelectedFileList) {
						mTaskProgress.incCurrentJob();
						mTaskProgress.setCurrentFileIdx(1);
						mTaskProgress.setCurrentFileName(curFile.getName());
						updateProgress();

						if (mPasteMode == PASTE_OP_CUT) {
							result = mEncfsVolume.movePath(curFile.getPath(),
									EncFSVolume.combinePath(mCurEncFSDir,
											curFile),
									new ProgressListener(this));
						} else {
							// If destination path exists, use a duplicate name
							String combinedPath = EncFSVolume.combinePath(
									mCurEncFSDir, curFile);
							if (mEncfsVolume.pathExists(combinedPath)) {
								// Bump up a counter until path doesn't exist
								int counter = 0;
								do {
									counter++;
									combinedPath = EncFSVolume.combinePath(
											mCurEncFSDir, "(Copy " + counter
													+ ") " + curFile.getName());
								} while (mEncfsVolume.pathExists(combinedPath));

								result = mEncfsVolume.copyPath(
										curFile.getPath(), combinedPath,
										new ProgressListener(this));
							} else {
								result = mEncfsVolume.copyPath(
										curFile.getPath(),
										mCurEncFSDir.getPath(),
										new ProgressListener(this));
							}
						}

						if (result == false) {
							if (mPasteMode == PASTE_OP_CUT) {
								mTaskFragment
										.returnError(String.format(
												mTaskFragment
														.getStringSafe(R.string.error_move_fail),
												curFile.getName(), mCurEncFSDir
														.getPath()));
							} else {
								mTaskFragment
										.returnError(String.format(
												mTaskFragment
														.getStringSafe(R.string.error_copy_fail),
												curFile.getName(), mCurEncFSDir
														.getPath()));
							}

							return false;
						}
					}
				} catch (Exception e) {
					if (e.getMessage() == null) {
						mTaskFragment.returnError(mTaskFragment
								.getStringSafe(R.string.paste_fail));
					} else {
						Logger.logException(TAG, e);
						mTaskFragment.returnError(e.getMessage());
					}
					return false;
				}

				return true;
			}

			// Run after the task is complete
			@Override
			protected void onPostExecute(Boolean result) {
				super.onPostExecute(result);

				if (!isCancelled()) {
					if (result) {
						mTaskFragment.returnResult(result);
					}
				}
			}
		}
	}

	private class MetadataOpTaskFragment extends TaskFragment {

		// Valid modes for the task
		public static final int DELETE_FILE = 0;
		public static final int RENAME_FILE = 1;
		public static final int CREATE_DIR = 2;

		// Mode we're operating in
		private int mMode;

		// Path for the metadata operation
		private String mPath;

		// List of selected files for multi file delete
		private ArrayList<EncFSFile> mSelectedFileList;

		// EncFS volume being operated on
		private EncFSVolume mEncfsVolume;

		// Current directory
		private EncFSFile mCurEncFSDir;

		public MetadataOpTaskFragment(Activity activity, int mode, String path,
				ArrayList<EncFSFile> selectedFileList, EncFSVolume encfsVolume,
				EncFSFile curEncFSDir) {
			super(activity);
			this.mMode = mode;
			this.mPath = path;
			this.mSelectedFileList = selectedFileList;
			this.mEncfsVolume = encfsVolume;
			this.mCurEncFSDir = curEncFSDir;
		}

		@Override
		protected int getTaskId() {
			switch (mMode) {
			case DELETE_FILE:
				return ASYNC_TASK_DELETE;
			case RENAME_FILE:
				return ASYNC_TASK_RENAME;
			case CREATE_DIR:
				return ASYNC_TASK_CREATE_DIR;
			default:
				return -1;
			}
		}

		@Override
		protected EDAsyncTask<Void, Void, Boolean> createTask() {
			return new MetadataOpTask(this);
		}

		private class MetadataOpTask extends EDAsyncTask<Void, Void, Boolean> {

			public MetadataOpTask(TaskFragment fragment) {
				super(fragment);
			}

			@Override
			protected ProgressDialog createProgressDialog(Activity activity) {
				switch (mMode) {
				case DELETE_FILE:
					mProgressDialogTitle = String
							.format(activity
									.getString(R.string.delete_dialog_title_str),
									getSelectedFileString(mTaskFragment,
											mSelectedFileList));
					mProgressDialogMsgResId = R.string.delete_dialog_msg_str;

					if (mSelectedFileList.size() > 1) {
						mProgressDialogMultiJob = true;
					}

					mProgressDialogMultiFile = true;
					break;
				case RENAME_FILE:
					mProgressDialogTitle = String.format(activity
							.getString(R.string.rename_dialog_title_str),
							mSelectedFileList.get(0).getName(), mPath);

					mProgressDialogMultiFile = true;
					break;
				case CREATE_DIR:
					mProgressDialogTitle = String
							.format(activity
									.getString(R.string.mkdir_dialog_title_str),
									mPath);

					mProgressDialogSpinnerOnly = true;
					break;
				}
				return super.createProgressDialog(activity);
			}

			@Override
			protected Boolean doInBackground(Void... args) {
				switch (mMode) {
				case DELETE_FILE:
					try {
						mTaskProgress.setNumJobs(mSelectedFileList.size());

						for (EncFSFile curFile : mSelectedFileList) {
							mTaskProgress.incCurrentJob();
							mTaskProgress.setCurrentFileIdx(1);
							mTaskProgress.setCurrentFileName(curFile.getName());
							updateProgress();

							boolean result = mEncfsVolume.deletePath(
									curFile.getPath(), true,
									new ProgressListener(this));

							if (result == false) {
								mTaskFragment
										.returnError(String.format(
												mTaskFragment
														.getStringSafe(R.string.error_delete_fail),
												curFile.getName()));
								return false;
							}
						}
					} catch (Exception e) {
						Logger.logException(TAG, e);
						mTaskFragment.returnError(e.getMessage());
						return false;
					}

					return true;
				case RENAME_FILE:
					try {
						String dstPath = EncFSVolume.combinePath(mCurEncFSDir,
								mPath);

						// Check if the destination path exists
						if (mEncfsVolume.pathExists(dstPath)) {
							mTaskFragment
									.returnError(String.format(
											mTaskFragment
													.getStringSafe(R.string.error_path_exists),
											dstPath));
							return false;
						}

						boolean result = mEncfsVolume.movePath(EncFSVolume
								.combinePath(mCurEncFSDir, mSelectedFileList
										.get(0).getName()), dstPath,
								new ProgressListener(this));

						if (result == false) {
							mTaskFragment
									.returnError(String.format(
											mTaskFragment
													.getStringSafe(R.string.error_rename_fail),
											mSelectedFileList.get(0).getName(),
											mPath));
							return false;
						}
					} catch (Exception e) {
						Logger.logException(TAG, e);
						mTaskFragment.returnError(e.getMessage());
						return false;
					}
					return true;
				case CREATE_DIR:
					try {
						boolean result = mEncfsVolume.makeDir(EncFSVolume
								.combinePath(mCurEncFSDir, mPath));

						if (result == false) {
							mTaskFragment
									.returnError(String.format(
											mTaskFragment
													.getStringSafe(R.string.error_mkdir_fail),
											mPath));
							return false;
						}
					} catch (Exception e) {
						Logger.logException(TAG, e);
						mTaskFragment.returnError(e.getMessage());
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

				if (!isCancelled()) {
					if (result) {
						mTaskFragment.returnResult(result);
					}
				}
			}
		}
	}

	private class ActivityRestoreTask extends AsyncTask<Void, Void, Void> {

		// Saved instance state to restore from
		private Bundle savedInstanceState;

		public ActivityRestoreTask(Bundle savedInstanceState) {
			super();
			this.savedInstanceState = savedInstanceState;
		}

		@Override
		protected Void doInBackground(Void... arg0) {
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

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

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

	// Method called from a TaskFragment to return task result
	@Override
	public void onTaskResult(int taskId, Object result) {

		removeTaskFragment();

		switch (taskId) {
		case ASYNC_TASK_EXPORT:
			// Show toast
			Toast.makeText(getApplicationContext(),
					getString(R.string.toast_files_exported),
					Toast.LENGTH_SHORT).show();
			break;
		case ASYNC_TASK_DECRYPT:
			if (result != null) {
				DecryptTaskResult dtr = (DecryptTaskResult) result;
				// Set up a file observer
				mFileObserver = new EDFileObserver(
						dtr.dstFile.getAbsolutePath());
				mFileObserver.startWatching();

				mOrigModifiedDate = new Date(dtr.dstFile.lastModified());

				String mimeType = FileUtils.getMimeTypeFromFileName(dtr.dstFile
						.getName().toLowerCase(Locale.getDefault()));

				// Launch viewer app
				Intent openIntent = new Intent(Intent.ACTION_VIEW);

				if (mimeType == null) {
					openIntent.setDataAndType(Uri.fromFile(dtr.dstFile),
							"application/unknown");
				} else {
					openIntent.setDataAndType(Uri.fromFile(dtr.dstFile),
							mimeType);
				}

				try {
					startActivityForResult(openIntent, VIEW_FILE_REQUEST);
				} catch (ActivityNotFoundException e) {
					mErrDialogText = String.format(
							getString(R.string.error_no_viewer_app),
							dtr.srcFile.getPath());
					Log.e(TAG, mErrDialogText);
					showDialog(DIALOG_ERROR);
				}
			}
			break;
		case ASYNC_TASK_SYNC:
			// Show toast
			Toast.makeText(
					getApplicationContext(),
					String.format(getString(R.string.toast_encrypt_file),
							mOpenFileName), Toast.LENGTH_SHORT).show();

			// Refresh view to get byte size changes
			launchFillTask();
			break;
		case ASYNC_TASK_IMPORT:
			// Show toast
			Toast.makeText(getApplicationContext(),
					getString(R.string.toast_files_imported),
					Toast.LENGTH_SHORT).show();
			launchFillTask();
			break;
		case ASYNC_TASK_PASTE:
			mPasteMode = PASTE_OP_NONE;
			invalidateOptionsMenu();
			launchFillTask();
			break;
		case ASYNC_TASK_CREATE_DIR:
		case ASYNC_TASK_RENAME:
		case ASYNC_TASK_DELETE:
			launchFillTask();
			break;
		}
	}

	// Method called from a TaskFragment to report task error
	@Override
	public void onTaskError(int taskId, String errorText) {
		mErrDialogText = errorText;
		removeTaskFragment();
		// Show error dialog from the UI thread
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				showDialog(DIALOG_ERROR);
				launchFillTask();
			}
		});
	}
}