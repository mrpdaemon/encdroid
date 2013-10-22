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

import org.mrpdaemon.sec.encfs.EncFSFile;

public class FileChooserItem implements Comparable<FileChooserItem> {

	private String name;

	private boolean isDirectory;

	private String path;

	private EncFSFile file;

	private long size;

	public FileChooserItem(String name, boolean isDirectory, String path,
			long size) {
		this.name = name;
		this.isDirectory = isDirectory;
		this.path = path;
		this.size = size;
		this.file = null;
	}

	public FileChooserItem(String name, boolean isDirectory, EncFSFile file,
			long size) {
		this.name = name;
		this.isDirectory = isDirectory;
		this.path = null;
		this.size = size;
		this.file = file;
	}

	/**
	 * @return the path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the isDirectory
	 */
	public boolean isDirectory() {
		return isDirectory;
	}

	/**
	 * @return the size
	 */
	public long getSize() {
		return size;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(FileChooserItem arg0) {
		return this.name.toLowerCase().compareTo(arg0.getName().toLowerCase());
	}

	public EncFSFile getFile() {
		return file;
	}
}