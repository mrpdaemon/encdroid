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

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session.AccessType;

public class DropboxAccount extends Account {
	// Dropbox app key
	private final static String APP_KEY = "<YOUR APP KEY HERE>";

	// Dropbox app secret
	private final static String APP_SECRET = "<YOUR APP SECRET HERE>";

	// Full access to dropbox
	private final static AccessType ACCESS_TYPE = AccessType.DROPBOX;

	// Log tag
	private final static String TAG = "DropboxAccount";

	// Preference keys
	private final static String PREFS_KEY = "dropbox_prefs";
	private final static String PREF_LINKED = "is_linked";
	private final static String PREF_ACCESS_KEY = "access_key";
	private final static String PREF_ACCESS_SECRET = "access_secret";
	private final static String PREF_USER_NAME = "user_name";

	// Application object
	private EDApplication mApp;

	// Dropbox API object
	private DropboxAPI<AndroidAuthSession> mApi;

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

	public DropboxAccount(EDApplication app) {
		mApp = app;

		mPrefs = mApp.getSharedPreferences(PREFS_KEY, 0);

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

	@Override
	public void startLinkOrAuth(Context context) {

		AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);
		AndroidAuthSession session;

		if (!linked) {
			// need to link with the account
			Log.d(TAG, "Linking with dropbox account");
			linkInProgress = true;
			session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE);
		} else {
			Log.d(TAG,
					"Using existing access tokens to authenticate with Dropbox");

			// Use tokens
			String key = mPrefs.getString(PREF_ACCESS_KEY, null);
			String secret = mPrefs.getString(PREF_ACCESS_SECRET, null);

			AccessTokenPair accessTokens = new AccessTokenPair(key, secret);
			session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE,
					accessTokens);

			authenticated = true;
		}

		mApi = new DropboxAPI<AndroidAuthSession>(session);

		if (!linked) {
			session.startAuthentication(context);
		}
	}

	@Override
	public boolean resumeLinkOrAuth() {
		AndroidAuthSession session = mApi.getSession();

		if (session.authenticationSuccessful()) {
			try {
				// Mandatory call to complete the authentication
				session.finishAuthentication();

				// Store tokens locally in the DB for later use
				AccessTokenPair tokens = session.getAccessTokenPair();
				Editor edit = mPrefs.edit();
				edit.putBoolean(PREF_LINKED, true);
				edit.putString(PREF_ACCESS_KEY, tokens.key);
				edit.putString(PREF_ACCESS_SECRET, tokens.secret);

				Thread apiThread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							userName = mApi.accountInfo().displayName;
						} catch (DropboxException e) {
							Log.e(TAG, e.getMessage());
						}
					}
				});
				apiThread.start();
				apiThread.join();

				edit.putString(PREF_USER_NAME, userName);
				edit.commit();

				linkInProgress = false;
				linked = true;
				authenticated = true;

				Log.d(TAG, "Dropbox account for " + userName
						+ " linked successfully");

				return true;
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());

				linkInProgress = false;
				authenticated = false;
				linked = false;
				userName = "";
				mApi = null;

				return false;
			}
		} else {
			Log.d(TAG, "Dropbox authentication failed");

			linkInProgress = false;
			authenticated = false;
			linked = false;
			userName = "";
			mApi = null;

			return false;
		}
	}

	@Override
	public void unLink() {
		if (linked) {
			if (authenticated) {
				mApi.getSession().unlink();
			}
		}

		// Clear preferences
		Editor edit = mPrefs.edit();
		edit.clear();
		edit.commit();

		// Clear data
		linkInProgress = false;
		authenticated = false;
		linked = false;
		userName = "";
		mApi = null;

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
		return new DropboxFileProvider(mApi, path);
	}

	@Override
	public boolean forwardActivityResult(Activity origin, int requestCode,
			int resultCode, Intent data) {
		// Not needed
		return false;
	}
}
