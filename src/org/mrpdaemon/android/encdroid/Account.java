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

// Base class for all account types
public abstract class Account {

	// Whether the account is linked to any user
	public abstract boolean isLinked();

	// Whether the account is currently authenticated
	public abstract boolean isAuthenticated();

	// Start linking and/or authenticating the account
	public abstract void startLinkOrAuth(Context context);

	// Whether linking or authentication is currently in progress
	public abstract boolean isLinkOrAuthInProgress();

	// Resume linking or authentication on return from another Activity
	public abstract boolean resumeLinkOrAuth();

	// Unlink account from user and destroy all tokens
	public abstract void unLink();

	// Return the user name associated with this Account
	public abstract String getUserName();

	// Return an EncFSFileProvider for this account at the given path
	public abstract EncFSFileProvider getFileProvider(String path);
}