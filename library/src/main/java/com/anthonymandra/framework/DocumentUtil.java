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

	public static String getNicePath(Uri uri)
	{
		String documentId = getDocumentId(uri);
		if (documentId == null)
			documentId = getTreeDocumentId(uri);    // If there's no document id resort to tree id
		return documentId;
	}
}
