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
import java.io.File;

import org.mrpdaemon.sec.encfs.EncFSConfig;
import org.mrpdaemon.sec.encfs.EncFSConfigFactory;
import org.mrpdaemon.sec.encfs.EncFSConfigParser;
import org.mrpdaemon.sec.encfs.EncFSFileProvider;
import org.mrpdaemon.sec.encfs.EncFSInvalidPasswordException;
import org.mrpdaemon.sec.encfs.EncFSVolume;
import org.mrpdaemon.sec.encfs.EncFSVolumeBuilder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public class VolumeListActivity extends ListActivity implements
		TaskResultListener {

	// Request into FileChooserActivity to run in different modes
	private final static int VOLUME_PICKER_REQUEST = 0;
	private final static int VOLUME_CREATE_REQUEST = 1;

	// Dialog ID's
	private final static int DIALOG_VOL_PASS = 0;
	private final static int DIALOG_VOL_NAME = 1;
	private final static int DIALOG_VOL_RENAME = 2;
	private final static int DIALOG_VOL_CREATE = 3;
	private final static int DIALOG_VOL_CREATEPASS = 4;
	private final static int DIALOG_VOL_DELETE = 5;
	private final static int DIALOG_FS_TYPE = 6;
	private final static int DIALOG_ERROR = 7;

	// Volume operation types
	private final static int VOLUME_OP_IMPORT = 0;
	private final static int VOLUME_OP_CREATE = 1;

	// Async task types
	private final static int ASYNC_TASK_UNLOCK = 0;
	private final static int ASYNC_TASK_CREATE = 1;
	private final static int ASYNC_TASK_DELETE = 2;
	private final static int ASYNC_TASK_LAUNCH_CHOOSER = 3;

	// Saved instance state keys
	private final static String SAVED_VOL_IDX_KEY = "vol_idx";
	private final static String SAVED_VOL_PICK_RESULT_KEY = "vol_pick_result";
	private final static String SAVED_VOL_PICK_CONFIG_RESULT_KEY = "vol_pick_config_result";
	private final static String SAVED_CREATE_VOL_NAME_KEY = "create_vol_name";
	private final static String SAVED_VOL_OP_KEY = "vol_op";
	private final static String SAVED_VOL_FS_IDX_KEY = "vol_fs_idx";

	// Logger tag
	private final static String TAG = "VolumeListActivity";

	// Task fragment tag
	private final static String TASK_FRAGMENT_TAG = "TaskFragment";

	// Suffix for newly created volume directories
	private final static String NEW_VOLUME_DIR_SUFFIX = ".encdroid";

	// List adapter
	private VolumeListAdapter mAdapter = null;

	// Application object
	private EDApplication mApp;

	// Currently selected VolumeList item
	private Volume mSelectedVolume;
	private int mSelectedVolIdx;

	// Result from the volume picker activity
	private String mVolPickerResult = null;

	// Result from the volume picker activity returning custom config path
	private String mVolConfigResult = null;

	// Text for the error dialog
	private String mErrDialogText = "";

	// Name of the volume being created
	private String mCreateVolumeName = "";

	// Current volume operation (import/create)
	private int mVolumeOp = -1;

	// FS type for current volume operation
	private FileSystem mVolumeFileSystem = null;

	// Shared preferences
	private SharedPreferences mPrefs = null;

	// Called when the activity is first created.
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.volume_list);
		registerForContextMenu(this.getListView());

		setTitle(getString(R.string.volume_list));

		mApp = (EDApplication) getApplication();
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		if (savedInstanceState != null) {
			// Activity being recreated

			mVolPickerResult = savedInstanceState
					.getString(SAVED_VOL_PICK_RESULT_KEY);
			mVolConfigResult = savedInstanceState
					.getString(SAVED_VOL_PICK_CONFIG_RESULT_KEY);
			mCreateVolumeName = savedInstanceState
					.getString(SAVED_CREATE_VOL_NAME_KEY);
			mVolumeOp = savedInstanceState.getInt(SAVED_VOL_OP_KEY);

			int volFsIndex = savedInstanceState.getInt(SAVED_VOL_FS_IDX_KEY);
			if (volFsIndex != -1) {
				mVolumeFileSystem = mApp.getFileSystemList().get(volFsIndex);
			}

			mSelectedVolIdx = savedInstanceState.getInt(SAVED_VOL_IDX_KEY);
		}

		refreshList();

		if (savedInstanceState != null) {
			// restore mSelectedVolume
			mSelectedVolume = mAdapter.getItem(mSelectedVolIdx);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt(SAVED_VOL_IDX_KEY, mSelectedVolIdx);
		outState.putString(SAVED_VOL_PICK_RESULT_KEY, mVolPickerResult);
		outState.putString(SAVED_VOL_PICK_CONFIG_RESULT_KEY, mVolConfigResult);
		outState.putString(SAVED_CREATE_VOL_NAME_KEY, mCreateVolumeName);
		outState.putInt(SAVED_VOL_OP_KEY, mVolumeOp);
		outState.putInt(SAVED_VOL_FS_IDX_KEY,
				mApp.getFSIndex(mVolumeFileSystem));
		super.onSaveInstanceState(outState);
	}

	// Create the options menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.volume_list_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	// Handler for options menu selections
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.volume_list_menu_import:
			mVolumeOp = VOLUME_OP_IMPORT;
			showDialog(DIALOG_FS_TYPE);
			return true;
		case R.id.volume_list_menu_create:
			mVolumeOp = VOLUME_OP_CREATE;
			showDialog(DIALOG_FS_TYPE);
			return true;
		case R.id.volume_list_menu_accounts:
			Intent showAccounts = new Intent(this, AccountsActivity.class);
			startActivity(showAccounts);
			return true;
		case R.id.volume_list_menu_settings:
			Intent showPrefs = new Intent(this, EDPreferenceActivity.class);
			startActivity(showPrefs);
			return true;
		default:
			return false;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		for (Account account : mApp.getAccountList()) {
			if (account.isLinkOrAuthInProgress()) {
				if (account.resumeLinkOrAuth() == false) {
					mErrDialogText = String.format(
							getString(R.string.account_login_error),
							account.getName());
					showDialog(DIALOG_ERROR);
				}
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
		Volume selected = mAdapter.getItem((int) info.id);

		switch (item.getItemId()) {
		case R.id.volume_list_menu_lock:
			if (selected.isLocked()) {
				mSelectedVolume = selected;
				mSelectedVolIdx = info.position;
				unlockSelectedVolume();
			} else {
				selected.lock();
			}
			mAdapter.notifyDataSetChanged();
			return true;
		case R.id.volume_list_menu_rename:
			this.mSelectedVolume = selected;
			showDialog(DIALOG_VOL_RENAME);
			return true;
		case R.id.volume_list_menu_delete:
			this.mSelectedVolume = selected;
			showDialog(DIALOG_VOL_DELETE);
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
		inflater.inflate(R.menu.volume_list_context, menu);

		// Change the text of the lock/unlock item based on volume status
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		Volume selected = mAdapter.getItem((int) info.id);

		MenuItem lockItem = menu.findItem(R.id.volume_list_menu_lock);

		if (selected.isLocked()) {
			lockItem.setTitle(getString(R.string.menu_unlock_volume));
		} else {
			lockItem.setTitle(getString(R.string.menu_lock_volume));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPrepareDialog(int, android.app.Dialog)
	 */
	@Override
	protected void onPrepareDialog(int id, final Dialog dialog) {
		final EditText input = (EditText) dialog
				.findViewById(R.id.dialog_edit_text);
		boolean rename = false;

		// Reset inputType for non-password dialogs
		if (input != null) {
			input.setInputType(InputType.TYPE_CLASS_TEXT);
		}

		switch (id) {
		case DIALOG_VOL_RENAME:
			rename = true;
		case DIALOG_VOL_PASS:
			if (id == DIALOG_VOL_PASS) {
				if (input != null) {
					// Don't auto complete
					input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD
							| InputType.TYPE_CLASS_TEXT
							| InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
				}
			}
		case DIALOG_VOL_CREATEPASS:
		case DIALOG_VOL_NAME:
		case DIALOG_VOL_CREATE:
			if (input != null) {
				if (rename && mSelectedVolume != null) {
					input.setText(mSelectedVolume.getName());
				} else {
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
		case DIALOG_VOL_DELETE:

			TextView confirmText = (TextView) dialog
					.findViewById(R.id.dialog_confirm_text);
			CheckBox confirmCheck = (CheckBox) dialog
					.findViewById(R.id.dialog_confirm_check);

			// Fix the dialog title
			dialog.setTitle(String.format(
					getString(R.string.delete_vol_dialog_confirm_str),
					mSelectedVolume.getName()));

			// Fix the volume type
			confirmText.setText(String.format(
					getString(R.string.delete_vol_dialog_disk_str),
					mSelectedVolume.getFileSystem().getName()));

			confirmCheck.setChecked(true);
			break;
		case DIALOG_ERROR:
			// Refresh error text
			((AlertDialog) dialog).setMessage(mErrDialogText);
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
		final int myId = id;

		switch (id) {
		case DIALOG_VOL_PASS: // Password dialog
			if (mSelectedVolume == null) {
				// Can happen when restoring a killed activity
				return null;
			}
			// Fall through
		case DIALOG_VOL_CREATEPASS: // Create volume password
			input = (EditText) inflater.inflate(R.layout.dialog_edit,
					(ViewGroup) findViewById(R.layout.volume_list));

			// Hide password input
			input.setTransformationMethod(new PasswordTransformationMethod());

			alertBuilder.setTitle(getString(R.string.pwd_dialog_title_str));
			alertBuilder.setView(input);
			alertBuilder.setPositiveButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {

							// Hide soft keyboard
							imm.hideSoftInputFromWindow(input.getWindowToken(),
									0);

							Editable value = input.getText();

							switch (myId) {
							case DIALOG_VOL_PASS:
								// Launch unlock task
								TaskFragment unlockTask = new UnlockVolumeTaskFragment(
										VolumeListActivity.this,
										mVolumeFileSystem, null,
										mSelectedVolume.getPath(), value
												.toString(), mSelectedVolume
												.getCustomConfigPath());
								addTaskFragment(unlockTask);
								unlockTask.startTask();
								break;
							case DIALOG_VOL_CREATEPASS:
								// Launch async task to create volume
								TaskFragment createTask = new CreateVolumeTaskFragment(
										VolumeListActivity.this,
										mVolPickerResult, mCreateVolumeName,
										value.toString(), mVolumeFileSystem);
								addTaskFragment(createTask);
								createTask.startTask();
								break;
							}
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

		case DIALOG_VOL_RENAME:
			if (mSelectedVolume == null) {
				// Can happen when restoring a killed activity
				return null;
			}
			// Fall through
		case DIALOG_VOL_CREATE:
		case DIALOG_VOL_NAME: // Volume name dialog

			input = (EditText) inflater.inflate(R.layout.dialog_edit,
					(ViewGroup) findViewById(R.layout.volume_list));

			alertBuilder.setTitle(getString(R.string.voladd_dialog_title_str));
			alertBuilder.setView(input);
			alertBuilder.setPositiveButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							Editable value = input.getText();
							switch (myId) {
							case DIALOG_VOL_NAME:
								if (mVolConfigResult == null) {
									importVolume(value.toString(),
											mVolPickerResult, mVolumeFileSystem);
								} else {
									importVolumeWithConfig(value.toString(),
											mVolPickerResult, mVolConfigResult,
											mVolumeFileSystem);
								}
								break;
							case DIALOG_VOL_RENAME:
								renameVolume(mSelectedVolume, value.toString());
								break;
							case DIALOG_VOL_CREATE:
								mCreateVolumeName = value.toString();
								showDialog(DIALOG_VOL_CREATEPASS);
								break;
							default:
								break;
							}
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
		case DIALOG_FS_TYPE:
			alertBuilder.setTitle(getString(R.string.fs_type_dialog_title_str));
			alertBuilder.setAdapter(new FSTypeListAdapter(this,
					R.layout.fs_list_item, mApp.getFileSystemList()),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int item) {
							/*
							 * Launch async task for launching file chooser
							 * needed for network auth
							 */
							TaskFragment launchTask = new LaunchChooserTaskFragment(
									VolumeListActivity.this, mApp, item);
							addTaskFragment(launchTask);
							launchTask.startTask();
						}
					});
			alertDialog = alertBuilder.create();
			break;
		case DIALOG_VOL_DELETE:
			if (mSelectedVolume == null) {
				// Can happen when restoring a killed activity
				return null;
			}

			alertBuilder.setTitle(String.format(
					getString(R.string.delete_vol_dialog_confirm_str),
					mSelectedVolume.getName()));

			View confirmView = inflater.inflate(R.layout.dialog_confirm,
					(ViewGroup) findViewById(R.layout.volume_list));

			TextView confirmText = (TextView) confirmView
					.findViewById(R.id.dialog_confirm_text);
			confirmText.setText(String.format(
					getString(R.string.delete_vol_dialog_disk_str),
					mSelectedVolume.getFileSystem().getName()));

			final CheckBox confirmCheck = (CheckBox) confirmView
					.findViewById(R.id.dialog_confirm_check);
			confirmCheck.setChecked(true);

			alertBuilder.setView(confirmView);

			alertBuilder.setPositiveButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {

							if (confirmCheck.isChecked()) {
								// Launch async task to delete volume
								TaskFragment deleteTask = new DeleteVolumeTaskFragment(
										VolumeListActivity.this,
										mSelectedVolume);
								addTaskFragment(deleteTask);
								deleteTask.startTask();
							} else {
								// Just remove from the volume list
								deleteVolume(mSelectedVolume);
							}
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
			Log.d(TAG, "Unknown dialog ID requested " + id);
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

		mSelectedVolume = mAdapter.getItem(position);
		mSelectedVolIdx = position;

		// Unlock via password
		if (mSelectedVolume.isLocked()) {
			unlockSelectedVolume();
		} else {
			launchVolumeBrowser(position);
		}
	}

	// Handler for results from called activities
	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == Activity.RESULT_OK) {

			mVolPickerResult = data.getExtras().getString(
					FileChooserActivity.RESULT_KEY);
			mVolConfigResult = data.getExtras().getString(
					FileChooserActivity.CONFIG_RESULT_KEY);

			switch (requestCode) {
			case VOLUME_PICKER_REQUEST:
				showDialog(DIALOG_VOL_NAME);
				break;
			case VOLUME_CREATE_REQUEST:
				showDialog(DIALOG_VOL_CREATE);
				break;
			default:
				// Assume it's from one of the account login activies
				for (Account account : mApp.getAccountList()) {
					if (account.isLinkOrAuthInProgress()) {
						if (account.forwardActivityResult(
								VolumeListActivity.this, requestCode,
								resultCode, data) == false) {
							mErrDialogText = String.format(
									getString(R.string.account_login_error),
									account.getName());
							showDialog(DIALOG_ERROR);
						}
					}
				}
				break;
			}
		} else {
			Log.e(TAG, "File chooser returned unexpected return code: "
					+ resultCode);
		}
	}

	// Launch the file chooser activity in the requested mode
	private void launchFileChooser(int mode, int fsIndex) {
		Intent startFileChooser = new Intent(this, FileChooserActivity.class);

		Bundle fileChooserParams = new Bundle();
		fileChooserParams.putInt(FileChooserActivity.MODE_KEY, mode);
		fileChooserParams.putInt(FileChooserActivity.FS_INDEX_KEY, fsIndex);
		startFileChooser.putExtras(fileChooserParams);

		int request = 0;

		switch (mode) {
		case FileChooserActivity.VOLUME_PICKER_MODE:
			request = VOLUME_PICKER_REQUEST;
			break;
		case FileChooserActivity.CREATE_VOLUME_MODE:
			request = VOLUME_CREATE_REQUEST;
			break;
		default:
			Log.e(TAG, "Unknown mode id: " + mode);
			return;
		}

		mVolumeFileSystem = mApp.getFileSystemList().get(fsIndex);

		startActivityForResult(startFileChooser, request);
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

	// Launch the volume browser activity for the given volume
	private void launchVolumeBrowser(int volIndex) {
		Intent startVolumeBrowser = new Intent(this,
				VolumeBrowserActivity.class);

		Bundle volumeBrowserParams = new Bundle();
		volumeBrowserParams.putInt(VolumeBrowserActivity.VOL_ID_KEY, volIndex);
		startVolumeBrowser.putExtras(volumeBrowserParams);

		startActivity(startVolumeBrowser);
	}

	private void refreshList() {
		if (mAdapter == null) {
			mAdapter = new VolumeListAdapter(this, R.layout.volume_list_item,
					mApp.getVolumeList());
			this.setListAdapter(mAdapter);
		} else {
			mAdapter.notifyDataSetChanged();
		}
	}

	private void importVolume(String volumeName, String volumePath,
			FileSystem fileSystem) {
		Volume volume = new Volume(volumeName, volumePath, fileSystem);
		mApp.getVolumeList().add(volume);
		mApp.getDbHelper().insertVolume(volume);
		refreshList();
	}

	private void importVolumeWithConfig(String volumeName, String volumePath,
			String configPath, FileSystem fileSystem) {
		Volume volume = new Volume(volumeName, volumePath, configPath,
				fileSystem);
		mApp.getVolumeList().add(volume);
		mApp.getDbHelper().insertVolume(volume);
		refreshList();
	}

	private void deleteVolume(Volume volume) {
		mApp.getVolumeList().remove(volume);
		mApp.getDbHelper().deleteVolume(volume);
		refreshList();
	}

	private void renameVolume(Volume volume, String newName) {
		mApp.getDbHelper().renameVolume(volume, newName);
		volume.setName(newName);
		refreshList();
	}

	/**
	 * Unlock the currently selected volume
	 */
	private void unlockSelectedVolume() {
		mVolumeFileSystem = mSelectedVolume.getFileSystem();

		// If key caching is enabled, see if a key is cached
		byte[] cachedKey = null;
		if (mPrefs.getBoolean("cache_key", false)) {
			cachedKey = mApp.getDbHelper().getCachedKey(mSelectedVolume);
		}

		if (cachedKey == null) {
			showDialog(DIALOG_VOL_PASS);
		} else {
			TaskFragment unlockTask = new UnlockVolumeTaskFragment(this,
					mVolumeFileSystem, cachedKey, mSelectedVolume.getPath(),
					null, mSelectedVolume.getCustomConfigPath());
			addTaskFragment(unlockTask);
			unlockTask.startTask();
		}
	}

	// Class to hold result from UnlockVolumeTask
	private class UnlockVolumeTaskResult {
		public EncFSVolume volume;
		public boolean invalidCachedKey;
		public byte[] cachedKey;

		public UnlockVolumeTaskResult(EncFSVolume volume,
				boolean invalidCachedKey, byte[] cachedKey) {
			this.volume = volume;
			this.invalidCachedKey = invalidCachedKey;
			this.cachedKey = cachedKey;
		}
	}

	private class UnlockVolumeTaskFragment extends TaskFragment {

		// Volume type
		private FileSystem mFileSystem;

		// Cached key
		private byte[] mCachedKey;

		// Path of the volume to unlock
		private String mVolumePath;

		// Password for unlocking (optional if cached key is given)
		private String mPassword;

		// Optional custom config path
		private String mConfigPath;

		public UnlockVolumeTaskFragment(Activity activity,
				FileSystem fileSystem, byte[] cachedKey, String volumePath,
				String password, String configPath) {
			super(activity);
			this.mFileSystem = fileSystem;
			this.mCachedKey = cachedKey;
			this.mVolumePath = volumePath;
			this.mPassword = password;
			this.mConfigPath = configPath;
		}

		@Override
		protected int getTaskId() {
			return ASYNC_TASK_UNLOCK;
		}

		@Override
		protected EDAsyncTask<Void, Void, UnlockVolumeTaskResult> createTask() {
			return new UnlockVolumeTask(this);
		}

		private class UnlockVolumeTask extends
				EDAsyncTask<Void, Void, UnlockVolumeTaskResult> {

			public UnlockVolumeTask(TaskFragment fragment) {
				super(fragment);
			}

			@Override
			protected ProgressDialog createProgressDialog(Activity activity) {
				// We only use a progress spinner
				mProgressDialogSpinnerOnly = true;

				if (mCachedKey == null) {
					mProgressDialogTitle = activity
							.getString(R.string.pbkdf_dialog_title_str);
					mProgressDialogMsgResId = R.string.pbkdf_dialog_msg_str;
				} else {
					mProgressDialogTitle = activity
							.getString(R.string.unlocking_volume);
				}

				return super.createProgressDialog(activity);
			}

			@SuppressWarnings("deprecation")
			@SuppressLint("Wakelock")
			@Override
			protected UnlockVolumeTaskResult doInBackground(Void... args) {

				WakeLock wl = null;
				EncFSVolume volume = null;

				if (mCachedKey == null) {
					PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
					wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG);

					// Acquire wake lock to prevent screen from dimming/timing
					// out
					wl.acquire();
				}

				// link or authenticate account if needed
				Account account = mFileSystem.getAccount();
				if (account != null) {
					// XXX: this shouldn't need activity
					mTaskFragment.blockForActivity();
					if (account.linkOrAuthIfNeeded(mActivity, TAG) == false) {
						mTaskFragment.returnError(String.format(mTaskFragment
								.getStringSafe(R.string.account_login_error),
								mFileSystem.getName()));
						return null;
					}
				}

				// Get file provider for this file system
				EncFSFileProvider fileProvider = mFileSystem
						.getFileProvider(mVolumePath);
				EncFSConfig volConfig = null;
				if (mConfigPath != null) {
					File config = new File(mConfigPath);
					try {
						volConfig = EncFSConfigParser.parseFile(config);
					} catch (Exception e) {
						Logger.logException(TAG, e);
						mTaskFragment.returnError(e.getMessage());
						return null;
					}
				}
				// Unlock the volume, takes long due to PBKDF2 calculation
				try {
					if (mCachedKey == null) {
						if (volConfig != null) {
							volume = new EncFSVolumeBuilder()
									.withFileProvider(fileProvider)
									.withConfig(volConfig)
									.withPassword(mPassword).buildVolume();
						} else {
							volume = new EncFSVolumeBuilder()
									.withFileProvider(fileProvider)
									.withPbkdf2Provider(
											mApp.getNativePBKDF2Provider())
									.withPassword(mPassword).buildVolume();
						}
					} else {
						if (volConfig != null) {
							volume = new EncFSVolumeBuilder()
									.withFileProvider(fileProvider)
									.withConfig(volConfig)
									.withDerivedKeyData(mCachedKey)
									.buildVolume();
						} else {
							volume = new EncFSVolumeBuilder()
									.withFileProvider(fileProvider)
									.withDerivedKeyData(mCachedKey)
									.buildVolume();
						}
					}
				} catch (EncFSInvalidPasswordException e) {
					if (mCachedKey != null) {
						return new UnlockVolumeTaskResult(null, true,
								mCachedKey);
					} else {
						mTaskFragment.returnError(mTaskFragment
								.getStringSafe(R.string.incorrect_pwd_str));
						return null;
					}
				} catch (Exception e) {
					Logger.logException(TAG, e);
					mTaskFragment.returnError(e.getMessage());
					return null;
				}

				if (mCachedKey == null) {
					// Release the wake lock
					wl.release();
				}

				return new UnlockVolumeTaskResult(volume, false, mCachedKey);
			}

			// Run after the task is complete
			@Override
			protected void onPostExecute(UnlockVolumeTaskResult result) {
				super.onPostExecute(result);
				if (!isCancelled()) {
					if (result != null) {
						mTaskFragment.returnResult(result);
					}
				}
			}
		}
	}

	// Class to return create volume task result back to the activity
	private class CreateVolumeTaskResult {
		public String volumeName;
		public String volumePath;
		public FileSystem volumeFs;

		public CreateVolumeTaskResult(String volumeName, String volumePath,
				FileSystem volumeFs) {
			this.volumeName = volumeName;
			this.volumePath = volumePath;
			this.volumeFs = volumeFs;
		}
	}

	private class CreateVolumeTaskFragment extends TaskFragment {

		// Root directory path of volume being created
		private String mVolumeRootPath;

		// Name of the volume being created
		private String mVolumeName;

		// Volume type
		private FileSystem mVolumeFs;

		// Password
		private String mPassword;

		public CreateVolumeTaskFragment(Activity activity,
				String volumeRootPath, String volumeName, String password,
				FileSystem fileSystem) {
			super(activity);
			this.mVolumeRootPath = volumeRootPath;
			this.mVolumeName = volumeName;
			this.mVolumeFs = fileSystem;
			this.mPassword = password;
		}

		@Override
		protected int getTaskId() {
			return ASYNC_TASK_CREATE;
		}

		@Override
		protected EDAsyncTask<Void, Void, CreateVolumeTaskResult> createTask() {
			return new CreateVolumeTask(this);
		}

		private class CreateVolumeTask extends
				EDAsyncTask<Void, Void, CreateVolumeTaskResult> {

			// Path of the volume
			private String volumePath;

			public CreateVolumeTask(TaskFragment fragment) {
				super(fragment);
			}

			@Override
			protected ProgressDialog createProgressDialog(Activity activity) {
				// We only use a progress spinner
				mProgressDialogSpinnerOnly = true;
				mProgressDialogTitle = String.format(String.format(
						activity.getString(R.string.mkvol_dialog_title_str),
						mVolumeName));
				return super.createProgressDialog(activity);
			}

			@Override
			protected CreateVolumeTaskResult doInBackground(Void... args) {

				// link or authenticate account if needed
				Account account = mVolumeFs.getAccount();
				if (account != null) {
					// XXX: shouldn't depend on mActivity
					mTaskFragment.blockForActivity();
					if (account.linkOrAuthIfNeeded(mActivity, TAG) == false) {
						mTaskFragment.returnError(String.format(mTaskFragment
								.getStringSafe(R.string.account_login_error),
								mVolumeFs.getName()));
						return null;
					}
				}

				EncFSFileProvider rootProvider = mVolumeFs.getFileProvider("/");

				try {
					if (!rootProvider.exists(mVolumeRootPath)) {
						mTaskFragment.returnError(String.format(mTaskFragment
								.getStringSafe(R.string.error_dir_not_found),
								mVolumeRootPath));
						return null;
					}

					volumePath = EncFSVolume.combinePath(mVolumeRootPath,
							mVolumeName + NEW_VOLUME_DIR_SUFFIX);

					if (rootProvider.exists(volumePath)) {
						mTaskFragment.returnError(mTaskFragment
								.getStringSafe(R.string.error_file_exists));
						return null;
					}

					// Create the new directory
					if (!rootProvider.mkdir(volumePath)) {
						mTaskFragment.returnError(String.format(mTaskFragment
								.getStringSafe(R.string.error_mkdir_fail),
								volumePath));
						return null;
					}
				} catch (IOException e) {
					mTaskFragment.returnError(e.getMessage());
					return null;
				}

				EncFSFileProvider fileProvider = mVolumeFs
						.getFileProvider(volumePath);

				// Create the volume
				try {
					new EncFSVolumeBuilder().withFileProvider(fileProvider)
							.withConfig(EncFSConfigFactory.createDefault())
							.withPbkdf2Provider(mApp.getNativePBKDF2Provider())
							.withPassword(mPassword).writeVolumeConfig();
				} catch (Exception e) {
					mTaskFragment.returnError(e.getMessage());
					return null;
				}

				return new CreateVolumeTaskResult(mVolumeName, volumePath,
						mVolumeFs);
			}

			// Run after the task is complete
			@Override
			protected void onPostExecute(CreateVolumeTaskResult result) {
				super.onPostExecute(result);

				if (!isCancelled()) {
					if (result != null) {
						mTaskFragment.returnResult(result);
					}
				}
			}
		}
	}

	private class DeleteVolumeTaskFragment extends TaskFragment {

		private Volume mVolume;

		public DeleteVolumeTaskFragment(Activity activity, Volume volume) {
			super(activity);
			this.mVolume = volume;
		}

		@Override
		protected int getTaskId() {
			return ASYNC_TASK_DELETE;
		}

		@Override
		protected EDAsyncTask<Void, Void, Volume> createTask() {
			return new DeleteVolumeTask(this);
		}

		private class DeleteVolumeTask extends EDAsyncTask<Void, Void, Volume> {

			public DeleteVolumeTask(TaskFragment fragment) {
				super(fragment);
			}

			@Override
			protected ProgressDialog createProgressDialog(Activity activity) {
				// We only use a progress spinner
				mProgressDialogSpinnerOnly = true;
				mProgressDialogTitle = String.format(
						activity.getString(R.string.delvol_dialog_title_str),
						mVolume.getName());
				return super.createProgressDialog(activity);
			}

			@Override
			protected Volume doInBackground(Void... args) {

				// link or authenticate account if needed
				Account account = mVolume.getFileSystem().getAccount();
				if (account != null) {
					// XXX: we should clear activity usage from here
					mTaskFragment.blockForActivity();
					if (account.linkOrAuthIfNeeded(mActivity, TAG) == false) {
						mTaskFragment.returnError(String.format(mTaskFragment
								.getStringSafe(R.string.account_login_error),
								mVolume.getFileSystem().getName()));
						return null;
					}
				}

				EncFSFileProvider rootProvider = mVolume.getFileSystem()
						.getFileProvider("/");

				try {
					if (!rootProvider.exists(mVolume.getPath())) {
						mTaskFragment.returnError(String.format(mTaskFragment
								.getStringSafe(R.string.error_dir_not_found),
								mVolume.getPath()));
						return null;
					}

					// Delete the volume
					if (!rootProvider.delete(mVolume.getPath())) {
						mTaskFragment.returnError(String.format(mTaskFragment
								.getStringSafe(R.string.error_delete_fail),
								mVolume.getPath()));
						return null;
					}
				} catch (IOException e) {
					mTaskFragment.returnError(e.getMessage());
					return null;
				}

				return mVolume;
			}

			// Run after the task is complete
			@Override
			protected void onPostExecute(Volume result) {
				super.onPostExecute(result);

				if (!isCancelled()) {
					if (result != null) {
						mTaskFragment.returnResult(result);
					}
				}
			}
		}
	}

	private class LaunchChooserTaskFragment extends TaskFragment {

		private EDApplication mApp;

		private int mVolumeIdx;

		public LaunchChooserTaskFragment(Activity activity, EDApplication app,
				int volumeIdx) {
			super(activity);
			this.mVolumeIdx = volumeIdx;
			this.mApp = app;
		}

		@Override
		protected int getTaskId() {
			return ASYNC_TASK_LAUNCH_CHOOSER;
		}

		@Override
		protected EDAsyncTask<Void, Void, Integer> createTask() {
			return new LaunchChooserTask(this);
		}

		// EDAsyncTask for launching file chooser
		private class LaunchChooserTask extends
				EDAsyncTask<Void, Void, Integer> {

			public LaunchChooserTask(TaskFragment fragment) {
				super(fragment);
			}

			@Override
			protected ProgressDialog createProgressDialog(Activity activity) {
				// We only use a progress spinner
				mProgressDialogSpinnerOnly = true;
				mProgressDialogTitle = activity
						.getString(R.string.launching_chooser);
				return super.createProgressDialog(activity);
			}

			@Override
			protected Integer doInBackground(Void... args) {

				FileSystem fs = mApp.getFileSystemList().get(mVolumeIdx);

				// link or authenticate account if needed
				Account account = fs.getAccount();
				if (account != null) {
					// XXX: we should clear activity usage from here
					mTaskFragment.blockForActivity();
					if (account.linkOrAuthIfNeeded(mActivity, TAG) == false) {
						mTaskFragment.returnError(String.format(mTaskFragment
								.getStringSafe(R.string.account_login_error),
								fs.getName()));
						return -1;
					}
				}

				return mVolumeIdx;
			}

			// Run after the task is complete
			@Override
			protected void onPostExecute(Integer result) {
				super.onPostExecute(result);

				if (!isCancelled()) {
					if (result >= 0) {
						mTaskFragment.returnResult(result);
					}
				}
			}
		}

	}

	// Method called from a TaskFragment to return task result
	@Override
	public void onTaskResult(int taskId, Object result) {
		removeTaskFragment();
		switch (taskId) {
		case ASYNC_TASK_LAUNCH_CHOOSER:
			int volumeIdx = (Integer) result;
			if (mVolumeOp == VOLUME_OP_IMPORT) {
				launchFileChooser(FileChooserActivity.VOLUME_PICKER_MODE,
						volumeIdx);
			} else {
				launchFileChooser(FileChooserActivity.CREATE_VOLUME_MODE,
						volumeIdx);
			}
			break;
		case ASYNC_TASK_DELETE:
			Volume volume = (Volume) result;
			deleteVolume(volume);
			break;
		case ASYNC_TASK_CREATE:
			CreateVolumeTaskResult cvtr = (CreateVolumeTaskResult) result;
			importVolume(cvtr.volumeName, cvtr.volumePath, cvtr.volumeFs);
			break;
		case ASYNC_TASK_UNLOCK:
			UnlockVolumeTaskResult ovtr = (UnlockVolumeTaskResult) result;

			if (ovtr.invalidCachedKey == true) {
				// Show toast for invalid password
				Toast.makeText(getApplicationContext(),
						getString(R.string.save_pass_invalid_str),
						Toast.LENGTH_SHORT).show();

				// Invalidate cached key from DB
				mApp.getDbHelper().clearKey(mSelectedVolume);

				// Kick off password dialog
				showDialog(DIALOG_VOL_PASS);
			} else {
				if (ovtr.volume != null) {
					mSelectedVolume.unlock(ovtr.volume);
					mAdapter.notifyDataSetChanged();

					if (ovtr.cachedKey == null) {
						// Cache key in DB if preference is enabled
						if (mPrefs.getBoolean("cache_key", false)) {
							byte[] keyToCache = ovtr.volume.getDerivedKeyData();
							mApp.getDbHelper().cacheKey(mSelectedVolume,
									keyToCache);
						}
					}

					launchVolumeBrowser(mSelectedVolIdx);
				}
			}
			break;
		default:
			Log.d(TAG, "Unknown task id: " + taskId);
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
			}
		});
	}
}
