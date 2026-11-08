package ma.prosolar.maestro;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String HOME = "https://appassets.androidplatform.net/index.html";
    private static final String HOST = "appassets.androidplatform.net";
    private static final int PICK_FILE = 2001;
    private WebView view;
    private ValueCallback<Uri[]> fileCallback;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        view = new WebView(this);
        view.setBackgroundColor(0xfff5fbfc);
        setContentView(view);
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        view.addJavascriptInterface(new AppBridge(), "MaestroAndroid");
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                Uri url=request.getUrl();
                if(HOST.equals(url.getHost()) && "https".equals(url.getScheme())) return false;
                if("https".equals(url.getScheme()) || "http".equals(url.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW,url)); }
                    catch(ActivityNotFoundException ignored) { toast("Aucun navigateur disponible"); }
                }
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                Uri u=request.getUrl();
                if(!"https".equals(u.getScheme()) || !HOST.equals(u.getHost())) return null;
                String p=u.getPath();
                if(p==null||p.equals("/")||p.equals("/index.html")) p="index.html";
                else p=p.substring(1);
                if(p.contains("..")||p.contains("\\")||!p.equals("index.html"))
                    return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",new java.util.HashMap<>(),new java.io.ByteArrayInputStream(new byte[0]));
                try {
                    InputStream in=getAssets().open(p);
                    return new WebResourceResponse("text/html","UTF-8",in);
                } catch(Exception e) {
                    return new WebResourceResponse("text/plain","UTF-8",404,"Not found",new java.util.HashMap<>(),new java.io.ByteArrayInputStream(new byte[0]));
                }
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if(fileCallback!=null) fileCallback.onReceiveValue(null);
                fileCallback=callback;
                try { startActivityForResult(params.createIntent(),PICK_FILE); return true; }
                catch(Exception e) {fileCallback=null;toast("Sélecteur de fichiers indisponible");return false;}
            }
            @Override public boolean onJsConfirm(WebView w,String url,String message,android.webkit.JsResult result) {
                new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton("Oui",(d,v)->result.confirm())
                    .setNegativeButton("Annuler",(d,v)->result.cancel()).show();
                return true;
            }
            @Override public boolean onJsAlert(WebView w,String url,String message,android.webkit.JsResult result) {
                new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton("OK",(d,v)->result.confirm()).show();
                return true;
            }
        });
        view.loadUrl(HOME);
    }
    private void toast(String s) { runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show()); }
    public final class AppBridge {
        @JavascriptInterface public void share(String text) {
            if(text==null||text.length()>30000)return;
            runOnUiThread(()->{
                Intent i=new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);
                try {startActivity(Intent.createChooser(i,"Envoyer à ChatGPT / MAESTRO"));}
                catch(Exception e){toast("Le partage n'est pas disponible");}
            });
        }
        @JavascriptInterface public void exportFile(String name,String body,String mime) {
            if(name==null||body==null||body.length()>2000000)return;
            String safe=name.replaceAll("[^a-zA-Z0-9._-]","_");
            if(safe.isEmpty())safe="maestro-export.txt";
            try {
                OutputStream out;
                if(Build.VERSION.SDK_INT>=29) {
                    ContentValues cv=new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME,safe);
                    cv.put(MediaStore.Downloads.MIME_TYPE,mime==null?"text/plain":mime);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/MAESTRO");
                    Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
                    if(uri==null)throw new java.io.IOException("Download URI missing");
                    out=getContentResolver().openOutputStream(uri);
                } else {
                    File dir=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if(dir==null)throw new java.io.IOException("Directory missing");
                    out=new FileOutputStream(new File(dir,safe));
                }
                if(out==null)throw new java.io.IOException("Cannot write");
                try(OutputStream f=out){f.write(body.getBytes(StandardCharsets.UTF_8));}
                toast("Enregistré dans Téléchargements/MAESTRO");
            }catch(Exception e){toast("Échec export : "+e.getMessage());}
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PICK_FILE&&fileCallback!=null){
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));
            fileCallback=null;
        }
    }
    @Override public void onBackPressed(){if(view!=null&&view.canGoBack())view.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(view!=null)view.destroy();super.onDestroy();}
}
