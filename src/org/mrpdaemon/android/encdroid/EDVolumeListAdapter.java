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
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class EDVolumeListAdapter extends ArrayAdapter<EDVolume> {

	Context context;
	private int resourceId;
	List<EDVolume> items;

	public EDVolumeListAdapter(Context context, int resourceId,
			List<EDVolume> items) {
		super(context, resourceId, items);
		this.context = context;
		this.resourceId = resourceId;
		this.items = items;
	}

	public EDVolume getItem(int i) {
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

		final EDVolume item = items.get(position);

		if (item != null) {
			TextView volumeName = (TextView) row
					.findViewById(R.id.volume_list_item_name);
			ImageView volumeIcon = (ImageView) row
					.findViewById(R.id.volume_list_item_icon);
			TextView volumePath = (TextView) row
					.findViewById(R.id.volume_list_item_path);

			if (volumeName != null) {
				volumeName.setText(item.getName());
			}

			if (volumePath != null) {
				switch (item.getType()) {
				case EDVolume.LOCAL_VOLUME:
					volumePath.setText(Environment
							.getExternalStorageDirectory().getAbsolutePath()
							+ item.getPath());
					break;
				case EDVolume.DROPBOX_VOLUME:
					volumePath.setText("[Dropbox]:" + item.getPath());
					break;
				default:
					volumePath.setText(item.getPath());
					break;
				}
			}

			if (volumeIcon != null) {
				if (item.isLocked()) {
					volumeIcon.setImageResource(R.drawable.ic_locked_volume);
				} else {
					volumeIcon.setImageResource(R.drawable.ic_unlocked_volume);
				}
			}
		}

		return row;
	}
}