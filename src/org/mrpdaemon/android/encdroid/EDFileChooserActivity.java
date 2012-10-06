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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mrpdaemon.sec.encfs.EncFSFileInfo;
import org.mrpdaemon.sec.encfs.EncFSFileProvider;
import org.mrpdaemon.sec.encfs.EncFSLocalFileProvider;
import org.mrpdaemon.sec.encfs.EncFSVolume;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;

public class EDFileChooserActivity extends ListActivity {
	// Parameter key for activity mode
	public final static String MODE_KEY = "mode";

	// Supported modes
	public final static int VOLUME_PICKER_MODE = 0;
	public final static int FILE_PICKER_MODE = 1;
	public final static int EXPORT_FILE_MODE = 2;
	public final static int CREATE_VOLUME_MODE = 3;

	// Parameter key for FS type
	public final static String FS_TYPE_KEY = "fs_type";

	// Parameter key for export file name
	public final static String EXPORT_FILE_KEY = "export_file";

	// Valid FS types
	public final static int LOCAL_FS = 0;
	public final static int DROPBOX_FS = 1;

	// Result key for the path returned by this activity
	public final static String RESULT_KEY = "result_path";

	// Name of the SD card directory for copying files into
	public final static String ENCDROID_SD_DIR_NAME = "Encdroid";

	// Logger tag
	private static final String TAG = "EDFileChooserActivity";

	// Dialog ID's
	private final static int DIALOG_AUTO_IMPORT = 0;

	// Adapter for the list
	private EDFileChooserAdapter mAdapter = null;

	// List that is currently being displayed
	private List<EDFileChooserItem> mCurFileList;

	// What mode we're running in
	private int mMode;

	// Current directory
	private String mCurrentDir;

	// The underlying FS type for this chooser
	private int mFsType;

	// File provider
	private EncFSFileProvider mFileProvider;

	// Application object
	private EDApplication mApp;

	// Action bar wrapper
	private EDActionBar mActionBar = null;

	// Text view for list header
	private TextView mListHeader = null;

	// File name being exported
	private String mExportFileName = null;

	// Progress dialog for async progress
	private ProgressDialog mProgDialog = null;

