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

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;

public class ActionBarHelper {
	private ActionBar mActionBar;

	/* class initialization fails when this throws an exception */
	static {
		try {
			Class.forName("android.app.ActionBar");
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/* calling here forces class initialization */
	public static void checkAvailable() {
	}

	@SuppressLint("NewApi")
	public ActionBarHelper(Activity activity) {
		mActionBar = activity.getActionBar();
	}

	@SuppressLint("NewApi")
	public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
		mActionBar.setDisplayHomeAsUpEnabled(showHomeAsUp);
	}

	@SuppressLint("NewApi")
	public void invalidateOptionsMenu(Activity activity) {
		activity.invalidateOptionsMenu();
	}
}