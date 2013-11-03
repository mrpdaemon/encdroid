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

import java.util.Arrays;

import org.mrpdaemon.sec.encfs.EncFSFileProvider;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.services.drive.DriveScopes;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class GoogleDriveAccount extends Account {

	static final int REQUEST_ACCOUNT_PICKER = 31;

	// Logger tag
	private final static String TAG = "GoogleDriveAccount";

	// Whether we're linked to an account
	private boolean linked;

	// Whether link is in progress
	private boolean linkInProgress;

	// Account name
	private String accountName = null;

	public GoogleDriveAccount(EDApplication app) {
		linked = false;
		linkInProgress = false;
	}

	@Override
	public String getName() {
		return "Google Drive";
	}

	@Override
	public int getIconResId() {
		// TODO Auto-generated method stub
		return R.drawable.ic_google_drive;
	}

	@Override
	public boolean isLinked() {
		return linked;
	}

	@Override
	public boolean isAuthenticated() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void startLinkOrAuth(Context context) {
		GoogleAccountCredential credential = GoogleAccountCredential
				.usingOAuth2(context, Arrays.asList(DriveScopes.DRIVE));

		// Select account to link
		if (linked == false) {
			linkInProgress = true;
			((Activity) context)
					.startActivityForResult(
							credential.newChooseAccountIntent(),
							REQUEST_ACCOUNT_PICKER);
		}
	}

	@Override
	public boolean isLinkOrAuthInProgress() {
		return linkInProgress;
	}

	@Override
	public boolean resumeLinkOrAuth() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void unLink() {
		// TODO Auto-generated method stub

	}

	@Override
	public String getUserName() {
		return accountName;
	}

	@Override
	public EncFSFileProvider getFileProvider(String path) {
		return new GoogleDriveFileProvider(path);
	}

	@Override
	public boolean forwardActivityResult(int requestCode, int resultCode,
			Intent data) {
		switch (requestCode) {
		case REQUEST_ACCOUNT_PICKER:
			if (resultCode == Activity.RESULT_OK && data != null
					&& data.getExtras() != null) {
				accountName = data
						.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
				if (accountName != null) {
					linked = true;
				}
			}
		}
		return true;
	}
}
