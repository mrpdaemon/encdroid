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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.mrpdaemon.sec.encfs.EncFSFileInfo;
import org.mrpdaemon.sec.encfs.EncFSFileProvider;
import org.mrpdaemon.sec.encfs.EncFSVolume;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewGroup.LayoutParams;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class FileChooserActivity extends ListActivity {
	// Parameter key for activity mode
	public final static String MODE_KEY = "mode";

	// Supported modes
	public final static int VOLUME_PICKER_MODE = 0;
	public final static int FILE_PICKER_MODE = 1;
	public final static int EXPORT_FILE_MODE = 2;
	public final static int CREATE_VOLUME_MODE = 3;

	// Parameter key for export file name
	public final static String EXPORT_FILE_KEY = "export_file";

	// Parameter key for FS type
	public final static String FS_INDEX_KEY = "fs_index";

	// Valid FS types
	public final static int LOCAL_FS = 0;
	public final static int EXT_SD_FS = 1;
	public final static int DROPBOX_FS = 2;

	// Result key for the path returned by this activity
	public final static String RESULT_KEY = "result_path";

	public final static String CONFIG_RESULT_KEY = "config_result_path";
	public final static String IS_DIALOG_SHOWN_KEY = "is_dialog_shown";
	public final static String DIALOG_SHOWN_DIR_KEY = "dialog_shown_dir";

	// Name of the SD card directory for copying files into
	public final static String ENCDROID_SD_DIR_NAME = "Encdroid";

	// Instance state bundle key for current directory
	public final static String CUR_DIR_KEY = "current_dir";
	public final static String VOL_HOME_DIR_KEY = "volume_home_dir";

	// Logger tag
	private static final String TAG = "FileChooserActivity";

	// Dialog ID's
	private final static int DIALOG_AUTO_IMPORT = 0;
	
	private final static int DIALOG_BROWSE_CONFIG= 1;

	public MyAlertDialogFragment browseDialogFragment;

	// Adapter for the list
	private FileChooserAdapter mAdapter = null;

	// List that is currently being displayed
	private List<FileChooserItem> mCurFileList;

	// What mode we're running in
	private int mMode;

	private String configPath;
	private boolean configFileFound = false;
	private File[] fileList;
	private boolean isDialogShown =false;
	private String dialogShownDir;

	// Current directory
	private String mCurrentDir;

	private String volumeHomeDir;

	// The underlying FS this chooser
	private FileSystem mFileSystem;

	// File provider
	private EncFSFileProvider mFileProvider;

	// Application object
	private EDApplication mApp;

	// Text view for list header
	private TextView mListHeader = null;

	// File name being exported
	private String mExportFileName = null;

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

		mApp = (EDApplication) getApplication();

		if (savedInstanceState == null) {
			// New activity creation
			Bundle params = getIntent().getExtras();
			mMode = params.getInt(MODE_KEY);
			mFileSystem = mApp.getFileSystemList().get(
					params.getInt(FS_INDEX_KEY));
			mExportFileName = params.getString(EXPORT_FILE_KEY);
			mCurrentDir = "/";
		} else {
			// Restoring previously killed activity
			mMode = savedInstanceState.getInt(MODE_KEY);
			mFileSystem = mApp.getFileSystemList().get(
					savedInstanceState.getInt(FS_INDEX_KEY));
			mExportFileName = savedInstanceState.getString(EXPORT_FILE_KEY);
			mCurrentDir = savedInstanceState.getString(CUR_DIR_KEY);
			volumeHomeDir = savedInstanceState.getString(VOL_HOME_DIR_KEY);
			isDialogShown = savedInstanceState.getBoolean(IS_DIALOG_SHOWN_KEY);
			dialogShownDir = savedInstanceState.getString(DIALOG_SHOWN_DIR_KEY);
			if(isDialogShown) {
				showBrowseDialog(dialogShownDir);
			}
		}

		mCurFileList = new ArrayList<FileChooserItem>();

		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		/*
		 * Instantiate the proper file provider
		 * 
		 * It's OK to do this from the Activity context since the caller
		 * guarantees that the account is already linked/authenticated.
		 */
		mFileProvider = mFileSystem.getFileProvider("/");

		launchFillTask();

		registerForContextMenu(this.getListView());

		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setIcon(mFileSystem.getIconResId());

		mListHeader = new TextView(this);
		mListHeader.setTypeface(null, Typeface.BOLD);
		mListHeader.setTextSize(16);
		this.getListView().addHeaderView(mListHeader);

		// Set title text
		switch (mMode) {
		case VOLUME_PICKER_MODE:
			setTitle(getString(R.string.menu_import_vol));
			break;
		case FILE_PICKER_MODE:
			setTitle(getString(R.string.menu_import_files));
			break;
		case EXPORT_FILE_MODE:
			setTitle(String.format(getString(R.string.export_file),
					mExportFileName));
			break;
		case CREATE_VOLUME_MODE:
			setTitle(getString(R.string.menu_create_vol));
			break;
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt(MODE_KEY, mMode);
		outState.putInt(FS_INDEX_KEY, mApp.getFSIndex(mFileSystem));
		outState.putString(EXPORT_FILE_KEY, mExportFileName);
		outState.putString(CUR_DIR_KEY, mCurrentDir);
		outState.putBoolean(IS_DIALOG_SHOWN_KEY, isDialogShown);
		outState.putString(DIALOG_SHOWN_DIR_KEY, dialogShownDir);
		outState.putString(VOL_HOME_DIR_KEY, volumeHomeDir);
		super.onSaveInstanceState(outState);
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
		MenuItem item = menu.findItem(R.id.file_chooser_menu_import);
		if (mMode == VOLUME_PICKER_MODE ) {
			item.setVisible(true);
		}
		else {
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
		case DIALOG_BROWSE_CONFIG: 
			alertBuilder.setTitle(R.string.custom_import_str);
			alertBuilder.setMessage(String
					.format(getString(R.string.browse_config_dialog_str),
							mCurrentDir));
			alertBuilder.setPositiveButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							showBrowseDialog(mApp.getFileSystemList().get(0).getPathPrefix());
						}
					});
			// Cancel button
			alertBuilder.setNegativeButton(getString(R.string.btn_cancel_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							dialog.cancel();
							finish();
						}
					});
			alertDialog = alertBuilder.create();
			alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialogInterface) {
					finish();
				}
			});
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
		case R.id.file_chooser_menu_import:
			volumeHomeDir=mCurrentDir;
			if(configFileFound) {
				returnResult(mCurrentDir);
			}
			else {
				showDialog(DIALOG_BROWSE_CONFIG);
			}
			return true;
		case android.R.id.home:
			Log.v(TAG, "Home icon clicked");
			if (!mCurrentDir.equalsIgnoreCase(mFileProvider
					.getFilesystemRootPath())) {

				if (mCurrentDir.lastIndexOf("/") == 0) {
					mCurrentDir = mFileProvider.getFilesystemRootPath();
				} else {
					mCurrentDir = mCurrentDir.substring(0,
							mCurrentDir.lastIndexOf("/"));
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
			if (!mCurrentDir.equalsIgnoreCase(mFileProvider
					.getFilesystemRootPath())) {

				if (mCurrentDir.lastIndexOf("/") == 0) {
					mCurrentDir = mFileProvider.getFilesystemRootPath();
				} else {
					mCurrentDir = mCurrentDir.substring(0,
							mCurrentDir.lastIndexOf("/"));
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

	private boolean fill() {

		configFileFound=false;
		List<EncFSFileInfo> childFiles = new ArrayList<EncFSFileInfo>();

		try {
			childFiles = mFileProvider.listFiles(mCurrentDir);
		} catch (IOException e) {
			Logger.logException(TAG, e);
			return false;
		}

		// Set list header from UI thread
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mListHeader.setText(mCurrentDir);
			}
		});

		List<FileChooserItem> directories = new ArrayList<FileChooserItem>();
		List<FileChooserItem> files = new ArrayList<FileChooserItem>();

		if (childFiles != null) {
			for (EncFSFileInfo file : childFiles) {
				if (file.isDirectory() && file.isReadable()) {
					directories.add(new FileChooserItem(file.getName(), true,
							file.getPath(), 0));
				} else {
					if (mMode == VOLUME_PICKER_MODE) {
						if (file.getName().equals(EncFSVolume.CONFIG_FILE_NAME)
								&& file.isReadable()) {
							files.add(new FileChooserItem(file.getName(),
									false, file.getPath(), file.getSize()));
							configFileFound = true;
						}
					} else if (mMode == FILE_PICKER_MODE
							|| mMode == EXPORT_FILE_MODE
							|| mMode == CREATE_VOLUME_MODE) {
						if (file.isReadable()) {
							files.add(new FileChooserItem(file.getName(),
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

		return configFileFound;
	}

	// Show a progress spinner and launch the fill task
	private void launchFillTask() {
		new FileChooserFillTask().execute();
	}

	private File[] loadFileList(String directory) {
		File path = new File(directory);

		if(path.exists()) {
			FilenameFilter filter = new FilenameFilter() {
				public boolean accept(File dir, String filename) {
					File file = new File(dir, filename);
					return filename.contains(".xml") || file.isDirectory();//config name not restricted to .encfs6.xml
				}
			};

			File[] list = path.listFiles(filter);
			Arrays.sort(list);
			return list == null? new File[0] : list;
		} else {
			return new File[0];
		}
	}

	public String upOneDirectory(String directory){
		String[] dirs = directory.split("/");
		StringBuilder stringBuilder = new StringBuilder("");

		for(int i = 0; i < dirs.length-1; i++)
			stringBuilder.append(dirs[i]).append("/");

		return stringBuilder.toString();
	}

	public void showBrowseDialog(String dir) {
		isDialogShown=true;
		dialogShownDir=dir;
		browseDialogFragment = new MyAlertDialogFragment();
		Bundle args = new Bundle();

		args.putString("directory", dir);
		browseDialogFragment.setArguments(args);
		browseDialogFragment.show(getFragmentManager(), "dialog");
	}

	public void doPositiveClick(int item) {
		File chosenFile = fileList[item];
		if(chosenFile.isDirectory()) {
			showBrowseDialog(chosenFile.getAbsolutePath());
		}
		else{
			configPath = chosenFile.getAbsolutePath();
			Intent intent = this.getIntent();
			intent.putExtra(RESULT_KEY, volumeHomeDir);
			intent.putExtra(CONFIG_RESULT_KEY, configPath);
			setResult(Activity.RESULT_OK, intent);
			finish();
		}
	}

	public void doNegativeClick() {
		isDialogShown=false;
		finish();
	}
	public  class MyAlertDialogFragment extends DialogFragment {
		public MyAlertDialogFragment(){
			super();
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setRetainInstance(true);
		}

		@Override
		public void onCancel (DialogInterface dialog) {
			isDialogShown=false;
		}
		
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {

			String dir =getArguments().getString("directory");

			String[] filenameList;
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

			File[] tempFileList = loadFileList(dir);

			if(dir.equals(mApp.getFileSystemList().get(0).getPathPrefix())){
				fileList = new File[tempFileList.length];
				filenameList = new String[tempFileList.length];

				for(int i = 0; i < tempFileList.length; i++){
					{
						fileList[i] = tempFileList[i];
						filenameList[i]=tempFileList[i].getName();
					}
				}
			} else {
				fileList = new File[tempFileList.length+1];
				filenameList = new String[tempFileList.length+1];

				fileList[0] = new File(upOneDirectory(dir));
				filenameList[0] = "..";

				for(int i = 0; i < tempFileList.length; i++){
					{
						fileList[i + 1] = tempFileList[i];
						filenameList[i + 1]= tempFileList[i].getName();
					}
				}
			}

			builder.setTitle(getString(R.string.choose_config_dialog_str) + dir);
			builder.setAdapter(new ConfigFileChooserAdapter(getActivity(), R.layout.file_chooser_item,
							fileList,filenameList),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int item) {
							doPositiveClick(item);

						}
					});
			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					doNegativeClick();

				}
			});
			return builder.create();
		}
		@Override
		public void onSaveInstanceState(Bundle outState) {
			super.onSaveInstanceState(outState);
		}
	}

	/*
	 * Task to fill the file chooser list. This is needed because fill() can end
	 * up doing network I/O with certain file providers and starting with API
	 * version 13 doing so results in a NetworkOnMainThreadException.
	 */
	private class FileChooserFillTask extends AsyncTask<Void, Void, Boolean> {

		private ProgressBar mProgBar;
		private ListView mListView;
		private LinearLayout mLayout;

		public FileChooserFillTask() {
			super();
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			try {
				// Replace the ListView with a ProgressBar
				mProgBar = new ProgressBar(FileChooserActivity.this, null,
						android.R.attr.progressBarStyleLarge);

				// Set the layout to fill the screen
				mListView = FileChooserActivity.this.getListView();
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
		protected Boolean doInBackground(Void... arg0) {
			return fill();
		}

		@Override
		protected void onPostExecute(Boolean result) {
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
		FileChooserItem selected = mAdapter.getItem(position - 1);

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
			if (info.id >= 0) {
				FileChooserItem selected = mAdapter.getItem((int) info.id);
				returnResult(selected.getPath());
			}
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
