package com.gouxiong.sleep;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public class VisionImageProvider extends ContentProvider {
    private static final String AUTHORITY = "com.gouxiong.sleep.vision";

    public static Uri newImageUri(Context context) {
        File dir = imageDir(context);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String name = "vision-capture-" + System.currentTimeMillis() + ".jpg";
        return Uri.parse("content://" + AUTHORITY + "/" + name);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("context unavailable");
        }
        File file = resolveFile(context, uri);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        int fileMode = ParcelFileDescriptor.MODE_READ_ONLY;
        if (mode != null && mode.contains("w")) {
            fileMode = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        }
        return ParcelFileDescriptor.open(file, fileMode);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Context context = getContext();
        if (context == null) return 0;
        File file = resolveFile(context, uri);
        return file.exists() && file.delete() ? 1 : 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private static File resolveFile(Context context, Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || !name.endsWith(".jpg") || name.contains("..") || name.contains("/")) {
            name = "vision-capture.jpg";
        }
        return new File(imageDir(context), name);
    }

    private static File imageDir(Context context) {
        return new File(context.getCacheDir(), "vision");
    }
}
