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

	// Timeout (in ms) for link wait
	private static final int LINK_TIMEOUT = 120000;

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

	public boolean isPermissionRequestInProgress() {
		return false;
	}

	/*
	 * Common code to link or authenticate the account if needed.
	 * 
	 * Must be called from a non-Activity thread, otherwise will deadlock.
	 */
	public boolean linkOrAuthIfNeeded(Context context, String logTag) {
		if (!isLinked() || !isAuthenticated()) {
			startLinkOrAuth(context);

			// Wait for link is longer than waiting for authentication
			int waitTimeout = !isLinked() ? LINK_TIMEOUT : AUTH_THREAD_TIMEOUT;

			/*
			 * If the account isn't yet authenticated and there's authentication
			 * in progress we loop around until the thread is done.
			 */
			int timer = 0;
			while (!isAuthenticated() && isLinkOrAuthInProgress()) {
				try {
					Thread.sleep(AUTH_THREAD_CHECK_INTERVAL);
				} catch (InterruptedException e) {
					Logger.logException(logTag, e);
				}

				// Check for timeout to break loop
				timer += AUTH_THREAD_CHECK_INTERVAL;
				if (timer >= waitTimeout) {
					Log.e(logTag,
							"Timeout while waiting for authentication thread");
					return false;
				}
			}
		}

		return isAuthenticated();
	}

	public void forwardPermissionRequestResults(Activity origin, int requestCode, String[] permissions, int[] grantResults) {
	}
}