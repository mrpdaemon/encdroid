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

import org.mrpdaemon.sec.encfs.EncFSFileProvider;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.http.StandardHttpRequestor;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.users.FullAccount;

class DropboxAccount extends Account {
	// Dropbox app key
	private final static String APP_KEY = "<YOUR APP KEY HERE>";

	// Log tag
	private final static String TAG = "DropboxAccount";

	// Preference keys
	private final static String PREFS_KEY = "dropbox_prefs";
	private final static String PREF_LINKED = "is_linked";
	private final static String PREF_ACCESS_TOKEN = "access_token";
	private final static String PREF_USER_NAME = "user_name";

	// Legacy preference keys (from API v1)
	private final static String PREF_LEGACY_ACCESS_KEY = "access_key";
	private final static String PREF_LEGACY_ACCESS_SECRET = "access_secret";

	// Dropbox API client
	private DbxClientV2 mDbxClient;

	// Whether there is a dropbox account linked
	private boolean linked;

	// Whether we are authenticated with the dropbox account
	private boolean authenticated;

	// Whether we are in progress of linking to the dropbox account
	private boolean linkInProgress;

	// Dropbox preferences
	private SharedPreferences mPrefs;

	// User name
	private String userName;

	// Account
	private FullAccount account;

	// Info toast type
	private static enum InfoType {
		API_UPGRADE
	};

	DropboxAccount(EDApplication app) {
		mPrefs = app.getSharedPreferences(PREFS_KEY, 0);

		// Figure out whether we're linked to a Dropbox account
		linked = mPrefs.getBoolean(PREF_LINKED, false);

		userName = mPrefs.getString(PREF_USER_NAME, "");

		authenticated = false;

		linkInProgress = false;
	}

	@Override
	public String getName() {
		return "Dropbox";
	}

	@Override
	public int getIconResId() {
		return R.drawable.ic_dropbox;
	}

	private void showInfoToast(final Activity activity,
							   final InfoType info) {
		if (activity != null) {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					int stringId = 0;
					switch (info) {
						case API_UPGRADE:
							stringId = R.string.dropbox_api_upgrade;
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

	private void createDbxClient(final String accessToken) {
		DbxRequestConfig requestConfig = DbxRequestConfig.newBuilder("encdroid")
				.withHttpRequestor(StandardHttpRequestor.INSTANCE)
				.build();

		mDbxClient = new DbxClientV2(requestConfig, accessToken);
	}

	private void doLinkOrAuthWork(final Context context) {
		Log.d(TAG, "Linking with dropbox account");
		linkInProgress = true;

		((Activity) context).runOnUiThread(new Thread(new Runnable() {
			public void run() {
				Auth.startOAuth2Authentication(context, APP_KEY);
			}
		}));
	}

	@Override
	public void startLinkOrAuth(final Context context) {

		Activity activity = null;

		// We might be called from a non-Activity context, so safely dereference
		if (context instanceof Activity) {
			activity = (Activity) context;
		}

		if (!linked) {
			// need to link with the account
			doLinkOrAuthWork(context);
		} else {
			String accessToken = mPrefs.getString(PREF_ACCESS_TOKEN, null);
			if (accessToken != null) {
				Log.d(TAG, "Reusing existing access token to authenticate to Dropbox API");

				createDbxClient(accessToken);

				authenticated = true;
			} else {
				// handle linked but no access token case (API upgrade)
				Log.d(TAG, "Performing API upgrade to Dropbox v2 API tokens");

				linked = false;

				showInfoToast(activity, InfoType.API_UPGRADE);

				// Clear linked status and legacy tokens
				Editor edit = mPrefs.edit();
				edit.putBoolean(PREF_LINKED, false);
				edit.remove(PREF_LEGACY_ACCESS_KEY);
				edit.remove(PREF_LEGACY_ACCESS_SECRET);
				edit.apply();

				// Go through new link flow
				doLinkOrAuthWork(context);
			}
		}
	}

	@Override
	public boolean resumeLinkOrAuth() {
		String accessToken = Auth.getOAuth2Token();

		if (accessToken != null) {
			try {
				createDbxClient(accessToken);

				Thread apiThread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							account = mDbxClient.users().getCurrentAccount();
							userName = account.getEmail();
							authenticated = true;
						} catch (DbxException e) {
							Log.e(TAG, e.getMessage());
							authenticated = false;
						}
					}
				});
				apiThread.start();
				apiThread.join();

				if (authenticated) {
					Log.d(TAG, "Successfully authenticated to Dropbox API");
				} else {
					Log.e(TAG, "Failed to authenticate to Dropbox API");
					linkInProgress = false;
					linked = false;
					return false;
				}

				// Store token locally in the DB for later use
				Editor edit = mPrefs.edit();
				edit.putBoolean(PREF_LINKED, true);
				edit.putString(PREF_ACCESS_TOKEN, accessToken);
				edit.putString(PREF_USER_NAME, userName);
				edit.apply();

				linkInProgress = false;
				linked = true;

				Log.d(TAG, "Dropbox account for " + userName
						+ " linked successfully");

				return true;
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());

				linkInProgress = false;
				authenticated = false;
				linked = false;
				userName = "";
				mDbxClient = null;

				return false;
			}
		} else {
			Log.d(TAG, "Dropbox authentication failed");

			linkInProgress = false;
			authenticated = false;
			linked = false;
			userName = "";
			mDbxClient = null;

			return false;
		}
	}

	@Override
	public void unLink() {
		if (linked) {
			if (authenticated) {
				Thread apiThread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							mDbxClient.auth().tokenRevoke();
						} catch (Exception e) {
							Log.e(TAG, e.getMessage());
						}
					}
				});
				apiThread.start();
				try {
					apiThread.join();
				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
				}
			}
		}

		// Clear preferences
		Editor edit = mPrefs.edit();
		edit.clear();
		edit.apply();

		// Clear data
		linkInProgress = false;
		authenticated = false;
		linked = false;
		userName = "";
		mDbxClient = null;

		Log.d(TAG, "Dropbox account unlinked");
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
	public boolean isLinkOrAuthInProgress() {
		return linkInProgress;
	}

	@Override
	public String getUserName() {
		return userName;
	}

	@Override
	public EncFSFileProvider getFileProvider(String path) {
		return new DropboxFileProvider(mDbxClient, path);
	}

	@Override
	public boolean forwardActivityResult(Activity origin, int requestCode,
			int resultCode, Intent data) {
		// Not needed
		return false;
	}
}
