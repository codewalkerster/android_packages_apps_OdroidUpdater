package com.hardkernel.odroid.updater;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.opengl.Visibility;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private ProgressDialog mProgressDialog = null;
    private Process mProcess;
    private String mUnzipLocation = Environment.getExternalStorageDirectory() + "/";
    private String mZipFile = Environment.getExternalStorageDirectory() + "/update.zip";
    private String mZipFileMd5sum = mZipFile + ".md5sum";
    private String mUpdateDate;

    private static final String KERNEL_4412_START_SECTOR = "2455\n";
    private static final String KERNEL_5410_START_SECTOR = "1263\n";
    private static final String TAG = "Updater";

    private EditText mEt_URL;
    private Button mBtnDownload;
    private Button mBtnUpdate;
    private Button mBtnValidate;
    private Button mBtnGetLastVersion;
    private Button mBtnExtract;
    private Button mBtnWriteKernel;
    private TextView mTv_CheckLast;
    private TextView mTv_MD5SUMResult;
    private TextView mTv_MD5SUM;
    private CheckBox mCbUpdateUboot;

    private String mVersionURL = "http://dn.odroid.com/";
    private String mProductName;
    private static final String DOWNLOAD_SITE = "http://dn.odroid.com/[product]/update.zip";
    private static final String INFORM_NODE = "/sys/devices/platform/odroid-sysfs/inform0";
    
    private Handler mHandler;
    
    private static final int DIALOG_PROGRESS = 0;
    private static final int DOWNLOAD_DISMISS_DIALOG = 1;
    private static final int MD5SUM_DISMISS_DIALOG = 2;
    private static final int UNZIP_DISMISS_DIALOG = 3;
    private static final int GET_LAST_VERSION = 4;
    
    private String mDownloadResult;
    private String mMd5sumResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mProductName = getProductName();
        if (mProductName.equals("ODROID-XU/"))
            mVersionURL += "ODROID-XU/version";
        else
            mVersionURL += "4412/Android/version";
        
        mEt_URL = (EditText)this.findViewById(R.id.et_url);
        mEt_URL.setText(DOWNLOAD_SITE);        
        
        mTv_CheckLast = (TextView)findViewById(R.id.tv_check_last);
        mTv_MD5SUM = (TextView)findViewById(R.id.tv_md5sum);

        mBtnDownload = (Button)findViewById(R.id.btn_Download);
        mBtnDownload.setEnabled(false);
        
        mBtnDownload.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

                File update_zip = new File(mZipFile);
                if (update_zip.exists())
                    update_zip.delete();

                File update_zip_md5sum = new File(mZipFileMd5sum);
                if (update_zip_md5sum.exists())
                    update_zip_md5sum.delete();

                mTv_MD5SUM.setText("");
                mBtnUpdate.setEnabled(false);
                mBtnWriteKernel.setEnabled(false);

				mProgressDialog = new ProgressDialog(MainActivity.this);
	            mProgressDialog.setMessage("Downloading Zip File..");
	            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	            mProgressDialog.setCancelable(false);
	            mProgressDialog.show();
                new DownloadMapAsync().execute(mEt_URL.getText().toString(), mEt_URL.getText() + ".md5sum");
            }
            
        });
        
        mBtnGetLastVersion = (Button)findViewById(R.id.btn_get_last_version);
        mBtnGetLastVersion.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub  				
                
				mProgressDialog = new ProgressDialog(MainActivity.this);
	            mProgressDialog.setMessage("Downloading Version File..");
	            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	            mProgressDialog.setCancelable(false);
	            mProgressDialog.show();
                new DownloadMapAsync().execute(mVersionURL);
            }
            
        });
        
        mBtnValidate = (Button)findViewById(R.id.btn_Validate);
        mBtnValidate.setEnabled(false);
        File update_zip = new File(mZipFile);
        if (update_zip.exists())
            mBtnValidate.setEnabled(true);
            mBtnValidate.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    
                    checkValidate();
                    File file = new File(mUnzipLocation + "/update/u-boot.bin");
                    if (file.exists())
                        mCbUpdateUboot.setEnabled(true);
                        
                    
                }
            
        });
            
        mBtnExtract = (Button) findViewById(R.id.btn_extract);
        mBtnExtract.setEnabled(false);
        mBtnExtract.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                try {
                    unzip();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
            
        mCbUpdateUboot = (CheckBox)findViewById(R.id.cb_update_uboot);
        mCbUpdateUboot.setEnabled(false);
        
        mBtnUpdate = (Button)findViewById(R.id.btn_Update);
        mBtnUpdate.setEnabled(false);
        mBtnUpdate.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

                CheckBox cb_userdata_format = (CheckBox)findViewById(R.id.cb_userdata_format);
                CheckBox cb_fat_format = (CheckBox)findViewById(R.id.cb_fat_format);
                CheckBox cb_clear_uboot = (CheckBox)findViewById(R.id.cb_clear_uboot_env);
                CheckBox cb_update_uboot = (CheckBox)findViewById(R.id.cb_update_uboot);
                FileOutputStream fos;
                try {
                    fos = new FileOutputStream(INFORM_NODE);
                    int value = 0;
                    if (cb_userdata_format.isChecked())
                        value |= 1;
                    if (cb_fat_format.isChecked())
                        value |= 2;
                    if (cb_clear_uboot.isChecked())
                        value |= 4;
                    if (cb_update_uboot.isChecked())
                        value |= 8;

                    byte[] bytes = new byte[4];                        
                    bytes[0] = '0';
                    bytes[1] = 'x';
 
                    if (value == 0xa)
                        bytes[2] = 'a';
                    else if(value == 0xb)
                        bytes[2] = 'b';
                    else if (value == 0xc)
                        bytes[2] = 'c';
                    else if (value == 0xd)
                        bytes[2] = 'd';
                    else if (value == 0xe)
                        bytes[2] = 'e';
                    else if (value == 0xf)
                        bytes[2] = 'f';
                    else
                        bytes[2] = (byte)('0' + value);
                    bytes[3] = '\n';
                    fos.write(bytes);
                    fos.close();
                } catch (FileNotFoundException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                    return;
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return;
                }

                try {
                    OutputStream os = mProcess.getOutputStream();
                    String cmd = "/system/bin/reboot update";
                    os.write(cmd.getBytes());
                    os.flush();
                    os.close();
                    mProcess.waitFor();
                } catch ( Exception e) {
                    Log.d(TAG, "rooting X");
                }   
            }
        });
        
        mBtnWriteKernel = (Button)findViewById(R.id.btn_dd);
        mBtnWriteKernel.setEnabled(false);
        mBtnWriteKernel.setVisibility(View.GONE);
        mBtnWriteKernel.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                try {
                    OutputStream os = mProcess.getOutputStream();
                    String cmd = "dd if=";
                    cmd += (Environment.getExternalStorageDirectory() + "/update/zImage of=/dev/block/mmcblk0 seek=");
                    if (mProductName.equals("ODROID-XU/"))
                        cmd += KERNEL_5410_START_SECTOR;
                    else
                        cmd += KERNEL_4412_START_SECTOR;
                    os.write(cmd.getBytes());
                    os.flush();
                    os.close();
                    mProcess.waitFor();
                    Log.e(TAG, cmd);
                } catch (Exception e) {
                    Log.d(TAG, "rooting X");
                    mBtnWriteKernel.setEnabled(false);
                }
            }
        });
        
        mTv_MD5SUMResult = (TextView)findViewById(R.id.tv_md5sum_result);

        try {
            File update_zip_md5sum = new File(mZipFileMd5sum);
            if (update_zip_md5sum.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(mZipFileMd5sum));
                String sum = reader.readLine();
                reader.close();
                mTv_MD5SUM.setText(sum);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        mHandler = new Handler() {

			@Override
			public void handleMessage(Message msg) {
				// TODO Auto-generated method stub
				super.handleMessage(msg);
				switch (msg.what) {
				case DIALOG_PROGRESS:
				{
					mProgressDialog.setProgress(msg.arg2);
				}
					break;
				case DOWNLOAD_DISMISS_DIALOG:
				{
					mProgressDialog.dismiss();
					File update_zip_md5sum = new File(mZipFileMd5sum);
		            if (update_zip_md5sum.exists()) {
		                String sum = "";
		                try {
		                    BufferedReader reader = new BufferedReader(new FileReader(mZipFileMd5sum));
		                    try {
		                        sum = reader.readLine();
		                        reader.close();
		                    } catch (IOException e) {
		                        // TODO Auto-generated catch block
		                        e.printStackTrace();
		                    }
		                    mTv_MD5SUM.setText(sum);
		                } catch (FileNotFoundException e) {
		                    // TODO Auto-generated catch block
		                    e.printStackTrace();
		                }
		            }
		
		            if (mDownloadResult.equalsIgnoreCase("true")) {
		                mBtnValidate.setEnabled(true);
		            } else {
		                Toast.makeText(getBaseContext(), "Download fail!\nCheck your site!", Toast.LENGTH_LONG).show();
		            }
				}
					break;
				case GET_LAST_VERSION:
				{
				    mProgressDialog.dismiss();
	                String version_path = Environment.getExternalStorageDirectory() + "/version";
	                
	                try {
	                    File version = new File(version_path);
	                    if (version.exists()) {
	                        BufferedReader reader = new BufferedReader(new FileReader(version_path));
	                        mUpdateDate = reader.readLine();
	                        String url;
	                        do {
	                            url = reader.readLine();
	                            if (url != null)
	                                if (url.contains(mProductName))
	                                    break;
	                        } while (url != null);
	                        reader.close();
	                        if (url == null) {
	                            mBtnDownload.setEnabled(false);
	                            return;
	                        }

	                        mEt_URL.setText(url);
	                    }
	                } catch (IOException e) {
	                    // TODO Auto-generated catch block
	                    e.printStackTrace();
	                }
	                if (checkLastUpdate(mUpdateDate)) {
	                    mTv_CheckLast.setText("Updated Last Version");
	                    checkDownloadDialog();
	                }
	                mBtnDownload.setEnabled(true);
				}
				    break;
				case MD5SUM_DISMISS_DIALOG:
				{
					mProgressDialog.dismiss();
					if (mMd5sumResult.equalsIgnoreCase("true")) {
		                mTv_MD5SUMResult.setText("matching");
		                mBtnWriteKernel.setEnabled(true);
		                mBtnExtract.setEnabled(true);
		            } else {
		                mTv_MD5SUMResult.setText("not matching");
		            }
				}
            		break;
				case UNZIP_DISMISS_DIALOG:
				{
					mProgressDialog.dismiss();
					File file = new File(mUnzipLocation + "/update/u-boot.bin");
					mBtnUpdate.setEnabled(true);
		            if (file.exists())
		            	mCbUpdateUboot.setEnabled(true);
				}
					break;
				}
			}
        	
        };
    }
    
    public boolean isOnline() {
        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }
        return false;
    }
    
    private void checkDownloadDialog() {
        
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    mBtnDownload.setEnabled(true);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    finish();
                    break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Updated last firmware").setPositiveButton("Continue", dialogClickListener)
            .setNegativeButton("Finish", dialogClickListener).show();
    }
    
    public static String createChecksum(String filename) throws Exception {
        InputStream fis =  new FileInputStream(filename);

        byte[] buffer = new byte[1024];
        MessageDigest complete = MessageDigest.getInstance("MD5");
        int numRead;

        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                complete.update(buffer, 0, numRead);
            }
        } while (numRead != -1);

        fis.close();
        byte[] digest = complete.digest();
        StringBuffer sb = new StringBuffer();
        for (byte b : digest) {
            if ((int)(b & 0xff) <= 0xf)
                sb.append(Integer.toHexString(0));
            sb.append(Integer.toHexString((int) (b & 0xff)));
        }
        
        return sb.toString();
    }
    
    private String getProductName() {
        String product_name = "odroid";
        InputStream inputstream = null;
        try {
            inputstream = Runtime.getRuntime().exec("getprop")
                    .getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedReader bufferedReader = new BufferedReader(
                  new InputStreamReader(inputstream));

        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("ro.build.product")) {
                    Log.e(TAG, line);
                    product_name = line.substring(21, line.length() -1);
                    Log.e(TAG, product_name);
                }
            }
            bufferedReader.close();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        
        String model = "ODROID-U"; 

        if (product_name.equals("odroidx2"))
            model = "ODROID-X2/";
        else if (product_name.equals("odroidx"))
            model = "ODROID-X/";
        else if (product_name.equals("odroidq2"))
            model = "ODROID-Q2/";
        else if (product_name.equals("odroidq"))
            model = "ODROID-Q/";
        else if (product_name.equals("odroidxu"))
            model = "ODROID-XU/";
        
        return model;
    }
    
    public boolean checkLastUpdate(String date) {
        InputStream inputstream = null;
        try {
            inputstream = Runtime.getRuntime().exec("getprop")
                    .getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedReader bufferedReader = new BufferedReader(
                  new InputStreamReader(inputstream));

        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                Log.e(TAG, line);
                if (line.contains(date)) {
                    return true;
                }
            }
            bufferedReader.close();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            return false;
        }
        
        return false;
    }

    class CheckValidateAsync extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... param) {
            try {
            	mMd5sumResult = "true";
                String new_sum = createChecksum(param[0]);
                
                String sum = param[1];
                    
                for (int i = 0; i < new_sum.length(); i++) {
                    Log.e(TAG, sum.charAt(i) + " : " + new_sum.charAt(i));
                    if (sum.charAt(i) != new_sum.charAt(i)) {
                        mMd5sumResult = "false";
                        break;
                    }
                }
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        
            return null;
        }
        
        /*
        protected void onProgressUpdate(Integer... progress) {
            //Message msg = mHandler.obtainMessage();
            //msg.arg1 = DIALOG_PROGRESS;
            //msg.arg2 = progress[0];
            //mHandler.sendMessage(msg);
        }
        */

        @Override
        protected void onPostExecute(String unused) {
        	mHandler.sendEmptyMessage(MD5SUM_DISMISS_DIALOG);
        }
    }

    private void checkValidate() {
		mProgressDialog = new ProgressDialog(MainActivity.this);
        mProgressDialog.setMessage("Please Wait... Calculate checksum... ");
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
        new CheckValidateAsync().execute(mZipFile, mTv_MD5SUM.getText().toString());
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();  

        try {
            mProcess = Runtime.getRuntime().exec("su");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }       

        if (!isOnline()) {
            new AlertDialog.Builder(this)
            .setTitle("Check connection")
            .setMessage("Not found internet connection!")
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int which) { 
                   finish();
               }
            })
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
        }
      
        String sdcardState = android.os.Environment.getExternalStorageState();
        if (!sdcardState.contentEquals(android.os.Environment.MEDIA_MOUNTED)) {
            new AlertDialog.Builder(this)
            .setTitle("Check disk")
            .setMessage("Not found internal storage mounted!")
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int which) { 
                   finish();
               }
            })
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
        }
        
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        double sdAvailSize = (double)stat.getAvailableBlocks()
                           * (double)stat.getBlockSize();
        if (sdAvailSize < 700000000) {
            new AlertDialog.Builder(this)
            .setTitle("Check free space")
            .setMessage("Insufficient free space!\nAbout 700M free space is required")
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int which) { 
                   finish();
               }
            })
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
        }
    }

    class DownloadMapAsync extends AsyncTask<String, Integer, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }
        
        private String first_args; 

        @Override
        protected String doInBackground(String... aurl) {
            int count;
            first_args = aurl[0];
            try {
                for (int i = 0; i < aurl.length; i++) {
                    
                    URL url = new URL(aurl[i]);
                    URLConnection conexion = url.openConnection();
                    conexion.connect();
                    int lenghtOfFile = conexion.getContentLength();
                    InputStream input = new BufferedInputStream(url.openStream());
                    String save_file = Environment.getExternalStorageDirectory() + "/version";
                    if (aurl[i].contains("update.zip.md5sum"))
                        save_file = mZipFileMd5sum;
                    else if (aurl[i].contains("update.zip"))
                        save_file = mZipFile;
                    Log.e(TAG, save_file);
                    OutputStream output = new FileOutputStream(save_file);
                    byte data[] = new byte[1024];
                    long total = 0;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        publishProgress((int)((total*100)/lenghtOfFile));
                        output.write(data, 0, count);
                    }
                    output.close();
                    input.close();
                }

                mDownloadResult = "true";
            } catch (Exception e) {
                mDownloadResult = "false";
            }
            return null;
        }

        protected void onProgressUpdate(Integer... progress) {
            Message msg = mHandler.obtainMessage();
            msg.arg1 = DIALOG_PROGRESS;
            msg.arg2 = progress[0];
            mHandler.sendMessage(msg);
        }

        @Override
        protected void onPostExecute(String unused) {
        	if (first_args.equals(mVersionURL))
        	    mHandler.sendEmptyMessage(GET_LAST_VERSION);
        	else
        	    mHandler.sendEmptyMessage(DOWNLOAD_DISMISS_DIALOG);
        }
    }
    
    public void unzip() throws IOException {
        mProgressDialog = new ProgressDialog(MainActivity.this);
	    mProgressDialog.setMessage("Please Wait... Extracting zip file... ");
	    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
	    mProgressDialog.setCancelable(false);
        mProgressDialog.show();
        new UnZipTask().execute(mZipFile, mUnzipLocation);
    }
    
    private class UnZipTask extends AsyncTask<String, Void, Boolean> {
        @SuppressWarnings("rawtypes")
        @Override
        protected Boolean doInBackground(String... params) {
            File update_folder = new File(mUnzipLocation + "update");
            if (update_folder.exists()) {
                String[] myFiles;      

                myFiles = update_folder.list();  
                for (int i = 0; i < myFiles.length; i++) {  
                    File myFile = new File(update_folder, myFiles[i]);   
                    myFile.delete();  
                }
            }

            
            String filePath = params[0];
            String destinationPath = params[1];
            File archive = new File(filePath);
            try {
                ZipFile zipfile = new ZipFile(archive);
                for (Enumeration e = zipfile.entries(); e.hasMoreElements();) {
                    ZipEntry entry = (ZipEntry) e.nextElement();
                    unzipEntry(zipfile, entry, destinationPath);
                }
                UnZipUtil d = new UnZipUtil();
                d.unZip(mZipFile, mUnzipLocation);

                } catch (Exception e) {

                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
        	mHandler.sendEmptyMessage(UNZIP_DISMISS_DIALOG);
        }
        
        private void unzipEntry(ZipFile zipfile, ZipEntry entry, String outputDir) throws IOException {
            if (entry.isDirectory()) {
                createDir(new File(outputDir, entry.getName()));
                return;
            }

            File outputFile = new File(outputDir, entry.getName());
                if (!outputFile.getParentFile().exists()) {
                createDir(outputFile.getParentFile());
            }

            // Log.v("", "Extracting: " + entry);
            BufferedInputStream inputStream = new BufferedInputStream(zipfile.getInputStream(entry));
            BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
            try {

            } finally {

                outputStream.flush();
                outputStream.close();
                inputStream.close();
            }
        }

        private void createDir(File dir) {
            if (dir.exists()) {
                return;
            }

            if (!dir.mkdirs()) {
                throw new RuntimeException("Can not create dir " + dir);
            }
        }
    }

    public class UnZipUtil {
        public boolean unZip(String mZipFile, String ToPath) {
            InputStream is;
            ZipInputStream zis;
            try {
                String filename;
                is = new FileInputStream(mZipFile);
                zis = new ZipInputStream(new BufferedInputStream(is));          
                ZipEntry ze;
                byte[] buffer = new byte[1024];
                int count;

                while ((ze = zis.getNextEntry()) != null) {
                    // zapis do souboru
                    filename = ze.getName();

                    // Need to create directories if not exists, or
                    // it will generate an Exception...
                    if (ze.isDirectory()) {
                       File fmd = new File(ToPath + filename);
                       fmd.mkdirs();
                       continue;
                    }

                    FileOutputStream fout = new FileOutputStream(ToPath + filename);

                    // cteni zipu a zapis
                    while ((count = zis.read(buffer)) != -1) {
                        fout.write(buffer, 0, count);             
                    }

                    fout.close();               
                    zis.closeEntry();
                }

                zis.close();
            } catch(IOException e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }
    }
}
