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

import java.util.List;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class FSTypeListAdapter extends ArrayAdapter<FileSystem> {

	Context context;
	private int resourceId;
	List<FileSystem> items;

	public FSTypeListAdapter(Context context, int resourceId,
			List<FileSystem> items) {
		super(context, resourceId, items);
		this.context = context;
		this.resourceId = resourceId;
		this.items = items;
	}

	public FileSystem getItem(int i) {
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

		final FileSystem item = items.get(position);

		if (item != null) {
			TextView fsName = (TextView) row.findViewById(R.id.fs_list_name);
			ImageView fsIcon = (ImageView) row.findViewById(R.id.fs_list_icon);

			if (fsName != null) {
				fsName.setText(item.getName());
				if (!isEnabled(position)) {
					fsName.setTextColor(Color.LTGRAY);
				} else {
					fsName.setTextColor(Color.BLACK);
				}
			}

			if (fsIcon != null) {
				fsIcon.setImageResource(item.getIconResId());
			}
		}

		return row;
	}

	@Override
	public boolean isEnabled(int position) {
		final FileSystem item = items.get(position);

		return item.isEnabled();
	}

}