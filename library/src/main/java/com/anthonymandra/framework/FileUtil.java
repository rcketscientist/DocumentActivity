package com.anthonymandra.framework;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

//https://github.com/jeisfeld/Augendiagnose/

/**
 * Utility class for helping parsing file systems.
 */
public class FileUtil
{
	private static final String TAG = FileUtil.class.getSimpleName();
	/**
	 * The name of the primary volume (LOLLIPOP).
	 */
	private static final String PRIMARY_VOLUME_NAME = "primary";

	private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

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
		// TODO: This technically doesn't work for content uris the url in code path separators
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

	/**
	 * Get the full path of a document from its tree URI.
	 *
	 * @param treeUri
	 *            The tree RI.
	 * @return The path (without trailing file separator).
	 */
	public static String getFullPathFromTreeUri(final Context context, final Uri treeUri) {
		if (treeUri == null) {
			return null;
		}
		String volumePath = FileUtil.getVolumePath(context, FileUtil.getVolumeIdFromTreeUri(treeUri));
		if (volumePath == null) {
			return File.separator;
		}
		if (volumePath.endsWith(File.separator)) {
			volumePath = volumePath.substring(0, volumePath.length() - 1);
		}

		String documentPath = FileUtil.getDocumentPathFromTreeUri(treeUri);
		if (documentPath.endsWith(File.separator)) {
			documentPath = documentPath.substring(0, documentPath.length() - 1);
		}

		if (documentPath != null && documentPath.length() > 0) {
			if (documentPath.startsWith(File.separator)) {
				return volumePath + documentPath;
			}
			else {
				return volumePath + File.separator + documentPath;
			}
		}
		else {
			return volumePath;
		}
	}

	/**
	 * Get the path of a certain volume.
	 *
	 * @param volumeId
	 *            The volume id.
	 * @return The path.
	 */
	private static String getVolumePath(final Context context, final String volumeId) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			return null;
		}

		try {
			StorageManager mStorageManager =
					(StorageManager) context.getSystemService(Context.STORAGE_SERVICE);

			Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");

			Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
			Method getUuid = storageVolumeClazz.getMethod("getUuid");
			Method getPath = storageVolumeClazz.getMethod("getPath");
			Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
			Object result = getVolumeList.invoke(mStorageManager);

			final int length = Array.getLength(result);
			for (int i = 0; i < length; i++) {
				Object storageVolumeElement = Array.get(result, i);
				String uuid = (String) getUuid.invoke(storageVolumeElement);
				Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);

				// primary volume?
				if (primary.booleanValue() && PRIMARY_VOLUME_NAME.equals(volumeId)) {
					return (String) getPath.invoke(storageVolumeElement);
				}

				// other volumes?
				if (uuid != null) {
					if (uuid.equals(volumeId)) {
						return (String) getPath.invoke(storageVolumeElement);
					}
				}
			}

			// not found.
			return null;
		}
		catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Get the volume ID from the tree URI.
	 *
	 * @param treeUri
	 *            The tree URI.
	 * @return The volume ID.
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static String getVolumeIdFromTreeUri(final Uri treeUri) {
		final String docId = DocumentsContract.getTreeDocumentId(treeUri);
		final String[] split = docId.split(":");

		if (split.length > 0) {
			return split[0];
		}
		else {
			return null;
		}
	}

	/**
	 * Get the document path (relative to volume name) for a tree URI (LOLLIPOP).
	 *
	 * @param treeUri
	 *            The tree URI.
	 * @return the document path.
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static String getDocumentPathFromTreeUri(final Uri treeUri) {
		final String docId = DocumentsContract.getTreeDocumentId(treeUri);
		final String[] split = docId.split(":");
		if ((split.length >= 2) && (split[1] != null)) {
			return split[1];
		}
		else {
			return File.separator;
		}
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
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 */
	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	public static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
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

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = {
				column
		};

		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
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

	/**
	 * Generates an appropriate {@link DocumentFile} based on the scheme of the supplied uri.
	 *
	 * Note: This assumes that the uri is either:
	 * <li>{@link ContentResolver#SCHEME_FILE} </li>
	 * <li>com.android...documents based singleUri</li>
	 *
	 * If {@link DocumentFile} is not generated with the appropriate factory then the fields will
	 * not be generated
	 *
	 * I am not certain what would happen with a non-document mediastore uri, etc.
	 * @param uri
	 * @return
     */
	public static UsefulDocumentFile getDocumentFile(Context c, Uri uri)
	{
		if (isFileScheme(uri))
		{
			return UsefulDocumentFile.fromFile(c, new File(uri.getPath()));
		}
		// TODO: Confirm if other cases need to be handled
		else
		{
			return UsefulDocumentFile.fromUri(c, uri);
		}
	}


	// copy from InputStream
	//-----------------------------------------------------------------------
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

	// read toByteArray
	//-----------------------------------------------------------------------
	/**
	 * Get the contents of an <code>InputStream</code> as a <code>byte[]</code>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 *
	 * @param input  the <code>InputStream</code> to read from
	 * @return the requested byte array
	 * @throws NullPointerException if the input is null
	 * @throws IOException if an I/O error occurs
	 */
	public static byte[] toByteArray(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		copy(input, output);
		return output.toByteArray();
	}

	public static String getRealPathFromURI(Context context, Uri contentUri)
	{
		Cursor cursor = null;
		try
		{
			String[] proj = {MediaStore.Images.Media.DATA};
			cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
			int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			cursor.moveToFirst();
			return cursor.getString(column_index);
		}
		finally
		{
			if (cursor != null)
			{
				cursor.close();
			}
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
}
