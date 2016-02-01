/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.anthonymandra.framework;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class containing some static utility methods.
 */
public class Util
{
    private static final String TAG = Util.class.getSimpleName();

    public static boolean hasFroyo()
    {
        // Can use static final constants like FROYO, declared in later versions
        // of the OS since they are inlined at compile time. This is guaranteed behavior.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
    }

    public static boolean hasGingerbread()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    }

    public static boolean hasHoneycomb()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean hasHoneycombMR1()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
    }

    public static boolean hasIceCreamSandwich()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    public static boolean hasJellyBean()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    public static boolean hasKitkat()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    public static boolean hasLollipop()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static void closeSilently(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException t) {
            Log.w(TAG, "close fail ", t);
        }
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
    @TargetApi(9)
    public static long getUsableSpace(File path)
    {
        if (Util.hasGingerbread())
        {
            return path.getUsableSpace();
        }
        final StatFs stats = new StatFs(path.getPath());
        return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
    }

    public static String swapExtention(String filename, String ext)
    {
        return filename.replaceFirst("[.][^.]+$", "") + "." + ext;
    }

    public static boolean isTabDelimited(Context c, Uri uri)
    {
        int numberOfLevels = 0;
        BufferedReader readbuffer = null;
        try
        {
            ParcelFileDescriptor pfd = c.getContentResolver().openFileDescriptor(uri, "r");
            readbuffer = new BufferedReader(new FileReader(pfd.getFileDescriptor()));
            String line;
            while ((line = readbuffer.readLine()) != null)
            {
                String tokens[] = line.split("\t");
                numberOfLevels = Math.max(numberOfLevels, tokens.length);
            }
        } catch (IOException e)
        {
            return false;
        }
        finally
        {
            Util.closeSilently(readbuffer);
        }
        return numberOfLevels > 0;
    }

    public static boolean isTabDelimited(String filepath)
    {
        int numberOfLevels = 0;
        BufferedReader readbuffer = null;
        try
        {
            readbuffer = new BufferedReader(new FileReader(filepath));
            String line;
            while ((line = readbuffer.readLine()) != null)
            {
                String tokens[] = line.split("\t");
                numberOfLevels = Math.max(numberOfLevels, tokens.length);
            }
        } catch (IOException e)
        {
            return false;
        }
        finally
        {
            Util.closeSilently(readbuffer);
        }
        return numberOfLevels > 0;
    }

    private static boolean endsWith(String[] extensions, String path)
    {
        for (String ext : extensions)
        {
            if (path.toLowerCase().endsWith(ext.toLowerCase()))
                return true;
        }
        return false;
    }

    public static byte[] getBytes(InputStream inputStream) throws IOException
    {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    /**
     * Returns intent that  opens app in Google Play or Amazon Appstore
     *
     * @param context
     * @param packageName
     * @return null if no market available, otherwise intent
     */
    public static Intent getStoreIntent(Context context, String packageName)
    {
        Intent i = new Intent(Intent.ACTION_VIEW);
        String url = "market://details?id=" + packageName;
        i.setData(Uri.parse(url));

        if (isIntentAvailable(context, i))
        {
            return i;
        }

        i.setData(Uri.parse("http://www.amazon.com/gp/mas/dl/android?p=" + packageName));
        if (isIntentAvailable(context, i))
        {
            return i;
        }
        return null;

    }

    public static boolean isIntentAvailable(Context context, Intent intent)
    {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
