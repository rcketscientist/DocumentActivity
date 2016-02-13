package com.anthonymandra.sample;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.View;

import com.anthonymandra.framework.DocumentActivity;
import com.anthonymandra.framework.UsefulDocumentFile;
import com.anthonymandra.framework.Util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseActivity extends DocumentActivity
{
	private static final String TAG = DocumentActivity.class.getSimpleName();

	private final static String testFolderName = "_documentTest";

	private final static String documentId1 = testFolderName + "/" + "test1.txt";
	private final static String documentId2 = testFolderName + "/" + "test2.txt";

	private static Uri test1Uri, test2Uri;

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
		findViewById(R.id.buttonRequest).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				requestWritePermission();
			}
		});
		findViewById(R.id.buttonRevoke).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				revokePermission();
			}
		});
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	@Override
	protected void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode)
		{
			// Grab the selected permission so we can generate valid test files paths
			case REQUEST_CODE_WRITE_PERMISSION:
				if (resultCode == RESULT_OK && data != null)
				{
					Snackbar.make(findViewById(android.R.id.content), data.getDataString(), Snackbar.LENGTH_SHORT).show();
					String root = DocumentsContract.getTreeDocumentId(data.getData());
					test1Uri = DocumentsContract.buildDocumentUriUsingTree(data.getData(), root + documentId1);
					test2Uri = DocumentsContract.buildDocumentUriUsingTree(data.getData(), root + documentId2);
				}
				break;
		}
	}

	protected void revokePermission()
	{
		// TODO: This does not stop writing once permission was granted...needs more testing might be an android bug
		for (UriPermission p : getContentResolver().getPersistedUriPermissions())
		{
			getContentResolver().releasePersistableUriPermission(p.getUri(),
					Intent.FLAG_GRANT_READ_URI_PERMISSION |
					Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		}
		if (getContentResolver().getPersistedUriPermissions().size() == 0)
		{
			Snackbar.make(findViewById(android.R.id.content), R.string.revokeSuccess, Snackbar.LENGTH_SHORT).show();
		}
		else
		{
			Snackbar.make(findViewById(android.R.id.content), R.string.revokeFail, Snackbar.LENGTH_SHORT).show();
		}
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
		List<Uri> testFiles = new ArrayList<>();
		testFiles.add(test1Uri);
		testFiles.add(test2Uri);
		return testFiles;
	}

	@Override
	protected void onResumeWriteAction(Enum callingMethod, Object[] callingParameters)
	{
		if (callingMethod == null)
			return;

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
					Snackbar.make(findViewById(android.R.id.content),
							destinationDoc.getName() + " " + getString(R.string.created),
							Snackbar.LENGTH_SHORT).show();

				}
				catch (WritePermissionException e)
				{
					Snackbar.make(findViewById(android.R.id.content), R.string.writeRequired, Snackbar.LENGTH_SHORT).show();
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

			Snackbar.make(findViewById(android.R.id.content), R.string.filesCreated, Snackbar.LENGTH_SHORT).show();
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
			try
			{
				UsefulDocumentFile test = getDocumentFile(getTestFileList().get(0), false, false);
				test.getParentFile().delete();

//				for (Uri testFile: getTestFileList())
//				{
//					UsefulDocumentFile testDoc = getDocumentFile(testFile, true, false);
//					testDoc.delete();
//				}

				Snackbar.make(findViewById(android.R.id.content), R.string.deleted, Snackbar.LENGTH_SHORT).show();
			}
			catch (WritePermissionException e)
			{
				Snackbar.make(findViewById(android.R.id.content), R.string.writeRequired, Snackbar.LENGTH_SHORT).show();
				return null;
			}
			clearWriteResume(); // Make sure to clear the pending write task on success!
			return null;
		}
	}
}
