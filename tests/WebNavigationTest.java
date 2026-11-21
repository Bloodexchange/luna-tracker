package app.luna.cycle;

public final class WebNavigationTest {
    private static int assertions;
    private static void check(boolean ok,String name){assertions++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args){
        check(WebNavigation.block("https://play.google.com/store/apps/details?id=com.zhiliaoapp.musically"),"HTTPS Play Store blocked");
        check(WebNavigation.block("https://PLAY.GOOGLE.COM/store/apps"),"case insensitive store host");
        check(WebNavigation.block("market://details?id=com.zhiliaoapp.musically"),"market deep link blocked");
        check(WebNavigation.block("intent://video#Intent;scheme=tiktok;end"),"intent deep link blocked");
        check(WebNavigation.block("snssdk1233://feed"),"native TikTok scheme blocked");
        check(WebNavigation.block("https://tiktok.onelink.me/123/abc"),"app handoff link blocked");
        check(WebNavigation.block("https://www.tiktok.com/download"),"TikTok download landing blocked");
        check(WebNavigation.block("https://www.tiktok.com/%64ownload/app"),"encoded download path blocked");
        check(WebNavigation.block("https://apps.apple.com/app/tiktok/id835599320"),"App Store blocked");
        check(!WebNavigation.block("https://www.tiktok.com/foryou"),"TikTok web feed allowed");
        check(!WebNavigation.block("https://www.tiktok.com/@creator/video/123"),"public video allowed");
        check(!WebNavigation.block("https://web.telegram.org/k/"),"Telegram allowed");
        check(!WebNavigation.block("https://accounts.google.com/"),"ordinary account login not mistaken for store");
        check(!WebNavigation.block("https://example.com/?next=play.google.com"),"query does not define destination host");
        check(!WebNavigation.block("https://play.google.com.example.org/"),"host suffix matched precisely");
        check(WebNavigation.block("file:///data/data/app.luna.cycle/"),"file scheme blocked");
        check(WebNavigation.block("javascript:alert(1)"),"script navigation blocked");
        check(WebNavigation.block("http://www.tiktok.com/"),"cleartext blocked");
        check(WebNavigation.block("https://user:pass@example.com/"),"credentials in navigation rejected");
        check(WebNavigation.block(null),"null navigation rejected");
        check(WebNavigation.block("not a valid URL"),"malformed navigation rejected");
        check(WebNavigation.isTikTok("https://www.tiktok.com/foryou"),"TikTok host recognized");
        check(!WebNavigation.isTikTok("https://tiktok.com.example.org/"),"lookalike not TikTok");
        String desktop=WebNavigation.desktopUserAgent("Mozilla/5.0 (Linux; Android 16; wv) Chrome/142.0.7444.1 Mobile Safari/537.36");
        check(desktop.contains("Chrome/142.0.7444.1"),"installed Chromium version retained");
        check(!desktop.contains("Android")&&!desktop.contains("Mobile")&&!desktop.contains("; wv"),"desktop UA has no mobile markers");
        check(WebNavigation.desktopUserAgent(null).contains("Mozilla/5.0"),"missing UA handled");
        check(WebNavigation.displayOrigin("https://accounts.example.com/login?code=secret").equals("https://accounts.example.com"),"login header contains origin without secrets");
        check(WebNavigation.displayOrigin("https://accounts.google.com.evil.example/login").equals("https://accounts.google.com.evil.example"),"lookalike origin displayed in full");
        check(WebNavigation.displayOrigin("https://example.com:8443/auth").equals("https://example.com:8443"),"nonstandard origin port displayed");
        check(WebNavigation.displayOrigin("https://example.com:443/auth").equals("https://example.com"),"default TLS port omitted");
        check(!WebNavigation.displayOrigin("about:blank").startsWith("https://"),"blank popup does not claim a verified host");
        check(!WebNavigation.displayOrigin("https://user:password@example.com").contains("password"),"URL credentials not shown in login header");
        System.out.println("PASS: "+assertions+" web navigation and browser-mode assertions");
    }
}
