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

import org.mrpdaemon.sec.encfs.EncFSVolume;

public class Volume {

	// Volume name
	private String name;

	// Volume path
	private String path;

	// Volume file system
	private FileSystem fileSystem;

	// Whether volume is locked or not
	private boolean isLocked;

	// EncFS volume associated with this volume
	private EncFSVolume volume;
	
	private String customConfigPath=null;

	public Volume(String name, String path, FileSystem fileSystem) {
		super();
		this.name = name;
		this.path = path;
		this.customConfigPath=null;
		this.fileSystem = fileSystem;
		this.isLocked = true;
		this.volume = null;
	}
	
	public Volume(String name, String path, String configPath, FileSystem fileSystem) {
		super();
		this.name = name;
		this.path = path;
		this.customConfigPath=configPath;
		this.fileSystem = fileSystem;
		this.isLocked = true;
		this.volume = null;
	}

	// Unlock the volume by passing in an EncFSVolume instance
	public void unlock(EncFSVolume volume) {
		if (this.isLocked) {
			this.volume = volume;
			this.isLocked = false;
		}
	}

	// Lock the volume
	public void lock() {
		if (!this.isLocked) {
			this.volume = null;
			this.isLocked = true;
		}
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return the type
	 */
	public FileSystem getFileSystem() {
		return fileSystem;
	}

	/**
	 * @return the isLocked
	 */
	public boolean isLocked() {
		return isLocked;
	}
	
	/**
	 * @return the customConfigPath
	 */
	public String getCustomConfigPath() {
		return customConfigPath;
	}

	/**
	 * @return the volume
	 */
	public EncFSVolume getVolume() {
		return volume;
	}
}