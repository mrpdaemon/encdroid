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
import javax.xml.parsers.ParserConfigurationException;

import org.mrpdaemon.sec.encfs.EncFSConfig;
import org.mrpdaemon.sec.encfs.EncFSConfigFactory;
import org.mrpdaemon.sec.encfs.EncFSConfigParser;
import org.mrpdaemon.sec.encfs.EncFSFileProvider;
import org.mrpdaemon.sec.encfs.EncFSInvalidConfigException;
import org.mrpdaemon.sec.encfs.EncFSInvalidPasswordException;
import org.mrpdaemon.sec.encfs.EncFSUnsupportedException;
import org.mrpdaemon.sec.encfs.EncFSVolume;
import org.mrpdaemon.sec.encfs.EncFSVolumeBuilder;
import org.xml.sax.SAXException;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public class VolumeListActivity extends ListActivity {

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
	private final static int ASYNC_TASK_UNLOCK_CACHE = 0;
	private final static int ASYNC_TASK_UNLOCK_PBKDF2 = 1;
	private final static int ASYNC_TASK_CREATE = 2;
	private final static int ASYNC_TASK_DELETE = 3;
	private final static int ASYNC_TASK_LAUNCH_CHOOSER = 4;

	// Saved instance state keys
	private final static String SAVED_VOL_IDX_KEY = "vol_idx";
	private final static String SAVED_VOL_PICK_RESULT_KEY = "vol_pick_result";
	private final static String SAVED_VOL_PICK_CONFIG_RESULT_KEY = "vol_pick_config_result";
	private final static String SAVED_CREATE_VOL_NAME_KEY = "create_vol_name";
	private final static String SAVED_VOL_OP_KEY = "vol_op";
	private final static String SAVED_VOL_FS_IDX_KEY = "vol_fs_idx";
	private final static String SAVED_ASYNC_TASK_ID_KEY = "async_task_id";
	private final static String SAVED_PROGRESS_BAR_STR_ARG_KEY = "prog_bar_str";

	// Logger tag
	private final static String TAG = "VolumeListActivity";

	// Suffix for newly created volume directories
	private final static String NEW_VOLUME_DIR_SUFFIX = ".encdroid";

	// List adapter
	private VolumeListAdapter mAdapter = null;

	// Application object
	private EDApplication mApp;

	// Currently selected VolumeList item
	private Volume mSelectedVolume;
	private int mSelectedVolIdx;

	// Async task object for running volume key derivation
	private EDAsyncTask<String, ?, ?> mAsyncTask = null;

	// Async task ID
	private int mAsyncTaskId = -1;

	// Progress dialog for async progress
	private ProgressDialog mProgDialog = null;

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

	// Saved instance state for progress bar string argument
	private String mSavedProgBarStrArg = null;

	// Restore context
	private class ActivityRestoreContext {
		public Volume savedVolume;
		public EDAsyncTask<String, ?, ?> savedTask;
	}

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

			ActivityRestoreContext restoreContext = (ActivityRestoreContext) getLastNonConfigurationInstance();
			if (restoreContext != null) {
				mSelectedVolume = restoreContext.savedVolume;
				mSelectedVolIdx = savedInstanceState.getInt(SAVED_VOL_IDX_KEY);

				// Restore async task
				mAsyncTask = restoreContext.savedTask;
				mAsyncTaskId = savedInstanceState
						.getInt(SAVED_ASYNC_TASK_ID_KEY);

				if (mAsyncTask != null) {
					// Create new progress dialog and replace the old one
					createProgressBarForTask(mAsyncTaskId,
							savedInstanceState
									.getString(SAVED_PROGRESS_BAR_STR_ARG_KEY));
					mAsyncTask.setProgressDialog(mProgDialog);

					// Fix the activity for the task
					mAsyncTask.setActivity(this);
				}
			}
		}
		refreshList();
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		ActivityRestoreContext restoreContext = new ActivityRestoreContext();
		restoreContext.savedVolume = mSelectedVolume;
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

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt(SAVED_VOL_IDX_KEY, mSelectedVolIdx);
		outState.putString(SAVED_VOL_PICK_RESULT_KEY, mVolPickerResult);
		outState.putString(SAVED_VOL_PICK_CONFIG_RESULT_KEY, mVolConfigResult);
		outState.putString(SAVED_CREATE_VOL_NAME_KEY, mCreateVolumeName);
		outState.putInt(SAVED_VOL_OP_KEY, mVolumeOp);
		outState.putInt(SAVED_VOL_FS_IDX_KEY,
				mApp.getFSIndex(mVolumeFileSystem));
		outState.putInt(SAVED_ASYNC_TASK_ID_KEY, mAsyncTaskId);
		outState.putString(SAVED_PROGRESS_BAR_STR_ARG_KEY, mSavedProgBarStrArg);
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
			input = (EditText) inflater.inflate(R.layout.dialog_edit, null);

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
								// Show progress dialog
								createProgressBarForTask(
										ASYNC_TASK_UNLOCK_PBKDF2, null);

								// Launch async task to import volume
								mAsyncTask = new UnlockVolumeTask(mProgDialog,
										mVolumeFileSystem, null);
								mAsyncTaskId = ASYNC_TASK_UNLOCK_PBKDF2;
								mAsyncTask.setActivity(VolumeListActivity.this);
								if(mSelectedVolume.getCustomConfigPath() == null) {
									mAsyncTask.execute(mSelectedVolume.getPath(),
											value.toString());
								}
								else {
									mAsyncTask.execute(mSelectedVolume.getPath(),
											value.toString(),
											mSelectedVolume.getCustomConfigPath());
								}
								break;
							case DIALOG_VOL_CREATEPASS:
								// Show progress dialog
								createProgressBarForTask(ASYNC_TASK_CREATE,
										mCreateVolumeName);

								// Launch async task to create volume
								mAsyncTask = new CreateVolumeTask(mProgDialog,
										mVolumeFileSystem);
								mAsyncTaskId = ASYNC_TASK_CREATE;
								mAsyncTask.setActivity(VolumeListActivity.this);
								mAsyncTask.execute(mVolPickerResult,
										mCreateVolumeName, value.toString());
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

			input = (EditText) inflater.inflate(R.layout.dialog_edit, null);

			alertBuilder.setTitle(getString(R.string.voladd_dialog_title_str));
			alertBuilder.setView(input);
			alertBuilder.setPositiveButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							Editable value = input.getText();
							switch (myId) {
							case DIALOG_VOL_NAME:
								if(mVolConfigResult == null) {
									importVolume(value.toString(),
											mVolPickerResult, mVolumeFileSystem);
								}
								else {
									importVolumeWithConfig(value.toString(),
											mVolPickerResult, mVolConfigResult, mVolumeFileSystem);
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
							createProgressBarForTask(ASYNC_TASK_LAUNCH_CHOOSER,
									null);

							// Launch async task for launching file chooser -
							// needed for network auth
							mAsyncTask = new LaunchChooserTask(mProgDialog,
									item);
							mAsyncTaskId = ASYNC_TASK_LAUNCH_CHOOSER;
							mAsyncTask.setActivity(VolumeListActivity.this);
							mAsyncTask.execute();
						}
					});
			alertDialog = alertBuilder.create();
			break;
		case DIALOG_VOL_DELETE:
			if (mSelectedVolume == null) {
				// Can happen when restoring a killed activity
				return null;
			}

			final CharSequence[] items = { getString(R.string.delete_vol_dialog_disk_str) };
			final boolean[] states = { true };

			alertBuilder.setTitle(String.format(
					getString(R.string.delete_vol_dialog_confirm_str),
					mSelectedVolume.getName()));
			alertBuilder.setMultiChoiceItems(items, states,
					new DialogInterface.OnMultiChoiceClickListener() {
						public void onClick(DialogInterface dialogInterface,
								int item, boolean state) {
						}
					});
			alertBuilder.setPositiveButton(getString(R.string.btn_ok_str),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							SparseBooleanArray checked = ((AlertDialog) dialog)
									.getListView().getCheckedItemPositions();
							if (checked.get(0)) {
								// Delete volume from disk
								createProgressBarForTask(ASYNC_TASK_DELETE,
										mSelectedVolume.getName());

								// Launch async task to delete volume
								mAsyncTask = new DeleteVolumeTask(mProgDialog,
										mSelectedVolume);
								mAsyncTaskId = ASYNC_TASK_DELETE;
								mAsyncTask.setActivity(VolumeListActivity.this);
								mAsyncTask.execute();
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
			mVolConfigResult= data.getExtras().getString(
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
		Volume volume = new Volume(volumeName, volumePath, configPath, fileSystem);
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
			createProgressBarForTask(ASYNC_TASK_UNLOCK_CACHE, null);

			mAsyncTask = new UnlockVolumeTask(null, mVolumeFileSystem,
					cachedKey);
			mAsyncTaskId = ASYNC_TASK_UNLOCK_CACHE;
			mAsyncTask.setActivity(VolumeListActivity.this);
			if(mSelectedVolume.getCustomConfigPath() == null) {
				mAsyncTask.execute(mSelectedVolume.getPath(), null);
			}
			else {
				mAsyncTask.execute(mSelectedVolume.getPath(), null,
						mSelectedVolume.getCustomConfigPath());
			}
		}
	}

	private void createProgressBarForTask(int taskId, String strArg) {
		mProgDialog = new ProgressDialog(VolumeListActivity.this);
		switch (taskId) {
		case ASYNC_TASK_CREATE:
			mProgDialog.setTitle(String.format(
					getString(R.string.mkvol_dialog_title_str), strArg));
			break;
		case ASYNC_TASK_DELETE:
			mProgDialog.setTitle(String.format(
					getString(R.string.delvol_dialog_title_str), strArg));
			break;
		case ASYNC_TASK_UNLOCK_CACHE:
			mProgDialog.setTitle(getString(R.string.unlocking_volume));
			break;
		case ASYNC_TASK_UNLOCK_PBKDF2:
			mProgDialog.setTitle(getString(R.string.pbkdf_dialog_title_str));
			mProgDialog.setMessage(getString(R.string.pbkdf_dialog_msg_str));
			break;
		case ASYNC_TASK_LAUNCH_CHOOSER:
			mProgDialog.setTitle(getString(R.string.launching_chooser));
			break;
		default:
			Log.e(TAG, "Unknown task ID: " + taskId);
			break;
		}

		mProgDialog.setCancelable(false);
		mProgDialog.show();
		mSavedProgBarStrArg = strArg;
	}

	private class UnlockVolumeTask extends
			EDAsyncTask<String, Void, EncFSVolume> {

		// File system for volume
		private FileSystem mFileSystem;

		// Cached key
		private byte[] cachedKey;

		// Invalid cached key
		boolean invalidCachedKey = false;

		public UnlockVolumeTask(ProgressDialog dialog, FileSystem fileSystem,
				byte[] cachedKey) {
			super();
			setProgressDialog(dialog);
			this.mFileSystem = fileSystem;
			this.cachedKey = cachedKey;
		}

		@SuppressWarnings("deprecation")
		@SuppressLint("Wakelock")
		@Override
		protected EncFSVolume doInBackground(String... args) {

			WakeLock wl = null;
			EncFSVolume volume = null;

			if (cachedKey == null) {
				PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
				wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG);

				// Acquire wake lock to prevent screen from dimming/timing out
				wl.acquire();
			}

			// link or authenticate account if needed
			Account account = mVolumeFileSystem.getAccount();
			if (account != null) {
				if (account.linkOrAuthIfNeeded(VolumeListActivity.this, TAG) == false) {
					mErrDialogText = String.format(
							getString(R.string.account_login_error),
							mVolumeFileSystem.getName());
					return null;
				}
			}

			// Get file provider for this file system
						EncFSFileProvider fileProvider = mFileSystem
							.getFileProvider(args[0]);
						EncFSConfig volConfig = null;
						if(args.length>2)
						{
							File config = new File(args[2]);
							try {
								volConfig = EncFSConfigParser.parseFile(config );
							} catch (Exception e) {
								Logger.logException(TAG, e);
								((VolumeListActivity) getActivity()).mErrDialogText = e
										.getMessage();
							}
					}
		// Unlock the volume, takes long due to PBKDF2 calculation
			try {
				if (cachedKey == null) {
					if(volConfig != null) {
					volume = new EncFSVolumeBuilder()
							.withFileProvider(fileProvider)
							.withConfig(volConfig)
							.withPassword(args[1]).buildVolume();
					} else {
					volume = new EncFSVolumeBuilder()
							.withFileProvider(fileProvider)
							.withPbkdf2Provider(mApp.getNativePBKDF2Provider())
							.withPassword(args[1]).buildVolume();
					}
					} else {
						if(volConfig != null) {
							volume = new EncFSVolumeBuilder()
								.withFileProvider(fileProvider)
								.withConfig(volConfig)
								.withDerivedKeyData(cachedKey).buildVolume();
							} else {
								volume = new EncFSVolumeBuilder()
									.withFileProvider(fileProvider)
									.withDerivedKeyData(cachedKey).buildVolume();
							}
						}
			} catch (EncFSInvalidPasswordException e) {
				if (cachedKey != null) {
					invalidCachedKey = true;
				} else {
					((VolumeListActivity) getActivity()).mErrDialogText = getString(R.string.incorrect_pwd_str);
				}
			} catch (Exception e) {
				Logger.logException(TAG, e);
				((VolumeListActivity) getActivity()).mErrDialogText = e
						.getMessage();
			}

			if (cachedKey == null) {
				// Release the wake lock
				wl.release();
			}

			return volume;
		}

		// Run after the task is complete
		@Override
		protected void onPostExecute(EncFSVolume result) {
			super.onPostExecute(result);

			final VolumeListActivity mActivity = (VolumeListActivity) getActivity();

			if (myDialog != null) {
				if (myDialog.isShowing()) {
					myDialog.dismiss();
				}
			}

			if (!isCancelled()) {
				if (invalidCachedKey == true) {
					// Show toast for invalid password
					Toast.makeText(getApplicationContext(),
							getString(R.string.save_pass_invalid_str),
							Toast.LENGTH_SHORT).show();

					// Invalidate cached key from DB
					mActivity.mApp.getDbHelper().clearKey(mSelectedVolume);

					// Kick off password dialog
					mActivity.showDialog(DIALOG_VOL_PASS);

					return;
				}

				if (result != null) {
					mActivity.mSelectedVolume.unlock(result);

					// Notify list adapter change from UI thread
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mActivity.mAdapter.notifyDataSetChanged();
						}
					});

					if (cachedKey == null) {
						// Cache key in DB if preference is enabled
						if (mActivity.mPrefs.getBoolean("cache_key", false)) {
							byte[] keyToCache = result.getDerivedKeyData();
							mActivity.mApp.getDbHelper().cacheKey(
									mSelectedVolume, keyToCache);
						}
					}

					mActivity.launchVolumeBrowser(mSelectedVolIdx);
				} else {
					mActivity.showDialog(DIALOG_ERROR);
				}
			}
		}
	}

	private class CreateVolumeTask extends EDAsyncTask<String, Void, Boolean> {

		// Name of the volume being created
		private String volumeName;

		// Path of the volume
		private String volumePath;

		// Volume type
		private FileSystem volumeFs;

		// Password
		private String password;

		public CreateVolumeTask(ProgressDialog dialog, FileSystem fileSystem) {
			super();
			this.volumeFs = fileSystem;
			setProgressDialog(dialog);
		}

		@Override
		protected Boolean doInBackground(String... args) {

			volumeName = args[1];
			password = args[2];

			// link or authenticate account if needed
			Account account = volumeFs.getAccount();
			if (account != null) {
				if (account.linkOrAuthIfNeeded(VolumeListActivity.this, TAG) == false) {
					mErrDialogText = String.format(
							getString(R.string.account_login_error),
							volumeFs.getName());
					return false;
				}
			}

			EncFSFileProvider rootProvider = volumeFs.getFileProvider("/");

			try {
				if (!rootProvider.exists(args[0])) {
					mErrDialogText = String.format(
							getString(R.string.error_dir_not_found), args[0]);
					return false;
				}

				volumePath = EncFSVolume.combinePath(args[0], volumeName
						+ NEW_VOLUME_DIR_SUFFIX);

				if (rootProvider.exists(volumePath)) {
					mErrDialogText = getString(R.string.error_file_exists);
					return false;
				}

				// Create the new directory
				if (!rootProvider.mkdir(volumePath)) {
					mErrDialogText = String.format(
							getString(R.string.error_mkdir_fail), volumePath);
					return false;
				}
			} catch (IOException e) {
				mErrDialogText = e.getMessage();
				return false;
			}

			EncFSFileProvider fileProvider = volumeFs
					.getFileProvider(volumePath);

			// Create the volume
			try {
				new EncFSVolumeBuilder().withFileProvider(fileProvider)
						.withConfig(EncFSConfigFactory.createDefault())
						.withPbkdf2Provider(mApp.getNativePBKDF2Provider())
						.withPassword(password).writeVolumeConfig();
			} catch (Exception e) {
				mErrDialogText = e.getMessage();
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

			if (!isCancelled()) {
				if (result) {
					((VolumeListActivity) getActivity()).importVolume(
							volumeName, volumePath, volumeFs);
				} else {
					((VolumeListActivity) getActivity())
							.showDialog(DIALOG_ERROR);
				}
			}
		}
	}

	private class DeleteVolumeTask extends EDAsyncTask<String, Void, Boolean> {

		// Volume being deleted
		private Volume volume;

		public DeleteVolumeTask(ProgressDialog dialog, Volume volume) {
			super();
			setProgressDialog(dialog);
			this.volume = volume;
		}

		@Override
		protected Boolean doInBackground(String... args) {

			// link or authenticate account if needed
			Account account = volume.getFileSystem().getAccount();
			if (account != null) {
				if (account.linkOrAuthIfNeeded(VolumeListActivity.this, TAG) == false) {
					mErrDialogText = String.format(
							getString(R.string.account_login_error), volume
									.getFileSystem().getName());
					return false;
				}
			}

			EncFSFileProvider rootProvider = volume.getFileSystem()
					.getFileProvider("/");

			try {
				if (!rootProvider.exists(volume.getPath())) {
					mErrDialogText = String.format(
							getString(R.string.error_dir_not_found),
							volume.getPath());
					return false;
				}

				// Delete the volume
				if (!rootProvider.delete(volume.getPath())) {
					mErrDialogText = String.format(
							getString(R.string.error_delete_fail),
							volume.getPath());
					return false;
				}
			} catch (IOException e) {
				mErrDialogText = e.getMessage();
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

			if (!isCancelled()) {
				if (result) {
					((VolumeListActivity) getActivity()).deleteVolume(volume);
				} else {
					((VolumeListActivity) getActivity())
							.showDialog(DIALOG_ERROR);
				}
			}
		}
	}

	private class LaunchChooserTask extends EDAsyncTask<String, Void, Boolean> {

		// Selected volume index
		private int item;

		public LaunchChooserTask(ProgressDialog dialog, int item) {
			super();
			setProgressDialog(dialog);
			this.item = item;
		}

		@Override
		protected Boolean doInBackground(String... args) {

			FileSystem fs = mApp.getFileSystemList().get(item);

			// link or authenticate account if needed
			Account account = fs.getAccount();
			if (account != null) {
				if (account.linkOrAuthIfNeeded(VolumeListActivity.this, TAG) == false) {
					mErrDialogText = String.format(
							getString(R.string.account_login_error),
							fs.getName());
					return false;
				}
			}

			if (mVolumeOp == VOLUME_OP_IMPORT) {
				launchFileChooser(FileChooserActivity.VOLUME_PICKER_MODE, item);
			} else {
				launchFileChooser(FileChooserActivity.CREATE_VOLUME_MODE, item);
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

			if (!isCancelled()) {
				if (!result) {
					((VolumeListActivity) getActivity())
							.showDialog(DIALOG_ERROR);
				}
			}
		}
	}
}
