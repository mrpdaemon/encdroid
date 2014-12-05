package org.mrpdaemon.android.encdroid;

import android.app.Activity;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.os.ConditionVariable;
import android.util.Log;

/*
 * Abstract class for classes that encapsulates an AsyncTask and the associated
 * ProgressDialog. Used to retain task state beyond Activity lifetime.
 */
public abstract class TaskFragment extends Fragment {

	// Associated Activity
	protected Activity mActivity = null;

	// Our task ID
	private int mTaskId = -1;

	// Whether the task was started
	private boolean mTaskStarted = false;

	// Whether the task is complete
	private boolean mTaskComplete = false;

	// Result of the task
	private Object mTaskResult = null;

	// Callback to return result
	private TaskResultListener mCallback = null;

	// Associated AsyncTask
	private EDAsyncTask<Void, Void, ?> mAsyncTask = null;

	// Associated ProgressDialog
	private ProgressDialog mProgDialog = null;

	// Condition variable for safely calling Activity methods
	private ConditionVariable mActivityCond;

	// Logger tag
	private static final String TAG = "TaskFragment";

	// Creation to be called by subclasses
	public TaskFragment(Activity activity) {
		super();
		this.mActivity = activity;
		this.mTaskId = getTaskId();
		this.mCallback = (TaskResultListener) activity;

		this.mActivityCond = new ConditionVariable();

		// retain this fragment across multiple Activities
		setRetainInstance(true);
	}

	// Method to start the associated task
	public void startTask() {
		mAsyncTask = createTask();
		mTaskStarted = true;
		mAsyncTask.execute();
	}

	/*
	 * Method for an attaching Activity to get task result if the task was
	 * completed while the Fragment was not attached to any Activity.
	 */
	public Object getResult() {
		return mTaskResult;
	}

	/*
	 * Method for an attaching Activity to determine whether the task was
	 * completed while the Fragment was not attached to any Activity.
	 */
	public boolean isTaskComplete() {
		return mTaskComplete;
	}

	// Method for subclasses to return task ID
	protected abstract int getTaskId();

	// Method for subclasses to create the async task
	protected abstract EDAsyncTask<Void, Void, ?> createTask();

	// Method for the AsyncTask to return its result
	protected void returnResult(Object result) {
		mTaskResult = result;
		mTaskComplete = true;

		if (mCallback != null) {
			mCallback.onTaskResult(mTaskId, mTaskResult);
		}

		if (mProgDialog != null) {
			mProgDialog.dismiss();
			mProgDialog = null;
		}
	}

	// Method for the AsyncTask to return an error
	protected void returnError(String errorText) {
		mTaskComplete = true;

		if (mCallback != null) {
			mCallback.onTaskError(mTaskId, errorText);
		}

		if (mProgDialog != null) {
			mProgDialog.dismiss();
			mProgDialog = null;
		}
	}

	// Method for AsyncTask to block until an Activity is attached
	protected void blockForActivity() {
		mActivityCond.block();
	}

	// Safe Fragment.getString() equivalent for AsyncTask to wait for Activity
	public final String getStringSafe(int resId) {
		blockForActivity();
		return super.getString(resId);
	}

	// Being attached by an Activity
	@Override
	public void onAttach(Activity activity) {
		Log.d(TAG, "onAttach");
		super.onAttach(activity);

		mActivity = activity;
		mCallback = (TaskResultListener) activity;
		mActivityCond.open();

		if (mTaskStarted) {
			mProgDialog = mAsyncTask.createProgressDialog(mActivity);
			mAsyncTask.setProgressDialog(mProgDialog);
			mProgDialog.show();
		}
	}

	// Being detached from the hosting activity, clean up view state
	@Override
	public void onDetach() {
		Log.d(TAG, "onDetach");
		super.onDetach();

		mActivityCond.close();
		mActivity = null;
		mCallback = null;

		mAsyncTask.setProgressDialog(null);

		if (mProgDialog != null) {
			mProgDialog.dismiss();
			mProgDialog = null;
		}
	}

}
