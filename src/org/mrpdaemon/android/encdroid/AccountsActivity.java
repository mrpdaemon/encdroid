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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class AccountsActivity extends Activity {

	// Error dialog
	private final static int DIALOG_ERROR = 0;

	// Logger tag
	private final static String TAG = "EDAccountsActivity";

	// Application object
	private EDApplication mApp;

	// Dropbox object
	private DropboxAccount mDropbox;

	// Action bar object
	private ActionBarHelper mActionBar = null;

	// Text for the error dialog
	private String mErrDialogText = "";

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mApp = (EDApplication) getApplication();
		mDropbox = mApp.getDropbox();

		setContentView(R.layout.accounts);
		setTitle(getString(R.string.accounts));

		if (mApp.isActionBarAvailable()) {
			mActionBar = new ActionBarHelper(this);
			mActionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// Go back to volume list
			Intent intent = new Intent(this, VolumeListActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			break;
		default:
			break;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onKeyDown(int, android.view.KeyEvent)
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			// Go back to volume list
			Intent intent = new Intent(this, VolumeListActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);

			return true;
		}
		return super.onKeyDown(keyCode, event);
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

	private void fill() {

		TextView dropboxStatus = (TextView) findViewById(R.id.accounts_dropbox_status);
		String status;

		if (mDropbox.isLinked()) {
			status = String.format(getString(R.string.account_linked),
					mDropbox.getUserName());
		} else {
			status = getString(R.string.account_not_linked);
		}

		if (mDropbox.isAuthenticated()) {
			status = String.format(getString(R.string.account_logged_in),
					mDropbox.getUserName());
		}

		dropboxStatus.setText(status);

		Button accountButton = (Button) findViewById(R.id.accounts_button);

		if (mDropbox.isLinked()) {
			accountButton.setText(getString(R.string.account_unlink_btn_str));
			accountButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					// Unlink the account
					mDropbox.unlink();
					fill();
				}
			});
		} else {
			accountButton.setText(getString(R.string.account_link_btn_str));
			accountButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					// Start linking an account
					mDropbox.startLinkorAuth(AccountsActivity.this);
				}
			});
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onStart() {
		super.onStart();
		fill();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
		if (mDropbox.isLinkInProgress()) {
			boolean success = mDropbox.resumeLinking();

			if (success == false) {
				mErrDialogText = getString(R.string.dropbox_login_error);
				showDialog(DIALOG_ERROR);
			} else {
				fill();
			}
		}
	}
}
