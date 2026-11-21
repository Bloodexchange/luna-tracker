package app.luna.cycle;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int BG=0xFFFFF8F5, INK=0xFF33282D, MUTED=0xFF85767C, ROSE=0xFF974860, PALE=0xFFF4E2E4;
    private Store store;
    private LinearLayout root, content;
    private boolean unlocked, normalSession, inSetup, authBusy;
    private int generation;
    private WebView browser;
    private FrameLayout browserFrame;
    private View videoView;
    private WebChromeClient.CustomViewCallback videoCallback;
    private boolean foreground;
    private final ReturnWindow returnWindow=new ReturnWindow();
    private final Handler lifecycleHandler=new Handler(Looper.getMainLooper());
    private final Runnable expireSession=new Runnable(){@Override public void run(){
        if(!unlocked || foreground)return;
        long now=SystemClock.elapsedRealtime();
        if(returnWindow.expired(now))lockToTracker();
        else lifecycleHandler.postDelayed(this,returnWindow.remaining(now));
    }};
    private static boolean webCleaning;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private YearMonth month=YearMonth.now();
    private final DateTimeFormatter dateFormat=DateTimeFormatter.ofPattern("d MMMM",new Locale("ru"));

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
        if(android.os.Build.VERSION.SDK_INT>=33) setRecentsScreenshotEnabled(false);
        WebView.setWebContentsDebuggingEnabled(false);
        store=new Store(this);
        try { normalSession=store.read().optBoolean("rememberNormal",false); route(); if(store.read().optBoolean("webCleanupPending",false) || store.read().optBoolean("clearWebOnExit",false)) eraseWebData(false); }
        catch(RuntimeException ex) { new AlertDialog.Builder(this).setMessage(ex.getMessage()).setPositiveButton("Закрыть",(d,w)->finish()).show(); }
    }
    private void route() {
        if(!store.read().has("normalHash")) signup();
        else if(normalSession) tracker();
        else login();
    }
    @Override protected void onStart() {
        super.onStart();
        // Check before exposing a retained WebView, even if Android froze the timer.
        if(unlocked && returnWindow.expired(SystemClock.elapsedRealtime()))lockToTracker();
    }
    @Override protected void onResume() {
        super.onResume();foreground=true;
        lifecycleHandler.removeCallbacks(expireSession);
        if(unlocked && returnWindow.expired(SystemClock.elapsedRealtime())) {lockToTracker();return;}
        returnWindow.clear();
        if(browser!=null){browser.onResume();browser.resumeTimers();browserFrame.setVisibility(View.VISIBLE);immersive(true);}
    }
    @Override protected void onPause() {
        super.onPause();
        foreground=false;generation++; authBusy=false;
        if(unlocked) {
            returnWindow.leave(SystemClock.elapsedRealtime());
            if(browser!=null){
                browser.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();});",null);
                browser.onPause();browser.pauseTimers();browserFrame.setVisibility(View.INVISIBLE);
            }
            lifecycleHandler.removeCallbacks(expireSession);
            lifecycleHandler.postDelayed(expireSession,returnWindow.remaining(SystemClock.elapsedRealtime()));
        } else if(!inSetup && store!=null && store.read().has("normalHash")) {
            // Clear any partially entered credentials when switching away from login.
            if(!normalSession) login();
        }
    }
    @Override protected void onDestroy() { lifecycleHandler.removeCallbacksAndMessages(null);generation++;unlocked=false;closeBrowser();worker.shutdownNow();super.onDestroy(); }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if(browser!=null && foreground){immersive(true);browserFrame.requestApplyInsets();}
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(focused && foreground && browser!=null)immersive(true);
    }
    @Override public void onBackPressed() {
        if(videoView!=null)hideVideo();
        else if(browser!=null) { if(browser.canGoBack())browser.goBack();else{closeBrowser();vault();} }
        else if(unlocked) { lockToTracker(); }
        else { super.onBackPressed(); }
    }
    private void lockToTracker() {
        lifecycleHandler.removeCallbacks(expireSession);returnWindow.clear();
        generation++; unlocked=false; closeBrowser();
        normalSession=true; store.put("rememberNormal",true); tracker();
    }
    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable rounded(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String value,int size,int color) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(0,dp(5),0,dp(5)); return t; }
    private TextView bold(String value,int size,int color) { TextView t=text(value,size,color); t.setTypeface(null,Typeface.BOLD); return t; }
    private void gap(LinearLayout l,int size) { View v=new View(this); l.addView(v,new LinearLayout.LayoutParams(1,dp(size))); }
    private LinearLayout vertical() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout card(LinearLayout parent) { LinearLayout l=vertical(); l.setPadding(dp(20),dp(18),dp(20),dp(18)); l.setBackground(rounded(Color.WHITE,24)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(14); parent.addView(l,p); return l; }
    private void screen(String eyebrow,String title,String subtitle) {
        immersive(false);
        inSetup=false;
        root=vertical(); root.setBackgroundColor(BG);
        root.setPadding(dp(22),dp(18),dp(22),dp(12));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(22),dp(12)+insets.getSystemWindowInsetTop(),dp(22),dp(12)+insets.getSystemWindowInsetBottom());return insets;});
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content=vertical(); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        TextView brand=bold("◔  Л У Н А",17,ROSE); content.addView(brand); gap(content,22);
        if(!eyebrow.isEmpty()) content.addView(text(eyebrow.toUpperCase(new Locale("ru")),11,MUTED));
        content.addView(bold(title,30,INK));
        if(!subtitle.isEmpty()) content.addView(text(subtitle,14,MUTED));
        gap(content,20); setContentView(root); root.requestApplyInsets();
    }
    private Button button(LinearLayout parent,String label,boolean primary,Runnable action) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(primary?Color.WHITE:ROSE);
        b.setBackground(rounded(primary?ROSE:PALE,16)); b.setPadding(dp(14),dp(8),dp(14),dp(8));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(8); p.bottomMargin=dp(4); b.setMinHeight(dp(50)); parent.addView(b,p);
        b.setOnClickListener(v->{ try { action.run(); } catch(RuntimeException ex) { message(ex.getMessage()==null?"Не удалось выполнить действие":ex.getMessage()); } }); return b;
    }
    private EditText input(LinearLayout parent,String label,boolean secret) {
        parent.addView(text(label,12,MUTED)); EditText e=new EditText(this); e.setSingleLine(true); e.setTextSize(16); e.setTextColor(INK); e.setPadding(dp(14),dp(12),dp(14),dp(12));
        e.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        e.setBackground(rounded(BG,12)); parent.addView(e,new LinearLayout.LayoutParams(-1,dp(52))); gap(parent,8); return e;
    }
    private void message(String value) { new AlertDialog.Builder(this).setMessage(value).setPositiveButton("Понятно",null).show(); }
    private void signup() {
        screen("Личный ритм","Знакомство с Луной","Календарь цикла и заметки о самочувствии. Данные остаются на телефоне."); inSetup=true;
        LinearLayout c=card(content); c.addView(bold("Обычный аккаунт",20,INK));
        EditText name=input(c,"Имя",false), login=input(c,"Логин",false), password=input(c,"Пароль · от 8 символов",true), confirm=input(c,"Повторите пароль",true);
        TextView error=text("",13,ROSE); c.addView(error);
        Button create=button(c,"Создать аккаунт",true,()->{
            String n=name.getText().toString().trim(), l=login.getText().toString().trim(), p=password.getText().toString();
            if(n.isEmpty() || l.length()<3 || p.length()<8 || !p.equals(confirm.getText().toString())) { error.setText("Укажите имя, логин от 3 символов и одинаковые пароли от 8 символов."); return; }
            if(authBusy) return; authBusy=true; error.setText("Сохраняем…"); int token=generation;
            worker.execute(()->{
                String hash=Core.credential(p.toCharArray());
                runOnUiThread(()->{
                    authBusy=false; if(token!=generation || isFinishing()) return;
                    store.put("normalName",n); store.put("normalLogin",l); store.put("normalHash",hash); store.put("cycleLength",28); store.put("periodLength",5); store.put("start",LocalDate.now().toString()); store.put("rememberNormal",true);
                    normalSession=true; configureVault();
                });
            });
        });
        content.addView(text("Локальный аккаунт: без сервера, e-mail и восстановления пароля. Удаление приложения удаляет его данные.",12,MUTED));
    }
    private void configureVault() {
        screen("Настройка входа","Дополнительный доступ","По желанию создайте отдельную пару логина и пароля. Она открывает закрытый раздел через обычную форму входа."); inSetup=true;
        LinearLayout c=card(content);
        EditText l=input(c,"Отдельный логин",false),p=input(c,"Пароль · от 12 символов",true),confirm=input(c,"Повторите пароль",true);
        TextView status=text("",13,ROSE); c.addView(status);
        button(c,"Сохранить дополнительный доступ",true,()->{
            String login=l.getText().toString().trim(), pass=p.getText().toString();
            if(login.length()<3 || login.equals(store.read().optString("normalLogin")) || pass.length()<12 || !pass.equals(confirm.getText().toString())) { status.setText("Нужен другой логин от 3 символов и одинаковые пароли от 12 символов."); return; }
            if(authBusy) return; authBusy=true; int token=generation; status.setText("Сохраняем…");
            worker.execute(()->{String hash=Core.credential(pass.toCharArray()); runOnUiThread(()->{authBusy=false; if(token!=generation || isFinishing()) return; store.put("vaultLogin",login);store.put("vaultHash",hash);tracker();});});
        });
        button(content,"Пока только календарь",false,this::tracker);
        content.addView(text("После настройки: выйдите из обычного аккаунта и введите дополнительный логин и пароль. После ухода в фон Луна вернётся к календарю.",13,MUTED));
    }
    private void login() {
        screen("С возвращением","Вход в аккаунт","Ваш календарь ждёт вас.");
        LinearLayout c=card(content); EditText l=input(c,"Логин",false),p=input(c,"Пароль",true); TextView status=text("",13,ROSE); c.addView(status);
        button(c,"Войти",true,()->{
            if(authBusy) return;
            JSONObject data=store.read(); long until=data.optLong("retryAt",0);
            if(System.currentTimeMillis()<until) {status.setText("Попробуйте через "+((until-System.currentTimeMillis())/1000+1)+" сек.");return;}
            String login=l.getText().toString().trim(),pass=p.getText().toString(); p.setText(""); authBusy=true; status.setText("Проверяем…"); int token=generation;
            worker.execute(()->{
                // Always evaluate both hashes, avoiding a fast path that reveals the extra username.
                boolean regular=Core.verify(pass.toCharArray(),data.optString("normalHash"));
                boolean privateOk=Core.verify(pass.toCharArray(),data.optString("vaultHash",data.optString("normalHash")));
                int result=login.equals(data.optString("normalLogin")) && regular?1:login.equals(data.optString("vaultLogin")) && data.has("vaultHash") && privateOk?2:0;
                runOnUiThread(()->{
                    authBusy=false; if(token!=generation || isFinishing()) return;
                    if(result==0) {int failures=data.optInt("failures",0)+1;store.put("failures",failures);if(failures>=5)store.put("retryAt",System.currentTimeMillis()+Math.min(300000L,30000L*(failures-4)));status.setText("Неверный логин или пароль");return;}
                    store.put("failures",0);store.put("retryAt",0);store.put("rememberNormal",true);normalSession=true;
                    if(result==2) {unlocked=true;vault();} else tracker();
                });
            });
        });
        content.addView(text("Данные хранятся только на этом устройстве.",12,MUTED));
    }
    private LocalDate start() { return LocalDate.parse(store.read().optString("start",LocalDate.now().toString())); }
    private void tracker() {
        month=YearMonth.now();
        JSONObject data=store.read();
        screen("Сегодня · "+LocalDate.now().format(dateFormat),"Привет, "+data.optString("normalName","ты"),"Немного внимания к себе — каждый день.");
        LinearLayout hero=card(content); hero.setBackground(rounded(PALE,26));
        int length=data.optInt("cycleLength",28); int day=Core.cycleDay(start(),LocalDate.now(),length);
        hero.addView(text("ВАШ РИТМ",11,ROSE)); hero.addView(bold(day==0?"Начало впереди":"День "+day,46,ROSE));
        hero.addView(text("из "+length+" дней цикла",15,INK)); gap(hero,12);
        hero.addView(text("Следующее начало · около "+Core.nextPeriod(start(),LocalDate.now(),length).format(dateFormat),14,INK));
        hero.addView(text("Оценка по введённым датам. Цикл может меняться.",12,MUTED));
        button(hero,"Отметить начало месячных",true,()->{
            LocalDate now=LocalDate.now(); DatePickerDialog picker=new DatePickerDialog(this,(view,y,m,d)->{store.put("start",LocalDate.of(y,m+1,d).toString());tracker();},now.getYear(),now.getMonthValue()-1,now.getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();
        });
        calendar(card(content));
        LinearLayout note=card(content); note.addView(bold("Как вы сегодня?",20,INK)); note.addView(text("Личная заметка на "+LocalDate.now().format(dateFormat),13,MUTED));
        EditText e=input(note,"Самочувствие",false); e.setSingleLine(false); e.setMinLines(2);
        JSONObject notes=data.optJSONObject("notes"); e.setText(notes==null?"":notes.optString(LocalDate.now().toString()));
        button(note,"Сохранить заметку",false,()->{try{JSONObject n=store.read().optJSONObject("notes");if(n==null)n=new JSONObject();n.put(LocalDate.now().toString(),e.getText().toString());store.put("notes",n);Toast.makeText(this,"Заметка сохранена",Toast.LENGTH_SHORT).show();}catch(Exception ex){message("Не удалось сохранить заметку");}});
        button(content,"Параметры цикла",false,this::cycleSettings);
        if(!data.has("vaultHash")) button(content,"Настроить дополнительный доступ",false,this::configureVault);
        button(content,"Выйти из аккаунта",false,()->{normalSession=false;store.put("rememberNormal",false);login();});
        content.addView(text("Календарь помогает вести наблюдения. Не подходит для определения безопасных дней или постановки диагноза.",12,MUTED));
    }
    private void calendar(LinearLayout panel) {
        Locale ru=new Locale("ru"); panel.addView(bold(month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy",ru)),20,INK));
        String[] weekdays={"Пн","Вт","Ср","Чт","Пт","Сб","Вс"}; LinearLayout row=new LinearLayout(this);
        for(String s:weekdays) {TextView t=text(s,12,MUTED);t.setGravity(Gravity.CENTER);row.addView(t,new LinearLayout.LayoutParams(0,dp(36),1));}panel.addView(row);
        int offset=month.atDay(1).getDayOfWeek().getValue()-1;
        int cells=((offset+month.lengthOfMonth()+6)/7)*7;
        JSONObject data=store.read(); int length=data.optInt("cycleLength",28),period=data.optInt("periodLength",5);
        for(int cell=0;cell<cells;cell++) {
            if(cell%7==0) {row=new LinearLayout(this);panel.addView(row);}
            int day=cell-offset+1; TextView t=text(day>0 && day<=month.lengthOfMonth()?Integer.toString(day):"",14,INK);t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1);lp.setMargins(dp(1),dp(2),dp(1),dp(2));row.addView(t,lp);
            if(day>0 && day<=month.lengthOfMonth()) {
                LocalDate date=month.atDay(day); int cycle=Core.cycleDay(start(),date,length);
                if(cycle>0 && cycle<=period)t.setBackground(rounded(PALE,12));
                if(date.equals(LocalDate.now())) {t.setBackground(rounded(ROSE,12));t.setTextColor(Color.WHITE);}
            }
        }
        panel.addView(text("Розовый — расчётные дни месячных · тёмный — сегодня",11,MUTED));
    }
    private void cycleSettings() {
        screen("Настройки","Ваш цикл","Укажите привычную длительность.");LinearLayout c=card(content);
        EditText cycle=input(c,"Длительность цикла · 15–90 дней",false),period=input(c,"Длительность месячных · 1–14 дней",false);
        cycle.setInputType(InputType.TYPE_CLASS_NUMBER);period.setInputType(InputType.TYPE_CLASS_NUMBER);
        cycle.setText(Integer.toString(store.read().optInt("cycleLength",28)));period.setText(Integer.toString(store.read().optInt("periodLength",5)));
        button(c,"Сохранить",true,()->{int a=Integer.parseInt(cycle.getText().toString()),b=Integer.parseInt(period.getText().toString());if(a<15||a>90||b<1||b>14||b>a)throw new IllegalArgumentException("Проверьте длительность");store.put("cycleLength",a);store.put("periodLength",b);tracker();});
        button(content,"К календарю",false,this::tracker);
    }
    private void vault() {
        if(!unlocked) {route();return;}
        screen("Личный доступ","Ваше пространство","После ухода из приложения у вас есть 2 минуты, чтобы вернуться. Затем закрытый раздел заблокируется.");
        LinearLayout web=card(content);web.addView(bold("Веб-сервисы",20,INK));web.addView(text("Работают внутри Луны. Отдельных значков и уведомлений нет. Это веб-версии, возможности которых определяет сервис.",13,MUTED));
        button(web,"Telegram Web",true,()->openWeb("Telegram","https://web.telegram.org/k/"));
        button(web,"TikTok Web",false,()->openWeb("TikTok","https://www.tiktok.com/"));
        button(web,"Удалить входы и данные веб-сервисов",false,()->new AlertDialog.Builder(this).setMessage("Будут удалены локальные веб-сессии и данные сайтов. Аккаунты Telegram и TikTok сохранятся.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->clearWeb()).show());
        CheckBox forget=new CheckBox(this);
        forget.setText("Удалять входы в сайты при закрытии"); forget.setTextColor(INK);
        forget.setChecked(store.read().optBoolean("clearWebOnExit",false)); web.addView(forget);
        forget.setOnCheckedChangeListener((b,checked)->{store.put("clearWebOnExit",checked);if(checked)eraseWebData(true);});
        web.addView(text("История переходов и веб-кэш очищаются при закрытии окна. При включении переключателя также запрашивается удаление cookie и веб-хранилищ: в сайты потребуется входить заново. Данные аккаунтов на серверах не удаляются.",12,MUTED));
        button(content,"Заблокировать и вернуться к календарю",true,this::lockToTracker);
        content.addView(text("Веб-страницы открываются внутри Луны и не добавляются в историю Chrome. Само приложение остаётся видно в настройках Android.",12,MUTED));
    }
    private void openWeb(String name,String url) {
        if(!unlocked)return;
        if(webCleaning || store.read().optBoolean("webCleanupPending",false)){eraseWebData(false);message("Завершается очистка веб-данных. Повторите открытие через несколько секунд.");return;}
        closeBrowser();
        if(webCleaning)return;
        if(store.read().optBoolean("clearWebOnExit",false))store.put("webCleanupPending",true);
        inSetup=false;
        browserFrame=new FrameLayout(this);browserFrame.setBackgroundColor(Color.BLACK);
        browserFrame.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.graphics.Insets cutout=insets.getInsets(WindowInsets.Type.displayCutout());
                int keyboard=insets.isVisible(WindowInsets.Type.ime())?insets.getInsets(WindowInsets.Type.ime()).bottom:0;
                v.setPadding(cutout.left,cutout.top,cutout.right,Math.max(cutout.bottom,keyboard));
            }else{
                android.view.DisplayCutout cutout=insets.getDisplayCutout();
                int bottom=insets.getSystemWindowInsetBottom();
                v.setPadding(cutout==null?0:cutout.getSafeInsetLeft(),cutout==null?0:cutout.getSafeInsetTop(),cutout==null?0:cutout.getSafeInsetRight(),bottom>dp(100)?bottom:0);
            }
            return insets;
        });
        WebView w=new WebView(this);browser=w;
        WebSettings s=w.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setGeolocationEnabled(false);s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setMediaPlaybackRequiresUserGesture(true);s.setSaveFormData(false);s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(w,false);
        w.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        w.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest r){r.deny();}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(videoView!=null || browser!=w || !foreground){callback.onCustomViewHidden();return;}
                videoView=view;videoCallback=callback;browserFrame.addView(view,new FrameLayout.LayoutParams(-1,-1));w.setVisibility(View.INVISIBLE);immersive(true);
            }
            @Override public void onHideCustomView(){if(browser==w)hideVideo();}
        });
        w.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return !"https".equals(req.getUrl().getScheme());}
            @Override public void onReceivedSslError(WebView view,android.webkit.SslErrorHandler handler,android.net.http.SslError error){handler.cancel();message("Сервис не подтвердил безопасное соединение.");}
            @Override public void onReceivedError(WebView view,WebResourceRequest req,android.webkit.WebResourceError error){if(req.isForMainFrame())Toast.makeText(MainActivity.this,"Не удалось загрузить сервис. Проверьте интернет.",Toast.LENGTH_LONG).show();}
        });
        w.setDownloadListener((u,agent,disposition,mime,length)->message("Загрузки отключены, чтобы файлы не попадали в общий список телефона."));
        browserFrame.addView(w,new FrameLayout.LayoutParams(-1,-1));
        TextView close=text("×",25,Color.WHITE);close.setGravity(Gravity.CENTER);close.setPadding(0,0,0,0);close.setBackground(rounded(0x9933282D,24));
        close.setContentDescription("Закрыть сайт и вернуться к списку сервисов");
        FrameLayout.LayoutParams closePosition=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);closePosition.setMargins(dp(8),dp(8),dp(8),dp(8));browserFrame.addView(close,closePosition);
        close.setOnClickListener(v->{closeBrowser();vault();});
        setContentView(browserFrame);immersive(true);browserFrame.requestApplyInsets();w.onResume();w.resumeTimers();w.loadUrl(url);
    }
    private void immersive(boolean enabled) {
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){
                if(enabled){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.systemBars());}
                else controller.show(WindowInsets.Type.systemBars());
            }
        }else getWindow().getDecorView().setSystemUiVisibility(enabled?
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private void hideVideo() {
        if(videoView==null)return;
        View old=videoView;videoView=null;
        if(old.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)old.getParent()).removeView(old);
        WebChromeClient.CustomViewCallback callback=videoCallback;videoCallback=null;
        if(browser!=null)browser.setVisibility(View.VISIBLE);
        if(callback!=null)callback.onCustomViewHidden();
    }
    private void closeBrowser() {
        if(browser==null)return;hideVideo();WebView old=browser;browser=null;
        old.stopLoading();old.onPause();old.pauseTimers();old.clearHistory();old.clearCache(true);
        if(old.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)old.getParent()).removeView(old);
        old.removeAllViews();old.destroy();browserFrame=null;immersive(false);
        if(store.read().optBoolean("clearWebOnExit",false))eraseWebData(false);
    }
    private void clearWeb() { closeBrowser(); eraseWebData(true); }
    private void eraseWebData(boolean notify) {
        if(webCleaning){if(notify)Toast.makeText(this,"Очистка уже выполняется",Toast.LENGTH_SHORT).show();return;}
        webCleaning=true;store.put("webCleanupPending",true);
        try {
            android.webkit.WebStorage.getInstance().deleteAllData();
            WebView temp=new WebView(this);temp.clearCache(true);temp.clearHistory();temp.destroy();
            CookieManager.getInstance().removeAllCookies(removed->{
                try{CookieManager.getInstance().flush();store.put("webCleanupPending",false);
                    if(notify)Toast.makeText(getApplicationContext(),"Cookie удалены; очистка веб-хранилищ запрошена",Toast.LENGTH_LONG).show();
                }catch(RuntimeException ex){Toast.makeText(getApplicationContext(),"Очистка не завершена. Перезапустите Луну.",Toast.LENGTH_LONG).show();}
                finally{webCleaning=false;}
            });
        } catch(RuntimeException ex){webCleaning=false;Toast.makeText(getApplicationContext(),"Очистка не завершена. Перезапустите Луну.",Toast.LENGTH_LONG).show();}
    }
}
