package com.anthonymandra.sample;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
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

	private List<Uri> getTestFileList()
	{
		// I emphasize Uri here because 6.0 USB support is Uri ONLY, no File(s) at all!
		// As such if you support 6.0 you must rely on Uris throughout your app
		List<Uri> testUri = new ArrayList<>();

		File testFolder = getTestDir();

		testUri.add(Uri.fromFile(new File(testFolder, filename1)));
		testUri.add(Uri.fromFile(new File(testFolder, filename2)));
		return testUri;
	}

	private File getTestDir()
	{
		// This is lazy, but it's for simplicity's sake in the sample
		File primaryExternal = Environment.getExternalStorageDirectory();
		return new File(primaryExternal, testFolderName);
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
			File testFolder = getTestDir();
			if (!testFolder.exists())
			{
				try
				{
					setWriteResume(WriteActions.WRITE, params);
					mkdir(testFolder);
				}
				catch (WritePermissionException e)
				{
					Snackbar.make(findViewById(android.R.id.content), R.string.writeRequired, Snackbar.LENGTH_INDEFINITE);
					// If we couldn't create the test folder DocumentActivity will request
					// permission and resume this action using the wiring in onResumeWriteAction
					return null;
				}
			}

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
			File testFolder = getTestDir();
			try
			{
				UsefulDocumentFile testDoc = getDocumentFile(testFolder, true, false);
				testDoc.delete();
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
