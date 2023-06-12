package com.dev.audioextractordemo

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.*

object FileUtils {
    fun getPath(context: Context, uri: Uri): String? {
        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // LocalStorageProvider
            /*if (isLocalStorageDocument(uri)) {
                // The path is the id
                return DocumentsContract.getDocumentId(uri);
            }*/
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
                )
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(
                    split[1]
                )
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        }
        // MediaStore (and general)
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            if (isGooglePhotosUri(uri)) return uri.lastPathSegment
            val cR = context.contentResolver
            val mime = MimeTypeMap.getSingleton()
            val type = mime.getExtensionFromMimeType(cR.getType(uri))
            var input: InputStream? = null
            var file: File? = null
            try {
                try {
                    input = context.contentResolver.openInputStream(uri)
                    //You can use this bitmap according to your purpose or Set bitmap to imageview
                    /* ----------- Support only to Android 10 --------------
                    String file_path = Environment.getExternalStorageDirectory().getAbsolutePath() +
                            "/" + context.getString(R.string.app_name) + "/" + type;
                    File dir = new File(file_path);
                    if (!dir.exists())
                        dir.mkdirs();
                    file = new File(dir, type + "Entity" + (int) System.currentTimeMillis() + "." + type);
                    --------------Support only to Android 10 ------------------*/
                    /* ------------------ Android 11 Supported ---------------*/
                    val storageDir = File(context.cacheDir, context.getString(R.string.app_name))
                    storageDir.mkdirs()
                    file = File(
                        storageDir,
                        type + "Entity" + System.currentTimeMillis().toInt() + "." + type
                    )
                    val output: OutputStream = FileOutputStream(file)
                    /* ------------------ Android 11 Supported ---------------*/try {
                        val buffer = ByteArray(4 * 1024) // or other buffer size
                        var read: Int
                        while (input!!.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    } finally {
                        output.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace() // handle exception, define IOException and others
                }
            } finally {
                try {
                    input?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            if (file != null) return file.absolutePath
            /* // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();
            try {
                InputStream is = context.getContentResolver().openInputStream(uri);
                if (is != null) {
                    Bitmap pictureBitmap = BitmapFactory.decodeStream(is);
                    //You can use this bitmap according to your purpose or Set bitmap to imageview
                    String file_path = Environment.getExternalStorageDirectory().getAbsolutePath() +
                            "/Images";
                    File dir = new File(file_path);
                    if (!dir.exists())
                        dir.mkdirs();
                    File file = new File(dir, "ImageEntity" + (int) System.currentTimeMillis() + ".png");
                    FileOutputStream fOut = new FileOutputStream(file);
                    pictureBitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
                    fOut.flush();
                    fOut.close();
                    return file.getAbsolutePath();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        } else if (uri.toString().startsWith("content://com.google.android.apps.photos.content")) {
            try {
                val `is` = context.contentResolver.openInputStream(uri)
                if (`is` != null) {
                    val pictureBitmap = BitmapFactory.decodeStream(`is`)
                    //You can use this bitmap according to your purpose or Set bitmap to imageview
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }
        return null
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     * @author dhruvraval
     */
    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     * @author dhruvraval
     */
    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     * @author dhruvraval
     */
    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     * @author dhruvraval
     */
    fun getDataColumn(
        context: Context, uri: Uri?, selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(
            column
        )
        try {
            cursor = context.contentResolver.query(
                uri!!, projection, selection, selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                if (BuildConfig.DEBUG) DatabaseUtils.dumpCursor(cursor)
                val column_index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(column_index)
            }
        } finally {
            cursor?.close()
        }
        return null
    }
}