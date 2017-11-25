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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;

public class AccountsActivity extends Activity {

	private final String FRAGMENT_TAG = "AccountFragment";

	private final String LOG_TAG = "AccountsActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Display the accounts fragment
		FragmentManager mFragmentManager = getFragmentManager();
		FragmentTransaction mFragmentTransaction = mFragmentManager
				.beginTransaction();
		EDAccountsFragment mAccountsFragment = new EDAccountsFragment();
		mFragmentTransaction.replace(android.R.id.content, mAccountsFragment,
				FRAGMENT_TAG);
		mFragmentTransaction.commit();

		getActionBar().setDisplayHomeAsUpEnabled(true);

		setTitle(getString(R.string.settings));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// Go back to volume list
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
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			// Go back to volume list
			Intent intent = new Intent(this, VolumeListActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);

			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		// Feed the activity result into the fragment
		EDAccountsFragment mAccountsFragment = (EDAccountsFragment) getFragmentManager()
				.findFragmentByTag(FRAGMENT_TAG);
		try {
			mAccountsFragment.onActivityResult(requestCode, resultCode, data);
		} catch (Exception e) {
			Log.e(LOG_TAG, e.getMessage());
		}
	}

	public class EDAccountsFragment extends ListFragment {

		// Application object
		private EDApplication mApp;

		// List adapter
		private AccountListAdapter mAdapter;

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			setRetainInstance(true);

			mApp = (EDApplication) getApplication();

			setContentView(R.layout.account_list);
			setTitle(getString(R.string.accounts));

			getActionBar().setDisplayHomeAsUpEnabled(true);

			refreshList();
		}

		private void refreshList() {
			if (mAdapter == null) {
				mAdapter = new AccountListAdapter(this.getActivity(),
						R.layout.account_list_item, mApp.getAccountList());
				this.setListAdapter(mAdapter);
			} else {
				mAdapter.notifyDataSetChanged();
			}
		}

		public class AccountsErrorDialogFragment extends DialogFragment {

			private String mErrorText;

			public AccountsErrorDialogFragment(String errorText) {
				super();
				mErrorText = errorText;
			}

			@Override
			public Dialog onCreateDialog(Bundle savedInstanceState) {

				AlertDialog.Builder alertBuilder = new AlertDialog.Builder(
						getActivity());

				alertBuilder.setMessage(mErrorText);
				alertBuilder.setCancelable(false);
				alertBuilder.setNeutralButton(getString(R.string.btn_ok_str),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								dialog.dismiss();
							}
						});
				return alertBuilder.create();

			}
		}

		@Override
		public void onStart() {
			super.onStart();
			refreshList();
		}

		private void showErrorDialog(String errorText) {
			new AccountsErrorDialogFragment(errorText).show(
					getFragmentManager(), "errorDialog");
		}

		@Override
		public void onActivityResult(int requestCode, int resultCode,
				Intent data) {
			super.onActivityResult(requestCode, resultCode, data);

			for (Account account : mApp.getAccountList()) {
				if (account.isLinkOrAuthInProgress()) {
					if (account.forwardActivityResult(AccountsActivity.this,
							requestCode, resultCode, data) == true) {
						refreshList();
					} else {
						showErrorDialog(String.format(
								getString(R.string.account_login_error),
								account.getName()));
					}
				}
			}
		}

		@Override
		public void onResume() {
			super.onResume();

			for (Account account : mApp.getAccountList()) {
				if (account.isLinkOrAuthInProgress()) {
					if (account.resumeLinkOrAuth() == true) {
						refreshList();
					} else {
						showErrorDialog(String.format(
								getString(R.string.account_login_error),
								account.getName()));
					}
				}
			}
		}

		@Override
		public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);

			for (Account account : mApp.getAccountList()) {
				if (account.isPermissionRequestInProgress()) {
					account.forwardPermissionRequestResults(getActivity(), requestCode, permissions, grantResults);
				}
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		EDAccountsFragment mAccountsFragment = (EDAccountsFragment) getFragmentManager()
				.findFragmentByTag(FRAGMENT_TAG);
		try {
			mAccountsFragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
		} catch (Exception e) {
			Log.e(LOG_TAG, e.getMessage());
		}
	}
}
