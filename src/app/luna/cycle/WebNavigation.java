package app.luna.cycle;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WebNavigation {
    private static URI parse(String url){try{return new URI(url);}catch(Exception ex){return null;}}
    private static boolean domain(String host,String suffix){return host.equals(suffix)||host.endsWith("."+suffix);}
    static boolean isTikTok(String url){URI u=parse(url);return u!=null&&u.getHost()!=null&&domain(u.getHost().toLowerCase(Locale.ROOT),"tiktok.com");}
    static boolean isStore(String url){
        URI u=parse(url);if(u==null)return false;
        String scheme=u.getScheme();
        if("market".equalsIgnoreCase(scheme)||"intent".equalsIgnoreCase(scheme))return true;
        if(u.getHost()==null)return false;
        String h=u.getHost().toLowerCase(Locale.ROOT);
        for(String d:new String[]{"play.google.com","market.android.com","apps.apple.com","itunes.apple.com","appgallery.huawei.com","galaxystore.samsung.com","onelink.me"})if(domain(h,d))return true;
        String p=u.getPath()==null?"":u.getPath().toLowerCase(Locale.ROOT);
        return domain(h,"tiktok.com")&&(p.equals("/download")||p.startsWith("/download/")||p.equals("/download-app")||p.startsWith("/download-app/"));
    }
    static boolean block(String url){
        URI u=parse(url);
        return u==null||!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||isStore(url);
    }
    static String desktopUserAgent(String installedUserAgent){
        Matcher m=Pattern.compile("Chrome/[0-9.]+").matcher(installedUserAgent==null?"":installedUserAgent);
        String chrome=m.find()?m.group():"Chrome/120.0.0.0";
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "+chrome+" Safari/537.36";
    }
}
