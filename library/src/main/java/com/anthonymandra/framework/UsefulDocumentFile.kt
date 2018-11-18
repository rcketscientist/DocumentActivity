package com.anthonymandra.framework

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import com.anthonymandra.support.v4.provider.DocumentsContractApi19
import com.anthonymandra.support.v4.provider.DocumentsContractApi21

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.ArrayList
import java.util.Arrays

/**
 * Representation of a document backed by either a
 * [android.provider.DocumentsProvider] or a raw file on disk.
 *
 *
 * There are several differences between documents and traditional files:
 *
 *  * Documents express their display name and MIME type as separate fields,
 * instead of relying on file extensions. Some documents providers may still
 * choose to append extensions to their display names, but that's an
 * implementation detail.
 *  * A single document may appear as the child of multiple directories, so it
 * doesn't inherently know who its parent is. That is, documents don't have a
 * strong notion of path. You can easily traverse a tree of documents from
 * parent to child, but not from child to parent.
 *  * Each document has a unique identifier within that provider. This
 * identifier is an *opaque* implementation detail of the provider, and
 * as such it must not be parsed.
 *
 *
 *
 * As you navigate the tree of DocumentFile instances, you can always use
 * [.getUri] to obtain the Uri representing the underlying document for
 * that object, for use with [ContentResolver.openInputStream], etc.
 *
 *
 * To simplify your code on devices running
 * [android.os.Build.VERSION_CODES.KITKAT] or earlier, you can use
 * file scheme uris.  Note: These ONLY work prior to 4.4.  Passing a file uri
 * on 4.4+ will not have write access!
 *
 *
 * Why was this necessary?  There are many flaws in the existing:
 *
 *  * [androidx.core.provider.SingleDocumentFile]
 *  * [androidx.core.provider.TreeDocumentFile]
 *
 * making them useless in any dynamic environment:
 *
 *  * https://code.google.com/p/android/issues/detail?id=200941
 *  * https://code.google.com/p/android/issues/detail?id=199562
 *
 * @see android.provider.DocumentsProvider
 *
 * @see DocumentsContract
 */
