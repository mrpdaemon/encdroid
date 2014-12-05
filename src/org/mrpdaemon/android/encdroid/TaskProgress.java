package org.mrpdaemon.android.encdroid;

// Class representing the progress of a task
public class TaskProgress {

	// Current job out of a multiple selection
	private int currentJob = 0;

	// Total number of jobs for the multiple selection
	private int numJobs = 0;

	// Current file out of multiple files
	private int currentFileIdx = 0;

	// Total number of files
	private int totalFiles = 0;

	// Byte position in current file
	private int currentBytes = 0;

	// Total length of current file
	private int totalBytes = 0;

	// Name of the current file
	private String currentFileName = "";

	public int getCurrentJob() {
		return currentJob;
	}

	public void setCurrentJob(int currentJob) {
		this.currentJob = currentJob;
	}

	public void incCurrentJob() {
		this.currentJob++;
	}

	public int getNumJobs() {
		return numJobs;
	}

	public void setNumJobs(int numJobs) {
		this.numJobs = numJobs;
	}

	public int getCurrentFileIdx() {
		return currentFileIdx;
	}

	public void setCurrentFileIdx(int currentFileIdx) {
		this.currentFileIdx = currentFileIdx;
	}

	public void incCurrentFileIdx() {
		this.currentFileIdx++;
	}

	public int getTotalFiles() {
		return totalFiles;
	}

	public void setTotalFiles(int totalFiles) {
		this.totalFiles = totalFiles;
	}

	public int getCurrentBytes() {
		return currentBytes;
	}

	public void setCurrentBytes(int currentBytes) {
		this.currentBytes = currentBytes;
	}

	public void incCurrentBytes(int bytes) {
		this.currentBytes += bytes;
	}

	public int getTotalBytes() {
		return totalBytes;
	}

	public void setTotalBytes(int totalBytes) {
		this.totalBytes = totalBytes;
	}

	public String getCurrentFileName() {
		return currentFileName;
	}

	public void setCurrentFileName(String currentFileName) {
		this.currentFileName = currentFileName;
	}
}