	// Shared preferences
	private SharedPreferences mPrefs = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.file_chooser);

		Bundle params = getIntent().getExtras();

		mMode = params.getInt(MODE_KEY);
		mFsType = params.getInt(FS_TYPE_KEY);
		mExportFileName = params.getString(EXPORT_FILE_KEY);

		mCurFileList = new ArrayList<EDFileChooserItem>();

		mCurrentDir = "/";

		// Instantiate the proper file provider
		switch (mFsType) {
		case LOCAL_FS:
			mFileProvider = new EncFSLocalFileProvider(
					Environment.getExternalStorageDirectory());
			break;
		case DROPBOX_FS:
			EDDropbox dropbox = ((EDApplication) getApplication()).getDropbox();

			if (dropbox.isLinked()) {
				// XXX: Use EDDropboxFileProvider.getRootPath() - pending
				// encfs-java issue #37
				mFileProvider = new EDDropboxFileProvider(dropbox.getApi(), "/");
			} else {
				returnFailure();
			}

			break;
		default:
			break;
		}

		launchFillTask();

		registerForContextMenu(this.getListView());

		mApp = (EDApplication) getApplication();

		if (mApp.isActionBarAvailable()) {
			mActionBar = new EDActionBar(this);
			mActionBar.setDisplayHomeAsUpEnabled(true);
		}

		mListHeader = new TextView(this);
		mListHeader.setTypeface(null, Typeface.BOLD);
		this.getListView().addHeaderView(mListHeader);

		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
	}

	// Create the options menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.file_chooser_menu, menu);

		// Make the export item visible and hide the refresh item
		if (mMode == EXPORT_FILE_MODE || mMode == CREATE_VOLUME_MODE) {
			MenuItem item = menu.findItem(R.id.file_chooser_menu_export);
			item.setVisible(true);
			if (mMode == EXPORT_FILE_MODE) {
				item.setIcon(R.drawable.ic_menu_import_file);
			} else {
				item.setIcon(R.drawable.ic_menu_newvolume);
			}
			
			item = menu.findItem(R.id.file_chooser_menu_refresh);
			item.setVisible(false);
		}

		return super.onCreateOptionsMenu(menu);
	}

	// Modify options menu items
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (mMode == CREATE_VOLUME_MODE) {
			MenuItem exportItem = menu.findItem(R.id.file_chooser_menu_export);
			exportItem.setTitle(R.string.menu_create_vol);
		}
		return super.onPrepareOptionsMenu(menu);
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

		switch (id) {
		case DIALOG_AUTO_IMPORT: // Auto import volume
			alertBuilder.setTitle(String
					.format(getString(R.string.auto_import_vol_dialog_str),
							mCurrentDir));
			alertBuilder.setPositiveButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							returnResult(mCurrentDir);
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
			break;
		default:
			Log.d(TAG, "Unknown dialog ID requested " + id);
			return null;
		}

		return alertDialog;
	}

	// Handler for options menu selections
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.file_chooser_menu_export:
			returnResult(mCurrentDir);
			return true;
		case R.id.file_chooser_menu_refresh:
			launchFillTask();
			return true;
		case android.R.id.home:
			Log.v(TAG, "Home icon clicked");
			if (!mCurrentDir.equalsIgnoreCase(mFileProvider.getRootPath())) {

				if (mCurrentDir.lastIndexOf(mFileProvider.getSeparator()) == 0) {
					mCurrentDir = mFileProvider.getRootPath();
				} else {
					mCurrentDir = mCurrentDir.substring(0, mCurrentDir
							.lastIndexOf(mFileProvider.getSeparator()));
				}

				launchFillTask();
			} else {
				finish();
			}
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
			if (!mCurrentDir.equalsIgnoreCase(mFileProvider.getRootPath())) {

				if (mCurrentDir.lastIndexOf(mFileProvider.getSeparator()) == 0) {
					mCurrentDir = mFileProvider.getRootPath();
				} else {
					mCurrentDir = mCurrentDir.substring(0, mCurrentDir
							.lastIndexOf(mFileProvider.getSeparator()));
				}

				launchFillTask();
			} else {
				finish();
			}

			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	private void returnResult(String path) {
		Intent intent = this.getIntent();
		intent.putExtra(RESULT_KEY, path);
		setResult(Activity.RESULT_OK, intent);
		finish();
	}

	private void returnFailure() {
		Intent intent = this.getIntent();
		setResult(Activity.RESULT_FIRST_USER, intent);
		finish();
	}

	private boolean fill() {

		boolean configFileFound = false;
		List<EncFSFileInfo> childFiles = new ArrayList<EncFSFileInfo>();

		try {
			childFiles = mFileProvider.listFiles(mCurrentDir);
		} catch (IOException e) {
			Log.e(TAG, "Failed to list files: " + e.getMessage());
			return false;
		}

		// Set title from UI thread
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				switch (mMode) {
				case VOLUME_PICKER_MODE:
					if (mFsType == DROPBOX_FS) {
						setTitle(getString(R.string.menu_import_vol) + " ("
								+ getString(R.string.dropbox) + ")");
					} else {
						setTitle(getString(R.string.menu_import_vol) + " ("
								+ getString(R.string.fs_dialog_local) + ")");
					}
					break;
				case FILE_PICKER_MODE:
					setTitle(getString(R.string.menu_import_files));
					break;
				case EXPORT_FILE_MODE:
					setTitle(String.format(getString(R.string.export_file),
							mExportFileName));
					break;
				case CREATE_VOLUME_MODE:
					if (mFsType == DROPBOX_FS) {
						setTitle(getString(R.string.menu_create_vol) + " ("
								+ getString(R.string.dropbox) + ")");
					} else {
						setTitle(getString(R.string.menu_create_vol) + " ("
								+ getString(R.string.fs_dialog_local) + ")");
					}
					break;
				}

				mListHeader.setText(mCurrentDir);
			}
		});

		List<EDFileChooserItem> directories = new ArrayList<EDFileChooserItem>();
		List<EDFileChooserItem> files = new ArrayList<EDFileChooserItem>();

		if (childFiles != null) {
			for (EncFSFileInfo file : childFiles) {
				if (file.isDirectory() && file.isReadable()) {
					directories.add(new EDFileChooserItem(file.getName(), true,
							file.getPath(), 0));
				} else {
					if (mMode == VOLUME_PICKER_MODE) {
						if (file.getName().equals(EncFSVolume.CONFIG_FILE_NAME)
								&& file.isReadable()) {
							files.add(new EDFileChooserItem(file.getName(),
									false, file.getPath(), file.getSize()));
							configFileFound = true;
						}
					} else if (mMode == FILE_PICKER_MODE
							|| mMode == EXPORT_FILE_MODE
							|| mMode == CREATE_VOLUME_MODE) {
						if (file.isReadable()) {
							files.add(new EDFileChooserItem(file.getName(),
									false, file.getPath(), file.getSize()));
						}
					}
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

		/*
		 * Add an item for the parent directory (..) in case where no ActionBar
		 * is present (API < 11). With ActionBar we use the Up icon for
		 * navigation.
		 */
		if ((mActionBar == null)
				&& (!mCurrentDir.equalsIgnoreCase(mFileProvider.getRootPath()))) {
			String parentPath;

			if (mCurrentDir.lastIndexOf(mFileProvider.getSeparator()) == 0) {
				parentPath = mFileProvider.getRootPath();
			} else {
				parentPath = mCurrentDir.substring(0,
						mCurrentDir.lastIndexOf(mFileProvider.getSeparator()));
			}

			mCurFileList.add(0,
					new EDFileChooserItem("..", true, parentPath, 0));
		}

		if (mAdapter == null) {
			mAdapter = new EDFileChooserAdapter(this,
					R.layout.file_chooser_item, mCurFileList);

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

		return configFileFound;
	}

	// Show a progress spinner and launch the fill task
	private void launchFillTask() {
		mProgDialog = new ProgressDialog(EDFileChooserActivity.this);
		mProgDialog.setTitle(getString(R.string.loading_contents));
		mProgDialog.setCancelable(false);
		mProgDialog.show();
		new EDFileChooserFillTask(mProgDialog).execute();
	}

	/*
	 * Task to fill the file chooser list. This is needed because fill() can end
	 * up doing network I/O with certain file providers and starting with API
	 * version 13 doing so results in a NetworkOnMainThreadException.
	 */
	private class EDFileChooserFillTask extends AsyncTask<Void, Void, Boolean> {

		private ProgressDialog myDialog;

		public EDFileChooserFillTask(ProgressDialog dialog) {
			super();
			myDialog = dialog;
		}

		@Override
		protected Boolean doInBackground(Void... arg0) {
			return fill();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			if (myDialog.isShowing()) {
				myDialog.dismiss();
			}

			if (result == true) {
				if (mPrefs.getBoolean("auto_import", true)) {
					showDialog(DIALOG_AUTO_IMPORT);
				}
			}
		}
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

		// Use position - 1 since we have a list header
		if (position == 0) {
			return;
		}
		EDFileChooserItem selected = mAdapter.getItem(position - 1);

		if (selected.isDirectory()) {
			mCurrentDir = selected.getPath();
			launchFillTask();
		} else {
			if (mMode == VOLUME_PICKER_MODE) {
				returnResult(mCurrentDir);
			} else if (mMode == FILE_PICKER_MODE) {
				returnResult(selected.getPath());
			}
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
		case R.id.file_chooser_menu_select:
		case R.id.file_picker_menu_import:
			EDFileChooserItem selected = mAdapter.getItem((int) info.id);
			returnResult(selected.getPath());
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
		if (mMode == FILE_PICKER_MODE) {
			inflater.inflate(R.menu.file_picker_context, menu);
		} else {
			inflater.inflate(R.menu.file_chooser_context, menu);
		}
	}
}