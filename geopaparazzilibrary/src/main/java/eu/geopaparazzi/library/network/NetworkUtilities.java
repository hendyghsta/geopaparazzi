/*
 * Geopaparazzi - Digital field mapping on Android based devices
 * Copyright (C) 2016  HydroloGIS (www.hydrologis.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.geopaparazzi.library.network;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.AndroidHttpClient;
import android.util.Base64;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import eu.geopaparazzi.library.R;
import eu.geopaparazzi.library.database.GPLog;
import eu.geopaparazzi.library.util.GPDialogs;
import eu.geopaparazzi.library.util.TimeUtilities;

/**
 * Network utils methods.
 *
 * @author Andrea Antonello (www.hydrologis.com)
 */
@SuppressWarnings("nls")
public class NetworkUtilities {

    private static final String TAG = "NETWORKUTILITIES";
    /**
     *
     */
    public static final long maxBufferSize = 4096;
    public static final String SLASH = "/";

    /**
     * Read url to string.
     *
     * @param urlString the url.
     * @return the fetched text.
     * @throws Exception if something goes wrong.
     */
    public static String readUrl(String urlString) throws Exception {
        URL url = new URL(urlString);
        InputStream inputStream = url.openStream();
        BufferedReader bi = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = bi.readLine()) != null) {
            sb.append(line).append("\n");
        }
        inputStream.close();
        return sb.toString().trim();
    }

    private static HttpURLConnection makeNewConnection(String fileUrl) throws Exception {
        URL url = new URL(normalizeUrl(fileUrl));
        return (HttpURLConnection) url.openConnection();
    }

    private static String normalizeUrl(String url) {
        return normalizeUrl(url, false);
    }

    private static String normalizeUrl(String url, boolean addSlash) {
        if ( (!url.startsWith("http://")) && (!url.startsWith("https://"))) {
            url = "http://" + url;
        }
        if (addSlash && !url.endsWith(SLASH)) {
            url = url + SLASH;
        }
        return url;
    }
    /**
     * Sends an HTTP GET request to a url
     *
     * @param urlStr            - The URL of the server. (Example: " http://www.yahoo.com/search")
     * @param file              the output file. If it is a folder, it tries to get the file name from the header.
     * @param requestParameters - all the request parameters (Example: "param1=val1&param2=val2").
     *                          Note: This method will add the question mark (?) to the request -
     *                          DO NOT add it yourself
     * @param user              user.
     * @param password          password.
     * @return the file written.
     * @throws Exception if something goes wrong.
     */
    public static File sendGetRequest4File(String urlStr, File file, String requestParameters, String user, String password)
            throws Exception {
        if (requestParameters != null && requestParameters.length() > 0) {
            urlStr += "?" + requestParameters;
        }
        HttpURLConnection conn = makeNewConnection(urlStr);
        conn.setRequestMethod("GET");
        // conn.setDoOutput(true);
        conn.setDoInput(true);
        // conn.setChunkedStreamingMode(0);
        conn.setUseCaches(false);

        if (user != null && password != null && user.trim().length() > 0 && password.trim().length() > 0) {
            conn.setRequestProperty("Authorization", getB64Auth(user, password));
        }
        conn.connect();

        if (file.isDirectory()) {
            // try to get the header
            String headerField = conn.getHeaderField("Content-Disposition");
            String fileName = null;
            if (headerField != null) {
                String[] split = headerField.split(";");
                for (String string : split) {
                    String pattern = "filename=";
                    if (string.toLowerCase().startsWith(pattern)) {
                        fileName = string.replaceFirst(pattern, "");
                        break;
                    }
                }
            }
            if (fileName == null) {
                // give a name
                fileName = "FILE_" + TimeUtilities.INSTANCE.TIMESTAMPFORMATTER_LOCAL.format(new Date());
            }
            file = new File(file, fileName);
        }

        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = conn.getInputStream();
            out = new FileOutputStream(file);

            byte[] buffer = new byte[(int) maxBufferSize];
            int bytesRead = in.read(buffer, 0, (int) maxBufferSize);
            while (bytesRead > 0) {
                out.write(buffer, 0, bytesRead);
                bytesRead = in.read(buffer, 0, (int) maxBufferSize);
            }
            out.flush();
        } finally {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (conn != null)
                conn.disconnect();
        }
        return file;
    }

    /**
     * Sends a string via POST to a given url.
     *
     * @param context      the context to use.
     * @param urlStr       the url to which to send to.
     * @param string       the string to send as post body.
     * @param user         the user or <code>null</code>.
     * @param password     the password or <code>null</code>.
     * @param readResponse if <code>true</code>, the response from the server is read and parsed as return message.
     * @return the response.
     * @throws Exception if something goes wrong.
     */
    public static String sendPost(Context context, String urlStr, String string, String user, String password,
                                  boolean readResponse) throws Exception {
        BufferedOutputStream wr = null;
        HttpURLConnection conn = null;
        try {
            conn = makeNewConnection(urlStr);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // conn.setChunkedStreamingMode(0);
            conn.setUseCaches(false);
            if (user != null && password != null && user.trim().length() > 0 && password.trim().length() > 0) {
                conn.setRequestProperty("Authorization", getB64Auth(user, password));
            }
            conn.connect();

            // Make server believe we are form data...
            wr = new BufferedOutputStream(conn.getOutputStream());
            byte[] bytes = string.getBytes();
            wr.write(bytes);
            wr.flush();

            int responseCode = conn.getResponseCode();
            if (readResponse) {
                StringBuilder returnMessageBuilder = new StringBuilder();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    while (true) {
                        String line = br.readLine();
                        if (line == null)
                            break;
                        returnMessageBuilder.append(line + "\n");
                    }
                    br.close();
                }

                return returnMessageBuilder.toString();
            } else {
                return getMessageForCode(context, responseCode, context.getResources()
                        .getString(R.string.post_completed_properly));
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    /**
     * Sends a string via POST to a given url expecting a file in return.
     *
     * @param context    the context to use.
     * @param urlStr     the url to which to send to.
     * @param string     the string to send as post body.
     * @param user       the user or <code>null</code>.
     * @param password   the password or <code>null</code>.
     * @param outputFile the file to save to.
     * @throws Exception if something goes wrong.
     */
    public static void sendPostForFile(Context context, String urlStr, String string, String user, String password,
                                       File outputFile) throws Exception {
        BufferedOutputStream wr = null;
        HttpURLConnection conn = null;
        try {
            if (!urlStr.endsWith(SLASH)) {
                urlStr = urlStr + SLASH;
            }

            conn = makeNewConnection(urlStr);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // conn.setChunkedStreamingMode(0);
            conn.setUseCaches(false);
            if (user != null && password != null && user.trim().length() > 0 && password.trim().length() > 0) {
                conn.setRequestProperty("Authorization", getB64Auth(user, password));
            }
            conn.connect();

            // Make server believe we are form data...
            wr = new BufferedOutputStream(conn.getOutputStream());
            byte[] bytes = string.getBytes();
            wr.write(bytes);
            wr.flush();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream in = null;
                FileOutputStream out = null;
                long bytesCount = 0;
                try {
                    in = conn.getInputStream();
                    out = new FileOutputStream(outputFile);

                    byte[] buffer = new byte[(int) maxBufferSize];
                    int bytesRead = in.read(buffer, 0, (int) maxBufferSize);
                    while (bytesRead > 0) {
                        out.write(buffer, 0, bytesRead);
                        bytesRead = in.read(buffer, 0, (int) maxBufferSize);
                        bytesCount += bytesRead;
                    }
                    out.flush();
                } finally {
                    if (in != null)
                        in.close();
                    if (out != null)
                        out.close();
                }
                if (bytesCount == 0) {
                    throw new RuntimeException("Error downloading the data. Buffer was empty.");
                }
            } else {
                throw new RuntimeException("Error downloading the data. Got error code: " + responseCode);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    /**
     * Get a default message for an HTTP code.
     *
     * @param context          the context to use.
     * @param responseCode     the http code.
     * @param defaultOkMessage an optional message for the ok code.
     * @return the message.
     */
    public static String getMessageForCode(Context context, int responseCode, String defaultOkMessage) {
        Resources resources = context.getResources();
        switch (responseCode) {
            case HttpURLConnection.HTTP_OK:
                if (defaultOkMessage != null) {
                    return defaultOkMessage;
                } else {
                    return resources.getString(R.string.http_ok_msg);
                }
            case HttpURLConnection.HTTP_FORBIDDEN:
                return resources.getString(R.string.http_forbidden_msg);
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                return resources.getString(R.string.http_forbidden_msg);
            case HttpURLConnection.HTTP_NOT_FOUND:
                return resources.getString(R.string.http_not_found_msg);
            default:
                return resources.getString(R.string.http_not_implemented_code_msg) + " " + responseCode;
        }
    }

    /**
     * Sends a {@link MultipartEntity} post with text and image files.
     *
     * @param url        the url to which to POST to.
     * @param user       the user or <code>null</code>.
     * @param pwd        the password or <code>null</code>.
     * @param stringsMap the {@link HashMap} containing the key and string pairs to send.
     * @param filesMap   the {@link HashMap} containing the key and image file paths
     *                   (jpg, png supported) pairs to send.
     * @throws Exception if something goes wrong.
     */
    public static void sentMultiPartPost(String url, String user, String pwd, HashMap<String, String> stringsMap,
                                         HashMap<String, File> filesMap) throws Exception {
        HttpClient httpclient = new DefaultHttpClient();
        httpclient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
        HttpPost httppost = new HttpPost(url);

        if (user != null && pwd != null && user.trim().length() > 0 && pwd.trim().length() > 0) {
            String ret = getB64Auth(user, pwd);
            httppost.setHeader("Authorization", ret);
        }

        MultipartEntity mpEntity = new MultipartEntity();
        Set<Entry<String, String>> stringsEntrySet = stringsMap.entrySet();
        for (Entry<String, String> stringEntry : stringsEntrySet) {
            ContentBody cbProperties = new StringBody(stringEntry.getValue());
            mpEntity.addPart(stringEntry.getKey(), cbProperties);
        }

        Set<Entry<String, File>> filesEntrySet = filesMap.entrySet();
        for (Entry<String, File> filesEntry : filesEntrySet) {
            String propName = filesEntry.getKey();
            File file = filesEntry.getValue();
            if (file.exists()) {
                String ext = file.getName().toLowerCase().endsWith("jpg") ? "jpeg" : "png";
                ContentBody cbFile = new FileBody(file, "image/" + ext);
                mpEntity.addPart(propName, cbFile);
            }
        }

        httppost.setEntity(mpEntity);
        HttpResponse response = httpclient.execute(httppost);
        HttpEntity resEntity = response.getEntity();

        if (resEntity != null) {
            resEntity.consumeContent();
        }

        httpclient.getConnectionManager().shutdown();
    }

    private static String getB64Auth(String login, String pass) {
        String source = login + ":" + pass;
        String ret = "Basic " + Base64.encodeToString(source.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP);
        return ret;
    }

    /**
     * Checks is the network is available.
     *
     * @param context the {@link Context}.
     * @return true if the network is available.
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return false;
        } else {
            NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
            return (networkInfo != null && networkInfo.isConnected());
        }
    }

    /**
     * Checks is the mobile network is connected.
     *
     * @param context the {@link Context}.
     * @return true if the mobile network is connected.
     */
    public static boolean isConnectionMobile(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return false;
        }
        NetworkInfo networkInfo = connectivity.getNetworkInfo(ConnectivityManager.TYPE_MOBILE); // for wifi ConnectivityManager.TYPE_WIFI
        boolean isMobileConn = networkInfo.isConnected();
        return isMobileConn;
    }

    /**
     * Send a GET request.
     *
     * @param urlStr            the url.
     * @param requestParameters request parameters or <code>null</code>.
     * @param user              user or <code>null</code>.
     * @param password          password or <code>null</code>.
     * @return the fetched text.
     * @throws Exception if something goes wrong.
     */
    public static String sendGetRequest(String urlStr, String requestParameters, String user, String password) throws Exception {
        urlStr = normalizeUrl(urlStr);
        if (requestParameters != null && requestParameters.length() > 0) {
            urlStr += "?" + requestParameters;
        }
        StringBuilder builder = new StringBuilder();
        HttpClient client = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(urlStr);

        if (user != null && password != null && user.trim().length() > 0 && password.trim().length() > 0) {
            httpGet.addHeader("Authorization", getB64Auth(user, password));
        }
        HttpResponse response = client.execute(httpGet);
        StatusLine statusLine = response.getStatusLine();
        int statusCode = statusLine.getStatusCode();
        if (statusCode == 200) {
            HttpEntity entity = response.getEntity();
            InputStream content = entity.getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(content));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } else {
            String message = "Failed the http GET request.";
            IOException ioException = new IOException(message);
            GPLog.error(TAG, message, ioException);
            throw ioException;
        }
        return builder.toString();
    }

    /**
     * Send a file via HTTP POST with basic authentication.
     *
     * @param context  the context to use.
     * @param urlStr   the server url to POST to.
     * @param file     the file to send.
     * @param user     the user or <code>null</code>.
     * @param password the password or <code>null</code>.
     * @return the return string from the POST.
     * @throws Exception if something goes wrong.
     */
    public static String sendFilePost(Context context, String urlStr, File file, String user, String password) throws Exception {
        BufferedOutputStream wr = null;
        FileInputStream fis = null;
        HttpURLConnection conn = null;
        try {
            long fileSize = file.length();
            fis = new FileInputStream(file);
            // Authenticator.setDefault(new Authenticator(){
            // protected PasswordAuthentication getPasswordAuthentication() {
            // return new PasswordAuthentication("test", "test".toCharArray());
            // }
            // });
            if (!urlStr.endsWith(SLASH)) {
                urlStr = urlStr + SLASH;
            }
            urlStr = urlStr + "?name=" + file.getName();
            conn = makeNewConnection(urlStr);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
//            conn.setChunkedStreamingMode(0);
            conn.setUseCaches(true);

            // conn.setRequestProperty("Accept-Encoding", "gzip ");
            // conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Content-Length", "" + fileSize);
            // conn.setRequestProperty("Connection", "Keep-Alive");

            if (user != null && password != null && user.trim().length() > 0 && password.trim().length() > 0) {
                conn.setRequestProperty("Authorization", getB64Auth(user, password));
            }
            conn.connect();

            wr = new BufferedOutputStream(conn.getOutputStream());
            long bufferSize = Math.min(fileSize, maxBufferSize);

            if (GPLog.LOG)
                GPLog.addLogEntry(TAG, "BUFFER USED: " + bufferSize);
            byte[] buffer = new byte[(int) bufferSize];
            int bytesRead = fis.read(buffer, 0, (int) bufferSize);
            long totalBytesWritten = 0;
            while (bytesRead > 0) {
                wr.write(buffer, 0, (int) bufferSize);
                totalBytesWritten = totalBytesWritten + bufferSize;
                if (totalBytesWritten >= fileSize)
                    break;

                bufferSize = Math.min(fileSize - totalBytesWritten, maxBufferSize);
                bytesRead = fis.read(buffer, 0, (int) bufferSize);
            }
            wr.flush();

            int responseCode = conn.getResponseCode();
            return getMessageForCode(context, responseCode,
                    context.getResources().getString(R.string.file_upload_completed_properly));

        } catch (Exception e) {
            throw e;
        } finally {
            if (wr != null)
                wr.close();
            if (fis != null)
                fis.close();
            if (conn != null)
                conn.disconnect();
        }
    }

    private static String encodeFormData(HashMap<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for(Map.Entry<String, String> entry : params.entrySet()){
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }

        return result.toString();
    }

    public static CookieManager getAuthenticatedSession(String loginUrl, String user, String password) throws IOException {
        HttpURLConnection conn = null;
        CookieManager manager;
        if (CookieHandler.getDefault()!=null && (CookieHandler.getDefault() instanceof CookieManager)) {
            manager = (CookieManager) CookieHandler.getDefault();
        }
        else {
            //manager = new CookieManager();
            //CookieHandler previousDefault = CookieHandler.getDefault();
            manager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            //CookieManager manager = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            CookieHandler.setDefault(manager);
        }
        try {
            if (user != null && password != null && user.trim().length() > 0 && password.trim().length() > 0) {
                conn = makeNewConnection(loginUrl);
                conn.connect();
                if (conn.getResponseCode() != 200) {
                    String message = "Authentication failed. Check loginUrl. Response code: " + conn.getResponseCode() + ". Response message: " + conn.getResponseMessage();
                    IOException ioException = new IOException(message);
                    GPLog.error(TAG, message, ioException);
                    throw ioException;
                }
                String csrfToken = null;
                for (HttpCookie c : manager.getCookieStore().getCookies()) {
                    if (c.getName().equals("csrftoken") && c.getDomain().equals(new URL(loginUrl).getHost())) {
                        csrfToken = c.getValue();
                    }
                }
                if (csrfToken == null) {
                    String message = "Authentication failed.";
                    IOException ioException = new IOException(message);
                    GPLog.error(TAG, message, ioException);
                    throw ioException;
                }

                conn = makeNewConnection(loginUrl);
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");

                HashMap<String, String> auth = new HashMap<String, String>();
                auth.put("username", user);
                auth.put("password", password);
                auth.put("csrfmiddlewaretoken", csrfToken);
                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(os, "UTF-8"));
                writer.write(encodeFormData(auth));
                writer.flush();
                writer.close();
                os.close();
                conn.connect();
                if (conn.getResponseCode() != 200) {
                    String message = "Authentication failed. Response code: " + conn.getResponseCode() + ". Response message: " + conn.getResponseMessage();
                    IOException ioException = new IOException(message);
                    GPLog.error(TAG, message, ioException);
                    throw ioException;
                }
            }
        }
        catch (IOException ex) {
            throw ex;
        } catch (Exception e) {
            String message = "Authentication failed.";
            IOException ioException = new IOException(message, e);
            GPLog.error(TAG, message, ioException);
            throw ioException;
        }
        return manager;
    }

    /**
     * Send a GET request, previously authenticating on the provided loginUrl.
     * Necessary to properly authenticate with CSRF protected frameworks such
     * as Django.
     *
     * @param urlStr            the url.
     * @param requestParameters request parameters or <code>null</code>.
     * @param user              user or <code>null</code>.
     * @param password          password or <code>null</code>.
     * @param loginUrl          The URL used to authenticate
     * @return the fetched text.
     * @throws Exception if something goes wrong.
     */
    public static String sendGetRequest(String urlStr,
                                        String requestParameters,
                                        String user,
                                        String password,
                                        String loginUrl) throws Exception {

        loginUrl = normalizeUrl(loginUrl, true);
        CookieManager manager = getAuthenticatedSession(loginUrl, user, password);
        HttpURLConnection conn = null;
        BufferedReader in = null;
        try {
            conn = makeNewConnection(urlStr);
            conn.connect();

            in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } catch (Exception e) {
            throw e;
        } finally {
            if (in != null)
                in.close();
            if (conn != null)
                conn.disconnect();
        }
    }

    /**
     * Download a bitmap from a given url.
     * <p/>
     * http://android-developers.blogspot.it/2010/07/multithreading-for-performance.html
     *
     * @param url the url.
     * @return the downloaded bitmap or null.
     */
    public static Bitmap downloadBitmap(String url) {
        AndroidHttpClient client = null;
        HttpGet getRequest = null;
        try {
            client = AndroidHttpClient.newInstance("Android");
            getRequest = new HttpGet(url);

            HttpResponse response = client.execute(getRequest);
            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                return null;
            }
            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream inputStream = null;
                try {
                    inputStream = entity.getContent();
                    final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    return bitmap;
                } finally {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    entity.consumeContent();
                }
            }
        } catch (Exception e) {
            if (getRequest != null)
                getRequest.abort();
        } finally {
            if (client != null) {
                client.close();
            }
        }

        return null;
    }

    private static void setCsrfHeader(CookieManager session, HttpURLConnection connection) throws IOException {
        String csrfToken = null;
        for (HttpCookie c : session.getCookieStore().getCookies()) {
            if (c.getName().equals("csrftoken")) {
                csrfToken = c.getValue();
            }
        }
        if (csrfToken==null) {
            String message = "The session is not correctly authenticated.";
            IOException ioException = new IOException(message);
            GPLog.error(TAG, message, ioException);
            throw ioException;
        }
        connection.setRequestProperty("X-CSRFToken", csrfToken);
    }

    /**
     * Send a file via HTTP POST using Django style authentication
     *
     * @param context  the context to use.
     * @param urlStr   the server url to POST to.
     * @param file     the file to send.
     * @param user     the user or <code>null</code>.
     * @param password the password or <code>null</code>.
     * @param loginUrl   the login URL
     *
     * @return the return string from the POST.
     * @throws Exception if something goes wrong.
     */
    public static String sendFilePost(Context context,
                                      String urlStr,
                                      File file,
                                      String user,
                                      String password,
                                      String loginUrl) throws Exception {
        loginUrl = normalizeUrl(loginUrl, true);
        CookieManager manager = getAuthenticatedSession(loginUrl, user, password);
        BufferedOutputStream wr = null;
        FileInputStream fis = null;
        HttpURLConnection conn = null;
        try {
            long fileSize = file.length();
            fis = new FileInputStream(file);
            urlStr = normalizeUrl(urlStr, true);
            urlStr = urlStr + "?name=" + file.getName();
            conn = makeNewConnection(urlStr);
            conn.setRequestMethod("POST");
            setCsrfHeader(manager, conn);
            conn.setDoOutput(true);
            conn.setDoInput(true);
//            conn.setChunkedStreamingMode(0);
            conn.setUseCaches(true);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Content-Length", "" + fileSize);
            // conn.setRequestProperty("Connection", "Keep-Alive");

            conn.connect();

            wr = new BufferedOutputStream(conn.getOutputStream());
            long bufferSize = Math.min(fileSize, maxBufferSize);

            if (GPLog.LOG)
                GPLog.addLogEntry(TAG, "BUFFER USED: " + bufferSize);
            byte[] buffer = new byte[(int) bufferSize];
            int bytesRead = fis.read(buffer, 0, (int) bufferSize);
            long totalBytesWritten = 0;
            while (bytesRead > 0) {
                wr.write(buffer, 0, (int) bufferSize);
                totalBytesWritten = totalBytesWritten + bufferSize;
                if (totalBytesWritten >= fileSize)
                    break;

                bufferSize = Math.min(fileSize - totalBytesWritten, maxBufferSize);
                bytesRead = fis.read(buffer, 0, (int) bufferSize);
            }
            wr.flush();

            int responseCode = conn.getResponseCode();
            return getMessageForCode(context, responseCode,
                    context.getResources().getString(R.string.file_upload_completed_properly));

        } catch (Exception e) {
            throw e;
        } finally {
            if (wr != null)
                wr.close();
            if (fis != null)
                fis.close();
            if (conn != null)
                conn.disconnect();
        }
    }

    /**
     * Send a file via HTTP POST using Django style authentication
     *
     * @param context    the context to use.
     * @param urlStr     the url to which to send to.
     * @param string     the string to send as post body.
     * @param user       the user or <code>null</code>.
     * @param password   the password or <code>null</code>.
     * @param outputFile the file to save to.
     * @param loginUrl   the login URL
     * @throws Exception if something goes wrong.
     */
    public static void sendPostForFile(Context context,
                                       String urlStr,
                                       String string,
                                       String user,
                                       String password,
                                       File outputFile,
                                       String loginUrl) throws Exception {

        loginUrl = normalizeUrl(loginUrl, true);
        CookieManager manager = getAuthenticatedSession(loginUrl, user, password);

        BufferedOutputStream wr = null;
        HttpURLConnection conn = null;
        try {
            urlStr = normalizeUrl(urlStr, true);
            conn = makeNewConnection(urlStr);
            setCsrfHeader(manager, conn);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // conn.setChunkedStreamingMode(0);
            conn.setUseCaches(false);
            if (user != null && password != null && user.trim().length() > 0 && password.trim().length() > 0) {
                conn.setRequestProperty("Authorization", getB64Auth(user, password));
            }
            conn.connect();

            // Make server believe we are form data...
            wr = new BufferedOutputStream(conn.getOutputStream());
            byte[] bytes = string.getBytes();
            wr.write(bytes);
            wr.flush();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream in = null;
                FileOutputStream out = null;
                long bytesCount = 0;
                try {
                    in = conn.getInputStream();
                    out = new FileOutputStream(outputFile);

                    byte[] buffer = new byte[(int) maxBufferSize];
                    int bytesRead = in.read(buffer, 0, (int) maxBufferSize);
                    while (bytesRead > 0) {
                        out.write(buffer, 0, bytesRead);
                        bytesRead = in.read(buffer, 0, (int) maxBufferSize);
                        bytesCount += bytesRead;
                    }
                    out.flush();
                } finally {
                    if (in != null)
                        in.close();
                    if (out != null)
                        out.close();
                }
                if (bytesCount == 0) {
                    throw new RuntimeException("Error downloading the data. Buffer was empty.");
                }
            } else {
                throw new RuntimeException("Error downloading the data. Got error code: " + responseCode);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }




    // public static String uploadFile( Context context, String urlStr, File file, String user,
    // String password ) {
    // try {
    // FileInputStream fileInputStream = new FileInputStream(file);
    // String lineEnd = "\r\n";
    // String twoHyphens = "--";
    // String boundary = "*****";
    // // ------------------ CLIENT REQUEST
    // URL connectURL = new URL(urlStr);
    // HttpURLConnection conn = (HttpURLConnection) connectURL.openConnection();
    // conn.setDoInput(true);
    // conn.setDoOutput(true);
    // conn.setUseCaches(false);
    // conn.setRequestMethod("POST");
    // conn.setRequestProperty("Connection", "Keep-Alive");
    // conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
    //
    // DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
    // dos.writeBytes(twoHyphens + boundary + lineEnd);
    // dos.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" +
    // file.getName() + "\"" + lineEnd);
    // dos.writeBytes(lineEnd);
    //
    // // create a buffer of maximum size
    // int bytesAvailable = fileInputStream.available();
    // int maxBufferSize = 1024;
    // int bufferSize = Math.min(bytesAvailable, maxBufferSize);
    // byte[] buffer = new byte[bufferSize];
    // // read file and write it into form...
    // int bytesRead = fileInputStream.read(buffer, 0, bufferSize);
    // while( bytesRead > 0 ) {
    // dos.write(buffer, 0, bufferSize);
    // bytesAvailable = fileInputStream.available();
    // bufferSize = Math.min(bytesAvailable, maxBufferSize);
    // bytesRead = fileInputStream.read(buffer, 0, bufferSize);
    // }
    // dos.writeBytes(lineEnd);
    // dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
    // fileInputStream.close();
    // dos.flush();
    //
    // InputStream is = conn.getInputStream();
    // int ch;
    // StringBuffer b = new StringBuffer();
    // while( (ch = is.read()) != -1 ) {
    // b.append((char) ch);
    // }
    // String s = b.toString();
    // Log.i("Response", s);
    // dos.close();
    // return s;
    //
    // } catch (Exception e) {
    // e.printStackTrace();
    // return null;
    // }
    // }

    // public void executeMultipartPost() throws Exception {
    //
    // try {
    // ByteArrayOutputStream bos = new ByteArrayOutputStream();
    // bm.compress(CompressFormat.JPEG, 75, bos);
    // byte[] data = bos.toByteArray();
    // HttpClient httpClient = new DefaultHttpClient();
    // HttpPost postRequest = new HttpPost(
    // "http://10.0.2.2/cfc/iphoneWebservice.cfc?returnformat=json&amp;method=testUpload");
    // ByteArrayBody bab = new ByteArrayBody(data, "forest.jpg");
    // // File file= new File("/mnt/sdcard/forest.png");
    // // FileBody bin = new FileBody(file);
    // MultipartEntity reqEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
    // reqEntity.addPart("uploaded", bab);
    // reqEntity.addPart("photoCaption", new StringBody("sfsdfsdf"));
    // postRequest.setEntity(reqEntity);
    // HttpResponse response = httpClient.execute(postRequest);
    // BufferedReader reader = new BufferedReader(new
    // InputStreamReader(response.getEntity().getContent(), "UTF-8"));
    // String sResponse;
    // StringBuilder s = new StringBuilder();
    // while( (sResponse = reader.readLine()) != null ) {
    // s = s.append(sResponse);
    // }
    // System.out.println("Response: " + s);
    // } catch (Exception e) {
    // Log.e(e.getClass().getName(), e.getMessage());
    // }
    // }

}