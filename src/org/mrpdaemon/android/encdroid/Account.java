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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

// Base class for all account types
public abstract class Account {

	// Timeout (in ms) for auth thread wait
	private static final int AUTH_THREAD_TIMEOUT = 5000;

	// Sleep interval (in ms) between checking authentication thread progress
	private static final int AUTH_THREAD_CHECK_INTERVAL = 10;

	// Return account name
	public abstract String getName();

	// Return icon resource id for this account
	public abstract int getIconResId();

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

	// Forwarded activity result on return from another Activity
	public abstract boolean forwardActivityResult(Activity origin,
			int requestCode, int resultCode, final Intent data);

	// Unlink account from user and destroy all tokens
	public abstract void unLink();

	// Return the user name associated with this Account
	public abstract String getUserName();

	// Return an EncFSFileProvider for this account at the given path
	public abstract EncFSFileProvider getFileProvider(String path);

	// Common code to authenticate the account if needed
	public static boolean authIfNeeded(Account account, Context context,
			String logTag) {
		if (!account.isAuthenticated()) {
			account.startLinkOrAuth(context);
			/*
			 * If the account isn't yet authenticated and there's authentication
			 * in progress we loop around until the thread is done.
			 */
			int authTimeout = 0;
			while (!account.isAuthenticated()
					&& account.isLinkOrAuthInProgress()) {
				try {
					Thread.sleep(AUTH_THREAD_CHECK_INTERVAL);
				} catch (InterruptedException e) {
					Logger.logException(logTag, e);
				}

				// Check for timeout to break loop
				authTimeout += AUTH_THREAD_CHECK_INTERVAL;
				if (authTimeout >= AUTH_THREAD_TIMEOUT) {
					Log.e(logTag,
							"Timeout while waiting for authentication thread");
					return false;
				}
			}
		}

		return account.isAuthenticated();
	}
}