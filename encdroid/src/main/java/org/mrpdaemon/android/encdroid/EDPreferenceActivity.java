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
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;

public class EDPreferenceActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Display the preference fragment
		FragmentManager mFragmentManager = getFragmentManager();
		FragmentTransaction mFragmentTransaction = mFragmentManager
				.beginTransaction();
		EDPreferenceFragment mPrefsFragment = new EDPreferenceFragment();
		mFragmentTransaction.replace(android.R.id.content, mPrefsFragment);
		mFragmentTransaction.commit();

		getActionBar().setDisplayHomeAsUpEnabled(true);

		setTitle(getString(R.string.settings));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home: // Go back to volume list
			Intent intent = new Intent(this, VolumeListActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			break;
		default:
			break;
		}
		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) { // Go back to volume list
			Intent intent = new Intent(this, VolumeListActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);

			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	public class EDPreferenceFragment extends PreferenceFragment implements
			OnSharedPreferenceChangeListener {

		// Application object
		private EDApplication mApp;

		// Logger tag
		private final static String TAG = "EDPreferenceFragment";

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			setRetainInstance(true);

			addPreferencesFromResource(R.layout.preferences);

			mApp = (EDApplication) getApplication();

			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(EDPreferenceActivity.this);

			adjustExtSdScreen(prefs);
		}

		@Override
		public void onResume() {
			super.onResume();
			// Set up a listener whenever a key changes
			getPreferenceScreen().getSharedPreferences()
					.registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onPause() {
			super.onPause();
			// Unregister the listener whenever a key changes
			getPreferenceScreen().getSharedPreferences()
					.unregisterOnSharedPreferenceChangeListener(this);
		}

		private void adjustExtSdScreen(SharedPreferences prefs) {
			Preference extSdPrefScreen = findPreference("ext_sd_prefs");
			if (prefs.getBoolean("ext_sd_enabled", false)) {
				Log.d(TAG, "External SD card enabled");
				extSdPrefScreen.setEnabled(true);
			} else {
				Log.d(TAG, "External SD card disabled");
				extSdPrefScreen.setEnabled(false);
			}
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs,
				String key) {
			if (key.equals("cache_key")) {
				if (!prefs.getBoolean("cache_key", false)) {
					Log.d(TAG, "Key caching disabled, clearing cached keys.");
					// Need to clear all cached keys
					mApp.getDbHelper().clearAllKeys();
				} else {
					Log.d(TAG, "Key caching enabled.");
				}
			} else if (key.equals("ext_sd_enabled")) {
				adjustExtSdScreen(prefs);
			} else if (key.equals("auto_import")) {
				if (prefs.getBoolean("auto_import", false)) {
					Log.d(TAG, "Auto import enabled");
				} else {
					Log.d(TAG, "Auto import disabled");
				}
			}
		}
	}
}
