package com.anthonymandra.framework;

import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.List;

public class DocumentUtil
{
	private static final String PATH_DOCUMENT = "document";
	private static final String PATH_TREE = "tree";

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
	 * Extract the via {@link DocumentsContract.Document#COLUMN_DOCUMENT_ID} from the given URI.
	 * From {@link DocumentsContract} but return null instead of throw
	 */
	public static String getTreeDocumentId(Uri uri) {
		final List<String> paths = uri.getPathSegments();
		if (paths.size() >= 2 && PATH_TREE.equals(paths.get(0))) {
			return paths.get(1);
		}
		return null;
	}

	public static boolean isTreeUri(Uri uri) {
		final List<String> paths = uri.getPathSegments();
		return (paths.size() == 2 && PATH_TREE.equals(paths.get(0)));
	}

	/**
	 * True if the uri has a tree segment
	 */
	public static boolean hasTreeDocumentId(Uri uri) {
		return getTreeDocumentId(uri) != null;
	}

	/**
	 * Extract the {@link DocumentsContract.Document#COLUMN_DOCUMENT_ID} from the given URI.
	 * From {@link DocumentsContract} but return null instead of throw
	 */
	public static String getDocumentId(Uri documentUri) {
		final List<String> paths = documentUri.getPathSegments();
		if (paths.size() >= 2 && PATH_DOCUMENT.equals(paths.get(0))) {
			return paths.get(1);
		}
		if (paths.size() >= 4 && PATH_TREE.equals(paths.get(0))
				&& PATH_DOCUMENT.equals(paths.get(2))) {
			return paths.get(3);
		}
		return null;
	}

	/**
	 * Given a typical document id this will return the root id.
	 * <p>
	 * Example:
	 * 0000-0000:folder/file.ext
	 * will return '0000-0000'
	 *
	 * @param documentId A valid document Id
	 * @see {@link #getDocumentId(Uri)}
	 * @see {@link DocumentsContract#getDocumentId(Uri)}
	 *
	 * @return
	 */
	public static String getRoot(String documentId)
	{
		String[] parts = documentId.split(":");
		if (parts.length > 0)
			return parts[0];
		return null;
	}

	/**
	 * Given a typical document id this will split at the root id.
	 * <p>
	 * Example:
	 * 0000-0000:folder/file.ext
	 * will return ['0000-0000','folder/file.ext']
	 *
	 * @param documentId A valid document Id
	 * @see {@link #getDocumentId(Uri)}
	 * @see {@link DocumentsContract#getDocumentId(Uri)}
	 *
	 * @return
	 */
	public static String[] getIdSegments(String documentId)
	{
		return documentId.split(":");
	}

	/**
	 * Given a typical document id this will split the path segements.
	 * <p>
	 * Example:
	 * 0000-0000:folder/file.ext
	 * will return ['folder','file.ext']
	 *
	 * @param documentUri A valid document uri
	 * @see {@link #getDocumentId(Uri)}
	 * @see {@link DocumentsContract#getDocumentId(Uri)}
	 *
	 * @return
	 */
	public static String[] getPathSegments(Uri documentUri)
	{
		String documentId = getDocumentId(documentUri);
		if (documentId == null)
			return null;

		return getPathSegments(documentId);
	}

	/**
	 * Given a typical document id this will split the path segements.
	 * <p>
	 * Example:
	 * 0000-0000:folder/file.ext
	 * will return ['folder','file.ext']
	 *
	 * @param documentId A valid document Id
	 * @see {@link #getDocumentId(Uri)}
	 * @see {@link DocumentsContract#getDocumentId(Uri)}
	 *
	 * @return
	 */
	public static String[] getPathSegments(String documentId)
	{
		String[] idParts = getIdSegments(documentId);
		// If there's only one part it's a root
		if (idParts.length <= 1)
		{
			return null;
		}

		// The last part should be the path for both document and tree uris
		String path = idParts[idParts.length-1];
		return path.split("/");
	}

	/**
	 * Given a valid document root this will create a new id with the appended path.
	 * <p>
	 * Example:
	 * 0000-0000:folder/file.ext
	 * will return ['folder','file.ext']
	 *
	 * @param root A valid document root
	 * @see {@link #getDocumentId(Uri)}
	 * @see {@link #getRoot(String)}}
	 * @see {@link DocumentsContract#getDocumentId(Uri)}
	 *
	 * @return
	 */
	public static String createNewDocumentId(String root, String path)
	{
		return root + ":" + path;
	}

	/**
	 * Processes the URL encoded path of a document uri to be easy on the eyes
	 * <p>
	 * Example:
	 * <p>
	 * ...tree/0000-0000%3A/document/0000-0000%3Afolder%2Ffile.ext
	 * <p>returns
	 * <p>
	 * '0000-0000:folder/file.ext'
	 * @param uri uri
	 * @return path for display or null if invalid
	 */
	public static String getNicePath(Uri uri)
	{
		String documentId = getDocumentId(uri);
		if (documentId == null)
			documentId = getTreeDocumentId(uri);    // If there's no document id resort to tree id
		return documentId;
	}
}
