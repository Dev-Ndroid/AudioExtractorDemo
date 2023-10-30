package com.dev.audioextractordemo;

import android.util.Log;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by Govind on 02/05/2018.
 */
public class FileHelper {
    private static final int BUFFER_SIZE = 8192;//2048;
    private static String TAG = FileHelper.class.getName().toString();
    private static String parentPath = "";

    public static boolean zip(String sourcePath, String destinationPath, String destinationFileName, boolean includeParentFolder) {
        if (!new File(destinationPath).isDirectory()) {
            new File(destinationPath).mkdir();
        }
        FileOutputStream fileOutputStream = null;
        ZipOutputStream zipOutputStream = null;

        try {
            if (!destinationPath.endsWith(File.separator)) {
                destinationPath += File.separator;
            }

            String destination = destinationPath + destinationFileName;
            File file = new File(destination);
            if (!file.exists()) {
                file.createNewFile();
            }

//            sevenZFIleCompressor(sourcePath, file);

            fileOutputStream = new FileOutputStream(file);
            zipOutputStream = new ZipOutputStream(new BufferedOutputStream(fileOutputStream));

            if (includeParentFolder) {
                parentPath = new File(sourcePath).getParent() + File.separator;
            } else {
                parentPath = sourcePath;
            }

            Log.d("Zip", "sourcePath: " + sourcePath);

            zipFile(zipOutputStream, sourcePath);

            return true;
        } catch (IOException ioe) {
            Log.e(TAG, "Error zipping file: " + ioe.getMessage());
            return false;
        } finally {
            try {
                if (zipOutputStream != null) {
                    zipOutputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing zipOutputStream: " + e.getMessage());
            }
        }
    }

    private static void sevenZFIleCompressor(String sourcePath, File destinationFile) throws IOException {
        File fileToArchieve = new File(sourcePath);
        SevenZOutputFile sevenZOutput = new SevenZOutputFile(destinationFile);
        SevenZArchiveEntry entry7z = sevenZOutput.createArchiveEntry(fileToArchieve, fileToArchieve.getName());
        sevenZOutput.putArchiveEntry(entry7z);
//            sevenZOutput.write(Files.toB); //this is what I don't understand!!
        sevenZOutput.write(Files.readAllBytes(fileToArchieve.toPath()));
        sevenZOutput.closeArchiveEntry();
    }

    private static void zipFile(ZipOutputStream zipOutputStream, String sourcePath) throws IOException {
        java.io.File sourceFile = new java.io.File(sourcePath);

        if (sourceFile.exists()) {
            String entryPath = sourceFile.getName(); // Use the file's name as the entry path
            BufferedInputStream input;
            byte data[] = new byte[BUFFER_SIZE];

            FileInputStream fileInputStream = new FileInputStream(sourceFile.getPath());
            input = new BufferedInputStream(fileInputStream, BUFFER_SIZE);

            ZipEntry entry = new ZipEntry(entryPath);
            zipOutputStream.putNextEntry(entry);
            int count;
            while ((count = input.read(data, 0, BUFFER_SIZE)) != -1) {
                zipOutputStream.write(data, 0, count);
            }

            Log.e("Zipping", "File Zipping Success for: " + sourcePath);

            input.close();
        } else {
            Log.e("Zipping", "Source file does not exist: " + sourcePath);
        }
    }


    /*private static void zipFile(ZipOutputStream zipOutputStream, String sourcePath) throws IOException {

        java.io.File sourceFile = new java.io.File(sourcePath);
//        java.io.File[] fileList = files.listFiles();

        String entryPath = "";
        BufferedInputStream input;
        if (sourceFile.exists()) { // Check if fileList is not null
            byte data[] = new byte[BUFFER_SIZE];
            FileInputStream fileInputStream = new FileInputStream(sourceFile.getPath());
            input = new BufferedInputStream(fileInputStream, BUFFER_SIZE);
            entryPath = sourceFile.getAbsolutePath().replace(parentPath, "");

            ZipEntry entry = new ZipEntry(entryPath);
            zipOutputStream.putNextEntry(entry);
            int count;
            while ((count = input.read(data, 0, BUFFER_SIZE)) != -1) {
                zipOutputStream.write(data, 0, count);
            }
            Log.e("Zipping", "File Zipping Success");
            input.close();

        } else {
            Log.e("Zipping", "source file is null");

        }

    }
*/
    public static Boolean unzip(String sourceFile, String destinationFolder) {
        ZipInputStream zis = null;

        try {
            zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(sourceFile)));
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((ze = zis.getNextEntry()) != null) {
                String fileName = ze.getName();
                fileName = fileName.substring(fileName.indexOf("/") + 1);
                File file = new File(destinationFolder, fileName);
                File dir = ze.isDirectory() ? file : file.getParentFile();

                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Invalid path: " + dir.getAbsolutePath());
                if (ze.isDirectory()) continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }

            }
        } catch (IOException ioe) {
            Log.d(TAG, ioe.getMessage());
            return false;
        } finally {
            if (zis != null)
                try {
                    zis.close();
                } catch (IOException e) {

                }
        }
        return true;
    }

    public static void saveToFile(String destinationPath, String data, String fileName) {
        try {
            new File(destinationPath).mkdirs();
            File file = new File(destinationPath + fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            fileOutputStream.write((data + System.getProperty("line.separator")).getBytes());

        } catch (FileNotFoundException ex) {
            Log.d(TAG, ex.getMessage());
        } catch (IOException ex) {
            Log.d(TAG, ex.getMessage());
        }
    }
}