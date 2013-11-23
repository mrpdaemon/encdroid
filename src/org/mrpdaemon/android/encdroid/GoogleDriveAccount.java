/*
 * encdroid - EncFS client application for Android
 * Copyright (C) 2013  Mark R. Pariente
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
import java.util.Arrays;

import org.mrpdaemon.sec.encfs.EncFSFileProvider;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.widget.Toast;

public class GoogleDriveAccount extends Account {

	// Activity request codes
	public static final int REQUEST_ACCOUNT_PICKER = 31;
	public static final int REQUEST_AUTH_TOKEN = 32;

	// Preference keys
	private final static String PREFS_KEY = "google_drive_prefs";
	private final static String PREF_LINKED = "is_linked";
	private final static String PREF_ACCOUNT_NAME = "user_name";

	// Login toast type
	private static enum LoginResult {
		OK, FAILED, NO_PLAY_SERVICES
	};

	// Logger tag
	private final static String TAG = "GoogleDriveAccount";

	// Whether we're linked to an account
	private boolean linked;

	// Whether link is in progress
	private boolean linkInProgress;

	// Whether authentication is in progress
	private boolean authInProgress;

	// Whether we're authenticated
	private boolean authenticated;

	// Saved preferences
	private SharedPreferences mPrefs;

	// Account name
	private String accountName = null;

	// Credential object
	private GoogleAccountCredential credential = null;

	// Drive API object
	private Drive driveService = null;

	// Create drive service
	private void createDriveService(String accountName) {
		credential.setSelectedAccountName(accountName);

		driveService = new Drive.Builder(AndroidHttp.newCompatibleTransport(),
				new GsonFactory(), credential).build();

		Log.v(TAG, "Drive service created: " + driveService.toString());
	}

	// Show toast when logged in
	private void showLoginToast(final Activity activity,
			final LoginResult result) {
		if (activity != null) {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					int stringId = 0;
					switch (result) {
					case OK:
						stringId = R.string.google_drive_login;
						break;
					case FAILED:
						stringId = R.string.google_drive_login_failed;
						break;
					case NO_PLAY_SERVICES:
						stringId = R.string.google_drive_no_play_services;
						break;
					default:
						stringId = 0;
						break;
					}
					Toast.makeText(activity, activity.getString(stringId),
							Toast.LENGTH_SHORT).show();
				}
			});
		}
	}

	// Kick off authentication thread
	private void startAuthThread(final Activity activity) {

		authInProgress = true;

		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					credential.getToken();

					// Do an about request to test if the API works
					driveService.about().get().execute();

					Log.v(TAG, "Already authenticated to Google API");
					showLoginToast(activity, LoginResult.OK);
					authenticated = true;
				} catch (UserRecoverableAuthException e) {
					Logger.logException(TAG, e);
					if (activity != null) {
						activity.startActivityForResult(e.getIntent(),
								REQUEST_AUTH_TOKEN);
					}
				} catch (IOException e) {
					if (activity != null) {
						showLoginToast(activity, LoginResult.FAILED);
					}
					Logger.logException(TAG, e);
				} catch (GoogleAuthException e) {
					if (activity != null) {
						showLoginToast(activity, LoginResult.FAILED);
					}
					Logger.logException(TAG, e);
				} finally {
					authInProgress = false;
				}
			}
		});

		thread.start();
	}

	public GoogleDriveAccount(EDApplication app) {
		mPrefs = app.getSharedPreferences(PREFS_KEY, 0);

		linkInProgress = false;
		authInProgress = false;
		authenticated = false;

		credential = GoogleAccountCredential.usingOAuth2(
				app.getApplicationContext(), Arrays.asList(DriveScopes.DRIVE));

		// Figure out whether we're linked to an account
		linked = mPrefs.getBoolean(PREF_LINKED, false);
		if (linked) {
			accountName = mPrefs.getString(PREF_ACCOUNT_NAME, null);

			// Kick off authentication thread
			createDriveService(accountName);
			startAuthThread(null);
		}
	}

	@Override
	public String getName() {
		return "Google Drive";
	}

	@Override
	public int getIconResId() {
		return R.drawable.ic_google_drive;
	}

	@Override
	public boolean isLinked() {
		return linked;
	}

	@Override
	public boolean isAuthenticated() {
		return authenticated;
	}

	@Override
	public void startLinkOrAuth(Context context) {
		Activity activity = null;

		// We might be called from a non-Activity context, so safely dereference
		if (context instanceof Activity) {
			activity = (Activity) context;
		}

		// Select account to link
		if (linked == false) {
			linkInProgress = true;
			try {
				activity.startActivityForResult(
						credential.newChooseAccountIntent(),
						REQUEST_ACCOUNT_PICKER);
			} catch (ActivityNotFoundException e) {
				// User doesn't have Google Play Services Framework installed
				showLoginToast(activity, LoginResult.NO_PLAY_SERVICES);
				Logger.logException(TAG, e);
			}
		} else {
			createDriveService(accountName);
			startAuthThread(activity);
		}
	}

	@Override
	public boolean isLinkOrAuthInProgress() {
		return linkInProgress || authInProgress;
	}

	@Override
	public boolean resumeLinkOrAuth() {
		return true;
	}

	@Override
	public void unLink() {
		// Clear preferences
		Editor edit = mPrefs.edit();
		edit.clear();
		edit.commit();

		// Clear data
		linkInProgress = false;
		authInProgress = false;
		authenticated = false;
		linked = false;
		accountName = null;
		driveService = null;

		Log.d(TAG, "Google Drive account unlinked");
	}

	@Override
	public String getUserName() {
		return accountName;
	}

	@Override
	public EncFSFileProvider getFileProvider(String path) {
		return new GoogleDriveFileProvider(driveService, path);
	}

	@Override
	public boolean forwardActivityResult(Activity origin, int requestCode,
			int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_ACCOUNT_PICKER:
			if (resultCode == Activity.RESULT_OK && data != null
					&& data.getExtras() != null) {
				accountName = data
						.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
				if (accountName != null) {
					linked = true;

					// write account name / linked to database
					Editor edit = mPrefs.edit();
					edit.putBoolean(PREF_LINKED, true);
					edit.putString(PREF_ACCOUNT_NAME, accountName);
					edit.commit();

					createDriveService(accountName);

					startAuthThread(origin);
					
					// Clear linkInProgress AFTER startAuthThread() sets authInProgress
					linkInProgress = false;

					return true;
				}
			}
			break;
		case REQUEST_AUTH_TOKEN:
			if (resultCode == Activity.RESULT_OK) {
				Log.v(TAG, "Successfully authenticated to Google API");
				showLoginToast(origin, LoginResult.OK);
				authenticated = true;
				return true;
			}
			break;
		}

		return false;
	}
}
