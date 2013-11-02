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

import org.mrpdaemon.sec.encfs.EncFSFileProvider;

import android.content.Context;

public class GoogleDriveAccount extends Account {

	public GoogleDriveAccount(EDApplication app) {
		
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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isAuthenticated() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void startLinkOrAuth(Context context) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isLinkOrAuthInProgress() {
		// TODO Auto-generated method stub
		return false;
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EncFSFileProvider getFileProvider(String path) {
		return new GoogleDriveFileProvider(path);
	}
}
