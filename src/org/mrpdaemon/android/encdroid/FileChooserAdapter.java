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
import java.util.Locale;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class FileChooserAdapter extends ArrayAdapter<FileChooserItem> {

	private Context context;
	private int resourceId;
	private List<FileChooserItem> items;

	public FileChooserAdapter(Context context, int resourceId,
			List<FileChooserItem> items) {
		super(context, resourceId, items);
		this.context = context;
		this.resourceId = resourceId;
		this.items = items;
	}

	public FileChooserItem getItem(int i) {
		return items.get(i);
	}

	public static String humanReadableByteCount(long bytes, boolean si) {
		int unit = si ? 1000 : 1024;
		if (bytes < unit)
			return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1)
				+ (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View row = convertView;
		if (row == null) {
			LayoutInflater inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			row = inflater.inflate(resourceId, null);
		}

		final FileChooserItem item = items.get(position);

		if (item != null) {
			TextView fileName = (TextView) row
					.findViewById(R.id.file_chooser_item_name);
			ImageView fileIcon = (ImageView) row
					.findViewById(R.id.file_chooser_item_icon);
			TextView fileSize = (TextView) row
					.findViewById(R.id.file_chooser_item_size);

			if (fileName != null) {
				fileName.setText(item.getName());
			}

			if (fileSize != null) {
				if (!item.isDirectory()) {
					fileSize.setText(humanReadableByteCount(item.getSize(),
							false));
				} else {
					fileSize.setText(null);
				}
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
