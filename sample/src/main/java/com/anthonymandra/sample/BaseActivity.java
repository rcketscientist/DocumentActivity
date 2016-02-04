package com.anthonymandra.sample;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;

import com.anthonymandra.framework.DocumentActivity;
import com.anthonymandra.framework.UsefulDocumentFile;
import com.anthonymandra.framework.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseActivity extends DocumentActivity
{
	private static final String TAG = DocumentActivity.class.getSimpleName();

	private final static String filename1 = "test1.txt";
	private final static String filename2 = "test2.txt";
	private final static String testFolderName = "_documentTest";

	private enum WriteActions
	{
		WRITE,
		DELETE
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sub);

		findViewById(R.id.buttonCreate).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				createTestFiles();
			}
		});
		findViewById(R.id.buttonDelete).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				cleanUpTestFiles();
			}
		});
	}

	protected void createTestFiles()
	{
		new WriteTask().execute(getTestFileList());
	}

	protected void cleanUpTestFiles()
	{
		new DeleteTask().execute();
	}

	/**
	 * Create a list of desired test files, two per any external storage root.  This list
	 * demonstrates that a list of write actions need not all be under the same root.
	 * DocumentActivity will request write permission as need for every file and resume
	 * afterwards.
	 * @return
     */
	private List<Uri> getTestFileList()
	{
		// I emphasize Uri here because 6.0 USB support is Uri ONLY, no File(s) at all!
		// As such if you support 6.0 you must rely on Uris throughout your app
		List<Uri> testUri = new ArrayList<>();

		List<File> testDirs = getTestDirs();
		for (File testFolder: testDirs)
		{
			testUri.add(Uri.fromFile(new File(testFolder, filename1)));
			testUri.add(Uri.fromFile(new File(testFolder, filename2)));
		}

		return testUri;
	}

	/**
	 * Get a list of external SD card paths. (Kitkat or higher.)
	 *
	 * @return A list of external SD card paths.
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT)
	protected String[] getExtSdCardPaths() {
		List<String> paths = new ArrayList<String>();
		for (File file : getExternalFilesDirs("external")) {
			if (file != null && !file.equals(getExternalFilesDir("external"))) {
				int index = file.getAbsolutePath().lastIndexOf("/Android/data");
				if (index < 0) {
					Log.w(TAG, "Unexpected external file dir: " + file.getAbsolutePath());
				}
				else {
					String path = file.getAbsolutePath().substring(0, index);
					try {
						path = new File(path).getCanonicalPath();
					}
					catch (IOException e) {
						// Keep non-canonical path.
					}
					paths.add(path);
				}
			}
		}
		return paths.toArray(new String[0]);
	}

	/**
	 * Gets any official external mounts points.  At least one should require write permission.
	 * @return
     */
	private List<File> getTestDirs()
	{
		List<File> testDirs = new ArrayList<>();

		// Get all external storage file folders with which we have automatic permission
		for (File file : getExternalFilesDirs("external")) {
			if (file != null)
			{
				// truncate at the Android data branch to get the root which may not have permission
				int index = file.getAbsolutePath().lastIndexOf("/Android/data");
				if (index < 0)
				{
					Log.w(TAG, "Unexpected external file dir: " + file.getAbsolutePath());
				}
				else
				{
					String path = file.getAbsolutePath().substring(0, index);
					try {
						path = new File(path).getCanonicalPath();
					}
					catch (IOException e) {
						// Keep non-canonical path.
					}
					testDirs.add(new File(path, testFolderName));
				}
			}
		}

		return testDirs;
	}

	@Override
	protected void onResumeWriteAction(Enum callingMethod, Object[] callingParameters)
	{
		switch ((WriteActions)callingMethod)
		{
			case WRITE:
				new WriteTask().execute(callingParameters);
				break;
			case DELETE:
				new DeleteTask().execute();
				break;
			default:
				throw new NoSuchMethodError("Write Action:" + callingMethod + " is undefined.");
		}
		clearWriteResume();
	}

	/**
	 * To ensure we engage the request feature we attempt to write to sd without permission first.
	 */
	private class WriteTask extends AsyncTask<Object, Void, Void>
	{
		@Override
		protected Void doInBackground(Object... params)
		{
			// This is lazy, but it's for simplicity's sake in the sample
//			File testFolder = getTestDirs();
//			if (!testFolder.exists())
//			{
//				try
//				{
//					setWriteResume(WriteActions.WRITE, params);
//					mkdir(testFolder);
//				}
//				catch (WritePermissionException e)
//				{
//					Snackbar.make(findViewById(android.R.id.content), R.string.writeRequired, Snackbar.LENGTH_INDEFINITE);
//					// If we couldn't create the test folder DocumentActivity will request
//					// permission and resume this action using the wiring in onResumeWriteAction
//					return null;
//				}
//			}

			List<Uri> totalFiles = (List<Uri>) params[0];
			List<Uri> remainingFiles = new ArrayList<>(totalFiles);
			String message1 = getString(R.string.message);

			OutputStream os = null;
			for (Uri destination : totalFiles)
			{
				try
				{
					// Set the write to resume with the remaining items
					setWriteResume(WriteActions.WRITE, new Object[]{remainingFiles});

					// Write the test file
					UsefulDocumentFile destinationDoc = getDocumentFile(destination, false, true);
					os = getContentResolver().openOutputStream(destinationDoc.getUri());
					OutputStreamWriter osw = new OutputStreamWriter(os);
					osw.write(message1);
					osw.flush();

					// Remove the processed file
					remainingFiles.remove(destination);
				}
				catch (WritePermissionException e)
				{
					Snackbar.make(findViewById(android.R.id.content), R.string.writeRequired, Snackbar.LENGTH_INDEFINITE);
					// If we couldn't create the test file DocumentActivity will request
					// permission and resume this action using the wiring in onResumeWriteAction
					return null;
				}
				catch (FileNotFoundException e)
				{
					e.printStackTrace();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				finally
				{
					Util.closeSilently(os);
				}
			}

			Snackbar.make(findViewById(android.R.id.content), R.string.filesCreated, Snackbar.LENGTH_INDEFINITE);
			clearWriteResume(); // Make sure to clear the pending write task on success!
			return null;
		}
	}

	private class DeleteTask extends AsyncTask<Void, Void, Void>
	{
		@Override
		protected Void doInBackground(Void... params)
		{
			setWriteResume(WriteActions.DELETE, null);
			List<File> testFolders = getTestDirs();
			try
			{
				for (File testFolder: testFolders)
				{
					UsefulDocumentFile testDoc = getDocumentFile(testFolder, true, false);
					testDoc.delete();
				}
				Snackbar.make(findViewById(android.R.id.content), R.string.deleted, Snackbar.LENGTH_INDEFINITE);
			}
			catch (WritePermissionException e)
			{
				Snackbar.make(findViewById(android.R.id.content), R.string.writeRequired, Snackbar.LENGTH_INDEFINITE);
				return null;
			}
			clearWriteResume(); // Make sure to clear the pending write task on success!
			return null;
		}
	}
}
