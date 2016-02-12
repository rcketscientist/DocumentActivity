package com.anthonymandra.framework;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.support.v4.provider.DocumentFile;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class UsefulDocumentFile
{
    private final String TAG = UsefulDocumentFile.class.getSimpleName();
    private DocumentFile mDocument;
    private Context mContext;

    // These constants must be kept in sync with DocumentsContract
    private static final String PATH_DOCUMENT = "document";
    private static final String PATH_TREE = "tree";

    private static final String URL_SLASH = "%2F";
    private static final String URL_COLON = "%3A";

    UsefulDocumentFile(Context c, DocumentFile document)
    {
        mDocument = document;
        mContext = c;
    }

    public static UsefulDocumentFile fromFile(Context c, File file)
    {
        return new UsefulDocumentFile(c, DocumentFile.fromFile(file));
    }

	/**
     * Generates a {@link UsefulDocumentFile} from the given uri.
     *
     * Note: File-based uris are not supported in 4.4+!
     * @param c context
     * @param uri uri
     * @return
     * @throws IllegalArgumentException If the uri is unrecognized or file-based in 4.4+.
     */
    public static UsefulDocumentFile fromUri(Context c, Uri uri)
    {
        // TODO: Should probably check the DF value and return null if it is null to be clear.
        if (FileUtil.isFileScheme(uri))
        {
            if (Util.hasKitkat())
            {
                /*  Although not documented file-based DocumentFiles are entirely unsupported
                    in 4.4+.  They are there solely for backwards compatibility.  To avoid
                    any confusion on the matter we throw an exception here.*/
                 throw new IllegalArgumentException("File-based DocumentFile is unsupported in 4.4+.");
            }
            return new UsefulDocumentFile(c, DocumentFile.fromFile(new File(uri.getPath())));
        }
        /** It's important we retain tree because TreeDocumentFile are way more useful
         *  for ex. SingleDocumentFile cannot createDirectory or createFile even it represents
         *  a directory */
        else if (hasTreeDocumentId(uri))
            return new UsefulDocumentFile(c, DocumentFile.fromTreeUri(c, uri));
        else if (DocumentsContract.isDocumentUri(c, uri))
            return new UsefulDocumentFile(c, DocumentFile.fromSingleUri(c, uri));
        else
            throw new IllegalArgumentException("Invalid URI: " + uri);
    }

    public DocumentFile getDocumentFile()
    {
        return mDocument;
    }

    public UsefulDocumentFile getParentFile()
    {
        DocumentFile parent = mDocument.getParentFile();
        if (parent != null) return new UsefulDocumentFile(mContext, parent);

        return getParentDocument();
    }

    protected static String getRoot(String documentId)
    {
        String[] parts = documentId.split(":");
        if (parts.length > 0)
            return parts[0];
        return null;
    }

    protected static String[] getIdSegments(String documentId)
    {
        // usb:folder/file.ext would effectively be:
        // content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3Afolder%2Ffile.ext
        // We want to return "folder/file.ext"
        return documentId.split(":");
    }

    protected static String[] getPathSegments(String documentId)
    {
        // usb:folder/file.ext would effectively be:
        // content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3Afolder%2Ffile.ext
        // We want to return ["folder", "file.ext"]
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

    protected String getDocumentId()
    {
        if (DocumentsContract.isDocumentUri(mContext, mDocument.getUri()))
        {
            return DocumentsContract.getDocumentId(mDocument.getUri());
        }
        else
        {
            return DocumentsContract.getTreeDocumentId(mDocument.getUri());
        }
    }

    protected static String createNewDocumentId(String documentId, String path)
    {
        return getRoot(documentId) + ":" + path;
    }

	/**
     *  Uri-based DocumentFile do not support parent at all
     *  Try to garner the logical parent through the uri itself
     * @return
     */
    protected UsefulDocumentFile getParentDocument()
    {
        Uri uri = mDocument.getUri();
        // TODO: Since file-based DF are <4.4 only, this is likely just an artifact of testing files
        // on 5.0+ which won't work anyway, double check
        if (FileUtil.isFileScheme(uri))
        {
            File f = new File(uri.getPath());
            File parent = f.getParentFile();
            if (parent == null)
                return null;
            return UsefulDocumentFile.fromFile(mContext, parent);
        }

        String documentId = getDocumentId();

        // usb:folder/file.ext would effectively be:
        // content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3Afolder%2Ffile.ext
        // We expect ["folder", "file.ext"] and we want to eventually return:
        // content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3Afolder
        String[] parts = getPathSegments(documentId);
        if (parts == null)
        {
            // Might be a root, try to get the tree uri
            try
            {
                String treeId = DocumentsContract.getTreeDocumentId(uri);
                return UsefulDocumentFile.fromUri(mContext, DocumentsContract.buildTreeDocumentUri(uri.getAuthority(), treeId));
            }
            catch (IllegalArgumentException e)
            {
                return null;
            }
        }

        String[] parentParts = Arrays.copyOfRange(parts, 0, parts.length - 1);
        String path = TextUtils.join("/", parentParts);
        String parentId = createNewDocumentId(documentId, path);

        Uri parentUri;
        if (DocumentsContract.isDocumentUri(mContext, uri))
        {
            /** It's important we retain tree because TreeDocumentFile are way more useful
             *  for ex. SingleDocumentFile cannot createDirectory or createFile even it represents
             *  a directory */
            if (hasTreeDocumentId(uri))
            {
                parentUri = DocumentsContract.buildDocumentUriUsingTree(uri, parentId);
            }
            else
            {
                parentUri = DocumentsContract.buildDocumentUri(uri.getAuthority(), parentId);
            }
        }
        else
        {
            parentUri = DocumentsContract.buildTreeDocumentUri(uri.getAuthority(), parentId);
        }
        return UsefulDocumentFile.fromUri(mContext, parentUri);
    }

    public static boolean isTreeUri(Uri uri) {
        final List<String> paths = uri.getPathSegments();
        return (paths.size() == 2 && PATH_TREE.equals(paths.get(0)));
    }

    // From DocumentsContract but return null instead of throw
    public static String getTreeDocumentId(Uri uri) {
        final List<String> paths = uri.getPathSegments();
        if (paths.size() >= 2 && PATH_TREE.equals(paths.get(0))) {
            return paths.get(1);
        }
        return null;
    }

    // From DocumentsContract but return null instead of throw
    public static boolean hasTreeDocumentId(Uri uri) {
        return getTreeDocumentId(uri) != null;
    }

    public boolean canRead()
    {
        return mDocument.canRead();
    }

    public boolean canWrite()
    {
        return mDocument.canWrite();
    }

    public UsefulDocumentFile createDirectory(String displayName)
    {
        DocumentFile directory = mDocument.createDirectory(displayName);
        if (directory == null)
            return null;
        return new UsefulDocumentFile(mContext, directory);
    }

    public DocumentFile createFile(String mimeType, String displayName)
    {
        return mDocument.createFile(mimeType, displayName);
    }

    public boolean delete()
    {
        return mDocument.delete();
    }

    public boolean exists()
    {
        return mDocument.exists();
    }

    public DocumentFile findFile(String displayName)
    {
        return mDocument.findFile(displayName);
    }

    public String getName()
    {
        String name = mDocument.getName();
        if (name == null)
        {
            Uri uri = mDocument.getUri();
            // Some tree uris have no name...do it the hard way.
            Log.d(TAG, uri + " produced null getName().");
            if (FileUtil.isFileScheme(uri))
            {
                File f = new File(uri.getPath());
                return f.getName();
            }

            String[] pathParts = getPathSegments(getDocumentId());
            if (pathParts != null)
                return pathParts[pathParts.length-1];
        }
        return name;
    }

    public String getType()
    {
        return mDocument.getType();
    }

    public Uri getUri()
    {
        return mDocument.getUri();
    }

    public boolean isDirectory()
    {
        return mDocument.isDirectory();
    }

    public boolean isFile()
    {
        return mDocument.isFile();
    }

    public long lastModified()
    {
        return mDocument.lastModified();
    }

    public long length()
    {
        return mDocument.length();
    }

    public DocumentFile[] listFiles()
    {
        return mDocument.listFiles();
    }

    public boolean renameTo(String displayName)
    {
        return mDocument.renameTo(displayName);
    }

    public boolean equals(Object o)
    {
        return mDocument.equals(o);
    }

    public int hashCode()
    {
        return mDocument.hashCode();
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
        String childUriString = hierarchicalTreeUri.toString() + URL_SLASH + filename;
        return Uri.parse(childUriString);
    }
}
