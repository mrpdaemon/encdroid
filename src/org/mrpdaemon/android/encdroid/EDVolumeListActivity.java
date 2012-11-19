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
import java.io.IOException;

import org.mrpdaemon.sec.encfs.EncFSConfig;
import org.mrpdaemon.sec.encfs.EncFSFileProvider;
import org.mrpdaemon.sec.encfs.EncFSInvalidPasswordException;
import org.mrpdaemon.sec.encfs.EncFSLocalFileProvider;
import org.mrpdaemon.sec.encfs.EncFSVolume;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.text.Editable;
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

public class EDVolumeListActivity extends ListActivity {

	// Request into EDFileChooserActivity to run in different modes
	private final static int LOCAL_VOLUME_PICKER_REQUEST = 0;
	private final static int LOCAL_VOLUME_CREATE_REQUEST = 1;
	private final static int EXT_SD_VOLUME_PICKER_REQUEST = 2;
	private final static int EXT_SD_VOLUME_CREATE_REQUEST = 3;
	private final static int DROPBOX_VOLUME_PICKER_REQUEST = 4;
	private final static int DROPBOX_VOLUME_CREATE_REQUEST = 5;

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

	// Logger tag
	private final static String TAG = "EDVolumeListActivity";

	// Suffix for newly created volume directories
	private final static String NEW_VOLUME_DIR_SUFFIX = ".encdroid";

	// List adapter
	private EDVolumeListAdapter mAdapter = null;

	// Application object
	private EDApplication mApp;

	// Currently selected EDVolumeList item
	private EDVolume mSelectedVolume;
	private int mSelectedVolIdx;

	// Async task object for running volume key derivation
	private AsyncTask<String, ?, ?> mAsyncTask = null;

	// Progress dialog for async progress
	private ProgressDialog mProgDialog = null;

	// Result from the volume picker activity
	private String mVolPickerResult = null;

	// Text for the error dialog
	private String mErrDialogText = "";

	// Name of the volume being created
	private String mCreateVolumeName = "";

	// Current volume operation (import/create)
	private int mVolumeOp = -1;

