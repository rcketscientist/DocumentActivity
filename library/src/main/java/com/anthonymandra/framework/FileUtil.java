package com.anthonymandra.framework;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

//https://github.com/jeisfeld/Augendiagnose/

/**
 * Utility class for helping parsing file systems.
 */
public class FileUtil
{
	private static final String TAG = FileUtil.class.getSimpleName();

	public static boolean isContentScheme(Uri uri)
	{
		return ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme());
	}

	public static boolean isFileScheme(Uri uri)
	{
		return ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme());
	}

	/**
	 * Returns a uri to a child file within a folder.  This can be used to get an assumed uri
	 * to a child within a folder.  This avoids heavy calls to DocumentFile.listFiles or
	 * write-locked createFile
	 *
	 * This will only work with a uri that is an heriacrchical tree similar to SCHEME_FILE
	 * @param hierarchicalTreeUri folder to install into
	 * @param filename filename of child file
	 * @return Uri to the child file
	 */
	public static Uri getChildUri(Uri hierarchicalTreeUri, String filename)
	{
		// TODO: This technically doesn't work for content uris the url encode path separators
		String childUriString = hierarchicalTreeUri.toString() + "/" + filename;
		return Uri.parse(childUriString);
	}

	/**
	 * Check is a file is writable. Detects write issues on external SD card.
	 *
	 * @param file
	 *            The file
	 * @return true if the file is writable.
	 */
	public static boolean isWritable(final File file) {
		boolean isExisting = file.exists();

		try {
			FileOutputStream output = new FileOutputStream(file, true);
			try {
				output.close();
			}
			catch (IOException e) {
				// do nothing.
			}
		}
		catch (FileNotFoundException e) {
			return false;
		}
		boolean result = file.canWrite();

		// Ensure that file is not created during this process.
		if (!isExisting) {
			file.delete();
		}

		return result;
	}

	// Utility methods for Android 5

	/**
	 * Get a list of external SD card paths. (Kitkat or higher.)
	 *
	 * @return A list of external SD card paths.
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT)
	private static String[] getExtSdCardPaths(Context context) {
		List<String> paths = new ArrayList<String>();
		for (File file : context.getExternalFilesDirs("external")) {
			if (file != null && !file.equals(context.getExternalFilesDir("external"))) {
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
	 * Determine the main folder of the external SD card containing the given file.
	 *
	 * @param file
	 *            the file.
	 * @return The main folder of the external SD card containing this file, if the file is on an SD card. Otherwise,
	 *         null is returned.
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static String getExtSdCardFolder(final Context context, final File file) {
		String[] extSdPaths = getExtSdCardPaths(context);
		try {
			for (int i = 0; i < extSdPaths.length; i++) {
				if (file.getCanonicalPath().startsWith(extSdPaths[i])) {
					return extSdPaths[i];
				}
			}
		}
		catch (IOException e) {
			return null;
		}
		return null;
	}

	/**
	 * Determine if a file is on external sd card. (Kitkat or higher.)
	 *
	 * @param file
	 *            The file.
	 * @return true if on external sd card.
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static boolean isOnExtSdCard(final Context context, final File file) {
		return getExtSdCardFolder(context, file) != null;
	}

	public static String getCanonicalPathSilently(File file)
	{
		try
		{
			return file.getCanonicalPath();
		}
		catch (IOException e)
		{
			return file.getPath();
		}
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @param selection (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 */
	public static String getDataColumn(Context context, Uri uri, String selection,
	                                   String[] selectionArgs) {

		final String column = "_data";
		final String[] projection = {
				column
		};

		try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null))
		{
			if (cursor != null && cursor.moveToFirst()) {
				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		}
		return null;
	}

	/**
	 * Opens an InputStream to uri.  Checks if it's a local file to create a FileInputStream,
	 * otherwise resorts to using the ContentResolver to request a stream.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 */
	public static InputStream getInputStream(final Context context, final Uri uri) throws FileNotFoundException
	{
		if (isFileScheme(uri))
		{
			return new FileInputStream(uri.getPath());
		}
		else
		{
			return context.getContentResolver().openInputStream(uri);
		}
	}

	/**
	 * Opens an InputStream to uri.  Checks if it's a local file to create a FileInputStream,
	 * otherwise resorts to using the ContentResolver to request a stream.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 */
	public static ParcelFileDescriptor getParcelFileDescriptor(final Context context, final Uri uri, String mode) throws FileNotFoundException
	{
		if (isFileScheme(uri))
		{
			int m = ParcelFileDescriptor.MODE_READ_ONLY;
			if ("w".equalsIgnoreCase(mode) || "rw".equalsIgnoreCase(mode)) m = ParcelFileDescriptor.MODE_READ_WRITE;
			else if ("rwt".equalsIgnoreCase(mode)) m = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE;

			//TODO: Is this any faster?  Otherwise could just rely on resolver
			return ParcelFileDescriptor.open(new File(uri.getPath()), m);
		}
		else
		{
			return context.getContentResolver().openFileDescriptor(uri, mode);
		}
	}

	public static String getRealPathFromURI(Context context, Uri contentUri)
	{
		String[] proj = {MediaStore.Images.Media.DATA};
		try(Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null))
		{
			if (cursor == null)
				return null;
			int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			cursor.moveToFirst();
			return cursor.getString(column_index);
		}
	}

	public static boolean isSymlink(File file) {
		try
		{
			File canon;
			if (file.getParent() == null)
			{
				canon = file;
			} else
			{
				File canonDir = file.getParentFile().getCanonicalFile();
				canon = new File(canonDir, file.getName());
			}
			return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
		}
		catch (IOException e)
		{
			return false;
		}
	}

	public static File[] getStorageRoots()
	{
		File mnt = new File("/storage");
		if (!mnt.exists())
			mnt = new File("/mnt");

		File[] roots = mnt.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory() && pathname.exists()
						&& pathname.canWrite() && !pathname.isHidden()
						&& !isSymlink(pathname);
			}
		});
		return roots;
	}

	public static List<File> getStoragePoints(File root)
	{
		List<File> matches = new ArrayList<>();

		if (root == null)
			return matches;

		File[] contents = root.listFiles();
		if (contents == null)
			return matches;

		for (File sub : contents)
		{
			if (sub.isDirectory())
			{
				if (isSymlink(sub))
					continue;

				if (sub.exists()
						&& sub.canWrite()
						&& !sub.isHidden())
				{
					matches.add(sub);
				}
				else
				{
					matches.addAll(getStoragePoints(sub));
				}
			}
		}
		return matches;
	}

	public static List<File> getStorageRoots(String[] roots)
	{
		List<File> valid = new ArrayList<>();
		for (String root : roots)
		{
			File check = new File(root);
			if (check.exists())
			{
				valid.addAll(getStoragePoints(check));
			}
		}
		return valid;
	}

	/**
	 * Get a usable cache directory (external if available, internal otherwise).
	 *
	 * @param context    The context to use
	 * @param uniqueName A unique directory name to append to the cache dir
	 * @return The cache dir
	 */
	public static File getDiskCacheDir(Context context, String uniqueName)
	{
		// Check if media is mounted or storage is built-in, if so, try and use external cache dir
		// otherwise use internal cache dir
		File cache = null;
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !isExternalStorageRemovable())
		{
			cache = context.getExternalCacheDir();
		}
		if (cache == null)
			cache = context.getCacheDir();

		return new File(cache, uniqueName);
	}

	/**
	 * Check if external storage is built-in or removable.
	 *
	 * @return True if external storage is removable (like an SD card), false otherwise.
	 */
	@TargetApi(9)
	public static boolean isExternalStorageRemovable()
	{
		if (Util.hasGingerbread())
		{
			return Environment.isExternalStorageRemovable();
		}
		return true;
	}

	/**
	 * Check how much usable space is available at a given path.
	 *
	 * @param path The path to check
	 * @return The space available in bytes
	 */
	public static long getUsableSpace(File path)
	{
		final StatFs stats = new StatFs(path.getPath());
		return stats.getBlockSizeLong() * stats.getAvailableBlocksLong();
	}

	public static String swapExtention(String filename, String ext)
	{
		if (filename == null)
			return null;
		return filename.replaceFirst("[.][^.]+$", "") + "." + ext;
	}

	private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
	/**
	 * Copy bytes from an <code>InputStream</code> to an
	 * <code>OutputStream</code>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 * <p>
	 * Large streams (over 2GB) will return a bytes copied value of
	 * <code>-1</code> after the copy has completed since the correct
	 * number of bytes cannot be returned as an int. For large streams
	 * use the <code>copyLarge(InputStream, OutputStream)</code> method.
	 *
	 * @param input  the <code>InputStream</code> to read from
	 * @param output  the <code>OutputStream</code> to write to
	 * @return the number of bytes copied
	 * @throws NullPointerException if the input or output is null
	 * @throws IOException if an I/O error occurs
	 * @throws ArithmeticException if the byte count is too large
	 * @since Commons IO 1.1
	 */
	public static int copy(InputStream input, OutputStream output) throws IOException {
		long count = copyLarge(input, output);
		if (count > Integer.MAX_VALUE) {
			return -1;
		}
		return (int) count;
	}

	/**
	 * Copy bytes from a large (over 2GB) <code>InputStream</code> to an
	 * <code>OutputStream</code>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 *
	 * @param input  the <code>InputStream</code> to read from
	 * @param output  the <code>OutputStream</code> to write to
	 * @return the number of bytes copied
	 * @throws NullPointerException if the input or output is null
	 * @throws IOException if an I/O error occurs
	 * @since Commons IO 1.3
	 */
	public static long copyLarge(InputStream input, OutputStream output)
		throws IOException {
		byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
		long count = 0;
		int n = 0;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
			count += n;
		}
		return count;
	}

	public static boolean copy(Context context, Uri source, Uri destination) throws IOException {
		// TODO: Clean this up and protect that NPE
		UsefulDocumentFile destinationFile = UsefulDocumentFile.fromUri(context, destination);
		destinationFile.cacheFileData();

		UsefulDocumentFile parent = destinationFile.getParentFile();
		if (parent == null) {
			return false;
		}
		parent.createFile(null, destinationFile.getName());

		try (
			InputStream inStream = context.getContentResolver().openInputStream(source);
			OutputStream outStream = context.getContentResolver().openOutputStream(destination)) {
			copy(inStream, outStream);
		}
		catch(ArithmeticException e) {
			Log.d(TAG, "File larger than 2GB copied.");
		}
		catch(Exception e) {
			throw new IOException("Failed to copy " + source.getPath() + ": " + e.toString());
		}
		return true;
	}

	/**
	 * Move a file within the constraints of SAF.
	 *
	 * @param source The source uri
	 * @param target The target uri
	 * @return true if the copying was successful.
	 * @throws IOException if an I/O error occurs
	 */
	public static boolean move(final Context context, final Uri source, final Uri target) throws IOException
	{
		boolean success = copy(context, source, target);
		if (success) {
			UsefulDocumentFile sourceFile = UsefulDocumentFile.fromUri(context, source);
			success = sourceFile.delete();
		}
		return success;
	}
}
