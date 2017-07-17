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

import java.io.File;
import java.util.Locale;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ConfigFileChooserAdapter extends ArrayAdapter<File> {

	private Context context;
	private int resourceId;
	private File[] items;
	private String[] itemNames;

	public ConfigFileChooserAdapter(Context context, int resourceId,
			File[] items,String[] itemNames) {
		super(context, resourceId, items);
		this.context = context;
		this.resourceId = resourceId;
		this.items = items;
		this.itemNames = itemNames;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View row = convertView;
		if (row == null) {
			LayoutInflater inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			row = inflater.inflate(resourceId, null);
		}

		final File item = items[position];

		if (item != null) {
			TextView fileName = (TextView) row.findViewById(R.id.file_chooser_item_name);
			ImageView fileIcon = (ImageView) row
					.findViewById(R.id.file_chooser_item_icon);

			if (fileName != null) {
					fileName.setText(itemNames[position]);
			}

			if (fileIcon != null) {
				if (item.isDirectory()) {
					fileIcon.setImageResource(R.drawable.ic_folder);
				} else {
					String mimeType = FileUtils.getMimeTypeFromFileName(item
							.getName().toLowerCase(Locale.getDefault()));
					if (mimeType != null) {
						fileIcon.setImageResource(FileUtils
								.getIconResourceForMimeType(mimeType));
					} else {
						fileIcon.setImageResource(FileUtils
								.getIconResourceForFileExtension(item.getName()));
					}
				}
			}
		}
		return row;
	}
}