class UsefulDocumentFile internal constructor(
   private val mParent: UsefulDocumentFile?,
   private val mContext: Context,
   uri: Uri) {
    /**
     * Return a Uri for the underlying document represented by this file. This
     * can be used with other platform APIs to manipulate or share the
     * underlying content.
     *
     * @see Intent.setData
     * @see Intent.setClipData
     * @see ContentResolver.openInputStream
     * @see ContentResolver.openOutputStream
     * @see ContentResolver.openFileDescriptor
     */
    var uri: Uri private set
    private lateinit var cachedData: FileData

    /**
     * Return the parent file of this document. Only defined inside of the
     * user-selected tree; you can never escape above the top of the tree.
     *
     *
     * This method is a significant enhancement over the official DocumentFile
     * variants in that it will attempt to determine the parent given the
     * hierarchical tree within the uri itself.
     *
     *
     * Note: This may not be the *only* parent as Documents may have multiple parents.
     * This will simply attempt to acquire the parent used to generate the tree.
     * For filesystem use this is sufficient.
     */
    val parentFile: UsefulDocumentFile?
        get() = mParent ?: parentDocument

    // This is not a document uri, for now I'll try to handle this gracefully.
    // While it may be convenient for a user to be able to use this object for all uri,
    // it may be difficult to manage all aspects gracefully.
    val documentId: String?
        get() {
			  return try {
				  if (DocumentsContract.isDocumentUri(mContext, uri)) {
					  DocumentsContract.getDocumentId(uri)
				  } else {
					  DocumentsContract.getTreeDocumentId(uri)
				  }
			  } catch (e: IllegalArgumentException) {
				  null
			  }
        }

    private val parentDocument: UsefulDocumentFile?
        get() {
            if (isFileScheme(uri)) {
                val f = File(uri.path)
                val parent = f.parentFile ?: return null
                return UsefulDocumentFile(null, mContext, Uri.fromFile(parent))
            }

            val documentId = documentId
            val parts = DocumentUtil.getPathSegments(documentId!!) ?: try {
                val treeId = DocumentsContract.getTreeDocumentId(uri)
                return UsefulDocumentFile.fromUri(mContext, DocumentsContract.buildTreeDocumentUri(uri.authority, treeId))
            } catch (e: IllegalArgumentException) {
                return null
            }

            val parentParts = Arrays.copyOfRange(parts, 0, parts.size - 1)
            val path = TextUtils.join("/", parentParts)
            val root = DocumentUtil.getRoot(documentId)
            val parentId = DocumentUtil.createNewDocumentId(root!!, path)

            val parentUri = if (DocumentUtil.hasTreeDocumentId(uri)) {
				  DocumentsContract.buildDocumentUriUsingTree(uri, parentId)
			  } else {
				  DocumentsContract.buildDocumentUri(uri.authority, parentId)
			  }
            return UsefulDocumentFile.fromUri(mContext, parentUri)
        }

    /**
     * Return the display name of this document.  Attempts to parse the name from the uri
     * when [DocumentsContractApi19.getName] fails.  It appears to fail
     * on hidden folders, possibly others.
     *
     * @see DocumentsContract.Document.COLUMN_DISPLAY_NAME
     */
    val name: String
        get() {
			  if (::cachedData.isInitialized)
				  return cachedData.name
			  return if (isFileScheme(uri)) File(uri.path).name
			  			else DocumentsContractApi19.getName(mContext, uri) ?: parseName(uri) ?: "error"
		  }

    /**
     * Return the MIME type of this document.
     *
     * @see DocumentsContract.Document.COLUMN_MIME_TYPE
     */
    val type: String?
        get() {
			  if (::cachedData.isInitialized && cachedData.type != null)
				  return cachedData.type
			  return if (isFileScheme(uri)) parseType(File(uri.path))
			  			else DocumentsContractApi19.getType(mContext, uri)
		  }

    /**
     * Indicates if this file represents a *directory*.
     *
     * @return `true` if this file is a directory, `false`
     * otherwise.
     * @see DocumentsContract.Document.MIME_TYPE_DIR
     */
    val isDirectory: Boolean
        get() {
			  if (::cachedData.isInitialized)
				  return cachedData.isDirectory
			  return if (isFileScheme(uri)) File(uri.path).isDirectory
			  			else DocumentsContractApi19.isDirectory(mContext, uri)
		  }

    /**
     * Indicates if this file represents a *file*.
     *
     * @return `true` if this file is a file, `false` otherwise.
     * @see DocumentsContract.Document.COLUMN_MIME_TYPE
     */
    val isFile: Boolean
        get() {
			  if (::cachedData.isInitialized)
				  return cachedData.isFile
			  return if (isFileScheme(uri)) File(uri.path).isFile
			  			else DocumentsContractApi19.isFile(mContext, uri)
		  }

	/**
	 * Returns the time when this file was last modified, measured in
	 * milliseconds since January 1st, 1970, midnight. Returns 0 if the file
	 * does not exist, or if the modified time is unknown.
	 *
	 * @return the time when this file was last modified.
	 * @see DocumentsContract.Document.COLUMN_LAST_MODIFIED
	 */
	val lastModified: Long
		get() {
			if(::cachedData.isInitialized)
				return cachedData.length
			return if (isFileScheme(uri)) File(uri.path).lastModified()
					else DocumentsContractApi19.lastModified(mContext, uri)
		}

	/**
	 * Returns the length of this file in bytes. Returns 0 if the file does not
	 * exist, or if the length is unknown. The result for a directory is not
	 * defined.
	 *
	 * @return the number of bytes in this file.
	 * @see DocumentsContract.Document.COLUMN_SIZE
	 */
	val length: Long
		get() {
			if(::cachedData.isInitialized)
				return cachedData.length
			return if (isFileScheme(uri)) File(uri.path).length()
					else DocumentsContractApi19.length(mContext, uri)
		}

	/**
	 * Indicates whether the current context is allowed to read from this file.
	 *
	 * @return `true` if this file can be read, `false` otherwise.
	 */
	val canRead: Boolean
		get() {
			if(::cachedData.isInitialized)
				return cachedData.canRead
			return if (isFileScheme(uri)) File(uri.path).canRead()
					else DocumentsContractApi19.canRead(mContext, uri)
		}


/**
 * Indicates whether the current context is allowed to write to this file.
 *
 * @return `true` if this file can be written, `false`
 * otherwise.
 * @see DocumentsContract.Document.COLUMN_FLAGS
 *
 * @see DocumentsContract.Document.FLAG_SUPPORTS_DELETE
 *
 * @see DocumentsContract.Document.FLAG_SUPPORTS_WRITE
 *
 * @see DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
 */
val canWrite: Boolean
	get() {
		if(::cachedData.isInitialized)
			return cachedData.canWrite
		return if (isFileScheme(uri)) File(uri.path).canWrite()
				else DocumentsContractApi19.canWrite(mContext, uri)
	}
    /**
     * For multiple file access calls it's beneficial to cache the data first.
     * If you cache the data, do not hold the reference for long periods of time as it
     * will potentially return stale data
     */
    fun cacheFileData() {
		 if (isFileScheme(uri))
			 cacheFile()
		 else {
			 cacheUri()
		 }
    }

	private fun cacheFile() {
		val f = File(uri.path)
		cachedData = FileData(
			f.canRead(),
			f.canWrite(),
			f.exists(),
			UsefulDocumentFile.parseType(f),
			Uri.fromFile(f),
			f.isDirectory,
			f.isFile,
			f.lastModified(),
			f.length(),
			f.name,
			Uri.fromFile(f.parentFile)
		)
	}

	/**
	 * Gather all file data in a single resolver call.  This is much faster if a code segment
	 * requires 2 or more calls to file-related data which individually involve resolver calls
	 */
	 private fun cacheUri() {
		val columns = arrayOf(
			DocumentsContract.Document.COLUMN_MIME_TYPE,
			DocumentsContract.Document.COLUMN_LAST_MODIFIED,
			DocumentsContract.Document.COLUMN_SIZE,
			DocumentsContract.Document.COLUMN_FLAGS,
			DocumentsContract.Document.COLUMN_DISPLAY_NAME)

		try {
			mContext.contentResolver.query(uri, columns, null, null, null).use { cursor ->
				if (cursor == null || cursor.count == 0) {
					return // This likely means !exists, nothing to cache
				} else {
					cursor.moveToFirst()

					// Ignore if grant doesn't allow read
					val readPerm = mContext.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
					val writePerm = mContext.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
					val rawType = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE))
					val flags = cursor.getInt(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS))
					val lastModified = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
					val length = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE))
					val hasMime = !TextUtils.isEmpty(rawType)
					val supportsDelete = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0
					val supportsCreate = flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
					val supportsWrite = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
					val name = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
					val isDirectory = DocumentsContract.Document.MIME_TYPE_DIR == rawType
					val type = if (isDirectory) null else rawType
					val isFile = if (isDirectory) false else hasMime

					cachedData = FileData(
						readPerm && hasMime,
						writePerm && (supportsDelete || isDirectory && supportsCreate || hasMime && supportsWrite),
						true,
						type,
						uri,
						isDirectory,
						isFile,
						lastModified,
						length,
						name ?: UsefulDocumentFile.parseName(uri) ?: "error",
						parentDocument?.uri
					)
				}
			}
		} catch (e: Exception) {
			// This is what DocumentContract.exists does, likely means !exists, nothing to cache
		}
	}
    init {
        this.uri = uri
    }

    /**
     * Search through [.listFiles] for the first document matching the
     * given display name. Returns `null` when no matching document is
     * found.
     */
    fun findFile(displayName: String): UsefulDocumentFile? {
        for (doc in listFiles()) {
            if (displayName == doc.name) {
                return doc
            }
        }
        return null
    }

    /**
     * Create a new document as a direct child of this directory.
     *
     * @param mimeType MIME type of new document, such as `image/png` or
     * `audio/flac`
     * @param displayName name of new document, without any file extension
     * appended; the underlying provider may choose to append the
     * extension
     * @return file representing newly created document, or null if failed
     * @see DocumentsContract.createDocument
     */
    fun createFile(mimeType: String?, displayName: String): UsefulDocumentFile? {
        return if (isFileScheme(uri)) {
			  var name = displayName
			  val mFile = File(uri.path)

			  // Tack on extension when valid MIME type provided
			  val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
			  if (extension != null) {
				  name += ".$extension"
			  }
			  val target = File(mFile, name)
			  try {
				  if (target.createNewFile())
					  UsefulDocumentFile(this, mContext, Uri.fromFile(target))
				  else
					  null
			  } catch (e: IOException) {
				  Log.w(TAG, "Failed to createFile: $e")
				  null
			  }
		  } else {
			  if (!Util.hasLollipop())
				  throw UnsupportedOperationException()

			  return try {
				  UsefulDocumentFile(
					  this,
					  mContext,
					  DocumentsContractApi21.createFile(mContext, uri, mimeType, displayName))
			  } catch (e: FileNotFoundException) {
				  null
			  }
		  }
    }

    /**
     * Create a new directory as a direct child of this directory.
     *
     * @param displayName name of new directory
     * @return file representing newly created directory, or null if failed
     * @see DocumentsContract.createDocument
     */
    fun createDirectory(displayName: String): UsefulDocumentFile? {
        return if (isFileScheme(uri)) {
			  val mFile = File(uri.path)

			  val target = File(mFile, displayName)
			  if (target.isDirectory || target.mkdir()) {
				  UsefulDocumentFile(this, mContext, Uri.fromFile(target))
			  } else {
				  null
			  }
		  } else {
			  if (!Util.hasLollipop())
				  throw UnsupportedOperationException()

			  try {
				  UsefulDocumentFile(
					  this,
					  mContext,
					  DocumentsContractApi21.createDirectory(mContext, uri, displayName))
			  } catch (e: FileNotFoundException) {
				  null
			  }
		  }
    }

    /**
     * Deletes this file.
     *
     *
     * Note that this method does *not* throw `IOException` on
     * failure. Callers must check the return value.
     *
     * @return `true` if this file was deleted, `false` otherwise.
     * @see DocumentsContract.deleteDocument
     */
    fun delete(): Boolean {
        return if (isFileScheme(uri)) deleteFile() else deleteUri()
    }

    private fun deleteFile(): Boolean {
        val mFile = File(uri.path)
        deleteContents(mFile)
        return mFile.delete()
    }

    private fun deleteUri(): Boolean {
        return DocumentsContractApi19.delete(mContext, uri)
    }

    /**
     * Returns a boolean indicating whether this file can be found.
     *
     * @return `true` if this file exists, `false` otherwise.
     */
    fun exists(): Boolean {
        return if (isFileScheme(uri)) File(uri.path).exists()
		  			else DocumentsContractApi19.exists(mContext, uri)
    }

    /**
     * Returns an array of files contained in the directory represented by this
     * file.
     *
     * @return an array of files or `null`.
     * @see DocumentsContract.buildChildDocumentsUriUsingTree
     */
    fun listFiles(): Array<UsefulDocumentFile> {
        return if (isFileScheme(uri)) {
			  val mFile = File(uri.path)
			  val results = ArrayList<UsefulDocumentFile>()
			  val files = mFile.listFiles()
			  if (files != null) {
				  for (file in files) {
					  results.add(UsefulDocumentFile(this, mContext, Uri.fromFile(file)))
				  }
			  }
			  results.toTypedArray()
		  } else {
			  if (!Util.hasLollipop())
				  throw UnsupportedOperationException()

			  DocumentsContractApi21.listFiles(mContext, uri).map {
				  UsefulDocumentFile(this, mContext, it)
			  }.toTypedArray()
		  }
    }

    /**
     * Renames this file to `displayName`.
     *
     *
     * Note that this method does *not* throw `IOException` on
     * failure. Callers must check the return value.
     *
     *
     * Some providers may need to create a new document to reflect the rename,
     * potentially with a different MIME type, so [.getUri] and
     * [.parseType] may change to reflect the rename.
     *
     *
     * When renaming a directory, children previously enumerated through
     * [.listFiles] may no longer be valid.
     *
     * @param displayName the new display name.
     * @return true on success.
     * @see DocumentsContract.renameDocument
     */
    fun renameTo(displayName: String): Boolean {
        return if (isFileScheme(uri)) renameToFile(displayName) else renameToUri(displayName)
    }

    private fun renameToFile(displayName: String): Boolean {
        val mFile = File(uri.path)
        val target = File(mFile.parentFile, displayName)
        if (mFile.renameTo(target)) {
            uri = Uri.fromFile(target)
            return true
        } else {
            return false
        }
    }

    private fun renameToUri(displayName: String): Boolean {
        if (!Util.hasLollipop())
            throw UnsupportedOperationException()

        val result: Uri?
        try {
            result = DocumentsContractApi21.renameTo(mContext, uri, displayName)
        } catch (e: FileNotFoundException) {
            return false
        }

        return if (result != null) {
            uri = result
            true
        } else {
            false
        }
    }

    companion object {
        private val TAG = UsefulDocumentFile::class.java.simpleName

		 @JvmStatic
		 fun fromUri(c: Context, uri: Uri): UsefulDocumentFile {
            var uri = uri
            if (DocumentUtil.isTreeUri(uri)) {    // A tree uri is not useful by itself
                uri = DocumentsContractApi21.prepareTreeUri(uri)   // Generate the document portion of uri
            }
            return UsefulDocumentFile(null, c, uri)
        }

        private fun parseName(uri: Uri?): String? {
            val pathParts = DocumentUtil.getPathSegments(uri)
            return if (pathParts != null) pathParts[pathParts.size - 1] else null
        }

        private fun parseType(mFile: File): String? {

            return if (mFile.isDirectory) {
                null
            } else {
                getTypeForName(mFile.name)
            }
        }

        private fun getTypeForName(name: String): String {
            val lastDot = name.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = name.substring(lastDot + 1).toLowerCase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                if (mime != null) {
                    return mime
                }
            }

            return "application/octet-stream"
        }

        private fun deleteContents(dir: File): Boolean {
            val files = dir.listFiles()
            var success = true
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        success = success and deleteContents(file)
                    }
                    if (!file.delete()) {
                        Log.w(TAG, "Failed to delete $file")
                        success = false
                    }
                }
            }
            return success
        }
	 }

	fun isFileScheme(uri: Uri): Boolean {
		return ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true)
	}
}

/**
 * POJO for storing all file data in one go.  If a user is interested in more than one
 * field at a time this will reduce many queries to a single query
 */
private data class FileData (
    val canRead: Boolean = false,
    val canWrite: Boolean = false,
    val exists: Boolean = false,
    val type: String? = null,
    val uri: Uri,
    val isDirectory: Boolean = false,
    val isFile: Boolean = false,
    val lastModified: Long = 0,
    val length: Long = 0,
    val name: String = "error",
    val parent: Uri? = null
)
