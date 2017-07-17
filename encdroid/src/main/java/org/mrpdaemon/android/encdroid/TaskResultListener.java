package org.mrpdaemon.android.encdroid;

/*
 * Interface to be extended by an Activity wanting to use EDTaskFragment to run
 * AsyncTask and get their result.
 */
public interface TaskResultListener {

	// Result being returned from a completed task
	public void onTaskResult(int taskId, Object result);

	// Error being returned from the task
	public void onTaskError(int taskId, String errorText);
}