	// FS type for current volume operation
	private int mVolumeType = -1;

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
		refreshList();
	}

	// Create the options menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.volume_list_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	// Launch the file chooser activity in the requested mode
	private void launchFileChooser(int mode, int fsType) {
		Intent startFileChooser = new Intent(this, EDFileChooserActivity.class);

		Bundle fileChooserParams = new Bundle();
		fileChooserParams.putInt(EDFileChooserActivity.MODE_KEY, mode);
		fileChooserParams.putInt(EDFileChooserActivity.FS_TYPE_KEY, fsType);
		startFileChooser.putExtras(fileChooserParams);

		int request = 0;

		switch (mode) {
		case EDFileChooserActivity.VOLUME_PICKER_MODE:
			if (fsType == EDFileChooserActivity.LOCAL_FS) {
				request = LOCAL_VOLUME_PICKER_REQUEST;
			} else if (fsType == EDFileChooserActivity.EXT_SD_FS) {
				request = EXT_SD_VOLUME_PICKER_REQUEST;
			} else if (fsType == EDFileChooserActivity.DROPBOX_FS) {
				request = DROPBOX_VOLUME_PICKER_REQUEST;
			}
			break;
		case EDFileChooserActivity.CREATE_VOLUME_MODE:
			if (fsType == EDFileChooserActivity.LOCAL_FS) {
				request = LOCAL_VOLUME_CREATE_REQUEST;
			} else if (fsType == EDFileChooserActivity.EXT_SD_FS) {
				request = EXT_SD_VOLUME_CREATE_REQUEST;
			} else if (fsType == EDFileChooserActivity.DROPBOX_FS) {
				request = DROPBOX_VOLUME_CREATE_REQUEST;
			}
			break;
		default:
			Log.e(TAG, "Unknown mode id: " + mode);
			return;
		}

		startActivityForResult(startFileChooser, request);
	}

	// Launch the volume browser activity for the given volume
	private void launchVolumeBrowser(int volIndex) {
		Intent startVolumeBrowser = new Intent(this,
				EDVolumeBrowserActivity.class);

		Bundle volumeBrowserParams = new Bundle();
		volumeBrowserParams
				.putInt(EDVolumeBrowserActivity.VOL_ID_KEY, volIndex);
		startVolumeBrowser.putExtras(volumeBrowserParams);

		startActivity(startVolumeBrowser);
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
			Intent showAccounts = new Intent(this, EDAccountsActivity.class);
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
		EDDropbox dropbox = mApp.getDropbox();

		if (dropbox.isLinkInProgress()) {
			boolean success = dropbox.resumeLinking();

			if (success == false) {
				mErrDialogText = getString(R.string.dropbox_login_error);
				showDialog(DIALOG_ERROR);
			}
		}
	}

	private void refreshList() {
		if (mAdapter == null) {
			mAdapter = new EDVolumeListAdapter(this, R.layout.volume_list_item,
					mApp.getVolumeList());
			this.setListAdapter(mAdapter);
		} else {
			mAdapter.notifyDataSetChanged();
		}
	}

	private void importVolume(String volumeName, String volumePath,
			int volumeType) {
		EDVolume volume = new EDVolume(volumeName, volumePath, volumeType);
		mApp.getVolumeList().add(volume);
		mApp.getDbHelper().insertVolume(volume);
		refreshList();
	}

	private void deleteVolume(EDVolume volume) {
		mApp.getVolumeList().remove(volume);
		mApp.getDbHelper().deleteVolume(volume);
		refreshList();
	}

	private void renameVolume(EDVolume volume, String newName) {
		mApp.getDbHelper().renameVolume(volume, newName);
		volume.setName(newName);
		refreshList();
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
		EDVolume selected = mAdapter.getItem((int) info.id);

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
		EDVolume selected = mAdapter.getItem((int) info.id);

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
		final EditText input;
		boolean rename = false;

		switch (id) {
		case DIALOG_VOL_RENAME:
			rename = true;
		case DIALOG_VOL_PASS:
		case DIALOG_VOL_CREATEPASS:
		case DIALOG_VOL_NAME:
		case DIALOG_VOL_CREATE:
			input = (EditText) dialog.findViewById(R.id.dialog_edit_text);
			if (input != null) {
				if (rename) {
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
								mProgDialog = new ProgressDialog(
										EDVolumeListActivity.this);
								mProgDialog
										.setTitle(getString(R.string.pbkdf_dialog_title_str));
								mProgDialog
										.setMessage(getString(R.string.pbkdf_dialog_msg_str));
								mProgDialog.setCancelable(true);
								mProgDialog
										.setOnCancelListener(new OnCancelListener() {
											@Override
											public void onCancel(
													DialogInterface dialog) {
												EDVolumeListActivity.this
														.cancelAsyncTask();
											}
										});
								mProgDialog.show();

								// Launch async task to import volume
								mAsyncTask = new EDUnlockVolumeTask(
										mProgDialog, mVolumeType, null);
								mAsyncTask.execute(mSelectedVolume.getPath(),
										value.toString());
								break;
							case DIALOG_VOL_CREATEPASS:
								// Show progress dialog
								mProgDialog = new ProgressDialog(
										EDVolumeListActivity.this);
								mProgDialog.setTitle(String
										.format(getString(R.string.mkvol_dialog_title_str),
												mCreateVolumeName));
								mProgDialog.setCancelable(true);
								mProgDialog
										.setOnCancelListener(new OnCancelListener() {
											@Override
											public void onCancel(
													DialogInterface dialog) {
												EDVolumeListActivity.this
														.cancelAsyncTask();
											}
										});
								mProgDialog.show();

								// Launch async task to create volume
								mAsyncTask = new EDCreateVolumeTask(
										mProgDialog, mVolumeType);
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
								importVolume(value.toString(),
										mVolPickerResult, mVolumeType);
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
			boolean extSd = mPrefs.getBoolean("ext_sd_enabled", false);
			CharSequence[] fsTypes;
			if (extSd == true) {
				fsTypes = new CharSequence[3];
				fsTypes[0] = getString(R.string.fs_dialog_local);
				fsTypes[1] = "Dropbox";
				fsTypes[2] = getString(R.string.fs_dialog_ext_sd);
			} else {
				fsTypes = new CharSequence[2];
				fsTypes[0] = getString(R.string.fs_dialog_local);
				fsTypes[1] = "Dropbox";
			}
			alertBuilder.setTitle(getString(R.string.fs_type_dialog_title_str));
			alertBuilder.setItems(fsTypes,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int item) {
							switch (item) {
							case 0:
								if (mVolumeOp == VOLUME_OP_IMPORT) {
									launchFileChooser(
											EDFileChooserActivity.VOLUME_PICKER_MODE,
											EDFileChooserActivity.LOCAL_FS);
								} else {
									launchFileChooser(
											EDFileChooserActivity.CREATE_VOLUME_MODE,
											EDFileChooserActivity.LOCAL_FS);
								}
								break;
							case 1:
								EDDropbox dropbox = mApp.getDropbox();

								if (!dropbox.isAuthenticated()) {
									dropbox.startLinkorAuth(EDVolumeListActivity.this);
									if (!dropbox.isAuthenticated()) {
										return;
									}
								}

								if (mVolumeOp == VOLUME_OP_IMPORT) {
									launchFileChooser(
											EDFileChooserActivity.VOLUME_PICKER_MODE,
											EDFileChooserActivity.DROPBOX_FS);
								} else {
									launchFileChooser(
											EDFileChooserActivity.CREATE_VOLUME_MODE,
											EDFileChooserActivity.DROPBOX_FS);
								}
								break;
							case 2:
								if (mVolumeOp == VOLUME_OP_IMPORT) {
									launchFileChooser(
											EDFileChooserActivity.VOLUME_PICKER_MODE,
											EDFileChooserActivity.EXT_SD_FS);
								} else {
									launchFileChooser(
											EDFileChooserActivity.CREATE_VOLUME_MODE,
											EDFileChooserActivity.EXT_SD_FS);
								}
								break;
							}
						}
					});
			alertDialog = alertBuilder.create();
			break;
		case DIALOG_VOL_DELETE:
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
								mProgDialog = new ProgressDialog(
										EDVolumeListActivity.this);
								mProgDialog.setTitle(String
										.format(getString(R.string.delvol_dialog_title_str),
												mSelectedVolume.getName()));
								mProgDialog.setCancelable(true);
								mProgDialog
										.setOnCancelListener(new OnCancelListener() {
											@Override
											public void onCancel(
													DialogInterface dialog) {
												EDVolumeListActivity.this
														.cancelAsyncTask();
											}
										});
								mProgDialog.show();

								// Dropbox auth if needed
								if (mSelectedVolume.getType() == EDVolume.DROPBOX_VOLUME) {
									EDDropbox dropbox = mApp.getDropbox();

									if (!dropbox.isAuthenticated()) {
										dropbox.startLinkorAuth(EDVolumeListActivity.this);
										if (!dropbox.isAuthenticated()) {
											return;
										}
									}
								}

								// Launch async task to delete volume
								mAsyncTask = new EDDeleteVolumeTask(
										mProgDialog, mSelectedVolume);
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

	/**
	 * Unlock the currently selected volume
	 */
	private void unlockSelectedVolume() {
		mVolumeType = mSelectedVolume.getType();

		if (mVolumeType == EDVolume.DROPBOX_VOLUME) {
			EDDropbox dropbox = mApp.getDropbox();

			if (!dropbox.isAuthenticated()) {
				dropbox.startLinkorAuth(EDVolumeListActivity.this);
				if (!dropbox.isAuthenticated()) {
					return;
				}
			}
		}

		// If key caching is enabled, see if a key is cached
		byte[] cachedKey = null;
		if (mPrefs.getBoolean("cache_key", false)) {
			cachedKey = mApp.getDbHelper().getCachedKey(mSelectedVolume);
		}

		if (cachedKey == null) {
			showDialog(DIALOG_VOL_PASS);
		} else {
			mProgDialog = new ProgressDialog(EDVolumeListActivity.this);
			mProgDialog.setTitle(getString(R.string.unlocking_volume));
			mProgDialog.setCancelable(true);
			mProgDialog.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					EDVolumeListActivity.this.cancelAsyncTask();
				}
			});
			mProgDialog.show();

			mAsyncTask = new EDUnlockVolumeTask(null, mVolumeType, cachedKey);
			mAsyncTask.execute(mSelectedVolume.getPath(), null);
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
					EDFileChooserActivity.RESULT_KEY);

			switch (requestCode) {
			case LOCAL_VOLUME_PICKER_REQUEST:
				mVolumeType = EDVolume.LOCAL_VOLUME;
				showDialog(DIALOG_VOL_NAME);
				break;
			case EXT_SD_VOLUME_PICKER_REQUEST:
				mVolumeType = EDVolume.EXT_SD_VOLUME;
				showDialog(DIALOG_VOL_NAME);
				break;
			case DROPBOX_VOLUME_PICKER_REQUEST:
				mVolumeType = EDVolume.DROPBOX_VOLUME;
				showDialog(DIALOG_VOL_NAME);
				break;
			case LOCAL_VOLUME_CREATE_REQUEST:
				mVolumeType = EDVolume.LOCAL_VOLUME;
				showDialog(DIALOG_VOL_CREATE);
				break;
			case EXT_SD_VOLUME_CREATE_REQUEST:
				mVolumeType = EDVolume.EXT_SD_VOLUME;
				showDialog(DIALOG_VOL_CREATE);
				break;
			case DROPBOX_VOLUME_CREATE_REQUEST:
				mVolumeType = EDVolume.DROPBOX_VOLUME;
				showDialog(DIALOG_VOL_CREATE);
				break;
			default:
				Log.e(TAG, "File chooser called with unknown request code: "
						+ requestCode);
				break;
			}
		} else {
			Log.e(TAG, "File chooser returned unexpected return code: "
					+ resultCode);
		}
	}

	// Cancel the async task and hide the progress bar
	private void cancelAsyncTask() {
		if (mAsyncTask != null) {
			mAsyncTask.cancel(true);
			mAsyncTask = null;
		}

		if (mProgDialog != null) {
			mProgDialog.dismiss();
			mProgDialog = null;
		}
	}

	// Back button press handler - cancel the async task
	@Override
	public void onBackPressed() {
		super.onBackPressed();
		cancelAsyncTask();
	}

	// Pause handler, cancel async task when the activity isn't foreground any
	// more
	@Override
	protected void onPause() {
		super.onPause();
		cancelAsyncTask();
	}

	private EncFSFileProvider getFileProvider(int volumeType, String relPath) {
		switch (volumeType) {
		case EDVolume.LOCAL_VOLUME:
			return new EncFSLocalFileProvider(new File(
					Environment.getExternalStorageDirectory(), relPath));
		case EDVolume.DROPBOX_VOLUME:
			return new EDDropboxFileProvider(mApp.getDropbox().getApi(),
					relPath);
		case EDVolume.EXT_SD_VOLUME:
			return new EncFSLocalFileProvider(new File(mPrefs.getString(
					"ext_sd_location", "/mnt/external1"), relPath));
		default:
			Log.e(TAG, "Unknown volume type");
			return null;
		}
	}

	private class EDUnlockVolumeTask extends
			AsyncTask<String, Void, EncFSVolume> {

		// The progress dialog for this task
		private ProgressDialog myDialog;

		// Volume type
		private int volumeType;

		// Cached key
		private byte[] cachedKey;

		// Invalid cached key
		boolean invalidCachedKey = false;

		public EDUnlockVolumeTask(ProgressDialog dialog, int volumeType,
				byte[] cachedKey) {
			super();
			this.myDialog = dialog;
			this.volumeType = volumeType;
			this.cachedKey = cachedKey;
		}

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

			// Get file provider for this volume
			EncFSFileProvider fileProvider = getFileProvider(volumeType,
					args[0]);

			// Unlock the volume, takes long due to PBKDF2 calculation
			try {
				if (cachedKey == null) {
					volume = new EncFSVolume(fileProvider, args[1],
							mApp.getNativePBKDF2Provider());
				} else {
					volume = new EncFSVolume(fileProvider, cachedKey);
				}
			} catch (EncFSInvalidPasswordException e) {
				if (cachedKey != null) {
					invalidCachedKey = true;
				} else {
					mErrDialogText = getString(R.string.incorrect_pwd_str);
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
				mErrDialogText = e.getMessage();
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
					mApp.getDbHelper().clearKey(mSelectedVolume);

					// Kick off password dialog
					showDialog(DIALOG_VOL_PASS);

					return;
				}

				if (result != null) {
					mSelectedVolume.unlock(result);

					mAdapter.notifyDataSetChanged();

					if (cachedKey == null) {
						// Cache key in DB if preference is enabled
						if (mPrefs.getBoolean("cache_key", false)) {
							byte[] keyToCache = result.getPasswordKey();
							mApp.getDbHelper().cacheKey(mSelectedVolume,
									keyToCache);
						}
					}

					launchVolumeBrowser(mSelectedVolIdx);
				} else {
					showDialog(DIALOG_ERROR);
				}
			}
		}
	}

	private class EDCreateVolumeTask extends AsyncTask<String, Void, Boolean> {

		// The progress dialog for this task
		private ProgressDialog myDialog;

		// Name of the volume being created
		private String volumeName;

		// Path of the volume
		private String volumePath;

		// Volume type
		private int volumeType;

		// Password
		private String password;

		public EDCreateVolumeTask(ProgressDialog dialog, int volumeType) {
			super();
			this.myDialog = dialog;
			this.volumeType = volumeType;
		}

		@Override
		protected Boolean doInBackground(String... args) {

			volumeName = args[1];
			password = args[2];

			EncFSFileProvider rootProvider = getFileProvider(volumeType, "/");

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

			EncFSFileProvider volumeProvider = getFileProvider(volumeType,
					volumePath);

			// Create the volume
			try {
				EncFSVolume.createVolume(volumeProvider, new EncFSConfig(),
						password, mApp.getNativePBKDF2Provider());
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
					importVolume(volumeName, volumePath, volumeType);
				} else {
					showDialog(DIALOG_ERROR);
				}
			}
		}
	}

	private class EDDeleteVolumeTask extends AsyncTask<String, Void, Boolean> {

		// The progress dialog for this task
		private ProgressDialog myDialog;

		// Volume being deleted
		private EDVolume volume;

		public EDDeleteVolumeTask(ProgressDialog dialog, EDVolume volume) {
			super();
			this.myDialog = dialog;
			this.volume = volume;
		}

		@Override
		protected Boolean doInBackground(String... args) {

			EncFSFileProvider rootProvider = getFileProvider(volume.getType(),
					"/");

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
					deleteVolume(volume);
				} else {
					showDialog(DIALOG_ERROR);
				}
			}
		}
	}
}