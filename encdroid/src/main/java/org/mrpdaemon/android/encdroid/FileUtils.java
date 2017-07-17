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

import android.text.TextUtils;
import android.webkit.MimeTypeMap;

public class FileUtils {

	public static String getExtensionFromFileName(String fileName) {
		// Figure out the MIME type
		String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
		if (TextUtils.isEmpty(extension)) {
			/*
			 * getFileExtensionFromUrl doesn't work for files with spaces
			 */
			int dotIndex = fileName.lastIndexOf('.');
			if (dotIndex >= 0) {
				extension = fileName.substring(dotIndex + 1);
			}
		}

		return extension;
	}

	public static String getMimeTypeFromFileName(String fileName) {
		return MimeTypeMap.getSingleton().getMimeTypeFromExtension(
				getExtensionFromFileName(fileName));
	}

	public static int getIconResourceForMimeType(String mimeType) {

		if (mimeType != null) {
			if (mimeType.equals("text/html")) {
				return R.drawable.ic_mimetype_html;
			} else if (mimeType.startsWith("text")) {
				return R.drawable.ic_mimetype_text;
			} else if (mimeType.startsWith("image")) {
				return R.drawable.ic_mimetype_image;
			} else if (mimeType.startsWith("audio")) {
				return R.drawable.ic_mimetype_audio;
			} else if (mimeType.startsWith("video")) {
				return R.drawable.ic_mimetype_video;
			} else if (mimeType.equals("application/msword")
					|| mimeType
							.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
					|| mimeType
							.equals("application/vnd.oasis.opendocument.text")
					|| mimeType.equals("application/vnd.sun.xml.writer")) {
				// Word processing document
				return R.drawable.ic_mimetype_document;
			} else if (mimeType.equals("application/vnd.ms-excel")
					|| mimeType
							.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
					|| mimeType
							.equals("application/vnd.oasis.opendocument.spreadsheet")
					|| mimeType.equals("application/vnd.sun.xml.calc")) {
				// Spreadsheet
				return R.drawable.ic_mimetype_spreadsheet;
			} else if (mimeType.equals("application/vnd.ms-powerpoint")
					|| mimeType
							.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")
					|| mimeType
							.equals("application/vnd.oasis.opendocument.presentation")
					|| mimeType.equals("application/vnd.sun.xml.impress")) {
				// Presentation
				return R.drawable.ic_mimetype_presentation;
			} else if (mimeType
					.equals("application/vnd.android.package-archive")
					|| mimeType.equals("application/x-tar")
					|| mimeType.equals("application/zip")
					|| mimeType.equals("application/x-bzip2")
					|| mimeType.equals("application/x-gzip")
					|| mimeType.equals("application/x-lzma")
					|| mimeType.equals("application/x-xz")
					|| mimeType.equals("application/x-7z-compressed")
					|| mimeType.equals("application/x-apple-diskimage")
					|| mimeType.equals("application/vnd.ms-cab-compressed")
					|| mimeType.equals("application/x-rar-compressed")
					|| mimeType.equals("application/x-gtar")) {
				// Archive files
				return R.drawable.ic_mimetype_archive;
			} else if (mimeType.equals("application/pdf")
					|| mimeType.equals("application/x-pdf")) {
				return R.drawable.ic_mimetype_pdf;
			}

		}

		// Default file icon
		return R.drawable.ic_file;
	}

	public static int getIconResourceForFileExtension(String fileName) {
		String extension = getExtensionFromFileName(fileName);

		if (extension != null) {
			if (extension.equals("kdb") || extension.equals("kdbx")) {
				return R.drawable.ic_mimetype_kdb;
			} else if (extension.equals("odp")) {
				return R.drawable.ic_mimetype_presentation;
			} else if (extension.equals("bz2")) {
				return R.drawable.ic_mimetype_archive;
			} else if (extension.equals("gz")) {
				return R.drawable.ic_mimetype_archive;
			} else if (extension.equals("xz")) {
				return R.drawable.ic_mimetype_archive;
			}
		}

		// Default file icon
		return R.drawable.ic_file;
	}
}
