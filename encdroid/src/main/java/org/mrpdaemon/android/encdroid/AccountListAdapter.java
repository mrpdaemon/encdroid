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

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class AccountListAdapter extends ArrayAdapter<Account> {

	Context context;
	private int resourceId;
	List<Account> items;

	public AccountListAdapter(Context context, int resourceId, List<Account> items) {
		super(context, resourceId, items);
		this.context = context;
		this.resourceId = resourceId;
		this.items = items;
	}

	public Account getItem(int i) {
		return items.get(i);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View row = convertView;
		if (row == null) {
			LayoutInflater inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			row = inflater.inflate(resourceId, null);
		}

		final Account item = items.get(position);

		if (item != null) {
			// Title text
			TextView titleText = (TextView) row.findViewById(R.id.account_title_text);
			titleText.setText(item.getName());
			
			// Icon image
			ImageView accountIcon = (ImageView) row.findViewById(R.id.account_image_view);
			accountIcon.setImageResource(item.getIconResId());
			
			// Status text
			TextView statusText = (TextView) row.findViewById(R.id.account_status_text);
			String status;

			if (item.isAuthenticated()) {
				status = String.format(context.getString(R.string.account_logged_in),
						item.getUserName());
			} else if (item.isLinked()) {
				status = String.format(context.getString(R.string.account_linked),
						item.getUserName());
			} else {
				status = context.getString(R.string.account_not_linked);
			}

			statusText.setText(status);

			// Button
			Button accountButton = (Button) row.findViewById(R.id.account_button);

			if (item.isLinked()) {
				accountButton.setText(context.getString(R.string.account_unlink_btn_str));
				accountButton.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						// Unlink the account
						item.unLink();
						notifyDataSetChanged();
					}
				});
			} else {
				accountButton.setText(context.getString(R.string.account_link_btn_str));
				accountButton.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						// Start linking an account
						item.startLinkOrAuth(context);
					}
				});
			}
		}

		return row;
	}
}