package top.plusalpha.downloader;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class InstallerActivity extends Activity {
    ApplicationExecutors exec;
    ProgressDialog progressDialog;
    boolean isCanceled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_installer2);

        exec = new ApplicationExecutors();

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("A message");
        progressDialog.setIndeterminate(true);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(true);

        Intent intent = getIntent();
        //String action = intent.getAction();
        Uri data = intent.getData();

        Log.d("Installer", "data: " + data);

        isCanceled = false;
        progressDialog.show();

        File apksPath = new File(getCacheDir(), "apks");

        //noinspection ResultOfMethodCallIgnored
        apksPath.mkdirs();

        File apkFile = new File(apksPath, "app.apk");

        exec.getBackground().execute(() -> {
            String result = download(data.toString(), apkFile.toString());
            exec.getMainThread().execute(() -> postDownload(result, apkFile.toString()));
        });
        //intent.setDataAndType(data, "application/vnd.android.package-archive");
        //startActivity(intent);


    }

    private String download(String sourceUrl, String destinationPath) {
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(sourceUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return "Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage();
            }

            // this will be useful to display download percentage
            // might be -1: server did not report the length
            int fileLength = connection.getContentLength();

            // download the file
            input = connection.getInputStream();

            output = new FileOutputStream(destinationPath);

            byte[] data = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                // allow canceling with back button
                if (isCanceled) {
                    input.close();
                    return "Canceled";
                }
                total += count;
                // publishing the progress....
                if (fileLength > 0) // only if total length is known
                {
                    publishProgress((int) (total * 100 / fileLength));
                }
                output.write(data, 0, count);
            }
        } catch (Exception e) {
            return e.toString();
        } finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }

            if (connection != null)
                connection.disconnect();
        }

        return null;
    }

    private void publishProgress(int i) {
        exec.getMainThread().execute(() -> {
            progressDialog.setIndeterminate(false);
            progressDialog.setMax(100);
            progressDialog.setProgress(i);
        });
    }

    private void postDownload(String result, String destinationPath) {
        //mWakeLock.release();
        progressDialog.dismiss();
        //Log.d("Downloader", result);
        if (result != null)
            Toast.makeText(this,"Download error: "+result, Toast.LENGTH_LONG).show();
        else {
            Toast.makeText(this,"File downloaded", Toast.LENGTH_SHORT).show();

            Uri apkUri = FileProvider.getUriForFile(this, BuildConfig.LIBRARY_PACKAGE_NAME + ".provider", new File(destinationPath));

            Intent promptInstall = new Intent(Intent.ACTION_VIEW);
            promptInstall.setDataAndType(apkUri, "application/vnd.android.package-archive");
            promptInstall.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(promptInstall);
        }
    }
}

